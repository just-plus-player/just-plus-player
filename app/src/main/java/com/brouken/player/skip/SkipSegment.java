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

    /**
     * The kind of SKIP segment as reported by the source. Cross-source voting groups segments of the
     * same category (intro vs recap vs credits), so a source's intro cannot "agree" with another's
     * credits. {@link #UNKNOWN} is used when the category is not preserved (e.g. intent segments).
     */
    public enum Category {
        INTRO,
        RECAP,
        CREDITS,
        PREVIEW,
        UNKNOWN
    }

    /**
     * The coordinate system a segment's timing lives in. Duration-aware sources adapt their timings to
     * the actual stream length; absolute sources return broadcast-cut seconds that only match when the
     * file is the same cut; chapters are file-accurate by definition. Voting never averages across
     * different coordinate systems — it takes the timing of the agreeing source with the highest
     * {@link #timeTrust}.
     */
    public enum CoordBase {
        DURATION_AWARE,
        ABSOLUTE,
        CHAPTER
    }

    /** Time-source priority tiers (higher wins when agreeing segments disagree on timing). */
    public static final int TIME_TRUST_ABSOLUTE = 100;
    public static final int TIME_TRUST_DURATION_AWARE = 200;
    public static final int TIME_TRUST_CHAPTER = 300;

    public final double startSec;
    public final double endSec;
    public final Type type;
    public final Category category;
    public final CoordBase coordBase;

    /** Time-source priority of the source that produced this timing (see {@code TIME_TRUST_*}). */
    public final int timeTrust;

    /** Runtime flag: set once the segment has been skipped, so it is only acted on once. */
    public boolean skipped;

    /**
     * Runtime flag: set by the voting aggregator when at least two sources agreed on this segment.
     * Kept for future UI/policy tightening; the current policy still offers/auto-skips unconfirmed
     * single-source segments (coverage is prioritized).
     */
    public boolean confirmed;

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

    /**
     * Full constructor used by the voting aggregator, carrying the segment's category and coordinate
     * metadata so voting can group by type and pick timing from the most file-accurate source.
     */
    public SkipSegment(double startSec, double endSec, Type type, Category category,
                       CoordBase coordBase, int timeTrust) {
        this.startSec = startSec;
        this.endSec = endSec;
        this.type = type;
        this.category = category;
        this.coordBase = coordBase;
        this.timeTrust = timeTrust;
    }

    /**
     * Legacy constructor: category {@link Category#UNKNOWN}, {@link CoordBase#ABSOLUTE} coordinates.
     * Used by {@link IntentSegmentsSource} (intent segments are a single source, never voted) and by
     * copy-loops that preserve an existing segment's already-resolved timing.
     */
    public SkipSegment(double startSec, double endSec, Type type) {
        this(startSec, endSec, type, Category.UNKNOWN, CoordBase.ABSOLUTE, TIME_TRUST_ABSOLUTE);
    }

    public long startMs() {
        return Math.round(startSec * 1000);
    }

    public long endMs() {
        return Math.round(endSec * 1000);
    }
}
