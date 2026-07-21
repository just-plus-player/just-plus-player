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

    /**
     * Runtime flag: set by {@link SkipManager} when this is the end-credits segment — a {@code SKIP}
     * segment ending in the closing stretch of the file (it need not run all the way to the end; a
     * post-credits scene or next-episode teaser may follow). Keyed on the end so an intro shifted to
     * mid-episode is not mistaken for credits. Credits follow their own skip-mode preference
     * ({@code skipModeCredits}).
     */
    public boolean credits;

    /**
     * Runtime flag: set by {@link SkipManager} when the segment ends at (or within tolerance of) the
     * file end. Only then does skipping it advance the playlist to the next episode; otherwise the
     * skip seeks to the segment end so any content after the credits still plays.
     */
    public boolean reachesEnd;

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
