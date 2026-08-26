package com.brouken.player;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import androidx.media3.common.C;
import androidx.media3.common.text.Cue;
import androidx.media3.common.text.CueGroup;
import androidx.media3.exoplayer.text.TextOutput;

import java.util.List;

/**
 * The second subtitle line — a hint under the one being read, for watching in a language being
 * learned: the original on the main line, a translation under it.
 *
 * <p>This class is only the drawing of it. Where its cues come from is the same two-sided arrangement
 * the first line has, and it lives in {@link PlayerActivity}: a {@link SubtitleOffset} of its own, fed
 * either by a text renderer of its own — see {@link SecondaryTextTrack} for how a second text renderer
 * is made to receive anything at all — or, for a subtitle the app holds as a file, painted straight
 * out of a {@link SubtitleTimeline}. The file path is the more exact of the two, so it is preferred
 * wherever there is a file.
 *
 * <p>A plain {@code TextView} rather than a second {@code SubtitleView}, for three reasons that each
 * on their own would decide it: only a {@code TextView} measures its own height, so the line can
 * collapse to nothing while the hint is silent; only a {@code TextView} carries a real background
 * plate with alpha; and {@code SubtitleView} would place the cue by its own rules, when the whole
 * point of this line is that it sits in a band of its own and never moves.
 *
 * <p>Shown or hidden, because a hint that is always there is a hint nobody reads — the native tongue
 * wins every time it is on screen. On demand nothing stands where the hint would be: the hint arrives
 * for the one line that did not land and leaves with it, and the first line keeps its usual place the
 * rest of the time. What it shows is drawn from {@link #current}, which is kept whether or not it is
 * on screen, so asking for it costs nothing and waits for nothing.
 */
final class SecondarySubtitles implements TextOutput {

    /** Hard cap on the hint: a longer one would climb out of its band and onto the main line. */
    private static final int MAX_LINES = 2;

    /**
     * How long a line that is still standing may be, before it is taken for a leftover rather than a
     * line — measured in the film, not on a clock.
     *
     * <p>It is the guard for the one case where nothing has said the line is over: a seek, which the
     * renderer answers by replaying the cues it has in hand, and the last of those sticks. Judging by the
     * film's own position is what tells a replayed backlog and a line that really is on screen apart. How
     * long a line stays worth asking about once it has gone is {@link #PEEK_GONE_MS}, which is a different
     * question and was for a while wrongly answered by this one.
     */
    private static final long PEEK_REACH_MS = 10_000;

    /**
     * How long a peek into a silence stays on screen. Separate from the reach above, and it has to be:
     * the reach says how far back a line may be to be worth offering, this says how long it is worth
     * looking at. One constant serving both left a line that had already gone hanging for ten seconds.
     */
    private static final long PEEK_STALE_MS = 3000;

    /**
     * How long after a line has gone a peek still answers with it — measured in the film, and from the
     * line's own end rather than from its start.
     *
     * <p>The end is not a guess: the empty cue group that clears the line carries the moment it went
     * (the renderer stamps it with the cue change time, in the same clock as the position). Judging by
     * the start instead is what let a line that had run out three seconds ago and a scene ago answer a
     * peek the same way — {@link #PEEK_REACH_MS} had to be long enough to cover a line's own length,
     * and so it covered the silence after it too.
     */
    private static final long PEEK_GONE_MS = 2500;

    /**
     * How long a line is taken to still be on screen, counted from its own moment in the film.
     *
     * <p>Only for a line nothing has cleared. In the ordinary way of things the end arrives as an empty
     * cue group and {@link #lastEndedAtMs} is exact; what is left is a line the renderer replayed into a
     * seek and never took back, and for that there is no end to read. Held over a pause that was the
     * whole fault: the film's position is frozen, so nothing could age it out, and a line from a scene
     * ago sat on screen for as long as the pause lasted. Past this it is treated as a recollection
     * instead — still worth offering while it is within reach, but shown for a moment rather than held.
     */
    private static final long PEEK_LIVE_MS = 7000;

    // The hint lives exactly as long as the line it belongs to, and not a frame longer. Two lines that
    // say the same thing have to leave together or they stop reading as one thing — which is also why
    // there is no minimum time and no grace: either would put the hint on screen under a line it does
    // not translate. A peek asked for in the last moments of a line is therefore short, and the answer
    // to that is to ask again: with nothing on the line, a peek shows the one before it for
    // PEEK_STALE_MS, which is the same request answered properly.

    /** How often a running peek checks whether the film has moved past the line it was asked about. */
    private static final long PEEK_WATCH_MS = 500;

    enum State {
        /**
         * Nothing drawn: picture-in-picture, no second line chosen, the feature switched off, or on
         * demand simply not asked for yet.
         */
        HIDDEN,
        /** The hint itself, on its plate. Collapses to nothing while the line is silent. */
        SHOWN,
    }

