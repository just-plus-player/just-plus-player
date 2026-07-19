package com.brouken.player.skip;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link SkipSource} backed by the raw segments JSON delivered through the launch Intent.
 *
 * <p>The JSON mirrors Lampa's {@code data.segments}:
 * <pre>{ "duration_ms": &lt;ms&gt;, "skip": [{"start": &lt;sec&gt;, "end": &lt;sec&gt;}], "ad": [ ... ] }</pre>
 * {@code start}/{@code end} are in seconds. Timings are remapped against the actual file duration
 * via {@link SegmentAdjuster}. Any parse failure yields an empty list — playback is never blocked.
 */
public class IntentSegmentsSource implements SkipSource {

    private final List<SegmentAdjuster.Seg> skip = new ArrayList<>();
    private final List<SegmentAdjuster.Seg> ad = new ArrayList<>();
    private double refDurationSec;
    private final boolean valid;

    public IntentSegmentsSource(String json) {
        this.valid = parse(json);
    }

    /** @return true if the JSON held at least one usable segment. */
    public boolean hasSegments() {
        return valid && !(skip.isEmpty() && ad.isEmpty());
    }

    private boolean parse(String json) {
        if (json == null || json.isEmpty()) {
            return false;
        }
        try {
            JSONObject root = new JSONObject(json);
            refDurationSec = parseDurationMs(root.opt("duration_ms"));
            parseArray(root.optJSONArray("skip"), skip);
            parseArray(root.optJSONArray("ad"), ad);
            return true;
        } catch (Exception e) {
            skip.clear();
            ad.clear();
            return false;
        }
    }

    private static void parseArray(JSONArray array, List<SegmentAdjuster.Seg> out) {
        if (array == null) {
            return;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject seg = array.optJSONObject(i);
            if (seg == null) {
                continue;
            }
            double start = seg.optDouble("start", 0);
            double end = seg.optDouble("end", 0);
            if (Double.isNaN(start) || Double.isNaN(end) || end <= start) {
                continue;
            }
            out.add(new SegmentAdjuster.Seg(start, end));
        }
    }

    /** Mirrors {@code parseDurationMs} in segments.js: ms → sec, guarding NaN/non-positive. */
    private static double parseDurationMs(Object value) {
        if (value == null) {
            return 0;
        }
        double ms;
        try {
            ms = Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
        if (Double.isNaN(ms) || ms <= 0) {
            return 0;
        }
        return ms / 1000.0;
    }

    @NonNull
    @Override
    public List<SkipSegment> getSegments(double durationSec) {
        SegmentAdjuster.Result result = SegmentAdjuster.adjust(skip, ad, refDurationSec, durationSec);

        List<SkipSegment> out = new ArrayList<>();
        for (SegmentAdjuster.Seg seg : result.skip) {
            out.add(new SkipSegment(seg.start, seg.end, SkipSegment.Type.SKIP));
        }
        for (SegmentAdjuster.Seg seg : result.ad) {
            out.add(new SkipSegment(seg.start, seg.end, SkipSegment.Type.AD));
        }
        return out;
    }
}
