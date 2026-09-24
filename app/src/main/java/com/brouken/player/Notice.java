package com.brouken.player;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.snackbar.Snackbar;

/**
 * The one passing notice this app has, everywhere it has one — and the same plate the player's own volume,
 * brightness, seek and zoom readouts wear (see {@code exo_error_message} in {@code exo_player_view.xml}).
 *
 * <p><b>One look, one line, one at a time.</b> Whatever the app says in passing appears as a pill on
 * {@link #topLine} — under the status bar or the cutout, standing off by R8's {@code max(16dp, overscan)} —
 * and leaves again on its own. The middle of the picture was tried first and is where a message is worst:
 * the frame's centre is the one part of a film nothing may cover, and a plate floating there is the only
 * thing in this app's chrome with no edge to hang from. The top is also where the phone itself puts its
 * volume banner, which is the place the owner named.
 *
 * <p>Sharing a line means the two can never be shown at once: a readout takes the line from a message
 * ({@code CustomPlayerView.readout}) and a message takes it from a readout
 * ({@code PlayerActivity.showNotice}), the way the system's own volume banner replaces whatever was there
 * rather than stacking on it.
 *
 * <p>It is still a {@link Snackbar} underneath — the queue, the timeout, the accessibility announcement
 * and the insets are all already written there, and none of that is what changes when a message moves off
 * the bottom edge. What changes is the plate: it hugs its text instead of spanning the window, it carries a
 * {@code 24dp} glyph and its corner is {@code 28dp}.
 *
 * <p>The glyph is not optional and has no default: every call names the one that says what the message is
 * about — skip, quality, subtitles, the room, the update — because a plate that always wore the app's own
 * mark said nothing the words had not already said. {@code ic_info_24dp} is the honest answer where a
 * message really is just a remark. It is drawn in {@code ink_high} whatever it was authored in, so a glyph
 * borrowed from a light screen cannot arrive black on the plate.
 *
 * <p>The plate is {@code sheet_surface} on {@code ink_high} whatever screen it is over — a notice is an
 * overlay, not part of the screen under it, so it does not follow that screen's ground (DESIGN.md R1).
 * Opaque, the way a panel is and the way a notification is: at the {@code chrome_surface} the snackbar
 * used to wear, the toolbar title under the plate ghosted through it — 39/255 where the letters were
 * against 19/255 beside them, which is text read through text.
 *
 * <p>The margins are written with {@code setLayoutParams} rather than straight onto the object because
 * Material snapshots the margins it is given at that moment and restores the top one from that snapshot
 * every time the insets change; set silently, the top margin is gone by the first frame.
 */
public final class Notice {

    /** The standoff every detached surface here keeps from an edge (DESIGN.md R8). */
    private static final int STANDOFF_DP = 16;
    /**
     * The widest the plate gets. Material stops a dialog at 560dp and gives a snackbar no maximum at all
     * ({@code design_snackbar_max_width} is {@code -1px}), which in landscape on a phone is an 840dp bar
     * carrying three words. A notice is the same kind of thing as a dialog — a transient overlay — so it
     * takes the same ceiling, and below it (a phone held upright) it is full width, as a notification is.
     */
    private static final int MAX_WIDTH_DP = 560;
    /**
     * Everything the plate puts around its text: {@code 8 + 8dp} of its own padding, the content layout's
     * {@code 8 + 8dp} margins, the {@code 24dp} glyph and the {@code 12dp} after it. Subtracted from
     * {@link #MAX_WIDTH_DP} to cap the text, since a plate that hugs cannot be capped by its own width.
     */
    private static final int CHROME_DP = 8 + 8 + 8 + 8 + 24 + 12;
    private static final int ICON_GAP_DP = 12;
    /**
     * Body Large (16sp), the {@code textBody} step of §4 — one size for the plate, whether it carries a
     * sentence or a percentage. Material's snackbar reads 14sp and the readout over video used to read
     * 20sp; neither could stay once the two became one surface, and the step between them is a real token.
     */
    private static final float TEXT_SP = 16;

