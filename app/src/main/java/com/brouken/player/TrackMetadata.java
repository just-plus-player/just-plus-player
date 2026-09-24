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
    /**
     * The coded picture width as the container header states it (Matroska {@code PixelWidth}), or 0 when
     * it does not. Read for the same reason as the rate: both are wanted before the decoder has
     * published a format, to ask the display for its mode early.
     */
    final int width;
    /**
     * The container's own name for the codec (Matroska {@code CodecID}), or null where it was not
     * read. It is the only trace a track leaves when Media3 drops it: an extractor that does not know
     * a CodecID skips the track silently, and what reaches the player is a file with sound and no
     * picture at all rather than a video that failed.
     */
    final String codec;

    TrackMetadata(int trackId, String name, String language, Type type, float frameRate) {
        this(trackId, name, language, type, frameRate, 0, null);
    }

    TrackMetadata(int trackId, String name, String language, Type type, float frameRate, int width) {
        this(trackId, name, language, type, frameRate, width, null);
    }

    TrackMetadata(int trackId, String name, String language, Type type, float frameRate, int width,
                  String codec) {
        this.trackId = trackId;
        this.name = name;
        this.language = language;
        this.type = type;
        this.frameRate = frameRate;
        this.width = width;
        this.codec = codec;
    }
}
