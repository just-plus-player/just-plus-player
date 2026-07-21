package com.brouken.player.skip;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Fetches intro/recap/outro skip segments for one movie or one series episode, keyed only by stable
 * ids (imdb, tmdb, and season/episode for series) — no title search.
 *
 * <p>Sources are queried in trust order and their results are <b>quality-scored</b>
 * ({@code score = trust × signal}); the best-scoring result wins rather than simply the first
 * non-empty one, so a weak early source (a single-submission guess) cannot shadow a precise later
 * one. Cost is bounded by two stop rules: a strong hit ({@code score ≥ ACCEPT}) returns immediately,
 * and empty responses never stop the search while at most {@link #NONEMPTY_BUDGET} non-empty results
 * are weighed before settling (see {@code SKIPME_SOURCE_PLAN.md}).
 *
 * <p>Runs on a background thread; the callback fires on that same worker thread (the caller marshals
 * to the UI thread). Results (including "nothing found") are cached in memory for the process
 * lifetime, keyed by {@code imdb|tmdb|season|episode}. On any error or timeout a source yields
 * nothing — playback is never affected.
 */
public final class SegmentFinder {

    /** Receives the resolved skip segments (possibly empty) on the worker thread. */
    public interface Callback {
        void onSegments(List<SkipSegment> segments);
    }

    private SegmentFinder() {}

    private static final int TIMEOUT_SEC = 5;
    private static final double OPEN_ENDED_SEC = 99999; // TheIntroDB null end → open-ended

    /** A result with {@code score ≥ ACCEPT} is accepted immediately, ending the probe loop. */
    private static final double ACCEPT = 0.60;
    /**
     * Max <b>non-empty</b> results to weigh before settling on the best. Empty responses never count
     * and never stop the search — they are the cheap negatives fallbacks exist for, so the ladder is
     * walked until a source has data (up to the whole list). This cap only bounds how many weak
     * (below-{@link #ACCEPT}) candidates we gather before picking the highest-scoring one.
     */
    private static final int NONEMPTY_BUDGET = 3;

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SEC, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SEC, TimeUnit.SECONDS)
            .callTimeout(TIMEOUT_SEC + 1, TimeUnit.SECONDS)
            .build();

    // Process-lifetime cache; empty results are cached too, to avoid re-hitting dead titles.
    private static final Map<String, List<SkipSegment>> CACHE = new ConcurrentHashMap<>();

    /**
     * Starts an async lookup. Returns the worker {@link Thread} so the caller can {@code interrupt()}
     * it when the media item changes. The callback is not invoked if the thread was interrupted.
     */
    public static Thread find(String imdbId, String tmdbId, int season, int episode, double durationSec,
                              Callback callback) {
        final Thread thread = new Thread(() -> {
            final List<SkipSegment> result = lookup(imdbId, tmdbId, season, episode, durationSec);
            if (!Thread.currentThread().isInterrupted()) {
                callback.onSegments(result);
            }
        }, "SegmentFinder");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private static List<SkipSegment> lookup(String imdbIdIn, String tmdbIdIn, int season, int episode,
                                            double durationSec) {
        final String imdbInput = isBlank(imdbIdIn) ? null : imdbIdIn;
        final String tmdbInput = isBlank(tmdbIdIn) ? null : tmdbIdIn;
        if (imdbInput == null && tmdbInput == null) {
            return new ArrayList<>();
        }
        // Season/episode presence is what distinguishes a series episode from a movie (per the intent).
        final boolean isMovie = season < 1 || episode < 1;
        final int keySeason = isMovie ? -1 : season;
        final int keyEpisode = isMovie ? -1 : episode;
        final String key = (imdbInput != null ? imdbInput : "") + "|"
                + (tmdbInput != null ? tmdbInput : "") + "|" + keySeason + "|" + keyEpisode;
        final List<SkipSegment> cached = CACHE.get(key);
        if (cached != null) {
            return cached;
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
        final List<Candidate> candidates = new ArrayList<>();
        if (isMovie) {
            if (imdb != null) {
                candidates.add(new Candidate(1.0, () -> skipDb(imdb, -1, -1, durationSec)));
            }
            if (imdb != null || tmdb >= 0) {
                candidates.add(new Candidate(0.8, () -> theIntroDb(imdb, tmdb, -1, -1, true)));
                // SkipMe.db is demoted to the tail: its server-side duration shift is erratic and can
                // return times offset from the actual encode. Kept only as a broad last resort.
                candidates.add(new Candidate(0.5, () -> skipMe(imdb, tmdb, -1, -1, durationSec)));
            }
            if (imdb != null) {
                candidates.add(new Candidate(0.4, () -> introHater(imdb, -1, -1)));
            }
        } else {
            if (imdb != null) {
                // Anime first: Aniskip is the anime-specialist (crowd-voted OP/ED, per-MAL episode). It
                // returns empty fast for non-anime (arm finds no MAL), so the ladder falls through.
                candidates.add(new Candidate(0.9, () -> aniskip(imdb, season, episode)));
                candidates.add(new Candidate(1.0, () -> skipDb(imdb, season, episode, durationSec)));
                candidates.add(new Candidate(0.7, () -> introDbApp(imdb, season, episode)));
            }
            if (imdb != null || tmdb >= 0) {
                candidates.add(new Candidate(0.8, () -> theIntroDb(imdb, tmdb, season, episode, false)));
                // SkipMe.db demoted to the tail (erratic duration shift → offset times).
                candidates.add(new Candidate(0.5, () -> skipMe(imdb, tmdb, season, episode, durationSec)));
            }
            if (imdb != null) {
                candidates.add(new Candidate(0.4, () -> introHater(imdb, season, episode)));
            }
        }

        final List<SkipSegment> result = bestScored(candidates);
        CACHE.put(key, result);
        return result;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    // ---- Quality-aware selection -------------------------------------------------------------

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

    private interface Step {
        Scored run();
    }

    /** A queryable source: its base {@code trust} weight paired with the {@link Step} that runs it. */
    private static final class Candidate {
        final double trust;
        final Step step;

        Candidate(double trust, Step step) {
            this.trust = trust;
            this.step = step;
        }
    }

    /**
     * Queries candidates in order, returning the segments of the highest-scoring result
     * ({@code trust × signal}). Stops early on a strong hit ({@code score ≥ ACCEPT}); empty responses
     * never stop the search, and at most {@link #NONEMPTY_BUDGET} non-empty results are weighed before
     * settling. Candidates lacking the required id are omitted upstream.
     */
    private static List<SkipSegment> bestScored(List<Candidate> candidates) {
        Scored best = null;
        double bestScore = -1;
        int hits = 0;
        for (Candidate candidate : candidates) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }
            final Scored result = candidate.step.run();
            if (result == null || result.isEmpty()) {
                continue; // empty doesn't consume the budget — keep looking down the ladder
            }
            final double score = candidate.trust * result.signal;
            if (score > bestScore) {
                bestScore = score;
                best = result;
            }
            if (score >= ACCEPT) {
                break; // strong hit — good enough
            }
            if (++hits >= NONEMPTY_BUDGET) {
                break; // gathered enough weak candidates; settle on the best so far
            }
        }
        return best != null ? best.segments : new ArrayList<>();
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
        addMs(out, intro);
        addMs(out, recap);
        addMs(out, outro);
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
        addSecObject(out, intro);
        addSecObject(out, recap);
        addSecObject(out, outro);
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
        addMsArray(out, intro);
        addMsArray(out, recap);
        addMsArray(out, credits);
        // Signal: min(1, maxSubmissions/5) — 5+ submissions is treated as fully trusted.
        final int maxSub = Math.max(maxSubmissions(intro),
                Math.max(maxSubmissions(recap), maxSubmissions(credits)));
        return new Scored(out, Math.min(1.0, maxSub / 5.0));
    }

    /** Anime gate: arm (imdb→MAL for the season), then Aniskip (MAL-relative episode). */
    private static Scored aniskip(String imdbId, int season, int episode) {
        final List<SkipSegment> out = new ArrayList<>();
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
                malId = entry.optLong("myanimelist", -1); // cand[0] — first match for the season
                break;
            }
        }
        if (malId < 0) {
            return new Scored(out, 0); // not anime (or no MAL for this season) → fall through
        }
        final HttpUrl skipUrl = HttpUrl.parse(SegmentEndpoints.ANISKIP).newBuilder()
                .addPathSegment(String.valueOf(malId))
                .addPathSegment(String.valueOf(episode))
                .addQueryParameter("types", "op")
                .addQueryParameter("types", "ed")
                .addQueryParameter("types", "recap")
                .addQueryParameter("episodeLength", "0")
                .build();
        final JSONObject root = getJson(skipUrl);
        if (root == null) {
            return new Scored(out, 0);
        }
        final JSONArray results = root.optJSONArray("results");
        if (results == null) {
            return new Scored(out, 0);
        }
        for (int i = 0; i < results.length(); i++) {
            final JSONObject r = results.optJSONObject(i);
            if (r == null) {
                continue;
            }
            final JSONObject interval = r.optJSONObject("interval");
            if (interval == null) {
                continue;
            }
            addSeg(out, interval.optDouble("startTime", Double.NaN), interval.optDouble("endTime", Double.NaN));
        }
        // Crowd-voted with real op/ed ends — a high, fixed signal for anime.
        return new Scored(out, 0.9);
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
        addMsArray(out, root.optJSONArray("intro"));
        addMsArray(out, root.optJSONArray("recap"));
        addMsArray(out, root.optJSONArray("credits"));
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
            addSeg(out, o.optDouble("start", Double.NaN), o.optDouble("end", Double.NaN));
            final double s = (o.optBoolean("verified", false) ? 0.5 : 0.3)
                    + Math.min(0.3, o.optInt("votes", 0) * 0.1);
            signal = Math.max(signal, s);
        }
        return new Scored(out, signal);
    }

    /** imdb → tmdb id via TMDB find (lazy; only reached from the TheIntroDB step). */
    private static long tmdbFind(String imdbId, boolean isMovie) {
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
    private static String tmdbExternalImdb(long tmdbId, boolean isMovie) {
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
    private static void addMs(List<SkipSegment> out, JSONObject o) {
        if (o == null) {
            return;
        }
        addSeg(out, o.optDouble("start_ms", Double.NaN) / 1000.0, o.optDouble("end_ms", Double.NaN) / 1000.0);
    }

    /** Add a segment from an object carrying {start_sec,end_sec} (falling back to *_ms). */
    private static void addSecObject(List<SkipSegment> out, JSONObject o) {
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
        addSeg(out, start, end);
    }

    /** Add segments from an array of {start_ms,end_ms} (null end_ms → open-ended). */
    private static void addMsArray(List<SkipSegment> out, JSONArray array) {
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
            final double end = Double.isNaN(endMs) ? OPEN_ENDED_SEC : endMs / 1000.0;
            addSeg(out, start, end);
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

    private static void addSeg(List<SkipSegment> out, double startSec, double endSec) {
        if (Double.isNaN(startSec) || Double.isNaN(endSec) || endSec <= startSec) {
            return;
        }
        // Clamp to avoid NaN-duration issues on the first HLS timeupdate (see FIND_INTO.MD §2).
        if (startSec < 1) {
            startSec = 1;
        }
        out.add(new SkipSegment(startSec, endSec, SkipSegment.Type.SKIP));
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
