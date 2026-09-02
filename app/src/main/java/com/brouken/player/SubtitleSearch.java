package com.brouken.player;

import android.net.Uri;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.brouken.player.skip.SegmentFinder;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Where an online subtitle comes from. Four sources, all asked at once and answered in the order
 * below, each switchable on its own so a single source can be exercised in isolation.
 *
 * <ol>
 *   <li>{@link OpenSubtitles} (api.opensubtitles.com) — the fullest index, the only one that filters
 *       by language server-side and the only one that knows whether a file is machine-translated,
 *       how often it was downloaded and whether its uploader is trusted. First because that is what
 *       decides whether the file is any good, and the three below can offer none of it.</li>
 *   <li>rest.opensubtitles.org — the retired legacy API, still answering and still the quickest to
 *       reply. One language per request and a gzipped file at the end of it.</li>
 *   <li>opensubtitles-v3.strem.io — imdb only, unmetered, already normalised to UTF-8.</li>
 *   <li>shegu.st — tmdb only, unmetered, no language filter (the whole index arrives and is sifted
 *       here). Last because it is the least dependable: its index changed size twice within five
 *       minutes of testing, and about half its entries point at the host source 3 already is.</li>
 * </ol>
 *
 * <p>The keyed source led once before and was demoted during the build for being the only metered
 * one — 100 downloads a day against three that cost nothing. That was the wrong way round, and the
 * measurements in the feature's own notes said so at the time: the quota is counted per client, not
 * pooled, so a hundred a day is a hundred for this device alone, which no viewer reaches. What was
 * actually being saved was a budget nobody was spending, and what it cost was every search — the
 * free indexes have no machine-translation flag, so the file that wins is whatever they list first.
 * Its cost now: a search is free, and the metered call is the download, so a unit goes on any file
 * this source offers that is looked at — including the rare one that loses to a better language from
 * a free index further down.</p>
 *
 * Two of these normalise their URLs the way api.opensubtitles.com does and answer a redirect when
 * they are not given what they want: {@code rest.opensubtitles.org} needs its path segments in
 * alphabetical order, and hands back a 302 to the host {@code _} — an unfollowable address — when
 * they are not. Hence the deliberate ordering in {@link #restOpenSubtitles}.
 */
final class SubtitleSearch {

    private SubtitleSearch() {}

    static final String SOURCE_OPENSUBTITLES = "openSubtitles";
    static final String SOURCE_SHEGU = "shegu";
    static final String SOURCE_STREMIO = "stremio";
    static final String SOURCE_REST = "restOpenSubtitles";

    private static final String UA = "JustPlayer v" + BuildConfig.VERSION_NAME;

    private static final int TIMEOUT_SEC = 10;

    /**
     * Shared on purpose. A fresh client brings a fresh connection pool, and the first thing that costs
     * is a new TLS handshake to a host we are already talking to — which is exactly where shegu.st
     * stops answering: it serves the search and then lets a second connection time out.
     */
    static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SEC, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SEC, TimeUnit.SECONDS)
            .build();

    /**
     * How many links of the winning language are handed on, so a dead one is not the end of it — and
     * so is one that turns out to be timed for another cut of the film, which the fetcher only learns
     * by downloading it. Six rather than three because those are not rare: of the five Russian files
     * shegu.st lists for The Martian the first three are the extended cut, and the theatrical one a
     * 141-minute video needs is fourth.
     */
    private static final int MAX_URLS = 6;

    /** What the search found: the language it is in, and the links to try in order. */
    static final class Result {
        final String source;
        /** ISO 639-2/T, the form the priority list is written in. */
        final String language;
        final List<String> urls;
        /**
         * Somebody else's machine translation. Both OpenSubtitles indexes say so, the legacy one in
         * {@code SubAutoTranslation}; the Stremio addon and shegu.st carry no such field, so a false
         * here means "not known to be", not "human".
         */
        final boolean machine;
        /**
         * OpenSubtitles' file id, or 0 when {@link #urls} is already the whole answer. That index hands
         * out a download link rather than a URL, and asking for one is the metered call — one of the
         * hundred a day, spent even when it fails. So this source alone answers with an id, and
         * {@link #delivered} turns it into a link at the moment the file is about to be taken.
         */
        final long fileId;

        Result(String source, String language, List<String> urls, boolean machine) {
            this(source, language, urls, machine, 0L);
        }

        Result(String source, String language, List<String> urls, boolean machine, long fileId) {
            this.source = source;
            this.language = language;
            this.urls = urls;
            this.machine = machine;
            this.fileId = fileId;
        }
    }

    /** One file a source offers. */
    private static final class Candidate {
        final String language;  // ISO 639-2/T
        final int downloads;
        final String url;
        /** Machine-translated, where the source says so. False also means "does not say". */
        final boolean machine;
        /**
         * Where the file's last line sits, in ms, or 0 when the index does not say. Only
         * rest.opensubtitles.org does ({@code SubLastTS}) — and it is the one thing that tells the cuts
         * of a film apart before anything is downloaded. See {@link #ofOneCut}.
         */
        final long lastMs;

        Candidate(String language, int downloads, String url, boolean machine, long lastMs) {
            this.language = language;
            this.downloads = downloads;
            this.url = url;
            this.machine = machine;
            this.lastMs = lastMs;
        }
    }

    /** Takes a hit and reports whether the file behind it could actually be downloaded. */
    interface Sink {
        boolean accept(Result result);
    }

    /**
     * Walks the enabled sources until one has something in a preferred language <em>and</em> that file
     * can be fetched. Finding an entry in an index is not the same as getting the file: these hosts are
     * hobby projects and aggregators, and a link that is listed but dead used to end the whole search.
     *
     * @param preferred ISO 639-2/T codes, most wanted first — already cut to the languages worth
     *                  searching for by the caller
     * @param answered set when at least one source replied at all, whatever it replied. "Nobody has
     *                 this" and "there was no way to ask" are the same empty result otherwise, and
     *                 remembering the second as the first keeps a title unsearchable long after the
     *                 network came back.
     * @return the hit that was downloaded, or null when no enabled source could deliver one
     */
    static Result find(MediaId identifier, List<String> preferred, Prefs prefs, long durationMs,
                       Sink sink, AtomicBoolean answered) {
        if (identifier == null || identifier.isEmpty() || preferred.isEmpty()) {
            Utils.log("subtitles: nothing to ask with (id="
                    + (identifier == null ? "null" : identifier.isEmpty() ? "empty" : "ok")
                    + ", want=" + preferred + ")");
            return null;
        }
        // A series whose episode number never arrived would be searched as "any episode of this
        // season", and every source answers that with some other episode's file. Subtitles for the
        // wrong episode are worse than none: they read as a broken player, not as a missing file.
        if (!identifier.isMovie() && identifier.episode < 1) {
            Utils.log("subtitles: no episode number, not searching");
            return null;
        }
        // Every source takes one id and not the other, so whichever is missing is resolved once, up
        // front, from the one we have. Without this a launcher that sends only a tmdb id leaves two
        // sources unable to run at all — and they look broken rather than unasked.
        //
        // Only for a source that is actually switched on, though: the lookup is a round trip to TMDB
        // ahead of every search, and it buys nothing when the sources that need that id are all off.
        final MediaId id = needsOtherId(identifier, prefs) ? enrich(identifier) : identifier;
        rememberPair(id);
        if (cancelled()) {
            return null;
        }
        Utils.log("subtitles: " + id.imdb + " / " + id.tmdb
                + " s" + id.season + "e" + id.episode + " want=" + preferred
                + " media=" + durationMs + "ms");

        // All four are asked at once: they disagree about which files exist, and asking them in turn
        // means waiting out each one's timeout before the next gets a chance. Answers are still taken
        // in source order, not in whichever order they arrive — but only the sources ahead of a hit
        // are waited for, so the keyed one leading costs the others nothing when it delivers.
        // Whether a machine translation may be taken at all is one answer for every source that says
        // so — the setting is about the class of file, not about who is offering it.
        final boolean allowMachine = prefs.subtitleTranslate;
        final List<Callable<Result>> sources = new ArrayList<>(4);
        if (prefs.subtitleSourceOpenSubtitles) {
            sources.add(() -> fromOpenSubtitles(id, preferred, allowMachine));
        }
        if (prefs.subtitleSourceRest) {
            sources.add(() -> best(SOURCE_REST,
                    restOpenSubtitles(id, preferred, answered, allowMachine), preferred, durationMs));
        }
        if (prefs.subtitleSourceStremio) {
            sources.add(() -> best(SOURCE_STREMIO, stremio(id, answered), preferred, durationMs));
        }
        if (prefs.subtitleSourceShegu) {
            sources.add(() -> best(SOURCE_SHEGU, shegu(id, answered), preferred, durationMs));
        }
        return firstDelivered(sources, sink, preferred);
    }

    /** How long all the sources together get before the search moves on without them. */
    private static final int PARALLEL_TIMEOUT_SEC = 15;

    /**
     * Runs the searches at once and hands over the best file that arrives: the wanted language first,
     * and only then the order the sources were given in.
     *
     * <p>Language has to outrank source, because the two preferences are not worth the same. Taking a
     * lesser language from an earlier source is not a smaller win — it ends the search with a file that
     * then has to be machine-translated, five seconds of it, into a worse rendering of the language a
     * source further down was offering outright. So the top language ends the search the moment it
     * arrives, and anything below it is held until the sources that might still beat it have answered.
     *
     *
     * <p>The results are collected one source at a time on purpose. Waiting for all of them before
     * looking at any of them made every search as slow as its slowest source: rest.opensubtitles.org
     * answers a search in about 0.2 s and shegu.st, which is one small origin behind Cloudflare with
     * nothing cached ({@code cf-cache-status: DYNAMIC}) and no language filter, takes 0.3 s warm and
     * several times that cold — and the whole search sat waiting for it even when the answer had been
     * in hand from the start. Now a slow source only delays the sources <em>after</em> it, and once
     * one of them delivers the rest are abandoned mid-flight.
     *
     * <p>The budget is shared: {@link #PARALLEL_TIMEOUT_SEC} covers all of them together, so one host
     * that hangs cannot spend everybody's patience one timeout at a time.
     */
    private static Result firstDelivered(List<Callable<Result>> tasks, Sink sink,
                                         List<String> preferred) {
        if (tasks.isEmpty()) {
            return null;
        }
        final ExecutorService pool = Executors.newFixedThreadPool(tasks.size(), runnable -> {
            final Thread thread = new Thread(runnable, "SubtitleSource");
            thread.setDaemon(true);
            return thread;
        });
        try {
            final List<Future<Result>> futures = new ArrayList<>(tasks.size());
            for (Callable<Result> task : tasks) {
                futures.add(pool.submit(task));
            }
            final long deadline = System.currentTimeMillis() + PARALLEL_TIMEOUT_SEC * 1000L;
            // Hits in a language below the top one, kept unfetched: whether any of them is worth
            // downloading is not known until every source has spoken.
            final List<Result> lesser = new ArrayList<>(tasks.size());
            for (Future<Result> future : futures) {
                if (cancelled()) {
                    return null;
                }
                try {
                    final long left = Math.max(deadline - System.currentTimeMillis(), 0);
                    final Result result = future.get(left, TimeUnit.MILLISECONDS);
                    if (result == null) {
                        continue;
                    }
                    if (preferred.get(0).equals(result.language)) {
                        if (delivered(result, sink)) {
                            return result;
                        }
                    } else {
                        lesser.add(result);
                    }
                } catch (InterruptedException e) {
                    // The player is gone or the item changed; leave the flag set so the caller stops too.
                    Thread.currentThread().interrupt();
                    return null;
                } catch (Exception e) {
                    Utils.log("subtitles: source failed " + e);
                }
            }
            // A stable sort, so within one language the sources keep the order they were asked in.
            Collections.sort(lesser, (a, b) -> Integer.compare(
                    preferred.indexOf(a.language), preferred.indexOf(b.language)));
            for (Result result : lesser) {
                if (cancelled()) {
                    return null;
                }
                if (delivered(result, sink)) {
                    return result;
                }
            }
        } finally {
            // Whatever is still in flight is of no use now: either something was delivered or the
            // budget is gone.
            pool.shutdownNow();
        }
        return null;
    }

    /**
     * How far past the end of the media a file's last line may sit and still be for this cut.
     *
     * <p>Ten flat seconds once, which turned out to reject perfectly good files: a report from a viewer
     * who "never finds subtitles" showed a 100-minute film whose only Ukrainian file — the same one from
     * all four sources — ends 39 s after the video does, so every source was refused and the search came
     * back empty. Nothing about that file is for another cut. A last line sits past the final frame
     * whenever the translator credited themselves after the picture, whenever the release the file was
     * timed to carried a distributor logo this copy does not, and whenever the copy is trimmed at the
     * tail.
     *
     * <p>A share of the length rather than a number of seconds, because that is what the difference
     * actually scales with: 39 s over a 100-minute film is 0.65 % and plainly the same cut, while the
     * same 39 s over a 22-minute episode is 3 % and worth a second look. Four per cent is four minutes
     * for a feature and under a minute for an episode, and it stays well below what a real second cut
     * costs — an extended feature runs ten to thirty minutes longer, so it is still turned away. The
     * floor is for the shortest clips, where a percentage is no margin at all.
     *
     * <p>The two mistakes are not worth the same: a file wrongly refused leaves the viewer with no
     * subtitles at all, while one wrongly taken is visibly out of sync and one tap from being replaced.
     * Which is also why nothing here is final — see {@link #ofOneCut} and SubtitleFetcher.fitsMedia.
     */
    static long cutMargin(long durationMs) {
        return Math.max(30_000L, durationMs / 25);
    }

    /**
     * An overhang too small to mean anything: the few seconds a re-encode loses off the tail. Kept
     * separate from {@link #cutMargin} because the two are asked different questions — this one is
     * "is there anything to look at", the other "how far out may a tail artefact sit".
     */
    static final long TAIL_GRACE_MS = 10_000;

    /**
     * How far apart two files' last lines may be and still be the same cut. Generous, because within
     * one cut they differ by whatever each translator did with the credits: the four Russian files for
     * Once Upon a Time in America end at 03:34:56, 03:36:56, 03:36:59 and 03:57:34, and only the last
     * of those is a different film — the extended cut, twenty-odd minutes longer.
     */
    private static final long CUT_TOLERANCE_MS = 6 * 60 * 1000L;

    /**
     * The files that are plausibly for the cut being played, out of one language's worth of them.
     *
     * <p>A film that exists in more than one cut has subtitles for each, and an index is keyed by the
     * title: nothing in it says which. The last line's timestamp does, and rest.opensubtitles.org
     * hands it over in the search response — so the wrong cut can be left alone here, before it costs
     * a download, rather than being caught by {@link SubtitleFetcher} after one.
     *
     * <p>Two steps, and the order matters. Anything ending after the media does is for a longer cut,
     * full stop. Of what is left, the one ending latest is the one that runs closest to this media's
     * own end, and everything within {@link #CUT_TOLERANCE_MS} of it is the same cut — kept, so the
     * pick among them is still made on popularity, which is what tells a good upload from a poor one.
     * A file the index said nothing about is never dropped: unknown is not the same as wrong.
     */
    private static List<Candidate> ofOneCut(List<Candidate> inLanguage, long durationMs) {
        if (durationMs <= 0) {
            return inLanguage;
        }
        final long margin = cutMargin(durationMs);
        // The anchor is drawn only from files that plainly fit, TAIL_GRACE_MS and no more.
        // Drawn from everything the margin allows instead, a file for a longer cut sets the anchor and
        // then evicts the correct ones through CUT_TOLERANCE_MS below: for The Martian's theatrical cut
        // the extended file's 2:25:35 would anchor the whole language and drop all four files that end
        // around 2:15, which is every honest one there is.
        long latest = 0;
        for (Candidate candidate : inLanguage) {
            if (candidate.lastMs > 0 && candidate.lastMs <= durationMs + TAIL_GRACE_MS) {
                latest = Math.max(latest, candidate.lastMs);
            }
        }
        final List<Candidate> kept = new ArrayList<>(inLanguage.size());
        for (Candidate candidate : inLanguage) {
            if (candidate.lastMs <= 0
                    || (candidate.lastMs <= durationMs + margin
                        && candidate.lastMs >= latest - CUT_TOLERANCE_MS)) {
                kept.add(candidate);
            }
        }
        // Nothing left means every file in this language looks like another cut, and the choice is no
        // longer between a good file and a bad one — it is between a bad file and none. Hand them all
        // back: out of sync is one tap from being replaced, and the download still gets the closer look
        // that SubtitleFetcher.fitsMedia gives it.
        return kept.isEmpty() ? inLanguage : kept;
    }

    /** {@code "03:57:34"} as ms, or 0 for anything else — the field is often absent or empty. */
    private static long lastTimestampMs(String value) {
        if (value == null) {
            return 0;
        }
        final Matcher matcher = LAST_TS.matcher(value);
        if (!matcher.matches()) {
            return 0;
        }
        return (Long.parseLong(matcher.group(1)) * 3600 + Long.parseLong(matcher.group(2)) * 60
                + Long.parseLong(matcher.group(3))) * 1000;
    }

    private static final Pattern LAST_TS = Pattern.compile("(\\d{1,2}):([0-5]\\d):([0-5]\\d)");

    /**
     * The imdb/tmdb pairs learned while searching, so a cached copy can be looked for under the name
     * the other id would have given it.
     *
     * <p>The two ways in do not carry the same id: a launcher may send only one, and a hand-picked
     * title deliberately drops the imdb one. The cached file is named after whichever was to hand, so
     * without this the same episode was downloaded twice — once per name. Resolving the missing id
     * before probing the cache would mean a round trip to TMDB on every open, which is exactly what
     * needsOtherId exists to avoid; remembering what a search already found out costs nothing.
     *
     * <p>Process-lifetime, like the misses in PlayerActivity: the automatic search runs before anyone
     * reaches the manual one, so by the time the pairing is needed it has been learned.
     */
    private static final Map<String, String> PAIRS = new java.util.concurrent.ConcurrentHashMap<>();

    private static void rememberPair(MediaId id) {
        if (id.imdb != null && id.tmdb != null) {
            PAIRS.put(id.imdb, id.tmdb);
            PAIRS.put(id.tmdb, id.imdb);
        }
    }

    /** What the given id was last seen paired with, or null. */
    static String pairedWith(String id) {
        return id == null ? null : PAIRS.get(id);
    }

    private static boolean cancelled() {
        return Thread.currentThread().isInterrupted();
    }

    private static boolean delivered(Result result, Sink sink) {
        if (result == null || cancelled()) {
            return false;
        }
        Result taken = result;
        if (result.fileId != 0) {
            // The metered call, and the only place it is made: this result is the one about to be used.
            final String link = OpenSubtitles.link(result.fileId);
            if (link == null) {
                Utils.log("subtitles: " + result.source + " picked " + result.language
                        + " but the download link was refused");
                return false;
            }
            taken = new Result(result.source, result.language,
                    Collections.singletonList(link), result.machine);
        }
        if (sink.accept(taken)) {
            return true;
        }
        Utils.log("subtitles: " + result.source + " listed but would not download");
        return false;
    }

    /**
     * Whether any enabled source is keyed by the id this one does not carry.
     *
     * <p>api.opensubtitles.com is counted among the imdb ones although it answers to either: asked by
     * imdb it is being asked the question every other index here is asked, and that is worth one lookup
     * rather than a second set of results to explain. It is the only source in the list with a choice.
     */
    private static boolean needsOtherId(MediaId id, Prefs prefs) {
        if (id.imdb == null && (prefs.subtitleSourceStremio || prefs.subtitleSourceRest
                || prefs.subtitleSourceOpenSubtitles)) {
            return true;
        }
        return id.tmdb == null && prefs.subtitleSourceShegu;
    }

    /**
     * The same id, with whichever of imdb/tmdb was missing looked up from the other. Reuses the
     * resolvers the skip-segment finder already has, and the TMDB key that comes with them.
     */
    private static MediaId enrich(MediaId id) {
        try {
            if (id.imdb == null && id.tmdb != null) {
                final String imdb = SegmentFinder.tmdbExternalImdb(Long.parseLong(id.tmdb), id.isMovie());
                if (imdb != null) {
                    return new MediaId(imdb, id.tmdb, id.season, id.episode);
                }
            } else if (id.tmdb == null && id.imdb != null) {
                final String imdb = id.imdb.startsWith("tt") ? id.imdb : "tt" + id.imdb;
                final long tmdb = SegmentFinder.tmdbFind(imdb, id.isMovie());
                if (tmdb >= 0) {
                    return new MediaId(id.imdb, String.valueOf(tmdb), id.season, id.episode);
                }
            }
        } catch (Exception e) {
            Utils.log("subtitles: id lookup " + e);
        }
        return id;
    }

    /**
     * The metered source, kept apart because minting its link costs quota: only the winner is
     * resolved, so a search that finds twelve files still spends exactly one download. The link is
     * minted last, once nothing has cancelled the search, because a unit is spent even when the
     * answer arrives to a player that no longer exists.
     */
    private static Result fromOpenSubtitles(MediaId id, List<String> preferred, boolean allowMachine) {
        // Every exit here used to be silent, and this is the one source that does not go through best():
        // so when it won, the trace jumped from its quota line straight to a file being painted, and when
        // it lost there was nothing at all. It is also the source most likely to answer, being the keyed
        // one, which made its silence the largest hole in a report about subtitles.
        final List<String> codes = OpenSubtitles.toIso639_1(preferred);
        if (codes.isEmpty()) {
            Utils.log("subtitles: " + SOURCE_OPENSUBTITLES + " has no 639-1 code for " + preferred);
            return null;
        }
        final List<OpenSubtitles.Candidate> found = OpenSubtitles.search(id, codes);
        final OpenSubtitles.Candidate best = OpenSubtitles.pick(found, codes, allowMachine);
        if (best == null || cancelled()) {
            Utils.log("subtitles: " + SOURCE_OPENSUBTITLES + " has " + found.size()
                    + " file(s), none taken" + (allowMachine ? "" : " (machine translations refused)"));
            return null;
        }
        Utils.log("subtitles: " + SOURCE_OPENSUBTITLES + " has 1 " + best.language
                + (best.machine ? " machine-translated" : "") + " of " + found.size());
        // The id, not a link: see Result.fileId. Every other source is asked and answered for free, and
        // this one used to spend a download here, inside the parallel sweep — so a search that stremio
        // or shegu.st went on to win still cost one of the hundred, as did a hit in a language that was
        // held back and never taken.
        return new Result(SOURCE_OPENSUBTITLES, Utils.toIso3Language(best.language),
                Collections.<String>emptyList(), best.machine, best.fileId);
    }

    /**
     * shegu.st: tmdb only, and it ignores every language parameter it is offered
     * ({@code language}, {@code lang}, {@code languages} all leave the result identical), so the whole
     * index — a hundred-odd entries — comes back and is sifted here.
     */
    private static List<Candidate> shegu(MediaId id, AtomicBoolean answered) {
        if (id.tmdb == null) {
            Utils.log("subtitles: " + SOURCE_SHEGU + " needs a tmdb id, and there is none");
            return Collections.emptyList();
        }
        final StringBuilder url = new StringBuilder("https://subtitles.shegu.st/subtitles?tmdb=")
                .append(Uri.encode(id.tmdb)).append("&type=").append(id.isMovie() ? "movie" : "tv");
        if (!id.isMovie()) {
            url.append("&season=").append(id.season);
            if (id.episode > 0) {
                url.append("&episode=").append(id.episode);
            }
        }
        final List<Candidate> candidates = new ArrayList<>();
        try {
            final JSONArray subtitles = json(url.toString(), answered).optJSONArray("subtitles");
            if (subtitles == null) {
                return candidates;
            }
            for (int i = 0; i < subtitles.length(); i++) {
                final JSONObject entry = subtitles.getJSONObject(i);
                final String language = Utils.toIso3Language(entry.optString("language"));
                final String link = entry.optString("url", null);
                // Its display name is the real file name for the entries it holds itself. The ones it
                // re-exports from the Stremio addon carry a placeholder instead — "Serbian (9797923)" —
                // but those are the very files that source already offered, where the name arrives with
                // the download.
                if (language != null && link != null && !forcedName(entry.optString("display"))) {
                    final String direct = origin(link);
                    candidates.add(new Candidate(language, 0, direct != null ? direct : link, false, 0));
                }
            }
        } catch (Exception e) {
            Utils.log("shegu.st: " + e);
        }
        return candidates;
    }

    /**
     * The file's own address, out of the base64url tail of a shegu.st link.
     *
     * <p>Worth the decode: their proxy fetches the origin itself when it has not got the file, so the
     * same 144 KB measured <b>2.2–4.4 s</b> through {@code /sub/<blob>} against <b>0.32 s</b> straight
     * from the origin on 2026-08-21. It is also the difference between having the uploader's file name
     * and not: the proxy strips {@code Content-Disposition}, and for the entries shegu re-exports from
     * the Stremio addon that header is the only place the name exists at all.
     *
     * <p>Every one of 612 entries across four titles decoded to an https URL, on two hosts
     * ({@code images.chuaxin.com} and {@code subs5.strem.io}). Anything else keeps the proxy link:
     * a scheme we did not expect is not something to go fetching on the strength of a hobby index.
     * A dead origin is not worth a second attempt through the proxy either — the next candidate of
     * the same language is right behind it.
     *
     * @return the origin URL, or null to leave the proxy link alone
     */
    private static String origin(String proxied) {
        final int slash = proxied.lastIndexOf('/');
        if (slash < 0 || slash + 1 >= proxied.length()) {
            return null;
        }
        try {
            final String decoded = new String(
                    Base64.decode(proxied.substring(slash + 1), Base64.URL_SAFE), "UTF-8");
            return decoded.startsWith("https://") ? decoded : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Stremio's OpenSubtitles addon: imdb only, no filtering, three-letter bibliographic codes. */
    private static List<Candidate> stremio(MediaId id, AtomicBoolean answered) {
        if (id.imdb == null) {
            // Said out loud: an empty list from here used to read in the trace as "asked and offered
            // nothing", which is a different fact from "was never in a position to ask".
            Utils.log("subtitles: " + SOURCE_STREMIO + " needs an imdb id, and there is none");
            return Collections.emptyList();
        }
        final String imdb = id.imdb.startsWith("tt") ? id.imdb : "tt" + id.imdb;
        final String path = id.isMovie()
                ? "movie/" + imdb
                : "series/" + imdb + ":" + id.season + ":" + Math.max(id.episode, 1);
        final List<Candidate> candidates = new ArrayList<>();
        try {
            final JSONArray subtitles =
                    json("https://opensubtitles-v3.strem.io/subtitles/" + path + ".json", answered)
                            .optJSONArray("subtitles");
            if (subtitles == null) {
                return candidates;
            }
            for (int i = 0; i < subtitles.length(); i++) {
                final JSONObject entry = subtitles.getJSONObject(i);
                final String language = Utils.toIso3Language(entry.optString("lang"));
                final String link = entry.optString("url", null);
                if (language != null && link != null) {
                    candidates.add(new Candidate(language, 0, link, false, 0));
                }
            }
        } catch (Exception e) {
            Utils.log("stremio: " + e);
        }
        return candidates;
    }

    /**
     * The retired legacy API. Two things to respect: the path segments must be in alphabetical order
     * (out of order it answers 302 to the host {@code _}, which resolves nowhere), and it takes exactly
     * one language per request — {@code sublanguageid-ukr,rus} is a 400 — so the priority list is
     * walked one entry at a time and the first hit wins.
     */
    private static List<Candidate> restOpenSubtitles(MediaId id, List<String> preferred,
                                                     AtomicBoolean answered, boolean allowMachine) {
        if (id.imdb == null) {
            Utils.log("subtitles: " + SOURCE_REST + " needs an imdb id, and there is none");
            return Collections.emptyList();
        }
        final String imdb = id.imdbNumeric();
        for (String language : preferred) {
            if (cancelled()) {
                break;
            }
            final StringBuilder url = new StringBuilder("https://rest.opensubtitles.org/search/");
            if (!id.isMovie() && id.episode > 0) {
                url.append("episode-").append(id.episode).append('/');
            }
            url.append("imdbid-").append(imdb).append('/');
            if (!id.isMovie()) {
                url.append("season-").append(id.season).append('/');
            }
            url.append("sublanguageid-").append(bibliographic(language));

            final List<Candidate> candidates = new ArrayList<>();
            try {
                final String body = get(url.toString(), answered);
                if (body == null) {
                    continue;
                }
                final JSONArray entries = new JSONArray(body);
                for (int i = 0; i < entries.length(); i++) {
                    final JSONObject entry = entries.getJSONObject(i);
                    final String link = entry.optString("SubDownloadLink", null);
                    final String found = Utils.toIso3Language(entry.optString("SubLanguageID"));
                    if (link == null || found == null) {
                        continue;
                    }
                    // This index says as much about a file as the metered API does, and both of these
                    // matter more than popularity: the Russian "forced" track for Avatar had twenty
                    // times the downloads of the real one, so sorting on downloads alone handed over a
                    // 4 KB file of Na'vi subtitles and called the search a success. The flag alone
                    // does not settle it either: it is set per upload rather than per file, and that
                    // very track — avatar-dvdscr-rus-navi.srt, 5858 bytes, 25 701 downloads — carries a
                    // SubForeignPartsOnly of 0. Hence the name is read as well.
                    if ("1".equals(entry.optString("SubForeignPartsOnly"))
                            || forcedName(entry.optString("SubFileName"))) {
                        continue;
                    }
                    final boolean machine = "1".equals(entry.optString("SubAutoTranslation"));
                    if (machine && !allowMachine) {
                        continue;
                    }
                    candidates.add(new Candidate(found,
                            parseInt(entry.optString("SubDownloadsCnt")), link, machine,
                            lastTimestampMs(entry.optString("SubLastTS"))));
                }
            } catch (Exception e) {
                Utils.log("rest.opensubtitles.org: " + e);
            }
            if (!candidates.isEmpty()) {
                return candidates;
            }
        }
        return Collections.emptyList();
    }

    /**
     * ISO 639-2/T to 639-2/B, for the one source that speaks the bibliographic form. Twenty codes
     * differ between the two lists and the rest are identical, so only those twenty are here. Getting
     * this wrong is invisible in testing with Ukrainian, Russian or English — they are spelled the
     * same either way — and silently finds nothing for German, French, Czech and seventeen others.
     */
    private static final Map<String, String> BIBLIOGRAPHIC = new HashMap<>();

    static {
        final String[][] codes = {
                {"sqi", "alb"}, {"hye", "arm"}, {"eus", "baq"}, {"mya", "bur"}, {"zho", "chi"},
                {"ces", "cze"}, {"nld", "dut"}, {"fas", "per"}, {"fra", "fre"}, {"kat", "geo"},
                {"deu", "ger"}, {"ell", "gre"}, {"isl", "ice"}, {"mkd", "mac"}, {"mri", "mao"},
                {"msa", "may"}, {"ron", "rum"}, {"slk", "slo"}, {"bod", "tib"}, {"cym", "wel"},
        };
        for (String[] pair : codes) {
            BIBLIOGRAPHIC.put(pair[0], pair[1]);
        }
    }

    private static String bibliographic(String iso639_2t) {
        final String biblio = BIBLIOGRAPHIC.get(iso639_2t);
        return biblio != null ? biblio : iso639_2t;
    }

    /**
     * What a forced track is called. The two aggregators carry no field for one — the Stremio addon
     * answers six fields and not one of them says so, shegu.st has none either — so the file name is
     * all that is left, and it is where the uploaders do write it.
     *
     * <p>Measured against 4059 names out of the legacy index: it matches 86, of which 70 are
     * confirmed forced by {@code SubForeignPartsOnly} while 15 of the remaining 16 are forced tracks
     * the flag missed ({@code FORZADOS.srt}, {@code castellano forzado.srt},
     * {@code Avatar Extended_navi_rus.srt}) — so it corrects the flag rather than repeating it.
     * The boundaries are letters on purpose: {@code signs} and {@code alien} are film titles
     * ({@code Star Wars … ( Alien Only )}) and are deliberately absent, and {@code na.?vi} does not
     * fire inside {@code Navidad}. What it costs is the forced track that is worth keeping — a
     * multilingual film's, where forced <em>is</em> the wanted one — which is two names of the 4059,
     * and both titles offer half a dozen other candidates for the fall-through to reach.
     */
    private static final Pattern FORCED_NAME = Pattern.compile(
            "(^|[^a-z])(forced|forc[eé]s?|forzados?|erzwungene?"
                    + "|foreign.?parts?|parts?.?only|na.?vi)([^a-z]|$)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** @param name a subtitle file name, or null where the source did not hand one over */
    static boolean forcedName(String name) {
        return name != null && FORCED_NAME.matcher(name).find();
    }

    /**
     * The most wanted language present, within it a human translation over a machine one, and within
     * that the most downloaded files. Language order beats everything — a rarely downloaded track in the
     * language asked for is still the one asked for — and popularity comes last rather than second,
     * because the files that are worse than the alternative are exactly the ones that outnumber it.
     *
     * <p>The whole list handed on is of one kind, human or machine, so the label the viewer ends up
     * seeing still describes the file that arrives when the first link turns out to be dead.
     */
    private static Result best(String source, List<Candidate> candidates, List<String> preferred,
                               long durationMs) {
        String language = null;
        for (String wanted : preferred) {
            for (Candidate candidate : candidates) {
                if (wanted.equals(candidate.language)) {
                    language = wanted;
                    break;
                }
            }
            if (language != null) {
                break;
            }
        }
        if (language == null) {
            Utils.log("subtitles: " + source + " has " + candidates.size()
                    + " file(s), none wanted");
            return null;
        }
        final List<Candidate> inLanguage = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (language.equals(candidate.language)) {
                inLanguage.add(candidate);
            }
        }
        final int listed = inLanguage.size();
        inLanguage.retainAll(ofOneCut(inLanguage, durationMs));
        if (inLanguage.isEmpty()) {
            Utils.log("subtitles: " + source + " has " + listed + " " + language
                    + ", all for another cut");
            return null;
        }
        Collections.sort(inLanguage, (a, b) -> a.machine != b.machine
                ? Boolean.compare(a.machine, b.machine)
                : Integer.compare(b.downloads, a.downloads));
        final boolean machine = inLanguage.get(0).machine;
        final List<String> urls = new ArrayList<>();
        for (Candidate candidate : inLanguage) {
            if (urls.size() >= MAX_URLS || candidate.machine != machine) {
                break;
            }
            urls.add(candidate.url);
        }
        Utils.log("subtitles: " + source + " has " + urls.size() + " " + language
                + (machine ? " machine-translated" : "") + " of " + candidates.size());
        return new Result(source, language, urls, machine);
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private static JSONObject json(String url, AtomicBoolean answered) throws Exception {
        final String body = get(url, answered);
        return body == null ? new JSONObject() : new JSONObject(body);
    }

    private static String get(String url, AtomicBoolean answered) {
        final Request request = new Request.Builder().url(url)
                .header("User-Agent", UA)
                .header("Accept", "application/json")
                .build();
        try (Response response = CLIENT.newCall(request).execute()) {
            // Even a 404 proves the host is up and the device has a way out, which is all this records.
            answered.set(true);
            final ResponseBody body = response.body();
            if (!response.isSuccessful() || body == null) {
                Utils.log("subtitles: " + response.code() + " " + request.url().host());
                return null;
            }
            return body.string();
        } catch (IOException e) {
            Utils.log("subtitles: " + e);
            return null;
        }
    }
}
