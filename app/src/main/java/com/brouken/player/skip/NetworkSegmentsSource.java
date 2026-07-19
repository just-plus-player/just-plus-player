package com.brouken.player.skip;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link SkipSource} backed by segments already fetched from the network (see {@link SegmentFinder}).
 *
 * <p>Coordinates are absolute broadcast seconds (SkipDB is duration-shifted server-side at fetch
 * time; other sources return absolute times), so — unlike {@link IntentSegmentsSource} — no
 * {@link SegmentAdjuster} remap is applied; the list is returned as-is.
 */
public class NetworkSegmentsSource implements SkipSource {

    private final List<SkipSegment> segments;

    public NetworkSegmentsSource(List<SkipSegment> segments) {
        this.segments = segments != null ? segments : new ArrayList<SkipSegment>();
    }

    public boolean hasSegments() {
        return !segments.isEmpty();
    }

    @NonNull
    @Override
    public List<SkipSegment> getSegments(double durationSec) {
        // Fresh instances each rebuild so the once-only `skipped` flags never leak across rebuilds.
        final List<SkipSegment> out = new ArrayList<>(segments.size());
        for (SkipSegment seg : segments) {
            out.add(new SkipSegment(seg.startSec, seg.endSec, seg.type));
        }
        return out;
    }
}
