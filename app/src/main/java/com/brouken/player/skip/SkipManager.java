package com.brouken.player.skip;

import java.util.ArrayList;
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

    /**
     * A segment spanning more than this fraction of the file is not an intro, recap or credits — no
     * such segment covers a third of an episode. Sources do return one: a credits entry whose start
     * was recorded wrong and whose end is open-ended arrives as "skip from minute six to the end".
     * Acting on it would skip the episode, so it is dropped once the duration is known.
     */
    private static final double MAX_SEGMENT_FRACTION = 1.0 / 3;

    private SkipSource source;
    private List<SkipSegment> segments = Collections.emptyList();

    /** User-configured global shift applied to every segment (seconds); 0 = off. */
    private double offsetSec = 0;

    /** Set the manual skip offset (session-scoped, set from the player). Applied on the next {@link #rebuild}. */
    public void setOffsetSec(double offsetSec) {
        this.offsetSec = offsetSec;
    }

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
        final List<SkipSegment> base = source != null
                ? source.getSegments(durationSec) : Collections.<SkipSegment>emptyList();
        segments = sanitize(offsetSec != 0 ? applyOffset(base, durationSec) : base, durationSec);
        classifyCredits(durationSec);
    }

    /**
     * Clamps segment ends to the file end — an open-ended credits segment arrives with a sentinel end
     * far past it — and drops whatever is left spanning more than {@link #MAX_SEGMENT_FRACTION} of the
     * file. A no-op while the duration is unknown: there is nothing to judge a segment against then,
     * and the next rebuild runs this once it is.
     */
    private static List<SkipSegment> sanitize(List<SkipSegment> in, double durationSec) {
        if (!(durationSec > 0)) {
            return in; // also covers NaN
        }
        final double maxSpan = durationSec * MAX_SEGMENT_FRACTION;
        final List<SkipSegment> out = new ArrayList<>(in.size());
        for (SkipSegment s : in) {
            final double end = Math.min(s.endSec, durationSec);
            if (end <= s.startSec || end - s.startSec > maxSpan) {
                continue;
            }
            if (end == s.endSec) {
                out.add(s);
                continue;
            }
            final SkipSegment clamped = new SkipSegment(s.startSec, end, s.type, s.category,
                    s.coordBase, s.timeTrust);
            clamped.confirmed = s.confirmed;
            out.add(clamped);
        }
        return out;
    }

    /** Shift every segment by {@link #offsetSec}, clamped to the media bounds; drop any pushed out. */
    private List<SkipSegment> applyOffset(List<SkipSegment> in, double durationSec) {
        final boolean durationKnown = durationSec > 0 && !Double.isNaN(durationSec);
        final List<SkipSegment> out = new ArrayList<>(in.size());
        for (SkipSegment s : in) {
            double start = s.startSec + offsetSec;
            double end = s.endSec + offsetSec;
            if (start < 0) {
                start = 0;
            }
            if (durationKnown && end > durationSec) {
                end = durationSec;
            }
            if (end <= start) {
                continue; // shifted out of range
            }
            final SkipSegment shifted = new SkipSegment(start, end, s.type, s.category, s.coordBase, s.timeTrust);
            shifted.confirmed = s.confirmed;
            out.add(shifted);
        }
        return out;
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
