package com.brouken.player;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.view.accessibility.CaptioningManager;

import com.brouken.player.together.AliasGenerator;
import android.preference.PreferenceManager;
import android.provider.DocumentsContract;
import android.text.TextUtils;

import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.CaptionStyleCompat;

import com.brouken.player.update.UpdateInfo;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

class Prefs {
    // Previously used
    // private static final String PREF_KEY_AUDIO_TRACK = "audioTrack";
    // private static final String PREF_KEY_AUDIO_TRACK_FFMPEG = "audioTrackFfmpeg";
    // private static final String PREF_KEY_SUBTITLE_TRACK = "subtitleTrack";

    private static final String PREF_KEY_MEDIA_URI = "mediaUri";
    private static final String PREF_KEY_MEDIA_TYPE = "mediaType";
    private static final String PREF_KEY_BRIGHTNESS = "brightness"; // legacy 0-30 levels, migrated on load
    private static final String PREF_KEY_BRIGHTNESS_PERCENT = "brightnessPercent";
    private static final String PREF_KEY_VOLUME_PERCENT = "volumePercent";
    private static final String PREF_KEY_FIRST_RUN = "firstRun";
    private static final String PREF_KEY_SUBTITLE_URI = "subtitleUri";

    private static final String PREF_KEY_AUDIO_TRACK_ID = "audioTrackId";
    private static final String PREF_KEY_SUBTITLE_TRACK_ID = "subtitleTrackId";
    private static final String PREF_KEY_RESIZE_MODE = "resizeMode";
    private static final String PREF_KEY_ORIENTATION = "orientation";
    private static final String PREF_KEY_SCALE = "scale";
    private static final String PREF_KEY_ASPECT_RATIO = "aspectRatio";
    private static final String PREF_KEY_SCOPE_URI = "scopeUri";
    private static final String PREF_KEY_ASK_SCOPE = "askScope";
    private static final String PREF_KEY_RESTORE_AUTO_ROTATE = "restoreAutoRotate";
    private static final String PREF_KEY_AUTO_PIP = "autoPiP";
    private static final String PREF_KEY_DISABLE_VOLUME_BRIGHTNESS_GESTURES = "disableVolumeBrightnessGestures";
    private static final String PREF_KEY_HOLD_SPEED = "holdSpeed";
    private static final String PREF_KEY_TUNNELING = "tunneling";
    private static final String PREF_KEY_FRAMERATE_MATCHING = "frameRateMatching";
    private static final String PREF_KEY_ALLOW_SYSTEM_FRAMERATE = "allowSystemFrameRate";
    private static final String PREF_KEY_REPEAT_TOGGLE = "repeatToggle";
    private static final String PREF_KEY_SPEED = "speed";
    private static final String PREF_KEY_FILE_ACCESS = "fileAccess";
    private static final String PREF_KEY_DECODER_PRIORITY = "decoderPriority";
    private static final String PREF_KEY_MAP_DV7 = "mapDV7ToHevc";
    private static final String PREF_KEY_LANGUAGE_AUDIO = "languageAudio";
    private static final String PREF_KEY_LANGUAGE_SUBTITLE = "languageSubtitle";
    // Online subtitle search, all of it behind one row on the settings screen. These used to be
    // checkboxes inside the language-priority dialog, which hid the feature behind a row that never
    // mentions it while leaving its lesser options in plain sight.
    private static final String PREF_KEY_SUBTITLE_SEARCH_MODE = "subtitleSearchMode";
    // Replaced by the mode above; still read once, to carry an existing choice over.
    private static final String PREF_KEY_SUBTITLE_SEARCH = "subtitleSearch";
    private static final String PREF_KEY_SUBTITLE_SEARCH_STRICT = "subtitleSearchStrict";
    private static final String PREF_KEY_SUBTITLE_SEARCH_LANGUAGE = "subtitleSearchLanguage";
    private static final String PREF_KEY_SUBTITLE_TRANSLATE_MODE = "subtitleTranslateMode";
    private static final String PREF_KEY_SUBTITLE_TRANSLATE_BACKENDS = "subtitleTranslateBackends";
    // Replaced by the mode above; still read once, to carry an existing choice over.
    private static final String PREF_KEY_SUBTITLE_TRANSLATE = "subtitleTranslate";
    // One per source, so a single one can be exercised on its own when something looks wrong.
    private static final String PREF_KEY_SOURCE_OPENSUBTITLES = "subtitleSourceOpenSubtitles";
    private static final String PREF_KEY_SOURCE_SHEGU = "subtitleSourceShegu";
    private static final String PREF_KEY_SOURCE_STREMIO = "subtitleSourceStremio";
    private static final String PREF_KEY_SOURCE_REST = "subtitleSourceRest";
    private static final String PREF_KEY_SUBTITLE_STYLE_EMBEDDED = "subtitleStyleEmbedded";
    private static final String PREF_KEY_SUBTITLE_STYLE_BOLD = "subtitleStyleBold";
    private static final String PREF_KEY_SUBTITLE_SCALE = "subtitleScale";
    private static final String PREF_KEY_SUBTITLE_TEXT_COLOR = "subtitleTextColor";
    private static final String PREF_KEY_SUBTITLE_BACKGROUND = "subtitleBackground";
    private static final String PREF_KEY_SUBTITLE_EDGE = "subtitleEdge";
    private static final String PREF_KEY_SKIP_ENABLED = "skipEnabled";
    private static final String PREF_KEY_SKIP_MODE = "skipMode";
    private static final String PREF_KEY_SKIP_MODE_CREDITS = "skipModeCredits";
    private static final String PREF_KEY_SKIP_FETCH = "skipFetchOnline";
    private static final String PREF_KEY_SKIP_UNDO = "skipUndo";
    private static final String PREF_KEY_SKIP_HIDE_LOCKED = "skipHideWhenLocked";
    private static final String PREF_KEY_SHOW_CLOCK = "showClock";
    private static final String PREF_KEY_TIME_REMAINING = "timeRemaining";
    private static final String PREF_KEY_SHOW_STATS = "showStats";
    private static final String PREF_KEY_SYSTEM_VOLUME = "systemVolume";
    private static final String PREF_KEY_TOGETHER_NICK = "togetherNick";
    private static final String PREF_KEY_TOGETHER_PASSWORD = "togetherPassword";
    private static final String PREF_KEY_TOGETHER_PUBLIC = "togetherPublic";
    private static final String PREF_KEY_TOGETHER_RELAY = "togetherRelay";
    private static final String PREF_KEY_TOGETHER_INVITE_PAGE = "togetherInvitePage";
    private static final String PREF_KEY_CRASH_REPORTING = "crashReporting";
    private static final String PREF_KEY_AUTO_UPDATE = "autoUpdate";
    private static final String PREF_KEY_UPDATE_LAST_CHECK = "updateLastCheck";
    private static final String PREF_KEY_UPDATE_SKIPPED = "updateSkippedVersionCode";
    private static final String PREF_KEY_UPDATE_PENDING = "updatePending";
    private static final String PREF_KEY_REVOKED_AUDIO_MIMES = "revokedAudioMimes";
    private static final String PREF_KEY_REVOKED_AUDIO_MIMES_RELEARNED = "revokedAudioMimesRelearned";

