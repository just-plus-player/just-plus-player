package com.brouken.player;

import com.brouken.player.skip.SegmentEndpoints;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Turns a name somebody typed into the tmdb id the subtitle sources are keyed by, so a title the
 * launcher got wrong — or never sent — can still be looked up.
 *
 * <p>Two catalogues, each for what it is actually good at. TMDB searches by name — its key is already
 * in the app and it answers with posters — while the season and episode numbers come from Cinemeta,
 * because TMDB's are in a different numbering than every subtitle source uses. {@link SubtitleSearch}
 * derives whichever id a source needs from the other, so neither call has to fetch both.
 */
final class TitleSearch {

    private TitleSearch() {}

    /** Image width that holds up in a 60dp row on a TV without paying for a full-size file. TMDB
     *  sizes on width, so the same one serves 2:3 posters and 16:9 episode stills. */
    private static final String IMAGE = "https://image.tmdb.org/t/p/w185";

    /** Cinemeta, the Stremio catalogue. Keyless, imdb-keyed, and the numbering the sources share. */
    private static final String CINEMETA = "https://v3-cinemeta.strem.io/meta/series/";

    /** How many seasons are asked for at once; TMDB appends at most twenty per request. */
    private static final int MAX_SEASONS = 20;

    /** TMDB pages at 20 and nobody scrolls a panel further than that to find what they just typed. */
    private static final int MAX_RESULTS = 20;

    /** An imdb id, wherever it sits — typed alone, or inside the imdb.com URL it was copied from. */
    private static final Pattern IMDB_ID = Pattern.compile("tt(\\d{5,})");

    /** A tmdb id in a themoviedb.org URL, where the path says which of the two catalogues it is in. */
    private static final Pattern TMDB_URL = Pattern.compile("themoviedb\\.org/(movie|tv)/(\\d+)");

    /** A tmdb id on its own. Capped because past nine digits it is not one. */
    private static final Pattern TMDB_ID = Pattern.compile("\\d{1,9}");

    /** One thing the typed name could have meant. */
    static final class Title {
        final String tmdb;
        final boolean movie;
        final String name;
        /** Release year, or null when TMDB has no date — an unreleased title still has a name. */
        final String year;
        /** Null when there is no poster, so the row leads with nothing rather than a grey card. */
        final String posterUrl;

        Title(String tmdb, boolean movie, String name, String year, String posterUrl) {
            this.tmdb = tmdb;
            this.movie = movie;
            this.name = name;
            this.year = year;
            this.posterUrl = posterUrl;
        }
    }

    /** An episode to pick from, in the numbering the subtitle sources use. */
    static final class Episode {
        final int season;
        final int number;
        final String name;
        final String stillUrl;

        Episode(int season, int number, String name, String stillUrl) {
            this.season = season;
            this.number = number;
            this.name = name;
            this.stillUrl = stillUrl;
        }
    }

    /**
     * Blocking; call from a worker. An empty list is "no such title"; {@code null} is "could not
     * ask" — a dead network, which retyping the name will not cure and the caller says so.
     *
     * <p>An id in the text is resolved as well as searched for, so pasting one is a way in of its
     * own: what {@code tt3659388} or {@code 286217} names comes first, the name matches after it.
     * Both questions are always asked because they cannot collide — TMDB's name search answers
     * nothing at all for an id or a URL.
     */
    static List<Title> search(String query) {
        final List<Title> out = new ArrayList<>();
        if (query == null || query.trim().isEmpty()) {
            return out;
        }
        out.addAll(byId(query.trim()));
        final HttpUrl.Builder url = base();
        if (url == null) {
            return out;
        }
        final JSONObject root = get(url
                .addPathSegment("search")
                .addPathSegment("multi")
                .addQueryParameter("query", query.trim())
                .addQueryParameter("include_adult", "false")
                .build());
        if (root == null && out.isEmpty()) {
            // Could not ask, as distinct from asked and told nothing.
            return null;
        }
        final JSONArray results = root == null ? null : root.optJSONArray("results");
        if (results == null) {
            return out;
        }
        for (int i = 0; i < results.length() && out.size() < MAX_RESULTS; i++) {
            final Title title = title(results.optJSONObject(i));
            if (title != null) {
                out.add(title);
            }
        }
        return out;
    }

