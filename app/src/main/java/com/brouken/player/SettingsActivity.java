package com.brouken.player;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.Layout;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.provider.Settings;
import android.text.StaticLayout;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewParent;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.app.Activity;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;
import androidx.core.content.ContextCompat;
import androidx.core.view.OneShotPreDrawListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaLibraryInfo;
import androidx.media3.common.text.Cue;
import androidx.media3.decoder.ffmpeg.FfmpegLibrary;
import androidx.media3.ui.SubtitleView;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.preference.EditTextPreference;
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
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

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
    // Set on the activity's own intent across a rebuild, which is the only thing that survives one:
    // a rebuilt screen comes back with nothing focused, and a remote then has nowhere to start.
    private static final String EXTRA_FOCUS_FIRST = "focusFirst";
    /** A line this screen owes the viewer once it has been rebuilt — see {@link #sayWhatWasOwed}. */
    private static final String EXTRA_SAY = "say";

    static RecyclerView recyclerView;

    /** Key of the sub-screen row last opened, so Back can put the list back on it. */
    private String openedScreenKey;

    /**
     * The fragment on screen, while it wants the remote's arrows. Set by the fragment itself, because
     * a key reaches the focused view and never the list around it.
     */
    private SettingsFragment remote;

    @Override
    public boolean dispatchKeyEvent(final KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && remote != null && remote.isResumed()
                && remote.onRemoteKey(event.getKeyCode())) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    /** The surface tones without the AMOLED overlay, resolved in onCreate before it is applied. */
    private int plainSurface;
    private int plainCard;

    @Override
    protected void onApplyThemeResource(final Resources.Theme theme, final int resid, final boolean first) {
        super.onApplyThemeResource(theme, resid, first);
        // Here and not in onCreate: when the appearance differs from the system's, AppCompat sets the
        // window theme again on the way to the recreated activity, and an overlay put on in onCreate
        // was gone by the time the list was built - the light appearance came up with coral glyphs.
        theme.applyStyle(Prefs.accentOverlay(this, Prefs.isLight(this)), true);
    }

    /**
     * The overlay again, because the night mode can change without the activity being rebuilt and that
     * path takes it off. AppCompat's delegate applies the mode from its own onCreate, where it is not
     * allowed to recreate, so it updates the resources instead and force-applies the window theme after
     * them - and {@code setTheme} with the id it already has is a no-op from API 23, so the hook above
     * does not run. What is left is the light coral fallback BaseTheme.Settings carries for the case
     * where no overlay was applied, under whichever appearance is now in force: reported as a light
     * screen lettered in white, with the wrong accent, every so often on a switch of the theme. This is
     * what AppCompat calls straight afterwards, and it is still before anything is inflated.
     */
    @Override
    public void onNightModeChanged(final int mode) {
        getTheme().applyStyle(Prefs.accentOverlay(this, Prefs.isLight(this)), true);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        // Before super.onCreate, or AppCompat applies the old mode first and then recreates.
        getDelegate().setLocalNightMode(Prefs.getNightMode(this));

        super.onCreate(savedInstanceState);

        // After super, so the configuration already carries the night mode set above, and before
        // anything asks the window for its decor view: reading getDecorView() builds it, and the decor
        // takes its background out of the theme exactly as it stands at that moment. Pure black is a
        // dark-theme idea only.
        // Prefs.isLight and not getConfiguration().uiMode. The comment below is right about what this
        // has to follow - the appearance in force, not the system's - and the configuration is not it:
        // setLocalNightMode above schedules the change, and on this pass getResources() can still be
        // carrying the system's mode while the DayNight theme has already resolved the app's. Measured
        // with the appearance set to light on a dark phone: the ground came up #FDF6E3 and the bar's
        // icons stayed white on it, 1.08:1. Prefs.isLight is the one answer the accent overlay, the
        // dialogs and the panels all already use.
        final boolean night = !Prefs.isLight(this);
        readPlainTones();
        if (night && Prefs.isAmoledBlack(this)) {
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
                .setAppearanceLightStatusBars(!night);

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
     * Says something back, at the top of the window.
     *
     * A Toast lands at the bottom, which on a television is the overscan strip this screen spends 48dp
     * staying out of — so the one row whose only feedback is a message ("Reset learned audio
     * workarounds") looked inert and invited a second press. {@link Notice} clears that strip at the
     * other end, and says it the way every other screen in the app now says it.
     */
    static void say(final Activity activity, final int textRes, final int iconRes) {
        Notice.show(activity, textRes, false, iconRes);
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
    /**
     * An accent pick, without rebuilding the activity. {@code recreate()} was what a pick used to do,
     * and it read as a jerk: the window torn down and drawn again, the list rebuilt, the tile row
     * starting at nought and jumping to its place, and on a television the focus lost with it. The
     * overlay goes on the live theme instead — the trick the AMOLED toggle settled on for the same
     * reason — and only what the theme actually paints is redone.
     *
     * <p>The rows have to be inflated again rather than merely re-bound: a switch track, a checked
     * segment and a section header each resolve their colour when they are inflated and no later.
     * Handing the list its own adapter back is what does it — every holder is built afresh, and the
     * pool is emptied first or a recycled row comes back in the colours it was inflated in. The
     * layout state goes there and back so the list does not jump to the top.
     *
     * <p>Not a fragment transaction: detach and attach in one of those cancel out, because the state
     * manager works out where a fragment should end up and moves it there once. Two transactions
     * would have worked and would have torn down more than the colours needed.
     */
    /**
     * The theme's own ground and card, read from the accent overlay rather than from the live theme.
     *
     * <p>Reading them from the theme was where the AMOLED bug lived: its overlay replaces colorSurface
     * with a literal black, applyStyle cannot be undone, and so a theme the option had ever been on
     * answered black — which then became the tone the option was supposed to return to.
     */
    private void readPlainTones() {
        final TypedArray a = obtainStyledAttributes(
                Prefs.accentOverlay(this, Prefs.isLight(this)), R.styleable.Accent);
        plainSurface = a.getColor(R.styleable.Accent_accentGround, Color.BLACK);
        plainCard = a.getColor(R.styleable.Accent_accentCard, Color.DKGRAY);
        a.recycle();
    }

    void repaintAccent(final RecyclerView list) {
        getTheme().applyStyle(Prefs.accentOverlay(this, Prefs.isLight(this)), true);
        readPlainTones();
        // Which of the two ground overlays belongs on top is repaintAmoledSurfaces' business, and it
        // is called for exactly that reason — the accent has just put its own grounds back.
        repaintAmoledSurfaces();
        if (list != null && list.getAdapter() != null && list.getLayoutManager() != null) {
            final Parcelable at = list.getLayoutManager().onSaveInstanceState();
            list.getRecycledViewPool().clear();
            list.setAdapter(list.getAdapter());
            list.getLayoutManager().onRestoreInstanceState(at);
        }
    }

    void repaintAmoledSurfaces() {
        final boolean amoled = isNight() && Prefs.isAmoledBlack(this);
        // The theme as well as the pixels, and the option's own inverse when it is off: applyStyle
        // cannot be undone, so a theme that has ever been black answers black for everything read
        // back from it — which is how switching a theme while it was on left the black behind.
        getTheme().applyStyle(amoled ? R.style.ThemeOverlay_JustPlus_Amoled
                : R.style.ThemeOverlay_JustPlus_Ground, true);
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

    /** The appearance in force, from the same place every other surface asks. Never the configuration:
     *  see the note in onCreate. */
    private boolean isNight() {
        return !Prefs.isLight(this);
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

            bindBrowser();

            final Preference language = findPreference("appLanguage");
            if (language != null) {
                language.setSummary(AppLanguage.summary(requireContext()));
                language.setOnPreferenceClickListener(preference -> {
                    AppLanguage.showPicker(requireContext());
                    return true;
                });
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
                // A blank name is not a name: Prefs would quietly generate another one on its next
                // read, so the field the viewer had just emptied would fill itself with a stranger.
                // Spaces alone count as blank, and the ones around a name are dropped rather than
                // saved — a room shows this to other people, and a trailing space is invisible there.
                // Blank after a reset, or before the player has ever been run: the field the room shows
                // other people is never empty, so it is filled here rather than waiting for the next
                // time Prefs is read.
                final String nick = preferenceNick.getText();
                if (nick == null || nick.trim().isEmpty()) {
                    preferenceNick.setText(AliasGenerator.random());
                }
                preferenceNick.setOnPreferenceChangeListener((preference, value) -> {
                    final String trimmed = value == null ? "" : value.toString().trim();
                    if (trimmed.isEmpty()) {
                        say(requireActivity(), R.string.pref_together_nick_blank, R.drawable.ic_person_24dp);
                        return false;
                    }
                    if (!trimmed.equals(value.toString())) {
                        // setText persists and redraws without coming back through this listener, so
                        // the trimmed name is what is stored and the untrimmed one never is. Refusing
                        // the change afterwards is what stops the dialog writing it back over.
                        preferenceNick.setText(trimmed);
                        return false;
                    }
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
                    enableTranslateBackends((Boolean) value);
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
                    LanguagePriorityDialog.show(requireActivity(),
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
            // so it gets the clash check for nothing.
            bindColorPair("subtitleTextColor", "subtitleBackground");
            bindColorPair("subtitleSecondaryTextColor", "subtitleSecondaryBackground");

            // A refusal learnt under forced pass-through is a property of that experiment, not of the
            // device, and the denylist it lands in is consulted whether forcing is on or off. So either
            // turn of the switch forgets it: otherwise turning it on a second time would be a no-op for
            // every format the first attempt had already failed on.
            Preference forcePassthrough = findPreference("audioPassthroughForce");
            if (forcePassthrough != null) {
                forcePassthrough.setOnPreferenceChangeListener((preference, value) -> {
                    Prefs.resetRevokedAudioMimes(requireContext());
                    return true;
                });
            }

            Preference resetAudioWorkarounds = findPreference("resetRevokedAudioMimes");
            if (resetAudioWorkarounds != null) {
                resetAudioWorkarounds.setOnPreferenceClickListener(preference -> {
                    Prefs.resetRevokedAudioMimes(requireContext());
                    say(requireActivity(), R.string.pref_reset_audio_workarounds_done,
                                R.drawable.ic_audiotrack_24dp);
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

            // Last, so that what it counts is the screen as it will be read.
            addResetRows();
        }

        /**
         * A reset at the foot of every screen that holds settings, and one for the lot at the foot of
         * the root. Built here rather than written into the XML: what a screen holds is the screen's
         * own business, and a row per screen written by hand is a row that goes missing the next time
         * a screen is added. A screen that holds nothing to reset — About, whose rows are readings and
         * actions — gets none.
         */
        private void addResetRows() {
            final PreferenceScreen screen = getPreferenceScreen();
            if (screen == null) {
                return;
            }
            final Context context = screen.getContext();
            final boolean root = getArguments() == null
                    || getArguments().getString(ARG_PREFERENCE_ROOT) == null;
            if (root) {
                final Preference all = new Preference(context);
                all.setTitle(R.string.pref_reset_all);
                all.setOnPreferenceClickListener(preference -> {
                    askReset(R.string.pref_reset_ask_all, keysUnder(screen), holdsLanguage(screen));
                    return true;
                });
                apart(screen, all);
                return;
            }
            // About is a reading, not a set of settings: the one switch on it belongs to the updater,
            // and a reset row there would stand under the badge that is meant to be the last thing on
            // the screen. What it holds is reset with everything else, from the root.
            if (!holdsSettings(screen) || "aboutScreen".equals(screen.getKey())) {
                return;
            }
            final Preference reset = new Preference(context);
            reset.setTitle(R.string.pref_reset_section);
            reset.setOnPreferenceClickListener(preference -> {
                askReset(R.string.pref_reset_ask, keysUnder(screen), holdsLanguage(screen));
                return true;
            });
            apart(screen, reset);
        }

        /**
         * Puts a row in a group of its own at the foot of a screen — a card with nothing above it and
         * no heading, so a press that undoes a screenful of choices cannot be taken for one more of
         * them. The group carries the separation; there is nothing to write over it that the row does
         * not already say.
         */
        private static void apart(final PreferenceScreen screen, final Preference row) {
            final PreferenceCategory group = new PreferenceCategory(screen.getContext());
            // Added before its child: a category outside a screen has nowhere to put one.
            screen.addPreference(group);
            row.setIconSpaceReserved(false);
            group.setIconSpaceReserved(false);
            group.addPreference(row);
        }

        /** Whether a screen offers anything a reset could put back — a value, not a reading. */
        private boolean holdsSettings(final PreferenceGroup group) {
            for (int i = 0; i < group.getPreferenceCount(); i++) {
                final Preference child = group.getPreference(i);
                if (child instanceof PreferenceGroup) {
                    if (holdsSettings((PreferenceGroup) child)) {
                        return true;
                    }
                    continue;
                }
                if (child.getKey() == null) {
                    continue;
                }
                // Either it is one of the kinds that carries a value, or it has already written one:
                // the theme and the accent are plain rows with a layout of their own, and what they
                // hold is a setting all the same.
                if (child instanceof TwoStatePreference || child instanceof ListPreference
                        || child instanceof EditTextPreference
                        || prefs().contains(child.getKey())) {
                    return true;
                }
            }
            return false;
        }

        /** Every key under a group that actually holds a value right now. */
        private List<String> keysUnder(final PreferenceGroup group) {
            final List<String> keys = new ArrayList<>();
            collectKeys(group, keys);
            return keys;
        }

        private void collectKeys(final PreferenceGroup group, final List<String> into) {
            for (int i = 0; i < group.getPreferenceCount(); i++) {
                final Preference child = group.getPreference(i);
                if (child instanceof PreferenceGroup) {
                    // A nested screen belongs to the screen that holds it, so the root reaches all of
                    // them and a section reaches its own sub-screens and no one else's.
                    collectKeys((PreferenceGroup) child, into);
                    continue;
                }
                final String key = child.getKey();
                if (key != null && prefs().contains(key)) {
                    into.add(key);
                }
            }
        }

        private SharedPreferences prefs() {
            return androidx.preference.PreferenceManager
                    .getDefaultSharedPreferences(requireContext());
        }

        /**
         * Whether the language row is among what is being reset. It is the one setting on this screen
         * that does not live in the preferences at all — the platform holds it, per app — so clearing
         * keys would leave the app in a language the viewer had chosen and the row claiming otherwise.
         */
        private static boolean holdsLanguage(final PreferenceGroup group) {
            for (int i = 0; i < group.getPreferenceCount(); i++) {
                final Preference child = group.getPreference(i);
                if (child instanceof PreferenceGroup) {
                    if (holdsLanguage((PreferenceGroup) child)) {
                        return true;
                    }
                } else if ("appLanguage".equals(child.getKey())) {
                    return true;
                }
            }
            return false;
        }

        private void askReset(final int question, final List<String> keys, final boolean language) {
            Dialogs.confirm(requireActivity(), getString(question), null,
                    getString(R.string.pref_reset_do), () -> reset(keys, language),
                    getString(android.R.string.cancel), null);
        }

        private void reset(final List<String> keys, final boolean language) {
            final SharedPreferences.Editor editor = prefs().edit();
            for (final String key : keys) {
                editor.remove(key);
            }
            // Committed rather than applied: the screen is rebuilt on the next line, and it reads these
            // same preferences to draw itself.
            editor.commit();
            final Activity activity = getActivity();
            if (activity == null) {
                return;
            }
            if (language) {
                // An empty list is "follow the system", which is where the app starts.
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList());
            }
            if (Utils.isTvBox(activity)) {
                // A rebuilt screen does not come back with the focus where it was: measured on the TV
                // emulator, it lands on the toolbar's back arrow, so the next press walks the top bar
                // rather than the list the viewer was in.
                activity.getIntent().putExtra(EXTRA_FOCUS_FIRST, true);
            }
            // Said by the screen that comes back, not by this one: the window a notice would hang in is
            // destroyed on the next line, and the plate would go with it before it could be read.
            activity.getIntent().putExtra(EXTRA_SAY, R.string.pref_reset_done);
            // Rebuilt rather than re-read row by row: a theme or an accent among the keys is applied
            // when the activity is created, and the rows below them have to show what the defaults are
            // now rather than what was stored a moment ago.
            activity.recreate();
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
            final Context dialogContext = Dialogs.dialogContext(activity);
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
                say(activity, R.string.error_copied, R.drawable.ic_content_copy_24dp);
            }
        }

        /** Asks GitHub, and says so either way: silence reads as a row that did nothing. */
        private void checkForUpdate() {
            final Activity activity = getActivity();
            if (activity == null) {
                return;
            }
            say(activity, R.string.update_checking, R.drawable.ic_update_24dp);
            Updater.find(info -> activity.runOnUiThread(() -> {
                if (activity.isFinishing()) {
                    return;
                }
                if (info != null) {
                    // The same three actions the background offer carries. Asking for the check does not
                    // mean wanting the version: ignoring it here is what stops it being offered again.
                    UpdateUi.showAvailableDialog(activity, activity, info, () -> {
                        final Prefs prefs = new Prefs(activity);
                        prefs.setUpdateSkippedVersionCode(info.versionCode);
                        prefs.setUpdatePending(null);
                    }, false);
                } else {
                    say(activity, R.string.update_none, R.drawable.ic_update_24dp);
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
                    tintIcon(holder);
                    reserveTallerSummary(holder, getItem(position));
                    bindThemeMode(holder, getItem(position));
                    bindAccent(holder, getItem(position));
                    bindAbout(holder, getItem(position));
                    bindSubtitlePreview(holder, getItem(position));
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
            final CharSequence[] states = statesOf(preference);
            if (states == null) {
                summary.setMinLines(0);
                return;
            }
            final CharSequence on = states[0];
            final CharSequence off = states[1];
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
            // The taller state outright. This used to reserve one line of slack instead, on the
            // argument that a full reservation leaves a row resting on one line with three empty ones
            // under it — a hole that reads as a broken layout. The argument was sound and the premise
            // is gone: it was written when the on state explained the whole feature and the off state
            // answered in a clause, so the pairs differed by a paragraph. They differ by a word or two
            // now, in all three languages, and a line of give bought nothing but the jump it failed to
            // catch. Half a reservation is the worst of both — it neither fills the row nor holds it
            // still.
            final int lines = Math.max(lineCount(summary, on, width), lineCount(summary, off, width));
            if (summary.getMinLines() != lines) {
                summary.setMinLines(lines);
            }
        }

        /**
         * The two texts a row has to be able to hold without changing height: the shortest it will
         * ever show and the tallest.
         *
         * <p>A switch has exactly two, which is the easy case. A list has as many as it has entries —
         * and it changes its summary to whichever one was picked, so "Default audio track" grew and
         * shrank the whole screen under it every time the choice changed. Its shortest and tallest
         * entries are the same question asked of a longer list. Anything else — a row whose summary is
         * written by the code, a plain preference — has no second state to compare against and keeps
         * whatever height its own words need.
         *
         * @return the pair, or null when this row has nothing to reserve against
         */
        private static CharSequence[] statesOf(final Preference preference) {
            if (preference instanceof TwoStatePreference) {
                final CharSequence on = ((TwoStatePreference) preference).getSummaryOn();
                final CharSequence off = ((TwoStatePreference) preference).getSummaryOff();
                return on == null || off == null || on.toString().equals(off.toString())
                        ? null : new CharSequence[]{ on, off };
            }
            if (preference instanceof ListPreference) {
                final CharSequence[] entries = ((ListPreference) preference).getEntries();
                if (entries == null || entries.length < 2) {
                    return null;
                }
                // Only where the summary really is the entry. A list told to say something else —
                // several of these join their own text — would be measured against words it never
                // shows, and reserve room for a line that cannot appear.
                final CharSequence shown = preference.getSummary();
                boolean isEntry = false;
                for (final CharSequence entry : entries) {
                    if (entry != null && entry.toString().contentEquals(
                            shown == null ? "" : shown)) {
                        isEntry = true;
                        break;
                    }
                }
                if (!isEntry) {
                    return null;
                }
                CharSequence shortest = entries[0];
                CharSequence longest = entries[0];
                for (final CharSequence entry : entries) {
                    if (entry == null) {
                        continue;
                    }
                    if (entry.length() < shortest.length()) {
                        shortest = entry;
                    }
                    if (entry.length() > longest.length()) {
                        longest = entry;
                    }
                }
                return shortest.toString().equals(longest.toString())
                        ? null : new CharSequence[]{ longest, shortest };
            }
            return null;
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

        /** Where the tile row stood and whether a tile held the focus, carried across a repaint. */
        private int accentScrollX = -1;
        private boolean accentFocusWanted;

        /**
         * The hub's glyphs are the player's own, drawn white for the chrome over video, and white on
         * a settings card is invisible in the light appearance. They take the surface accent instead
         * — the Two Grounds Rule, learned three times already in the panels.
         *
         * <p>At bind and as a filter on the row's own view, not once on the preference tree: an
         * accent picked while the screen is open repaints in place, and a colour baked into the
         * drawable then would still be the old one. It also settles the older hazard the tree
         * version had to mutate around — a filter on one ImageView cannot reach another row sharing
         * the drawable's constant state.
         */
        private void tintIcon(final PreferenceViewHolder holder) {
            final View icon = holder.findViewById(android.R.id.icon);
            if (icon instanceof ImageView) {
                ((ImageView) icon).setColorFilter(
                        MaterialColors.getColor(icon.getContext(), R.attr.colorPrimary, Color.WHITE));
            }
        }

        /** Tints one part of a tile's little screen with a role read off the theme's own overlay. */
        private static void paint(final View tile, final int id, final TypedArray a,
                                  final int role, final int fallback) {
            tile.findViewById(id).setBackgroundTintList(
                    ColorStateList.valueOf(a.getColor(role, fallback)));
        }

        /**
         * The accent row: one tile per theme, each a small screen wearing that theme — the ground, a
         * toolbar and a row raised off it, a control filled with the accent, a dot of the ink, and a
         * chosen row in the container — read off the theme's overlay style rather than from a table here — Prefs.Accent is the only
         * place the set is listed. The tile shows the appearance in force: a Nord tile is Nord's light
         * ground under a light appearance and its dark one under a dark, because that is what picking
         * it will do to this screen. The chosen tile wears a 2dp edge in its fill and its name in the
         * same; a focused one the ring every control wears, by the same rule as Utils.focusRing (an
         * edge that widens, drawn over its neighbours), restated here because that helper is typed to
         * a button. A pick recreates the screen, as a change of Dark/Light already does.
         */
        private void bindAccent(final PreferenceViewHolder holder, final Preference preference) {
            if (preference == null || !Prefs.ACCENT_KEY.equals(preference.getKey())
                    || !(holder.itemView instanceof HorizontalScrollView)) {
                return;
            }
            final HorizontalScrollView scroller = (HorizontalScrollView) holder.itemView;
            final LinearLayout row = (LinearLayout) scroller.getChildAt(0);
            final Context context = row.getContext();
            final boolean light = Prefs.isLight(context);
            if (row.getChildCount() == 0) {
                for (final Prefs.Accent accent : Prefs.Accent.values()) {
                    final MaterialCardView tile = (MaterialCardView) LayoutInflater.from(context)
                            .inflate(R.layout.accent_card, row, false);
                    tile.setTag(accent.key);
                    // Straight off the overlay that a pick would apply, so a tile cannot drift from
                    // what it promises. Six roles, because a theme is judged on more than its accent:
                    // the ground behind everything, the card tone its rows sit in, the accent a
                    // control is filled with, and the container pair that says "chosen".
                    final TypedArray a = context.obtainStyledAttributes(
                            light ? accent.light : accent.dark, R.styleable.Accent);
                    paint(tile, R.id.accent_ground, a, R.styleable.Accent_accentGround, Color.BLACK);
                    paint(tile, R.id.accent_bar_top, a, R.styleable.Accent_accentRaised, Color.DKGRAY);
                    paint(tile, R.id.accent_row, a, R.styleable.Accent_accentRaised, Color.DKGRAY);
                    paint(tile, R.id.accent_chosen, a, R.styleable.Accent_accentContainer, Color.GRAY);
                    paint(tile, R.id.accent_pill, a, R.styleable.Accent_accentFill, Color.GRAY);
                    paint(tile, R.id.accent_chip, a, R.styleable.Accent_accentInk, Color.WHITE);
                    final int fill = a.getColor(R.styleable.Accent_accentFill, Color.GRAY);
                    a.recycle();
                    ((TextView) tile.findViewById(R.id.accent_name)).setText(accent.name);
                    tile.setStrokeColor(new ColorStateList(new int[][]{
                            {android.R.attr.state_focused}, {android.R.attr.state_checked}, {}}, new int[]{
                            MaterialColors.getColor(context, R.attr.colorOnSurface, Color.WHITE), fill,
                            MaterialColors.getColor(context, R.attr.colorOutlineVariant, Color.GRAY)}));
                    tile.setOnFocusChangeListener((v, focused) -> {
                        tile.setTranslationZ(focused ? 1f : 0f);
                        edge(tile);
                    });
                    row.addView(tile);
                }
            }
            final String chosen = Prefs.getAccent(context);
            View current = null;
            for (int i = 0; i < row.getChildCount(); i++) {
                final MaterialCardView tile = (MaterialCardView) row.getChildAt(i);
                final boolean checked = chosen.equals(tile.getTag());
                tile.setChecked(checked);
                if (checked)
                    current = tile;
                final int fill = tile.getStrokeColorStateList().getColorForState(
                        new int[]{android.R.attr.state_checked}, 0);
                ((TextView) tile.findViewById(R.id.accent_name)).setTextColor(checked ? fill
                        : MaterialColors.getColor(context, R.attr.colorOnSurface, Color.WHITE));
                edge(tile);
                // The holder is recycled, so the listener from its last binding has to go first.
                tile.setOnClickListener(checked ? null : v -> {
                    Prefs.setAccent(context, (String) tile.getTag());
                    // Kept so the repaint below does not move the row out from under the finger, and
                    // so a remote goes on pointing at the tile it just pressed.
                    accentScrollX = scroller.getScrollX();
                    accentFocusWanted = tile.isFocused();
                    ((SettingsActivity) requireActivity()).repaintAccent(getListView());
                });
            }
            // Before the frame is drawn, not posted after it: a post shows the row at nought for one
            // frame and then jumps it, which is the shift this was reported for. A repaint restores
            // where the row stood; a fresh screen puts the chosen tile second from the left rather
            // than first, so the row says it has a left as well as a right.
            final View target = current;
            final int restore = accentScrollX;
            final boolean focus = accentFocusWanted;
            accentScrollX = -1;
            accentFocusWanted = false;
            if (restore >= 0 || target != null) {
                OneShotPreDrawListener.add(scroller, () -> {
                    scroller.scrollTo(restore >= 0 ? restore
                            : Math.max(0, target.getLeft() - target.getWidth()), 0);
                    if (focus && target != null) {
                        target.requestFocus();
                    }
                });
            }
        }

        /** Chosen or focused, the edge is the ring's width; otherwise the hairline of an outlined tile. */
        private static void edge(final MaterialCardView tile) {
            tile.setStrokeWidth(tile.isChecked() || tile.isFocused()
                    ? tile.getResources().getDimensionPixelSize(R.dimen.focus_ring_width) : Utils.dpToPx(1));
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
            // A sub-screen is titled when it is opened, and the activity forgets that the moment it is
            // built again — a rotation, or the language row taking effect. The screen it is rooted at
            // knows its own name, so it says it here as well.
            if (getArguments() != null && getArguments().getString(ARG_PREFERENCE_ROOT) != null
                    && getPreferenceScreen() != null && getPreferenceScreen().getTitle() != null) {
                requireActivity().setTitle(getPreferenceScreen().getTitle());
            }
            final RecyclerView cardList = getListView();
            if (cardList != null) {
                // The card's own hairlines replace the list's full-width dividers, which would cut
                // across the card edges.
                setDivider(null);
                setDividerHeight(0);
                cardList.addItemDecoration(new GroupCards(cardList.getContext()));
                bindRemote(cardList);
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
                // of into the list. Hand it the first row. The same is true of a screen rebuilt after
                // a reset, which is restored rather than created and so has state to restore — just
                // not the focus.
                if (savedInstanceState == null || takeFocusFirst()) {
                    openAtPosition(0, 3);
                }
                return;
            }
            final SettingsActivity activity = (SettingsActivity) requireActivity();
            final String returning = activity.openedScreenKey;
            activity.openedScreenKey = null;
            if (takeFocusFirst()) {
                openAtPosition(0, 3);
            } else if (returning != null) {
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

        /** The line the screen that rebuilt this one could not stay alive long enough to say. */
        private void sayWhatWasOwed() {
            final Activity activity = getActivity();
            if (activity == null || activity.getIntent() == null) {
                return;
            }
            final int textRes = activity.getIntent().getIntExtra(EXTRA_SAY, 0);
            if (textRes == 0) {
                return;
            }
            // Spent here, so a rotation does not say it again.
            activity.getIntent().removeExtra(EXTRA_SAY);
            say(activity, textRes, R.drawable.ic_settings_24dp);
        }

        /** Whether this screen was rebuilt by a reset and owes a remote somewhere to stand. */
        private boolean takeFocusFirst() {
            final Activity activity = getActivity();
            if (activity == null || activity.getIntent() == null
                    || !activity.getIntent().getBooleanExtra(EXTRA_FOCUS_FIRST, false)) {
                return false;
            }
            // Spent here: the screen is rebuilt again on a rotation, and that one keeps its focus.
            activity.getIntent().removeExtra(EXTRA_FOCUS_FIRST);
            return true;
        }

        /**
         * Offers this fragment the remote's arrows.
         *
         * <p>Given to the activity rather than set on the list, because a key event is delivered to
         * the view that holds the focus, and in a list that is always a row - an OnKeyListener on the
         * RecyclerView is never called.
         */
        private void bindRemote(final RecyclerView list) {
            if (!Utils.isTvBox(list.getContext())) {
                return;
            }
            ((SettingsActivity) requireActivity()).remote = this;
        }

        /**
         * Up and Down, offered this fragment before the view tree gets them, and taken only where the
         * view tree would do nothing useful with them.
         *
         * <p>Left and Right used to jump to the next section here - written as a way to cross a list
         * 26 stops deep without a fast scroll, and never once run, because the listener it lived in
         * was never called. Waking it up put it in front of somebody for the first time and it read
         * as the focus flying off by itself, several rows at a time. Gone; now a sideways key reaches
         * only the rows that have their own use for one.
         */
        boolean onRemoteKey(final int keyCode) {
            final boolean vertical = keyCode == KeyEvent.KEYCODE_DPAD_UP
                    || keyCode == KeyEvent.KEYCODE_DPAD_DOWN;
            final boolean sideways = keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                    || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT;
            if (!vertical && !sideways) {
                return false;
            }
            final RecyclerView list = getListView();
            if (list == null || !Utils.isTvBox(list.getContext())) {
                return false;
            }
            // Null when the focus is on the toolbar or inside a dialog, where none of this applies.
            final View focused = list.findFocus();
            if (focused == null) {
                return false;
            }
            if (vertical) {
                return reachEnd(list, focused, keyCode == KeyEvent.KEYCODE_DPAD_DOWN);
            }
            return !usedByRow(list.findContainingItemView(focused), focused,
                    keyCode == KeyEvent.KEYCODE_DPAD_RIGHT);
        }

        /**
         * Whether the row can do something with a sideways key itself: hand the focus to another
         * control of its own, or scroll in that direction. The theme segments can, the accent tiles
         * can, the About block can; an ordinary row cannot, and for it the key is taken and thrown
         * away.
         *
         * <p>Thrown away rather than left to the view tree, which answers a sideways key on a
         * full-width row by sending the focus to the nearest thing in that general direction - the
         * back arrow, one row up. A remote does not expect Left to move it vertically.
         *
         * <p>Asked of the row and not of the classes in it: naming the widgets was the first try, and
         * it was wrong the moment a row was built out of something else.
         */
        private static boolean usedByRow(final View row, final View focused, final boolean right) {
            for (View v = focused; v != null; v = parentView(v)) {
                if (v.canScrollHorizontally(right ? 1 : -1)) {
                    return true;
                }
                if (v == row) {
                    break;
                }
            }
            final View sideways = focused.focusSearch(right ? View.FOCUS_RIGHT : View.FOCUS_LEFT);
            return sideways != null && sideways != focused && inside(row, sideways);
        }

        /** The parent, when it is a view at all. */
        private static View parentView(final View view) {
            final ViewParent parent = view.getParent();
            return parent instanceof View ? (View) parent : null;
        }

        /**
         * What Up and Down do once the focus has run out of rows to move to.
         *
         * <p>A remote moves the focus and the list scrolls only as far as the focused row needs, so
         * anything on the page that cannot take focus is unreachable: the subtitle preview above the
         * first row - coming back up the page left it off screen with no way to see it again short of
         * leaving - the AMOLED switch below the last one, which the light appearance greys out, and the
         * back arrow above the list, which nothing ever handed the focus to.
         *
         * <p>So where the focus cannot move, the key scrolls: to the top of the list or the end of it,
         * which is where those things are. With the list already at the top, Up gives the focus to the
         * toolbar, which is what a remote pressing Up at the top of a page is reaching for. Down at the
         * end has nowhere further to go and says so by doing nothing.
         */
        private boolean reachEnd(final RecyclerView list, final View focused, final boolean down) {
            if (movesOn(list, focused, down)) {
                return false;
            }
            if (list.canScrollVertically(down ? 1 : -1)) {
                // Down by a screen and not to the last row: a smooth scroller stops as soon as the row
                // it was sent to is visible by an edge, which on a television leaves the last switch
                // half in the overscan. A page scroll clamps itself at the end instead.
                if (down) {
                    list.smoothScrollBy(0, list.getHeight());
                } else {
                    list.smoothScrollToPosition(0);
                }
                return true;
            }
            if (down) {
                // Taken and thrown away. Left to the view tree, Down at the last row wraps the focus
                // round to the top of the list - on the owner's television it jumped from About back
                // up to Skip segments, four rows above, which reads as the remote having done
                // something random. The end of a list is the end of it.
                return true;
            }
            final View toolbar = requireActivity().findViewById(R.id.toolbar);
            // requestFocus on the bar gives it to the first child that will take it, which is the
            // navigation button - the only thing up there a remote has any use for.
            return toolbar != null && toolbar.requestFocus();
        }


        /**
         * Whether the focus has a row to move to in that direction, as opposed to nothing at all or
         * the wrap-around the framework offers when it has run out.
         *
         * <p>Three things disqualify what the search comes back with. It can be outside the list,
         * which is the toolbar, and the toolbar is the answer only once the list has been scrolled.
         * It can be merely disabled: a focus search skips a view that is not focusable but not one
         * that is switched off, so the greyed AMOLED switch is offered as the next stop and then
         * refuses the focus, and the key does nothing at all. And it can be behind the row the focus
         * is on - the wrap - which is a jump, not a step.
         */
        private static boolean movesOn(final RecyclerView list, final View focused,
                                       final boolean down) {
            final View next = list.focusSearch(focused, down ? View.FOCUS_DOWN : View.FOCUS_UP);
            if (next == null || next == focused || !inside(list, next) || !next.isEnabled()) {
                return false;
            }
            final View fromRow = list.findContainingItemView(focused);
            final View toRow = list.findContainingItemView(next);
            if (fromRow == null || toRow == null) {
                return true;
            }
            final int from = list.getChildAdapterPosition(fromRow);
            final int to = list.getChildAdapterPosition(toRow);
            if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) {
                return true;
            }
            // Equal is a move inside one row, which the About block is made of.
            return down ? to >= from : to <= from;
        }

        /** Whether {@code view} is the list or sits inside it. */
        private static boolean inside(final View list, final View view) {
            for (ViewParent parent = view.getParent(); parent != null; parent = parent.getParent()) {
                if (parent == list) {
                    return true;
                }
            }
            return false;
        }

        /**
         * What Up and Down do once the focus has run out of rows to move to.
         *
         * <p>A remote moves the focus, and the list scrolls only as far as the focused row needs. So
         * everything that cannot take focus is unreachable: the subtitle preview above the first row
         * (coming back up the page left it off screen, and there was no way to see it again without
         * leaving the screen), the AMOLED switch below the last one when the light appearance greys it
         * out, and the back arrow above the list, which nothing ever handed the focus to.
         *
         * <p>So when there is nothing to move to, the key scrolls instead: to the top of the list or
         * the bottom of it, which is where the unfocusable things are. Once the list is already there,
         * Up hands the focus to the toolbar, which is what a remote is reaching for when it presses Up
         * at the top of a page. Down at the end has nowhere further to go and says so by doing nothing.
         */
        private boolean reachEnd(final RecyclerView list, final boolean down) {
            final View focused = list.findFocus();
            // Only at the ends: anywhere else the focus has a row to move to and this is not its
            // business. focusSearch answers for the list's own rows, which is the question here.
            if (focused == null || focused.focusSearch(down ? View.FOCUS_DOWN : View.FOCUS_UP) != null) {
                return false;
            }
            if (list.canScrollVertically(down ? 1 : -1)) {
                list.smoothScrollToPosition(down ? Math.max(0, list.getAdapter() == null
                        ? 0 : list.getAdapter().getItemCount() - 1) : 0);
                return true;
            }
            if (down) {
                return false;
            }
            final View toolbar = requireActivity().findViewById(R.id.toolbar);
            // requestFocus on the bar gives it to the first child that will take it, which is the
            // navigation button - the only thing up there a remote has any use for.
            return toolbar != null && toolbar.requestFocus();
        }


        /**
         * Opens the list on one row: scrolled so the section header above it is still on screen, and
         * with the row itself focused. scrollToPreference alone scrolls the row barely into view — at
         * the bottom edge, inside a TV's overscan — and leaves a remote's focus on the first row, so
         * the first D-pad press yanks the list straight back to the top.
         *
         * <p>Scrolls to the row asked for and focuses the first row at or after it that can actually
         * take focus, which is not always the same one: a header takes none, and neither does a
         * picture or a row switched off. The subtitle previews are what made the two differ — each
         * heads a section, so a section jump aimed straight at one and moved the list without moving
         * the focus. The search stops at the next header, so a section with nothing live in it scrolls
         * into view and leaves the focus where it was rather than throwing it into the section below.
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
            final int focusAt = focusableFrom(list.getAdapter(), position);
            manager.scrollToPositionWithOffset(Math.max(0, position - 1), 0);
            if (focusAt == RecyclerView.NO_POSITION) {
                return;
            }
            OneShotPreDrawListener.add(list, () -> {
                final RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(focusAt);
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
         * The first row at or after {@code start} that a remote can land on, within the section
         * {@code start} falls in. A {@link PreferenceCategory} is a header and takes no focus; a row
         * that is not selectable (the subtitle previews) or is switched off cannot take it either -
         * {@code View.requestFocus} refuses a disabled view.
         */
        private static int focusableFrom(final RecyclerView.Adapter<?> adapter, final int start) {
            if (!(adapter instanceof PreferenceGroupAdapter)) {
                return start;
            }
            final PreferenceGroupAdapter rows = (PreferenceGroupAdapter) adapter;
            for (int i = Math.max(0, start); i < rows.getItemCount(); i++) {
                final Preference item = rows.getItem(i);
                if (item instanceof PreferenceCategory) {
                    // The one at start is the header of the section being entered; a later one is the
                    // next section, and a jump does not carry on into it.
                    if (i > start) {
                        return RecyclerView.NO_POSITION;
                    }
                    continue;
                }
                if (item.isSelectable() && item.isEnabled()) {
                    return i;
                }
            }
            return RecyclerView.NO_POSITION;
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
                LanguagePriorityDialog.show(requireActivity(), getString(titleRes),
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

        /** The two preview rows, one on each screen that dresses a subtitle line. */
        private static final String PREVIEW_KEY = "subtitlePreview";
        private static final String SECONDARY_PREVIEW_KEY = "subtitleSecondaryPreview";
        private static final String BOLD_KEY = "subtitleStyleBold";

        /**
         * The line the rows under it dress, drawn over a scene whose shore is pale where its water is
         * dark, so the caption lands on both at once. A colour, a plate and an outline are judged against
         * the picture they will sit on, which is the one thing a swatch beside a name cannot say - and
         * what the swatches this replaced could not say either, since three of the six text colours are
         * near-white and the transparent plate showed nothing at all.
         *
         * <p>Both lines are drawn the way the player draws them rather than imitated: the first through
         * Media3 from the same {@link SubtitleUtils#captionStyle} the player builds, the second as a
         * TextView on a plate, which is what {@link SecondarySubtitles} is. A preview built from its own
         * copy of the rules is a preview of something else.
         *
         * <p>Sized as a screen in miniature: the row's height stands for the player's, so every fraction
         * Media3 works in lands where it would, and the plate's corner and padding come down by the same
         * ratio. The screen it stands for is the short side of the display - a film is watched sideways.
         */
        private void bindSubtitlePreview(final PreferenceViewHolder holder,
                                         final Preference preference) {
            if (preference == null) {
                return;
            }
            final boolean hint = SECONDARY_PREVIEW_KEY.equals(preference.getKey());
            if (!hint && !PREVIEW_KEY.equals(preference.getKey())) {
                return;
            }
            // A disabled row dims its own title and summary; this one has neither, so the picture is what
            // has to say the section is off - the look of a hint means nothing with no second line.
            holder.itemView.setAlpha(preference.isEnabled() ? 1f : 0.38f);
            final View lineView = holder.findViewById(R.id.subtitle_preview_line);
            final TextView hintView = (TextView) holder.findViewById(R.id.subtitle_preview_hint);
            if (!(lineView instanceof SubtitleView) || hintView == null) {
                return;
            }
            final ListPreference scale =
                    findPreference(hint ? "subtitleSecondaryScale" : "subtitleScale");
            final ListPreference textColor =
                    findPreference(hint ? "subtitleSecondaryTextColor" : "subtitleTextColor");
            final ListPreference background =
                    findPreference(hint ? "subtitleSecondaryBackground" : "subtitleBackground");
            if (scale == null || textColor == null || background == null) {
                return;
            }
            final Context context = holder.itemView.getContext();
            final int height =
                    context.getResources().getDimensionPixelSize(R.dimen.subtitle_preview_height);
            final DisplayMetrics metrics = context.getResources().getDisplayMetrics();
            final boolean small = Utils.isTvBox(context) || Utils.isTablet(context);
            final float textFraction = SubtitleView.DEFAULT_TEXT_SIZE_FRACTION
                    * SubtitleUtils.normalizeFontScale(Float.parseFloat(scale.getValue()), small);
            final float gapFraction = SubtitleView.DEFAULT_BOTTOM_PADDING_FRACTION * 2f / 3f;
            final int ink = Color.parseColor(textColor.getValue());
            final int plate = Color.parseColor(background.getValue());
            // Weight belongs to both lines and its row is on the first line's screen only, so from the
            // second one it is read where it is stored rather than off a row that is not there.
            final SwitchPreferenceCompat boldRow = findPreference(BOLD_KEY);
            final boolean bold = boldRow != null ? boldRow.isChecked()
                    : getPreferenceManager().getSharedPreferences().getBoolean(BOLD_KEY, false);
            final String sample = getString(R.string.pref_subtitle_preview_sample);

            final SubtitleView line = (SubtitleView) lineView;
            line.setVisibility(hint ? View.GONE : View.VISIBLE);
            hintView.setVisibility(hint ? View.VISIBLE : View.GONE);
            if (!hint) {
                final ListPreference edge = findPreference("subtitleEdge");
                if (edge == null) {
                    return;
                }
                line.setStyle(SubtitleUtils.captionStyle(ink, plate,
                        Integer.parseInt(edge.getValue()), bold));
                line.setFractionalTextSize(textFraction);
                line.setBottomPaddingFraction(gapFraction);
                line.setCues(Collections.singletonList(new Cue.Builder().setText(sample).build()));
                return;
            }
            final float mini = height / (float) Math.min(metrics.widthPixels, metrics.heightPixels);
            final int padH = Math.round(Utils.dpToPx(8) * mini);
            final int padV = Math.round(Utils.dpToPx(4) * mini);
            hintView.setText(sample);
            hintView.setMaxLines(2);
            hintView.setTextColor(ink);
            hintView.setTypeface(Typeface.create(Typeface.DEFAULT,
                    bold ? Typeface.BOLD : Typeface.NORMAL));
            hintView.setTextSize(TypedValue.COMPLEX_UNIT_PX, textFraction * height);
            hintView.setPadding(padH, padV, padH, padV);
            if (plate == Color.TRANSPARENT) {
                hintView.setBackground(null);
            } else {
                final GradientDrawable drawable = new GradientDrawable();
                drawable.setCornerRadius(Utils.dpToPx(6) * mini);
                drawable.setColor(plate);
                hintView.setBackground(drawable);
            }
            final FrameLayout.LayoutParams params =
                    (FrameLayout.LayoutParams) hintView.getLayoutParams();
            params.bottomMargin = Math.round(gapFraction * height);
            hintView.setLayoutParams(params);
        }

        /**
         * A preview is drawn from what is stored, so anything that writes a subtitle setting redraws it
         * - and a pick refused as a clash writes nothing, so it asks for nothing. One listener rather
         * than one per row: seven rows feed the two previews, and two of them already carry a listener
         * of their own.
         */
        private final SharedPreferences.OnSharedPreferenceChangeListener previewWatch =
                (preferences, key) -> refreshPreviews();

        private void refreshPreviews() {
            final RecyclerView list = getListView();
            final RecyclerView.Adapter<?> adapter = list == null ? null : list.getAdapter();
            if (!(adapter instanceof PreferenceGroupAdapter)) {
                return;
            }
            for (final String key : new String[]{PREVIEW_KEY, SECONDARY_PREVIEW_KEY}) {
                final int position =
                        ((PreferenceGroupAdapter) adapter).getPreferenceAdapterPosition(key);
                if (position != RecyclerView.NO_POSITION) {
                    adapter.notifyItemChanged(position);
                }
            }
        }

        /**
         * Every list and every field on a settings screen, taken off the preference framework and
         * raised the way the rest of the app raises a question.
         *
         * <p>The framework builds its own dialog for a {@code ListPreference} and an
         * {@code EditTextPreference}, and there are twenty-one of them here. Nothing in this app ever
         * saw those calls, so while every window the app built for itself had moved to the bottom edge
         * of a phone (see {@link Dialogs#asksAtTheEdge}), these twenty-one went on arriving as cards in
         * the middle — the one place a viewer meets most of them.
         *
         * <p>Above 600dp this hands them straight back: the framework's dialog is the Material one, and
         * {@link Dialogs} would raise the same thing.
         */
        @Override
        public void onDisplayPreferenceDialog(@NonNull Preference preference) {
            final Context context = getContext();
            if (context == null || !Dialogs.asksAtTheEdge(context)) {
                super.onDisplayPreferenceDialog(preference);
                return;
            }
            if (preference instanceof ListPreference) {
                final ListPreference list = (ListPreference) preference;
                final CharSequence[] entries = list.getEntries();
                final CharSequence[] values = list.getEntryValues();
                if (entries == null || values == null) {
                    super.onDisplayPreferenceDialog(preference);
                    return;
                }
                Dialogs.choice(requireActivity(), list.getDialogTitle() != null
                                ? list.getDialogTitle() : list.getTitle(),
                        java.util.Arrays.asList(entries), list.findIndexOfValue(list.getValue()),
                        which -> {
                            final String picked = values[which].toString();
                            // Through the listener, never straight into the store: the screens here
                            // hang their own rules off it — a mode that greys out the rows below it,
                            // a colour pair that refuses its own twin — and a value written past it
                            // would apply while the screen went on showing the old one.
                            if (list.callChangeListener(picked)) {
                                list.setValue(picked);
                            }
                        });
                return;
            }
            if (preference instanceof androidx.preference.EditTextPreference) {
                final androidx.preference.EditTextPreference field =
                        (androidx.preference.EditTextPreference) preference;
                final Context themed = Dialogs.dialogContext(requireActivity());
                // The preference's own dialog layout, inflated rather than rebuilt. Three of these
                // carry one, and what it holds is not decoration: the URL fields get their hint and
                // android:inputType="textUri" from there, which is the difference between a keyboard
                // with a slash on it and one without, and all three get the explaining line above the
                // box. A hand-built field loses every one of those, silently, on the phone only —
                // which is the half of the app where they matter.
                final int layoutRes = field.getDialogLayoutResource();
                final View body = layoutRes == 0 ? null
                        : LayoutInflater.from(themed).inflate(layoutRes, null);
                if (body != null) {
                    // That layout carries androidx's own 24dp, which is the gutter of a dialog in the
                    // middle of the screen. This is a sheet, whose title and buttons stand 6dp in, so the
                    // field was inset past both of them — visible as a box narrower than the row of
                    // answers under it. One rule for both kinds of field.
                    final int gutter = Dialogs.fieldGutter(themed);
                    body.setPaddingRelative(gutter, body.getPaddingTop(),
                            gutter, body.getPaddingBottom());
                }
                final EditText editor = body == null ? null : body.findViewById(android.R.id.edit);
                final View strip;
                final EditText input;
                if (editor != null) {
                    final TextView message = body.findViewById(android.R.id.message);
                    if (message != null) {
                        final CharSequence text = field.getDialogMessage();
                        message.setText(text);
                        message.setVisibility(text == null || text.length() == 0
                                ? View.GONE : View.VISIBLE);
                    }
                    strip = body;
                    input = editor;
                } else {
                    final ViewGroup built = Dialogs.dialogFields(themed);
                    input = Dialogs.textField(built, field.getTitle());
                    strip = built;
                }
                input.setText(field.getText());
                input.setSelection(input.getText().length());
                Dialogs.fields(requireActivity(), field.getDialogTitle() != null
                                ? field.getDialogTitle() : field.getTitle(), strip,
                        getString(android.R.string.ok), () -> {
                            final String typed = input.getText().toString();
                            if (field.callChangeListener(typed)) {
                                field.setText(typed);
                            }
                        });
                return;
            }
            super.onDisplayPreferenceDialog(preference);
        }

        /** Only the storage grant is asked for from this screen, so one code is all there is. */
        private static final int REQUEST_STORAGE = 1;

        /**
         * Where a runtime permission is taken back, and the only place it can be granted once it has
         * been refused for good: from then on the system answers the request without showing anything,
         * which would leave the row looking broken. Told apart from a first refusal by the rationale
         * flag — false with the permission still missing means the system will no longer ask.
         */
        @Override
        public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                               @NonNull int[] results) {
            super.onRequestPermissionsResult(requestCode, permissions, results);
            final Context context = getContext();
            if (requestCode != REQUEST_STORAGE || context == null) {
                return;
            }
            if (!Utils.canListStorage(context)
                    && !shouldShowRequestPermissionRationale(
                            Manifest.permission.READ_EXTERNAL_STORAGE)) {
                openAppDetails(context);
                return;
            }
            bindBrowser(); // granted, or refused with another ask still possible: say which
        }

        /** The app's own page in system settings, where every permission it holds is listed. */
        private void openAppDetails(final Context context) {
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + context.getPackageName())));
            } catch (Exception e) {
                Notice.show(getActivity(), R.string.browse_no_source, true, R.drawable.ic_folder_open_24dp);
            }
        }

        @Override
        public void onStart() {
            super.onStart();
            sayWhatWasOwed();
            // The all-files screen belongs to the system and hands back no result, and a folder can
            // be hidden in the browser while this screen sits behind it. Both rows say what is true
            // now rather than what was true when they were built.
            bindBrowser();
            final SharedPreferences preferences = getPreferenceManager().getSharedPreferences();
            if (preferences != null) {
                preferences.registerOnSharedPreferenceChangeListener(previewWatch);
            }
        }

        /**
         * The two rows of the Browser screen that answer for something outside the preference store:
         * the grant the system holds, and the list the browser writes.
         */
        private void bindBrowser() {
            final Context context = getContext();
            if (context == null) {
                return;
            }
            final Preference access = findPreference("allFilesAccess");
            if (access != null) {
                final boolean granted = Utils.canListStorage(context);
                access.setSummary(granted
                        ? R.string.pref_all_files_access_on : R.string.pref_all_files_access_off);
                access.setOnPreferenceClickListener(preference -> {
                    // Two doors, and which one this build has is not the viewer's business — the row
                    // says the same thing either way, because either way it is the same answer: can
                    // this app list the device. Shown on every version for the same reason. It used
                    // to be hidden below API 30, where the runtime permission is the door and the
                    // browser asks for it when it needs it — but a grant refused once is then only
                    // ever asked for again by walking back into the place that failed, and nothing
                    // in Settings admits the feature exists. That is the state this row is for.
                    if (Utils.permissionOpensStorage(context)) {
                        if (Utils.canListStorage(context)) {
                            // Nothing to ask for. The way out is the way in: the app's own page is
                            // where the grant is taken back, and the row is how you get there.
                            openAppDetails(context);
                        } else {
                            requestPermissions(
                                    new String[]{ Manifest.permission.READ_EXTERNAL_STORAGE },
                                    REQUEST_STORAGE);
                        }
                        return true;
                    }
                    if (!Utils.askForAllFiles(context)) {
                        Notice.show(getActivity(), R.string.browse_no_source, true, R.drawable.ic_folder_open_24dp);
                    }
                    return true;
                });
            }
        }

        @Override
        public void onStop() {
            final SharedPreferences preferences = getPreferenceManager().getSharedPreferences();
            if (preferences != null) {
                preferences.unregisterOnSharedPreferenceChangeListener(previewWatch);
            }
            super.onStop();
        }

        /** One text/background pair: neither of the two allowed to be set to the colour of the other. */
        private void bindColorPair(final String textColorKey, final String backgroundKey) {
            final ListPreference textColor = findPreference(textColorKey);
            final ListPreference background = findPreference(backgroundKey);
            if (textColor == null || background == null) {
                return;
            }
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
            say(requireActivity(), R.string.pref_subtitle_color_clash, R.drawable.ic_palette_24dp);
            return false;
        }

        /** The chosen languages, in order, or a note that nothing is preferred. */
        /**
         * Everything the second line's screen holds besides the mode itself - the whole look section,
         * preview included, so the section greys out as one thing rather than as four rows under a
         * picture that stayed bright.
         */
        private static final String[] SECONDARY_DEPENDENTS = {
                "languageSubtitleSecondary", "subtitleSecondaryPreview", "subtitleSecondaryScale",
                "subtitleSecondaryTextColor", "subtitleSecondaryBackground",
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

        /**
         * Reflects the chosen mode on the row that leads here, and nothing else.
         *
         * <p>It used to grey out every other row on that screen while the mode was "never", on the
         * reasoning that they configure a search that is not running. They do not: a search asked for
         * from the player runs whatever the mode says - {@code offline = !subtitleSearch && !manual} in
         * PlayerActivity - and it reads the sources, the translation and "ask for the language" exactly
         * as an automatic one would. "Ask for the language" is manual-only, so it was the setting most
         * plainly in force and most plainly greyed. A row that is dimmed while it still decides
         * something is worse than no gate at all, so the gate is gone and the mode only reports itself.
         */
        private void applySearchMode(final ListPreference searchMode, final String mode,
                                     final SwitchPreferenceCompat translate) {
            // The endpoint list is the one true gate on that screen: nothing to order if nothing is
            // translated.
            enableTranslateBackends(translate == null || translate.isChecked());
            // Null while the fragment is rooted at the search screen: the row lives one level up.
            final Preference screen = findPreference("subtitleSearchScreen");
            final int index = searchMode.findIndexOfValue(mode);
            if (screen != null && index >= 0) {
                screen.setSummary(searchMode.getEntries()[index]);
            }
        }

        /**
         * The endpoint list is live only while translation is on. By hand rather than app:dependency,
         * which watches a parent's enablement rather than its checked state.
         */
        private void enableTranslateBackends(final boolean translating) {
            final Preference backends = findPreference("subtitleTranslateBackends");
            if (backends != null) {
                backends.setEnabled(translating);
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

        // Corner.Medium, the step Material gives a card - and the step this app gives anything 72dp or
        // taller. 20dp was a value nothing else here used: the dialog is 28, a value tile 8, a button a pill.
        private static final int RADIUS = Utils.dpToPx(12);

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
                // With the translation, for the reason the hairlines carry it below: while the animator
                // is carrying rows to their new places the card would otherwise be drawn around where
                // they are going rather than around where they are, and the plate is the largest thing
                // on the screen to have that happen to.
                float top = first.getTop() + first.getTranslationY();
                float bottom = lastView.getBottom() + lastView.getTranslationY();
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
                // Where the row is drawn, not where it has been laid out. Those are the same number
                // until something above it changes height — a switch whose off state needs four lines
                // to say what its on state says in two — and then every row below is carried to its new
                // place by the item animator, which moves them with translationY and leaves getBottom()
                // reporting the destination. A hairline drawn there sits on empty sheet for the length
                // of the animation while the row it belongs to is still on its way, which is the line
                // seen struck through the text of a row two places down.
                final float y = row.getBottom() + row.getTranslationY();
                canvas.drawLine(row.getLeft() + row.getTranslationX() + hairlineInset, y,
                        row.getRight() + row.getTranslationX() - hairlineInset, y, hairline);
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
