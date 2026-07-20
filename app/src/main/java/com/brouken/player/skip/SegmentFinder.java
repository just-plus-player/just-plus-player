package com.brouken.player.skip;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Fetches intro/recap/outro skip segments for one movie or one series episode, keyed only by stable
 * ids (imdb, and season/episode for series) — no title search. Implements the ordered fallback
 * ladder from {@code FIND_INTO.MD}: the first source that returns a non-empty result wins.
 *
 * <p>Runs on a background thread; the callback fires on that same worker thread (the caller marshals
 * to the UI thread). Results (including "nothing found") are cached in memory for the process
 * lifetime, keyed by {@code imdb|season|episode}. On any error or timeout a source yields nothing —
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
        // imdb-keyed sources (SkipDB, IntroDB.app, Aniskip) can still be used. If that fails we fall
        // back to TheIntroDB, which is queried by tmdb id directly.
        String imdbId = imdbInput;
        if (imdbId == null && tmdbNumeric >= 0) {
            imdbId = tmdbExternalImdb(tmdbNumeric, isMovie);
        }

        final String imdb = imdbId;      // effectively final for the step lambdas
        final long tmdb = tmdbNumeric;
        List<SkipSegment> result;
        if (isMovie) {
            result = firstNonEmpty(
                    () -> imdb != null ? skipDb(imdb, -1, -1, durationSec) : null,
                    () -> theIntroDb(imdb, tmdb, -1, -1, true));
        } else {
            result = firstNonEmpty(
                    () -> imdb != null ? skipDb(imdb, season, episode, durationSec) : null,
                    () -> imdb != null ? introDbApp(imdb, season, episode) : null,
                    () -> imdb != null ? aniskip(imdb, season, episode) : null,
                    () -> theIntroDb(imdb, tmdb, season, episode, false));
        }
        if (result == null) {
            result = new ArrayList<>();
        }
        CACHE.put(key, result);
        return result;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private interface Step {
        List<SkipSegment> run();
    }

    @SafeVarargs
    private static List<SkipSegment> firstNonEmpty(Step... steps) {
        for (Step step : steps) {
            if (Thread.currentThread().isInterrupted()) {
                return new ArrayList<>();
            }
            final List<SkipSegment> segments = step.run();
            if (segments != null && !segments.isEmpty()) {
                return segments;
            }
        }
        return new ArrayList<>();
    }

    // ---- Sources -----------------------------------------------------------------------------

    /** SkipDB: {segments:{intro,recap,outro,preview}}, each null or {start_ms,end_ms}. */
    private static List<SkipSegment> skipDb(String imdbId, int season, int episode, double durationSec) {
        final HttpUrl.Builder url = HttpUrl.parse(SegmentEndpoints.SKIPDB).newBuilder()
                .addQueryParameter("imdb_id", imdbId);
        if (season >= 1 && episode >= 1) {
            url.addQueryParameter("season", String.valueOf(season));
            url.addQueryParameter("episode", String.valueOf(episode));
        }
        if (durationSec > 0) {
            url.addQueryParameter("duration", String.valueOf((long) durationSec));
        }
        final JSONObject root = getJson(url.build());
        final List<SkipSegment> out = new ArrayList<>();
        if (root == null) {
            return out;
        }
        final JSONObject segs = root.optJSONObject("segments");
        if (segs == null) {
            return out;
        }
        addMs(out, segs.optJSONObject("intro"));
        addMs(out, segs.optJSONObject("recap"));
        addMs(out, segs.optJSONObject("outro"));
        return out;
    }

    /** IntroDB.app: {intro,recap,outro}, each null or {start_sec,end_sec,start_ms,end_ms}. */
    private static List<SkipSegment> introDbApp(String imdbId, int season, int episode) {
        final HttpUrl url = HttpUrl.parse(SegmentEndpoints.INTRODB).newBuilder()
                .addQueryParameter("imdb_id", imdbId)
                .addQueryParameter("season", String.valueOf(season))
                .addQueryParameter("episode", String.valueOf(episode))
                .build();
        final JSONObject root = getJson(url);
        final List<SkipSegment> out = new ArrayList<>();
        if (root == null) {
            return out;
        }
        addSecObject(out, root.optJSONObject("intro"));
        addSecObject(out, root.optJSONObject("recap"));
        addSecObject(out, root.optJSONObject("outro"));
        return out;
    }

    /** Anime gate: arm (imdb→MAL for the season), then Aniskip (MAL-relative episode). */
    private static List<SkipSegment> aniskip(String imdbId, int season, int episode) {
        final List<SkipSegment> out = new ArrayList<>();
        // arm — note: never pass ?include= (it drops the -season fields).
        final HttpUrl armUrl = HttpUrl.parse(SegmentEndpoints.ARM).newBuilder()
                .addQueryParameter("id", imdbId)
                .build();
        final JSONArray entries = getJsonArray(armUrl);
        if (entries == null) {
            return out;
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
            return out; // not anime (or no MAL for this season) → fall through
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
            return out;
        }
        final JSONArray results = root.optJSONArray("results");
        if (results == null) {
            return out;
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
        return out;
    }

    /**
     * TheIntroDB (tmdb-keyed): fetch intro/recap/credits arrays. Uses {@code knownTmdbId} when the
     * caller already has it (from the intent); otherwise resolves it lazily via TMDB find from the imdb id.
     */
    private static List<SkipSegment> theIntroDb(String imdbId, long knownTmdbId, int season, int episode,
                                                boolean isMovie) {
        final long tmdbId = knownTmdbId >= 0 ? knownTmdbId : tmdbFind(imdbId, isMovie);
        final List<SkipSegment> out = new ArrayList<>();
        if (tmdbId < 0) {
            return out;
        }
        final HttpUrl.Builder url = HttpUrl.parse(SegmentEndpoints.THEINTRODB).newBuilder()
                .addQueryParameter("tmdb_id", String.valueOf(tmdbId));
        if (!isMovie) {
            url.addQueryParameter("season", String.valueOf(season));
            url.addQueryParameter("episode", String.valueOf(episode));
        }
        final JSONObject root = getJson(url.build());
        if (root == null) {
            return out;
        }
        addMsArray(out, root.optJSONArray("intro"));
        addMsArray(out, root.optJSONArray("recap"));
        addMsArray(out, root.optJSONArray("credits"));
        return out;
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
        final Request request = new Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build();
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
