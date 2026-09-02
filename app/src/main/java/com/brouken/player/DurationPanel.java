package com.brouken.player;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.color.MaterialColors;

import java.util.Date;
import java.util.Locale;

/**
 * The sleep timer, whole, in one end-docked panel: the ready-made durations as a segmented control,
 * "after the current file" beside them, and a keypad underneath for a length nobody thought to offer.
 *
 * <p>One panel rather than the list-then-keypad it replaced. The list held the same five durations,
 * the end-of-file choice and a "Custom…" row that opened this panel — so the two commonest answers
 * cost a menu each, and the panel behind the last row could not say what was already running. Here
 * every choice is on one surface: what is armed is the checked segment, and what is left of it stands
 * in the readout until the first digit is typed.
 *
 * <p>Taken from Material's own time input (its two fields, corner-small containers with the unit named
 * underneath) and from VLC's picker (dialog_time_picker.xml) for the keypad: digits shift in from the
 * right like an alarm clock, so 1-3-0 reads as 1ʰ30ᵐ and a bare 90 means an hour and a half; keys with
 * no background carrying a layout weight instead of a width; a backspace beside the readout; and the
 * ":00"/":30" keys that fill the minutes in one tap. The fields are a readout and not two inputs
 * because the digits cross them as they shift — there is no field the next digit belongs to, so
 * neither one is ever drawn as the one being filled.
 *
 * <p>A ready-made choice arms as it is pressed and takes the panel with it, the way the list rows did;
 * only a typed length needs Start, which is why Start is the one control here that can be disabled.
 *
 * <p>Sizing is derived rather than fixed. The keypad rows take what is left of the panel's height, so
 * the whole thing fits by construction: a docked panel is roughly 304 × 804 dp in portrait but only
 * 360 × 363 dp in landscape, and the segmented row plus a readout plus four finger-sized rows plus the
 * actions do not fit the latter stacked — hence landscape sets the keypad beside the readout instead
 * of below it, with the durations still spanning the panel where five segments have the width to be hit.
 */
final class DurationPanel {

    interface Listener {
        /** @param minutes the picked duration, or 0 to turn the timer off */
        void onDurationPicked(int minutes);

        /** Stop once the file playing now ends, whenever that is. */
        void onEndOfItemPicked();
    }

    /** The durations offered outright. Five fit a panel's width; anything else is typed. */
    static final int[] PRESETS_MIN = {15, 30, 45, 60, 90};

    private static final int MAX_MINUTES = 12 * 60;

    private DurationPanel() {
    }

    /**
     * @param armedMinutes the duration currently armed, or 0 for none — checks its segment
     * @param endOfItem    whether the end-of-file choice is what is armed
     * @param remainingMs  what is left of an armed duration; shown until the first digit is typed
     */
    static Dialog create(final Activity activity, final UiMetrics ui, final String title,
                         final int armedMinutes, final boolean endOfItem, final long remainingMs,
                         final Listener listener) {
        final int[] typed = {0}; // up to 4 digits, read as HHMM

        final Configuration cfg = activity.getResources().getConfiguration();
        final boolean landscape = cfg.orientation == Configuration.ORIENTATION_LANDSCAPE;
        final int hPad = Utils.dpToPx(24);
        final int usableW = ui.panelWidthPx(cfg) - 2 * hPad;
        final int colGap = Utils.dpToPx(16);
        // Side by side the width splits evenly: three keys to a row on one side, two fields and a
        // backspace on the other, and neither wins by being asked first.
        final int colW = landscape ? (usableW - colGap) / 2 : usableW;

        // Portrait has height to spare, so the rows simply take VLC's own size. Landscape has to divide
        // what is left of the window: a phone held sideways is 411dp tall and the docked card gets 346
        // of that once the margins and the bars are out, of which the padding, the title and the
        // durations row spend 140. Hence the 205 — 64 of insets and margins the panel never sees plus
        // those 140 — and hence the keypad standing beside the readout rather than under it.
        final int rowHeight = ui.dp(landscape
                ? Math.max(36, Math.min(52, (cfg.screenHeightDp - 205) / 4 - 8))
                : 52);

        // Built against the appearance choice rather than the player's own dark theme — see
        // OffsetPanel for the same move and Utils.dialogContext for what it resolves.
        final Context ctx = Utils.dialogContext(activity);
        final int onSurface = MaterialColors.getColor(ctx, R.attr.colorOnSurface, Color.WHITE);
        final int dim = MaterialColors.getColor(ctx, R.attr.colorOnSurfaceVariant,
                ContextCompat.getColor(ctx, R.color.ink_medium));
        // The interface accent, not the chrome's: see OffsetPanel and @color/brand_accent.
        final int accent = MaterialColors.getColor(ctx, R.attr.colorPrimary,
                ContextCompat.getColor(ctx, R.color.brand_accent));

        final Dialog dialog = new Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar);

