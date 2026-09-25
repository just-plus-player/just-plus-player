package com.brouken.player.skip;

/**
 * Hardcoded base URLs and the TMDB key used by {@link SegmentFinder}. Kept in one place so a dead
 * endpoint / rotated key is a single-file change (see FIND_INTO.MD). All are public, imdb/tmdb-keyed
 * APIs; no user credentials are involved.
 */
public final class SegmentEndpoints {

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
    /** TV + movies (is_movie=true), imdb-keyed. */
    static final String INTRODB = "https://api.introdb.app/segments";
    /** imdb → MAL mapping per season (never pass ?include=). */
    static final String ARM = "https://arm.haglund.dev/api/v2/imdb";
    /** anime only, MAL-relative episode. */
    static final String ANISKIP = "https://api.aniskip.com/v2/skip-times";
    /** anime, Russian video CDN; shikimoriID (= MAL id) → player page carrying the skip ranges. */
    static final String KODIK = "https://kodik-api.com/get-player";
    /**
     * The embed token Kodik ships to every site in its own public embed script
     * (kodik-add.com/add-players.min.js), used there with this same get-player call.
     */
    static final String KODIK_TOKEN = "447d179e875efe44217f20d1ee2146be";
    /**
     * Chinese animation (donghua): Bilibili's own opening/ending marks per episode, with the episode's exact
     * length. No key; the search wants a WBI signature, whose keys the nav call hands out to anyone.
     */
    static final String BILIBILI_NAV = "https://api.bilibili.com/x/web-interface/nav";
    static final String BILIBILI_SEARCH = "https://api.bilibili.com/x/web-interface/wbi/search/all/v2";
    /** An episode's player: {@code clip_info_list} (CLIP_TYPE_OP / _ED, seconds) and {@code timelength} (ms). */
    static final String BILIBILI_PLAYURL = "https://api.bilibili.com/pgc/player/web/playurl";
    /**
     * The search's risk control scores the client: OkHttp's own User-Agent gets an empty
     * {@code v_voucher} reply most of the time (1 in 4 answered), a browser's none (4 in 4, 2026-09-25);
     * the episode player answers OkHttp's with a flat 412.
     */
    static final String BILIBILI_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
            + " (KHTML, like Gecko) Chrome/126.0 Safari/537.36";
    /** iQIYI, no key: search by name, an album's episodes one per page, and an episode's skip points. */
    static final String IQIYI_SEARCH = "https://mesh.if.iqiyi.com/portal/lw/search/homePageV3";
    static final String IQIYI_EPISODES = "https://pcw-api.iqiyi.com/albums/album/avlistinfo";
    static final String IQIYI_INFO = "https://pcw-api.iqiyi.com/video/video/baseinfo/";
    /** anime only, GraphQL, AniList-keyed ({@code findShowsByExternalId}). */
    static final String ANIMESKIP = "https://api.anime-skip.com/graphql";
    /**
     * The shared read-only client id from anime-skip.com/docs/api; a request without one is refused.
     * The docs call it heavily rate limited and not meant for production: swap in a registered id here.
     */
    static final String ANIMESKIP_CLIENT_ID = "ZGfO0sMF3eCwLYf8yMSCJjlynwNGRXWE";
    /** TV + movies, tmdb-keyed (may 403 behind Cloudflare — treated as empty). */
    static final String THEINTRODB = "https://api.theintrodb.org/v3/media";
    /** imdb → tmdb id. Called lazily, only inside the TheIntroDB step. */
    static final String TMDB_FIND = "https://api.themoviedb.org/3/find/";
    /** TMDB v3 base; used to resolve tmdb → imdb via {movie,tv}/{id}/external_ids. */
    public static final String TMDB_BASE = "https://api.themoviedb.org/3";
    public static final String TMDB_KEY = "875965c1ae50e299f1c13c8c00c54af8";
}
