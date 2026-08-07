package com.brouken.player;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Editor for an ordered list of preferred languages: the player walks it top to bottom and plays the
 * first language the media actually carries.
 *
 * Reordering is done with per-row up/down buttons rather than by dragging. Dragging on Android TV is
 * "focus the handle, press OK, hold a direction, press OK again", which is worse than two buttons for
 * everyone — and buttons need no RecyclerView, so a list of two to five entries stays a plain
 * LinearLayout that is simply rebuilt after every change. The rebuild has to hand focus back to the
 * button that was just pressed, or a remote would be thrown out of the list on every single press.
 */
final class AudioLanguagePriorityDialog {

    interface Listener {
        void onLanguagesPicked(List<String> languages);
    }

    // Child positions inside a row: the label sits at 0.
    private static final int UP = 1;
    private static final int DOWN = 2;
    private static final int REMOVE = 3;

    private AudioLanguagePriorityDialog() {
    }

    /**
     * @param allLanguages every selectable code mapped to its label, in the order the picker lists them
     * @param pinned       codes worth offering first (device languages, languages of the open media)
     */
    static void show(final Context context, final String title, final List<String> initial,
                     final LinkedHashMap<String, String> allLanguages, final List<String> pinned,
                     final Listener listener) {
        final List<String> languages = new ArrayList<>(initial);

        final LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(Utils.dpToPx(16), Utils.dpToPx(8), Utils.dpToPx(16), Utils.dpToPx(8));

        final ScrollView scroll = new ScrollView(context);
        scroll.addView(list);

        rebuild(context, list, languages, allLanguages, pinned, -1, 0);

        new AlertDialog.Builder(context)
                .setTitle(title)
                .setView(scroll)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok,
                        (dialog, which) -> listener.onLanguagesPicked(languages))
                .show();
    }

    /**
     * @param focusRow   row to hand the focus back to after the rebuild, or -1 to leave focus alone
     * @param focusChild which of that row's buttons to prefer: UP, DOWN or REMOVE
     */
    private static void rebuild(final Context context, final LinearLayout list,
                                final List<String> languages,
                                final LinkedHashMap<String, String> allLanguages,
                                final List<String> pinned,
                                final int focusRow, final int focusChild) {
        list.removeAllViews();

        if (languages.isEmpty()) {
            final TextView empty = new TextView(context);
            empty.setText(R.string.pref_language_audio_none);
            empty.setPadding(0, Utils.dpToPx(8), 0, Utils.dpToPx(16));
            list.addView(empty);
        }

        for (int i = 0; i < languages.size(); i++) {
            final int index = i;
            final LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            final TextView label = new TextView(context);
            label.setText(label(allLanguages, languages.get(i)));
            label.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(label);

            final ColorStateList tint = ColorStateList.valueOf(label.getCurrentTextColor());
            final ImageButton up = iconButton(context, R.drawable.ic_arrow_upward_24dp,
                    context.getString(R.string.pref_language_audio_move_up), tint);
            Utils.setButtonEnabled(context, up, index > 0);
            up.setOnClickListener(v -> {
                Collections.swap(languages, index, index - 1);
                rebuild(context, list, languages, allLanguages, pinned, index - 1, UP);
            });
            final ImageButton down = iconButton(context, R.drawable.ic_arrow_downward_24dp,
                    context.getString(R.string.pref_language_audio_move_down), tint);
            Utils.setButtonEnabled(context, down, index < languages.size() - 1);
            down.setOnClickListener(v -> {
                Collections.swap(languages, index, index + 1);
                rebuild(context, list, languages, allLanguages, pinned, index + 1, DOWN);
            });
            final ImageButton remove = iconButton(context, R.drawable.ic_close_24dp,
                    context.getString(R.string.pref_language_audio_remove), tint);
            remove.setOnClickListener(v -> {
                languages.remove(index);
                // The row is gone, so focus the one that slid into its place — or the last one left.
                // Emptying the list leaves no row at all, and restoreFocus falls back to "Add".
                rebuild(context, list, languages, allLanguages, pinned,
                        Math.max(0, Math.min(index, languages.size() - 1)), REMOVE);
            });
            // Disabled buttons still take a D-pad stop otherwise, so the first row's "up" would swallow
            // a press that should have moved the focus out of the list.
            up.setFocusable(up.isEnabled());
            down.setFocusable(down.isEnabled());
            row.addView(up);
            row.addView(down);
            row.addView(remove);
            list.addView(row);
        }

        final TextView add = new TextView(context);
        add.setText(R.string.pref_language_audio_add);
        add.setGravity(Gravity.CENTER_VERTICAL);
        add.setClickable(true);
        add.setFocusable(true);
        add.setCompoundDrawablePadding(Utils.dpToPx(12));
        add.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_add_24dp, 0, 0, 0);
        add.setCompoundDrawableTintList(ColorStateList.valueOf(add.getCurrentTextColor()));
        add.setPadding(0, Utils.dpToPx(12), 0, Utils.dpToPx(12));
        add.setBackgroundResource(themeAttr(context, android.R.attr.selectableItemBackground));
        add.setOnClickListener(v -> showPicker(context, list, languages, allLanguages, pinned));
        list.addView(add);

        restoreFocus(list, focusRow, focusChild, add);
    }

    /**
     * removeAllViews drops the focused view, and the framework then parks the focus on the first
     * focusable in the window — the dialog's buttons. Put it back where the user was.
     */
    private static void restoreFocus(final LinearLayout list, final int focusRow,
                                     final int focusChild, final View fallback) {
        if (focusRow < 0) {
            return;
        }
        final View row = focusRow < list.getChildCount() ? list.getChildAt(focusRow) : null;
        View target = null;
        if (row instanceof ViewGroup) {
            // The pressed button can be the one that just became disabled (moved to an end of the
            // list, or removed the last row), so fall back through the row's other buttons.
            for (final int child : new int[]{focusChild, UP, DOWN, REMOVE}) {
                final View candidate = ((ViewGroup) row).getChildAt(child);
                if (candidate != null && candidate.isFocusable()) {
                    target = candidate;
                    break;
                }
            }
        }
        final View focus = target != null ? target : fallback;
        focus.post(focus::requestFocus);
    }

    private static void showPicker(final Context context, final LinearLayout list,
                                   final List<String> languages,
                                   final LinkedHashMap<String, String> allLanguages,
                                   final List<String> pinned) {
        final List<String> codes = new ArrayList<>();
        for (final String code : pinned) {
            if (!languages.contains(code) && !codes.contains(code)) {
                codes.add(code);
            }
        }
        for (final String code : allLanguages.keySet()) {
            if (!languages.contains(code) && !codes.contains(code)) {
                codes.add(code);
            }
        }
        final String[] labels = new String[codes.size()];
        for (int i = 0; i < codes.size(); i++) {
            labels[i] = label(allLanguages, codes.get(i));
        }
        new AlertDialog.Builder(context)
                .setTitle(R.string.pref_language_audio_add)
                .setItems(labels, (dialog, which) -> {
                    languages.add(codes.get(which));
                    rebuild(context, list, languages, allLanguages, pinned,
                            languages.size() - 1, UP);
                })
                .show();
    }

    private static String label(final LinkedHashMap<String, String> allLanguages, final String code) {
        final String label = allLanguages.get(code);
        return label != null ? label : code;
    }

    private static ImageButton iconButton(final Context context, final int iconRes,
                                          final String description, final ColorStateList tint) {
        final ImageButton button = new ImageButton(context);
        button.setImageResource(iconRes);
        button.setImageTintList(tint);
        button.setContentDescription(description);
        button.setBackground(null);
        // Foreground, not background: a RippleDrawable set as an ImageButton background hides the glyph.
        button.setForeground(ContextCompat.getDrawable(context,
                themeAttr(context, android.R.attr.selectableItemBackgroundBorderless)));
        // 48dp tap target, 24dp glyph — ImageView scales the vector up to the whole view otherwise.
        button.setPadding(Utils.dpToPx(12), Utils.dpToPx(12), Utils.dpToPx(12), Utils.dpToPx(12));
        button.setLayoutParams(new LinearLayout.LayoutParams(Utils.dpToPx(48), Utils.dpToPx(48)));
        return button;
    }

    private static int themeAttr(final Context context, final int attr) {
        final TypedValue value = new TypedValue();
        context.getTheme().resolveAttribute(attr, value, true);
        return value.resourceId;
    }
}
