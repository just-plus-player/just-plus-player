package com.brouken.player;

import android.os.Handler;
import android.os.Looper;

import androidx.media3.common.C;
import androidx.media3.common.text.Cue;
import androidx.media3.common.text.CueGroup;
import androidx.media3.exoplayer.ExoPlaybackException;
import androidx.media3.exoplayer.ForwardingRenderer;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.text.TextOutput;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The subtitle timing offset (positive = subtitles later). Media3 has no such setting and re-timing the
 * subtitle file would mean reloading the media at every step of the slider, so it is done around the
 * text renderer, in two halves that both live here:
 * <ul>
 *   <li><b>Earlier</b> (negative offset): the renderer runs on a clock shifted <em>forward</em>, so it
 *       resolves the cue for a moment that has not arrived on screen yet.
 *   <li><b>Later</b> (positive offset): the renderer keeps the true clock, and every cue group it emits
 *       is held here until the media position reaches the cue's own time plus the offset.
 * </ul>
 * The renderer's clock is never shifted <em>backwards</em>, which is what a delay would naively do: a
 * text renderer consumes each cue once and drops it, so asking it for an earlier moment shows nothing
 * at all until the clock has caught back up.
 * <p>
 * Groups are therefore kept for {@link #HISTORY_MS} after they have been shown, which is what makes the
 * panel answer at once: raising the delay by 10 s has to put back on screen the line from 10 s ago, and
 * then play the lines after it in order — subtitles no one kept cannot be re-timed.
 * <p>
 * All of that is the fallback, and even it cannot answer for the first moments after a jump forward —
 * those lines were never read at all. An external subtitle file is therefore taken over outright: given
 * a {@link SubtitleTimeline}, the renderer's cues are dropped and the screen is painted from the file
 * itself at {@code position - offset}. That is exact in both directions, at any offset, from the first
 * frame after any seek. Tracks embedded in the media have no such timeline and keep the fallback.
 * <p>
 * Every deadline is measured in media time rather than on a real-time timer, so a pause holds with it, a
 * seek lands where it should and playback speed does not stretch the offset.
 */
final class SubtitleOffset implements TextOutput {

    /** Where the media position comes from — the player does not exist yet when this is built. */
    interface Position {
        /**
         * Position within the current item in ms, the same clock {@link CueGroup#presentationTimeUs} is
         * on, or {@link C#TIME_UNSET} when there is no player to ask.
         */
        long currentMs();

        /** Whether that position is moving on its own — when it is not, nothing needs repainting. */
        boolean playing();
    }

    private static final long TICK_MS = 50; // resolution of the hold, far below what an eye can catch
    private static final long HISTORY_MS = 32_000; // the panel's ±30 s, and room to spare

    private final TextOutput output;
    private final Position position;
    private final Handler handler;
    private final List<CueGroup> groups = new ArrayList<>(); // as they arrived, oldest first
    private final Runnable release = this::release;
    /** Index of the first group not passed on yet; everything before it is history. */
    private int next;
    /** The whole subtitle file, when the selected track is one we can hold. Read on the playback thread. */
    private volatile SubtitleTimeline timeline;
    /** Blocks of {@link #timeline} currently on screen, or null when what is on screen is unknown. */
    private int[] painted;

    // Written on the app thread as the panel moves, read on the playback thread by the renderer half.
    private volatile double offsetSec;

    SubtitleOffset(TextOutput output, Looper outputLooper, Position position) {
        this.output = output;
        this.position = position;
        this.handler = new Handler(outputLooper);
    }

    /** The text renderer, wrapped so a negative offset can shift its clock. */
    Renderer wrap(Renderer textRenderer) {
        return new OffsetRenderer(textRenderer);
    }

    /**
     * Takes the selected track over from the renderer, or hands it back with {@code null}. Cheap to call
     * with what is already set — every track change comes through here.
     */
    void setTimeline(SubtitleTimeline timeline) {
        final boolean same = this.timeline == timeline;
        this.timeline = timeline;
        if (!same) {
            groups.clear();
            next = 0;
            painted = null;
        }
        release();
    }

    /** The position started or stopped moving: pick the painting back up, or let it stop. */
    void wake() {
        release();
    }

    void setOffsetSec(double sec) {
        offsetSec = sec;
        final long nowMs = position.currentMs();
        // A larger delay moves groups that were already shown back into the future: hand them out again
        // from wherever they now belong, so the panel takes effect on this line and not in ten seconds.
        while (next > 0 && dueMs(groups.get(next - 1)) > nowMs) {
            next--;
        }
        release();
    }

    /** Drops everything — after a seek or an item change it all belongs to another moment. */
    void clear() {
        handler.removeCallbacks(release);
        groups.clear();
        next = 0;
        painted = null;
        release();
    }

    @Override
    public void onCues(CueGroup cueGroup) {
        if (timeline != null) {
            return; // the file itself is the source now, and it holds more than the renderer has left
        }
        groups.add(cueGroup);
        release();
    }

    @Override
    public void onCues(List<Cue> cues) {
        // Dropped on purpose: the renderer sends this deprecated form immediately before the group it
        // belongs to, and release() re-sends the pair in the same order once the group is due.
    }

    /** Paints from the file, or passes on every held group whose moment has come. */
    private void release() {
        handler.removeCallbacks(release);
        final long nowMs = position.currentMs();
        final SubtitleTimeline own = timeline;
        if (own != null) {
            if (nowMs != C.TIME_UNSET) {
                paint(own, nowMs);
            }
            if (position.playing()) {
                handler.postDelayed(release, TICK_MS);
            }
            return;
        }
        // At zero or below there is nothing to hold back: the renderer's own clock has already placed the
        // cue, and passing it straight through keeps that timing exactly as it resolved it.
        final long dueBy = (offsetSec <= 0 || nowMs == C.TIME_UNSET) ? Long.MAX_VALUE : nowMs;
        while (next < groups.size() && dueMs(groups.get(next)) <= dueBy) {
            // Re-stamped with the moment it actually reaches the screen rather than the moment the
            // renderer resolved it for: an offset moves both, and downstream the stamp is read as "when
            // this line was up". The painted half already emits at the true position, so the two agree.
            final CueGroup group = groups.get(next++);
            emit(new CueGroup(group.cues, dueMs(group) * 1000));
        }
        if (next < groups.size()) {
            handler.postDelayed(release, TICK_MS);
        }
        int stale = 0;
        while (stale < next && nowMs != C.TIME_UNSET
                && groups.get(stale).presentationTimeUs / 1000 < nowMs - HISTORY_MS) {
            stale++;
        }
        groups.subList(0, stale).clear();
        next -= stale;
    }

    /** What belongs on screen at this moment, sent on only when it is not there already. */
    private void paint(SubtitleTimeline timeline, long nowMs) {
        final long nowUs = nowMs * 1000;
        final int[] visible = timeline.visibleAt(nowUs - (long) (offsetSec * C.MICROS_PER_SECOND));
        if (Arrays.equals(visible, painted)) {
            return;
        }
        painted = visible;
        emit(new CueGroup(timeline.cuesOf(visible), nowUs));
    }

    /**
     * The one way out of here, and where the file's own presentation is taken off. Both halves pass
     * through it — cues held back from the renderer and cues painted from a file — so every subtitle
     * reaching the screen has had the same thing done to it. See
     * {@link SubtitleUtils#withoutEmbeddedLook}.
     */
    private void emit(CueGroup group) {
        final CueGroup uniform = SubtitleUtils.withoutEmbeddedLook(group);
        output.onCues(uniform.cues);
        output.onCues(uniform);
    }

    private long dueMs(CueGroup group) {
        return group.presentationTimeUs / 1000 + (long) (offsetSec * 1000);
    }

    private final class OffsetRenderer extends ForwardingRenderer {

        OffsetRenderer(Renderer textRenderer) {
            super(textRenderer);
        }

        @Override
        public void render(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {
            // Nothing to shift while the file is in charge: its cues are dropped in onCues anyway.
            final double sec = timeline != null ? 0 : offsetSec;
            // ponytail: read ahead of what is buffered and there is simply no cue yet, so a subtitle can
            // lag behind on a slow stream; and right after a seek the reading starts at the seek point,
            // so the first moments carry no cue. Both settle by themselves as playback goes on.
            super.render(sec < 0 ? positionUs - (long) (sec * C.MICROS_PER_SECOND) : positionUs,
                    elapsedRealtimeUs);
        }

        @Override
        public boolean isEnded() {
            // Wrapped, the delegate is no longer the `instanceof TextRenderer` the player looks for when
            // it hands a text renderer its hard stream-end position — the one thing that ends a text
            // renderer whose track stops short of the media. Without it playback could sit at the end
            // without ever reaching STATE_ENDED (no post-playback action, no auto-next), so: ended once
            // the stream is read out and the player has declared it final. Cues keep coming — the
            // delegate is not told — and every renderer must be ended before playback is, so the picture
            // still decides when the item is over.
            return super.isEnded() || (isCurrentStreamFinal() && hasReadStreamToEnd());
        }
    }
}
