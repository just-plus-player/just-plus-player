package com.brouken.player;

/**
 * Central catalog ("map") of app-level playback error codes surfaced to the user.
 *
 * <p>Each constant maps a distinct failure the app recognises to a short alphanumeric code. When a
 * message shows a code, this list tells exactly which case it was. Codes are stable identifiers:
 * add new ones for new cases, never renumber or reuse an existing one.
 */
enum PlayerErrorCode {
    /** General fallback: a playback error that matched no more specific category below. */
    GENERAL_ERROR("JPP-1000"),
    /** Lampac stream resolver returned its {@code {"rch":…}} WebSocket handshake instead of media —
     *  the real stream URL is resolved by client-side code over that socket, which this player does
     *  not implement, so the link can never be obtained here. */
    RESOLVER_NOT_READY("JPP-1001"),
    /** ExoPlayer {@code TYPE_SOURCE}: the media could not be read or parsed. */
    SOURCE_ERROR("JPP-1002"),
    /** ExoPlayer {@code TYPE_RENDERER}: a decoder/renderer failed. */
    RENDERER_ERROR("JPP-1003"),
    /** ExoPlayer {@code TYPE_UNEXPECTED}: an internal player error. */
    UNEXPECTED_ERROR("JPP-1004"),
    /** Playback never reached a ready state within the load watchdog window. */
    LOAD_TIMEOUT("JPP-1005");

    final String code;

    PlayerErrorCode(String code) {
        this.code = code;
    }

    @Override
    public String toString() {
        return code;
    }
}