    // How a skippable segment is offered. BRIEF shows the Skip button for PlayerActivity.SKIP_NOTICE_MS and
    // then leaves the picture alone; the option's name says "5 seconds", so that constant and the
    // pref_skip_mode_brief strings have to move together.
    public static final String SKIP_MODE_BRIEF = "brief";
    public static final String SKIP_MODE_BUTTON = "button";
    public static final String SKIP_MODE_AUTO = "auto";

    // Which skips offer the "go back" pill afterwards.
    // When the online subtitle search runs. One choice, because the two switches this replaced were
    // not independent: the second meant nothing while the first was off.
    public static final String SEARCH_OFF = "off";
    public static final String SEARCH_FIRST = "first";
    public static final String SEARCH_NONE = "none";

    // Machine translation, and whether the source line stays under it. One choice for the same reason:
    // keeping the original means nothing when nothing is being translated.
    public static final String TRANSLATE_OFF = "off";
    public static final String TRANSLATE_ONLY = "only";
    public static final String TRANSLATE_BOTH = "both";

    public static final String SKIP_UNDO_ALL = "all";
    public static final String SKIP_UNDO_MANUAL = "manual";
    public static final String SKIP_UNDO_AUTO = "auto";
    public static final String SKIP_UNDO_OFF = "off";

    // Legacy shapes of PREF_KEY_LANGUAGE_AUDIO, still read once by migrateLanguageAudio.
    private static final String TRACK_DEFAULT = "default";
    private static final String TRACK_DEVICE = "device";

    final Context mContext;
    final SharedPreferences mSharedPreferences;

    public Uri mediaUri;
    // Set for a launch that brought no media of its own: the remembered clip stays on disk
    // (the picker starts there, its position is kept) but the player must not resume it by
    // itself. Cleared by updateMedia, i.e. as soon as any media is actually opened.
    public boolean suppressResume;
    public Uri subtitleUri;
    public Uri scopeUri;
    public String mediaType;
    public int resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT;
    // VIDEO from the start: UNSPECIFIED is a no-op in Utils.setOrientation, so defaulting to it left the
    // first-ever launch following the device until the first STATE_READY upgraded it to VIDEO anyway.
    public Utils.Orientation orientation = Utils.Orientation.VIDEO;
    public float scale = 1.f;
    public float aspectRatio = 0f; // 0 = natural video AR; >0 = forced display AR (16:9, 4:3, …)
    public float speed = 1.f;

    public String subtitleTrackId;
    public String audioTrackId;

    public int brightness = -1;
    // The player's own volume, only used while systemVolume is off (see Utils.applyPlayerVolume)
    public int volume = 100;
    public boolean firstRun = true;
    public boolean askScope = true;
    // Set while we have turned the device's own auto-rotate on for a system picker, so that a process death
    // with the picker still open cannot leave the whole phone rotating — see PlayerActivity.enableRotation.
    public boolean restoreAutoRotate = false;
    public boolean autoPiP = false;
    // Off means the vertical swipes work as they always have; on takes them away entirely.
    public boolean disableVolumeBrightnessGestures = false;
    // Off takes the whole hold gesture away: a long press on the picture then does nothing at all.
    public boolean holdSpeed = true;

