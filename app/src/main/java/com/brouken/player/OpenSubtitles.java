package com.brouken.player;

import android.net.Uri;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * api.opensubtitles.com v1 — the one source that takes both imdb and tmdb, filters by language
 * server-side, and says of every file whether it is machine-translated. Everything the picker needs
 * arrives in a single search response.
 *
 * Written against the live API rather than its documentation, because three of its rules are
 * invisible until they bite:
 *
 * <ul>
 *   <li>query parameters must be in alphabetical order or the API 301s to the sorted form — which is
 *       what the TreeMap below is for;
 *   <li>{@code imdb_id} wants the bare number: {@code tt14688458} earns another 301;
 *   <li>a request with no recognizable User-Agent is refused outright
 *       ({@code 403 Blocked (kong-user-agent-block)}), so {@link #UA} is not decoration.
 * </ul>
 *
 * Searching is free; only {@code /download} is metered, at 100 a day, and a <em>failed</em> download
 * spends one as well. Hence {@link #link} sends nothing optional: asking for {@code sub_format} is
 * the trap, because format conversion needs a logged-in user and without one the call fails and
 * still costs a unit. Re-requesting a link for a file already fetched today is free.
 */
final class OpenSubtitles {

    private OpenSubtitles() {}

    private static final String API = "https://api.opensubtitles.com/api/v1";

    /**
     * Consumer key shipped with the app. The 100 downloads a day are counted per client rather than
     * pooled, so one heavy user cannot drain everybody else's — but it is still a shared resource:
     * abuse gets it revoked for every install at once. Hence this source is asked last, and only for
     * what the keyless ones could not produce.
     */
    private static final String KEY = "IxrxupVBKx7dhBkAAtW7QbwnhDMgOdEO";

    /** Must identify the app — see the class comment for what a missing one gets you. */
    private static final String UA = "JustPlayer v" + BuildConfig.VERSION_NAME;

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private static final int TIMEOUT_SEC = 10;

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SEC, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SEC, TimeUnit.SECONDS)
            .build();

    /** One usable file out of a search response. */
    static final class Candidate {
        /** ISO 639-1, as the API reports it. */
        final String language;
        final long fileId;
        final int downloads;
        final String release;
        /** Somebody else's machine translation. Offered, but only once no human wrote one. */
        final boolean machine;

        Candidate(String language, long fileId, int downloads, String release, boolean machine) {
            this.language = language;
            this.fileId = fileId;
            this.downloads = downloads;
            this.release = release;
            this.machine = machine;
        }
    }

    /**
     * Searches one title in every wanted language at once. The API takes them comma-separated, so
     * walking a five-entry priority list costs one request instead of five — a property only this
     * source has, the keyless indexes ignoring every language parameter they are given.
     *
     * @param languages ISO 639-1 codes, most wanted first
     * @return every usable candidate, unordered; empty when nothing matched or the call failed
     */
    static List<Candidate> search(MediaId id, List<String> languages) {
        if (id == null || id.isEmpty() || languages.isEmpty()) {
            return Collections.emptyList();
        }
        // Sorted by key: the TreeMap is the whole defence against the alphabetical-order 301.
        final TreeMap<String, String> params = new TreeMap<>();
        params.put("languages", TextUtils.join(",", languages));
        if (id.imdbNumeric() != null) {
            params.put("imdb_id", id.imdbNumeric());
        } else {
            params.put("tmdb_id", id.tmdb);
        }
        if (!id.isMovie()) {
            params.put("season_number", String.valueOf(id.season));
            if (id.episode > 0) {
                params.put("episode_number", String.valueOf(id.episode));
            }
        }
        final StringBuilder url = new StringBuilder(API).append("/subtitles?");
        boolean first = true;
        for (Map.Entry<String, String> param : params.entrySet()) {
            if (!first) {
                url.append('&');
            }
            first = false;
            url.append(param.getKey()).append('=').append(Uri.encode(param.getValue(), ","));
        }

        final String body = get(url.toString());
        if (body == null) {
            return Collections.emptyList();
        }
        final List<Candidate> candidates = new ArrayList<>();
        try {
            final JSONArray data = new JSONObject(body).optJSONArray("data");
            if (data == null) {
                return Collections.emptyList();
            }
            for (int i = 0; i < data.length(); i++) {
                final JSONObject attributes = data.getJSONObject(i).optJSONObject("attributes");
                if (attributes == null) {
                    continue;
                }
                // A forced track is a handful of cues for foreign dialogue; selected as the only
                // subtitle it reads as a broken feature, and nothing else here would ever want one.
                if (attributes.optBoolean("foreign_parts_only")) {
                    continue;
                }
                // A machine translation is kept but marked, and pick() puts every human file ahead of
                // it. These are not rare leftovers — the most downloaded Russian file for the test
                // episode is one — and dropping them threw away the answer to the question this whole
                // feature asks: for a language nobody translates into, a machine rendering is what
                // there is, and one somebody already made costs no request and no waiting.
                final boolean machine = attributes.optBoolean("machine_translated")
                        || attributes.optBoolean("ai_translated");
                final JSONArray files = attributes.optJSONArray("files");
                if (files == null || files.length() == 0) {
                    continue;
                }
                final long fileId = files.getJSONObject(0).optLong("file_id", -1);
                if (fileId < 0) {
                    continue;
                }
                // download_count reads 0 on plenty of entries; new_download_count is the live one.
                final int downloads =
                        attributes.optInt("new_download_count", attributes.optInt("download_count"));
                candidates.add(new Candidate(attributes.optString("language"), fileId, downloads,
                        attributes.optString("release"), machine));
            }
        } catch (Exception e) {
            Utils.log("OpenSubtitles: " + e);
            return Collections.emptyList();
        }
        return candidates;
    }

    /**
     * The best candidate: the most wanted language that turned up at all, within it a human translation
     * over a machine one, and within that the most downloaded file. Language order beats everything — a
     * rarely downloaded track in the language the user asked for is still the language they asked for —
     * and popularity is the last word rather than the second, because a machine translation is the one
     * kind of file that outnumbers the human ones it is worse than.
     *
     * @param allowMachine whether somebody else's machine translation may be taken at all. Off means
     *                     the viewer emptied the translate-from list, i.e. asked for no machine
     *                     translation — and a stranger's is still one, however little it cost us.
     */
    static Candidate pick(List<Candidate> candidates, List<String> languages, boolean allowMachine) {
        Candidate best = null;
        for (Candidate candidate : candidates) {
            if (candidate.language == null || (candidate.machine && !allowMachine)) {
                continue;
            }
            if (languages.indexOf(candidate.language.toLowerCase(Locale.US)) < 0) {
                continue;
            }
            if (best == null || better(candidate, best, languages)) {
                best = candidate;
            }
        }
        return best;
    }

    private static boolean better(Candidate candidate, Candidate than, List<String> languages) {
        final int rank = languages.indexOf(candidate.language.toLowerCase(Locale.US));
        final int rankThan = languages.indexOf(than.language.toLowerCase(Locale.US));
        if (rank != rankThan) {
            return rank < rankThan;
        }
        if (candidate.machine != than.machine) {
            return than.machine;
        }
        return candidate.downloads > than.downloads;
    }

    /**
     * Turns a file id into a temporary download link. This is the metered call — one of the 100 a
     * day, spent even when it fails. The link needs no credentials of its own, so it can be handed
     * straight to {@link SubtitleFetcher}.
     *
     * @return the link, or null on any failure
     */
    static String link(long fileId) {
        final Request request = auth(new Request.Builder()
                .url(API + "/download")
                .post(RequestBody.create("{\"file_id\":" + fileId + "}", JSON))).build();
        final String body = execute(request);
        if (body == null) {
            return null;
        }
        try {
            final JSONObject json = new JSONObject(body);
            final String link = json.optString("link", null);
            if (link != null) {
                Utils.log("OpenSubtitles: " + json.optInt("remaining", -1) + " downloads left today");
            }
            return link;
        } catch (Exception e) {
            Utils.log("OpenSubtitles: " + e);
            return null;
        }
    }

    /** ISO 639-2/T, which is what the priority list stores, to the two-letter codes the API speaks. */
    static List<String> toIso639_1(List<String> languages) {
        final List<String> result = new ArrayList<>();
        for (String language : languages) {
            final String two = twoLetter(language);
            if (two != null && !result.contains(two)) {
                result.add(two);
            }
        }
        return result;
    }

    private static String twoLetter(String language) {
        if (language == null || language.isEmpty()) {
            return null;
        }
        final String lower = language.toLowerCase(Locale.US);
        if (lower.length() == 2) {
            return lower;
        }
        // No table of our own: the JVM already knows every two-letter code's three-letter form.
        for (String code : Locale.getISOLanguages()) {
            if (lower.equals(new Locale(code).getISO3Language())) {
                return code;
            }
        }
        return null;
    }

    private static String get(String url) {
        return execute(auth(new Request.Builder().url(url)).build());
    }

    private static Request.Builder auth(Request.Builder builder) {
        return builder.header("Api-Key", KEY)
                .header("User-Agent", UA)
                .header("Accept", "application/json");
    }

    private static String execute(Request request) {
        try (Response response = CLIENT.newCall(request).execute()) {
            final ResponseBody body = response.body();
            if (!response.isSuccessful() || body == null) {
                Utils.log("OpenSubtitles: " + response.code() + " " + request.url().encodedPath());
                return null;
            }
            return body.string();
        } catch (IOException e) {
            Utils.log("OpenSubtitles: " + e);
            return null;
        }
    }
}
