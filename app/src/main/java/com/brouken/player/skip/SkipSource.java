package com.brouken.player.skip;

import java.util.List;

/**
 * A source of skip segments for the currently playing media.
 *
 * <p>Today the only implementation is {@link IntentSegmentsSource} (segments delivered via the
 * launch Intent). Future implementations may look segments up by TMDB/IMDB identifiers over the
 * network (AniSkip / SkipDB / IntroDB style), or scan embedded chapters — each as another
 * {@code SkipSource}.
 */
public interface SkipSource {

    /**
     * @param durationSec actual media duration in seconds ({@code <= 0} when unknown); used for
     *                    timing remap and, in future sources, for network lookups
     * @return skip segments in seconds (never {@code null}; empty when none)
     */
    List<SkipSegment> getSegments(double durationSec);
}
