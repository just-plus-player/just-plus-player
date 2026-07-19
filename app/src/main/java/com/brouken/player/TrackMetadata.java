package com.brouken.player;

/**
 * A single track's metadata read directly from the media container (MP4 {@code udta/name},
 * Matroska {@code TrackEntry/Name}), used to surface rich release labels that Media3 does not
 * expose through {@link androidx.media3.common.Format#label}.
 */
class TrackMetadata {
    enum Type { VIDEO, AUDIO, SUBTITLE, UNKNOWN }

    final int trackId;
    final String name;
    final String language;
    final Type type;

    TrackMetadata(int trackId, String name, String language, Type type) {
        this.trackId = trackId;
        this.name = name;
        this.language = language;
        this.type = type;
    }
}
