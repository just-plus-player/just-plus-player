package com.brouken.player;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.StateSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.widget.TextViewCompat;

import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A reusable end-docked panel for adjusting signed offsets in seconds (skip timing, subtitle timing):
 * a large centred readout, a brand-tinted SeekBar flanked by −/+ icon buttons, and a Reset pill.
 *
 * <p>A panel can carry more than one of those, which is what {@link Line} is for. Two subtitle lines
 * are two offsets, and two menu rows for one setting is a row too many — so they are stacked here
 * instead, each with its own caption, readout and slider. With a single line no caption is drawn and
 * the panel is exactly what it was before there was a second one.
 *
 * Input is split by device idiom, because one step size cannot serve both: a finger crossing the
 * narrow phone panel covers the whole ± range, so a raw drag moves the value in ragged sub-second
 * hops. Dragging therefore snaps to whole seconds, while the fine {@code stepSec} stays reachable
 * through the ± buttons. A remote gets the coarse step instead: a focused {@code SeekBar} answers
 * left and right itself, so the ± buttons beside it cannot be reached by a D-pad at all — the step
 * a press moves is sized for that in {@link #addLine}.
 *
 * The panel owns the current values and reports every change through {@link Listener}; the caller
 * only holds the returned dialog so it can dismiss it.
 */
final class OffsetPanel {

    interface Listener {
        void onOffsetChanged(double sec);
    }

    /** One offset the panel adjusts: what to call it, where it starts, and who to tell. */
    static final class Line {
        private final CharSequence caption;
        private final double initialSec;
        private final Listener listener;

        /** @param caption named only when the panel carries more than one; null draws no caption */
        Line(final CharSequence caption, final double initialSec, final Listener listener) {
            this.caption = caption;
            this.initialSec = initialSec;
            this.listener = listener;
        }
    }

    /**
     * One thing the panel picks rather than nudges: a row of pills, the one in force lit.
     *
     * <p>It lives in this panel and not in a menu of its own because what is being picked and what is
     * being nudged are one thing to the person watching — how this film's skips behave. Pills rather
     * than a list: four short words fit across the panel, and a choice that shows all of its options at
     * once is one press to change from a remote as well as from a finger. No caption: the panel's title
     * already says what is being chosen, and a third label in the same style as the two below it turned
     * the panel into a list of look-alikes.
     */
    static final class Choice {

        interface Picked {
            /** @param value the chosen option, or null to go back to following the settings */
            void onPicked(String value);
        }

        private final CharSequence[] labels;
        private final String[] values;
        private final String current;
        private final String inherited;
        private final Picked listener;

        /**
         * @param current   what is in force, lit; null lights nothing (the settings disagree with
         *                  themselves and no choice has been made yet)
         * @param inherited what the settings say, lit again when the panel is reset; may be null
         */
        Choice(final CharSequence[] labels, final String[] values, final String current,
               final String inherited, final Picked listener) {
            this.labels = labels;
            this.values = values;
            this.current = current;
            this.inherited = inherited;
            this.listener = listener;
        }
    }

    private OffsetPanel() {
    }

    /**
     * @param insetSource view used to read the window insets for the panel padding (any attached view)
     * @param accent      brand accent for the readout, track and thumb
     */
    static Dialog create(final Activity activity, final UiMetrics ui, final View insetSource,
                         final int accent, final String title, final double maxSec,
                         final double stepSec, final Line... lines) {
        return create(activity, ui, insetSource, accent, title, maxSec, stepSec, null, lines);
    }

    /**
     * @param choices picked rather than nudged, drawn above the sliders; null or empty for none
     */
    static Dialog create(final Activity activity, final UiMetrics ui, final View insetSource,
                         final int accent, final String title, final double maxSec,
                         final double stepSec, final Choice[] choices, final Line... lines) {
        final LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);

        final TextView header = new TextView(activity);
        header.setText(title);
        ViewCompat.setAccessibilityHeading(header, true);
        header.setTextColor(Color.WHITE);
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, ui.textTitle());
        header.setTypeface(Typeface.DEFAULT_BOLD);
        // Space, not a rule: the hairline that used to sit here had a caption under it, and once the
        // caption went it landed a few pixels above the row of pills and read as their top border.
        header.setPadding(0, 0, 0, Utils.dpToPx(20));
        root.addView(header);

        final List<Runnable> resets = new ArrayList<>();
        final boolean picking = choices != null && choices.length > 0;
        TextView firstPill = null;
        if (picking) {
            for (final Choice choice : choices) {
                final TextView lit = addChoice(activity, ui, root, accent, choice, resets);
                if (firstPill == null) {
                    firstPill = lit;
                }
            }
        }

        SeekBar firstBar = null;
        // One value gets the big readout it was designed for; two share the height, so they take the
        // clock's size instead. Two 44sp numbers plus their sliders do not fit a phone held sideways,
        // and what fell off the bottom was the second slider — the panel looked like it could only
        // shift the first line.
        // A choice row costs the same room as a second slider, so the readout gives up the same height
        // for it. At the full size the panel did not fit its own window: the title was pushed off the
        // top and the reset pill off the bottom.
        final float valueSp = lines.length > 1 || picking ? ui.textClock() : ui.textValue();
        for (final Line line : lines) {
            root.addView(verticalSpacer(activity));
            final SeekBar bar = addLine(activity, ui, root, accent, line, maxSec, stepSec, valueSp, resets);
            if (firstBar == null) {
                firstBar = bar;
            }
        }

        root.addView(verticalSpacer(activity));

        final TextView reset = new TextView(activity);
        reset.setText(activity.getString(R.string.skip_offset_reset));
        reset.setTextColor(Color.WHITE);
        reset.setTextSize(TypedValue.COMPLEX_UNIT_SP, ui.textAction());
        reset.setGravity(Gravity.CENTER);
        reset.setClickable(true);
        reset.setFocusable(true);
        reset.setPadding(Utils.dpToPx(28), Utils.dpToPx(11), Utils.dpToPx(28), Utils.dpToPx(11));
        reset.setMinHeight(ui.dpS(48)); // a pill this small is missed as often as it is hit
        // The same focus edge the pills take: a ripple is touch feedback, not something a remote across
        // a room can find, and this pill is the last stop of the D-pad path through here.
        final int resetCorner = Utils.dpToPx(22);
        reset.setBackground(new RippleDrawable(
                ColorStateList.valueOf(ContextCompat.getColor(activity, R.color.ripple_chrome)),
                pillFill(activity, accent, false, resetCorner), roundMask(resetCorner)));
        final LinearLayout.LayoutParams resetLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        resetLp.gravity = Gravity.CENTER_HORIZONTAL;
        reset.setLayoutParams(resetLp);
        // One pill for the panel rather than one per slider: every value it clears is on screen and goes
        // white as it happens, and dragging a slider back to the middle undoes it in a second.
        reset.setOnClickListener(v -> {
            for (final Runnable one : resets) {
                one.run();
            }
        });
        root.addView(reset);

        Utils.padForPickerInsets(activity, ui, insetSource, root, Utils.dpToPx(24) + ui.overscanH(),
                Utils.dpToPx(20), Utils.dpToPx(24));

        // Scrolled, because a clipped panel is a panel that lies: the readouts stayed on screen while
        // the slider under the second one did not. fillViewport keeps the weighted spacers centring the
        // content while it still fits, and the SeekBars drag across, so nothing fights the scroll.
        final ScrollView scroller = new ScrollView(activity);
        scroller.setFillViewport(true);
        scroller.addView(root);

        final Dialog dialog = new Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar);
        dialog.setContentView(scroller);
        // The theme carries no title bar, so this is the only name a screen reader can announce.
        dialog.setTitle(title);
        dialog.setCanceledOnTouchOutside(true);
        final Window window = dialog.getWindow();
        if (window != null) {
            // Deliberately NOT fullscreen/edge-to-edge: a fullscreen dialog window makes OxygenOS treat the
            // panel as immersive and apply its two-swipe back-gesture guard. A plain window closes on one back.
            window.setLayout(ui.pickerWidthPx(activity.getResources().getConfiguration()),
                    ViewGroup.LayoutParams.MATCH_PARENT);
            window.setGravity(Gravity.END);
            window.setBackgroundDrawable(
                    new ColorDrawable(ContextCompat.getColor(activity, R.color.panel_surface)));
        }
        // The choice, not the slider: it is the panel's first decision and it is at the top, so a
        // remote lands on it and the scroller stays where the title is. Focusing the slider scrolled
        // the panel to its own bottom the moment it opened.
        final View focus = firstPill != null ? firstPill : firstBar;
        if (focus != null) {
            focus.post(focus::requestFocus);
        }
        return dialog;
    }

    /**
     * A row of pills for one choice, the one in force lit — and lit means the brightest thing in the
     * row, not a tinted version of it.
     *
     * <p>That is the whole of the second attempt at this row. The first drew the option in force as the
     * accent at a third of its alpha with accent text, which measured 1.1:1 against its neighbours and
     * read as the one option that had been switched off. Filled with the accent and lettered in the
     * panel's own near-black, it is unmistakable, it carries the same colour the readouts use for "you
     * changed this", and {@code setSelected} says the same thing again to a screen reader — which no
     * amount of colour can.
     *
     * @return the pill to hand the first focus to
     */
    private static TextView addChoice(final Activity activity, final UiMetrics ui,
                                      final LinearLayout root, final int accent, final Choice choice,
                                      final List<Runnable> resets) {
        final int corner = Utils.dpToPx(16);
        final LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        final TextView[] pills = new TextView[choice.values.length];
        final int[] picked = {indexOf(choice.values, choice.current)};
        final Runnable render = () -> {
            for (int i = 0; i < pills.length; i++) {
                final boolean on = i == picked[0];
                pills[i].setBackground(new RippleDrawable(
                        ColorStateList.valueOf(ContextCompat.getColor(activity, R.color.ripple_chrome)),
                        pillFill(activity, accent, on, corner), roundMask(corner)));
                pills[i].setTextColor(ContextCompat.getColor(activity,
                        on ? R.color.ink_on_accent : R.color.ink_medium));
                pills[i].setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
                // Said again without colour, for a screen reader and for anyone who cannot tell these
                // two shades apart.
                pills[i].setSelected(on);
            }
        };
        for (int i = 0; i < choice.values.length; i++) {
            final TextView pill = new TextView(activity);
            pill.setText(choice.labels[i]);
            pill.setGravity(Gravity.CENTER);
            pill.setClickable(true);
            pill.setFocusable(true);
            pill.setMaxLines(1);
            // Shrunk rather than wrapped or clipped: the widest label already fills its share of the
            // row at the ordinary size, so a longer language or a system font a notch up used to break
            // one word across two lines and leave the row ragged.
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                    pill, (int) ui.textAction() - 4, (int) ui.textAction(), 1,
                    TypedValue.COMPLEX_UNIT_SP);
            pill.setPadding(ui.dpS(4), ui.dpS(12), ui.dpS(4), ui.dpS(12));
            pill.setMinHeight(ui.dpS(48)); // the platform's floor for anything a finger has to hit
            // Equal shares of the row rather than each pill's own width: four words of different
            // lengths read as a set this way, and the widest of them decides nothing.
            final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            // Gaps between the pills only: an outer margin here would set the row in from the title
            // above it by the width of the gap, which is exactly the misalignment it looks like.
            lp.leftMargin = i == 0 ? 0 : Utils.dpToPx(3);
            lp.rightMargin = i == choice.values.length - 1 ? 0 : Utils.dpToPx(3);
            pill.setLayoutParams(lp);
            final int index = i;
            pill.setOnClickListener(v -> {
                if (picked[0] == index) {
                    return;
                }
                picked[0] = index;
                render.run();
                choice.listener.onPicked(choice.values[index]);
            });
            pills[i] = pill;
            row.addView(pill);
        }
        render.run();
        root.addView(row);
        resets.add(() -> {
            // Back to whatever the settings say, which is what "reset" means for a panel that only ever
            // held this session's answers. Without it there was no way back from a choice at all.
            picked[0] = indexOf(choice.values, choice.inherited);
            render.run();
            choice.listener.onPicked(null);
        });
        return pills[Math.max(0, picked[0])];
    }

    private static int indexOf(final String[] values, final String value) {
        for (int i = 0; value != null && i < values.length; i++) {
            if (values[i].equals(value)) {
                return i;
            }
        }
        return -1; // nothing lit: the panel is not going to guess which one is in force
    }

    /** Pill background: filled with the accent while in force, and edged in white while focused. */
    private static Drawable pillFill(final Context context, final int accent, final boolean on,
                                     final int corner) {
        final StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused},
                pillShape(context, accent, on, corner, true));
        states.addState(StateSet.WILD_CARD, pillShape(context, accent, on, corner, false));
        return states;
    }

    private static Drawable pillShape(final Context context, final int accent, final boolean on,
                                      final int corner, final boolean focused) {
        final GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(corner);
        shape.setColor(on ? accent : ContextCompat.getColor(context, R.color.pill_off_fill));
        if (focused) {
            // The accent, like the Skip pill's own focus ring — and white only on the pill that is
            // already the accent, where a coral edge would not show. One signal and one language: an
            // edge appears, nothing moves and nothing changes size, so the row keeps its rhythm.
            shape.setStroke(Utils.dpToPx(2), on ? Color.WHITE : accent);
        }
        return shape;
    }

    private static Drawable roundMask(final int corner) {
        final GradientDrawable mask = new GradientDrawable();
        mask.setCornerRadius(corner);
        mask.setColor(Color.WHITE);
        return mask;
    }

    /** Caption, readout and slider for one offset. Returns its SeekBar; adds its reset to {@code resets}. */
    private static SeekBar addLine(final Activity activity, final UiMetrics ui, final LinearLayout root,
                                   final int accent, final Line line, final double maxSec,
                                   final double stepSec, final float valueSp,
                                   final List<Runnable> resets) {
        final int trackBg = ContextCompat.getColor(activity, R.color.track_white);
        final int progressMax = (int) Math.round(2 * maxSec / stepSec);
        final int mid = progressMax / 2;
        final int dragSnap = Math.max(1, (int) Math.round(1 / stepSec)); // drag resolution: 1 s
        final double[] current = {line.initialSec};

        if (line.caption != null) {
            final TextView caption = new TextView(activity);
            caption.setText(line.caption);
            caption.setTextColor(ContextCompat.getColor(activity, R.color.ink_medium));
            caption.setTextSize(TypedValue.COMPLEX_UNIT_SP, ui.textAction());
            caption.setGravity(Gravity.CENTER);
            // Asymmetric on purpose, and fixed rather than left to the weighted spacers: a caption
            // belongs to what is under it, and the block above it needs a gap that does not vanish
            // when the panel fills up. The spacers only share out what is left over — when there is
            // nothing left over they collapse to nothing and two blocks end up touching.
            caption.setPadding(0, ui.dpS(28), 0, Utils.dpToPx(4));
            root.addView(caption);
        }

        final TextView value = new TextView(activity);
        value.setTextSize(TypedValue.COMPLEX_UNIT_SP, valueSp);
        value.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        value.setFontFeatureSettings("tnum"); // fixed-width digits: the readout stops twitching as it counts
        value.setGravity(Gravity.CENTER);
        value.setPadding(0, 0, 0, Utils.dpToPx(20));
        root.addView(value);

        final SeekBar seekBar = new SeekBar(activity);
        seekBar.setMax(progressMax);
        // D-pad step, scaled to the range instead of fixed at 2 × stepSec. Fixed, it made the wide
        // subtitle range unreachable from a remote: measured on a television, five presses moved the
        // value 2.5 s, so ±180 s was 360 presses to one edge with the thumb barely leaving the middle.
        // A press is now a ninetieth of the range, floored at 2 × stepSec so the narrow skip panel keeps
        // exactly the step it had. The fine step stays on the ± buttons, which is where it belongs.
        seekBar.setKeyProgressIncrement(Math.max(2, (int) Math.round(maxSec / 90 / stepSec)));
        seekBar.setProgress((int) Math.round(line.initialSec / stepSec) + mid);
        seekBar.setFocusable(true);
        seekBar.setSplitTrack(false);
        seekBar.setProgressTintList(ColorStateList.valueOf(accent));
        seekBar.setThumbTintList(ColorStateList.valueOf(accent));
        seekBar.setProgressBackgroundTintList(ColorStateList.valueOf(trackBg));

        final ImageButton minus = new ImageButton(activity, null, 0, R.style.ExoStyledControls_Button_Bottom);
        minus.setImageResource(R.drawable.ic_remove_24dp);
        minus.setContentDescription("-");
        final ImageButton plus = new ImageButton(activity, null, 0, R.style.ExoStyledControls_Button_Bottom);
        plus.setImageResource(R.drawable.ic_add_24dp);
        plus.setContentDescription("+");

        final LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        final LinearLayout.LayoutParams seekLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        seekLp.leftMargin = Utils.dpToPx(6);
        seekLp.rightMargin = Utils.dpToPx(6);
        seekBar.setLayoutParams(seekLp);
        row.addView(minus);
        row.addView(seekBar);
        row.addView(plus);
        // Optical alignment: each ± button is a 24dp glyph in 12dp of its own padding, so the row is
        // pulled out by that padding. What lines up with the title and the pills is the glyph.
        final LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.leftMargin = -Utils.dpToPx(12);
        rowLp.rightMargin = -Utils.dpToPx(12);
        row.setLayoutParams(rowLp);
        root.addView(row);

        // Readout: accent when non-zero, white at rest.
        final Runnable render = () -> {
            value.setText(format(activity, current[0]));
            value.setTextColor(isZero(current[0]) ? Color.WHITE : accent);
        };
        render.run();
        final Runnable apply = () -> {
            render.run();
            line.listener.onOffsetChanged(current[0]);
        };

        final boolean[] dragging = {false};
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (!fromUser) {
                    return;
                }
                int p = progress;
                if (dragging[0]) {
                    // Snap the finger to whole seconds. setProgress re-enters with fromUser=false, so the
                    // value is applied here and the nested callback is a no-op.
                    p = Math.round((float) p / dragSnap) * dragSnap;
                    if (p != progress) {
                        sb.setProgress(p);
                    }
                }
                current[0] = (p - mid) * stepSec;
                apply.run();
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
                dragging[0] = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
                dragging[0] = false;
            }
        });
        minus.setOnClickListener(v -> step(seekBar, -1, progressMax, mid, stepSec, current, apply));
        plus.setOnClickListener(v -> step(seekBar, +1, progressMax, mid, stepSec, current, apply));
        resets.add(() -> {
            seekBar.setProgress(mid);
            current[0] = 0;
            apply.run();
        });
        return seekBar;
    }

    private static void step(SeekBar seekBar, int delta, int progressMax, int mid, double stepSec,
                             double[] current, Runnable apply) {
        final int p = Math.min(progressMax, Math.max(0, seekBar.getProgress() + delta));
        seekBar.setProgress(p);
        current[0] = (p - mid) * stepSec;
        apply.run();
    }

    private static boolean isZero(double sec) {
        return Math.abs(sec) < 0.001;
    }

    /**
     * "0 s" / "+2.5 s" / "-10 s" — signed, trailing zeros trimmed, and in the viewer's own language.
     *
     * <p>Both halves used to be English: the unit was a literal {@code " s"} and the number was
     * formatted against {@code Locale.US}, so a Ukrainian panel read "0 s" two rows under a Ukrainian
     * caption and printed "+2.5" where the language writes "+2,5".
     */
    static String format(final Context context, final double sec) {
        final Locale locale = Locale.getDefault();
        String number = "0";
        if (!isZero(sec)) {
            number = String.format(locale, "%+.2f", sec);
            // Trim trailing zeros: "+2,50" -> "+2,5", "+3,00" -> "+3". Against the locale's own
            // separator, which is not a dot everywhere this app is read.
            final char point = DecimalFormatSymbols.getInstance(locale).getDecimalSeparator();
            int end = number.length();
            while (end > 0 && number.charAt(end - 1) == '0') {
                end--;
            }
            if (end > 0 && number.charAt(end - 1) == point) {
                end--;
            }
            number = number.substring(0, end);
        }
        return context.getString(R.string.offset_seconds, number);
    }

    private static View verticalSpacer(Activity activity) {
        final View spacer = new View(activity);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return spacer;
    }
}
