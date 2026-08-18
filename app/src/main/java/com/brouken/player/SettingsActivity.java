package com.brouken.player;

import android.content.res.Configuration;
import android.graphics.Color;
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
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.brouken.player.together.AliasGenerator;
import com.brouken.player.together.Relay;
import com.brouken.player.together.Room;
import com.brouken.player.update.Updater;
import com.brouken.player.update.UpdateUi;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;

public class SettingsActivity extends AppCompatActivity {

    /** ISO-639-2/T codes of the audio tracks in the clip the player has open, if any. */
    public static final String EXTRA_MEDIA_LANGUAGES = "mediaLanguages";

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

    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            // Inflation materializes switch defaults, so record whether the key was already
            // persisted before that happens.
            boolean hadAllowSystemFrameRateKey =
                    androidx.preference.PreferenceManager.getDefaultSharedPreferences(getContext())
                            .contains("allowSystemFrameRate");

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

            // Fire OS and some TV boxes ship no system captioning screen, and the preference
            // launches its intent itself — an unhandled one takes the app down.
            Preference preferenceCaptioning = findPreference("captioningPreferences");
            if (preferenceCaptioning != null && (preferenceCaptioning.getIntent() == null
                    || preferenceCaptioning.getIntent().resolveActivity(
                            getContext().getPackageManager()) == null)) {
                preferenceCaptioning.setVisible(false);
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
            if (preferenceLanguageAudio != null) {
                final LinkedHashMap<String, String> languages = getLanguages();
                updateLanguageSummary(preferenceLanguageAudio, languages);
                preferenceLanguageAudio.setOnPreferenceClickListener(preference -> {
                    AudioLanguagePriorityDialog.show(requireContext(),
                            getString(R.string.pref_language_audio),
                            Utils.splitLanguages(Prefs.getLanguageAudio(requireContext())),
                            languages, pinnedLanguages(), picked -> {
                                Prefs.setLanguageAudio(requireContext(), TextUtils.join(",", picked));
                                updateLanguageSummary(preference, languages);
                            });
                    return true;
                });
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
        }

        /** The chosen languages, in order, or a note that nothing is preferred. */
        private void updateLanguageSummary(final Preference preference,
                                           final LinkedHashMap<String, String> languages) {
            final List<String> chosen = Utils.splitLanguages(Prefs.getLanguageAudio(requireContext()));
            if (chosen.isEmpty()) {
                preference.setSummary(R.string.pref_language_audio_none);
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

        LinkedHashMap<String, String> getLanguages() {
            LinkedHashMap<String, String> languages = new LinkedHashMap<>();
            for (Locale locale : Locale.getAvailableLocales()) {
                try {
                    // MissingResourceException: Couldn't find 3-letter language code for zz
                    String key = locale.getISO3Language();
                    if (languages.containsKey(key)) {
                        // Hundreds of locales collapse onto the same language here, and the display
                        // name never depends on region or script — resolving it again only burns
                        // main-thread time while Settings opens.
                        continue;
                    }
                    String language = locale.getDisplayLanguage();
                    int length = language.offsetByCodePoints(0, 1);
                    if (!language.isEmpty()) {
                        language = language.substring(0, length).toUpperCase(locale) + language.substring(length);
                    }
                    String value = language + " [" + key + "]";
                    languages.put(key, value);
                } catch (MissingResourceException e) {
                    e.printStackTrace();
                }
            }
            Collator collator = Collator.getInstance();
            collator.setStrength(Collator.PRIMARY);
            Utils.orderByValue(languages, collator::compare);
            return languages;
        }
    }
}