    private final TextView view;
    /** Run when a peek runs out on its own, so the layout it opened can close again. */
    private final Runnable onPeekEnd;
    private final SubtitleOffset.Position position;
    private final Runnable peekEnd = this::endPeek;
    /**
     * A peek cannot rely on the next cue to end it. Before a long silence the renderer simply stops
     * sending, with no empty group to say the line is over, so a peek waiting for one hangs until the
     * next line — which can be a scene away. This watches the film's own position instead and ends the
     * peek once it has carried past the line that was asked about. Paused, the position does not move,
     * so a peek held over a still frame stays as long as the pause does.
     */
    private final Runnable peekWatch = new Runnable() {
        @Override
        public void run() {
            if (!peeking) {
                return;
            }
            final long now = position.currentMs();
            if (now != C.TIME_UNSET && now - peekedAtMs > PEEK_REACH_MS) {
                endPeek();
                return;
            }
            view.postDelayed(this, PEEK_WATCH_MS);
        }
    };

    /**
     * The line as it stands right now, kept whether or not it is on screen. Showing the hint on
     * demand then draws what is already known instead of waiting for the next cue to come round.
     */
    private CharSequence current = "";
    /** The last line that had anything on it, which is what a peek into a silence has to show. */
    private CharSequence last = "";
    private State state = State.SHOWN;

    private boolean peeking;
    /** The line this peek was asked about; when the cue moves off it, the peek is over. */
    private CharSequence peeked = "";
    /** Whether this peek is showing {@link #last} because the line was silent when it was asked for. */
    private boolean stale;
    /** The moment in the film each of them belongs to, taken from the cue group that carried it. */
    private long currentAtMs;
    private long lastAtMs;
    /** When {@link #last} went off screen, off the empty group that cleared it; unset while it is up. */
    private long lastEndedAtMs = C.TIME_UNSET;
    /** Where in the film the line a running peek was asked about belongs. */
    private long peekedAtMs;

    // Style, kept rather than applied once: which parts of it are used depends on the state.
    private int textColor = Color.WHITE;
    private float textSizePx;
    private Typeface typeface = Typeface.DEFAULT;
    private int padHPx;
    private int padVPx;
    private Drawable plate;
    /** The state the view's chrome is currently set up for, so a cue change does not redo all of it. */
    private State applied;

    SecondarySubtitles(final TextView view, final Runnable onPeekEnd,
                       final SubtitleOffset.Position position) {
        this.view = view;
        this.onPeekEnd = onPeekEnd;
        this.position = position;
        view.setMaxLines(MAX_LINES);
    }

    void setState(final State state) {
        if (this.state != state) {
            this.state = state;
            render();
        }
    }

    State getState() {
        return state;
    }

    /** Whether anything is drawn at all — the band under the first line is reserved on this. */
    boolean isVisible() {
        return state != State.HIDDEN;
    }

    /**
     * Colour, plate and size. Everything else about the look is the main line's to decide.
     *
     * <p>The typeface is passed in rather than left to the view, and that is not cosmetic. The first
     * line is drawn by Media3 with {@code Typeface.DEFAULT}; a {@code TextView} left alone takes its
     * font from the theme, which on some devices is the vendor's own. Two faces at the same pixel size
     * do not look the same size, and the difference reads as the size setting having failed.
     */
    void style(final int textColor, final int backgroundColor, final float textSizePx,
               final Typeface typeface, final int cornerPx, final int padHPx, final int padVPx) {
        this.textColor = textColor;
        this.textSizePx = textSizePx;
        this.typeface = typeface;
        this.padHPx = padHPx;
        this.padVPx = padVPx;
        if (backgroundColor == Color.TRANSPARENT) {
            plate = null;
        } else {
            final GradientDrawable drawable = new GradientDrawable();
            drawable.setCornerRadius(cornerPx);
            drawable.setColor(backgroundColor);
            plate = drawable;
        }
        applied = null;
        render();
    }

    /**
     * Takes the line off screen and forgets what was on it — after a seek, an item change or a rebuild,
     * where what was showing is no longer an answer to anything. Whatever the renderer sends next is
     * still welcome.
     *
     * <p>There used to be a {@code forget()} beside this with a byte-identical body and a comment
     * claiming it left the screen alone, which {@code show("", 0)} plainly does not.
     */
    void clear() {
        endPeek();
        last = "";
        lastAtMs = 0;
        // Nothing is on screen to have ended, so the moment the last line went is no longer a fact about
        // anything. Left behind, it was a stale timestamp that only happened to be harmless.
        lastEndedAtMs = C.TIME_UNSET;
        show("", 0);
    }

