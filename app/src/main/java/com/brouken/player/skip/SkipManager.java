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
