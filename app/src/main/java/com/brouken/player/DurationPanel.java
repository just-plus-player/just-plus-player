package com.brouken.player;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.SpannableStringBuilder;
import android.text.format.DateFormat;
import android.text.style.RelativeSizeSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Date;
import java.util.Locale;

/**
 * An end-docked panel for typing a duration on a numeric keypad, modelled on VLC's time picker
 * (dialog_time_picker.xml): digits shift in from the right like an alarm clock, so 1-3-0 reads as 1ʰ30ᵐ
 * and a bare 90 means an hour and a half.
 *
 * Taken from that picker: keys with no background, bold and 18sp, carrying a layout weight instead of a
 * width so three of them divide whatever the column measures; a backspace beside the readout, which is the
 * way back from a mistyped digit; the ":00"/":30" keys that fill the minutes in one tap; and two equal
 * actions across the foot. Typed rather than dragged because the values wanted here span minutes to hours,
 * and the ± / SeekBar idiom of {@link OffsetPanel} needs one step size that would serve neither end.
 *
 * Sizing is derived rather than fixed. Key rows take what is left of the panel's height after the parts
 * whose size is set by their text, so the whole thing fits by construction: a docked panel is roughly
 * 304 × 804 dp in portrait but only 360 × 363 dp in landscape, and four finger-sized rows plus a readout
 * plus actions do not fit the latter stacked — hence landscape sets the keypad beside the readout instead
 * of below it, which is the one thing here that VLC's bottom sheet never has to solve.
 */
final class DurationPanel {

    interface Listener {
        /** @param minutes the picked duration, or 0 to turn the timer off */
        void onDurationPicked(int minutes);
    }

    private static final int MAX_MINUTES = 12 * 60;

    private DurationPanel() {
    }

    /**
     * @param insetSource view used to read the window insets for the panel padding (any attached view)
     * @param accent      brand accent for the readout and the Start action
     */
    static Dialog create(final Activity activity, final UiMetrics ui, final View insetSource,
                         final int accent, final String title, final Listener listener) {
        final int[] typed = {0}; // up to 4 digits, read as HHMM

        final Configuration cfg = activity.getResources().getConfiguration();
        final boolean landscape = cfg.orientation == Configuration.ORIENTATION_LANDSCAPE;
        final int hPad = Utils.dpToPx(24) + ui.overscanH();
        final int usableW = ui.pickerWidthPx(cfg) - 2 * hPad;

        // Portrait has height to spare, so the rows simply take VLC's own size. Landscape has to divide what
        // is left after the title and the window insets — and only those: with the keypad beside the readout
        // rather than under it, the readout and the actions cost the rows nothing. Counting them anyway is
        // what squeezed these rows to 33dp.
        final int rowHeight = ui.dp(landscape
                ? Math.max(36, Math.min(52, (cfg.screenHeightDp - 110) / 4 - 8))
                : 52);

        final LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);