    /**
     * Asks for the hint, and reports whether there was anything to ask for. It shows the line that is
     * running, or the one that has just gone if none is, and takes itself off again — see
     * {@link #PEEK_STALE_MS}. Only the flags and the timers live here; whether that actually puts the
     * hint on screen is {@link PlayerActivity}'s to decide, so that the lock screen and
     * picture-in-picture stay the last word.
     */
    boolean peek(final long positionMs) {
        final boolean silent = current.length() == 0;
        final CharSequence answer = silent ? last : current;
        final long belongsTo = silent ? lastAtMs : currentAtMs;
        if (answer.length() == 0) {
            return false; // nothing being said and nothing said before it
        }
        if (silent) {
            // The line is over and it is known exactly when, so that is what freshness is measured from.
            if (lastEndedAtMs == C.TIME_UNSET || positionMs - lastEndedAtMs > PEEK_GONE_MS) {
                return false;
            }
        } else if (positionMs - belongsTo > PEEK_REACH_MS) {
            // Still standing as far as anything here knows, but from far enough back that it is the
            // renderer talking to itself — a backlog replayed into a seek.
            return false;
        }
        // Held only while the line can still be on screen. Older than that it is a recollection, however
        // the renderer left it — and a recollection is shown for a moment, not held.
        view.removeCallbacks(peekEnd);
        view.removeCallbacks(peekWatch);
        peeking = true;
        stale = silent || positionMs - belongsTo > PEEK_LIVE_MS;
        peeked = answer;
        peekedAtMs = belongsTo;
        view.postDelayed(peekWatch, PEEK_WATCH_MS);
        if (stale) {
            // Nothing is coming to end this one, so it ends itself.
            view.postDelayed(peekEnd, PEEK_STALE_MS);
        }
        render();
        return true;
    }

    /** Ends a peek now, whether it was running out or not. Silent if none was. */
    void endPeek() {
        view.removeCallbacks(peekEnd);
        view.removeCallbacks(peekWatch);
        if (!peeking) {
            return;
        }
        peeking = false;
        stale = false;
        peeked = "";
        render();
        onPeekEnd.run();
    }

    boolean isPeeking() {
        return peeking;
    }

    @Override
    public void onCues(final CueGroup cueGroup) {
        // Styled rather than flattened. Colour, size and typeface from the file have already been
        // taken off upstream (SubtitleUtils.withoutEmbeddedLook), so what is left is italics and the
        // other marks that carry meaning rather than decoration — and the hint keeps every one of
        // them the first line keeps.
        final SpannableStringBuilder text = new SpannableStringBuilder();
        for (final Cue cue : cueGroup.cues) {
            if (cue.text == null) {
                continue;
            }
            if (text.length() > 0) {
                text.append('\n');
            }
            text.append(trimmed(cue.text));
        }
        show(text, cueGroup.presentationTimeUs / 1000);
    }

    @Override
    public void onCues(final List<Cue> cues) {
        // Dropped: SubtitleOffset sends this deprecated form immediately before the group it belongs
        // to, and the group above is what gets read.
    }

    /** {@code String.trim()} for a styled sequence: the same cut, with the spans still attached. */
    private static CharSequence trimmed(final CharSequence text) {
        int start = 0;
        int end = text.length();
        while (start < end && text.charAt(start) <= ' ') {
            start++;
        }
        while (end > start && text.charAt(end - 1) <= ' ') {
            end--;
        }
        return start == 0 && end == text.length() ? text : text.subSequence(start, end);
    }

    private void show(final CharSequence text, final long atMs) {
        if (TextUtils.equals(current, text)) {
            return;
        }
        final boolean wasSaying = current.length() > 0;
        current = text;
        currentAtMs = atMs;
        if (text.length() > 0) {
            last = text;
            lastAtMs = atMs;
            lastEndedAtMs = C.TIME_UNSET; // it is on screen; nothing has ended it yet
        } else if (wasSaying) {
            // The moment the line went, which is the empty group's whole content.
            lastEndedAtMs = atMs;
        }
        if (peeking && !TextUtils.equals(text, peeked)) {
            // The line this peek was asked about has passed, so the hint goes with it. Posted rather
            // than called straight from here: this runs inside the cue callback, and ending a peek
            // re-enters the layout pass that decides what the hint is allowed to draw.
            stale = false;
            view.removeCallbacks(peekEnd);
            view.post(peekEnd);
        }
        render();
    }

    /**
     * What the hint draws right now — and while a peek is running that is the line it was asked about,
     * whatever has come along since.
     *
     * <p>It used to hand over to the next line as soon as one arrived, which made the grace worse than
     * no grace at all: the hint offered a translation of a line still on screen and then took it away a
     * second into reading it. A peek answers one question. It finishes answering it and goes.
     */
    private CharSequence displayed() {
        return peeking ? peeked : current;
    }

    private void render() {
        if (state == State.HIDDEN) {
            applied = state;
            view.setVisibility(View.GONE);
            return;
        }
        if (applied != state) {
            applied = state;
            applyChrome();
        }
        final CharSequence text = displayed();
        view.setText(text);
        // GONE rather than INVISIBLE: the band above it is reserved by the main line's padding, so a
        // silent hint has to give its height back or it props the layout open for nothing.
        view.setVisibility(text.length() == 0 ? View.GONE : View.VISIBLE);
    }

    /**
     * Everything about the view that follows from the state rather than from the text. Kept out of the
     * per-cue path: swapping a compound drawable in and out asks for a layout whether it changed or
     * not, and a line of dialogue arrives every few seconds.
     */
    private void applyChrome() {
        // Kept whether or not there is a plate, so turning one on does not move the text.
        view.setBackground(plate);
        view.setPadding(padHPx, padVPx, padHPx, padVPx);
        view.setTextColor(textColor);
        view.setTypeface(typeface);
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx);
    }
}
