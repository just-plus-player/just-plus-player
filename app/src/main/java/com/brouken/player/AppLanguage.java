package com.brouken.player;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.XmlResourceParser;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.xmlpull.v1.XmlPullParser;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The app's own language, picked in the app rather than three screens deep in the system settings — which
 * is where Android puts it, and only from 13 onwards. AppCompat carries the choice down to the versions
 * that have no per-app language of their own, so one row answers for every device the app runs on.
 *
 * <p>The list is read from {@code locales_config.xml}, the same file the system picker reads, so a
 * translation added to the app appears here without anything else being edited. English is added to it:
 * that file names the translations, and the base resources are the English the app is written in.
 */
final class AppLanguage {

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    private AppLanguage() {}

    /** What the row says under its title: the language in force, or that the system decides it. */
    static String summary(final Context context) {
        final LocaleListCompat chosen = AppCompatDelegate.getApplicationLocales();
        if (chosen.isEmpty()) {
            return context.getString(R.string.pref_app_language_system);
        }
        return nativeName(chosen.get(0));
    }

    /**
     * The picker: the languages the app speaks, each in its own writing and then in the reader's, so a
     * viewer who cannot read the alphabet in front of them can still find their way out of it.
     */
    static void showPicker(final Context context) {
        final List<String> tags = tags(context);
        final LocaleListCompat current = AppCompatDelegate.getApplicationLocales();
        final String chosenTag = current.isEmpty() ? null : current.get(0).toLanguageTag();

        final CharSequence[] labels = new CharSequence[tags.size() + 1];
        labels[0] = context.getString(R.string.pref_app_language_system);
        int checked = 0;
        for (int i = 0; i < tags.size(); i++) {
            final Locale locale = Locale.forLanguageTag(tags.get(i));
            labels[i + 1] = label(context, locale);
            if (chosenTag != null && sameLanguage(chosenTag, tags.get(i))) {
                checked = i + 1;
            }
        }

        final int[] picked = {checked};
        final Context dialogContext = Utils.dialogContext(context);
        new MaterialAlertDialogBuilder(dialogContext)
                .setCustomTitle(header(dialogContext))
                .setSingleChoiceItems(labels, checked, (dialog, which) -> picked[0] = which)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> AppCompatDelegate
                        .setApplicationLocales(picked[0] == 0
                                ? LocaleListCompat.getEmptyLocaleList()
                                : LocaleListCompat.forLanguageTags(tags.get(picked[0] - 1))))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /**
     * The mark and the name, stacked and centred, the way Material heads a dialog that carries an icon —
     * and the way the update dialog next door heads itself. The builder's own icon slot sets them side by
     * side instead, which reads as a row of the list rather than the head of one.
     */
    private static View header(final Context context) {
        final LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        final int pad = Utils.dpToPx(24);
        column.setPadding(pad, pad, pad, Utils.dpToPx(8));

        final ImageView icon = new ImageView(context);
        icon.setImageResource(R.drawable.ic_language_24dp);
        icon.setImageTintList(ColorStateList.valueOf(MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorOnSurfaceVariant, 0)));
        final LinearLayout.LayoutParams iconLp =
                new LinearLayout.LayoutParams(Utils.dpToPx(32), Utils.dpToPx(32));
        iconLp.gravity = Gravity.CENTER_HORIZONTAL;
        column.addView(icon, iconLp);

        final TextView title = new TextView(context);
        title.setText(R.string.pref_app_language);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorOnSurface, 0));
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        final LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleLp.topMargin = Utils.dpToPx(12);
        column.addView(title, titleLp);
        return column;
    }

    /** A language in its own words, and — when that is not what the reader reads — in theirs underneath. */
    private static CharSequence label(final Context context, final Locale locale) {
        final String own = nativeName(locale);
        final String here = capitalise(locale.getDisplayName(Locale.getDefault()));
        if (own.equals(here)) {
            return own;
        }
        final SpannableStringBuilder text = new SpannableStringBuilder(own).append('\n').append(here);
        final int from = own.length() + 1;
        text.setSpan(new RelativeSizeSpan(0.8f), from, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new ForegroundColorSpan(MaterialColors.getColor(context,
                        com.google.android.material.R.attr.colorOnSurfaceVariant, 0)),
                from, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return text;
    }

    private static String nativeName(final Locale locale) {
        return capitalise(locale.getDisplayName(locale));
    }

    /** Some languages name themselves in lower case; a list reads better with every entry starting alike. */
    private static String capitalise(final String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        return name.substring(0, 1).toUpperCase(Locale.getDefault()) + name.substring(1);
    }

    /** A stored "pt-BR" answers for the "pt-BR" row, and a stored "uk-UA" for the "uk" one. */
    private static boolean sameLanguage(final String chosen, final String tag) {
        return chosen.equals(tag) || chosen.startsWith(tag + "-")
                || Locale.forLanguageTag(chosen).getLanguage().equals(Locale.forLanguageTag(tag).getLanguage())
                && !tag.contains("-");
    }

    /**
     * The translations, in the reader's own alphabetical order. The file lists them in the Android resource
     * form ({@code pt-rBR}); a language tag wants the plain one.
     */
    private static List<String> tags(final Context context) {
        final List<String> tags = new ArrayList<>();
        tags.add("en");
        try (XmlResourceParser parser = context.getResources().getXml(R.xml.locales_config)) {
            int event = parser.getEventType();
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && "locale".equals(parser.getName())) {
                    final String name = parser.getAttributeValue(ANDROID_NS, "name");
                    if (name != null && !name.isEmpty()) {
                        tags.add(name.replace("-r", "-"));
                    }
                }
                event = parser.next();
            }
        } catch (Exception e) {
            // A list that cannot be read is still a list: the system entry alone leaves a way back.
        }
        final Collator collator = Collator.getInstance(Locale.getDefault());
        Collections.sort(tags, (a, b) -> collator.compare(
                Locale.forLanguageTag(a).getDisplayName(Locale.getDefault()),
                Locale.forLanguageTag(b).getDisplayName(Locale.getDefault())));
        return tags;
    }
}
