package com.brouken.player;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ImageSpan;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.StaticLayout;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.app.Activity;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.core.content.ContextCompat;
import androidx.core.view.OneShotPreDrawListener;
import androidx.core.view.WindowCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceGroupAdapter;
import androidx.preference.PreferenceScreen;
import androidx.preference.PreferenceViewHolder;
import androidx.preference.SwitchPreferenceCompat;
import androidx.preference.TwoStatePreference;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.color.MaterialColors;

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

    /** Key of the sub-screen row last opened, so Back can put the list back on it. */
    private String openedScreenKey;

    /** The surface tones without the AMOLED overlay, resolved in onCreate before it is applied. */
    private int plainSurface;
    private int plainCard;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        // Before super.onCreate, or AppCompat applies the old mode first and then recreates.
        getDelegate().setLocalNightMode(Prefs.getNightMode(this));

        super.onCreate(savedInstanceState);

        // After super, so the configuration already carries the night mode set above, and before
        // anything asks the window for its decor view: reading getDecorView() builds it, and the decor
        // takes its background out of the theme exactly as it stands at that moment. Pure black is a
        // dark-theme idea only.
        final int night = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        // Read before the overlay goes on, because applyStyle cannot be undone and these are the
        // values AMOLED has to be able to go back to. Taken from this theme in this configuration —
        // a ContextThemeWrapper built here does not carry the local night mode and answers light.
        plainSurface = MaterialColors.getColor(this, R.attr.colorSurface, Color.BLACK);
        plainCard = MaterialColors.getColor(this, R.attr.colorSurfaceContainer, Color.DKGRAY);
        if (night == Configuration.UI_MODE_NIGHT_YES && Prefs.isAmoledBlack(this)) {
            getTheme().applyStyle(R.style.ThemeOverlay_JustPlus_Amoled, true);
        }

        // Hence below the overlay and not above it, where this block used to sit: its getDecorView()
        // call was building the decor against the un-overlaid theme, which left the window grey while
        // the cards drawn later came out black.
        if (Build.VERSION.SDK_INT >= 29) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            );
            getWindow().setNavigationBarColor(Color.TRANSPARENT);
        }

        // The bar takes the window's own colour (see Theme.Settings), so its icons have to follow the
        // theme actually in force — which is the one chosen above, not the system's. Only the light-icon
        // bit, and on every SDK: the block above replaces the whole flag set.
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(night != Configuration.UI_MODE_NIGHT_YES);

        setContentView(R.layout.settings_activity);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings, new SettingsFragment())
                    .commit();
        }
        setSupportActionBar(findViewById(R.id.toolbar));
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

        // The title belongs over the column, not at the far edge of a 4K window.
        final MaterialToolbar bar = findViewById(R.id.toolbar);
        final View settingsRoot = findViewById(R.id.settings_layout);
        settingsRoot.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or2, ob) -> {
            // Padding rather than content insets: those move the title and leave the up arrow at the
            // window edge, which reads as two different left margins. On a TV it also brings the arrow
            // inside the overscan-safe strip, where a remote can be sure of finding it.
            // Only the part the toolbar does not already inset by itself, or a phone — where the two
            // are the same 16dp — would indent the title twice.
            final int extra = Math.max(0,
                    contentSideInset(this, r - l) - bar.getContentInsetStart());
            bar.setPadding(extra, bar.getPaddingTop(), extra, bar.getPaddingBottom());
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

    /**
     * Side inset that holds the content to one readable column, given the room there is.
     *
     * Material asks for a maximum width rather than letting content stretch, and hands widths past
     * 840dp to layouts with more than one pane. This screen is deliberately one pane — the switches
     * are meant to be read, not hidden behind rows — so the honest single-pane answer is to stop the
     * column at the width a label-and-control row still reads as one thing and centre it. 720dp sits
     * inside Material's medium window, which is the widest a single pane is meant to get.
     *
     * A 4K monitor therefore gets the same column with a lot of space around it. Filling that space
     * properly needs a second pane, which is a different screen, not a wider inset.
     *
     * On a TV the floor is the overscan margin the TV guidance asks for (48dp horizontally, 5% of
     * 960dp) rather than the phone's 16dp: a set narrow enough to miss the cap — 1280x720 at 320 dpi
     * is exactly 640dp across — would otherwise put rows in the strip a TV may not show.
     */
    static int contentSideInset(final Context context, final int availableWidth) {
        final int base = Utils.isTvBox(context) ? Utils.dpToPx(48) : Utils.dpToPx(16);
        return Math.max(base, (availableWidth - Utils.dpToPx(720)) / 2);
    }

    /**
     * Repaints the surfaces the AMOLED option owns, so toggling it does not need the window rebuilt.
     * Rebuilding was visible: the pressed row lit, the switch travelled, and only then did the whole
     * screen cut over in one frame — two beats where the eye expects none.
     *
     * ponytail: the theme object itself is left alone, so a dialog opened between a toggle and the
     * next launch still carries the previous surface tone (a 20/255 difference on its background).
     * Give the overlay an inverse and apply that here if it ever shows.
     */
    void repaintAmoledSurfaces() {
        final boolean amoled = isNight() && Prefs.isAmoledBlack(this);
        final int surface = amoled ? ContextCompat.getColor(this, R.color.black) : plainSurface;
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(surface));
        getWindow().setStatusBarColor(surface);
        final View toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setBackgroundColor(surface);
        }
        final int card = amoled ? ContextCompat.getColor(this, R.color.amoled_card) : plainCard;
        GroupCards.repaint(recyclerView, card);
    }

    private boolean isNight() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
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
        openedScreenKey = screen.getKey();
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

            // Before inflation: the preferences below read these keys, and either value may still be
            // living in the shape it was stored in two versions ago.
            Prefs.getHoldSpeedMode(requireContext());
            Prefs.getSubtitleSearchMode(requireContext());
            Prefs.getSubtitleTranslate(requireContext());

            setPreferencesFromResource(R.xml.root_preferences, rootKey);

            final Preference preferenceAmoled = findPreference("amoledBlack");
            if (preferenceAmoled != null) {
                preferenceAmoled.setOnPreferenceChangeListener((preference, value) -> {
                    // Posted, because returning true is what persists the value and repaintAmoledSurfaces
                    // reads it back. Repainting rather than recreating: the window is the same one, so the
                    // switch keeps animating over surfaces that have already changed under it.
                    // getActivity, not requireActivity: a row can be bound on a fragment that is on its
                    // way out, and throwing there would take the app down over a colour.
                    final Activity host = getActivity();
                    if (host instanceof SettingsActivity) {
                        new Handler(Looper.getMainLooper())
                                .post(((SettingsActivity) host)::repaintAmoledSurfaces);
                    }
                    return true;
                });
            }
            syncAmoledEnabled();

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
            Preference preferenceHoldSpeed = findPreference("holdSpeedMode");
            if (preferenceHoldSpeed != null && Utils.isTvBox(getContext())) {
                // Same reason: there is no finger to hold on the picture.
                preferenceHoldSpeed.setVisible(false);
            }
            Preference preferenceSingleBack = findPreference("tvSingleBack");
            if (preferenceSingleBack != null && !Utils.isTvBox(getContext())) {
                // Only the remote gets asked for a second Back; touch already leaves on the first one.
                preferenceSingleBack.setVisible(false);
            }
            Preference preferenceKeepAwake = findPreference("keepAwakeOnPause");
            if (preferenceKeepAwake != null && !Utils.isTvBox(getContext())) {
                // Holding a phone awake through a pause drains it for no one's benefit: the complaint is
                // the TV's screensaver, which takes the audio output and the process down with it.
                preferenceKeepAwake.setVisible(false);
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

            // Three ordered language lists, one shape. Several hundred locales, resolved and sorted
            // once for all of them.
            final LinkedHashMap<String, String> languages = Utils.allLanguages();
            bindLanguageRow("languageAudio", languages, R.string.pref_language_audio,
                    R.string.pref_language_audio_none,
                    Prefs.getLanguageAudio(requireContext()), Prefs::setLanguageAudio);
            bindLanguageRow("languageSubtitle", languages, R.string.pref_language_subtitle,
                    R.string.pref_language_subtitle_none,
                    Prefs.getLanguageSubtitle(requireContext()), Prefs::setLanguageSubtitle);
            bindLanguageRow("languageSubtitleSecondary", languages,
                    R.string.pref_language_subtitle_secondary,
                    R.string.pref_language_subtitle_secondary_none,
                    Prefs.getLanguageSubtitleSecondary(requireContext()),
                    Prefs::setLanguageSubtitleSecondary);

            // The second line lives behind its own row, and Off there means the whole feature rather
            // than just the line — so the rows that would configure one are greyed out rather than left
            // live and inert.
            final ListPreference secondaryMode = findPreference("subtitleSecondaryMode");
            if (secondaryMode != null) {
                applySecondaryMode(secondaryMode, secondaryMode.getValue());
                secondaryMode.setOnPreferenceChangeListener((preference, value) -> {
                    applySecondaryMode(secondaryMode, (String) value);
                    return true;
                });
            }

            // The search lives behind its own row, so its state has to read from the outside: without
            // this the row says nothing and the whole feature is a tap away from being discovered.
            final ListPreference searchMode = findPreference("subtitleSearchMode");
            final SwitchPreferenceCompat translate = findPreference("subtitleTranslateOn");
            if (searchMode != null) {
                applySearchMode(searchMode, searchMode.getValue(), translate);
                searchMode.setOnPreferenceChangeListener((preference, value) -> {
                    applySearchMode(searchMode, (String) value, translate);
                    return true;
                });
            }
            if (translate != null) {
                translate.setOnPreferenceChangeListener((preference, value) -> {
                    enableTranslateBackends(searchMode == null
                            || !Prefs.SEARCH_OFF.equals(searchMode.getValue()), (Boolean) value);
                    // A translation cached under the previous choice would keep being served for
                    // everything watched recently, so the new choice would look like it did nothing.
                    SubtitleUtils.clearTranslatedCache(requireContext());
                    return true;
                });
            }

            // Both of these exist to take a source out of the picture while a result is being
            // chased down, and neither is anybody's setting: which index answered and which endpoint
            // translated are not decisions a viewer has any way to judge. So they are shown in a debug
            // build and are not in a release one — where Prefs also stops reading what they wrote.
            if (!BuildConfig.DEBUG) {
                final Preference sources = findPreference("subtitleSourcesCategory");
                if (sources != null) {
                    sources.setVisible(false);
                }
                final Preference endpoints = findPreference("subtitleTranslateBackends");
                if (endpoints != null) {
                    endpoints.setVisible(false);
                }
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

            // Both subtitle lines, each with its own pair. The second line reuses the same two lists,
            // so it gets the chips and the clash check for nothing.
            bindColorPair("subtitleTextColor", "subtitleBackground");
            bindColorPair("subtitleSecondaryTextColor", "subtitleSecondaryBackground");

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
                                UpdateUi.showAvailableDialog(activity, activity, info, null, false);
                            } else {
                                Toast.makeText(activity, R.string.update_none, Toast.LENGTH_SHORT).show();
                            }
                        }));
                        return true;
                    });
                }
            }
        }

        /**
         * Rows are corner-clipped to the card they sit in, so a ripple — and the focus highlight a
         * D-pad leaves on TV — stops at the rounded corner instead of squaring it off.
         */
        @Override
        protected RecyclerView.Adapter onCreateAdapter(@NonNull PreferenceScreen preferenceScreen) {
            return new PreferenceGroupAdapter(preferenceScreen) {
                @Override
                public void onBindViewHolder(@NonNull PreferenceViewHolder holder, int position) {
                    super.onBindViewHolder(holder, position);
                    GroupCards.clip(this, holder.itemView, position);
                    final TextView title = (TextView) holder.findViewById(android.R.id.title);
                    if (title != null) {
                        // The library's row keeps the title on one line and marquees the overflow,
                        // which on a phone just clips it ("Allow system to adjust refres.."), and the
                        // card's side insets make the line shorter still. Let it wrap instead.
                        title.setSingleLine(false);
                        title.setEllipsize(null);
                    }
                    reserveTallerSummary(holder, getItem(position));
                    bindThemeMode(holder, getItem(position));
                }
            };
        }

        /**
         * A switch whose summaryOn and summaryOff wrap to a different number of lines makes its row —
         * and everything below it — jump the moment it is toggled. Reserve the taller of the two up
         * front, so flipping the switch only changes the words.
         */
        private static void reserveTallerSummary(final PreferenceViewHolder holder,
                                                 final Preference preference) {
            final View view = holder.findViewById(android.R.id.summary);
            if (!(view instanceof TextView)) {
                return;
            }
            final TextView summary = (TextView) view;
            final CharSequence on = preference instanceof TwoStatePreference
                    ? ((TwoStatePreference) preference).getSummaryOn() : null;
            final CharSequence off = preference instanceof TwoStatePreference
                    ? ((TwoStatePreference) preference).getSummaryOff() : null;
            if (on == null || off == null || on.toString().equals(off.toString())) {
                summary.setMinLines(0);
                return;
            }
            // Straight away where the row already has a width — a re-bind after the switch was flipped
            // does — because clearing the reservation and restoring it before the draw is itself the
            // jump this is here to prevent.
            applyTallerSummary(summary, on, off);
            // A holder bound for the first time has no width yet, but it has one before the draw.
            OneShotPreDrawListener.add(summary, () -> applyTallerSummary(summary, on, off));
        }

        private static void applyTallerSummary(final TextView summary, final CharSequence on,
                                               final CharSequence off) {
            // The summary is laid out wrap_content, so its own width is the width of whichever text it
            // happens to hold — measuring the other one against that is what reserved a line for rows
            // where both fit on one. The room either text may wrap into is the column it sits in.
            if (!(summary.getParent() instanceof View)) {
                return;
            }
            final View column = (View) summary.getParent();
            final int width = column.getWidth() - column.getPaddingLeft() - column.getPaddingRight()
                    - summary.getPaddingLeft() - summary.getPaddingRight();
            if (width <= 0) {
                return;
            }
            final int lines = Math.max(lineCount(summary, on, width), lineCount(summary, off, width));
            if (summary.getMinLines() != lines) {
                summary.setMinLines(lines);
            }
        }

        private static int lineCount(final TextView view, final CharSequence text, final int width) {
            return new StaticLayout(text, view.getPaint(), width, Layout.Alignment.ALIGN_NORMAL,
                    view.getLineSpacingMultiplier(), view.getLineSpacingExtra(), false).getLineCount();
        }

        /**
         * The theme row is a segmented control rather than a dialog behind a row: three choices, all
         * worth seeing without opening anything. Bound here because the row is a plain Preference
         * carrying a custom layout, which is one class fewer than a Preference subclass would be.
         */
        private void bindThemeMode(final PreferenceViewHolder holder, final Preference preference) {
            if (preference == null || !Prefs.THEME_MODE_KEY.equals(preference.getKey())) {
                return;
            }
            // The row itself, not a child: Preference.onBindViewHolder resets the id of the view it
            // binds, so an id on the layout root would not survive to be looked up here.
            if (!(holder.itemView instanceof MaterialButtonToggleGroup)) {
                return;
            }
            final MaterialButtonToggleGroup group = (MaterialButtonToggleGroup) holder.itemView;
            final Context context = group.getContext();
            // A TV box has no system theme of its own to follow — PlayerActivity makes dark the default
            // there, so "System" resolves to dark and does exactly what the button next to it does. An
            // option that duplicates its neighbour is worse than one fewer option, so it goes; a choice
            // stored from a phone becomes the Dark it already behaves as.
            if (Utils.isTvBox(context)) {
                group.findViewById(R.id.theme_mode_system).setVisibility(View.GONE);
                if (Prefs.THEME_SYSTEM.equals(Prefs.getThemeMode(context))) {
                    Prefs.setThemeMode(context, Prefs.THEME_DARK);
                }
            }
            // The holder is recycled, so the listener from its last binding has to go first.
            group.clearOnButtonCheckedListeners();
            group.check(buttonFor(Prefs.getThemeMode(context)));
            group.addOnButtonCheckedListener((checkedGroup, checkedId, isChecked) -> {
                if (!isChecked) {
                    return;
                }
                Prefs.setThemeMode(context, modeFor(checkedId));
                // Light and dark can share a configuration, in which case the delegate below has
                // nothing to recreate and this row has to be brought up to date here.
                syncAmoledEnabled();
                // Recreates the activity by itself, and only when the mode actually differs.
                ((SettingsActivity) requireActivity()).getDelegate()
                        .setLocalNightMode(Prefs.getNightMode(context));
            });
        }

        /**
         * Pure black is something only a dark theme can be, so with Light chosen the row is greyed
         * rather than hidden: it says the option exists and what it waits for. Under System it stays
         * live — it takes effect whenever the system turns dark.
         */
        private void syncAmoledEnabled() {
            final Preference amoled = findPreference("amoledBlack");
            if (amoled != null) {
                amoled.setEnabled(!Prefs.THEME_LIGHT.equals(Prefs.getThemeMode(requireContext())));
            }
        }

        private static int buttonFor(final String mode) {
            if (Prefs.THEME_DARK.equals(mode)) {
                return R.id.theme_mode_dark;
            }
            if (Prefs.THEME_LIGHT.equals(mode)) {
                return R.id.theme_mode_light;
            }
            return R.id.theme_mode_system;
        }

        private static String modeFor(final int buttonId) {
            if (buttonId == R.id.theme_mode_dark) {
                return Prefs.THEME_DARK;
            }
            if (buttonId == R.id.theme_mode_light) {
                return Prefs.THEME_LIGHT;
            }
            return Prefs.THEME_SYSTEM;
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
            final RecyclerView cardList = getListView();
            if (cardList != null) {
                // The card's own hairlines replace the list's full-width dividers, which would cut
                // across the card edges.
                setDivider(null);
                setDividerHeight(0);
                cardList.addItemDecoration(new GroupCards(cardList.getContext()));
                // Toggling a switch re-binds its row, and cross-fading the old text over the new one
                // reads as a flicker in a list that is otherwise still.
                if (cardList.getItemAnimator() instanceof SimpleItemAnimator) {
                    ((SimpleItemAnimator) cardList.getItemAnimator())
                            .setSupportsChangeAnimations(false);
                }
            }
            // Unconditionally: the inset padding below wants it only on 29+, but repaintAmoledSurfaces
            // needs the list on every SDK, or the cards keep the old tone on 23-28 until the next launch.
            recyclerView = getListView();
            if (getArguments() != null) {
                // A sub-screen is a replaced fragment, and a replaced fragment starts with nothing
                // focused: the first D-pad press then goes wherever the view root guesses instead
                // of into the list. Hand it the first row.
                if (savedInstanceState == null) {
                    openAtPosition(0, 3);
                }
                return;
            }
            final SettingsActivity activity = (SettingsActivity) requireActivity();
            final String returning = activity.openedScreenKey;
            activity.openedScreenKey = null;
            if (returning != null) {
                // Back from a sub-screen rebuilt this list from scratch, so it would open at the
                // top with no row focused. Put it back on the row the sub-screen was opened from.
                openAtPreference(returning, 3);
            } else if (savedInstanceState == null) {
                // Long-pressing a player button lands on the section that button is about, the way
                // a quick-settings tile opens its own page.
                final String key = activity.getIntent().getStringExtra(EXTRA_SCROLL_TO);
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
            if (!(adapter instanceof PreferenceGroup.PreferencePositionCallback)) {
                return;
            }
            openAtPosition(((PreferenceGroup.PreferencePositionCallback) adapter)
                    .getPreferenceAdapterPosition(key), attemptsLeft);
        }

        /** Same, for a row known by its place in the list rather than by a key. */
        private void openAtPosition(final int position, final int attemptsLeft) {
            final RecyclerView list = getListView();
            if (attemptsLeft <= 0 || position == RecyclerView.NO_POSITION || list == null
                    || !(list.getLayoutManager() instanceof LinearLayoutManager)) {
                return;
            }
            final LinearLayoutManager manager = (LinearLayoutManager) list.getLayoutManager();
            manager.scrollToPositionWithOffset(Math.max(0, position - 1), 0);
            OneShotPreDrawListener.add(list, () -> {
                final RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(position);
                if (holder == null) {
                    openAtPosition(position, attemptsLeft - 1);
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

        /** Where an edited language list is written back to. */
        private interface LanguageWriter {
            void write(Context context, String languages);
        }

        /**
         * One ordered language list: the chosen languages as the summary, and the same picker behind
         * the row. Three of these — audio, subtitles, second subtitles — and nothing about them differs
         * but the key, the title and where the result is stored.
         */
        private void bindLanguageRow(final String key, final LinkedHashMap<String, String> languages,
                                     final int titleRes, final int noneRes, final String stored,
                                     final LanguageWriter writer) {
            final Preference row = findPreference(key);
            if (row == null) {
                return;
            }
            // Held rather than re-read: the row can be opened again without leaving the screen, and the
            // picker has to start from what the last edit left, not from what was stored at bind time.
            final String[] current = {stored};
            updateLanguageSummary(row, languages, current[0], noneRes);
            row.setOnPreferenceClickListener(preference -> {
                LanguagePriorityDialog.show(requireContext(), getString(titleRes),
                        noneRes, R.string.pref_language_audio_add,
                        Utils.splitLanguages(current[0]),
                        languages, pinnedLanguages(), picked -> {
                            current[0] = TextUtils.join(",", picked);
                            writer.write(requireContext(), current[0]);
                            updateLanguageSummary(preference, languages, current[0], noneRes);
                        });
                return true;
            });
        }

        /** One text/background pair: colour chips on both lists, and neither allowed to match the other. */
        private void bindColorPair(final String textColorKey, final String backgroundKey) {
            final ListPreference textColor = findPreference(textColorKey);
            final ListPreference background = findPreference(backgroundKey);
            if (textColor == null || background == null) {
                return;
            }
            showColorChips(textColor);
            showColorChips(background);
            // Text in the colour of its own box is invisible subtitles, and the two lists are far
            // enough apart that nobody would connect the cause. Refuse the pick instead.
            textColor.setOnPreferenceChangeListener((preference, value) ->
                    allowColor((String) value, background.getValue()));
            background.setOnPreferenceChangeListener((preference, value) ->
                    allowColor(textColor.getValue(), (String) value));
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
        /** Everything the second line's screen holds besides the mode itself. */
        private static final String[] SECONDARY_DEPENDENTS = {
                "languageSubtitleSecondary", "subtitleSecondaryScale", "subtitleSecondaryTextColor",
                "subtitleSecondaryBackground",
        };

        /**
         * Reflects the chosen mode, the same way {@link #applySearchMode} does for the search: the row
         * that leads here reports it, and with the second line off the rows that dress one are greyed
         * out. By hand for the same reason — app:dependency watches a parent's enablement, not its value.
         */
        private void applySecondaryMode(final ListPreference secondaryMode, final String mode) {
            final boolean enabled = !Prefs.SECONDARY_OFF.equals(mode);
            for (final String key : SECONDARY_DEPENDENTS) {
                final Preference dependent = findPreference(key);
                if (dependent != null) {
                    dependent.setEnabled(enabled);
                }
            }
            // Null while the fragment is rooted at the second line's screen: the row lives one level up.
            final Preference screen = findPreference("subtitleSecondaryScreen");
            final int index = secondaryMode.findIndexOfValue(mode);
            if (screen != null && index >= 0) {
                screen.setSummary(secondaryMode.getEntries()[index]);
            }
        }

        /** Everything the search screen holds besides the mode itself. */
        private static final String[] SEARCH_DEPENDENTS = {
                "subtitleTranslateOn", "subtitleTranslateBackends", "subtitleSearchLanguage",
                "subtitleSourceRest", "subtitleSourceStremio", "subtitleSourceShegu",
                "subtitleSourceOpenSubtitles",
        };

        /**
         * Reflects the chosen mode: the row that leads here reports it, and with no search running the
         * rows that configure one are greyed out rather than left live and inert. This is by hand
         * because app:dependency watches a parent's enablement, not its value.
         */
        private void applySearchMode(final ListPreference searchMode, final String mode,
                                     final SwitchPreferenceCompat translate) {
            final boolean searching = !Prefs.SEARCH_OFF.equals(mode);
            for (final String key : SEARCH_DEPENDENTS) {
                final Preference dependent = findPreference(key);
                if (dependent != null) {
                    dependent.setEnabled(searching);
                }
            }
            // The endpoint list answers to both: no search means no translation either, and the list is
            // meaningless while translation itself is off.
            enableTranslateBackends(searching, translate == null || translate.isChecked());
            // Null while the fragment is rooted at the search screen: the row lives one level up.
            final Preference screen = findPreference("subtitleSearchScreen");
            final int index = searchMode.findIndexOfValue(mode);
            if (screen != null && index >= 0) {
                screen.setSummary(searchMode.getEntries()[index]);
            }
        }

        /**
         * The endpoint list is live only while a search runs and translation is on. By hand rather than
         * app:dependency, which answers to one parent and this answers to two.
         */
        private void enableTranslateBackends(final boolean searching, final boolean translating) {
            final Preference backends = findPreference("subtitleTranslateBackends");
            if (backends != null) {
                backends.setEnabled(searching && translating);
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

    /**
     * Groups the list the way a Material settings screen is grouped: every run of rows under one
     * category is one rounded card, inset from the edges, with a hairline between its rows.
     *
     * Drawn behind the rows instead of being set as their background, so each row keeps the ripple
     * and the D-pad focus highlight that androidx.preference gives it — replacing the background is
     * what costs a TV remote its focus indicator.
     */
    private static final class GroupCards extends RecyclerView.ItemDecoration {

        private static final int RADIUS = Utils.dpToPx(20);
        private final int inset = Utils.dpToPx(16);

        private final int headerGap = Utils.dpToPx(8);
        private final int hairlineInset = Utils.dpToPx(16);
        private final Paint card = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint hairline = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF bounds = new RectF();

        GroupCards(final Context context) {
            card.setColor(MaterialColors.getColor(context, R.attr.colorSurfaceContainer, Color.DKGRAY));
            hairline.setColor(MaterialColors.getColor(context, R.attr.colorOutlineVariant, Color.GRAY));
            hairline.setStrokeWidth(Utils.dpToPx(1));
        }

        /**
         * Recolours the cards of a list already on screen. The hairlines are left alone — outlineVariant
         * does not move with the AMOLED option, and they read the same over either card tone.
         */
        static void repaint(final RecyclerView list, final int cardColor) {
            if (list == null) {
                return;
            }
            for (int i = 0; i < list.getItemDecorationCount(); i++) {
                final RecyclerView.ItemDecoration decoration = list.getItemDecorationAt(i);
                if (decoration instanceof GroupCards) {
                    ((GroupCards) decoration).card.setColor(cardColor);
                }
            }
            list.invalidate();
        }

        @Override
        public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                                   @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
            // Centred once the list is wider than the column is allowed to be. The cards are drawn
            // from the rows' own bounds, so they follow this without knowing about it.
            final int side = contentSideInset(parent.getContext(), parent.getWidth());
            outRect.left = side;
            outRect.right = side;
            // The header sits above its card, not against the one before it.
            if (isCategory(parent, parent.getChildAdapterPosition(view))) {
                outRect.top = headerGap;
            }
        }

        @Override
        public void onDraw(@NonNull Canvas canvas, @NonNull RecyclerView parent,
                           @NonNull RecyclerView.State state) {
            final int count = parent.getChildCount();
            int i = 0;
            while (i < count) {
                final View first = parent.getChildAt(i);
                final int firstPosition = parent.getChildAdapterPosition(first);
                if (firstPosition == RecyclerView.NO_POSITION || isCategory(parent, firstPosition)) {
                    i++;
                    continue;
                }
                // Collect the rest of this card: everything up to the next header or the last row on screen.
                int last = i;
                while (last + 1 < count) {
                    final int next = parent.getChildAdapterPosition(parent.getChildAt(last + 1));
                    if (next == RecyclerView.NO_POSITION || isCategory(parent, next)) {
                        break;
                    }
                    last++;
                }
                final View lastView = parent.getChildAt(last);
                float top = first.getTop();
                float bottom = lastView.getBottom();
                // A card scrolled off either edge keeps its corners out of sight, so it does not read
                // as a card that ends where the viewport does.
                if (!isCardTop(parent, firstPosition)) {
                    top -= RADIUS * 2f;
                }
                if (!isCardBottom(parent, parent.getChildAdapterPosition(lastView))) {
                    bottom += RADIUS * 2f;
                }
                bounds.set(first.getLeft(), top, first.getRight(), bottom);
                canvas.drawRoundRect(bounds, RADIUS, RADIUS, card);
                i = last + 1;
            }
        }

        /**
         * The hairlines go on top of the rows, not under them: a pressed or focused row paints a state
         * layer over its whole height, and a divider drawn beneath it would vanish for as long as the
         * touch lasts.
         */
        @Override
        public void onDrawOver(@NonNull Canvas canvas, @NonNull RecyclerView parent,
                               @NonNull RecyclerView.State state) {
            for (int i = 0; i < parent.getChildCount(); i++) {
                final View row = parent.getChildAt(i);
                final int position = parent.getChildAdapterPosition(row);
                if (position == RecyclerView.NO_POSITION || isCategory(parent, position)
                        || isCardBottom(parent, position)) {
                    continue;
                }
                final float y = row.getBottom();
                canvas.drawLine(row.getLeft() + hairlineInset, y,
                        row.getRight() - hairlineInset, y, hairline);
            }
        }

        /** Corner-clips one row to its card: rounded at the card's ends, square inside it. */
        static void clip(final PreferenceGroupAdapter adapter, final View row, final int position) {
            if (adapter.getItem(position) instanceof PreferenceCategory) {
                row.setClipToOutline(false);
                row.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
                return;
            }
            final boolean top = position == 0
                    || adapter.getItem(position - 1) instanceof PreferenceCategory;
            final boolean bottom = position == adapter.getItemCount() - 1
                    || adapter.getItem(position + 1) instanceof PreferenceCategory;
            row.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(final View view, final Outline outline) {
                    // An outline carries one radius for all four corners, so the end that has to stay
                    // square is pushed a radius beyond the row rather than rounded.
                    outline.setRoundRect(0, top ? 0 : -RADIUS, view.getWidth(),
                            view.getHeight() + (bottom ? 0 : RADIUS), RADIUS);
                }
            });
            row.setClipToOutline(true);
        }

        private static boolean isCardTop(final RecyclerView parent, final int position) {
            return position == 0 || isCategory(parent, position - 1);
        }

        private static boolean isCardBottom(final RecyclerView parent, final int position) {
            final RecyclerView.Adapter<?> adapter = parent.getAdapter();
            return adapter == null || position == adapter.getItemCount() - 1
                    || isCategory(parent, position + 1);
        }

        private static boolean isCategory(final RecyclerView parent, final int position) {
            final RecyclerView.Adapter<?> adapter = parent.getAdapter();
            if (position == RecyclerView.NO_POSITION || !(adapter instanceof PreferenceGroupAdapter)) {
                return false;
            }
            return ((PreferenceGroupAdapter) adapter).getItem(position) instanceof PreferenceCategory;
        }
    }
}
