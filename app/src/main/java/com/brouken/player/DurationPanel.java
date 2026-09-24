package com.brouken.player;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
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
import android.widget.FrameLayout;
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
        final int usableW = Dialogs.panelContentPx(activity, ui, hPad);
        // One column, whichever way the window is held. Sideways it used to be two - the readout and
        // the actions on one side, the keypad on the other - and that put every control of the panel
        // into half of 264dp: the field came out 36dp wide, the digits wrapped, and the Start button
        // was cut off by the card's own edge. Meanwhile the bottom 150dp of the card stood empty. The
        // panel was saving a height it had and spending a width it did not.
        final int colW = usableW;

        // Sideways the keypad turns: four columns by three rows rather than three by four. Three rows
        // of 48dp with their gaps are 160, which is exactly what the readout's column beside it needs
        // for its own three controls — so the two columns match line for line, and every key is the
        // size of every other control in the panel instead of the 36dp that were left over. The digits
        // keep their 3x3 block and the fourth column takes what is not a digit; a keypad is read by the
        // shape of that block, and the shape does not move.
        //
        // A short window sideways cannot hold both ways of naming a duration. Measured on a phone held
        // sideways — 360dp tall, of which insets and margins take 88 and the card's own padding and
        // title another 84 — the body is left 132dp, and the keypad alone wants 168 at its smallest
        // legal row. The ready-made durations are the 56dp that unblock it, and they are the half the
        // keypad can say in two presses; a length nobody thought to offer has no other way in at all.
        // So they stand down on a window this short, and nowhere else: a television is 541dp tall and
        // keeps both, as does any tablet.
        // The five durations are always there. They are one press each and they are what a sleep
        // timer is actually set to; it was they that stood down on a short window, which left the rare
        // case - typing a length nobody thought to offer - taking the room from the common one.
        //
        // The keypad is what goes instead, and only where the window genuinely cannot hold it: in one
        // column it wants four rows and about 200dp, and a telephone held sideways has around 360dp of
        // window before insets. A television at 540 keeps it, as does a tablet, as does every portrait
        // window. Where it goes, so does the readout it fills - a field nothing can type into is a
        // decoration - and what is armed is said by the line that names the hour it ends at.
        // One panel, whichever way the window is held and whatever it is held on.
        //
        // The keypad is gone. It was four rows and about 200dp, which is what made the panel need
        // two columns sideways, and two columns is what cut the digits and the Start button off. It
        // was never the way a length got set either: the five durations are one press and cover the
        // case, and what they do not cover is now a minus and a plus - five minutes each, which is
        // the grain a sleep timer is nudged in. A remote still types the digits straight in.
        //
        // What that costs, said plainly: a mistyped digit on a television can no longer be taken
        // back one at a time. A duration press starts again, and the minus walks it down.
        final boolean presets = true;
        // Portrait has height to spare, so the rows simply take VLC's own size. Landscape divides what
        // is left: 205 is 64dp of insets and margins the panel never sees plus the 140 the padding, the
        // title and the durations row spend — less those 56 when the durations are not there.
        // Sideways every row is the smallest a finger is entitled to and not a pixel more: the whole
        // panel has to stand on the screen at once, and on a television at 540dp it is the sum of these
        // that decides whether it does.
        final int rowHeight = landscape ? ui.dpS(48) : ui.dp(52);
        final int gap = Utils.dpToPx(8);

        // Built against the appearance choice rather than the player's own dark theme — see
        // OffsetPanel for the same move and Utils.dialogContext for what it resolves.
        final Context ctx = Dialogs.dialogContext(activity);
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
        // Sideways the panel is counted in single figures of dp: the header gives up what it can
        // without the title touching the durations under it.
        header.setMinHeight(ui.dp(48));
        header.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(Dialogs.pickerHeader(ctx, ui, header));

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
        durationsLp.topMargin = gap;
        durations.setLayoutParams(durationsLp);
        if (presets) {
            root.addView(durations);
        }

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
        body.setOrientation(LinearLayout.VERTICAL);
        body.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(body);

        final LinearLayout readoutColumn = new LinearLayout(ctx);
        readoutColumn.setOrientation(LinearLayout.VERTICAL);
        readoutColumn.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
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
        // A horizontal LinearLayout lines its children up by the baseline of their text unless told not
        // to, and that is what cut the fields off. The row is as tall as the colon and the backspace
        // beside them; the fields, shifted down to put their baseline on the colon's, then ran 5dp past
        // its bottom and were clipped there — square corners under rounded ones, and the digits' feet
        // gone with them. They are all centred in boxes of the same height, so the baseline they would
        // be aligned to is the one they already share.
        valueRow.setBaselineAligned(false);
        // Centred both ways. It used to hang from the top, so that the round controls beside the
        // fields cleared the unit named under each of them - and with that label gone there is nothing
        // to clear: a slot the height of a field, hung from the top of a row a taller field sets, put
        // the minus and the plus visibly above the digits they belong to.
        valueRow.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL);
        final LinearLayout.LayoutParams valueLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        // The gap that separates the picked from the typed. Sideways there is no height to spend on
        // saying it twice — the column already sets the readout apart by standing it under a button.
        valueLp.topMargin = landscape ? gap : Utils.dpToPx(24);
        valueRow.setLayoutParams(valueLp);
        readoutColumn.addView(valueRow);

        // dpS and not dp: Utils.iconButton sizes its glyph and its padding in the scaled unit, so a
        // slot handed it the unscaled 48 is smaller than the button it holds - and a MaterialButton
        // that does not fit its own content lays the icon against paddingStart instead of centring it.
        // Measured on tv_720p before this line changed: the minus and the plus stood 9px right of the
        // middle of their own circles. On a telephone the two units are the same number, which is why
        // it was never seen there.
        final int backspaceBox = ui.dpS(48);
        // Material's floor between two touch targets, and the reason it is needed here rather than
        // assumed: the button used to be a bare glyph, so nothing of it reached the field. A circle with
        // an edge does, and at no gap at all it read as attached to the minutes.
        final int backspaceGap = ui.dp(8);
        final int colonW = ui.dp(20);
        // Portrait has the room to hang the backspace off the end and still centre the fields, which is
        // what the leading spacer buys; half a landscape panel does not, so there the pair sits from the
        // start edge and the backspace follows it.
        final boolean centred = false;
        // The pair of fields sits between the two of them, which is what centres it: a minus on one
        // side and a plus on the other, each the size of every other round control in these panels.
        final MaterialButton less = Dialogs.iconButton(ctx, ui, R.drawable.ic_remove_24dp,
                R.string.sleep_timer_less, true);
        final MaterialButton more = Dialogs.iconButton(ctx, ui, R.drawable.ic_add_24dp,
                R.string.sleep_timer_more, true);
        // Material's field is 96dp wide; narrower only when the column cannot seat two of them.
        // Less a few dp of slack: at exactly the width that is left the backspace's own edge landed
        // on the card's, and a circle with its rim on the boundary reads as a circle cut in half.
        // Two round controls and a colon come out of the width before the fields do, and a few dp of
        // slack after that: at exactly what was left, a circle's rim landed on the card's own edge.
        final int boxW = Math.min(ui.dpS(96),
                (colW - colonW - (backspaceBox + backspaceGap) * 2 - Utils.dpToPx(8)) / 2);
        // And 72dp tall, except where the height is the scarce dimension: sideways the column has 206dp
        // for the end-of-file button, the readout, its wall-clock line and the actions, and at Material's
        // own size the actions came to rest below the bottom of the card.
        // Material's 72dp field upright. Sideways it is exactly one row of the keypad standing beside
        // it — the readout, the button above it and the actions below then draw the same three lines as
        // the keys do, which is the whole point of putting them side by side. The digits are 40sp,
        // whose cap height is about 28dp, so they sit in 48 without touching its edges.
        final int boxH = landscape ? rowHeight : ui.dpS(72);
        final int fieldFill = MaterialColors.getColor(ctx, R.attr.colorSurfaceContainerHighest,
                ContextCompat.getColor(ctx, R.color.sheet_surface));

        // The unit under each field is what portrait has the room for; sideways the pair of fields, the
        // colon between them and the keypad beside them say the same thing in the height that is left.
        // The digits are sized to the box rather than the box to the digits. Sideways the column has
        // about 148dp for two fields, a colon and a backspace, which leaves a field 36dp wide, and two
        // numerals at the panel's value size want 48 - so they wrapped to a second line, each field
        // reading as one digit above another, and the column grew by that line until the button under
        // it was cut off by the card. Measured and stepped down until the pair fits, and never allowed
        // to wrap whatever happens.
        final float valueSp = fittingSp(ctx, ui.textValue(), boxW - Utils.dpToPx(8));
        final FrameLayout lessSlot = new FrameLayout(ctx);
        lessSlot.addView(less,
                new FrameLayout.LayoutParams(backspaceBox, backspaceBox, Gravity.CENTER));
        final LinearLayout.LayoutParams lessLp = new LinearLayout.LayoutParams(backspaceBox, boxH);
        lessLp.setMarginEnd(backspaceGap);
        valueRow.addView(lessSlot, lessLp);

        final TextView hours = field(ctx, ui, boxW, boxH, fieldFill, valueSp);
        // No word under them: the colon between the pair says which is which, and one panel that
        // reads the same way round every window is worth more than a label saying "Hours".
        valueRow.addView(hours);
        final TextView colon = new TextView(ctx);
        colon.setText(":");
        colon.setTextColor(dim);
        // The same size the digits came out at, not the size they were asked for: fitted down to a
        // narrow field they shrank and the colon did not, and a colon half again as tall as the
        // numerals reads as two marks that fell off something rather than as a separator.
        colon.setTextSize(TypedValue.COMPLEX_UNIT_SP, valueSp);
        colon.setGravity(Gravity.CENTER);
        final LinearLayout.LayoutParams colonLp = new LinearLayout.LayoutParams(colonW, boxH);
        valueRow.addView(colon, colonLp);
        final TextView minutes = field(ctx, ui, boxW, boxH, fieldFill, valueSp);
        valueRow.addView(minutes);

        // Square, not the field's height: it is a circle now that it carries an edge, and a 48 × 72 one
        // is an oval. It takes a slot as tall as a field and centres in it, rather than being nudged
        // down by half the difference — the row is as tall as a field plus the unit named under it, so
        // arithmetic from the row's own top put the circle 22dp below the middle of the fields.
        final FrameLayout backspaceSlot = new FrameLayout(ctx);
        backspaceSlot.addView(more,
                new FrameLayout.LayoutParams(backspaceBox, backspaceBox, Gravity.CENTER));
        final LinearLayout.LayoutParams slotLp =
                new LinearLayout.LayoutParams(backspaceBox, boxH);
        slotLp.setMarginStart(backspaceGap);
        valueRow.addView(backspaceSlot, slotLp);

        // Where the duration lands in wall-clock terms — the same phrasing the header uses for the end
        // of the video, since it answers the same question.
        final TextView endsAt = new TextView(ctx);
        endsAt.setTextColor(dim);
        endsAt.setTextSize(TypedValue.COMPLEX_UNIT_SP, ui.textEndsAt());
        endsAt.setGravity(centred ? Gravity.CENTER_HORIZONTAL : Gravity.START);
        endsAt.setPadding(0, Utils.dpToPx(4), 0, 0);
        // Portrait only. Sideways this is the one line in the column that is derived rather than
        // pressed — the readout right above it already says how long is left — and it is what put the
        // actions past the bottom of the card on both short windows: 11dp on a phone, 12 on a
        // television, where every control is a third larger again.
        if (!landscape) {
            readoutColumn.addView(endsAt);
        }

        // Right-aligned at the foot, as Material ends a sheet: the way out of a running timer, then the
        // one action a typed length needs.
        final LinearLayout actions = new LinearLayout(ctx);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END);
        final LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsLp.topMargin = landscape ? gap : Utils.dpToPx(16);
        actions.setLayoutParams(actionsLp);

        final MaterialButton off = action(ctx, ui, ctx.getString(R.string.sleep_timer_off), false);
        off.setOnClickListener(v -> {
            listener.onDurationPicked(0);
            dialog.dismiss();
        });
        final MaterialButton start = action(ctx, ui, ctx.getString(R.string.sleep_timer_start), true);
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
            // The minus has nothing to take off nothing.
            less.setEnabled(typed[0] != 0);
            less.setAlpha(typed[0] != 0 ? 1f : 0.35f);

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


        // The foot of its own column either way: under the readout when the keypad is beside it, under
        // everything when the keypad is below it. Sideways the column is as tall as the keypad standing
        // next to it, so a spring above the actions puts them on the keypad's own last line — two
        // columns that end together read as one block, and two that end 11dp apart read as a mistake.
        // At the foot of the card, which is where a sheet ends and where the drawing put them: a
        // spring above, and a viewport the scroll view fills so the spring has something to push
        // against. Where the content is taller than the card the spring is nothing and the actions
        // follow it as before.
        root.addView(new View(ctx), new LinearLayout.LayoutParams(1, 0, 1f));
        root.addView(actions);

        less.setOnClickListener(v -> step(typed, -5, render));
        more.setOnClickListener(v -> step(typed, 5, render));
        render.run();

        // Backstop only. The sizing above is meant to make the panel fit outright; this catches what it
        // cannot foresee — a large font scale, split screen, a window shape not thought of — so that no
        // action can ever end up somewhere unreachable. No fillViewport: nothing in here wants to stretch.
        final ScrollView scrollView = new ScrollView(ctx);
        scrollView.setFillViewport(true);
        scrollView.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        // Only the sheet's own padding now — see Utils.pickerWindow for where the bars and the
        // overscan went.
        scrollView.setPadding(hPad, Utils.dpToPx(16), hPad, Utils.dpToPx(20));

        Dialogs.pickerWindow(activity, ui, dialog, scrollView);
        // On TV the remote's number keys are the natural way in — arrowing across ten buttons is not.
        dialog.setOnKeyListener((d, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN
                    || keyCode < KeyEvent.KEYCODE_0 || keyCode > KeyEvent.KEYCODE_9) {
                return false;
            }
            appendDigit(typed, keyCode - KeyEvent.KEYCODE_0, render);
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

    /**
     * Five minutes on or off what the readout holds, in the HHMM the rest of this panel counts in.
     *
     * <p>Five because that is the grain a sleep timer is nudged in - the ready-made durations are the
     * coarse move and this is the fine one. It does not wrap: at nothing it stays at nothing and at
     * twelve hours it stops there, so a held press cannot roll the value round behind the viewer.
     */
    private static void step(int[] typed, int minutes, Runnable render) {
        final int total = Math.max(0, Math.min(MAX_MINUTES, totalMinutes(typed[0]) + minutes));
        typed[0] = (total / 60) * 100 + total % 60;
        render.run();
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
    /**
     * The largest size, at or below {@code wanted}, at which two numerals fit in {@code widthPx}.
     *
     * <p>A readout that wraps is not a readout. Where the window will not give the field the width
     * Material's own asks for, the digits give way rather than the layout.
     */
    private static float fittingSp(final Context ctx, final float wanted, final int widthPx) {
        final android.text.TextPaint probe = new android.text.TextPaint();
        probe.setFontFeatureSettings("tnum");
        for (float sp = wanted; sp > 16f; sp -= 1f) {
            probe.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp,
                    ctx.getResources().getDisplayMetrics()));
            if (probe.measureText("00") <= widthPx) {
                return sp;
            }
        }
        return 16f;
    }

    private static TextView field(final Context ctx, final UiMetrics ui,
                                  final int width, final int height, final int fill,
                                  final float sizeSp) {
        final TextView box = new TextView(ctx);
        box.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        box.setFontFeatureSettings("tnum"); // fixed-width digits: the readout stops twitching as it fills
        // One line, always: two numerals that do not fit used to become one above the other, and a
        // readout twice its height took the actions under it off the bottom of the card.
        box.setSingleLine(true);
        box.setGravity(Gravity.CENTER);
        // Without this a 40sp digit carries about 5dp of font padding it never draws in, which is what
        // made the readout 53dp tall inside a 48dp box sideways — and a column 5dp taller than the
        // keypad beside it takes those 5 out of the actions at its foot. The glyphs stay centred.
        box.setIncludeFontPadding(false);
        box.setMinHeight(height); // a minimum, not a height: the digits follow the system font scale
        final GradientDrawable container = new GradientDrawable();
        container.setCornerRadius(ui.pillCorner()); // 8dp, Material's small corner, as its field wears
        container.setColor(fill);
        box.setBackground(container);
        // A minimum and not a fixed height: forced to exactly the row's height the box came out shorter
        // than the digits it holds and cut their feet off. Without the font padding the 40sp numerals
        // measure just under a 48dp row, so the minimum is what decides the box and nothing is clipped.
        box.setLayoutParams(new LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT));
        return box;
    }


    /**
     * One of the two actions at the foot.
     *
     * @param filled the one action that arms the timer; everything else on a surface is outlined, and a
     *               panel is allowed exactly one filled control — the thing it exists to do
     */
    private static MaterialButton action(final Context ctx, final UiMetrics ui, final String label,
                                         final boolean filled) {
        final MaterialButton button = new MaterialButton(ctx, null, filled
                ? com.google.android.material.R.attr.materialButtonStyle
                : com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(label);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, ui.textAction());
        button.setMaxLines(1);
        // Material insets a button by 6dp top and bottom to reach its 48dp touch target from a 36dp
        // box. This panel sizes its own rows, so the inset only shortens them.
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setMinHeight(ui.dpS(48));
        if (!filled) {
            Utils.quietInk(button);
        }
        Utils.focusRing(button);
        return button;
    }

    private static LinearLayout keyRow(final Context ctx, final int rowHeight, final int cols) {
        final LinearLayout line = new LinearLayout(ctx);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setWeightSum(cols);
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
        key.setBackground(Dialogs.pickerRow(ctx, Color.TRANSPARENT, true));
        key.setOnClickListener(v -> onTap.run());
        return key;
    }
}
