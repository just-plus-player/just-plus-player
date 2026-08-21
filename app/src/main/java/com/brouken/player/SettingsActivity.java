package com.brouken.player;

import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ImageSpan;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.app.Activity;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.core.view.OneShotPreDrawListener;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.brouken.player.together.AliasGenerator;
import com.brouken.player.together.Relay;
import com.brouken.player.together.Room;
import com.brouken.player.update.Updater;
import com.brouken.player.update.UpdateUi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

public class SettingsActivity extends AppCompatActivity
        implements PreferenceFragmentCompat.OnPreferenceStartScreenCallback {

    /** ISO-639-2/T codes of the audio tracks in the clip the player has open, if any. */
    public static final String EXTRA_MEDIA_LANGUAGES = "mediaLanguages";

    /** Key of the preference to open on: the screen scrolls to it instead of starting at the top. */
    public static final String EXTRA_SCROLL_TO = "scrollTo";

    static RecyclerView recyclerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        if (Build.VERSION.SDK_INT >= 29) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            );
            getWindow().setNavigationBarColor(Color.TRANSPARENT);

            if (Build.VERSION.SDK_INT >= 35) {
                int nightModeFlags = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;

                if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES) {
                    getWindow().getDecorView().setSystemUiVisibility(0);
                } else if (nightModeFlags == Configuration.UI_MODE_NIGHT_NO) {
                    getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
                }
            }
        }

        super.onCreate(savedInstanceState);

        setContentView(R.layout.settings_activity);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings, new SettingsFragment())
                    .commit();
        }
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        final CharSequence rootTitle = getTitle();
        getSupportFragmentManager().addOnBackStackChangedListener(() -> {
            if (getSupportFragmentManager().getBackStackEntryCount() == 0) {
                setTitle(rootTitle);
            }
        });

        if (Build.VERSION.SDK_INT >= 29) {
            LinearLayout layout = findViewById(R.id.settings_layout);
            layout.setOnApplyWindowInsetsListener((view, windowInsets) -> {
                view.setPadding(windowInsets.getSystemWindowInsetLeft(),
                        windowInsets.getSystemWindowInsetTop(),
                        windowInsets.getSystemWindowInsetRight(),
                        0);
                if (recyclerView != null) {
                    recyclerView.setPadding(0,0,0, windowInsets.getSystemWindowInsetBottom());
                }
                windowInsets.consumeSystemWindowInsets();
                return windowInsets;
            });
        }
    }

    /** Up from a sub-screen goes back one level, not out of the settings altogether. */
    @Override
    public boolean onSupportNavigateUp() {
        return getSupportFragmentManager().popBackStackImmediate() || super.onSupportNavigateUp();
    }

    /**
     * A nested <PreferenceScreen> opens as the same fragment rooted at its key, which is how the
     * subtitle look stays one button instead of six rows on an already long screen.
     */
    @Override
    public boolean onPreferenceStartScreen(@NonNull PreferenceFragmentCompat caller,
                                           @NonNull PreferenceScreen screen) {
        final SettingsFragment fragment = new SettingsFragment();
        final Bundle arguments = new Bundle();
        arguments.putString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT, screen.getKey());
        fragment.setArguments(arguments);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings, fragment)
                .addToBackStack(null)
                .commit();
        setTitle(screen.getTitle());
        return true;
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            // Inflation materializes switch defaults, so record whether the key was already
            // persisted before that happens.
            boolean hadAllowSystemFrameRateKey =
                    androidx.preference.PreferenceManager.getDefaultSharedPreferences(getContext())
                            .contains("allowSystemFrameRate");

            // Before inflation: the list preference below reads this key, and the value may still be
            // living in the switch pair it replaced.
            Prefs.getSubtitleSearchMode(requireContext());
            Prefs.getSubtitleTranslateMode(requireContext());

            setPreferencesFromResource(R.xml.root_preferences, rootKey);

            Preference preferenceAutoPiP = findPreference("autoPiP");
            if (preferenceAutoPiP != null) {
                preferenceAutoPiP.setEnabled(Utils.isPiPSupported(this.getContext()));
            }
            Preference preferenceFrameRateMatching = findPreference("frameRateMatching");
            if (preferenceFrameRateMatching != null) {
                preferenceFrameRateMatching.setEnabled(Build.VERSION.SDK_INT >= 23);
            }
            SwitchPreferenceCompat preferenceAllowSystemFrameRate = findPreference("allowSystemFrameRate");
            if (preferenceAllowSystemFrameRate != null) {
                // Surface.setFrameRate() only exists on API 30+; below that this toggle is a no-op.
                preferenceAllowSystemFrameRate.setEnabled(Build.VERSION.SDK_INT >= 30);
                if (!hadAllowSystemFrameRateKey) {
                    // Device-specific default: off on TV (avoids the Hz-switch flicker), on elsewhere.
                    preferenceAllowSystemFrameRate.setChecked(!Utils.isTvBox(getContext()));
                }
            }
            // The display name is generated on first use, so the field is never empty — but the
            // regenerate button is worth having: it is how you get a different one without inventing
            // it yourself, and it is what LocalSend offers beside the same field.
            final androidx.preference.EditTextPreference preferenceNick = findPreference("togetherNick");
            final Preference preferenceNickRandom = findPreference("togetherNickRandom");
            if (preferenceNick != null && preferenceNickRandom != null) {
                preferenceNickRandom.setOnPreferenceClickListener(preference -> {
                    preferenceNick.setText(AliasGenerator.random());
                    return true;
                });
            }
            final androidx.preference.EditTextPreference preferencePassword =
                    findPreference("togetherPassword");
            if (preferencePassword != null) {
                // Shown as dots rather than as itself: a settings list is read over shoulders, and
                // the password is what keeps a room from being walked into.
                preferencePassword.setSummaryProvider(preference -> {
                    final String value = preferencePassword.getText();
                    return value == null || value.isEmpty()
                            ? getString(R.string.pref_together_password_none)
                            : "••••••";
                });
            }

            // Both fields hold an override; empty means the built-in address, which is also how one is put
            // back. The summary therefore shows what is actually in effect rather than echoing an empty
            // field back — and it is where the default is read, so nothing else has to offer it.
            final androidx.preference.EditTextPreference preferenceRelay = findPreference("togetherRelay");
            if (preferenceRelay != null) {
                preferenceRelay.setSummaryProvider(preference -> {
                    final String value = preferenceRelay.getText();
                    return value == null || value.trim().isEmpty()
                            ? getString(R.string.pref_together_relay_default, Relay.DEFAULT_BASE)
                            : value.trim();
                });
            }

            // The page an invite link points at, on the same terms. Only links written here follow it — an
            // invite that arrives is read by its room parameter whatever page sent it.
            final androidx.preference.EditTextPreference preferenceInvite =
                    findPreference("togetherInvitePage");
            if (preferenceInvite != null) {
                preferenceInvite.setSummaryProvider(preference -> {
                    final String value = preferenceInvite.getText();
                    return value == null || value.trim().isEmpty()
                            ? getString(R.string.pref_together_relay_default, Room.DEFAULT_INVITE_PAGE)
                            : value.trim();
                });
            }

            Preference preferenceSystemVolume = findPreference("systemVolume");
            if (preferenceSystemVolume != null && Utils.isTvBox(getContext())) {
                // TV remotes route volume to the panel or receiver over CEC, where only the system
                // stream responds — an isolated player volume would look broken there.
                preferenceSystemVolume.setVisible(false);
            }
            Preference preferenceDisableGestures = findPreference("disableVolumeBrightnessGestures");
            if (preferenceDisableGestures != null && Utils.isTvBox(getContext())) {
                // A remote has no swipes to give up, so there is nothing here to turn off.
                preferenceDisableGestures.setVisible(false);
            }
            Preference preferenceHoldSpeed = findPreference("holdSpeed");
            if (preferenceHoldSpeed != null && Utils.isTvBox(getContext())) {
                // Same reason: there is no finger to hold on the picture.
                preferenceHoldSpeed.setVisible(false);
            }
            ListPreference listPreferenceFileAccess = findPreference("fileAccess");
            if (listPreferenceFileAccess != null) {
                List<String> entries = new ArrayList<>(Arrays.asList(getResources().getStringArray(R.array.file_access_entries)));
                List<String> values = new ArrayList<>(Arrays.asList(getResources().getStringArray(R.array.file_access_values)));
                if (Build.VERSION.SDK_INT < 30) {
                    int index = values.indexOf("mediastore");
                    entries.remove(index);
                    values.remove(index);
                }
                if (!Utils.hasSAFChooser(getContext().getPackageManager())) {
                    int index = values.indexOf("saf");
                    entries.remove(index);
                    values.remove(index);
                }
                listPreferenceFileAccess.setEntries(entries.toArray(new String[0]));
                listPreferenceFileAccess.setEntryValues(values.toArray(new String[0]));
            }

            Preference preferenceLanguageAudio = findPreference("languageAudio");
            Preference preferenceLanguageSubtitle = findPreference("languageSubtitle");
            if (preferenceLanguageAudio != null || preferenceLanguageSubtitle != null) {
                // Several hundred locales, resolved and sorted once for both lists.
                final LinkedHashMap<String, String> languages = Utils.allLanguages();
                if (preferenceLanguageAudio != null) {
                    updateLanguageSummary(preferenceLanguageAudio, languages,
                            Prefs.getLanguageAudio(requireContext()),
                            R.string.pref_language_audio_none);
                    preferenceLanguageAudio.setOnPreferenceClickListener(preference -> {
                        LanguagePriorityDialog.show(requireContext(),
                                getString(R.string.pref_language_audio),
                                R.string.pref_language_audio_none, R.string.pref_language_audio_add,
                                Utils.splitLanguages(Prefs.getLanguageAudio(requireContext())),
                                languages, pinnedLanguages(), picked -> {
                                    final String stored = TextUtils.join(",", picked);
                                    Prefs.setLanguageAudio(requireContext(), stored);
                                    updateLanguageSummary(preference, languages, stored,
                                            R.string.pref_language_audio_none);
                                });
                        return true;
                    });
                }
                if (preferenceLanguageSubtitle != null) {
                    updateLanguageSummary(preferenceLanguageSubtitle, languages,
                            Prefs.getLanguageSubtitle(requireContext()),
                            R.string.pref_language_subtitle_none);
                    preferenceLanguageSubtitle.setOnPreferenceClickListener(preference -> {
                        LanguagePriorityDialog.show(requireContext(),
                                getString(R.string.pref_language_subtitle),
                                R.string.pref_language_subtitle_none, R.string.pref_language_audio_add,
                                Utils.splitLanguages(Prefs.getLanguageSubtitle(requireContext())),
                                languages, pinnedLanguages(), picked -> {
                                    final String stored = TextUtils.join(",", picked);
                                    Prefs.setLanguageSubtitle(requireContext(), stored);
                                    updateLanguageSummary(preference, languages, stored,
                                            R.string.pref_language_subtitle_none);
                                });
                        return true;
                    });
                }
            }

            // The search lives behind its own row, so its state has to read from the outside: without
            // this the row says nothing and the whole feature is a tap away from being discovered.
            final ListPreference searchMode = findPreference("subtitleSearchMode");
            final ListPreference translateMode = findPreference("subtitleTranslateMode");
            if (searchMode != null) {
                applySearchMode(searchMode, searchMode.getValue(), translateMode);
                searchMode.setOnPreferenceChangeListener((preference, value) -> {
                    applySearchMode(searchMode, (String) value, translateMode);
                    return true;
                });
            }
            if (translateMode != null) {
                applyTranslateMode(translateMode, translateMode.getValue(), searchMode);
                translateMode.setOnPreferenceChangeListener((preference, value) -> {
                    applyTranslateMode(translateMode, (String) value, searchMode);
                    // A translation cached under the previous choice would keep being served for
                    // everything watched recently, so the new choice would look like it did nothing.
                    SubtitleUtils.clearTranslatedCache(requireContext());
                    return true;
                });
            }

            final Preference translateBackends = findPreference("subtitleTranslateBackends");
            if (translateBackends != null) {
                final LinkedHashMap<String, String> services = SubtitleTranslate.backends();
                updateLanguageSummary(translateBackends, services,
                        Prefs.getSubtitleTranslateBackends(requireContext()),
                        R.string.pref_subtitle_translate_backends_none);
                translateBackends.setOnPreferenceClickListener(preference -> {
                    LanguagePriorityDialog.show(requireContext(),
                            getString(R.string.pref_subtitle_translate_backends),
                            R.string.pref_subtitle_translate_backends_none,
                            R.string.pref_subtitle_translate_backends_add,
                            Utils.splitLanguages(Prefs.getSubtitleTranslateBackends(requireContext())),
                            services, Collections.emptyList(), picked -> {
                                final String stored = TextUtils.join(",", picked);
                                Prefs.setSubtitleTranslateBackends(requireContext(), stored);
                                updateLanguageSummary(preference, services, stored,
                                        R.string.pref_subtitle_translate_backends_none);
                                // The endpoints changed, so what they produced is no longer what this
                                // setting says would be produced.
                                SubtitleUtils.clearTranslatedCache(requireContext());
                            });
                    return true;
                });
            }

            final ListPreference textColor = findPreference("subtitleTextColor");
            final ListPreference background = findPreference("subtitleBackground");
            if (textColor != null && background != null) {
                showColorChips(textColor);
                showColorChips(background);
                // Text in the colour of its own box is invisible subtitles, and the two lists are far
                // enough apart that nobody would connect the cause. Refuse the pick instead.
                textColor.setOnPreferenceChangeListener((preference, value) ->
                        allowColor((String) value, background.getValue()));
                background.setOnPreferenceChangeListener((preference, value) ->
                        allowColor(textColor.getValue(), (String) value));
            }

            Preference resetAudioWorkarounds = findPreference("resetRevokedAudioMimes");
            if (resetAudioWorkarounds != null) {
                resetAudioWorkarounds.setOnPreferenceClickListener(preference -> {
                    Prefs.resetRevokedAudioMimes(requireContext());
                    Toast.makeText(getContext(), R.string.pref_reset_audio_workarounds_done, Toast.LENGTH_SHORT).show();
                    return true;
                });
            }

            PreferenceCategory privacyCategory = findPreference("privacyCategory");
            if (privacyCategory != null && !BuildConfig.ENABLE_CRASH_REPORTING) {
                privacyCategory.setVisible(false);
            }

            PreferenceCategory updateCategory = findPreference("updateCategory");
            if (!BuildConfig.ENABLE_UPDATE) {
                if (updateCategory != null) {
                    updateCategory.setVisible(false);
                }
            } else {
                Preference currentVersion = findPreference("currentVersion");
                if (currentVersion != null) {
                    currentVersion.setSummary(BuildConfig.VERSION_NAME);
                }
                Preference checkUpdate = findPreference("checkUpdateNow");
                if (checkUpdate != null) {
                    checkUpdate.setOnPreferenceClickListener(preference -> {
                        final Activity activity = getActivity();
                        if (activity == null) {
                            return true;
                        }
                        Toast.makeText(activity, R.string.update_checking, Toast.LENGTH_SHORT).show();
                        Updater.find(info -> activity.runOnUiThread(() -> {
                            if (activity.isFinishing()) {
                                return;
                            }
                            if (info != null) {
                                UpdateUi.showAvailableDialog(activity, info, null, false);
                            } else {
                                Toast.makeText(activity, R.string.update_none, Toast.LENGTH_SHORT).show();
                            }
                        }));
                        return true;
                    });
                }
            }
        }

        // A D-pad can only reach a row that is laid out, and when focus search fails
        // LinearLayoutManager extends the layout by a third of a screen and looks again — which is
        // not past a run of unfocusable rows: the group a switched-off "dependency" disables, or the
        // warning text in "Dangerous". Lay out two screens extra so the next focusable row is there.
        @Override
        public RecyclerView.LayoutManager onCreateLayoutManager() {
            return new LinearLayoutManager(getContext()) {
                @Override
                protected void calculateExtraLayoutSpace(@NonNull RecyclerView.State state,
                                                         @NonNull int[] extraLayoutSpace) {
                    extraLayoutSpace[0] = extraLayoutSpace[1] = getHeight() * 2;
                }
            };
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            if (Build.VERSION.SDK_INT >= 29) {
                recyclerView = getListView();
            }
            // Long-pressing a player button lands on the section that button is about, the way a
            // quick-settings tile opens its own page. Only on the way in: coming back from a
            // sub-screen must not yank the list somewhere the user did not ask for.
            if (savedInstanceState == null && getArguments() == null) {
                final String key = requireActivity().getIntent().getStringExtra(EXTRA_SCROLL_TO);
                if (key != null) {
                    openAtPreference(key, 3);
                }
            }
        }

        /**
         * Opens the list on one row: scrolled so the section header above it is still on screen, and
         * with the row itself focused. scrollToPreference alone scrolls the row barely into view — at
         * the bottom edge, inside a TV's overscan — and leaves a remote's focus on the first row, so
         * the first D-pad press yanks the list straight back to the top.
         *
         * The holder can be missing on the first pre-draw, hence the few attempts. On a phone the
         * focus request is a no-op: a preference row is not focusable in touch mode.
         */
        private void openAtPreference(final String key, final int attemptsLeft) {
            final RecyclerView list = getListView();
            final RecyclerView.Adapter<?> adapter = list == null ? null : list.getAdapter();
            if (attemptsLeft <= 0 || !(adapter instanceof PreferenceGroup.PreferencePositionCallback)
                    || !(list.getLayoutManager() instanceof LinearLayoutManager)) {
                return;
            }
            final int position = ((PreferenceGroup.PreferencePositionCallback) adapter)
                    .getPreferenceAdapterPosition(key);
            if (position == RecyclerView.NO_POSITION) {
                return;
            }
            final LinearLayoutManager manager = (LinearLayoutManager) list.getLayoutManager();
            manager.scrollToPositionWithOffset(Math.max(0, position - 1), 0);
            OneShotPreDrawListener.add(list, () -> {
                final RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(position);
                if (holder == null) {
                    openAtPreference(key, attemptsLeft - 1);
                    return;
                }
                holder.itemView.requestFocus();
                // Taking the focus makes RecyclerView scroll the row just barely into view, which
                // parks it against the bottom edge — inside a TV's overscan. Put the list back where
                // it belongs afterwards: one row before the target, so its category header shows.
                list.post(() -> manager.scrollToPositionWithOffset(Math.max(0, position - 1), 0));
            });
        }

        /**
         * Puts a chip of the actual colour in front of every label, in the list and in the summary —
         * a colour is recognised, a colour name is recalled. Outlined, or white would be a blank gap
         * on a light row and the transparent entry would show nothing at all.
         */
        private void showColorChips(final ListPreference preference) {
            final CharSequence[] entries = preference.getEntries();
            final CharSequence[] values = preference.getEntryValues();
            final CharSequence[] chipped = new CharSequence[entries.length];
            for (int i = 0; i < entries.length; i++) {
                final GradientDrawable chip = new GradientDrawable();
                chip.setShape(GradientDrawable.OVAL);
                chip.setColor(Color.parseColor(values[i].toString()));
                chip.setStroke(Math.max(1, Utils.dpToPx(1)), 0x80808080);
                // The span replaces its one character with the chip's own width, so the space after it
                // is the whole gap — no guessing how wide two blanks come out in the current font.
                final SpannableStringBuilder label =
                        new SpannableStringBuilder("   ").append(entries[i]);
                label.setSpan(new ChipSpan(chip), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                chipped[i] = label;
            }
            preference.setEntries(chipped);
        }

        /**
         * Sits the chip on the optical middle of the text. ImageSpan's own alignments are baseline and
         * line-bottom, both of which hang a round chip visibly low, and ALIGN_CENTER is API 29+.
         */
        private static class ChipSpan extends ImageSpan {
            ChipSpan(final Drawable drawable) {
                super(drawable);
            }

            @Override
            public int getSize(@NonNull Paint paint, CharSequence text, int start, int end,
                               @Nullable Paint.FontMetricsInt fontMetrics) {
                resize(paint);
                return super.getSize(paint, text, start, end, fontMetrics);
            }

            /**
             * The chip is sized off the text beside it rather than a fixed dp, so it holds its
             * proportion on a TV row (larger type than a phone's) and at any system font size.
             */
            private void resize(final Paint paint) {
                final int size = Math.round(paint.getTextSize() * 0.7f);
                final Drawable chip = getDrawable();
                if (chip.getBounds().height() != size) {
                    chip.setBounds(0, 0, size, size);
                }
            }

            @Override
            public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x,
                             int top, int y, int bottom, @NonNull Paint paint) {
                resize(paint);
                final Drawable chip = getDrawable();
                final Paint.FontMetricsInt metrics = paint.getFontMetricsInt();
                // y is the baseline; ascent is negative, so this lands mid-glyph rather than mid-line.
                final float middle = y + (metrics.ascent + metrics.descent) / 2f;
                canvas.save();
                canvas.translate(x, middle - chip.getBounds().height() / 2f);
                chip.draw(canvas);
                canvas.restore();
            }
        }

        /** @return false to reject the pick, which is what a preference change listener does. */
        private boolean allowColor(final String textColor, final String backgroundColor) {
            if (textColor == null || backgroundColor == null
                    || Color.parseColor(textColor) != Color.parseColor(backgroundColor)) {
                return true;
            }
            Toast.makeText(getContext(), R.string.pref_subtitle_color_clash, Toast.LENGTH_SHORT).show();
            return false;
        }

        /** The chosen languages, in order, or a note that nothing is preferred. */
        /** Everything the search screen holds besides the mode itself. */
        private static final String[] SEARCH_DEPENDENTS = {
                "subtitleTranslateMode", "subtitleTranslateBackends", "subtitleSearchLanguage",
                "subtitleSourceRest", "subtitleSourceStremio", "subtitleSourceShegu",
                "subtitleSourceOpenSubtitles",
        };

        /**
         * Reflects the chosen mode: the row that leads here reports it, and with no search running the
         * rows that configure one are greyed out rather than left live and inert. This is by hand
         * because app:dependency watches a parent's enablement, not its value.
         */
        private void applySearchMode(final ListPreference searchMode, final String mode,
                                     final ListPreference translateMode) {
            final boolean searching = !Prefs.SEARCH_OFF.equals(mode);
            for (final String key : SEARCH_DEPENDENTS) {
                final Preference dependent = findPreference(key);
                if (dependent != null) {
                    dependent.setEnabled(searching);
                }
            }
            // The endpoint list answers to both: no search means no translation either, and the list is
            // meaningless while translation itself is off.
            enableTranslateBackends(searching, translateMode == null ? null : translateMode.getValue());
            // Null while the fragment is rooted at the search screen: the row lives one level up.
            final Preference screen = findPreference("subtitleSearchScreen");
            final int index = searchMode.findIndexOfValue(mode);
            if (screen != null && index >= 0) {
                screen.setSummary(searchMode.getEntries()[index]);
            }
        }

        /**
         * The chosen entry as the summary, and with it who the text is handed to — a machine
         * translation is somebody else's service, and that belongs next to the switch that turns it on
         * rather than in a changelog.
         */
        private void applyTranslateMode(final ListPreference translateMode, final String mode,
                                        final ListPreference searchMode) {
            enableTranslateBackends(searchMode == null
                    || !Prefs.SEARCH_OFF.equals(searchMode.getValue()), mode);
            final int index = translateMode.findIndexOfValue(mode);
            if (index < 0) {
                return;
            }
            final CharSequence entry = translateMode.getEntries()[index];
            translateMode.setSummary(Prefs.TRANSLATE_OFF.equals(mode)
                    ? entry : getString(R.string.pref_subtitle_translate_summary, entry));
        }

        /** The endpoint list is live only while a search runs and translation is on. */
        private void enableTranslateBackends(final boolean searching, final String translateMode) {
            final Preference backends = findPreference("subtitleTranslateBackends");
            if (backends != null) {
                backends.setEnabled(searching && !Prefs.TRANSLATE_OFF.equals(translateMode));
            }
        }

        private void updateLanguageSummary(final Preference preference,
                                           final LinkedHashMap<String, String> languages,
                                           final String stored, final int emptyRes) {
            final List<String> chosen = Utils.splitLanguages(stored);
            if (chosen.isEmpty()) {
                preference.setSummary(emptyRes);
                return;
            }
            final List<String> labels = new ArrayList<>();
            for (final String code : chosen) {
                final String label = languages.get(code);
                labels.add(label != null ? label : code);
            }
            preference.setSummary(TextUtils.join(", ", labels));
        }

        /** Offered at the top of the picker: what the device speaks, and what the open media carries. */
        private List<String> pinnedLanguages() {
            final List<String> pinned = new ArrayList<>(Arrays.asList(Utils.getDeviceLanguages()));
            final String[] media = requireActivity().getIntent()
                    .getStringArrayExtra(EXTRA_MEDIA_LANGUAGES);
            if (media != null) {
                for (final String language : media) {
                    if (!pinned.contains(language)) {
                        pinned.add(language);
                    }
                }
            }
            return pinned;
        }

        }
}