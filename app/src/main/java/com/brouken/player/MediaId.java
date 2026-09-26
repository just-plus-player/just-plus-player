package com.brouken.player;

import android.text.TextUtils;

/**
 * The stable ids that say what is playing: imdb / tmdb, plus season and episode for a series episode.
 *
 * Built per lookup by {@link PlayerActivity#mediaIdAt(int)} and passed around by value, so nothing
 * here can outlive the item it describes — an id from the previous episode cannot leak into the next
 * one, which is the failure a long-lived holder invites. Skip segments and the online subtitle search
 * read the same object, so both features agree on what they are looking at.
 */
final class MediaId {

    /** Always the {@code tt} form, e.g. {@code "tt14688458"}, whatever the launcher sent. Null when absent. */
    final String imdb;
    final String tmdb;
    /** Below 1 for a movie. A season implies a series even when the episode number is missing. */
    final int season;
    final int episode;

    MediaId(String imdb, String tmdb, int season, int episode) {
        this.imdb = withTt(blankToNull(imdb));
        this.tmdb = blankToNull(tmdb);
        this.season = season;
        this.episode = episode;
    }

    boolean isEmpty() {
        return imdb == null && tmdb == null;
    }

    boolean isMovie() {
        return season < 1;
    }

    /**
     * The imdb id without the {@code tt}. api.opensubtitles.com answers only this form — given the
     * prefixed one it replies 301 to the stripped one, costing a round trip to learn nothing.
     */
    String imdbNumeric() {
        if (imdb == null) {
            return null;
        }
        final String digits = imdb.startsWith("tt") ? imdb.substring(2) : imdb;
        return digits.isEmpty() ? null : digits;
    }

    boolean sameAs(MediaId other) {
        return other != null && TextUtils.equals(imdb, other.imdb) && TextUtils.equals(tmdb, other.tmdb)
                && season == other.season && episode == other.episode;
    }

    /** Cache key: the title and the episode, nothing else. */
    String key() {
        return imdb + "|" + tmdb + "|" + season + "|" + episode;
    }

    /**
     * An absent id, whatever shape it arrived in. Blank is the obvious one; the literal word is the
     * one that cost a search. Android's {@code JSONObject.optString} stringifies a JSON null, so a
     * TMDB title whose {@code imdb_id} is null hands back {@code "null"} — a non-empty string that
     * every source then keyed on. api.opensubtitles.com answered {@code imdb_id=null} with 400,
     * rest.opensubtitles.org answered {@code imdbid-null} with 400, and the trace read as though the
     * id had been absent all along. A launcher can send the same word, so the guard belongs here,
     * where every id this app holds is made.
     */
    /**
     * A launcher may send the imdb id as bare digits ({@code "903747"}). SkipDB, IntroDB.app and arm
     * answer that with 400, and without the leading zeros {@code tt903747} is simply another title
     * that has no segments — so the id is prefixed and padded to imdb's seven digits here, once.
     */
    private static String withTt(String imdb) {
        if (imdb == null || !imdb.matches("\\d+")) {
            return imdb;
        }
        final StringBuilder sb = new StringBuilder("tt");
        for (int i = imdb.length(); i < 7; i++) {
            sb.append('0');
        }
        return sb.append(imdb).toString();
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        final String trimmed = value.trim();
        return trimmed.isEmpty() || "null".equalsIgnoreCase(trimmed)
                || "undefined".equalsIgnoreCase(trimmed) ? null : trimmed;
    }
}