    public boolean tunneling = false;
    public boolean frameRateMatching = false;
    public boolean allowSystemFrameRate = true;
    public boolean repeatToggle = false;
    public String fileAccess = "auto";
    public int decoderPriority = DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON;
    public boolean mapDV7ToHevc = false;
    // Preferred audio languages, most wanted first: comma-separated ISO-639-2/T codes ("ukr,eng").
    // Empty means no preference at all, i.e. whatever the media itself puts first.
    public String languageAudio = "";
    // Preferred subtitle languages, same shape as languageAudio. Empty means no preference, which is
    // what every install starts from: unlike audio, a subtitle nobody asked for is in the way.
    public String languageSubtitle = "";
    // Look for a missing subtitle language online. Off by default: it sends what is being watched,
    // by id, to third-party services, which is not something to start doing on a user's behalf.
    public boolean subtitleSearch = false;
    // false: search whenever the top language is missing, walking down the list. true: only when the
    // media carries none of the preferred languages at all.
    public boolean subtitleSearchStrict = false;
    // Ask which language a manual search is for, seeded from the list above. Off by default: the
    // priority list is already the answer, and a step that only ever gets confirmed is a step.
    public boolean subtitleSearchLanguage = false;
    // Machine-translate into the wanted language when no track exists in it. Which language it is
    // translated from is not a setting: it follows from the wanted one (SubtitleTranslate.sourcesFor).
    public boolean subtitleTranslate = true;
    // Keep the source line under the translated one. The only way a viewer can judge a machine
    // rendering at all, so it is worth the two lines of screen it costs.
    public boolean subtitleTranslateOriginal = false;
    // Translation endpoints to try, in order: comma-separated ids from SubtitleTranslate. Editable
    // because they are strangers' free services and three of five died within a fortnight.
    public String subtitleTranslateBackends = SubtitleTranslate.DEFAULT_BACKENDS;
    // Tried in this order until one has the wanted language; see SubtitleSearch.
    public boolean subtitleSourceOpenSubtitles = true;
    public boolean subtitleSourceShegu = true;
    public boolean subtitleSourceStremio = true;
    public boolean subtitleSourceRest = true;
    public boolean subtitleStyleEmbedded = true;
    public boolean subtitleStyleBold = false;
    // How subtitles look. Owned here since the app stopped reading the system captioning screen: it
    // named a language too, and one language belongs in one place (see getLanguageSubtitle).
    // The scale keeps the five steps that screen had, so normalizeFontScale still does the mapping.
    public float subtitleScale = 1.0f;
    public int subtitleTextColor = Color.WHITE;
    public int subtitleBackgroundColor = Color.TRANSPARENT;
    public int subtitleEdgeType = CaptionStyleCompat.EDGE_TYPE_OUTLINE;
    public boolean skipEnabled = true;
    public String skipMode = SKIP_MODE_BRIEF;
    public String skipModeCredits = SKIP_MODE_BRIEF;
    public boolean skipHideWhenLocked = false;
    public boolean skipFetchOnline = true;
    public String skipUndo = SKIP_UNDO_ALL;
    public boolean showClock = false;
    /** Bottom bar counts down what is left instead of showing the total duration. */
    public boolean timeRemaining = false;
    public boolean showStats = false;
    public boolean systemVolume = true;
    /** How other people in a watch-together room see this device. Generated once, then editable. */
    public String togetherNick = "";
    /** Password put on rooms this device creates. Empty means anyone with the code walks in. */
    public String togetherPassword = "";
    /** Whether rooms this device creates announce themselves for anyone to find. Off by default:
     *  being listed means the name, poster and viewer count are readable without ever joining. */
    public boolean togetherPublic = false;
    /** Relay to hold rooms on. Empty means the built-in default, which is also the plugin's. */
    public String togetherRelay = "";
    /** Page an invite link points at. Empty means the built-in default, which is the web player's own. */
    public String togetherInvitePage = "";
    public boolean crashReporting = false;
    public boolean autoUpdate = true;
    public long updateLastCheck = 0L;
    public int updateSkippedVersionCode = 0;
    // Last update the check found, remembered across launches so the button beside the gear is there from
    // the first frame instead of only on the launches where the hourly throttle lets a request through.
    public UpdateInfo updatePending;
    // Audio sample mimes (Format.sampleMimeType, e.g. MimeTypes.AUDIO_DTS) this device has proven
    // cannot passthrough — see PlayerActivity.recoverByRevokingAudioMime(). Never auto-expires;
    // only the "Reset learned audio workarounds" setting or a full app data reset clears it.
    public Set<String> revokedAudioMimes = Collections.emptySet();

    private LinkedHashMap positions;

    public boolean persistentMode = true;
    public long nonPersitentPosition = -1L;

    public Prefs(Context context) {
        mContext = context;
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        loadSavedPreferences();
        relearnRevokedAudioMimes();
        loadPositions();
    }

    /**
     * Clears the learned passthrough denylist once, so entries left by builds whose stall recovery blamed
     * whichever mime happened to be playing do not stay for good. Those entries deny nothing (the track was
     * being decoded, not bitstreamed) but keep the set non-empty, which forces the ffmpeg audio renderer in
     * for "device decoders only" and puts a misleading line in every error report.
     *
     * Nothing here inspects the mimes: no list can separate a bogus entry from a real one, since a device
     * that bitstreams AAC is as plausible as one that bitstreams AC4. Dropping the whole set is
     * self-correcting instead: a mime this device really cannot bitstream is revoked again by its next
     * failure, at the cost of one recoverable hiccup, while a bogus entry can no longer come back now that
     * the stall path checks the sink first.
     */
    private void relearnRevokedAudioMimes() {
        if (mSharedPreferences.getBoolean(PREF_KEY_REVOKED_AUDIO_MIMES_RELEARNED, false)) {
            return;
        }
        revokedAudioMimes = Collections.emptySet();
        mSharedPreferences.edit()
                .remove(PREF_KEY_REVOKED_AUDIO_MIMES)
                .putBoolean(PREF_KEY_REVOKED_AUDIO_MIMES_RELEARNED, true)
                .apply();
    }