    /**
     * What an id in the typed text points at: {@code tt3659388}, {@code 286217}, or the imdb /
     * themoviedb.org URL either was copied out of. Empty when there is no id in there, or no
     * catalogue entry answers to it.
     *
     * <p>Offered alongside the name matches rather than instead of them, because a bare number is
     * both: {@code 1917} is a film and it is also a tmdb id (a different film). Which of the two
     * catalogues a bare number belongs to is unknowable as well — 286217 is The Martian and, in the
     * series numbering, something else entirely — so both are asked and both are put on the list.
     */
    private static List<Title> byId(String text) {
        final List<Title> out = new ArrayList<>();
        final Matcher imdb = IMDB_ID.matcher(text);
        if (imdb.find()) {
            final HttpUrl.Builder url = base();
            if (url == null) {
                return out;
            }
            // find answers with one array per kind; the items carry media_type, so they read exactly
            // like a search result.
            final JSONObject root = get(url.addPathSegment("find").addPathSegment(imdb.group())
                    .addQueryParameter("external_source", "imdb_id").build());
            for (String kind : new String[] { "movie_results", "tv_results" }) {
                final JSONArray results = root == null ? null : root.optJSONArray(kind);
                for (int i = 0; results != null && i < results.length(); i++) {
                    final Title title = title(results.optJSONObject(i));
                    if (title != null) {
                        out.add(title);
                    }
                }
            }
            return out;
        }
        final Matcher url = TMDB_URL.matcher(text);
        if (url.find()) {
            add(out, url.group(2), "movie".equals(url.group(1)));
            return out;
        }
        if (TMDB_ID.matcher(text).matches()) {
            add(out, text, true);
            add(out, text, false);
        }
        return out;
    }

    /** Adds the movie or the series a tmdb id names, if there is one; a 404 adds nothing. */
    private static void add(List<Title> out, String tmdb, boolean movie) {
        final HttpUrl.Builder url = base();
        if (url == null) {
            return;
        }
        final JSONObject root = get(url.addPathSegment(movie ? "movie" : "tv")
                .addPathSegment(tmdb).build());
        final String name = root == null ? null : string(root, movie ? "title" : "name");
        if (name == null) {
            return;
        }
        final String poster = string(root, "poster_path");
        out.add(new Title(tmdb, movie, name, year(string(root, movie ? "release_date" : "first_air_date")),
                poster == null ? null : IMAGE + poster));
    }

    /**
     * One result of a search or a find, or null for anything no subtitle source can be asked about —
     * search/multi also answers with people and collections.
     */
    private static Title title(JSONObject item) {
        if (item == null) {
            return null;
        }
        final String type = item.optString("media_type", "");
        final boolean movie = "movie".equals(type);
        if (!movie && !"tv".equals(type)) {
            return null;
        }
        final long id = item.optLong("id", -1);
        final String name = string(item, movie ? "title" : "name");
        if (id < 1 || name == null) {
            return null;
        }
        final String poster = string(item, "poster_path");
        return new Title(String.valueOf(id), movie, name,
                year(string(item, movie ? "release_date" : "first_air_date")),
                poster == null ? null : IMAGE + poster);
    }

    /** The year out of a TMDB date, or null when there is no date — an unreleased title still has a name. */
    private static String year(String date) {
        return date != null && date.length() >= 4 ? date.substring(0, 4) : null;
    }

    /**
     * The list to put in front of somebody: Cinemeta's numbering carrying TMDB's names and stills.
     *
     * <p>Each catalogue supplies what it is right about. The numbers have to be Cinemeta's because
     * that is what the subtitle sources answer to. The text has to be TMDB's because Cinemeta is
     * English only, and so do the images because Cinemeta's are missing exactly where they are most
     * wanted — {@code episodes.metahub.space} answers 404 for the second season of an anime while
     * happily serving its specials.
     *
     * <p>Lined up by aired position, which is the one thing the two agree on: the same physical
     * episode is Cinemeta's S02E01 and TMDB's S01E13, and both put it thirteenth. Only when the two
     * runs are the same length though — differing lengths mean the positions no longer describe the
     * same episodes, and a confidently mislabelled list is worse than an English one, so that case
     * keeps Cinemeta's own text.
     */
    static List<Episode> episodes(String imdb, String tmdb) {
        final List<Episode> known = episodes(imdb);
        final List<Episode> display = tmdbRun(tmdb);
        int aired = 0;
        for (Episode episode : known) {
            if (episode.season > 0) {
                aired++;
            }
        }
        if (display.isEmpty() || display.size() != aired) {
            Utils.log("titles: " + display.size() + " tmdb vs " + aired + " cinemeta episodes, not aligned");
            return known;
        }
        final List<Episode> out = new ArrayList<>(known.size());
        int position = 0;
        for (Episode episode : known) {
            // Specials are not part of the aired run, so they have no position to line up with and
            // keep what Cinemeta said — including its thumbnail, which for those does exist.
            if (episode.season < 1) {
                out.add(episode);
                continue;
            }
            final Episode named = display.get(position++);
            out.add(new Episode(episode.season, episode.number,
                    named.name != null ? named.name : episode.name,
                    named.stillUrl != null ? named.stillUrl : episode.stillUrl));
        }
        return out;
    }

