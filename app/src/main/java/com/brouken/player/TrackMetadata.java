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
    /**
     * Frames per second as the container header states it (Matroska {@code DefaultDuration}, AVI
     * {@code avih/dwMicroSecPerFrame}), or 0 when the container does not say. Media3 uses both values
     * for its own timing but never puts them in {@link androidx.media3.common.Format}.
     */
    final float frameRate;

    TrackMetadata(int trackId, String name, String language, Type type, float frameRate) {
        this.trackId = trackId;
        this.name = name;
        this.language = language;
        this.type = type;
        this.frameRate = frameRate;
    }
}
