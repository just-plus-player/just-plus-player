package com.brouken.player;

import android.content.Context;
import android.net.Uri;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.text.Cue;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSourceUtil;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.extractor.text.CuesWithTiming;
import androidx.media3.extractor.text.DefaultSubtitleParserFactory;
import androidx.media3.extractor.text.SubtitleParser;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Every cue of one external subtitle file, held in memory and addressable by time.
 * <p>
 * This is what lets the subtitle offset work in both directions and survive a seek. The player's text
 * renderer reads its stream strictly forward and drops each cue once it has been shown, so it can never
 * answer for a moment that has already gone by — and a delay is precisely a question about the past.
 * A jump forward is the same problem: the lines between the new position and the delay behind it were
 * skipped, and nothing can hand them back. With the file itself in memory, any moment is one lookup
 * away and the offset is applied by simply looking somewhere else.
 * <p>
 * Only sideloaded files can be held this way (a subtitle the app added itself, whose track carries its
 * URI as the format id — see {@link SubtitleUtils#buildSubtitle}). Tracks embedded in the media stay on
 * the renderer, where {@link SubtitleOffset} still delays their cues as best it can.
 * <p>
 * The bytes are read through the same data source and parsed by the same Media3 parser the player would
 * have used, so what ends up on screen is what the renderer would have produced, only re-timed. The app
 * has already re-encoded anything that was not UTF-8 by the time the track exists (Utils.convertInputStreamToUTF).
 */
final class SubtitleTimeline {

    private static final int[] NONE = new int[0];
    /** Shown for this long when a cue carries no duration and nothing follows it. */
    private static final long OPEN_ENDED_US = 5 * C.MICROS_PER_SECOND;

    private final long[] startUs;
    private final long[] endUs;
    private final List<ImmutableList<Cue>> cues;

    private SubtitleTimeline(long[] startUs, long[] endUs, List<ImmutableList<Cue>> cues) {
        this.startUs = startUs;
        this.endUs = endUs;
        this.cues = cues;
    }

    /**
     * Reads and parses the whole file. Null when Media3 has no parser for it, when it holds no cue, or
     * when reading fails — the caller then leaves the track to the renderer.
     */
    /**
     * The file parsed last, handed on once.
     *
     * <p>{@link SubtitleFetcher} builds a timeline to read the last cue's time and decide whether the
     * file was timed for this cut, and {@link PlayerActivity} then builds the very same one again to put
     * it on screen — a few thousand cues parsed twice for every subtitle found. Handed over rather than
     * cached: the second reader is always the next one along, so keeping one entry until it is taken
     * costs a single reference, where a real cache would be a film's worth of cues held on the chance of
     * a third reader.
     */
    private static Uri pendingUri;
    private static SubtitleTimeline pending;

    private static synchronized SubtitleTimeline take(Uri uri) {
        if (pendingUri == null || !pendingUri.equals(uri)) {
            return null;
        }
        final SubtitleTimeline taken = pending;
        pendingUri = null;
        pending = null;
        return taken;
    }

    private static synchronized void offer(Uri uri, SubtitleTimeline timeline) {
        pendingUri = uri;
        pending = timeline;
    }

    static SubtitleTimeline load(Context context, Uri uri, String mimeType) {
        final SubtitleTimeline reused = take(uri);
        if (reused != null) {
            return reused;
        }
        try {
            final Format format = new Format.Builder().setSampleMimeType(mimeType).build();
            final SubtitleParser.Factory factory = new DefaultSubtitleParserFactory();
            if (!factory.supportsFormat(format)) {
                return null;
            }
            final DataSource source = new DefaultDataSource.Factory(context).createDataSource();
            final byte[] data;
            try {
                source.open(new DataSpec(uri));
                data = DataSourceUtil.readToEnd(source);
            } finally {
                DataSourceUtil.closeQuietly(source);
            }
            final List<CuesWithTiming> blocks = new ArrayList<>();
            factory.create(format).parse(data, SubtitleParser.OutputOptions.allCues(), blocks::add);
            final List<CuesWithTiming> ordered = new ArrayList<>(blocks.size());
            for (final CuesWithTiming block : blocks) {
                if (block.startTimeUs != C.TIME_UNSET && !block.cues.isEmpty()) {
                    ordered.add(block);
                }
            }
            if (ordered.isEmpty()) {
                return null;
            }
            // ASS dialogue lines are not required to be in order, and the lookup below counts on it.
            Collections.sort(ordered, (a, b) -> Long.compare(a.startTimeUs, b.startTimeUs));
            final List<CuesWithTiming> kept = withoutPromos(ordered);
            if (kept.isEmpty()) {
                return null;
            }
            final int count = kept.size();
            final long[] startUs = new long[count];
            final long[] endUs = new long[count];
            final List<ImmutableList<Cue>> cues = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                final CuesWithTiming block = kept.get(i);
                startUs[i] = block.startTimeUs;
                endUs[i] = block.durationUs != C.TIME_UNSET && block.durationUs > 0
                        ? block.startTimeUs + block.durationUs
                        : (i + 1 < count ? kept.get(i + 1).startTimeUs : block.startTimeUs + OPEN_ENDED_US);
                cues.add(block.cues);
            }
            final SubtitleTimeline timeline = new SubtitleTimeline(startUs, endUs, cues);
            offer(uri, timeline);
            return timeline;
        } catch (Throwable t) {
            // A subtitle is not worth a broken playback: the renderer keeps the track either way.
            Utils.log("subtitles: timeline failed " + t);
            return null;
        }
    }

    // Blocks in file order, for whatever needs the whole file rather than one moment in it — see
    // SubtitleTranslate, which reads the cues out and writes a translated copy on the same timings.
    int size() {
        return startUs.length;
    }

    long startUs(int index) {
        return startUs[index];
    }

    long endUs(int index) {
        return endUs[index];
    }

    ImmutableList<Cue> cuesAt(int index) {
        return cues.get(index);
    }

    /**
     * How many blocks at each end of the file may be dropped as advertising: the very first and the very
     * last, and nothing else.
     * <p>
     * Do not widen this. A wider window was tried and it deleted real content — the Ukrainian subtitles
     * for Avatar carry two helpline cards from the film's own end credits, three and four blocks from the
     * end, and both name a website. The uploader's own notices sat at blocks 1 and 2498 of 2498, i.e. at
     * the exact ends. Two stacked advertisements would leave the second one showing, which is the right
     * way round: an advertisement shown is a nuisance, a line of the film deleted is a defect.
     */
    private static final int PROMO_WINDOW = 1;

    /**
     * A web address, which is what an inserted advertisement always carries and what a film's dialogue
     * essentially never does.
     * <p>
     * ponytail: an explicit list of endings rather than "word dot word". The loose form matches
     * {@code report.pdf} and Russian abbreviations as readily as it matches a host, and the cost of
     * being wrong here is a deleted subtitle. Add an ending when one turns up.
     * <p>
     * {@code link} turned up: OpenSubtitles shortens its own notices through {@code osdb.link}, and
     * the opening one — "Watch any video online with Open-SUBTITLES / Free Browser extension:
     * osdb.link/ext" — carries neither a scheme nor {@code www.}, so it went straight through, while
     * its twin over the closing credits ("Оцените данный субтитр, пожалуйста, www.osdb.link/...")
     * was caught. Seen 2026-08-21 in a Russian track for Salt, by way of shegu.st.
     */
    private static final Pattern PROMO = Pattern.compile(
            "https?://|www\\.|[a-z0-9][a-z0-9-]*\\.(?:app|com|net|org|io|tv|me|info|link|ru|ua|pl)\\b",
            Pattern.CASE_INSENSITIVE);

    /**
     * The file without the advertising blocks uploaders wrap it in — the notice that shows for a few
     * seconds over the opening, and its twin over the closing credits.
     * <p>
     * Dropped here rather than while downloading, so it covers every file the app puts on screen itself,
     * whatever produced it: a search result, a sidecar file beside the video, or a translation this app
     * made — where an untouched promo would be worse still, since a machine-translated advertisement
     * reads as one the player inserted.
     */
    private static List<CuesWithTiming> withoutPromos(final List<CuesWithTiming> ordered) {
        final int count = ordered.size();
        final List<CuesWithTiming> kept = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            final CuesWithTiming block = ordered.get(i);
            if ((i < PROMO_WINDOW || i >= count - PROMO_WINDOW) && isPromo(block)) {
                Utils.log("subtitles: dropped an ad at block " + (i + 1) + " of " + count);
                continue;
            }
            kept.add(block);
        }
        return kept;
    }

    /**
     * What an inserted advertisement says about itself, beside carrying an address. Required on top of
     * {@link #PROMO}, because an address alone is also what a line of dialogue looks like when somebody
     * reads a website out — "Зайди на sciencedirect.com, там всё есть" was deleted as an advertisement,
     * which is the defect this whole window is supposed to be careful about.
     * <p>
     * Every notice this feature has actually met names itself: "Устали искать субтитры? … getray.app",
     * "Натисніть відтворення. Субтитри з'являються … tryray.app", "Watch any video online with
     * Open-SUBTITLES / Free Browser extension: osdb.link/ext", "Оцените данный субтитр, пожалуйста,
     * www.osdb.link/…". The Avatar end-credit helpline cards, which must be kept, name none of this.
     * <p>
     * The cost is the bare-URL advertisement with no words around it, which now survives — and by this
     * file's own reckoning that is the right way round: an advertisement shown is a nuisance, a line of
     * the film deleted is a defect.
     */
    private static final Pattern PROMO_WORDS = Pattern.compile(
            "subtitl|субтитр|переклад|перевод|translat|download|скача|завантаж|"
                    + "extension|browser|расширени|розширенн|\\bfree\\b|бесплатн|безкоштовн|"
                    + "\\bai\\b|нейросет|нейромереж|оцените|оцініть|watch any",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** Both marks are looked for across the whole block: a notice may put its address on its own line. */
    private static boolean isPromo(final CuesWithTiming block) {
        final StringBuilder text = new StringBuilder();
        for (final Cue cue : block.cues) {
            if (cue.text != null) {
                text.append(cue.text).append('\n');
            }
        }
        return PROMO.matcher(text).find() && PROMO_WORDS.matcher(text).find();
    }

    /** Indices of the blocks on screen at this moment, ascending; empty when there is nothing to show. */
    int[] visibleAt(long timeUs) {
        int count = 0;
        int[] found = null;
        // ponytail: a plain scan that stops at the first cue starting later. A two-hour file is a few
        // thousand entries and this runs a few times a second — a binary search is worth it only if that
        // ever shows up in a profile.
        for (int i = 0; i < startUs.length && startUs[i] <= timeUs; i++) {
            if (timeUs < endUs[i]) {
                if (found == null) {
                    found = new int[4];
                } else if (count == found.length) {
                    found = Arrays.copyOf(found, count * 2);
                }
                found[count++] = i;
            }
        }
        if (count == 0) {
            return NONE;
        }
        return count == found.length ? found : Arrays.copyOf(found, count);
    }

    /** The cues of those blocks, in file order — overlapping lines (ASS) all show, as they should. */
    ImmutableList<Cue> cuesOf(int[] indices) {
        if (indices.length == 0) {
            return ImmutableList.of();
        }
        if (indices.length == 1) {
            return cues.get(indices[0]);
        }
        final ImmutableList.Builder<Cue> all = ImmutableList.builder();
        for (final int index : indices) {
            all.addAll(cues.get(index));
        }
        return all.build();
    }
}
