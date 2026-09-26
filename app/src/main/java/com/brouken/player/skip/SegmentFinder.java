package com.brouken.player.skip;

import com.brouken.player.Utils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceArray;

import okhttp3.Cache;
import okhttp3.CacheControl;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Fetches intro/recap/credits skip segments for one movie or one series episode, keyed only by stable
 * ids (imdb, tmdb, and season/episode for series) — no title search.
 *
 * <p>All sources in the applicable profile are probed <b>in parallel</b>, then the results are merged
 * by <b>cross-source voting</b> rather than a single winner-take-all pick:
 * <ul>
 *   <li>Segments are grouped by {@link SkipSegment.Category} (a source's intro can only agree with
 *       another source's intro), then clustered by start time within {@link #AGREE_TOLERANCE_SEC}.</li>
 *   <li>A cluster backed by at least {@link #MIN_VOTES} distinct sources is {@code confirmed} — this
 *       is what kills phantom segments a single bad source would otherwise inject.</li>
 *   <li>The kept segment's <b>timing comes from the most file-accurate agreeing source</b> (highest
 *       {@link SkipSegment#timeTrust}); timings from different coordinate systems are never averaged.</li>
 * </ul>
 * Coverage is prioritized for single-source categories: a category seen by only one source is still
 * offered (as {@code confirmed=false}) rather than dropped.
 *
 * <p>Results are published <b>as they firm up</b>: the vote is recomputed every time a source lands
 * and re-delivered whenever it changed, so the first usable answer arrives in one source's latency
 * instead of the whole profile's. The callback is therefore called more than once per lookup.
 *
 * <p>Sources that need the stream length (SkipDB, SkipMe.db send it in the request) are only asked
 * when it is known. A lookup with an unknown duration is the <b>early wave</b>: it queries the
 * duration-independent sources by id alone, which is what makes a lookup possible before playback is
 * ready and for an episode that has not started yet. Aniskip sits in between — it uses the length to
 * pick the submission matching this file's cut, but answers without one, so it runs in both waves.
 *
 * <p>Runs on a background thread; the callback fires on that same worker thread (the caller marshals
 * to the UI thread). Results are cached in memory keyed by {@code imdb|tmdb|season|episode|duration};
 * negative (empty) results expire after {@link #NEG_CACHE_TTL_MS} so a transient network error does
 * not silence a title for the whole process. On any error or timeout a source yields nothing —
 * playback is never affected.
 */
public final class SegmentFinder {

    /** Receives the resolved skip segments (possibly empty) on the worker thread. */
    public interface Callback {
        void onSegments(List<SkipSegment> segments);
    }

    private SegmentFinder() {}

    private static final int TIMEOUT_SEC = 5;
    private static final double OPEN_ENDED_SEC = 99999; // TheIntroDB null end → open-ended

    // ---- Voting / probe tuning (all thresholds centralized here) -----------------------------

    /** Two segments of the same category whose starts fall within this window are treated as agreeing. */
    private static final double AGREE_TOLERANCE_SEC = 30;
    /** Distinct sources needed to mark a segment {@code confirmed}. */
    private static final int MIN_VOTES = 2;
    /**
     * A take shorter than this fraction of its cluster's longest is a truncated submission, not a
     * differing opinion on the same segment — a five-second "intro" against a ninety-second one. It
     * must never supply the cluster's timing, however much its source is trusted on timing in general.
     * Between clusters whose timing is trusted alike it counts only where the full takes leave them
     * level (bestCluster, step two): Silo S1E5 had IntroDB and TheIntroDB at 866 s against TheIntroDB
     * and an 11 s SkipDB take at 187 s, and the level count handed the intro to the earlier, wrong one.
     * Elsewhere it counts as a vote as it always did.
     *
     * <p>Deliberately far below the disagreement sources show in practice: across 52 cross-source
     * pairs the widest honest split was 17 s against 46 s (0.37) — and there the shorter take was the
     * right one — while the truncated submissions this guards against sit near 0.06.
     */
    private static final double TRUNCATED_TAKE_RATIO = 0.25;
    /** Overall wall-clock ceiling for the parallel probe (sources run concurrently, not summed). */
    private static final int PROBE_DEADLINE_SEC = TIMEOUT_SEC + 3;
    /** How long an empty ("nothing found") result stays cached before it is re-probed. */
    private static final long NEG_CACHE_TTL_MS = 10 * 60 * 1000L;

    // Time-source priority per source (higher wins when agreeing segments disagree on timing).
    private static final int TT_SKIPDB = SkipSegment.TIME_TRUST_DURATION_AWARE;       // 200
    private static final int TT_SKIPME = SkipSegment.TIME_TRUST_DURATION_AWARE - 10;  // 190 (erratic shift)
    private static final int TT_ANIME = SkipSegment.TIME_TRUST_DURATION_AWARE + 50;   // 250 (Aniskip primary)
    private static final int TT_ANIMESKIP = TT_ANIME - 10;                            // 240 (one curator, no votes)
    private static final int TT_ABS = SkipSegment.TIME_TRUST_ABSOLUTE;                // 100
    // A platform's own marks on a file of its episode's exact length (Bilibili, iQIYI): its cut, measured by it.
    private static final int TT_PLATFORM = TT_ANIME + 20;                             // 270

    /**
     * Aniskip sends an ETag and no lifetime, so OkHttp would ask it again on every lookup. It is one
     * volunteer server, and slow when loaded (2-8 s a request for a while on 2026-09-25), so a reply —
     * a 404 included — is reused for a day. That makes the full wave's any-length fallback free after the
     * early wave asked the same, and a rewatched episode cost the server nothing. The price is a day's
     * delay before a new submission shows.
     */
    private static final long ANISKIP_STALE_MS = TimeUnit.DAYS.toMillis(1);
    private static final CacheControl ANISKIP_STALE = new CacheControl.Builder()
            .maxStale((int) TimeUnit.MILLISECONDS.toSeconds(ANISKIP_STALE_MS), TimeUnit.SECONDS)
            .build();

    /** An {@code episodeLength} this close to the file length means the timings are for our cut. */
    private static final double CUT_MATCH_SEC = 5;
    /**
     * Offset of the second Aniskip probe. Aniskip matches {@code episodeLength} within ±20 s and then
     * picks from that window itself — not the nearest entry — so a submission of exactly our length can
     * sit hidden behind a longer one. Probing below the file length keeps ours inside the window while
     * dropping the longer cuts out of it.
     */
    private static final double CUT_PROBE_SHIFT_SEC = 18;
    /**
     * Anime Skip entries further than this from the file length are another release. Its lengths spread
     * by half a minute within one series (1439.8 s against 1470 s for Frieren), hence wider than
     * {@link #CUT_MATCH_SEC}.
     */
    private static final double ANIMESKIP_CUT_SEC = 60;
    /**
     * How long a show's Anime Skip episode list is kept on disk. It is the same for every episode of the
     * series and costs 0.8 s (One Piece: 1.5 s, 260 KB) each time; an episode missing from it (a new one
     * of a running show) refetches it early anyway.
     */
    private static final long ANIMESKIP_LIST_TTL_MS = 30L * 24 * 60 * 60 * 1000;
    /** How far into a Kodik player page to look for the skip ranges (found at about 17 KB). */
    private static final long KODIK_PAGE_BYTES = 64 * 1024;
    /** Ceiling for everything saved from Anime Skip together, the HTTP cache's budget; the oldest go first. */
    private static final long ANIMESKIP_DIR_BYTES = 1024 * 1024;

    /**
     * IntroHater is off: introhater.com answers "This service has been suspended by its owner" to every
     * request — an HTML body that parses to nothing, for a ~280 ms round-trip and one probe slot per
     * lookup. Left wired up rather than deleted, so re-enabling is this one flag if it ever comes back.
     */
    private static final boolean INTROHATER_ENABLED = false;

    /**
     * SkipMe.db is off: since 2026-09 db.skipme.workers.dev answers every request with 410
     * {"error":"Service termination"}, and its front page is a YouTube embed. Same one-flag switch as
     * IntroHater.
     */
    private static final boolean SKIPME_ENABLED = false;

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    /**
     * Disk budget for the HTTP cache. The bodies are small JSON documents, so this is roomy for the id
     * resolutions and every segment reply a long binge produces.
     */
    private static final long HTTP_CACHE_BYTES = 1024 * 1024;

    /**
     * Where Anime Skip episode lists and timestamps, Kodik's ranges and what Bilibili, iQIYI and Tencent said
     * are kept; null until {@link #setCacheDir}.
     */
    private static volatile java.io.File animeSkipDir;

    /** Replaced once by {@link #setCacheDir} with the same client plus a cache; volatile for that swap. */
    private static volatile OkHttpClient CLIENT = new OkHttpClient.Builder()
            // One idle connection per host a lookup talks to (fourteen now), so the full wave, seconds after
            // the early one, finds them open instead of paying a TLS handshake each; OkHttp keeps five.
            .connectionPool(new okhttp3.ConnectionPool(14, 5, TimeUnit.MINUTES))
            .connectTimeout(TIMEOUT_SEC, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SEC, TimeUnit.SECONDS)
            .callTimeout(TIMEOUT_SEC + 1, TimeUnit.SECONDS)
            .build();

    /**
     * Gives the finder an HTTP cache, once, at startup. The id resolvers ask to be cached for hours —
     * arm sends {@code max-age=21600}, TMDB {@code max-age=10527} — and arm sits on Aniskip's critical
     * path, resolving before either probe can be issued, twice per episode and identically for every
     * episode of a series. Without a cache configured OkHttp discards those headers and re-fetches every
     * time. The segment endpoints send only an ETag, so they revalidate instead: same latency, empty body.
     *
     * <p>Safe to skip — an un-cached client just behaves as before.
     */
    public static void setCacheDir(java.io.File cacheDir) {
        if (cacheDir == null) {
            return;
        }
        CLIENT = CLIENT.newBuilder() // shares the existing connection pool and dispatcher
                .cache(new Cache(new java.io.File(cacheDir, "segments"), HTTP_CACHE_BYTES))
                .build();
        // Anime Skip answers POST only and sends no cache headers, so the HTTP cache never holds it.
        animeSkipDir = new java.io.File(cacheDir, "animeskip");
    }

    // Process-lifetime cache; empty results are cached with an expiry (see NEG_CACHE_TTL_MS).
    private static final Map<String, CacheEntry> CACHE = new ConcurrentHashMap<>();

    private static final class CacheEntry {
        final List<SkipSegment> segments;
        final long expiresAt; // 0 = never expires (non-empty result)

        CacheEntry(List<SkipSegment> segments, long expiresAt) {
            this.segments = segments;
            this.expiresAt = expiresAt;
        }
    }

    /**
     * Starts an async lookup. Returns the worker {@link Thread} so the caller can {@code interrupt()}
     * it when the media item changes. The callback is not invoked if the thread was interrupted.
     */
    public static Thread find(String imdbId, String tmdbId, int season, int episode, double durationSec,
                              Callback callback) {
        // Nothing a source answers, and no mistake of ours in reading it, may take the player down: skip
        // points are a convenience, and a bug in the vote crashed 2.0.3 from this very thread. What fails
        // is logged (the trace carries it into a report) and the lookup simply delivers nothing.
        final Thread thread = new Thread(() -> {
            try {
                lookup(imdbId, tmdbId, season, episode, durationSec, new Emitter(callback));
            } catch (Throwable t) {
                Utils.log("segments: lookup failed " + t);
                io.sentry.Sentry.captureException(t);
            }
        }, "SegmentFinder");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    /**
     * Delivers results to the caller, dropping empties and repeats so the progressive publishing never
     * re-delivers a list the caller already has. Silent once the lookup thread has been interrupted.
     */
    private static final class Emitter {
        private final Callback callback;
        private List<SkipSegment> last;

        Emitter(Callback callback) {
            this.callback = callback;
        }

        void emit(List<SkipSegment> segments) {
            if (segments.isEmpty() || same(segments, last) || Thread.currentThread().isInterrupted()) {
                return;
            }
            last = segments;
            callback.onSegments(segments);
        }

        private static boolean same(List<SkipSegment> a, List<SkipSegment> b) {
            if (b == null || a.size() != b.size()) {
                return false;
            }
            for (int i = 0; i < a.size(); i++) {
                final SkipSegment x = a.get(i);
                final SkipSegment y = b.get(i);
                if (x.category != y.category || x.startSec != y.startSec || x.endSec != y.endSec) {
                    return false;
                }
            }
            return true;
        }
    }

    private static void lookup(String imdbIdIn, String tmdbIdIn, int season, int episode,
                               double durationSec, Emitter emitter) {
        final String imdbInput = isBlank(imdbIdIn) ? null : imdbIdIn;
        final String tmdbInput = isBlank(tmdbIdIn) ? null : tmdbIdIn;
        if (imdbInput == null && tmdbInput == null) {
            return;
        }
        // A season implies a series episode even if the episode number is missing/0 — don't blindly
        // treat it as a movie (that would query the wrong, movie-only sources).
        final boolean isMovie = season < 1;
        final int keySeason = isMovie ? -1 : season;
        final int keyEpisode = isMovie ? -1 : Math.max(episode, -1);
        // Up to three lookups trace at once (this item's two waves and the next item's prefetch), so every
        // line says which title it is about.
        final String tag = (imdbInput != null ? imdbInput : "tmdb " + tmdbInput)
                + (isMovie ? " film" : " S" + keySeason + "E" + keyEpisode);
        // Cache key includes the duration bucket: duration-aware sources adapt to the stream length,
        // so a replay of a differently-cut rip must not reuse stale timings.
        final long durationBucket = durationSec > 0 ? Math.round(durationSec) : -1;
        final String title = (imdbInput != null ? imdbInput : "") + "|"
                + (tmdbInput != null ? tmdbInput : "") + "|" + keySeason + "|" + keyEpisode + "|";
        final String key = title + durationBucket;
        final List<SkipSegment> cached = getCached(key);
        if (cached != null) {
            Utils.log("segments: " + tag + " cached " + describe(cached));
            emitter.emit(cached);
            return;
        }
        // The length-less answer for this episode — the previous item's prefetch, or this item's own early
        // wave — goes out at once while the length-aware one is worked out. An opening can start at 0:00,
        // and a provisional answer then is worth more than a precise one two seconds in; the vote below
        // replaces it as soon as it differs.
        if (durationBucket != -1) {
            final List<SkipSegment> early = getCached(title + -1);
            if (early != null && !early.isEmpty()) {
                Utils.log("segments: " + tag + " early answer " + describe(early));
                emitter.emit(early);
            }
        }

        // A tmdb id from the intent is numeric and needs no network to use.
        long tmdbNumeric = -1;
        if (tmdbInput != null) {
            try {
                tmdbNumeric = Long.parseLong(tmdbInput.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        // No imdb id but we have a tmdb id → reverse-resolve imdb (TMDB external_ids) so the broad
        // imdb-keyed sources (SkipDB, IntroDB.app, Aniskip, IntroHater) can still be used. If that
        // fails we fall back to the multi-id / tmdb-keyed sources (SkipMe.db, TheIntroDB).
        String imdbId = imdbInput;
        if (imdbId == null && tmdbNumeric >= 0) {
            imdbId = tmdbExternalImdb(tmdbNumeric, isMovie);
            Utils.log("segments: " + tag + " is imdb " + imdbId);
        }
        Utils.log("segments: " + tag + " search, imdb " + imdbId + " / tmdb " + tmdbInput
                + (durationSec > 0 ? ", " + Math.round(durationSec) + " s" : ", length unknown"));

        final String imdb = imdbId;      // effectively final for the step lambdas
        final long tmdb = tmdbNumeric;
        final int ep = Math.max(episode, -1);
        // SkipDB and SkipMe.db take the stream length as a request parameter and answer in its
        // coordinates. Asked without it they still answer — with the highest timeTrust of the profile —
        // so a length-less early wave would let them win the vote with timings meant for another cut.
        // They are therefore left out until the duration is known; the second wave picks them up.
        final boolean durationKnown = durationSec > 0;
        final List<Step> steps = new ArrayList<>();
        if (isMovie) {
            // Duration-aware first, absolute community sources last.
            if (imdb != null && durationKnown) {
                steps.add(traced(tag, "SkipDB", () -> skipDb(imdb, -1, -1, durationSec)));
            }
            if (imdb != null || tmdb >= 0) {
                if (durationKnown && SKIPME_ENABLED) {
                    steps.add(traced(tag, "SkipMe", () -> skipMe(imdb, tmdb, -1, -1, durationSec)));
                }
                steps.add(traced(tag, "TheIntroDB", () -> theIntroDb(imdb, tmdb, -1, -1, true)));
            }
            if (imdb != null) {
                steps.add(traced(tag, "IntroDB", () -> introDbApp(imdb, -1, -1)));
            }
            if (imdb != null && INTROHATER_ENABLED) {
                steps.add(traced(tag, "IntroHater", () -> introHater(imdb, -1, -1)));
            }
            if (imdb != null) {
                // An anime film is episode 1 of its own arm entry (media MOVIE, no season). For any other
                // film arm answers an empty list in well under 100 ms, and both steps drop out.
                steps.add(traced(tag, "Aniskip", () -> aniskip(imdb, -1, 1, durationSec)));
                // Anime Skip files a film as "1" beside its TV edit (Mugen Train: a 1576 s "Episode 1"), so
                // only the length picks the film itself out.
                if (durationKnown) {
                    steps.add(traced(tag, "AnimeSkip", () -> animeSkip(imdb, -1, 1, durationSec)));
                    steps.add(traced(tag, "Kodik", () -> kodik(imdb, -1, 1, durationSec)));
                }
            }
        } else {
            if (imdb != null) {
                // Aniskip is the anime specialist (crowd-voted OP/ED with real ends); for anime files
                // — usually the broadcast cut — its absolute timings match, so it is the primary time
                // source (TT_ANIME). It returns empty fast for non-anime, so it simply drops out then.
                steps.add(traced(tag, "Aniskip", () -> aniskip(imdb, season, ep, durationSec)));
                steps.add(traced(tag, "AnimeSkip", () -> animeSkip(imdb, season, ep, durationSec)));
                if (durationKnown) {
                    // Timed to Russian dubs and blind to the file length: only the length can vet it.
                    steps.add(traced(tag, "Kodik", () -> kodik(imdb, season, ep, durationSec)));
                }
                if (durationKnown) {
                    steps.add(traced(tag, "SkipDB", () -> skipDb(imdb, season, ep, durationSec)));
                    if (SKIPME_ENABLED) {
                        steps.add(traced(tag, "SkipMe", () -> skipMe(imdb, tmdb, season, ep, durationSec)));
                    }
                }
                steps.add(traced(tag, "IntroDB", () -> introDbApp(imdb, season, ep)));
            }
            if (imdb != null || tmdb >= 0) {
                steps.add(traced(tag, "TheIntroDB", () -> theIntroDb(imdb, tmdb, season, ep, false)));
            }
            if (tmdb >= 0) {
                // Chinese titles only (they ask TMDB first); their trust rests on the episode length they
                // state. In the early wave too, though no length can vouch for them yet: three requests to
                // China take 3-5 s from Europe and up to 20 on a slow link, and a fetch the deadline cuts off
                // still finishes and saves, so the full wave reads it from disk well inside its deadline.
                steps.add(traced(tag, "Bilibili", () -> bilibili(tmdb, season, ep, durationSec)));
                steps.add(traced(tag, "iQIYI", () -> iqiyi(tmdb, season, ep, durationSec)));
                steps.add(traced(tag, "Tencent", () -> tencent(tmdb, season, ep, durationSec)));
            }
            if (imdb != null && INTROHATER_ENABLED) {
                steps.add(traced(tag, "IntroHater", () -> introHater(imdb, season, ep)));
            }
        }

        if (steps.isEmpty()) {
            Utils.log("segments: " + tag + " nothing can be asked with these ids");
            return; // nothing in this profile can be asked with the ids that resolved
        }
        final List<Scored> results = probeAll(steps, emitter);
        if (Thread.currentThread().isInterrupted()) {
            return; // media changed mid-probe — don't deliver or cache
        }
        final List<SkipSegment> result = voteSegments(results);
        Utils.log("segments: " + tag + " picked " + describe(result));
        putCached(key, result);
        emitter.emit(result); // no-op when the progressive publishing already delivered this list
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    // ---- Cache -------------------------------------------------------------------------------

    private static List<SkipSegment> getCached(String key) {
        final CacheEntry entry = CACHE.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.expiresAt != 0 && System.currentTimeMillis() >= entry.expiresAt) {
            CACHE.remove(key); // stale negative — re-probe
            return null;
        }
        return entry.segments;
    }

    private static void putCached(String key, List<SkipSegment> result) {
        final long expiresAt = result.isEmpty() ? System.currentTimeMillis() + NEG_CACHE_TTL_MS : 0;
        CACHE.put(key, new CacheEntry(result, expiresAt));
    }

    // ---- Parallel probe ----------------------------------------------------------------------

    private interface Step {
        Scored run();
    }

    /**
     * Wraps a source so the trace says what it answered and how long it took. One interrupted — by the
     * probe deadline or by the next item — answered nothing on its own account, and says only that.
     */
    private static Step traced(String tag, String name, Step step) {
        return () -> {
            final long start = System.currentTimeMillis();
            final Scored result = step.run();
            if (Thread.currentThread().isInterrupted()) {
                Utils.log("segments: " + tag + " " + name + " cut off after "
                        + (System.currentTimeMillis() - start) + " ms");
                return result;
            }
            Utils.log("segments: " + tag + " " + name + " " + (System.currentTimeMillis() - start) + " ms, "
                    + (result == null || result.isEmpty() ? "nothing"
                        : describe(result.segments) + String.format(Locale.US, ", signal %.2f", result.signal)));
            return result;
        };
    }

    /** "INTRO 93-183 CREDITS 1340-1430", "nothing" when empty; a trailing * marks a confirmed pick. */
    private static String describe(List<SkipSegment> segments) {
        if (segments.isEmpty()) {
            return "nothing";
        }
        final StringBuilder sb = new StringBuilder();
        for (SkipSegment seg : segments) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(seg.category).append(' ').append(Math.round(seg.startSec)).append('-')
                    .append(seg.endSec >= OPEN_ENDED_SEC ? "end" : String.valueOf(Math.round(seg.endSec)))
                    .append(seg.confirmed ? "*" : "");
        }
        return sb.toString();
    }

    /** A source result carrying its quality {@link #signal} (0..1); the segment list may be empty. */
    private static final class Scored {
        final List<SkipSegment> segments;
        final double signal;

        Scored(List<SkipSegment> segments, double signal) {
            this.segments = segments;
            this.signal = signal;
        }

        boolean isEmpty() {
            return segments == null || segments.isEmpty();
        }
    }

    /**
     * Runs every step concurrently and collects their results, bounded by {@link #PROBE_DEADLINE_SEC}.
     * Slots for steps that time out remain null. Each source's own call timeouts keep this well under
     * the deadline in practice.
     *
     * <p>The vote is recomputed on every arrival and handed to {@code emitter} when it changed, so a
     * source that answers in 300 ms is acted on immediately instead of waiting out a peer that hangs
     * until the deadline. Returns the final slots for the authoritative vote.
     */
    private static List<Scored> probeAll(List<Step> steps, Emitter emitter) {
        final int n = steps.size();
        final AtomicReferenceArray<Scored> slots = new AtomicReferenceArray<>(n);
        final BlockingQueue<Integer> arrivals = new ArrayBlockingQueue<>(n);
        final Thread[] workers = new Thread[n];
        for (int i = 0; i < n; i++) {
            final int idx = i;
            final Step step = steps.get(i);
            final Thread worker = new Thread(() -> {
                try {
                    slots.set(idx, step.run());
                } catch (Throwable ignored) {
                    // A misbehaving source must never break the probe.
                } finally {
                    arrivals.offer(idx);
                }
            }, "SegmentSource-" + i);
            worker.setDaemon(true);
            workers[i] = worker;
            worker.start();
        }
        final long deadlineAt = System.currentTimeMillis() + PROBE_DEADLINE_SEC * 1000L;
        try {
            for (int arrived = 0; arrived < n; arrived++) {
                final long waitMs = deadlineAt - System.currentTimeMillis();
                if (waitMs <= 0 || arrivals.poll(waitMs, TimeUnit.MILLISECONDS) == null) {
                    break;
                }
                if (arrived < n - 1) {
                    emitter.emit(voteSegments(snapshot(slots, n))); // last arrival: caller votes anyway
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            for (Thread worker : workers) {
                worker.interrupt();
            }
        }
        return snapshot(slots, n);
    }

    private static List<Scored> snapshot(AtomicReferenceArray<Scored> slots, int n) {
        final List<Scored> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(slots.get(i));
        }
        return out;
    }

    // ---- Voting ------------------------------------------------------------------------------

    /** One source's take on a segment, kept with its source index (for vote counting) and signal. */
    private static final class Vote {
        final SkipSegment seg;
        final double signal;
        final int sourceId;

        Vote(SkipSegment seg, double signal, int sourceId) {
            this.seg = seg;
            this.signal = signal;
            this.sourceId = sourceId;
        }
    }

    /**
     * Merges per-source results into a final list: for each category, the best agreeing cluster wins,
     * its timing taken from the highest-{@code timeTrust} member. At most one segment per category is
     * emitted, which also removes duplicate/conflicting picks.
     */
    private static List<SkipSegment> voteSegments(List<Scored> results) {
        // Collect every source's segments into per-category vote buckets.
        final Map<SkipSegment.Category, List<Vote>> byCategory = new LinkedHashMap<>();
        for (int sourceId = 0; sourceId < results.size(); sourceId++) {
            final Scored r = results.get(sourceId);
            if (r == null || r.isEmpty()) {
                continue;
            }
            for (SkipSegment seg : r.segments) {
                List<Vote> bucket = byCategory.get(seg.category);
                if (bucket == null) {
                    bucket = new ArrayList<>();
                    byCategory.put(seg.category, bucket);
                }
                bucket.add(new Vote(seg, r.signal, sourceId));
            }
        }

        final List<SkipSegment> out = new ArrayList<>();
        for (Map.Entry<SkipSegment.Category, List<Vote>> entry : byCategory.entrySet()) {
            final SkipSegment best = bestCluster(entry.getValue());
            if (best != null) {
                out.add(best);
            }
        }
        // Chronological, not arrival-ordered: the progressive publishing compares consecutive results
        // to decide whether anything changed, so the same segments must always come back in one order.
        Collections.sort(out, (a, b) -> Double.compare(a.startSec, b.startSec));
        return out;
    }

    /**
     * Clusters same-category votes by start time and returns the winning cluster's representative
     * segment (a fresh copy with {@code confirmed} set), or null if there are no votes. In two steps, so
     * the answer does not depend on the order the clusters come in: the old winner first (most distinct
     * sources, then highest {@code timeTrust}, then highest signal, then earliest); then, among the
     * clusters whose timing is trusted as much as that winner's, the one with most full takes (see
     * {@link #TRUNCATED_TAKE_RATIO}), then most sources, then signal, then earliest.
     */
    private static SkipSegment bestCluster(List<Vote> votes) {
        if (votes.isEmpty()) {
            return null;
        }
        Collections.sort(votes, (a, b) -> Double.compare(a.seg.startSec, b.seg.startSec));

        final List<Vote> reps = new ArrayList<>();
        final List<int[]> counts = new ArrayList<>(); // {distinct sources, sources with a full take}

        int i = 0;
        while (i < votes.size()) {
            final double anchor = votes.get(i).seg.startSec;
            int j = i;
            // Grow the cluster while starts stay within the agreement window of the anchor.
            final List<Vote> cluster = new ArrayList<>();
            while (j < votes.size() && votes.get(j).seg.startSec - anchor <= AGREE_TOLERANCE_SEC) {
                cluster.add(votes.get(j));
                j++;
            }
            i = j;

            // Representative: most file-accurate timing (highest timeTrust), then signal, then earliest —
            // chosen among the takes that actually span the segment. The longest always qualifies, so
            // rep is never left null.
            // An open end is a "runs to the file end" marker, not a measured length — counting its
            // sentinel span would make every real take look truncated next to it. With none to measure,
            // longest stays 0 and the guard simply never fires.
            double longest = 0;
            for (Vote v : cluster) {
                if (v.seg.endSec < OPEN_ENDED_SEC) {
                    longest = Math.max(longest, v.seg.endSec - v.seg.startSec);
                }
            }
            Vote rep = null;
            final java.util.Set<Integer> sources = new java.util.HashSet<>();
            final java.util.Set<Integer> fullSources = new java.util.HashSet<>();
            for (Vote v : cluster) {
                sources.add(v.sourceId);
                if (v.seg.endSec - v.seg.startSec < longest * TRUNCATED_TAKE_RATIO) {
                    continue;
                }
                fullSources.add(v.sourceId);
                if (rep == null || v.seg.timeTrust > rep.seg.timeTrust
                        || (v.seg.timeTrust == rep.seg.timeTrust && v.signal > rep.signal)) {
                    rep = v;
                }
            }
            if (rep == null) {
                continue; // cannot happen since addSeg drops empty spans; a cluster without one is skipped
            }
            reps.add(rep);
            counts.add(new int[]{sources.size(), fullSources.size()});
        }
        if (reps.isEmpty()) {
            return null;
        }
        // Step one, the rule as it always was. A truncated take beside a file-accurate one counts here:
        // Aniskip timed for this file plus a short take must still beat two sources of another cut.
        int w = 0;
        for (int k = 1; k < reps.size(); k++) {
            final Vote c = reps.get(k);
            final Vote b = reps.get(w);
            if (counts.get(k)[0] > counts.get(w)[0]
                    || (counts.get(k)[0] == counts.get(w)[0] && c.seg.timeTrust > b.seg.timeTrust)
                    || (counts.get(k)[0] == counts.get(w)[0] && c.seg.timeTrust == b.seg.timeTrust
                        && c.signal > b.signal)) {
                w = k;
            }
        }
        // Step two, among clusters trusted like the winner: full takes first. Silo S1E5 had IntroDB and
        // TheIntroDB at 866 s against TheIntroDB and an 11 s SkipDB take at 187 s, all trusted alike.
        final int trust = reps.get(w).seg.timeTrust;
        int best = -1;
        for (int k = 0; k < reps.size(); k++) {
            if (reps.get(k).seg.timeTrust != trust) {
                continue;
            }
            if (best < 0 || counts.get(k)[1] > counts.get(best)[1]
                    || (counts.get(k)[1] == counts.get(best)[1] && counts.get(k)[0] > counts.get(best)[0])
                    || (counts.get(k)[1] == counts.get(best)[1] && counts.get(k)[0] == counts.get(best)[0]
                        && reps.get(k).signal > reps.get(best).signal)) {
                best = k;
            }
        }
        final SkipSegment src = reps.get(best).seg;
        final SkipSegment kept = new SkipSegment(src.startSec, src.endSec, src.type,
                src.category, src.coordBase, src.timeTrust);
        kept.confirmed = counts.get(best)[0] >= MIN_VOTES;
        return kept;
    }

    // ---- Sources -----------------------------------------------------------------------------

    /** SkipDB: {segments:{intro,recap,outro,preview}}, each null or {start_ms,end_ms,confidence}. */
    private static Scored skipDb(String imdbId, int season, int episode, double durationSec) {
        final HttpUrl.Builder url = HttpUrl.parse(SegmentEndpoints.SKIPDB).newBuilder()
                .addQueryParameter("imdb_id", imdbId);
        if (season >= 1 && episode >= 1) {
            url.addQueryParameter("season", String.valueOf(season));
            url.addQueryParameter("episode", String.valueOf(episode));
        }
        if (durationSec > 0) {
            url.addQueryParameter("duration", String.valueOf((long) durationSec));
        }
        final List<SkipSegment> out = new ArrayList<>();
        final JSONObject root = getJson(url.build());
        if (root == null) {
            return new Scored(out, 0);
        }
        final JSONObject segs = root.optJSONObject("segments");
        if (segs == null) {
            return new Scored(out, 0);
        }
        final JSONObject intro = segs.optJSONObject("intro");
        final JSONObject recap = segs.optJSONObject("recap");
        final JSONObject outro = segs.optJSONObject("outro");
        addMs(out, intro, SkipSegment.Category.INTRO, SkipSegment.CoordBase.DURATION_AWARE, TT_SKIPDB);
        addMs(out, recap, SkipSegment.Category.RECAP, SkipSegment.CoordBase.DURATION_AWARE, TT_SKIPDB);
        addMs(out, outro, SkipSegment.Category.CREDITS, SkipSegment.CoordBase.DURATION_AWARE, TT_SKIPDB);
        // Signal: average confidence (0..1) over the present segment objects.
        double sum = 0;
        int n = 0;
        for (JSONObject o : new JSONObject[]{intro, recap, outro}) {
            if (o != null) {
                sum += o.optDouble("confidence", 0.5);
                n++;
            }
        }
        return new Scored(out, n > 0 ? sum / n : 0.5);
    }

    /**
     * IntroDB.app: {intro,recap,outro}, each null or {start_sec,end_sec,confidence,submission_count}.
     * Films are asked with {@code is_movie=true}; an episode needs both numbers, and a season alone is
     * answered with 400, so that question is not sent.
     */
    private static Scored introDbApp(String imdbId, int season, int episode) {
        final List<SkipSegment> out = new ArrayList<>();
        final HttpUrl.Builder url = HttpUrl.parse(SegmentEndpoints.INTRODB).newBuilder()
                .addQueryParameter("imdb_id", imdbId);
        if (season < 1) {
            url.addQueryParameter("is_movie", "true");
        } else if (episode >= 1) {
            url.addQueryParameter("season", String.valueOf(season));
            url.addQueryParameter("episode", String.valueOf(episode));
        } else {
            return new Scored(out, 0);
        }
        final JSONObject root = getJson(url.build());
        if (root == null) {
            return new Scored(out, 0);
        }
        final JSONObject intro = root.optJSONObject("intro");
        final JSONObject recap = root.optJSONObject("recap");
        final JSONObject outro = root.optJSONObject("outro");
        addSecObject(out, intro, SkipSegment.Category.INTRO, TT_ABS);
        addSecObject(out, recap, SkipSegment.Category.RECAP, TT_ABS);
        addSecObject(out, outro, SkipSegment.Category.CREDITS, TT_ABS);
        // Signal: best of confidence × min(1, submission_count/3) — a single submission scores low.
        double best = 0;
        for (JSONObject o : new JSONObject[]{intro, recap, outro}) {
            if (o != null) {
                final double confidence = o.optDouble("confidence", 0.5);
                final double subs = o.optDouble("submission_count", 1);
                best = Math.max(best, confidence * Math.min(1.0, subs / 3.0));
            }
        }
        return new Scored(out, best);
    }

    /**
     * SkipMe.db: crowd-sourced, multi-id (imdb/tmdb/tvdb/anilist), duration-aware. {@code POST
     * /v1/movies} with a single-item JSON array; response element carries {intro,recap,credits,
     * preview} arrays of {start_ms,end_ms(nullable),submissions}.
     */
    private static Scored skipMe(String imdbId, long tmdbId, int season, int episode, double durationSec) {
        final List<SkipSegment> out = new ArrayList<>();
        final JSONObject req = new JSONObject();
        try {
            if (imdbId != null) {
                req.put("imdb_id", imdbId);
            }
            if (tmdbId >= 0) {
                req.put("tmdb_id", tmdbId);
            }
            if (season >= 1 && episode >= 1) {
                req.put("season", season);
                req.put("episode", episode);
            }
            if (durationSec > 0) {
                req.put("duration_ms", (long) (durationSec * 1000));
            }
        } catch (JSONException e) {
            return new Scored(out, 0);
        }
        final JSONArray response = postJsonArray(SegmentEndpoints.SKIPME, new JSONArray().put(req),
                SegmentEndpoints.SKIPME_UA);
        if (response == null) {
            return new Scored(out, 0);
        }
        final JSONObject media = response.optJSONObject(0);
        if (media == null) {
            return new Scored(out, 0);
        }
        final JSONArray intro = media.optJSONArray("intro");
        final JSONArray recap = media.optJSONArray("recap");
        final JSONArray credits = media.optJSONArray("credits");
        addMsArray(out, intro, SkipSegment.Category.INTRO, SkipSegment.CoordBase.DURATION_AWARE, TT_SKIPME);
        addMsArray(out, recap, SkipSegment.Category.RECAP, SkipSegment.CoordBase.DURATION_AWARE, TT_SKIPME);
        addMsArray(out, credits, SkipSegment.Category.CREDITS, SkipSegment.CoordBase.DURATION_AWARE, TT_SKIPME);
        // Signal: min(1, maxSubmissions/5) — 5+ submissions is treated as fully trusted.
        final int maxSub = Math.max(maxSubmissions(intro),
                Math.max(maxSubmissions(recap), maxSubmissions(credits)));
        return new Scored(out, Math.min(1.0, maxSub / 5.0));
    }

    /**
     * Anime gate: arm (imdb→MAL for the season), then Aniskip (MAL-relative episode).
     *
     * <p>An episode is typically submitted several times, once per release cut, and the cuts do not
     * share timings — a rip with a longer cold open puts its opening two minutes later. Aniskip filters
     * submissions by {@code episodeLength} but chooses within that window itself, so asking at the file
     * length can still answer with a foreign cut; the timings are then plain wrong, not merely shifted.
     * This picks, per category, the submission recorded closest to this file. {@code durationSec <= 0}
     * (early wave) has nothing to match on and takes whatever the wildcard returns.
     */
    private static Scored aniskip(String imdbId, int season, int seriesEpisode, double durationSec) {
        final List<SkipSegment> out = new ArrayList<>();
        if (seriesEpisode < 1) {
            return new Scored(out, 0); // Aniskip is per-episode; nothing to ask without one
        }
        final long[] arm = armId(imdbId, season, seriesEpisode, "myanimelist");
        if (arm == null) {
            return new Scored(out, 0); // not anime (or no MAL for this season) → drop out
        }
        final long malId = arm[0];
        final int episode = (int) arm[1];
        // All probes at once. The lower one is what surfaces a cut shorter than this file, and issuing it
        // only after the first has answered would put a second round-trip on the critical path — with arm
        // already resolving ahead of them, that made Aniskip the slowest step of the profile by far. Asking
        // unconditionally also stops a segment submitted only for a neighbouring cut from being missed
        // whenever the first probe happens to answer for ours. The any-length fallback goes along too: when
        // nothing is recorded near this length it is needed, and waiting for the other two to say so was a
        // third round-trip (2-4 s each while Aniskip was loaded). It usually costs the server nothing — the
        // early wave asked the same, and ANISKIP_STALE answers it from the cache.
        final Map<SkipSegment.Category, JSONObject> best = new LinkedHashMap<>();
        final JSONArray anyLength;
        if (durationSec > 0) {
            final long mal = malId;
            final JSONArray[] replies = new JSONArray[2];
            // Guarded like every other worker here (see find): an uncaught throw in a thread ends the process.
            final Thread lowerProbe = new Thread(() -> {
                try {
                    replies[0] = aniskipTimes(mal, episode, durationSec - CUT_PROBE_SHIFT_SEC);
                } catch (Throwable ignored) {
                    // No reply, as for a failed request.
                }
            }, "SegmentSource-aniskip-lower");
            final Thread anyProbe = new Thread(() -> {
                try {
                    replies[1] = aniskipTimes(mal, episode, 0);
                } catch (Throwable ignored) {
                    // No reply, as for a failed request.
                }
            }, "SegmentSource-aniskip-any");
            lowerProbe.setDaemon(true);
            anyProbe.setDaemon(true);
            lowerProbe.start();
            anyProbe.start();
            final JSONArray atLength = aniskipTimes(mal, episode, durationSec);
            try {
                lowerProbe.join(); // bounded by the client's own call timeout
                anyProbe.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // media changed — go with whatever landed
            }
            // At-length first: on an equal distance to the file it keeps the entry Aniskip itself chose.
            collectCuts(best, atLength, durationSec);
            collectCuts(best, replies[0], durationSec);
            anyLength = replies[1];
        } else {
            anyLength = aniskipTimes(malId, episode, 0); // the early wave's only option
        }
        if (best.isEmpty()) {
            // Nothing recorded near this length — 0 is the documented "any length" wildcard.
            collectCuts(best, anyLength, durationSec);
        }

        boolean anyOurs = false;
        for (Map.Entry<SkipSegment.Category, JSONObject> entry : best.entrySet()) {
            final JSONObject interval = entry.getValue().optJSONObject("interval");
            final double end = interval.optDouble("endTime", Double.NaN);
            // A submission from a longer cut marks a moment this file does not reach — not our episode.
            if (durationSec > 0 && end > durationSec + CUT_MATCH_SEC) {
                continue;
            }
            // Anime files are usually the broadcast cut, so Aniskip's absolute times are the primary time
            // source for anime (TT_ANIME, above duration-aware) — but only for a submission recorded for
            // this cut. A foreign cut's seconds must not outrank the sources that adapt to the stream.
            final boolean ours = cutDelta(entry.getValue(), durationSec) <= CUT_MATCH_SEC;
            anyOurs |= ours;
            addSeg(out, interval.optDouble("startTime", Double.NaN), end, entry.getKey(),
                    SkipSegment.CoordBase.ABSOLUTE, ours ? TT_ANIME : TT_ABS);
        }
        // Crowd-voted with real op/ed ends — a high signal for anime, halved for a foreign cut.
        return new Scored(out, anyOurs ? 0.9 : 0.45);
    }

    /**
     * arm: {@code {id, episode}} — the {@code field} id ({@code myanimelist}, {@code anilist}) of the
     * anime entry for this season (a film: {@code season < 1}) and the episode's number inside that entry;
     * null when the title is not anime or that season has none. Never pass {@code ?include=}: it drops
     * the -season fields.
     *
     * <p>A long-running show can be one entry with no season at all (One Piece: MAL 21 counts 1 to 1100+),
     * while the launcher numbers episodes the TMDB way, by season. That entry is taken when no season
     * matches and the show has no seasoned entry, and the episode becomes its absolute number; when TMDB cannot say what that is, the entry
     * is not guessed at — another episode's timings are worse than none.
     */
    private static long[] armId(String imdbId, int season, int episode, String field) {
        final HttpUrl armUrl = HttpUrl.parse(SegmentEndpoints.ARM).newBuilder()
                .addQueryParameter("id", imdbId)
                .build();
        final JSONArray entries = getJsonArray(armUrl);
        if (entries == null) {
            return null;
        }
        JSONObject unseasoned = null;
        JSONObject seasonOne = null;
        boolean anySeasoned = false;
        for (int i = 0; i < entries.length(); i++) {
            final JSONObject entry = entries.optJSONObject(i);
            if (entry == null || entry.isNull(field)) {
                continue;
            }
            // A film is asked with no season, and so is a series entry arm left unseasoned: only the media
            // type tells them apart. Its season fields are no help either — Mugen Train is filed under the
            // series as thetvdb-season 0.
            if (season < 1) {
                if ("MOVIE".equals(entry.optString("media"))) {
                    return new long[]{entry.optLong(field, -1), episode};
                }
                continue;
            }
            int entrySeason = -1;
            if (!entry.isNull("themoviedb-season")) {
                entrySeason = entry.optInt("themoviedb-season", -1);
            } else if (!entry.isNull("thetvdb-season")) {
                entrySeason = entry.optInt("thetvdb-season", -1);
            } else if (unseasoned == null && !"MOVIE".equals(entry.optString("media"))) {
                unseasoned = entry;
            }
            anySeasoned |= entrySeason >= 0;
            if (entrySeason == season) {
                return new long[]{entry.optLong(field, -1), episode}; // first match for the season
            }
            if (entrySeason == 1 && seasonOne == null && !"MOVIE".equals(entry.optString("media"))) {
                seasonOne = entry;
            }
        }
        // A season arm does not have, of a show TMDB keeps as a single season long enough for the episode:
        // the launcher numbers it as Kodik files it. Kodik puts all 64 episodes of Fullmetal Alchemist:
        // Brotherhood under "season 2" (after the 2003 series), so a viewer's S2E5 is episode 5 of the one
        // season TMDB, arm and Aniskip know. Nothing matched before, so this can only add an answer.
        if (season > 1 && seasonOne != null
                && tmdbOnlySeasonHolds(seasonOne.optLong("themoviedb", -1), episode)) {
            return new long[]{seasonOne.optLong(field, -1), episode};
        }
        // Only a show arm keeps as that one entry: next to seasoned ones, an unseasoned entry is some
        // other part (an OVA filed as TV), and its absolute numbers would name the wrong episode.
        if (unseasoned == null || anySeasoned) {
            return null;
        }
        final int absolute = season == 1 ? episode
                : tmdbAbsoluteEpisode(unseasoned.optLong("themoviedb", -1), season, episode);
        return absolute < 1 ? null : new long[]{unseasoned.optLong(field, -1), absolute};
    }

    /** Whether TMDB keeps the show as one regular season (specials aside) of at least {@code episode} episodes. */
    private static boolean tmdbOnlySeasonHolds(long tmdbTvId, int episode) {
        if (tmdbTvId < 0 || episode < 1) {
            return false;
        }
        // The same URL tmdbAbsoluteEpisode asks, so it is answered from the HTTP cache after the first time.
        final JSONObject root = getJson(HttpUrl.parse(SegmentEndpoints.TMDB_BASE).newBuilder()
                .addPathSegment("tv")
                .addPathSegment(String.valueOf(tmdbTvId))
                .addQueryParameter("api_key", SegmentEndpoints.TMDB_KEY)
                .build());
        final JSONArray seasons = root == null ? null : root.optJSONArray("seasons");
        int regular = 0;
        int count = 0;
        for (int i = 0; seasons != null && i < seasons.length(); i++) {
            final JSONObject s = seasons.optJSONObject(i);
            if (s != null && s.optInt("season_number", 0) >= 1) {
                regular++;
                count = s.optInt("episode_count", 0);
            }
        }
        return regular == 1 && episode <= count;
    }

    /**
     * TMDB season S, episode E as one count from the first episode: the episodes of seasons 1..S-1, plus
     * E. Specials (season 0) are not counted. -1 when TMDB does not answer or lacks those seasons.
     */
    private static int tmdbAbsoluteEpisode(long tmdbTvId, int season, int episode) {
        if (tmdbTvId < 0) {
            return -1;
        }
        final HttpUrl url = HttpUrl.parse(SegmentEndpoints.TMDB_BASE).newBuilder()
                .addPathSegment("tv")
                .addPathSegment(String.valueOf(tmdbTvId))
                .addQueryParameter("api_key", SegmentEndpoints.TMDB_KEY)
                .build();
        final JSONObject root = getJson(url);
        final JSONArray seasons = root == null ? null : root.optJSONArray("seasons");
        if (seasons == null) {
            return -1;
        }
        int before = 0;
        int counted = 0;
        for (int i = 0; i < seasons.length(); i++) {
            final JSONObject s = seasons.optJSONObject(i);
            final int number = s == null ? 0 : s.optInt("season_number", 0);
            if (number >= 1 && number < season) {
                before += s.optInt("episode_count", 0);
                counted++;
            }
        }
        return counted == season - 1 ? before + episode : -1;
    }

    /**
     * Anime Skip (GraphQL, AniList-keyed via arm): an anime fallback that covers episodes Aniskip lacks,
     * and recaps/previews it rarely has. Timestamps are start markers only: each segment runs to the
     * next marker, the last one to {@code baseDuration}.
     *
     * <p>Its numbering is untidy: the episode number sits in {@code number}, or only in {@code name},
     * the season field is often wrong, and one number can appear more than once. So the episode is
     * matched by number or name alone, the entry closest to the file length wins, and one more than
     * {@link #ANIMESKIP_CUT_SEC} away is another release whose seconds do not apply here.
     *
     * <p>Two requests, not one. The server builds a show's reply slowly, per timestamp: the first byte
     * comes in 0.3 s, but all of Frieren's 35 episodes with their timestamps take 2-10 s and One Piece's
     * 674 KB take 15 s, past every timeout. The episode list alone takes 0.8 s (One Piece 1.5 s), and the
     * timestamps of one episode another 0.9 s.
     */
    private static Scored animeSkip(String imdbId, int season, int seriesEpisode, double durationSec) {
        final List<SkipSegment> out = new ArrayList<>();
        if (seriesEpisode < 1) {
            return new Scored(out, 0);
        }
        final long[] arm = armId(imdbId, season, seriesEpisode, "anilist");
        if (arm == null) {
            return new Scored(out, 0);
        }
        final long anilist = arm[0];
        final String number = String.valueOf(arm[1]);
        final JSONArray shows = animeSkipShows(anilist, number);
        if (shows == null) {
            return new Scored(out, 0);
        }
        // Every entry for this episode number that can be this release, nearest the file first. One recorded
        // with its zero shifted (below) states a length short by that shift, so a shorter entry stays in
        // until its timestamps say what it really was.
        final List<JSONObject> candidates = new ArrayList<>();
        for (int i = 0; i < shows.length(); i++) {
            final JSONObject show = shows.optJSONObject(i);
            final JSONArray eps = show == null ? null : show.optJSONArray("episodes");
            for (int j = 0; eps != null && j < eps.length(); j++) {
                final JSONObject ep = eps.optJSONObject(j);
                if (animeSkipIs(ep, number) && (animeSkipDelta(ep, durationSec) <= ANIMESKIP_CUT_SEC
                        || ep.optDouble("baseDuration", 0) < durationSec)) {
                    candidates.add(ep);
                }
            }
        }
        Collections.sort(candidates, (a, b) ->
                Double.compare(animeSkipDelta(a, durationSec), animeSkipDelta(b, durationSec)));
        // A duplicate entry often carries no timestamps (Spirited Away has two, both empty), so the next
        // nearest gets one more request. ponytail: two tries, a third entry would cost another 0.9 s.
        JSONObject best = null;
        JSONArray ts = null;
        double shift = 0;
        double bestDelta = 0;
        for (int i = 0; i < Math.min(2, candidates.size()); i++) {
            final JSONObject ep = candidates.get(i);
            final JSONArray got = animeSkipTimestamps(ep.optString("id"));
            if (got == null || got.length() == 0) {
                continue;
            }
            // An entry recorded with its zero shifted keeps its spacing but starts below 0: Full-Time
            // Magister S4E6 has Branding -199, Intro -176, Canon -79, Credits 936 and a baseDuration of
            // 1037.5, where every other episode of the season starts at 0. Moved up to its first marker,
            // it is 1236.5 s long, the length of the file it was asked for.
            //
            // Moved only for an entry the stated length would have ruled out, and only when the moved one
            // is this file's cut: a marker below 0 is more often a lead-in before the recording's zero than
            // a shifted recording (13 of 379 entries across 15 shows have one; Demon Slayer's Branding at
            // -12 is right as it stands). So every entry taken before is read exactly as before, and with
            // no length to compare nothing moves.
            double first = 0;
            for (int k = 0; k < got.length(); k++) {
                final JSONObject t = got.optJSONObject(k);
                if (t != null) {
                    first = Math.min(first, t.optDouble("at", 0));
                }
            }
            final double stated = animeSkipDelta(ep, durationSec);
            final double moved = durationSec > 0
                    ? Math.abs(ep.optDouble("baseDuration", 0) - first - durationSec) : stated;
            final boolean move = stated > ANIMESKIP_CUT_SEC && moved <= CUT_MATCH_SEC;
            final double delta = move ? moved : stated;
            if (delta <= ANIMESKIP_CUT_SEC) {
                best = ep;
                ts = got;
                shift = move ? -first : 0;
                bestDelta = delta;
                break;
            }
        }
        if (ts == null) {
            return new Scored(out, 0);
        }
        final List<JSONObject> marks = new ArrayList<>();
        for (int i = 0; i < ts.length(); i++) {
            if (ts.optJSONObject(i) != null) {
                marks.add(ts.optJSONObject(i));
            }
        }
        Collections.sort(marks, (a, b) -> Double.compare(a.optDouble("at", 0), b.optDouble("at", 0)));
        final double fileEnd = best.has("baseDuration") ? best.optDouble("baseDuration") + shift : durationSec;
        // Recorded for this file's cut, its seconds are this file's seconds — the same reason Aniskip's own
        // cut outranks the duration-shifted sources. Another cut's only point the right way.
        final int trust = durationSec > 0 && bestDelta <= CUT_MATCH_SEC ? TT_ANIMESKIP : TT_ABS;
        SkipSegment.Category prev = null;
        double prevStart = 0;
        for (int i = 0; i <= marks.size(); i++) {
            final boolean last = i == marks.size();
            final SkipSegment.Category cat = last ? null : animeSkipCategory(marks.get(i));
            if (!last && cat == prev) {
                continue; // back-to-back markers of one kind (two recaps) are one segment
            }
            final double at = last ? fileEnd : marks.get(i).optDouble("at", 0) + shift;
            // Only credits and the preview after them run to the end of the file. An intro that is the
            // last marker (Weathering With You has nothing else) says where it starts, not where it ends.
            if (prev != null && !(last && prev != SkipSegment.Category.CREDITS
                    && prev != SkipSegment.Category.PREVIEW)) {
                addSeg(out, prevStart, at, prev, SkipSegment.CoordBase.ABSOLUTE, trust);
            }
            prev = cat;
            prevStart = at;
        }
        return new Scored(out, bestDelta <= CUT_MATCH_SEC ? 0.8 : 0.4);
    }

    /** The episode by number, or — where that is all Anime Skip kept (One Piece) — by name. */
    private static boolean animeSkipIs(JSONObject ep, String number) {
        return ep != null && (number.equals(ep.optString("number")) || number.equals(ep.optString("name")));
    }

    /**
     * The show's episode list, from disk while it is fresh and still has this episode, otherwise from the
     * server (and then saved). Null when neither has it.
     */
    private static JSONArray animeSkipShows(long anilist, String number) {
        final String name = anilist + ".json";
        final JSONArray saved = animeSkipLoad(name, ANIMESKIP_LIST_TTL_MS);
        for (int i = 0; saved != null && i < saved.length(); i++) {
            final JSONObject show = saved.optJSONObject(i);
            final JSONArray eps = show == null ? null : show.optJSONArray("episodes");
            for (int j = 0; eps != null && j < eps.length(); j++) {
                if (animeSkipIs(eps.optJSONObject(j), number)) {
                    return saved;
                }
            }
        }
        final JSONArray shows = animeSkipQuery("findShowsByExternalId(service: ANILIST, serviceId: \""
                + anilist + "\") { episodes { id number name baseDuration } }", "findShowsByExternalId");
        animeSkipSave(name, shows);
        return shows;
    }

    /**
     * One episode's timestamps, kept for a day like Aniskip's replies: a rewatch, or the full wave after
     * the early one, then asks the server nothing. An empty list is kept too — it is an answer. A day, not
     * the platforms' three: people add and correct these, and a fix should arrive by the next evening.
     */
    private static JSONArray animeSkipTimestamps(String episodeId) {
        final String name = "ts-" + episodeId.replaceAll("[^A-Za-z0-9-]", "") + ".json";
        final JSONArray saved = animeSkipLoad(name, ANISKIP_STALE_MS);
        if (saved != null) {
            return saved;
        }
        final JSONArray ts = animeSkipQuery("findTimestampsByEpisodeId(episodeId: \"" + episodeId
                + "\") { at type { name } }", "findTimestampsByEpisodeId");
        animeSkipSave(name, ts);
        return ts;
    }

    /** A saved reply younger than {@code ttlMs}; null when missing, stale, truncated or unreadable. */
    private static JSONArray animeSkipLoad(String name, long ttlMs) {
        final java.io.File dir = animeSkipDir;
        final java.io.File file = dir == null ? null : new java.io.File(dir, name);
        if (file == null || System.currentTimeMillis() - file.lastModified() >= ttlMs) {
            return null; // a missing file has lastModified 0, always stale
        }
        try (java.io.DataInputStream in = new java.io.DataInputStream(new java.io.FileInputStream(file))) {
            final byte[] bytes = new byte[(int) file.length()];
            in.readFully(bytes);
            return new JSONArray(new String(bytes, "UTF-8"));
        } catch (Exception e) {
            return null;
        }
    }

    /** Saves a reply for {@link #animeSkipLoad}; a failure only costs the next lookup a request. */
    private static void animeSkipSave(String name, JSONArray reply) {
        final java.io.File dir = animeSkipDir;
        if (reply == null || dir == null) {
            return;
        }
        final java.io.File file = new java.io.File(dir, name);
        // Written aside and renamed, so a lookup reading it at the same moment never sees half a file;
        // a temp name of its own, because both waves of one episode can get here together.
        java.io.File tmp = null;
        try {
            dir.mkdirs();
            tmp = java.io.File.createTempFile("save", ".tmp", dir);
            try (java.io.FileOutputStream os = new java.io.FileOutputStream(tmp)) {
                os.write(reply.toString().getBytes("UTF-8"));
            }
            // Linux renames over the old file atomically; where rename will not replace (Windows), make room.
            if (!tmp.renameTo(file) && !(file.delete() && tmp.renameTo(file))) {
                tmp.delete();
            }
            pruneAnimeSkipDir(dir, file);
        } catch (Exception ignored) {
            if (tmp != null) {
                tmp.delete(); // disk full or cache dir gone
            }
        }
    }

    /**
     * Keeps the saved lists bounded: expired ones go, then the oldest until the rest fit
     * {@link #ANIMESKIP_DIR_BYTES}. {@code keep}, just written, always stays. Runs only after a fetch,
     * which is rare, so listing the directory costs nothing that matters.
     */
    private static void pruneAnimeSkipDir(java.io.File dir, java.io.File keep) {
        final java.io.File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        java.util.Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified())); // newest first
        final long now = System.currentTimeMillis();
        long total = onDisk(keep);
        for (java.io.File f : files) {
            if (f.equals(keep)) {
                continue;
            }
            // A .tmp younger than a minute belongs to a write still in flight; an older one was abandoned.
            final boolean inFlight = f.getName().endsWith(".tmp") && now - f.lastModified() < 60_000;
            if (inFlight) {
                continue;
            }
            total += onDisk(f);
            if (f.getName().endsWith(".tmp") || now - f.lastModified() >= ANIMESKIP_LIST_TTL_MS
                    || total > ANIMESKIP_DIR_BYTES) {
                total -= onDisk(f);
                f.delete();
            }
        }
    }

    /** What a file takes on disk: whole 4 KB blocks, however few bytes it holds. */
    private static long onDisk(java.io.File f) {
        return (f.length() + 4095) / 4096 * 4096;
    }

    /**
     * Kodik (Russian anime video CDN): the skip ranges its player shows, e.g. {@code "00:13-01:43,22:50-24:30"}.
     * MAL-keyed through arm — Kodik's shikimoriID is the MAL id. Two requests: get-player names the player
     * page, and the page carries the ranges in a {@code parseSkipButton("…")} call.
     *
     * <p>The ranges are timed to whichever Russian dub Kodik picked (a 5-30 s studio card sometimes comes
     * first), carry no type and no length, and part of them look copied from Aniskip. So it asks only with
     * the file length known, a range ending past the file is another cut and is dropped, a range starting
     * in the first half is the opening and the rest are credits, and its timings rank lowest: a vote, not a
     * clock.
     */
    private static Scored kodik(String imdbId, int season, int seriesEpisode, double durationSec) {
        final List<SkipSegment> out = new ArrayList<>();
        final long[] arm = seriesEpisode < 1 ? null : armId(imdbId, season, seriesEpisode, "myanimelist");
        if (arm == null) {
            return new Scored(out, 0);
        }
        final boolean film = season < 1;
        final String saved = "kodik-" + arm[0] + (film ? "" : "-" + arm[1]) + ".json";
        final JSONArray cached = animeSkipLoad(saved, ANISKIP_STALE_MS);
        String ranges = cached != null ? cached.optString(0, null) : null;
        if (ranges == null) {
            ranges = kodikRanges(arm[0], film ? -1 : (int) arm[1]);
            if (ranges == null) {
                return new Scored(out, 0); // not there, or the request failed: nothing to remember
            }
            animeSkipSave(saved, new JSONArray().put(ranges));
        }
        for (String range : ranges.split(",")) {
            final int dash = range.indexOf('-');
            if (dash < 0) {
                continue;
            }
            final double start = clockSec(range.substring(0, dash));
            final double end = clockSec(range.substring(dash + 1));
            if (Double.isNaN(start) || Double.isNaN(end) || end > durationSec + CUT_MATCH_SEC) {
                continue;
            }
            addSeg(out, start, end, start < durationSec / 2 ? SkipSegment.Category.INTRO
                    : SkipSegment.Category.CREDITS, SkipSegment.CoordBase.ABSOLUTE, TT_ABS);
        }
        return new Scored(out, 0.4);
    }

    /**
     * The player's skip ranges for this MAL title and episode ({@code episode < 1}: a film), "" when the
     * player has none, null when Kodik lacks the title or a request failed.
     */
    private static String kodikRanges(long malId, int episode) {
        final HttpUrl.Builder api = HttpUrl.parse(SegmentEndpoints.KODIK).newBuilder()
                .addQueryParameter("token", SegmentEndpoints.KODIK_TOKEN)
                .addQueryParameter("shikimoriID", String.valueOf(malId));
        if (episode >= 1) {
            api.addQueryParameter("episode", String.valueOf(episode));
        }
        final JSONObject found = getJson(api.build());
        final String link = found == null || !found.optBoolean("found") ? null : found.optString("link", null);
        final HttpUrl page = link == null ? null : HttpUrl.parse("https:" + link);
        if (page == null) {
            return null;
        }
        final HttpUrl.Builder url = page.newBuilder();
        if (episode >= 1) {
            url.addQueryParameter("season", "1").addQueryParameter("episode", String.valueOf(episode));
        }
        // A series page lists every episode (One Piece: 888 KB, 93 KB gzipped), but the ranges come at about
        // 17 KB, so it is read line by line up to them and the call cancelled: closing alone would have
        // OkHttp drain the rest to reuse the connection.
        final Request request = new Request.Builder().url(url.build()).build();
        final okhttp3.Call call = CLIENT.newCall(request);
        try (Response response = call.execute()) {
            final ResponseBody body = response.body();
            if (!response.isSuccessful() || body == null) {
                Utils.log(request.url().host() + ": " + response.code());
                return null;
            }
            final okio.BufferedSource source = body.source();
            long read = 0;
            String line;
            while (read < KODIK_PAGE_BYTES && (line = source.readUtf8Line()) != null) {
                read += line.length();
                final int at = line.indexOf("parseSkipButton(\"");
                if (at >= 0) {
                    final int from = at + "parseSkipButton(\"".length();
                    final int to = line.indexOf('"', from);
                    call.cancel();
                    return to > from ? line.substring(from, to) : "";
                }
            }
            call.cancel();
            return "";
        } catch (Exception e) {
            if (!Thread.currentThread().isInterrupted()) {
                Utils.log(request.url().host() + ": " + e);
            }
            return null;
        }
    }

    /**
     * Fetches of the platforms' marks outlive the lookup that started them. The three sit in China, one to
     * several seconds a request from Europe, and the early wave that starts them at load was cut off by the
     * full wave a few seconds later, which then began the same requests again: on the emulator 大王饶命
     * S2E11 threw away 2.8 s that way. Now a fetch runs in a thread of its own, once per episode: the full
     * wave, or the next item's prefetch, waits for the one already under way, and a lookup that is cut off
     * leaves it to finish and save to disk. The answer is the same; only when it arrives changes.
     */
    private static final java.util.concurrent.ExecutorService PLATFORM_POOL =
            java.util.concurrent.Executors.newCachedThreadPool(r -> {
                final Thread t = new Thread(r, "segments-platform");
                t.setDaemon(true);
                return t;
            });
    private static final Map<String, java.util.concurrent.Future<JSONArray>> PLATFORM_FETCHES =
            new ConcurrentHashMap<>();

    /** {@code fetch}'s answer, joining one already under way for {@code key}; null when it failed. */
    private static JSONArray shared(String key, java.util.concurrent.Callable<JSONArray> fetch) {
        java.util.concurrent.Future<JSONArray> future =
                PLATFORM_FETCHES.computeIfAbsent(key, k -> PLATFORM_POOL.submit(fetch));
        // One that already failed, left behind by a waiter that was cut off, is not an answer: start again.
        if (future.isDone() && doneValue(future) == null) {
            PLATFORM_FETCHES.remove(key, future);
            future = PLATFORM_FETCHES.computeIfAbsent(key, k -> PLATFORM_POOL.submit(fetch));
        }
        try {
            return future.get();
        } catch (java.util.concurrent.ExecutionException e) {
            return null;
        } catch (InterruptedException e) {
            // This lookup was cut off; the fetch goes on. traced() reads the flag and says so.
            Thread.currentThread().interrupt();
            return null;
        } finally {
            // Done is forgotten, so the next lookup reads the disk (or retries a failure) afresh. A future
            // whose waiter was cut off stays until the next one for the key picks it up, answer and all.
            if (future.isDone()) {
                PLATFORM_FETCHES.remove(key, future);
            }
        }
    }

    private static JSONArray doneValue(java.util.concurrent.Future<JSONArray> future) {
        try {
            return future.get();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * How long what the platforms said is kept. A released episode's marks do not change, and a series
     * is watched over days, so found answers last three; "not there" (an empty answer) and an episode
     * with no marks yet last a day, since a title can arrive, a season be added or marks be set later.
     * A list missing the episode asked for is asked again sooner (see the callers).
     */
    private static final long PLATFORM_TTL_MS = TimeUnit.DAYS.toMillis(3);

    private static JSONArray platformLoad(String name) {
        final JSONArray kept = animeSkipLoad(name, PLATFORM_TTL_MS);
        return kept != null && kept.length() == 0 ? animeSkipLoad(name, ANISKIP_STALE_MS) : kept;
    }

    /**
     * One episode's marks out of its season's file, which holds a row
     * {@code [episode, length, op start, op end, ed start, ed end, saved at]} per episode: one small file a season
     * rather than a file an episode, each taking a 4 KB block of disk for its 30 bytes.
     */
    private static JSONArray seasonMarks(String prefix, int episode) {
        final JSONArray rows = animeSkipLoad(prefix + "-marks.json", PLATFORM_TTL_MS);
        for (int i = 0; rows != null && i < rows.length(); i++) {
            final JSONArray row = rows.optJSONArray(i);
            if (row != null && row.length() >= 6 && row.optInt(0) == episode) {
                // No marks yet (or no such episode): a day, like any empty answer, then asked again. Timed by
                // the row's own save time, since writing another episode refreshes the file's; a row an
                // older build wrote without one goes by the file.
                final long savedAt = row.optLong(6, 0);
                final boolean dayOld = savedAt > 0
                        ? System.currentTimeMillis() - savedAt >= ANISKIP_STALE_MS
                        : animeSkipLoad(prefix + "-marks.json", ANISKIP_STALE_MS) == null;
                if (row.optDouble(3) <= 0 && row.optDouble(5) <= 0 && dayOld) {
                    return null;
                }
                return marks(row.optDouble(1), row.optDouble(2), row.optDouble(3), row.optDouble(4),
                        row.optDouble(5));
            }
        }
        return null;
    }

    private static final Object SEASON_MARKS_LOCK = new Object();

    /** Adds episodes to their season's file; an episode already there is replaced. */
    private static void saveSeasonMarks(String prefix, Map<Integer, JSONArray> byEpisode) {
        if (byEpisode.isEmpty()) {
            return;
        }
        // Read, merge and write as one: two episodes of a season can be fetched at once (this one and the
        // next item's prefetch), and each would otherwise write over the other's row.
        synchronized (SEASON_MARKS_LOCK) {
            final JSONArray old = animeSkipLoad(prefix + "-marks.json", PLATFORM_TTL_MS);
            final JSONArray out = new JSONArray();
            for (int i = 0; old != null && i < old.length(); i++) {
                final JSONArray row = old.optJSONArray(i);
                if (row != null && !byEpisode.containsKey(row.optInt(0))) {
                    out.put(row);
                }
            }
            final long now = System.currentTimeMillis();
            for (Map.Entry<Integer, JSONArray> e : byEpisode.entrySet()) {
                final JSONArray row = new JSONArray().put(e.getKey());
                for (int i = 0; i < 5; i++) {
                    row.put(e.getValue().opt(i));
                }
                out.put(row.put(now)); // when this row was learnt, for seasonMarks' day on an empty one
            }
            animeSkipSave(prefix + "-marks.json", out);
        }
    }

    /**
     * The streaming platforms' own marks for Chinese animation (donghua), which the anime sources barely
     * cover: Bilibili and iQIYI each mark an episode's opening and ending for their skip button, and say
     * how long that episode is. Asked only for a series TMDB calls Chinese, by its original name.
     *
     * <p>The length is what the trust rests on. A file within {@link #CUT_MATCH_SEC} of it is that
     * platform's cut, and its seconds are this file's seconds: the most exact timing any source here has.
     * Any other length is another release (Tencent's, a re-encode, a dub with a studio card), and the marks
     * only point the right way: a vote, like Kodik's, with an ending past the file dropped.
     *
     * <p>{@code marks} is {@code [length s, op start, op end, ed start, ed end]}; 0-0 is "none", and an
     * empty array "not on this platform".
     */
    private static Scored platformMarks(JSONArray marks, double durationSec, boolean moveOpeningEnd) {
        final List<SkipSegment> out = new ArrayList<>();
        if (marks == null || marks.length() < 5) {
            return new Scored(out, 0);
        }
        final boolean ours = durationSec > 0 && Math.abs(marks.optDouble(0) - durationSec) <= CUT_MATCH_SEC;
        final int trust = ours ? TT_PLATFORM : TT_ABS;
        // Tencent only (a new source, so nothing it did before can get worse): a file shorter than its
        // episode ends its opening earlier by the difference. The viewer's rip of 大王饶命 S2E12, 10 s short of
        // Tencent's 1694 s, ends its opening at 113 s against Tencent's 123, by frames. Only the end moves;
        // the start is where the vote clusters and the opening begins, so it stays. Bilibili and iQIYI keep
        // their marks as they were: had the loss been at the end, this would leave seconds of opening.
        final double lost = moveOpeningEnd && durationSec > 0 && !ours ? marks.optDouble(0) - durationSec : 0;
        final double shift = lost > 0 && lost <= ANIMESKIP_CUT_SEC ? lost : 0;
        addSeg(out, marks.optDouble(1), marks.optDouble(2) - shift, SkipSegment.Category.INTRO,
                SkipSegment.CoordBase.ABSOLUTE, trust);
        if (ours || durationSec <= 0 || marks.optDouble(4) <= durationSec + CUT_MATCH_SEC) {
            addSeg(out, marks.optDouble(3), marks.optDouble(4), SkipSegment.Category.CREDITS,
                    SkipSegment.CoordBase.ABSOLUTE, trust);
        }
        return new Scored(out, ours ? 0.9 : 0.4);
    }

    /**
     * Bilibili: the search lists a season's episodes with their ids, and an episode's player carries its
     * marks ({@code clip_info_list}) and its exact length. Both sit in China, a second or more a request
     * from Europe, so the season's ids and each episode's marks are kept on disk for a day.
     */
    private static Scored bilibili(long tmdbTvId, int season, int episode, double durationSec) {
        if (tmdbTvId < 0 || season < 1 || episode < 1) {
            return new Scored(new ArrayList<>(), 0);
        }
        return platformMarks(shared("bilibili-" + tmdbTvId + "-" + season + "-" + episode,
                () -> bilibiliFetch(tmdbTvId, season, episode)), durationSec, false);
    }

    /** The episode's marks, from disk or from Bilibili (and then saved); null when not to be had. */
    private static JSONArray bilibiliFetch(long tmdbTvId, int season, int episode) {
        final String show = "bilibili-" + tmdbTvId + "-" + season;
        JSONArray marks = seasonMarks(show, episode);
        if (marks == null) {
            JSONArray eps = platformLoad(show + ".json");
            long epId = pairedId(eps, episode);
            // Not listed, or listed without this episode: a running show adds one a week, so the list is asked
            // again, but no more than once a day.
            if (eps == null || (epId < 0 && eps.length() > 0 && animeSkipLoad(show + ".json", ANISKIP_STALE_MS) == null)) {
                // Not a Chinese series: nothing is saved, TMDB's reply being HTTP-cached for hours, so a
                // Western show costs no disk block here.
                final String name = chineseName(tmdbTvId);
                if (name == null || name.isEmpty()) {
                    return null;
                }
                eps = bilibiliEpisodes(tmdbTvId, season);
                if (eps == null) {
                    return null; // a request failed: nothing to remember
                }
                animeSkipSave(show + ".json", eps); // an empty one remembers "not there", for a day
                epId = pairedId(eps, episode);
            }
            marks = epId < 0 ? null : bilibiliMarks(epId);
            if (marks == null) {
                return null;
            }
            saveSeasonMarks(show, java.util.Collections.singletonMap(episode, marks));
        }
        return marks;
    }

    /** The id paired with {@code episode} in {@code [episode, id]} pairs; -1 when it is not there. */
    private static long pairedId(JSONArray pairs, int episode) {
        for (int i = 0; pairs != null && i < pairs.length(); i++) {
            final JSONArray pair = pairs.optJSONArray(i);
            if (pair != null && pair.optInt(0) == episode) {
                return pair.optLong(1, -1);
            }
        }
        return -1;
    }

    /**
     * TMDB season {@code season} on Bilibili as {@code [episode, ep_id]} pairs in TMDB's numbering, straight
     * from the search, which lists every episode of each entry. Empty when the title is not Chinese or not
     * on Bilibili; null when a request failed.
     */
    private static JSONArray bilibiliEpisodes(long tmdbTvId, int season) {
        final String name = chineseName(tmdbTvId);
        if (name == null || name.isEmpty()) {
            return name == null ? null : new JSONArray();
        }
        final JSONObject found = bilibiliSearch(name);
        // A refused signature answers 200 with a code of its own, and a search its risk control turned away
        // 200 with no result list at all (a v_voucher instead): either is a failed request, not "not there".
        final JSONObject data = found == null || found.optInt("code", -1) != 0 ? null
                : found.optJSONObject("data");
        if (data == null || !data.has("result")) {
            return null;
        }
        final List<String> rests = new ArrayList<>();
        final List<JSONArray> episodes = new ArrayList<>();
        final JSONArray blocks = data.optJSONArray("result");
        for (int i = 0; blocks != null && i < blocks.length(); i++) {
            final JSONObject block = blocks.optJSONObject(i);
            final JSONArray items = block == null || !"media_bangumi".equals(block.optString("result_type"))
                    ? null : block.optJSONArray("data");
            for (int j = 0; items != null && j < items.length(); j++) {
                final JSONObject item = items.optJSONObject(j);
                // 1 is anime, 4 is Chinese animation; the rest are live-action namesakes.
                if (item == null || (item.optInt("media_type") != 1 && item.optInt("media_type") != 4)) {
                    continue;
                }
                final String title = item.optString("title").replaceAll("<[^>]+>", "").trim();
                if (title.startsWith(name) && item.optJSONArray("eps") != null) {
                    rests.add(title.substring(name.length()).trim());
                    episodes.add(item.optJSONArray("eps"));
                }
            }
        }
        final int[] entry = seasonEntry(rests, tmdbTvId, season);
        final JSONArray out = new JSONArray();
        if (entry == null) {
            return out;
        }
        final JSONArray eps = episodes.get(entry[0]);
        for (int i = 0; i < eps.length(); i++) {
            final JSONObject e = eps.optJSONObject(i);
            final int number = e == null ? -1 : wholeNumber(e.optString("title"));
            if (number - entry[1] >= 1) { // not a special ("SP", "12.5") or an episode of another season
                out.put(new JSONArray().put(number - entry[1]).put(e.optLong("id")));
            }
        }
        return out;
    }

    /** One Bilibili episode's marks, from its player; null when the request failed. */
    private static JSONArray bilibiliMarks(long epId) {
        // The player refuses OkHttp's User-Agent outright (412), as the search's risk control mostly does.
        final String body = execute(new Request.Builder()
                .url(HttpUrl.parse(SegmentEndpoints.BILIBILI_PLAYURL).newBuilder()
                        .addQueryParameter("ep_id", String.valueOf(epId)).build())
                .header("User-Agent", SegmentEndpoints.BILIBILI_UA)
                .build());
        final JSONObject result;
        try {
            final JSONObject reply = body == null ? null : new JSONObject(body);
            result = reply == null || reply.optInt("code", -1) != 0 ? null : reply.optJSONObject("result");
        } catch (Exception e) {
            return null;
        }
        if (result == null) {
            return null;
        }
        final double[] op = new double[2];
        final double[] ed = new double[2];
        final JSONArray clips = result.optJSONArray("clip_info_list");
        for (int i = 0; clips != null && i < clips.length(); i++) {
            final JSONObject clip = clips.optJSONObject(i);
            final String type = clip == null ? "" : clip.optString("clipType");
            final double[] into = "CLIP_TYPE_OP".equals(type) ? op : "CLIP_TYPE_ED".equals(type) ? ed : null;
            if (into != null) {
                into[0] = clip.optDouble("start", 0);
                into[1] = clip.optDouble("end", 0);
            }
        }
        return marks(result.optLong("timelength") / 1000.0, op[0], op[1], ed[0], ed[1]);
    }

    /**
     * iQIYI: its own donghua (万古神话 and the like; the big titles it lists are links to Tencent). An
     * episode says where its opening ends ({@code startTime}, the point its skip button jumps to) and where
     * its credits begin ({@code endTime}); 0 or -1 is none. Its servers answer in 1 to 20 s from Europe, so
     * the album and each episode's marks are kept on disk for a day, as Bilibili's are.
     */
    private static Scored iqiyi(long tmdbTvId, int season, int episode, double durationSec) {
        if (tmdbTvId < 0 || season < 1 || episode < 1) {
            return new Scored(new ArrayList<>(), 0);
        }
        return platformMarks(shared("iqiyi-" + tmdbTvId + "-" + season + "-" + episode,
                () -> iqiyiFetch(tmdbTvId, season, episode)), durationSec, false);
    }

    /** The episode's marks, from disk or from iQIYI (and then saved); null when not to be had. */
    private static JSONArray iqiyiFetch(long tmdbTvId, int season, int episode) {
        final String show = "iqiyi-" + tmdbTvId + "-" + season;
        JSONArray marks = seasonMarks(show, episode);
        if (marks == null) {
            JSONArray album = platformLoad(show + ".json");
            if (album == null) {
                // Not a Chinese series: nothing is saved, TMDB's reply being HTTP-cached for hours, so a
                // Western show costs no disk block here.
                final String name = chineseName(tmdbTvId);
                if (name == null || name.isEmpty()) {
                    return null;
                }
                album = iqiyiAlbum(tmdbTvId, season);
                if (album == null) {
                    return null;
                }
                animeSkipSave(show + ".json", album);
            }
            if (album.length() < 2) {
                return null; // not on iQIYI
            }
            marks = iqiyiMarks(album.optLong(0), episode + album.optInt(1));
            if (marks == null) {
                return null; // a request failed: asked again next time
            }
            if (marks.length() < 5) {
                marks = marks(0, 0, 0, 0, 0); // no such episode (yet): remembered for a day, see seasonMarks
            }
            saveSeasonMarks(show, java.util.Collections.singletonMap(episode, marks));
        }
        return marks;
    }

    /**
     * {@code [album id, episodes before this season]} for TMDB season {@code season} on iQIYI; empty when
     * the title is not Chinese or not iQIYI's own; null when a request failed.
     */
    private static JSONArray iqiyiAlbum(long tmdbTvId, int season) {
        final String name = chineseName(tmdbTvId);
        if (name == null || name.isEmpty()) {
            return name == null ? null : new JSONArray();
        }
        final JSONObject found = getJson(HttpUrl.parse(SegmentEndpoints.IQIYI_SEARCH).newBuilder()
                .addQueryParameter("key", name)
                .addQueryParameter("current_page", "1")
                .addQueryParameter("mode", "1")
                .addQueryParameter("source", "input")
                .addQueryParameter("pcode", "pcw_search")
                .addQueryParameter("version", "13.034.21571")
                .build());
        final JSONArray templates = found == null || found.optJSONObject("data") == null ? null
                : found.optJSONObject("data").optJSONArray("templates");
        if (templates == null) {
            return null;
        }
        final List<String> rests = new ArrayList<>();
        final List<Long> ids = new ArrayList<>();
        for (int i = 0; i < templates.length(); i++) {
            final JSONObject t = templates.optJSONObject(i);
            final JSONObject album = t == null ? null : t.optJSONObject("albumInfo");
            // Its own animation only: the search also lists what it links to on Tencent or Youku, and the
            // clips people cut from an episode, which carry the show's name in theirs.
            if (album == null || !"iqiyi".equals(album.optString("siteId"))
                    || !album.optString("channel").startsWith("动漫")) { // 动漫
                continue;
            }
            final String title = album.optString("title").trim();
            if (title.startsWith(name)) {
                rests.add(title.substring(name.length()).trim());
                ids.add(album.optLong("qipuId", -1));
            }
        }
        final int[] entry = seasonEntry(rests, tmdbTvId, season);
        return entry == null ? new JSONArray() : new JSONArray().put(ids.get(entry[0])).put(entry[1]);
    }

    /** Episode {@code order} of an iQIYI album: its length and marks; empty when absent, null on failure. */
    private static JSONArray iqiyiMarks(long albumId, int order) {
        // One episode a page, so the page number is the episode; the order it names is checked all the same.
        final JSONObject list = getJson(HttpUrl.parse(SegmentEndpoints.IQIYI_EPISODES).newBuilder()
                .addQueryParameter("aid", String.valueOf(albumId))
                .addQueryParameter("page", String.valueOf(order))
                .addQueryParameter("size", "1")
                .build());
        final JSONArray eps = list == null || list.optJSONObject("data") == null ? null
                : list.optJSONObject("data").optJSONArray("epsodelist");
        if (eps == null) {
            return null;
        }
        final JSONObject ep = eps.optJSONObject(0);
        if (ep == null || ep.optInt("order") != order) {
            return new JSONArray();
        }
        final JSONObject info = getJson(HttpUrl.parse(SegmentEndpoints.IQIYI_INFO + ep.optLong("tvId")));
        final JSONObject data = info == null ? null : info.optJSONObject("data");
        if (data == null) {
            return null;
        }
        final double length = clockSec(ep.optString("duration"));
        final double opEnd = Math.max(0, data.optDouble("startTime", 0));
        final double edStart = Math.max(0, data.optDouble("endTime", 0));
        return marks(length, 0, opEnd, edStart, edStart > 0 ? length : 0);
    }

    /** The {@link #platformMarks} array; a value that is not a number (an unreadable length) becomes 0. */
    private static JSONArray marks(double... values) {
        final JSONArray out = new JSONArray();
        for (double v : values) {
            out.put(Double.valueOf(Double.isNaN(v) ? 0 : v)); // put(Object): put(double) throws on NaN
        }
        return out;
    }

    /**
     * Tencent Video, where most big donghua stream only (斗罗大陆, 全职法师, 大王饶命, 完美世界, 仙逆).
     * Playback is region-locked outside China, but the metadata its web player reads is not: per episode
     * its length, {@code head_time} (where the skip jumps, the opening's end) and {@code tail_time} (how
     * long the credits run to the end, 0 when none: 87 on 大王饶命 S2E11, whose credits start at 1264-1268 s
     * of 1355).
     *
     * <p>Three requests to China, each once: the search (1.8 s, 380 KB whatever the page size) lists every
     * season of the show and is kept per show; a season's cover names its episodes in order (1 s); and one
     * union call answers for up to thirty episodes at a time (1 s), all kept, so the rest of a binge reads
     * from disk. Kept as platformLoad says: three days found, a day empty.
     */
    private static Scored tencent(long tmdbTvId, int season, int episode, double durationSec) {
        if (tmdbTvId < 0 || season < 1 || episode < 1) {
            return new Scored(new ArrayList<>(), 0);
        }
        final JSONArray marks = shared("tencent-" + tmdbTvId + "-" + season + "-" + episode,
                () -> tencentFetch(tmdbTvId, season, episode));
        if (marks == null) {
            return new Scored(new ArrayList<>(), 0);
        }
        // An episode more than a minute off the file is not this show's: TMDB's 斗罗大陆 drama shares its name
        // with the animation Tencent returns, and its 45-minute episodes would get the cartoon's marks.
        if (durationSec > 0 && marks.length() >= 5
                && Math.abs(marks.optDouble(0) - durationSec) > ANIMESKIP_CUT_SEC) {
            return new Scored(new ArrayList<>(), 0);
        }
        return platformMarks(marks, durationSec, true);
    }

    /** The episode's marks, from disk or from Tencent (and then saved); null when not to be had. */
    private static JSONArray tencentFetch(long tmdbTvId, int season, int episode) {
        final String show = "tencent-" + tmdbTvId;
        final JSONArray marks = seasonMarks(show + "-" + season, episode);
        if (marks != null) {
            return marks;
        }
        JSONArray covers = platformLoad(show + ".json");
        int[] entry = covers == null ? null : tencentEntry(covers, tmdbTvId, season);
        // Not kept, or kept without this season, which a running show may have added since: asked again,
        // no more than once a day.
        if (covers == null || (entry == null && covers.length() > 0
                && animeSkipLoad(show + ".json", ANISKIP_STALE_MS) == null)) {
            // Not a Chinese series: nothing is saved, TMDB's reply being HTTP-cached for hours, so a
            // Western show costs no disk block here.
            final String name = chineseName(tmdbTvId);
            if (name == null || name.isEmpty()) {
                return null;
            }
            covers = tencentCovers(tmdbTvId);
            if (covers == null) {
                return null; // a request failed: nothing to remember
            }
            animeSkipSave(show + ".json", covers); // an empty one remembers "not there", for a day
            entry = tencentEntry(covers, tmdbTvId, season);
        }
        if (entry == null) {
            return null;
        }
        final String cover = covers.optJSONArray(entry[0]).optString(1);
        final String prefix = show + "-" + season;
        tencentSeason(cover, episode, entry[1], prefix, false);
        JSONArray found = seasonMarks(prefix, episode);
        // Not among the episodes kept: a running season gives a new episode a new id, which a list older
        // than a day may not have yet (仙逆 lists episode 160 only as a trailer until it airs).
        if (found == null && animeSkipLoad(prefix + ".json", ANISKIP_STALE_MS) == null) {
            tencentSeason(cover, episode, entry[1], prefix, true);
            found = seasonMarks(prefix, episode);
        }
        return found;
    }

    /** Which kept cover is TMDB season {@code season}, as seasonEntry answers; null when none is. */
    private static int[] tencentEntry(JSONArray covers, long tmdbTvId, int season) {
        final List<String> rests = new ArrayList<>();
        for (int i = 0; i < covers.length(); i++) {
            rests.add(covers.optJSONArray(i) == null ? "\u0000" : covers.optJSONArray(i).optString(0));
        }
        return seasonEntry(rests, tmdbTvId, season);
    }

    /**
     * Every season of the show on Tencent as {@code [what the cover adds to the name, cover id]}; empty
     * when the title is not Chinese animation or not Tencent's own; null when a request failed.
     */
    private static JSONArray tencentCovers(long tmdbTvId) {
        final String name = chineseName(tmdbTvId);
        if (name == null || name.isEmpty()) {
            return name == null ? null : new JSONArray();
        }
        // Animation only (TMDB genre 16): Tencent's search keeps the cartoon under a name a live-action
        // drama shares, which Bilibili and iQIYI filter on their side. The TMDB reply is the HTTP-cached one
        // chineseName just read.
        final JSONObject tv = getJson(HttpUrl.parse(SegmentEndpoints.TMDB_BASE).newBuilder()
                .addPathSegment("tv").addPathSegment(String.valueOf(tmdbTvId))
                .addQueryParameter("api_key", SegmentEndpoints.TMDB_KEY).build());
        if (tv == null) {
            return null;
        }
        final JSONArray genres = tv.optJSONArray("genres");
        boolean animation = false;
        for (int i = 0; genres != null && i < genres.length(); i++) {
            animation |= genres.optJSONObject(i) != null && genres.optJSONObject(i).optInt("id") == 16;
        }
        if (!animation) {
            return new JSONArray();
        }
        final JSONObject found;
        try {
            found = tencentPost(SegmentEndpoints.TENCENT_SEARCH, new JSONObject()
                    .put("query", name).put("pagenum", 0).put("pagesize", 10).put("isneedQc", true)
                    .put("extraInfo", new JSONObject().put("multi_terminal_pc", "1")));
        } catch (Exception e) {
            return null;
        }
        final JSONObject data = found == null || found.optInt("ret", -1) != 0 ? null : found.optJSONObject("data");
        if (data == null) {
            return null;
        }
        // The seasons sit in the main box for some titles (全职法师) and in the plain list for others (大王饶命).
        final List<JSONObject> items = new ArrayList<>();
        final JSONObject normal = data.optJSONObject("normalList");
        final JSONArray boxes = data.optJSONArray("areaBoxList");
        for (int b = -1; b < (boxes == null ? 0 : boxes.length()); b++) {
            final JSONObject box = b < 0 ? normal : boxes.optJSONObject(b);
            final JSONArray list = box == null ? null : box.optJSONArray("itemList");
            for (int i = 0; list != null && i < list.length(); i++) {
                if (list.optJSONObject(i) != null) {
                    items.add(list.optJSONObject(i));
                }
            }
        }
        final JSONArray out = new JSONArray();
        final List<String> seen = new ArrayList<>();
        for (JSONObject item : items) {
            final JSONObject doc = item.optJSONObject("doc");
            final JSONObject info = item.optJSONObject("videoInfo");
            // A cover (dataType 2) of animation (videoType 3) that Tencent plays itself: the same name also
            // heads a live-action drama (斗罗大陆, videoType 2) and covers it only links to elsewhere.
            if (doc == null || info == null || doc.optInt("dataType") != 2 || info.optInt("videoType") != 3
                    || info.optJSONArray("episodeSites") == null || info.optJSONArray("episodeSites").length() == 0) {
                continue;
            }
            final String title = info.optString("title").replaceAll("<[^>]+>", "").trim();
            if (title.startsWith(name) && !seen.contains(doc.optString("id"))) {
                seen.add(doc.optString("id"));
                out.put(new JSONArray().put(title.substring(name.length()).trim()).put(doc.optString("id")));
            }
        }
        return out;
    }

    /**
     * Saves the marks of the thirty episodes around TMDB episode {@code episode} of a season's cover into
     * the season's file (saveSeasonMarks); {@code offset} is how many of the cover's episodes come before
     * this TMDB season (a show kept as one long cover). {@code fresh} asks for the episode list again rather
     * than reading the kept one. Nothing is saved when a request fails.
     */
    private static void tencentSeason(String coverId, int episode, int offset, String prefix, boolean fresh) {
        JSONArray vids = fresh ? null : platformLoad(prefix + ".json");
        // A running season lists more episodes each week: one past the end of the kept list asks for the list
        // again, no more than once a day.
        if (vids != null && episode + offset > vids.length()
                && animeSkipLoad(prefix + ".json", ANISKIP_STALE_MS) == null) {
            vids = null;
        }
        if (vids == null) {
            final JSONObject cover = tencentUnion("431", coverId);
            vids = cover == null ? null : cover.optJSONArray("video_ids");
            if (vids == null) {
                return;
            }
            animeSkipSave(prefix + ".json", vids);
        }
        final int order = episode + offset;
        // The cover lists its episodes in order, so the block holding position order - 1 usually holds the
        // episode; where trailers or specials push it along (完美世界 lists 293 of 338), the block next to it
        // is asked once. ponytail: two blocks, a list off by more than thirty would need a search through it.
        int start = Math.max(0, Math.min(order - 1, vids.length() - 1)) / 30 * 30;
        for (int tries = 0; tries < 2 && start >= 0 && start < vids.length(); tries++) {
            final StringBuilder ids = new StringBuilder();
            for (int i = start; i < Math.min(start + 30, vids.length()); i++) {
                ids.append(ids.length() > 0 ? "," : "").append(vids.optString(i));
            }
            final JSONArray results = tencentUnionAll("682", ids.toString());
            if (results == null) {
                return;
            }
            int lowest = Integer.MAX_VALUE;
            int highest = Integer.MIN_VALUE;
            boolean found = false;
            final Map<Integer, JSONArray> block = new java.util.HashMap<>();
            for (int i = 0; i < results.length(); i++) {
                final JSONObject r = results.optJSONObject(i);
                final JSONObject fields = r == null ? null : r.optJSONObject("fields");
                final int number = fields == null ? -1 : wholeNumber(fields.optString("episode"));
                // The episodes proper; a cover also lists trailers and extras under episode numbers of their own.
                final JSONArray kind = fields == null ? null : fields.optJSONArray("category_map");
                if (number < 1 || (kind != null && !kind.toString().contains("正片"))) { // 正片
                    continue;
                }
                lowest = Math.min(lowest, number);
                highest = Math.max(highest, number);
                found |= number == order;
                if (number - offset >= 1) {
                    final double length = fields.optDouble("duration", 0);
                    final double opEnd = Math.max(0, fields.optDouble("head_time", 0));
                    final double tail = Math.max(0, fields.optDouble("tail_time", 0));
                    block.put(number - offset, tail > 0 && tail < length
                            ? marks(length, 0, opEnd, length - tail, length) : marks(length, 0, opEnd, 0, 0));
                }
            }
            saveSeasonMarks(prefix, block);
            if (found || lowest == Integer.MAX_VALUE) {
                return;
            }
            start = order > highest ? start + 30 : start - 30;
        }
    }

    /** The {@code fields} of one id from Tencent's union metadata API (table {@code tid}); null on failure. */
    private static JSONObject tencentUnion(String tid, String id) {
        final JSONArray results = tencentUnionAll(tid, id);
        final JSONObject first = results == null ? null : results.optJSONObject(0);
        return first == null ? null : first.optJSONObject("fields");
    }

    /** Every result for comma-separated {@code ids} from the union API; null when the request failed. */
    private static JSONArray tencentUnionAll(String tid, String ids) {
        final String body = execute(new Request.Builder()
                .url(HttpUrl.parse(SegmentEndpoints.TENCENT_UNION).newBuilder()
                        .addQueryParameter("otype", "json").addQueryParameter("tid", tid)
                        .addQueryParameter("appid", "20001238")
                        .addQueryParameter("appkey", SegmentEndpoints.TENCENT_UNION_KEY)
                        .addQueryParameter("idlist", ids).build())
                .build());
        try {
            // JSONP: "QZOutputJson={...};"
            return body == null ? null
                    : new JSONObject(body.substring(body.indexOf('{'), body.lastIndexOf('}') + 1))
                            .optJSONArray("results");
        } catch (Exception e) {
            return null;
        }
    }

    /** A JSON POST the way Tencent's web pages send it: with v.qq.com as Referer and Origin, or it refuses. */
    private static JSONObject tencentPost(String url, JSONObject body) {
        final String reply = execute(new Request.Builder()
                .url(url)
                .header("Referer", SegmentEndpoints.TENCENT_REFERER)
                .header("Origin", "https://v.qq.com")
                .post(RequestBody.create(body.toString(), JSON))
                .build());
        try {
            return reply == null ? null : new JSONObject(reply);
        } catch (Exception e) {
            return null;
        }
    }

    /** TMDB's original name for a Chinese series; "" when it is not Chinese, null when TMDB did not answer. */
    private static String chineseName(long tmdbTvId) {
        final JSONObject tv = getJson(HttpUrl.parse(SegmentEndpoints.TMDB_BASE).newBuilder()
                .addPathSegment("tv").addPathSegment(String.valueOf(tmdbTvId))
                .addQueryParameter("api_key", SegmentEndpoints.TMDB_KEY).build());
        if (tv == null) {
            return null;
        }
        final JSONArray countries = tv.optJSONArray("origin_country");
        final boolean chinese = "zh".equals(tv.optString("original_language"))
                || (countries != null && countries.toString().contains("\"CN\""));
        return chinese ? tv.optString("original_name", "") : "";
    }

    /**
     * Which of a platform's entries for a show is TMDB season {@code season}, by what each adds to the
     * show's name: nothing or "第一季" for the first, "第二季" / "第2季" for the rest. A show kept as one
     * long entry (Bilibili's 凡人修仙传, 193 episodes) is taken only when no entry names a season, and then
     * counts straight through. {@code {index, episodes before this season}}; null when none fits or TMDB
     * cannot say where the season starts.
     */
    private static int[] seasonEntry(List<String> rests, long tmdbTvId, int season) {
        int bare = -1;
        boolean anySeasoned = false;
        for (int i = 0; i < rests.size(); i++) {
            final int named = cnSeasonNumber(rests.get(i));
            if (named == season) {
                return new int[]{i, 0};
            }
            anySeasoned |= named > 0;
            if (bare < 0 && rests.get(i).isEmpty()) {
                bare = i;
            }
        }
        if (bare < 0 || (season > 1 && anySeasoned)) {
            return null;
        }
        if (season == 1) {
            return new int[]{bare, 0};
        }
        final int first = tmdbAbsoluteEpisode(tmdbTvId, season, 1);
        return first < 1 ? null : new int[]{bare, first - 1};
    }

    /** "第二季" or "第2季" as 2; 0 for anything else, nothing at all included. */
    private static int cnSeasonNumber(String rest) {
        if (!rest.startsWith("第") || !rest.endsWith("季") || rest.length() < 3) { // 第…季
            return 0;
        }
        final String n = rest.substring(1, rest.length() - 1);
        final int cn = "一二三四五六七八九十".indexOf(n); // 一…十
        if (n.length() == 1 && cn >= 0) {
            return cn + 1;
        }
        return wholeNumber(n);
    }

    /** A whole number, or -1: Bilibili titles an episode "12", a special "SP" or "12.5". */
    private static int wholeNumber(String title) {
        try {
            return Integer.parseInt(title.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // WBI: the search signs its query with a key mixed from two file names the nav call returns. The table
    // and the steps are Bilibili's own, from its web client. The key changes daily; a day-old one is
    // refetched. ponytail: no retry on a rejected signature, the next day's lookup gets a fresh key.
    private static final int[] WBI_MIX = {46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35, 27,
            43, 5, 49, 33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13, 37, 48, 7, 16, 24, 55, 40, 61, 26, 17,
            0, 1, 60, 51, 30, 4, 22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36, 20, 34, 44, 52};
    private static volatile String wbiKey;
    private static volatile long wbiKeyAt;

    private static JSONObject bilibiliSearch(String keyword) {
        if (wbiKey == null || System.currentTimeMillis() - wbiKeyAt > TimeUnit.DAYS.toMillis(1)) {
            final JSONObject nav = getJson(HttpUrl.parse(SegmentEndpoints.BILIBILI_NAV));
            final JSONObject img = nav == null || nav.optJSONObject("data") == null ? null
                    : nav.optJSONObject("data").optJSONObject("wbi_img");
            if (img == null) {
                return null;
            }
            final String raw = wbiName(img.optString("img_url")) + wbiName(img.optString("sub_url"));
            if (raw.length() < 64) {
                return null;
            }
            final StringBuilder key = new StringBuilder();
            for (int i = 0; i < 32; i++) {
                key.append(raw.charAt(WBI_MIX[i]));
            }
            wbiKey = key.toString();
            wbiKeyAt = System.currentTimeMillis();
        }
        try {
            // Keys in order, and the query signed exactly as it is sent.
            final String query = "keyword=" + java.net.URLEncoder.encode(keyword.replaceAll("[!'()*]", ""), "UTF-8")
                    .replace("+", "%20") + "&wts=" + System.currentTimeMillis() / 1000;
            final byte[] md5 = java.security.MessageDigest.getInstance("MD5")
                    .digest((query + wbiKey).getBytes("UTF-8"));
            final StringBuilder rid = new StringBuilder();
            for (byte b : md5) {
                rid.append(String.format(java.util.Locale.US, "%02x", b));
            }
            final String reply = execute(new Request.Builder()
                    .url(SegmentEndpoints.BILIBILI_SEARCH + "?" + query + "&w_rid=" + rid)
                    .header("User-Agent", SegmentEndpoints.BILIBILI_UA)
                    .build());
            return reply == null ? null : new JSONObject(reply);
        } catch (Exception e) {
            return null;
        }
    }

    /** "https://i0.hdslb.com/bfs/wbi/7cd0...077c.png" as "7cd0...077c". */
    private static String wbiName(String url) {
        final String file = url.substring(url.lastIndexOf('/') + 1);
        final int dot = file.indexOf('.');
        return dot < 0 ? file : file.substring(0, dot);
    }

    /** "mm:ss" or "h:mm:ss" in seconds; NaN when it is neither. */
    private static double clockSec(String clock) {
        double sec = 0;
        for (String part : clock.trim().split(":")) {
            try {
                sec = sec * 60 + Integer.parseInt(part);
            } catch (NumberFormatException e) {
                return Double.NaN;
            }
        }
        return clock.contains(":") ? sec : Double.NaN;
    }

    /** |baseDuration − file length|; 0 while the length is unknown, when no entry is nearer than another. */
    private static double animeSkipDelta(JSONObject ep, double durationSec) {
        return durationSec > 0 ? Math.abs(ep.optDouble("baseDuration", 0) - durationSec) : 0;
    }

    /** One Anime Skip GraphQL query; the array under {@code data.<field>}, null on any failure. */
    private static JSONArray animeSkipQuery(String query, String field) {
        final JSONObject body = new JSONObject();
        try {
            body.put("query", "{ " + query + " }");
        } catch (JSONException e) {
            return null;
        }
        final String reply = execute(new Request.Builder()
                .url(SegmentEndpoints.ANIMESKIP)
                .header("Accept", "application/json")
                .header("X-Client-ID", SegmentEndpoints.ANIMESKIP_CLIENT_ID)
                .post(RequestBody.create(body.toString(), JSON))
                .build());
        try {
            return new JSONObject(reply).getJSONObject("data").getJSONArray(field);
        } catch (Exception e) {
            return null; // null reply, GraphQL error, or nothing by that id
        }
    }

    /** Anime Skip type name to category; null for what is not skipped (Canon, Filler, Title Card...). */
    private static SkipSegment.Category animeSkipCategory(JSONObject mark) {
        final JSONObject type = mark.optJSONObject("type");
        final String t = type == null ? "" : type.optString("name").toLowerCase(Locale.US);
        if (t.contains("intro")) {
            return SkipSegment.Category.INTRO;
        }
        if (t.contains("recap")) {
            return SkipSegment.Category.RECAP;
        }
        if (t.contains("credits")) {
            return SkipSegment.Category.CREDITS;
        }
        if (t.contains("preview")) {
            return SkipSegment.Category.PREVIEW;
        }
        return null;
    }

    /**
     * Folds one skip-times response into {@code best}, keeping per category the submission recorded
     * closest to {@code durationSec}. Null (404 / any failure) simply contributes nothing.
     */
    private static void collectCuts(Map<SkipSegment.Category, JSONObject> best, JSONArray results,
                                    double durationSec) {
        if (results == null) {
            return;
        }
        for (int i = 0; i < results.length(); i++) {
            final JSONObject r = results.optJSONObject(i);
            if (r == null || r.optJSONObject("interval") == null) {
                continue;
            }
            final SkipSegment.Category cat = aniskipCategory(r.optString("skipType", ""));
            final JSONObject prev = best.get(cat);
            if (prev == null || cutDelta(r, durationSec) < cutDelta(prev, durationSec)) {
                best.put(cat, r);
            }
        }
    }

    /** |episodeLength − file length|; 0 when the length is unknown — no cut is then closer than another. */
    private static double cutDelta(JSONObject result, double durationSec) {
        return durationSec > 0 ? Math.abs(result.optDouble("episodeLength", 0) - durationSec) : 0;
    }

    /** {@code GET /v2/skip-times/{mal}/{ep}}; the results array, or null on 404 / any failure. */
    private static JSONArray aniskipTimes(long malId, int episode, double episodeLength) {
        final HttpUrl url = HttpUrl.parse(SegmentEndpoints.ANISKIP).newBuilder()
                .addPathSegment(String.valueOf(malId))
                .addPathSegment(String.valueOf(episode))
                .addQueryParameter("types", "op")
                .addQueryParameter("types", "ed")
                .addQueryParameter("types", "recap")
                .addQueryParameter("episodeLength", String.valueOf(Math.max(0, episodeLength)))
                .build();
        final String body = execute(new Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .cacheControl(ANISKIP_STALE)
                .build());
        try {
            return body != null ? new JSONObject(body).optJSONArray("results") : null;
        } catch (JSONException e) {
            return null;
        }
    }

    private static SkipSegment.Category aniskipCategory(String skipType) {
        final String t = skipType.toLowerCase(Locale.US);
        if (t.contains("recap")) {
            return SkipSegment.Category.RECAP;
        }
        if (t.contains("op")) {
            return SkipSegment.Category.INTRO;
        }
        if (t.contains("ed")) {
            return SkipSegment.Category.CREDITS;
        }
        return SkipSegment.Category.UNKNOWN;
    }

    /**
     * TheIntroDB (tmdb-keyed): fetch intro/recap/credits arrays. Uses {@code knownTmdbId} when the
     * caller already has it (from the intent); otherwise resolves it lazily via TMDB find from the imdb id.
     */
    private static Scored theIntroDb(String imdbId, long knownTmdbId, int season, int episode,
                                     boolean isMovie) {
        final long tmdbId = knownTmdbId >= 0 ? knownTmdbId : tmdbFind(imdbId, isMovie);
        final List<SkipSegment> out = new ArrayList<>();
        if (tmdbId < 0) {
            return new Scored(out, 0);
        }
        final HttpUrl.Builder url = HttpUrl.parse(SegmentEndpoints.THEINTRODB).newBuilder()
                .addQueryParameter("tmdb_id", String.valueOf(tmdbId));
        if (!isMovie) {
            url.addQueryParameter("season", String.valueOf(season));
            url.addQueryParameter("episode", String.valueOf(episode));
        }
        final JSONObject root = getJson(url.build());
        if (root == null) {
            return new Scored(out, 0);
        }
        addMsArray(out, root.optJSONArray("intro"), SkipSegment.Category.INTRO, SkipSegment.CoordBase.ABSOLUTE, TT_ABS);
        addMsArray(out, root.optJSONArray("recap"), SkipSegment.Category.RECAP, SkipSegment.CoordBase.ABSOLUTE, TT_ABS);
        addMsArray(out, root.optJSONArray("credits"), SkipSegment.Category.CREDITS, SkipSegment.CoordBase.ABSOLUTE, TT_ABS);
        // No per-result confidence field; a fixed medium-high signal (matches SkipDB on tested titles).
        return new Scored(out, 0.8);
    }

    /**
     * IntroHater community DB (imdb-keyed): {@code GET /segments/{imdb[:season:episode]}} with the
     * public {@code x-api-key}. Returns an array of {start,end,label,votes,verified} in seconds.
     */
    private static Scored introHater(String imdbId, int season, int episode) {
        final boolean isEpisode = season >= 1 && episode >= 1;
        final String videoId = isEpisode ? imdbId + ":" + season + ":" + episode : imdbId;
        final List<SkipSegment> out = new ArrayList<>();
        final HttpUrl url = HttpUrl.parse(SegmentEndpoints.INTROHATER + videoId);
        if (url == null) {
            return new Scored(out, 0);
        }
        final Request request = new Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("x-api-key", SegmentEndpoints.INTROHATER_KEY)
                .build();
        final String body = execute(request);
        if (body == null) {
            return new Scored(out, 0);
        }
        final JSONArray array;
        try {
            array = new JSONArray(body);
        } catch (Exception e) {
            return new Scored(out, 0);
        }
        double signal = 0;
        for (int i = 0; i < array.length(); i++) {
            final JSONObject o = array.optJSONObject(i);
            if (o == null) {
                continue;
            }
            final SkipSegment.Category cat = introHaterCategory(o.optString("label", ""));
            addSeg(out, o.optDouble("start", Double.NaN), o.optDouble("end", Double.NaN),
                    cat, SkipSegment.CoordBase.ABSOLUTE, TT_ABS);
            final double s = (o.optBoolean("verified", false) ? 0.5 : 0.3)
                    + Math.min(0.3, o.optInt("votes", 0) * 0.1);
            signal = Math.max(signal, s);
        }
        return new Scored(out, signal);
    }

    private static SkipSegment.Category introHaterCategory(String label) {
        final String l = label.toLowerCase(Locale.US);
        if (l.contains("recap")) {
            return SkipSegment.Category.RECAP;
        }
        if (l.contains("credit") || l.contains("outro") || l.contains("ending")) {
            return SkipSegment.Category.CREDITS;
        }
        if (l.contains("preview") || l.contains("next")) {
            return SkipSegment.Category.PREVIEW;
        }
        if (l.contains("intro") || l.contains("opening")) {
            return SkipSegment.Category.INTRO;
        }
        return SkipSegment.Category.UNKNOWN;
    }

    /** imdb → tmdb id via TMDB find. Also used by the online subtitle search, whose sources take
     *  one id or the other and never the same one. */
    public static long tmdbFind(String imdbId, boolean isMovie) {
        final HttpUrl url = HttpUrl.parse(SegmentEndpoints.TMDB_FIND).newBuilder()
                .addPathSegment(imdbId)
                .addQueryParameter("api_key", SegmentEndpoints.TMDB_KEY)
                .addQueryParameter("external_source", "imdb_id")
                .build();
        final JSONObject root = getJson(url);
        if (root == null) {
            return -1;
        }
        final JSONArray results = root.optJSONArray(isMovie ? "movie_results" : "tv_results");
        if (results == null || results.length() == 0) {
            return -1;
        }
        final JSONObject first = results.optJSONObject(0);
        return first != null ? first.optLong("id", -1) : -1;
    }

    /** tmdb → imdb id via TMDB external_ids (movie or tv). Returns null when unavailable. */
    /** tmdb → imdb id. Public for the same reason as {@link #tmdbFind}. */
    public static String tmdbExternalImdb(long tmdbId, boolean isMovie) {
        final HttpUrl url = HttpUrl.parse(SegmentEndpoints.TMDB_BASE).newBuilder()
                .addPathSegment(isMovie ? "movie" : "tv")
                .addPathSegment(String.valueOf(tmdbId))
                .addPathSegment("external_ids")
                .addQueryParameter("api_key", SegmentEndpoints.TMDB_KEY)
                .build();
        final JSONObject root = getJson(url);
        if (root == null) {
            return null;
        }
        // isNull first: optString stringifies a JSON null into the word "null", and TMDB leaves
        // imdb_id null for plenty of titles.
        if (root.isNull("imdb_id")) {
            return null;
        }
        final String imdbId = root.optString("imdb_id", null);
        return (imdbId != null && !imdbId.isEmpty()) ? imdbId : null;
    }

    // ---- Normalization -----------------------------------------------------------------------

    /** Add a segment from an object carrying {start_ms,end_ms}. */
    private static void addMs(List<SkipSegment> out, JSONObject o, SkipSegment.Category category,
                              SkipSegment.CoordBase coordBase, int timeTrust) {
        if (o == null) {
            return;
        }
        addSeg(out, o.optDouble("start_ms", Double.NaN) / 1000.0,
                o.optDouble("end_ms", Double.NaN) / 1000.0, category, coordBase, timeTrust);
    }

    /** Add a segment from an object carrying {start_sec,end_sec} (falling back to *_ms). */
    private static void addSecObject(List<SkipSegment> out, JSONObject o, SkipSegment.Category category,
                                     int timeTrust) {
        if (o == null) {
            return;
        }
        double start = o.optDouble("start_sec", Double.NaN);
        double end = o.optDouble("end_sec", Double.NaN);
        if (Double.isNaN(start)) {
            start = o.optDouble("start_ms", Double.NaN) / 1000.0;
        }
        if (Double.isNaN(end)) {
            end = o.optDouble("end_ms", Double.NaN) / 1000.0;
        }
        addSeg(out, start, end, category, SkipSegment.CoordBase.ABSOLUTE, timeTrust);
    }

    /**
     * Add segments from an array of {start_ms,end_ms}. A null {@code end_ms} means open-ended; that is
     * only meaningful for end credits (which run to the file end), so an open-ended non-credits segment
     * is dropped rather than left spanning most of the file.
     */
    private static void addMsArray(List<SkipSegment> out, JSONArray array, SkipSegment.Category category,
                                   SkipSegment.CoordBase coordBase, int timeTrust) {
        if (array == null) {
            return;
        }
        for (int i = 0; i < array.length(); i++) {
            final JSONObject o = array.optJSONObject(i);
            if (o == null) {
                continue;
            }
            final double start = o.optDouble("start_ms", Double.NaN) / 1000.0;
            final double endMs = o.optDouble("end_ms", Double.NaN);
            final boolean openEnded = Double.isNaN(endMs);
            if (openEnded && category != SkipSegment.Category.CREDITS) {
                continue; // open-ended only makes sense for credits
            }
            final double end = openEnded ? OPEN_ENDED_SEC : endMs / 1000.0;
            addSeg(out, start, end, category, coordBase, timeTrust);
        }
    }

    /** Max {@code submissions} across an array of timestamp objects (0 when absent). */
    private static int maxSubmissions(JSONArray array) {
        if (array == null) {
            return 0;
        }
        int max = 0;
        for (int i = 0; i < array.length(); i++) {
            final JSONObject o = array.optJSONObject(i);
            if (o != null) {
                max = Math.max(max, o.optInt("submissions", 1));
            }
        }
        return max;
    }

    private static void addSeg(List<SkipSegment> out, double startSec, double endSec,
                               SkipSegment.Category category, SkipSegment.CoordBase coordBase, int timeTrust) {
        // A missing start (start_ms: null) means "from the beginning of the file" — common for intro/recap.
        // Symmetric with the open-ended-end handling; excluded for credits, where a file-start segment
        // would span nearly the whole file.
        if (Double.isNaN(startSec) && !Double.isNaN(endSec)
                && category != SkipSegment.Category.CREDITS) {
            startSec = 0;
        }
        // Clamp to avoid NaN-duration issues on the first HLS timeupdate (see FIND_INTO.MD §2) — before the
        // check, not after: a take ending inside the first second (0-0.8) would otherwise come out as 1-0.8,
        // and a cluster of nothing but backwards spans leaves bestCluster without a representative (a crash
        // in 2.0.3).
        if (startSec < 1) {
            startSec = 1;
        }
        if (Double.isNaN(startSec) || Double.isNaN(endSec) || endSec <= startSec) {
            return;
        }
        out.add(new SkipSegment(startSec, endSec, SkipSegment.Type.SKIP, category, coordBase, timeTrust));
    }

    // ---- HTTP --------------------------------------------------------------------------------

    private static JSONObject getJson(HttpUrl url) {
        final String body = get(url);
        if (body == null) {
            return null;
        }
        try {
            return new JSONObject(body);
        } catch (Exception e) {
            return null;
        }
    }

    private static JSONArray getJsonArray(HttpUrl url) {
        final String body = get(url);
        if (body == null) {
            return null;
        }
        try {
            return new JSONArray(body);
        } catch (Exception e) {
            return null;
        }
    }

    private static String get(HttpUrl url) {
        if (url == null) {
            return null;
        }
        return execute(new Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build());
    }

    /** POST a JSON array body, sending {@code userAgent} (some APIs gate on it), and parse the array reply. */
    private static JSONArray postJsonArray(String url, JSONArray body, String userAgent) {
        final HttpUrl httpUrl = HttpUrl.parse(url);
        if (httpUrl == null) {
            return null;
        }
        final Request request = new Request.Builder()
                .url(httpUrl)
                .header("Accept", "application/json")
                .header("User-Agent", userAgent)
                .post(RequestBody.create(body.toString(), JSON))
                .build();
        final String reply = execute(request);
        if (reply == null) {
            return null;
        }
        try {
            return new JSONArray(reply);
        } catch (Exception e) {
            return null;
        }
    }

    /** Executes a prepared request, returning the body string or null on any non-2xx / failure. */
    private static String execute(Request request) {
        try (Response response = CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                // 404 is how several sources say "not in the database" — not worth a line.
                if (response.code() != 404) {
                    Utils.log(request.url().host() + ": " + response.code());
                }
                return null;
            }
            final ResponseBody body = response.body();
            return body != null ? body.string() : null;
        } catch (Exception e) {
            // Timeout / Cloudflare / offline yield nothing. An interrupt is the probe deadline or the next item
            // cancelling the call: traced() reports that once per source. OkHttp surfaces it as an
            // InterruptedIOException with the flag still set — or, from the connect phase, as a bare
            // InterruptedException that has cleared it, so the flag is put back for traced() to see.
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            } else if (!Thread.currentThread().isInterrupted()) {
                Utils.log(request.url().host() + ": " + e);
            }
            return null;
        }
    }
}
