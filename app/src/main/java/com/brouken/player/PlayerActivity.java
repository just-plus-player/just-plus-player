package com.brouken.player;

import static android.content.pm.PackageManager.FEATURE_EXPANDED_PICTURE_IN_PICTURE;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.app.PendingIntent;
import android.app.PictureInPictureParams;
import android.app.RemoteAction;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.UriPermission;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.content.res.ColorStateList;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.Icon;
import android.hardware.display.DisplayManager;
import android.media.AudioManager;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.LoudnessEnhancer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.os.SystemClock;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Rational;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.window.OnBackInvokedDispatcher;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.documentfile.provider.DocumentFile;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.util.Util;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.VideoSize;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.Timeline;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.util.StuckPlayerException;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.decoder.ffmpeg.FfmpegLibrary;
import androidx.media3.exoplayer.DecoderCounters;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlaybackException;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.ExoTimeoutException;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.RenderersFactory;
import androidx.media3.exoplayer.SeekParameters;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.exoplayer.audio.AudioRendererEventListener;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.DefaultAudioSink;
import androidx.media3.exoplayer.audio.ForwardingAudioSink;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.text.TextOutput;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.media3.extractor.text.DefaultSubtitleParserFactory;
import androidx.media3.extractor.text.SubtitleParser;
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory;
import androidx.media3.extractor.ts.TsExtractor;
import androidx.media3.session.MediaSession;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.CaptionStyleCompat;
import androidx.media3.ui.DefaultTimeBar;
import androidx.media3.ui.PlayerControlView;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.SubtitleView;
import androidx.media3.ui.TimeBar;

import com.brouken.player.dtpv.DoubleTapPlayerView;
import com.brouken.player.dtpv.youtube.YouTubeOverlay;
import com.brouken.player.skip.IntentSegmentsSource;
import com.brouken.player.skip.NetworkSegmentsSource;
import com.brouken.player.skip.SegmentFinder;
import com.brouken.player.skip.SkipManager;
import com.brouken.player.skip.SkipSegment;
import com.brouken.player.together.Relay;
import com.brouken.player.together.Room;
import com.brouken.player.together.SessionCodec;
import com.brouken.player.together.TogetherManager;
import com.brouken.player.update.UpdateInfo;
import com.brouken.player.update.UpdateUi;
import com.brouken.player.update.Updater;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.google.android.material.snackbar.Snackbar;

import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Date;
import java.util.Formatter;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;

public class PlayerActivity extends Activity {

    private PlayerListener playerListener;
    private BroadcastReceiver mReceiver;
    private AudioManager mAudioManager;
    private MediaSession mediaSession;
    private DefaultTrackSelector trackSelector;
    public static LoudnessEnhancer loudnessEnhancer;
    // Boost fallback for devices where the effect above is a no-op, see BoostAudioProcessor
    public static BoostAudioProcessor boostProcessor;

    private CustomDefaultTrackNameProvider trackNameProvider;
    // Track names read from the container (MP4 udta/name, MKV TrackEntry/Name), and the resolved
    // Format.id -> name map that the track list and header read from once tracks are known.
    private final java.util.List<TrackMetadata> containerTracks = new java.util.ArrayList<>();
    private final java.util.Map<String, String> resolvedTrackNames = new java.util.HashMap<>();
    /**
     * The media item {@link #containerTracks} was parsed from, keyed like {@link #contentLengths}.
     * ExoPlayer opens the next item of a playlist while the current one is still playing, so metadata
     * that is merely "the last parsed" belongs to whichever item won the race — the panel would print
     * the previous episode's frame rate and the tap would be told there was nothing left to parse.
     * Read from a load thread, written on the UI thread.
     */
    private volatile String containerTracksUri;
    // Streaming manifest type (HLS/DASH/SS) discovered from the real HTTP response of a media item
    // whose request URL had no telling extension. Keyed by the requested URI (== MediaItem URI).
    // Written from a load thread, read on the player thread — hence concurrent.
    private final java.util.Map<String, String> resolvedMediaTypes = new java.util.concurrent.ConcurrentHashMap<>();
    // Byte length of each media item as its data source reported it, keyed the same way. Feeds the
    // stats panel's average bitrate for the containers that state no bitrate at all.
    private final java.util.Map<String, Long> contentLengths = new java.util.concurrent.ConcurrentHashMap<>();
    private final TrackNameParsingDataSource.Listener trackNameListener = new TrackNameParsingDataSource.Listener() {
        @Override
        public void onMetadataParsed(Uri originalUri, java.util.List<TrackMetadata> tracks) {
            if (originalUri == null) {
                return;
            }
            // Parses on a load thread; hop to the UI thread to touch player/views.
            final String key = originalUri.toString();
            runOnUiThread(() -> onContainerMetadata(key, tracks));
        }

        @Override
        public boolean isMetadataParsed(Uri originalUri) {
            return originalUri != null && originalUri.toString().equals(containerTracksUri);
        }

        @Override
        public void onContentLength(Uri originalUri, long length) {
            if (originalUri != null) {
                contentLengths.put(originalUri.toString(), length);
            }
        }

        @Override
        public void onMediaTypeResolved(Uri originalUri, String mimeType) {
            if (originalUri != null && mimeType != null) {
                resolvedMediaTypes.put(originalUri.toString(), mimeType);
            }
        }

        @Override
        public void onResolverNotReady(Uri originalUri) {
            if (originalUri != null) {
                resolverNotReadyUri = originalUri.toString();
            }
        }
    };

    // Set (on a load thread) to the URI whose response was a Lampac resolver handshake instead of
    // media; read in onPlayerError to show a friendly message rather than retrying/reporting.
    private volatile String resolverNotReadyUri;

    public CustomPlayerView playerView;
    public static ExoPlayer player;
    // Live handle to the current player's audio sink wrapper, for recoverByRevokingAudioMime().
    private AudioPassthroughDenylistSink audioSink;
    private YouTubeOverlay youTubeOverlay;

    private Object mPictureInPictureParamsBuilder;
    // True between the two onPictureInPictureModeChanged callbacks. Read instead of
    // isInPictureInPictureMode() so the guards below need no API-level dance and cannot be caught by the
    // window's own state lagging a frame behind the callback.
    private boolean inPip;

    public Prefs mPrefs;
    public BrightnessControl mBrightnessControl;
    public static boolean haveMedia;
    private boolean videoLoading;
    // Watchdog for a silent load failure: if the player never reaches STATE_READY within this window
    // (stuck buffering, a broken next-episode URL, etc.) a friendly LOAD_TIMEOUT message is shown.
    // Such stalls often produce no PlaybackException, so onPlayerError alone would never catch them.
    private static final long VIDEO_LOAD_TIMEOUT_MS = 30_000L;
    /** How long to wait for a requested display mode to report back before starting playback anyway. */
    private static final long FRAME_RATE_SWITCH_TIMEOUT_MS = 3_000L;
    // Bytes seen when the watchdog was last armed, so its verdict is "nothing is arriving" rather than
    // "this is taking a while". Filling the first buffer of a large torrent-backed file routinely needs
    // several of these windows — the backend fetches pieces from peers at whatever rate it can, and the
    // extractor additionally reads the container index from the far end of the file, which is a second
    // fetch from cold. That load is slow, not stuck, and killing it mid-download was the failure users
    // saw as a timeout on big files over a connection that never dropped.
    private long loadWatchdogBytes;
    // Progress below this over a whole window is not a load: 8 KB/s is far under anything playable, so it
    // is a socket dribbling keepalives rather than a source that is still working. Wait only for real bytes.
    private static final long LOAD_PROGRESS_MIN_BYTES = 256 * 1024;
    // Buffering that resolves this fast is not worth an indicator: the spinner would replace the play button
    // for a frame or two and read as a glitch. Recreating the audio track (restartPassthroughAudio) takes
    // about 35 ms, a track switch or a seek inside the buffer are the same order, and a real wait is always
    // longer than this. Explicit updateLoading() calls (opening a clip, switching source) are unaffected.
    private static final long LOADING_INDICATOR_DELAY_MS = 250L;
    // Heavy MKV audio codecs whose platform MediaCodec decoder can wedge on init on some TV boxes
    // (JPP-1005). On TV+MKV these are hidden from the platform decoder via a MediaCodecSelector, so
    // MediaCodecAudioRenderer either bitstreams to a receiver that advertises passthrough (Atmos kept)
    // or the track falls through to the ffmpeg software renderer — never the wedging platform decoder.
    // Set matches the ffmpeg build's decoders (app/libs/README.md); AC4 / DTS:X-UHD are excluded since
    // ffmpeg has no decoder for them and blocking would leave the track unplayable (muted audio).
    private static final List<String> HEAVY_MKV_AUDIO_MIMES = Arrays.asList(
            MimeTypes.AUDIO_AC3, MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_E_AC3_JOC,
            MimeTypes.AUDIO_DTS, MimeTypes.AUDIO_DTS_HD, MimeTypes.AUDIO_DTS_EXPRESS,
            MimeTypes.AUDIO_TRUEHD);
    // How long playback has to be stalled before it is worth rebuilding the audio track for it (see
    // audioRestartPending). This is a rate limiter, not a correctness guard — audioRestartSettling is what
    // keeps our own recreate from arming itself. A marginal network stutters in bursts, and each recreate
    // costs its own ~111 ms of audio, so curing every sub-second hiccup would sound worse than the fault it
    // cures. The cost of the threshold is that a stall shorter than this is left to the user's own pause.
    private static final long REBUFFER_ARM_MS = 1_500L;
    private final Runnable loadTimeoutRunnable = this::reportVideoLoadTimeout;
    private final Runnable rebufferArmRunnable = () -> audioRestartPending = true;
    // Deferred by LOADING_INDICATOR_DELAY_MS from STATE_BUFFERING; cancelled by any explicit updateLoading().
    private final Runnable showLoadingRunnable = () -> {
        updateLoading(true);
        setEpisodeNavLoading(true);
    };
    // One-shot recovery from the device's Dolby Vision decoder failing on a stream: re-decode the DV
    // track as plain HEVC — its base layer — so the picture comes back as HDR10. Reached from both
    // symptoms the decoder shows: wedging silently (Media3 StuckPlayerDetector → ERROR_CODE_TIMEOUT) and
    // failing loudly mid-render (ERROR_CODE_DECODING_FAILED). forceHevcForDolbyVision drives the codec
    // selector at the next player build and doubles as the guard against firing twice;
    // pendingStuckRecovery marks that rebuild so the reset in initializePlayer keeps the flag.
    // Read by the codec selector on the playback thread.
    private volatile boolean forceHevcForDolbyVision;
    private boolean pendingStuckRecovery;
    // Installed for this player build when Dolby Vision profile 7 is being rewritten as profile 8.1;
    // null when it is not. Kept only so the dump can say which mode the stream actually got.
    @Nullable
    private Dv7Converter dv7Converter;
    // Per process, not per player: a device that keeps needing the fallback must not re-report it.
    private static boolean dolbyVisionFallbackReported;
    // Names of the decoders that actually opened, for the error report. The mime does not tell them apart
    // — c2.android.* (software) and OMX.<vendor>.* (hardware) decode the same hevc very differently — and
    // "the device decoder wedged" is unreadable without knowing which one it was. Written on the app
    // thread by the analytics listener, reset per player build.
    private String videoDecoderName;
    private String audioDecoderName;
    // Loader's throughput estimate — the one stats figure the player itself cannot be asked for.
    private long bandwidthBitrate;
    private final AnalyticsListener playbackInfoListener = new AnalyticsListener() {
        @Override
        public void onVideoDecoderInitialized(AnalyticsListener.EventTime eventTime, String decoderName,
                                             long initializedTimestampMs, long initializationDurationMs) {
            videoDecoderName = decoderName;
        }

        @Override
        public void onAudioDecoderInitialized(AnalyticsListener.EventTime eventTime, String decoderName,
                                              long initializedTimestampMs, long initializationDurationMs) {
            audioDecoderName = decoderName;
        }

        @Override
        public void onBandwidthEstimate(AnalyticsListener.EventTime eventTime, int totalLoadTimeMs,
                                        long totalBytesLoaded, long bitrateEstimate) {
            bandwidthBitrate = bitrateEstimate;
        }
    };
    // A fatal report has just been shown for the current clip. onStart re-initialises the player every
    // time the activity comes back, so without this the clip is prepared again the moment the report is
    // closed, fails the same way and reopens it — a window that cannot be dismissed. Fall back to the
    // empty state instead, as the media-gone path does. One-shot: consumed by that next initialisation,
    // so re-opening the same clip, or picking another one, plays normally.
    private boolean skipMediaAfterFatalError;
    // Re-reads spent on network source errors this session (see recoverFromSourceError), reset per player
    // build so a chronically bad stream costs a bounded number of re-prepares instead of one per resume.
    private int sourceRetries;
    private static final int MAX_SOURCE_RETRIES = 3;
    // Held in a field (like loadTimeoutRunnable) so releasePlayer can drop a re-read that no longer has a
    // session to belong to.
    private final Runnable sourceRetryRunnable = () -> {
        if (player != null) {
            player.prepare();
        }
    };
    // Less progress than this counts as "never started" rather than "stopped mid-stream" — see
    // stalledAtStart(). Generous on purpose: the point is only to separate a decoder that took the stream
    // and produced nothing from one that played for a while and then stopped.
    private static final long STALL_AT_START_MS = 2_000L;
    // Whether the bundled ffmpeg decoder loaded on this ABI, for the error report. Sampled where the
    // renderers are built — that is where the library is loaded anyway — so a report never pays for a
    // dlopen on the main thread, and stays null until then rather than lying.
    private static Boolean ffmpegAvailable;
    // Position where playback actually began (first STATE_READY, then every item change), so progress is
    // measured against it rather than against zero: a film resumed at the fortieth minute, or a live stream
    // positioned inside its window, still has a large position when it wedges on its first frame, and
    // judging by the position alone would call that a mid-stream stall. C.TIME_UNSET until known.
    private long playerStartPositionMs = C.TIME_UNSET;
    private final Timeline.Period stallPeriod = new Timeline.Period();
    // Re-prepares spent on a live channel that stopped advancing (see recoverLiveStall).
    private int liveStallRecoveries;
    private long lastLiveStallMs;
    private static final int MAX_LIVE_STALL_RECOVERIES = 2;
    private static final long LIVE_STALL_FORGET_MS = TimeUnit.MINUTES.toMillis(1);
    // Per process, not per player: a rebuilt player must not re-report what is already known.
    private static boolean liveRejoinReported;
    // Re-reads spent on a transient decoder failure this session (see recoverFromDecoderFailure), reset
    // per player build and on reaching STATE_READY.
    private int decoderRetries;
    private static final int MAX_DECODER_RETRIES = 3;
    private final Runnable decoderRetryRunnable = () -> {
        if (player != null) {
            player.prepare();
        }
    };
    // Backgrounding keeps the player (see onStop) instead of tearing it down, so a quick round-trip —
    // settings, notification shade, app switch — returns to the same session, paused, instead of
    // re-buffering the stream. The decoder is held for it, so give up once the user is plainly gone: after
    // this the teardown is the same as before. Tune if holding a decoder that long proves a problem.
    private static final long BACKGROUND_RELEASE_MS = TimeUnit.MINUTES.toMillis(5);
    private final Runnable backgroundReleaseRunnable = () -> {
        // The return finds no player and rebuilds it; that rebuild must not start playing on its own.
        sourceSwitchKeepPaused = true;
        releasePlayer(false);
    };
    // How long a resumed session gets to put a frame back on screen. The retained player is attached to a
    // SurfaceView that was destroyed while we were away, and on a slow box the decoder does not always
    // hand the surface back (Media3 reports that as ERROR_CODE_TIMEOUT — but not always at all).
    private static final long RESUME_WATCHDOG_MS = 3_000L;
    private boolean resumeFrameRendered;
    private final Runnable resumeWatchdogRunnable = () -> {
        if (player == null || resumeFrameRendered) {
            return;
        }
        // Nothing has been drawn since we came back — or the player stopped outright: treat the retained
        // session as dead and rebuild it. Position and meta were saved in onPause. Playback is paused on
        // return, which is no excuse for a black frame: a paused player still re-renders when the surface
        // comes back (setOutput drops firstFrameState to FIRST_FRAME_NOT_RENDERED, which releases a frame
        // whether started or not). It does not "join" while paused though, so until that frame lands the
        // renderer reports not-ready and the player sits in BUFFERING rather than READY — which is why
        // buffering counts here. What separates it from an honestly slow stream is the buffer: the retained
        // session still holds the one it had. Audio-only media never renders a frame, so it is exempt.
        final int state = player.getPlaybackState();
        final boolean stalledWithData = (state == Player.STATE_READY || state == Player.STATE_BUFFERING)
                && player.getVideoFormat() != null && player.getTotalBufferedDuration() > 0;
        if (state == Player.STATE_IDLE || stalledWithData) {
            sourceSwitchKeepPaused = true;
            releasePlayer(false);
            initializePlayer();
        }
    };
    public static boolean controllerVisible;
    public static boolean controllerVisibleFully;
    // Whether the chrome that floats beside the controls (the stats panel, the room pill) belongs on screen.
    // Neither of the two flags above says that. controllerVisible stays true through the hide fade and the
    // two seconds of seek-bar-only state that follow it, so chrome keyed off it stands over a picture the
    // bars have already left; controllerVisibleFully is false for the whole show animation too, so chrome
    // keyed off that one comes back a beat after everything else. What is left is the two edges — the
    // controls have just appeared, or they have just stopped being fully visible, which is the first frame
    // of their fade — with the states in between keeping whatever the last edge decided.
    private boolean controllerChromeVisible;
    public static Snackbar snackbar;
    private ExoPlaybackException errorToShow;
    public static int boostLevel = 0;
    // Whether the hearing warning has already been shown this session, see Utils.warnAboutBoost
    public static boolean boostWarned = false;
    // Off = volume gestures and keys drive the player alone and never touch the device's stream. Mirrors
    // Prefs.systemVolume, except on TV boxes where volume has to go through the system (CEC).
    public static boolean systemVolume = true;
    // The player's own level, 0-100, only meaningful while systemVolume is off. Anything above 100 lives
    // in boostLevel, exactly as in the synced mode.
    public static float playerVolume = 100f;
    private boolean isScaling = false;
    private boolean isScaleStarting = false;
    private float scaleFactor = 1.0f;
    // Preference state as it was when the settings screen opened, see openSettings()
    private Map<String, ?> settingsBefore;

    private static final int REQUEST_CHOOSER_VIDEO = 1;
    private static final int REQUEST_CHOOSER_SUBTITLE = 2;
    private static final int REQUEST_CHOOSER_SCOPE_DIR = 10;
    private static final int REQUEST_CHOOSER_VIDEO_MEDIASTORE = 20;
    private static final int REQUEST_CHOOSER_SUBTITLE_MEDIASTORE = 21;
    private static final int REQUEST_SETTINGS = 100;
    public static final int CONTROLLER_TIMEOUT = 3500;
    // Media3's own DURATION_FOR_HIDING_ANIMATION_MS — see fadeChrome, which has to match it.
    private static final int CHROME_FADE_MS = 250;
    private static final String ACTION_MEDIA_CONTROL = "media_control";
    private static final String EXTRA_CONTROL_TYPE = "control_type";
    private static final int REQUEST_PLAY = 1;
    private static final int REQUEST_PAUSE = 2;
    private static final int CONTROL_TYPE_PLAY = 1;
    private static final int CONTROL_TYPE_PAUSE = 2;

    CoordinatorLayout coordinatorLayout;
    private LinearLayout topInfoPanel;
    private LinearLayout headerButtons;
    private FrameLayout posterSlot;
    private ImageView posterView;
    private TextView posterPlaceholderView;
    private TextView posterBadgeView;
    private TextView titleView;
    private TextView videoInfoView;
    private TextView audioInfoView;
    private TextView endsAtView;
    private TextView roomPill;
    private TextView statsView;
    private OutlineTextClock overlayClock;
    private OutlineTextClock headerClock;
    private ImageButton buttonOpen;
    private ImageButton buttonPlaylist;
    private ImageButton buttonQuality;
    private ImageButton buttonAudio;
    private ImageButton buttonMore;
    private ImageButton buttonUpdate;
    private ImageButton buttonSkipOffset;
    private android.app.Dialog qualityDialog;
    private android.app.Dialog playlistDialog;
    private android.app.Dialog skipOffsetDialog;
    private android.app.Dialog subtitleOffsetDialog;
    private android.app.Dialog sleepTimerDialog;
    private android.app.Dialog menuDialog;
    // While a picker panel is open the app must stay out of immersive/fullscreen, otherwise OxygenOS/ColorOS
    // applies its fullscreen back-gesture guard ("swipe again to go back") and the panel needs two swipes.
    private boolean pickerDialogOpen;
    // Media3 keeps the controller shown indefinitely while paused (it forces the auto-hide timeout to 0),
    // so reuse the same CONTROLLER_TIMEOUT + hideController() to also clear the UI on pause. A tap re-shows
    // and re-arms it, exactly like during playback (see scheduleHideControllerOnPause).
    private final Runnable hideControllerAction = () -> {
        if (player != null && !player.getPlayWhenReady() && controllerVisibleFully)
            playerView.hideController();
    };
    // Adaptive sizing source of truth (phone/tablet/TV). Computed in onCreate, recomputed on config change.
    UiMetrics ui;
    private ImageButton buttonPiP;
    private ImageButton buttonAspectRatio;
    // Forced display aspect ratio currently applied (0 = natural video AR). Persisted via Prefs.aspectRatio.
    private float currentAspectRatio = 0f;
    private List<AspectMode> aspectModes;
    private ImageButton buttonRotation;
    private ImageButton buttonLock;
    // Swipe-to-unlock bar shown over the video while the screen is locked; the only affordance for leaving
    // the locked state (drag on touch, hold a D-pad key on TV).
    private SwipeToUnlockView swipeToUnlock;
    // Back-button guard while locked: the first Back arms this, a second Back within the window exits.
    private boolean lockBackPressedOnce;
    // Same guard on TV, once Back has nothing left to close: a stray press on a remote must not drop
    // out of the player.
    private boolean backPressedOnce;
    // Set while a Down press is opening the controls, so focus lands on the time bar instead of play/pause.
    private boolean focusTimeBarOnShow;
    // The page shown when there is nothing to play; owns its own views, reveal and pulse.
    private final EmptyState emptyState = new EmptyState(this);
    private ImageButton exoSettings;
    private ImageButton exoSubtitle;
    private ImageButton exoPlayPause;
    private ImageButton exoPrev;
    private ImageButton exoNext;
    private boolean episodeNavLoading;
    private ProgressBar loadingProgressBar;
    // Transfer rate under the loading ring. A spinning ring says nothing about whether bytes are still
    // arriving, so once the wait gets noticeable the rate answers it: 0,0 MB/s reads as "nothing is
    // coming", anything else as "alive, just slow". Delayed so the short reloads after a seek stay clean.
    private TextView loadingSpeedView;
    private static final long LOADING_SPEED_DELAY_MS = 2_500L;
    private static final long LOADING_SPEED_TICK_MS = 1_000L;
    private long loadingSpeedBytes;
    private boolean loadingSpeedScheduled;
    private final Runnable loadingSpeedRunnable = new Runnable() {
        @Override
        public void run() {
            final long total = TrackNameParsingDataSource.bytesRead.get();
            final double mbPerSec = Math.max(0, total - loadingSpeedBytes)
                    * 1000d / LOADING_SPEED_TICK_MS / (1024 * 1024);
            loadingSpeedBytes = total;
            loadingSpeedView.setText(getString(R.string.loading_speed, mbPerSec));
            loadingSpeedView.setVisibility(View.VISIBLE);
            playerView.postDelayed(this, LOADING_SPEED_TICK_MS);
        }
    };
    private PlayerControlView controlView;
    private CustomDefaultTimeBar timeBar;

    private boolean restoreOrientationLock;
    private boolean restorePlayState;
    private boolean restorePlayStateAllowed;
    // "Start playing once the player is ready". Read live by Utils.playIfCan, whose frame-rate probe runs on
    // a background thread: a snapshot taken before it started would still play after onStop cleared this.
    boolean play;
    private float subtitlesScale;
    private boolean isScrubbing;
    private boolean scrubbingNoticeable;
    private long scrubbingStart;
    // Key seek (D-pad arrows, ◀◀/▶▶) accelerates: the longer the key is held — or the faster it is
    // clicked — the bigger the step, and the seek itself happens once, after the presses stop. A fixed
    // 10s per press cannot get through a feature-length film, and one seekTo per press is one
    // re-buffering per press: on a network stream a burst of clicks means seconds of black screen
    // instead of a single jump.
    private long keyScrubTarget = -1;   // -1 = no scrub in progress
    private int keyScrubSteps;          // presses in a row (drives the step size)
    private long keyScrubLastMs;
    private final Runnable keyScrubCommit = this::commitKeyScrub;
    // A passthrough AudioTrack that has been paused and resumed comes back silent on a fair number of TVs
    // and receivers: the bitstream still leaves the box but nothing downstream re-locks onto it. Video keeps
    // playing and the position keeps advancing, so nothing in Media3 (nor recoverByRevokingAudioMime) ever
    // sees a failure — the user just loses the sound, and only a seek brings it back. What the seek actually
    // cures is a single thing: it releases the AudioOutput and the next buffer opens a fresh AudioTrack,
    // which the resume then starts — that start is what makes a receiver re-lock. Everything else a seek
    // does is collateral, and expensive: it repositions the source, and with no back buffer configured the
    // sample queues hold no keyframe at the target, so the whole look-ahead is thrown away and re-read.
    // Recreating the track needs none of that, so ask for exactly it — disable the audio track type, then
    // put it back. That path (reselectTracksInternal → MediaPeriodHolder.applyTrackSelection →
    // ProgressiveMediaPeriod.selectTracks) retains the video stream untouched, and the re-enable re-reads
    // the audio from the sample queue that is still in memory (selectTracks seeks inside the queue and only
    // falls back to the source if that fails), so nothing is lost and nothing re-loads. It is the same
    // machinery as the app's own audio-track menu. Restoring has to wait for onTracksChanged, because a
    // reselect reads the selector's current parameters and skips equivalent results: two calls made back to
    // back can both land after the restore and cancel each other out. Posted rather than run inline from the
    // listener, which is dispatched inside player.play() / player.pause() — there isScrubbing is not set yet.
    private boolean audioRestartInFlight;
    private final Runnable passthroughRestartRunnable = this::restartPassthroughAudio;
    // Every way playback can stop leaves the output stale: a pause and a rebuffer stop the AudioTrack
    // (RendererHolder.stop() -> MediaCodecAudioRenderer.onStopped() -> audioSink.pause(), reached from both),
    // a transient audio-focus suppression does the same, and a seek releases it outright
    // (MediaCodecAudioRenderer.onPositionReset -> audioSink.flush()). Hence one latch for all of them,
    // and it is spent only while playback is actually wanted. That condition is the whole fix: a reselect made
    // while paused ends in enableRenderer computing playing = shouldPlayWhenReady() && READY, so
    // RendererHolder.start() is skipped and the fresh AudioTrack handleBuffer builds is left *unstarted*. It
    // then idles for the length of the pause — the box's screensaver comes on, the platform puts the direct
    // output into standby — and merely starting an already-built track does not renegotiate the HDMI format,
    // so the sound never comes back. Recreating while playing starts the track inside the same handleBuffer
    // pass, which is why a seek cures what a second pause cannot. Only for a seek within the same item — an
    // item change brings its own fresh output, and tearing that one down is how a just-started stream ends up
    // silent.
    private boolean audioRestartPending;
    // Retry budget for one request: restartPassthroughAudio drops nothing on a transient blocker, it waits.
    private int audioRestartRetries;
    // A recreate is in progress and its own interruption of playback must not be read as a stall. Without
    // this the trigger feeds itself: the reselect stops the audio renderer, playback stops, that arms the
    // latch, and the next start recreates again — forever. Causal rather than timed on purpose. A window
    // measured from when the reselect was requested only holds while the playback thread answers promptly,
    // and the boxes this feature is for are the ones that do not (see blockHeavyMkvAudio); this is cleared by
    // the return to playing itself, however late that is.
    private boolean audioRestartSettling;
    // Whether the current item has ever actually played. The first start of an item opens its own fresh
    // output, so there is nothing to re-lock — and recreating the track at that exact instant is what left
    // roughly one opening in three with no sound at all, twice. Spending the latch waits for a real second
    // start, which is the only kind that follows a stopped AudioTrack.
    private boolean audioEverStarted;
    public boolean frameRendered;
    private boolean alive;
    public static boolean focusPlay = false;
    private Uri nextUri;
    static boolean isTvBox;
    public static boolean locked = false;
    private Thread nextUriThread;
    private Thread segmentFinderThread;
    public Thread frameRateSwitchThread;

    public static boolean restoreControllerTimeout = false;
    public static boolean shortControllerTimeout = false;

    final Rational rationalLimitWide = new Rational(239, 100);
    final Rational rationalLimitTall = new Rational(100, 239);

    static final String API_POSITION = "position";
    static final String API_DURATION = "duration";
    static final String API_RETURN_RESULT = "return_result";
    static final String API_SUBS = "subs";
    static final String API_SUBS_ENABLE = "subs.enable";
    static final String API_SUBS_NAME = "subs.name";
    static final String API_TITLE = "title";
    static final String API_THUMBNAIL = "thumbnail";
    static final String API_SEGMENTS = "segments";
    static final String API_HEADERS = "headers";
    static final String API_VIDEO_LIST = "video_list";
    static final String API_VIDEO_LIST_NAME = "video_list.name";
    static final String API_VIDEO_LIST_FILENAME = "video_list.filename";
    static final String API_VIDEO_LIST_THUMBNAIL = "video_list.thumbnail";
    static final String API_VIDEO_LIST_SEGMENTS = "video_list.segments";
    static final String API_VIDEO_LIST_SEASON = "video_list.season";
    static final String API_VIDEO_LIST_EPISODE = "video_list.episode";
    static final String API_VIDEO_LIST_IMDB_ID = "video_list.imdb_id";
    static final String API_VIDEO_LIST_ID = "video_list.id";
    static final String API_VIDEO_LIST_SUBTITLES = "video_list.subtitles";
    static final String API_SEASON = "season";
    static final String API_EPISODE = "episode";
    static final String API_IMDB_ID = "imdb_id";
    static final String API_ID = "id";
    static final String API_END_BY = "end_by";
    // Manual video-quality contract (LAMPA -> player): parallel label/url arrays for the current item,
    // and per-episode variants keyed "<prefix>.$index" (mirrors the video_list.* pattern).
    static final String API_QUALITY_LEVELS = "quality_levels";
    static final String API_QUALITY_URLS = "quality_urls";
    static final String API_VIDEO_LIST_QUALITY_LEVELS = "video_list.quality_levels";
    static final String API_VIDEO_LIST_QUALITY_URLS = "video_list.quality_urls";
    // Everything an api session keeps in RAM (Prefs is non-persistent under apiAccess), handed to the
    // system as one nested Bundle so a kill while backgrounded does not come back to the launch intent's
    // stale `position`. See onSaveInstanceState()/restoreApiSession().
    private static final String STATE_API_SESSION = "apiSession";
    boolean apiAccess;
    boolean apiAccessPartial;
    String apiTitle;
    Uri apiThumbnailUri;
    String apiSegments;
    String[] apiHeaders;
    final List<MediaItem> apiMediaItems = new ArrayList<>();
    final List<String> apiPlaylistSegments = new ArrayList<>();
    int apiPlaylistStartIndex;
    // Per-episode resume positions for non-persistent playlist sessions (in-session only). One slot per
    // playlist item aligned by index; null when there is no playlist. Kept off the player so it survives
    // an onStop/release rebuild. See onPositionDiscontinuity()/savePlayer().
    long[] apiPlaylistPositions;
    // Episode metadata received via the launch Intent (from LAMPA, com.justplus.player branch). Stored for
    // now; not consumed yet. apiSeason/apiEpisode are -1 when absent; the per-item lists hold null.
    int apiSeason = -1;
    int apiEpisode = -1;
    String apiImdbId;
    // TMDB id (LAMPA's "id" extra). Used as a fallback for online skip-segment lookup when no imdb id
    // is supplied; series vs movie is decided by whether season/episode are present.
    String apiTmdbId;
    final List<Integer> apiPlaylistSeasons = new ArrayList<>();
    final List<Integer> apiPlaylistEpisodes = new ArrayList<>();
    final List<String> apiPlaylistNames = new ArrayList<>();
    final List<String> apiPlaylistImdbIds = new ArrayList<>();
    final List<String> apiPlaylistTmdbIds = new ArrayList<>();
    // The title picked by hand in the subtitle search, overriding whatever the launcher sent. Session
    // scoped and deliberately the title only, never the episode: the next item of the same playlist is
    // the same series, and its episode number still comes from its own name.
    private String manualTmdbId;
    // Whether that title is a film. MediaId reads movie-vs-series off the season number alone, so the
    // type TMDB reported has to arrive as a season — otherwise a series nobody could parse an episode
    // out of is asked about as a film, which is the one case this whole dialog exists for.
    private boolean manualMovie;
    // The episode picked along with it, and the playlist item it was picked for. The title outlives that
    // item and the episode does not: the next file of the same series is the same title and a different
    // episode, which its own name (or, failing that, the whole-season query) answers better than this.
    private int manualSeason = -1;
    private int manualEpisode = -1;
    private int manualIndex = -1;
    // The aired run of that series in the numbering the sources use, and where in it the pick landed.
    // Kept so the rest of the playlist can be lined up against it without having to know which
    // numbering the launcher wrote its own season and episode numbers in.
    private List<TitleSearch.Episode> manualEpisodes;
    private int manualAbsolute = -1;

    /** Characters before the live title search starts asking; below this every name matches. */
    private static final int TITLE_QUERY_MIN = 3;
    /** Long enough that typing a name is one request rather than one per letter. */
    private static final long TITLE_QUERY_DEBOUNCE_MS = 400;
    /** Bumped on every keystroke so a slow reply cannot land on a query nobody is looking at. */
    private int titleSearchGeneration;
    // Manual quality selection (LAMPA quality-switching port). Per-episode label->url maps aligned by
    // index with apiMediaItems; apiSingleQuality holds the top-level map for a single (non-playlist) video.
    // Maps are empty when the sender supplied no quality variants.
    final List<LinkedHashMap<String, String>> apiPlaylistQuality = new ArrayList<>();
    LinkedHashMap<String, String> apiSingleQuality = new LinkedHashMap<>();
    // Current manual choice — in-session only, never persisted.
    int selectedVideoQualityMode = VideoQualityChoice.MODE_AUTO;
    TrackGroup selectedVideoTrackGroup;
    int selectedVideoTrackIndex = -1;
    // Sticky quality across auto-next: number of lines of the last chosen SOURCE label (0 = none).
    int stickyQualityLines;
    // Skip-segment timing offset (seconds) — in-session only, never persisted; applies to all
    // playlist items and is reset on a new media session (resetApiAccess).
    private double skipOffsetSec = 0;
    // True once any skip segment has appeared this session; keeps the offset button available
    // afterwards even on an item that itself has no segments.
    private boolean skipSeenThisSession;
    // Shared by the skip and the subtitle offset panel — one shape, one range.
    private static final double OFFSET_MAX_SEC = 30;   // ± range of the offset slider
    private static final double OFFSET_STEP_SEC = 0.25; // fine step (± buttons / D-pad)
    // Subtitle timing offset (seconds, positive = subtitles later) — session-only like the skip offset
    // above. Applied by SubtitleOffsetRenderer, which is rebuilt with the player and re-seeded from here.
    private double subtitleOffsetSec = 0;
    private SubtitleOffset subtitleOffset;
    // The external subtitle file taken over from the renderer, and which URI it was read from. Parsed
    // once per track choice on a worker; kept across player rebuilds, dropped with the media session.
    private SubtitleTimeline subtitleTimeline;
    private Uri subtitleTimelineUri;
    // A subtitle shown without a track of its own, painted from the file by SubtitleOffset: attaching it
    // as a track means re-preparing the player, which on an HTTP stream costs a re-open of the media and
    // seconds of re-buffering — for a file the app found by itself, unasked, mid-playback.
    // ponytail: switch a painted subtitle off and a later search that hits the disk cache can paint it
    // again. Remembering "the viewer said no to this file" needs a second field; not worth it until
    // someone hits it (subtitleSearchStarted and the miss TTL cover it in practice).
    private Uri paintedSubtitleUri;
    // Set before a reinitialisation that must not auto-play — a SOURCE switch, or any rebuild caused by
    // returning to the foreground (initializePlayer otherwise force-plays under apiAccess or at position
    // zero). Consumed once inside initializePlayer.
    boolean sourceSwitchKeepPaused;
    List<MediaItem.SubtitleConfiguration> apiSubs = new ArrayList<>();
    boolean intentReturnResult;
    boolean playbackFinished;

    DisplayManager displayManager;
    DisplayManager.DisplayListener displayListener;
    SubtitleFinder subtitleFinder;

    Runnable barsHider = () -> {
        if (playerView != null && !controllerVisible) {
            Utils.toggleSystemUi(PlayerActivity.this, playerView, false);
        }
    };

    static final long SKIP_POLL_INTERVAL_MS = 250;
    // How early the Skip button is offered before a segment actually starts, so it is already on screen
    // when the intro's first frames arrive. Only the button is pre-shown; auto-skip waits for the
    // segment itself, or it would silently cut the seconds of real content that still precede it.
    static final double SKIP_LEAD_SEC = 3;
    // How long a timed pill stays up: the "undo" offer after a jump, and brief mode's Skip offer. The
    // pref_skip_mode_brief option names these seconds out loud ("Skip button for 3 seconds"), so changing
    // this means changing that string in every locale too.
    static final long SKIP_NOTICE_MS = 5000;
    // Faint groove the pill's countdown underline drains along; transparent when there is no underline.
    static final int SKIP_PILL_GROOVE_COLOR = 0x33FFFFFF;
    // Segment highlights (see CustomDefaultTimeBar): a *_FILL band across the segment plus a crisp boundary
    // hairline in the lighter *_HIGHLIGHT colour. Three-colour timeline system — coral = playback (hue 354,
    // @color/timebar_played), blue = skip, amber = ad (hue 38). The blue (hue 192) sits opposite the warm
    // coral, so it never merges over the played track and stays legible over the dark unplayed track. The
    // skip band is opaque, so it reads as the same blue over both.
    static final int SKIP_HIGHLIGHT_COLOR = 0xFFEAF6FF;
    static final int SKIP_FILL_COLOR = 0xFF0696BB;
    static final int AD_HIGHLIGHT_COLOR = 0xFFFFD27A;
    static final int AD_FILL_COLOR = 0xC7FFA000;
    // Watch together: one room per screen, alive across the player rebuilds a session accumulates.
    TogetherManager together;
    /** True only while the room's own media change is being applied — see checkRoomMedia. */
    private boolean applyingRoomMedia;
    /** Set for the one transition a skip off the end of an episode causes. Media3 calls that a seek,
     *  because it is one, but nobody chose the episode — so a room follows it as an end of media. */
    private boolean steppedBySkip;
    private TextView roomBadge;

    SkipManager skipManager;
    boolean skipBuilt;
    Button buttonSkip;
    ClipDrawable skipButtonProgress;

    /**
     * What the floating skip pill is currently offering — which is also what a tap (or OK on a remote)
     * does. All three states share one view, so they share its place on screen, its look and its focus:
     * {@link #SKIP} offers to jump, {@link #CANCEL} offers to refuse an automatic jump that is about to
     * happen, {@link #UNDO} offers to take one back.
     */
    private enum SkipPill { NONE, SKIP, CANCEL, UNDO }

    private SkipPill skipPill = SkipPill.NONE;
    private Drawable skipIconForward;
    private Drawable skipIconKeep;
    private Drawable skipIconBack;
    private GradientDrawable skipPillGroove;
    // Top-center pill shown while hold-to-speed is active. Non-clickable so it never intercepts the hold.
    TextView speedBoostIndicator;
    private Drawable speedBoostIconForward;
    private Drawable speedBoostIconRewind;
    final Runnable skipPillHider = new Runnable() {
        @Override
        public void run() {
            hideSkipPill();
        }
    };
    SkipSegment pendingSkip;
    // True while the current item's segments come from the launch Intent: those are authoritative, so
    // the online lookup never replaces them (it does replace its own earlier, less certain results).
    private boolean skipSourceFromIntent;
    // Bumped whenever a lookup is cancelled or superseded; a callback carrying an older generation is a
    // late arrival from an abandoned lookup and is dropped.
    private int segmentFetchGeneration;
    // The next playlist item's segments are warmed into the finder's cache once per item.
    private boolean skipNextPrefetched;
    // What an online subtitle search has already been started for. onTracksChanged fires more than
    // once per item — a sideloaded subtitle is itself a track change — and every extra pass would be a
    // request nobody asked for.
    private String subtitleSearchStarted;
    // The search is cancelled the way the segment finder is, and for a sharper reason: `player` is
    // static, so a worker outliving its session would hand its subtitle to whatever is playing by then.
    private Thread subtitleSearchThread;
    private volatile int subtitleSearchGeneration;
    // Automatic-skip undo: where the jump started, where it landed, and — once the user undid it — the
    // position up to which auto-skip stays disarmed so the same stretch is not skipped straight again.
    private long skipUndoFromMs = C.TIME_UNSET;
    private long skipUndoToMs = C.TIME_UNSET;
    private long skipUndoneUntilMs = C.TIME_UNSET;
    // End of the segment the pill is currently announcing ahead of an automatic skip, or TIME_UNSET when
    // the pill is not a heads-up. Doubles as the tap's meaning: heads-up = refuse, otherwise = undo.
    private long skipHeadsUpEndMs = C.TIME_UNSET;
    // Seconds currently written on the pill, so a countdown is only repainted when it ticks and not on
    // every 250 ms poll.
    private int skipPillSecs;
    // When the post-skip notice is due to disappear, so its underline can drain like the other states'.
    private long skipNoticeHideAtMs;
    // Brief mode: when the timed Skip offer is due to go. Ownership of the pill is derived from it rather
    // than latched (see skipFlashActive), so anything that takes the pill over ends the flash by itself.
    private long skipFlashEndMs;
    // Brief mode: end of the segment whose one automatic offer has already been made. Keyed on the position
    // and not the segment object for the reason spelled out at autoSkipUndone — a segment can come back
    // re-derived, with its once-only flags cleared.
    private long skipBriefFlashedUntilMs = C.TIME_UNSET;
    // Confirm key whose ACTION_UP must be swallowed after triggering a Skip on its ACTION_DOWN (TV).
    private int skipKeyUpToConsume = 0;
    final Runnable skipRunnable = new Runnable() {
        @Override
        public void run() {
            skipTick();
            if (player != null && player.isPlaying()) {
                playerView.postDelayed(this, SKIP_POLL_INTERVAL_MS);
            }
        }
    };

    final Runnable endsAtRunnable = new Runnable() {
        @Override
        public void run() {
            updateEndsAt();
            // The stats panel needs the same once-a-second tick over the same lifetime (controls visible),
            // so it rides this one rather than starting a second timer.
            updateStats();
            if (controllerVisible) {
                playerView.postDelayed(this, 1000);
            }
        }
    };

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Rotate ASAP, before super/inflating to avoid glitches with activity launch animation
        mPrefs = new Prefs(this);
        systemVolume = mPrefs.systemVolume;
        playerVolume = mPrefs.volume;
        // Boost is session state kept in statics, so a launch must not inherit it from the last one
        boostLevel = 0;
        boostWarned = false;
        // Only when something is actually going to play: a launcher start opens on the empty state, and the
        // orientation preference is about the video, so there is nothing to rotate for there. Doing it here
        // rather than leaving it to showEmptyState below avoids launching landscape and flipping back.
        if (getIntent().getData() != null || Intent.ACTION_SEND.equals(getIntent().getAction())) {
            Utils.setOrientation(this, mPrefs.orientation);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }

        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT == 28 && Build.MANUFACTURER.equalsIgnoreCase("xiaomi") &&
                (Build.DEVICE.equalsIgnoreCase("oneday") || Build.DEVICE.equalsIgnoreCase("once"))) {
            setContentView(R.layout.activity_player_textureview);
        } else {
            setContentView(R.layout.activity_player);
        }

        if (Build.VERSION.SDK_INT >= 31) {
            Window window = getWindow();
            if (window != null) {
                window.setDecorFitsSystemWindows(false);
                WindowInsetsController windowInsetsController = window.getInsetsController();
                if (windowInsetsController != null) {
                    // On Android 12 BEHAVIOR_DEFAULT allows system gestures without visible system bars
                    windowInsetsController.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_DEFAULT);
                }
            }
        }

        isTvBox = Utils.isTvBox(this);
        ui = UiMetrics.of(this, isTvBox);

        // Android 13+ delivers Back through OnBackInvokedDispatcher instead of onBackPressed(), and from
        // targetSdk 36 the manifest opt-out is ignored — with no callback of our own the system finishes
        // the task without ever asking the activity. Register one so onBackPressed() stays the single
        // place that decides what Back means on every API level.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::onBackPressed);
        }

        if (isTvBox) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        }

        final Intent launchIntent = getIntent();
        final String action = launchIntent.getAction();
        final String type = launchIntent.getType();

        if ("com.brouken.player.action.SHORTCUT_VIDEOS".equals(action)) {
            openFile(Utils.getMoviesFolderUri());
        } else if (handleRoomIntent(launchIntent)) {
            // An invite link carries only a room code; what to play arrives over its channel.
        } else if (Intent.ACTION_SEND.equals(action) && "text/plain".equals(type)) {
            String text = launchIntent.getStringExtra(Intent.EXTRA_TEXT);
            if (text != null) {
                final Uri parsedUri = Uri.parse(text);
                if (parsedUri.isAbsolute()) {
                    mPrefs.updateMedia(this, parsedUri, null);
                    focusPlay = true;
                }
            }
        } else if (launchIntent.getData() != null) {
            handleViewIntent(launchIntent);
        } else {
            // Nothing came in — a launcher start opens on the empty state instead of resuming
            // whatever was last watched.
            mPrefs.suppressResume = true;
        }
        // After the intent, so a session being restored wins over the launch extras it was started with.
        restoreApiSession(savedInstanceState);

        coordinatorLayout = findViewById(R.id.coordinatorLayout);
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        playerView = findViewById(R.id.video_view);
        exoPlayPause = findViewById(R.id.exo_play_pause);
        // Brand hero: the central Play/Pause sits on a disc (inset from the large tap target) carrying the
        // icon's ramp, with a white glyph. Doubles as a contrast anchor on bright frames, where a bare
        // white glyph washes out. TR_BL is the direction the ramp runs in the mark itself.
        final GradientDrawable playDisc = new GradientDrawable(GradientDrawable.Orientation.TR_BL,
                new int[]{ContextCompat.getColor(this, R.color.brand_ramp_start),
                        ContextCompat.getColor(this, R.color.brand_ramp_end)});
        playDisc.setShape(GradientDrawable.OVAL);
        exoPlayPause.setBackground(new InsetDrawable((Drawable) playDisc, ui.heroInset()));
        // Hero size scales per device class (phone = 90dp, unchanged; larger on tablet/TV). Overrides the
        // Media3 style's exo_icon_size so the transport isn't tiny on a 10-foot screen.
        final ViewGroup.LayoutParams heroLp = exoPlayPause.getLayoutParams();
        heroLp.width = ui.heroBox();
        heroLp.height = ui.heroBox();
        exoPlayPause.setLayoutParams(heroLp);
        exoPlayPause.setImageTintList(ColorStateList.valueOf(Color.WHITE));
        // Replacing the button background drops the D-pad focus / touch-press highlight, so re-add both as a
        // foreground — critical for TV navigation, harmless on touch. The ripple carries its own oval mask, so
        // no outline clip is needed (which would also cut off the focus ring sitting outside the disc).
        exoPlayPause.setForeground(discFocusForeground(ui.heroInset()));
        loadingProgressBar = findViewById(R.id.loading);
        // Keep the loading ring proportional to the hero it overlays.
        final ViewGroup.LayoutParams spinnerLp = loadingProgressBar.getLayoutParams();
        spinnerLp.width = ui.spinnerSize();
        spinnerLp.height = ui.spinnerSize();
        loadingProgressBar.setLayoutParams(spinnerLp);
        // The ring holds the remote's focus while it stands in for play/pause (see parkFocusOnLoadingRing),
        // so it must not draw a focus rectangle of its own: having no stateful background, it would otherwise
        // get the framework's default highlight painted around the spinner.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            loadingProgressBar.setDefaultFocusHighlightEnabled(false);
        }
        loadingSpeedView = findViewById(R.id.loading_speed);
        loadingSpeedView.setTextSize(TypedValue.COMPLEX_UNIT_SP, ui.textSkip());
        // Just below the ring, whatever size the ring is on this device.
        final FrameLayout.LayoutParams speedLp = (FrameLayout.LayoutParams) loadingSpeedView.getLayoutParams();
        speedLp.topMargin = ui.spinnerSize() / 2 + ui.dpS(12);
        loadingSpeedView.setLayoutParams(speedLp);
        exoPrev = findViewById(R.id.exo_prev);
        exoNext = findViewById(R.id.exo_next);
        setupEpisodeNavButtons();

        playerView.setShowNextButton(false);
        playerView.setShowPreviousButton(false);
        playerView.setShowFastForwardButton(false);
        playerView.setShowRewindButton(false);

        playerView.setRepeatToggleModes(Player.REPEAT_MODE_ONE);

        playerView.setControllerHideOnTouch(false);
        playerView.setControllerAutoShow(true);

        ((DoubleTapPlayerView)playerView).setDoubleTapEnabled(false);

        timeBar = playerView.findViewById(R.id.exo_progress);
        timeBar.addListener(new TimeBar.OnScrubListener() {
            @Override
            public void onScrubStart(TimeBar timeBar, long position) {
                if (player == null) {
                    return;
                }
                restorePlayState = player.isPlaying();
                if (restorePlayState) {
                    player.pause();
                }
                scrubbingNoticeable = false;
                isScrubbing = true;
                frameRendered = true;
                playerView.setControllerShowTimeoutMs(-1);
                scrubbingStart = player.getCurrentPosition();
                player.setSeekParameters(SeekParameters.CLOSEST_SYNC);
                reportScrubbing(position);
            }

            @Override
            public void onScrubMove(TimeBar timeBar, long position) {
                reportScrubbing(position);
            }

            @Override
            public void onScrubStop(TimeBar timeBar, long position, boolean canceled) {
                playerView.setCustomErrorMessage(null);
                isScrubbing = false;
                if (restorePlayState) {
                    restorePlayState = false;
                    playerView.setControllerShowTimeoutMs(PlayerActivity.CONTROLLER_TIMEOUT);
                    if (player != null) {
                        player.setPlayWhenReady(true);
                    }
                }
            }
        });

        buttonOpen = new ImageButton(this, null, 0, R.style.ExoStyledControls_Button_Bottom);
        buttonOpen.setImageResource(R.drawable.ic_folder_open_24dp);
        buttonOpen.setId(View.generateViewId());
        buttonOpen.setContentDescription(getString(R.string.button_open));

        buttonOpen.setOnClickListener(view -> openFile(mPrefs.mediaUri));

        buttonOpen.setOnLongClickListener(view -> {
            if (!isTvBox && mPrefs.askScope) {
                askForScope(true, false);
            } else {
                loadSubtitleFile(mPrefs.mediaUri);
            }
            return true;
        });

        buttonPlaylist = new ImageButton(this, null, 0, R.style.ExoStyledControls_Button_Bottom);
        buttonPlaylist.setImageResource(R.drawable.ic_playlist_24dp);
        buttonPlaylist.setId(View.generateViewId());
        buttonPlaylist.setContentDescription("Playlist");
        buttonPlaylist.setVisibility(View.GONE);
        buttonPlaylist.setOnClickListener(view -> showPlaylistDialog());

        buttonQuality = new ImageButton(this, null, 0, R.style.ExoStyledControls_Button_Bottom);
        buttonQuality.setImageResource(R.drawable.ic_high_quality_24dp);
        buttonQuality.setImageTintList(ContextCompat.getColorStateList(this, R.color.control_icon_tint));
        buttonQuality.setId(View.generateViewId());
        buttonQuality.setContentDescription(getString(R.string.button_quality));
        buttonQuality.setVisibility(View.GONE);
        buttonQuality.setOnClickListener(view -> showQualityDialog());

        buttonAudio = new ImageButton(this, null, 0, R.style.ExoStyledControls_Button_Bottom);
        buttonAudio.setImageResource(R.drawable.ic_audiotrack_24dp);
        buttonAudio.setId(View.generateViewId());
        buttonAudio.setContentDescription(getString(R.string.button_audio_track));
        buttonAudio.setVisibility(View.GONE);
        buttonAudio.setOnClickListener(view -> showAudioDialog());

        buttonMore = new ImageButton(this, null, 0, R.style.ExoStyledControls_Button_Bottom);
        buttonMore.setImageResource(R.drawable.ic_settings_24dp);
        buttonMore.setId(View.generateViewId());
        buttonMore.setContentDescription(getString(R.string.button_more));
        buttonMore.setOnClickListener(view -> showMoreMenu());
        buttonMore.setOnLongClickListener(view -> {
            openSettings();
            return true;
        });

        // The only ambient sign that a release is waiting. It exists solely while one is (see
        // refreshUpdateButton) and lives in the controls, which are on screen only when the user asked for
        // them — so nothing is ever interrupted, and the brand colour is what makes it findable anyway.
        buttonUpdate = new ImageButton(this, null, 0, R.style.ExoStyledControls_Button_Bottom);
        buttonUpdate.setImageResource(R.drawable.ic_update_24dp);
        buttonUpdate.setId(View.generateViewId());
        buttonUpdate.setContentDescription(getString(R.string.button_update));
        buttonUpdate.setVisibility(View.GONE);
        // Tint the glyph, never the background: a background on these buttons swallows it.
        buttonUpdate.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.brand)));
        buttonUpdate.setOnClickListener(view -> {
            final UpdateInfo info = mPrefs.updatePending;
            if (info != null) {
                UpdateUi.showAvailableDialog(this, info, skipUpdate(info),
                        player != null && player.isPlaying());
            }
        });

        buttonSkipOffset = new ImageButton(this, null, 0, R.style.ExoStyledControls_Button_Bottom);
        buttonSkipOffset.setImageResource(R.drawable.ic_skip_offset_24dp);
        buttonSkipOffset.setId(View.generateViewId());
        buttonSkipOffset.setContentDescription(getString(R.string.button_skip_offset));
        buttonSkipOffset.setVisibility(View.GONE);
        buttonSkipOffset.setOnClickListener(view -> showSkipOffsetDialog());

        if (Utils.isPiPSupported(this)) {
            // TODO: Android 12 improvements:
            // https://developer.android.com/about/versions/12/features/pip-improvements
            mPictureInPictureParamsBuilder = new PictureInPictureParams.Builder();
            boolean success = updatePictureInPictureActions(R.drawable.ic_play_arrow_24dp, R.string.exo_controls_play_description, CONTROL_TYPE_PLAY, REQUEST_PLAY);

            if (success) {
                buttonPiP = new ImageButton(this, null, 0, R.style.ExoStyledControls_Button_Bottom);
                buttonPiP.setContentDescription(getString(R.string.button_pip));
                buttonPiP.setImageResource(R.drawable.ic_picture_in_picture_alt_24dp);

                buttonPiP.setOnClickListener(view -> enterPiP());
            }
        }

        buttonAspectRatio = new ImageButton(this, null, 0, R.style.ExoStyledControls_Button_Bottom);
        buttonAspectRatio.setId(Integer.MAX_VALUE - 100);
        buttonAspectRatio.setContentDescription(getString(R.string.button_crop));
        updatebuttonAspectRatioIcon();
        buttonAspectRatio.setOnClickListener(view -> {
            cycleAspectMode();
            resetHideCallbacks();
        });
        if (isTvBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            buttonAspectRatio.setOnLongClickListener(v -> {
                scaleStart();
                updatebuttonAspectRatioIcon();
                return true;
            });
        } else {
            buttonAspectRatio.setOnLongClickListener(v -> {
                showAspectModePicker();
                return true;
            });
        }
        buttonRotation = new ImageButton(this, null, 0, R.style.ExoStyledControls_Button_Bottom);
        buttonRotation.setContentDescription(getString(R.string.button_rotate));
        updateButtonRotation();
        buttonRotation.setOnClickListener(view -> cycleOrientation());

        buttonLock = new ImageButton(this, null, 0, R.style.ExoStyledControls_Button_Bottom);
        buttonLock.setImageResource(R.drawable.ic_lock_24dp);
        buttonLock.setImageTintList(ContextCompat.getColorStateList(this, R.color.control_icon_tint));
        buttonLock.setId(View.generateViewId());
        buttonLock.setContentDescription(getString(R.string.button_lock));
        buttonLock.setOnClickListener(view -> playerView.toggleLock());

        final int titleViewPaddingHorizontal = ui.gridH();
        final int titleViewPaddingVertical = getResources().getDimensionPixelOffset(R.dimen.exo_styled_bottom_bar_time_padding);
        FrameLayout centerView = playerView.findViewById(R.id.exo_controls_background);

        topInfoPanel = new LinearLayout(this);
        topInfoPanel.setOrientation(LinearLayout.HORIZONTAL);
        topInfoPanel.setGravity(Gravity.TOP);
        // Soft top scrim (dark → transparent) instead of a flat opaque band, so the video breathes under the header.
        topInfoPanel.setBackgroundResource(R.drawable.scrim_top);
        topInfoPanel.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        topInfoPanel.setPadding(titleViewPaddingHorizontal, titleViewPaddingVertical, titleViewPaddingHorizontal, titleViewPaddingVertical);
        topInfoPanel.setVisibility(View.GONE);

        posterSlot = new FrameLayout(this);
        // Poster anchors the left column and is sized to roughly match the right column's two rows (time +
        // icons) so neither side leaves a void. Bumped on TV for 10-foot legibility.
        final LinearLayout.LayoutParams slotParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, ui.posterHeight());
        slotParams.setMarginEnd(ui.dpS(16));
        slotParams.gravity = Gravity.TOP;
        posterSlot.setLayoutParams(slotParams);
        posterSlot.setBackgroundColor(0xFF333333); // bg_placeholder_card
        final int posterCornerRadius = Utils.dpToPx(4);
        posterSlot.setClipToOutline(true);
        posterSlot.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), posterCornerRadius);
            }
        });
        posterSlot.setVisibility(View.GONE);

        posterView = new ImageView(this);
        posterView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.MATCH_PARENT));
        posterView.setAdjustViewBounds(true);
        posterView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        posterSlot.addView(posterView);

        posterPlaceholderView = new TextView(this);
        posterPlaceholderView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        posterPlaceholderView.setMinWidth(Utils.dpToPx(54));
        posterPlaceholderView.setGravity(Gravity.CENTER);
        posterPlaceholderView.setTextColor(0x80FFFFFF); // text_tertiary
        posterPlaceholderView.setTextSize(TypedValue.COMPLEX_UNIT_SP, ui.textPlaceholder());
        posterPlaceholderView.setTypeface(Typeface.DEFAULT_BOLD);
        posterPlaceholderView.setVisibility(View.GONE);
        posterSlot.addView(posterPlaceholderView);

        posterBadgeView = createPosterNumberBadge();
        posterSlot.addView(posterBadgeView);

        topInfoPanel.addView(posterSlot);

        // Left column of the header grid: title (row 1) over a single combined metadata line (row 2).
        final LinearLayout infoColumn = new LinearLayout(this);
        infoColumn.setOrientation(LinearLayout.VERTICAL);
        final LinearLayout.LayoutParams infoColumnParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        infoColumnParams.gravity = Gravity.TOP;
        infoColumnParams.setMarginEnd(Utils.dpToPx(16));
        infoColumn.setLayoutParams(infoColumnParams);

        titleView = new TextView(this);
        titleView.setTextColor(Color.WHITE);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, ui.textHeaderTitle());
        titleView.setMaxLines(1);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setTextDirection(View.TEXT_DIRECTION_LOCALE);
        infoColumn.addView(titleView);

        // Two meta lines: video (resolution · codec · HDR) and the audio track (label / codec / language).
        // The gaps are the design's, and they are what makes the text column as tall as the poster beside it,
        // so the two header columns end on the same line.
        videoInfoView = createInfoLine(ui.dpS(7));
        infoColumn.addView(videoInfoView);
        audioInfoView = createInfoLine(ui.dpS(3));
        infoColumn.addView(audioInfoView);

        topInfoPanel.addView(infoColumn);

        // Right block of the header, mirroring the left (poster + text column): a one-line time row on top, with
        // the display icons right-aligned directly beneath it — so their glyphs land on the same grid line as
        // the clock and the bottom-bar pill.
        final LinearLayout headerClockColumn = new LinearLayout(this);
        headerClockColumn.setOrientation(LinearLayout.VERTICAL);
        headerClockColumn.setGravity(Gravity.END);
        // Full height, so the icon row below can be pushed to the header's bottom line rather than trailing
        // the clock: the left column (poster, or the last meta line) is what sets that line.
        final LinearLayout.LayoutParams headerClockColumnParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
        headerClockColumnParams.gravity = Gravity.TOP;
        headerClockColumn.setLayoutParams(headerClockColumnParams);

        // Time row (row 1): "until …" then the clock on one line. The clock is the bold, right-pinned anchor,
        // so it never jumps sideways when the dynamically-computed end time appears/updates while loading.
        // No vertical gravity: that lets LinearLayout's baseline alignment sit the smaller end time on the
        // clock's baseline, instead of centring two different text sizes against each other.
        final LinearLayout timeRow = new LinearLayout(this);
        timeRow.setOrientation(LinearLayout.HORIZONTAL);
        final LinearLayout.LayoutParams timeRowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        timeRowLp.gravity = Gravity.END;
        timeRow.setLayoutParams(timeRowLp);

        endsAtView = new TextView(this);
        endsAtView.setTextColor(0xB3FFFFFF);
        // A step below the clock: the clock is the anchor, the end time is the qualifier next to it.
        endsAtView.setTextSize(TypedValue.COMPLEX_UNIT_SP, ui.textEndsAt());
        endsAtView.setVisibility(View.GONE);
        timeRow.addView(endsAtView);

        headerClock = new OutlineTextClock(this);
        headerClock.setFormat12Hour("h:mm a");
        headerClock.setFormat24Hour("HH:mm");
        // A step above the "until …" text but short of pure white, which read as too harsh; the black outline
        // and bold weight carry the rest of the legibility. The overlay clock must use the same value.
        headerClock.setTextColor(0xC2FFFFFF);
        headerClock.setTypeface(Typeface.DEFAULT_BOLD);
        headerClock.setTextSize(TypedValue.COMPLEX_UNIT_SP, ui.textClock());
        final LinearLayout.LayoutParams headerClockLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        headerClockLp.setMarginStart(Utils.dpToPx(6));
        headerClock.setLayoutParams(headerClockLp);
        timeRow.addView(headerClock);

        headerClockColumn.addView(timeRow);

        // All the slack goes between the two rows, so the icons ride the header's bottom line whatever the
        // left column's height turns out to be, instead of trailing the clock with a fixed gap.
        final View headerSpacer = new View(this);
        headerSpacer.setLayoutParams(new LinearLayout.LayoutParams(0, 0, 1f));
        headerClockColumn.addView(headerSpacer);

        // Display icons (row 2): aspect / PiP / rotation, right-aligned under the clock, bare — no pill behind
        // them. The nudge that lands their glyphs on the header's right and bottom grid lines is applied in the
        // controls assembly, where the button padding is known.
        // Populated in the controls assembly; empty on TV (those controls live in the bottom bar there).
        headerButtons = new LinearLayout(this);
        headerButtons.setOrientation(LinearLayout.HORIZONTAL);
        final LinearLayout.LayoutParams headerButtonsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        headerButtonsParams.gravity = Gravity.END;
        headerButtonsParams.topMargin = Utils.dpToPx(4);
        headerButtons.setLayoutParams(headerButtonsParams);
        headerClockColumn.addView(headerButtons);

        // Both header columns are top-aligned, and the title's ascent is taller than the time row's, so equal
        // tops leave the clock's baseline above the title's — the design has the two on one line. Push the
        // column down by the difference between the two first-baseline offsets, read from the paints so it
        // holds at any font scale.
        headerClockColumnParams.topMargin = Math.max(0,
                headerClock.getPaint().getFontMetricsInt().top
                        - titleView.getPaint().getFontMetricsInt().top);
        headerClockColumn.setLayoutParams(headerClockColumnParams);

        // This column asks for MATCH_PARENT height so the spacer can push the icon row onto the header's
        // bottom line. A LinearLayout ignores such a child when it works out how tall it has to be — a
        // MATCH_PARENT child contributes only its margins — so the header's height is decided by the text
        // column alone, and the column is then re-measured to exactly that. With no poster and one meta line
        // missing (a file with no audio track drops the audio line) that came out shorter than the clock plus
        // the icons, and the icon row was clipped to a 35px sliver of its 120px.
        //
        // So the floor goes on the text column, which is what the header measures: it may not end above the
        // line the icons need. It only ever grows the header where the text alone would not reach; with a
        // poster, or a full set of meta lines, the column is already taller and nothing changes.
        if (!isTvBox) {
            infoColumn.setMinimumHeight(headerClockColumnParams.topMargin
                    + headerClock.getLineHeight()          // the clock row this column sits beside
                    + headerButtonsParams.topMargin
                    + ui.clusterBox()                      // the icon row itself
                    + ui.clusterPad());                    // and the nudge that lands it on the grid line
        }

        topInfoPanel.addView(headerClockColumn);

        centerView.addView(topInfoPanel);

        // Skip button — a solid dark pill floating over the video (bottom-end), independent of the
        // controller. Modern TV focus: a coral ring + slight scale-up on focus (replacing the dated flat
        // grey selectableItemBackground wash), with the remaining-time countdown drawn as an underline
        // integrated into the pill rather than a detached bar below it. Label and glyph stay white; only
        // what counts time down — the underline and the seconds inside the label — is on the brand accent,
        // so the timing reads at a glance without costing the wording its contrast over a bright frame.
        final int skipCornerRadius = Utils.dpToPx(8);
        buttonSkip = new Button(this);
        buttonSkip.setText(R.string.button_skip);
        buttonSkip.setAllCaps(false);
        buttonSkip.setTextColor(Color.WHITE);
        buttonSkip.setTextSize(TypedValue.COMPLEX_UNIT_SP, ui.textSkip());
        buttonSkip.setTypeface(Typeface.DEFAULT_BOLD);
        buttonSkip.setMinHeight(0);
        buttonSkip.setMinimumHeight(0);
        // Extra bottom padding leaves room for the integrated countdown underline below the label.
        buttonSkip.setPadding(Utils.dpToPx(14), Utils.dpToPx(7), Utils.dpToPx(16), Utils.dpToPx(10));

        // Two glyphs, chosen by what the pill offers: forward for a jump, back for refusing or undoing one.
        final int skipIconSize = Utils.dpToPx(18);
        // One glyph per state, all three from the app's own 24dp set so they match in weight: forward to
        // jump, play to keep watching instead, back to return. Note exo_styled_controls_next is a
        // layer-list (controller gradient + inset glyph), not a glyph — as a compound drawable it drags
        // the gradient in and the white tint paints it, hence the bare ic_skip_next here.
        skipIconForward = ContextCompat.getDrawable(this, R.drawable.ic_skip_next);
        skipIconKeep = ContextCompat.getDrawable(this, R.drawable.ic_play_arrow_24dp);
        skipIconBack = ContextCompat.getDrawable(this, R.drawable.ic_skip_previous);
        for (Drawable glyph : new Drawable[]{skipIconForward, skipIconKeep, skipIconBack}) {
            if (glyph != null) {
                glyph.setBounds(0, 0, skipIconSize, skipIconSize);
            }
        }
        buttonSkip.setCompoundDrawablesRelative(skipIconForward, null, null, null);
        buttonSkip.setCompoundDrawablePadding(Utils.dpToPx(6));
        buttonSkip.setCompoundDrawableTintList(ColorStateList.valueOf(Color.WHITE));

        // Solid dark pill with the neutral white countdown underline baked into its background as inset layers.
        // (A separate MATCH_PARENT underline View resolves to the full screen width inside a wrap-content
        // FrameLayout, which stretched the whole floating unit across the screen — hence layers instead.)
        final int skipRingWidth = Utils.dpToPx(2);
        final int skipBarHeight = Utils.dpToPx(3);
        final int skipBarCorner = Utils.dpToPx(2);
        final GradientDrawable skipPillFill = new GradientDrawable();
        skipPillFill.setColor(Color.argb(0xF0, 0x16, 0x16, 0x16));
        skipPillFill.setCornerRadius(skipCornerRadius);
        skipPillGroove = new GradientDrawable();
        skipPillGroove.setColor(SKIP_PILL_GROOVE_COLOR);
        skipPillGroove.setCornerRadius(skipBarCorner);
        final GradientDrawable skipBarFill = new GradientDrawable();
        skipBarFill.setColor(brandColor()); // accent: this is a countdown, not decoration
        skipBarFill.setCornerRadius(skipBarCorner);
        skipButtonProgress = new ClipDrawable(skipBarFill, Gravity.START, ClipDrawable.HORIZONTAL);
        skipButtonProgress.setLevel(0);
        final LayerDrawable skipPillBackground = new LayerDrawable(
                new Drawable[]{skipPillFill, skipPillGroove, skipButtonProgress});
        // Pin the underline (track + draining fill) to the pill's bottom edge, inset from the corners.
        for (int layer = 1; layer <= 2; layer++) {
            skipPillBackground.setLayerGravity(layer, Gravity.BOTTOM);
            skipPillBackground.setLayerHeight(layer, skipBarHeight);
            skipPillBackground.setLayerInsetBottom(layer, Utils.dpToPx(5));
            skipPillBackground.setLayerInsetLeft(layer, skipCornerRadius);
            skipPillBackground.setLayerInsetRight(layer, skipCornerRadius);
        }
        buttonSkip.setBackground(skipPillBackground);
        buttonSkip.setOnClickListener(v -> {
            // While the screen is locked the pill must never act, whether tapped or activated via a TV
            // remote's confirm key (which routes through performClick()).
            if (PlayerActivity.locked) {
                return;
            }
            if (skipPill == SkipPill.CANCEL) {
                cancelUpcomingSkip();
            } else if (skipPill == SkipPill.UNDO) {
                undoSkip();
            } else if (pendingSkip != null && player != null) {
                final SkipSegment segment = pendingSkip;
                segment.skipped = true;
                hideSkipButton();
                skipSeekTo(segment);
                if (skipUndoOffered(false)) {
                    showSkipNotification(false);
                }
            }
        });

        // Add the pill straight to the coordinator, floating bottom-end. No wrapper view: the countdown
        // underline is baked into the button's own background, so the previous wrapping FrameLayout — which
        // stretched to full width inside the CoordinatorLayout and pinned the pill to the left edge — is gone.
        // Modern TV focus: a coral ring on the pill (state-driven stroke) plus a slight scale-up, replacing
        // the dated flat grey selectableItemBackground wash.
        buttonSkip.setOnFocusChangeListener((v, hasFocus) -> {
            skipPillFill.setStroke(hasFocus ? skipRingWidth : 0, brandColor());
            final float scale = hasFocus ? 1.06f : 1f;
            buttonSkip.animate().scaleX(scale).scaleY(scale).setDuration(150).start();
        });
        final CoordinatorLayout.LayoutParams skipButtonParams = new CoordinatorLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        skipButtonParams.gravity = Gravity.BOTTOM | Gravity.END;
        skipButtonParams.setMargins(0, 0, Utils.dpToPx(24), Utils.dpToPx(96));
        buttonSkip.setLayoutParams(skipButtonParams);
        buttonSkip.setVisibility(View.GONE);
        coordinatorLayout.addView(buttonSkip);

        // Hold-to-speed indicator: the same rounded dark pill as the skip pill (rate + direction arrows),
        // floating top-centre. Non-clickable so it never intercepts the hold.
        speedBoostIndicator = new TextView(this);
        speedBoostIndicator.setText("2.0×");
        speedBoostIndicator.setAllCaps(false);
        speedBoostIndicator.setTextColor(Color.WHITE);
        speedBoostIndicator.setTextSize(TypedValue.COMPLEX_UNIT_SP, ui.textSkip());
        speedBoostIndicator.setTypeface(Typeface.DEFAULT_BOLD);
        speedBoostIndicator.setGravity(Gravity.CENTER_VERTICAL);
        speedBoostIndicator.setPadding(Utils.dpToPx(14), Utils.dpToPx(9), Utils.dpToPx(16), Utils.dpToPx(9));
        speedBoostIndicator.setClickable(false);
        speedBoostIndicator.setFocusable(false);

        speedBoostIconForward = ContextCompat.getDrawable(this, R.drawable.exo_icon_fastforward);
        speedBoostIconRewind = ContextCompat.getDrawable(this, R.drawable.exo_icon_rewind);
        final int speedBoostIconSize = Utils.dpToPx(18);
        if (speedBoostIconForward != null)
            speedBoostIconForward.setBounds(0, 0, speedBoostIconSize, speedBoostIconSize);
        if (speedBoostIconRewind != null)
            speedBoostIconRewind.setBounds(0, 0, speedBoostIconSize, speedBoostIconSize);
        speedBoostIndicator.setCompoundDrawablesRelative(null, null, speedBoostIconForward, null);
        speedBoostIndicator.setCompoundDrawablePadding(Utils.dpToPx(6));
        speedBoostIndicator.setCompoundDrawableTintList(ColorStateList.valueOf(brandColor()));

        final GradientDrawable speedBoostBackground = new GradientDrawable();
        speedBoostBackground.setColor(Color.argb(0xF0, 0x16, 0x16, 0x16));
        speedBoostBackground.setCornerRadius(skipCornerRadius);
        speedBoostIndicator.setBackground(speedBoostBackground);

        final CoordinatorLayout.LayoutParams speedBoostParams = new CoordinatorLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        speedBoostParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        speedBoostParams.setMargins(0, Utils.dpToPx(28), 0, 0);
        speedBoostIndicator.setLayoutParams(speedBoostParams);
        speedBoostIndicator.setVisibility(View.GONE);
        coordinatorLayout.addView(speedBoostIndicator);

        // Persistent clock over the video, shown only when the controls (and thus the in-header clock) are
        // hidden and the "show clock" preference is on. It is positioned to exactly mirror the in-header
        // clock (see syncOverlayClockPosition), so toggling the controls swaps between the two clocks in the
        // same spot with no jump.
        overlayClock = new OutlineTextClock(this);
        overlayClock.setFormat12Hour("h:mm a");
        overlayClock.setFormat24Hour("HH:mm");
        // Same white as the header clock — the black outline keeps it readable over bright frames.
        overlayClock.setTextColor(0xC2FFFFFF);
        overlayClock.setTypeface(Typeface.DEFAULT_BOLD);
        // Must match the header clock size (see below) so the two line up exactly when controls toggle.
        overlayClock.setTextSize(TypedValue.COMPLEX_UNIT_SP, ui.textClock());
        final CoordinatorLayout.LayoutParams overlayClockLp = new CoordinatorLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        overlayClockLp.gravity = Gravity.TOP | Gravity.START;
        overlayClock.setLayoutParams(overlayClockLp);
        overlayClock.setVisibility(View.GONE);
        coordinatorLayout.addView(overlayClock);

        // Live playback stats, opened from the overflow menu. It rides the controls (updated by
        // endsAtRunnable, hidden by stopEndsAtUpdates), so it is never left sitting over the video, and it
        // carries no touch handling of its own — the left half of the screen is the brightness swipe zone.
        statsView = new TextView(this);
        statsView.setTextColor(0xB3FFFFFF);
        statsView.setTypeface(Typeface.MONOSPACE);
        statsView.setTextSize(TypedValue.COMPLEX_UNIT_SP, ui.textInfo());
        // Same corner as the lock button, the time pill and the poster — a floating box in this UI is rounded.
        final GradientDrawable statsBackground = new GradientDrawable();
        statsBackground.setColor(0x99000000);
        statsBackground.setCornerRadius(ui.pillCorner());
        statsView.setBackground(statsBackground);
        final int statsPadding = Utils.dpToPx(8);
        statsView.setPadding(statsPadding, statsPadding, statsPadding, statsPadding);
        final CoordinatorLayout.LayoutParams statsLp = new CoordinatorLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        // Mid-left: the only band the controls leave free, since the header and the bottom bar are on
        // screen whenever the panel is. The left margin is the shared content grid, set with the insets.
        statsLp.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
        statsView.setLayoutParams(statsLp);
        statsView.setVisibility(View.GONE);
        coordinatorLayout.addView(statsView);

        // Which room we are in. Floating, so it costs no row in the header or the bottom bar — both are
        // grids whose height a fourth line visibly changes — but tied to the controls (see updateRoomBadge),
        // so it is not standing over the film for the whole evening either. Bottom-start is the mirror of
        // the Skip pill's corner and the only band that stays free while the controls are up: the header is
        // above, the transport below, the stats panel is centred on the left edge.
        roomPill = new TextView(this);
        roomPill.setTextColor(0xE6FFFFFF);
        roomPill.setTextSize(TypedValue.COMPLEX_UNIT_SP, ui.textInfo());
        final GradientDrawable roomPillBackground = new GradientDrawable();
        roomPillBackground.setColor(0x99000000);
        roomPillBackground.setCornerRadius(ui.pillCorner());
        roomPill.setBackground(roomPillBackground);
        roomPill.setPadding(Utils.dpToPx(10), Utils.dpToPx(5), Utils.dpToPx(10), Utils.dpToPx(5));
        // The glyph is what makes it a room at a glance rather than a stray number; sized to the text so it
        // follows the font scale with it.
        final Drawable roomGlyph = ContextCompat.getDrawable(this, R.drawable.ic_together_24dp);
        if (roomGlyph != null) {
            final int glyphBox = Math.round(roomPill.getTextSize());
            roomGlyph.setBounds(0, 0, glyphBox, glyphBox);
            roomPill.setCompoundDrawablesRelative(roomGlyph, null, null, null);
            roomPill.setCompoundDrawablePadding(ui.dpS(6));
        }
        final CoordinatorLayout.LayoutParams roomPillLp = new CoordinatorLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        roomPillLp.gravity = Gravity.BOTTOM | Gravity.START;
        roomPill.setLayoutParams(roomPillLp);
        roomPill.setVisibility(View.GONE);
        coordinatorLayout.addView(roomPill);

        // The room itself reads in the pill above; this floats only for the one thing that pill cannot say
        // in time — that the pause was the room's doing and not the viewer's, which has to show while the
        // controls are down. It goes under the centre disc, on the loading rate's geometry, because that is
        // where the eye already is when the picture stops.
        roomBadge = new TextView(this);
        roomBadge.setTextColor(0xE6FFFFFF);
        roomBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, ui.textInfo());
        final GradientDrawable roomBackground = new GradientDrawable();
        roomBackground.setColor(0x99000000);
        roomBackground.setCornerRadius(ui.pillCorner());
        roomBadge.setBackground(roomBackground);
        roomBadge.setPadding(Utils.dpToPx(10), Utils.dpToPx(5), Utils.dpToPx(10), Utils.dpToPx(5));
        final CoordinatorLayout.LayoutParams roomLp = new CoordinatorLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        roomLp.gravity = Gravity.CENTER;
        // Clear of the hero's tap target, whatever size the hero is on this device.
        roomLp.topMargin = ui.heroBox() / 2 + ui.dpS(12);
        roomBadge.setLayoutParams(roomLp);
        roomBadge.setVisibility(View.GONE);
        coordinatorLayout.addView(roomBadge);


        // Whenever the in-header clock is (re)laid out, mirror its position onto the floating clock.
        headerClock.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> syncOverlayClockPosition());

        // Swipe-to-unlock bar, shown while the screen is locked: the lock icon is dragged to the right edge
        // to unlock. The only way out of the locked state, and touch only (the lock feature is not offered on
        // TV). Centered near the bottom.
        if (!isTvBox) {
            swipeToUnlock = new SwipeToUnlockView(this);
            swipeToUnlock.setVisibility(View.GONE);
            final CoordinatorLayout.LayoutParams swipeLp = new CoordinatorLayout.LayoutParams(
                    Utils.dpToPx(260), Utils.dpToPx(48));
            swipeLp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            swipeLp.bottomMargin = Utils.dpToPx(48);
            swipeToUnlock.setLayoutParams(swipeLp);
            swipeToUnlock.setOnUnlockListener(() -> playerView.toggleLock());
            swipeToUnlock.setOnStartTouchingListener(() -> {
                if (playerView != null) {
                    playerView.removeCallbacks(swipeHider);
                }
            });
            swipeToUnlock.setOnStopTouchingListener(this::rescheduleSwipeHide);
            coordinatorLayout.addView(swipeToUnlock);
        }

        if (Build.VERSION.SDK_INT >= 35) {
            getWindow().setNavigationBarContrastEnforced(false);
        }

        controlView = playerView.findViewById(R.id.exo_controller);

        // The right-hand slot of the bottom bar shows the total duration, or counts down what is left when
        // the setting says so. PlayerControlView writes that TextView itself, so rewrite it after every
        // progress tick — updateTimeline() ends with updateProgress(), so its own value never lingers.
        final TextView durationView = playerView.findViewById(R.id.exo_duration);
        final StringBuilder timeBuilder = new StringBuilder();
        final Formatter timeFormatter = new Formatter(timeBuilder, Locale.getDefault());
        controlView.setProgressUpdateListener((position, bufferedPosition) -> {
            final long duration = player == null ? C.TIME_UNSET : player.getContentDuration();
            if (duration == C.TIME_UNSET)
                return;
            durationView.setText(mPrefs != null && mPrefs.timeRemaining
                    ? "-" + Util.getStringForTime(timeBuilder, timeFormatter, Math.max(0, duration - position))
                    : Util.getStringForTime(timeBuilder, timeFormatter, duration));
        });
        controlView.setOnApplyWindowInsetsListener((view, windowInsets) -> {
            if (windowInsets != null) {
                if (Build.VERSION.SDK_INT >= 31) {
                    boolean visibleBars = windowInsets.isVisible(WindowInsets.Type.statusBars());
                    if (visibleBars && !controllerVisible) {
                        playerView.postDelayed(barsHider, 2500);
                    } else {
                        playerView.removeCallbacks(barsHider);
                    }
                }

                int insetLeft = windowInsets.getSystemWindowInsetLeft();
                int insetRight = windowInsets.getSystemWindowInsetRight();

                // Balance the horizontal insets: offset BOTH sides by the larger of the two so the header and
                // bottom-bar content stay symmetric even when only one side carries the status bar or a display
                // cutout (in landscape that side would otherwise get a much bigger margin — the lopsided look).
                // Applied as padding with no margin, so the scrim backgrounds still span the full width.
                // On TV all system insets are 0, so synthesize overscan-safe insets here — every edge-anchored
                // element (header, bottom bar, seek bar, Skip pill) keys off these, so the whole content grid
                // moves inward as a unit and stays aligned. overscanH/V are 0 on phone/tablet (no visual change).
                final int overscanV = ui.overscanV();
                final int insetH = Math.max(Math.max(insetLeft, insetRight), ui.overscanH());
                int paddingLeft = insetH;
                int marginLeft = 0;
                int paddingRight = insetH;
                int marginRight = 0;

                int bottomBarPaddingBottom = 0;
                int progressBarMarginBottom = 0;

                // Don't use exo_top (the built-in top scrim): it is a sibling of exo_controls_background and Media3
                // animates it on a different schedule, so it appears before / lingers after the header. Instead the
                // header panel's own background is extended up over the status-bar area (see topInfoPanel below) —
                // being the header itself, it can never desync from it. Keep exo_top collapsed.
                findViewById(R.id.exo_top).getLayoutParams().height = 0;

                if (Build.VERSION.SDK_INT >= 35) {
                    final int left = windowInsets.getInsets(WindowInsets.Type.navigationBars()).left;
                    final int right = windowInsets.getInsets(WindowInsets.Type.navigationBars()).right;

                    final FrameLayout exoBottomBar = findViewById(R.id.exo_bottom_bar);
                    ViewGroup.LayoutParams params = exoBottomBar.getLayoutParams();
                    params.height = getResources().getDimensionPixelSize(R.dimen.exo_styled_bottom_bar_height) + windowInsets.getSystemWindowInsetBottom() + overscanV;
                    exoBottomBar.setLayoutParams(params);

                    findViewById(R.id.exo_left).getLayoutParams().width = left;
                    findViewById(R.id.exo_right).getLayoutParams().width = right;

                    bottomBarPaddingBottom = windowInsets.getSystemWindowInsetBottom() + overscanV;
                    progressBarMarginBottom = windowInsets.getSystemWindowInsetBottom() + overscanV;
                } else {
                    // No top padding: the header panel's background (below) covers the status-bar area instead.
                    view.setPadding(0, 0, 0, windowInsets.getSystemWindowInsetBottom() + overscanV);
                }

                // Extend the header's background up over the status-bar area (top margin -> 0, top inset moved into
                // the top padding). The content position is unchanged (padding pushes it down by the same amount the
                // margin used to), but the panel now paints the status-bar strip, in perfect sync with the header.
                // Reserve that strip whether or not the status bar happens to be showing: the controls hide together
                // with the system bars, so a top padding that tracked the live inset moved the header's clock every
                // time they toggled, and the floating clock mirrors that position while remaining visible — which is
                // how it crept upwards when a picker panel hid the controls. Landscape is where it showed, the top
                // inset there really does fall to 0; in portrait a display cutout keeps it non-zero.
                final int insetTop = Build.VERSION.SDK_INT >= 30
                        ? Math.max(windowInsets.getSystemWindowInsetTop(),
                                windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars()).top)
                        : windowInsets.getSystemWindowInsetTop();
                Utils.setViewParams(topInfoPanel, paddingLeft + titleViewPaddingHorizontal, insetTop + overscanV + Utils.dpToPx(4), paddingRight + titleViewPaddingHorizontal, titleViewPaddingVertical,
                        marginLeft, 0, marginRight, 0);


                Utils.setViewParams(findViewById(R.id.exo_bottom_bar), paddingLeft, 0, paddingRight, bottomBarPaddingBottom,
                        marginLeft, 0, marginRight, 0);

                Utils.setViewParams(findViewById(R.id.exo_progress), insetH, 0, insetH, 0,
                        0, 0, 0, getResources().getDimensionPixelSize(R.dimen.exo_styled_progress_margin_bottom) + progressBarMarginBottom);

                // Keep the Skip pill above the seek bar and clear of the nav-bar inset. It floats on the
                // full-screen coordinator (not the controller), so a fixed bottom offset overlapped the
                // progress bar on tablets — derive it from the seek bar's own geometry (its top sits at
                // insetBottom + progress margin + progress layout height above the screen bottom).
                if (buttonSkip != null) {
                    final CoordinatorLayout.LayoutParams skipLp = (CoordinatorLayout.LayoutParams) buttonSkip.getLayoutParams();
                    // Float a small, deliberate gap above the bottom control bar (progress + time/pills) — not
                    // flush against it, and not the huge gap the full progress touch-target height produced.
                    skipLp.bottomMargin = windowInsets.getSystemWindowInsetBottom() + overscanV
                            + getResources().getDimensionPixelSize(R.dimen.exo_styled_progress_margin_bottom)
                            + ui.dpS(24);
                    // Align the floating Skip button's right edge to the shared content grid (same as the pills
                    // and the progress bar), instead of a fixed 24dp + insetRight that overshoots in landscape.
                    skipLp.rightMargin = insetH + ui.gridH();
                    buttonSkip.setLayoutParams(skipLp);
                }

                // The room pill is the Skip pill's opposite corner, so it takes the same two offsets — the
                // seek bar's own geometry for the bottom, the shared content grid for the side. Kept in step
                // with Skip on purpose: the two sit on one line when both are up.
                if (roomPill != null) {
                    final CoordinatorLayout.LayoutParams pillLp =
                            (CoordinatorLayout.LayoutParams) roomPill.getLayoutParams();
                    pillLp.bottomMargin = windowInsets.getSystemWindowInsetBottom() + overscanV
                            + getResources().getDimensionPixelSize(R.dimen.exo_styled_progress_margin_bottom)
                            + ui.dpS(24);
                    pillLp.leftMargin = insetH + ui.gridH();
                    roomPill.setLayoutParams(pillLp);
                }

                // Mirror of the Skip pill on the other edge: the stats panel floats on the same coordinator,
                // so its left edge needs the same grid the header and the bottom bar are padded to — a raw
                // margin from the screen edge lands it in the display cutout's strip in landscape.
                if (statsView != null) {
                    final CoordinatorLayout.LayoutParams statsParams =
                            (CoordinatorLayout.LayoutParams) statsView.getLayoutParams();
                    statsParams.leftMargin = insetH + ui.gridH();
                    // The panel is centred vertically, which is where the play/pause cluster lives, so its
                    // width has to stop short of it: half the window, less half that cluster (hero disc plus
                    // an episode arrow beside it) and the panel's own offset. A decoder name longer than
                    // that wraps instead of sliding under the buttons.
                    statsView.setMaxWidth(ui.dp(getResources().getConfiguration().screenWidthDp) / 2
                            - ui.heroBox() / 2 - ui.episodeDisc() - statsParams.leftMargin);
                    statsView.setLayoutParams(statsParams);
                }

                Utils.setViewMargins(findViewById(R.id.exo_error_message), 0, windowInsets.getSystemWindowInsetTop() / 2, 0, getResources().getDimensionPixelSize(R.dimen.exo_error_message_margin_bottom) + windowInsets.getSystemWindowInsetBottom() / 2);

                windowInsets.consumeSystemWindowInsets();
            }
            return windowInsets;
        });
        timeBar.setAdMarkerColor(Color.argb(0x00, 0xFF, 0xFF, 0xFF));
        timeBar.setPlayedAdMarkerColor(Color.argb(0x98, 0xFF, 0xFF, 0xFF));
        // Brand the timeline: the played portion and the scrubber (the surfaces the user actually touches)
        // share one colour, over a solid dark rail instead of Media3's wash of the frame behind.
        final int timeBarPlayed = ContextCompat.getColor(this, R.color.timebar_played);
        timeBar.setPlayedColor(timeBarPlayed);
        timeBar.setScrubberColor(timeBarPlayed);
        timeBar.setUnplayedColor(ContextCompat.getColor(this, R.color.timebar_track));

        try {
            trackNameProvider = new CustomDefaultTrackNameProvider(getResources());
            trackNameProvider.setTrackNames(resolvedTrackNames);
            final Field field = PlayerControlView.class.getDeclaredField("trackNameProvider");
            field.setAccessible(true);
            field.set(controlView, trackNameProvider);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }

        findViewById(R.id.delete).setOnClickListener(view -> askDeleteMedia());

        findViewById(R.id.next).setOnClickListener(view -> {
            if (!isTvBox && mPrefs.askScope) {
                askForScope(false, true);
            } else {
                skipToNext();
            }
        });

        exoPlayPause.setOnClickListener(view -> dispatchPlayPause());

        // Prevent double tap actions in controller
        findViewById(R.id.exo_bottom_bar).setOnTouchListener((v, event) -> true);
        //titleView.setOnTouchListener((v, event) -> true);

        playerListener = new PlayerListener();

        mBrightnessControl = new BrightnessControl(this);
        // Only the level is restored here. Putting it on the window as well tied the brightness to the
        // activity instead of the video: the empty state kept the last clip's dimming, and every trip to
        // the settings screen — a window of its own — flipped back to the device brightness and back again.
        // show/hideEmptyState own the window now.
        mBrightnessControl.percent = mPrefs.brightness;
        playerView.setBrightnessControl(mBrightnessControl);

        final LinearLayout exoBasicControls = playerView.findViewById(R.id.exo_basic_controls);
        exoSubtitle = exoBasicControls.findViewById(R.id.exo_subtitle);
        exoBasicControls.removeView(exoSubtitle);
        // Managed like the audio/quality buttons: hidden until the media actually has subtitle tracks,
        // so it never shows greyed-out while loading. Re-asserted after Media3's own updates (see onEvents).
        exoSubtitle.setVisibility(View.GONE);
        exoSubtitle.setImageTintList(ContextCompat.getColorStateList(this, R.color.control_icon_tint));

        exoSettings = exoBasicControls.findViewById(R.id.exo_settings);
        exoBasicControls.removeView(exoSettings);
        final ImageButton exoRepeat = exoBasicControls.findViewById(R.id.exo_repeat_toggle);
        exoBasicControls.removeView(exoRepeat);
        //exoBasicControls.setVisibility(View.GONE);

        // Open our native subtitle panel instead of Media3's built-in track popup.
        exoSubtitle.setOnClickListener(v -> showSubtitleDialog());

        exoSubtitle.setOnLongClickListener(v -> {
            openSettings("languageSubtitle");
            return true;
        });

        updateButtons(false);

        final HorizontalScrollView horizontalScrollView = (HorizontalScrollView) getLayoutInflater().inflate(R.layout.controls, null);
        final LinearLayout controls = horizontalScrollView.findViewById(R.id.controls);

        // Multimedia pickers, each shown when relevant, live in the bottom bar on every device. Order per
        // the design: quality, audio, subtitles, playlist.
        controls.addView(buttonQuality);
        controls.addView(buttonAudio);
        controls.addView(exoSubtitle);
        controls.addView(buttonPlaylist);
        if (mPrefs.repeatToggle) {
            controls.addView(exoRepeat);
        }

        // Display / screen controls: beside the header clock on touch; in the bottom bar on TV so the remote
        // keeps a single left/right focus zone.
        final LinearLayout displayParent = isTvBox ? controls : headerButtons;
        displayParent.addView(buttonAspectRatio);
        if (Utils.isPiPSupported(this) && buttonPiP != null) {
            displayParent.addView(buttonPiP);
        }
        if (!isTvBox) {
            displayParent.addView(buttonRotation);
        }
        // "Update available" sits immediately before the gear — one insertion point that lands in the same
        // place on a phone and on TV, because the gear ends the bottom bar on both.
        controls.addView(buttonUpdate);
        // "More" (overflow) always lives at the end of the bottom bar.
        controls.addView(buttonMore);

        // One uniform button box across both clusters so the header pill and the bottom pill match in height,
        // size and inter-button gap.
        styleClusterButton(exoSubtitle);
        styleClusterButton(buttonAudio);
        styleClusterButton(buttonQuality);
        styleClusterButton(buttonPlaylist);
        if (mPrefs.repeatToggle) {
            styleClusterButton(exoRepeat);
        }
        styleClusterButton(buttonUpdate);
        refreshUpdateButton();
        styleClusterButton(buttonMore);
        styleClusterButton(buttonAspectRatio);
        if (buttonPiP != null) {
            styleClusterButton(buttonPiP);
        }
        if (!isTvBox) {
            styleClusterButton(buttonRotation);
            // No chrome behind the header icons: the design keeps the top light, so the glyphs are the only
            // thing there — and it is the glyph edge, not a pill edge, that has to sit on the header's grid
            // lines. Nudge the row out by the button padding that used to hide inside the pill: its glyphs
            // then finish on the clock's right-hand line and on the bottom line where the poster and the last
            // meta line end. Translation, not margins: a negative end margin squeezes the last button instead
            // of moving the row. The panel must stop clipping to its padding for the nudge to survive.
            final boolean rtl = getResources().getConfiguration().getLayoutDirection()
                    == View.LAYOUT_DIRECTION_RTL;
            headerButtons.setTranslationX(rtl ? -ui.clusterPad() : ui.clusterPad());
            headerButtons.setTranslationY(ui.clusterPad());
            // The nudge moves the row outside its own layout box, so every ancestor that would clip it has to
            // stop: the padding clip on the panel, and the child clip on both the panel and the column. Without
            // the child clips off, the row is cut by exactly the nudge — a fifth of every glyph.
            topInfoPanel.setClipToPadding(false);
            topInfoPanel.setClipChildren(false);
            if (headerButtons.getParent() instanceof ViewGroup) {
                ((ViewGroup) headerButtons.getParent()).setClipChildren(false);
            }
        }
        // Group the bottom-right pickers (subtitle / audio / HD / playlist / settings) into a matching pill.
        applyControlPill(controls);

        // Inset the bottom pill to the shared 14dp content grid so its right edge lines up with the header
        // pill / clock and stays inside the progress bar, instead of running to the screen edge.
        final LinearLayout.LayoutParams horizontalScrollViewLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        horizontalScrollViewLp.gravity = Gravity.CENTER_VERTICAL;
        horizontalScrollViewLp.setMarginEnd(ui.gridH());
        exoBasicControls.addView(horizontalScrollView, horizontalScrollViewLp);

        // Lock sits isolated at the far-left of the bottom bar — prepended into the time row, away from the
        // display cluster (MX-style) so it is no longer adjacent to the rotation button. Touch only (the lock
        // feature is not offered on TV).
        if (!isTvBox) {
            final View exoTime = findViewById(R.id.exo_time);
            if (exoTime instanceof LinearLayout) {
                // Match the right-hand controls: same 40dp box + chrome pill, so the lock reads as part of the
                // same control language instead of a lone heavy glyph. The time text stays bare, to its right.
                styleClusterButton(buttonLock);
                final GradientDrawable lockPill = new GradientDrawable();
                lockPill.setColor(ContextCompat.getColor(this, R.color.ui_controls_background));
                lockPill.setCornerRadius(ui.pillCorner());
                buttonLock.setBackground(lockPill);
                buttonLock.setClipToOutline(true);
                final LinearLayout.LayoutParams lockLp = (LinearLayout.LayoutParams) buttonLock.getLayoutParams();
                lockLp.setMarginEnd(ui.lockMarginEnd());
                buttonLock.setLayoutParams(lockLp);
                ((LinearLayout) exoTime).addView(buttonLock, 0);

                // exo_basic_controls (the right-hand cluster, holding a full-width HorizontalScrollView) is
                // laid out after exo_time, so it sits on top of it and its empty left area swallows taps on
                // the lock (a scroll view consumes touches for its own drag detection). Bring exo_time to the
                // front so the lock wins its taps. The cluster's buttons sit to the right, clear of exo_time
                // (which is ~494px wide and non-clickable outside the lock), so they and scrolling still work.
                exoTime.bringToFront();
            }
        }

        if (Build.VERSION.SDK_INT > 23) {
            horizontalScrollView.setOnScrollChangeListener((view, i, i1, i2, i3) -> resetHideCallbacks());
        }

        playerView.setControllerVisibilityListener(new PlayerView.ControllerVisibilityListener() {
            @Override
            public void onVisibilityChanged(int visibility) {
                final boolean wasVisible = controllerVisible;
                final boolean wasVisibleFully = controllerVisibleFully;
                controllerVisible = visibility == View.VISIBLE;
                controllerVisibleFully = playerView.isControllerFullyVisible();

                // See controllerChromeVisible. Both animations report their first frame here as "visible but
                // not fully visible", which on its own says nothing about the direction — what tells them
                // apart is where they came from: the show starts from hidden, the hide from fully visible.
                if (!controllerVisible) {
                    controllerChromeVisible = false;
                } else if (controllerVisibleFully || !wasVisible) {
                    controllerChromeVisible = true;
                } else if (wasVisibleFully) {
                    controllerChromeVisible = false;
                }

                if (controllerVisible) {
                    updateMediaInfo();
                    startEndsAtUpdates();
                    playerView.post(PlayerActivity.this::updateSubtitleButton);
                } else {
                    stopEndsAtUpdates();
                }
                updateOverlayClock();
                scheduleHideControllerOnPause();
                // Brief skip mode: the Skip button comes and goes with the controls — see rideSkipWithController.
                updateBriefSkipWithController();
                // The room pill rides the controls too — it is chrome, not something to leave over the film.
                updateRoomBadge();

                if (PlayerActivity.restoreControllerTimeout) {
                    restoreControllerTimeout = false;
                    if (player == null || !player.isPlaying()) {
                        playerView.setControllerShowTimeoutMs(-1);
                    } else {
                        playerView.setControllerShowTimeoutMs(PlayerActivity.CONTROLLER_TIMEOUT);
                    }
                }

                // https://developer.android.com/training/system-ui/immersive
                // While a picker panel is open keep the nav/gesture bar visible (avoids OxygenOS's two-swipe
                // back-gesture guard) but keep the status bar hidden for a clean top — see applyPickerBars.
                if (pickerDialogOpen) {
                    applyPickerBars();
                } else {
                    Utils.toggleSystemUi(PlayerActivity.this, playerView, visibility == View.VISIBLE);
                }
                if (visibility == View.VISIBLE && !emptyState.isVisible()) {
                    // Because when using dpad controls, focus resets to first item in bottom controls bar
                    if (focusTimeBarOnShow) {
                        // Set by Down, which opens the controls straight on the time bar. Deciding it here
                        // rather than at the key press is what makes it stick: this listener runs twice per
                        // show (once when the controls appear, once when the animation ends) and the second
                        // pass would otherwise pull focus back to play/pause.
                        timeBar.requestFocus();
                        if (controllerVisibleFully) {
                            focusTimeBarOnShow = false;
                        }
                    } else if (!exoPlayPause.requestFocus()) {
                        // Shown while the loading ring is up (a key press during buffering): play/pause is
                        // INVISIBLE and cannot take focus, so the ring holds it instead.
                        parkFocusOnLoadingRing();
                    }
                } else {
                    focusTimeBarOnShow = false;
                }

                if (controllerVisible && playerView.isControllerFullyVisible()) {
                    if (errorToShow != null) {
                        showError(errorToShow);
                        errorToShow = null;
                    }
                }
            }
        });

        youTubeOverlay = findViewById(R.id.youtube_overlay);
        youTubeOverlay.performListener(new YouTubeOverlay.PerformListener() {
            @Override
            public void onAnimationStart() {
                youTubeOverlay.setAlpha(1.0f);
                youTubeOverlay.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationEnd() {
                youTubeOverlay.animate()
                        .alpha(0.0f)
                        .setDuration(300)
                        .setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                youTubeOverlay.setVisibility(View.GONE);
                                youTubeOverlay.setAlpha(1.0f);
                            }
                        });
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (useMediaStore()) {
                Utils.scanMediaStorage(this);
            }
        }

        maybeCheckForUpdate(launchIntent);
    }

    // Silent, non-intrusive self-update check. Only the sideloaded universal build self-updates
    // (BuildConfig.ENABLE_UPDATE), since everywhere else a store does it.
    //
    // Checking and telling are deliberately separate. The check is one background request, it disturbs
    // nobody, and it now runs on every launch (throttled to an hour) — gating it on an idle launch meant
    // it never ran at all for anyone who reaches the player through another app's intent, which is how
    // most people arrive, so a hotfix could sit unseen for weeks. What stays gated is the *dialog*: it is
    // only ever raised on an idle launch with nothing playing. Every other find surfaces as the
    // brand-coloured button beside the gear, which is visible only once the user asks for the controls.
    private static final long UPDATE_CHECK_INTERVAL_MS = 60 * 60 * 1000L;

    private void maybeCheckForUpdate(final Intent launchIntent) {
        if (!BuildConfig.ENABLE_UPDATE || !mPrefs.autoUpdate) {
            return;
        }
        final String action = launchIntent.getAction();
        final boolean launchedIdle = launchIntent.getData() == null
                && !Intent.ACTION_SEND.equals(action)
                && !"com.brouken.player.action.SHORTCUT_VIDEOS".equals(action);
        final long now = System.currentTimeMillis();
        if (now - mPrefs.updateLastCheck < UPDATE_CHECK_INTERVAL_MS) {
            return;
        }
        mPrefs.setUpdateLastCheck(now);
        Updater.find(info -> runOnUiThread(() -> {
            if (isFinishing()) {
                return;
            }
            // Remembered so the button survives the launches the throttle skips; cleared when there is
            // nothing on offer any more, so a pulled release takes the button down with it.
            final boolean offer = info != null && info.versionCode != mPrefs.updateSkippedVersionCode;
            mPrefs.setUpdatePending(offer ? info : null);
            refreshUpdateButton();
            if (!offer || !launchedIdle) {
                return;
            }
            if (haveMedia && player != null && player.isPlaying()) {
                return;
            }
            UpdateUi.showAvailableDialog(PlayerActivity.this, info, skipUpdate(info), false);
        }));
    }

    private void refreshUpdateButton() {
        if (buttonUpdate != null) {
            // autoUpdate is the off switch for the whole feature, so it has to take a remembered find with
            // it — otherwise turning updates off would leave the button sitting there.
            final boolean show = BuildConfig.ENABLE_UPDATE && mPrefs.autoUpdate && mPrefs.updatePending != null;
            buttonUpdate.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    private Runnable skipUpdate(final UpdateInfo info) {
        return () -> {
            mPrefs.setUpdateSkippedVersionCode(info.versionCode);
            mPrefs.setUpdatePending(null);
            refreshUpdateButton();
        };
    }

    @Override
    public void onStart() {
        super.onStart();
        alive = true;
        updateSubtitleStyle(this);
        if (Build.VERSION.SDK_INT >= 31) {
            playerView.removeCallbacks(barsHider);
            Utils.toggleSystemUi(this, playerView, true);
        }
        playerView.removeCallbacks(backgroundReleaseRunnable);
        // Coming back never resumes playback — that is the user's call. Clear the latch before anything
        // below can read it: whatever the trip to the background interrupted (a scrub drag) may have left
        // it armed, and a rebuild here or in onActivityResult would consume it as "play". Show the
        // controls with it, so the return lands on an obviously paused player and not a frozen frame.
        restorePlayState = false;
        // Except over a locked screen, which now survives the trip: the controller's buttons are ordinary
        // views and take taps whatever the gesture handler thinks, and scheduleHideControllerOnPause refuses
        // to hide it while locked, so it would sit there permanently. Show the way out instead.
        if (locked) {
            showSwipeToUnlock();
        } else {
            playerView.showController();
        }
        if (player == null) {
            initializePlayer();
        } else if (player.getPlayerError() != null) {
            // The session survived being backgrounded but the player did not: Media3 stops it when the
            // surface detach times out (see onPlayerError), so resuming would show a dead picture.
            sourceSwitchKeepPaused = true;
            releasePlayer(false);
            initializePlayer();
        } else {
            resumeFrameRendered = false;
            playerView.postDelayed(resumeWatchdogRunnable, RESUME_WATCHDOG_MS);
            // Put back what onStop cancelled, and only that: a load that was still buffering when the user
            // left gets a full timeout from the moment they are looking at it again.
            if (player.getPlaybackState() == Player.STATE_BUFFERING) {
                armLoadWatchdog();
            }
        }
        // The room stayed connected while we were away, so this only starts sampling again — and it
        // re-anchors first: the gap is not something the viewer did, and broadcasting it would drag
        // everyone else back to where we left off.
        if (together != null) {
            together.resume();
        }
        updateRoomBadge();
        updateButtonRotation();
    }

    @Override
    public void onResume() {
        super.onResume();
        restorePlayStateAllowed = true;
        // Back in front, so nothing we launched can still need the device's auto-rotate. onActivityResult
        // normally beats us to this; it does not run for a picker that finishes without handing back a result.
        restoreRotationLock();
    }

    @Override
    protected void onPause() {
        super.onPause();
        savePlayer();
    }

    @Override
    public void onStop() {
        super.onStop();
        alive = false;
        // Stop the empty-state pulse while backgrounded: no point ticking the Choreographer with no media
        // loaded and the activity stopped. It does not come back on return — the page is already revealed.
        emptyState.stopPulse();
        if (Build.VERSION.SDK_INT >= 31) {
            playerView.removeCallbacks(barsHider);
        }
        playerView.setCustomErrorMessage(null);
        // Stop sampling before the pause below: that pause is the trip to the background, not the
        // viewer reaching for the button, and the room must not be told to stop with us.
        if (together != null) {
            together.suspend();
        }
        // With no session worth keeping (leaving for good, or nothing loaded) tear down as before —
        // the empty state and its pulse are only ever (re)built by initializePlayer.
        if (isFinishing() || player == null || !haveMedia) {
            if (isFinishing() && together != null) {
                together.leave();
            }
            releasePlayer(false);
            return;
        }
        // Otherwise keep the player and only stop the sound: a rebuild would re-buffer the stream and
        // drop everything that lives on the instance (quality override, selected tracks, lock).
        // Unconditional, and the pending auto-play with it: while buffering isPlaying() is false but
        // playWhenReady is set, and the STATE_READY handler would call play() the moment the buffer fills,
        // leaving video playing with sound in the background. Nothing is latched for a resume.
        play = false;
        // The frame-rate switch registers this listener while waiting to auto-play; with play cleared behind
        // its back it would never unregister itself.
        playerView.removeCallbacks(frameRateGiveUpRunnable);
        if (displayManager != null && displayListener != null) {
            displayManager.unregisterDisplayListener(displayListener);
        }
        player.pause();
        // The load watchdog only ever ran while the activity was on screen by accident: nothing cancelled it
        // on this path, so an item still buffering when the user left would time out in the background —
        // stopping a session they may come straight back to, and posting a snackbar nobody can see. onStart
        // re-arms it, which it has to do by hand: pausing does not leave STATE_BUFFERING, so the state does
        // not change across the trip and onPlaybackStateChanged never re-fires.
        cancelLoadWatchdog();
        playerView.removeCallbacks(resumeWatchdogRunnable);
        playerView.postDelayed(backgroundReleaseRunnable, BACKGROUND_RELEASE_MS);
    }

    /**
     * The room is anchored to this screen, so it cannot outlive it. onStop only leaves the room when the
     * activity is finishing, which is the ordinary way out — but a screen destroyed from the background
     * (low-memory reclaim, or "don't keep activities") is not finishing, and its socket stayed open:
     * pinging every twenty seconds, reconnecting for ever, and holding the whole player view tree
     * reachable, while the room went on listing a member whose heartbeats had stopped.
     */
    @Override
    protected void onDestroy() {
        if (together != null) {
            together.leave();
            together = null;
        }
        super.onDestroy();
    }

    /**
     * An api session (a LAMPA launch) lives entirely in memory: apiAccess switches Prefs to
     * non-persistent, so the position and the picked tracks are held on this instance and nothing is
     * written to disk. Killed while backgrounded — a screensaver plus the low-memory pressure that
     * follows it is enough — the recreated activity would replay the launch intent and resume where the
     * *previous* viewing ended. Hand the session to the system instead: this bundle comes back in
     * onCreate even when the process was killed.
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (!apiAccess || !haveMedia) {
            return;
        }
        // Take the position off the player rather than trusting the last onPause: PiP pauses the activity
        // once and playback carries on for the life of the window, so by the time it is stopped what
        // onPause stored can be minutes behind.
        if (player != null) {
            savePlayer();
        }
        final Bundle state = new Bundle();
        final int index = player == null ? apiPlaylistStartIndex : player.getCurrentMediaItemIndex();
        final boolean inPlaylist = index >= 0 && index < apiMediaItems.size();
        state.putInt("index", index);
        // The uri belonging to that index — where switchSource writes a SOURCE quality swap. Not
        // mPrefs.mediaUri: in a playlist the swap never reaches it, and the listener that re-keys it
        // (onMediaItemTransition) is only registered after initializePlayer has set the media items, so
        // it misses the item a rebuild starts on.
        final MediaItem item = inPlaylist ? apiMediaItems.get(index) : null;
        final Uri uri = item != null && item.localConfiguration != null
                ? item.localConfiguration.uri : currentPlayingUri();
        if (uri != null) {
            state.putString("uri", uri.toString());
        }
        final long position = mPrefs.getPosition();
        if (position >= 0) {
            state.putLong("position", position);
        }
        state.putLongArray("episodePositions", apiPlaylistPositions);
        state.putInt("stickyQuality", stickyQualityLines);
        state.putString("audioTrack", mPrefs.audioTrackId);
        state.putString("subtitleTrack", mPrefs.subtitleTrackId);
        state.putInt("resizeMode", mPrefs.resizeMode);
        state.putFloat("scale", mPrefs.scale);
        state.putFloat("aspectRatio", mPrefs.aspectRatio);
        state.putFloat("speed", mPrefs.speed);
        outState.putBundle(STATE_API_SESSION, state);
    }

    /**
     * Puts a saved api session back over what the launch intent just seeded (see onCreate) — the intent
     * is the same one that started the session, so its `position` and base urls are stale by definition.
     * A relaunch carrying genuinely new media arrives through onNewIntent, which runs after this: its
     * updateMedia() drops the position restored here along with the rest of the meta.
     */
    private void restoreApiSession(final Bundle savedInstanceState) {
        if (savedInstanceState == null || !apiAccess) {
            return;
        }
        final Bundle state = savedInstanceState.getBundle(STATE_API_SESSION);
        if (state == null) {
            return;
        }
        final String uri = state.getString("uri");
        final Uri target = uri == null ? null : Uri.parse(uri);
        final int index = state.getInt("index");
        if (index >= 0 && index < apiMediaItems.size()) {
            apiPlaylistStartIndex = index;
            if (target != null) {
                apiMediaItems.set(index, apiMediaItems.get(index).buildUpon().setUri(target).build());
            }
        }
        if (target != null) {
            // Straight assignment, as switchSource does: updateMedia() would clear the meta restored
            // below. Everything else keyed off mediaUri (request headers, the frame-rate switch, the title
            // fallback) then names the episode being restored instead of the one the intent launched.
            mPrefs.mediaUri = target;
        }
        final long[] episodePositions = state.getLongArray("episodePositions");
        if (episodePositions != null && apiPlaylistPositions != null
                && episodePositions.length == apiPlaylistPositions.length) {
            apiPlaylistPositions = episodePositions;
        }
        stickyQualityLines = state.getInt("stickyQuality");
        // Both are memory-only writes here; initializePlayer re-asserts the track ids on the fresh player
        // and takes the position as the start position of its media item.
        mPrefs.updateMeta(state.getString("audioTrack"), state.getString("subtitleTrack"),
                state.getInt("resizeMode"), state.getFloat("scale"), state.getFloat("aspectRatio"),
                state.getFloat("speed"));
        if (state.containsKey("position")) {
            mPrefs.updatePosition(state.getLong("position"));
        }
    }

    @SuppressLint("GestureBackNavigation")
    @Override
    public void onBackPressed() {
        // While locked, swallow the first Back (re-showing the unlock bar with a hint) and only exit if Back
        // is pressed again within the window — so a stray Back can't drop out of a locked video.
        if (locked && !lockBackPressedOnce) {
            lockBackPressedOnce = true;
            showSwipeToUnlock();
            Utils.showText(playerView, getString(R.string.press_back_again), 2000);
            if (playerView != null) {
                playerView.postDelayed(() -> lockBackPressedOnce = false, 2000);
            }
            return;
        }
        // On TV the remote's Back is pressed mid-interaction all the time, so it has to consume what is
        // open before it means "leave": a pending key-seek, then the controls. Only with nothing left to
        // close does it ask for a second press. This lives here rather than in onKeyDown because
        // enableOnBackInvokedCallback routes Back straight to onBackPressed on Android 13+, where it
        // never arrives as a key event at all.
        if (isTvBox && haveMedia && !locked) {
            if (keyScrubTarget >= 0) {
                playerView.removeCallbacks(keyScrubCommit);
                keyScrubTarget = -1;
                keyScrubSteps = 0;
                playerView.removeCallbacks(playerView.textClearRunnable);
                playerView.textClearRunnable.run();
                return;
            }
            if (controllerVisible) {
                // A timebar D-pad scrub needs no case of its own: it can only happen with the controls
                // up, and DefaultTimeBar stops scrubbing on its own timeout, which resumes playback.
                playerView.hideController();
                return;
            }
            if (!backPressedOnce) {
                backPressedOnce = true;
                Utils.showText(playerView, getString(R.string.press_back_again), 2000);
                playerView.postDelayed(() -> backPressedOnce = false, 2000);
                return;
            }
        }
        restorePlayStateAllowed = false;
        super.onBackPressed();
    }

    @Override
    public void finish() {
        if (intentReturnResult) {
            Intent intent = new Intent("com.mxtech.intent.result.VIEW");
            // Report which item finished so the launcher can attribute the position to the
            // correct playlist entry (and mark preceding ones watched), not just the launched one.
            if (player != null) {
                final MediaItem currentMediaItem = player.getCurrentMediaItem();
                if (currentMediaItem != null && currentMediaItem.localConfiguration != null) {
                    intent.setData(currentMediaItem.localConfiguration.uri);
                }
            }
            intent.putExtra(API_END_BY, playbackFinished ? "playback_completion" : "user");
            if (!playbackFinished) {
                if (player != null) {
                    long duration = player.getDuration();
                    if (duration != C.TIME_UNSET) {
                        intent.putExtra(API_DURATION, (int) player.getDuration());
                    }
                    if (player.isCurrentMediaItemSeekable()) {
                        intent.putExtra(API_POSITION, (int) player.getCurrentPosition());
                    }
                }
            }
            setResult(Activity.RESULT_OK, intent);
        }

        super.finish();
    }

    // Media plus every api extra (title, playlist, subs, position, ...) of a VIEW intent. Shared by
    // onCreate and onNewIntent: with launchMode="singleTask" a second intent lands in the running
    // activity, and its extras have to replace the previous ones instead of being ignored.
    void handleViewIntent(Intent intent) {
        resetApiAccess();
        final Uri uri = intent.getData();
        final String type = intent.getType();
        if (SubtitleUtils.isSubtitle(uri, type)) {
            handleSubtitles(uri);
        } else {
            Bundle bundle = intent.getExtras();
            if (bundle != null) {
                apiAccess = bundle.containsKey(API_POSITION) || bundle.containsKey(API_RETURN_RESULT)
                        || bundle.containsKey(API_SUBS) || bundle.containsKey(API_SUBS_ENABLE)
                        || bundle.containsKey(API_VIDEO_LIST) || bundle.containsKey(API_QUALITY_LEVELS);
                if (apiAccess) {
                    mPrefs.setPersistent(false);
                } else if (bundle.containsKey(API_TITLE)) {
                    apiAccessPartial = true;
                }
                // Read as CharSequence: some senders pass a Spanned/CharSequence title,
                // which getString() would silently drop.
                final CharSequence titleExtra = bundle.getCharSequence(API_TITLE);
                apiTitle = Utils.unescapeHtml(titleExtra == null ? null : titleExtra.toString());
                final String thumbnail = bundle.getString(API_THUMBNAIL);
                if (thumbnail != null) {
                    apiThumbnailUri = Uri.parse(thumbnail);
                }
                apiSegments = bundle.getString(API_SEGMENTS);
                apiHeaders = bundle.getStringArray(API_HEADERS);
                apiSeason = bundle.getInt(API_SEASON, -1);
                apiEpisode = bundle.getInt(API_EPISODE, -1);
                apiImdbId = bundle.getString(API_IMDB_ID);
                apiTmdbId = getStringOrIntExtra(bundle, API_ID);
                // Quality variants for a single (non-playlist) video; playlists carry per-episode maps.
                apiSingleQuality = readQualityMap(bundle, API_QUALITY_LEVELS, API_QUALITY_URLS);
                if (bundle.containsKey(API_VIDEO_LIST)) {
                    parseApiPlaylist(bundle, uri);
                }
            }

            mPrefs.updateMedia(this, uri, type);

            if (bundle != null) {
                Uri defaultSub = null;
                Parcelable[] subsEnable = bundle.getParcelableArray(API_SUBS_ENABLE);
                if (subsEnable != null && subsEnable.length > 0) {
                    defaultSub = (Uri) subsEnable[0];
                }

                Parcelable[] subs = bundle.getParcelableArray(API_SUBS);
                String[] subsName = bundle.getStringArray(API_SUBS_NAME);
                if (subs != null && subs.length > 0) {
                    for (int i = 0; i < subs.length; i++) {
                        Uri sub = (Uri) subs[i];
                        String name = null;
                        if (subsName != null && subsName.length > i) {
                            name = subsName[i];
                        }
                        apiSubs.add(SubtitleUtils.buildSubtitle(this, sub, name, sub.equals(defaultSub)));
                    }
                }
            }

            if (apiSubs.isEmpty()) {
                searchSubtitles();
            }

            if (bundle != null) {
                intentReturnResult = bundle.getBoolean(API_RETURN_RESULT);

                if (bundle.containsKey(API_POSITION)) {
                    mPrefs.updatePosition((long) bundle.getInt(API_POSITION));
                }
            }
        }
        focusPlay = true;
        checkRoomMedia(false, false);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        if (intent != null) {
            final String action = intent.getAction();
            final String type = intent.getType();
            final Uri uri = intent.getData();
            // New media, not a return: it must play even if a background teardown armed the keep-paused
            // one-shot while we were away (this can be delivered before onStart consumes it).
            sourceSwitchKeepPaused = false;

            if (handleRoomIntent(intent)) {
                // An invite link carries only a room code; what to play arrives over its channel.
            } else if (Intent.ACTION_VIEW.equals(action) && uri != null) {
                // Keep getIntent() pointing at what is actually playing (used by the intent report).
                setIntent(intent);
                handleViewIntent(intent);
                initializePlayer();
            } else if (Intent.ACTION_SEND.equals(action) && "text/plain".equals(type)) {
                String text = intent.getStringExtra(Intent.EXTRA_TEXT);
                if (text != null) {
                    final Uri parsedUri = Uri.parse(text);
                    if (parsedUri.isAbsolute()) {
                        // A shared link is new media, not a continuation: end whatever session was
                        // running, as the VIEW branch above does through handleViewIntent. Otherwise the
                        // launcher's return_result stays armed and finish() reports this link's progress
                        // against the episode the launcher asked for.
                        resetApiAccess();
                        mPrefs.updateMedia(this, parsedUri, null);
                        focusPlay = true;
                        initializePlayer();
                    }
                }
            }
        }
    }

    @SuppressLint("GestureBackNavigation")
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_BUTTON_SELECT:
                if (player == null)
                    break;
                if (keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) {
                    player.pause();
                } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY) {
                    player.play();
                } else if (player.isPlaying()) {
                    player.pause();
                } else {
                    player.play();
                }
                return true;
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                Utils.adjustVolume(this, mAudioManager, playerView, keyCode == KeyEvent.KEYCODE_VOLUME_UP, event.getRepeatCount() == 0, true);
                return true;
            case KeyEvent.KEYCODE_BUTTON_START:
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_SPACE:
                if (player == null)
                    break;
                if (!controllerVisibleFully) {
                    if (player.isPlaying()) {
                        player.pause();
                    } else {
                        player.play();
                    }
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_BUTTON_L2:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                if (!controllerVisibleFully || keyCode == KeyEvent.KEYCODE_MEDIA_REWIND) {
                    if (seekWithKey(false, event.getRepeatCount() > 0))
                        return true;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_BUTTON_R2:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                if (!controllerVisibleFully || keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) {
                    if (seekWithKey(true, event.getRepeatCount() > 0))
                        return true;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (controllerVisibleFully) {
                    // Up from the topmost row dismisses the controls instead of waiting out the timeout.
                    // Anywhere else it stays plain focus navigation: consuming the key here would suppress
                    // the focus search this mirrors, because onKeyDown runs before it.
                    final View focusedUp = getCurrentFocus();
                    if (!haveMedia || (focusedUp != null && focusedUp.focusSearch(View.FOCUS_UP) != null))
                        break;
                    // Repeats are swallowed below: a held key would re-show what the first press dismissed.
                    if (event.getRepeatCount() == 0)
                        playerView.hideController();
                } else if (event.getRepeatCount() == 0) {
                    playerView.showController();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (controllerVisibleFully) {
                    // Mirror of Up above: Down from the bottom row dismisses the controls, anywhere
                    // else it stays plain focus navigation.
                    final View focusedDown = getCurrentFocus();
                    if (!haveMedia || (focusedDown != null && focusedDown.focusSearch(View.FOCUS_DOWN) != null))
                        break;
                    if (event.getRepeatCount() == 0)
                        playerView.hideController();
                } else if (event.getRepeatCount() == 0) {
                    // Down opens the controls straight on the time bar, saving the press it takes to get
                    // there from play/pause; Up still lands on play/pause. See the visibility listener.
                    focusTimeBarOnShow = haveMedia;
                    playerView.showController();
                }
                return true;
            case KeyEvent.KEYCODE_BACK:
                // Handled in onBackPressed(), the one path every API level shares. The case still has to
                // exist so Back does not fall into default: below, which would show the controller.
                break;
            case KeyEvent.KEYCODE_UNKNOWN:
                return super.onKeyDown(keyCode, event);
            default:
                if (!controllerVisibleFully) {
                    playerView.showController();
                    return true;
                }
                break;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                playerView.postDelayed(playerView.textClearRunnable, CustomPlayerView.MESSAGE_TIMEOUT_KEY);
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_BUTTON_L2:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_BUTTON_R2:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                if (!isScrubbing) {
                    playerView.postDelayed(playerView.textClearRunnable, 1000);
                }
                if (keyScrubTarget >= 0) {
                    // Wait longer than the acceleration window, so a burst of clicks coalesces into one
                    // seek instead of one seek (and one re-buffering) per click.
                    playerView.removeCallbacks(keyScrubCommit);
                    playerView.postDelayed(keyScrubCommit, 520);
                }
                break;
        }
        return super.onKeyUp(keyCode, event);
    }

    private boolean seekWithKey(boolean forward, boolean held) {
        if (player == null)
            return false;
        playerView.removeCallbacks(playerView.textClearRunnable);
        final long pos = player.getCurrentPosition();
        if (playerView.keySeekStart == -1) {
            playerView.keySeekStart = pos;
        }
        final long duration = player.getDuration();
        if (duration <= 0) {
            // Live, unseekable, or a duration not known yet (C.TIME_UNSET): nothing to clamp the target
            // against — clamping to a zero duration would throw every press to the start of the file, so
            // keep the plain 10s step per press.
            final long seekTo = Math.max(0, pos + (forward ? 10_000 : -10_000));
            player.setSeekParameters(forward ? SeekParameters.NEXT_SYNC : SeekParameters.PREVIOUS_SYNC);
            player.seekTo(seekTo);
            showKeySeekMessage(seekTo);
            return true;
        }
        final long now = SystemClock.uptimeMillis();
        keyScrubSteps = held || now - keyScrubLastMs < 450 ? keyScrubSteps + 1 : 0;
        keyScrubLastMs = now;
        final long from = keyScrubTarget >= 0 ? keyScrubTarget : pos;
        final long step = keyScrubStep(duration);
        keyScrubTarget = Math.max(0, Math.min(duration, from + (forward ? step : -step)));
        showKeySeekMessage(keyScrubTarget);
        // While the key is held down the target keeps flying; don't seek until it settles.
        playerView.removeCallbacks(keyScrubCommit);
        playerView.postDelayed(keyScrubCommit, 700);
        return true;
    }

    /** 10s → 30s → 1m → 2% of the duration (≈2.5 min per press in a 2-hour film). */
    private long keyScrubStep(long duration) {
        if (keyScrubSteps < 1)
            return 10_000;
        if (keyScrubSteps < 5)
            return 30_000;
        if (keyScrubSteps < 12)
            return 60_000;
        return Math.max(120_000, duration / 50);
    }

    private void showKeySeekMessage(long target) {
        playerView.setCustomErrorMessage(Utils.formatMilisSign(target - playerView.keySeekStart)
                + "\n" + Utils.formatMilis(target));
    }

    private void commitKeyScrub() {
        final long target = keyScrubTarget;
        keyScrubTarget = -1;
        keyScrubSteps = 0;
        if (target < 0 || player == null)
            return;
        player.setSeekParameters(target >= player.getCurrentPosition()
                ? SeekParameters.NEXT_SYNC : SeekParameters.PREVIOUS_SYNC);
        player.seekTo(target);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // While locked, swallow every key at the earliest point — before the view hierarchy,
        // onKeyDown, and the window's default (MediaSession-backed) volume handling — so hardware
        // volume/media/seek keys can't act. BACK is excluded so the normal lock-aware exit path
        // still works. Re-show the unlock hint on the first press, mirroring the touch tap().
        if (locked && event.getKeyCode() != KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                showSwipeToUnlock();
            }
            return true;
        }

        if (isScaling) {
            final int keyCode = event.getKeyCode();
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                switch (keyCode) {
                    case KeyEvent.KEYCODE_DPAD_UP:
                        scale(true);
                        break;
                    case KeyEvent.KEYCODE_DPAD_DOWN:
                        scale(false);
                        break;
                }
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                switch (keyCode) {
                    case KeyEvent.KEYCODE_DPAD_UP:
                    case KeyEvent.KEYCODE_DPAD_DOWN:
                        break;
                    default:
                        if (isScaleStarting) {
                            isScaleStarting = false;
                        } else {
                            scaleEnd();
                        }
                }
            }
            return true;
        }

        // TV: while the floating Skip button is showing (controller hidden), OK/Enter must trigger the
        // skip. Keys are handled here (see below) rather than through view-focus dispatch, and the
        // button does not reliably hold focus when it appears, so key off its visibility rather than
        // focus. Route the confirm key straight to the button on ACTION_DOWN and swallow the paired
        // ACTION_UP — otherwise, once the skip hides the button and the controller appears, that
        // trailing key-up lands on the newly focused play/pause button and pauses playback.
        if (isTvBox && isSkipConfirmKey(event.getKeyCode())) {
            if (event.getAction() == KeyEvent.ACTION_DOWN
                    && !locked
                    && !controllerVisibleFully
                    && buttonSkip != null
                    && buttonSkip.getVisibility() == View.VISIBLE) {
                buttonSkip.performClick();
                skipKeyUpToConsume = event.getKeyCode();
                return true;
            }
            if (event.getAction() == KeyEvent.ACTION_UP
                    && skipKeyUpToConsume == event.getKeyCode()) {
                skipKeyUpToConsume = 0;
                return true;
            }
        }

        // BACK is excluded: it is handled in onBackPressed(), and hijacking it here breaks the
        // framework's key tracking. Without super.dispatchKeyEvent the DOWN event never reaches
        // KeyEvent.dispatch(), so the UP event is not marked as tracking and Activity.onKeyUp never
        // calls onBackPressed() — leaving Back dead below Android 13, where it still arrives as a key.
        if (isTvBox && !controllerVisibleFully && event.getKeyCode() != KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                onKeyDown(event.getKeyCode(), event);
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                onKeyUp(event.getKeyCode(), event);
            }
            return true;
        } else {
            return super.dispatchKeyEvent(event);
        }
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (locked) {
            return true;
        }
        if (0 != (event.getSource() & InputDevice.SOURCE_CLASS_POINTER)) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_SCROLL:
                    final float value = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
                    Utils.adjustVolume(this, mAudioManager, playerView, value > 0.0f, Math.abs(value) > 1.0f, true);
                    return true;
            }
        } else if ((event.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK &&
                event.getAction() == MotionEvent.ACTION_MOVE) {
            // TODO: This somehow works, but it would use better filtering
            float value = event.getAxisValue(MotionEvent.AXIS_RZ);
            for (int i = 0; i < event.getHistorySize(); i++) {
                float historical = event.getHistoricalAxisValue(MotionEvent.AXIS_RZ, i);
                if (Math.abs(historical) > value) {
                    value = historical;
                }
            }
            if (Math.abs(value) == 1.0f) {
                Utils.adjustVolume(this, mAudioManager, playerView, value < 0, true, true);
            }
        }
        return super.onGenericMotionEvent(event);
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        inPip = isInPictureInPictureMode;

        if (isInPictureInPictureMode) {
            // On Android TV it is required to hide controller in this PIP change callback
            playerView.hideController();
            // The floating overlays are not part of the controller, so hiding it leaves them on the video.
            // Nothing of ours belongs in the PiP window: it is a thumbnail with the system's own controls
            // over it, and a tap there expands the window instead of reaching our views.
            hideSkipPill();
            hideSwipeToUnlock();
            setSpeedBoostIndicatorVisible(false);
            updateRoomBadge();
            updateOverlayClock();
            setSubtitleTextSizePiP();
            playerView.setScale(1.f);
            mReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent == null || !ACTION_MEDIA_CONTROL.equals(intent.getAction()) || player == null) {
                        return;
                    }

                    switch (intent.getIntExtra(EXTRA_CONTROL_TYPE, 0)) {
                        case CONTROL_TYPE_PLAY:
                            player.play();
                            break;
                        case CONTROL_TYPE_PAUSE:
                            player.pause();
                            break;
                    }
                }
            };
            ContextCompat.registerReceiver(this, mReceiver, new IntentFilter(ACTION_MEDIA_CONTROL), ContextCompat.RECEIVER_EXPORTED);
        } else {
            setSubtitleTextSize();
            // Back to the full window: the clock returns if the preference wants it, and the skip pill
            // comes back by itself on the next poll while a segment is still current.
            updateOverlayClock();
            updateRoomBadge();
            if (mPrefs.aspectRatio > 0) {
                playerView.applyAspectMode(mPrefs.resizeMode, mPrefs.aspectRatio);
            } else if (mPrefs.resizeMode == AspectRatioFrameLayout.RESIZE_MODE_ZOOM) {
                playerView.setScale(mPrefs.scale);
            }
            if (mReceiver != null) {
                unregisterReceiver(mReceiver);
                mReceiver = null;
            }
            playerView.setControllerAutoShow(true);
            if (player != null) {
                if (player.isPlaying())
                    Utils.toggleSystemUi(this, playerView, false);
                else if (!locked)
                    playerView.showController();
            }
            // Entering PiP only hid the unlock bar, so a lock held on the way in comes back out still on.
            // Put its one affordance back — and above, keep the controller a locked screen must not show.
            if (locked) {
                showSwipeToUnlock();
            }
        }
    }

    void resetApiAccess() {
        apiAccess = false;
        apiAccessPartial = false;
        intentReturnResult = false;
        // The end-of-playback flag belongs to the session being reported. Left set, the next launcher
        // session inherits it and finish() reports a video watched to its end that the user just started.
        playbackFinished = false;
        apiTitle = null;
        apiThumbnailUri = null;
        apiSegments = null;
        apiHeaders = null;
        apiMediaItems.clear();
        apiPlaylistSegments.clear();
        apiPlaylistStartIndex = 0;
        apiPlaylistPositions = null;
        resolvedMediaTypes.clear();
        apiSeason = -1;
        apiEpisode = -1;
        apiImdbId = null;
        apiTmdbId = null;
        manualTmdbId = null;
        manualMovie = false;
        manualSeason = -1;
        manualEpisode = -1;
        manualIndex = -1;
        manualEpisodes = null;
        manualAbsolute = -1;
        apiPlaylistSeasons.clear();
        apiPlaylistEpisodes.clear();
        apiPlaylistNames.clear();
        apiPlaylistImdbIds.clear();
        apiPlaylistTmdbIds.clear();
        apiPlaylistQuality.clear();
        apiSingleQuality = new LinkedHashMap<>();
        selectedVideoQualityMode = VideoQualityChoice.MODE_AUTO;
        selectedVideoTrackGroup = null;
        selectedVideoTrackIndex = -1;
        stickyQualityLines = 0;
        apiSubs.clear();
        mPrefs.setPersistent(true);
        if (skipManager != null) {
            skipManager.clear();
        }
        skipBuilt = false;
        skipSourceFromIntent = false;
        skipNextPrefetched = false;
        clearSkipUndo();
        cancelSegmentFinder();
        cancelSubtitleSearch();
        hideSkipButton();
        // Skip offset is session-scoped: a new media session resets it and hides its control.
        skipOffsetSec = 0;
        skipSeenThisSession = false;
        if (skipOffsetDialog != null && skipOffsetDialog.isShowing()) {
            skipOffsetDialog.dismiss();
        }
        updateSkipOffsetButton();
        // Same for the subtitle offset: it was tuned against one file's timing and means nothing to the next.
        applySubtitleOffset(0);
        clearSubtitleTimeline();
        if (subtitleOffsetDialog != null && subtitleOffsetDialog.isShowing()) {
            subtitleOffsetDialog.dismiss();
        }
        if (timeBar != null) {
            timeBar.clearSkipHighlights();
        }
    }

    // Skip segments (intro/ad) received via the launch Intent — see com.brouken.player.skip.

    private void setupSkipSource() {
        if (skipManager == null) {
            skipManager = new SkipManager();
        }
        skipBuilt = false;
        // Do not hide the auto-skip notification here: when a skip lands at the very end of an item, the
        // next item auto-advances through onMediaItemTransition -> setupSkipSource almost immediately, and
        // hiding here would cut the notification short. Its own 3s timer governs it, so it rides across the
        // transition ("carry-through") and disappears on schedule.
        final String json = currentSegmentsJson();
        skipSourceFromIntent = json != null && !json.isEmpty();
        skipManager.setSource(skipSourceFromIntent ? new IntentSegmentsSource(json) : null);
        skipNextPrefetched = false;
        clearSkipUndo();
        // Source (re)set → the manager holds no segments until rebuildSkip() runs against the new
        // duration. Drop any highlights from the previous item right now so switching episodes never
        // leaves stale timecodes on the bar; the new segments (intent or online) repaint on rebuild.
        if (timeBar != null) {
            timeBar.clearSkipHighlights();
        }
        // Online lookup, first wave: the ids are known now, the duration only becomes known at
        // STATE_READY — on a network stream that is seconds of head start for the sources that do not
        // need the stream length. When the duration is already there (local file), the full second wave
        // below runs immediately anyway, so this wave is skipped rather than doubling the requests.
        if (currentDurationSec() <= 0) {
            maybeFetchSegmentsOnline();
        }
    }

    private String currentSegmentsJson() {
        if (player != null && !apiPlaylistSegments.isEmpty()) {
            final int index = player.getCurrentMediaItemIndex();
            if (index >= 0 && index < apiPlaylistSegments.size()) {
                return apiPlaylistSegments.get(index);
            }
            return null;
        }
        return apiSegments;
    }

    /** Duration of the current media in seconds, or 0 while it is still unknown. */
    private double currentDurationSec() {
        if (player == null) {
            return 0;
        }
        final long durationMs = player.getDuration();
        return (durationMs != C.TIME_UNSET && durationMs > 0) ? durationMs / 1000.0 : 0;
    }

    private void rebuildSkip() {
        if (skipManager == null) {
            return;
        }
        skipManager.setOffsetSec(skipOffsetSec);
        skipManager.rebuild(currentDurationSec());
        updateSkipHighlights();
        if (skipManager.hasSegments()) {
            skipSeenThisSession = true;
        }
        updateSkipOffsetButton();
    }

    /** The offset button is shown once any skip segment exists — now or earlier this session. */
    private void updateSkipOffsetButton() {
        if (buttonSkipOffset == null) {
            return;
        }
        final boolean show = mPrefs.skipEnabled && skipManager != null
                && (skipManager.hasSegments() || skipSeenThisSession);
        buttonSkipOffset.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    /** Apply a new session skip offset and re-derive the segments (moves timeline highlights live). */
    private void applySkipOffset(double sec) {
        skipOffsetSec = sec;
        rebuildSkip();
    }

    /** Session-only skip-offset panel — the shared {@link OffsetPanel} bound to {@link #skipOffsetSec}. */
    private void showSkipOffsetDialog() {
        if (player == null) {
            return;
        }
        if (skipOffsetDialog != null) {
            skipOffsetDialog.dismiss();
        }
        skipOffsetDialog = OffsetPanel.create(this, ui, coordinatorLayout,
                brandColor(),   // coral, matches the skip timeline highlight
                getString(R.string.skip_offset_title),
                OFFSET_MAX_SEC, OFFSET_STEP_SEC, skipOffsetSec,
                this::applySkipOffset);
        showPickerDialog(skipOffsetDialog);
    }

    /** Apply a new subtitle offset to the live text renderer (no reload, no reselection). */
    private void applySubtitleOffset(double sec) {
        subtitleOffsetSec = sec;
        if (subtitleOffset != null) {
            subtitleOffset.setOffsetSec(sec);
        }
    }

    /** Session-only subtitle-offset panel — the same {@link OffsetPanel}, bound to the text renderer. */
    private void showSubtitleOffsetDialog() {
        if (player == null) {
            return;
        }
        if (subtitleOffsetDialog != null) {
            subtitleOffsetDialog.dismiss();
        }
        subtitleOffsetDialog = OffsetPanel.create(this, ui, coordinatorLayout, brandColor(),
                getString(R.string.subtitle_offset_title),
                OFFSET_MAX_SEC, OFFSET_STEP_SEC, subtitleOffsetSec, this::applySubtitleOffset);
        showPickerDialog(subtitleOffsetDialog);
    }

    // Online skip-segment lookup (FIND_INTO.MD): when the current item has no intent-provided segments,
    // fetch them by imdb/season/episode and feed the result through the same SkipManager path.

    private void maybeFetchSegmentsOnline() {
        if (player == null || skipManager == null) {
            return;
        }
        // Intent segments win outright; anything else this lookup may already have found is its own
        // earlier, less certain result and is meant to be replaced.
        if (!mPrefs.skipEnabled || !mPrefs.skipFetchOnline || skipSourceFromIntent) {
            return;
        }
        final int index = player.getCurrentMediaItemIndex();
        final MediaId id = mediaIdAt(index);
        if (id.isEmpty()) {
            return;
        }
        cancelSegmentFinder();
        final int generation = segmentFetchGeneration;
        segmentFinderThread = SegmentFinder.find(id.imdb, id.tmdb, id.season, id.episode,
                currentDurationSec(),
                segments -> runOnUiThread(() -> onSegmentsFetched(generation, index, segments)));
        prefetchNextSegments();
    }

    /**
     * Warms the finder's cache for the next playlist item while this one is still playing, so switching
     * episodes shows its segments without a network round-trip. The next file is not prepared yet, so its
     * duration is unknown and this is an early-wave lookup (duration-independent sources only).
     *
     * <p>The worker is deliberately neither tracked nor cancelled: it is a daemon thread bounded by the
     * per-source call timeouts whose only effect is a write to the finder's in-memory cache. Track it if
     * it ever gains a side effect on the UI.
     */
    private void prefetchNextSegments() {
        if (skipNextPrefetched || player == null || !player.hasNextMediaItem()) {
            return;
        }
        final int index = player.getCurrentMediaItemIndex();
        final int next = player.getNextMediaItemIndex();
        final MediaId id = mediaIdAt(next);
        if (id.isEmpty()) {
            return;
        }
        // A playlist that carries no per-item ids resolves every item to the same title and episode —
        // warming that is just a duplicate request for what is already playing.
        if (id.sameAs(mediaIdAt(index))) {
            return;
        }
        skipNextPrefetched = true;
        SegmentFinder.find(id.imdb, id.tmdb, id.season, id.episode, 0, segments -> { });
    }

    private void onSegmentsFetched(int generation, int targetIndex, java.util.List<SkipSegment> segments) {
        if (player == null || skipManager == null || segments == null || segments.isEmpty()) {
            return;
        }
        // Drop a late callback from a cancelled lookup, a result for another media item, or anything at
        // all once intent segments have appeared. A newer result for this item does replace an older
        // one: that is how the first quick answer gets corrected by the full vote.
        if (generation != segmentFetchGeneration
                || player.getCurrentMediaItemIndex() != targetIndex
                || skipSourceFromIntent) {
            return;
        }
        skipManager.setSource(new NetworkSegmentsSource(segments));
        rebuildSkip();
    }

    private void cancelSegmentFinder() {
        segmentFetchGeneration++;
        if (segmentFinderThread != null) {
            segmentFinderThread.interrupt();
            segmentFinderThread = null;
        }
    }

    // Per-item metadata for the lookup. The playlist lists, when present, are authoritative for the
    // item at that index; a single-media launch falls back to the intent's own extras.

    /**
     * What is playing at {@code index}, as the ids the online lookups are keyed by. Per-item values from
     * a playlist win over the single-item extras; a playlist without them resolves every item to the
     * same title, which the callers guard against themselves.
     *
     * <p>Built fresh on every call rather than cached in a field: skip segments and the subtitle search
     * both read it, and a shared holder is exactly how the previous episode's id ends up fetching
     * subtitles for the next one.
     */
    private MediaId mediaIdAt(int index) {
        // A hand-picked title wins outright, and takes the launcher's imdb id down with it: the sources
        // that only speak imdb would otherwise keep answering about the wrong title, which is the very
        // thing the person went looking for a title to fix. SubtitleSearch.enrich() derives the right
        // imdb id from the tmdb one anyway.
        final String imdb = manualTmdbId != null ? null : stringAt(apiPlaylistImdbIds, index, apiImdbId);
        final String tmdb = manualTmdbId != null
                ? manualTmdbId : stringAt(apiPlaylistTmdbIds, index, apiTmdbId);
        int season = valueAt(apiPlaylistSeasons, index, apiSeason);
        int episode = valueAt(apiPlaylistEpisodes, index, apiEpisode);
        // Last resort, and for at least one launcher the only one that works: LAMPA sends every
        // per-episode field as an empty string and writes the numbers into the item's name instead.
        // Without them a series is looked up as a film, which is not just a worse match but a
        // different question — the answer is subtitles for some other part of the same title.
        if (episode < 1) {
            final int[] fromName = seasonEpisodeIn(stringAt(apiPlaylistNames, index, null));
            if (fromName != null) {
                season = season >= 1 ? season : fromName[0];
                episode = fromName[1];
            }
        }
        // A hand-picked title overrules both. For a film that means no season at all, even when the
        // release name carries something that reads like one. For a series the picked episode applies to
        // the item it was picked for, and every other item falls back to its own name — or, when the
        // name says nothing, to the whole season, which every source treats as an answerable question.
        if (manualTmdbId != null) {
            if (manualMovie) {
                season = -1;
                episode = -1;
            } else if (index == manualIndex) {
                season = manualSeason;
                episode = manualEpisode;
            } else {
                final int[] lined = lineUpWithPick(index, season, episode);
                season = lined[0];
                episode = lined[1];
            }
        }
        return new MediaId(imdb, tmdb, season, episode);
    }

    /**
     * A playlist item other than the one a title was picked for, as {season, episode} in the numbering
     * the sources use.
     *
     * <p>Self-checking rather than trusting either side: a coordinate the episode list actually
     * contains is already in the right numbering and is left alone, and only one it does not know is
     * lined up by position from the pick. That is what makes this safe without knowing which numbering
     * the launcher used — TMDB folds an anime's seasons together, so {@code S01E13} exists there and
     * nowhere else, while {@code S01E05} means the same episode either way.
     */
    private int[] lineUpWithPick(int index, int season, int episode) {
        if (manualEpisodes == null || manualAbsolute < 0) {
            // Nothing to line up against. A season with no episode still asks an answerable question.
            return new int[] { season < 1 ? 1 : season, episode };
        }
        for (TitleSearch.Episode known : manualEpisodes) {
            if (known.season == season && known.number == episode) {
                return new int[] { season, episode };
            }
        }
        final int position = manualAbsolute + (index - manualIndex);
        if (position < 0 || position >= manualEpisodes.size()) {
            return new int[] { season < 1 ? 1 : season, episode };
        }
        final TitleSearch.Episode lined = manualEpisodes.get(position);
        return new int[] { lined.season, lined.number };
    }

    /** {@code S01E05} or {@code 1x05} anywhere in the text, as {season, episode}; null if absent. */
    private static int[] seasonEpisodeIn(String text) {
        if (text == null) {
            return null;
        }
        for (Pattern pattern : SEASON_EPISODE_PATTERNS) {
            final Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                try {
                    return new int[] { Integer.parseInt(matcher.group(1)),
                            Integer.parseInt(matcher.group(2)) };
                } catch (NumberFormatException ignored) {
                    // Try the next shape.
                }
            }
        }
        return null;
    }

    // The Cyrillic "х" is in the second one on purpose: a localized episode list writes 1х05 with it,
    // and it is indistinguishable from the Latin letter on screen.
    private static final Pattern[] SEASON_EPISODE_PATTERNS = {
            Pattern.compile("(?i)s\\s*(\\d{1,2})\\s*[.\\-_ ]?\\s*e\\s*(\\d{1,3})"),
            Pattern.compile("(?<!\\d)(\\d{1,2})\\s*[x\u0445]\\s*(\\d{1,3})(?!\\d)"),
    };

    /** Per-item value from a playlist, or the single-item extra where the playlist has none. */
    private String stringAt(List<String> playlist, int index, String fallback) {
        final String value = player != null && index >= 0 && index < playlist.size()
                ? playlist.get(index) : null;
        return value != null && !value.isEmpty() ? value : fallback;
    }

    /** Per-item number from a playlist, or the single-item extra where the playlist has none. */
    private int valueAt(List<Integer> playlist, int index, int fallback) {
        final Integer value = player != null && index >= 0 && index < playlist.size()
                ? playlist.get(index) : null;
        return value != null ? value : fallback;
    }

    /** Brand accent color from {@code @color/brand}; pass an alpha (0x00..0xFF) for a translucent variant. */
    private int brandColor() {
        return ContextCompat.getColor(this, R.color.brand);
    }

    private int brandColor(int alpha) {
        return (alpha << 24) | (brandColor() & 0x00FFFFFF);
    }

    /** Brand accent at reduced brightness (same hue/saturation) for large selected-row fills, where full
     *  brand is too intense on the eyes. Scaling RGB proportionally lowers only the value, unlike alpha
     *  blending which desaturates the color against the dark panel. */
    private int brandColorDim() {
        final int c = brandColor();
        final float f = 0.72f; // tweakable: lower = darker
        return Color.rgb(Math.round(Color.red(c) * f),
                Math.round(Color.green(c) * f),
                Math.round(Color.blue(c) * f));
    }

    // Show a picker panel (audio/subtitle/playlist/quality/skip). OxygenOS/ColorOS applies a fullscreen
    // back-gesture guard (the "swipe again to go back" toast → two swipes) while the app is immersive, i.e.
    // system bars hidden. Our pickers call hideController(), which hides the bars, so keep the bars visible
    // while a panel is open and restore immersive on dismiss — this lets the back gesture close the panel in
    // one swipe (the reference lampaua build never goes immersive for its pickers).
    private void showPickerDialog(final android.app.Dialog dialog) {
        // Hide the controls for a clean panel. Keep the navigation/gesture bar visible (so OxygenOS doesn't
        // apply its fullscreen back-gesture guard) but hide the status bar (clean top, no strip over the
        // panel). Restore immersive when the panel dismisses.
        pickerDialogOpen = true;
        playerView.hideController();
        applyPickerBars();
        dialog.setOnDismissListener(d -> {
            pickerDialogOpen = false;
            Utils.toggleSystemUi(PlayerActivity.this, playerView, controllerVisibleFully);
        });
        dialog.show();
    }

    // A long label does not fit the width of a picker panel, and the two device classes want opposite
    // answers. On TV the row that has D-pad focus scrolls its text: focus makes the movement mean
    // something ("this row"), it is the 10-foot idiom, and only one row is ever moving. In touch mode
    // nothing is ever focused, so scrolling could only mean scrolling — six rows crawling in a list the
    // user is trying to scan is motion without a message, so there the label wraps to a second line and
    // ellipsizes instead, which shows more of it than a marquee does at any one moment. Scrolling also
    // needs the view focused or selected, and the focusable view is the row, not its texts, hence the
    // relay below; only the TextViews move, the poster/number box of a playlist row is a sibling.
    private void fitLongText(final View row, final TextView... texts) {
        final boolean scroll = ui.deviceClass == UiMetrics.DeviceClass.TV && !Utils.isReducedMotion(this);
        for (final TextView text : texts) {
            if (text == null) {
                continue;
            }
            if (scroll) {
                text.setSingleLine(true);
                text.setEllipsize(TextUtils.TruncateAt.MARQUEE);
                text.setMarqueeRepeatLimit(-1);
                text.setHorizontalFadingEdgeEnabled(true);
            } else {
                text.setMaxLines(2);
                text.setEllipsize(TextUtils.TruncateAt.END);
            }
        }
        if (scroll) {
            row.setOnFocusChangeListener((v, hasFocus) -> {
                for (final TextView text : texts) {
                    if (text != null) {
                        text.setSelected(hasFocus);
                    }
                }
            });
        }
    }

    // Bar state while a picker panel is open: navigation/gesture bar shown (avoids OxygenOS's fullscreen
    // back-gesture guard, which keys on hidden nav gestures), status bar hidden (clean top edge).
    private void applyPickerBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            final WindowInsetsController c = getWindow() != null ? getWindow().getInsetsController() : null;
            if (c != null) {
                c.hide(WindowInsets.Type.statusBars());
                c.show(WindowInsets.Type.navigationBars());
            }
        } else {
            playerView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_FULLSCREEN);
        }
    }

    /** Group a control cluster into a rounded, semi-transparent chrome pill (matches the Skip button chrome). */
    private void applyControlPill(ViewGroup cluster) {
        final GradientDrawable pill = new GradientDrawable();
        pill.setColor(ContextCompat.getColor(this, R.color.ui_controls_background));
        pill.setCornerRadius(ui.pillCorner());
        cluster.setBackground(pill);
        cluster.setClipToOutline(true);
        final int padH = ui.pillPadH();
        cluster.setPadding(padH, cluster.getPaddingTop(), padH, cluster.getPaddingBottom());
    }

    private void updateSkipHighlights() {
        if (timeBar == null) {
            return;
        }
        final java.util.List<SkipSegment> segments = skipManager != null ? skipManager.getSegments() : null;
        final long durationMs = player != null ? player.getDuration() : C.TIME_UNSET;
        if (segments == null || segments.isEmpty() || durationMs == C.TIME_UNSET || durationMs <= 0 || !mPrefs.skipEnabled) {
            timeBar.clearSkipHighlights();
            return;
        }
        final int count = segments.size();
        final long[] starts = new long[count];
        final long[] ends = new long[count];
        final int[] colors = new int[count];
        final int[] fillColors = new int[count];
        for (int i = 0; i < count; i++) {
            final SkipSegment segment = segments.get(i);
            final boolean ad = segment.type == SkipSegment.Type.AD;
            starts[i] = segment.startMs();
            ends[i] = segment.endMs();
            colors[i] = ad ? AD_HIGHLIGHT_COLOR : SKIP_HIGHLIGHT_COLOR;
            fillColors[i] = ad ? AD_FILL_COLOR : SKIP_FILL_COLOR;
        }
        timeBar.setSkipHighlights(starts, ends, colors, fillColors, durationMs);
    }

    private void skipTick() {
        if (player == null || skipManager == null || !mPrefs.skipEnabled) {
            hideSkipButton();
            return;
        }
        final double posSec = player.getCurrentPosition() / 1000.0;
        if (skipPill == SkipPill.UNDO) {
            updatePillCountdown(skipNoticeHideAtMs, R.string.notification_skipped_undo);
        } else if (skipFlashActive()) {
            updatePillCountdown(skipFlashEndMs, R.string.button_skip_countdown);
        }
        final SkipSegment segment = skipManager.activeSegment(posSec);
        if (segment == null) {
            // Nothing to skip here and now — but a segment starting shortly ahead is announced early
            // (see SKIP_LEAD_SEC): as the Skip button when the user is asked, as the pill when the jump
            // is automatic and there is no button to offer.
            final SkipSegment upcoming = skipManager.upcomingSegment(posSec, SKIP_LEAD_SEC);
            if (upcoming == null || autoSkipUndone(posSec)) {
                hideSkipButton();
                hideSkipHeadsUp();
            } else if (isAutoSkip(upcoming)) {
                hideSkipButton();
                showSkipHeadsUp(upcoming);
            } else if (isBriefSkip(upcoming)) {
                // No head start in brief mode: the offer belongs to the segment, and three seconds spent
                // before it even begins would be three the viewer never gets over the intro itself.
                hideSkipHeadsUp();
                if (!skipFlashActive()) {
                    hideSkipButton(); // clear anything the segment just behind us left up
                }
            } else {
                hideSkipHeadsUp();
                updateSkipButtonProgress(upcoming);
                showSkipButton(upcoming);
            }
            return;
        }
        if (isAutoSkip(segment) && !autoSkipUndone(posSec)) {
            segment.skipped = true;
            hideSkipButton();
            skipSeekTo(segment);
            showSkipNotification(true);
        } else if (isBriefSkip(segment)) {
            briefSkipTick(segment);
        } else {
            updateSkipButtonProgress(segment);
            showSkipButton(segment);
        }
    }

    /**
     * Ad segments are always skipped silently. Skip segments follow a per-position preference: end
     * credits use skipModeCredits, everything else (intro/recap) uses skipMode.
     */
    private boolean isAutoSkip(SkipSegment segment) {
        if (segment.type == SkipSegment.Type.AD) {
            return true;
        }
        return Prefs.SKIP_MODE_AUTO.equals(segment.credits ? mPrefs.skipModeCredits : mPrefs.skipMode);
    }

    /**
     * True while playback is still inside the stretch the user just took back with Undo. Keyed on the
     * position rather than the segment object, because a segment the user un-skipped can be re-derived
     * (a firmer lookup result, a changed offset) with its once-only flag cleared and slightly different
     * bounds — and it must not be skipped away again the moment it reappears.
     */
    private boolean autoSkipUndone(double posSec) {
        if (skipUndoneUntilMs == C.TIME_UNSET) {
            return false;
        }
        if (posSec * 1000 >= skipUndoneUntilMs) {
            skipUndoneUntilMs = C.TIME_UNSET; // played past it — auto-skip is armed again
            return false;
        }
        return true;
    }

    private Drawable pillIcon(SkipPill mode) {
        switch (mode) {
            case CANCEL:
                return skipIconKeep;
            case UNDO:
                return skipIconBack;
            default:
                return skipIconForward;
        }
    }

    /** Sizes the underline to the fraction of the segment still remaining (1 at start, 0 at the end). */
    private void updateSkipButtonProgress(SkipSegment segment) {
        if (player == null || segment == null) {
            return;
        }
        final long totalMs = segment.endMs() - segment.startMs();
        setSkipPillUnderline(totalMs > 0
                ? (segment.endMs() - player.getCurrentPosition()) / (double) totalMs : 0);
    }

    private void skipSeekTo(SkipSegment segment) {
        if (player == null)
            return;
        // A segment reaching the file end maps its end to the duration, so skipping it lands on the
        // very last frame. seekTo(duration) parks there — playback stalls, paused, without advancing —
        // so it must move to the next episode like a natural end-of-media instead. Credits that stop
        // short of the end (a post-credits scene/teaser follows) and the last item, with no next
        // episode, fall through to an exact seek so that trailing content still plays.
        if (segment.reachesEnd && player.hasNextMediaItem()) {
            clearSkipUndo(); // advancing an episode is not something Undo can take back
            steppedBySkip = true;
            player.seekToNextMediaItem();
            return;
        }
        // Remember both ends of the jump so an automatic skip can be taken back with one tap.
        skipUndoFromMs = player.getCurrentPosition();
        skipUndoToMs = segment.endMs();
        // Exact seek so playback resumes precisely at the segment end, not at an earlier keyframe.
        player.setSeekParameters(SeekParameters.EXACT);
        player.seekTo(segment.endMs());
    }

    /**
     * Auto mode never offers a Skip button, so the pill counts the jump down instead and offers to refuse
     * it — the same insurance as Undo, one step earlier. Ads stay silent: they are never announced.
     */
    private void showSkipHeadsUp(SkipSegment segment) {
        if (buttonSkip == null || segment.type == SkipSegment.Type.AD) {
            return;
        }
        if (locked && mPrefs.skipHideWhenLocked) {
            return;
        }
        // Whole seconds still to go, rounded up: the countdown reads 3, 2, 1 and never a bare 0.
        final int secsLeft = (int) Math.max(1,
                Math.ceil((segment.startMs() - player.getCurrentPosition()) / 1000.0));
        final boolean fresh = skipHeadsUpEndMs != segment.endMs();
        if (fresh) {
            skipHeadsUpEndMs = segment.endMs();
            // No auto-hide: the announcement stands until the jump happens, is refused, or becomes moot.
            playerView.removeCallbacks(skipPillHider);
            hideSkipPillUnderline(); // the number is the countdown here; no bar to say it twice
        }
        if (fresh || secsLeft != skipPillSecs) {
            skipPillSecs = secsLeft;
            showSkipPill(SkipPill.CANCEL, countdownLabel(R.string.notification_skipping_cancel, secsLeft), true, true);
        }
    }

    private void hideSkipHeadsUp() {
        if (skipPill != SkipPill.CANCEL) {
            return; // the pill is showing something else — leave it alone
        }
        hideSkipPill();
    }

    /** Tap on the countdown pill: let the announced segment play instead of skipping it. */
    private void cancelUpcomingSkip() {
        skipUndoneUntilMs = skipHeadsUpEndMs; // auto-skip disarmed for that stretch; Skip is still offered
        hideSkipPill();
    }

    /**
     * Paints and shows the one floating pill. {@code actionable} also drives focusability: a pill that
     * does nothing must not take D-pad focus away from the player. {@code claimFocus} says whether this
     * pill may take the focus at all — a pill the viewer summoned themselves must not, or it snatches the
     * focus out from under the controls they are navigating.
     */
    private void showSkipPill(SkipPill mode, CharSequence label, boolean actionable, boolean claimFocus) {
        if (buttonSkip == null) {
            return;
        }
        if (inPip) {
            hideSkipPill(); // unusable in the PiP window; automatic skips still happen, just silently
            return;
        }
        // Whatever timer the outgoing pill had is not the incoming one's. Without this a pill replaced
        // mid-countdown — back-to-back segments land the next offer inside the previous notice's three
        // seconds — inherited a hider that took the new pill away early. Callers that want a timer arm
        // it after calling this.
        if (playerView != null) {
            playerView.removeCallbacks(skipPillHider);
        }
        // Read before the assignment below: a change of state is what earns a focus request, so a countdown
        // repainting itself every second cannot keep yanking the focus back.
        final boolean stateChanged = skipPill != mode;
        skipPill = mode;
        buttonSkip.setText(label);
        buttonSkip.setCompoundDrawablesRelative(
                pillIcon(mode), null, null, null);
        buttonSkip.setClickable(actionable);
        buttonSkip.setFocusable(actionable);
        final boolean appearing = buttonSkip.getVisibility() != View.VISIBLE;
        if (appearing) {
            buttonSkip.setVisibility(View.VISIBLE);
        }
        if (isTvBox && actionable && claimFocus && (appearing || stateChanged)) {
            buttonSkip.requestFocus();
        }
    }

    /**
     * A pill label with its seconds picked out in the accent colour, so the number reads as a countdown
     * against the white wording. Falls back to the plain string if the format left no number to find.
     */
    private CharSequence countdownLabel(int labelRes, int secs) {
        final String seconds = String.valueOf(secs);
        final String label = getString(labelRes, secs);
        final int at = label.lastIndexOf(seconds);
        if (at < 0) {
            return label;
        }
        final SpannableString out = new SpannableString(label);
        // Lightened accent, not the flat brand colour: at full strength the coral carries about half the
        // luminance of the white wording, and a single narrow glyph that much darker than its neighbours
        // reads as sitting off the line rather than as coloured. Same geometry, matched optical weight.
        final int digitColor = ColorUtils.blendARGB(brandColor(), Color.WHITE, 0.25f);
        out.setSpan(new ForegroundColorSpan(digitColor), at, at + seconds.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return out;
    }

    /**
     * Sizes the pill's underline to a 0..1 fraction. Only the Skip offer uses it, where it measures the
     * segment it sits over. The two cancel states count down in their label instead — a bar and a number
     * saying the same thing is one of them too many.
     */
    private void setSkipPillUnderline(double fraction) {
        if (skipPillGroove != null) {
            skipPillGroove.setColor(SKIP_PILL_GROOVE_COLOR);
        }
        if (skipButtonProgress != null) {
            skipButtonProgress.setLevel((int) Math.round(Math.max(0, Math.min(1, fraction)) * 10000));
        }
    }

    private void hideSkipPillUnderline() {
        if (skipPillGroove != null) {
            skipPillGroove.setColor(Color.TRANSPARENT);
        }
        if (skipButtonProgress != null) {
            skipButtonProgress.setLevel(0);
        }
    }

    /** Ticks the seconds left on whichever timed pill is up — the undo offer or brief mode's Skip offer. */
    private void updatePillCountdown(long deadlineMs, int labelRes) {
        if (buttonSkip == null || !buttonSkip.isClickable()) {
            return; // a plain "skipped" notice has nothing to count down to
        }
        final int secsLeft = (int) Math.max(1,
                Math.ceil((deadlineMs - SystemClock.uptimeMillis()) / 1000.0));
        if (secsLeft != skipPillSecs) {
            skipPillSecs = secsLeft;
            buttonSkip.setText(countdownLabel(labelRes, secsLeft));
        }
    }

    /** Takes the pill away whatever it is showing, cancelling its timer. */
    private void hideSkipPill() {
        skipPill = SkipPill.NONE;
        skipHeadsUpEndMs = C.TIME_UNSET;
        skipPillSecs = 0;
        pendingSkip = null;
        if (playerView != null) {
            playerView.removeCallbacks(skipPillHider);
        }
        if (buttonSkip != null) {
            if (isTvBox && buttonSkip.hasFocus() && playerView != null) {
                playerView.requestFocus();
            }
            buttonSkip.setVisibility(View.GONE);
        }
    }

    /** Tap on the "skipped" pill: back to where the automatic skip started, and leave that stretch alone. */
    private void undoSkip() {
        if (player == null || skipUndoFromMs == C.TIME_UNSET) {
            hideSkipPill();
            return;
        }
        final long backTo = skipUndoFromMs;
        skipUndoneUntilMs = skipUndoToMs; // survives clearSkipUndo below: it is what disarms the re-skip
        skipUndoFromMs = C.TIME_UNSET;
        skipUndoToMs = C.TIME_UNSET;
        player.setSeekParameters(SeekParameters.EXACT);
        player.seekTo(backTo);
        hideSkipPill();
    }

    private void clearSkipUndo() {
        skipUndoFromMs = C.TIME_UNSET;
        skipUndoToMs = C.TIME_UNSET;
        skipUndoneUntilMs = C.TIME_UNSET;
        skipHeadsUpEndMs = C.TIME_UNSET;
        skipPillSecs = 0;
        // Called on every media item change, which is exactly when brief mode's "already offered" mark has
        // to go: two episodes of the same length can carry intros that end on the very same millisecond, and
        // the mark is keyed on that position, so keeping it would swallow the next episode's offer.
        skipBriefFlashedUntilMs = C.TIME_UNSET;
    }

    // OK/Enter-style keys that activate the focused Skip button on a TV remote / gamepad.
    private static boolean isSkipConfirmKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_BUTTON_START:
                return true;
            default:
                return false;
        }
    }

    // Called from CustomPlayerView.toggleLock() whenever the touch lock changes. When "hide skip
    // controls while locked" is on, drop the Skip button and auto-skip notification immediately;
    // showSkipButton/showSkipNotification keep them suppressed until unlock, after which the next
    // skip poll restores the button if a segment is still active.
    void onLockChanged() {
        if (locked) {
            lockScreen();
        } else {
            clearLockUi();
        }
        if (locked && mPrefs != null && mPrefs.skipHideWhenLocked) {
            hideSkipPill();
        }
        updateRoomBadge();
    }

    // Entering the lock: pin the current orientation (restored on unlock), arm the swipe bar and reset the
    // Back guard. The controller is already hidden by CustomPlayerView.toggleLock().
    private void lockScreen() {
        // A concrete side, not SCREEN_ORIENTATION_LOCKED. LOCKED means "whichever way round it is now", and
        // the system works that out afresh every time the activity takes charge of the screen again — so a
        // lock set in landscape came back from the background pinned to however the phone was being held at
        // that moment. Naming the side outright is the same freeze that cannot drift.
        final int rotation = getWindowManager().getDefaultDisplay().getRotation();
        final boolean portrait = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
        // Which way up comes from the rotation, which side from the configuration: that pair is right whether
        // the device is naturally portrait (phone) or naturally landscape (tablet).
        final boolean reverse = rotation == Surface.ROTATION_180 || rotation == Surface.ROTATION_270;
        if (portrait) {
            setRequestedOrientation(reverse
                    ? ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                    : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        } else {
            setRequestedOrientation(reverse
                    ? ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                    : ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
        lockBackPressedOnce = false;
        showSwipeToUnlock();
    }

    // Some paths flip `locked` directly (new media, playback ended) without going through onLockChanged;
    // the lock does not persist, so undo its UI (bar + orientation pin) here too.
    private void clearLockUi() {
        hideSwipeToUnlock();
        lockBackPressedOnce = false;
        if (mPrefs != null) {
            Utils.setOrientation(this, mPrefs.orientation);
        }
        updateRoomBadge();
    }

    private void showSkipButton(SkipSegment segment) {
        // When configured, keep the pill hidden while the screen is locked.
        if (locked && mPrefs.skipHideWhenLocked) {
            hideSkipButton();
            return;
        }
        pendingSkip = segment;
        showSkipPill(SkipPill.SKIP, getString(R.string.button_skip), true, true);
    }

    /** Whether this segment's position is set to offer the Skip button briefly rather than throughout. */
    private boolean isBriefSkip(SkipSegment segment) {
        return Prefs.SKIP_MODE_BRIEF.equals(segment.credits ? mPrefs.skipModeCredits : mPrefs.skipMode);
    }

    /** True while brief mode's timed offer is on screen and owns the pill. */
    private boolean skipFlashActive() {
        return skipPill == SkipPill.SKIP && SystemClock.uptimeMillis() < skipFlashEndMs;
    }

    /**
     * Brief mode, once playback is inside a segment the viewer is asked about. The offer comes twice over:
     * once by itself when the segment starts, and thereafter only while the controller is up. Between the
     * two the picture is left alone, which is the whole point of the mode — the Skip button in the corner
     * for the length of an intro is exactly what it exists to avoid.
     */
    private void briefSkipTick(SkipSegment segment) {
        pendingSkip = segment;
        if (skipFlashActive()) {
            if (!controllerVisible) {
                return; // still counting down; leave it its three seconds
            }
            // Controls came up mid-countdown. End the flash here and hand the pill over rather than letting
            // its timer run out under an open controller, which would blink the button out and back in.
            skipFlashEndMs = 0;
        }
        // Controls already up when the segment arrived: ride along with them and keep the segment's own
        // offer in hand. Counting down over an open controller would say nothing — the button is not about
        // to go while the controls are there — and would then blink out and straight back in as this rides.
        if (controllerVisible) {
            rideSkipWithController();
            return;
        }
        if (segment.endMs() != skipBriefFlashedUntilMs) {
            flashSkipOffer(segment);
            return;
        }
        rideSkipWithController();
    }

    /**
     * The one unprompted offer: three seconds of Skip, counted down in the label, then gone. Same timer and
     * same countdown as the undo pill, because it is the same promise in the other direction.
     */
    private void flashSkipOffer(SkipSegment segment) {
        if (buttonSkip == null || playerView == null) {
            return;
        }
        // Do not spend the segment's one offer where it cannot be seen — it would be spent invisibly and
        // never come back on its own. The controller can still summon it, and PiP/unlock re-arms nothing.
        if (inPip || (locked && mPrefs.skipHideWhenLocked)) {
            return;
        }
        skipBriefFlashedUntilMs = segment.endMs();
        skipFlashEndMs = SystemClock.uptimeMillis() + SKIP_NOTICE_MS;
        skipPillSecs = (int) (SKIP_NOTICE_MS / 1000);
        hideSkipPillUnderline(); // the number is the countdown here; no bar to say it twice
        showSkipPill(SkipPill.SKIP, countdownLabel(R.string.button_skip_countdown, skipPillSecs), true, true);
        playerView.postDelayed(skipPillHider, SKIP_NOTICE_MS);
    }

    /**
     * After the flash, the button is the controller's companion: shown whenever the controls are, gone with
     * them. No countdown and no timer — the screen is already given over to controls, so there is nothing
     * left to keep clean, and a button vanishing from under the viewer's finger would read as a fault. It
     * takes no focus either: they are steering, and the pill must not grab the D-pad out of their hands.
     */
    private void rideSkipWithController() {
        if (locked && mPrefs.skipHideWhenLocked) {
            return;
        }
        if (controllerVisible) {
            hideSkipPillUnderline();
            showSkipPill(SkipPill.SKIP, getString(R.string.button_skip), true, false);
        } else if (skipPill == SkipPill.SKIP) {
            hideSkipButton();
        }
    }

    /**
     * Brief mode's second trigger, driven by the controller rather than the clock. Called from the
     * controller's visibility listener because the 250 ms poll is both too slow to feel like a response to
     * a tap and stopped altogether while playback is paused.
     */
    private void updateBriefSkipWithController() {
        if (player == null || skipManager == null || mPrefs == null || !mPrefs.skipEnabled) {
            return;
        }
        final double posSec = player.getCurrentPosition() / 1000.0;
        final SkipSegment segment = skipManager.activeSegment(posSec);
        if (segment == null || isAutoSkip(segment) || !isBriefSkip(segment)) {
            return;
        }
        briefSkipTick(segment);
    }

    /**
     * What the skip polling calls when there is nothing to offer. The post-skip notice is the exception:
     * it owns the pill for its three seconds, so the poll running 250 ms later must not sweep it away.
     */
    private void hideSkipButton() {
        if (skipPill == SkipPill.UNDO) {
            return;
        }
        hideSkipPill();
    }

    /**
     * Whether the "go back" pill is offered for a skip the user just made or the player just made for
     * them — {@code skipUndo} decides, so someone who never wants the pill can turn it off and someone
     * who only distrusts the automatic jumps can keep it just for those.
     */
    private boolean skipUndoOffered(boolean automatic) {
        if (Prefs.SKIP_UNDO_ALL.equals(mPrefs.skipUndo)) {
            return true;
        }
        return automatic
                ? Prefs.SKIP_UNDO_AUTO.equals(mPrefs.skipUndo)
                : Prefs.SKIP_UNDO_MANUAL.equals(mPrefs.skipUndo);
    }

    private void showSkipNotification(boolean automatic) {
        if (buttonSkip == null) {
            return;
        }
        // When configured, suppress the auto-skip notice while the screen is locked.
        if (locked && mPrefs.skipHideWhenLocked) {
            return;
        }
        // The same pill now offers to take the jump back — the cheap insurance against a wrong automatic
        // skip. It only promises undo where the promise can be kept: an episode advance has no position to
        // return to, and then it is a plain notice that takes no focus.
        final boolean undoable = skipUndoFromMs != C.TIME_UNSET && skipUndoOffered(automatic);
        skipNoticeHideAtMs = SystemClock.uptimeMillis() + SKIP_NOTICE_MS;
        skipPillSecs = (int) (SKIP_NOTICE_MS / 1000);
        hideSkipPillUnderline();
        showSkipPill(SkipPill.UNDO, undoable
                        ? countdownLabel(R.string.notification_skipped_undo, skipPillSecs)
                        : getString(R.string.notification_skipped),
                undoable, true);
        playerView.postDelayed(skipPillHider, SKIP_NOTICE_MS);
    }

    // Note: the pill is deliberately not dismissed on any user interaction. onUserInteraction runs on the
    // ACTION_DOWN, before the event reaches the view, so dismissing it there would swallow the tap that is
    // supposed to undo the skip. Its own 3s timer takes it away instead.

    private void startSkipPolling() {
        if (playerView == null) {
            return;
        }
        playerView.removeCallbacks(skipRunnable);
        playerView.post(skipRunnable);
    }

    private void stopSkipPolling() {
        if (playerView != null) {
            playerView.removeCallbacks(skipRunnable);
        }
    }

    private void parseApiPlaylist(Bundle bundle, Uri dataUri) {
        final Parcelable[] parcelableList = bundle.getParcelableArray(API_VIDEO_LIST);
        final String[] stringList = parcelableList == null ? getSmartStringArray(bundle, API_VIDEO_LIST) : null;
        final int size = parcelableList != null ? parcelableList.length
                : (stringList != null ? stringList.length : 0);
        if (size == 0) {
            return;
        }
        final String[] names = getSmartStringArray(bundle, API_VIDEO_LIST_NAME);
        final String[] filenames = getSmartStringArray(bundle, API_VIDEO_LIST_FILENAME);
        final String[] posters = getSmartStringArray(bundle, API_VIDEO_LIST_THUMBNAIL);
        final String[] segments = getSmartStringArray(bundle, API_VIDEO_LIST_SEGMENTS);
        final String[] seasons = getSmartStringArray(bundle, API_VIDEO_LIST_SEASON);
        final String[] episodes = getSmartStringArray(bundle, API_VIDEO_LIST_EPISODE);
        final String[] imdbIds = getSmartStringArray(bundle, API_VIDEO_LIST_IMDB_ID);
        final String[] tmdbIds = getSmartStringArray(bundle, API_VIDEO_LIST_ID);
        final Parcelable[] subtitles = getSmartParcelableArray(bundle, API_VIDEO_LIST_SUBTITLES);

        apiMediaItems.clear();
        apiPlaylistSegments.clear();
        apiPlaylistSeasons.clear();
        apiPlaylistEpisodes.clear();
        apiPlaylistNames.clear();
        apiPlaylistImdbIds.clear();
        apiPlaylistTmdbIds.clear();
        apiPlaylistQuality.clear();
        apiPlaylistStartIndex = 0;

        for (int i = 0; i < size; i++) {
            Uri uri = null;
            if (parcelableList != null) {
                if (parcelableList[i] instanceof Uri) {
                    uri = (Uri) parcelableList[i];
                }
            } else if (stringList[i] != null) {
                uri = Uri.parse(stringList[i]);
            }
            if (uri == null) {
                continue;
            }

            String title = names != null && i < names.length ? names[i] : null;
            if (title == null || title.isEmpty()) {
                title = filenames != null && i < filenames.length ? filenames[i] : null;
            }
            title = Utils.unescapeHtml(title);
            if (title == null || title.isEmpty()) {
                title = uri.getLastPathSegment();
            }

            Uri poster = null;
            if (posters != null && i < posters.length && posters[i] != null && !posters[i].isEmpty()) {
                poster = Uri.parse(posters[i]);
            }

            final MediaMetadata.Builder metadataBuilder = new MediaMetadata.Builder()
                    .setTitle(title)
                    .setDisplayTitle(title);
            if (poster != null) {
                metadataBuilder.setArtworkUri(poster);
            }

            if (dataUri != null && uri.equals(dataUri)) {
                apiPlaylistStartIndex = apiMediaItems.size();
            }
            final MediaItem.Builder itemBuilder = new MediaItem.Builder()
                    .setUri(uri)
                    .setMediaMetadata(metadataBuilder.build());
            // Per-episode external subtitles. They have to ride on the item itself: a playlist replaces the
            // single media item wholesale, so the apiSubs of a lone video never reach it.
            final List<MediaItem.SubtitleConfiguration> itemSubs = readPlaylistSubtitles(subtitles, i);
            if (!itemSubs.isEmpty()) {
                itemBuilder.setSubtitleConfigurations(itemSubs);
            }
            apiMediaItems.add(itemBuilder.build());
            // Keep segments aligned by index with apiMediaItems (null when absent)
            apiPlaylistSegments.add(segments != null && i < segments.length ? segments[i] : null);
            // Episode metadata, aligned by index (null when absent). Stored, not yet used.
            apiPlaylistSeasons.add(parseIntOrNull(seasons, i));
            apiPlaylistEpisodes.add(parseIntOrNull(episodes, i));
            apiPlaylistNames.add(names != null && i < names.length ? names[i] : null);
            apiPlaylistImdbIds.add(imdbIds != null && i < imdbIds.length
                    && imdbIds[i] != null && !imdbIds[i].isEmpty() ? imdbIds[i] : null);
            apiPlaylistTmdbIds.add(tmdbIds != null && i < tmdbIds.length
                    && tmdbIds[i] != null && !tmdbIds[i].isEmpty() ? tmdbIds[i] : null);
            // Per-episode quality variants, aligned by index (empty map when absent).
            apiPlaylistQuality.add(readQualityMap(bundle,
                    API_VIDEO_LIST_QUALITY_LEVELS + "." + i, API_VIDEO_LIST_QUALITY_URLS + "." + i));
        }

        // One resume slot per episode, unset until the episode has actually been played.
        apiPlaylistPositions = new long[apiMediaItems.size()];
        for (int i = 0; i < apiPlaylistPositions.length; i++) {
            apiPlaylistPositions[i] = C.TIME_UNSET;
        }
    }

    /**
     * One entry of {@code video_list.subtitles}: a Bundle per playlist item carrying parallel {@code uris}
     * and {@code names} arrays, which is the shape Lampa sends. Nothing is pre-selected — the same as a
     * single video launched with {@code subs} but no {@code subs.enable}.
     */
    private List<MediaItem.SubtitleConfiguration> readPlaylistSubtitles(Parcelable[] items, int index) {
        final List<MediaItem.SubtitleConfiguration> result = new ArrayList<>();
        if (items == null || index >= items.length || !(items[index] instanceof Bundle)) {
            return result;
        }
        final Bundle item = (Bundle) items[index];
        final Parcelable[] uris = getSmartParcelableArray(item, "uris");
        if (uris == null) {
            return result;
        }
        final String[] names = getSmartStringArray(item, "names");
        for (int i = 0; i < uris.length; i++) {
            if (!(uris[i] instanceof Uri)) {
                continue;
            }
            final String name = names != null && i < names.length ? names[i] : null;
            result.add(SubtitleUtils.buildSubtitle(this, (Uri) uris[i], name, false));
        }
        return result;
    }

    /** As {@link #getSmartStringArray}: an array or an array list, since senders use both. */
    private static Parcelable[] getSmartParcelableArray(Bundle bundle, String key) {
        final Parcelable[] array = bundle.getParcelableArray(key);
        if (array != null) {
            return array;
        }
        final ArrayList<Parcelable> list = bundle.getParcelableArrayList(key);
        return list == null ? null : list.toArray(new Parcelable[0]);
    }

    // Reads two parallel extras (labels + urls) into an insertion-ordered label->url map, sorted from
    // highest resolution to lowest and truncated to the shorter of the two arrays. Returns an empty map
    // when either side is missing, so callers never deal with null.
    private static LinkedHashMap<String, String> readQualityMap(Bundle bundle, String levelsKey, String urlsKey) {
        final LinkedHashMap<String, String> map = new LinkedHashMap<>();
        final String[] levels = getSmartStringArray(bundle, levelsKey);
        final String[] urls = getSmartQualityUrls(bundle, urlsKey);
        if (levels == null || urls == null) {
            return map;
        }
        final int count = Math.min(levels.length, urls.length);
        final ArrayList<Integer> order = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            order.add(i);
        }
        Collections.sort(order, (a, b) -> Integer.compare(qualityNumber(levels[b]), qualityNumber(levels[a])));
        for (int i : order) {
            final String label = levels[i];
            final String url = urls[i];
            if (label != null && !label.trim().isEmpty() && url != null && !url.trim().isEmpty()) {
                map.put(label, url);
            }
        }
        return map;
    }

    // Quality URLs may arrive as a String[]/ArrayList<String> or, per the LAMPA contract, as a Uri[]
    // (Parcelable[]). Normalise all of these to a String[].
    private static String[] getSmartQualityUrls(Bundle bundle, String key) {
        final String[] strings = getSmartStringArray(bundle, key);
        if (strings != null) {
            return strings;
        }
        final Parcelable[] parcelables = bundle.getParcelableArray(key);
        if (parcelables != null) {
            final String[] result = new String[parcelables.length];
            for (int i = 0; i < parcelables.length; i++) {
                result[i] = parcelables[i] == null ? null : parcelables[i].toString();
            }
            return result;
        }
        return null;
    }

    // Reads an extra that senders may pass as either a String or a numeric (e.g. TMDB "id"), returning
    // its string form, or null when absent/empty.
    private static String getStringOrIntExtra(Bundle bundle, String key) {
        if (bundle == null || !bundle.containsKey(key)) {
            return null;
        }
        final Object value = bundle.get(key);
        if (value == null) {
            return null;
        }
        final String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }

    private static Integer parseIntOrNull(String[] array, int i) {
        if (array == null || i >= array.length || array[i] == null || array[i].isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(array[i].trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String[] getSmartStringArray(Bundle bundle, String key) {
        final String[] array = bundle.getStringArray(key);
        if (array != null) {
            return array;
        }
        final ArrayList<String> list = bundle.getStringArrayList(key);
        if (list != null) {
            return list.toArray(new String[0]);
        }
        final CharSequence[] charSequences = bundle.getCharSequenceArray(key);
        if (charSequences != null) {
            final String[] result = new String[charSequences.length];
            for (int i = 0; i < charSequences.length; i++) {
                result[i] = charSequences[i] == null ? null : charSequences[i].toString();
            }
            return result;
        }
        return null;
    }

    void updateTopInfo() {
        if (player == null) {
            return;
        }
        final MediaItem item = player.getCurrentMediaItem();
        final MediaMetadata metadata = item != null ? item.mediaMetadata : null;

        CharSequence title = metadata != null ? metadata.title : null;
        if (title == null || title.length() == 0) {
            title = Utils.getFileName(this, mPrefs.mediaUri);
        }
        titleView.setText(title);

        final Uri artworkUri = metadata != null ? metadata.artworkUri : null;
        updatePoster(artworkUri, player.getCurrentMediaItemIndex(), player.getMediaItemCount());

        final boolean hasPlaylist = player.getMediaItemCount() > 1;
        if (buttonPlaylist != null) {
            buttonPlaylist.setVisibility(hasPlaylist ? View.VISIBLE : View.GONE);
        }
        updateQualityButton();
        updateAudioButton();
        // Show prev/next episode arrows (Media3 built-in, flanking play/pause) only for playlists
        playerView.setShowNextButton(hasPlaylist);
        playerView.setShowPreviousButton(hasPlaylist);

        topInfoPanel.setVisibility(View.VISIBLE);
        updateMediaInfo();
        updateEndsAt();
    }

    private TextView createInfoLine(int topMargin) {
        final TextView view = new TextView(this);
        view.setTextColor(0x99FFFFFF); // text_secondary
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, ui.textInfo());
        view.setMaxLines(1);
        view.setEllipsize(TextUtils.TruncateAt.END);
        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = topMargin;
        view.setLayoutParams(lp);
        view.setVisibility(View.GONE);
        return view;
    }

    void updateMediaInfo() {
        if (player == null) {
            return;
        }
        final Format video = player.getVideoFormat();
        setInfoLine(videoInfoView, buildVideoInfo(video));
        setInfoLine(audioInfoView, buildAudioInfo(getSelectedAudioFormat()));
    }

    private static void setInfoLine(TextView view, String text) {
        if (view == null) {
            return;
        }
        if (text != null && !text.isEmpty()) {
            view.setText(text);
            view.setVisibility(View.VISIBLE);
        } else {
            view.setVisibility(View.GONE);
        }
    }

    private static String buildVideoInfo(Format video) {
        if (video == null) {
            return null;
        }
        final StringBuilder b = new StringBuilder();
        appendField(b, resolutionClass(video.width, video.height));
        appendField(b, codecName(video));
        appendField(b, hdrName(video.colorInfo));
        return b.toString();
    }

    // Coarse resolution label (4K / 1080p / …) instead of raw pixel dimensions, to keep the header tidy.
    private static String resolutionClass(int width, int height) {
        if (width <= 0 || height <= 0) {
            return null;
        }
        final int longSide = Math.max(width, height);
        final int shortSide = Math.min(width, height);
        if (longSide >= 3840 || shortSide >= 2160) return "4K";
        if (longSide >= 2560 || shortSide >= 1440) return "1440p";
        if (longSide >= 1920 || shortSide >= 1080) return "1080p";
        if (longSide >= 1280 || shortSide >= 720) return "720p";
        if (longSide >= 640 || shortSide >= 480) return "480p";
        return shortSide + "p";
    }

    private String buildAudioInfo(Format audio) {
        if (audio == null) {
            return null;
        }
        // Same shape as the track list: <label or container name or language> [<codec> <channels> <bitrate>k] (<lang>)
        final String language = languageDisplayName(audio.language);
        // Rich release label: Media3's Format.label first, then the name read from the container.
        String metaName = audio.label;
        if ((metaName == null || metaName.isEmpty()) && audio.id != null) {
            metaName = resolvedTrackNames.get(audio.id);
        }
        final String title = (metaName != null && !metaName.isEmpty()) ? metaName : language;
        final StringBuilder b = new StringBuilder();
        if (title != null && !title.isEmpty()) {
            b.append(title);
        }
        final String tech = CustomDefaultTrackNameProvider.techInfo(audio);
        if (!tech.isEmpty()) {
            if (b.length() > 0) b.append(' ');
            b.append('[').append(tech).append(']');
        }
        // If we led with a rich name, still surface the language after it.
        if (metaName != null && !metaName.isEmpty() && language != null) {
            if (b.length() > 0) b.append(' ');
            b.append('(').append(language).append(')');
        }
        return b.toString();
    }

    /** Called on the UI thread once the container parser has recovered track names. */
    private void onContainerMetadata(String uri, java.util.List<TrackMetadata> tracks) {
        containerTracks.clear();
        containerTracks.addAll(tracks);
        containerTracksUri = uri;
        resolveTrackNames();
        updateMediaInfo();
    }

    /** {@link #containerTracks}, but only when they belong to the item playing now. */
    private java.util.List<TrackMetadata> currentContainerTracks() {
        final Uri uri = currentMediaUri();
        return uri != null && uri.toString().equals(containerTracksUri)
                ? containerTracks : java.util.Collections.emptyList();
    }

    /**
     * Maps container track names onto the player's current tracks by {@code Format.id} (the tkhd
     * trackId / MKV TrackNumber), falling back to order within each type, and stores the result in
     * {@link #resolvedTrackNames} (shared live with {@link #trackNameProvider}).
     */
    private void resolveTrackNames() {
        resolvedTrackNames.clear();
        if (player == null || currentContainerTracks().isEmpty()) {
            return;
        }
        resolveNamesForType(C.TRACK_TYPE_AUDIO, TrackMetadata.Type.AUDIO);
        resolveNamesForType(C.TRACK_TYPE_TEXT, TrackMetadata.Type.SUBTITLE);
    }

    private void resolveNamesForType(int trackType, TrackMetadata.Type metaType) {
        final java.util.List<TrackMetadata> ordered = new java.util.ArrayList<>();
        for (TrackMetadata t : currentContainerTracks()) {
            if (t.type == metaType) {
                ordered.add(t);
            }
        }
        java.util.Collections.sort(ordered, (a, b) -> Integer.compare(a.trackId, b.trackId));

        int counter = 0;
        for (Tracks.Group group : player.getCurrentTracks().getGroups()) {
            if (group.getType() != trackType) {
                continue;
            }
            for (int i = 0; i < group.length; i++) {
                final Format format = group.getMediaTrackGroup().getFormat(i);
                String name = null;
                // 1. Match by trackId (Format.id == tkhd trackId / MKV TrackNumber).
                if (format.id != null) {
                    final Integer id = tryParseInt(format.id);
                    if (id != null) {
                        for (TrackMetadata t : currentContainerTracks()) {
                            if (t.trackId == id && t.name != null && !t.name.isEmpty()) {
                                name = t.name;
                                break;
                            }
                        }
                    }
                }
                // 2. Fall back to order within this type.
                if (name == null && counter < ordered.size()) {
                    final String byOrder = ordered.get(counter).name;
                    if (byOrder != null && !byOrder.isEmpty()) {
                        name = byOrder;
                    }
                }
                if (name != null && format.id != null) {
                    resolvedTrackNames.put(format.id, name);
                }
                counter++;
            }
        }
    }

    private static Integer tryParseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String codecName(Format format) {
        final String codec = CustomDefaultTrackNameProvider.formatNameFromMime(format.sampleMimeType);
        return codec != null ? codec : CustomDefaultTrackNameProvider.formatNameFromMime(format.codecs);
    }

    private static void appendField(StringBuilder builder, String field) {
        if (field != null && !field.isEmpty()) {
            if (builder.length() > 0) builder.append(" · ");
            builder.append(field);
        }
    }

    private static String hdrName(ColorInfo colorInfo) {
        if (colorInfo == null) {
            return null;
        }
        switch (colorInfo.colorTransfer) {
            case C.COLOR_TRANSFER_ST2084:
                return "HDR10";
            case C.COLOR_TRANSFER_HLG:
                return "HLG";
            default:
                return null;
        }
    }

    private Format getSelectedAudioFormat() {
        if (player == null) {
            return null;
        }
        for (Tracks.Group group : player.getCurrentTracks().getGroups()) {
            if (group.getType() == C.TRACK_TYPE_AUDIO && group.isSelected()) {
                for (int i = 0; i < group.length; i++) {
                    if (group.isTrackSelected(i)) {
                        return group.getMediaTrackGroup().getFormat(i);
                    }
                }
                return group.getMediaTrackGroup().getFormat(0);
            }
        }
        return null;
    }

    private static String languageDisplayName(final String language) {
        if (language == null || language.isEmpty() || "und".equals(language)) {
            return null;
        }
        try {
            final String name = new Locale(language).getDisplayLanguage();
            if (name != null && !name.isEmpty() && !name.equalsIgnoreCase(language)) {
                return name.substring(0, 1).toUpperCase(Locale.getDefault()) + name.substring(1);
            }
        } catch (Exception ignored) {
        }
        return language;
    }

    void updateEndsAt() {
        if (player == null || endsAtView == null) {
            return;
        }
        final long duration = player.getDuration();
        if (!controllerVisible || duration == C.TIME_UNSET || duration <= 0) {
            endsAtView.setVisibility(View.GONE);
            return;
        }
        final long remaining = Math.max(0, duration - player.getCurrentPosition());
        float speed = player.getPlaybackParameters().speed;
        if (speed <= 0) {
            speed = 1f;
        }
        final long endMs = System.currentTimeMillis() + (long) (remaining / speed);
        final String time = DateFormat.getTimeFormat(this).format(new Date(endMs));
        endsAtView.setText(getString(R.string.time_ends_at_inline, time));
        endsAtView.setVisibility(View.VISIBLE);
    }

    // With "show clock" on, a single floating clock stays lit at all times, positioned over the header's
    // clock slot (which is kept laid-out but invisible via alpha). Toggling the controls therefore no longer
    // swaps between two clocks and makes it blink. With the preference off, only the in-header clock is used.
    void updateOverlayClock() {
        if (overlayClock == null) {
            return;
        }
        final boolean show = mPrefs.showClock && !inPip;
        if (headerClock != null) {
            headerClock.setAlpha(show ? 0f : 1f);
        }
        if (show) {
            syncOverlayClockPosition();
        }
        overlayClock.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    // Mirror the floating clock onto the in-header clock's on-screen position so switching between the two
    // (as the controls hide/show) is seamless. Skips while the header clock isn't laid out, keeping the last
    // known position rather than snapping to the top-left corner.
    private void syncOverlayClockPosition() {
        if (overlayClock == null || headerClock == null || coordinatorLayout == null
                || headerClock.getWidth() == 0) {
            return;
        }
        final int[] clockLoc = new int[2];
        final int[] rootLoc = new int[2];
        headerClock.getLocationInWindow(clockLoc);
        coordinatorLayout.getLocationInWindow(rootLoc);
        final ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) overlayClock.getLayoutParams();
        final int left = clockLoc[0] - rootLoc[0];
        final int top = clockLoc[1] - rootLoc[1];
        if (lp.leftMargin != left || lp.topMargin != top) {
            lp.leftMargin = left;
            lp.topMargin = top;
            overlayClock.setLayoutParams(lp);
        }
    }

    private final Runnable swipeHider = this::hideSwipeToUnlock;

    // Reveal the swipe-to-unlock bar and auto-hide it after the standard long-touch timeout. While the user
    // is actively dragging (onStartTouching) the auto-hide is cancelled and rescheduled on release.
    void showSwipeToUnlock() {
        if (swipeToUnlock == null || inPip) {
            return;
        }
        swipeToUnlock.setVisibility(View.VISIBLE);
        rescheduleSwipeHide();
    }

    private void rescheduleSwipeHide() {
        if (playerView != null) {
            playerView.removeCallbacks(swipeHider);
            playerView.postDelayed(swipeHider, CustomPlayerView.MESSAGE_TIMEOUT_LONG);
        }
    }

    void hideSwipeToUnlock() {
        if (swipeToUnlock == null) {
            return;
        }
        if (playerView != null) {
            playerView.removeCallbacks(swipeHider);
        }
        swipeToUnlock.setVisibility(View.GONE);
    }

    /**
     * Show or hide a view that floats on the coordinator beside the controls, fading it out the way the
     * controls fade themselves. A sibling of the control view cannot inherit that fade (only its children
     * can — the header panel is inside exo_controls_background for exactly this reason), and Media3 reports
     * the controls hidden a good two seconds after the bars have gone: it fades the bars, leaves the seek
     * bar alone for ANIMATION_INTERVAL_MS, and only then drops the whole thing to GONE. Chrome switched off
     * at that point stands at full opacity over a picture the rest of the chrome has already left, then
     * blinks out. So it is faded here instead, on Media3's own duration, from the frame the bars start
     * fading — the visibility listener is called then too, with the controls still nominally visible but no
     * longer <em>fully</em> visible, which is what {@code controllerVisibleFully} carries to the callers.
     */
    private static void fadeChrome(View view, boolean show) {
        if (view == null) {
            return;
        }
        if (show) {
            view.animate().cancel();
            view.setAlpha(1f);
            view.setVisibility(View.VISIBLE);
        } else if (view.getVisibility() == View.VISIBLE && view.getAlpha() == 1f) {
            // Full alpha, not mere visibility, is the guard: this runs again on every stats tick and every
            // room heartbeat while the controls go away, and a restarted fade would never finish.
            view.animate().alpha(0f).setDuration(CHROME_FADE_MS).withEndAction(() -> {
                if (view.getAlpha() == 0f) { // a re-show mid-fade put it back; do not undo that here
                    view.setVisibility(View.GONE);
                }
            });
        }
    }

    private void startEndsAtUpdates() {
        if (playerView == null) {
            return;
        }
        playerView.removeCallbacks(endsAtRunnable);
        playerView.post(endsAtRunnable);
    }

    private void stopEndsAtUpdates() {
        if (playerView != null) {
            playerView.removeCallbacks(endsAtRunnable);
        }
        if (endsAtView != null) {
            endsAtView.setVisibility(View.GONE);
        }
        if (statsView != null) {
            statsView.setVisibility(View.GONE);
        }
    }

    /**
     * The stats panel's text. Deliberately only what the header does not already say — it is on screen at
     * the same time, and repeating "1080p H264" there would be noise. What is left is the figures that
     * move and the decoder names the coarse codec label hides.
     */
    private void updateStats() {
        if (statsView == null) {
            return;
        }
        if (!mPrefs.showStats || player == null || !controllerChromeVisible || inPip) {
            fadeChrome(statsView, false);
            return;
        }
        final StringBuilder text = new StringBuilder();
        // Ahead of the playhead, against the load control's target — the "how much slack is there"
        // reading. On a local file this is always full, which is itself the answer.
        final long bufferedMs = player.getTotalBufferedDuration();
        text.append(getString(R.string.stats_buffer, bufferedMs / 1000,
                (int) Math.min(100, bufferedMs * 100 / DefaultLoadControl.DEFAULT_MAX_BUFFER_MS)));
        if (bandwidthBitrate > 0) {
            text.append('\n').append(getString(R.string.stats_network,
                    getString(R.string.quality_bitrate, bandwidthBitrate / 1_000_000f)));
        }
        final Format video = player.getVideoFormat();
        if (video != null) {
            text.append('\n').append(video.width).append('×').append(video.height);
            // Media3 fills the rate in from MP4 only; for MKV and AVI it reads the container's own figure
            // for its timing but never publishes it, so the parser we already run picks it up instead.
            final float frameRate = videoFrameRate();
            if (frameRate > 0) {
                text.append(String.format(Locale.US, " · %.2f fps", frameRate));
            }
            // Its own line rather than a third field: the panel has to stay narrower than the gap to the
            // centred transport buttons, and every line here is kept short enough to never wrap.
            if (video.bitrate != Format.NO_VALUE) {
                text.append('\n').append(getString(R.string.stats_stream,
                        getString(R.string.quality_bitrate, video.bitrate / 1_000_000f)));
            } else {
                // Matroska and AVI carry no bitrate to read, so the whole file over its duration is all
                // there is. Labelled apart from Stream: it counts audio and subtitles in too.
                final float overall = overallBitrate();
                if (overall > 0) {
                    text.append('\n').append(getString(R.string.stats_overall,
                            getString(R.string.quality_bitrate, overall)));
                }
            }
        }
        // The mime says "hevc"; only the decoder name says whether that went to the vendor's hardware
        // codec or to a software one, which is the whole question behind most "it stutters" reports.
        // One per line: the two of them on one line was the panel's widest content by a wide margin.
        if (videoDecoderName != null) {
            text.append('\n').append(videoDecoderName);
        }
        if (audioDecoderName != null) {
            text.append('\n').append(audioDecoderName);
        }
        final DecoderCounters counters = player.getVideoDecoderCounters();
        if (counters != null) {
            text.append('\n').append(getString(R.string.stats_dropped, counters.droppedBufferCount));
        }
        statsView.setText(text);
        fadeChrome(statsView, true);
    }

    /**
     * The playing video's frame rate: from {@link Format} where Media3 fills it in — MP4, DASH, and HLS
     * that declares FRAME-RATE — and from the container header otherwise. 0 when neither states one,
     * which is Matroska and AVI until the header parse lands, and MPEG-TS always.
     */
    private float videoFrameRate() {
        final Format video = player != null ? player.getVideoFormat() : null;
        return video != null && video.frameRate != Format.NO_VALUE
                ? video.frameRate : containerFrameRate();
    }

    /** The video track's frame rate as its container states it, or 0 when it does not. */
    private float containerFrameRate() {
        for (TrackMetadata track : currentContainerTracks()) {
            if (track.type == TrackMetadata.Type.VIDEO && track.frameRate > 0) {
                return track.frameRate;
            }
        }
        return 0f;
    }

    /** The whole file's average bitrate in Mbit/s, or 0 while either its length or duration is unknown. */
    private float overallBitrate() {
        final long durationMs = player.getDuration();
        if (durationMs <= 0 || mPrefs.mediaUri == null) {
            return 0f;
        }
        final Long length = contentLengths.get(mPrefs.mediaUri.toString());
        // bytes * 8 bits / (ms / 1000) / 1e6 Mbit == bytes / (ms * 125)
        return length == null ? 0f : length / (durationMs * 125f);
    }

    /**
     * The same dump the error screen carries, on the error screen itself — so a report about stuttering
     * or a wrong decoder reads like one about a crash, appendPlayerState stays the single source for
     * both, and the dump leaves a TV box by QR instead of a clipboard nothing there can paste from.
     */
    private void showPlayerState() {
        final StringBuilder state = new StringBuilder();
        appendPlayerState(state);
        if (state.length() == 0) {
            return;
        }
        final Format video = player == null ? null : player.getVideoFormat();
        ErrorActivity.showReport(this, getString(R.string.stats_report_title),
                getString(R.string.stats_report_message),
                video == null ? null : Format.toLogString(video), state.toString().trim());
    }

    // Small episode-number chip, inset from the poster's top-start corner so its rounded corners don't
    // clash with the poster's rounded clip. Reused by the header poster and the playlist rows.
    private TextView createPosterNumberBadge() {
        final TextView badge = new TextView(this);
        final FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.setMargins(Utils.dpToPx(3), Utils.dpToPx(3), 0, 0);
        badge.setLayoutParams(lp);
        final GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xCC000000);
        bg.setCornerRadius(Utils.dpToPx(3));
        badge.setBackground(bg);
        badge.setGravity(Gravity.CENTER);
        badge.setMinWidth(Utils.dpToPx(18));
        badge.setPadding(Utils.dpToPx(5), 0, Utils.dpToPx(5), Utils.dpToPx(1));
        badge.setTextColor(Color.WHITE);
        badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, ui.textBadge());
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setVisibility(View.GONE);
        return badge;
    }

    private void updatePoster(final Uri uri, final int index, final int count) {
        final boolean isPlaylist = count > 1;
        final String number = String.valueOf(index + 1);
        posterBadgeView.setText(number);
        posterPlaceholderView.setText(number);

        if (uri != null) {
            posterSlot.setVisibility(View.VISIBLE);
            posterView.setVisibility(View.VISIBLE);
            posterPlaceholderView.setVisibility(View.GONE);
            posterBadgeView.setVisibility(isPlaylist ? View.VISIBLE : View.GONE);
            Glide.with(this)
                    .load(uri)
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                            showPosterFallback(isPlaylist);
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                            return false;
                        }
                    })
                    .into(posterView);
        } else {
            showPosterFallback(isPlaylist);
        }
    }

    private void showPosterFallback(final boolean isPlaylist) {
        if (isPlaylist) {
            posterSlot.setVisibility(View.VISIBLE);
            posterView.setVisibility(View.GONE);
            posterBadgeView.setVisibility(View.GONE);
            posterPlaceholderView.setVisibility(View.VISIBLE);
        } else {
            posterSlot.setVisibility(View.GONE);
        }
    }

    private void showPlaylistDialog() {
        // A failure releases the player but not the playlist, and stepping off the broken episode is
        // exactly what this list is for — so take the items from whichever source is still alive.
        final boolean fromPlayer = player != null && player.getMediaItemCount() > 1;
        final List<MediaItem> mediaItems = new ArrayList<>();
        if (fromPlayer) {
            for (int i = 0; i < player.getMediaItemCount(); i++) {
                mediaItems.add(player.getMediaItemAt(i));
            }
        } else {
            mediaItems.addAll(apiMediaItems);
        }
        if (mediaItems.size() <= 1) {
            return;
        }
        final int count = mediaItems.size();
        final int current = fromPlayer ? player.getCurrentMediaItemIndex() : apiPlaylistStartIndex;
        final int radius = Utils.dpToPx(4);
        final View[] currentRow = new View[1];

        final LinearLayout listLayout = new LinearLayout(this);
        listLayout.setOrientation(LinearLayout.VERTICAL);
        final int listPad = Utils.dpToPx(10);
        listLayout.setPadding(listPad, listPad, listPad, listPad);

        final TextView header = new TextView(this);
        header.setText(getString(R.string.playlist));
        header.setTextColor(Color.WHITE);
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, ui.textTitle());
        header.setTypeface(Typeface.DEFAULT_BOLD);
        header.setPadding(Utils.dpToPx(10), Utils.dpToPx(10), Utils.dpToPx(10), Utils.dpToPx(10));
        listLayout.addView(header);

        final View divider = new View(this);
        final LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Utils.dpToPx(1));
        dividerLp.bottomMargin = Utils.dpToPx(4);
        divider.setLayoutParams(dividerLp);
        divider.setBackgroundColor(0x1AFFFFFF);
        listLayout.addView(divider);

        for (int i = 0; i < count; i++) {
            final int index = i;
            final MediaItem item = mediaItems.get(i);
            final MediaMetadata md = item.mediaMetadata;
            CharSequence title = md != null ? md.title : null;
            if (title == null || title.length() == 0) {
                title = "Video " + (i + 1);
            }
            final Uri artwork = md != null ? md.artworkUri : null;
            final boolean isCurrent = i == current;

            final LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Utils.dpToPx(8), Utils.dpToPx(7), Utils.dpToPx(10), Utils.dpToPx(7));
            row.setClickable(true);
            row.setFocusable(true);
            row.setMinimumHeight(ui.rowMinHeight());
            // Rounded row: subtle fill for the current item, plus a rounded ripple for touch/D-pad focus.
            final GradientDrawable rowContent = new GradientDrawable();
            rowContent.setCornerRadius(Utils.dpToPx(8));
            rowContent.setColor(isCurrent ? brandColorDim() : Color.TRANSPARENT);
            final GradientDrawable rowMask = new GradientDrawable();
            rowMask.setCornerRadius(Utils.dpToPx(8));
            rowMask.setColor(Color.WHITE);
            row.setBackground(new RippleDrawable(ColorStateList.valueOf(0x40FFFFFF), rowContent, rowMask));
            if (isCurrent) {
                currentRow[0] = row;
            }

            final FrameLayout box = new FrameLayout(this);
            final LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, Utils.dpToPx(56));
            boxLp.setMarginEnd(Utils.dpToPx(12));
            boxLp.gravity = Gravity.CENTER_VERTICAL;
            box.setLayoutParams(boxLp);
            box.setMinimumWidth(Utils.dpToPx(40));
            box.setBackgroundColor(0xFF2A2A2A);
            box.setClipToOutline(true);
            box.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
                }
            });

            final ImageView poster = new ImageView(this);
            poster.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.MATCH_PARENT));
            poster.setAdjustViewBounds(true);
            poster.setScaleType(ImageView.ScaleType.FIT_CENTER);
            box.addView(poster);

            if (artwork != null) {
                Glide.with(this).load(artwork).into(poster);
                final TextView numberChip = createPosterNumberBadge();
                numberChip.setText(String.valueOf(i + 1));
                numberChip.setVisibility(View.VISIBLE);
                box.addView(numberChip);
            } else {
                poster.setVisibility(View.GONE);
                final TextView number = new TextView(this);
                number.setText(String.valueOf(i + 1));
                number.setTypeface(Typeface.DEFAULT_BOLD);
                number.setLayoutParams(new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
                number.setGravity(Gravity.CENTER);
                number.setMinWidth(Utils.dpToPx(40));
                number.setTextColor(0x99FFFFFF);
                number.setTextSize(TypedValue.COMPLEX_UNIT_SP, ui.textListNumber());
                box.addView(number);
            }
            row.addView(box);

            final TextView titleText = new TextView(this);
            titleText.setText(title);
            titleText.setTextColor(isCurrent ? 0xFFFFFFFF : 0xFFDDDDDD);
            titleText.setTextSize(TypedValue.COMPLEX_UNIT_SP, ui.textList());
            if (isCurrent) {
                titleText.setTypeface(Typeface.DEFAULT_BOLD);
            }
            final LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            titleLp.gravity = Gravity.CENTER_VERTICAL;
            titleText.setLayoutParams(titleLp);
            row.addView(titleText);
            fitLongText(row, titleText);

            row.setOnClickListener(v -> {
                if (player != null) {
                    player.seekToDefaultPosition(index);
                    player.setPlayWhenReady(true);
                    prepareIfIdle();
                } else {
                    // Nothing left to seek: a failure released the player, so start the picked episode
                    // from scratch (initializePlayer rebuilds the playlist from this index) and resume
                    // it where it was left, if it was ever played.
                    apiPlaylistStartIndex = index;
                    final long saved = apiPlaylistPositions != null && index < apiPlaylistPositions.length
                            ? apiPlaylistPositions[index] : C.TIME_UNSET;
                    mPrefs.updatePosition(saved == C.TIME_UNSET ? 0 : saved);
                    playerView.setCustomErrorMessage(null);
                    initializePlayer();
                }
                if (playlistDialog != null) {
                    playlistDialog.dismiss();
                }
            });

            listLayout.addView(row);
        }

        final android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        scrollView.addView(listLayout);
        // The dialog spans the full height behind the status/navigation bars, so pad the content clear of
        // them. Use a FIXED inset captured now (the status bar is visible while the controls — and thus this
        // dialog — are shown): a dynamic inset listener would drop to 0 when hideController() flips the
        // activity to immersive flags, making the list visibly "jump" up under the still-visible status bar.
        Utils.padForPickerInsets(this, ui, coordinatorLayout, scrollView, ui.overscanH(), 0, 0);

        if (playlistDialog != null) {
            playlistDialog.dismiss();
        }
        playlistDialog = new android.app.Dialog(this, android.R.style.Theme_Translucent_NoTitleBar);
        playlistDialog.setContentView(scrollView);
        playlistDialog.setCanceledOnTouchOutside(true);
        final Window window = playlistDialog.getWindow();
        if (window != null) {
            // Deliberately NOT fullscreen/edge-to-edge: a fullscreen dialog window makes OxygenOS treat the
            // panel as immersive and apply its two-swipe back-gesture guard. A plain window closes on one back.
            window.setLayout(ui.pickerWidthPx(getResources().getConfiguration()), ViewGroup.LayoutParams.MATCH_PARENT);
            window.setGravity(Gravity.END);
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0xF0141414));
        }
        // Hide the player's overlay (header + bottom controls) so only the playlist panel is shown.
        showPickerDialog(playlistDialog);
        if (currentRow[0] != null) {
            currentRow[0].post(() -> currentRow[0].requestFocus());
        }
    }

    // ---- Manual video quality (LAMPA quality-switching port) --------------------------------------

    // Quality map for the item currently playing: per-episode for a playlist, or the single-video map.
    private LinkedHashMap<String, String> currentQualityMap() {
        if (player != null && !apiPlaylistQuality.isEmpty()) {
            final int index = player.getCurrentMediaItemIndex();
            if (index >= 0 && index < apiPlaylistQuality.size()) {
                return apiPlaylistQuality.get(index);
            }
        }
        return apiSingleQuality;
    }

    // URI of the item currently loaded in the player (or the persisted media URI as a fallback).
    private Uri currentPlayingUri() {
        if (player != null) {
            final MediaItem item = player.getCurrentMediaItem();
            if (item != null && item.localConfiguration != null) {
                return item.localConfiguration.uri;
            }
        }
        return mPrefs.mediaUri;
    }

    // Builds the list shown in the quality menu. Auto/Maximum and per-rendition entries are added ONLY
    // when the stream offers a real in-stream choice (>= 2 selectable video renditions); a single
    // progressive track degrades to a plain list of SOURCE (separate-URL) variants.
    private ArrayList<VideoQualityChoice> buildQualityChoices() {
        final ArrayList<VideoQualityChoice> choices = new ArrayList<>();
        if (player == null) {
            return choices;
        }

        final HashMap<Integer, VideoQualityChoice> renditions = new HashMap<>();
        for (Tracks.Group group : player.getCurrentTracks().getGroups()) {
            if (group.getType() != C.TRACK_TYPE_VIDEO) continue;
            for (int index = 0; index < group.length; index++) {
                if (!group.isTrackSupported(index)) continue;
                Format format = group.getTrackFormat(index);
                int longSide = Math.max(format.width, format.height);
                int shortSide = Math.min(format.width, format.height);
                if (longSide <= 0) continue;
                VideoQualityChoice previous = renditions.get(longSide);
                if (previous == null || format.bitrate > previous.bitrate) {
                    String codec = shortCodec(format.sampleMimeType);
                    String dimensions = shortSide > 0
                            ? longSide + " × " + shortSide : String.valueOf(longSide);
                    String details = codec == null ? dimensions : dimensions + "  •  " + codec;
                    String bitrate = format.bitrate > 0
                            ? getString(R.string.quality_bitrate, format.bitrate / 1_000_000f) : "";
                    // Same label the header badge shows, so the two never disagree.
                    String label = resolutionClass(format.width, format.height);
                    renditions.put(longSide, VideoQualityChoice.track(
                            label != null ? label : longSide + "p", details, bitrate,
                            group.getMediaTrackGroup(), index, format.bitrate));
                }
            }
        }
        if (renditions.size() >= 2) {
            choices.add(VideoQualityChoice.auto());
            choices.add(VideoQualityChoice.maximum());
            ArrayList<Integer> longSides = new ArrayList<>(renditions.keySet());
            Collections.sort(longSides, Collections.reverseOrder());
            for (Integer longSide : longSides) choices.add(renditions.get(longSide));
        }

        final LinkedHashMap<String, String> quality = currentQualityMap();
        if (quality != null) {
            for (Map.Entry<String, String> entry : quality.entrySet()) {
                if (entry.getValue() != null && !entry.getValue().trim().isEmpty()) {
                    choices.add(VideoQualityChoice.source(entry.getKey(), entry.getValue()));
                }
            }
        }
        return choices;
    }

    // Shows the quality button whenever there is more than one thing to choose between.
    private void updateQualityButton() {
        if (buttonQuality == null) {
            return;
        }
        final boolean show = player != null && buildQualityChoices().size() >= 2;
        buttonQuality.setVisibility(show ? View.VISIBLE : View.GONE);
        // Light the HD icon coral when a specific quality is pinned (anything other than Auto).
        buttonQuality.setSelected(selectedVideoQualityMode != VideoQualityChoice.MODE_AUTO);
    }

    private String qualityChoiceTitle(VideoQualityChoice choice) {
        switch (choice.mode) {
            case VideoQualityChoice.MODE_AUTO:
                return getString(R.string.quality_auto);
            case VideoQualityChoice.MODE_MAXIMUM:
                return getString(R.string.quality_maximum);
            default:
                return choice.label;
        }
    }

    private String qualityChoiceSubtitle(VideoQualityChoice choice) {
        switch (choice.mode) {
            case VideoQualityChoice.MODE_AUTO:
                return getString(R.string.quality_auto_description);
            case VideoQualityChoice.MODE_MAXIMUM:
                return getString(R.string.quality_maximum_badge);
            default:
                return choice.details;
        }
    }

    // Quality menu in JAPP's native style (modelled on showPlaylistDialog): a full-height translucent
    // panel docked to the end edge, with the current choice ticked and reachable by remote.
    private void showQualityDialog() {
        if (player == null) {
            Toast.makeText(this, R.string.quality_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        final ArrayList<VideoQualityChoice> choices = buildQualityChoices();
        if (choices.size() < 2) {
            Toast.makeText(this, R.string.quality_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        final int selected = selectedQualityIndex(choices);
        final View[] currentRow = new View[1];

        final LinearLayout listLayout = new LinearLayout(this);
        listLayout.setOrientation(LinearLayout.VERTICAL);
        final int listPad = Utils.dpToPx(10);
        listLayout.setPadding(listPad, listPad, listPad, listPad);

        final TextView header = new TextView(this);
        header.setText(getString(R.string.quality_title));
        header.setTextColor(Color.WHITE);
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, ui.textTitle());
        header.setTypeface(Typeface.DEFAULT_BOLD);
        header.setPadding(Utils.dpToPx(10), Utils.dpToPx(10), Utils.dpToPx(10), Utils.dpToPx(10));
        listLayout.addView(header);

        final View divider = new View(this);
        final LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Utils.dpToPx(1));
        dividerLp.bottomMargin = Utils.dpToPx(4);
        divider.setLayoutParams(dividerLp);
        divider.setBackgroundColor(0x1AFFFFFF);
        listLayout.addView(divider);

        for (int i = 0; i < choices.size(); i++) {
            final VideoQualityChoice choice = choices.get(i);
            final boolean isCurrent = i == selected;

            final LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Utils.dpToPx(12), Utils.dpToPx(10), Utils.dpToPx(12), Utils.dpToPx(10));
            row.setClickable(true);
            row.setFocusable(true);
            row.setMinimumHeight(ui.rowMinHeight());
            final GradientDrawable rowContent = new GradientDrawable();
            rowContent.setCornerRadius(Utils.dpToPx(8));
            rowContent.setColor(isCurrent ? brandColorDim() : Color.TRANSPARENT);
            final GradientDrawable rowMask = new GradientDrawable();
            rowMask.setCornerRadius(Utils.dpToPx(8));
            rowMask.setColor(Color.WHITE);
            row.setBackground(new RippleDrawable(ColorStateList.valueOf(0x40FFFFFF), rowContent, rowMask));
            if (isCurrent) {
                currentRow[0] = row;
            }

            final LinearLayout textBlock = new LinearLayout(this);
            textBlock.setOrientation(LinearLayout.VERTICAL);
            final LinearLayout.LayoutParams blockLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            blockLp.gravity = Gravity.CENTER_VERTICAL;
            textBlock.setLayoutParams(blockLp);

            final TextView title = new TextView(this);
            title.setText(qualityChoiceTitle(choice));
            title.setTextColor(isCurrent ? 0xFFFFFFFF : 0xFFDDDDDD);
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, ui.textBody());
            if (isCurrent) {
                title.setTypeface(Typeface.DEFAULT_BOLD);
            }
            textBlock.addView(title);

            final String subtitle = qualityChoiceSubtitle(choice);
            TextView details = null;
            if (subtitle != null && !subtitle.isEmpty()) {
                details = new TextView(this);
                details.setText(subtitle);
                details.setTextColor(0x99FFFFFF);
                details.setTextSize(TypedValue.COMPLEX_UNIT_SP, ui.textCaption());
                textBlock.addView(details);
            }
            row.addView(textBlock);
            fitLongText(row, title, details);

            if (choice.bitrateText != null && !choice.bitrateText.isEmpty()) {
                final TextView bitrate = new TextView(this);
                bitrate.setText(choice.bitrateText);
                bitrate.setTextColor(0x99FFFFFF);
                bitrate.setTextSize(TypedValue.COMPLEX_UNIT_SP, ui.textCaption());
                bitrate.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
                bitrate.setSingleLine(true);
                final LinearLayout.LayoutParams bitrateLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                bitrateLp.setMarginEnd(Utils.dpToPx(10));
                bitrateLp.gravity = Gravity.CENTER_VERTICAL;
                bitrate.setLayoutParams(bitrateLp);
                row.addView(bitrate);
            }

            row.setOnClickListener(v -> {
                applyVideoQuality(choice);
                if (qualityDialog != null) {
                    qualityDialog.dismiss();
                }
            });
            listLayout.addView(row);
        }

        final android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        scrollView.addView(listLayout);
        Utils.padForPickerInsets(this, ui, coordinatorLayout, scrollView, ui.overscanH(), 0, 0);

        if (qualityDialog != null) {
            qualityDialog.dismiss();
        }
        qualityDialog = new android.app.Dialog(this, android.R.style.Theme_Translucent_NoTitleBar);
        qualityDialog.setContentView(scrollView);
        qualityDialog.setCanceledOnTouchOutside(true);
        final Window window = qualityDialog.getWindow();
        if (window != null) {
            // Deliberately NOT fullscreen/edge-to-edge: a fullscreen dialog window makes OxygenOS treat the
            // panel as immersive and apply its two-swipe back-gesture guard. A plain window closes on one back.
            window.setLayout(ui.pickerWidthPx(getResources().getConfiguration()), ViewGroup.LayoutParams.MATCH_PARENT);
            window.setGravity(Gravity.END);
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0xF0141414));
        }
        showPickerDialog(qualityDialog);
        if (currentRow[0] != null) {
            currentRow[0].post(() -> currentRow[0].requestFocus());
        }
    }

    private void applyVideoQuality(VideoQualityChoice choice) {
        if (player == null || choice == null) {
            return;
        }
        if (choice.mode == VideoQualityChoice.MODE_SOURCE) {
            if (choice.sourceUrl == null || choice.sourceUrl.trim().isEmpty()) {
                return;
            }
            final Uri target = Uri.parse(choice.sourceUrl);
            if (target.equals(currentPlayingUri())) {
                return; // re-selecting the current URL is a no-op
            }
            selectedVideoQualityMode = VideoQualityChoice.MODE_SOURCE;
            selectedVideoTrackGroup = null;
            selectedVideoTrackIndex = -1;
            stickyQualityLines = qualityNumber(choice.label);
            switchSource(target, Math.max(0, player.getCurrentPosition()), player.getPlayWhenReady());
            return;
        }

        selectedVideoQualityMode = choice.mode;
        selectedVideoTrackGroup = choice.group;
        selectedVideoTrackIndex = choice.trackIndex;
        // A manual in-stream choice clears any sticky SOURCE preference for following episodes.
        stickyQualityLines = 0;
        TrackSelectionParameters.Builder builder = player.getTrackSelectionParameters().buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                .setForceHighestSupportedBitrate(choice.mode == VideoQualityChoice.MODE_MAXIMUM);
        if (choice.mode == VideoQualityChoice.MODE_TRACK && choice.group != null) {
            builder.setOverrideForType(new TrackSelectionOverride(
                    choice.group, Collections.singletonList(choice.trackIndex)));
        }
        player.setTrackSelectionParameters(builder.build());
    }

    // A row in the native side-panel menus (audio / speed / more).
    private static class MenuItem {
        final int iconRes;
        /** Artwork to lead the row with instead of the glyph — a room's poster. Null for everything else. */
        final String imageUrl;
        final CharSequence title;
        final CharSequence subtitle;
        final boolean checked;
        final Runnable action;

        MenuItem(CharSequence title, CharSequence subtitle, boolean checked, Runnable action) {
            this(0, title, subtitle, checked, action);
        }

        MenuItem(int iconRes, CharSequence title, CharSequence subtitle, boolean checked, Runnable action) {
            this(iconRes, null, title, subtitle, checked, action);
        }

        MenuItem(int iconRes, String imageUrl, CharSequence title, CharSequence subtitle,
                 boolean checked, Runnable action) {
            this.iconRes = iconRes;
            this.imageUrl = imageUrl;
            this.title = title;
            this.subtitle = subtitle;
            this.checked = checked;
            this.action = action;
        }
    }

    // Full-height translucent panel docked to the end edge, matching the quality/playlist menus.
    private void showSideMenu(CharSequence menuTitle, List<MenuItem> items) {
        showSideMenu(menuTitle, items, 34, 48);
    }

    /**
     * @param posterWDp poster size for rows that carry artwork. The default is a thumbnail beside a
     *                  name that already says everything; a list where the poster is what tells the
     *                  rows apart — search results for a name typed from across the room — asks for
     *                  bigger, and nothing else about the panel changes.
     */
    private void showSideMenu(CharSequence menuTitle, List<MenuItem> items, int posterWDp, int posterHDp) {
        if (items == null || items.isEmpty()) {
            return;
        }
        final View[] currentRow = new View[1];

        final LinearLayout listLayout = new LinearLayout(this);
        listLayout.setOrientation(LinearLayout.VERTICAL);
        final int listPad = Utils.dpToPx(10);
        listLayout.setPadding(listPad, listPad, listPad, listPad);

        final TextView header = new TextView(this);
        header.setText(menuTitle);
        header.setTextColor(Color.WHITE);
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, ui.textTitle());
        header.setTypeface(Typeface.DEFAULT_BOLD);
        header.setPadding(Utils.dpToPx(10), Utils.dpToPx(10), Utils.dpToPx(10), Utils.dpToPx(10));
        listLayout.addView(header);

        final View divider = new View(this);
        final LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Utils.dpToPx(1));
        dividerLp.bottomMargin = Utils.dpToPx(4);
        divider.setLayoutParams(dividerLp);
        divider.setBackgroundColor(0x1AFFFFFF);
        listLayout.addView(divider);

        for (final MenuItem item : items) {
            final boolean isCurrent = item.checked;

            final LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Utils.dpToPx(12), Utils.dpToPx(10), Utils.dpToPx(12), Utils.dpToPx(10));
            row.setClickable(true);
            row.setFocusable(true);
            row.setMinimumHeight(ui.rowMinHeight());
            final GradientDrawable rowContent = new GradientDrawable();
            rowContent.setCornerRadius(Utils.dpToPx(8));
            rowContent.setColor(isCurrent ? brandColorDim() : Color.TRANSPARENT);
            final GradientDrawable rowMask = new GradientDrawable();
            rowMask.setCornerRadius(Utils.dpToPx(8));
            rowMask.setColor(Color.WHITE);
            row.setBackground(new RippleDrawable(ColorStateList.valueOf(0x40FFFFFF), rowContent, rowMask));
            if (isCurrent) {
                currentRow[0] = row;
            }

            // A room with artwork leads with it rather than with a glyph: in a list of rooms the poster is
            // what tells them apart, and the same corner radius the header's poster uses keeps the two
            // readings of "this film" looking like one thing. Cropped, not fitted — a row of posters with
            // ragged widths reads as broken, and losing a sliver of a 2:3 image costs nothing.
            if (item.imageUrl != null && !item.imageUrl.isEmpty()) {
                final ImageView art = new ImageView(this);
                art.setScaleType(ImageView.ScaleType.CENTER_CROP);
                // Same placeholder as the header's poster slot, so a slow load is a quiet grey card
                // rather than a hole that shifts the text when it fills.
                art.setBackgroundColor(0xFF333333);
                final int artCorner = Utils.dpToPx(4);
                art.setClipToOutline(true);
                art.setOutlineProvider(new ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, Outline outline) {
                        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), artCorner);
                    }
                });
                final LinearLayout.LayoutParams artLp =
                        new LinearLayout.LayoutParams(ui.dpS(posterWDp), ui.dpS(posterHDp));
                artLp.setMarginEnd(Utils.dpToPx(16));
                art.setLayoutParams(artLp);
                row.addView(art);
                Glide.with(this).load(item.imageUrl).into(art);
            } else if (item.iconRes != 0) {
                final ImageView icon = new ImageView(this);
                icon.setImageResource(item.iconRes);
                icon.setImageTintList(ColorStateList.valueOf(isCurrent ? 0xFFFFFFFF : 0xFFDDDDDD));
                final int iconSize = Utils.dpToPx(22);
                final LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconSize, iconSize);
                iconLp.setMarginEnd(Utils.dpToPx(16));
                icon.setLayoutParams(iconLp);
                row.addView(icon);
            }

            final LinearLayout textBlock = new LinearLayout(this);
            textBlock.setOrientation(LinearLayout.VERTICAL);
            final LinearLayout.LayoutParams blockLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            blockLp.gravity = Gravity.CENTER_VERTICAL;
            textBlock.setLayoutParams(blockLp);

            final TextView title = new TextView(this);
            title.setText(item.title);
            title.setTextColor(isCurrent ? 0xFFFFFFFF : 0xFFDDDDDD);
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, ui.textBody());
            if (isCurrent) {
                title.setTypeface(Typeface.DEFAULT_BOLD);
            }
            // The poster took the glyph's place, so whatever the glyph was saying moves in beside the
            // name — for a room that is the padlock, and losing it would leave the password to be
            // discovered by tapping.
            if (item.imageUrl != null && !item.imageUrl.isEmpty() && item.iconRes != 0) {
                final Drawable mark = ContextCompat.getDrawable(this, item.iconRes);
                if (mark != null) {
                    final int markBox = Math.round(title.getTextSize());
                    mark.setBounds(0, 0, markBox, markBox);
                    mark.setTintList(ColorStateList.valueOf(isCurrent ? 0xFFFFFFFF : 0xFFDDDDDD));
                    title.setCompoundDrawablesRelative(mark, null, null, null);
                    title.setCompoundDrawablePadding(Utils.dpToPx(6));
                }
            }
            textBlock.addView(title);

            TextView details = null;
            if (item.subtitle != null && item.subtitle.length() > 0) {
                details = new TextView(this);
                details.setText(item.subtitle);
                details.setTextColor(0x99FFFFFF);
                details.setTextSize(TypedValue.COMPLEX_UNIT_SP, ui.textCaption());
                textBlock.addView(details);
            }
            row.addView(textBlock);
            fitLongText(row, title, details);

            row.setOnClickListener(v -> {
                if (menuDialog != null) {
                    menuDialog.dismiss();
                }
                if (item.action != null) {
                    item.action.run();
                }
            });
            listLayout.addView(row);
        }

        final android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        scrollView.addView(listLayout);
        Utils.padForPickerInsets(this, ui, coordinatorLayout, scrollView, ui.overscanH(), 0, 0);

        if (menuDialog != null) {
            menuDialog.dismiss();
        }
        menuDialog = new android.app.Dialog(this, android.R.style.Theme_Translucent_NoTitleBar);
        menuDialog.setContentView(scrollView);
        menuDialog.setCanceledOnTouchOutside(true);
        final Window window = menuDialog.getWindow();
        if (window != null) {
            // Deliberately NOT fullscreen/edge-to-edge: a fullscreen dialog window makes OxygenOS treat the
            // panel as immersive and apply its two-swipe back-gesture guard. A plain window closes on one back.
            window.setLayout(ui.pickerWidthPx(getResources().getConfiguration()), ViewGroup.LayoutParams.MATCH_PARENT);
            window.setGravity(Gravity.END);
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0xF0141414));
        }
        showPickerDialog(menuDialog);
        if (currentRow[0] != null) {
            currentRow[0].post(() -> currentRow[0].requestFocus());
        }
    }

    private static class AudioChoice {
        final String label;
        final TrackGroup group;
        final int trackIndex;
        final boolean selected;
        final String language; // ISO-639-2/T, null when the track declares none

        AudioChoice(String label, TrackGroup group, int trackIndex, boolean selected, String language) {
            this.label = label;
            this.group = group;
            this.trackIndex = trackIndex;
            this.selected = selected;
            this.language = language;
        }
    }

    private ArrayList<AudioChoice> buildAudioChoices() {
        final ArrayList<AudioChoice> choices = new ArrayList<>();
        if (player == null) {
            return choices;
        }
        int number = 0;
        for (Tracks.Group group : player.getCurrentTracks().getGroups()) {
            if (group.getType() != C.TRACK_TYPE_AUDIO) {
                continue;
            }
            final TrackGroup trackGroup = group.getMediaTrackGroup();
            for (int i = 0; i < group.length; i++) {
                if (!group.isTrackSupported(i)) {
                    continue;
                }
                final Format format = trackGroup.getFormat(i);
                number++;
                // Same descriptor as the header meta line, so the picker matches what is shown up top.
                String label = buildAudioInfo(format);
                if (label == null || label.isEmpty()) {
                    label = getString(R.string.audio_track_number, number);
                }
                choices.add(new AudioChoice(label, trackGroup, i, group.isTrackSelected(i),
                        Utils.toIso3Language(format.language)));
            }
        }
        return choices;
    }

    // Shows the audio button only when there is more than one audio track to pick from.
    private void updateAudioButton() {
        if (buttonAudio == null) {
            return;
        }
        final boolean show = player != null && buildAudioChoices().size() >= 2;
        buttonAudio.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    // Media3 keeps the subtitle button visible-but-disabled while loading; we instead hide it entirely
    // until the media actually exposes subtitle tracks, matching the audio/quality buttons.
    /**
     * Shows an external subtitle on the item playing now, without interrupting it. The file is parsed
     * into a {@link SubtitleTimeline} and painted by {@link SubtitleOffset} straight to the player's text
     * output — which is where a selected sideloaded subtitle already ends up for the offset to work — so
     * the player never re-prepares: no re-open, no re-buffer, no gap in the sound, no reset progress bar.
     * The track itself turns up on the next player rebuild, from {@code mPrefs.subtitleUri} (see
     * {@link #rememberedSubtitle()}), so this state heals by itself.
     *
     * <p>It also breaks a loop at its root: painting raises no track change, so the finder is not sent
     * looking again by the very subtitle it just found.
     *
     * @return whether this was a new subtitle, i.e. whether it is worth telling the viewer about
     */
    boolean addSubtitleTrack(Uri subtitleUri) {
        if (player == null || subtitleUri == null) {
            return false;
        }
        final int index = player.getCurrentMediaItemIndex();
        final int count = player.getMediaItemCount();
        if (index < 0 || index >= count) {
            return false;
        }
        // Already on screen: no second read of the file, and no second toast for it — a re-search that
        // hits the cache asks for the same one again.
        if (subtitleUri.equals(paintedSubtitleUri)) {
            return false;
        }
        final MediaItem current = player.getMediaItemAt(index);
        if (current.localConfiguration != null) {
            for (MediaItem.SubtitleConfiguration existing : current.localConfiguration.subtitleConfigurations) {
                if (existing.uri.equals(subtitleUri)) {
                    return false; // a real track carries it: the renderer and the picker have it already
                }
            }
        }
        paintSubtitle(subtitleUri);
        return true;
    }

    /** Reads the file on a worker and hands it to {@link SubtitleOffset}; re-prepares only if it cannot. */
    private void paintSubtitle(Uri uri) {
        paintedSubtitleUri = uri;
        subtitleTimelineUri = uri;
        subtitleTimeline = null;
        if (subtitleOffset != null) {
            subtitleOffset.setTimeline(null);
        }
        final String mimeType = SubtitleUtils.getSubtitleMime(uri);
        final Thread worker = new Thread(() -> {
            final SubtitleTimeline loaded = SubtitleTimeline.load(this, uri, mimeType);
            runOnUiThread(() -> {
                if (!uri.equals(paintedSubtitleUri)) {
                    return; // another file, another episode, or a rebuild got there first
                }
                if (loaded == null) {
                    // Nothing to paint: no parser, no cue, or the read failed. Only the renderer can
                    // show it now, and that costs the re-prepare this exists to avoid.
                    paintedSubtitleUri = null;
                    subtitleTimelineUri = null;
                    attachSubtitleTrack(uri);
                    return;
                }
                subtitleTimeline = loaded;
                if (subtitleOffset != null) {
                    subtitleOffset.setTimeline(loaded);
                }
                updateSubtitleButton(); // no track moved, so nothing else would
            });
        }, "SubtitleTimeline");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * Sideloads an external subtitle as a real track, keeping the playlist and the position. The
     * fallback for a file {@link SubtitleTimeline} cannot parse — everything else is painted instead
     * (see {@link #addSubtitleTrack}), because this re-opens the media.
     *
     * <p>The timeline is rebuilt rather than the item replaced. With the media URI unchanged
     * {@code replaceMediaItem} updates the item in place and never re-instantiates the source, so an
     * added subtitle configuration is accepted and then silently ignored — the player reports no new
     * text track at all. It is the same trap {@link #recoverFromContainerError()} documents for the mime
     * type. {@code setMediaItems} with the whole list is what re-creates the merged source, and unlike
     * {@code setMediaItem} it keeps the other episodes, their arrows and their remembered positions.
     */
    private void attachSubtitleTrack(Uri subtitleUri) {
        if (player == null) {
            return;
        }
        // Re-read rather than passed in: this runs a whole file read after the decision to attach.
        final int index = player.getCurrentMediaItemIndex();
        final int count = player.getMediaItemCount();
        if (index < 0 || index >= count) {
            return;
        }
        final MediaItem current = player.getMediaItemAt(index);
        if (current.localConfiguration != null) {
            for (MediaItem.SubtitleConfiguration existing : current.localConfiguration.subtitleConfigurations) {
                if (existing.uri.equals(subtitleUri)) {
                    // Doing nothing when it is already attached is what stops this from looping: the
                    // re-prepare produces another track change, which sends the finder looking again.
                    return;
                }
            }
        }
        final MediaItem updated =
                withSubtitle(current, SubtitleUtils.buildSubtitle(this, subtitleUri, null, true));

        final long position = player.getCurrentPosition();
        final List<MediaItem> items = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            items.add(i == index ? updated : player.getMediaItemAt(i));
        }
        player.setMediaItems(items, index, position);
        player.prepare();
    }

    /** The external subtitle remembered for this media, or null when there is nothing to restore. */
    private MediaItem.SubtitleConfiguration rememberedSubtitle() {
        if (mPrefs.subtitleUri == null || !Utils.fileExists(this, mPrefs.subtitleUri)) {
            return null;
        }
        return SubtitleUtils.buildSubtitle(this, mPrefs.subtitleUri, null, true);
    }

    /** The item plus one more subtitle, keeping every one it already carries. */
    private static MediaItem withSubtitle(MediaItem item, MediaItem.SubtitleConfiguration subtitle) {
        final List<MediaItem.SubtitleConfiguration> subtitles = new ArrayList<>();
        if (item.localConfiguration != null) {
            subtitles.addAll(item.localConfiguration.subtitleConfigurations);
        }
        subtitles.add(subtitle);
        return item.buildUpon().setSubtitleConfigurations(subtitles).build();
    }

    private void updateSubtitleButton() {
        if (exoSubtitle == null) {
            return;
        }
        // Seeded from a subtitle painted without a track of its own; the loop below can only add.
        boolean hasSubtitles = subtitleWithoutTrack() != null;
        boolean textSelected = paintedSubtitleUri != null;
        if (player != null) {
            for (Tracks.Group group : player.getCurrentTracks().getGroups()) {
                if (group.getType() == C.TRACK_TYPE_TEXT
                        && !isPhantomClosedCaption(group.getMediaTrackGroup().getFormat(0))) {
                    hasSubtitles = true;
                    if (group.isSelected()) {
                        textSelected = true;
                        break;
                    }
                }
            }
        }
        exoSubtitle.setVisibility(hasSubtitles ? View.VISIBLE : View.GONE);
        // Media3 owns this button too (it keeps the id it found) and disables it whenever the player
        // reports no text track — which is exactly the case for a subtitle painted from its file. It
        // dims the icon with the same alpha this helper applies, so setEnabled alone would leave a
        // working button that still looks dead. The deferred post() this runs from gives us last word.
        Utils.setButtonEnabled(this, exoSubtitle, hasSubtitles);
        // Light the CC icon coral while a subtitle track is actually showing.
        exoSubtitle.setSelected(textSelected);
    }

    private void applyAudio(AudioChoice choice) {
        if (player == null || choice == null || choice.group == null) {
            return;
        }
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                .setOverrideForType(new TrackSelectionOverride(
                        choice.group, Collections.singletonList(choice.trackIndex)))
                .build());
    }

    private void showAudioDialog() {
        final ArrayList<AudioChoice> choices = buildAudioChoices();
        if (choices.size() < 2) {
            return;
        }
        final List<MenuItem> items = new ArrayList<>();
        String selectedLanguage = null;
        for (final AudioChoice choice : choices) {
            items.add(new MenuItem(choice.label, null, choice.selected,
                    () -> applyAudio(choice)));
            if (choice.selected) {
                selectedLanguage = choice.language;
            }
        }
        // The moment a language is worth preferring is the moment it gets picked by hand — the settings
        // screen is nowhere near it. Offered only after a deliberate pick (an override exists), and
        // only while that language does not already lead the list: every other row of this menu
        // applies to one clip, this one to all of them.
        final List<String> preferred = Utils.splitLanguages(mPrefs.languageAudio);
        if (selectedLanguage != null && hasOverrideType(C.TRACK_TYPE_AUDIO)
                && (preferred.isEmpty() || !preferred.get(0).equals(selectedLanguage))) {
            final String language = selectedLanguage;
            items.add(new MenuItem(R.drawable.ic_star_24dp,
                    getString(R.string.audio_prefer_language, languageDisplayName(language)),
                    null, false, () -> preferAudioLanguage(language)));
        }
        showSideMenu(getString(R.string.audio_title), items);
    }

    /**
     * Moves one language to the head of the audio priority list. The track playing right now is an
     * explicit override and stays put, but the selector is updated too — the next item of a playlist
     * is selected by this same player, which is never rebuilt on this path.
     */
    private void preferAudioLanguage(final String language) {
        final List<String> languages = Utils.splitLanguages(mPrefs.languageAudio);
        languages.remove(language);
        languages.add(0, language);
        mPrefs.setLanguageAudio(TextUtils.join(",", languages));
        applyPreferredAudioLanguages();
        Utils.showText(playerView, getString(R.string.audio_prefer_language_done,
                languageDisplayName(language)));
    }

    /**
     * Ordered fallback chain: the selector walks the list and takes the first language the media
     * actually carries. An empty list leaves the media's own order alone.
     */
    private void applyPreferredAudioLanguages() {
        if (trackSelector == null) {
            return;
        }
        final List<String> languages = Utils.splitLanguages(mPrefs.languageAudio);
        if (languages.isEmpty()) {
            return;
        }
        trackSelector.setParameters(trackSelector.buildUponParameters()
                .setPreferredAudioLanguages(languages.toArray(new String[0]))
        );
    }

    /**
     * Same ordered fallback chain as the audio list, for subtitles. The system captioning language is
     * deliberately not consulted here — it seeds this list once (see Prefs.getLanguageSubtitle) and
     * the list is the only place the language is chosen afterwards. An empty list prefers nothing.
     */
    private void applyPreferredTextLanguages() {
        if (trackSelector == null) {
            return;
        }
        final List<String> languages = Utils.splitLanguages(mPrefs.languageSubtitle);
        if (languages.isEmpty()) {
            return;
        }
        trackSelector.setParameters(trackSelector.buildUponParameters()
                .setPreferredTextLanguages(languages.toArray(new String[0]))
        );
    }

    // ---------------------------------------------------------------------------------------------
    // Online subtitle search: when the media carries nothing in the language the priority list asks
    // for, fetch it rather than leave the viewer with no subtitles and no way to get any.

    /**
     * How long a title that turned up nothing stays written off. Short on purpose: "nothing found" can
     * also mean the sources were having a bad minute, and an evening is far too long to hold that
     * against a title the user is still sitting in front of.
     */
    private static final long SUBTITLE_MISS_TTL_MS = 30 * 60 * 1000L;

    /**
     * Stands where the plain dot would in a cached subtitle's name, marking the file as translated by a
     * machine — either by this app or by whoever uploaded it. The name is the only place such a mark
     * survives a restart, and it sits before the language rather than after it so the language is still
     * read back from between the last two dots.
     */
    private static final String MACHINE_TRANSLATED = ".auto.";

    /** Both spellings of a cached subtitle's name, human first. */
    private static final String[] SUBTITLE_CACHE_PREFIXES = { ".", MACHINE_TRANSLATED };

    /**
     * Titles already searched without a hit, so restarting an episode or rewatching it does not spend
     * the daily download budget re-learning the same nothing. Process-lifetime and keyed per episode:
     * the next episode of the same series is a different key and is looked up on its own.
     */
    private static final Map<String, Long> subtitleSearchMisses = new ConcurrentHashMap<>();

    /**
     * Looks online for a preferred subtitle language the media does not carry, and sideloads it. Runs
     * off the playback thread and never blocks playback: the video starts on time and the track turns
     * up a second or two in.
     *
     * <p>Does nothing at all when the priority list is empty, which is what a fresh install has — with
     * no preferred language there is nothing to go looking for, and the player stays off the network.
     */
    private void cancelSubtitleSearch() {
        subtitleSearchGeneration++;
        subtitleSearchStarted = null;
        if (subtitleSearchThread != null) {
            subtitleSearchThread.interrupt();
            subtitleSearchThread = null;
        }
    }

    private void maybeSearchSubtitlesOnline(Tracks tracks) {
        maybeSearchSubtitlesOnline(tracks, false, null);
    }

    /**
     * @param manual    the viewer asked for this by name. That changes what the guards are for: the
     *                  feature switch, a language the media already carries, and an earlier "nothing
     *                  found" are all good reasons not to go looking unprompted, and none of them is a
     *                  reason to refuse.
     * @param languages for this search only, in place of the stored priority list; null to use it.
     */
    private void maybeSearchSubtitlesOnline(Tracks tracks, boolean manual, List<String> languages) {
        // An empty track list is not "this media has no subtitles" — it is Media3 reporting that it
        // does not know yet, which it does on every prepare and before an HLS or DASH manifest has been
        // read. Searching then asks the internet for subtitles the file is about to expose by itself.
        if (player == null || (!mPrefs.subtitleSearch && !manual) || tracks.getGroups().isEmpty()
                || player.getPlaybackState() == Player.STATE_IDLE) {
            return;
        }
        List<String> preferred = languages != null && !languages.isEmpty()
                ? languages : Utils.splitLanguages(mPrefs.languageSubtitle);
        if (preferred.isEmpty()) {
            if (!manual) {
                return;
            }
            // A fresh install has no priority list, and somebody who has just typed in a title should
            // not be sent to the settings screen to discover that. The device's language is the guess.
            final String device = Utils.toIso3Language(Locale.getDefault().getLanguage());
            if (device == null) {
                return;
            }
            preferred = Collections.singletonList(device);
        }
        List<String> missing = subtitleLanguagesToSearch(tracks, preferred);
        if (missing.isEmpty()) {
            if (!manual) {
                return;
            }
            // Asked for regardless: whatever track outranked the list is evidently not doing the job.
            missing = preferred;
        }
        final List<String> targets = missing;
        final String target = targets.get(0);
        // Which languages those are follows from the target rather than from a setting — see
        // SubtitleTranslate.sourcesFor. Note that a language further down the viewer's own list is
        // included: ranking a language second says it is readable, not that it is what was wanted. The
        // top of the list is what was wanted, and a machine rendering of it is nearer to that than a
        // human file in the next language down. Without this the feature would never fire for a list
        // like "ukr, rus, eng", where Russian is always found and always ends the search.
        final List<String> translateFrom = mPrefs.subtitleTranslate
                ? SubtitleTranslate.sourcesFor(target)
                : Collections.<String>emptyList();
        final String translateTo = translateFrom.isEmpty() ? null : target;
        // Searched for as well, so a language only worth translating is still found — after everything
        // actually wanted, so it can never be preferred over a real hit.
        final List<String> wanted = new ArrayList<>(targets);
        for (String source : translateFrom) {
            if (!wanted.contains(source)) {
                wanted.add(source);
            }
        }
        final MediaId id = mediaIdAt(player.getCurrentMediaItemIndex());
        if (id.isEmpty()) {
            return;
        }
        // Keyed by the question, not just the media: which sources are on and which languages are
        // wanted are half of it. Turning a source on asks something that has not been asked before, so
        // an earlier "nothing found" is no longer an answer to it — without this, changing a switch
        // appears to do nothing at all.
        final String key = id.key() + "|" + wanted + "|" + enabledSubtitleSources();
        if (manual) {
            // Both of these answer "that has been asked already" — true, and beside the point. Somebody
            // pressing the row is asking for the question to be put again, so it is put again.
            subtitleSearchMisses.remove(key);
        } else {
            if (key.equals(subtitleSearchStarted)) {
                return;
            }
            final Long missedAt = subtitleSearchMisses.get(key);
            if (missedAt != null && System.currentTimeMillis() - missedAt < SUBTITLE_MISS_TTL_MS) {
                return;
            }
        }
        final int index = player.getCurrentMediaItemIndex();
        final String cacheName = "subs." + id.key().replaceAll("[^A-Za-z0-9]", "-");

        // A copy kept from an earlier watch answers the same question for nothing — no request, no
        // quota, no waiting. This is why the file is named after the title and language rather than
        // after whichever release the source happened to hand over.
        // Never a language being translated from: a cached Russian copy is the raw material for this
        // search, not its answer, and probing for it would hand back the very file the translation was
        // meant to replace — on every replay, for good. What the translation produced is cached in its
        // own right, under the marked name, and that is what answers here from the second watch on.
        for (String language : targets) {
            if (translateFrom.contains(language)) {
                continue;
            }
            for (String prefix : SUBTITLE_CACHE_PREFIXES) {
                // Any extension: the copy was named after what it turned out to be, so looking only for
                // .srt would miss an ASS one and pay for it again on every replay.
                for (String extension : SubtitleUtils.EXTENSIONS) {
                    final java.io.File cached = new java.io.File(getCacheDir(),
                            cacheName + prefix + language + extension);
                    if (cached.isFile() && cached.length() > 0) {
                        // Touched so the twenty-file trim treats "watched again" as recently used.
                        cached.setLastModified(System.currentTimeMillis());
                        cancelSubtitleSearch();
                        subtitleSearchStarted = key;
                        attachSearchedSubtitle(subtitleSearchGeneration, index,
                                Uri.fromFile(cached), language);
                        return;
                    }
                }
            }
        }

        cancelSubtitleSearch();
        subtitleSearchStarted = key;
        final int generation = subtitleSearchGeneration;

        final Thread worker = new Thread(() -> {
            final AtomicBoolean answered = new AtomicBoolean();
            SubtitleSearch.Result found = null;
            try {
                found = SubtitleSearch.find(id, wanted, mPrefs, result -> {
                    if (generation != subtitleSearchGeneration) {
                        return false;
                    }
                    final List<Uri> urls = new ArrayList<>(result.urls.size());
                    for (String url : result.urls) {
                        urls.add(Uri.parse(url));
                    }
                    // The language goes in the file name because that is where it is read back from: a
                    // track whose language is unknown is not the one setPreferredTextLanguages picks,
                    // so an aggregator's opaque URL arrives as an unnamed track nobody switched on.
                    // MACHINE_TRANSLATED goes in the same place for the same reason — it is the only
                    // marker that survives a restart, and it is what keeps the label honest.
                    final Uri file = new SubtitleFetcher(this, urls, cacheName
                            + (result.machine ? MACHINE_TRANSLATED : ".")
                            + result.language + ".srt").fetchNow();
                    if (file == null) {
                        return false;
                    }
                    Uri show = file;
                    String shown = result.language;
                    if (translateTo != null && translateFrom.contains(result.language)) {
                        final Uri translated = SubtitleTranslate.translate(this, file, result.language,
                                translateTo, new java.io.File(getCacheDir(),
                                        cacheName + MACHINE_TRANSLATED + translateTo + ".srt"),
                                mPrefs.subtitleTranslateOriginal, mPrefs.subtitleTranslateBackends);
                        if (translated != null) {
                            show = translated;
                            shown = translateTo;
                        }
                        // Not translated: show it as it came. It is still a subtitle in a language the
                        // viewer ranked, and it is what the player would have shown before it could
                        // translate at all — failing the search instead would turn an outage at the
                        // translation endpoint into no subtitles.
                    }
                    final Uri attach = show;
                    final String language = shown;
                    runOnUiThread(() -> attachSearchedSubtitle(generation, index, attach, language));
                    return true;
                }, answered);
            } catch (Throwable t) {
                // Playback is not part of this. Whatever went wrong looking for a subtitle, it must not
                // be what takes the player down.
                Utils.log("subtitles: search failed " + t);
            }
            // Only a search that actually reached the sources proves anything about this title. A
            // cancelled one proves nothing, and neither does one that ran with no way out to the
            // network — writing either off would keep the title unsearchable long after the cause.
            if (found == null && answered.get() && !Thread.currentThread().isInterrupted()) {
                subtitleSearchMisses.put(key, System.currentTimeMillis());
            }
        }, "SubtitleSearch");
        worker.setDaemon(true);
        subtitleSearchThread = worker;
        worker.start();
    }

    /** Attaches a downloaded subtitle, but only to the item it was actually searched for. */
    private void attachSearchedSubtitle(int generation, int index, Uri file, String language) {
        if (generation != subtitleSearchGeneration || player == null
                || player.getCurrentMediaItemIndex() != index) {
            return;
        }
        mPrefs.updateSubtitle(file);
        if (addSubtitleTrack(file)) {
            String message = getString(R.string.subtitle_search_found, displayLanguage(language));
            if (isMachineTranslated(file)) {
                message = getString(R.string.subtitle_machine_translated, message);
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        }
    }

    /** Translated by a machine — by this app, or by whoever uploaded it. See {@link #MACHINE_TRANSLATED}. */
    private static boolean isMachineTranslated(Uri uri) {
        final String path = uri.getPath();
        return path != null && path.contains(MACHINE_TRANSLATED);
    }

    /** The enabled sources, in order, as part of what a search result is an answer to. */
    private String enabledSubtitleSources() {
        return (mPrefs.subtitleSourceRest ? "1" : "")
                + (mPrefs.subtitleSourceStremio ? "2" : "")
                + (mPrefs.subtitleSourceShegu ? "3" : "")
                + (mPrefs.subtitleSourceOpenSubtitles ? "4" : "");
    }

    /** An ISO 639-2/T code as something to show a person: "ukr" becomes "українська". */
    private static String displayLanguage(String language) {
        final List<String> two = OpenSubtitles.toIso639_1(Collections.singletonList(language));
        return two.isEmpty() ? language : new Locale(two.get(0)).getDisplayLanguage();
    }

    /**
     * The languages worth searching for: everything the priority list ranks above the best one the media
     * already carries. A file with English on a {@code uk, ru, en} list is searched for Ukrainian and
     * Russian and no further — the English already there is as good as anything downloaded.
     *
     * <p>In strict mode it is all-or-nothing instead: any preferred language present at all means no
     * search. An empty result either way means leave the media alone.
     */
    private List<String> subtitleLanguagesToSearch(Tracks tracks, List<String> preferred) {
        final Set<String> present = new HashSet<>();
        for (Tracks.Group group : tracks.getGroups()) {
            if (group.getType() != C.TRACK_TYPE_TEXT) {
                continue;
            }
            for (int i = 0; i < group.length; i++) {
                final Format format = group.getTrackFormat(i);
                if (isPhantomClosedCaption(format)) {
                    continue;
                }
                // An untagged track ("und", or nothing at all) is not evidence of any language, so it
                // counts as absence — the alternative is a file full of Polish passing for Ukrainian.
                final String language = Utils.toIso3Language(format.language);
                if (language != null) {
                    present.add(language);
                }
            }
        }
        int best = preferred.size();
        for (int i = 0; i < preferred.size(); i++) {
            if (present.contains(preferred.get(i))) {
                best = i;
                break;
            }
        }
        if (best == 0 || (mPrefs.subtitleSearchStrict && best < preferred.size())) {
            return Collections.emptyList();
        }
        return preferred.subList(0, best);
    }

    // ExoPlayer invents an empty CEA-608 track for every HLS stream whose playlist declares no closed
    // captions at all (DefaultHlsExtractorFactory.exposeCea608WhenMissingDeclarations, on by default
    // and not reachable through the final DefaultMediaSourceFactory). Almost no such stream actually
    // carries captions, so the picker offered a subtitle that showed nothing once selected, on streams
    // where every other player correctly reports no subtitles. A CC track a playlist really declares
    // always carries INSTREAM-ID (CC1..CC4) — an accessibility channel — which the invented one lacks.
    // ponytail: this also hides an undeclared but real CEA-608 track, the same trade-off ExoPlayer's
    // own flag makes. Flipping the flag instead needs a custom HlsMediaSource.Factory, which means
    // re-doing the sideloaded-subtitle merging DefaultMediaSourceFactory does for us.
    private static boolean isPhantomClosedCaption(Format format) {
        return MimeTypes.APPLICATION_CEA608.equals(format.sampleMimeType)
                && format.accessibilityChannel == Format.NO_VALUE;
    }

    private String buildSubtitleInfo(Format text) {
        final String language = languageDisplayName(text.language);
        String name = text.label;
        if ((name == null || name.isEmpty()) && text.id != null) {
            name = resolvedTrackNames.get(text.id);
        }
        final StringBuilder b = new StringBuilder();
        final String title = (name != null && !name.isEmpty()) ? name : language;
        if (title != null && !title.isEmpty()) {
            b.append(title);
        }
        // If we led with a rich name, still surface the language after it.
        if (name != null && !name.isEmpty() && language != null) {
            b.append(' ').append('(').append(language).append(')');
        }
        return b.toString();
    }

    // Subtitle picker in the same native side panel as audio/quality (replaces the Media3 built-in popup).
    private void showSubtitleDialog() {
        if (player == null) {
            return;
        }
        final boolean textEnabled = player.getCurrentTracks().isTypeSelected(C.TRACK_TYPE_TEXT);
        // A subtitle painted from its file has no track here, so it needs a row of its own — otherwise
        // it could never be switched off, and "off" would read as the choice while it is on screen.
        final Uri fileOnly = subtitleWithoutTrack();
        final boolean painting = paintedSubtitleUri != null;
        final List<MenuItem> items = new ArrayList<>();
        items.add(new MenuItem(getString(R.string.subtitle_off), null, !textEnabled && !painting,
                this::disableSubtitles));
        if (fileOnly != null) {
            // addSubtitleTrack rather than paintSubtitle: it carries the already-on-screen guard, so
            // tapping the ticked row costs nothing and tapping it after "off" puts the subtitle back.
            items.add(new MenuItem(subtitleFileLabel(fileOnly), null, painting,
                    () -> addSubtitleTrack(fileOnly)));
        }
        int number = 0;
        for (Tracks.Group group : player.getCurrentTracks().getGroups()) {
            if (group.getType() != C.TRACK_TYPE_TEXT) {
                continue;
            }
            final TrackGroup trackGroup = group.getMediaTrackGroup();
            for (int i = 0; i < group.length; i++) {
                if (!group.isTrackSupported(i)) {
                    continue;
                }
                final Format format = trackGroup.getFormat(i);
                if (isPhantomClosedCaption(format)) {
                    continue;
                }
                number++;
                String label = buildSubtitleInfo(format);
                if (label == null || label.isEmpty()) {
                    label = getString(R.string.audio_track_number, number);
                }
                final int index = i;
                // !painting: an embedded track can stay selected while the file is painted over it
                // (SubtitleOffset drops the renderer's cues), and two ticked rows is a lie.
                items.add(new MenuItem(label, null, textEnabled && !painting && group.isTrackSelected(i),
                        () -> applySubtitle(trackGroup, index)));
            }
        }
        // Last, and with no tick: it is an action rather than a track. Offered whatever the automatic
        // search's switch says — pressing it is the consent that switch stands in for.
        items.add(new MenuItem(getString(R.string.subtitle_search_manual), null, false,
                this::showSubtitleSearchDialog));
        showSideMenu(getString(R.string.subtitle_title), items);
    }

    /**
     * Looks for subtitles by a name rather than by whatever the launcher said this is. The way in when
     * the automatic search has nothing to go on — a stream whose URL is a hash, or a launcher that sent
     * the wrong id — since every source is keyed by the title and none of them can be told a filename.
     *
     * <p>Results arrive while the name is still being typed, from the third character on: nobody
     * recalls a title exactly, and the point of the posters is to be recognised rather than remembered.
     */
    private void showSubtitleSearchDialog() {
        final EditText query = new EditText(this);
        query.setInputType(InputType.TYPE_CLASS_TEXT);
        query.setSingleLine(true);
        query.setHint(R.string.subtitle_search_hint);
        // Deliberately not prefilled from the file name: reaching this dialog means the name is already
        // what failed, and clearing a line of release noise with a remote costs more than typing.

        final LinearLayout results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        final android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.addView(results);

        final LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        final int pad = Utils.dpToPx(16);
        fields.setPadding(pad, 0, pad, 0);
        fields.addView(query);
        // Capped rather than free: the list has to leave the field and the keyboard on screen, because
        // the next thing typed is what narrows it down. Half the window is the ceiling because the
        // player is landscape — a fixed height that fits a phone upright pushes the field off it.
        final int listHeight = Math.min(ui.dpS(260),
                getResources().getDisplayMetrics().heightPixels / 2);
        fields.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, listHeight));

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.subtitle_search_manual)
                .setView(fields)
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        final Handler handler = new Handler(Looper.getMainLooper());
        final Runnable[] pending = new Runnable[1];
        query.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (pending[0] != null) {
                    handler.removeCallbacks(pending[0]);
                }
                final String text = s.toString().trim();
                // Every keystroke invalidates the answer to the last one, whether or not a new request
                // goes out — otherwise a slow reply for "Sil" lands on top of the results for "Silo".
                titleSearchGeneration++;
                if (text.length() < TITLE_QUERY_MIN) {
                    results.removeAllViews();
                    return;
                }
                final int generation = titleSearchGeneration;
                pending[0] = () -> searchTitles(text, generation, results, dialog);
                handler.postDelayed(pending[0], TITLE_QUERY_DEBOUNCE_MS);
            }
        });
        dialog.show();
    }

    /** Asks TMDB what the typed name could be, then fills the list in place. */
    private void searchTitles(String query, int generation, LinearLayout results, AlertDialog dialog) {
        final Thread worker = new Thread(() -> {
            final List<TitleSearch.Title> titles = TitleSearch.search(query);
            runOnUiThread(() -> {
                if (isFinishing() || generation != titleSearchGeneration) {
                    return;
                }
                results.removeAllViews();
                if (titles.isEmpty()) {
                    results.addView(searchNote(getString(R.string.subtitle_search_none)));
                    return;
                }
                for (final TitleSearch.Title title : titles) {
                    // Movie or series is not decoration: the sources are asked a different question for
                    // each, and an adaptation sharing its name and year with the film is told apart by
                    // nothing else.
                    final String kind = getString(title.movie
                            ? R.string.subtitle_search_movie : R.string.subtitle_search_series);
                    results.addView(searchRow(title.posterUrl, ui.dpS(40), ui.dpS(60), title.name,
                            title.year == null ? kind : title.year + " · " + kind, () -> {
                        dialog.dismiss();
                        if (title.movie) {
                            applyManualTitle(title, -1, -1, null);
                        } else {
                            chooseSeason(title);
                        }
                    }));
                }
            });
        }, "TitleSearch");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * Which season, then which episode. Asked rather than guessed: the episode number is the half of
     * the question the sources answer most literally, and a file whose name never carried one is
     * exactly the case this dialog gets reached from.
     *
     * <p>The whole series arrives in one request, so picking a season costs nothing after this and the
     * numbering cannot drift between the two steps.
     */
    private void chooseSeason(TitleSearch.Title title) {
        final Thread worker = new Thread(() -> {
            // Cinemeta is keyed by imdb and the search gave a tmdb id, so one hop first. The resolver
            // is the one the automatic search already uses for the same reason.
            String imdb = null;
            try {
                imdb = SegmentFinder.tmdbExternalImdb(Long.parseLong(title.tmdb), false);
            } catch (Exception e) {
                Utils.log("titles: imdb lookup " + e);
            }
            final List<TitleSearch.Episode> episodes = TitleSearch.episodes(imdb, title.tmdb);
            runOnUiThread(() -> {
                if (isFinishing()) {
                    return;
                }
                // Nothing came back. Rather than quietly guess the first season, hand over the numbers:
                // a title no catalogue lists is exactly when somebody knows them and we do not.
                if (episodes.isEmpty()) {
                    askSeasonEpisode(title, episodes);
                    return;
                }
                final List<Integer> seasons = new ArrayList<>();
                for (TitleSearch.Episode episode : episodes) {
                    if (!seasons.contains(episode.season)) {
                        seasons.add(episode.season);
                    }
                }
                if (seasons.size() == 1) {
                    chooseEpisode(title, episodes, seasons.get(0));
                    return;
                }
                final List<MenuItem> items = new ArrayList<>(seasons.size() + 1);
                items.add(typeNumbersRow(title, episodes));
                for (final int season : seasons) {
                    items.add(new MenuItem(getString(season == 0
                                    ? R.string.subtitle_search_specials
                                    : R.string.subtitle_search_season, season),
                            null, false, () -> chooseEpisode(title, episodes, season)));
                }
                showSideMenu(title.name, items);
            });
        }, "TitleEpisodes");
        worker.setDaemon(true);
        worker.start();
    }

    /** No request of its own — the season's episodes are already in hand. */
    private void chooseEpisode(TitleSearch.Title title, List<TitleSearch.Episode> episodes, int season) {
        final List<MenuItem> items = new ArrayList<>();
        for (final TitleSearch.Episode episode : episodes) {
            if (episode.season != season) {
                continue;
            }
            final String number = getString(R.string.subtitle_search_episode, episode.number);
            items.add(new MenuItem(0, episode.stillUrl,
                    episode.name != null ? episode.name : number,
                    episode.name != null ? number : null, false,
                    () -> applyManualTitle(title, season, episode.number, episodes)));
        }
        if (items.isEmpty()) {
            applyManualTitle(title, season, -1, episodes);
            return;
        }
        // Here as well as on the season list: a series with one season skips that step entirely, and
        // typing the numbers must not end up being something only multi-season shows offer.
        items.add(0, typeNumbersRow(title, episodes));
        // Stills are 16:9, so the row leads with a wide frame rather than a tall poster.
        showSideMenu(title.name, items, 72, 41);
    }

    /** The way past the catalogues, first in the list because it is what somebody who knows reaches for. */
    private MenuItem typeNumbersRow(TitleSearch.Title title, List<TitleSearch.Episode> episodes) {
        return new MenuItem(getString(R.string.subtitle_search_type), null, false,
                () -> askSeasonEpisode(title, episodes));
    }

    /**
     * Season and episode by hand. The catalogues disagree often enough that a list can be wrong or
     * simply not have the thing playing, and then the numbers somebody read off the file are better
     * than anything on offer here.
     *
     * <p>Prefilled with what is currently believed, so correcting one digit is one digit of typing.
     * A blank season means the first, and a blank episode means the whole season — which every source
     * reads as an answerable question.
     */
    private void askSeasonEpisode(TitleSearch.Title title, List<TitleSearch.Episode> episodes) {
        final MediaId current = player != null ? mediaIdAt(player.getCurrentMediaItemIndex()) : null;

        final EditText season = new EditText(this);
        season.setInputType(InputType.TYPE_CLASS_NUMBER);
        season.setSingleLine(true);
        season.setHint(R.string.subtitle_search_season_label);
        if (current != null && current.season >= 0) {
            season.setText(String.valueOf(current.season));
        }

        final EditText episode = new EditText(this);
        episode.setInputType(InputType.TYPE_CLASS_NUMBER);
        episode.setSingleLine(true);
        episode.setHint(R.string.subtitle_search_episode_label);
        if (current != null && current.episode >= 1) {
            episode.setText(String.valueOf(current.episode));
        }

        final LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        final int pad = Utils.dpToPx(16);
        fields.setPadding(pad, 0, pad, 0);
        fields.addView(season);
        fields.addView(episode);

        new AlertDialog.Builder(this)
                .setTitle(R.string.subtitle_search_type)
                .setView(fields)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> applyManualTitle(title,
                        number(season.getText().toString(), 1),
                        number(episode.getText().toString(), -1), episodes))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** A typed number, or {@code fallback} for anything that is not one. */
    private static int number(String text, int fallback) {
        try {
            return Integer.parseInt(text.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    /**
     * Adopts the hand-picked title and puts the search question again with it. The episode list is
     * kept so the rest of the playlist can be lined up against it — see {@link #mediaIdAt(int)}.
     */
    private void applyManualTitle(TitleSearch.Title title, int season, int episode,
                                  List<TitleSearch.Episode> episodes) {
        manualTmdbId = title.tmdb;
        manualMovie = title.movie;
        manualSeason = season;
        manualEpisode = episode;
        // Pinned to the item it was chosen for: the title carries over to the rest of the playlist —
        // the next file is the same series — but the episode number does not.
        manualIndex = player != null ? player.getCurrentMediaItemIndex() : -1;
        manualEpisodes = null;
        manualAbsolute = -1;
        if (episodes != null) {
            // Specials are left out: they are not part of the aired run a playlist walks through, so
            // counting them would shift every position after the first one.
            final List<TitleSearch.Episode> run = new ArrayList<>(episodes.size());
            for (TitleSearch.Episode item : episodes) {
                if (item.season > 0) {
                    run.add(item);
                }
            }
            manualEpisodes = run;
            for (int i = 0; i < run.size(); i++) {
                if (run.get(i).season == season && run.get(i).number == episode) {
                    manualAbsolute = i;
                    break;
                }
            }
        }
        if (player == null) {
            return;
        }
        if (!mPrefs.subtitleSearchLanguage) {
            maybeSearchSubtitlesOnline(player.getCurrentTracks(), true, null);
            return;
        }
        // Seeded from the priority list every time and never written back: the list is the standing
        // answer, and this is one search that wants a different one. Confirming it costs a press.
        LanguagePriorityDialog.show(this, getString(R.string.subtitle_search_language_title),
                R.string.pref_language_subtitle_none, R.string.pref_language_audio_add,
                Utils.splitLanguages(mPrefs.languageSubtitle),
                Utils.allLanguages(), pinnedLanguages(), picked -> {
                    if (player != null) {
                        maybeSearchSubtitlesOnline(player.getCurrentTracks(), true, picked);
                    }
                });
    }

    /**
     * Languages worth offering first: the device's own, then those the clip carries. Without them the
     * one being looked for is buried somewhere in several hundred locales.
     */
    private List<String> pinnedLanguages() {
        final List<String> pinned = new ArrayList<>(Arrays.asList(Utils.getDeviceLanguages()));
        for (final AudioChoice choice : buildAudioChoices()) {
            if (choice.language != null && !pinned.contains(choice.language)) {
                pinned.add(choice.language);
            }
        }
        return pinned;
    }

    /** A line of plain text where a row would go, for "nothing found". */
    private TextView searchNote(CharSequence text) {
        final TextView note = new TextView(this);
        note.setText(text);
        note.setTextColor(0x99FFFFFF);
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, ui.textBody());
        note.setPadding(Utils.dpToPx(12), Utils.dpToPx(10), Utils.dpToPx(12), Utils.dpToPx(10));
        return note;
    }

    /**
     * A result row: artwork, name, and a line of detail under it. The side panels get theirs from
     * {@link #showSideMenu}, but this list is rebuilt on every keystroke inside a dialog that has to
     * keep both the keyboard and the field it belongs to, so it builds its own.
     */
    private View searchRow(String imageUrl, int imageW, int imageH, CharSequence name,
                           CharSequence detail, Runnable action) {
        final LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Utils.dpToPx(6), 0, Utils.dpToPx(6));
        row.setClickable(true);
        row.setFocusable(true);
        row.setMinimumHeight(ui.rowMinHeight());
        final GradientDrawable content = new GradientDrawable();
        content.setCornerRadius(Utils.dpToPx(8));
        content.setColor(Color.TRANSPARENT);
        final GradientDrawable mask = new GradientDrawable();
        mask.setCornerRadius(Utils.dpToPx(8));
        mask.setColor(Color.WHITE);
        row.setBackground(new RippleDrawable(ColorStateList.valueOf(0x40FFFFFF), content, mask));

        final ImageView art = new ImageView(this);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.setBackgroundColor(0xFF333333);
        final int corner = Utils.dpToPx(4);
        art.setClipToOutline(true);
        art.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), corner);
            }
        });
        final LinearLayout.LayoutParams artLp = new LinearLayout.LayoutParams(imageW, imageH);
        artLp.setMarginEnd(Utils.dpToPx(12));
        art.setLayoutParams(artLp);
        row.addView(art);
        if (imageUrl != null) {
            Glide.with(this).load(imageUrl).into(art);
        }

        final LinearLayout textBlock = new LinearLayout(this);
        textBlock.setOrientation(LinearLayout.VERTICAL);
        textBlock.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final TextView titleView = new TextView(this);
        titleView.setText(name);
        titleView.setTextColor(0xFFDDDDDD);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, ui.textBody());
        textBlock.addView(titleView);
        TextView detailView = null;
        if (detail != null && detail.length() > 0) {
            detailView = new TextView(this);
            detailView.setText(detail);
            detailView.setTextColor(0x99FFFFFF);
            detailView.setTextSize(TypedValue.COMPLEX_UNIT_SP, ui.textCaption());
            textBlock.addView(detailView);
        }
        row.addView(textBlock);
        fitLongText(row, titleView, detailView);
        row.setOnClickListener(v -> action.run());
        return row;
    }

    /** Nothing is painted from a file any more: the renderer's own cues get through again. */
    private void clearSubtitleTimeline() {
        paintedSubtitleUri = null;
        subtitleTimeline = null;
        subtitleTimelineUri = null;
        if (subtitleOffset != null) {
            subtitleOffset.setTimeline(null);
        }
    }

    /** Drops a subtitle that had no track of its own — the viewer just picked something else, or off. */
    private void clearPaintedSubtitle() {
        if (paintedSubtitleUri == null) {
            return;
        }
        clearSubtitleTimeline();
        updateSubtitleButton(); // no track moved, so nothing else would
    }

    /**
     * Hands the selected subtitle over to {@link SubtitleTimeline} when it is a file the app sideloaded,
     * and back to the renderer when it is not (an embedded track, or subtitles off). Runs on every track
     * change, so it has to be cheap when nothing moved — the file is read once per choice, on a worker.
     */
    private void updateSubtitleTimeline(Tracks tracks) {
        final MediaItem.SubtitleConfiguration selected = selectedSideloadedSubtitle(tracks);
        if (selected == null) {
            // A painted subtitle has no track to be "selected", and this runs on every track change —
            // an audio pick, an HLS variant switch. Reading that absence as "nothing to show" is what
            // would wipe it off the screen.
            if (paintedSubtitleUri == null) {
                clearSubtitleTimeline();
            }
            return;
        }
        if (selected.uri.equals(subtitleTimelineUri)) {
            // Already read, or being read. A player rebuild brings a new SubtitleOffset, so re-hand it.
            if (subtitleOffset != null && subtitleTimeline != null) {
                subtitleOffset.setTimeline(subtitleTimeline);
            }
            return;
        }
        subtitleTimelineUri = selected.uri;
        subtitleTimeline = null;
        if (subtitleOffset != null) {
            subtitleOffset.setTimeline(null);
        }
        final Uri uri = selected.uri;
        final String mimeType = selected.mimeType;
        final Thread worker = new Thread(() -> {
            final SubtitleTimeline loaded = SubtitleTimeline.load(this, uri, mimeType);
            runOnUiThread(() -> {
                // Another track (or another media) was chosen while this was being read.
                if (!uri.equals(subtitleTimelineUri)) {
                    return;
                }
                subtitleTimeline = loaded;
                if (subtitleOffset != null) {
                    subtitleOffset.setTimeline(loaded);
                }
            });
        }, "SubtitleTimeline");
        worker.setDaemon(true);
        worker.start();
    }

    /** The sideloaded subtitle currently selected, matched by the URI its track carries as its id. */
    private MediaItem.SubtitleConfiguration selectedSideloadedSubtitle(Tracks tracks) {
        if (player == null) {
            return null;
        }
        final MediaItem item = player.getCurrentMediaItem();
        if (item == null || item.localConfiguration == null
                || item.localConfiguration.subtitleConfigurations.isEmpty()) {
            return null;
        }
        for (final Tracks.Group group : tracks.getGroups()) {
            if (group.getType() != C.TRACK_TYPE_TEXT) {
                continue;
            }
            for (int i = 0; i < group.length; i++) {
                if (!group.isTrackSelected(i)) {
                    continue;
                }
                final String id = group.getTrackFormat(i).id;
                for (final MediaItem.SubtitleConfiguration config : item.localConfiguration.subtitleConfigurations) {
                    if (config.mimeType != null && carriesUri(id, config.uri)) {
                        return config;
                    }
                }
                return null; // selected, but embedded — the renderer keeps it
            }
        }
        return null;
    }

    /**
     * Whether a track id is the one the app gave this sideloaded subtitle. The id it set is the URI
     * (SubtitleUtils.buildSubtitle), but Media3 hands the track out prefixed with the index its source
     * has in the merge — "1:file:///…" — so the URI is the tail of the id, not all of it.
     */
    private static boolean carriesUri(String formatId, Uri uri) {
        if (formatId == null) {
            return false;
        }
        final String text = uri.toString();
        return formatId.equals(text) || formatId.endsWith(":" + text);
    }

    /**
     * The remembered external subtitle when the player carries no track for it — the one that is
     * painted, or can be. This is what keeps it in the picker after the viewer switches it off: without
     * a track of its own it would otherwise take the CC button with it and become unreachable.
     */
    private Uri subtitleWithoutTrack() {
        final Uri uri = mPrefs.subtitleUri;
        if (uri == null || player == null) {
            return null;
        }
        final MediaItem item = player.getCurrentMediaItem();
        if (item != null && item.localConfiguration != null) {
            for (final MediaItem.SubtitleConfiguration config : item.localConfiguration.subtitleConfigurations) {
                if (config.uri.equals(uri)) {
                    return null; // the item carries it, so the renderer and the picker do too
                }
            }
        }
        return uri;
    }

    /** What a file with no track is called in the picker — the name its track would have carried. */
    private String subtitleFileLabel(Uri uri) {
        // Media3 normalises a track language before it reaches Format.language; a file name is raw.
        final String language =
                languageDisplayName(Util.normalizeLanguageCode(SubtitleUtils.getSubtitleLanguage(uri)));
        final String label = language != null ? language : Utils.getFileName(this, uri);
        // A machine translation that reads like a human one in the picker is the feature lying about
        // itself: the first odd line then looks like a broken player rather than what was agreed to.
        return isMachineTranslated(uri) ? getString(R.string.subtitle_machine_translated, label) : label;
    }

    private void disableSubtitles() {
        if (player == null) {
            return;
        }
        clearPaintedSubtitle();
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build());
    }

    private void applySubtitle(TrackGroup group, int index) {
        if (player == null || group == null) {
            return;
        }
        // Without this the picked track would show nothing: SubtitleOffset drops the renderer's cues
        // for as long as a file is painted.
        clearPaintedSubtitle();
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(new TrackSelectionOverride(group, Collections.singletonList(index)))
                .build());
    }

    private static final float[] SPEED_PRESETS =
            {0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f};

    private String formatSpeed(float speed) {
        if (Math.abs(speed - 1f) < 0.001f) {
            return getString(R.string.speed_normal);
        }
        final String number = speed == Math.rint(speed)
                ? String.valueOf((int) speed)
                : String.valueOf(speed);
        return number + "×";
    }

    private void showSpeedDialog() {
        if (player == null) {
            return;
        }
        final float current = userSpeed();
        final List<MenuItem> items = new ArrayList<>();
        for (final float speed : SPEED_PRESETS) {
            items.add(new MenuItem(formatSpeed(speed), null,
                    Math.abs(current - speed) < 0.001f, () -> applySpeed(speed)));
        }
        showSideMenu(getString(R.string.speed_title), items);
    }

    private void applySpeed(float speed) {
        if (player == null) {
            return;
        }
        player.setPlaybackSpeed(speed);
        mPrefs.speed = speed;
        updateEndsAt();
    }

    private void cycleOrientation() {
        mPrefs.orientation = Utils.getNextOrientation(mPrefs.orientation);
        Utils.setOrientation(PlayerActivity.this, mPrefs.orientation);
        updateButtonRotation();
        Utils.showText(playerView, getString(mPrefs.orientation.description), 2500);
        resetHideCallbacks();
    }

    // Uniform box for every button that lives inside a control pill (header display cluster + bottom pickers),
    // so both pills share one height, one button size and one inter-button gap. 40dp box, 8dp padding keeps
    // the glyph at the standard 24dp.
    private void styleClusterButton(final ImageButton button) {
        if (button == null) {
            return;
        }
        final int pad = ui.clusterPad();
        button.setPadding(pad, pad, pad, pad);
        final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ui.clusterBox(), ui.clusterBox());
        params.gravity = Gravity.CENTER_VERTICAL;
        button.setLayoutParams(params);
    }

    // ---- Sleep timer ------------------------------------------------------------------------------------
    // Two shapes of "stop once I am asleep" in one list, because they answer different nights: a duration,
    // for a film playing on into an empty room, and "after this file", for a series whose autoplay would
    // otherwise run until morning. Session-only — nothing is persisted, so a timer can never outlive the
    // sitting that set it.
    //
    // Firing closes the player rather than only pausing it. In an api session (a resolver handing over a
    // temporary link) the position is written nowhere on disk and reaches the launcher solely through the
    // result intent that finish() builds, so pausing and then being killed overnight would lose the very
    // position the timer exists to protect. Closing also frees the decoder and the receiver's audio lock.

    private static final int[] SLEEP_PRESETS_MIN = {15, 30, 45, 60, 90};
    private static final long SLEEP_WARN_MS = 30_000;   // fade + notice window ahead of the close
    private static final long SLEEP_TICK_MS = 1000;
    private static final long SLEEP_FADE_TICK_MS = 250; // fine enough a ramp to read as a fade, not a step

    private long sleepDeadlineMs;      // uptimeMillis deadline; 0 = nothing armed
    private int sleepSetMinutes;       // what was picked, for the ✓ in the list
    private boolean sleepAtEndOfItem;  // stop when the current file ends, instead of on a clock
    private boolean sleepWarned;

    private final Runnable sleepTickRunnable = new Runnable() {
        @Override
        public void run() {
            final long remaining = sleepRemainingMs();
            if (remaining <= 0) {
                fireSleepTimer();
                return;
            }
            if (remaining <= SLEEP_WARN_MS) {
                if (!sleepWarned) {
                    sleepWarned = true;
                    Utils.showText(playerView, getString(R.string.sleep_timer_warning,
                            Math.round(remaining / 1000f)));
                }
                // Ease the sound out rather than cut it — the one warning that still lands with the screen
                // already dark. A no-op on a passthrough output, where the player has no gain of its own to
                // give; the notice above covers that case.
                if (player != null) {
                    player.setVolume(baseVolume() * (remaining / (float) SLEEP_WARN_MS));
                }
                playerView.postDelayed(this, SLEEP_FADE_TICK_MS);
            } else {
                playerView.postDelayed(this, SLEEP_TICK_MS);
            }
        }
    };

    /** Arms a duration; {@code minutes <= 0} turns the timer off. */
    private void armSleepTimer(final int minutes) {
        cancelSleepTimer();
        if (minutes <= 0) {
            Utils.showText(playerView, getString(R.string.sleep_timer_cancelled));
            return;
        }
        sleepSetMinutes = minutes;
        // uptimeMillis, to match the handler's own clock. That it does not advance in deep sleep is no
        // constraint here: a sleep timer runs with the video playing and the screen awake.
        sleepDeadlineMs = SystemClock.uptimeMillis() + minutes * 60_000L;
        playerView.postDelayed(sleepTickRunnable, SLEEP_TICK_MS);
        Utils.showText(playerView, getString(R.string.sleep_timer_set, formatSleepDuration(minutes)));
    }

    private void armSleepAtEndOfItem() {
        cancelSleepTimer();
        sleepAtEndOfItem = true;
        applySleepAtEndOfItem();
        Utils.showText(playerView, getString(R.string.sleep_timer_set,
                getString(R.string.sleep_timer_end_of_item)));
    }

    /** Clears whatever was armed and undoes the fade — the way back from every off/cancel path. */
    private void cancelSleepTimer() {
        playerView.removeCallbacks(sleepTickRunnable);
        sleepDeadlineMs = 0;
        sleepSetMinutes = 0;
        sleepWarned = false;
        if (sleepAtEndOfItem) {
            sleepAtEndOfItem = false;
            applySleepAtEndOfItem();
        }
        applyVolumeMode();
    }

    // Media3's own end-of-item pause is the entire mechanism; the listener below turns that pause into a
    // close. Re-applied after every player rebuild (a quality switch makes a new instance).
    private void applySleepAtEndOfItem() {
        if (player != null) {
            player.setPauseAtEndOfMediaItems(sleepAtEndOfItem);
        }
    }

    private long sleepRemainingMs() {
        return sleepDeadlineMs == 0 ? 0 : Math.max(0, sleepDeadlineMs - SystemClock.uptimeMillis());
    }

    private void fireSleepTimer() {
        playerView.removeCallbacks(sleepTickRunnable);
        sleepDeadlineMs = 0;
        sleepSetMinutes = 0;
        sleepAtEndOfItem = false;
        sleepWarned = false;
        // Write the position before closing: finish() carries it out of an api session in its result intent,
        // and savePlayer covers the ordinary one. Deliberately no applyVolumeMode() — restoring the level for
        // the last instants before the window goes would put the sound back at full.
        savePlayer();
        if (player != null) {
            player.pause();
        }
        finish();
    }

    /** "15 min" / "1 h" / "1 h 30 min" — whole hours lose the minutes. */
    private String formatSleepDuration(final int minutes) {
        if (minutes % 60 == 0) {
            return getString(R.string.sleep_timer_hours, String.valueOf(minutes / 60));
        }
        if (minutes > 60) {
            return getString(R.string.sleep_timer_hours_minutes, minutes / 60, minutes % 60);
        }
        return getString(R.string.sleep_timer_minutes, minutes);
    }

    /** Detail line for the overflow row: what is armed, or null when nothing is. */
    private String sleepTimerSummary() {
        if (sleepAtEndOfItem) {
            return getString(R.string.sleep_timer_end_of_item);
        }
        final long remaining = sleepRemainingMs();
        return remaining > 0 ? Utils.formatMilis(remaining) : null;
    }

    private void showSleepTimerMenu() {
        final List<MenuItem> items = new ArrayList<>();
        items.add(new MenuItem(getString(R.string.sleep_timer_off), null,
                !sleepAtEndOfItem && sleepRemainingMs() == 0, () -> armSleepTimer(0)));
        for (final int minutes : SLEEP_PRESETS_MIN) {
            final boolean current = !sleepAtEndOfItem && sleepSetMinutes == minutes;
            items.add(new MenuItem(formatSleepDuration(minutes),
                    current ? Utils.formatMilis(sleepRemainingMs()) : null,
                    current, () -> armSleepTimer(minutes)));
        }
        items.add(new MenuItem(getString(R.string.sleep_timer_end_of_item), null, sleepAtEndOfItem,
                this::armSleepAtEndOfItem));
        items.add(new MenuItem(getString(R.string.sleep_timer_custom), null, false,
                this::showSleepTimerCustomDialog));
        showSideMenu(getString(R.string.sleep_timer_title), items);
    }

    private void showSleepTimerCustomDialog() {
        if (sleepTimerDialog != null) {
            sleepTimerDialog.dismiss();
        }
        sleepTimerDialog = DurationPanel.create(this, ui, coordinatorLayout, brandColor(),
                getString(R.string.sleep_timer_title), this::armSleepTimer);
        showPickerDialog(sleepTimerDialog);
    }

    // Overflow menu: everything used rarely or once per session lives here so the main row stays calm.
    private void showMoreMenu() {
        final List<MenuItem> items = new ArrayList<>();
        if (player != null) {
            items.add(new MenuItem(R.drawable.ic_speed_24dp, getString(R.string.speed_title),
                    formatSpeed(userSpeed()), false, this::showSpeedDialog));
            items.add(new MenuItem(R.drawable.ic_sleep_24dp, getString(R.string.sleep_timer_title),
                    sleepTimerSummary(), false, this::showSleepTimerMenu));
            // Rides the stats panel: the details it reports are the ones on screen, and the row would be
            // noise for everyone who has not asked for them. From the menu rather than a long-press on the
            // panel, so it is reachable with a D-pad and the panel stays free of touch handling.
            if (mPrefs.showStats) {
                items.add(new MenuItem(R.drawable.ic_content_copy_24dp,
                        getString(R.string.stats_report_title), null, false, this::showPlayerState));
            }
        }
        if (buttonSkipOffset != null && buttonSkipOffset.getVisibility() == View.VISIBLE) {
            items.add(new MenuItem(R.drawable.ic_skip_offset_24dp, getString(R.string.button_skip_offset),
                    skipOffsetSec == 0 ? null : OffsetPanel.format(skipOffsetSec),
                    false, this::showSkipOffsetDialog));
        }
        // Right below its twin. Only with subtitles on: there is nothing to shift otherwise. A painted
        // file counts — it is the case SubtitleTimeline was built for — and selects no track.
        if (player != null && (player.getCurrentTracks().isTypeSelected(C.TRACK_TYPE_TEXT)
                || paintedSubtitleUri != null)) {
            items.add(new MenuItem(R.drawable.ic_subtitle_offset_24dp,
                    getString(R.string.subtitle_offset_title),
                    subtitleOffsetSec == 0 ? null : OffsetPanel.format(subtitleOffsetSec),
                    false, this::showSubtitleOffsetDialog));
        }
        // Here as well as in the subtitle panel: the panel is where somebody who has looked for
        // subtitles ends up, and this menu is where they look when the panel had nothing to offer.
        if (player != null) {
            items.add(new MenuItem(R.drawable.ic_search_24dp,
                    getString(R.string.subtitle_search_manual), null, false,
                    this::showSubtitleSearchDialog));
        }
        // Only for network media: a room syncs one shared URL, and there is nothing to share about a
        // file that lives on this device alone.
        if (togetherAvailable()) {
            items.add(new MenuItem(R.drawable.ic_together_24dp, getString(R.string.together_title),
                    togetherSummary(), false, this::showTogetherMenu));
        }
        // Same two entry points the empty state offers (hence its strings), so opening something else is
        // not a matter of first getting back to an empty player.
        items.add(new MenuItem(R.drawable.ic_folder_open_24dp, getString(R.string.empty_state_open), null, false, () -> openFile(mPrefs.mediaUri)));
        items.add(new MenuItem(R.drawable.ic_link_24dp, getString(R.string.empty_state_link), null, false, emptyState::askForLink));
        // "More" → the full app settings screen (long-pressing the gear opens it directly, too).
        items.add(new MenuItem(R.drawable.ic_settings_24dp, getString(R.string.button_more), null, false, this::openSettings));
        showSideMenu(getString(R.string.pref_title), items);
    }

    // --- Watch together -------------------------------------------------------------------------
    // A room syncs one shared http(s) URL between devices over a public relay. It is fixed when the
    // room is created: no frame can change what a room plays, so nobody can push a stranger's video
    // onto anybody else's screen. Everything here is session-scoped — no preferences, no persistence.

    /**
     * The speed the viewer chose. While a room is closing a gap it nudges the player's rate by a few
     * percent, so the value on the player is not the one to show in the menu or to remember on exit.
     */
    private float userSpeed() {
        if (together != null && together.isActive()) {
            return together.userSpeed();
        }
        return player == null ? 1f : player.getPlaybackParameters().speed;
    }

    /** Rooms are offered for network media only — see the note above. */
    private boolean togetherAvailable() {
        if (together != null && together.isActive()) {
            return true;
        }
        final Uri uri = currentPlayingUri();
        return player != null && haveMedia && uri != null && Utils.isSupportedNetworkUri(uri);
    }

    private String togetherSummary() {
        if (together == null || !together.isActive()) {
            return null;
        }
        final String name = together.roomName();
        // A room is named after the media by default (createRoom), and unnamed rooms are named after their own
        // code — so the named form is only worth printing when someone actually typed something else into it.
        // Otherwise it read "Room 123456 [123456] · 2", with the code in it twice.
        return name.isEmpty() || name.equals(getString(R.string.together_room_default_name, together.code()))
                ? getString(R.string.together_badge, together.code(), together.peers())
                : getString(R.string.together_badge_named, name, together.code(), together.peers());
    }

    private void showTogetherMenu() {
        final List<MenuItem> items = new ArrayList<>();
        if (together != null && together.isActive()) {
            items.add(new MenuItem(R.drawable.ic_share_24dp, getString(R.string.together_share),
                    together.code(), false, this::shareInvite));
            items.add(new MenuItem(R.drawable.ic_close_24dp, getString(R.string.together_leave),
                    null, false, this::leaveRoom));
        } else {
            items.add(new MenuItem(R.drawable.ic_add_24dp, getString(R.string.together_create),
                    null, false, this::createRoom));
            items.add(new MenuItem(R.drawable.ic_search_24dp, getString(R.string.together_find),
                    null, false, this::findRooms));
            items.add(new MenuItem(R.drawable.ic_link_24dp, getString(R.string.together_enter_code),
                    null, false, this::askRoomCode));
        }
        showSideMenu(getString(R.string.together_title), items);
    }

    /** The way in when nothing is playing yet — from the empty page, where creating is impossible. */
    void showJoinMenu() {
        final List<MenuItem> items = new ArrayList<>();
        items.add(new MenuItem(R.drawable.ic_search_24dp, getString(R.string.together_find),
                null, false, this::findRooms));
        items.add(new MenuItem(R.drawable.ic_link_24dp, getString(R.string.together_enter_code),
                null, false, this::askRoomCode));
        showSideMenu(getString(R.string.together_join), items);
    }

    /**
     * Ask the shared lobby who is watching what. There is no directory anywhere — the list is
     * whichever rooms happen to be listening and choose to answer, collected over about a second and
     * a half, so an empty result means "nobody answered", not "nobody is out there".
     */
    private void findRooms() {
        Relay.setBase(mPrefs.togetherRelay);
        showSnack(getString(R.string.together_searching), null);
        TogetherManager.discover(rooms -> {
            if (isFinishing()) {
                return;
            }
            if (rooms.isEmpty()) {
                showSnack(getString(R.string.together_none_found), null);
                return;
            }
            final List<MenuItem> items = new ArrayList<>();
            for (final JSONObject ad : rooms) {
                final String id = ad.optString("id");
                final boolean locked = ad.optInt("pwd") == 1;
                final String title = ad.optString("title", "").isEmpty()
                        ? ad.optString("name", id) : ad.optString("title");
                final String poster = ad.optString("poster", "");
                // The padlock is worth carrying whatever else the row has; the group glyph is only there
                // so a row is not blank, so it steps aside for a poster rather than doubling up with it.
                items.add(new MenuItem(
                        locked ? R.drawable.ic_lock_24dp
                                : poster.isEmpty() ? R.drawable.ic_together_24dp : 0,
                        poster,
                        title,
                        getString(R.string.together_room_summary,
                                ad.optString("owner", ""), ad.optInt("members")),
                        false,
                        () -> {
                            if (locked) {
                                askRoomPassword(id);
                            } else {
                                joinRoom(id, "");
                            }
                        }));
            }
            showSideMenu(getString(R.string.together_find), items);
        });
    }

    /** A room from the list said it has a password; the code we already know. */
    private void askRoomPassword(final String code) {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setSingleLine(true);
        input.setHint(getString(R.string.together_password_hint));
        // The room has said it is locked, so the default is worth offering here exactly as it is offered
        // when creating one: people watching together tend to reuse one password, and this is the only
        // dialog that knows for certain a password is wanted. Not offered when joining by code, where
        // nothing is known about the room — the password goes into its address, so guessing one at an
        // open room lands in a different channel and reports, truthfully and uselessly, that it does
        // not exist.
        input.setText(mPrefs.togetherPassword);
        input.setSelection(input.getText().length());
        new AlertDialog.Builder(this)
                .setTitle(code)
                .setView(input)
                .setPositiveButton(android.R.string.ok,
                        (dialog, which) -> joinRoom(code, input.getText().toString()))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /**
     * Name the room and settle its password before opening it, which is the plugin's own order of
     * questions. The password is pre-filled from settings so the common case is one confirmation,
     * while a one-off room can still be given its own — or none. Whether the room is listed is asked
     * here too, and the answer is remembered as the next room's default: it is a decision about the
     * room being made, not a standing preference worth hunting for in settings.
     */
    private void createRoom() {
        final JSONObject session = sessionDescription();
        if (session == null) {
            return;
        }
        final String code = Room.newCode();
        final JSONObject card = roomCard();
        final String suggested = card == null || card.optString("title", "").isEmpty()
                ? getString(R.string.together_room_default_name, code)
                : card.optString("title");

        final EditText name = new EditText(this);
        name.setInputType(InputType.TYPE_CLASS_TEXT);
        name.setSingleLine(true);
        name.setText(suggested);
        name.setSelection(name.getText().length());

        final EditText password = new EditText(this);
        password.setInputType(InputType.TYPE_CLASS_TEXT);
        password.setSingleLine(true);
        password.setHint(getString(R.string.together_password_hint));
        password.setText(mPrefs.togetherPassword);

        final CheckBox listed = new CheckBox(this);
        listed.setText(R.string.together_public);
        listed.setChecked(mPrefs.togetherPublic);

        // A listed room without a password is open to whoever reads the list. Said as a quiet line under
        // the tick that put it there, not as an error after the fact: the accent here is already the brand
        // coral, and a red field on top of it reads as alarm rather than as the one thing left to fill in.
        final TextView note = new TextView(this);
        note.setText(R.string.together_public_needs_password);
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        note.setTextColor(ContextCompat.getColor(this, R.color.error_ink_muted));
        note.setPadding(0, 0, 0, Utils.dpToPx(4));
        note.setVisibility(View.GONE);

        final LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        final int pad = Utils.dpToPx(16);
        fields.setPadding(pad, 0, pad, 0);
        fields.addView(name);
        fields.addView(password);
        fields.addView(listed);
        fields.addView(note);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.together_create_title, code))
                .setView(fields)
                .setPositiveButton(android.R.string.ok, (d, which) -> {
                    mPrefs.updateTogetherPublic(listed.isChecked());
                    openRoom(code,
                            name.getText().toString().trim(),
                            password.getText().toString(),
                            session);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        // OK carries the refusal — greyed out while the pair is impossible, live from both the tick and
        // the field. Wired on show: the buttons do not exist before that.
        dialog.setOnShowListener(d -> {
            final Runnable sync = () -> {
                final boolean open = listed.isChecked() && password.getText().length() == 0;
                note.setVisibility(open ? View.VISIBLE : View.GONE);
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(!open);
            };
            listed.setOnCheckedChangeListener((button, checked) -> sync.run());
            password.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable s) {
                    sync.run();
                }
            });
            sync.run();
        });
        dialog.show();
    }

    private void openRoom(final String code, final String name, final String password,
                          final JSONObject session) {
        ensureTogether();
        together.create(code, password, mPrefs.togetherPublic,
                name.isEmpty() ? getString(R.string.together_room_default_name, code) : name,
                session, roomNick());
        // The invite is the useful half, so it goes straight to the clipboard — on a phone the next
        // step is pasting it into a chat, and on TV the code is on screen to read out.
        copyToClipboard(together.invite());
        showSnack(getString(R.string.together_created, together.code()), null);
    }

    /** Ask for the six digits. Also the empty state's way in, which is the only one a TV has when
     *  nothing is playing yet — the gear menu lives in the player's controls. */
    void askRoomCode() {
        final EditText code = new EditText(this);
        // Text, not digits: a room made in Lampa has a letter code, and the same six characters
        // have to be typeable here.
        code.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        code.setSingleLine(true);
        code.setHint("ABC234");

        // Rooms made in Lampa carry a password whenever that setting is on there, and it goes into
        // the room's address — so without it we would land somewhere else entirely and report, quite
        // truthfully and quite uselessly, that no such room exists.
        final EditText password = new EditText(this);
        password.setInputType(InputType.TYPE_CLASS_TEXT);
        password.setSingleLine(true);
        password.setHint(getString(R.string.together_password_hint));

        final LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        final int pad = Utils.dpToPx(16);
        fields.setPadding(pad, 0, pad, 0);
        fields.addView(code);
        fields.addView(password);

        new AlertDialog.Builder(this)
                .setTitle(R.string.together_join)
                .setView(fields)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    final String entered = code.getText().toString().trim().toUpperCase(Locale.US);
                    if (Room.isCode(entered)) {
                        joinRoom(entered, password.getText().toString());
                    } else {
                        showSnack(getString(R.string.together_code_invalid), null);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Enter a room by code; the room says what it is playing as soon as we are in. */
    private void joinRoom(final String code, final String password) {
        ensureTogether();
        together.join(code, password, roomNick());
    }

    /**
     * The whole launch payload of this screen, as the room carries it: the media, its mime type, and
     * every extra the player was started with — the playlist and its per-episode names, posters,
     * seasons, imdb/tmdb ids, quality variants and subtitle lists, plus the headers a stream needs.
     * A member who joins with nothing but a code rebuilds exactly this session.
     */
    private JSONObject sessionDescription() {
        final Uri uri = currentPlayingUri();
        if (uri == null || !Utils.isSupportedNetworkUri(uri)) {
            return null;
        }
        try {
            final JSONObject session = new JSONObject().put("uri", uri.toString());
            final Intent intent = getIntent();
            if (intent != null) {
                if (intent.getType() != null) {
                    session.put("type", intent.getType());
                }
                final Bundle extras = intent.getExtras();
                if (extras != null) {
                    final Bundle copy = new Bundle(extras);
                    // Not the guest's to honour: it would report this playback to a launcher on their
                    // device that never asked for it.
                    copy.remove(API_RETURN_RESULT);
                    // Start them where the room actually is rather than where its host began — but
                    // only refresh a position the launcher already sent. Adding the key to a session
                    // that had none is what flips handleViewIntent into api mode, and a guest must
                    // not end up in a different mode from everyone else over a resume point the
                    // first heartbeat corrects anyway.
                    if (copy.containsKey(API_POSITION)
                            && player != null && player.isCurrentMediaItemSeekable()) {
                        copy.putInt(API_POSITION, (int) Math.max(0, player.getCurrentPosition()));
                    }
                    session.put("extras", SessionCodec.toJson(copy));
                }
            }
            return session;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * The thin card a Lampa peer understands. Their room model is a resolved URL plus a little
     * decoration — no season, no episode, no playlist — so this is all of what a viewer in the web
     * player can be told. Our own players get {@link #sessionDescription()} instead.
     */
    private JSONObject roomCard() {
        final Uri uri = currentPlayingUri();
        if (uri == null || !Utils.isSupportedNetworkUri(uri)) {
            return null;
        }
        final String title = apiTitle != null ? apiTitle : Utils.getFileName(this, uri);
        int tmdb = 0;
        if (apiTmdbId != null) {
            try {
                tmdb = Integer.parseInt(apiTmdbId);
            } catch (NumberFormatException ignored) {
                // Not every launcher sends a numeric id; the field is decoration on their side.
            }
        }
        try {
            return new JSONObject()
                    .put("url", uri.toString())
                    .put("title", title == null ? "" : title)
                    // Only a poster another device can actually fetch. A launcher's thumbnail is very often
                    // a content:// or file:// URI, which is meaningless off this device — and this field is
                    // handed straight to the other client's player and into the room list it publishes,
                    // where it shows as a broken image.
                    .put("poster", Utils.isSupportedNetworkUri(apiThumbnailUri)
                            ? apiThumbnailUri.toString() : "")
                    .put("tmdb", tmdb)
                    .put("source", "tmdb")
                    .put("type", apiSeason > 0 ? "tv" : "movie");
        } catch (Exception e) {
            return null;
        }
    }

    /** Play what the room plays, rebuilding the host's launch intent and running it through the very
     *  path a launcher's own intent takes — so the playlist, ids and subtitles behave identically. */
    private void openSession(final JSONObject session) {
        final String uriText = session == null ? null : session.optString("uri", null);
        if (uriText == null) {
            return;
        }
        final Uri uri = Uri.parse(uriText);
        if (!Utils.isSupportedNetworkUri(uri)) {
            return;
        }
        final Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, session.optString("type", null));
        final JSONObject extras = session.optJSONObject("extras");
        if (extras != null) {
            intent.putExtras(SessionCodec.toBundle(extras));
        }
        // A media change from a Lampa peer carries a URL and a display title and nothing else, so
        // pass the title through rather than leaving the header showing the tail of a URL. A room
        // snapshot adds the artwork, which is the same extra a launcher would have sent — so the header
        // gets its poster instead of the numbered placeholder.
        final String title = session.optString("title", null);
        final String poster = session.optString("poster", null);
        if (extras == null && title != null && !title.isEmpty()) {
            intent.putExtra(API_TITLE, title);
        }
        if (extras == null && poster != null && !poster.isEmpty()) {
            intent.putExtra(API_THUMBNAIL, poster);
        }
        applyingRoomMedia = true;
        try {
            setIntent(intent);
            handleViewIntent(intent);
            // Start where the room is, not where this device last left this film. Between the two,
            // handleViewIntent has just loaded our own remembered position — so playback began at it,
            // played from there, and was then dragged across to the room a second or two later, in full
            // view. The same lever a launcher uses to name a start position (see API_POSITION), applied
            // after the media is set up and before the player is built, so there is nothing to see.
            final long roomPosition = together == null ? -1 : together.roomPositionMs();
            if (roomPosition >= 0) {
                mPrefs.updatePosition(roomPosition);
            }
            initializePlayer();
        } finally {
            applyingRoomMedia = false;
        }
    }

    /** How the room sees us: the name from settings, generated on first use and editable there. */
    private String roomNick() {
        final String nick = mPrefs.togetherNick;
        return nick == null || nick.isEmpty() ? Build.MODEL : nick;
    }

    private void leaveRoom() {
        if (together != null) {
            together.leave();
        }
    }

    /** True when the intent was an invite link and has been handled here. Such a link carries only the
     *  room; what to play arrives over its channel a moment later. */
    boolean handleRoomIntent(final Intent intent) {
        final Room.Invite invite = Room.inviteFrom(intent == null ? null : intent.getData());
        if (invite == null) {
            return false;
        }
        joinRoom(invite.code, invite.password);
        return true;
    }

    /**
     * Playback has moved to something else. Mirrors what the Lampa plugin does, which is narrower
     * than it first looks: only the owner, and only stepping through the playlist, pulls the room
     * along. Everything else — a guest changing episode, anyone opening an unrelated video — simply
     * leaves the room, and leaves it quietly. Announcing the departure would be scolding somebody
     * for doing the obvious thing.
     *
     * @param playlistStep true when this is the next item of the same playlist rather than
     *                     altogether different media
     * @param auto         true when the player stepped by itself at the end of an item, rather than the
     *                     viewer picking another episode
     */
    private void checkRoomMedia(final boolean playlistStep, final boolean auto) {
        // The room itself asked for this switch, so it cannot be a divergence from the room. Worth
        // saying out loud because the check runs from handleViewIntent, where the *previous* player
        // is still alive and still reporting the previous item — which made following the host look
        // exactly like walking away from him.
        if (applyingRoomMedia) {
            return;
        }
        if (together == null || !together.isActive() || together.url() == null) {
            return;
        }
        final Uri uri = currentPlayingUri();
        if (uri != null && together.url().equals(uri.toString())) {
            return;
        }
        final JSONObject session =
                playlistStep && together.isOwner() ? sessionDescription() : null;
        if (session != null) {
            together.changeMedia(session);
            return;
        }
        // A guest whose playlist ran into the next episode has not walked away from anything: it was
        // given the room's own playlist along with the media, so the owner is stepping to the very same
        // episode and its word for it is one relay hop behind us. Leaving here dropped a room at nearly
        // every episode boundary, and it was a race — whoever's player happened to reach the end first
        // was the one to lose the room. Choosing another episode by hand is still walking away.
        if (playlistStep && auto) {
            together.mediaStepped(sessionDescription());
            return;
        }
        together.leave();
    }

    private void shareInvite() {
        final String invite = together == null ? null : together.invite();
        if (invite == null) {
            return;
        }
        final Intent share = new Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, invite);
        // Whether anything on this device can take it. A TV box has no messenger to hand a link to, and
        // a chooser with nothing in it is a dead end — so that device is shown the invite instead, in the
        // one form another phone can pick up off a screen.
        if (getPackageManager().queryIntentActivities(share, 0).isEmpty()) {
            showInviteQr(invite);
            return;
        }
        try {
            startActivity(Intent.createChooser(share, getString(R.string.together_share)));
        } catch (Exception e) {
            showInviteQr(invite);
        }
    }

    /** The invite as something to point a camera at, for a screen that cannot pass it on itself. The
     *  clipboard gets it too — a box with a keyboard, or a remote-control browser, can still use it. */
    private void showInviteQr(final String invite) {
        copyToClipboard(invite);
        final DisplayMetrics metrics = getResources().getDisplayMetrics();
        final Bitmap qr = Utils.qrBitmap(invite,
                (int) (Math.min(metrics.widthPixels, metrics.heightPixels) * 0.6f));
        if (qr == null) {
            showSnack(getString(R.string.together_created, together.code()), null);
            return;
        }
        final ImageView image = new ImageView(this);
        image.setImageBitmap(qr);
        image.setAdjustViewBounds(true);
        final int padding = Math.round(16 * metrics.density);
        image.setPadding(padding, padding, padding, padding);
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.together_qr_title, together.code()))
                .setMessage(getString(R.string.together_qr_hint))
                .setView(image)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void copyToClipboard(final String text) {
        final android.content.ClipboardManager clipboard =
                (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null && text != null) {
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("", text));
        }
    }

    private void ensureTogether() {
        // Read here rather than once at startup: this is the last moment before a socket opens, so a
        // relay changed in settings takes effect on the next room without the screen being rebuilt. The
        // invite page goes with it — it is read when a link is written, which is later still.
        Relay.setBase(mPrefs.togetherRelay);
        Room.setInvitePage(mPrefs.togetherInvitePage);
        if (together == null) {
            together = new TogetherManager(togetherHost());
        }
    }

    void updateRoomBadge() {
        final boolean active = together != null && together.isActive();

        // Which room, and how many of us. Shown with the controls and gone with them: the viewer asked for
        // the chrome, this is part of it. The room's name is deliberately absent — it defaults to the media's
        // own title, which the header prints in full two rows above.
        if (roomPill != null) {
            final boolean showPill = active && controllerChromeVisible && !inPip && !locked;
            if (showPill) {
                // Three states, not two: a room we are talking to, one we are still getting into, and one
                // that has gone quiet on us. Entering used to read as "reconnecting…" — nothing was
                // reconnecting, and a first connection that then failed left a coral line that had said
                // the wrong thing from the start and never changed.
                roomPill.setText(together.connected()
                        ? getString(R.string.together_badge, together.code(), together.peers())
                        : getString(together.everConnected()
                                ? R.string.together_offline : R.string.together_connecting,
                                together.code()));
                // Losing the relay does not stop the picture, so it is stated rather than alarmed about —
                // but on the brand colour, because the room has stopped being in step and nothing else says so.
                final int tint = together.connected()
                        ? 0xE6FFFFFF
                        : ContextCompat.getColor(this, R.color.brand);
                roomPill.setTextColor(tint);
                roomPill.setCompoundDrawableTintList(ColorStateList.valueOf(tint));
            }
            fadeChrome(roomPill, showPill);
        }

        // The float says only what the header cannot say in time: this pause is the room's, not yours. Bounded
        // by SyncEngine.BUFFER_WAIT_MAX_MS, so it is never on screen for long. Nothing of ours belongs in the
        // PiP thumbnail, and a locked screen is meant to be bare.
        if (roomBadge == null) {
            return;
        }
        // Two reasons the picture can be standing still through no fault of the viewer's: the room is
        // filling everybody's buffer at one position, or it is waiting on one member who fell behind.
        final boolean holding = active && together.holding();
        final String waiting = active ? together.waitingFor() : null;
        if ((!holding && waiting == null) || inPip || locked) {
            roomBadge.setVisibility(View.GONE);
            return;
        }
        // A stall read off a member's own position rather than announced by it comes with no name: the
        // heartbeat it was read from carries none. Their client's word for an unnamed member does here too.
        roomBadge.setText(holding
                ? getString(R.string.together_hold)
                : getString(R.string.together_waiting, waiting.isEmpty()
                        ? getString(R.string.together_act_somebody) : waiting));
        roomBadge.setVisibility(View.VISIBLE);
    }

    /**
     * A room message for the centred line the gestures use, but not at its size: that 20sp is set for a
     * brightness percentage read at a glance, and a sentence at 20sp shouts across the picture. Scaled on
     * the message rather than on the view, so nothing has to be put back afterwards and the readouts
     * sharing this line are untouched. A ratio, not a fixed size, so the system font scale carries through.
     */
    private CharSequence roomNotice(final String text) {
        final SpannableString notice = new SpannableString(text);
        final float hudSp = getResources().getDimension(R.dimen.exo_error_message_text_size)
                / getResources().getDisplayMetrics().scaledDensity;
        if (hudSp > 0) {
            notice.setSpan(new RelativeSizeSpan(ui.textSkip() / hudSp), 0, notice.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return notice;
    }

    /** The wait is over and the room is moving again — said in passing, because the float that announced
     *  the wait has just gone and its disappearance on its own reads as something having gone wrong. */
    private void announceHoldLifted() {
        if (playerView == null || inPip || locked) {
            return;
        }
        Utils.showText((CustomPlayerView) playerView, roomNotice(getString(R.string.together_go)),
                CustomPlayerView.MESSAGE_TIMEOUT_LONG);
    }

    /** Say who just took the controls. Playback has already followed them by the time this runs — the
     *  point is that the viewer knows the pause or the jump was not theirs and not a glitch. */
    private void announceRoomAction(final String nick, final TogetherManager.Act act) {
        // The PiP window is a thumbnail under the system's own controls; there is nowhere to say this.
        // A locked screen is meant to be bare — both pills hide there, and so does this.
        if (playerView == null || inPip || locked) {
            return;
        }
        final int verb;
        switch (act) {
            case PAUSED:   verb = R.string.together_act_paused; break;
            case RESUMED:  verb = R.string.together_act_resumed; break;
            case LEFT:     verb = R.string.together_act_left; break;
            default:       verb = R.string.together_act_seeked; break;
        }
        // A member who never introduced themselves is still worth announcing — the room did move.
        final String who = nick == null || nick.isEmpty()
                ? getString(R.string.together_act_somebody)
                : nick;
        // MESSAGE_TIMEOUT_LONG, not the touch timeout: this is a name to read, not a number to glance at.
        Utils.showText((CustomPlayerView) playerView, roomNotice(getString(verb, who)),
                CustomPlayerView.MESSAGE_TIMEOUT_LONG);
    }

    private TogetherManager.Host togetherHost() {
        return new TogetherManager.Host() {
            @Override
            public boolean ready() {
                return player != null && haveMedia;
            }

            @Override
            public boolean scrubbing() {
                // All three are gestures held under a finger: sampling mid-drag would broadcast every
                // intermediate frame, and the hold-to-speed-up preview is not a choice to share.
                return isScrubbing || ((CustomPlayerView) playerView).isSpeedBoosting()
                        || ((CustomPlayerView) playerView).isSeekGesture();
            }

            @Override
            public long positionMs() {
                return player == null ? 0 : Math.max(0, player.getCurrentPosition());
            }

            @Override
            public boolean playWhenReady() {
                // A film that has ended is not playing, whatever playWhenReady still says: Media3 leaves
                // the flag set and the position frozen at the duration. What that used to be broadcast as
                // — a seek to the end credits, every 750 ms, or a pause that stopped the whole room — is
                // now nobody's business but ours, because ended() below silences the diff outright. This
                // stays because it is true: the room's intent is followed from here, and a film that has
                // run out is not this device's vote to keep playing.
                return player != null && player.getPlayWhenReady()
                        && player.getPlaybackState() != Player.STATE_ENDED;
            }

            @Override
            public float speed() {
                return player == null ? 1f : player.getPlaybackParameters().speed;
            }

            @Override
            public boolean buffering() {
                return player != null && player.getPlaybackState() == Player.STATE_BUFFERING;
            }

            @Override
            public boolean ended() {
                // haveMedia is already covered by ready(), without which a prepare with nothing to play
                // reports this state at once.
                return player != null && player.getPlaybackState() == Player.STATE_ENDED;
            }

            @Override
            public long durationMs() {
                if (player == null) {
                    return 0;
                }
                final long duration = player.getDuration();
                return duration == C.TIME_UNSET ? 0 : duration;
            }

            @Override
            public String playingUri() {
                final Uri uri = currentPlayingUri();
                return uri == null ? null : uri.toString();
            }

            @Override
            public void applyPlay(final boolean play) {
                if (player != null) {
                    player.setPlayWhenReady(play);
                }
            }

            @Override
            public void applySeek(final long positionMs) {
                if (player != null) {
                    // Exactly where the room asked, not the nearest keyframe. setSeekParameters is sticky
                    // and every gesture sets its own (a scrub leaves CLOSEST_SYNC, a key seek NEXT/PREVIOUS),
                    // so without this a room seek inherited whatever the viewer last did — landing a whole
                    // keyframe interval out, which the room then reads as a gap and seeks at again.
                    player.setSeekParameters(SeekParameters.EXACT);
                    player.seekTo(positionMs);
                }
            }

            @Override
            public void applySpeed(final float speed) {
                if (player != null) {
                    player.setPlaybackSpeed(speed);
                }
            }

            @Override
            public void onRoomChanged() {
                updateRoomBadge();
            }

            @Override
            public void onRoomAction(final String nick, final TogetherManager.Act act) {
                announceRoomAction(nick, act);
            }

            @Override
            public long bufferedAheadMs() {
                // The same reading the statistics panel shows as "buffer": how far ahead of the playhead
                // the loader has got. A hold is over when everybody has enough of it.
                return player == null ? 0 : Math.max(0, player.getTotalBufferedDuration());
            }

            @Override
            public void onHoldLifted() {
                announceHoldLifted();
            }

            @Override
            public JSONObject sessionDescription() {
                return PlayerActivity.this.sessionDescription();
            }

            @Override
            public JSONObject roomCard() {
                return PlayerActivity.this.roomCard();
            }

            @Override
            public void openSession(final JSONObject session) {
                PlayerActivity.this.openSession(session);
            }

            @Override
            public void onJoinFailed() {
                showSnack(getString(R.string.together_no_room), null);
            }
        };
    }

    // Replaces the current item's URL with a separate-URL quality variant and reinitialises the player,
    // preserving the playback position in-session. Under apiAccess Prefs is non-persistent, so the
    // position is held in memory (not keyed by URI) and never written to disk.
    private void switchSource(Uri target, long positionMs, boolean resume) {
        if (player == null || target == null) {
            return;
        }
        // Carry the session's meta (selected tracks above all) into Prefs before the rebuild reads it
        // back — this path only wrote the position, so the restore used to pick up a stale track id.
        savePlayer();
        final int index = player.getCurrentMediaItemIndex();
        if (!apiMediaItems.isEmpty() && index >= 0 && index < apiMediaItems.size()) {
            final MediaItem old = apiMediaItems.get(index);
            apiMediaItems.set(index, old.buildUpon().setUri(target).build());
            apiPlaylistStartIndex = index;
        } else {
            mPrefs.mediaUri = target;
        }
        mPrefs.updatePosition(positionMs);
        sourceSwitchKeepPaused = !resume;
        restorePlayState = resume;
        initializePlayer();
    }

    // Re-applies a remembered SOURCE quality (by number of lines) to the item now playing — used after
    // an auto-next so a chosen quality carries across episodes. Falls back to the base URL when the new
    // episode has no matching label.
    private void applyStickyQuality() {
        if (player == null || stickyQualityLines <= 0) {
            return;
        }
        final LinkedHashMap<String, String> quality = currentQualityMap();
        if (quality == null || quality.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : quality.entrySet()) {
            if (qualityNumber(entry.getKey()) != stickyQualityLines) {
                continue;
            }
            final String url = entry.getValue();
            if (url == null || url.trim().isEmpty()) {
                return;
            }
            final Uri target = Uri.parse(url);
            if (target.equals(currentPlayingUri())) {
                return; // already playing the sticky quality
            }
            selectedVideoQualityMode = VideoQualityChoice.MODE_SOURCE;
            switchSource(target, 0, player.getPlayWhenReady());
            return;
        }
    }

    // The quality override lives on the player, so a rebuild drops it while the remembered choice (which
    // lights up the quality button and ticks the dialog row) stays. Re-assert it once tracks are known.
    // MODE_SOURCE needs nothing: its URL is already in mPrefs.mediaUri / apiMediaItems.
    private void restoreVideoQuality() {
        if (selectedVideoQualityMode == VideoQualityChoice.MODE_MAXIMUM) {
            applyVideoQuality(VideoQualityChoice.maximum());
        } else if (selectedVideoQualityMode == VideoQualityChoice.MODE_TRACK && selectedVideoTrackGroup != null) {
            applyVideoQuality(VideoQualityChoice.track("", "", "", selectedVideoTrackGroup,
                    selectedVideoTrackIndex, -1));
        }
    }

    private int selectedQualityIndex(List<VideoQualityChoice> choices) {
        if (selectedVideoQualityMode != VideoQualityChoice.MODE_SOURCE) {
            for (int i = 0; i < choices.size(); i++) {
                final VideoQualityChoice choice = choices.get(i);
                if (choice.mode == VideoQualityChoice.MODE_TRACK
                        && selectedVideoQualityMode == VideoQualityChoice.MODE_TRACK
                        && choice.group == selectedVideoTrackGroup
                        && choice.trackIndex == selectedVideoTrackIndex) {
                    return i;
                }
                if ((choice.mode == VideoQualityChoice.MODE_AUTO
                        || choice.mode == VideoQualityChoice.MODE_MAXIMUM)
                        && choice.mode == selectedVideoQualityMode) {
                    return i;
                }
            }
        }
        final Uri current = currentPlayingUri();
        if (current != null) {
            for (int i = 0; i < choices.size(); i++) {
                final VideoQualityChoice choice = choices.get(i);
                if (choice.mode == VideoQualityChoice.MODE_SOURCE && choice.sourceUrl != null
                        && choice.sourceUrl.equals(current.toString())) {
                    return i;
                }
            }
        }
        return 0;
    }

    // Number of scan lines a quality label denotes ("1080p" -> 1080, "4K"/"UHD" -> 2160, else 0).
    private static int qualityNumber(String label) {
        if (label == null) {
            return 0;
        }
        String normalized = label.toLowerCase(Locale.US);
        if (normalized.contains("4k") || normalized.contains("uhd")) {
            return 2160;
        }
        String digits = label.replaceAll("[^0-9]", "");
        try {
            return digits.isEmpty() ? 0 : Integer.parseInt(digits);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String shortCodec(String mimeType) {
        if (mimeType == null) return null;
        if (mimeType.contains("avc")) return "H.264";
        if (mimeType.contains("hevc")) return "H.265";
        if (mimeType.contains("av01")) return "AV1";
        if (mimeType.contains("vp9")) return "VP9";
        if (mimeType.contains("eac3")) return "E-AC3";
        if (mimeType.contains("ac3")) return "AC3";
        if (mimeType.contains("aac") || mimeType.contains("mp4a")) return "AAC";
        return mimeType.substring(mimeType.lastIndexOf('/') + 1).toUpperCase(Locale.US);
    }

    private static final class VideoQualityChoice {
        static final int MODE_AUTO = 0;
        static final int MODE_MAXIMUM = 1;
        static final int MODE_TRACK = 2;
        static final int MODE_SOURCE = 3;
        final String label;
        final String details;
        final String bitrateText;
        final int mode;
        final TrackGroup group;
        final int trackIndex;
        final int bitrate;
        final String sourceUrl;

        private VideoQualityChoice(String label, String details, String bitrateText,
                                   int mode, TrackGroup group, int trackIndex,
                                   int bitrate, String sourceUrl) {
            this.label = label;
            this.details = details;
            this.bitrateText = bitrateText;
            this.mode = mode;
            this.group = group;
            this.trackIndex = trackIndex;
            this.bitrate = bitrate;
            this.sourceUrl = sourceUrl;
        }

        static VideoQualityChoice auto() {
            return new VideoQualityChoice("", "", "", MODE_AUTO, null, -1, -1, null);
        }

        static VideoQualityChoice maximum() {
            return new VideoQualityChoice("", "", "", MODE_MAXIMUM, null, -1, -1, null);
        }

        static VideoQualityChoice track(String label, String details, String bitrateText,
                                        TrackGroup group, int index, int bitrate) {
            return new VideoQualityChoice(label, details, bitrateText,
                    MODE_TRACK, group, index, bitrate, null);
        }

        static VideoQualityChoice source(String label, String url) {
            return new VideoQualityChoice(label, "", "", MODE_SOURCE, null, -1, -1, url);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        restoreRotationLock();

        if (resultCode == RESULT_OK && alive) {
            releasePlayer();
        }

        if (requestCode == REQUEST_CHOOSER_VIDEO || requestCode == REQUEST_CHOOSER_VIDEO_MEDIASTORE) {
            if (resultCode == RESULT_OK) {
                resetApiAccess();
                restorePlayState = false;

                final Uri uri = data.getData();

                if (requestCode == REQUEST_CHOOSER_VIDEO) {
                    boolean uriAlreadyTaken = false;

                    // https://commonsware.com/blog/2020/06/13/count-your-saf-uri-permission-grants.html
                    final ContentResolver contentResolver = getContentResolver();
                    for (UriPermission persistedUri : contentResolver.getPersistedUriPermissions()) {
                        if (persistedUri.getUri().equals(mPrefs.scopeUri)) {
                            continue;
                        } else if (persistedUri.getUri().equals(uri)) {
                            uriAlreadyTaken = true;
                        } else {
                            try {
                                contentResolver.releasePersistableUriPermission(persistedUri.getUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            } catch (SecurityException e) {
                                e.printStackTrace();
                            }
                        }
                    }

                    if (!uriAlreadyTaken && uri != null) {
                        try {
                            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        } catch (SecurityException e) {
                            e.printStackTrace();
                        }
                    }
                }

                mPrefs.setPersistent(true);
                mPrefs.updateMedia(this, uri, data.getType());

                if (requestCode == REQUEST_CHOOSER_VIDEO) {
                    searchSubtitles();
                }
            }
        } else if (requestCode == REQUEST_CHOOSER_SUBTITLE || requestCode == REQUEST_CHOOSER_SUBTITLE_MEDIASTORE) {
            if (resultCode == RESULT_OK) {
                Uri uri = data.getData();

                if (requestCode == REQUEST_CHOOSER_SUBTITLE) {
                    try {
                        getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (SecurityException e) {
                        e.printStackTrace();
                    }
                }

                handleSubtitles(uri);
            }
        } else if (requestCode == REQUEST_CHOOSER_SCOPE_DIR) {
            if (resultCode == RESULT_OK) {
                final Uri uri = data.getData();
                try {
                    getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    mPrefs.updateScope(uri);
                    mPrefs.markScopeAsked();
                    searchSubtitles();
                } catch (SecurityException e) {
                    e.printStackTrace();
                }
            }
        } else if (requestCode == REQUEST_SETTINGS) {
            final Map<String, ?> before = settingsBefore;
            settingsBefore = null;
            mPrefs.loadUserPreferences();
            systemVolume = mPrefs.systemVolume;
            // Also switch the volume path over here, so it is right even when nothing below rebuilds
            applyVolumeMode();
            updateSubtitleStyle(this);
            updateOverlayClock();
            updateStats();
            // Coming back from the settings screen no longer rebuilds the player by itself (see onStop),
            // but options like the decoder priority or tunneling are baked into it at build time. So
            // rebuild when the screen actually changed something — going in for a look costs nothing.
            // A missing snapshot means the activity was recreated meanwhile, so rebuild to be safe.
            if (player != null && (before == null || !before.equals(mPrefs.snapshot()))) {
                sourceSwitchKeepPaused = true;
                releasePlayer();
                initializePlayer();
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }

        // Init here because onStart won't follow when app was only paused when file chooser was shown
        // (for example pop-up file chooser on tablets)
        if (resultCode == RESULT_OK && alive) {
            initializePlayer();
        }
    }

    private void handleSubtitles(Uri uri) {
        // Convert subtitles to UTF-8 if necessary
        SubtitleUtils.clearCache(this);
        uri = Utils.convertToUTF(this, uri);
        mPrefs.updateSubtitle(uri);
    }

    // Whether the current media is a Matroska container, detected from the resolved MIME type or the
    // URI extension. Extensionless streams that never reveal a matroska type are not matched.
    private boolean isMatroskaMedia() {
        if (MimeTypes.VIDEO_MATROSKA.equals(mPrefs.mediaType)) {
            return true;
        }
        if (mPrefs.mediaUri == null) {
            return false;
        }
        final String path = mPrefs.mediaUri.getPath();
        // Case-insensitive ".mkv" suffix test without allocating a lower-cased copy of the path.
        return path != null && path.regionMatches(true, path.length() - 4, ".mkv", 0, 4);
    }

    // Denies passthrough only for mimes this device has already proven broken (see
    // recoverByRevokingAudioMime()). Keyed by mime, not by wire encoding: DefaultAudioSink.getFormatSupport
    // in this build routes through the newer AudioOutputProvider abstraction rather than AudioCapabilities
    // directly, and a mime-keyed denial needs neither that internal plumbing nor AudioCapabilities
    // reconstruction (whose only public constructor drops speaker-layout/spatializer info). Delegates
    // everything else untouched. Mutable so a revocation applies to the player already built, but
    // deliberately silent: notifying the sink's listener here (Media3's own renderer-capabilities-changed
    // path, the one it uses when an HDMI receiver is unplugged mid-playback) is only valid while the
    // player is playing. Every revocation is decided from onPlayerError, by which point ExoPlayer has
    // already reset and cleared its media period queue, so that notification lands in
    // seekToCurrentPosition() with no playing period and throws (JPP-15). The caller re-prepares instead.
    private static final class AudioPassthroughDenylistSink extends ForwardingAudioSink {
        private final Set<String> deniedMimes;
        // True while the sink bitstreams a compressed format: the renderer configures us with a non-PCM
        // format only when it runs in bypass (passthrough), never when a decoder feeds us. Read on the app
        // thread by restartPassthroughAudio(), written here on the playback thread. Deliberately
        // conservative rather than exact: configure() is the only writer, so disabling the audio renderer
        // leaves the flag set from the last bitstream, and DefaultAudioSink may still be holding the new
        // config in pendingConfiguration when we read it. Both only ever cost one needless seek.
        private volatile boolean passthrough;

        AudioPassthroughDenylistSink(AudioSink sink, Set<String> initiallyDenied) {
            super(sink);
            this.deniedMimes = new CopyOnWriteArraySet<>(initiallyDenied);
        }

        @Override
        public void configure(AudioSink.AudioSinkConfig config) throws AudioSink.ConfigurationException {
            passthrough = !MimeTypes.AUDIO_RAW.equals(config.format.sampleMimeType);
            super.configure(config);
        }

        boolean isPassthrough() {
            return passthrough;
        }

        @Override
        public int getFormatSupport(Format format) {
            return deniedMimes.contains(format.sampleMimeType)
                    ? AudioSink.SINK_FORMAT_UNSUPPORTED : super.getFormatSupport(format);
        }

        @Override
        public boolean supportsFormat(Format format) {
            return !deniedMimes.contains(format.sampleMimeType) && super.supportsFormat(format);
        }

        // Called from the app thread by recoverByRevokingAudioMime(). deniedMimes is read on the
        // playback thread by getFormatSupport/supportsFormat above — CopyOnWriteArraySet needs no lock.
        void revoke(String mime) {
            deniedMimes.add(mime);
        }
    }

    public void initializePlayer() {
        boolean isNetworkUri = Utils.isSupportedNetworkUri(mPrefs.mediaUri);
        haveMedia = mPrefs.mediaUri != null && !mPrefs.suppressResume;
        // Only the transition it was set for may read it. A skip that never produced one would otherwise
        // leave it behind, and the next episode the viewer picked by hand would pass for an automatic step.
        steppedBySkip = false;
        if (skipMediaAfterFatalError) {
            skipMediaAfterFatalError = false;
            haveMedia = false;
        }

        // A reinitialisation that must not auto-play — a SOURCE quality switch, or a rebuild triggered by
        // returning to the foreground; otherwise apiAccess or a zero position would force it. Consumed here
        // rather than next to its use below, which the empty state skips: the flag must never outlive this
        // call and suppress the next file the user opens.
        final boolean keepPaused = sourceSwitchKeepPaused;
        sourceSwitchKeepPaused = false;
        // A watchdog armed for the player being replaced must not judge the fresh one. The load watchdog
        // needs saying too: the teardown below is inline rather than releasePlayer(), which is where it
        // would otherwise be cancelled, and callers that replace a still-buffering player (onNewIntent,
        // a playlist pick) would leave it armed to stop the new session 30 s in.
        resumeFrameRendered = true;
        cancelLoadWatchdog();

        // Unless this is the recovery rebuild itself (which must keep forceHevcForDolbyVision), clear the
        // one-shot Dolby Vision recovery state so a normal open plays DV through its regular path and a
        // future failure on any item can recover again.
        if (pendingStuckRecovery) {
            pendingStuckRecovery = false;
        } else {
            forceHevcForDolbyVision = false;
        }

        // Fresh player — the source re-read budget starts over.
        sourceRetries = 0;
        decoderRetries = 0;
        liveStallRecoveries = 0;
        playerStartPositionMs = C.TIME_UNSET;
        videoDecoderName = null;
        audioDecoderName = null;
        bandwidthBitrate = 0;
        // A restart that never got its onTracksChanged belongs to the player being replaced here.
        audioRestartInFlight = false;
        audioRestartSettling = false;
        audioEverStarted = false;
        playerView.removeCallbacks(rebufferArmRunnable);
        audioRestartPending = false;

        // Fresh media — drop any container track names so the tap re-parses for this item.
        containerTracks.clear();
        containerTracksUri = null;
        resolvedTrackNames.clear();

        // Also drops a deferred error here, not only in releasePlayer: onNewIntent (sharing a video into
        // the running player) reaches this inline teardown instead, and the stashed error would then
        // surface over the new clip.
        errorToShow = null;
        if (player != null) {
            player.removeListener(playerListener);
            // Renderer decoder-init events reach the collector via a post from the playback thread, so one
            // enqueued during teardown would write the old player's decoder name onto the next session.
            player.removeAnalyticsListener(playbackInfoListener);
            player.clearMediaItems();
            player.release();
            player = null;
            audioSink = null;
            boostProcessor = null;
        }

        trackSelector = new DefaultTrackSelector(this);
        trackSelector.setParameters(trackSelector.buildUponParameters()
                .setAllowInvalidateSelectionsOnRendererCapabilitiesChange(true));
        if (mPrefs.tunneling) {
            trackSelector.setParameters(trackSelector.buildUponParameters()
                    .setTunnelingEnabled(true)
            );
        }
        // Ordered fallback chain: the selector walks the list and takes the first language the media
        // actually carries. An empty list leaves the media's own order alone.
        applyPreferredAudioLanguages();
        // A subtitle nobody asked for is in the way, so the file marking one as default is not enough
        // on its own: subtitles come on when the preferred-language list below matches, or by hand.
        // This used to depend on the system captioning toggle, which is no longer read anywhere.
        trackSelector.setParameters(trackSelector.buildUponParameters()
                .setIgnoredTextSelectionFlags(C.SELECTION_FLAG_DEFAULT)
        );
        applyPreferredTextLanguages();
        // Set rather than left to the default so Dv7Converter can hand the very same instance to the
        // Matroska extractor it re-creates — subtitle parsing is a constructor argument there, and
        // matching it by construction beats matching Media3's defaults from memory.
        final SubtitleParser.Factory subtitleParserFactory = new DefaultSubtitleParserFactory();
        // https://github.com/google/ExoPlayer/issues/8571
        DefaultExtractorsFactory extractorsFactory = new DefaultExtractorsFactory()
                .setSubtitleParserFactory(subtitleParserFactory)
                .setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS)
                .setTsExtractorTimestampSearchBytes(1500 * TsExtractor.TS_PACKET_SIZE);
        // On TV boxes the platform MediaCodec decoder for the heavy codecs common in MKV remuxes
        // (DTS/EAC3/TrueHD) can wedge during init on the playback thread — no exception is thrown, so
        // the load never reaches a ready state (JPP-1005). Instead of blanket-forcing software audio
        // (which also kills Atmos/passthrough on every TV), hide only those codecs from the platform
        // decoder via the combined MediaCodecSelector below. MediaCodecAudioRenderer consults the sink
        // first, so where the receiver advertises passthrough the compressed bitstream still goes out
        // (Atmos preserved); where it does not, the track falls through to the ffmpeg software
        // renderer. Only MKV audio on a TV is affected; video keeps the user's decoder priority.
        final boolean blockHeavyMkvAudio = isTvBox && isMatroskaMedia();
        // Audio sample mimes this device has already proven cannot passthrough (see
        // recoverByRevokingAudioMime()) — denied at the sink only; the platform decoder for the same
        // mime is left alone since it is an independent subsystem that may work fine.
        final Set<String> revokedAudioMimes = mPrefs.revokedAudioMimes;
        // Always subclassed (not only for blockHeavyMkvAudio/an existing revocation) so buildAudioSink
        // below can install the live AudioPassthroughDenylistSink from a device's very first play —
        // it needs to be present before any revocation exists, so a first stall can revoke into it
        // without a player rebuild (see recoverByRevokingAudioMime()).
        DefaultRenderersFactory baseRenderersFactory = new DefaultRenderersFactory(this) {
            @Override
            protected void buildAudioRenderers(Context context, int extensionRendererMode,
                                               MediaCodecSelector mediaCodecSelector,
                                               boolean enableDecoderFallback, AudioSink audioSink,
                                               Handler eventHandler,
                                               AudioRendererEventListener eventListener,
                                               ArrayList<Renderer> out) {
                // Ensure the ffmpeg audio renderer exists as a fallback whenever a heavy MKV codec is
                // hidden from the platform decoder or a mime has been revoked — even when the user
                // chose "device decoders only". Keep it behind the platform renderer (ON, not PREFER)
                // so passthrough/decode still wins when available. An explicit PREFER stands.
                super.buildAudioRenderers(context,
                        (blockHeavyMkvAudio || !revokedAudioMimes.isEmpty())
                                && extensionRendererMode == EXTENSION_RENDERER_MODE_OFF
                                ? EXTENSION_RENDERER_MODE_ON : extensionRendererMode,
                        mediaCodecSelector, enableDecoderFallback, audioSink, eventHandler,
                        eventListener, out);
            }

            @Override
            protected AudioSink buildAudioSink(Context context, boolean enableFloatOutput,
                                                boolean enableAudioTrackPlaybackParams) {
                // Same sink the base class builds, plus the boost processor — it has to be in the chain
                // from the start, since the chain is fixed once the sink exists. Silence skipping and
                // playback speed are appended after it by DefaultAudioProcessorChain, as before.
                boostProcessor = new BoostAudioProcessor();
                AudioSink sink = new DefaultAudioSink.Builder(context)
                        .setEnableFloatOutput(enableFloatOutput)
                        .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                        .setAudioProcessors(new AudioProcessor[]{boostProcessor})
                        .build();
                audioSink = new AudioPassthroughDenylistSink(sink, revokedAudioMimes);
                return audioSink;
            }

            @Override
            protected void buildTextRenderers(Context context, TextOutput output, Looper outputLooper,
                                              int extensionRendererMode, ArrayList<Renderer> out) {
                // The subtitle offset wraps the text renderer on both sides — its cue output and its
                // clock (see SubtitleOffset). The list the base class appends to already holds the
                // video/audio renderers, hence the index.
                final SubtitleOffset offset = new SubtitleOffset(output, outputLooper,
                        new SubtitleOffset.Position() {
                            @Override
                            public long currentMs() {
                                return player == null ? C.TIME_UNSET : player.getCurrentPosition();
                            }

                            @Override
                            public boolean playing() {
                                return player != null && player.isPlaying();
                            }
                        });
                offset.setOffsetSec(subtitleOffsetSec);
                offset.setTimeline(subtitleTimeline);
                subtitleOffset = offset;
                final int first = out.size();
                super.buildTextRenderers(context, offset, outputLooper, extensionRendererMode, out);
                for (int i = first; i < out.size(); i++) {
                    out.set(i, offset.wrap(out.get(i)));
                }
            }
        };
        @SuppressLint("WrongConstant") DefaultRenderersFactory renderersFactory = baseRenderersFactory
                .setExtensionRendererMode(mPrefs.decoderPriority)
                // Several decoders often claim the same mime, and the first one is not always the one that
                // works. Without this, a decoder that fails to initialise ends playback outright instead
                // of letting the next candidate try.
                .setEnableDecoderFallback(true)
                .setMapDV7ToHevc(mPrefs.mapDV7ToHevc);
        if (forceHevcForDolbyVision || blockHeavyMkvAudio) {
            // One combined codec selector for two independent needs:
            // - blockHeavyMkvAudio: no platform decoder for the heavy MKV audio codecs, so they
            //   passthrough (if the sink supports it) or fall back to ffmpeg (see above).
            // - forceHevcForDolbyVision: route a Dolby Vision track to the plain HEVC decoder (its base
            //   layer is HEVC), bypassing a device DV decoder that wedged or failed while decoding.
            //   Picture stays HDR10.
            renderersFactory.setMediaCodecSelector((mimeType, requiresSecureDecoder, requiresTunnelingDecoder) -> {
                if (blockHeavyMkvAudio && HEAVY_MKV_AUDIO_MIMES.contains(mimeType)) {
                    return Collections.emptyList();
                }
                return MediaCodecSelector.DEFAULT.getDecoderInfos(
                        forceHevcForDolbyVision && MimeTypes.VIDEO_DOLBY_VISION.equals(mimeType)
                                ? MimeTypes.VIDEO_H265 : mimeType,
                        requiresSecureDecoder, requiresTunnelingDecoder);
            });
        }

        ExoPlayer.Builder playerBuilder = new ExoPlayer.Builder(this, renderersFactory)
                .setTrackSelector(trackSelector);

        // Build the upstream data source factory (content://, file://, http(s)), applying any
        // launch-intent HTTP headers/User-Agent, then wrap it so we can tap the byte stream and
        // read rich track names straight from the container (see TrackNameParsingDataSource).
        androidx.media3.datasource.DataSource.Factory upstreamFactory = new DefaultDataSource.Factory(this);

        if (haveMedia && isNetworkUri && mPrefs.mediaUri.getScheme().toLowerCase().startsWith("http")) {
            HashMap<String, String> headers = new HashMap<>();
            String userAgent = null;

            // Headers supplied by the launching app as a flat [name, value, name, value, ...] array
            // (MX Player / Lampa convention). Some CDNs require a specific User-Agent to authorize.
            if (apiHeaders != null) {
                for (int i = 0; i + 1 < apiHeaders.length; i += 2) {
                    final String name = apiHeaders[i];
                    final String value = apiHeaders[i + 1];
                    if (name == null || value == null) {
                        continue;
                    }
                    if ("User-Agent".equalsIgnoreCase(name)) {
                        userAgent = value;
                    } else {
                        headers.put(name, value);
                    }
                }
            }

            String userInfo = mPrefs.mediaUri.getUserInfo();
            if (userInfo != null && userInfo.length() > 0 && userInfo.contains(":")) {
                headers.put("Authorization", "Basic " + Base64.encodeToString(userInfo.getBytes(), Base64.NO_WRAP));
            }

            // Always our own factory, headers or not, for the read timeout. Media3 defaults it to 8 s,
            // which is a verdict a torrent-backed server cannot meet: it answers the range request at
            // once and then goes quiet for as long as fetching those pieces from peers takes. Silence is
            // not a broken stream there, but the socket timeout raised it as a source error, so the
            // re-read budget was spent on a stream that was working — the "timeout" reported on large
            // files over a connection that never dropped. Give a read the same patience the load watchdog
            // gives the load: past that, the watchdog stops the player with a message that says what to
            // do, instead of a retry storm ending in a broken-stream error.
            final DefaultHttpDataSource.Factory defaultHttpDataSourceFactory = new DefaultHttpDataSource.Factory()
                    .setAllowCrossProtocolRedirects(true)
                    .setReadTimeoutMs((int) VIDEO_LOAD_TIMEOUT_MS);
            if (userAgent != null) {
                defaultHttpDataSourceFactory.setUserAgent(userAgent);
            }
            if (!headers.isEmpty()) {
                defaultHttpDataSourceFactory.setDefaultRequestProperties(headers);
            }
            upstreamFactory = new DefaultDataSource.Factory(this, defaultHttpDataSourceFactory);
        }

        final androidx.media3.datasource.DataSource.Factory dataSourceFactory = new TrackNameParsingDataSource.Factory(upstreamFactory, trackNameListener);
        // Dolby Vision profile 7 is rewritten as profile 8.1 on the way out of the extractor, so the
        // display gets Dolby Vision instead of the base layer's HDR10 (see Dv7Converter). Not when the
        // user asked for the base layer outright ("Dolby Vision profile 7 fallback", handled by
        // setMapDV7ToHevc above), and not during the recovery that exists to get away from the device's
        // Dolby Vision decoder altogether.
        final boolean convertDV7 = !mPrefs.mapDV7ToHevc && !forceHevcForDolbyVision;
        dv7Converter = convertDV7 ? new Dv7Converter(extractorsFactory, subtitleParserFactory) : null;
        playerBuilder.setMediaSourceFactory(
                new DefaultMediaSourceFactory(this, convertDV7 ? dv7Converter : extractorsFactory)
                        .setDataSourceFactory(dataSourceFactory));

        player = playerBuilder.build();

        if (!mPrefs.allowSystemFrameRate) {
            // Stop ExoPlayer from voting Surface.setFrameRate() on start/pause/seek. On many TV
            // panels a refresh-rate switch (even a "seamless" one) re-syncs the panel and flickers.
            player.setVideoChangeFrameRateStrategy(C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF);
        }

        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build();
        player.setAudioAttributes(audioAttributes, true);
        applyVolumeMode();
        applySleepAtEndOfItem();

        youTubeOverlay.player(player);
        playerView.setPlayer(player);

        if (mediaSession != null) {
            mediaSession.release();
        }

        if (player.canAdvertiseSession()) {
            try {
                mediaSession = new MediaSession.Builder(this, player).build();
            } catch (IllegalStateException e) {
                e.printStackTrace();
            }
        }

        playerView.setControllerShowTimeoutMs(-1);

        // Only when a lock is actually there to undo — the flag is static, so it can outlive the session
        // that set it. Undoing one that was never on also restored the video orientation, which turned a
        // launcher start into landscape until showEmptyState relaxed it again a few lines below: a visible
        // flip to landscape and back on a phone held upright. Both branches below set the orientation the
        // page really needs, so nothing is lost by leaving it alone here.
        if (locked) {
            locked = false;
            clearLockUi();
        }

        if (haveMedia) {
            emptyState.hide();
            if (isNetworkUri) {
                // Reads as a light rail ahead of the playhead, the way the design shows a buffering stream.
                timeBar.setBufferedColor(0xC0FFFFFF);
            } else {
                // Local files report the whole file as buffered, so anything brighter floods the bar:
                // https://github.com/google/ExoPlayer/issues/5765
                timeBar.setBufferedColor(0x33FFFFFF);
            }

            playerView.setResizeMode(mPrefs.resizeMode);
            currentAspectRatio = mPrefs.aspectRatio;
            if (mPrefs.aspectRatio > 0) {
                playerView.applyAspectMode(mPrefs.resizeMode, mPrefs.aspectRatio);
            } else if (mPrefs.resizeMode == AspectRatioFrameLayout.RESIZE_MODE_ZOOM) {
                playerView.setScale(mPrefs.scale);
            } else {
                playerView.setScale(1.f);
            }
            updatebuttonAspectRatioIcon();

            MediaItem.Builder mediaItemBuilder = new MediaItem.Builder()
                    .setUri(mPrefs.mediaUri)
                    .setMimeType(mPrefs.mediaType);
            String title;
            if (apiTitle != null) {
                title = apiTitle;
            } else {
                title = Utils.getFileName(PlayerActivity.this, mPrefs.mediaUri);
            }
            if (title != null) {
                final MediaMetadata mediaMetadata = new MediaMetadata.Builder()
                        .setTitle(title)
                        .setDisplayTitle(title)
                        .setArtworkUri(apiThumbnailUri)
                        .build();
                mediaItemBuilder.setMediaMetadata(mediaMetadata);
            }
            // Both, not one or the other: an external subtitle found for this media is added to the
            // ones the launcher supplied rather than in their place. Dropping either is how a track the
            // viewer was watching disappears on the next rebuild.
            final MediaItem.SubtitleConfiguration remembered = rememberedSubtitle();
            final List<MediaItem.SubtitleConfiguration> startingSubs = new ArrayList<>();
            if (apiAccess) {
                startingSubs.addAll(apiSubs);
            }
            if (remembered != null) {
                startingSubs.add(remembered);
            }
            if (!startingSubs.isEmpty()) {
                mediaItemBuilder.setSubtitleConfigurations(startingSubs);
            }
            if (!apiMediaItems.isEmpty()) {
                // The playlist items are built from the intent and never saw the remembered subtitle, so
                // the one item about to play gets it here. The rest pick up their own when they start.
                final List<MediaItem> items = new ArrayList<>(apiMediaItems);
                if (remembered != null && apiPlaylistStartIndex >= 0 && apiPlaylistStartIndex < items.size()) {
                    items.set(apiPlaylistStartIndex, withSubtitle(items.get(apiPlaylistStartIndex), remembered));
                }
                player.setMediaItems(items, apiPlaylistStartIndex, mPrefs.getPosition());
            } else {
                player.setMediaItem(mediaItemBuilder.build(), mPrefs.getPosition());
            }

            try {
                if (loudnessEnhancer != null) {
                    loudnessEnhancer.release();
                }
                loudnessEnhancer = new LoudnessEnhancer(player.getAudioSessionId());
                Utils.applyBoost();
            } catch (Exception e) {
                e.printStackTrace();
            }

            notifyAudioSessionUpdate(true);

            videoLoading = true;

            updateLoading(true);

            if ((mPrefs.getPosition() == 0L || apiAccess || apiAccessPartial) && !keepPaused) {
                play = true;
            }

            updateTopInfo();

            setupSkipSource();

            updateButtons(true);

            ((DoubleTapPlayerView)playerView).setDoubleTapEnabled(true);

            if (!apiAccess) {
                if (nextUriThread != null) {
                    nextUriThread.interrupt();
                }
                nextUri = null;
                nextUriThread = new Thread(() -> {
                    Uri uri = findNext();
                    if (!Thread.currentThread().isInterrupted()) {
                        nextUri = uri;
                    }
                });
                nextUriThread.start();
            }

            player.setHandleAudioBecomingNoisy(!isTvBox);
//            mediaSession.setActive(true);
        } else {
            playerView.showController();
            emptyState.show();
        }

        player.addListener(playerListener);
        player.addAnalyticsListener(playbackInfoListener);
        // The renderers factory has just loaded the extension libraries it needs, so this is free here.
        if (ffmpegAvailable == null
                && mPrefs.decoderPriority != DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF) {
            ffmpegAvailable = FfmpegLibrary.isAvailable();
        }
        player.prepare();

        // Only while the activity is up: a recovery rebuild posted from onPlayerError can land after the user
        // has already left, and resuming there would play in the background.
        if (restorePlayState && alive) {
            restorePlayState = false;
            playerView.showController();
            playerView.setControllerShowTimeoutMs(PlayerActivity.CONTROLLER_TIMEOUT);
            player.setPlayWhenReady(true);
        }
    }

    /**
     * Spend the start-up play the loading player is holding — unless the room is standing still to let
     * everybody fill a buffer. That pause is not ours to undo: releasing the hold is what starts playback
     * then, at the position the whole room agreed on.
     *
     * <p>Checked here rather than where the hold pauses us, because on the join this is for there is
     * nothing to pause yet — the other client announces its hold before it says what the room is playing,
     * so the player that ignores it is one built afterwards. Left to itself it played through the hold,
     * and the room's "carry on" then had to drag it back, which cost a second load of the very buffer the
     * hold had just filled.
     */
    private final Runnable frameRateGiveUpRunnable = this::frameRateSettled;

    /**
     * Nothing more to wait for from the display: disarm the listener armed for a mode change and spend
     * the play the loading player is holding. Every path that does not request a new mode ends here, the
     * ones that give up early included — those used to leave the play pending for good.
     */
    private void frameRateSettled() {
        playerView.removeCallbacks(frameRateGiveUpRunnable);
        if (displayManager != null && displayListener != null) {
            displayManager.unregisterDisplayListener(displayListener);
        }
        if (play) {
            play = false;
            playPending();
        }
    }

    private void playPending() {
        if (together != null && together.holding()) {
            return;
        }
        if (player != null) {
            player.play();
        }
        if (playerView != null) {
            playerView.hideController();
        }
    }

    /**
     * Opens the settings screen, recording the preference state first. The screen writes into the same
     * SharedPreferences instance this activity reads, so the "did anything actually change" comparison
     * has to be against a snapshot taken before it opens — taking one on the way back always compares
     * the new state with itself.
     */
    void openSettings() {
        openSettings(null);
    }

    /** @param scrollToKey preference to open the screen on, or null to start at the top. */
    void openSettings(final String scrollToKey) {
        settingsBefore = mPrefs.snapshot();
        final Intent intent = new Intent(this, SettingsActivity.class);
        if (scrollToKey != null) {
            intent.putExtra(SettingsActivity.EXTRA_SCROLL_TO, scrollToKey);
        }
        // Lets the audio language picker put the languages of the clip on screen right now on top,
        // instead of burying them somewhere in the device's several hundred locales.
        final List<String> languages = new ArrayList<>();
        for (final AudioChoice choice : buildAudioChoices()) {
            if (choice.language != null && !languages.contains(choice.language)) {
                languages.add(choice.language);
            }
        }
        if (!languages.isEmpty()) {
            intent.putExtra(SettingsActivity.EXTRA_MEDIA_LANGUAGES, languages.toArray(new String[0]));
        }
        startActivityForResult(intent, REQUEST_SETTINGS);
    }

    /**
     * Restores the isolated level on a fresh player, and clears the attenuation whenever the system
     * volume is in charge, so switching the setting back can never leave the player quietened.
     */
    private void applyVolumeMode() {
        if (player != null) {
            player.setVolume(baseVolume());
        }
    }

    /** The player gain the current volume mode asks for — the level a fade ramps down from and back to. */
    private float baseVolume() {
        return systemVolume ? 1f : Math.min(playerVolume, 100f) / 100f;
    }

    private void savePlayer() {
        // Outside the player guard: the error screen keeps the volume gestures alive without a player.
        mPrefs.updateVolume(Math.round(playerVolume));
        if (player != null) {
            mPrefs.updateBrightness(Math.round(mBrightnessControl.percent));
            mPrefs.updateOrientation();

            if (haveMedia) {
                // Prevent overwriting temporarily inaccessible media position
                if (player.isCurrentMediaItemSeekable()) {
                    // A clip watched to its end is remembered as unwatched: keeping the end as its
                    // position reopens it already finished — seeked to the last frame and paused, since
                    // initializePlayer only autoplays from zero. Keyed on STATE_ENDED and not on being
                    // near the end, because savePlayer is also how a recovery rebuild carries the
                    // position across a released player: a decoder that wedges in the closing seconds
                    // has to come back where it was, not at the start.
                    final long position = player.getPlaybackState() == Player.STATE_ENDED
                            ? 0 : player.getCurrentPosition();
                    mPrefs.updatePosition(position);
                    rememberEpisodePosition(player.getCurrentMediaItemIndex(), position);
                }
                mPrefs.updateMeta(getSelectedTrack(C.TRACK_TYPE_AUDIO),
                        // A painted subtitle is on screen with no track selected, so getSelectedTrack
                        // answers "#none" — which reads back as "the viewer chose off" and disables the
                        // very track the next rebuild restores from mPrefs.subtitleUri. Null means no
                        // choice recorded, letting that track's DEFAULT flag select it as a fresh open does.
                        paintedSubtitleUri != null ? null : getSelectedTrack(C.TRACK_TYPE_TEXT),
                        playerView.getResizeMode(),
                        playerView.getVideoSurfaceView().getScaleX(),
                        currentAspectRatio,
                        // The viewer's speed, not the one a room may be nudging to close a gap —
                        // otherwise leaving mid-correction remembers 1.05× as a preference.
                        userSpeed());
            }
        }
    }

    private void rememberEpisodePosition(final int index, final long position) {
        if (apiPlaylistPositions != null && index >= 0 && index < apiPlaylistPositions.length) {
            apiPlaylistPositions[index] = position;
        }
    }

    private void cancelLoadWatchdog() {
        if (playerView != null) {
            playerView.removeCallbacks(loadTimeoutRunnable);
        }
    }

    // Start a fresh window, remembering how much had been transferred when it opened.
    private void armLoadWatchdog() {
        if (playerView == null) {
            return;
        }
        cancelLoadWatchdog();
        loadWatchdogBytes = TrackNameParsingDataSource.bytesRead.get();
        playerView.postDelayed(loadTimeoutRunnable, VIDEO_LOAD_TIMEOUT_MS);
    }

    private void reportVideoLoadTimeout() {
        if (player == null) {
            return;
        }
        // Still downloading — give it another window instead of stopping it. The user is not left
        // guessing: the loading ring carries the transfer rate (loadingSpeedRunnable), so a slow fill
        // reads as "alive, just slow", and Back still leaves whenever they have had enough.
        if (TrackNameParsingDataSource.bytesRead.get() - loadWatchdogBytes >= LOAD_PROGRESS_MIN_BYTES) {
            armLoadWatchdog();
            return;
        }
        // A silent stall (buffering never reached READY). Stop the loaders: they keep pulling bytes for as
        // long as the player sits in BUFFERING, and hiding the spinner below hides the rate readout with
        // it, so the wait turns into an invisible download that shows nothing. Stopped, not released or
        // rebuilt — that is what strands a codec on a wedged playback thread; stop() is only a message to
        // it. STATE_IDLE keeps the timeline and the position, so the play button (dispatchPlayPause ->
        // handlePlayButtonAction) re-prepares this very item, which is what the message asks for.
        player.stop();
        // Not sent to Sentry — it is usually an upstream/network condition, not an app bug.
        updateLoading(false);
        // Entering the wait disabled the episode arrows (showLoadingRunnable) and only STATE_READY and the
        // error paths ever re-enabled them, so a timeout left the one escape from a stuck episode dead.
        // They work from here: stepEpisodeWhileIdle reloads out of an idle player.
        setEpisodeNavLoading(false);
        // A film that has been playing for an hour and then stopped is not a film that "took too long to
        // start", and that was the message it got. Deliberately not stalledAtStart(): that answers a
        // different question — whether *this* playback attempt produced anything — and its baseline is not
        // rebased on a seek (see playerStartPositionMs), so a rewind reads as "at start" in mid-film. All
        // this message needs is whether the item ever became ready, which is exactly what a set baseline
        // means. Both messages ask for the same thing, and the play button re-prepares either way.
        showSnack(getString(playerStartPositionMs == C.TIME_UNSET
                ? R.string.error_playback_timeout : R.string.error_playback_stalled), null);
    }

    public void releasePlayer() {
        releasePlayer(true);
    }

    public void releasePlayer(boolean save) {
        cancelLoadWatchdog();
        // A pending source re-read belongs to the session being torn down here, same as errorToShow below.
        // So do both watchdogs that guard a retained session (see onStop / onStart): whatever path got
        // here, the session they were watching is gone.
        if (playerView != null) {
            playerView.removeCallbacks(sourceRetryRunnable);
            playerView.removeCallbacks(decoderRetryRunnable);
            playerView.removeCallbacks(backgroundReleaseRunnable);
            playerView.removeCallbacks(resumeWatchdogRunnable);
            playerView.removeCallbacks(passthroughRestartRunnable);
            playerView.removeCallbacks(rebufferArmRunnable);
            audioRestartPending = false;
            audioRestartInFlight = false;
            audioRestartSettling = false;
            audioEverStarted = false;
            playerView.removeCallbacks(showLoadingRunnable);
            // A pending key-seek target belongs to the session being torn down; left armed it would land
            // on whatever plays next.
            playerView.removeCallbacks(keyScrubCommit);
        }
        keyScrubTarget = -1;
        keyScrubSteps = 0;
        stopLoadingSpeed();
        // An error deferred until the controller is fully visible belongs to the playback session being
        // torn down here. Kept around, it would surface over whatever plays next — an error screen for a
        // clip the user already moved on from.
        errorToShow = null;
        if (save) {
            savePlayer();
        }

        if (player != null) {
            notifyAudioSessionUpdate(false);

//            mediaSession.setActive(false);
            if (mediaSession != null) {
                mediaSession.release();
            }

            if (player.isPlaying() && restorePlayStateAllowed) {
                restorePlayState = true;
            }
            player.removeListener(playerListener);
            // Renderer decoder-init events reach the collector via a post from the playback thread, so one
            // enqueued during teardown would write the old player's decoder name onto the next session.
            player.removeAnalyticsListener(playbackInfoListener);
            player.clearMediaItems();
            player.release();
            player = null;
            audioSink = null;
            boostProcessor = null;
        }
        stopSkipPolling();
        cancelSegmentFinder();
        cancelSubtitleSearch();
        // The paint had no track behind it; the rebuild puts the file back as a real one
        // (rememberedSubtitle). Deliberately not clearSubtitleTimeline(): the parsed timeline has to
        // survive so initializePlayer can seed the new SubtitleOffset with it, and updateSubtitleTimeline
        // recognises it by subtitleTimelineUri and re-hands the same object — no re-read, no blank gap.
        // After the savePlayer() above, which still needs to see the flag.
        paintedSubtitleUri = null;
        hideSkipPill();
        skipBuilt = false;
        if (timeBar != null) {
            timeBar.clearSkipHighlights();
        }
        stopEndsAtUpdates();
        if (overlayClock != null) {
            overlayClock.setVisibility(View.GONE);
        }
        setEpisodeNavLoading(false);
        Glide.with(getApplicationContext()).clear(posterView);
        posterSlot.setVisibility(View.GONE);
        topInfoPanel.setVisibility(View.GONE);
        if (playlistDialog != null) {
            playlistDialog.dismiss();
            playlistDialog = null;
        }
        if (qualityDialog != null) {
            qualityDialog.dismiss();
            qualityDialog = null;
        }
        if (skipOffsetDialog != null) {
            skipOffsetDialog.dismiss();
            skipOffsetDialog = null;
        }
        if (subtitleOffsetDialog != null) {
            subtitleOffsetDialog.dismiss();
            subtitleOffsetDialog = null;
        }
        // Only the panel goes: this runs on a quality switch too, and an armed timer has to ride across that.
        if (sleepTimerDialog != null) {
            sleepTimerDialog.dismiss();
            sleepTimerDialog = null;
        }
        if (menuDialog != null) {
            menuDialog.dismiss();
            menuDialog = null;
        }
        if (buttonPlaylist != null) {
            // Keep it while there is a playlist to step through: after a failed episode this is the way
            // off it, and showPlaylistDialog builds the list without a player.
            buttonPlaylist.setVisibility(apiMediaItems.size() > 1 ? View.VISIBLE : View.GONE);
        }
        if (buttonQuality != null) {
            buttonQuality.setVisibility(View.GONE);
        }
        updateButtons(false);
    }

    private class PlayerListener implements Player.Listener {
        @Override
        public void onVideoSizeChanged(VideoSize videoSize) {
            // Fires when the new rendition actually renders, which is when getVideoFormat() finally
            // reports it — onTracksChanged is too early for the header badge.
            updateMediaInfo();
            // Media3 resets the content-frame AR to the video's natural AR on every size change (e.g. a
            // mid-stream video-track switch), silently dropping a forced ratio. Reassert it after that
            // update (posted, so it wins).
            // Skip while ZOOM is active: that means free pinch-zoom has taken over, and reasserting would
            // fight it (adaptive streams fire this on every resolution switch).
            if (currentAspectRatio > 0 && playerView != null
                    && playerView.getResizeMode() == AspectRatioFrameLayout.RESIZE_MODE_FIT) {
                playerView.post(() ->
                        playerView.applyAspectMode(AspectRatioFrameLayout.RESIZE_MODE_FIT, currentAspectRatio));
            }
        }

        // Also fires when a destroyed surface comes back, which is what the resume watchdog waits for.
        @Override
        public void onRenderedFirstFrame() {
            resumeFrameRendered = true;
            // Fires again after every seek's flush, which is the only signal a seek that never left
            // STATE_READY gives. Without it the one-seek-at-a-time gate the scrubbing and swipe-seek
            // paths share can latch shut mid-drag.
            frameRendered = true;
        }

        @Override
        public void onAudioSessionIdChanged(int audioSessionId) {
            try {
                if (loudnessEnhancer != null) {
                    loudnessEnhancer.release();
                }
                loudnessEnhancer = new LoudnessEnhancer(audioSessionId);
                Utils.applyBoost();
            } catch (Exception e) {
                e.printStackTrace();
            }
            notifyAudioSessionUpdate(true);
        }

        @Override
        public void onPositionDiscontinuity(Player.PositionInfo oldPosition,
                                            Player.PositionInfo newPosition, int reason) {
            // A new item starts its own playback, so progress is measured from where it begins — otherwise
            // an item that wedges on its first frame would look mid-stream because the previous one played.
            // Only on an item change: rebasing on every discontinuity would also rebase on a seek (the user
            // scrubbing to shake a frozen picture loose is exactly when the distinction has to hold), on
            // each skipped silence, and on internal live-window updates.
            if (oldPosition.mediaItemIndex != newPosition.mediaItemIndex) {
                playerStartPositionMs = currentPeriodPositionMs();
            }
            // A delayed cue still waiting to be shown belongs to the moment it was read at, not to this one.
            if (subtitleOffset != null) {
                subtitleOffset.clear();
            }
            // The seek flushed the audio output, so the bitstream may need re-locking (see
            // audioRestartPending). Latched, not run here. Within one item only.
            if (reason == Player.DISCONTINUITY_REASON_SEEK
                    && oldPosition.mediaItemIndex == newPosition.mediaItemIndex) {
                audioRestartPending = true;
            }
            if (apiPlaylistPositions == null || player == null) {
                return;
            }
            final int oldIndex = oldPosition.mediaItemIndex;
            final int newIndex = newPosition.mediaItemIndex;
            // Leaving an episode: remember where we left it, except that an auto transition means it
            // played to its end, so that one is remembered as unwatched. Keeping its end would send a
            // manual jump back to it straight to its last frame, from where it advances off again.
            if (oldIndex != newIndex) {
                rememberEpisodePosition(oldIndex,
                        reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION ? 0 : oldPosition.positionMs);
            }
            // Manually jumping back to an already-watched episode: resume where we left it. Auto-advance
            // (gapless) keeps starting the next episode from the beginning, as it should. The follow-up
            // seek lands with oldIndex == newIndex, so it neither loops nor overwrites the saved slot.
            if (reason == Player.DISCONTINUITY_REASON_SEEK && oldIndex != newIndex
                    && newIndex >= 0 && newIndex < apiPlaylistPositions.length) {
                final long saved = apiPlaylistPositions[newIndex];
                if (saved != C.TIME_UNSET && saved > 0 && newPosition.positionMs < 1000) {
                    player.seekTo(newIndex, saved);
                }
            }
        }

        @Override
        public void onMediaItemTransition(MediaItem mediaItem, int reason) {
            // Keep the playlist start index (and mediaUri) tracking the item that is actually
            // playing. They would otherwise stay frozen at the session's initial episode, so a
            // rebuild after onStop (setMediaItems uses apiPlaylistStartIndex) would restart the
            // first episode while applying the current episode's saved position.
            if (!apiMediaItems.isEmpty() && player != null) {
                final int idx = player.getCurrentMediaItemIndex();
                if (idx >= 0 && idx < apiMediaItems.size()) {
                    apiPlaylistStartIndex = idx;
                    final MediaItem current = apiMediaItems.get(idx);
                    if (current.localConfiguration != null) {
                        mPrefs.mediaUri = current.localConfiguration.uri;
                    }
                }
            }
            // The remembered external subtitle belonged to the item that just ended: it was downloaded
            // or picked for that episode, and the next rebuild would put it back — minutes out of sync
            // with what is playing now. PLAYLIST_CHANGED is excluded because that is the transition
            // attachSubtitleTrack() itself causes, and clearing there would undo the attach that raised it.
            if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                mPrefs.updateSubtitle(null);
                // Painting is not tied to a track, so nothing else would stop the previous episode's
                // subtitles from being drawn over this one.
                clearSubtitleTimeline();
                cancelSubtitleSearch();
            }
            // The new item opens its own audio output; a restart latched on the old one must not tear it down.
            // The armer goes with it: a stall at the end of an episode would otherwise land on the next one's
            // opening track, which is exactly how a stream ends up silent from the first second. Clearing
            // audioEverStarted is what actually makes that safe rather than merely likely — a jump to a saved
            // position inside the new item seeks again after this callback has run (the seek is issued from
            // onPositionDiscontinuity, and re-entrant events are dispatched after the batch that raised them),
            // so the latch can be armed for the new item no matter what is cleared here.
            playerView.removeCallbacks(rebufferArmRunnable);
            audioRestartPending = false;
            audioRestartSettling = false;
            audioEverStarted = false;
            updateTopInfo();
            hideSkipButton();
            cancelSegmentFinder();
            setupSkipSource();
            // The next episode of the same playlist — the one case the room can follow.
            final boolean stepped = steppedBySkip;
            steppedBySkip = false;
            checkRoomMedia(true, stepped || reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO);
        }

        @Override
        public void onEvents(Player player, Player.Events events) {
            // Media3 re-shows the subtitle button (greyed, disabled) on its own control updates while
            // loading. Re-assert our "hidden until subtitle tracks exist" rule afterwards (deferred so it wins),
            // but only on events that can actually touch the controls — not on every frequent event dispatch.
            if (playerView != null && events.containsAny(
                    Player.EVENT_TRACKS_CHANGED,
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_AVAILABLE_COMMANDS_CHANGED,
                    Player.EVENT_PLAYBACK_STATE_CHANGED)) {
                playerView.post(PlayerActivity.this::updateSubtitleButton);
            }

            // A gapless playlist auto-advance can move to the next item while staying STATE_READY, so
            // onPlaybackStateChanged never re-fires and rebuildSkip() is never called for the new item.
            // onMediaItemTransition has already reset skipBuilt via setupSkipSource(); once the new item's
            // duration is known, build its highlights exactly once. The skipBuilt guard shares the work with
            // the STATE_READY path so there is no double build.
            if (!skipBuilt && player.getPlaybackState() == Player.STATE_READY) {
                final long duration = player.getDuration();
                if (duration != C.TIME_UNSET && duration > 0) {
                    rebuildSkip();
                    skipBuilt = true;
                    maybeFetchSegmentsOnline();
                }
            }
        }

        @Override
        public void onTracksChanged(Tracks tracks) {
            // Second half of restartPassthroughAudio(): the disable has provably reached the playback thread,
            // so put the audio track type back — rebuilt from the current parameters, not from a snapshot, so
            // a track choice made in between is not clobbered. Returns early: this intermediate selection has
            // no audio and is replaced within the same pass, and refreshing the UI for it would only make the
            // audio button flicker.
            if (audioRestartInFlight) {
                audioRestartInFlight = false;
                if (player != null) {
                    player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false).build());
                }
                return;
            }
            // Tracks are now known — (re)map any container names onto them, then refresh the header.
            resolveTrackNames();
            updateMediaInfo();
            // In-stream renditions are known only now, so the quality button's visibility can change.
            updateQualityButton();
            updateAudioButton();
            if (playerView != null) {
                playerView.post(PlayerActivity.this::updateSubtitleButton);
            }
            updateSubtitleTimeline(tracks);
            // The track list is what decides whether anything is missing, so this is the first moment
            // the question can be asked at all.
            maybeSearchSubtitlesOnline(tracks);
            // Apply a sticky quality choice to a freshly auto-advanced episode once its variants are known.
            // Posted so the reinitialisation never runs while listeners are being dispatched.
            if (playerView != null) {
                playerView.post(PlayerActivity.this::applyStickyQuality);
            }
        }

        // A pause only latches; onIsPlayingChanged is where the AudioTrack gets rebuilt. Immediately rather
        // than through REBUFFER_ARM_MS: a pause is one discrete act, so there is no burst to rate-limit, and
        // it cannot be our own recreate, which never touches playWhenReady.
        //
        // No filter on the reason. Every way playWhenReady goes down stops the AudioTrack, and by the time the
        // latch is spent the only thing that matters is that playback is starting again — why it stopped has
        // stopped being relevant. Losing audio focus to another app is if anything the likeliest way to lose a
        // receiver's lock, and the output going away (AUDIO_BECOMING_NOISY) is filtered at the far end anyway,
        // by the sink no longer reporting passthrough.
        @Override
        public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
            if (playWhenReady) {
                return;
            }
            // The end-of-item pause Media3 was asked for on behalf of the sleep timer — turn it into a close.
            if (sleepAtEndOfItem && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
                fireSleepTimer();
                return;
            }
            // Whatever was posted would now run against a paused player, which is the one thing to avoid.
            playerView.removeCallbacks(passthroughRestartRunnable);
            audioRestartPending = true;
        }

        // The one place a stale bitstream output is rebuilt, and the one place the remaining stalls are
        // noticed. isPlaying is READY && playWhenReady && no suppression, so becoming true is every moment
        // playback actually starts — after a resume, after a seek, after a rebuffer, after a chime took the
        // output away — which makes it the only spend point needed. And a drop to false with playWhenReady
        // still set is a stall rather than a pause: neither a rebuffer nor a suppression touches playWhenReady
        // or reports a reason, so there is nothing else to hear them by. ENDED and IDLE are not stalls —
        // playWhenReady survives both, and a latch armed there would be spent on whatever output comes next.
        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            // Subtitles are painted off the media position, which only moves while this is true.
            if (subtitleOffset != null) {
                subtitleOffset.wake();
            }
            if (isPlaying) {
                playerView.removeCallbacks(rebufferArmRunnable);
                audioRestartSettling = false;
                if (audioEverStarted) {
                    if (audioRestartPending) {
                        requestPassthroughRestart();
                    }
                } else {
                    audioEverStarted = true;
                    audioRestartPending = false;
                }
            } else if (player != null && player.getPlayWhenReady() && !audioRestartSettling
                    && (player.getPlaybackState() == Player.STATE_BUFFERING
                        || player.getPlaybackState() == Player.STATE_READY)) {
                playerView.postDelayed(rebufferArmRunnable, REBUFFER_ARM_MS);
            }
            playerView.setKeepScreenOn(isPlaying);

            if (Utils.isPiPSupported(PlayerActivity.this)) {
                if (isPlaying) {
                    updatePictureInPictureActions(R.drawable.ic_pause_24dp, R.string.exo_controls_pause_description, CONTROL_TYPE_PAUSE, REQUEST_PAUSE);
                } else {
                    updatePictureInPictureActions(R.drawable.ic_play_arrow_24dp, R.string.exo_controls_play_description, CONTROL_TYPE_PLAY, REQUEST_PLAY);
                }
            }

            if (!isScrubbing) {
                if (isPlaying) {
                    if (shortControllerTimeout) {
                        playerView.setControllerShowTimeoutMs(CONTROLLER_TIMEOUT / 3);
                        shortControllerTimeout = false;
                        restoreControllerTimeout = true;
                    } else {
                        playerView.setControllerShowTimeoutMs(CONTROLLER_TIMEOUT);
                    }
                } else {
                    playerView.setControllerShowTimeoutMs(-1);
                }
            }

            // Only a film that has actually run out drops the lock. Every pause used to, and playback stops
            // for reasons the viewer never asked for: onStop pauses on the way to the background, a network
            // stream re-buffering reports isPlaying() false mid-film, and the PiP action pauses too — each
            // one silently unlocked the screen and, through clearLockUi, threw the pinned orientation away
            // with it. Nobody is trapped by keeping it: a tap brings the unlock bar back and two Backs leave.
            if (!isPlaying && PlayerActivity.locked
                    && player != null && player.getPlaybackState() == Player.STATE_ENDED) {
                PlayerActivity.locked = false;
                clearLockUi();
            }

            if (isPlaying) {
                startSkipPolling();
            } else {
                stopSkipPolling();
            }

            // Pausing while the controller is already visible doesn't change its visibility, so arm the
            // pause auto-hide here too; resuming cancels it (guarded inside scheduleHideControllerOnPause).
            scheduleHideControllerOnPause();
        }

        @SuppressLint("SourceLockedOrientationActivity")
        @Override
        public void onPlaybackStateChanged(int state) {
            boolean isNearEnd = false;
            final long duration = player.getDuration();
            if (duration != C.TIME_UNSET) {
                final long position = player.getCurrentPosition();
                if (position + 4000 >= duration) {
                    isNearEnd = true;
                }
            }
            setEndControlsVisible(haveMedia && (state == Player.STATE_ENDED || isNearEnd));

            if (state == Player.STATE_READY) {
                frameRendered = true;
                cancelLoadWatchdog();
                // Loaded successfully — clear any pending resolver-handshake flag from a prior attempt.
                resolverNotReadyUri = null;
                decoderRetries = 0;
                // Same for the re-read budget, which was per player build: a stream that recovered and
                // played on has not spent anything. Three hiccups spread over a long film — a torrent
                // backend refilling, a proxy dropping one read an hour — would otherwise add up until the
                // fourth ended playback outright. Three in a row still gives up.
                sourceRetries = 0;
                // Playback starts here, so this is what stalledAtStart() measures progress against. A live
                // stream is positioned well inside its window before it becomes ready, and a resumed file
                // starts at its saved timecode — neither is progress.
                if (playerStartPositionMs == C.TIME_UNSET) {
                    playerStartPositionMs = currentPeriodPositionMs();
                }

                // Ready — hide the spinner and re-enable the episode arrows. Done unconditionally (not only on
                // the initial open) so episode switches, which don't set videoLoading, are also cleared.
                updateLoading(false);
                setEpisodeNavLoading(false);

                if (!skipBuilt) {
                    rebuildSkip();
                    // The online lookup needs the stream length: it is what SkipDB/SkipMe are asked with
                    // at all, and what picks Aniskip's submission for this file's cut. While the duration
                    // is still unknown, leave skipBuilt unset so the onEvents path — the one that does
                    // check it — runs this once it lands, instead of the lookup silently staying
                    // length-less for the whole item with no third attempt coming.
                    if (currentDurationSec() > 0) {
                        skipBuilt = true;
                        maybeFetchSegmentsOnline();
                    }
                }

                updateMediaInfo();

                if (videoLoading) {
                    videoLoading = false;

                    if (mPrefs.orientation == Utils.Orientation.UNSPECIFIED) {
                        mPrefs.orientation = Utils.getNextOrientation(mPrefs.orientation);
                        Utils.setOrientation(PlayerActivity.this, mPrefs.orientation);
                    }

                    final Format format = player.getVideoFormat();

                    if (format != null) {
                        if (!isTvBox && mPrefs.orientation == Utils.Orientation.VIDEO) {
                            if (Utils.isPortrait(format)) {
                                PlayerActivity.this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
                            } else {
                                PlayerActivity.this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                            }
                            updateButtonRotation();
                        }

                        updateSubtitleViewMargin(format);
                    }

                    if (duration != C.TIME_UNSET && duration > TimeUnit.MINUTES.toMillis(20)) {
                        timeBar.setKeyTimeIncrement(TimeUnit.MINUTES.toMillis(1));
                    } else {
                        timeBar.setKeyCountIncrement(20);
                    }

                    boolean switched = false;
                    if (mPrefs.frameRateMatching) {
                        if (play) {
                            if (displayManager == null) {
                                displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
                            }
                            if (displayListener == null) {
                                displayListener = new DisplayManager.DisplayListener() {
                                    @Override
                                    public void onDisplayAdded(int displayId) {

                                    }

                                    @Override
                                    public void onDisplayRemoved(int displayId) {

                                    }

                                    @Override
                                    public void onDisplayChanged(int displayId) {
                                        frameRateSettled();
                                    }
                                };
                            }
                            displayManager.registerDisplayListener(displayListener, null);
                        }
                        // The rate the stats panel prints is the rate the display needs, and by now the app
                        // usually has it. Measuring it again through a second MediaExtractor costs seconds
                        // and cannot open HLS at all, which is why online sources never switched.
                        final float rate = videoFrameRate();
                        if (rate > 0) {
                            Utils.handleFrameRate(PlayerActivity.this, rate);
                            switched = true;
                        } else {
                            // Nothing published a rate — a container neither Media3 nor our parser reads.
                            switched = Utils.switchFrameRate(PlayerActivity.this, currentMediaUri());
                        }
                    }
                    if (switched) {
                        // A requested mode either comes back as onDisplayChanged or never comes back at
                        // all: a panel can apply a refresh-rate-only change without reporting one, and a
                        // request the system declines reports nothing. Without a floor the pending play is
                        // never spent — on a phone whose display is pinned to 60 Hz the file just sits on
                        // its first frame, paused, with no spinner and no error.
                        playerView.postDelayed(frameRateGiveUpRunnable, FRAME_RATE_SWITCH_TIMEOUT_MS);
                    } else {
                        frameRateSettled();
                    }

                    if (mPrefs.speed <= 0.99f || mPrefs.speed >= 1.01f) {
                        player.setPlaybackSpeed(mPrefs.speed);
                    }
                    // Fresh player: re-assert what the user had picked. Also under apiAccess — the ids are
                    // kept in memory there (Prefs is non-persistent) and cleared by updateMedia, so a
                    // launcher-driven session restores its own choice and not a previous clip's.
                    setSelectedTracks(mPrefs.subtitleTrackId, mPrefs.audioTrackId);
                    restoreVideoQuality();
                }
            } else if (state == Player.STATE_BUFFERING) {
                // Buffering (e.g. switching episodes) — show the spinner in place of play and disable the
                // arrows, but only once the wait is worth showing (see LOADING_INDICATOR_DELAY_MS).
                playerView.postDelayed(showLoadingRunnable, LOADING_INDICATOR_DELAY_MS);
                // (Re)arm the watchdog: if this buffering never resolves to STATE_READY, it is a stuck load.
                armLoadWatchdog();
            // Only real media can end. initializePlayer prepares unconditionally, and a prepare with no
            // media items reports STATE_ENDED at once, so the empty state — a launcher start with nothing
            // to resume, the fallback after a fatal error, a deleted file — would otherwise count as a
            // video watched to its end and report that to the launcher. Same guard as on the end controls
            // above.
            } else if (state == Player.STATE_ENDED && haveMedia) {
                cancelLoadWatchdog();
                playbackFinished = true;
                // A single item, or the last of a playlist, ends here rather than in an end-of-item pause.
                if (sleepAtEndOfItem) {
                    fireSleepTimer();
                } else if (apiAccess) {
                    finish();
                }
            }
        }

        @Override
        public void onPlayerError(PlaybackException error) {
            updateLoading(false);
            cancelLoadWatchdog();
            // The Lampac stream resolver returned its handshake ({"rch":…}) instead of media: it
            // resolves the real stream by running client-side code over a WebSocket, which this player
            // does not implement, so the link can never be obtained here. Show a friendly message and
            // stop — this is an unsupported upstream flow, not an app bug, so it is not reported to Sentry.
            if (isResolverNotReadyForCurrentItem()) {
                resolverNotReadyUri = null;
                stopWithMessage(getString(R.string.error_stream_not_ready), null);
                return;
            }
            // An extensionless streaming URL (e.g. a resolver that returns HLS) gets guessed as a
            // progressive source and then fails to parse. Re-prepare it as the manifest type the
            // real response revealed before treating the source error as fatal.
            if (error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED
                    && recoverFromContainerError()) {
                return;
            }
            // Media3 reports two unrelated failures as ERROR_CODE_TIMEOUT, and only one of them is a
            // playback problem.
            //
            // An ExoTimeoutException is the player's own lifecycle plumbing giving up. Leaving the
            // player (Back, or opening the settings screen) makes the window invisible, which destroys
            // the SurfaceView the player is still attached to; on a slow box the decoder does not hand
            // the surface back inside Media3's 2 s budget, so this lands here about two seconds after
            // the user has already moved on — an error window popping up over whatever they went back
            // to. Nothing failed for them: the position was saved in onPause, and onStart builds a new
            // player from scratch, so there is nothing to say and nothing to report.
            //
            // A StuckPlayerException is the real thing, and it carries which failure this was: a dead
            // buffer, a frozen playback clock, an item running past its declared duration without ending,
            // or a suppression that never lifts. The detector reports them through
            // createForUnexpected(e, ERROR_CODE_TIMEOUT), so the type has to be read here. Only a frozen
            // clock is something this app can act on: re-decoding Dolby Vision as plain HEVC, dropping
            // tunneling or denying a passthrough mime cannot help a source that stopped sending bytes.
            if (error.errorCode == PlaybackException.ERROR_CODE_TIMEOUT) {
                final Throwable cause = error.getCause();
                if (cause instanceof ExoTimeoutException) {
                    return;
                }
                final StuckPlayerException stuck = cause instanceof StuckPlayerException
                        ? (StuckPlayerException) cause : null;
                if (stuck == null || stuck.stuckType == StuckPlayerException.STUCK_PLAYING_NO_PROGRESS) {
                    if (recoverByForcingHevcForDolbyVision(error,
                            player != null ? player.getVideoFormat() : null)) {
                        return;
                    }
                    if (recoverByDisablingTunneling()) {
                        return;
                    }
                    // Rejoining a broadcast is cheap and undoes itself, so it goes before revoking a mime,
                    // which is permanent and device-wide: a source that froze while a bitstream track
                    // happened to be playing would otherwise cost the user passthrough for that codec for
                    // good. A device that really cannot bitstream still gets cured — by the same rung on
                    // any non-live item, or by the AudioTrack failure below.
                    if (recoverLiveStall(error, stuck)) {
                        return;
                    }
                    // Passthrough audio that opened but never drained. Blame whichever mime was playing,
                    // but only while the sink actually is bitstreaming: a track being decoded to PCM (AAC,
                    // MP3) cannot be wedged by passthrough, and revoking its mime would persist a
                    // workaround that denies nothing and, for "device decoders only", pull the ffmpeg
                    // renderer in for good.
                    final Format stalledAudioFormat = player != null ? player.getAudioFormat() : null;
                    if (audioSink != null && audioSink.isPassthrough()
                            && recoverByRevokingAudioMime(stalledAudioFormat != null ? stalledAudioFormat.sampleMimeType : null)) {
                        return;
                    }
                }
                reportStall(error, stuck, null);
                // A broadcast that kept freezing gets one line rather than a full-screen decoder report:
                // what failed is the channel, not this clip. The report stays reachable behind "Details"
                // so support does not have to go to the crash reporter for it.
                if (player != null && player.isCurrentMediaItemLive()) {
                    stopWithMessage(getString(R.string.error_stream_interrupted), errorReport(error));
                    return;
                }
                showErrorScreen(errorSummary(error),
                        "Stall class: " + stallClass(stuck) + "\n\n" + errorReport(error));
                releasePlayer(false);
                return;
            }
            // The device claims Dolby/DTS passthrough support it doesn't actually have: AudioTrack
            // opening for that mime throws outright instead of just never draining. Same fix, reached
            // from the loud symptom instead of the silent one.
            if (error.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED
                    && recoverByRevokingAudioMime(audioTrackInitFailureMime(error))) {
                return;
            }
            // A bitstream's AudioTrack can also die under a player that was happily feeding it for an hour
            // (AudioTrack.write returns ERROR_DEAD_OBJECT: the audio server restarted, or the HDMI route to
            // the receiver dropped). Media3 calls that recoverable but spends its single attempt at the front
            // of the message queue — milliseconds after the output died, so it fails again and ends playback.
            // The same delayed re-read is what this needs: by then the route is usually back, and prepare()
            // keeps the item, position, surface and track selection. Not a reason to revoke the mime as the
            // branch above does — this device had been bitstreaming it fine until the output went away, and
            // if it comes back without passthrough the reselect falls to ffmpeg on its own.
            if ((isDecoderFailure(error) || isUnexpectedPlaybackError(error)
                    || error.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED)
                    && recoverFromDecoderFailure()) {
                return;
            }
            // Those re-reads handed the same stream back to the same decoder: for a failure that happens
            // while decoding rather than while opening the codec, Media3 never picks a different one. So if
            // it was the device's Dolby Vision decoder, decode the base layer instead of giving up. Only
            // that error code — a decoder-init failure was already offered the HEVC decoders as fallback
            // candidates, so those have failed too and rebuilding for them would be a pointless teardown.
            if (error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED
                    && error instanceof ExoPlaybackException
                    && recoverByForcingHevcForDolbyVision(error,
                            ((ExoPlaybackException) error).rendererFormat)) {
                return;
            }
            // The remembered clip can no longer be opened: a foreign app's one-off URI grant has
            // expired (a video streamed from a messenger, reopened after that app restarted) or the
            // file is gone. Expected external state, not an app bug — forget the clip so it stops
            // failing on every launch, fall back to the empty state, and report nothing.
            // Forgetting also breaks the loop where closing the error screen resumes the activity,
            // onStart re-prepares the same dead URI and the error screen comes straight back, which
            // reads as a window that cannot be dismissed. For an API session (persistentMode off)
            // updateMedia only drops the in-memory URI, so nothing remembered in prefs is lost.
            final int unavailable = mediaUnavailableMessage(error);
            if (unavailable != 0) {
                // A playlist keeps everything: the other episodes are still watchable, and the user may
                // want to step back to the one they were on (an accidental switch, say). So never tear the
                // view down and never walk the list on its own — that would march past every episode with
                // a message each time (a modal dialog each time on TV) and lose the user's place. Stay on
                // this episode and re-enable the arrows, which are gated while loading and would otherwise
                // only be cleared by STATE_READY or releasePlayer.
                if (player != null && player.getMediaItemCount() > 1) {
                    setEpisodeNavLoading(false);
                    showSnack(getString(unavailable), null);
                    return;
                }
                releasePlayer(false);
                // Forget the clip only when nothing entitles us to it any more: a one-off grant from
                // another app is gone for good, so retrying it on every launch is pointless. A URI we hold
                // a persisted grant for may just be unreachable right now (cloud provider offline, card
                // ejected), so keep it and let the next launch try again.
                if (!holdsPersistedGrant(mPrefs.mediaUri)) {
                    mPrefs.updateMedia(PlayerActivity.this, null, null);
                }
                // Before the snackbar: showEmptyState() brings the opaque overlay to the front, and
                // the Snackbar is only added to the CoordinatorLayout afterwards, so it stays on top.
                showEmptyStateWithoutMedia();
                showSnack(getString(unavailable), null);
                return;
            }
            // A single bad read from a streaming server is not the end of playback — re-read before
            // treating it as a failure. Nothing is reported yet: if the retry succeeds this was a server
            // hiccup, not an app problem.
            if (recoverFromSourceError(error)) {
                return;
            }
            // The stream still won't read after that retry: the server or the file is the problem, not
            // the app — an unfinished torrent piece, a malformed rip, a link that died mid-playback. One
            // line is all the user can act on, so no full-screen stack trace, and nothing to report: the
            // same bad host otherwise files a fresh issue on every attempt.
            if (isBrokenNetworkSource(error)) {
                // Prefer the status the server actually returned over the generic wording, so the user
                // (and support) sees the real cause.
                final HttpDataSource.InvalidResponseCodeException httpError = httpStatusFailure(error);
                final String message = httpError != null && httpError.dataSpec != null
                        ? getString(R.string.error_stream_http_status, httpError.responseCode,
                                Utils.uriToReportString(httpError.dataSpec.uri))
                        : getString(R.string.error_stream_broken);
                // A playlist keeps everything: the other episodes are still watchable and the user may
                // want to step back to this one, so stay here and re-enable the arrows (gated while loading).
                if (player != null && player.getMediaItemCount() > 1) {
                    showSnack(message, null);
                    setEpisodeNavLoading(false);
                    return;
                }
                stopWithMessage(message, null);
                return;
            }
            // Enrich via the per-capture ScopeCallback overload (not withScope) so the tags/extras land
            // on exactly this event.
            io.sentry.Sentry.captureException(error, scope -> enrichPlaybackScope(error, scope));
            if (error instanceof ExoPlaybackException) {
                final ExoPlaybackException exoPlaybackException = (ExoPlaybackException) error;
                if (exoPlaybackException.type == ExoPlaybackException.TYPE_SOURCE) {
                    // A source error is fatal — surface it (message + code) before releasing, since
                    // after teardown the deferred controller-visible path can no longer fire.
                    showError(exoPlaybackException);
                    releasePlayer(false);
                    return;
                }
                if (controllerVisible && controllerVisibleFully) {
                    showError(exoPlaybackException);
                } else {
                    errorToShow = exoPlaybackException;
                }
            } else {
                // Any other playback error — surface a general message + code instead of failing
                // silently (it was already reported to Sentry above).
                showErrorScreen(errorSummary(error), errorReport(error));
            }
        }
    }

    // Re-prepare the current network item as the streaming manifest type discovered from its HTTP
    // response (or HLS as the common fallback), so an extensionless resolver URL that actually
    // serves HLS/DASH plays instead of dying on a progressive-parse error. Rebuilding the whole
    // timeline forces DefaultMediaSourceFactory to re-instantiate the source with the new type
    // (buildUpon()/replace won't, since the URI is unchanged). Guarded so a genuinely unsupported
    // stream fails once rather than looping.
    private boolean recoverFromContainerError() {
        if (player == null) {
            return false;
        }
        final int index = player.getCurrentMediaItemIndex();
        final int count = player.getMediaItemCount();
        if (index < 0 || index >= count) {
            return false;
        }
        final MediaItem currentItem = player.getMediaItemAt(index);
        if (currentItem.localConfiguration == null) {
            return false;
        }
        final Uri uri = currentItem.localConfiguration.uri;
        if (!Utils.isSupportedNetworkUri(uri)) {
            return false;
        }
        String targetMime = resolvedMediaTypes.get(uri.toString());
        if (targetMime == null) {
            targetMime = MimeTypes.APPLICATION_M3U8;
        }
        if (targetMime.equals(currentItem.localConfiguration.mimeType)) {
            return false;
        }

        final long position = player.getCurrentPosition();
        final List<MediaItem> items = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            final MediaItem item = player.getMediaItemAt(i);
            items.add(i == index ? item.buildUpon().setMimeType(targetMime).build() : item);
        }
        player.setMediaItems(items, index, position);
        player.prepare();
        // And nothing is started here. Re-preparing keeps playWhenReady, so a player that was running
        // carries on by itself, and one that had not started yet still has its pending start to spend at
        // STATE_READY — where whether it may run is decided properly. Starting playback outright made this
        // recovery override every reason not to: the pause of a room holding for somebody who has just
        // joined, and the viewer's own pause during a load.
        return true;
    }

    // Re-read after a source error on network media. A streaming server can hand back bytes that are not
    // the media at all — a torrent piece that has not arrived yet, a hole padded with zeros — and the
    // extractor then dies on the first malformed element ("No valid varint length mask found" from
    // MatroskaExtractor). Media3 never retries these itself: DefaultLoadErrorHandlingPolicy gives
    // Loader.UnexpectedLoaderException and ParserException no retry delay, so one bad read ends playback
    // outright. The same tolerance covers any HTTP status the server hands back (a torrent/proxy backend
    // can answer 416 for a byte range it simply hasn't fetched yet) — a jitter, not a verdict, so it gets
    // the same retry budget as a malformed read instead of ending playback on the first bad response.
    //
    // Recovery stays on the same player instance: after an error it is merely STATE_IDLE, and prepare()
    // re-reads the source keeping the media item, the position, the surface and the track selection — the
    // user sees the spinner for a moment, not a rebuilt player and no surface detach. The re-read is delayed a
    // second so the server has time to actually fetch the missing bytes, and the budget is MAX_SOURCE_RETRIES
    // per player build: a stream that keeps failing gives up with a message instead of re-preparing forever.
    private boolean recoverFromSourceError(PlaybackException error) {
        if (player == null || !isBrokenNetworkSource(error)) {
            return false;
        }
        if (sourceRetries >= MAX_SOURCE_RETRIES) {
            return false;
        }
        // The position is gone from the window, so re-preparing there would only reproduce the error until
        // the budget is spent. Rejoin the window instead. Only for this code: isCurrentMediaItemLive() is
        // true for any HLS without #EXT-X-ENDLIST, which includes proxy/torrent remuxes of ordinary films —
        // seeking those on a plain read error would throw the viewer's place away.
        if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW
                && player.isCurrentMediaItemLive()) {
            player.seekToDefaultPosition();
        }
        sourceRetries++;
        updateLoading(true);
        playerView.postDelayed(sourceRetryRunnable, 1_000);
        return true;
    }

    // Rebuild the player forcing a Dolby Vision track through the plain HEVC decoder, bypassing a device
    // DV decoder that either wedged (ERROR_CODE_TIMEOUT) or failed outright mid-render
    // (ERROR_CODE_DECODING_FAILED). The failing format is passed in rather than read from the player: on
    // the loud path ExoPlayer has already disabled its renderers by the time this runs, which nulls
    // getVideoFormat(). The renderers factory / codec selector can only be set at construction, so a full
    // rebuild is required; the position is preserved via savePlayer(). The flag is its own guard — it
    // outlives a media-item change, so a stream that fails again after the switch (or the next episode
    // failing the same way) fails once instead of rebuilding in a loop. Returns true when a recovery
    // rebuild was scheduled.
    private boolean recoverByForcingHevcForDolbyVision(PlaybackException error, Format failingFormat) {
        if (player == null || forceHevcForDolbyVision || failingFormat == null) {
            return false;
        }
        // Media3's own verdict on whether the base layer can stand in for the whole track: HEVC for DV
        // profiles 4, 7 and 8, other codec families (or nothing at all) for the rest, which this rung does
        // not offer, and nothing when the container gave no parsable codec string. Profile 7 needs the
        // flag, which is passed regardless of the "Dolby Vision profile 7" setting: that setting governs
        // remapping a decoder that works, while this runs after one has already failed, and a base-layer
        // picture beats an error screen.
        if (!MimeTypes.VIDEO_DOLBY_VISION.equals(failingFormat.sampleMimeType)
                || !MimeTypes.VIDEO_H265.equals(MediaCodecUtil.getAlternativeCodecMimeType(
                        failingFormat, /* mapDv7ToHevc= */ true))) {
            return false;
        }
        // Once per process, as information rather than an error: a device silently dropping to HDR10 would
        // otherwise look like the decoder failure had simply stopped happening. Same reason reportStall
        // carries recoveredAs. Before the flag is set, so the report says what the player was doing when it
        // failed rather than what it is about to be rebuilt as. The codecs string comes from the exception
        // because the player has already dropped its video format by now, so enrichPlaybackScope cannot
        // see which Dolby Vision profile this was.
        if (!dolbyVisionFallbackReported) {
            dolbyVisionFallbackReported = true;
            io.sentry.Sentry.captureException(error, scope -> {
                scope.setFingerprint(Collections.singletonList("dv-hevc-fallback"));
                scope.setLevel(io.sentry.SentryLevel.INFO);
                enrichPlaybackScope(error, scope);
                scope.setTag("media.video_codecs", String.valueOf(failingFormat.codecs));
            });
        }
        forceHevcForDolbyVision = true;
        pendingStuckRecovery = true;
        // Only resume if it was playing: a decode failure can also reach a paused clip (a codec reclaimed
        // while the picture stood still), and the rebuild must not start playing on its own. playWhenReady
        // survives the error.
        restorePlayState = player.getPlayWhenReady();
        // Rebuild on the next loop, after this onPlayerError callback returns, so the player is not
        // released while its own listener is executing.
        playerView.post(() -> {
            releasePlayer();
            initializePlayer();
        });
        return true;
    }

    // A live channel stopped advancing. An error screen is the wrong answer for a broadcast: re-preparing
    // rebuilds the decoder at the same timecode, which is all a frozen render path needs, and playWhenReady
    // survives the error so playback simply resumes. No seek — the buffer and the loader were both healthy
    // when the clock froze, and seeking would cost a DVR viewer their place.
    //
    // The budget only bites on a channel that keeps freezing: it decays LIVE_STALL_FORGET_MS after the last
    // stall, so hours-apart blips each get a fresh one while a stall every few seconds runs out and gets a
    // message instead. Should the freeze be in the playback thread rather than the render path,
    // prepare() masks the state to BUFFERING, which re-arms the 30 s load watchdog as the backstop.
    //
    // Declines when the stream never actually started (stalledAtStart): re-preparing at a position that
    // was never reached changes nothing, and spending the budget on it only delays the message.
    private boolean recoverLiveStall(PlaybackException error, StuckPlayerException stuck) {
        if (player == null || !player.isCurrentMediaItemLive() || stalledAtStart()) {
            return false;
        }
        final long now = SystemClock.elapsedRealtime();
        if (now - lastLiveStallMs > LIVE_STALL_FORGET_MS) {
            liveStallRecoveries = 0;
        }
        lastLiveStallMs = now;
        if (liveStallRecoveries >= MAX_LIVE_STALL_RECOVERIES) {
            return false;
        }
        liveStallRecoveries++;
        // Once per process: enough to tell a working recovery apart from a channel that silently rejoins
        // every few seconds, without spending an event on every rejoin.
        if (!liveRejoinReported) {
            liveRejoinReported = true;
            reportStall(error, stuck, "live-rejoin");
        }
        updateLoading(true);
        playerView.post(sourceRetryRunnable);
        return true;
    }

    // Position measured from the start of the period rather than of the window, which is the only monotonic
    // reading on a live stream: getCurrentPosition() is relative to a window that slides, so on a live
    // channel it drifts backwards as segments age out even while playback runs normally. This is the same
    // quantity StuckPlayerDetector itself compares, so a stall verdict and this test agree by construction.
    private long currentPeriodPositionMs() {
        if (player == null) {
            return C.TIME_UNSET;
        }
        long position = player.getCurrentPosition();
        final Timeline timeline = player.getCurrentTimeline();
        if (!timeline.isEmpty() && player.getCurrentAdGroupIndex() == C.INDEX_UNSET) {
            position -= timeline.getPeriod(player.getCurrentPeriodIndex(), stallPeriod)
                    .getPositionInWindowMs();
        }
        return position;
    }

    // Whether the clock froze before playback had really begun. A tunneled or wedging decoder takes the
    // stream, renders the first frames and stops, so the position never moves away from where it started;
    // a stall after minutes of fine playback is a different failure and must not be blamed on the same
    // things. Used both to pick a recovery and to group the reports.
    private boolean stalledAtStart() {
        return player != null && (playerStartPositionMs == C.TIME_UNSET
                || currentPeriodPositionMs() - playerStartPositionMs < STALL_AT_START_MS);
    }

    // The device advertises tunneled playback and takes the stream through its tunneled decoder, then
    // never advances: the picture freezes on the first frames with no error of its own. Confirmed in the
    // field on a Realtek box, where playback froze at 2 ms of a live channel and 62 ms of a file, and
    // recovered as soon as tunneling was switched off by hand.
    //
    // Media3 makes this easy to hit: tunneling needs both the video and the audio renderer to claim
    // support, and DecoderAudioRenderer.supportsFormat claims it unconditionally, so preferring the
    // bundled ffmpeg decoder for audio still ends up driving a tunneled video codec from software-decoded
    // PCM — a pairing vendor stacks are not built for.
    //
    // So the setting is switched off rather than overridden: what Settings shows then matches what
    // playback does, and a user who needs tunneling can turn it back on. Writing false is also the whole
    // guard — the rung cannot fire twice. Only on a stall at the very start (see stalledAtStart): if the
    // stream played for a while, tunneling was evidently working and something else stopped it.
    private boolean recoverByDisablingTunneling() {
        // The baseline has to be known: this rung changes a setting the user made, so it must not act on a
        // player that was never ready. A frozen clock implies it was, but that is a library invariant.
        if (player == null || !mPrefs.tunneling
                || playerStartPositionMs == C.TIME_UNSET || !stalledAtStart()) {
            return false;
        }
        mPrefs.disableTunneling();
        Toast.makeText(this, R.string.notice_tunneling_disabled, Toast.LENGTH_LONG).show();
        restorePlayState = true;
        // Keeps the Dolby Vision workaround across this rebuild: without it a device that needs both fixes
        // would lose the first one here, and the DV rung would be free to fire again.
        pendingStuckRecovery = true;
        // Tunneling is applied to the track selector when the player is built, so it takes a rebuild.
        // Rebuild on the next loop, after this onPlayerError callback returns, so the player is not
        // released while its own listener is executing.
        playerView.post(() -> {
            releasePlayer();
            initializePlayer();
        });
        return true;
    }

    // Revoke audio passthrough for this specific mime, for good, persisted for future playbacks.
    // Reached from two symptoms of the same root cause — AudioTrack.Builder throwing at open, or a
    // StuckPlayerException where the AudioTrack opened but never drained — since there is no way to
    // ask a device in advance which mime will misbehave. Both symptoms arrive through onPlayerError,
    // so the player is always STATE_IDLE here: prepare() re-reads the source keeping the media item,
    // the position, the surface and the track selection (same as recoverFromSourceError), and the
    // already-installed sink now denies the mime, so the track falls back to decoding. No player
    // rebuild, so it does not read as a restart. One-way (until the user resets it in Settings),
    // guarded by mPrefs.revokedAudioMimes itself, so a repeat failure on the same mime after the
    // switch falls through to the normal error screen instead of looping.
    private boolean recoverByRevokingAudioMime(String mime) {
        if (mime == null || mPrefs.revokedAudioMimes.contains(mime) || player == null || audioSink == null) {
            return false;
        }
        mPrefs.revokeAudioMime(mime);
        audioSink.revoke(mime);
        if (mPrefs.decoderPriority == DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF) {
            // "Device decoders only", and this is the first revocation: the ffmpeg audio renderer the
            // mime may now need is absent, since buildAudioRenderers only forces it in once a revocation
            // exists. The one case that does need a rebuild — position is kept by savePlayer().
            restorePlayState = true;
            // Same reason as in recoverByDisablingTunneling(): keep the Dolby Vision workaround rather
            // than letting this rebuild clear it.
            pendingStuckRecovery = true;
            playerView.post(() -> {
                releasePlayer();
                initializePlayer();
            });
        } else {
            player.prepare();
        }
        return true;
    }

    // Recreates the AudioTrack behind a bitstream that was torn down (see audioRestartInFlight). Runs one
    // message after playback started, so the guards are real: the player may already be gone, and the user may
    // have gone on to scrub, in which case their own seek recreates the track anyway. alive drops a start that
    // is not on screen — the work would be spent on a session that is going away. Nothing here seeks, so
    // seekability and liveness do not matter — and with no audio track selected there is nothing to recreate.
    private void restartPassthroughAudio() {
        if (!alive || player == null || isScrubbing
                || audioSink == null || !audioSink.isPassthrough() || mPrefs.tunneling
                || !player.getCurrentTracks().isTypeSelected(C.TRACK_TYPE_AUDIO)) {
            return;
        }
        // Transient blockers, so wait rather than drop: a previous reselect has not reported back through
        // onTracksChanged yet, or playback stopped again between the trigger and this message. isPlaying, not
        // STATE_READY: the state stays READY while a transient audio-focus loss holds the renderers stopped, so
        // a request landing there would build the fresh track unstarted — the exact failure this whole thing
        // exists to prevent. Dropping the request instead of waiting is the other way to lose: the trigger is
        // spent and nothing retries.
        if (audioRestartInFlight || !player.isPlaying()) {
            if (audioRestartRetries < 5) {
                audioRestartRetries++;
                playerView.postDelayed(passthroughRestartRunnable, 100);
            }
            return;
        }
        // Cleared here rather than at the trigger: until the reselect is actually issued the request still
        // has to survive, or every guard above becomes a silent no-cure path. It is also what backs up the
        // retry budget above — a request that spends it all keeps the latch, and the next time playback starts
        // asks again.
        audioRestartPending = false;
        audioRestartInFlight = true;
        audioRestartSettling = true;
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true).build());
    }

    /** One restart request, with a fresh retry budget. */
    private void requestPassthroughRestart() {
        audioRestartRetries = 0;
        playerView.removeCallbacks(passthroughRestartRunnable);
        playerView.post(passthroughRestartRunnable);
    }

    // AudioSink.InitializationException.format is a public field in this build — no reflection needed.
    private static String audioTrackInitFailureMime(PlaybackException error) {
        if (error.errorCode != PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED) {
            return null;
        }
        for (Throwable cause = error.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof AudioSink.InitializationException) {
                Format format = ((AudioSink.InitializationException) cause).format;
                return format != null ? format.sampleMimeType : null;
            }
        }
        return null;
    }

    // A transient MediaCodec allocation race (common right after boot, or when another app just
    // released a codec) can succeed on a second attempt with no state change. prepare() re-reads
    // keeping the media item, position, surface and track selection (same as recoverFromSourceError).
    // Budget is MAX_DECODER_RETRIES per player build; a genuinely unsupported format is excluded below
    // since retrying it only delays a certain error.
    private static boolean isDecoderFailure(PlaybackException error) {
        switch (error.errorCode) {
            case PlaybackException.ERROR_CODE_DECODER_INIT_FAILED:
            case PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED:
            case PlaybackException.ERROR_CODE_DECODING_FAILED:
                return true;
            default:
                return false;
        }
    }

    // A RuntimeException that escaped on Media3's playback thread, reported as "Unexpected runtime
    // error". Always an internal player bug rather than anything wrong with the media: the one seen in
    // the field is the renderer losing the queued entry for its output format when a track reselection
    // seeks before the first frame has been drained, so the next output buffer finds no format at all
    // (https://github.com/androidx/media/issues/2965 — the same hole is still open in MediaCodecRenderer).
    // Nothing about the item, the position or the selection is at fault, so it gets the same bounded
    // re-prepare as a decoder race instead of ending playback; matched on the exception type, not on
    // ERROR_CODE_UNSPECIFIED, which is the catch-all code for far more than this.
    private static boolean isUnexpectedPlaybackError(PlaybackException error) {
        return error instanceof ExoPlaybackException
                && ((ExoPlaybackException) error).type == ExoPlaybackException.TYPE_UNEXPECTED;
    }

    private boolean recoverFromDecoderFailure() {
        if (player == null || decoderRetries >= MAX_DECODER_RETRIES) {
            return false;
        }
        decoderRetries++;
        updateLoading(true);
        playerView.postDelayed(decoderRetryRunnable, 1_200L * decoderRetries);
        return true;
    }

    // True when the just-failed load was a Lampac resolver handshake for the item that is playing now.
    private boolean isResolverNotReadyForCurrentItem() {
        if (resolverNotReadyUri == null || player == null) {
            return false;
        }
        final MediaItem item = player.getCurrentMediaItem();
        return item != null && item.localConfiguration != null
                && resolverNotReadyUri.equals(item.localConfiguration.uri.toString());
    }

    // Turns the device's own auto-rotate on so a system picker can be read the way the phone is held. This is
    // global state belonging to the whole device, so the promise to put it back has to be kept even if this
    // activity never gets another callback — hence the flag goes to disk as well as onto the instance.
    private void enableRotation() {
        try {
            if (Settings.System.getInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION) == 0) {
                Settings.System.putInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 1);
                restoreOrientationLock = true;
                mPrefs.setRestoreAutoRotate(true);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Puts back what enableRotation borrowed, from onActivityResult (the ordinary way back) and from onResume.
     * onResume is the one that closes the leak: being in front at all means nothing we launched still needs
     * the setting, which covers both a picker that hands back no result and a launch whose predecessor was
     * killed with the picker open — that process left the flag on disk and nothing else would have read it.
     * <p>
     * Deliberately not called from onStop: the picker covering us is itself what stops this activity, so
     * restoring there would switch auto-rotate off at the exact moment the picker needs it.
     */
    private void restoreRotationLock() {
        if (!restoreOrientationLock && !mPrefs.restoreAutoRotate) {
            return;
        }
        try {
            Settings.System.putInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0);
        } catch (Exception e) {
            e.printStackTrace();
        }
        restoreOrientationLock = false;
        mPrefs.setRestoreAutoRotate(false);
    }

    boolean useMediaStore() {
        final int targetSdkVersion = getApplicationContext().getApplicationInfo().targetSdkVersion;
        return (isTvBox && Build.VERSION.SDK_INT >= 30 && targetSdkVersion >= 30 && mPrefs.fileAccess.equals("auto")) || mPrefs.fileAccess.equals("mediastore");
    }

    void openFile(Uri pickerInitialUri) {
        if (useMediaStore()) {
            Intent intent = new Intent(this, MediaStoreChooserActivity.class);
            startActivityForResult(intent, REQUEST_CHOOSER_VIDEO_MEDIASTORE);
        } else if ((isTvBox && mPrefs.fileAccess.equals("auto")) || mPrefs.fileAccess.equals("legacy")) {
            Utils.alternativeChooser(this, pickerInitialUri, true);
        } else {
            enableRotation();

            if (pickerInitialUri == null || Utils.isSupportedNetworkUri(pickerInitialUri)) {
                pickerInitialUri = Utils.getMoviesFolderUri();
            }

            final Intent intent = createBaseFileIntent(Intent.ACTION_OPEN_DOCUMENT, pickerInitialUri);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("video/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, Utils.supportedMimeTypesVideo);

            if (Build.VERSION.SDK_INT < 30) {
                final ComponentName systemComponentName = Utils.getSystemComponent(this, intent);
                if (systemComponentName != null) {
                    intent.setComponent(systemComponentName);
                }
            }

            safelyStartActivityForResult(intent, REQUEST_CHOOSER_VIDEO);
        }
    }

    private void loadSubtitleFile(Uri pickerInitialUri) {
        Toast.makeText(PlayerActivity.this, R.string.open_subtitles, Toast.LENGTH_SHORT).show();
        final int targetSdkVersion = getApplicationContext().getApplicationInfo().targetSdkVersion;
        if ((isTvBox && Build.VERSION.SDK_INT >= 30 && targetSdkVersion >= 30 && mPrefs.fileAccess.equals("auto")) || mPrefs.fileAccess.equals("mediastore")) {
            Intent intent = new Intent(this, MediaStoreChooserActivity.class);
            intent.putExtra(MediaStoreChooserActivity.SUBTITLES, true);
            startActivityForResult(intent, REQUEST_CHOOSER_SUBTITLE_MEDIASTORE);
        } else if ((isTvBox && mPrefs.fileAccess.equals("auto")) || mPrefs.fileAccess.equals("legacy")) {
            Utils.alternativeChooser(this, pickerInitialUri, false);
        } else {
            enableRotation();

            final Intent intent = createBaseFileIntent(Intent.ACTION_OPEN_DOCUMENT, pickerInitialUri);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");

            final String[] supportedMimeTypes = {
                    MimeTypes.APPLICATION_SUBRIP,
                    MimeTypes.TEXT_SSA,
                    MimeTypes.TEXT_VTT,
                    MimeTypes.APPLICATION_TTML,
                    "text/*",
                    "application/octet-stream"
            };
            intent.putExtra(Intent.EXTRA_MIME_TYPES, supportedMimeTypes);

            if (Build.VERSION.SDK_INT < 30) {
                final ComponentName systemComponentName = Utils.getSystemComponent(this, intent);
                if (systemComponentName != null) {
                    intent.setComponent(systemComponentName);
                }
            }

            safelyStartActivityForResult(intent, REQUEST_CHOOSER_SUBTITLE);
        }
    }

    private void requestDirectoryAccess() {
        enableRotation();
        final Intent intent = createBaseFileIntent(Intent.ACTION_OPEN_DOCUMENT_TREE, Utils.getMoviesFolderUri());
        safelyStartActivityForResult(intent, REQUEST_CHOOSER_SCOPE_DIR);
    }

    private Intent createBaseFileIntent(final String action, final Uri initialUri) {
        final Intent intent = new Intent(action);

        // http://stackoverflow.com/a/31334967/1615876
        intent.putExtra("android.content.extra.SHOW_ADVANCED", true);

        if (Build.VERSION.SDK_INT >= 26 && initialUri != null) {
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri);
        }

        return intent;
    }

    void safelyStartActivityForResult(final Intent intent, final int code) {
        if (intent.resolveActivity(getPackageManager()) == null)
            showSnack(getText(R.string.error_files_missing).toString(), intent.toString());
        else
            startActivityForResult(intent, code);
    }

    private TrackGroup getTrackGroupFromFormatId(int trackType, String id) {
        // No id means nothing was recorded for that track type, so there is nothing to match. Not just a
        // shortcut: HLS leaves Format.id null on every rendition, so a null id would match the first
        // group of the type and force-select it — the first subtitle track switching itself on at start.
        if (id == null || player == null) {
            return null;
        }
        for (Tracks.Group group : player.getCurrentTracks().getGroups()) {
            if (group.getType() == trackType) {
                final TrackGroup trackGroup = group.getMediaTrackGroup();
                final Format format = trackGroup.getFormat(0);
                if (Objects.equals(id, format.id)) {
                    return trackGroup;
                }
            }
        }
        return null;
    }

    public void setSelectedTracks(final String subtitleId, final String audioId) {
        if ("#none".equals(subtitleId)) {
            if (trackSelector == null) {
                return;
            }
            trackSelector.setParameters(trackSelector.buildUponParameters().setDisabledTextTrackSelectionFlags(C.SELECTION_FLAG_DEFAULT | C.SELECTION_FLAG_FORCED));
        }

        TrackGroup subtitleGroup = getTrackGroupFromFormatId(C.TRACK_TYPE_TEXT, subtitleId);
        TrackGroup audioGroup = getTrackGroupFromFormatId(C.TRACK_TYPE_AUDIO, audioId);

        if (player == null) {
            return;
        }
        // setOverrideForType replaces only the overrides of that track type, so applying both keeps both —
        // a single shared override variable used to let the audio one drop the subtitle one on the floor.
        final List<Integer> tracks = Collections.singletonList(0);
        TrackSelectionParameters.Builder builder = player.getTrackSelectionParameters().buildUpon();
        if (subtitleGroup != null) {
            builder.setOverrideForType(new TrackSelectionOverride(subtitleGroup, tracks));
        }
        if (audioGroup != null) {
            builder.setOverrideForType(new TrackSelectionOverride(audioGroup, tracks));
        }
        player.setTrackSelectionParameters(builder.build());
    }

    private boolean hasOverrideType(final int trackType) {
        TrackSelectionParameters trackSelectionParameters = player.getTrackSelectionParameters();
        for (TrackSelectionOverride override : trackSelectionParameters.overrides.values()) {
            if (override.getType() == trackType)
                return true;
        }
        return false;
    }

    public String getSelectedTrack(final int trackType) {
        if (player == null) {
            return null;
        }
        Tracks tracks = player.getCurrentTracks();

        // Disabled (e.g. selected subtitle "None" - different than default)
        if (!tracks.isTypeSelected(trackType)) {
            return "#none";
        }

        // Audio track set to "Auto"
        if (trackType == C.TRACK_TYPE_AUDIO) {
            if (!hasOverrideType(C.TRACK_TYPE_AUDIO)) {
                return null;
            }
        }

        for (Tracks.Group group : tracks.getGroups()) {
            if (group.isSelected() && group.getType() == trackType) {
                Format format = group.getMediaTrackGroup().getFormat(0);
                return format.id;
            }
        }

        return null;
    }

    void setSubtitleTextSize() {
        setSubtitleTextSize(getResources().getConfiguration().orientation);
    }

    void setSubtitleTextSize(final int orientation) {
        // Tweak text size as fraction size doesn't work well in portrait
        final SubtitleView subtitleView = playerView.getSubtitleView();
        if (subtitleView != null) {
            final float size;
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                size = SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * subtitlesScale;
            } else {
                DisplayMetrics metrics = getResources().getDisplayMetrics();
                float ratio = ((float)metrics.heightPixels / (float)metrics.widthPixels);
                if (ratio < 1)
                    ratio = 1 / ratio;
                size = SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * subtitlesScale / ratio;
            }

            subtitleView.setFractionalTextSize(size);
        }
    }

    void updateSubtitleViewMargin() {
        if (player == null) {
            return;
        }

        updateSubtitleViewMargin(player.getVideoFormat());
    }

    // Set margins to fix PGS aspect as subtitle view is outside of content frame
    void updateSubtitleViewMargin(Format format) {
        if (format == null) {
            return;
        }

        final Rational aspectVideo = Utils.getRational(format);
        final DisplayMetrics metrics = getResources().getDisplayMetrics();
        final Rational aspectDisplay = new Rational(metrics.widthPixels, metrics.heightPixels);

        int marginHorizontal = 0;
        int marginVertical = 0;

        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            if (aspectDisplay.floatValue() > aspectVideo.floatValue()) {
                // Left & right bars
                int videoWidth = metrics.heightPixels / aspectVideo.getDenominator() * aspectVideo.getNumerator();
                marginHorizontal = (metrics.widthPixels - videoWidth) / 2;
            }
        }

        Utils.setViewParams(playerView.getSubtitleView(), 0, 0, 0, 0,
                marginHorizontal, marginVertical, marginHorizontal, marginVertical);
    }

    void setSubtitleTextSizePiP() {
        final SubtitleView subtitleView = playerView.getSubtitleView();
        if (subtitleView != null)
            subtitleView.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * 2);
    }

    @TargetApi(26)
    boolean updatePictureInPictureActions(final int iconId, final int resTitle, final int controlType, final int requestCode) {
        try {
            final ArrayList<RemoteAction> actions = new ArrayList<>();
            final PendingIntent intent = PendingIntent.getBroadcast(PlayerActivity.this, requestCode,
                    new Intent(ACTION_MEDIA_CONTROL).putExtra(EXTRA_CONTROL_TYPE, controlType), PendingIntent.FLAG_IMMUTABLE);
            final Icon icon = Icon.createWithResource(PlayerActivity.this, iconId);
            final String title = getString(resTitle);
            actions.add(new RemoteAction(icon, title, title, intent));
            ((PictureInPictureParams.Builder) mPictureInPictureParamsBuilder).setActions(actions);
            setPictureInPictureParams(((PictureInPictureParams.Builder) mPictureInPictureParamsBuilder).build());
            return true;
        } catch (IllegalStateException e) {
            // On Samsung devices with Talkback active:
            // Caused by: java.lang.IllegalStateException: setPictureInPictureParams: Device doesn't support picture-in-picture mode.
            e.printStackTrace();
        }
        return false;
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private boolean isInPip() {
        if (!Utils.isPiPSupported(this))
            return false;
        return isInPictureInPictureMode();
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (!isInPip()) {
            setSubtitleTextSize(newConfig.orientation);
        }
        updateSubtitleViewMargin();

        updateButtonRotation();

        // Recompute adaptive metrics on resize/fold/rotation (manifest opts out of recreate for these, so
        // playback isn't interrupted). Re-run the inset pass (grid/overscan) and drop any open picker — it was
        // sized for the old width/orientation and is rebuilt fresh on next open. Density/fontScale changes are
        // NOT in configChanges, so those recreate the activity and re-apply everything via onCreate.
        final UiMetrics next = UiMetrics.of(this, isTvBox);
        if (!next.sameClassAndWidth(ui)) {
            ui = next;
            dismissOpenPickers();
            if (controlView != null) {
                controlView.requestApplyInsets();
            }
        }
    }

    private void dismissOpenPickers() {
        final android.app.Dialog[] pickers = { qualityDialog, playlistDialog, skipOffsetDialog,
                subtitleOffsetDialog, sleepTimerDialog, menuDialog };
        for (final android.app.Dialog d : pickers) {
            if (d != null && d.isShowing()) {
                d.dismiss();
            }
        }
    }

    // Playback is over for this clip, but the page is not: the snackbar fades, so leave the reason on
    // screen, and hand the controller a null player so its play/seek cannot poke a released instance.
    // What stays usable is everything that never needed the player — the volume/brightness gestures, the
    // gear (and the settings screen behind it) and the playlist, if there is one to step through.
    private void stopWithMessage(final String text, final String details) {
        showSnack(text, details);
        releasePlayer(false);
        playerView.setPlayer(null);
        playerView.setCustomErrorMessage(text);
        playerView.setControllerShowTimeoutMs(-1);
        playerView.showController();
    }

    void showError(ExoPlaybackException error) {
        // A fatal playback error: go straight to the full error screen (friendly explanation + report),
        // rather than a dismissible toast the user has to expand.
        showErrorScreen(errorSummary(error), errorReport(error));
    }

    // Whether a SAF grant we took ourselves still covers this uri, i.e. it is ours to retry later.
    private boolean holdsPersistedGrant(final Uri uri) {
        if (uri == null) {
            return false;
        }
        for (final UriPermission permission : getContentResolver().getPersistedUriPermissions()) {
            if (permission.getUri().equals(uri)) {
                return true;
            }
        }
        return false;
    }

    // The message for a clip that can no longer be opened, or 0 when this is a different failure.
    // Matching on errorCode is not enough: a revoked grant arrives as ERROR_CODE_IO_UNSPECIFIED
    // because Loader wraps the SecurityException, so walk the cause chain. Network media is excluded:
    // HTTP failures are transient and belong on the normal error path.
    private int mediaUnavailableMessage(PlaybackException error) {
        if (Utils.isSupportedNetworkUri(currentMediaUri())) {
            return 0;
        }
        // A local file that stops short of its own container index: the extractor re-opens the source
        // near the end to read a trailing moov or the Matroska cues, and the read lands past the last
        // byte there is — a download that never finished, a truncated copy. The file's fault, not the
        // app's, and the same thing isBrokenNetworkSource already forgives on a remote stream. Matched
        // by code because Media3 raises ContentDataSourceException with no cause to walk.
        if (error.errorCode == PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE) {
            return R.string.error_playback_general;
        }
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof SecurityException) {
                return R.string.error_media_access_expired;
            }
            if (t instanceof FileNotFoundException) {
                return R.string.error_media_missing;
            }
        }
        return 0;
    }

    // Whether a failure belongs to the network media rather than to the app: everything the loader can
    // hit on a remote stream — a read that returns bytes which are not the media, a dead or refusing host,
    // a connection dropped mid-playback. These get one line and no report, and are worth re-reading.
    private boolean isBrokenNetworkSource(PlaybackException error) {
        if (!(error instanceof ExoPlaybackException)
                || ((ExoPlaybackException) error).type != ExoPlaybackException.TYPE_SOURCE
                || !Utils.isSupportedNetworkUri(currentMediaUri())) {
            return false;
        }
        switch (error.errorCode) {
            // Not the stream's fault, and re-reading changes nothing: the player has no support for what
            // it was handed, or we are configured to refuse the connection ourselves (cleartext blocked,
            // permission missing). Both say something about the app, so they keep the full report.
            case PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED:
            case PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED:
            case PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED:
            case PlaybackException.ERROR_CODE_IO_NO_PERMISSION:
                return false;
            default:
                return true;
        }
    }

    // The HTTP status the server actually returned, if that's what ended the broken-source retries
    // above — so the user (and support) sees the real cause instead of a generic message.
    private static HttpDataSource.InvalidResponseCodeException httpStatusFailure(PlaybackException error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof HttpDataSource.InvalidResponseCodeException) {
                return (HttpDataSource.InvalidResponseCodeException) t;
            }
        }
        return null;
    }

    // Short, human-facing text for the error screen's panel: the stable ExoPlayer error-code name, the
    // underlying error text, and the network URL that was being played (sanitised). No internal codes.
    private String errorSummary(PlaybackException error) {
        final StringBuilder sb = new StringBuilder(error.getErrorCodeName());
        final String message = ErrorActivity.rootMessage(error);
        if (message != null) {
            sb.append('\n').append(message);
        }
        final Uri uri = currentMediaUri();
        if (Utils.isSupportedNetworkUri(uri)) {
            sb.append("\n\n").append(Utils.uriToReportString(uri));
        }
        return sb.toString();
    }

    // Full diagnostic report — the same detail sent to Sentry: error-code name, the sanitised media
    // URI (for network media), and the complete stack trace with its "Caused by" chain.
    private String errorReport(PlaybackException error) {
        final StringBuilder sb = new StringBuilder("Error code: ").append(error.getErrorCodeName());
        final Uri uri = currentMediaUri();
        if (Utils.isSupportedNetworkUri(uri)) {
            sb.append("\nMedia: ").append(Utils.uriToReportString(uri));
        } else if (uri != null) {
            // Local media: the scheme only. A path or file name can identify the user's library, so it
            // never leaves the device — the formats below say everything a decoder bug needs anyway.
            sb.append("\nMedia: ").append(uri.getScheme()).append(" (local)");
        }
        appendPlayerState(sb);
        return sb.append("\n\n").append(ErrorActivity.stackTrace(error)).toString();
    }

    /**
     * Player-side state for the error report — the part no crash reporter can see: which formats were
     * actually selected, where playback had got to, and which decoder-affecting settings and recovery
     * flags were in effect. Absent when the player is already gone (a process crash reaches
     * ErrorActivity through its own handler, with no player to ask).
     */
    private void appendPlayerState(final StringBuilder sb) {
        if (player == null) {
            return;
        }
        int video = 0, audio = 0, text = 0;
        Format subtitle = null;
        for (Tracks.Group group : player.getCurrentTracks().getGroups()) {
            switch (group.getType()) {
                case C.TRACK_TYPE_VIDEO:
                    video++;
                    break;
                case C.TRACK_TYPE_AUDIO:
                    audio++;
                    break;
                case C.TRACK_TYPE_TEXT:
                    text++;
                    if (group.isSelected()) {
                        subtitle = group.getMediaTrackGroup().getFormat(0);
                    }
                    break;
            }
        }
        sb.append("\nVideo: ").append(Format.toLogString(player.getVideoFormat()));
        sb.append("\nAudio: ").append(Format.toLogString(player.getAudioFormat()));
        // Which decoders actually opened — the mime does not say whether this was the vendor's hardware
        // codec or the platform software one, and for a wedged decoder that is the whole question.
        if (videoDecoderName != null || audioDecoderName != null) {
            sb.append("\nDecoders: ").append(videoDecoderName != null ? videoDecoderName : "none")
                    .append(" / ").append(audioDecoderName != null ? audioDecoderName : "none");
        }
        sb.append("\nSubtitle: ").append(subtitle != null ? Format.toLogString(subtitle) : "none");
        sb.append("\nTracks: ").append(video).append(" video, ").append(audio).append(" audio, ")
                .append(text).append(" subtitle");
        final long duration = player.getDuration();
        sb.append("\nPosition: ").append(player.getCurrentPosition()).append('/')
                .append(duration == C.TIME_UNSET ? "unknown" : String.valueOf(duration))
                .append(" ms, buffered ").append(player.getBufferedPosition())
                .append(" ms, state ").append(player.getPlaybackState())
                .append(player.isPlaying() ? " (playing)" : " (paused)")
                .append(", item ").append(player.getCurrentMediaItemIndex() + 1)
                .append('/').append(player.getMediaItemCount());
        final long liveOffset = player.getCurrentLiveOffset();
        if (liveOffset != C.TIME_UNSET) {
            sb.append("\nLive: ").append(liveOffset).append(" ms behind the edge");
        }
        sb.append("\nPlayback: decoder priority ").append(mPrefs.decoderPriority)
                .append(", speed ").append(mPrefs.speed)
                .append(", resize ").append(mPrefs.resizeMode)
                .append(mPrefs.tunneling ? ", tunneling" : "")
                .append(mPrefs.frameRateMatching ? ", frame rate matching" : "")
                .append(mPrefs.mapDV7ToHevc ? ", map DV7" : "");
        if (forceHevcForDolbyVision) {
            sb.append("\nRecovery: forced HEVC for Dolby Vision");
        }
        final String dv7Status = dv7Converter != null ? dv7Converter.status() : null;
        if (dv7Status != null) {
            sb.append("\nDolby Vision profile 7: ").append(dv7Status);
        }
        if (!mPrefs.revokedAudioMimes.isEmpty()) {
            sb.append("\nAudio passthrough revoked: ").append(mPrefs.revokedAudioMimes);
        }
    }

    private Uri currentMediaUri() {
        final MediaItem item = player != null ? player.getCurrentMediaItem() : null;
        return item != null && item.localConfiguration != null
                ? item.localConfiguration.uri : mPrefs.mediaUri;
    }

    /**
     * Playback context for a report: the values worth grouping and filtering on as tags, and the same
     * detail the error screen shows as one extra — appendPlayerState is the single source for that text.
     */
    private void enrichPlaybackScope(final PlaybackException error, final io.sentry.IScope scope) {
        scope.setTag("player.error_code", error.getErrorCodeName());
        final Uri uri = currentMediaUri();
        if (Utils.isSupportedNetworkUri(uri)) {
            scope.setExtra("media_uri", Utils.uriToReportString(uri));
        }
        scope.setTag("decoder.priority", String.valueOf(mPrefs.decoderPriority));
        scope.setTag("player.tunneling", String.valueOf(mPrefs.tunneling));
        if (ffmpegAvailable != null) {
            scope.setTag("decoder.ffmpeg", String.valueOf(ffmpegAvailable));
        }
        if (player == null) {
            return;
        }
        final Format videoFormat = player.getVideoFormat();
        final Format audioFormat = player.getAudioFormat();
        if (videoFormat != null) {
            scope.setTag("media.video_mime", String.valueOf(videoFormat.sampleMimeType));
        }
        if (audioFormat != null) {
            scope.setTag("media.audio_mime", String.valueOf(audioFormat.sampleMimeType));
        }
        if (videoDecoderName != null) {
            scope.setTag("decoder.video_name", videoDecoderName);
        }
        // Worth a tag of its own rather than only the text below: whether the track went to a platform
        // codec or to the bundled ffmpeg one is what separates the audio-side failures from each other.
        if (audioDecoderName != null) {
            scope.setTag("decoder.audio_name", audioDecoderName);
        }
        scope.setTag("media.is_live", String.valueOf(player.isCurrentMediaItemLive()));
        if (audioSink != null) {
            scope.setTag("audio.sink_passthrough", String.valueOf(audioSink.isPassthrough()));
        }
        final StringBuilder state = new StringBuilder();
        appendPlayerState(state);
        scope.setExtra("player_state", state.toString());
    }

    /** What stalled — device_decoder means the playback clock froze, which is the only decoder verdict. */
    private static String stallClass(final StuckPlayerException stuck) {
        if (stuck == null) {
            return "device_decoder";
        }
        switch (stuck.stuckType) {
            case StuckPlayerException.STUCK_PLAYING_NO_PROGRESS:
                return "device_decoder";
            case StuckPlayerException.STUCK_PLAYING_NOT_ENDING:
                return "not_ending";
            case StuckPlayerException.STUCK_SUPPRESSED:
                return "suppressed";
            default:
                return "source_stalled";
        }
    }

    /**
     * Report a StuckPlayerDetector verdict, grouped by what actually stalled: ExoPlayerImpl reports five
     * unrelated failures under one error code, and a single issue holding all of them cannot be reasoned
     * about. The remaining detail stays in tags, which break down inside the issue.
     *
     * @param recoveredAs non-null when playback was rescued rather than surfaced, grouped separately so a
     *                    working recovery is visible instead of merely looking like the issue went away.
     */
    private void reportStall(final PlaybackException error, final StuckPlayerException stuck,
                             final String recoveredAs) {
        final String stallClass = stallClass(stuck);
        // "Froze at 2 ms" and "froze in the fortieth minute" are different failures with different
        // causes, so they must not share an issue.
        final String when = stalledAtStart() ? "at-start" : "mid-stream";
        io.sentry.Sentry.captureException(error, scope -> {
            scope.setFingerprint(Arrays.asList(
                    recoveredAs != null ? "stuck-recovered" : "stuck", stallClass, when));
            scope.setTag("player.stall_class", stallClass);
            scope.setTag("player.stall_when", when);
            scope.setTag("player.stuck_type",
                    stuck != null ? String.valueOf(stuck.stuckType) : "unknown");
            if (recoveredAs != null) {
                scope.setTag("player.stuck_recovery", recoveredAs);
                scope.setLevel(io.sentry.SentryLevel.INFO);
            }
            enrichPlaybackScope(error, scope);
        });
    }

    void showSnack(final String textPrimary, final String textSecondary) {
        // On TV the Snackbar action button is not reachable with the D-pad, so the "Details" affordance
        // would be lost. Present the error as an AlertDialog instead — its buttons are D-pad focusable.
        if (isTvBox) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(textPrimary);
            builder.setPositiveButton(android.R.string.ok, (dialogInterface, i) -> dialogInterface.dismiss());
            if (textSecondary != null) {
                builder.setNeutralButton(R.string.error_details, (dialogInterface, i) -> showErrorScreen(textSecondary, textSecondary));
            }
            builder.show();
            return;
        }
        snackbar = Snackbar.make(coordinatorLayout, textPrimary, Snackbar.LENGTH_LONG);
        if (textSecondary != null) {
            snackbar.setAction(R.string.error_details, v -> showErrorScreen(textSecondary, textSecondary));
        }
        snackbar.setAnchorView(R.id.exo_bottom_bar);
        snackbar.show();
    }

    private void showErrorScreen(final String summary, final String report) {
        // Every full-screen report from the player passes through here, so this is the one place that has
        // to keep the next resume from walking straight back into the failure (see the field's comment).
        skipMediaAfterFatalError = true;
        startActivity(new Intent(this, ErrorActivity.class)
                .putExtra(ErrorActivity.EXTRA_SUMMARY, summary)
                .putExtra(ErrorActivity.EXTRA_REPORT, report));
    }

    void reportScrubbing(long position) {
        final long diff = position - scrubbingStart;
        if (Math.abs(diff) > 1000) {
            scrubbingNoticeable = true;
        }
        if (scrubbingNoticeable) {
            playerView.clearIcon();
            playerView.setCustomErrorMessage(Utils.formatMilisSign(diff));
        }
        if (frameRendered) {
            frameRendered = false;
            if (player != null) {
                player.seekTo(position);
            }
        }
    }

    /**
     * The whole look comes from this app's own settings. The system captioning screen used to supply
     * it — and a language with it, which is why it is no longer read or offered anywhere.
     */
    void updateSubtitleStyle(final Context context) {
        final SubtitleView subtitleView = playerView.getSubtitleView();
        final boolean isTablet = Utils.isTablet(context);
        subtitlesScale = SubtitleUtils.normalizeFontScale(mPrefs.subtitleScale, isTvBox || isTablet);
        if (subtitleView != null) {
            // A window behind the text is a captioning concept nobody asks for, so it stays off. The
            // outline needs no knob either, it just has to contrast: black around every colour except
            // black text, which only reads against a light outline.
            final CaptionStyleCompat captionStyle = new CaptionStyleCompat(
                    mPrefs.subtitleTextColor,
                    mPrefs.subtitleBackgroundColor,
                    Color.TRANSPARENT,
                    mPrefs.subtitleEdgeType,
                    mPrefs.subtitleTextColor == Color.BLACK ? Color.WHITE : Color.BLACK,
                    Typeface.create(Typeface.DEFAULT,
                            mPrefs.subtitleStyleBold ? Typeface.BOLD : Typeface.NORMAL));
            subtitleView.setStyle(captionStyle);
            subtitleView.setApplyEmbeddedStyles(mPrefs.subtitleStyleEmbedded);
            subtitleView.setBottomPaddingFraction(SubtitleView.DEFAULT_BOTTOM_PADDING_FRACTION * 2f / 3f);
        }
        setSubtitleTextSize();
    }

    void searchSubtitles() {
        if (mPrefs.mediaUri == null)
            return;

        if (Utils.isSupportedNetworkUri(mPrefs.mediaUri) && Utils.isProgressiveContainerUri(mPrefs.mediaUri)) {
            SubtitleUtils.clearCache(this);
            if (SubtitleFinder.isUriCompatible(mPrefs.mediaUri)) {
                subtitleFinder = new SubtitleFinder(PlayerActivity.this, mPrefs.mediaUri);
                subtitleFinder.start();
            }
            return;
        }

        if (mPrefs.scopeUri != null || isTvBox) {
            DocumentFile video = null;
            File videoRaw = null;
            final String scheme = mPrefs.mediaUri.getScheme();

            if (mPrefs.scopeUri != null) {
                if ("com.android.externalstorage.documents".equals(mPrefs.mediaUri.getHost()) ||
                        "org.courville.nova.provider".equals(mPrefs.mediaUri.getHost())) {
                    // Fast search based on path in uri
                    video = SubtitleUtils.findUriInScope(this, mPrefs.scopeUri, mPrefs.mediaUri);
                } else {
                    // Slow search based on matching metadata, no path in uri
                    // Provider "com.android.providers.media.documents" when using "Videos" tab in file picker
                    DocumentFile fileScope = DocumentFile.fromTreeUri(this, mPrefs.scopeUri);
                    DocumentFile fileMedia = DocumentFile.fromSingleUri(this, mPrefs.mediaUri);
                    video = SubtitleUtils.findDocInScope(fileScope, fileMedia);
                }
            } else if (ContentResolver.SCHEME_FILE.equals(scheme)) {
                videoRaw = new File(mPrefs.mediaUri.getSchemeSpecificPart());
                video = DocumentFile.fromFile(videoRaw);
            }

            if (video != null) {
                DocumentFile subtitle = null;
                if (mPrefs.scopeUri != null) {
                    subtitle = SubtitleUtils.findSubtitle(video);
                } else if (ContentResolver.SCHEME_FILE.equals(scheme)) {
                    File parentRaw = videoRaw.getParentFile();
                    DocumentFile dir = DocumentFile.fromFile(parentRaw);
                    subtitle = SubtitleUtils.findSubtitle(video, dir);
                }

                if (subtitle != null) {
                    handleSubtitles(subtitle.getUri());
                }
            }
        }
    }

    Uri findNext() {
        // TODO: Unify with searchSubtitles()
        if (mPrefs.scopeUri != null || isTvBox) {
            DocumentFile video = null;
            File videoRaw = null;

            if (!isTvBox && mPrefs.scopeUri != null) {
                if ("com.android.externalstorage.documents".equals(mPrefs.mediaUri.getHost())) {
                    // Fast search based on path in uri
                    video = SubtitleUtils.findUriInScope(this, mPrefs.scopeUri, mPrefs.mediaUri);
                } else {
                    // Slow search based on matching metadata, no path in uri
                    // Provider "com.android.providers.media.documents" when using "Videos" tab in file picker
                    DocumentFile fileScope = DocumentFile.fromTreeUri(this, mPrefs.scopeUri);
                    DocumentFile fileMedia = DocumentFile.fromSingleUri(this, mPrefs.mediaUri);
                    video = SubtitleUtils.findDocInScope(fileScope, fileMedia);
                }
            } else if (isTvBox) {
                videoRaw = new File(mPrefs.mediaUri.getSchemeSpecificPart());
                video = DocumentFile.fromFile(videoRaw);
            }

            if (video != null) {
                DocumentFile next;
                if (!isTvBox) {
                    next = SubtitleUtils.findNext(video);
                } else {
                    File parentRaw = videoRaw.getParentFile();
                    DocumentFile dir = DocumentFile.fromFile(parentRaw);
                    next = SubtitleUtils.findNext(video, dir);
                }
                if (next != null) {
                    return next.getUri();
                }
            }
        }
        return null;
    }

    void askForScope(boolean loadSubtitlesOnCancel, boolean skipToNextOnCancel) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(PlayerActivity.this);
        builder.setMessage(String.format(getString(R.string.request_scope), getString(R.string.app_name)));
        builder.setPositiveButton(android.R.string.ok, (dialogInterface, i) -> requestDirectoryAccess()
        );
        builder.setNegativeButton(android.R.string.cancel, (dialog, which) -> {
            mPrefs.markScopeAsked();
            if (loadSubtitlesOnCancel) {
                loadSubtitleFile(mPrefs.mediaUri);
            }
            if (skipToNextOnCancel) {
                nextUri = findNext();
                if (nextUri != null) {
                    skipToNext();
                }
            }
        });
        final AlertDialog dialog = builder.create();
        dialog.show();
    }

    void resetHideCallbacks() {
        if (haveMedia && player != null && player.isPlaying()) {
            // Keep controller UI visible - alternative to resetHideCallbacks()
            playerView.setControllerShowTimeoutMs(PlayerActivity.CONTROLLER_TIMEOUT);
        }
    }

    // The rate lives exactly as long as the ring above it: when loading ends — for good or for the next
    // retry — both go away together.
    private void stopLoadingSpeed() {
        loadingSpeedScheduled = false;
        if (playerView != null) {
            playerView.removeCallbacks(loadingSpeedRunnable);
        }
        if (loadingSpeedView != null) {
            loadingSpeedView.setVisibility(View.GONE);
        }
    }

    private void updateLoading(final boolean enableLoading) {
        // Whatever decided the indicator's state now outranks a deferred STATE_BUFFERING show — including the
        // show itself, which posts this call and must not leave its own trigger armed.
        if (playerView != null) {
            playerView.removeCallbacks(showLoadingRunnable);
        }
        if (enableLoading) {
            // Read before hiding it: making a focused view invisible clears the focus. Only a remote that was
            // sitting on play/pause is handed to the ring — a viewer who walked the D-pad to a bottom-bar
            // button keeps their place when a mid-film rebuffer brings the ring back.
            final boolean heroHadFocus = exoPlayPause.hasFocus();
            // INVISIBLE (not GONE): keep the 90dp slot so the row doesn't resize while the spinner shows over it.
            exoPlayPause.setVisibility(View.INVISIBLE);
            loadingProgressBar.setVisibility(View.VISIBLE);
            if (heroHadFocus) {
                parkFocusOnLoadingRing();
            }
            // Network sources only: nothing flows through the media data source for a local file or a SAF
            // document, so the rate would read 0,0 MB/s exactly where it is meant to reassure.
            Uri loading = currentPlayingUri();
            if (loading == null) {
                loading = mPrefs.mediaUri;
            }
            if (Utils.isSupportedNetworkUri(loading)) {
                // Arm it once per wait: this runs again on every STATE_BUFFERING, and re-posting would
                // push the delay back past the wait itself.
                if (!loadingSpeedScheduled) {
                    loadingSpeedScheduled = true;
                    loadingSpeedBytes = TrackNameParsingDataSource.bytesRead.get();
                    playerView.postDelayed(loadingSpeedRunnable, LOADING_SPEED_DELAY_MS);
                }
            }
        } else {
            stopLoadingSpeed();
            // Again before hiding: the ring loses focus the moment it goes.
            final boolean ringHadFocus = loadingProgressBar.hasFocus();
            loadingProgressBar.setFocusable(false);
            loadingProgressBar.setVisibility(View.GONE);
            exoPlayPause.setVisibility(View.VISIBLE);
            if (focusPlay || ringHadFocus) {
                focusPlay = false;
                exoPlayPause.requestFocus();
            }
        }
    }

    // TV: play/pause goes INVISIBLE while the loading ring takes its slot, and a hidden view cannot hold focus.
    // Left to the framework, focus is re-homed to the first focusable view in the controls — the first icon of
    // the bottom row, because exo_bottom_bar is declared before exo_center_controls — so the remote sat on
    // "aspect ratio" / "quality" for the whole controller timeout, and OK there changed the picture instead of
    // pausing. The ring holds focus in play/pause's place instead: same slot, no highlight of its own (see the
    // default-focus-highlight call in onCreate), and updateLoading(false) hands it straight back. A no-op on
    // touch devices, where neither view is focusable in touch mode.
    private void parkFocusOnLoadingRing() {
        loadingProgressBar.setFocusable(true);
        loadingProgressBar.requestFocus();
    }

    // Shrink the built-in prev/next episode arrows (Media3 defaults render them as large as play/pause) and
    // take over their click handling so we can gate them while a video is loading. Media3 keeps updating the
    // buttons' enabled state on player events, so an OnClickListener guard — not setEnabled — is the reliable gate.
    private void setupEpisodeNavButtons() {
        // The prev/next episode arrows are a clear secondary tier below the coral Play/Pause hero: their 46dp
        // disc is ~0.66 of the hero's 70dp, so Play reads as primary rather than a near-peer. Both use the same
        // filled-white skip glyphs so they read as a symmetric pair (the default Media3 src drawables are
        // mismatched — only the "next" one carries a gradient halo).
        final int size = ui.episodeDisc();
        final int padding = ui.episodeDiscPad();
        final int margin = ui.episodeDiscMargin();
        if (exoPrev != null) {
            exoPrev.setImageResource(R.drawable.ic_skip_previous);
        }
        if (exoNext != null) {
            exoNext.setImageResource(R.drawable.ic_skip_next);
        }
        setupEpisodeNavButton(exoPrev, size, padding, margin);
        setupEpisodeNavButton(exoNext, size, padding, margin);
        // "Next file" and "delete" sit in the same secondary tier — "next file" even replaces the arrows
        // outside a playlist — so they get the same disc. Their layer-list sources carry Media3's gradient
        // halo and no disc, which made the single-video "next" read as a different button than the
        // playlist one it stands in for.
        final ImageButton nextFile = findViewById(R.id.next);
        if (nextFile != null) {
            nextFile.setImageResource(R.drawable.ic_skip_next);
        }
        final ImageButton deleteFile = findViewById(R.id.delete);
        if (deleteFile != null) {
            deleteFile.setImageResource(R.drawable.ic_delete_24dp_);
        }
        setupEpisodeNavButton(nextFile, size, padding, margin);
        setupEpisodeNavButton(deleteFile, size, padding, margin);
        if (exoPrev != null) {
            exoPrev.setOnClickListener(v -> {
                if (!episodeNavLoading && player != null && player.hasPreviousMediaItem()) {
                    if (player.getPlaybackState() == Player.STATE_IDLE) {
                        stepEpisodeWhileIdle(-1);
                    } else {
                        player.seekToPrevious();
                    }
                    resetHideCallbacks();
                }
            });
        }
        if (exoNext != null) {
            exoNext.setOnClickListener(v -> {
                if (!episodeNavLoading && player != null && player.hasNextMediaItem()) {
                    if (player.getPlaybackState() == Player.STATE_IDLE) {
                        stepEpisodeWhileIdle(1);
                    } else {
                        player.seekToNext();
                    }
                    resetHideCallbacks();
                }
            });
        }
        // Media3's PlayerControlView re-enables these arrows in updateNavigation()/updateAll() — on player
        // events AND every time the controller is shown — based on command availability, which keeps "prev"
        // enabled on the first item and "next" on the last. Enforcing our state via a posted re-assert lands
        // one frame late (visible enable→disable flicker on load). Correcting it in a pre-draw pass instead
        // fixes it before the frame is drawn, so Media3's transient enable is never rendered.
        if (playerView != null) {
            playerView.getViewTreeObserver().addOnPreDrawListener(() -> {
                updateEpisodeNavButtons();
                return true;
            });
        }
    }

    /**
     * Foreground for a round control disc: the press ripple, masked to the disc so it stays circular, plus
     * the D-pad focus state. Focus is a thin white ring orbiting just outside the disc, not a wash over it —
     * the theme's borderless ripple alone was a ~20% white scrim the brand disc swallowed, and filling the
     * disc to signal focus throws away the very colour the button exists to carry.
     *
     * @param discInset how far the disc itself is inset within the view (0 when the disc fills the view);
     *                  the ring is placed halfway into that gap so it reads as attached to the disc.
     */
    private Drawable discFocusForeground(final int discInset) {
        // The ring's own stroke carries the state: white on focus, transparent otherwise. A StateListDrawable
        // cannot express "nothing" — a null entry leaves the previously drawn state on screen.
        final GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setStroke(ui.dpS(3), new ColorStateList(
                new int[][]{{android.R.attr.state_focused}, {}},
                new int[]{Color.WHITE, Color.TRANSPARENT}));
        final GradientDrawable mask = new GradientDrawable();
        mask.setShape(GradientDrawable.OVAL);
        mask.setColor(Color.WHITE);
        return new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF),
                new InsetDrawable((Drawable) ring, discInset / 2),
                new InsetDrawable((Drawable) mask, discInset));
    }

    private void setupEpisodeNavButton(final ImageButton button, final int size, final int padding, final int margin) {
        if (button == null) {
            return;
        }
        final ViewGroup.LayoutParams lp = button.getLayoutParams();
        lp.width = size;
        lp.height = size;
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            ((ViewGroup.MarginLayoutParams) lp).setMarginStart(margin);
            ((ViewGroup.MarginLayoutParams) lp).setMarginEnd(margin);
        }
        button.setLayoutParams(lp);
        button.setPadding(padding, padding, padding, padding);
        button.setScaleType(ImageView.ScaleType.FIT_CENTER);
        button.setImageTintList(ColorStateList.valueOf(Color.WHITE));
        // Neutral chrome disc echoing the coral Play/Pause hero: same pill fill (@color/ui_controls_background),
        // circular to suit the round glyphs. The 12dp icon padding leaves a ring matching the hero's proportion.
        final GradientDrawable disc = new GradientDrawable();
        disc.setShape(GradientDrawable.OVAL);
        disc.setColor(ContextCompat.getColor(this, R.color.ui_controls_background));
        button.setBackground(disc);
        // Replacing the background drops the D-pad focus / touch-press highlight, so re-add both as a
        // foreground — critical for TV navigation, harmless on touch. The disc fills the whole view here, so
        // the focus ring lands on its own edge.
        button.setForeground(discFocusForeground(0));
    }

    // Grey out and disable the prev/next episode arrows while a video is loading, using the same disabled
    // styling (enabled state + opacity) as the other control buttons via Utils.setButtonEnabled.
    private void setEpisodeNavLoading(final boolean loading) {
        episodeNavLoading = loading;
        updateEpisodeNavButtons();
    }

    // Picking an episode after a fatal error has to reload it: the player is left idle with its timeline
    // intact, so a seek alone would move the index without ever loading anything.
    private void prepareIfIdle() {
        if (player != null && player.getPlaybackState() == Player.STATE_IDLE) {
            player.prepare();
        }
    }

    // Stepping episodes out of that idle state: seekToPrevious would restart the failed item once past its
    // first seconds, and under the repeat-one toggle seekToNext/Previous target the current item as well.
    // Move by index instead so the arrows really leave a broken episode, then reload.
    private void stepEpisodeWhileIdle(final int delta) {
        final int target = player.getCurrentMediaItemIndex() + delta;
        if (target >= 0 && target < player.getMediaItemCount()) {
            player.seekToDefaultPosition(target);
            player.prepare();
        }
    }

    // Enable each episode arrow only when that direction exists in the playlist: "prev" is disabled on the
    // first item, "next" on the last. Both are additionally disabled while a switch is loading. Idempotent
    // (only writes on an actual change) so the per-draw enforcer below can call it every frame cheaply.
    private void updateEpisodeNavButtons() {
        final boolean canPrev = !episodeNavLoading && player != null && player.hasPreviousMediaItem();
        final boolean canNext = !episodeNavLoading && player != null && player.hasNextMediaItem();
        if (exoPrev != null && exoPrev.isEnabled() != canPrev) {
            Utils.setButtonEnabled(this, exoPrev, canPrev);
        }
        if (exoNext != null && exoNext.isEnabled() != canNext) {
            Utils.setButtonEnabled(this, exoNext, canNext);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    protected void onUserLeaveHint() {
        if (mPrefs!= null && mPrefs.autoPiP && player != null && player.isPlaying() && Utils.isPiPSupported(this))
            enterPiP();
        else
            super.onUserLeaveHint();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void enterPiP() {
        final AppOpsManager appOpsManager = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        if (AppOpsManager.MODE_ALLOWED != appOpsManager.checkOpNoThrow(AppOpsManager.OPSTR_PICTURE_IN_PICTURE, android.os.Process.myUid(), getPackageName())) {
            final Intent intent = new Intent("android.settings.PICTURE_IN_PICTURE_SETTINGS", Uri.fromParts("package", getPackageName(), null));
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
            }
            return;
        }

        if (player == null) {
            return;
        }

        playerView.setControllerAutoShow(false);
        playerView.hideController();

        final Format format = player.getVideoFormat();

        if (format != null) {
            // https://github.com/google/ExoPlayer/issues/8611
            // TODO: Test/disable on Android 11+
            final View videoSurfaceView = playerView.getVideoSurfaceView();
            if (videoSurfaceView instanceof SurfaceView) {
                ((SurfaceView)videoSurfaceView).getHolder().setFixedSize(format.width, format.height);
            }

            Rational rational = Utils.getRational(format);
            if (Build.VERSION.SDK_INT >= 33 &&
                    getPackageManager().hasSystemFeature(FEATURE_EXPANDED_PICTURE_IN_PICTURE) &&
                    (rational.floatValue() > rationalLimitWide.floatValue() || rational.floatValue() < rationalLimitTall.floatValue())) {
                ((PictureInPictureParams.Builder)mPictureInPictureParamsBuilder).setExpandedAspectRatio(rational);
            }
            if (rational.floatValue() > rationalLimitWide.floatValue())
                rational = rationalLimitWide;
            else if (rational.floatValue() < rationalLimitTall.floatValue())
                rational = rationalLimitTall;

            ((PictureInPictureParams.Builder)mPictureInPictureParamsBuilder).setAspectRatio(rational);
        }
        enterPictureInPictureMode(((PictureInPictureParams.Builder)mPictureInPictureParamsBuilder).build());
    }

    void setEndControlsVisible(boolean visible) {
        final boolean hasPlaylist = player != null && player.getMediaItemCount() > 1;
        final int deleteVisible = (visible && haveMedia && Utils.isDeletable(this, mPrefs.mediaUri)) ? View.VISIBLE : View.INVISIBLE;
        final int nextVisible = (visible && haveMedia && !hasPlaylist && (nextUri != null || (mPrefs.askScope && !isTvBox))) ? View.VISIBLE : View.INVISIBLE;
        findViewById(R.id.delete).setVisibility(deleteVisible);
        findViewById(R.id.next).setVisibility(nextVisible);
    }

    void askDeleteMedia() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(PlayerActivity.this);
        builder.setMessage(getString(R.string.delete_query));
        builder.setPositiveButton(R.string.delete_confirmation, (dialogInterface, i) -> {
            releasePlayer();
            deleteMedia();
            if (nextUri == null) {
                showEmptyStateWithoutMedia();
            } else {
                skipToNext();
            }
        });
        builder.setNegativeButton(android.R.string.cancel, (dialog, which) -> {});
        final AlertDialog dialog = builder.create();
        dialog.show();
    }

    // Nothing left to play: drop the end controls and hand the screen over to the empty state.
    private void showEmptyStateWithoutMedia() {
        haveMedia = false;
        setEndControlsVisible(false);
        playerView.setControllerShowTimeoutMs(-1);
        emptyState.show();
    }

    void deleteMedia() {
        try {
            if (ContentResolver.SCHEME_CONTENT.equals(mPrefs.mediaUri.getScheme())) {
                DocumentsContract.deleteDocument(getContentResolver(), mPrefs.mediaUri);
            } else if (ContentResolver.SCHEME_FILE.equals(mPrefs.mediaUri.getScheme())) {
                final File file = new File(mPrefs.mediaUri.getSchemeSpecificPart());
                if (file.canWrite()) {
                    file.delete();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void dispatchPlayPause() {
        if (player == null)
            return;

        @Player.State int state = player.getPlaybackState();
        if (state == Player.STATE_IDLE || state == Player.STATE_ENDED || !player.getPlayWhenReady()) {
            shortControllerTimeout = true;
            androidx.media3.common.util.Util.handlePlayButtonAction(player);
        } else {
            androidx.media3.common.util.Util.handlePauseButtonAction(player);
        }
    }

    void skipToNext() {
        if (nextUri != null) {
            releasePlayer();
            mPrefs.updateMedia(this, nextUri, null);
            searchSubtitles();
            initializePlayer();
        }
    }

    void notifyAudioSessionUpdate(final boolean active) {
        final Intent intent = new Intent(active ? AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION
                : AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION);
        intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, player.getAudioSessionId());
        intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, getPackageName());
        if (active) {
            intent.putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MOVIE);
        }
        try {
            sendBroadcast(intent);
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    void updateButtons(final boolean enable) {
        if (buttonPiP != null) {
            Utils.setButtonEnabled(this, buttonPiP, enable);
        }
        Utils.setButtonEnabled(this, buttonAspectRatio, enable);
        // The gear stays reachable with no player: its menu drops the player-dependent rows by itself
        // (see showMoreMenu) and keeps "Open" and the settings screen — the way out of a failed clip.
        Utils.setButtonEnabled(this, exoSettings, true);
    }

    private void scaleStart() {
        isScaling = true;
        if (playerView.getResizeMode() != AspectRatioFrameLayout.RESIZE_MODE_ZOOM) {
            playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM);
        }
        scaleFactor = playerView.getVideoSurfaceView().getScaleX();
        playerView.removeCallbacks(playerView.textClearRunnable);
        playerView.clearIcon();
        playerView.setCustomErrorMessage((int)(scaleFactor * 100) + "%");
        playerView.hideController();
        isScaleStarting = true;
    }

    private void scale(boolean up) {
        if (up) {
            scaleFactor += 0.01;
        } else {
            scaleFactor -= 0.01;
        }
        scaleFactor = Utils.normalizeScaleFactor(scaleFactor, playerView.getScaleFit());
        playerView.setScale(scaleFactor);
        playerView.setCustomErrorMessage((int)(scaleFactor * 100) + "%");
    }

    private void scaleEnd() {
        isScaling = false;
        playerView.postDelayed(playerView.textClearRunnable, 200);
        if (player != null && !player.isPlaying()) {
            playerView.showController();
        }
        if (Math.abs(playerView.getScaleFit() - scaleFactor) < 0.01 / 2) {
            playerView.setScale(1.f);
            playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        }
        updatebuttonAspectRatioIcon();
    }

    // A scale mode = a Media3 resize mode plus an optional forced display aspect ratio (ratio 0 = natural).
    // Indices 0..2 (Fit/Crop/Fill) are the tap cycle; the rest (forced ratios) are picker-only, like VLC.
    private static final class AspectMode {
        final int resizeMode;
        final float ratio;
        final String label;
        AspectMode(int resizeMode, float ratio, String label) {
            this.resizeMode = resizeMode;
            this.ratio = ratio;
            this.label = label;
        }
    }

    private List<AspectMode> getAspectModes() {
        if (aspectModes == null) {
            aspectModes = new ArrayList<>();
            aspectModes.add(new AspectMode(AspectRatioFrameLayout.RESIZE_MODE_FIT, 0f, getString(R.string.video_resize_fit)));
            aspectModes.add(new AspectMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, 0f, getString(R.string.video_resize_crop)));
            aspectModes.add(new AspectMode(AspectRatioFrameLayout.RESIZE_MODE_FILL, 0f, getString(R.string.video_resize_fill)));
            aspectModes.add(new AspectMode(AspectRatioFrameLayout.RESIZE_MODE_FIT, 16f / 9f, "16:9"));
            aspectModes.add(new AspectMode(AspectRatioFrameLayout.RESIZE_MODE_FIT, 4f / 3f, "4:3"));
            aspectModes.add(new AspectMode(AspectRatioFrameLayout.RESIZE_MODE_FIT, 16f / 10f, "16:10"));
            aspectModes.add(new AspectMode(AspectRatioFrameLayout.RESIZE_MODE_FIT, 2f / 1f, "2:1"));
            aspectModes.add(new AspectMode(AspectRatioFrameLayout.RESIZE_MODE_FIT, 2.35f, "2.35:1"));
            aspectModes.add(new AspectMode(AspectRatioFrameLayout.RESIZE_MODE_FIT, 2.39f, "2.39:1"));
            aspectModes.add(new AspectMode(AspectRatioFrameLayout.RESIZE_MODE_FIT, 5f / 4f, "5:4"));
        }
        return aspectModes;
    }

    private void applyAspectMode(int index) {
        final AspectMode mode = getAspectModes().get(index);
        currentAspectRatio = mode.ratio;
        playerView.applyAspectMode(mode.resizeMode, mode.ratio);
        Utils.showText(playerView, mode.label);
        updatebuttonAspectRatioIcon();
    }

    // Tap: cycle Fit → Crop → Fill → 16:9 → 4:3. From any other picker-only ratio, a tap returns to Fit.
    private static final int ASPECT_CYCLE_COUNT = 5;

    private void cycleAspectMode() {
        final List<AspectMode> modes = getAspectModes();
        int current = -1;
        for (int i = 0; i < ASPECT_CYCLE_COUNT; i++) {
            if (isCurrentAspectMode(modes.get(i))) {
                current = i;
                break;
            }
        }
        applyAspectMode((current + 1) % ASPECT_CYCLE_COUNT);
    }

    private void showAspectModePicker() {
        final List<AspectMode> modes = getAspectModes();
        final String[] labels = new String[modes.size()];
        int checked = -1;
        for (int i = 0; i < modes.size(); i++) {
            labels[i] = modes.get(i).label;
            if (isCurrentAspectMode(modes.get(i)))
                checked = i;
        }
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.button_crop)
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    applyAspectMode(which);
                    d.dismiss();
                })
                .create();
        showPickerDialog(dialog);
    }

    private boolean isCurrentAspectMode(AspectMode mode) {
        if (mode.ratio > 0)
            return Math.abs(mode.ratio - currentAspectRatio) < 0.001f;
        return currentAspectRatio == 0 && playerView.getResizeMode() == mode.resizeMode;
    }

    // The arrows sit on the side the picture is travelling: after the rate going forward, before it
    // going back.
    public void setSpeedBoostIndicator(float speed, boolean rewind) {
        if (speedBoostIndicator == null)
            return;
        speedBoostIndicator.setText(String.format(Locale.US, "%.1f×", speed));
        speedBoostIndicator.setCompoundDrawablesRelative(rewind ? speedBoostIconRewind : null, null,
                rewind ? null : speedBoostIconForward, null);
        setSpeedBoostIndicatorVisible(true);
    }

    public void setSpeedBoostIndicatorVisible(boolean visible) {
        if (speedBoostIndicator != null)
            speedBoostIndicator.setVisibility(visible && !inPip ? View.VISIBLE : View.GONE);
    }

    // Arms the pause auto-hide when the controller is fully visible and playback is paused (ready, not
    // scrubbing/locked/in a picker). Called from the visibility listener and on play/pause transitions.
    private void scheduleHideControllerOnPause() {
        if (playerView == null)
            return;
        playerView.removeCallbacks(hideControllerAction);
        if (controllerVisibleFully && haveMedia && player != null
                && player.getPlaybackState() == Player.STATE_READY && !player.getPlayWhenReady()
                && !locked && !pickerDialogOpen && !isScrubbing) {
            playerView.postDelayed(hideControllerAction, CONTROLLER_TIMEOUT);
        }
    }

    private void updatebuttonAspectRatioIcon() {
        if (playerView.getResizeMode() == AspectRatioFrameLayout.RESIZE_MODE_ZOOM) {
            buttonAspectRatio.setImageResource(R.drawable.ic_fit_screen_24dp);
        } else {
            buttonAspectRatio.setImageResource(R.drawable.ic_aspect_ratio_24dp);
        }
    }

    private void updateButtonRotation() {
        boolean portrait = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
        boolean auto = false;
        try {
            auto = Settings.System.getInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION) == 1;
        } catch (Settings.SettingNotFoundException e) {
            e.printStackTrace();
        }

        if (mPrefs.orientation == Utils.Orientation.VIDEO) {
            if (auto) {
                buttonRotation.setImageResource(R.drawable.ic_screen_lock_rotation_24dp);
            } else if (portrait) {
                buttonRotation.setImageResource(R.drawable.ic_screen_lock_portrait_24dp);
            } else {
                buttonRotation.setImageResource(R.drawable.ic_screen_lock_landscape_24dp);
            }
        } else {
            if (auto) {
                buttonRotation.setImageResource(R.drawable.ic_screen_rotation_24dp);
            } else if (portrait) {
                buttonRotation.setImageResource(R.drawable.ic_screen_portrait_24dp);
            } else {
                buttonRotation.setImageResource(R.drawable.ic_screen_landscape_24dp);
            }
        }
    }
}
