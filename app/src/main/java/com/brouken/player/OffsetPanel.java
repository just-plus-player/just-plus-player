package com.brouken.player;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
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
 * through the ± buttons (touch) and the D-pad (TV).
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

    private OffsetPanel() {
    }

    /**
     * @param insetSource view used to read the window insets for the panel padding (any attached view)
     * @param accent      brand accent for the readout, track and thumb
     */
    static Dialog create(final Activity activity, final UiMetrics ui, final View insetSource,
                         final int accent, final String title, final double maxSec,
                         final double stepSec, final Line... lines) {
        final LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);

        final TextView header = new TextView(activity);
        header.setText(title);
        header.setTextColor(Color.WHITE);
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, ui.textTitle());
        header.setTypeface(Typeface.DEFAULT_BOLD);
        header.setPadding(0, 0, 0, Utils.dpToPx(14));
        root.addView(header);

        final View divider = new View(activity);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Utils.dpToPx(1)));
        divider.setBackgroundColor(0x1AFFFFFF);
        root.addView(divider);

        final List<Runnable> resets = new ArrayList<>();
        SeekBar firstBar = null;
        // One value gets the big readout it was designed for; two share the height, so they take the
        // clock's size instead. Two 44sp numbers plus their sliders do not fit a phone held sideways,
        // and what fell off the bottom was the second slider — the panel looked like it could only
        // shift the first line.
        final float valueSp = lines.length > 1 ? ui.textClock() : ui.textValue();
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
        final GradientDrawable resetContent = new GradientDrawable();
        resetContent.setCornerRadius(Utils.dpToPx(22));
        resetContent.setColor(0x1AFFFFFF);
        final GradientDrawable resetMask = new GradientDrawable();
        resetMask.setCornerRadius(Utils.dpToPx(22));
        resetMask.setColor(Color.WHITE);
        reset.setBackground(new RippleDrawable(ColorStateList.valueOf(0x40FFFFFF), resetContent, resetMask));
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
        dialog.setCanceledOnTouchOutside(true);
        final Window window = dialog.getWindow();
        if (window != null) {
            // Deliberately NOT fullscreen/edge-to-edge: a fullscreen dialog window makes OxygenOS treat the
            // panel as immersive and apply its two-swipe back-gesture guard. A plain window closes on one back.
            window.setLayout(ui.pickerWidthPx(activity.getResources().getConfiguration()),
                    ViewGroup.LayoutParams.MATCH_PARENT);
            window.setGravity(Gravity.END);
            window.setBackgroundDrawable(new ColorDrawable(0xF0141414));
        }
        if (firstBar != null) {
            final SeekBar focus = firstBar;
            focus.post(focus::requestFocus);
        }
        return dialog;
    }

    /** Caption, readout and slider for one offset. Returns its SeekBar; adds its reset to {@code resets}. */
    private static SeekBar addLine(final Activity activity, final UiMetrics ui, final LinearLayout root,
                                   final int accent, final Line line, final double maxSec,
                                   final double stepSec, final float valueSp,
                                   final List<Runnable> resets) {
        final int trackBg = 0x33FFFFFF;
        final int progressMax = (int) Math.round(2 * maxSec / stepSec);
        final int mid = progressMax / 2;
        final int dragSnap = Math.max(1, (int) Math.round(1 / stepSec)); // drag resolution: 1 s
        final double[] current = {line.initialSec};

        if (line.caption != null) {
            final TextView caption = new TextView(activity);
            caption.setText(line.caption);
            caption.setTextColor(0xB3FFFFFF);
            caption.setTextSize(TypedValue.COMPLEX_UNIT_SP, ui.textAction());
            caption.setGravity(Gravity.CENTER);
            caption.setPadding(0, 0, 0, Utils.dpToPx(4));
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
        root.addView(row);

        // Readout: accent when non-zero, white at rest.
        final Runnable render = () -> {
            value.setText(format(current[0]));
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

    /** "0 s" / "+2.5 s" / "-10 s" — signed, trailing zeros trimmed. */
    static String format(double sec) {
        if (isZero(sec)) {
            return "0 s";
        }
        String s = String.format(Locale.US, "%+.2f", sec);
        if (s.indexOf('.') >= 0) { // trim trailing zeros: "+2.50" -> "+2.5", "+3.00" -> "+3"
            int end = s.length();
            while (end > 0 && s.charAt(end - 1) == '0') {
                end--;
            }
            if (end > 0 && s.charAt(end - 1) == '.') {
                end--;
            }
            s = s.substring(0, end);
        }
        return s + " s";
    }

    private static View verticalSpacer(Activity activity) {
        final View spacer = new View(activity);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return spacer;
    }
}
