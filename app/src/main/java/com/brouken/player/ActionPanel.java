package com.brouken.player;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.widget.TextViewCompat;

import com.google.android.material.color.MaterialColors;

import java.util.List;

/**
 * A short list of things that can be done, in the app's own panel.
 *
 * <p>The same surface the player raises for its tracks and its speed, and the same one the offset and
 * duration panels are: bottom-docked under 600dp, end-docked and full height above it (R8). It is
 * that shape because {@link Utils#pickerWindow} decides it — the window does, never the control that
 * opened the panel — so a list of actions read in a file browser is the list of actions read in a
 * film, and neither is a floating card.
 *
 * <p>This is the plain half of {@code PlayerActivity.showSideMenu}: labelled rows, each with a glyph
 * where the caller has one for it. No poster, no chosen row, no captions or rules between groups -
 * which is why it is forty lines here rather than a lift of the two hundred and thirty there. What both share is what makes
 * them one interface: {@link Utils#dialogContext}, {@link Utils#pickerHeader},
 * {@link Utils#pickerRow} and {@code pickerWindow}.
 *
 * <p>R6 is what says a list belongs here at all: a surface you browse is a panel at the edge, and a
 * surface that asks one question and waits is a dialog in the middle. So the row menu is a panel and
 * "Forget this folder?" stays a dialog.
 */
final class ActionPanel {

    interface OnPick {
        void picked(int which);
    }

    private ActionPanel() {
    }

    static void show(final Activity activity, final CharSequence title,
                     final List<CharSequence> labels, final OnPick onPick) {
        show(activity, title, labels, null, onPick);
    }

    /**
     * @param icons one drawable per label, or null for a list of plain words. A 0 in it is a row
     *              with nothing to lead it - which is why the whole list is not made optional: a
     *              list where some rows carry a glyph and the rest start where the glyph would have
     *              been reads as a list with holes in it, so the caller answers for every row.
     */
    static void show(final Activity activity, final CharSequence title,
                     final List<CharSequence> labels, final List<Integer> icons,
                     final OnPick onPick) {
        if (labels == null || labels.isEmpty()) {
            return;
        }
        // Built against the appearance choice rather than the activity's theme, as every panel and
        // dialog in the app is - see Utils.dialogContext.
        final Context ctx = Dialogs.dialogContext(activity);
        final UiMetrics ui = UiMetrics.of(activity, Utils.isTvBox(activity));
        final int onSurface = MaterialColors.getColor(ctx, R.attr.colorOnSurface, Color.WHITE);

        final LinearLayout list = new LinearLayout(ctx);
        list.setOrientation(LinearLayout.VERTICAL);
        final int listPad = Utils.dpToPx(10);
        list.setPadding(listPad, listPad, listPad, listPad);

        final TextView header = new TextView(ctx);
        header.setText(title);
        ViewCompat.setAccessibilityHeading(header, true);
        header.setTextColor(onSurface);
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, ui.textTitle());
        header.setTypeface(Typeface.DEFAULT_BOLD);
        header.setPadding(Utils.dpToPx(6), Utils.dpToPx(10), Utils.dpToPx(6), Utils.dpToPx(10));
        list.addView(Dialogs.pickerHeader(ctx, ui, header));

        final Dialog dialog = new Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar);
        final View[] first = new View[1];
        for (int index = 0; index < labels.size(); index++) {
            final int which = index;
            final TextView row = new TextView(ctx);
            row.setText(labels.get(index));
            final int glyph = icons == null || index >= icons.size() ? 0 : icons.get(index);
            if (glyph != 0) {
                row.setCompoundDrawablesRelativeWithIntrinsicBounds(glyph, 0, 0, 0);
                // The same ink as the label rather than the drawable's own white: these are the
                // player's glyphs, drawn for chrome over video.
                TextViewCompat.setCompoundDrawableTintList(row, ColorStateList.valueOf(onSurface));
                row.setCompoundDrawablePadding(Utils.dpToPx(16));
            }
            row.setTextColor(onSurface);
            row.setTextSize(TypedValue.COMPLEX_UNIT_SP, ui.textBody());
            row.setGravity(Gravity.CENTER_VERTICAL);
            // 6dp inside the list's own 10dp, so a label starts 16dp from the sheet's edge - the
            // number Material states for a list item without a leading icon, which none of these has.
            row.setPadding(Utils.dpToPx(6), Utils.dpToPx(8), Utils.dpToPx(6), Utils.dpToPx(8));
            row.setMinHeight(ui.rowMinHeight());
            row.setClickable(true);
            row.setFocusable(true);
            // Nothing here is chosen, so no row is filled; the plate carries the ripple and the 2dp
            // contour that is how this app says "focused" (R5).
            row.setBackground(Dialogs.pickerRow(ctx, Color.TRANSPARENT));
            row.setOnClickListener(v -> {
                dialog.dismiss();
                onPick.picked(which);
            });
            if (first[0] == null) {
                first[0] = row;
            }
            list.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        final ScrollView scroller = new ScrollView(ctx);
        scroller.addView(list);
        scroller.setPadding(0, Utils.dpToPx(8), 0, 0);

        final FrameLayout panel = new FrameLayout(ctx);
        panel.addView(scroller, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        // The band that says there is more below, for the reason showSideMenu records: the card is
        // clipped to its outline, so the platform's own fading edge is composited away and never
        // appears. A list this short rarely scrolls, and on a television it can.
        final View fade = new View(ctx);
        fade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.TRANSPARENT, MaterialColors.getColor(ctx, R.attr.colorSurface,
                        ContextCompat.getColor(ctx, R.color.sheet_surface))}));
        fade.setVisibility(View.GONE);
        panel.addView(fade, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ui.dpS(28), Gravity.BOTTOM));
        final Runnable syncFade = () -> fade.setVisibility(
                scroller.canScrollVertically(1) ? View.VISIBLE : View.GONE);
        scroller.getViewTreeObserver().addOnScrollChangedListener(syncFade::run);
        scroller.post(syncFade);

        Dialogs.pickerWindow(activity, ui, dialog, panel);
        dialog.show();
        // A remote has to land somewhere, and the first row is the only somewhere here - nothing in
        // this list is chosen. Posted, because the rows are not laid out until the window is.
        if (first[0] != null) {
            first[0].post(() -> first[0].requestFocus());
        }
    }
}