        // Left-aligned 18sp medium, as VLC's BottomSheetTitle is — and as every other picker header in
        // this app already is.
        final TextView header = new TextView(activity);
        header.setText(title);
        header.setTextColor(Color.WHITE);
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, ui.textTitle());
        header.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        header.setMinHeight(ui.dp(48));
        header.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(header);

        // Laid out top down with plain margins, exactly as VLC's sheet is — no weight, no vertical centring.
        // Those two put a void above the readout and another below the keypad, and gave the scroll view room
        // to carry the title off the top edge. Nothing here needs to stretch.
        final LinearLayout body = new LinearLayout(activity);
        body.setOrientation(landscape ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        body.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(body);

        // Side by side the width splits 45/55 in the keypad's favour: it has three columns to seat, the
        // readout only its own digits and the two actions.
        final LinearLayout readoutColumn = new LinearLayout(activity);
        readoutColumn.setOrientation(LinearLayout.VERTICAL);
        readoutColumn.setLayoutParams(new LinearLayout.LayoutParams(
                landscape ? Math.round(usableW * 0.45f) : ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        body.addView(readoutColumn);

        final LinearLayout valueRow = new LinearLayout(activity);
        valueRow.setOrientation(LinearLayout.HORIZONTAL);
        valueRow.setGravity(Gravity.CENTER_VERTICAL);
        // A key row's height and margin, so the readout and the backspace share the centre line of the
        // "1 2 3" row instead of hovering a few pixels above it.
        final LinearLayout.LayoutParams valueLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, rowHeight);
        valueLp.topMargin = Utils.dpToPx(8);
        valueRow.setLayoutParams(valueLp);
        readoutColumn.addView(valueRow);

        final TextView value = new TextView(activity);
        // 24sp bold, VLC's size for this readout — not the big light numeral of the skip-offset panel. That
        // one owns its whole panel; this one shares a line with a backspace and a narrow landscape column.
        value.setTextSize(TypedValue.COMPLEX_UNIT_SP, ui.sp(24));
        value.setTypeface(Typeface.DEFAULT_BOLD);
        value.setFontFeatureSettings("tnum"); // fixed-width digits: the readout stops twitching as it fills
        value.setGravity(Gravity.CENTER_VERTICAL);
        value.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        valueRow.addView(value);

        final ImageButton backspace = new ImageButton(activity, null, 0,
                R.style.ExoStyledControls_Button_Bottom);
        backspace.setImageResource(R.drawable.ic_backspace_24dp);
        backspace.setContentDescription(activity.getString(R.string.sleep_timer_backspace));
        valueRow.addView(backspace);

        // Where the duration lands in wall-clock terms — the same phrasing the header uses for the end of
        // the video, since it answers the same question.
        final TextView endsAt = new TextView(activity);
        endsAt.setTextColor(0xB3FFFFFF);
        endsAt.setTextSize(TypedValue.COMPLEX_UNIT_SP, ui.textEndsAt());
        endsAt.setPadding(0, 0, 0, Utils.dpToPx(6));
        readoutColumn.addView(endsAt);

        // Under the readout, not under the title: here the rule is the underline of a field being typed into.
        final View divider = new View(activity);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Utils.dpToPx(1)));
        divider.setBackgroundColor(0x1AFFFFFF);
        readoutColumn.addView(divider);

        final Runnable render = () -> {
            final SpannableStringBuilder text = new SpannableStringBuilder();
            appendUnit(text, String.format(Locale.US, "%d", typed[0] / 100), "h");
            text.append("  ");
            appendUnit(text, String.format(Locale.US, "%02d", typed[0] % 100), "m");
            value.setText(text);
            value.setTextColor(typed[0] == 0 ? Color.WHITE : accent);
            backspace.setEnabled(typed[0] != 0);
            backspace.setAlpha(typed[0] != 0 ? 1f : 0.35f);

            final int total = totalMinutes(typed[0]);
            if (total > 0) {
                final Date end = new Date(System.currentTimeMillis() + total * 60_000L);
                endsAt.setText(activity.getString(R.string.time_ends_at_inline,
                        DateFormat.getTimeFormat(activity).format(end)));
                endsAt.setVisibility(View.VISIBLE);
            } else {
                // INVISIBLE, not GONE: nothing below may shift as the line comes and goes.
                endsAt.setVisibility(View.INVISIBLE);
            }
        };

        final View[] firstKey = new View[1];
        final View[] digitKeys = new View[10];
        final LinearLayout keypad = new LinearLayout(activity);
        keypad.setOrientation(LinearLayout.VERTICAL);
        final LinearLayout.LayoutParams keypadLp = new LinearLayout.LayoutParams(
                landscape ? Math.round(usableW * 0.55f) : ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        if (!landscape) {
            keypadLp.topMargin = Utils.dpToPx(8);
        }
        keypad.setLayoutParams(keypadLp);
        for (int row = 0; row < 3; row++) {
            final LinearLayout line = keyRow(activity, rowHeight);
            for (int col = 0; col < 3; col++) {
                final int digit = row * 3 + col + 1;
                final View key = keyButton(activity, ui, String.valueOf(digit),
                        () -> appendDigit(typed, digit, render));
                digitKeys[digit] = key;
                if (firstKey[0] == null) {
                    firstKey[0] = key;
                }
                line.addView(key);
            }
            keypad.addView(line);
        }
        // Last row as VLC has it: the two minute values worth a shortcut, either side of the zero.
        final LinearLayout lastRow = keyRow(activity, rowHeight);
        lastRow.addView(keyButton(activity, ui, ":00", () -> appendMinutes(typed, 0, render)));
        digitKeys[0] = keyButton(activity, ui, "0", () -> appendDigit(typed, 0, render));
        lastRow.addView(digitKeys[0]);
        lastRow.addView(keyButton(activity, ui, ":30", () -> appendMinutes(typed, 30, render)));
        keypad.addView(lastRow);
        body.addView(keypad);

        // Right-aligned at the foot, the way VLC ends its picker with "remove current" and "ok".
        final LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END);
        final LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsLp.topMargin = Utils.dpToPx(landscape ? 8 : 12);
        actions.setLayoutParams(actionsLp);

        final Dialog dialog = new Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar);

        final TextView stop = actionButton(activity, ui,
                activity.getString(R.string.sleep_timer_stop), Color.WHITE);
        stop.setOnClickListener(v -> {
            listener.onDurationPicked(0);
            dialog.dismiss();
        });
        final TextView start = actionButton(activity, ui,
                activity.getString(R.string.sleep_timer_start), accent);
        start.setOnClickListener(v -> {
            final int total = totalMinutes(typed[0]);
            if (total <= 0) {
                return;
            }
            listener.onDurationPicked(total);
            dialog.dismiss();
        });
        actions.addView(stop);
        actions.addView(start);
        // The foot of its own column either way: under the readout when the keypad is beside it, under
        // everything when the keypad is below it.
        if (landscape) {
            readoutColumn.addView(actions);
        } else {
            root.addView(actions);
        }

        backspace.setOnClickListener(v -> {
            typed[0] /= 10;
            render.run();
        });
        render.run();

        // Backstop only. The sizing above is meant to make the panel fit outright; this catches what it
        // cannot foresee — a large font scale, split screen, a window shape not thought of — so that no
        // action can ever end up somewhere unreachable. No fillViewport: nothing in here wants to stretch.
        final ScrollView scrollView = new ScrollView(activity);
        scrollView.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        Utils.padForPickerInsets(activity, ui, insetSource, scrollView, hPad,
                Utils.dpToPx(16), Utils.dpToPx(20));

        dialog.setContentView(scrollView);
        dialog.setCanceledOnTouchOutside(true);
        // On TV the remote's number keys are the natural way in — arrowing across ten buttons is not.
        dialog.setOnKeyListener((d, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN
                    || keyCode < KeyEvent.KEYCODE_0 || keyCode > KeyEvent.KEYCODE_9) {
                return false;
            }
            digitKeys[keyCode - KeyEvent.KEYCODE_0].performClick();
            return true;
        });
        final Window window = dialog.getWindow();
        if (window != null) {
            // Deliberately NOT fullscreen/edge-to-edge — see OffsetPanel: a fullscreen dialog window makes
            // OxygenOS treat the panel as immersive and demand two back swipes.
            window.setLayout(ui.pickerWidthPx(cfg), ViewGroup.LayoutParams.MATCH_PARENT);
            window.setGravity(Gravity.END);
            window.setBackgroundDrawable(new ColorDrawable(0xF0141414));
        }
        // Only where there is a D-pad. In touch mode this scrolls the title out of sight for nothing, since
        // a focus ring is not drawn there anyway.
        if (firstKey[0] != null && ui.deviceClass == UiMetrics.DeviceClass.TV) {
            firstKey[0].post(firstKey[0]::requestFocus);
        }
        return dialog;
    }

    private static void appendDigit(int[] typed, int digit, Runnable render) {
        if (typed[0] >= 1000) { // already four digits — further taps have nowhere to go
            return;
        }
        typed[0] = typed[0] * 10 + digit;
        render.run();
    }

    /** ":00" / ":30" — fills the minutes in one tap, so a typed "1" becomes 1ʰ00ᵐ or 1ʰ30ᵐ. */
    private static void appendMinutes(int[] typed, int minutes, Runnable render) {
        if (typed[0] >= 100) { // the minutes are already spoken for
            return;
        }
        typed[0] = typed[0] * 100 + minutes;
        render.run();
    }

    /** HHMM as typed → total minutes, capped. Minutes past 59 carry over, so a bare "90" means 1 h 30 m. */
    private static int totalMinutes(int typed) {
        return Math.min(MAX_MINUTES, (typed / 100) * 60 + typed % 100);
    }

    private static void appendUnit(SpannableStringBuilder text, String number, String unit) {
        text.append(number);
        final int start = text.length();
        text.append(unit);
        text.setSpan(new RelativeSizeSpan(0.5f), start, text.length(), 0);
    }

    private static LinearLayout keyRow(final Activity activity, final int rowHeight) {
        final LinearLayout line = new LinearLayout(activity);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setWeightSum(3f);
        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, rowHeight);
        lp.topMargin = Utils.dpToPx(8);
        line.setLayoutParams(lp);
        return line;
    }

    /** A bare key: bold text on nothing, as VLC's are — only the touch ripple marks it out. */
    private static TextView keyButton(final Activity activity, final UiMetrics ui,
                                      final String label, final Runnable onTap) {
        final TextView key = new TextView(activity);
        key.setText(label);
        key.setTextColor(Color.WHITE);
        key.setTextSize(TypedValue.COMPLEX_UNIT_SP, ui.sp(18));
        key.setTypeface(Typeface.DEFAULT_BOLD);
        key.setGravity(Gravity.CENTER);
        key.setClickable(true);
        key.setFocusable(true);
        // Weight, no width: three keys divide the column whatever it measures, at any panel size. Sizing
        // them in pixels instead is what once squeezed this keypad down to its middle column.
        key.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        key.setBackground(new RippleDrawable(ColorStateList.valueOf(0x40FFFFFF), null, null));
        key.setOnClickListener(v -> onTap.run());
        return key;
    }

    /** One of two equal-width actions sharing the foot of the column. */
    private static TextView actionButton(final Activity activity, final UiMetrics ui,
                                        final String label, final int color) {
        final TextView action = new TextView(activity);
        action.setText(label.toUpperCase(Locale.getDefault()));
        action.setTextColor(color);
        action.setTextSize(TypedValue.COMPLEX_UNIT_SP, ui.textAction());
        action.setTypeface(Typeface.DEFAULT_BOLD);
        action.setGravity(Gravity.CENTER);
        action.setClickable(true);
        action.setFocusable(true);
        // One line, always: wrapping once turned "START" into "STAR"/"T". Should a column still come up
        // short, an ellipsis is a legible failure where a broken word is not.
        action.setSingleLine(true);
        action.setPadding(Utils.dpToPx(12), Utils.dpToPx(11), Utils.dpToPx(12), Utils.dpToPx(11));
        final GradientDrawable mask = new GradientDrawable();
        mask.setCornerRadius(Utils.dpToPx(8));
        mask.setColor(Color.WHITE);
        action.setBackground(new RippleDrawable(ColorStateList.valueOf(0x40FFFFFF), null, mask));
        return action;
    }
}
