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
    /**
     * TV + movies, crowd-sourced, duration-aware. Multi-id: accepts imdb / tmdb / tvdb / anilist in
     * one POST, so it also works when only a tmdb id is known. {@code POST /v1/movies} with a
     * single-item JSON array; 404 → nothing found. Gated on a recognized client User-Agent
     * ({@link #SKIPME_UA}) — a plain request is rejected with {@code "Client not supported"}.
     */
    static final String SKIPME = "https://db.skipme.workers.dev/v1/movies";
    /** Client User-Agent SkipMe.db accepts (the published client string; any other UA gets a 403). */
    static final String SKIPME_UA = "SkipMe.db/0.0";
    /** Community DB, imdb-keyed ({@code imdb[:season:episode]}), coordinates in seconds. */
    static final String INTROHATER = "https://introhater.com/api/v1/segments/";
    /** Baked-in public read key (permission read:segments); a request without it gets a 401. */
    static final String INTROHATER_KEY = "introhater_mpv_client";
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