    private void loadSavedPreferences() {
        if (mSharedPreferences.contains(PREF_KEY_MEDIA_URI))
            mediaUri = Uri.parse(mSharedPreferences.getString(PREF_KEY_MEDIA_URI, null));
        if (mSharedPreferences.contains(PREF_KEY_MEDIA_TYPE))
            mediaType = mSharedPreferences.getString(PREF_KEY_MEDIA_TYPE, null);
        if (mSharedPreferences.contains(PREF_KEY_BRIGHTNESS_PERCENT)) {
            brightness = mSharedPreferences.getInt(PREF_KEY_BRIGHTNESS_PERCENT, brightness);
        } else {
            final int level = mSharedPreferences.getInt(PREF_KEY_BRIGHTNESS, -1);
            brightness = level < 0 ? -1 : level * 100 / 30;
        }
        volume = mSharedPreferences.getInt(PREF_KEY_VOLUME_PERCENT, volume);
        firstRun = mSharedPreferences.getBoolean(PREF_KEY_FIRST_RUN, firstRun);
        if (mSharedPreferences.contains(PREF_KEY_SUBTITLE_URI))
            subtitleUri = Uri.parse(mSharedPreferences.getString(PREF_KEY_SUBTITLE_URI, null));
        if (mSharedPreferences.contains(PREF_KEY_AUDIO_TRACK_ID))
            audioTrackId = mSharedPreferences.getString(PREF_KEY_AUDIO_TRACK_ID, audioTrackId);
        if (mSharedPreferences.contains(PREF_KEY_SUBTITLE_TRACK_ID))
            subtitleTrackId = mSharedPreferences.getString(PREF_KEY_SUBTITLE_TRACK_ID, subtitleTrackId);
        if (mSharedPreferences.contains(PREF_KEY_RESIZE_MODE))
            resizeMode = mSharedPreferences.getInt(PREF_KEY_RESIZE_MODE, resizeMode);
        orientation = Utils.Orientation.values()[mSharedPreferences.getInt(PREF_KEY_ORIENTATION, orientation.value)];
        scale = mSharedPreferences.getFloat(PREF_KEY_SCALE, scale);
        aspectRatio = mSharedPreferences.getFloat(PREF_KEY_ASPECT_RATIO, aspectRatio);
        if (mSharedPreferences.contains(PREF_KEY_SCOPE_URI))
            scopeUri = Uri.parse(mSharedPreferences.getString(PREF_KEY_SCOPE_URI, null));
        askScope = mSharedPreferences.getBoolean(PREF_KEY_ASK_SCOPE, askScope);
        restoreAutoRotate = mSharedPreferences.getBoolean(PREF_KEY_RESTORE_AUTO_ROTATE, restoreAutoRotate);
        speed = mSharedPreferences.getFloat(PREF_KEY_SPEED, speed);
        updateLastCheck = mSharedPreferences.getLong(PREF_KEY_UPDATE_LAST_CHECK, updateLastCheck);
        updateSkippedVersionCode = mSharedPreferences.getInt(PREF_KEY_UPDATE_SKIPPED, updateSkippedVersionCode);
        updatePending = UpdateInfo.fromJson(mSharedPreferences.getString(PREF_KEY_UPDATE_PENDING, null));
        // A remembered find that has since been installed or skipped is not an offer any more.
        if (updatePending != null && (updatePending.versionCode <= BuildConfig.VERSION_CODE
                || updatePending.versionCode == updateSkippedVersionCode)) {
            updatePending = null;
        }
        loadUserPreferences();
    }