    /** The plate on screen now, if any, so a readout can take the line back from it. */
    private static Snackbar current;

    private Notice() {
    }

    /**
     * The line every passing thing is drawn on: under the status bar or the cutout, plus the standoff.
     * One function, because the readout in the player view is placed by it too.
     */
    static int topLine(final View host) {
        final UiMetrics ui = UiMetrics.of(host.getContext(), Utils.isTvBox(host.getContext()));
        return topInset(host) + Math.max(ui.dp(STANDOFF_DP), ui.overscanV());
    }

    /** Takes the line back — called when the player has a readout to put there instead. */
    static void dismiss() {
        if (current != null) {
            current.dismiss();
            current = null;
        }
    }

    public static void show(final Activity activity, final int textRes, final boolean longer,
                            final int iconRes) {
        if (activity != null) {
            show(activity, activity.getText(textRes), longer, iconRes);
        }
    }

    public static void show(final Activity activity, final CharSequence text, final boolean longer,
                            final int iconRes) {
        if (activity == null) {
            return;
        }
        final Snackbar bar = make(activity, text, longer, iconRes);
        if (bar == null) {
            // No content view to hang it on — a window on its way out, or one being rebuilt. The
            // platform's toast outlives that; nothing else does.
            Toast.makeText(activity, text, longer ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show();
            return;
        }
        bar.show();
    }

    /** The same plate, handed back unshown, for the one notice that carries an action. */
    public static Snackbar make(final Activity activity, final CharSequence text, final boolean longer,
                                final int iconRes) {
        final ViewGroup host = activity == null ? null : activity.findViewById(android.R.id.content);
        if (host == null) {
            return null;
        }
        final Snackbar bar = Snackbar.make(host, text,
                longer ? Snackbar.LENGTH_LONG : Snackbar.LENGTH_SHORT);
        // Material's slide animation travels up from below the plate's resting place, which at the top of
        // the window reads as coming out of the picture. Fade, plus the drop below.
        bar.setAnimationMode(Snackbar.ANIMATION_MODE_FADE);

        final View plate = bar.getView();
        plate.setBackgroundResource(R.drawable.notice_plate);
        plate.setBackgroundTintList(
                ColorStateList.valueOf(ContextCompat.getColor(activity, R.color.sheet_surface)));

        final UiMetrics ui = UiMetrics.of(activity, Utils.isTvBox(activity));
        final int standoffH = Math.max(ui.dp(STANDOFF_DP), ui.overscanH());

        final ViewGroup.LayoutParams lp = plate.getLayoutParams();
        if (lp instanceof FrameLayout.LayoutParams) {
            ((FrameLayout.LayoutParams) lp).gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        }
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            final ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) lp;
            margins.topMargin = topLine(host);
            margins.leftMargin = standoffH;
            margins.rightMargin = standoffH;
            margins.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            plate.setLayoutParams(margins);
        }

        final TextView label = plate.findViewById(com.google.android.material.R.id.snackbar_text);
        if (label != null) {
            label.setTextColor(ContextCompat.getColor(activity, R.color.ink_high));
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_SP);
            label.setMaxWidth(ui.dp(MAX_WIDTH_DP - CHROME_DP));
            label.setCompoundDrawablesRelativeWithIntrinsicBounds(iconRes, 0, 0, 0);
            label.setCompoundDrawableTintList(
                    ColorStateList.valueOf(ContextCompat.getColor(activity, R.color.ink_high)));
            label.setCompoundDrawablePadding(ui.dp(ICON_GAP_DP));
        }

        current = bar;
        return bar;
    }

    private static int topInset(final View host) {
        final WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(host);
        return insets == null ? 0 : insets.getInsets(
                WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()).top;
    }
}
