package com.brouken.player.skip;

import java.util.Collections;
import java.util.List;

/**
 * Orchestrates skip segments for the current media: holds the active source, rebuilds the segment
 * list when the duration becomes known (or the playlist item changes), and answers runtime
 * position queries.
 *
 * <p>Today it wraps a single {@link SkipSource}. When network sources (TMDB/IMDB lookups) or a
 * chapter scanner are added, this is where the priority/fallback between sources will live.
 */
public class SkipManager {

    /**
     * A SKIP segment whose end lands past this fraction of the file is treated as the end credits.
     * Keyed on the end (not the start) so it survives an intro that is shifted deep into the episode
     * after a long cold open: such an intro still ends far from the finish because the bulk of the
     * episode follows it, whereas credits end in the closing stretch even when a post-credits scene
     * or teaser follows.
     */
    private static final double CREDITS_END_FRACTION = 0.75;

    /**
     * A segment ending within this many seconds of the file end counts as reaching the end. The tail
     * segment is remapped to exactly the duration, but online sources return absolute times that may
     * stop a touch earlier, so a small tolerance keeps them classified.
     */
    private static final double CREDITS_END_TOLERANCE_SEC = 1.5;

    private SkipSource source;
    private List<SkipSegment> segments = Collections.emptyList();

    /** Set the source for the current media (e.g. a new {@link IntentSegmentsSource}); clears segments. */
    public void setSource(SkipSource source) {
        this.source = source;
        this.segments = Collections.emptyList();
    }

    public void clear() {
        this.source = null;
        this.segments = Collections.emptyList();
    }

    /** Recompute segments against the now-known duration. Safe to call repeatedly. */
    public void rebuild(double durationSec) {
        segments = source != null ? source.getSegments(durationSec) : Collections.<SkipSegment>emptyList();
        classifyCredits(durationSec);
    }

    /**
     * Classify each segment's end-of-file relationship. {@code credits} marks a SKIP segment in the
     * closing stretch (drives the credits skip-mode); {@code reachesEnd} marks a segment ending at
     * the file end (drives advancing to the next episode). Guarded on a known positive duration —
     * otherwise a fraction/tolerance test against a non-positive duration would flag everything.
     */
    private void classifyCredits(double durationSec) {
        final boolean durationKnown = durationSec > 0 && !Double.isNaN(durationSec);
        for (SkipSegment seg : segments) {
            seg.credits = durationKnown
                    && seg.type == SkipSegment.Type.SKIP
                    && seg.endSec >= durationSec * CREDITS_END_FRACTION;
            seg.reachesEnd = durationKnown
                    && seg.endSec >= durationSec - CREDITS_END_TOLERANCE_SEC;
        }
    }

    public boolean hasSegments() {
        return !segments.isEmpty();
    }

    /** Current segments (post-remap); for timeline highlighting. */
    public List<SkipSegment> getSegments() {
        return segments;
    }

    /** Reset the once-only skipped flags (e.g. on media item change or a manual seek back). */
    public void resetSkipped() {
        for (SkipSegment seg : segments) {
            seg.skipped = false;
        }
    }

    /** Segment containing the position (not yet skipped), or null. */
    public SkipSegment activeSegment(double posSec) {
        for (SkipSegment seg : segments) {
            if (seg.skipped) {
                continue;
            }
            if (posSec >= seg.startSec && posSec < seg.endSec) {
                return seg;
            }
        }
        return null;
    }

    /** Nearest not-yet-skipped segment starting within {@code leadSec} ahead of the position, or null. */
    public SkipSegment upcomingSegment(double posSec, double leadSec) {
        SkipSegment nearest = null;
        for (SkipSegment seg : segments) {
            if (seg.skipped) {
                continue;
            }
            if (seg.startSec > posSec && seg.startSec <= posSec + leadSec) {
                if (nearest == null || seg.startSec < nearest.startSec) {
                    nearest = seg;
                }
            }
        }
        return nearest;
    }
}
