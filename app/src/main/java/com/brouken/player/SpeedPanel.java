package com.brouken.player;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.color.MaterialColors;

import java.util.Locale;

/**
 * Playback speed as one end-docked panel: the rate in the middle, a ± either side of it, and five
 * ready-made speeds under it as one segmented control.
 *
 * <p>It replaces a side menu of eight rows, which could say which of the eight was in force and nothing
 * else — 1.15× for a slow talker or 1.9× to finish before the train arrives had no way in at all. The
 * ± is what the menu had no room for: 0.05 a press, from a quarter speed to four times it.
 *
 * <p>Nothing here waits for an OK and nothing closes the panel. Speed is heard rather than read, so
 * every press applies at once and the way back from a wrong one is the next press. Reset stands at the
 * foot even though 1× is one of the eight: "back to normal" is an action, and asking somebody to find
 * the value that happens to mean it is asking them to do the deducing.
 *
 * <p>The readout is the light 40sp numeral {@link OffsetPanel} gives its own value, not the boxed field
 * of {@link DurationPanel}: a box says "type into me", and nothing here is typed. It carries the accent
 * whenever the speed is not 1×, so the panel says "changed" in the same colour the rest of the app
 * uses for "this one is chosen" — and when the speed is one of the eight, the lit segment underneath is
 * saying the same thing about the same value rather than a second thing.
 *
 * <p>Short enough to need no landscape arrangement of its own: 268dp against the 346dp a docked card
 * gets on a phone held sideways, where the sleep timer had to put its keypad beside its readout.
 */
final class SpeedPanel {

    interface Listener {
        void onSpeedPicked(float speed);
    }

    /**
     * The speeds offered outright. Five, which is the most a Material segmented button carries — the
     * eight this started with needed two rows, and two rows of segments are a grid somebody built by
     * hand, not a control the system knows. What is not here is a press or two of ± away, and holding
     * ± walks the whole range.
     */
    static final float[] PRESETS = {0.5f, 0.75f, 1f, 1.5f, 2f};

    private static final float MIN = 0.25f;
    // Past the last preset on purpose: the ± exists for the speeds the eight do not offer, and stopping
    // it dead at 2× would disable it exactly where somebody reaches for it.
    private static final float MAX = 4f;
    private static final float STEP = 0.05f;
    private static final float EPS = 0.001f;

    private SpeedPanel() {
    }

    static Dialog create(final Activity activity, final UiMetrics ui, final String title,
                         final float current, final Listener listener) {
        final float[] speed = {snap(current)};
        final int usableW = ui.panelWidthPx(activity.getResources().getConfiguration())
                - 2 * Utils.dpToPx(24);

        // Built against the appearance choice rather than the player's own dark theme — see
        // OffsetPanel for the same move and Utils.dialogContext for what it resolves.
        final Context ctx = Utils.dialogContext(activity);
        final int onSurface = MaterialColors.getColor(ctx, R.attr.colorOnSurface, Color.WHITE);
        final int accent = MaterialColors.getColor(ctx, R.attr.colorPrimary,
                ContextCompat.getColor(ctx, R.color.brand_accent));

        final LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);