    public void loadUserPreferences() {
        autoPiP = mSharedPreferences.getBoolean(PREF_KEY_AUTO_PIP, autoPiP);
        disableVolumeBrightnessGestures = mSharedPreferences.getBoolean(
                PREF_KEY_DISABLE_VOLUME_BRIGHTNESS_GESTURES, disableVolumeBrightnessGestures);
        holdSpeed = mSharedPreferences.getBoolean(PREF_KEY_HOLD_SPEED, holdSpeed);
        tunneling = mSharedPreferences.getBoolean(PREF_KEY_TUNNELING, tunneling);
        frameRateMatching = mSharedPreferences.getBoolean(PREF_KEY_FRAMERATE_MATCHING, frameRateMatching);
        allowSystemFrameRate = mSharedPreferences.getBoolean(PREF_KEY_ALLOW_SYSTEM_FRAMERATE, !Utils.isTvBox(mContext));
        repeatToggle = mSharedPreferences.getBoolean(PREF_KEY_REPEAT_TOGGLE, repeatToggle);
        fileAccess = mSharedPreferences.getString(PREF_KEY_FILE_ACCESS, fileAccess);
        decoderPriority = Integer.parseInt(mSharedPreferences.getString(PREF_KEY_DECODER_PRIORITY, String.valueOf(decoderPriority)));
        mapDV7ToHevc = mSharedPreferences.getBoolean(PREF_KEY_MAP_DV7, mapDV7ToHevc);
        languageAudio = getLanguageAudio(mContext);
        languageSubtitle = getLanguageSubtitle(mContext);
        final String searchMode = getSubtitleSearchMode(mContext);
        subtitleSearch = !SEARCH_OFF.equals(searchMode);
        subtitleSearchStrict = SEARCH_NONE.equals(searchMode);
        subtitleSearchLanguage = mSharedPreferences.getBoolean(PREF_KEY_SUBTITLE_SEARCH_LANGUAGE, subtitleSearchLanguage);
        final String translateMode = getSubtitleTranslateMode(mContext);
        subtitleTranslate = !TRANSLATE_OFF.equals(translateMode);
        subtitleTranslateOriginal = TRANSLATE_BOTH.equals(translateMode);
        subtitleTranslateBackends = getSubtitleTranslateBackends(mContext);
        subtitleSourceOpenSubtitles = mSharedPreferences.getBoolean(PREF_KEY_SOURCE_OPENSUBTITLES, subtitleSourceOpenSubtitles);
        subtitleSourceShegu = mSharedPreferences.getBoolean(PREF_KEY_SOURCE_SHEGU, subtitleSourceShegu);
        subtitleSourceStremio = mSharedPreferences.getBoolean(PREF_KEY_SOURCE_STREMIO, subtitleSourceStremio);
        subtitleSourceRest = mSharedPreferences.getBoolean(PREF_KEY_SOURCE_REST, subtitleSourceRest);
        subtitleStyleEmbedded = mSharedPreferences.getBoolean(PREF_KEY_SUBTITLE_STYLE_EMBEDDED, subtitleStyleEmbedded);
        subtitleStyleBold = mSharedPreferences.getBoolean(PREF_KEY_SUBTITLE_STYLE_BOLD, subtitleStyleBold);
        subtitleScale = Float.parseFloat(mSharedPreferences.getString(PREF_KEY_SUBTITLE_SCALE, String.valueOf(subtitleScale)));
        subtitleTextColor = Color.parseColor(mSharedPreferences.getString(PREF_KEY_SUBTITLE_TEXT_COLOR, "#FFFFFFFF"));
        subtitleBackgroundColor = Color.parseColor(mSharedPreferences.getString(PREF_KEY_SUBTITLE_BACKGROUND, "#00000000"));
        subtitleEdgeType = Integer.parseInt(mSharedPreferences.getString(PREF_KEY_SUBTITLE_EDGE, String.valueOf(subtitleEdgeType)));
        skipEnabled = mSharedPreferences.getBoolean(PREF_KEY_SKIP_ENABLED, skipEnabled);
        skipMode = mSharedPreferences.getString(PREF_KEY_SKIP_MODE, skipMode);
        skipModeCredits = mSharedPreferences.getString(PREF_KEY_SKIP_MODE_CREDITS, skipModeCredits);
        skipFetchOnline = mSharedPreferences.getBoolean(PREF_KEY_SKIP_FETCH, skipFetchOnline);
        skipUndo = mSharedPreferences.getString(PREF_KEY_SKIP_UNDO, skipUndo);
        skipHideWhenLocked = mSharedPreferences.getBoolean(PREF_KEY_SKIP_HIDE_LOCKED, skipHideWhenLocked);
        showClock = mSharedPreferences.getBoolean(PREF_KEY_SHOW_CLOCK, showClock);
        timeRemaining = mSharedPreferences.getBoolean(PREF_KEY_TIME_REMAINING, timeRemaining);
        showStats = mSharedPreferences.getBoolean(PREF_KEY_SHOW_STATS, showStats);
        // Forced on for TV boxes, where the remote routes volume to the panel or receiver over CEC and
        // only the system stream responds — the setting is hidden there too.
        systemVolume = Utils.isTvBox(mContext) || mSharedPreferences.getBoolean(PREF_KEY_SYSTEM_VOLUME, systemVolume);
        togetherPassword = mSharedPreferences.getString(PREF_KEY_TOGETHER_PASSWORD, togetherPassword);
        togetherPublic = mSharedPreferences.getBoolean(PREF_KEY_TOGETHER_PUBLIC, togetherPublic);
        togetherRelay = mSharedPreferences.getString(PREF_KEY_TOGETHER_RELAY, togetherRelay);
        togetherInvitePage =
                mSharedPreferences.getString(PREF_KEY_TOGETHER_INVITE_PAGE, togetherInvitePage);
        // Generated on first use and persisted, so it stays the same name from one room to the next —
        // and so the settings screen has something to show rather than an empty field.
        togetherNick = mSharedPreferences.getString(PREF_KEY_TOGETHER_NICK, "");
        if (togetherNick.isEmpty()) {
            togetherNick = AliasGenerator.random();
            mSharedPreferences.edit().putString(PREF_KEY_TOGETHER_NICK, togetherNick).apply();
        }
        crashReporting = mSharedPreferences.getBoolean(PREF_KEY_CRASH_REPORTING, crashReporting);
        autoUpdate = mSharedPreferences.getBoolean(PREF_KEY_AUTO_UPDATE, autoUpdate);
        // Defaulting to the field would hand back the stale in-memory set once the key is gone, so
        // "Reset learned audio workarounds" (which removes the key) would not take effect until the
        // process restarted — including for the player rebuild that follows leaving the settings screen.
        revokedAudioMimes = mSharedPreferences.getStringSet(
                PREF_KEY_REVOKED_AUDIO_MIMES, Collections.emptySet());
    }

    public void setLanguageAudio(final String languages) {
        this.languageAudio = languages;
        setLanguageAudio(mContext, languages);
    }

