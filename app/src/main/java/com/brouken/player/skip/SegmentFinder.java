package com.brouken.player.skip;

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
     * still counts as a vote, but must never supply the cluster's timing, however much its source is
     * trusted on timing in general.
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
    private static final int TT_ABS = SkipSegment.TIME_TRUST_ABSOLUTE;                // 100

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
     * IntroHater is off: introhater.com answers "This service has been suspended by its owner" to every
     * request — an HTML body that parses to nothing, for a ~280 ms round-trip and one probe slot per
     * lookup. Left wired up rather than deleted, so re-enabling is this one flag if it ever comes back.
     */
    private static final boolean INTROHATER_ENABLED = false;

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    /**
     * Disk budget for the HTTP cache. The bodies are small JSON documents, so this is roomy for the id
     * resolutions and every segment reply a long binge produces.
     */
    private static final long HTTP_CACHE_BYTES = 1024 * 1024;

    /** Replaced once by {@link #setCacheDir} with the same client plus a cache; volatile for that swap. */
    private static volatile OkHttpClient CLIENT = new OkHttpClient.Builder()
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
        final Thread thread = new Thread(
                () -> lookup(imdbId, tmdbId, season, episode, durationSec, new Emitter(callback)),
                "SegmentFinder");
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
        // Cache key includes the duration bucket: duration-aware sources adapt to the stream length,
        // so a replay of a differently-cut rip must not reuse stale timings.
        final long durationBucket = durationSec > 0 ? Math.round(durationSec) : -1;
        final String key = (imdbInput != null ? imdbInput : "") + "|"
                + (tmdbInput != null ? tmdbInput : "") + "|" + keySeason + "|" + keyEpisode
                + "|" + durationBucket;
        final List<SkipSegment> cached = getCached(key);
        if (cached != null) {
            emitter.emit(cached);
            return;
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
        }

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
                steps.add(() -> skipDb(imdb, -1, -1, durationSec));
            }
            if (imdb != null || tmdb >= 0) {
                if (durationKnown) {
                    steps.add(() -> skipMe(imdb, tmdb, -1, -1, durationSec));
                }
                steps.add(() -> theIntroDb(imdb, tmdb, -1, -1, true));
            }
            if (imdb != null && INTROHATER_ENABLED) {
                steps.add(() -> introHater(imdb, -1, -1));
            }
        } else {
            if (imdb != null) {
                // Aniskip is the anime specialist (crowd-voted OP/ED with real ends); for anime files
                // — usually the broadcast cut — its absolute timings match, so it is the primary time
                // source (TT_ANIME). It returns empty fast for non-anime, so it simply drops out then.
                steps.add(() -> aniskip(imdb, season, ep, durationSec));
                if (durationKnown) {
                    steps.add(() -> skipDb(imdb, season, ep, durationSec));
                    steps.add(() -> skipMe(imdb, tmdb, season, ep, durationSec));
                }
                steps.add(() -> introDbApp(imdb, season, ep));
            }
            if (imdb != null || tmdb >= 0) {
                steps.add(() -> theIntroDb(imdb, tmdb, season, ep, false));
            }
            if (imdb != null && INTROHATER_ENABLED) {
                steps.add(() -> introHater(imdb, season, ep));
            }
        }

        if (steps.isEmpty()) {
            return; // nothing in this profile can be asked with the ids that resolved
        }
        final List<Scored> results = probeAll(steps, emitter);
        if (Thread.currentThread().isInterrupted()) {
            return; // media changed mid-probe — don't deliver or cache
        }
        final List<SkipSegment> result = voteSegments(results);
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
     * segment (a fresh copy with {@code confirmed} set), or null if there are no votes. Winner =
     * most distinct sources, then highest {@code timeTrust}, then highest signal, then earliest.
     */
    private static SkipSegment bestCluster(List<Vote> votes) {
        if (votes.isEmpty()) {
            return null;
        }
        Collections.sort(votes, (a, b) -> Double.compare(a.seg.startSec, b.seg.startSec));

        SkipSegment bestSeg = null;
        int bestSources = -1;
        int bestTrust = -1;
        double bestSignal = -1;

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
            for (Vote v : cluster) {
                sources.add(v.sourceId);
                if (v.seg.endSec - v.seg.startSec < longest * TRUNCATED_TAKE_RATIO) {
                    continue;
                }
                if (rep == null || v.seg.timeTrust > rep.seg.timeTrust
                        || (v.seg.timeTrust == rep.seg.timeTrust && v.signal > rep.signal)) {
                    rep = v;
                }
            }
            final int distinctSources = sources.size();

            final boolean better = distinctSources > bestSources
                    || (distinctSources == bestSources && rep.seg.timeTrust > bestTrust)
                    || (distinctSources == bestSources && rep.seg.timeTrust == bestTrust
                        && rep.signal > bestSignal);
            if (better) {
                bestSources = distinctSources;
                bestTrust = rep.seg.timeTrust;
                bestSignal = rep.signal;
                final SkipSegment src = rep.seg;
                final SkipSegment kept = new SkipSegment(src.startSec, src.endSec, src.type,
                        src.category, src.coordBase, src.timeTrust);
                kept.confirmed = distinctSources >= MIN_VOTES;
                bestSeg = kept;
            }
        }
        return bestSeg;
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

    /** IntroDB.app: {intro,recap,outro}, each null or {start_sec,end_sec,confidence,submission_count}. */
    private static Scored introDbApp(String imdbId, int season, int episode) {
        final HttpUrl url = HttpUrl.parse(SegmentEndpoints.INTRODB).newBuilder()
                .addQueryParameter("imdb_id", imdbId)
                .addQueryParameter("season", String.valueOf(season))
                .addQueryParameter("episode", String.valueOf(episode))
                .build();
        final List<SkipSegment> out = new ArrayList<>();
        final JSONObject root = getJson(url);
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
    private static Scored aniskip(String imdbId, int season, int episode, double durationSec) {
        final List<SkipSegment> out = new ArrayList<>();
        if (episode < 1) {
            return new Scored(out, 0); // Aniskip is per-episode; nothing to ask without one
        }
        // arm — note: never pass ?include= (it drops the -season fields).
        final HttpUrl armUrl = HttpUrl.parse(SegmentEndpoints.ARM).newBuilder()
                .addQueryParameter("id", imdbId)
                .build();
        final JSONArray entries = getJsonArray(armUrl);
        if (entries == null) {
            return new Scored(out, 0);
        }
        long malId = -1;
        for (int i = 0; i < entries.length(); i++) {
            final JSONObject entry = entries.optJSONObject(i);
            if (entry == null || entry.isNull("myanimelist")) {
                continue;
            }
            int entrySeason = -1;
            if (!entry.isNull("themoviedb-season")) {
                entrySeason = entry.optInt("themoviedb-season", -1);
            } else if (!entry.isNull("thetvdb-season")) {
                entrySeason = entry.optInt("thetvdb-season", -1);
            }
            if (entrySeason == season) {
                malId = entry.optLong("myanimelist", -1); // first MAL match for the season
                break;
            }
        }
        if (malId < 0) {
            return new Scored(out, 0); // not anime (or no MAL for this season) → drop out
        }
        // Both probes at once. The lower one is what surfaces a cut shorter than this file, and issuing
        // it only after the first has answered would put a second round-trip on the critical path — with
        // arm already resolving ahead of them, that made Aniskip the slowest step of the profile by far.
        // Asking unconditionally also stops a segment submitted only for a neighbouring cut from being
        // missed whenever the first probe happens to answer for ours.
        final Map<SkipSegment.Category, JSONObject> best = new LinkedHashMap<>();
        if (durationSec > 0) {
            final long mal = malId;
            final JSONArray[] lower = new JSONArray[1];
            final Thread lowerProbe = new Thread(
                    () -> lower[0] = aniskipTimes(mal, episode, durationSec - CUT_PROBE_SHIFT_SEC),
                    "SegmentSource-aniskip-lower");
            lowerProbe.setDaemon(true);
            lowerProbe.start();
            final JSONArray atLength = aniskipTimes(mal, episode, durationSec);
            try {
                lowerProbe.join(); // bounded by the client's own call timeout
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // media changed — go with whatever landed
            }
            // At-length first: on an equal distance to the file it keeps the entry Aniskip itself chose.
            collectCuts(best, atLength, durationSec);
            collectCuts(best, lower[0], durationSec);
        }
        if (best.isEmpty()) {
            // Nothing recorded near this length — 0 is the documented "any length" wildcard, and the
            // early wave's only option.
            collectCuts(best, aniskipTimes(malId, episode, 0), durationSec);
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
        final JSONObject root = getJson(url);
        return root != null ? root.optJSONArray("results") : null;
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
        if (Double.isNaN(startSec) || Double.isNaN(endSec) || endSec <= startSec) {
            return;
        }
        // Clamp to avoid NaN-duration issues on the first HLS timeupdate (see FIND_INTO.MD §2).
        if (startSec < 1) {
            startSec = 1;
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
                return null;
            }
            final ResponseBody body = response.body();
            return body != null ? body.string() : null;
        } catch (Exception e) {
            // Timeout / 4xx-5xx / Cloudflare 403 / offline — silently yield nothing.
            return null;
        }
    }
}
