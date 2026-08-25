package com.brouken.player;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

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
 */
final class SecondarySubtitles implements TextOutput {

    /** Hard cap on the hint: a longer one would climb out of its band and onto the main line. */
    private static final int MAX_LINES = 2;

    private final TextView view;

    /**
     * The line as it stands right now, kept whether or not it is on screen. Showing the hint on
     * demand then draws what is already known instead of waiting for the next cue to come round.
     */
    private CharSequence current = "";
    private boolean visible = true;

    SecondarySubtitles(final TextView view) {
        this.view = view;
    }

    /**
     * Hides the line without forgetting it — the lock screen, picture-in-picture, and the hint being
     * asked for rather than always shown all come through here.
     */
    void setVisible(final boolean visible) {
        if (this.visible != visible) {
            this.visible = visible;
            render();
        }
    }

    boolean isVisible() {
        return visible;
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
        view.setTextColor(textColor);
        view.setTypeface(typeface);
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx);
        if (backgroundColor == Color.TRANSPARENT) {
            view.setBackground(null);
        } else {
            final GradientDrawable plate = new GradientDrawable();
            plate.setCornerRadius(cornerPx);
            plate.setColor(backgroundColor);
            view.setBackground(plate);
        }
        // Kept whether or not there is a plate, so turning one on does not move the text.
        view.setPadding(padHPx, padVPx, padHPx, padVPx);
    }

    /** Takes the line off screen and forgets what was on it. */
    void clear() {
        show("");
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
        show(text);
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

    private void show(final CharSequence text) {
        if (current.toString().equals(text.toString())) {
            return;
        }
        current = text;
        render();
    }

    private void render() {
        final boolean empty = current.length() == 0;
        view.setText(current);
        view.setMaxLines(MAX_LINES);
        // GONE rather than INVISIBLE: the band above it is reserved by the main line's padding, so a
        // silent hint has to give its height back or it props the layout open for nothing.
        view.setVisibility(visible && !empty ? View.VISIBLE : View.GONE);
    }
}