    /**
     * The audio language setting used to hold one value: "default" (no preference), "device" (the
     * system languages) or a single ISO-639-2/T code. It is now an ordered list of codes, so the two
     * legacy keywords are converted once and written back — a stored code is already a valid
     * one-entry list. A missing key is a fresh install, which starts from the device languages, the
     * same tracks "device" used to pick.
     *
     * Every read goes through here, not just the one in loadUserPreferences: the settings screen is
     * exported (ACTION_APPLICATION_PREFERENCES), so it can be the first thing a fresh process opens,
     * with no Prefs instance ever built — and it would otherwise offer "device" as if it were a
     * language, then persist it.
     *
     * Note the list is a snapshot from here on: unlike the old "device", it no longer follows a later
     * change of the device language. That is the point — what the dialog shows is what plays.
     */
    public static String getLanguageAudio(final Context context) {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        final String stored = preferences.getString(PREF_KEY_LANGUAGE_AUDIO, null);
        if (stored != null && !TRACK_DEFAULT.equals(stored) && !TRACK_DEVICE.equals(stored)) {
            return stored;
        }
        final String migrated = TRACK_DEFAULT.equals(stored)
                ? "" : TextUtils.join(",", Utils.getDeviceLanguages());
        preferences.edit().putString(PREF_KEY_LANGUAGE_AUDIO, migrated).apply();
        return migrated;
    }

