package com.brouken.player.skip;

/**
 * Hardcoded base URLs and the TMDB key used by {@link SegmentFinder}. Kept in one place so a dead
 * endpoint / rotated key is a single-file change (see FIND_INTO.MD). All are public, imdb/tmdb-keyed
 * APIs; no user credentials are involved.
 */
final class SegmentEndpoints {

    private SegmentEndpoints() {}

    /** TV + movies, imdb-keyed, duration-aware. Primary source. */
    static final String SKIPDB = "https://api.skipdb.tv/api/segments";
    /** TV only, imdb-keyed. */
    static final String INTRODB = "https://api.introdb.app/segments";
    /** imdb → MAL mapping per season (never pass ?include=). */
    static final String ARM = "https://arm.haglund.dev/api/v2/imdb";
    /** anime only, MAL-relative episode. */
    static final String ANISKIP = "https://api.aniskip.com/v2/skip-times";
    /** TV + movies, tmdb-keyed (may 403 behind Cloudflare — treated as empty). */
    static final String THEINTRODB = "https://api.theintrodb.org/v3/media";
    /** imdb → tmdb id. Called lazily, only inside the TheIntroDB step. */
    static final String TMDB_FIND = "https://api.themoviedb.org/3/find/";
    /** TMDB v3 base; used to resolve tmdb → imdb via {movie,tv}/{id}/external_ids. */
    static final String TMDB_BASE = "https://api.themoviedb.org/3";
    static final String TMDB_KEY = "875965c1ae50e299f1c13c8c00c54af8";
}
