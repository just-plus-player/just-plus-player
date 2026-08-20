package com.brouken.player;

import android.net.Uri;

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

import com.brouken.player.skip.SegmentFinder;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Where an online subtitle comes from. Four sources, tried in the order below until one of them has
 * the wanted language, each switchable on its own so a single source can be exercised in isolation.
 *
 * <ol>
 *   <li>rest.opensubtitles.org — the retired legacy API, still answering and still the quickest to
 *       reply. One language per request and a gzipped file at the end of it.</li>
 *   <li>opensubtitles-v3.strem.io — imdb only, unmetered, already normalised to UTF-8.</li>
 *   <li>{@link OpenSubtitles} — the fullest index, the only one that filters by language server-side
 *       and knows whether a file is machine-translated. Third because it is the only metered one:
 *       100 downloads a day, and the two above cost nothing.</li>
 *   <li>shegu.st — tmdb only, unmetered, no language filter (the whole index arrives and is sifted
 *       here). Last because it is the least dependable: its index changed size twice within five
 *       minutes of testing, and about half its entries point at the host source 2 already is.</li>
 * </ol>
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

    /** How many links of the winning language are handed on, so a dead one is not the end of it. */
    private static final int MAX_URLS = 3;

    /** What the search found: the language it is in, and the links to try in order. */
    static final class Result {
        final String source;
        /** ISO 639-2/T, the form the priority list is written in. */
        final String language;
        final List<String> urls;

        Result(String source, String language, List<String> urls) {
            this.source = source;
            this.language = language;
            this.urls = urls;
        }
    }

    /** One file a source offers. */
    private static final class Candidate {
        final String language;  // ISO 639-2/T
        final int downloads;
        final String url;

        Candidate(String language, int downloads, String url) {
            this.language = language;
            this.downloads = downloads;
            this.url = url;
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
    static Result find(MediaId identifier, List<String> preferred, Prefs prefs, Sink sink,
                       AtomicBoolean answered) {
        if (identifier == null || identifier.isEmpty() || preferred.isEmpty()) {
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
        final MediaId id = enrich(identifier);
        if (cancelled()) {
            return null;
        }
        Utils.log("subtitles: " + id.imdb + " / " + id.tmdb
                + " s" + id.season + "e" + id.episode + " want=" + preferred);

        // The three keyless indexes are asked at once: none of them costs anything, they disagree
        // about which files exist, and asking them in turn means waiting out each one's timeout
        // before the next gets a chance. Results are still taken in source order, not in whichever
        // order they happen to answer.
        final List<Callable<Result>> keyless = new ArrayList<>(3);
        if (prefs.subtitleSourceRest) {
            keyless.add(() -> best(SOURCE_REST,
                    restOpenSubtitles(id, preferred, answered), preferred));
        }
        if (prefs.subtitleSourceStremio) {
            keyless.add(() -> best(SOURCE_STREMIO, stremio(id, answered), preferred));
        }
        if (prefs.subtitleSourceShegu) {
            keyless.add(() -> best(SOURCE_SHEGU, shegu(id, answered), preferred));
        }
        for (Result result : inParallel(keyless)) {
            if (delivered(result, sink)) {
                return result;
            }
        }
        // Only now the metered one. It is the fullest index, but its download call is the single
        // thing here that spends one of a hundred a day — so it is asked only for what the free
        // sources could not produce.
        if (!cancelled() && prefs.subtitleSourceOpenSubtitles) {
            final Result result = fromOpenSubtitles(id, preferred);
            if (delivered(result, sink)) {
                return result;
            }
        }
        return null;
    }

    /** How long all the keyless sources together get before the search moves on without them. */
    private static final int PARALLEL_TIMEOUT_SEC = 15;

    /**
     * Runs the searches at once and returns what they found, in the order the tasks were given —
     * source order is a preference, so it must survive the race. A source that times out, throws or
     * finds nothing contributes nothing and holds nobody up.
     */
    private static List<Result> inParallel(List<Callable<Result>> tasks) {
        final List<Result> results = new ArrayList<>(tasks.size());
        if (tasks.isEmpty()) {
            return results;
        }
        final ExecutorService pool = Executors.newFixedThreadPool(tasks.size(), runnable -> {
            final Thread thread = new Thread(runnable, "SubtitleSource");
            thread.setDaemon(true);
            return thread;
        });
        try {
            for (Future<Result> future : pool.invokeAll(tasks, PARALLEL_TIMEOUT_SEC, TimeUnit.SECONDS)) {
                try {
                    final Result result = future.isCancelled() ? null : future.get();
                    if (result != null) {
                        results.add(result);
                    }
                } catch (Exception e) {
                    Utils.log("subtitles: source failed " + e);
                }
            }
        } catch (InterruptedException e) {
            // The player is gone or the item changed; leave the flag set so the caller stops too.
            Thread.currentThread().interrupt();
        } finally {
            pool.shutdownNow();
        }
        return results;
    }

    private static boolean cancelled() {
        return Thread.currentThread().isInterrupted();
    }

    private static boolean delivered(Result result, Sink sink) {
        if (result == null || cancelled()) {
            return false;
        }
        if (sink.accept(result)) {
            return true;
        }
        Utils.log("subtitles: " + result.source + " listed but would not download");
        return false;
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
    private static Result fromOpenSubtitles(MediaId id, List<String> preferred) {
        final List<String> codes = OpenSubtitles.toIso639_1(preferred);
        if (codes.isEmpty()) {
            return null;
        }
        final OpenSubtitles.Candidate best =
                OpenSubtitles.pick(OpenSubtitles.search(id, codes), codes);
        if (best == null || cancelled()) {
            return null;
        }
        final String link = OpenSubtitles.link(best.fileId);
        if (link == null) {
            return null;
        }
        return new Result(SOURCE_OPENSUBTITLES, Utils.toIso3Language(best.language),
                Collections.singletonList(link));
    }

    /**
     * shegu.st: tmdb only, and it ignores every language parameter it is offered
     * ({@code language}, {@code lang}, {@code languages} all leave the result identical), so the whole
     * index — a hundred-odd entries — comes back and is sifted here.
     */
    private static List<Candidate> shegu(MediaId id, AtomicBoolean answered) {
        if (id.tmdb == null) {
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
                if (language != null && link != null) {
                    candidates.add(new Candidate(language, 0, link));
                }
            }
        } catch (Exception e) {
            Utils.log("shegu.st: " + e);
        }
        return candidates;
    }

    /** Stremio's OpenSubtitles addon: imdb only, no filtering, three-letter bibliographic codes. */
    private static List<Candidate> stremio(MediaId id, AtomicBoolean answered) {
        if (id.imdb == null) {
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
                    candidates.add(new Candidate(language, 0, link));
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
                                                     AtomicBoolean answered) {
        if (id.imdb == null) {
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
                    if (link != null && found != null) {
                        candidates.add(new Candidate(found,
                                parseInt(entry.optString("SubDownloadsCnt")), link));
                    }
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
     * The most wanted language present, and within it the most downloaded files. Language order beats
     * popularity: a rarely downloaded track in the language asked for is still the one asked for.
     */
    private static Result best(String source, List<Candidate> candidates, List<String> preferred) {
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
        Collections.sort(inLanguage, (a, b) -> Integer.compare(b.downloads, a.downloads));
        final List<String> urls = new ArrayList<>();
        for (Candidate candidate : inLanguage) {
            if (urls.size() >= MAX_URLS) {
                break;
            }
            urls.add(candidate.url);
        }
        Utils.log("subtitles: " + source + " has " + urls.size() + " " + language
                + " of " + candidates.size());
        return new Result(source, language, urls);
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