        final LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);

        // Left-aligned 18sp medium, as every other picker header in this app is.
        final TextView header = new TextView(ctx);
        header.setText(title);
        header.setTextColor(onSurface);
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, ui.textTitle());
        header.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        header.setMinHeight(ui.dp(48));
        header.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(Utils.pickerHeader(ctx, ui, header));

        // The durations across the panel, whichever way it is held: five segments share a full-width
        // row at 60dp or more, and half a landscape panel would leave each of them under the 48dp a
        // finger is entitled to. The same segmented control the settings screen gives the appearance
        // choice and OffsetPanel gives its skip modes — one language for "pick one of these".
        final MaterialButtonToggleGroup durations = new MaterialButtonToggleGroup(ctx);
        durations.setSingleSelection(true);
        final MaterialButton[] segments = new MaterialButton[PRESETS_MIN.length];
        for (int i = 0; i < PRESETS_MIN.length; i++) {
            final int minutes = PRESETS_MIN[i];
            segments[i] = Utils.pickerSegment(ctx, ui,
                    activity.getString(R.string.sleep_timer_minutes, minutes));
            durations.addView(segments[i], new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        final LinearLayout.LayoutParams durationsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        durationsLp.topMargin = Utils.dpToPx(8);
        durations.setLayoutParams(durationsLp);
        root.addView(durations);

        // What is armed is checked by the readout pass below, never here: check() fires the same
        // listener a press does, and a panel that armed the running timer again as it opened would
        // close on the way up. Hence the mute the pass holds while it moves the selection.
        final boolean[] muted = {false};
        durations.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked || muted[0]) {
                return;
            }
            for (int i = 0; i < segments.length; i++) {
                if (segments[i].getId() == checkedId) {
                    listener.onDurationPicked(PRESETS_MIN[i]);
                    dialog.dismiss();
                    return;
                }
            }
        });

        // Laid out top down with plain margins: nothing in here wants to stretch.
        final LinearLayout body = new LinearLayout(ctx);
        body.setOrientation(landscape ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        body.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(body);

        final LinearLayout readoutColumn = new LinearLayout(ctx);
        readoutColumn.setOrientation(LinearLayout.VERTICAL);
        readoutColumn.setLayoutParams(new LinearLayout.LayoutParams(
                landscape ? colW : ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        body.addView(readoutColumn);

        // A duration and "however long this file has left" are one choice — what stops the film — so
        // this wears the segmented control's clothes and holds its own selection. Its own group and not
        // a sixth segment: it is not a length, and the row of five would have had to give up the width.
        final MaterialButtonToggleGroup endOfFile = new MaterialButtonToggleGroup(ctx);
        endOfFile.setSingleSelection(true);
        final MaterialButton endOfFileButton = Utils.pickerSegment(ctx, ui,
                activity.getString(R.string.sleep_timer_end_of_item));
        endOfFile.addView(endOfFileButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        final LinearLayout.LayoutParams endOfFileLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        endOfFileLp.topMargin = Utils.dpToPx(8);
        endOfFile.setLayoutParams(endOfFileLp);
        readoutColumn.addView(endOfFile);
        endOfFile.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked && !muted[0]) {
                listener.onEndOfItemPicked();
                dialog.dismiss();
            }
        });

        // Two fields, Material's own shape for them, and the unit named underneath — but a readout
        // rather than an input: the keypad below fills them, and a tap on one picks nothing.
        final LinearLayout valueRow = new LinearLayout(ctx);
        valueRow.setOrientation(LinearLayout.HORIZONTAL);
        // Top, so the colon and the backspace centre on the fields themselves rather than on the
        // fields plus the unit named under them. Centred as well where the row is wider than the two
        // fields, the spacer and the backspace it holds — packed from the start edge, the pair of
        // fields sat 23dp to the left of the panel's middle.
        valueRow.setGravity(landscape ? Gravity.TOP : (Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        final LinearLayout.LayoutParams valueLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        valueLp.topMargin = Utils.dpToPx(24); // the gap that separates the picked from the typed
        valueRow.setLayoutParams(valueLp);
        readoutColumn.addView(valueRow);

        final int backspaceBox = ui.dp(48);
        final int colonW = ui.dp(20);
        // Portrait has the room to hang the backspace off the end and still centre the fields, which is
        // what the leading spacer buys; half a landscape panel does not, so there the pair sits from the
        // start edge and the backspace follows it.
        final boolean centred = !landscape;
        if (centred) {
            valueRow.addView(new View(ctx), new LinearLayout.LayoutParams(backspaceBox, 1));
        }
        // Material's field is 96dp wide; narrower only when the column cannot seat two of them.
        final int boxW = Math.min(ui.dpS(96),
                (colW - colonW - backspaceBox - (centred ? backspaceBox : 0)) / 2);
        // And 72dp tall, except where the height is the scarce dimension: sideways the column has 206dp
        // for the end-of-file button, the readout, its wall-clock line and the actions, and at Material's
        // own size the actions came to rest below the bottom of the card.
        final int boxH = ui.dpS(landscape ? 56 : 72);
        final int fieldFill = MaterialColors.getColor(ctx, R.attr.colorSurfaceContainerHighest,
                ContextCompat.getColor(ctx, R.color.sheet_surface));

        // The unit under each field is what portrait has the room for; sideways the pair of fields, the
        // colon between them and the keypad beside them say the same thing in the height that is left.
        final TextView hours = field(ctx, ui, boxW, boxH, fieldFill);
        valueRow.addView(landscape ? hours
                : unitColumn(ctx, ui, hours, activity.getString(R.string.duration_hours), dim));
        final TextView colon = new TextView(ctx);
        colon.setText(":");
        colon.setTextColor(dim);
        colon.setTextSize(TypedValue.COMPLEX_UNIT_SP, ui.textValue());
        colon.setGravity(Gravity.CENTER);
        final LinearLayout.LayoutParams colonLp = new LinearLayout.LayoutParams(colonW, boxH);
        valueRow.addView(colon, colonLp);
        final TextView minutes = field(ctx, ui, boxW, boxH, fieldFill);
        valueRow.addView(landscape ? minutes
                : unitColumn(ctx, ui, minutes, activity.getString(R.string.duration_minutes), dim));

        final ImageButton backspace = new ImageButton(ctx, null, 0,
                R.style.ExoStyledControls_Button_Bottom);
        backspace.setImageResource(R.drawable.ic_backspace_24dp);
        backspace.setContentDescription(activity.getString(R.string.sleep_timer_backspace));
        // The style behind it draws for the chrome over video, which is never light: white on a light
        // panel is a glyph that is not there. Tinted like the unit labels beside it instead.
        backspace.setImageTintList(ColorStateList.valueOf(dim));
        // And it scales fitXY, which is fine for the square buttons of the control row but not here: the
        // button is as tall as the field it belongs to, so the 24dp glyph came out 24 × 48 and read as a
        // narrow sliver. Drawn at its own size, centred in whatever the button measures.
        backspace.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        valueRow.addView(backspace, new LinearLayout.LayoutParams(backspaceBox, boxH));

        // Where the duration lands in wall-clock terms — the same phrasing the header uses for the end
        // of the video, since it answers the same question.
        final TextView endsAt = new TextView(ctx);
        endsAt.setTextColor(dim);
        endsAt.setTextSize(TypedValue.COMPLEX_UNIT_SP, ui.textEndsAt());
        endsAt.setGravity(centred ? Gravity.CENTER_HORIZONTAL : Gravity.START);
        endsAt.setPadding(0, Utils.dpToPx(4), 0, 0);
        readoutColumn.addView(endsAt);

        // Right-aligned at the foot, as Material ends a sheet: the way out of a running timer, then the
        // one action a typed length needs.
        final LinearLayout actions = new LinearLayout(ctx);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END);
        final LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsLp.topMargin = Utils.dpToPx(landscape ? 8 : 16);
        actions.setLayoutParams(actionsLp);

        final MaterialButton off = action(ctx, ui, ctx.getString(R.string.sleep_timer_off));
        off.setOnClickListener(v -> {
            listener.onDurationPicked(0);
            dialog.dismiss();
        });
        final MaterialButton start = action(ctx, ui, ctx.getString(R.string.sleep_timer_start));
        final LinearLayout.LayoutParams startLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        startLp.setMarginStart(Utils.dpToPx(8));
        start.setLayoutParams(startLp);
        start.setOnClickListener(v -> {
            listener.onDurationPicked(totalMinutes(typed[0]));
            dialog.dismiss();
        });
        actions.addView(off);
        actions.addView(start);

        // The deadline, read once: what is left of the timer is a snapshot the panel does not tick, but
        // the clock time it ends at is a fixed point, and recomputing it from that snapshot walked it
        // forward a minute for every minute the panel stayed open.
        final long armedEndMs = System.currentTimeMillis() + remainingMs;

        final Runnable render = () -> {
            // Nothing typed yet: the fields report what is running rather than a pair of zeroes, so a
            // panel opened to check on the timer answers without anything being pressed. Dimmed, because
            // it is the timer speaking and not the digits anyone has entered.
            final boolean showing = typed[0] == 0 && remainingMs > 0;
            final long shown = showing ? remainingMs : totalMinutes(typed[0]) * 60_000L;
            hours.setText(String.format(Locale.US, "%02d", shown / 3_600_000L));
            minutes.setText(String.format(Locale.US, "%02d", shown / 60_000L % 60));
            final int ink = typed[0] == 0 ? dim : accent;
            hours.setTextColor(ink);
            minutes.setTextColor(ink);
            backspace.setEnabled(typed[0] != 0);
            backspace.setAlpha(typed[0] != 0 ? 1f : 0.35f);

            if (shown > 0) {
                final Date end = new Date(showing ? armedEndMs : System.currentTimeMillis() + shown);
                endsAt.setText(activity.getString(R.string.time_ends_at_inline,
                        DateFormat.getTimeFormat(activity).format(end)));
                endsAt.setVisibility(View.VISIBLE);
            } else {
                // INVISIBLE, not GONE: nothing below may shift as the line comes and goes.
                endsAt.setVisibility(View.INVISIBLE);
            }
            // The only control here that waits: everything else on the panel arms something as it is
            // pressed, and Start has nothing to arm until a digit has been typed.
            start.setEnabled(typed[0] != 0);

            // One selection on the panel at a time. A digit typed over a running timer is an answer of
            // its own, so the segment the timer checked lets go of it — otherwise the accent stood for
            // "this is running" and "this is what you typed" in the same breath. Backspaced away, the
            // timer takes its segment back.
            muted[0] = true;
            final int armed = typed[0] == 0 ? armedIndex(armedMinutes, endOfItem) : -1;
            if (armed >= 0) {
                durations.check(segments[armed].getId());
            } else {
                durations.clearChecked();
            }
            if (typed[0] == 0 && endOfItem) {
                endOfFile.check(endOfFileButton.getId());
            } else {
                endOfFile.clearChecked();
            }
            muted[0] = false;
        };

        final View[] digitKeys = new View[10];
        final LinearLayout keypad = new LinearLayout(ctx);
        keypad.setOrientation(LinearLayout.VERTICAL);
        final LinearLayout.LayoutParams keypadLp = new LinearLayout.LayoutParams(
                landscape ? colW : ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        keypadLp.topMargin = Utils.dpToPx(8);
        if (landscape) {
            keypadLp.setMarginStart(colGap);
        }
        keypad.setLayoutParams(keypadLp);
        for (int row = 0; row < 3; row++) {
            final LinearLayout line = keyRow(ctx, rowHeight);
            for (int col = 0; col < 3; col++) {
                final int digit = row * 3 + col + 1;
                final View key = keyButton(ctx, ui, String.valueOf(digit),
                        () -> appendDigit(typed, digit, render));
                digitKeys[digit] = key;
                line.addView(key);
            }
            keypad.addView(line);
        }
        // Last row as VLC has it: the two minute values worth a shortcut, either side of the zero.
        final LinearLayout lastRow = keyRow(ctx, rowHeight);
        lastRow.addView(keyButton(ctx, ui, ":00", () -> appendMinutes(typed, 0, render)));
        digitKeys[0] = keyButton(ctx, ui, "0", () -> appendDigit(typed, 0, render));
        lastRow.addView(digitKeys[0]);
        lastRow.addView(keyButton(ctx, ui, ":30", () -> appendMinutes(typed, 30, render)));
        keypad.addView(lastRow);
        body.addView(keypad);

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
        final ScrollView scrollView = new ScrollView(ctx);
        scrollView.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        // Only the sheet's own padding now — see Utils.pickerWindow for where the bars and the
        // overscan went.
        scrollView.setPadding(hPad, Utils.dpToPx(16), hPad, Utils.dpToPx(20));

        Utils.pickerWindow(activity, ui, dialog, scrollView);
        // On TV the remote's number keys are the natural way in — arrowing across ten buttons is not.
        dialog.setOnKeyListener((d, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN
                    || keyCode < KeyEvent.KEYCODE_0 || keyCode > KeyEvent.KEYCODE_9) {
                return false;
            }
            digitKeys[keyCode - KeyEvent.KEYCODE_0].performClick();
            return true;
        });
        // Only where there is a D-pad. In touch mode this scrolls the title out of sight for nothing,
        // since a focus ring is not drawn there anyway. The durations rather than the keypad: they are
        // the panel's first decision, and they are what a remote can reach in one press.
        if (ui.deviceClass == UiMetrics.DeviceClass.TV) {
            final int armed = armedIndex(armedMinutes, endOfItem);
            final View focus = segments[Math.max(armed, 0)];
            focus.post(focus::requestFocus);
        }
        return dialog;
    }

    private static int armedIndex(final int armedMinutes, final boolean endOfItem) {
        if (!endOfItem) {
            for (int i = 0; i < PRESETS_MIN.length; i++) {
                if (PRESETS_MIN[i] == armedMinutes) {
                    return i;
                }
            }
        }
        return -1;
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

    /** One field of the readout: Material's time-input container, filled and cornered as it draws one. */
    private static TextView field(final Context ctx, final UiMetrics ui,
                                  final int width, final int height, final int fill) {
        final TextView box = new TextView(ctx);
        box.setTextSize(TypedValue.COMPLEX_UNIT_SP, ui.textValue());
        box.setFontFeatureSettings("tnum"); // fixed-width digits: the readout stops twitching as it fills
        box.setGravity(Gravity.CENTER);
        box.setMinHeight(height); // a minimum, not a height: the digits follow the system font scale
        final GradientDrawable container = new GradientDrawable();
        container.setCornerRadius(ui.pillCorner()); // 8dp, Material's small corner, as its field wears
        container.setColor(fill);
        box.setBackground(container);
        box.setLayoutParams(new LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT));
        return box;
    }

    /** A field with its unit named underneath, the way Material labels the two of a time input. */
    private static LinearLayout unitColumn(final Context ctx, final UiMetrics ui, final TextView box,
                                           final String label, final int ink) {
        final LinearLayout column = new LinearLayout(ctx);
        column.setOrientation(LinearLayout.VERTICAL);
        column.addView(box);
        final TextView caption = new TextView(ctx);
        caption.setText(label);
        caption.setTextColor(ink);
        caption.setTextSize(TypedValue.COMPLEX_UNIT_SP, ui.textInfo());
        caption.setGravity(Gravity.CENTER_HORIZONTAL);
        caption.setPadding(0, Utils.dpToPx(4), 0, 0);
        column.addView(caption, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return column;
    }

    /** One of the two actions at the foot — outlined, as every button this app's panels carry is. */
    private static MaterialButton action(final Context ctx, final UiMetrics ui, final String label) {
        final MaterialButton button = new MaterialButton(ctx, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(label);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, ui.textAction());
        button.setMaxLines(1);
        // Material insets a button by 6dp top and bottom to reach its 48dp touch target from a 36dp
        // box. This panel sizes its own rows, so the inset only shortens them.
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setMinHeight(ui.dpS(48));
        Utils.focusRing(button);
        return button;
    }

    private static LinearLayout keyRow(final Context ctx, final int rowHeight) {
        final LinearLayout line = new LinearLayout(ctx);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setWeightSum(3f);
        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, rowHeight);
        lp.topMargin = Utils.dpToPx(8);
        line.setLayoutParams(lp);
        return line;
    }

    /** A bare key: bold text on nothing, as VLC's are — only the touch ripple marks it out. */
    private static TextView keyButton(final Context ctx, final UiMetrics ui,
                                      final String label, final Runnable onTap) {
        final TextView key = new TextView(ctx);
        key.setText(label);
        key.setTextColor(MaterialColors.getColor(ctx, R.attr.colorOnSurface, Color.WHITE));
        key.setTextSize(TypedValue.COMPLEX_UNIT_SP, ui.sp(18));
        key.setTypeface(Typeface.DEFAULT_BOLD);
        key.setGravity(Gravity.CENTER);
        key.setClickable(true);
        key.setFocusable(true);
        // Weight, no width: three keys divide the column whatever it measures, at any panel size. Sizing
        // them in pixels instead is what once squeezed this keypad down to its middle column.
        key.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        key.setBackground(Utils.pickerRow(ctx, Color.TRANSPARENT));
        key.setOnClickListener(v -> onTap.run());
        return key;
    }
}
