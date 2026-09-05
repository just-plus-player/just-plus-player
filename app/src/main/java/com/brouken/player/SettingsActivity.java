package com.brouken.player;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ImageSpan;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.StaticLayout;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.app.Activity;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.core.content.ContextCompat;
import androidx.core.view.OneShotPreDrawListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaLibraryInfo;
import androidx.media3.decoder.ffmpeg.FfmpegLibrary;
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
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

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
            // The width the list will get, not the root's: the window insets are applied to this
            // root as padding, and the list sits inside them. Measuring the outer width put the
            // title off the column by half the inset wherever a system bar takes a side.
            final int usable = r - l - v.getPaddingLeft() - v.getPaddingRight();
            final int extra = Math.max(0,
                    contentSideInset(this, usable) - bar.getContentInsetStart());
            bar.setPadding(extra, bar.getPaddingTop(), extra, bar.getPaddingBottom());
        });

        if (Build.VERSION.SDK_INT >= 29) {
            LinearLayout layout = findViewById(R.id.settings_layout);
            layout.setOnApplyWindowInsetsListener((view, windowInsets) -> {
                view.setPadding(windowInsets.getSystemWindowInsetLeft(),
                        windowInsets.getSystemWindowInsetTop(),
                        windowInsets.getSystemWindowInsetRight(),
                        0);
                windowInsets.consumeSystemWindowInsets();
                return windowInsets;
            });
        }
    }

    /**
     * Says something back, inside the content column.
     *
     * A Toast lands at the bottom of the window, which on a television is the overscan strip this
     * screen spends 48dp staying out of — so the one row whose only feedback is a message ("Reset
     * learned audio workarounds") looked inert and invited a second press. A Snackbar over the list
     * is inset to the same column as the cards and clears the strip.
     */
    static void say(final Activity activity, final int textRes) {
        final View host = activity.findViewById(R.id.settings);
        if (host == null) {
            Toast.makeText(activity, textRes, Toast.LENGTH_SHORT).show();
            return;
        }
        final Snackbar bar = Snackbar.make(host, textRes, Snackbar.LENGTH_SHORT);
        final int side = contentSideInset(activity, host.getWidth());
        final View view = bar.getView();
        final ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
        lp.leftMargin += side;
        lp.rightMargin += side;
        if (Utils.isTvBox(activity)) {
            lp.bottomMargin += Utils.dpToPx(27);
        }
        view.setLayoutParams(lp);
        bar.show();
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
     * Past twice that the column is allowed to grow to 960dp, because a 720dp strip in the middle of
     * a 1932dp television — what a set rendering its interface at 4K gives you — leaves 63% of the
     * panel empty and reads as a mistake rather than a margin. 960dp is the ceiling: wider rows put
     * the switch too far from its label again, and filling more than that properly needs a second
     * pane, which is a different screen, not a wider inset.
     *
     * On a TV the floor is the overscan margin the TV guidance asks for (48dp horizontally, 5% of
     * 960dp) rather than the phone's 16dp: a set narrow enough to miss the cap — 1280x720 at 320 dpi
     * is exactly 640dp across — would otherwise put rows in the strip a TV may not show.
     */
    static int contentSideInset(final Context context, final int availableWidth) {
        final int base = Utils.isTvBox(context) ? Utils.dpToPx(48) : Utils.dpToPx(16);
        final int column = Math.max(Utils.dpToPx(720),
                Math.min(Utils.dpToPx(960), availableWidth / 2));
        return Math.max(base, (availableWidth - column) / 2);
    }

    /**
     * What the list keeps clear at its foot: the system bars, and on a television the overscan strip a
     * set may not show. The sides already keep 48dp of it ({@link #contentSideInset}); the foot kept
     * none, so the last row came to rest on the very edge of the screen - which is why a badge at the
     * end of About read as cut off there, and sat under the navigation bar on a phone.
     *
     * <p>Held as padding with {@code clipToPadding} false, so it is scrollable room rather than a gap:
     * the list still fills the screen, and its end can now come up past the strip.
     */
    static int listBottomInset(final Context context) {
        return Utils.isTvBox(context) ? Utils.dpToPx(48) : 0;
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
        if (getSupportFragmentManager().popBackStackImmediate()) {
            return true;
        }
        // Never the framework's Up: it synthesises a launch of the manifest parent — PlayerActivity with
        // no data — and a player asked to start with nothing to play opens the empty state over the session
        // that was running. This screen is always entered from somewhere, so leaving it is a finish: the
        // caller comes back exactly as it was, and gets its result.
        finish();
        return true;
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

        /** Every icon on the screen, recoloured for the surface it is drawn on. */
        private static void tintIcons(final PreferenceGroup group, final int color) {
            for (int i = 0; i < group.getPreferenceCount(); i++) {
                final Preference preference = group.getPreference(i);
                final Drawable icon = preference.getIcon();
                if (icon != null) {
                    // Mutated, or the tint reaches every row sharing the drawable's constant state.
                    final Drawable own = icon.mutate();
                    own.setTint(color);
                    preference.setIcon(own);
                }
                if (preference instanceof PreferenceGroup) {
                    tintIcons((PreferenceGroup) preference, color);
                }
            }
        }

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

            // The hub's glyphs are the player's own, drawn white for the chrome over video, and white
            // on a settings card is invisible in the light appearance. They take the surface accent
            // instead - the Two Grounds Rule, learned three times already in the panels.
            tintIcons(getPreferenceScreen(),
                    MaterialColors.getColor(requireContext(), R.attr.colorPrimary, Color.WHITE));

            final Preference dangerousWarning = findPreference("dangerousWarning");
            if (dangerousWarning != null && Utils.isTvBox(requireContext())) {
                // A remote cannot land on an unselectable row, so it stepped over the one piece of
                // safety text in front of the two settings that can stop video from playing. It does
                // nothing when pressed, which is the correct amount for a warning.
                dangerousWarning.setSelectable(true);
            }

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
                    say(requireActivity(), R.string.pref_reset_audio_workarounds_done);
                    return true;
                });
            }

            PreferenceCategory privacyCategory = findPreference("privacyCategory");
            if (privacyCategory != null && !BuildConfig.ENABLE_CRASH_REPORTING) {
                privacyCategory.setVisible(false);
            }

            final Preference stand = findPreference("aboutStand");
            if (stand != null && Utils.isTvBox(requireContext())) {
                // The badge is the last thing on the screen and does nothing, so a remote would stop at
                // the row above it and never scroll far enough to show it. Selectable on a television
                // for the same reason the decoder warning is: a D-pad only reaches what it can land on.
                stand.setSelectable(true);
            }

            Preference checkUpdate = findPreference("checkUpdateNow");
            if (checkUpdate != null) {
                checkUpdate.setOnPreferenceClickListener(preference -> {
                    checkForUpdate();
                    return true;
                });
            }

            Preference source = findPreference("aboutSource");
            if (source != null) {
                source.setOnPreferenceClickListener(preference -> {
                    openSource();
                    return true;
                });
            }

            // Only the group: About itself stands in every build, because what it says first -
            // what this is and which version is running - is true with or without an updater.
            PreferenceCategory updateCategory = findPreference("updateCategory");
            if (updateCategory != null && !BuildConfig.ENABLE_UPDATE) {
                updateCategory.setVisible(false);
            }
        }

        /**
         * The About block: the version, the lines under it, and the press that copies them. Views
         * inside a row's own layout rather than a title and a summary, so they are filled in where
         * every other custom layout in this screen is - see {@link #bindThemeMode}.
         */
        private void bindAbout(final PreferenceViewHolder holder, final Preference preference) {
            if (preference == null) {
                return;
            }
            if ("aboutHeader".equals(preference.getKey())) {
                final View version = holder.findViewById(R.id.about_version);
                if (version instanceof TextView) {
                    ((TextView) version).setText("v" + BuildConfig.VERSION_NAME);
                }
                final View device = holder.findViewById(R.id.about_device);
                if (device instanceof TextView) {
                    ((TextView) device).setText(facts());
                }
                final View info = holder.findViewById(R.id.about_info);
                final View hint = holder.findViewById(R.id.about_copy_hint);
                // Nothing on a television reads a clipboard, and nothing there can tap either: the
                // report is read off the screen, so the press and the mark that promises it both go.
                final boolean canCopy = !Utils.isTvBox(requireContext());
                if (hint != null) {
                    hint.setVisibility(canCopy ? View.VISIBLE : View.GONE);
                }
                if (info != null) {
                    // The listener first: setOnClickListener makes a view clickable even when what it
                    // is handed is null, so clearing the flag before it does nothing.
                    info.setOnClickListener(canCopy ? v -> copy(report()) : null);
                    info.setClickable(canCopy);
                    info.setFocusable(false);
                    info.setContentDescription(canCopy ? getString(R.string.error_copy) : null);
                }
                return;
            }
        }

        /**
         * What a bug report needs, in the shape {@code ErrorActivity} already pastes: the build, then
         * the device, then the hardware. Three lines rather than that screen's full dump, because this
         * one is read on screen as well as pasted.
         */
        private static String facts() {
            final StringBuilder sb = new StringBuilder();
            sb.append("Android: ").append(Build.VERSION.RELEASE)
                    .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
            sb.append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                    .append(" (").append(Build.DEVICE).append(")\n");
            sb.append("ABI: ").append(primaryAbi()).append('\n');
            sb.append("Media3: ").append(MediaLibraryInfo.VERSION);
            // The extension decoder is what plays AC3/EAC3/DTS here, and whether it loaded at all is
            // the first question a "no sound" report raises.
            if (FfmpegLibrary.isAvailable()) {
                sb.append("\nFFmpeg: ").append(FfmpegLibrary.getVersion());
            }
            return sb.toString();
        }

        /**
         * What a bug report needs, in the shape {@code ErrorActivity} already pastes: the build, then
         * everything the block shows, then the firmware the lines above cannot pin.
         */
        private static String report() {
            return BuildConfig.APPLICATION_ID + "@" + BuildConfig.VERSION_NAME
                    + " (build " + BuildConfig.VERSION_CODE + ", " + BuildConfig.FLAVOR
                    + (BuildConfig.DEBUG ? " debug)" : " release)") + "\n"
                    + facts() + "\n"
                    + "Build: " + Build.FINGERPRINT;
        }

        private static String primaryAbi() {
            return Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "?";
        }

        /**
         * The project's address, handed over by the first means this device actually has: share it,
         * failing that open it, failing that keep it, and on a television the code, always. No menu -
         * the viewer pressed a row that names one thing, and being asked how to do it is a question
         * they did not ask.
         *
         * <p>The two intents are resolved the way the room invite already resolves its own; copying is
         * judged rather than resolved, because no API says whether there is anywhere to paste.
         */
        private void openSource() {
            final Activity activity = getActivity();
            if (activity == null) {
                return;
            }
            final String url = getString(R.string.about_github_url);
            // A television first, whatever else resolves there: reading a page with a remote is not
            // why anyone presses this row, and a phone camera is the way off that screen.
            if (Utils.isTvBox(activity)) {
                if (!showQr(activity, url)) {
                    copy(url);
                }
                return;
            }
            final Intent share = new Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, url);
            if (canHandle(activity, share)) {
                startActivity(Intent.createChooser(share, getString(R.string.error_share)));
                return;
            }
            final Intent open = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            if (canHandle(activity, open)) {
                startActivity(open);
                return;
            }
            copy(url);
        }

        private static boolean canHandle(final Activity activity, final Intent intent) {
            return !activity.getPackageManager().queryIntentActivities(intent, 0).isEmpty();
        }

        /** The link as a code a phone can read, or false where one could not be drawn. */
        private boolean showQr(final Activity activity, final String url) {
            final Context dialogContext = Utils.dialogContext(activity);
            final DisplayMetrics metrics = getResources().getDisplayMetrics();
            // Half the shorter side, and pinned to that: left to fill the dialog's width, a code on a
            // television grew until the OK button underneath it was off the bottom of the screen.
            final int side = (int) (Math.min(metrics.widthPixels, metrics.heightPixels) * 0.5f);
            final Bitmap qr = Utils.qrBitmap(url, side);
            if (qr == null) {
                return false;
            }
            final ImageView image = new ImageView(dialogContext);
            image.setImageBitmap(qr);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            final FrameLayout frame = new FrameLayout(dialogContext);
            final int padding = Math.round(16 * metrics.density);
            frame.setPadding(padding, padding, padding, padding);
            frame.addView(image, new FrameLayout.LayoutParams(side, side, Gravity.CENTER));
            new MaterialAlertDialogBuilder(dialogContext)
                    .setTitle(R.string.about_github)
                    .setMessage(R.string.about_github_qr)
                    .setView(frame)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return true;
        }

        private void copyReport() {
            copy(report());
        }

        private void copy(final String text) {
            final Activity activity = getActivity();
            if (activity == null) {
                return;
            }
            final ClipboardManager clipboard =
                    (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null) {
                return;
            }
            clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.pref_about_header), text));
            // From 33 the system shows its own copy confirmation, and two of them is one too many.
            if (Build.VERSION.SDK_INT < 33) {
                say(activity, R.string.error_copied);
            }
        }

        /** Asks GitHub, and says so either way: silence reads as a row that did nothing. */
        private void checkForUpdate() {
            final Activity activity = getActivity();
            if (activity == null) {
                return;
            }
            say(activity, R.string.update_checking);
            Updater.find(info -> activity.runOnUiThread(() -> {
                if (activity.isFinishing()) {
                    return;
                }
                if (info != null) {
                    UpdateUi.showAvailableDialog(activity, activity, info, null, false);
                } else {
                    say(activity, R.string.update_none);
                }
            }));
        }

        /**
         * Rows are corner-clipped to the card they sit in, so a ripple stops at the rounded corner
         * instead of squaring it off, and each wears the D-pad focus ring on that same outline.
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
                        restoreReadoutInk(title, getItem(position));
                    }
                    reserveTallerSummary(holder, getItem(position));
                    bindThemeMode(holder, getItem(position));
                    bindAbout(holder, getItem(position));
                }
            };
        }

        /**
         * A row that cannot be selected is lettered like a row that is switched off - the library
         * dims both by the same rule. That is right for something unavailable and wrong for something
         * simply not pressable: measured on the About screen, the developer's name came out 8.13:1
         * against its card where the source line beside it reads 14.85:1, and the pair looked like one
         * row was greyed out. Enabled and unselectable takes the full ink back.
         */
        private static void restoreReadoutInk(final TextView title, final Preference preference) {
            if (preference == null || preference.isSelectable() || !preference.isEnabled()) {
                return;
            }
            title.setTextColor(MaterialColors.getColor(title, R.attr.colorOnSurface));
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
            // The same ring the panels' segmented control wears: a border that only changes colour is
            // a focus event you have to be looking at already.
            for (int i = 0; i < group.getChildCount(); i++) {
                Utils.focusRing((MaterialButton) group.getChildAt(i));
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
         * Pure black is something only a dark theme can be, so while the screen is not dark the row is
         * greyed rather than hidden: it says the option exists and what it waits for. Read from the
         * configuration in force, not from the stored choice — under System with a light system the
         * option is as inert as it is under Light, and offering it there is offering nothing.
         */
        private void syncAmoledEnabled() {
            final Preference amoled = findPreference("amoledBlack");
            if (amoled != null) {
                amoled.setEnabled(((SettingsActivity) requireActivity()).isNight());
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
                bindCategoryJump(cardList);
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
            final int overscan = listBottomInset(requireContext());
            recyclerView.setClipToPadding(false);
            ViewCompat.setOnApplyWindowInsetsListener(recyclerView, (list, insets) -> {
                list.setPadding(0, 0, 0, overscan
                        + insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom);
                return insets;
            });
            ViewCompat.requestApplyInsets(recyclerView);
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
                // a quick-settings tile opens its own page. When the key names a section rather than a
                // row, the section is opened: the subtitle button used to name a preference that has
                // since moved inside one, and naming a row that is no longer on this list left the
                // screen at the top with nothing said.
                final String key = activity.getIntent().getStringExtra(EXTRA_SCROLL_TO);
                final Preference target = key == null ? null : findPreference(key);
                if (target instanceof PreferenceScreen) {
                    // After this pass, not during it: replacing the fragment while it is still being
                    // created leaves the screen blank.
                    view.post(() -> activity.onPreferenceStartScreen(this, (PreferenceScreen) target));
                } else if (key != null) {
                    openAtPreference(key, 3);
                } else if (Utils.isTvBox(activity)) {
                    // Opened from the player menu with no section to land on. A remote needs
                    // something focused or the first press goes wherever the view root guesses;
                    // the sub-screen path above has said so for a while, and the root path never
                    // did the same.
                    openAtPosition(0, 3);
                }
            }
        }

        /**
         * Left and Right do nothing in a one-column list, and this one is 26 stops deep on a
         * television with no fast scroll and no search. Give them the jump a category rail would
         * have given them: to the first row of the previous or next section, nine stops instead of
         * twenty-six, without a second pane and without undoing the rule that keeps short groups
         * visible.
         *
         * Only on a remote, and never on the theme row, whose segmented control needs Left and
         * Right for itself.
         */
        private void bindCategoryJump(final RecyclerView list) {
            if (!Utils.isTvBox(list.getContext())) {
                return;
            }
            list.setOnKeyListener((view, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN
                        || (keyCode != KeyEvent.KEYCODE_DPAD_LEFT
                        && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT)) {
                    return false;
                }
                final View focused = list.findFocus();
                // A control that has horizontal neighbours of its own keeps the axis: the theme
                // control moves between its three segments, and the About block between Copy and
                // GitHub. Jumping a section from there would swallow the move a remote means.
                if (focused == null || focused instanceof MaterialButtonToggleGroup
                        || focused instanceof MaterialButton) {
                    return false;
                }
                final int from = list.getChildAdapterPosition(list.findContainingItemView(focused));
                if (from == RecyclerView.NO_POSITION) {
                    return false;
                }
                final int target = categoryNeighbour(list, from,
                        keyCode == KeyEvent.KEYCODE_DPAD_RIGHT);
                if (target == RecyclerView.NO_POSITION) {
                    return false;
                }
                openAtPosition(target, 3);
                return true;
            });
        }

        /** First row under the section before or after the one holding {@code from}. */
        private static int categoryNeighbour(final RecyclerView list, final int from,
                                             final boolean forward) {
            final RecyclerView.Adapter<?> adapter = list.getAdapter();
            if (!(adapter instanceof PreferenceGroupAdapter)) {
                return RecyclerView.NO_POSITION;
            }
            final PreferenceGroupAdapter rows = (PreferenceGroupAdapter) adapter;
            final int step = forward ? 1 : -1;
            // Walk off the current section first, or Left from its first row would find its own
            // header and go nowhere.
            int i = from;
            if (!forward) {
                while (i > 0 && !(rows.getItem(i - 1) instanceof PreferenceCategory)) {
                    i--;
                }
                i--;
            }
            for (i += step; i >= 0 && i < rows.getItemCount(); i += step) {
                if (rows.getItem(i) instanceof PreferenceCategory && i + 1 < rows.getItemCount()) {
                    return i + 1;
                }
            }
            return RecyclerView.NO_POSITION;
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
            say(requireActivity(), R.string.pref_subtitle_color_clash);
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
     * Drawn behind the rows instead of being set as their background: a card behind a row is one
     * drawable, a card cut into row backgrounds is one per row that has to know where in the card it
     * sits. The row's own background is the press ripple; the D-pad focus is a ring on the row's outline,
     * as it is on every other control in the app, in place of the wash androidx.preference gives it.
     */
    private static final class GroupCards extends RecyclerView.ItemDecoration {

        private static final int RADIUS = Utils.dpToPx(20);

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
         * The hairlines go on top of the rows rather than under them, so that a row's own state layer
         * cannot tint them — but a lit row takes the two lines that touch it with it. That is what the
         * system settings do: while a row is pressed the dividers at its edges are gone and the highlight
         * is the only edge, instead of a line running through it. A line belongs to the row above it, so
         * both the row it is drawn for and the row below have to be quiet for it to appear.
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
                if (isLit(row) || isLit(i + 1 < parent.getChildCount() ? parent.getChildAt(i + 1) : null)) {
                    continue;
                }
                final float y = row.getBottom();
                canvas.drawLine(row.getLeft() + hairlineInset, y,
                        row.getRight() - hairlineInset, y, hairline);
            }
        }

        /** Whether a row is currently wearing a mark of its own: the press state layer, or the focus contour. */
        private static boolean isLit(final View row) {
            return row != null && (row.isPressed() || row.isFocused());
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
            // The slice of the card this row is: rounded where the card is, square where the next row
            // continues it. The focus ring traces exactly that, so it is never cut by the clip above.
            final float t = top ? RADIUS : 0;
            final float b = bottom ? RADIUS : 0;
            row.setForeground(Utils.focusOutline(row.getContext(), new float[]{t, t, t, t, b, b, b, b}));
            row.setBackground(new RippleDrawable(Utils.pressOnly(MaterialColors.getColor(row,
                    R.attr.colorControlHighlight)), null, new ColorDrawable(Color.WHITE)));
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
