package com.brouken.player.skip;

import java.util.ArrayList;
import java.util.List;

/**
 * Java port of the timing-remap logic from Lampa's {@code src/interaction/player/segments.js}.
 *
 * <p>Segment timings are measured against a reference duration ({@code duration_ms}). When the
 * actual file duration differs, the segments are remapped so intro/credits markers line up. If no
 * reference duration is available (or the actual duration is unknown), the original timings are
 * returned unchanged — the same graceful fallback the web player uses.
 *
 * <p>All values are in seconds. This class is pure and stateless.
 */
public final class SegmentAdjuster {

    private SegmentAdjuster() {
    }

    /** Plain start/end pair in seconds (mirrors the web segment objects). */
    public static final class Seg {
        public double start;
        public double end;

        public Seg(double start, double end) {
            this.start = start;
            this.end = end;
        }

        Seg copy() {
            return new Seg(start, end);
        }
    }

    /** Adjusted skip and ad lists. */
    public static final class Result {
        public final List<Seg> skip;
        public final List<Seg> ad;

        Result(List<Seg> skip, List<Seg> ad) {
            this.skip = skip;
            this.ad = ad;
        }
    }

    /**
     * @param skip        original skip segments (seconds)
     * @param ad          original ad segments (seconds)
     * @param refDuration reference duration in seconds ({@code <= 0} when unknown)
     * @param duration    actual file duration in seconds ({@code <= 0} when unknown)
     */
    public static Result adjust(List<Seg> skip, List<Seg> ad, double refDuration, double duration) {
        boolean valid = duration > 0 && !Double.isNaN(duration);

        if (!valid || refDuration <= 0) {
            return new Result(cloneList(skip), cloneList(ad));
        }

        Seg tail = tailSkipSegment(skip, refDuration);
        double creditRefLen = tail != null ? Math.max(0, tail.end - tail.start) : 0;
        double tailStart = tail != null ? tail.start : 0;
        boolean tailAtRef = tail != null
                && Math.abs(tail.end - refDuration) <= 15
                && tail.start >= refDuration - Math.max(creditRefLen, 60) - 15;

        List<Seg> mappedSkip = new ArrayList<>();
        for (Seg seg : skip) {
            boolean isTail = tailAtRef && seg == tail;
            mappedSkip.add(mapSegment(seg, duration, isTail, creditRefLen, tailStart, refDuration));
        }

        List<Seg> mappedAd = new ArrayList<>();
        for (Seg seg : ad) {
            // ad segments are never the skip tail, so isTail is always false here.
            mappedAd.add(mapSegment(seg, duration, false, creditRefLen, tailStart, refDuration));
        }

        mappedSkip = pruneSkipDuplicates(mappedSkip, refDuration);

        return new Result(mappedSkip, mappedAd);
    }

    private static Seg mapSegment(Seg seg, double duration, boolean isTail,
                                  double creditRefLen, double tailStart, double refDuration) {
        double start = seg.start;
        double end = seg.end;

        if (refDuration == 0) return seg.copy();

        // Almost the reference — markers as-is.
        if (Math.abs(duration - refDuration) <= 2) {
            return seg.copy();
        }

        double slack = duration - refDuration;

        // Shorter than the reference — trimmed at the start, leave as-is.
        if (slack < 0) {
            return seg.copy();
        }

        if (isTail) {
            double creditLen = Math.max(1, end - start);
            double mappedStart = duration - creditLen;

            // Extra length is usually at the start — fixed-length credits at the file end.
            if (creditRefLen > 0 && slack < creditRefLen) {
                mappedStart -= Math.floor(slack * 0.3);
            }

            return new Seg(
                    Math.max(0, Math.min(duration - 1, mappedStart)),
                    duration);
        }

        // Bumper 0–30s is not shifted.
        if (start < 30) return seg.copy();

        // Small slack (+3…20s) — part of the offset is in the credits, intro nearer the title card.
        if (slack <= 20 && tailStart > start) {
            double width = (end - start) != 0 ? (end - start) : 1;
            double base = duration - (refDuration - start);
            double booster = slack <= 10 ? Math.floor(slack * 2) : Math.floor(slack * 0.75);
            double guide = start + Math.floor(start / 45) + booster;
            double mapped = Math.max(base, guide);

            double cStart = mapped;
            double cEnd = mapped + width;

            cStart = Math.max(0, Math.min(duration, cStart));
            cEnd = Math.max(cStart + 1, Math.min(duration, cEnd));

            return new Seg(cStart, cEnd);
        }

        double cStart = duration - (refDuration - start);
        double cEnd = duration - (refDuration - end);

        // Medium slack — part of the offset is in the credits, intro nearer the content.
        if (creditRefLen > 0 && slack < creditRefLen) {
            double width = (end - start) != 0 ? (end - start) : 1;
            double base = duration - (refDuration - start);
            double anchor = start + Math.floor(slack * 0.84);
            double softened = start + Math.floor(start / 45) + Math.floor(slack * 0.52);

            if (base > anchor) {
                cStart = anchor;
            } else {
                cStart = base + Math.floor(slack * 0.2);
            }

            if (slack >= 100 && softened > start && softened < cStart) {
                cStart = softened;
            }

            cEnd = cStart + width;
        }

        cStart = Math.max(0, Math.min(duration, cStart));
        cEnd = Math.max(cStart + 1, Math.min(duration, cEnd));

        return new Seg(cStart, cEnd);
    }