        final TextView header = new TextView(ctx);
        header.setText(title);
        header.setTextColor(onSurface);
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, ui.textTitle());
        header.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        header.setMinHeight(ui.dp(48));
        header.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(Utils.pickerHeader(ctx, ui, header));

        final TextView value = new TextView(ctx);
        value.setTextSize(TypedValue.COMPLEX_UNIT_SP, ui.textValue());
        value.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        value.setFontFeatureSettings("tnum"); // fixed-width digits: the readout stops twitching as it counts
        value.setGravity(Gravity.CENTER);
        value.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final MaterialButton minus = iconButton(ctx, ui, R.drawable.ic_remove_24dp,
                activity.getString(R.string.speed_slower));
        final MaterialButton plus = iconButton(ctx, ui, R.drawable.ic_add_24dp,
                activity.getString(R.string.speed_faster));

        final LinearLayout valueRow = new LinearLayout(ctx);
        valueRow.setOrientation(LinearLayout.HORIZONTAL);
        valueRow.setGravity(Gravity.CENTER_VERTICAL);
        valueRow.addView(minus);
        valueRow.addView(value);
        valueRow.addView(plus);
        // A cluster in the middle of the panel rather than a button at each edge. Pushed to the edges
        // the ± had to be pulled back out by their own padding to line their glyphs up with the title,
        // which put a strip of each button outside the column that holds it — and a view outside its
        // parent's bounds is not merely drawn oddly, it is never handed the touch. Reported as "плюс не
        // всегда нажимается". Fixed rather than wrapped, so the buttons do not shuffle sideways as the
        // number between them changes width.
        final LinearLayout.LayoutParams valueLp = new LinearLayout.LayoutParams(
                Math.min(usableW, ui.dpS(280)), ViewGroup.LayoutParams.WRAP_CONTENT);
        valueLp.gravity = Gravity.CENTER_HORIZONTAL;
        valueLp.topMargin = Utils.dpToPx(8);
        valueRow.setLayoutParams(valueLp);
        root.addView(valueRow);

        // One row, five segments at 71dp on a phone's panel — the shape Material draws for "pick one of
        // these", within the 2 to 5 its segmented button is specified for.
        final MaterialButton[] segments = new MaterialButton[PRESETS.length];
        final MaterialButtonToggleGroup group = new MaterialButtonToggleGroup(ctx);
        group.setSingleSelection(true);
        for (int i = 0; i < PRESETS.length; i++) {
            segments[i] = Utils.pickerSegment(ctx, ui, format(PRESETS[i]));
            group.addView(segments[i], new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        final LinearLayout.LayoutParams groupLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        groupLp.topMargin = Utils.dpToPx(16);
        group.setLayoutParams(groupLp);
        root.addView(group);

        // Named rather than deduced. 1× is one of the eight and picking it does the same thing, but a
        // way back to normal is an action in anybody's head, not a value to go and find — reported as
        // "интуитивно непонятно, что для сброса надо выбрать 1×". Dim while the speed is already normal,
        // so it never claims there is something to undo. The label is the one the skip panel's reset
        // already carries; the two are the same word and the same act.
        final MaterialButton reset = new MaterialButton(ctx, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        reset.setText(activity.getString(R.string.skip_offset_reset));
        reset.setTextSize(TypedValue.COMPLEX_UNIT_SP, ui.textAction());
        reset.setInsetTop(0);
        reset.setInsetBottom(0);
        reset.setMinHeight(ui.dpS(48));
        Utils.quietInk(reset);
        Utils.focusRing(reset);
        final LinearLayout.LayoutParams resetLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        resetLp.gravity = Gravity.CENTER_HORIZONTAL;
        resetLp.topMargin = Utils.dpToPx(16);
        reset.setLayoutParams(resetLp);
        root.addView(reset);

        final Runnable[] render = new Runnable[1];
        final Runnable apply = () -> {
            render[0].run();
            listener.onSpeedPicked(speed[0]);
        };
        render[0] = () -> {
            value.setText(format(speed[0]));
            value.setTextColor(Math.abs(speed[0] - 1f) < EPS ? onSurface : accent);
            minus.setEnabled(speed[0] > MIN + EPS);
            plus.setEnabled(speed[0] < MAX - EPS);
            reset.setEnabled(Math.abs(speed[0] - 1f) >= EPS);
            // The lit segment follows the value rather than the last press: nudged off a ready-made
            // speed, the panel stops claiming that speed is set — and pressed again, the group's own
            // toggling would have cleared it, so the pass puts it back inside the same press.
            final int index = indexOf(speed[0]);
            if (index >= 0) {
                group.check(segments[index].getId());
            } else {
                group.clearChecked();
            }
        };

        for (int i = 0; i < segments.length; i++) {
            final float preset = PRESETS[i];
            segments[i].setOnClickListener(v -> {
                speed[0] = preset;
                apply.run();
            });
        }
        reset.setOnClickListener(v -> {
            speed[0] = 1f;
            apply.run();
        });
        final Runnable slower = () -> {
            speed[0] = snap(Math.max(MIN, speed[0] - STEP));
            apply.run();
        };
        final Runnable faster = () -> {
            speed[0] = snap(Math.min(MAX, speed[0] + STEP));
            apply.run();
        };
        minus.setOnClickListener(v -> slower.run());
        plus.setOnClickListener(v -> faster.run());
        // Held down they walk. Five segments cover the speeds worth a name; everything between and
        // beyond them is 0.05 a press, and a quarter speed is fifteen presses from normal — which is a
        // hold of about a second, and a press nobody has to count.
        repeatWhileHeld(minus, slower);
        repeatWhileHeld(plus, faster);
        render[0].run();

        // Backstop only: the panel is sized to fit outright, and this catches what it cannot foresee —
        // a large font scale, split screen, a window shape not thought of.
        final ScrollView scrollView = new ScrollView(ctx);
        scrollView.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        // Only the sheet's own padding — see Utils.pickerWindow for where the bars and the overscan went.
        scrollView.setPadding(Utils.dpToPx(24), Utils.dpToPx(16),
                Utils.dpToPx(24), Utils.dpToPx(20));

        final Dialog dialog = new Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar);
        Utils.pickerWindow(activity, ui, dialog, scrollView);
        // The theme carries no title bar, so this is the only name a screen reader can announce.
        dialog.setTitle(title);
        // Only where there is a D-pad. The speed in force, so a remote lands on what it came to change;
        // off the eight, the first segment, since the panel would otherwise open with nothing focused.
        if (ui.deviceClass == UiMetrics.DeviceClass.TV) {
            final View focus = segments[Math.max(indexOf(speed[0]), 0)];
            focus.post(focus::requestFocus);
        }
        return dialog;
    }

    /**
     * A press that keeps going while the finger stays down. The view's own pressed state is what ends
     * it — lifting, sliding off, or the button going dim at the end of the range all clear it, so there
     * is nothing to unsubscribe and nothing left running behind a dismissed panel.
     */
    private static void repeatWhileHeld(final View button, final Runnable step) {
        final Runnable[] tick = new Runnable[1];
        tick[0] = () -> {
            if (button.isPressed() && button.isEnabled()) {
                step.run();
                button.postDelayed(tick[0], 80);
            }
        };
        // True, so no ordinary click follows the hold: the first step of a hold is the long press itself.
        button.setOnLongClickListener(v -> {
            step.run();
            button.postDelayed(tick[0], 80);
            return true;
        });
    }

    /** "1×", "1,25×" — the locale's own decimal separator, and no zeroes anybody has to read past. */
    static String format(final float speed) {
        final Locale locale = Locale.getDefault();
        return Utils.trimZeros(String.format(locale, "%.2f", speed), locale) + "×";
    }

    /** Which of the eight this is, or -1 for a speed the ± reached between them. */
    private static int indexOf(final float speed) {
        for (int i = 0; i < PRESETS.length; i++) {
            if (Math.abs(PRESETS[i] - speed) < EPS) {
                return i;
            }
        }
        return -1;
    }

    /** To the nearest step, so a rate arriving from elsewhere lands on the same grid the ± walks. */
    private static float snap(final float speed) {
        return Math.round(Math.min(MAX, Math.max(MIN, speed)) / STEP) * STEP;
    }

    private static MaterialButton iconButton(final Context ctx, final UiMetrics ui, final int icon,
                                             final String description) {
        final MaterialButton button = new MaterialButton(ctx, null,
                com.google.android.material.R.attr.materialIconButtonOutlinedStyle);
        button.setIconResource(icon);
        // Two states, not one colour: these are the only ± in the app that can be disabled — at a
        // quarter speed and at four times it — and a flat ColorStateList had them shine at the ends of
        // the range exactly as brightly as in the middle of it. 97/255 is Material's 38% for disabled
        // content.
        final int ink = MaterialColors.getColor(ctx, R.attr.colorOnSurface, Color.WHITE);
        button.setIconTint(new ColorStateList(
                new int[][]{{-android.R.attr.state_enabled}, {}},
                new int[]{MaterialColors.compositeARGBWithAlpha(ink, 97), ink}));
        button.setContentDescription(description);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        // Material's icon button is a 20dp glyph in 10dp of padding — a 40dp box, and with the insets
        // taken off, 40dp tall. The platform's floor for anything a finger has to hit is 48.
        button.setMinWidth(ui.dpS(48));
        button.setMinHeight(ui.dpS(48));
        button.setMinimumWidth(ui.dpS(48));
        button.setMinimumHeight(ui.dpS(48));
        Utils.focusRing(button);
        return button;
    }
}