    /**
     * Every episode TMDB lists, in aired order. Season 0 is left out because it is not part of that
     * order. Used for its text and stills only — its season and episode numbers are the ones this
     * whole arrangement exists to avoid handing to a subtitle source.
     *
     * <p>One request: appending the seasons costs nothing for the ones that do not exist, which is
     * what makes asking for twenty of them blindly cheaper than first asking how many there are.
     */
    private static List<Episode> tmdbRun(String tmdb) {
        final List<Episode> out = new ArrayList<>();
        final HttpUrl.Builder url = base();
        if (url == null || tmdb == null) {
            return out;
        }
        final StringBuilder append = new StringBuilder();
        for (int season = 1; season <= MAX_SEASONS; season++) {
            if (append.length() > 0) {
                append.append(',');
            }
            append.append("season/").append(season);
        }
        final JSONObject root = get(url.addPathSegment("tv").addPathSegment(tmdb)
                .addQueryParameter("append_to_response", append.toString()).build());
        if (root == null) {
            return out;
        }
        for (int season = 1; season <= MAX_SEASONS; season++) {
            final JSONObject block = root.optJSONObject("season/" + season);
            final JSONArray episodes = block == null ? null : block.optJSONArray("episodes");
            if (episodes == null) {
                continue;
            }
            for (int i = 0; i < episodes.length(); i++) {
                final JSONObject item = episodes.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                final int number = item.optInt("episode_number", -1);
                if (number < 1) {
                    continue;
                }
                final String still = string(item, "still_path");
                out.add(new Episode(season, number, string(item, "name"),
                        still == null ? null : IMAGE + still));
            }
        }
        return out;
    }

    /**
     * Every episode of the series, ordered, in the numbering the subtitle sources actually use.
     *
     * <p>Not TMDB's: TMDB folds an anime's seasons together (Solo Leveling is one season of 25 there,
     * two of 12 and 13 everywhere else), and asked in TMDB's coordinates every source answers with
     * nothing or with the wrong episode — including shegu.st, which is keyed by a tmdb id but names
     * its files {@code S02E01}. Cinemeta is the catalogue the Stremio/OpenSubtitles side is numbered
     * against, so its coordinates are the ones worth handing on. Specials (season 0) are included:
     * somebody watching one needs subtitles for it as much as for the rest.
     *
     * <p>Keyed by imdb, which is why the caller resolves the tmdb id first. Empty when the lookup
     * failed or the title is unknown, which the caller reads as "ask about the whole season".
     */
    static List<Episode> episodes(String imdb) {
        final List<Episode> out = new ArrayList<>();
        if (imdb == null || imdb.isEmpty()) {
            return out;
        }
        final HttpUrl url = HttpUrl.parse(CINEMETA + (imdb.startsWith("tt") ? imdb : "tt" + imdb) + ".json");
        final JSONObject root = url == null ? null : get(url);
        final JSONObject meta = root == null ? null : root.optJSONObject("meta");
        final JSONArray videos = meta == null ? null : meta.optJSONArray("videos");
        if (videos == null) {
            return out;
        }
        for (int i = 0; i < videos.length(); i++) {
            final JSONObject item = videos.optJSONObject(i);
            if (item == null) {
                continue;
            }
            final int season = item.optInt("season", -1);
            final int number = item.optInt("episode", -1);
            if (season < 0 || number < 1) {
                continue;
            }
            out.add(new Episode(season, number, string(item, "name"), string(item, "thumbnail")));
        }
        // Aired order, so a position in this list is the same episode whichever way a launcher
        // numbered it — which is what lets the rest of a playlist be lined up against it.
        Collections.sort(out, (a, b) -> a.season != b.season
                ? Integer.compare(a.season, b.season) : Integer.compare(a.number, b.number));
        return out;
    }

    /**
     * The shared start of every request: the key, and the device's language so the names offered are
     * the ones the viewer reads. Language only affects what comes back, never what matches — TMDB
     * searches the original titles and every translation either way.
     */
    private static HttpUrl.Builder base() {
        final HttpUrl base = HttpUrl.parse(SegmentEndpoints.TMDB_BASE);
        return base == null ? null : base.newBuilder()
                .addQueryParameter("api_key", SegmentEndpoints.TMDB_KEY)
                .addQueryParameter("language", Locale.getDefault().toLanguageTag());
    }

    /** A field's value, or null when TMDB left it out or sent it as JSON null (which it does often). */
    private static String string(JSONObject item, String key) {
        if (item.isNull(key)) {
            return null;
        }
        final String value = item.optString(key, "");
        return value.isEmpty() ? null : value;
    }

    private static JSONObject get(HttpUrl url) {
        final Request request = new Request.Builder().url(url)
                .header("Accept", "application/json")
                .build();
        try (Response response = SubtitleSearch.CLIENT.newCall(request).execute()) {
            final ResponseBody body = response.body();
            if (!response.isSuccessful() || body == null) {
                Utils.log("titles: " + response.code() + " " + request.url().host());
                return null;
            }
            return new JSONObject(body.string());
        } catch (Exception e) {
            Utils.log("titles: " + e);
            return null;
        }
    }
}
