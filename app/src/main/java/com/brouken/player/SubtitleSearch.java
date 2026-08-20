package com.brouken.player;

import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
     * @return the hit that was downloaded, or null when no enabled source could deliver one
     */
    static Result find(MediaId identifier, List<String> preferred, Prefs prefs, Sink sink) {
        if (identifier == null || identifier.isEmpty() || preferred.isEmpty()) {
            return null;
        }
        // Every source below takes one id and not the other, so whichever is missing is resolved once,
        // up front, from the one we have. Without this a launcher that sends only a tmdb id leaves
        // three of the five unable to run at all — and they look broken rather than unasked.
        final MediaId id = enrich(identifier);
        Utils.log("subtitles: " + id.imdb + " / " + id.tmdb
                + " s" + id.season + "e" + id.episode + " want=" + preferred);

        if (prefs.subtitleSourceRest) {
            final Result result = best(SOURCE_REST, restOpenSubtitles(id, preferred), preferred);
            if (delivered(result, sink)) {
                return result;
            }
        }
        if (prefs.subtitleSourceStremio) {
            final Result result = best(SOURCE_STREMIO, stremio(id), preferred);
            if (delivered(result, sink)) {
                return result;
            }
        }
        if (prefs.subtitleSourceOpenSubtitles) {
            final Result result = fromOpenSubtitles(id, preferred, prefs);
            if (delivered(result, sink)) {
                return result;
            }
        }
        if (prefs.subtitleSourceShegu) {
            final Result result = best(SOURCE_SHEGU, shegu(id), preferred);
            if (delivered(result, sink)) {
                return result;
            }
        }
        return null;
    }

    private static boolean delivered(Result result, Sink sink) {
        if (result == null) {
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
     * The metered source, kept apart because minting its links costs quota: the winner is resolved and
     * nothing else is, so a search that finds twelve files still spends exactly one download.
     */
    private static Result fromOpenSubtitles(MediaId id, List<String> preferred, Prefs prefs) {
        final String token = bearer(prefs);
        final List<String> codes = OpenSubtitles.toIso639_1(preferred);
        final OpenSubtitles.Candidate best = OpenSubtitles.pick(
                OpenSubtitles.search(id, codes, prefs.openSubtitlesKey, token), codes);
        if (best == null) {
            return null;
        }
        final String link = OpenSubtitles.link(best.fileId, prefs.openSubtitlesKey, token);
        if (link == null) {
            return null;
        }
        return new Result(SOURCE_OPENSUBTITLES, Utils.toIso3Language(best.language),
                Collections.singletonList(link));
    }

    /** Logs in only when the user filled in an account; see OpenSubtitles#login for why not cached. */
    private static String bearer(Prefs prefs) {
        if (prefs.openSubtitlesUser.isEmpty() || prefs.openSubtitlesPassword.isEmpty()) {
            return null;
        }
        return OpenSubtitles.login(prefs.openSubtitlesUser, prefs.openSubtitlesPassword,
                prefs.openSubtitlesKey);
    }

    /**
     * shegu.st: tmdb only, and it ignores every language parameter it is offered
     * ({@code language}, {@code lang}, {@code languages} all leave the result identical), so the whole
     * index — a hundred-odd entries — comes back and is sifted here.
     */
    private static List<Candidate> shegu(MediaId id) {
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
            final JSONArray subtitles = json(url.toString()).optJSONArray("subtitles");
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
    private static List<Candidate> stremio(MediaId id) {
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
                    json("https://opensubtitles-v3.strem.io/subtitles/" + path + ".json")
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
    private static List<Candidate> restOpenSubtitles(MediaId id, List<String> preferred) {
        if (id.imdb == null) {
            return Collections.emptyList();
        }
        final String imdb = id.imdbNumeric();
        for (String language : preferred) {
            final StringBuilder url = new StringBuilder("https://rest.opensubtitles.org/search/");
            if (!id.isMovie() && id.episode > 0) {
                url.append("episode-").append(id.episode).append('/');
            }
            url.append("imdbid-").append(imdb).append('/');
            if (!id.isMovie()) {
                url.append("season-").append(id.season).append('/');
            }
            url.append("sublanguageid-").append(language);

            final List<Candidate> candidates = new ArrayList<>();
            try {
                final String body = get(url.toString());
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

    private static JSONObject json(String url) throws Exception {
        final String body = get(url);
        return body == null ? new JSONObject() : new JSONObject(body);
    }

    private static String get(String url) {
        final Request request = new Request.Builder().url(url)
                .header("User-Agent", UA)
                .header("Accept", "application/json")
                .build();
        try (Response response = CLIENT.newCall(request).execute()) {
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
