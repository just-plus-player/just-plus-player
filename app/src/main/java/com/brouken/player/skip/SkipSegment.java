package com.brouken.player.skip;

/**
 * A single skip segment, in seconds, with its Lampa type preserved.
 *
 * <p>{@link Type#AD} segments are always skipped silently; {@link Type#SKIP} segments are either
 * auto-skipped or offered via the "Skip" button depending on the user's mode preference.
 */
public class SkipSegment {

    public enum Type {
        SKIP,
        AD
    }

    public final double startSec;
    public final double endSec;
    public final Type type;

    /** Runtime flag: set once the segment has been skipped, so it is only acted on once. */
    public boolean skipped;

    public SkipSegment(double startSec, double endSec, Type type) {
        this.startSec = startSec;
        this.endSec = endSec;
        this.type = type;
    }

    public long startMs() {
        return Math.round(startSec * 1000);
    }

    public long endMs() {
        return Math.round(endSec * 1000);
    }
}
