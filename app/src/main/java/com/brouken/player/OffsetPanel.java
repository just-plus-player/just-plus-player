package com.brouken.player;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.widget.TextViewCompat;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.slider.LabelFormatter;
import com.google.android.material.slider.Slider;

import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A reusable end-docked panel for adjusting signed offsets in seconds (skip timing, subtitle timing):
 * a large centred readout, a brand-tinted Material {@link Slider} flanked by −/+ icon buttons, and a
 * Reset button. What the panel picks rather than nudges is a {@link MaterialButtonToggleGroup}, the
 * same segmented control the settings screen uses for the appearance choice.
 *
 * <p>A panel can carry more than one of those, which is what {@link Line} is for. Two subtitle lines
 * are two offsets, and two menu rows for one setting is a row too many — so they are stacked here
 * instead, each with its own caption, readout and slider. With a single line no caption is drawn and
 * the panel is exactly what it was before there was a second one.
 *
 * Input is split by device idiom, because one step size cannot serve both: a finger crossing the
 * narrow phone panel covers the whole ± range, so a raw drag moves the value in ragged sub-second
 * hops. Dragging therefore snaps to whole seconds, while the fine {@code stepSec} stays reachable
 * through the ± buttons. A remote gets the coarse step instead: a focused slider answers left and
 * right itself, so the ± buttons beside it cannot be reached by a D-pad at all — the step a press
 * moves is sized for that in {@link #addLine}.
 *
 * The panel owns the current values and reports every change through {@link Listener}; the caller
 * only holds the returned dialog so it can dismiss it.
 */
final class OffsetPanel {

    /**
     * The panel's vertical rhythm, on Material's 8dp grid, at two densities.
     *
     * <p>Two, because a phone held sideways gives the card about 355dp and the panel at its roomy rhythm
     * wants some 410dp — the weighted spacers this replaced hid that by collapsing to nothing, which is
     * how Reset came to sit against the slider. Stated gaps have to admit the shortfall instead, so a
     * short canvas gets the tight column and everything else gets the roomy one.
     */
    private static final class Rhythm {
        private final int title;    // title to the first control
        private final int block;    // between two things that are not one thing
        private final int readout;  // a readout to the slider it belongs to
        private final int pad;      // the card's own padding

        static Rhythm of(final Configuration cfg) {
            return cfg.screenHeightDp < 500 ? new Rhythm(16, 16, 4, 16) : new Rhythm(24, 32, 8, 24);
        }

        private Rhythm(int title, int block, int readout, int pad) {
            this.title = Utils.dpToPx(title);
            this.block = Utils.dpToPx(block);
            this.readout = Utils.dpToPx(readout);
            this.pad = Utils.dpToPx(pad);
        }
    }

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
     * One thing the panel picks rather than nudges: a segmented control, the option in force checked.
     *
     * <p>It lives in this panel and not in a menu of its own because what is being picked and what is
     * being nudged are one thing to the person watching — how this film's skips behave. A segmented
     * control rather than a list: four short words fit across the panel, and a choice that shows all of
     * its options at once is one press to change from a remote as well as from a finger. No caption:
     * the panel's title already says what is being chosen, and a third label in the same style as the
     * two below it turned the panel into a list of look-alikes.
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

    static Dialog create(final Activity activity, final UiMetrics ui,
                         final String title, final double maxSec,
                         final double stepSec, final Line... lines) {
        return create(activity, ui, title, maxSec, stepSec, null, lines);
    }

    /**
     * @param choices picked rather than nudged, drawn above the sliders; null or empty for none
     */
    static Dialog create(final Activity activity, final UiMetrics ui,
                         final String title, final double maxSec,
                         final double stepSec, final Choice[] choices, final Line... lines) {
        // Every view in here is built against the appearance choice, not against the player's own dark
        // theme, so the panel is light when the app is light and black under AMOLED. A
        // ContextThemeWrapper keeps the activity's window token, which the dialog still needs.
        final Context ctx = Utils.dialogContext(activity);
        final int onSurface = MaterialColors.getColor(ctx, R.attr.colorOnSurface, Color.WHITE);
        // The interface accent, not the chrome's: this panel is a surface, and @color/brand is the
        // coral that lives over video. One accent per world — see @color/brand_accent.
        final int accent = MaterialColors.getColor(ctx, R.attr.colorPrimary,
                ContextCompat.getColor(ctx, R.color.brand_accent));
        final Rhythm rhythm = Rhythm.of(activity.getResources().getConfiguration());
        final LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);

        final TextView header = new TextView(ctx);
        header.setText(title);
        ViewCompat.setAccessibilityHeading(header, true);
        header.setTextColor(onSurface);
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, ui.textTitle());
        header.setTypeface(Typeface.DEFAULT_BOLD);
        // Space, not a rule: the hairline that used to sit here had a caption under it, and once the
        // caption went it landed a few pixels above the row of pills and read as their top border.
        root.addView(header);
        root.addView(gap(ctx, rhythm.title));

        final List<Runnable> resets = new ArrayList<>();
        final boolean picking = choices != null && choices.length > 0;
        MaterialButton firstPill = null;
        if (picking) {
            for (final Choice choice : choices) {
                final MaterialButton lit = addChoice(ctx, ui, root, choice, resets);
                if (firstPill == null) {
                    firstPill = lit;
                }
            }
        }

        Slider firstBar = null;
        // One value gets the big readout it was designed for; two share the height, so they take the
        // clock's size instead. Two 44sp numbers plus their sliders do not fit a phone held sideways,
        // and what fell off the bottom was the second slider — the panel looked like it could only
        // shift the first line.
        // A choice row costs the same room as a second slider, so the readout gives up the same height
        // for it. At the full size the panel did not fit its own window: the title was pushed off the
        // top and the reset pill off the bottom.
        final float valueSp = lines.length > 1 || picking ? ui.textClock() : ui.textValue();
        // Fixed gaps on an 8dp grid rather than weighted spacers. The spacers shared out whatever the
        // full-height panel had left over, so the rhythm was a different one on every screen — and on a
        // phone in landscape what was left over was 8dp, which put Reset against the slider. A card sized
        // to its content has nothing left over to share, so the gaps have to be stated.
        boolean firstBlock = !picking;
        for (final Line line : lines) {
            if (!firstBlock) {
                root.addView(gap(ctx, rhythm.block));
            }
            firstBlock = false;
            final Slider bar = addLine(ctx, ui, root, accent, line, maxSec, stepSec, valueSp,
                    rhythm, resets);
            if (firstBar == null) {
                firstBar = bar;
            }
        }

        root.addView(gap(ctx, rhythm.block));

        // Outlined, like the segmented control above it: one button family for the whole panel. Material
        // draws its own focus and press states, which is what replaced the hand-drawn ring here.
        final MaterialButton reset = new MaterialButton(ctx, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        reset.setText(ctx.getString(R.string.skip_offset_reset));
        reset.setTextSize(TypedValue.COMPLEX_UNIT_SP, ui.textAction());
        // Material insets a button by 6dp top and bottom to reach its 48dp touch target from a 36dp
        // box. This panel sizes its own rows, so the inset only shortens them.
        reset.setInsetTop(0);
        reset.setInsetBottom(0);
        reset.setMinHeight(ui.dpS(48)); // a target this small is missed as often as it is hit
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

        // Only the sheet's own padding now: Utils.pickerWindow insets the card from the system bars
        // and the overscan, so nothing here has to know about either.
        root.setPadding(Utils.dpToPx(24), rhythm.pad, Utils.dpToPx(24), rhythm.pad);

        // Scrolled, because a clipped panel is a panel that lies: the readouts stayed on screen while
        // the slider under the second one did not. No fillViewport — nothing in here stretches now that
        // the card is sized to its content, and the sliders drag across, so nothing fights the scroll.
        final ScrollView scroller = new ScrollView(ctx);
        scroller.addView(root);

        final Dialog dialog = new Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar);
        Utils.pickerWindow(activity, ui, dialog, scroller);
        // The theme carries no title bar, so this is the only name a screen reader can announce.
        dialog.setTitle(title);
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
     * One choice as a Material segmented control, the option in force checked.
     *
     * <p>Two earlier attempts are worth not repeating. The first drew the option in force as the accent
     * at a third of its alpha with accent text, which measured 1.1:1 against its neighbours and read as
     * the one option that had been switched off. The second filled it with the accent and lettered it
     * in the panel's own near-black — unmistakable, but a shape this app drew by hand and nothing else
     * in it shared. {@link MaterialButtonToggleGroup} is the same control the settings screen gives the
     * appearance choice, so the two now say "pick one of these" in one language, and checked state
     * reaches a screen reader without any of it depending on colour.
     *
     * @return the button to hand the first focus to
     */
    private static MaterialButton addChoice(final Context ctx, final UiMetrics ui,
                                            final LinearLayout root, final Choice choice,
                                            final List<Runnable> resets) {
        final MaterialButtonToggleGroup group = new MaterialButtonToggleGroup(ctx);
        group.setSingleSelection(true);
        // The panel opens with nothing checked when the settings disagree with themselves, and this only
        // stops a *press* from clearing the last one — it does not force a selection at bind time.
        group.setSelectionRequired(true);

        final MaterialButton[] pills = new MaterialButton[choice.values.length];
        for (int i = 0; i < choice.values.length; i++) {
            final MaterialButton pill = new MaterialButton(ctx, null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle);
            pill.setId(View.generateViewId()); // the group tracks its buttons by id
            pill.setText(choice.labels[i]);
            pill.setMaxLines(1);
            pill.setInsetTop(0);
            pill.setInsetBottom(0);
            pill.setMinHeight(ui.dpS(48)); // the platform's floor for anything a finger has to hit
            pill.setPadding(ui.dpS(4), pill.getPaddingTop(), ui.dpS(4), pill.getPaddingBottom());
            // Shrunk rather than wrapped or clipped: the widest label already fills its share of the
            // row at the ordinary size, so a longer language or a system font a notch up used to break
            // one word across two lines and leave the row ragged.
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                    pill, (int) ui.textAction() - 4, (int) ui.textAction(), 1,
                    TypedValue.COMPLEX_UNIT_SP);
            // Equal shares of the row rather than each button's own width: four words of different
            // lengths read as a set this way, and the widest of them decides nothing. The group is a
            // LinearLayout, so weights work and it joins the segments into one control itself.
            group.addView(pill, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            pills[i] = pill;
        }

        final int start = indexOf(choice.values, choice.current);
        if (start >= 0) {
            group.check(pills[start].getId());
        }
        // Reset checks a button itself, and a programmatic check fires the same listener a press does.
        final boolean[] muted = {false};
        group.addOnButtonCheckedListener((g, checkedId, isChecked) -> {
            if (!isChecked || muted[0]) {
                return;
            }
            for (int i = 0; i < pills.length; i++) {
                if (pills[i].getId() == checkedId) {
                    choice.listener.onPicked(choice.values[i]);
                    return;
                }
            }
        });

        root.addView(group);
        resets.add(() -> {
            // Back to whatever the settings say, which is what "reset" means for a panel that only ever
            // held this session's answers. Without it there was no way back from a choice at all.
            final int inherited = indexOf(choice.values, choice.inherited);
            muted[0] = true;
            if (inherited < 0) {
                group.clearChecked();
            } else {
                group.check(pills[inherited].getId());
            }
            muted[0] = false;
            choice.listener.onPicked(null);
        });
        return pills[Math.max(0, start)];
    }

    private static int indexOf(final String[] values, final String value) {
        for (int i = 0; value != null && i < values.length; i++) {
            if (values[i].equals(value)) {
                return i;
            }
        }
        return -1; // nothing lit: the panel is not going to guess which one is in force
    }

    /** Caption, readout and slider for one offset. Returns its Slider; adds its reset to {@code resets}. */
    private static Slider addLine(final Context ctx, final UiMetrics ui, final LinearLayout root,
                                  final int accent, final Line line, final double maxSec,
                                  final double stepSec, final float valueSp, final Rhythm rhythm,
                                  final List<Runnable> resets) {
        // The unplayed half of a track is a surface, not white: white is invisible on a light panel.
        final int trackBg = MaterialColors.getColor(ctx, R.attr.colorSurfaceVariant,
                ContextCompat.getColor(ctx, R.color.track_white));
        final int onSurface = MaterialColors.getColor(ctx, R.attr.colorOnSurface, Color.WHITE);
        final int progressMax = (int) Math.round(2 * maxSec / stepSec);
        final int mid = progressMax / 2;
        final int dragSnap = Math.max(1, (int) Math.round(1 / stepSec)); // drag resolution: 1 s
        final double[] current = {line.initialSec};

        if (line.caption != null) {
            final TextView caption = new TextView(ctx);
            caption.setText(line.caption);
            caption.setTextColor(MaterialColors.getColor(ctx, R.attr.colorOnSurfaceVariant,
                    ContextCompat.getColor(ctx, R.color.ink_medium)));
            caption.setTextSize(TypedValue.COMPLEX_UNIT_SP, ui.textAction());
            caption.setGravity(Gravity.CENTER);
            // A caption belongs to what is under it: the gap above it is the block's, drawn by the
            // caller, and all it owns is the hair of space down to its own readout.
            caption.setPadding(0, 0, 0, Utils.dpToPx(4));
            root.addView(caption);
        }

        final TextView value = new TextView(ctx);
        value.setTextSize(TypedValue.COMPLEX_UNIT_SP, valueSp);
        value.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        value.setFontFeatureSettings("tnum"); // fixed-width digits: the readout stops twitching as it counts
        value.setGravity(Gravity.CENTER);
        value.setPadding(0, 0, 0, rhythm.readout);
        root.addView(value);

        // Counted in steps, not in seconds: valueFrom/valueTo/stepSize are floats, and a Slider refuses
        // a range that is not a whole number of steps — ±180 s at 0.1 s is exactly the sum that does not
        // survive float division. Whole steps keep the arithmetic that was already here.
        final Slider seekBar = new Slider(ctx);
        seekBar.setValueFrom(0f);
        seekBar.setValueTo(progressMax);
        seekBar.setStepSize(1f);
        seekBar.setValue(Math.round(line.initialSec / stepSec) + mid);
        // A tick per step is 3600 dots across the subtitle range.
        seekBar.setTickVisible(false);
        // The readout above is the label, and it is four times the size of the bubble.
        seekBar.setLabelBehavior(LabelFormatter.LABEL_GONE);
        seekBar.setThumbTintList(ColorStateList.valueOf(accent));
        seekBar.setTrackActiveTintList(ColorStateList.valueOf(accent));
        seekBar.setTrackInactiveTintList(ColorStateList.valueOf(trackBg));
        // The one stop on the D-pad path that had no ring. Material rings a focused thumb in the
        // accent, which here is the colour of the thumb and of the track under it — measured
        // 1.38:1 against that track, where every other stop in this panel reads 12.99:1.
        seekBar.setHaloTintList(ContextCompat.getColorStateList(ctx, R.color.focus_halo));
        seekBar.setThumbStrokeColor(ContextCompat.getColorStateList(ctx, R.color.focus_ring));
        seekBar.setThumbStrokeWidth(Utils.dpToPx(2));

        // D-pad step, scaled to the range instead of the slider's own one-step-per-press. Left to
        // itself it made the wide subtitle range unreachable from a remote: measured on a television,
        // five presses moved the value 2.5 s, so ±180 s was 360 presses to one edge with the thumb
        // barely leaving the middle. A press is now a ninetieth of the range, floored at 2 × stepSec so
        // the narrow skip panel keeps exactly the step it had. The fine step stays on the ± buttons,
        // which is where it belongs. Slider has no key-increment setter, hence the key listener.
        final int keyStep = Math.max(2, (int) Math.round(maxSec / 90 / stepSec));

        final MaterialButton minus = iconButton(ctx, R.drawable.ic_remove_24dp, "-");
        final MaterialButton plus = iconButton(ctx, R.drawable.ic_add_24dp, "+");

        final LinearLayout row = new LinearLayout(ctx);
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
            value.setText(format(ctx, current[0]));
            value.setTextColor(isZero(current[0]) ? onSurface : accent);
        };
        render.run();
        final Runnable apply = () -> {
            render.run();
            line.listener.onOffsetChanged(current[0]);
        };

        final boolean[] dragging = {false};
        seekBar.addOnChangeListener((sb, v, fromUser) -> {
            if (!fromUser) {
                return;
            }
            final int progress = Math.round(v);
            int p = progress;
            if (dragging[0]) {
                // Snap the finger to whole seconds. setValue re-enters with fromUser=false, so the
                // value is applied here and the nested callback is a no-op.
                p = Math.round((float) p / dragSnap) * dragSnap;
                if (p != progress) {
                    sb.setValue(p);
                }
            }
            current[0] = (p - mid) * stepSec;
            apply.run();
        });
        seekBar.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(@NonNull Slider sb) {
                dragging[0] = true;
            }

            @Override
            public void onStopTrackingTouch(@NonNull Slider sb) {
                dragging[0] = false;
            }
        });
        seekBar.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) {
                return false;
            }
            int dir = keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -1
                    : keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? +1 : 0;
            if (dir == 0) {
                return false;
            }
            if (v.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) {
                dir = -dir; // the slider mirrors, so left is more and right is less
            }
            step(seekBar, dir * keyStep, progressMax, mid, stepSec, current, apply);
            return true;
        });
        minus.setOnClickListener(v -> step(seekBar, -1, progressMax, mid, stepSec, current, apply));
        plus.setOnClickListener(v -> step(seekBar, +1, progressMax, mid, stepSec, current, apply));
        resets.add(() -> {
            seekBar.setValue(mid);
            current[0] = 0;
            apply.run();
        });
        return seekBar;
    }

    /** A ± button: Material's icon button, as legible on a light panel as on a dark one. */
    private static MaterialButton iconButton(final Context ctx, final int icon,
                                             final String description) {
        final MaterialButton button = new MaterialButton(ctx, null,
                com.google.android.material.R.attr.materialIconButtonStyle);
        button.setIconResource(icon);
        button.setIconTint(ColorStateList.valueOf(
                MaterialColors.getColor(ctx, R.attr.colorOnSurface, Color.WHITE)));
        button.setContentDescription(description);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        return button;
    }

    private static void step(Slider seekBar, int delta, int progressMax, int mid, double stepSec,
                             double[] current, Runnable apply) {
        final int p = Math.min(progressMax, Math.max(0, Math.round(seekBar.getValue()) + delta));
        seekBar.setValue(p);
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

    private static View gap(final Context ctx, final int heightPx) {
        final View spacer = new View(ctx);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, heightPx));
        return spacer;
    }
}