    public static void setLanguageAudio(final Context context, final String languages) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(PREF_KEY_LANGUAGE_AUDIO, languages).apply();
    }

    /**
     * The subtitle language is picked here and nowhere else. The system captioning screen (still
     * offered, for size and style) also names a language, and that used to be what selected the
     * subtitle track — so a fresh key inherits it once, and from then on this list is the only thing
     * that decides. Empty means no preference at all, like the audio list.
     */
    public static String getLanguageSubtitle(final Context context) {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        final String stored = preferences.getString(PREF_KEY_LANGUAGE_SUBTITLE, null);
        if (stored != null) {
            return stored;
        }
        final CaptioningManager captioningManager =
                (CaptioningManager) context.getSystemService(Context.CAPTIONING_SERVICE);
        final Locale locale = captioningManager == null ? null : captioningManager.getLocale();
        final String inherited = locale == null ? "" : Utils.toIso3Language(locale.getLanguage());
        final String seeded = inherited == null ? "" : inherited;
        preferences.edit().putString(PREF_KEY_LANGUAGE_SUBTITLE, seeded).apply();
        return seeded;
    }

    /**
     * When the online search runs, migrated once from the switch pair it replaced. Called before the
     * settings screen inflates as well as on playback, so the key exists by the time the list
     * preference reads it — otherwise a viewer who had the search on would be shown "never".
     */
    public static String getSubtitleSearchMode(final Context context) {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        final String stored = preferences.getString(PREF_KEY_SUBTITLE_SEARCH_MODE, null);
        if (stored != null) {
            return stored;
        }
        final String migrated;
        if (!preferences.getBoolean(PREF_KEY_SUBTITLE_SEARCH, false)) {
            migrated = SEARCH_OFF;
        } else {
            migrated = preferences.getBoolean(PREF_KEY_SUBTITLE_SEARCH_STRICT, false)
                    ? SEARCH_NONE : SEARCH_FIRST;
        }
        preferences.edit().putString(PREF_KEY_SUBTITLE_SEARCH_MODE, migrated)
                .remove(PREF_KEY_SUBTITLE_SEARCH)
                .remove(PREF_KEY_SUBTITLE_SEARCH_STRICT)
                .apply();
        return migrated;
    }

    /**
     * Whether subtitles are machine-translated, and whether the source line is kept under the result.
     * Migrated once from the switch this replaced, and read before the settings screen inflates for the
     * same reason {@link #getSubtitleSearchMode} is.
     */
    public static String getSubtitleTranslateMode(final Context context) {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        final String stored = preferences.getString(PREF_KEY_SUBTITLE_TRANSLATE_MODE, null);
        if (stored != null) {
            return stored;
        }
        final String migrated = preferences.getBoolean(PREF_KEY_SUBTITLE_TRANSLATE, true)
                ? TRANSLATE_ONLY : TRANSLATE_OFF;
        preferences.edit().putString(PREF_KEY_SUBTITLE_TRANSLATE_MODE, migrated)
                .remove(PREF_KEY_SUBTITLE_TRANSLATE)
                .apply();
        return migrated;
    }

    /** Translation endpoints to try, in order. Unset means the two that were answering when shipped. */
    public static String getSubtitleTranslateBackends(final Context context) {
        final String stored = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(PREF_KEY_SUBTITLE_TRANSLATE_BACKENDS, null);
        // Folded onto the ids that exist now, so a setting written when every Mozhi host was its
        // own entry still means Mozhi rather than nothing.
        return SubtitleTranslate.normalize(
                stored != null ? stored : SubtitleTranslate.DEFAULT_BACKENDS);
    }

    public static void setSubtitleTranslateBackends(final Context context, final String backends) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(PREF_KEY_SUBTITLE_TRANSLATE_BACKENDS, backends).apply();
    }

    public static void setLanguageSubtitle(final Context context, final String languages) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(PREF_KEY_LANGUAGE_SUBTITLE, languages).apply();
    }

    public void updateMedia(final Context context, final Uri uri, final String type) {
        mediaUri = uri;
        mediaType = type;
        suppressResume = false;
        updateSubtitle(null);
        updateMeta(null, null, AspectRatioFrameLayout.RESIZE_MODE_FIT, 1.f, 0f, 1.f);
        // Opening something else drops the in-memory position with the rest of the meta. It is not keyed by
        // uri (see getPosition), so left behind it becomes the start position of the new media: a sender
        // that supplies no "position" extra — most do not — would drop the user into the middle of it.
        nonPersitentPosition = -1L;

        if (mediaType != null && mediaType.endsWith("/*")) {
            mediaType = null;
        }

        if (mediaType == null) {
            // A null uri clears the remembered media (handled by the persist block below).
            if (mediaUri != null && ContentResolver.SCHEME_CONTENT.equals(mediaUri.getScheme())) {
                mediaType = context.getContentResolver().getType(mediaUri);
            }
        }

        if (persistentMode) {
            final SharedPreferences.Editor sharedPreferencesEditor = mSharedPreferences.edit();
            if (mediaUri == null)
                sharedPreferencesEditor.remove(PREF_KEY_MEDIA_URI);
            else
                sharedPreferencesEditor.putString(PREF_KEY_MEDIA_URI, mediaUri.toString());
            if (mediaType == null)
                sharedPreferencesEditor.remove(PREF_KEY_MEDIA_TYPE);
            else
                sharedPreferencesEditor.putString(PREF_KEY_MEDIA_TYPE, mediaType);
            sharedPreferencesEditor.apply();
        }
    }

    public void updateSubtitle(final Uri uri) {
        subtitleUri = uri;
        subtitleTrackId = null;
        if (persistentMode) {
            final SharedPreferences.Editor sharedPreferencesEditor = mSharedPreferences.edit();
            if (uri == null)
                sharedPreferencesEditor.remove(PREF_KEY_SUBTITLE_URI);
            else
                sharedPreferencesEditor.putString(PREF_KEY_SUBTITLE_URI, uri.toString());
            sharedPreferencesEditor.remove(PREF_KEY_SUBTITLE_TRACK_ID);
            sharedPreferencesEditor.apply();
        }
    }

    public void updatePosition(final long position) {
        if (mediaUri == null)
            return;

        while (positions.size() > 100)
            positions.remove(positions.keySet().toArray()[0]);

        if (persistentMode) {
            positions.put(mediaUri.toString(), position);
            savePositions();
        } else {
            nonPersitentPosition = position;
        }
    }

    public void updateBrightness(final int brightness) {
        if (brightness >= -1) {
            this.brightness = brightness;
            final SharedPreferences.Editor sharedPreferencesEditor = mSharedPreferences.edit();
            sharedPreferencesEditor.putInt(PREF_KEY_BRIGHTNESS_PERCENT, brightness);
            sharedPreferencesEditor.apply();
        }
    }

    public void updateVolume(final int volume) {
        this.volume = volume;
        final SharedPreferences.Editor sharedPreferencesEditor = mSharedPreferences.edit();
        sharedPreferencesEditor.putInt(PREF_KEY_VOLUME_PERCENT, volume);
        sharedPreferencesEditor.apply();
    }

    /** Asked when a room is created, remembered as the next one's default. */
    public void updateTogetherPublic(final boolean value) {
        this.togetherPublic = value;
        mSharedPreferences.edit().putBoolean(PREF_KEY_TOGETHER_PUBLIC, value).apply();
    }

    public void markFirstRun() {
        this.firstRun = false;
        final SharedPreferences.Editor sharedPreferencesEditor = mSharedPreferences.edit();
        sharedPreferencesEditor.putBoolean(PREF_KEY_FIRST_RUN, false);
        sharedPreferencesEditor.apply();
    }

    /**
     * commit, not apply: the flag exists precisely so that a process killed with a system picker still open
     * can put the device's auto-rotate back on the next launch, and an apply() queued behind that kill would
     * be the one write we cannot afford to lose.
     */
    public void setRestoreAutoRotate(final boolean restore) {
        this.restoreAutoRotate = restore;
        final SharedPreferences.Editor sharedPreferencesEditor = mSharedPreferences.edit();
        sharedPreferencesEditor.putBoolean(PREF_KEY_RESTORE_AUTO_ROTATE, restore);
        sharedPreferencesEditor.commit();
    }

    public void markScopeAsked() {
        this.askScope = false;
        final SharedPreferences.Editor sharedPreferencesEditor = mSharedPreferences.edit();
        sharedPreferencesEditor.putBoolean(PREF_KEY_ASK_SCOPE, false);
        sharedPreferencesEditor.apply();
    }

    public void setUpdateLastCheck(final long timestamp) {
        this.updateLastCheck = timestamp;
        final SharedPreferences.Editor sharedPreferencesEditor = mSharedPreferences.edit();
        sharedPreferencesEditor.putLong(PREF_KEY_UPDATE_LAST_CHECK, timestamp);
        sharedPreferencesEditor.apply();
    }

    public void setUpdateSkippedVersionCode(final int versionCode) {
        this.updateSkippedVersionCode = versionCode;
        final SharedPreferences.Editor sharedPreferencesEditor = mSharedPreferences.edit();
        sharedPreferencesEditor.putInt(PREF_KEY_UPDATE_SKIPPED, versionCode);
        sharedPreferencesEditor.apply();
    }

    public void setUpdatePending(final UpdateInfo info) {
        this.updatePending = info;
        final SharedPreferences.Editor sharedPreferencesEditor = mSharedPreferences.edit();
        if (info == null) {
            sharedPreferencesEditor.remove(PREF_KEY_UPDATE_PENDING);
        } else {
            sharedPreferencesEditor.putString(PREF_KEY_UPDATE_PENDING, info.toJson());
        }
        sharedPreferencesEditor.apply();
    }

    /**
     * Turns tunneled playback off after this device has proven it freezes with it — see
     * PlayerActivity.recoverByDisablingTunneling(). Writes the same key the settings switch uses, so the
     * switch itself goes off: the user can see what happened and turn it back on if they want to.
     */
    public void disableTunneling() {
        tunneling = false;
        mSharedPreferences.edit().putBoolean(PREF_KEY_TUNNELING, false).apply();
    }

    public void revokeAudioMime(final String mime) {
        final Set<String> updated = new HashSet<>(revokedAudioMimes);
        updated.add(mime);
        revokedAudioMimes = updated;
        final SharedPreferences.Editor sharedPreferencesEditor = mSharedPreferences.edit();
        sharedPreferencesEditor.putStringSet(PREF_KEY_REVOKED_AUDIO_MIMES, updated);
        sharedPreferencesEditor.apply();
    }

    public static void resetRevokedAudioMimes(final Context context) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .remove(PREF_KEY_REVOKED_AUDIO_MIMES).apply();
    }

    private void savePositions() {
        try {
            FileOutputStream fos = mContext.openFileOutput("positions", Context.MODE_PRIVATE);
            ObjectOutputStream os = new ObjectOutputStream(fos);
            os.writeObject(positions);
            os.close();
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadPositions() {
        try {
            FileInputStream fis = mContext.openFileInput("positions");
            ObjectInputStream is = new ObjectInputStream(fis);
            positions = (LinkedHashMap) is.readObject();
            is.close();
            fis.close();
        } catch (Exception e) {
            e.printStackTrace();
            positions = new LinkedHashMap(10);
        }
    }

    public long getPosition() {
        if (!persistentMode) {
            return nonPersitentPosition;
        }

        Object val = positions.get(mediaUri.toString());
        if (val != null)
            return (long) val;

        // Return position for uri from limited scope (loaded after using Next action)
        final String searchId = documentIdentity(mediaUri);
        if (searchId != null) {
            final Object[] keys = positions.keySet().toArray();
            for (int i = keys.length; i > 0; i--) {
                final String key = (String) keys[i - 1];
                if (searchId.equals(documentIdentity(Uri.parse(key)))) {
                    return (long) positions.get(key);
                }
            }
        }

        return 0L;
    }

    // How two uris for one document compare. The picker hands out .../document/<id> and the folder
    // walk hands out .../tree/<tree>/document/<id> for the same file: same authority, same document
    // id, different string. The id is what has to match — the tail of the path alone drops the
    // storage volume, which is the only thing telling Movies/1.mkv on a memory card apart from
    // Movies/1.mkv in internal storage. Null for anything that is not a document uri.
    private static String documentIdentity(final Uri uri) {
        if (!ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
            return null;
        }
        try {
            return uri.getAuthority() + '/' + DocumentsContract.getDocumentId(uri);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public void updateOrientation() {
        final SharedPreferences.Editor sharedPreferencesEditor = mSharedPreferences.edit();
        sharedPreferencesEditor.putInt(PREF_KEY_ORIENTATION, orientation.value);
        sharedPreferencesEditor.apply();
    }

    public void updateMeta(final String audioTrackId, final String subtitleTrackId, final int resizeMode, final float scale, final float aspectRatio, final float speed) {
        this.audioTrackId = audioTrackId;
        this.subtitleTrackId = subtitleTrackId;
        this.resizeMode = resizeMode;
        this.scale = scale;
        this.aspectRatio = aspectRatio;
        this.speed = speed;
        if (persistentMode) {
            final SharedPreferences.Editor sharedPreferencesEditor = mSharedPreferences.edit();
            if (audioTrackId == null)
                sharedPreferencesEditor.remove(PREF_KEY_AUDIO_TRACK_ID);
            else
                sharedPreferencesEditor.putString(PREF_KEY_AUDIO_TRACK_ID, audioTrackId);
            if (subtitleTrackId == null)
                sharedPreferencesEditor.remove(PREF_KEY_SUBTITLE_TRACK_ID);
            else
                sharedPreferencesEditor.putString(PREF_KEY_SUBTITLE_TRACK_ID, subtitleTrackId);
            sharedPreferencesEditor.putInt(PREF_KEY_RESIZE_MODE, resizeMode);
            sharedPreferencesEditor.putFloat(PREF_KEY_SCALE, scale);
            sharedPreferencesEditor.putFloat(PREF_KEY_ASPECT_RATIO, aspectRatio);
            sharedPreferencesEditor.putFloat(PREF_KEY_SPEED, speed);
            sharedPreferencesEditor.apply();
        }
    }

    public void updateScope(final Uri uri) {
        scopeUri = uri;
        final SharedPreferences.Editor sharedPreferencesEditor = mSharedPreferences.edit();
        if (uri == null)
            sharedPreferencesEditor.remove(PREF_KEY_SCOPE_URI);
        else
            sharedPreferencesEditor.putString(PREF_KEY_SCOPE_URI, uri.toString());
        sharedPreferencesEditor.apply();
    }

    public void setPersistent(boolean persistentMode) {
        this.persistentMode = persistentMode;
    }

    // Everything the settings screen could have written, in one comparable value: the player bakes some
    // of these in at build time, so the caller can tell "settings were changed" from "settings were only
    // looked at" without keeping a hand-written list of the keys that matter.
    /**
     * What the player screen compares before and after a trip to settings, to decide whether it has
     * to be rebuilt. Room settings are left out on purpose: they change nothing about how playback
     * is built, and rebuilding for them would restart the film over a change of display name.
     */
    public Map<String, ?> snapshot() {
        final Map<String, Object> all = new HashMap<>(mSharedPreferences.getAll());
        all.remove(PREF_KEY_TOGETHER_NICK);
        all.remove(PREF_KEY_TOGETHER_PASSWORD);
        all.remove(PREF_KEY_TOGETHER_PUBLIC);
        all.remove(PREF_KEY_TOGETHER_RELAY);
        return all;
    }
}