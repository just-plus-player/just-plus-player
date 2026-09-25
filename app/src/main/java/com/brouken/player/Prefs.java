package com.brouken.player;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.view.accessibility.CaptioningManager;

import com.brouken.player.together.AliasGenerator;
import android.preference.PreferenceManager;
import android.provider.DocumentsContract;
import android.text.TextUtils;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.media3.common.MimeTypes;
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
    private static final String PREF_KEY_SUBTITLE_SECONDARY_URI = "subtitleSecondaryUri";

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
    /** Settings-screen appearance. The player and the error screen are dark by design, so these two
     *  reach only the settings window. */
    public static final String THEME_MODE_KEY = "themeMode";
    private static final String PREF_KEY_AMOLED = "amoledBlack";
    public static final String THEME_DARK = "dark";
    public static final String THEME_LIGHT = "light";
    public static final String THEME_SYSTEM = "system";
    /** The accent, as one of the {@link Accent} overlays; reaches every window. */
    public static final String ACCENT_KEY = "accentTheme";
    public static final String ACCENT_CORAL = "coral";
    private static final String PREF_KEY_HOLD_SPEED_MODE = "holdSpeedMode";
    private static final String PREF_KEY_TUNNELING = "tunneling";
    private static final String PREF_KEY_FRAMERATE_MATCHING = "frameRateMatching";
    private static final String PREF_KEY_BACK_BUFFER_MS = "backBufferMs";
    private static final String PREF_KEY_DISPLAY_RESOLUTION_MATCHING = "displayResolutionMatching";
    private static final String PREF_KEY_ALLOW_SYSTEM_FRAMERATE = "allowSystemFrameRate";
    private static final String PREF_KEY_REPEAT_TOGGLE = "repeatToggle";
    private static final String PREF_KEY_PLAYLIST_GRID = "playlistGrid";
    private static final String PREF_KEY_TV_SINGLE_BACK = "tvSingleBack";
    private static final String PREF_KEY_KEEP_AWAKE_ON_PAUSE = "keepAwakeOnPause";
    private static final String PREF_KEY_AUDIO_PASSTHROUGH = "audioPassthrough";
    private static final String PREF_KEY_SPEED = "speed";
    private static final String PREF_KEY_BROWSE_TRAIL = "browseTrail";
    private static final String PREF_KEY_BROWSE_DEST = "browseDest";
    private static final String PREF_KEY_DECODER_PRIORITY = "decoderPriority";
    private static final String PREF_KEY_AUDIO_SYNC_MS = "audioSyncMs";
    private static final String PREF_KEY_AUDIO_PASSTHROUGH_SYNC_MS = "audioPassthroughSyncMs";
    private static final String PREF_KEY_AUDIO_PASSTHROUGH_FORCE = "audioPassthroughForce";
    private static final String PREF_KEY_CENTRE_BOOST = "centreBoost";
    private static final String PREF_KEY_DYNAMIC_RANGE = "dynamicRange";
    private static final String PREF_KEY_MAP_DV7 = "mapDV7ToHevc";
    private static final String PREF_KEY_REMOVE_HDR10_PLUS = "removeHdr10Plus";
    private static final String PREF_KEY_REFUSE_DOLBY_VISION = "refuseDolbyVision";
    private static final String PREF_KEY_LANGUAGE_AUDIO = "languageAudio";
    private static final String PREF_KEY_LANGUAGE_SUBTITLE = "languageSubtitle";
    private static final String PREF_KEY_LANGUAGE_SUBTITLE_SECONDARY = "languageSubtitleSecondary";
    // Online subtitle search, all of it behind one row on the settings screen. These used to be
    // checkboxes inside the language-priority dialog, which hid the feature behind a row that never
    // mentions it while leaving its lesser options in plain sight.
    private static final String PREF_KEY_SUBTITLE_SEARCH_MODE = "subtitleSearchMode";
    // Replaced by the mode above; still read once, to carry an existing choice over.
    private static final String PREF_KEY_SUBTITLE_SEARCH = "subtitleSearch";
    private static final String PREF_KEY_SUBTITLE_SEARCH_STRICT = "subtitleSearchStrict";
    private static final String PREF_KEY_SUBTITLE_SEARCH_LANGUAGE = "subtitleSearchLanguage";
    private static final String PREF_KEY_SUBTITLE_TRANSLATE_ON = "subtitleTranslateOn";
    private static final String PREF_KEY_SUBTITLE_TRANSLATE_BACKENDS = "subtitleTranslateBackends";
    // Both shapes the setting had before the switch above; each is read once by getSubtitleTranslate
    // to carry an existing choice over, then removed.
    private static final String PREF_KEY_SUBTITLE_TRANSLATE_MODE = "subtitleTranslateMode";
    private static final String PREF_KEY_SUBTITLE_TRANSLATE = "subtitleTranslate";
    // One per source, so a single one can be exercised on its own when something looks wrong.
    private static final String PREF_KEY_SOURCE_OPENSUBTITLES = "subtitleSourceOpenSubtitles";
    private static final String PREF_KEY_SOURCE_SHEGU = "subtitleSourceShegu";
    private static final String PREF_KEY_SOURCE_STREMIO = "subtitleSourceStremio";
    private static final String PREF_KEY_SOURCE_REST = "subtitleSourceRest";
    private static final String PREF_KEY_SUBTITLE_STYLE_BOLD = "subtitleStyleBold";
    private static final String PREF_KEY_SUBTITLE_SCALE = "subtitleScale";
    private static final String PREF_KEY_SUBTITLE_SECONDARY_MODE = "subtitleSecondaryMode";
    private static final String PREF_KEY_SUBTITLE_SECONDARY_SCALE = "subtitleSecondaryScale";
    private static final String PREF_KEY_SUBTITLE_TEXT_COLOR = "subtitleTextColor";
    private static final String PREF_KEY_SUBTITLE_BACKGROUND = "subtitleBackground";
    private static final String PREF_KEY_SUBTITLE_SECONDARY_TEXT_COLOR = "subtitleSecondaryTextColor";
    private static final String PREF_KEY_SUBTITLE_SECONDARY_BACKGROUND = "subtitleSecondaryBackground";
    private static final String PREF_KEY_SUBTITLE_EDGE = "subtitleEdge";
    private static final String PREF_KEY_SKIP_ENABLED = "skipEnabled";
    private static final String PREF_KEY_SKIP_MODE = "skipMode";
    private static final String PREF_KEY_SKIP_MODE_CREDITS = "skipModeCredits";
    private static final String PREF_KEY_SKIP_FETCH = "skipFetchOnline";
    private static final String PREF_KEY_SKIP_UNDO = "skipUndo";
    private static final String PREF_KEY_SKIP_HIDE_LOCKED = "skipHideWhenLocked";
    private static final String PREF_KEY_SKIP_COUNTDOWN = "skipCountdown";
    private static final String PREF_KEY_SHOW_CLOCK = "showClock";
    private static final String PREF_KEY_TIME_REMAINING = "timeRemaining";
    private static final String PREF_KEY_SHOW_STATS = "showStats";
    private static final String PREF_KEY_SHOW_TRANSFER = "showTransfer";
    private static final String PREF_KEY_SYSTEM_VOLUME = "systemVolume";
    private static final String PREF_KEY_TOGETHER_NICK = "togetherNick";
    private static final String PREF_KEY_TOGETHER_PASSWORD = "togetherPassword";
    private static final String PREF_KEY_TOGETHER_PUBLIC = "togetherPublic";
    private static final String PREF_KEY_TOGETHER_RELAY = "togetherRelay";
    private static final String PREF_KEY_TOGETHER_INVITE_PAGE = "togetherInvitePage";
    private static final String PREF_KEY_CRASH_REPORTING = "crashReporting";
    static final String PREF_KEY_MASK_REPORTS = "maskReports";
    private static final String PREF_KEY_AUTO_UPDATE = "autoUpdate";
    private static final String PREF_KEY_UPDATE_LAST_CHECK = "updateLastCheck";
    private static final String PREF_KEY_UPDATE_SKIPPED = "updateSkippedVersionCode";
    private static final String PREF_KEY_UPDATE_PENDING = "updatePending";
    private static final String PREF_KEY_REVOKED_AUDIO_MIMES = "revokedAudioMimes";
    private static final String PREF_KEY_REVOKED_AUDIO_MIMES_RELEARNED = "revokedAudioMimesRelearned3";

    // How a skippable segment is offered. BRIEF shows the Skip button for PlayerActivity.SKIP_NOTICE_MS and
    // then leaves the picture alone; the option's name says "5 seconds", so that constant and the
    // pref_skip_mode_brief strings have to move together.
    public static final String SKIP_MODE_BRIEF = "brief";
    public static final String SKIP_MODE_BUTTON = "button";
    public static final String SKIP_MODE_AUTO = "auto";
    /**
     * Offer nothing: the session's mute for segments. Only ever a choice made in the player's skip
     * panel — deliberately not in the settings list, where switching skipping off is a switch for the
     * whole feature rather than one film's worth of it.
     */
    public static final String SKIP_MODE_OFF = "off";
    /** How the pill shows the time it has left, if at all. */
    public static final String SKIP_COUNTDOWN_RING = "ring";
    public static final String SKIP_COUNTDOWN_OFF = "off";

    // Which skips offer the "go back" pill afterwards.
    // When the online subtitle search runs. One choice, because the two switches this replaced were
    // not independent: the second meant nothing while the first was off.
    public static final String HOLD_SPEED_OFF = "off";
    public static final String HOLD_SPEED_ADJUST = "adjust";
    public static final String HOLD_SPEED_FIXED = "fixed";

    public static final String SEARCH_OFF = "off";
    public static final String SEARCH_FIRST = "first";
    public static final String SEARCH_NONE = "none";

    // When the second subtitle line is drawn. "Off" is the whole feature, not just the line: no row in
    // the subtitle picker, nothing found for it, no band under the first line.
    public static final String SECONDARY_OFF = "off";
    public static final String SECONDARY_ALWAYS = "always";
    public static final String SECONDARY_DEMAND = "demand";

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
    // The second line's file. Remembered next to the first one and cleared with it when the media
    // changes: a hint belongs to the film it was chosen for.
    public Uri subtitleSecondaryUri;
    public Uri scopeUri;
    public String mediaType;
    public int resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT;
    // VIDEO from the start: UNSPECIFIED is a no-op in Utils.setOrientation, so defaulting to it left the
    // first-ever launch following the device until the first STATE_READY upgraded it to VIDEO anyway.
    public Utils.Orientation orientation = Utils.Orientation.VIDEO;
    public float scale = 1.f;
    public float aspectRatio = 0f; // 0 = natural video AR; >0 = forced display AR (16:9, 4:3, …)
    // Which shape of picture the three fields above were chosen for, -1 until the video reports one.
    // The frame mode is remembered per shape, not once for everything: cropping a scope film to fill
    // the screen says nothing about how a 4:3 recording should sit. See aspectClassOf.
    public int aspectClass = -1;
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
    // What a long press on the picture does: nothing at all, a fixed 2x, or 2x that the same finger
    // then drags sideways to change.
    public String holdSpeedMode = HOLD_SPEED_ADJUST;

    public boolean tunneling = false;
    public boolean frameRateMatching = false;
    // How much played media to keep behind the playhead, in milliseconds, so that a step back lands in
    // memory instead of in a new request. Streams only — see initializePlayer.
    //
    // Five seconds by default, and the number is small on purpose: the cost is the bitrate times the
    // time, and this player is aimed at boxes with a few hundred megabytes of heap playing remuxes of
    // a hundred gigabytes. At 16 Mbps five seconds is 10 MB; at the 110 Mbps such a remux runs to it is
    // 69 MB, and it is charged to the same allocator the forward buffer draws on, so it is taken out of
    // what plays ahead. Enough for the rewind people actually make — the one where a line was missed
    // — and the viewer on a box that cannot spare even that turns it off.
    public int backBufferMs = 5_000;
    // Whether the display may also change resolution for the video, not only refresh rate. Off by
    // default like the reference: a resolution change is a longer black screen than a rate change, and
    // on a television that has already been set to 4K it buys nothing.
    public boolean displayResolutionMatching = false;
    public boolean allowSystemFrameRate = true;
    public boolean repeatToggle = false;
    /** The playlist panel draws frames in a grid rather than a row per file. Off is the row per file. */
    /**
     * Frames rather than rows. On by default since the playlist became a rail standing on the progress
     * bar: a rail is a row of pictures, and a column of names laid along the foot of the picture is the
     * panel again, in the wrong place. Still a choice — a folder whose files are told apart by their
     * names rather than by what is in them is better read as a list.
     */
    public boolean playlistGrid = true;
    public boolean tvSingleBack = false;
    public boolean keepAwakeOnPause = true;
    // Whether compressed surround (Dolby, DTS) may be bitstreamed to the receiver. Off by default: every
    // track is decoded in the player to PCM (see PlayerActivity's audio sink), multichannel included -
    // only the compressed bitstream path is refused. That path is the whole fragile chain on a box whose
    // HDMI audio route drops the bitstream across a pause, and the reference players decode by default
    // for the same reason. On is for a receiver that should render Atmos, DTS:X or lossless itself.
    public boolean audioPassthrough = false;
    // Whether the encodings the receiver takes are declared by this app instead of asked of the route.
    // A box that under-reports its HDMI route — and they do — leaves a bitstream the receiver would
    // have rendered decoded in the player instead, with no way to say otherwise. On, the five standard
    // bitstream encodings are offered whatever the route claims, and a refusal is learnt from the
    // failure rather than predicted (see AudioPassthroughDenylistSink).
    public boolean audioPassthroughForce = false;
    // Whether the centre channel, where a film keeps its dialogue, is lifted above the rest. On/off as
    // in the reference player: how far to lift is not a question to put to the viewer, since the answer
    // depends on whether the route carries the channels or folds them into stereo, and that is known
    // here and not there. Decoded audio only — a bitstream belongs to the receiver, and a stereo
    // track has no discrete centre to lift.
    public boolean centreBoost = false;
    // Whether the loud is pulled down towards the quiet, so a film can be watched at night without the
    // volume control. On/off as in the reference player, which keeps three shapes behind its own switch;
    // which of ours is used is decided by the route, like the dialogue lift. Decoded audio only.
    public boolean dynamicRange = false;
    public int decoderPriority = DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON;
    // Milliseconds the sound is moved against the picture, positive meaning later. Two of them:
    // a receiver decoding a bitstream adds a latency the app decoding it does not.
    public int audioSyncMs = 0;
    public int audioPassthroughSyncMs = 0;
    public boolean mapDV7ToHevc = false;
    // Whether HDR10+ metadata is dropped from a Dolby Vision track while it is being converted, so the
    // display is handed one set of dynamic metadata rather than two. The reference player does this by
    // default; here the default is the other way about, because a display that picks HDR10+ over Dolby
    // Vision is not obviously picking wrong, and issue #166 is a viewer asking for that choice rather
    // than against it. Off, nothing is removed and the display decides.
    public boolean removeHdr10Plus = false;
    // Whether Dolby Vision is kept away from the decoder altogether, every profile of it, so the base
    // layer plays as ordinary HEVC. For a television that renders Dolby Vision badly, or a box whose DV
    // decoder wedges — the reference player has the same switch and clears its whole DV mask with it.
    public boolean refuseDolbyVision = false;
    // Preferred audio languages, most wanted first: comma-separated ISO-639-2/T codes ("ukr,eng").
    // Empty means no preference at all, i.e. whatever the media itself puts first.
    public String languageAudio = "";
    // Preferred subtitle languages, same shape as languageAudio. Empty means no preference, which is
    // what every install starts from: unlike audio, a subtitle nobody asked for is in the way.
    public String languageSubtitle = "";
    // The same list again, for the second line. Its own, because the two lines want opposite things:
    // the first is the language being learned, the second the one already known. Empty — the default —
    // means no second line unless one is picked by hand.
    public String languageSubtitleSecondary = "";
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
    // Translation endpoints to try, in order: comma-separated ids from SubtitleTranslate. Editable
    // because they are strangers' free services and three of five died within a fortnight.
    public String subtitleTranslateBackends = SubtitleTranslate.DEFAULT_BACKENDS;
    // Tried in this order until one has the wanted language; see SubtitleSearch.
    public boolean subtitleSourceOpenSubtitles = true;
    public boolean subtitleSourceShegu = true;
    public boolean subtitleSourceStremio = true;
    public boolean subtitleSourceRest = true;
    public boolean subtitleStyleBold = false;
    // How subtitles look. Owned here since the app stopped reading the system captioning screen: it
    // named a language too, and one language belongs in one place (see getLanguageSubtitle).
    // The scale keeps the five steps that screen had, so normalizeFontScale still does the mapping.
    public float subtitleScale = 1.0f;
    public int subtitleTextColor = Color.WHITE;
    public int subtitleBackgroundColor = Color.TRANSPARENT;
    public int subtitleEdgeType = CaptionStyleCompat.EDGE_TYPE_OUTLINE;
    // The second line's own three. Dimmed text on a translucent plate is what tells a hint apart from
    // the line being read, so the defaults are the whole setting for anyone who never opens this
    // screen. All three come from the same lists the main line uses, and the size defaults to matching
    // it: a hint set smaller by decree reads as harder to read rather than as secondary, and whether
    // the two lines should differ is the viewer's call, not this file's.
    // Defaults to what the second line did before it had a mode: shown for as long as one is chosen.
    public String subtitleSecondaryMode = SECONDARY_ALWAYS;
    public int subtitleSecondaryTextColor = 0xFFCCCCCC;
    public int subtitleSecondaryBackgroundColor = 0x80000000;
    public float subtitleSecondaryScale = 1.0f;
    public boolean skipEnabled = true;
    public String skipMode = SKIP_MODE_BRIEF;
    public String skipModeCredits = SKIP_MODE_BRIEF;
    public boolean skipHideWhenLocked = false;
    /** Whether the pill draws the time it has left: the seconds in its label, and the bar under it. */
    public String skipCountdown = SKIP_COUNTDOWN_RING;
    public boolean skipFetchOnline = true;
    public String skipUndo = SKIP_UNDO_ALL;
    public boolean showClock = false;
    /** Bottom bar counts down what is left instead of showing the total duration. */
    public boolean timeRemaining = false;
    public boolean showStats = false;
    /** Buffer, network and bitrate on one line above the seek bar, apart from the stats panel. */
    public boolean showTransfer = false;
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
    // Off prints the whole media link into the reports the person sends by hand; see Utils.reportUri.
    public boolean maskReports = true;
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
     * whichever mime happened to be playing do not stay for good. Nothing writes the list any more — a
     * failed AudioTrack now falls back for the current run only (PlayerActivity.recoverByRevokingAudioMime),
     * because a field trace showed a transient HDMI route loss taking AC-3 bitstreaming away from a box for
     * good. The key this one-shot is remembered under was bumped with that change, so every device gets one
     * clean slate; after it the list stays empty unless an older build filled it. Those entries deny nothing (the track was
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
        if (mSharedPreferences.contains(PREF_KEY_SUBTITLE_SECONDARY_URI))
            subtitleSecondaryUri = Uri.parse(
                    mSharedPreferences.getString(PREF_KEY_SUBTITLE_SECONDARY_URI, null));
        if (mSharedPreferences.contains(PREF_KEY_AUDIO_TRACK_ID))
            audioTrackId = mSharedPreferences.getString(PREF_KEY_AUDIO_TRACK_ID, audioTrackId);
        if (mSharedPreferences.contains(PREF_KEY_SUBTITLE_TRACK_ID))
            subtitleTrackId = mSharedPreferences.getString(PREF_KEY_SUBTITLE_TRACK_ID, subtitleTrackId);
        orientation = Utils.Orientation.values()[mSharedPreferences.getInt(PREF_KEY_ORIENTATION, orientation.value)];
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
        holdSpeedMode = getHoldSpeedMode(mContext);
        tunneling = mSharedPreferences.getBoolean(PREF_KEY_TUNNELING, tunneling);
        frameRateMatching = mSharedPreferences.getBoolean(PREF_KEY_FRAMERATE_MATCHING, frameRateMatching);
        backBufferMs = Integer.parseInt(mSharedPreferences.getString(PREF_KEY_BACK_BUFFER_MS,
                String.valueOf(backBufferMs)));
        displayResolutionMatching = mSharedPreferences.getBoolean(PREF_KEY_DISPLAY_RESOLUTION_MATCHING,
                displayResolutionMatching);
        allowSystemFrameRate = mSharedPreferences.getBoolean(PREF_KEY_ALLOW_SYSTEM_FRAMERATE, !Utils.isTvBox(mContext));
        repeatToggle = mSharedPreferences.getBoolean(PREF_KEY_REPEAT_TOGGLE, repeatToggle);
        playlistGrid = mSharedPreferences.getBoolean(PREF_KEY_PLAYLIST_GRID, playlistGrid);
        tvSingleBack = mSharedPreferences.getBoolean(PREF_KEY_TV_SINGLE_BACK, tvSingleBack);
        keepAwakeOnPause = mSharedPreferences.getBoolean(PREF_KEY_KEEP_AWAKE_ON_PAUSE, keepAwakeOnPause);
        audioPassthrough = mSharedPreferences.getBoolean(PREF_KEY_AUDIO_PASSTHROUGH, audioPassthrough);
        audioPassthroughForce = mSharedPreferences.getBoolean(PREF_KEY_AUDIO_PASSTHROUGH_FORCE,
                audioPassthroughForce);
        centreBoost = mSharedPreferences.getBoolean(PREF_KEY_CENTRE_BOOST, centreBoost);
        dynamicRange = mSharedPreferences.getBoolean(PREF_KEY_DYNAMIC_RANGE, dynamicRange);
        decoderPriority = Integer.parseInt(mSharedPreferences.getString(PREF_KEY_DECODER_PRIORITY, String.valueOf(decoderPriority)));
        audioSyncMs = Integer.parseInt(mSharedPreferences.getString(PREF_KEY_AUDIO_SYNC_MS, String.valueOf(audioSyncMs)));
        audioPassthroughSyncMs = Integer.parseInt(mSharedPreferences.getString(PREF_KEY_AUDIO_PASSTHROUGH_SYNC_MS, String.valueOf(audioPassthroughSyncMs)));
        mapDV7ToHevc = mSharedPreferences.getBoolean(PREF_KEY_MAP_DV7, mapDV7ToHevc);
        removeHdr10Plus = mSharedPreferences.getBoolean(PREF_KEY_REMOVE_HDR10_PLUS, removeHdr10Plus);
        refuseDolbyVision = mSharedPreferences.getBoolean(PREF_KEY_REFUSE_DOLBY_VISION, refuseDolbyVision);
        languageAudio = getLanguageAudio(mContext);
        languageSubtitle = getLanguageSubtitle(mContext);
        languageSubtitleSecondary = getLanguageSubtitleSecondary(mContext);
        final String searchMode = getSubtitleSearchMode(mContext);
        subtitleSearch = !SEARCH_OFF.equals(searchMode);
        subtitleSearchStrict = SEARCH_NONE.equals(searchMode);
        subtitleSearchLanguage = mSharedPreferences.getBoolean(PREF_KEY_SUBTITLE_SEARCH_LANGUAGE, subtitleSearchLanguage);
        subtitleTranslate = getSubtitleTranslate(mContext);
        subtitleTranslateBackends = getSubtitleTranslateBackends(mContext);
        // Which indexes are asked is a debug affordance — a way to exercise one source on its own
        // when a result looks wrong — and the release build has no screen for it. So the release build
        // does not read these either: a source switched off while testing would otherwise stay off for
        // good, invisibly, with nothing anywhere to turn it back on.
        if (BuildConfig.DEBUG) {
            subtitleSourceOpenSubtitles = mSharedPreferences.getBoolean(PREF_KEY_SOURCE_OPENSUBTITLES, subtitleSourceOpenSubtitles);
            subtitleSourceShegu = mSharedPreferences.getBoolean(PREF_KEY_SOURCE_SHEGU, subtitleSourceShegu);
            subtitleSourceStremio = mSharedPreferences.getBoolean(PREF_KEY_SOURCE_STREMIO, subtitleSourceStremio);
            subtitleSourceRest = mSharedPreferences.getBoolean(PREF_KEY_SOURCE_REST, subtitleSourceRest);
        }
        subtitleStyleBold = mSharedPreferences.getBoolean(PREF_KEY_SUBTITLE_STYLE_BOLD, subtitleStyleBold);
        subtitleScale = Float.parseFloat(mSharedPreferences.getString(PREF_KEY_SUBTITLE_SCALE, String.valueOf(subtitleScale)));
        subtitleTextColor = Color.parseColor(mSharedPreferences.getString(PREF_KEY_SUBTITLE_TEXT_COLOR, "#FFFFFFFF"));
        subtitleBackgroundColor = Color.parseColor(mSharedPreferences.getString(PREF_KEY_SUBTITLE_BACKGROUND, "#00000000"));
        subtitleSecondaryMode = mSharedPreferences.getString(
                PREF_KEY_SUBTITLE_SECONDARY_MODE, subtitleSecondaryMode);
        subtitleSecondaryScale = Float.parseFloat(mSharedPreferences.getString(
                PREF_KEY_SUBTITLE_SECONDARY_SCALE, String.valueOf(subtitleSecondaryScale)));
        subtitleSecondaryTextColor = Color.parseColor(
                mSharedPreferences.getString(PREF_KEY_SUBTITLE_SECONDARY_TEXT_COLOR, "#FFCCCCCC"));
        subtitleSecondaryBackgroundColor = Color.parseColor(
                mSharedPreferences.getString(PREF_KEY_SUBTITLE_SECONDARY_BACKGROUND, "#80000000"));
        subtitleEdgeType = Integer.parseInt(mSharedPreferences.getString(PREF_KEY_SUBTITLE_EDGE, String.valueOf(subtitleEdgeType)));
        skipEnabled = mSharedPreferences.getBoolean(PREF_KEY_SKIP_ENABLED, skipEnabled);
        skipMode = mSharedPreferences.getString(PREF_KEY_SKIP_MODE, skipMode);
        skipModeCredits = mSharedPreferences.getString(PREF_KEY_SKIP_MODE_CREDITS, skipModeCredits);
        skipFetchOnline = mSharedPreferences.getBoolean(PREF_KEY_SKIP_FETCH, skipFetchOnline);
        skipUndo = mSharedPreferences.getString(PREF_KEY_SKIP_UNDO, skipUndo);
        skipHideWhenLocked = mSharedPreferences.getBoolean(PREF_KEY_SKIP_HIDE_LOCKED, skipHideWhenLocked);
        skipCountdown = migrateWithdrawnValues(mSharedPreferences);
        showClock = mSharedPreferences.getBoolean(PREF_KEY_SHOW_CLOCK, showClock);
        timeRemaining = mSharedPreferences.getBoolean(PREF_KEY_TIME_REMAINING, timeRemaining);
        showStats = mSharedPreferences.getBoolean(PREF_KEY_SHOW_STATS, showStats);
        showTransfer = mSharedPreferences.getBoolean(PREF_KEY_SHOW_TRANSFER, showTransfer);
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
        maskReports = mSharedPreferences.getBoolean(PREF_KEY_MASK_REPORTS, maskReports);
        autoUpdate = mSharedPreferences.getBoolean(PREF_KEY_AUTO_UPDATE, autoUpdate);
        // Defaulting to the field would hand back the stale in-memory set once the key is gone, so
        // "Reset learned audio workarounds" (which removes the key) would not take effect until the
        // process restarted — including for the player rebuild that follows leaving the settings screen.
        revokedAudioMimes = mSharedPreferences.getStringSet(
                PREF_KEY_REVOKED_AUDIO_MIMES, Collections.emptySet());
        // A build up to 1.4.2 could write audio/raw here, when an AudioTrack carrying decoded PCM failed
        // to open. That mime is what both audio renderers probe the sink with before they decode, so the
        // entry left the device with no selectable audio track at all — silence for every file, every
        // codec and every decoder priority, remembered for good. Dropped on read (rather than by another
        // one-shot reset, which would throw away verdicts that are real) and rewritten, so the error
        // report and the ffmpeg fallback see the same set as the sink.
        if (revokedAudioMimes.contains(MimeTypes.AUDIO_RAW)) {
            final Set<String> cleaned = new HashSet<>(revokedAudioMimes);
            cleaned.remove(MimeTypes.AUDIO_RAW);
            revokedAudioMimes = cleaned;
            mSharedPreferences.edit()
                    .putStringSet(PREF_KEY_REVOKED_AUDIO_MIMES, cleaned)
                    .apply();
        }
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
     * The second line's language list. Nothing seeds it: an empty list is what says the viewer does not
     * want a second line, and inheriting one from anywhere would turn the feature on for everybody.
     */
    public static String getLanguageSubtitleSecondary(final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getString(PREF_KEY_LANGUAGE_SUBTITLE_SECONDARY, "");
    }

    public static void setLanguageSubtitleSecondary(final Context context, final String languages) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(PREF_KEY_LANGUAGE_SUBTITLE_SECONDARY, languages).apply();
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

    public static String getThemeMode(final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getString(THEME_MODE_KEY, THEME_SYSTEM);
    }

    public static void setThemeMode(final Context context, final String mode) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(THEME_MODE_KEY, mode).apply();
    }

    /**
     * The stored choice as the night mode AppCompat wants. "System" is UNSPECIFIED rather than
     * FOLLOW_SYSTEM: a local mode outranks the default one, and on a TV box PlayerActivity sets that
     * default to dark on purpose. Asking to follow the system there would quietly hand a TV whose
     * system is light a light settings screen, which is not what "System" is being offered for.
     * UNSPECIFIED defers to whatever default is in force — the TV's dark, the phone's system.
     */
    public static int getNightMode(final Context context) {
        switch (getThemeMode(context)) {
            case THEME_DARK:
                return AppCompatDelegate.MODE_NIGHT_YES;
            case THEME_LIGHT:
                return AppCompatDelegate.MODE_NIGHT_NO;
            default:
                return AppCompatDelegate.MODE_NIGHT_UNSPECIFIED;
        }
    }

    /**
     * The accent, as a theme rather than a colour: an overlay per entry, in two variants because six
     * of its ten roles differ by mode (themes_accents.xml, generated). This enum is the one place
     * the set is listed — the picker builds itself from values() and each tile paints itself from its
     * own overlay's attributes, so adding a theme is a row here and a row in the generator.
     */
    public enum Accent {
        CORAL("coral", R.string.pref_accent_coral,
                R.style.ThemeOverlay_JustPlus_Accent_Coral,
                R.style.ThemeOverlay_JustPlus_Accent_Coral_Light),
        ROSEGOLD("rosegold", R.string.pref_accent_rosegold,
                R.style.ThemeOverlay_JustPlus_Accent_RoseGold,
                R.style.ThemeOverlay_JustPlus_Accent_RoseGold_Light),
        STRAWBERRY("strawberry", R.string.pref_accent_strawberry,
                R.style.ThemeOverlay_JustPlus_Accent_Strawberry,
                R.style.ThemeOverlay_JustPlus_Accent_Strawberry_Light),
        COTTONCANDY("cottoncandy", R.string.pref_accent_cottoncandy,
                R.style.ThemeOverlay_JustPlus_Accent_CottonCandy,
                R.style.ThemeOverlay_JustPlus_Accent_CottonCandy_Light),
        ORCHID("orchid", R.string.pref_accent_orchid,
                R.style.ThemeOverlay_JustPlus_Accent_Orchid,
                R.style.ThemeOverlay_JustPlus_Accent_Orchid_Light),
        VIOLET("violet", R.string.pref_accent_violet,
                R.style.ThemeOverlay_JustPlus_Accent_Violet,
                R.style.ThemeOverlay_JustPlus_Accent_Violet_Light),
        ROSEPINE("rosepine", R.string.pref_accent_rosepine,
                R.style.ThemeOverlay_JustPlus_Accent_RosePine,
                R.style.ThemeOverlay_JustPlus_Accent_RosePine_Light),
        DRACULA("dracula", R.string.pref_accent_dracula,
                R.style.ThemeOverlay_JustPlus_Accent_Dracula,
                R.style.ThemeOverlay_JustPlus_Accent_Dracula_Light),
        LAVENDER("lavender", R.string.pref_accent_lavender,
                R.style.ThemeOverlay_JustPlus_Accent_Lavender,
                R.style.ThemeOverlay_JustPlus_Accent_Lavender_Light),
        CATPPUCCIN("catppuccin", R.string.pref_accent_catppuccin,
                R.style.ThemeOverlay_JustPlus_Accent_Catppuccin,
                R.style.ThemeOverlay_JustPlus_Accent_Catppuccin_Light),
        PERIWINKLE("periwinkle", R.string.pref_accent_periwinkle,
                R.style.ThemeOverlay_JustPlus_Accent_Periwinkle,
                R.style.ThemeOverlay_JustPlus_Accent_Periwinkle_Light),
        KANAGAWA("kanagawa", R.string.pref_accent_kanagawa,
                R.style.ThemeOverlay_JustPlus_Accent_Kanagawa,
                R.style.ThemeOverlay_JustPlus_Accent_Kanagawa_Light),
        TOKYONIGHT("tokyonight", R.string.pref_accent_tokyonight,
                R.style.ThemeOverlay_JustPlus_Accent_TokyoNight,
                R.style.ThemeOverlay_JustPlus_Accent_TokyoNight_Light),
        AYU("ayu", R.string.pref_accent_ayu,
                R.style.ThemeOverlay_JustPlus_Accent_Ayu,
                R.style.ThemeOverlay_JustPlus_Accent_Ayu_Light),
        SAPPHIRE("sapphire", R.string.pref_accent_sapphire,
                R.style.ThemeOverlay_JustPlus_Accent_Sapphire,
                R.style.ThemeOverlay_JustPlus_Accent_Sapphire_Light),
        MIDNIGHT("midnight", R.string.pref_accent_midnight,
                R.style.ThemeOverlay_JustPlus_Accent_Midnight,
                R.style.ThemeOverlay_JustPlus_Accent_Midnight_Light),
        NORD("nord", R.string.pref_accent_nord,
                R.style.ThemeOverlay_JustPlus_Accent_Nord,
                R.style.ThemeOverlay_JustPlus_Accent_Nord_Light),
        SOLARIZED("solarized", R.string.pref_accent_solarized,
                R.style.ThemeOverlay_JustPlus_Accent_Solarized,
                R.style.ThemeOverlay_JustPlus_Accent_Solarized_Light),
        OCEAN("ocean", R.string.pref_accent_ocean,
                R.style.ThemeOverlay_JustPlus_Accent_Ocean,
                R.style.ThemeOverlay_JustPlus_Accent_Ocean_Light),
        TEAL("teal", R.string.pref_accent_teal,
                R.style.ThemeOverlay_JustPlus_Accent_Teal,
                R.style.ThemeOverlay_JustPlus_Accent_Teal_Light),
        EVERBLUSH("everblush", R.string.pref_accent_everblush,
                R.style.ThemeOverlay_JustPlus_Accent_Everblush,
                R.style.ThemeOverlay_JustPlus_Accent_Everblush_Light),
        TAKO("tako", R.string.pref_accent_tako,
                R.style.ThemeOverlay_JustPlus_Accent_Tako,
                R.style.ThemeOverlay_JustPlus_Accent_Tako_Light),
        EVERFOREST("everforest", R.string.pref_accent_everforest,
                R.style.ThemeOverlay_JustPlus_Accent_Everforest,
                R.style.ThemeOverlay_JustPlus_Accent_Everforest_Light),
        FOREST("forest", R.string.pref_accent_forest,
                R.style.ThemeOverlay_JustPlus_Accent_Forest,
                R.style.ThemeOverlay_JustPlus_Accent_Forest_Light),
        MONOKAI("monokai", R.string.pref_accent_monokai,
                R.style.ThemeOverlay_JustPlus_Accent_Monokai,
                R.style.ThemeOverlay_JustPlus_Accent_Monokai_Light),
        AMBER("amber", R.string.pref_accent_amber,
                R.style.ThemeOverlay_JustPlus_Accent_Amber,
                R.style.ThemeOverlay_JustPlus_Accent_Amber_Light),
        CLOUDFLARE("cloudflare", R.string.pref_accent_cloudflare,
                R.style.ThemeOverlay_JustPlus_Accent_Cloudflare,
                R.style.ThemeOverlay_JustPlus_Accent_Cloudflare_Light),
        GRUVBOX("gruvbox", R.string.pref_accent_gruvbox,
                R.style.ThemeOverlay_JustPlus_Accent_Gruvbox,
                R.style.ThemeOverlay_JustPlus_Accent_Gruvbox_Light),
        SUNSET("sunset", R.string.pref_accent_sunset,
                R.style.ThemeOverlay_JustPlus_Accent_Sunset,
                R.style.ThemeOverlay_JustPlus_Accent_Sunset_Light),
        MOCHA("mocha", R.string.pref_accent_mocha,
                R.style.ThemeOverlay_JustPlus_Accent_Mocha,
                R.style.ThemeOverlay_JustPlus_Accent_Mocha_Light),
        SLATE("slate", R.string.pref_accent_slate,
                R.style.ThemeOverlay_JustPlus_Accent_Slate,
                R.style.ThemeOverlay_JustPlus_Accent_Slate_Light),
        MONOCHROME("monochrome", R.string.pref_accent_monochrome,
                R.style.ThemeOverlay_JustPlus_Accent_Monochrome,
                R.style.ThemeOverlay_JustPlus_Accent_Monochrome_Light);

        final String key;
        final int name;
        final int dark;
        final int light;

        Accent(final String key, final int name, final int dark, final int light) {
            this.key = key;
            this.name = name;
            this.dark = dark;
            this.light = light;
        }

        static Accent of(final String key) {
            for (final Accent accent : values()) {
                if (accent.key.equals(key))
                    return accent;
            }
            return CORAL;
        }
    }

    public static String getAccent(final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getString(ACCENT_KEY, ACCENT_CORAL);
    }

    public static void setAccent(final Context context, final String accent) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(ACCENT_KEY, accent).apply();
    }

    /**
     * The stored accent as the overlay to applyStyle(…, true) onto a window or dialog theme. The
     * caller says which ground it is on, because the themes that read the roles cannot branch on the
     * mode themselves: chrome over video is dark whatever the appearance choice says, a settings
     * window or a dialog follows {@link #isLight}.
     */
    public static int accentOverlay(final Context context, final boolean light) {
        final Accent accent = Accent.of(getAccent(context));
        return light ? accent.light : accent.dark;
    }

    /** Whether the appearance choice, resolved, puts surfaces on a light ground. */
    /**
     * Values left behind by options this app has withdrawn, written back as what they have become.
     *
     * <p>The skip countdown was once a choice of three - a bar along the button's edge, a ring around
     * the glyph, or nothing - and the bar was withdrawn. The player has read "bar" as the ring ever
     * since, so the picture was right; the settings row was not. A {@code ListPreference} names the
     * entry whose value it holds, and holding a value no entry has, it named nothing: an empty row
     * where "A ring around the glyph" belongs. Reading around a stale value is not enough, it has to be
     * replaced - and it has to be replaced somewhere both the player and the settings screen pass
     * through, which is why this is a static run from {@link App} as well.
     *
     * @return what the countdown setting is, after any correction
     */
    static String migrateWithdrawnValues(final SharedPreferences preferences) {
        final String stored = preferences.getString(PREF_KEY_SKIP_COUNTDOWN, SKIP_COUNTDOWN_RING);
        final String countdown = SKIP_COUNTDOWN_OFF.equals(stored)
                ? SKIP_COUNTDOWN_OFF : SKIP_COUNTDOWN_RING;
        if (!countdown.equals(stored)) {
            preferences.edit().putString(PREF_KEY_SKIP_COUNTDOWN, countdown).apply();
        }
        return countdown;
    }

    public static boolean isLight(final Context context) {
        final String mode = getThemeMode(context);
        if (THEME_LIGHT.equals(mode))
            return true;
        if (THEME_DARK.equals(mode))
            return false;
        // A TV box has no system theme worth following, and PlayerActivity makes dark the default
        // there — so on TV the app answers for itself.
        if (Utils.isTvBox(context))
            return false;
        return (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                != Configuration.UI_MODE_NIGHT_YES;
    }

    /**
     * Whether a folder carrying a {@code .nomedia} file is listed anyway.
     *
     * <p>Off, which is what the marker asks for: a {@code .nomedia} is somebody writing "not media"
     * on a directory, and every gallery and player on the device obeys it. What the browser did
     * before this existed was neither - it obeyed the marker by accident for a folder whose videos
     * sit a level down (MediaStore skips the tree, and the empty-folder filter trusts MediaStore) and
     * ignored it for a folder with a video directly inside (the one-level safety valve found it). One
     * layout vanished, the other did not, and nothing said why.
     */
    public static boolean ignoreNoMedia(final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean("ignoreNoMedia", false);
    }

    /**
     * Whether the browser lists dot-prefixed names. Off, as every file manager and the platform's own
     * picker have it off: without the filter the first thing in a media folder is .thumbnails.
     */
    public static boolean showHiddenFiles(final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean("showHiddenFiles", false);
    }

    /** How the browser draws a folder: one of {@code rows}, {@code tiles}, {@code columns}. */
    public static String browseView(final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getString("browseView", "rows");
    }

    public static void setBrowseView(final Context context, final String view) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString("browseView", view).apply();
    }

    public static boolean isAmoledBlack(final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(PREF_KEY_AMOLED, false);
    }

    /**
     * What the hold-to-speed gesture does, migrated once from the switch it replaced. Read before the
     * settings screen inflates as well as on playback, so a viewer who had the gesture off is not shown
     * it back on.
     */
    public static String getHoldSpeedMode(final Context context) {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        final String stored = preferences.getString(PREF_KEY_HOLD_SPEED_MODE, null);
        if (stored != null) {
            return stored;
        }
        final String migrated = preferences.getBoolean(PREF_KEY_HOLD_SPEED, true)
                ? HOLD_SPEED_ADJUST : HOLD_SPEED_OFF;
        preferences.edit().putString(PREF_KEY_HOLD_SPEED_MODE, migrated)
                .remove(PREF_KEY_HOLD_SPEED)
                .apply();
        return migrated;
    }

    /**
     * Whether subtitles are machine-translated. Read before the settings screen inflates for the same
     * reason {@link #getSubtitleSearchMode} is.
     *
     * <p>Two hops of history collapse here. The setting began as a switch, became a three-way list
     * whose third choice kept the source line under the translation, and is a switch again now that a
     * second subtitle track does that job properly. Both old keys are read once and dropped; the list
     * value has to be read out of {@code getAll} rather than with {@code getString}, because either of
     * the two shapes may be what is stored.
     */
    public static boolean getSubtitleTranslate(final Context context) {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        if (preferences.contains(PREF_KEY_SUBTITLE_TRANSLATE_ON)) {
            return preferences.getBoolean(PREF_KEY_SUBTITLE_TRANSLATE_ON, true);
        }
        final Object mode = preferences.getAll().get(PREF_KEY_SUBTITLE_TRANSLATE_MODE);
        final boolean migrated = mode instanceof String
                ? !"off".equals(mode)
                : preferences.getBoolean(PREF_KEY_SUBTITLE_TRANSLATE, true);
        preferences.edit().putBoolean(PREF_KEY_SUBTITLE_TRANSLATE_ON, migrated)
                .remove(PREF_KEY_SUBTITLE_TRANSLATE_MODE)
                .remove(PREF_KEY_SUBTITLE_TRANSLATE)
                .apply();
        return migrated;
    }

    /**
     * Translation endpoints to try, in order. Unset means the two that were answering when shipped —
     * and in a release build that is the only answer, for the same reason as the source switches
     * above: the choice is a debug affordance, so a release build must not be carrying one made
     * during testing with no screen on which to see or undo it.
     */
    /**
     * The relay and the room password, read without an instance. The browser asks about rooms now —
     * on its own page, rather than sending the viewer to an empty player to be asked there — and it
     * holds no Prefs of its own, the way every other setting it reads is read statically.
     */
    public static String getTogetherRelay(final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getString(PREF_KEY_TOGETHER_RELAY, "");
    }

    public static String getTogetherPassword(final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getString(PREF_KEY_TOGETHER_PASSWORD, "");
    }

    public static String getSubtitleTranslateBackends(final Context context) {
        final String stored = BuildConfig.DEBUG
                ? PreferenceManager.getDefaultSharedPreferences(context)
                        .getString(PREF_KEY_SUBTITLE_TRANSLATE_BACKENDS, null)
                : null;
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

    /**
     * The media the player last wrote down, read without building a {@link Prefs} for it - the browser
     * wants one line of this and none of the rest.
     *
     * <p>It is the file the player <i>closed on</i> and not the one it was opened with: a folder played
     * as a playlist rewrites this on every item. Cleared only when a clip turns out to be gone for good
     * (see the media-unavailable branch of the player's error handling), so an ordinary close leaves it
     * standing.
     */
    static Uri lastMedia(final Context context) {
        final String uri = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(PREF_KEY_MEDIA_URI, null);
        return uri == null ? null : Uri.parse(uri);
    }

    public void updateMedia(final Context context, final Uri uri, final String type) {
        mediaUri = uri;
        mediaType = type;
        suppressResume = false;
        updateSubtitle(null);
        updateSecondarySubtitle(null);
        // Before the reset, so the fit it resets to is not written over the shape the previous media had.
        aspectClass = -1;
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

    /** The second line's file, remembered the same way the first one is. */
    public void updateSecondarySubtitle(final Uri uri) {
        subtitleSecondaryUri = uri;
        if (persistentMode) {
            final SharedPreferences.Editor editor = mSharedPreferences.edit();
            if (uri == null)
                editor.remove(PREF_KEY_SUBTITLE_SECONDARY_URI);
            else
                editor.putString(PREF_KEY_SUBTITLE_SECONDARY_URI, uri.toString());
            editor.apply();
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
        updatePosition(mediaUri, position);
    }

    /**
     * The same, for a file other than the one playing — the episode a playlist has just left behind.
     * Without persistence there is one slot and it belongs to whatever is on screen, so a sibling has
     * nowhere to go and is dropped rather than written over it.
     */
    public void updatePosition(final Uri uri, final long position) {
        if (uri == null)
            return;

        if (!persistentMode) {
            if (uri.equals(mediaUri))
                nonPersitentPosition = position;
            return;
        }

        while (positions.size() > 100)
            positions.remove(positions.keySet().toArray()[0]);

        positions.put(uri.toString(), position);
        savePositions();
        // A torrent server keeps the timecode for everything that plays off it, which is what lets a
        // film started in one player be continued in another. Told on the way out rather than as it
        // plays: this is called when a file is left or an episode changes, not per second.
        if (TorrFiles.speaks(uri)) {
            final Context context = mContext.getApplicationContext();
            new Thread(() -> TorrFiles.remember(context, uri, position), "torr-viewed").start();
        }
    }

    /** Which way the playlist panel is drawn. Remembered, because it is a habit, not a per-film choice. */
    public void updatePlaylistGrid(final boolean grid) {
        this.playlistGrid = grid;
        final SharedPreferences.Editor editor = mSharedPreferences.edit();
        editor.putBoolean(PREF_KEY_PLAYLIST_GRID, grid);
        editor.apply();
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

    /**
     * Remembers that this device cannot bitstream {@code mime}, so the next run does not pay the failure
     * again. Only written for an AudioTrack that refused to open at all — see
     * PlayerActivity.recoverByRevokingAudioMime, which keeps a track that died mid-playback to its own run.
     */
    public void revokeAudioMime(final String mime) {
        final Set<String> updated = new HashSet<>(revokedAudioMimes);
        updated.add(mime);
        revokedAudioMimes = updated;
        mSharedPreferences.edit()
                .putStringSet(PREF_KEY_REVOKED_AUDIO_MIMES, updated)
                .apply();
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

    /**
     * Everything the player has remembered, keyed by uri — what a browse row needs to draw how far
     * into a file the viewer got.
     *
     * <p>Read out of the file rather than off an instance: the screen that wants it does not own a
     * {@link Prefs}, and the player writes this while that screen is away, so a cached copy would be
     * one film out of date on every return. A listing reads it once and asks the map per row.
     */
    @SuppressWarnings("unchecked")
    static Map<String, Long> readPositions(final Context context) {
        try (FileInputStream fis = context.openFileInput("positions");
             ObjectInputStream is = new ObjectInputStream(fis)) {
            return (LinkedHashMap<String, Long>) is.readObject();
        } catch (Exception e) {
            // No file yet, or one written by a version that stored something else. Neither is worth a
            // log line on a screen that only wants to know whether to draw a 4dp run.
            return Collections.emptyMap();
        }
    }

    /**
     * Writes a position for a file this app has not played — the timecode another player left on a
     * server, brought back while its folder was being listed.
     *
     * <p>Static, and read-modify-write, for the same reason {@link #readPositions} is: the thread
     * that has the timecode is a listing thread and owns no {@link Prefs}. It runs before the player
     * is built, so the instance that will read this file has not loaded it yet.
     */
    @SuppressWarnings("unchecked")
    static void rememberPosition(final Context context, final Uri uri, final long position) {
        if (uri == null) {
            return;
        }
        LinkedHashMap<String, Long> known;
        try (FileInputStream fis = context.openFileInput("positions");
             ObjectInputStream is = new ObjectInputStream(fis)) {
            known = (LinkedHashMap<String, Long>) is.readObject();
        } catch (Exception e) {
            known = new LinkedHashMap<>(10);
        }
        final Long had = known.get(uri.toString());
        if (had != null && had == position) {
            // Nothing to write, and writing it would be a file rewritten per row of a listing.
            return;
        }
        while (known.size() > 100) {
            known.remove(known.keySet().toArray()[0]);
        }
        known.put(uri.toString(), position);
        try (FileOutputStream fos = context.openFileOutput("positions", Context.MODE_PRIVATE);
             ObjectOutputStream os = new ObjectOutputStream(fos)) {
            os.writeObject(known);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Reads the position map off disk again.
     *
     * <p>It is loaded once when this object is built, which was true while the player was the only
     * thing that wrote it. It is not any more: a torrent server keeps the timecode, and the browser
     * writes what the server said into this file while listing a folder — so a player activity still
     * alive from the last film (it is {@code singleTask}) would answer out of a map written before
     * that, and open at where this device stopped rather than where the server says the viewer did.
     */
    public void reloadPositions() {
        if (persistentMode) {
            loadPositions();
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
        return getPosition(mediaUri);
    }

    /** The same, for a file other than the one playing — an episode a playlist is about to jump to. */
    public long getPosition(final Uri mediaUri) {
        if (!persistentMode) {
            return mediaUri != null && mediaUri.equals(this.mediaUri) ? nonPersitentPosition : 0L;
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

    /**
     * Which of four shapes a picture of this display aspect ratio belongs to: 0 widescreen, 1 squarish,
     * 2 wider than widescreen, 3 taller than wide, -1 no usable size. Boundaries taken from the
     * reference player, which splits at 1.55 and 1.9 — wide enough that 1.78 and 1.85 share a slot,
     * and scope (2.35, 2.39) gets its own.
     */
    public static int aspectClassOf(final float displayAspectRatio) {
        if (displayAspectRatio <= 0.1f)
            return -1;
        if (displayAspectRatio < 1.f)
            return 3;
        if ((double) displayAspectRatio < 1.55d)
            return 1;
        return (double) displayAspectRatio > 1.9d ? 2 : 0;
    }

    /**
     * Brings back the frame mode last chosen for this shape of picture. Called when the shape becomes
     * known and whenever it changes; an unusable size (-1) leaves the fit the media was opened with.
     */
    public void loadFrameMode(final int aspectClass) {
        this.aspectClass = aspectClass;
        if (aspectClass < 0)
            return;
        resizeMode = mSharedPreferences.getInt(PREF_KEY_RESIZE_MODE + "_" + aspectClass,
                AspectRatioFrameLayout.RESIZE_MODE_FIT);
        scale = mSharedPreferences.getFloat(PREF_KEY_SCALE + "_" + aspectClass, 1.f);
        aspectRatio = mSharedPreferences.getFloat(PREF_KEY_ASPECT_RATIO + "_" + aspectClass, 0f);
    }

    /**
     * Records the frame mode against the shape of picture it was chosen for. Called the moment the
     * viewer chooses — as the reference player does — and not only when the player is torn down: a
     * choice that lives on the view alone is lost the first time the shape changes under it.
     *
     * <p>With no shape known (-1) nothing is written, which is what keeps the reset in updateMedia —
     * a new media opening at fit — from wiping what the previous shape had remembered.
     */
    public void updateFrameMode(final int resizeMode, final float scale, final float aspectRatio) {
        this.resizeMode = resizeMode;
        this.scale = scale;
        this.aspectRatio = aspectRatio;
        if (!persistentMode || aspectClass < 0)
            return;
        mSharedPreferences.edit()
                .putInt(PREF_KEY_RESIZE_MODE + "_" + aspectClass, resizeMode)
                .putFloat(PREF_KEY_SCALE + "_" + aspectClass, scale)
                .putFloat(PREF_KEY_ASPECT_RATIO + "_" + aspectClass, aspectRatio)
                .apply();
    }

    public void updateMeta(final String audioTrackId, final String subtitleTrackId, final int resizeMode, final float scale, final float aspectRatio, final float speed) {
        this.audioTrackId = audioTrackId;
        this.subtitleTrackId = subtitleTrackId;
        updateFrameMode(resizeMode, scale, aspectRatio);
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
            sharedPreferencesEditor.putFloat(PREF_KEY_SPEED, speed);
            sharedPreferencesEditor.apply();
        }
    }

    /**
     * Where the browser had got to, so that coming back to it means coming back to the same folder
     * rather than to the top of the device. Kept as the trail rather than the one folder, because the
     * crumbs above the list are the trail and a single path could not rebuild them - and with the
     * names beside the paths, because a volume's label is not its directory name.
     *
     * <p>Read straight off SharedPreferences rather than held in a field: only the browser wants it,
     * and it is written on every step through a folder.
     */
    public static void setBrowseTrail(final Context context, final String destination,
                                      final String trail) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(PREF_KEY_BROWSE_DEST, destination)
                .putString(PREF_KEY_BROWSE_TRAIL, trail).apply();
    }

    public static String getBrowseTrail(final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getString(PREF_KEY_BROWSE_TRAIL, null);
    }

    /** Which of the browser's destinations that trail belongs to; null before one was ever written. */
    public static String getBrowseDest(final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getString(PREF_KEY_BROWSE_DEST, null);
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
     * is built, and rebuilding for them would restart the film over a change of display name. The
     * appearance is left out for the same reason: the player's chrome is dark over the picture
     * whichever appearance is chosen, so light/dark and AMOLED cost it nothing. The accent stays in
     * — the player does show it, and the caller looks for that key by name.
     */
    public Map<String, ?> snapshot() {
        final Map<String, Object> all = new HashMap<>(mSharedPreferences.getAll());
        all.remove(PREF_KEY_TOGETHER_NICK);
        all.remove(PREF_KEY_TOGETHER_PASSWORD);
        all.remove(PREF_KEY_TOGETHER_PUBLIC);
        all.remove(PREF_KEY_TOGETHER_RELAY);
        all.remove(THEME_MODE_KEY);
        all.remove(PREF_KEY_AMOLED);
        all.keySet().removeAll(SESSION_STATE);
        // The frame mode is kept per aspect class - resizeMode_0, scale_2 - so there is no bare key to
        // name. Reported by review: the three bare names matched nothing, and the first film of a class
        // this device had not seen wrote three keys on the stop that opening the settings screen causes.
        for (final java.util.Iterator<String> key = all.keySet().iterator(); key.hasNext(); ) {
            final String name = key.next();
            for (final String stem : SESSION_STATE_PREFIXES) {
                if (name.startsWith(stem)) {
                    key.remove();
                    break;
                }
            }
        }
        return all;
    }

    /**
     * What the app writes about the session it is in the middle of, rather than what somebody chose on
     * the settings screen. It shares a store with the settings and must stay out of {@link #snapshot()}:
     * the question that snapshot answers is "did a setting change while the viewer was away", and the
     * player answers part of it itself.
     *
     * <p>Concretely, {@code savePlayer()} runs when the player stops - which is exactly what happens
     * when the settings screen opens over it - and writes the volume, the brightness, the orientation
     * and the chosen tracks. On a file with one audio track and no subtitles that meant two keys
     * appearing out of nowhere (audioTrackId=#none, subtitleTrackId=#none), the comparison finding a
     * difference, and the player being rebuilt on the way back. Over a torrent server that rebuild is a
     * stream re-opened and a torrent re-buffered from the beginning, for a screen the viewer opened and
     * closed without touching anything.
     *
     * <p>A key belongs here when the settings screen cannot change it (see {@code root_preferences.xml}).
     * Getting that wrong costs a rebuild nobody asked for, which is this bug.
     */
    private static final Set<String> SESSION_STATE = Collections.unmodifiableSet(new HashSet<>(
            java.util.Arrays.asList(
                    PREF_KEY_MEDIA_URI, PREF_KEY_MEDIA_TYPE,
                    PREF_KEY_SUBTITLE_URI, PREF_KEY_SUBTITLE_SECONDARY_URI,
                    PREF_KEY_AUDIO_TRACK_ID, PREF_KEY_SUBTITLE_TRACK_ID,
                    PREF_KEY_BRIGHTNESS, PREF_KEY_BRIGHTNESS_PERCENT, PREF_KEY_VOLUME_PERCENT,
                    PREF_KEY_ORIENTATION, PREF_KEY_SPEED, PREF_KEY_HOLD_SPEED,
                    PREF_KEY_PLAYLIST_GRID, PREF_KEY_SCOPE_URI, PREF_KEY_ASK_SCOPE,
                    PREF_KEY_FIRST_RUN, PREF_KEY_RESTORE_AUTO_ROTATE,
                    PREF_KEY_BROWSE_TRAIL, PREF_KEY_BROWSE_DEST,
                    PREF_KEY_UPDATE_LAST_CHECK, PREF_KEY_UPDATE_PENDING, PREF_KEY_UPDATE_SKIPPED,
                    // Removed by migrations that SettingsActivity runs before it inflates, which is
                    // while the player is stopped behind it: the keys vanish, the snapshot differs, and
                    // the film is rebuilt once per upgrade.
                    PREF_KEY_SUBTITLE_SEARCH, PREF_KEY_SUBTITLE_SEARCH_STRICT,
                    PREF_KEY_SUBTITLE_TRANSLATE, PREF_KEY_SUBTITLE_TRANSLATE_MODE,
                    MediaCache.PREF_KEY_CACHE_BUILD)));

    /** The same thing for what is stored one key per aspect class - see {@link #updateFrameMode}. */
    private static final String[] SESSION_STATE_PREFIXES = {
            PREF_KEY_RESIZE_MODE + "_", PREF_KEY_SCALE + "_", PREF_KEY_ASPECT_RATIO + "_"};
}