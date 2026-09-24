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

    /** As it arrived from the launcher: {@code "tt14688458"} or {@code "14688458"}. Null when absent. */
    final String imdb;
    final String tmdb;
    /** Below 1 for a movie. A season implies a series even when the episode number is missing. */
    final int season;
    final int episode;

    MediaId(String imdb, String tmdb, int season, int episode) {
        this.imdb = blankToNull(imdb);
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

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        final String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