    private static Seg skipHeadSegment(List<Seg> skip) {
        if (skip == null || skip.isEmpty()) return null;

        Seg head = null;
        for (Seg seg : skip) {
            if (head == null || seg.start < head.start) head = seg;
        }
        return head;
    }

    private static Seg introSkipSegment(List<Seg> skip, double refDuration) {
        if (skip == null || skip.isEmpty()) return null;

        Seg head = null;
        double limit = refDuration > 0 ? refDuration * 0.45 : Double.POSITIVE_INFINITY;

        for (Seg seg : skip) {
            double start = seg.start;

            // Logo/bumper in the first seconds is not the episode intro.
            if (start < 30) continue;
            if (start > limit) continue;

            if (head == null || start > head.start) head = seg;
        }

        return head != null ? head : skipHeadSegment(skip);
    }

    private static Seg tailSkipSegment(List<Seg> skip, double refDuration) {
        if (skip == null || skip.isEmpty()) return null;

        Seg tail = null;
        double limit = refDuration > 0 ? refDuration * 0.55 : Double.POSITIVE_INFINITY;

        for (Seg seg : skip) {
            double start = seg.start;

            // Credits near the reference end are not a bumper/promo at 0s.
            if (start < 30) continue;
            if (refDuration > 0 && start < limit) continue;

            if (tail == null || seg.end > tail.end) tail = seg;
        }

        return tail;
    }

    private static boolean keepEarlySkip(Seg seg, List<Seg> list) {
        double start = seg.start;
        double end = seg.end;

        if (start >= 30) return true;

        // Only drop a duplicated intro: overlap or a tail reaching into the intro zone.
        for (Seg other : list) {
            double oStart = other.start;
            if (oStart < 30) continue;

            double oEnd = other.end;

            if (end > oStart && start < oEnd) return false;
            if (end > oStart + 15) return false;
        }

        return true;
    }

    private static boolean shouldKeepSkipSegment(Seg seg, List<Seg> list, double anchor, double refDuration) {
        double start = seg.start;
        double end = seg.end;

        if (!keepEarlySkip(seg, list)) return false;

        if (end >= refDuration - 60) return true;

        if (start >= anchor - 1) return true;

        // Bumper/logo before the intro is a separate zone, not an intro duplicate.
        if (end <= anchor + 15) return true;

        return false;
    }

    private static List<Seg> pruneSkipDuplicates(List<Seg> skip, double refDuration) {
        if (skip == null || skip.isEmpty()) return skip;

        Seg intro = introSkipSegment(skip, refDuration);
        if (intro == null) return skip;

        double anchor = intro.start;

        List<Seg> result = new ArrayList<>();
        for (Seg seg : skip) {
            if (shouldKeepSkipSegment(seg, skip, anchor, refDuration)) result.add(seg);
        }
        return result;
    }

    private static List<Seg> cloneList(List<Seg> list) {
        List<Seg> out = new ArrayList<>();
        if (list != null) {
            for (Seg seg : list) out.add(seg.copy());
        }
        return out;
    }
}
