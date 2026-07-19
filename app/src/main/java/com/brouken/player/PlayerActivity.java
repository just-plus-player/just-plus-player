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
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.UriPermission;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.content.res.ColorStateList;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
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
import android.os.Parcelable;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Rational;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.accessibility.CaptioningManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlaybackException;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.RenderersFactory;
import androidx.media3.exoplayer.SeekParameters;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.extractor.DefaultExtractorsFactory;
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
import com.brouken.player.skip.SkipManager;
import com.brouken.player.skip.SkipSegment;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.getkeepsafe.taptargetview.TapTarget;
import com.getkeepsafe.taptargetview.TapTargetView;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class PlayerActivity extends Activity {

    private PlayerListener playerListener;
    private BroadcastReceiver mReceiver;
    private AudioManager mAudioManager;
    private MediaSession mediaSession;
    private DefaultTrackSelector trackSelector;
    public static LoudnessEnhancer loudnessEnhancer;

    private CustomDefaultTrackNameProvider trackNameProvider;
    // Track names read from the container (MP4 udta/name, MKV TrackEntry/Name), and the resolved
    // Format.id -> name map that the track list and header read from once tracks are known.
    private final java.util.List<TrackMetadata> containerTracks = new java.util.ArrayList<>();
    private final java.util.Map<String, String> resolvedTrackNames = new java.util.HashMap<>();
    private final TrackNameParsingDataSource.Listener trackNameListener = new TrackNameParsingDataSource.Listener() {
        @Override
        public void onMetadataParsed(java.util.List<TrackMetadata> tracks) {
            // Parser runs on a background thread; hop to the UI thread to touch player/views.
            runOnUiThread(() -> onContainerMetadata(tracks));
        }

        @Override
        public boolean isMetadataParsed() {
            return !containerTracks.isEmpty();
        }
    };

    public CustomPlayerView playerView;
    public static ExoPlayer player;
    private YouTubeOverlay youTubeOverlay;

    private Object mPictureInPictureParamsBuilder;

    public Prefs mPrefs;
    public BrightnessControl mBrightnessControl;
    public static boolean haveMedia;
    private boolean videoLoading;
    public static boolean controllerVisible;
    public static boolean controllerVisibleFully;
    public static Snackbar snackbar;
    private ExoPlaybackException errorToShow;
    public static int boostLevel = 0;
    private boolean isScaling = false;
    private boolean isScaleStarting = false;
    private float scaleFactor = 1.0f;

    private static final int REQUEST_CHOOSER_VIDEO = 1;
    private static final int REQUEST_CHOOSER_SUBTITLE = 2;
    private static final int REQUEST_CHOOSER_SCOPE_DIR = 10;
    private static final int REQUEST_CHOOSER_VIDEO_MEDIASTORE = 20;
    private static final int REQUEST_CHOOSER_SUBTITLE_MEDIASTORE = 21;
    private static final int REQUEST_SETTINGS = 100;
    private static final int REQUEST_SYSTEM_CAPTIONS = 200;
    public static final int CONTROLLER_TIMEOUT = 3500;
    private static final String ACTION_MEDIA_CONTROL = "media_control";
    private static final String EXTRA_CONTROL_TYPE = "control_type";
    private static final int REQUEST_PLAY = 1;
    private static final int REQUEST_PAUSE = 2;
    private static final int CONTROL_TYPE_PLAY = 1;
    private static final int CONTROL_TYPE_PAUSE = 2;

    private CoordinatorLayout coordinatorLayout;
    private LinearLayout topInfoPanel;
    private FrameLayout posterSlot;
    private ImageView posterView;
    private TextView posterPlaceholderView;
    private TextView posterBadgeView;
    private TextView titleView;
    private TextView videoInfoView;
    private TextView audioInfoView;
    private TextView endsAtView;
    private OutlineTextClock overlayClock;
    private OutlineTextClock headerClock;
    private ImageButton buttonOpen;
    private ImageButton buttonPlaylist;
    private android.app.Dialog playlistDialog;
    private ImageButton buttonPiP;
    private ImageButton buttonAspectRatio;
    private ImageButton buttonRotation;
    private ImageButton exoSettings;
    private ImageButton exoPlayPause;
    private ImageButton exoPrev;
    private ImageButton exoNext;
    private boolean episodeNavLoading;
    private ProgressBar loadingProgressBar;
    private PlayerControlView controlView;
    private CustomDefaultTimeBar timeBar;

    private boolean restoreOrientationLock;
    private boolean restorePlayState;
    private boolean restorePlayStateAllowed;
    private boolean play;
    private float subtitlesScale;
    private boolean isScrubbing;
    private boolean scrubbingNoticeable;
    private long scrubbingStart;
    public boolean frameRendered;
    private boolean alive;
    public static boolean focusPlay = false;
    private Uri nextUri;
    private static boolean isTvBox;
    public static boolean locked = false;
    private Thread nextUriThread;
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
    static final String API_SEASON = "season";
    static final String API_EPISODE = "episode";
    static final String API_IMDB_ID = "imdb_id";
    static final String API_END_BY = "end_by";
    boolean apiAccess;
    boolean apiAccessPartial;
    String apiTitle;
    Uri apiThumbnailUri;
    String apiSegments;
    String[] apiHeaders;
    final List<MediaItem> apiMediaItems = new ArrayList<>();
    final List<String> apiPlaylistSegments = new ArrayList<>();
    int apiPlaylistStartIndex;
    // Episode metadata received via the launch Intent (from LAMPA, com.justplus.player branch). Stored for
    // now; not consumed yet. apiSeason/apiEpisode are -1 when absent; the per-item lists hold null.
    int apiSeason = -1;
    int apiEpisode = -1;
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
    static final int SKIP_HIGHLIGHT_COLOR = 0x99fe6f61; // translucent coral — skip segments
    static final int AD_HIGHLIGHT_COLOR = 0x99FFA000;   // translucent amber — ad segments
    SkipManager skipManager;
    boolean skipBuilt;
    Button buttonSkip;
    ClipDrawable skipButtonProgress;
    LinearLayout skipButtonContainer;
    TextView notificationSkip;
    final Runnable skipNotificationHider = new Runnable() {
        @Override
        public void run() {
            hideSkipNotification();
        }
    };
    SkipSegment pendingSkip;
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
        Utils.setOrientation(this, mPrefs.orientation);

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

        if (isTvBox) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        }

        final Intent launchIntent = getIntent();
        final String action = launchIntent.getAction();
        final String type = launchIntent.getType();

        if ("com.brouken.player.action.SHORTCUT_VIDEOS".equals(action)) {
            openFile(Utils.getMoviesFolderUri());
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
            resetApiAccess();
            final Uri uri = launchIntent.getData();
            if (SubtitleUtils.isSubtitle(uri, type)) {
                handleSubtitles(uri);
            } else {
                Bundle bundle = launchIntent.getExtras();
                if (bundle != null) {
                    apiAccess = bundle.containsKey(API_POSITION) || bundle.containsKey(API_RETURN_RESULT)
                            || bundle.containsKey(API_SUBS) || bundle.containsKey(API_SUBS_ENABLE)
                            || bundle.containsKey(API_VIDEO_LIST);
                    if (apiAccess) {
                        mPrefs.setPersistent(false);
                    } else if (bundle.containsKey(API_TITLE)) {
                        apiAccessPartial = true;
                    }
                    apiTitle = bundle.getString(API_TITLE);
                    final String thumbnail = bundle.getString(API_THUMBNAIL);
                    if (thumbnail != null) {
                        apiThumbnailUri = Uri.parse(thumbnail);
                    }
                    apiSegments = bundle.getString(API_SEGMENTS);
                    apiHeaders = bundle.getStringArray(API_HEADERS);
                    apiSeason = bundle.getInt(API_SEASON, -1);
                    apiEpisode = bundle.getInt(API_EPISODE, -1);
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
        }

        coordinatorLayout = findViewById(R.id.coordinatorLayout);
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        playerView = findViewById(R.id.video_view);
        exoPlayPause = findViewById(R.id.exo_play_pause);
        loadingProgressBar = findViewById(R.id.loading);
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
            playerView.setScale(1.f);
            if (playerView.getResizeMode() == AspectRatioFrameLayout.RESIZE_MODE_FIT) {
                playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM);
                Utils.showText(playerView, getString(R.string.video_resize_crop));
            } else {
                // Default mode
                playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
                Utils.showText(playerView, getString(R.string.video_resize_fit));
            }
            updatebuttonAspectRatioIcon();
            resetHideCallbacks();
        });
        if (isTvBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            buttonAspectRatio.setOnLongClickListener(v -> {
                scaleStart();
                updatebuttonAspectRatioIcon();
                return true;
            });
        }
        buttonRotation = new ImageButton(this, null, 0, R.style.ExoStyledControls_Button_Bottom);
        buttonRotation.setContentDescription(getString(R.string.button_rotate));
        updateButtonRotation();
        buttonRotation.setOnClickListener(view -> {
            mPrefs.orientation = Utils.getNextOrientation(mPrefs.orientation);
            Utils.setOrientation(PlayerActivity.this, mPrefs.orientation);
            updateButtonRotation();
            Utils.showText(playerView, getString(mPrefs.orientation.description), 2500);
            resetHideCallbacks();
        });

        final int titleViewPaddingHorizontal = Utils.dpToPx(14);
        final int titleViewPaddingVertical = getResources().getDimensionPixelOffset(R.dimen.exo_styled_bottom_bar_time_padding);
        FrameLayout centerView = playerView.findViewById(R.id.exo_controls_background);

        topInfoPanel = new LinearLayout(this);
        topInfoPanel.setOrientation(LinearLayout.HORIZONTAL);
        topInfoPanel.setGravity(Gravity.CENTER_VERTICAL);
        topInfoPanel.setBackgroundResource(R.color.ui_controls_background);
        topInfoPanel.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        topInfoPanel.setPadding(titleViewPaddingHorizontal, titleViewPaddingVertical, titleViewPaddingHorizontal, titleViewPaddingVertical);
        topInfoPanel.setVisibility(View.GONE);

        posterSlot = new FrameLayout(this);
        final LinearLayout.LayoutParams slotParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, Utils.dpToPx(54));
        slotParams.setMarginEnd(Utils.dpToPx(16));
        slotParams.gravity = Gravity.CENTER_VERTICAL;
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
        posterPlaceholderView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        posterPlaceholderView.setTypeface(Typeface.DEFAULT_BOLD);
        posterPlaceholderView.setVisibility(View.GONE);
        posterSlot.addView(posterPlaceholderView);

        posterBadgeView = createPosterNumberBadge();
        posterSlot.addView(posterBadgeView);

        topInfoPanel.addView(posterSlot);

        // Info column (title + media info lines); a small gap separates it from the clock column at the end.
        final LinearLayout infoColumn = new LinearLayout(this);
        infoColumn.setOrientation(LinearLayout.VERTICAL);
        final LinearLayout.LayoutParams infoColumnParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        infoColumnParams.gravity = Gravity.CENTER_VERTICAL;
        infoColumnParams.setMarginEnd(Utils.dpToPx(16));
        infoColumn.setLayoutParams(infoColumnParams);

        titleView = new TextView(this);
        titleView.setTextColor(Color.WHITE);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        titleView.setMaxLines(1);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setTextDirection(View.TEXT_DIRECTION_LOCALE);
        infoColumn.addView(titleView);

        videoInfoView = createInfoLine(Utils.dpToPx(2));
        infoColumn.addView(videoInfoView);
        audioInfoView = createInfoLine(0);
        infoColumn.addView(audioInfoView);

        topInfoPanel.addView(infoColumn);

        // Right part of the header: clock over the "ends at" estimate, vertically centered so it lines up
        // with the poster/title. A separate floating clock (below) covers the controls-hidden state.
        final LinearLayout headerClockColumn = new LinearLayout(this);
        headerClockColumn.setOrientation(LinearLayout.VERTICAL);
        headerClockColumn.setGravity(Gravity.END);
        final LinearLayout.LayoutParams headerClockColumnParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        headerClockColumnParams.gravity = Gravity.CENTER_VERTICAL;
        headerClockColumn.setLayoutParams(headerClockColumnParams);

        headerClock = new OutlineTextClock(this);
        headerClock.setFormat12Hour("h:mm a");
        headerClock.setFormat24Hour("HH:mm");
        headerClock.setTextColor(Color.WHITE);
        headerClock.setTypeface(Typeface.DEFAULT_BOLD);
        headerClock.setTextSize(TypedValue.COMPLEX_UNIT_SP, isTvBox ? 20 : 18);
        final LinearLayout.LayoutParams headerClockLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        headerClockLp.gravity = Gravity.END;
        headerClock.setLayoutParams(headerClockLp);
        headerClockColumn.addView(headerClock);

        endsAtView = new TextView(this);
        endsAtView.setTextColor(0xB3FFFFFF);
        endsAtView.setTextSize(TypedValue.COMPLEX_UNIT_SP, isTvBox ? 13 : 11);
        final LinearLayout.LayoutParams endsAtLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        endsAtLp.gravity = Gravity.END;
        endsAtView.setLayoutParams(endsAtLp);
        endsAtView.setVisibility(View.GONE);
        headerClockColumn.addView(endsAtView);

        // Long-press the clock to copy the full launch intent to the clipboard, for diagnostics.
        headerClockColumn.setOnLongClickListener(view -> {
            copyLaunchIntentToClipboard();
            return true;
        });

        topInfoPanel.addView(headerClockColumn);

        centerView.addView(topInfoPanel);

        // Skip button — floats over the video (bottom-end), independent of the controller.
        // Styled to match the app's control buttons: chrome-coloured pill, icon + label, and the
        // same selectableItemBackground focus/press highlight (white on TV via colorControlHighlight).
        final int skipCornerRadius = Utils.dpToPx(6);
        buttonSkip = new Button(this);
        buttonSkip.setText(R.string.button_skip);
        buttonSkip.setAllCaps(false);
        buttonSkip.setTextColor(Color.WHITE);
        buttonSkip.setTextSize(TypedValue.COMPLEX_UNIT_SP, isTvBox ? 15 : 13);
        buttonSkip.setTypeface(Typeface.DEFAULT_BOLD);
        buttonSkip.setMinHeight(0);
        buttonSkip.setMinimumHeight(0);
        buttonSkip.setPadding(Utils.dpToPx(14), Utils.dpToPx(6), Utils.dpToPx(16), Utils.dpToPx(6));

        final Drawable skipIcon = ContextCompat.getDrawable(this, R.drawable.exo_styled_controls_next);
        if (skipIcon != null) {
            final int skipIconSize = Utils.dpToPx(18);
            skipIcon.setBounds(0, 0, skipIconSize, skipIconSize);
            buttonSkip.setCompoundDrawablesRelative(skipIcon, null, null, null);
            buttonSkip.setCompoundDrawablePadding(Utils.dpToPx(6));
            buttonSkip.setCompoundDrawableTintList(ColorStateList.valueOf(Color.WHITE));
        }

        final GradientDrawable skipButtonBackground = new GradientDrawable();
        skipButtonBackground.setColor(ContextCompat.getColor(this, R.color.ui_controls_background));
        skipButtonBackground.setCornerRadius(skipCornerRadius);
        buttonSkip.setBackground(skipButtonBackground);
        // Round the corners and clip the focus/press ripple to them, like the other controls.
        buttonSkip.setClipToOutline(true);
        buttonSkip.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), skipCornerRadius);
            }
        });
        final TypedValue skipHighlight = new TypedValue();
        if (getTheme().resolveAttribute(android.R.attr.selectableItemBackground, skipHighlight, true)
                && skipHighlight.resourceId != 0) {
            buttonSkip.setForeground(ContextCompat.getDrawable(this, skipHighlight.resourceId));
        }
        buttonSkip.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        buttonSkip.setOnClickListener(v -> {
            if (pendingSkip != null && player != null) {
                pendingSkip.skipped = true;
                final long endMs = pendingSkip.endMs();
                hideSkipButton();
                skipSeekTo(endMs);
            }
        });

        // Thin progress bar under the button (player-timebar style) showing how long the button stays
        // available — i.e. the remaining skip-section duration. The coral fill drains as it plays out.
        final int skipBarCorner = Utils.dpToPx(2);
        final GradientDrawable skipBarTrack = new GradientDrawable();
        skipBarTrack.setColor(Color.parseColor("#40fe6f61"));
        skipBarTrack.setCornerRadius(skipBarCorner);
        final GradientDrawable skipBarFill = new GradientDrawable();
        skipBarFill.setColor(Color.parseColor("#fe6f61"));
        skipBarFill.setCornerRadius(skipBarCorner);
        skipButtonProgress = new ClipDrawable(skipBarFill, Gravity.START, ClipDrawable.HORIZONTAL);
        skipButtonProgress.setLevel(0);
        final View skipProgressBar = new View(this);
        skipProgressBar.setBackground(new LayerDrawable(new Drawable[]{skipBarTrack, skipButtonProgress}));
        final LinearLayout.LayoutParams skipBarParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Utils.dpToPx(3));
        skipBarParams.topMargin = Utils.dpToPx(3);
        skipProgressBar.setLayoutParams(skipBarParams);

        // Button + progress bar travel together as one bottom-end floating unit.
        skipButtonContainer = new LinearLayout(this);
        skipButtonContainer.setOrientation(LinearLayout.VERTICAL);
        skipButtonContainer.addView(buttonSkip);
        skipButtonContainer.addView(skipProgressBar);
        final CoordinatorLayout.LayoutParams skipButtonParams = new CoordinatorLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        skipButtonParams.gravity = Gravity.BOTTOM | Gravity.END;
        skipButtonParams.setMargins(0, 0, Utils.dpToPx(24), Utils.dpToPx(96));
        skipButtonContainer.setLayoutParams(skipButtonParams);
        skipButtonContainer.setVisibility(View.GONE);
        coordinatorLayout.addView(skipButtonContainer);

        // Toast-style notification shown after an automatic skip: same pill as the Skip button (icon + label,
        // no progress bar), floating top-centre. Auto-hides after 5s or on any interaction (see onUserInteraction).
        notificationSkip = new TextView(this);
        notificationSkip.setText(R.string.notification_skipped);
        notificationSkip.setAllCaps(false);
        notificationSkip.setTextColor(Color.WHITE);
        notificationSkip.setTextSize(TypedValue.COMPLEX_UNIT_SP, isTvBox ? 15 : 13);
        notificationSkip.setTypeface(Typeface.DEFAULT_BOLD);
        notificationSkip.setGravity(Gravity.CENTER_VERTICAL);
        notificationSkip.setPadding(Utils.dpToPx(14), Utils.dpToPx(6), Utils.dpToPx(16), Utils.dpToPx(6));

        final Drawable skipDoneIcon = ContextCompat.getDrawable(this, R.drawable.exo_styled_controls_next);
        if (skipDoneIcon != null) {
            final int skipDoneIconSize = Utils.dpToPx(18);
            skipDoneIcon.setBounds(0, 0, skipDoneIconSize, skipDoneIconSize);
            notificationSkip.setCompoundDrawablesRelative(skipDoneIcon, null, null, null);
            notificationSkip.setCompoundDrawablePadding(Utils.dpToPx(6));
            notificationSkip.setCompoundDrawableTintList(ColorStateList.valueOf(Color.WHITE));
        }

        final GradientDrawable notificationBackground = new GradientDrawable();
        notificationBackground.setColor(ContextCompat.getColor(this, R.color.ui_controls_background));
        notificationBackground.setCornerRadius(skipCornerRadius);
        notificationSkip.setBackground(notificationBackground);
        notificationSkip.setClipToOutline(true);
        notificationSkip.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), skipCornerRadius);
            }
        });

        final CoordinatorLayout.LayoutParams notificationParams = new CoordinatorLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        notificationParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        notificationParams.setMargins(0, Utils.dpToPx(28), 0, 0);
        notificationSkip.setLayoutParams(notificationParams);
        notificationSkip.setVisibility(View.GONE);
        notificationSkip.setOnClickListener(v -> hideSkipNotification());
        coordinatorLayout.addView(notificationSkip);

        // Persistent clock over the video, shown only when the controls (and thus the in-header clock) are
        // hidden and the "show clock" preference is on. It is positioned to exactly mirror the in-header
        // clock (see syncOverlayClockPosition), so toggling the controls swaps between the two clocks in the
        // same spot with no jump.
        overlayClock = new OutlineTextClock(this);
        overlayClock.setFormat12Hour("h:mm a");
        overlayClock.setFormat24Hour("HH:mm");
        overlayClock.setTextColor(Color.WHITE);
        overlayClock.setTypeface(Typeface.DEFAULT_BOLD);
        overlayClock.setTextSize(TypedValue.COMPLEX_UNIT_SP, isTvBox ? 20 : 18);
        final CoordinatorLayout.LayoutParams overlayClockLp = new CoordinatorLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        overlayClockLp.gravity = Gravity.TOP | Gravity.START;
        overlayClock.setLayoutParams(overlayClockLp);
        overlayClock.setVisibility(View.GONE);
        coordinatorLayout.addView(overlayClock);

        // Whenever the in-header clock is (re)laid out, mirror its position onto the floating clock.
        headerClock.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> syncOverlayClockPosition());

        topInfoPanel.setOnLongClickListener(view -> {
            // Prevent FileUriExposedException
            if (mPrefs.mediaUri != null && ContentResolver.SCHEME_FILE.equals(mPrefs.mediaUri.getScheme())) {
                return false;
            }

            final Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.putExtra(Intent.EXTRA_STREAM, mPrefs.mediaUri);
            if (mPrefs.mediaType == null)
                shareIntent.setType("video/*");
            else
                shareIntent.setType(mPrefs.mediaType);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            // Start without intent chooser to allow any target to be set as default
            startActivity(shareIntent);

            return true;
        });

        if (Build.VERSION.SDK_INT >= 35) {
            getWindow().setNavigationBarContrastEnforced(false);
        }

        controlView = playerView.findViewById(R.id.exo_controller);
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

                int paddingLeft = 0;
                int marginLeft = insetLeft;

                int paddingRight = 0;
                int marginRight = insetRight;

                if (Build.VERSION.SDK_INT >= 28 && windowInsets.getDisplayCutout() != null) {
                    if (windowInsets.getDisplayCutout().getSafeInsetLeft() == insetLeft) {
                        paddingLeft = insetLeft;
                        marginLeft = 0;
                    }
                    if (windowInsets.getDisplayCutout().getSafeInsetRight() == insetRight) {
                        paddingRight = insetRight;
                        marginRight = 0;
                    }
                }

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
                    params.height = getResources().getDimensionPixelSize(R.dimen.exo_styled_bottom_bar_height) + windowInsets.getSystemWindowInsetBottom();
                    exoBottomBar.setLayoutParams(params);

                    findViewById(R.id.exo_left).getLayoutParams().width = left;
                    findViewById(R.id.exo_right).getLayoutParams().width = right;

                    bottomBarPaddingBottom = windowInsets.getSystemWindowInsetBottom();
                    progressBarMarginBottom = windowInsets.getSystemWindowInsetBottom();
                } else {
                    // No top padding: the header panel's background (below) covers the status-bar area instead.
                    view.setPadding(0, 0, 0, windowInsets.getSystemWindowInsetBottom());
                }

                // Extend the header's background up over the status-bar area (top margin -> 0, top inset moved into
                // the top padding). The content position is unchanged (padding pushes it down by the same amount the
                // margin used to), but the panel now paints the status-bar strip, in perfect sync with the header.
                Utils.setViewParams(topInfoPanel, paddingLeft + titleViewPaddingHorizontal, windowInsets.getSystemWindowInsetTop() + titleViewPaddingVertical, paddingRight + titleViewPaddingHorizontal, titleViewPaddingVertical,
                        marginLeft, 0, marginRight, 0);


                Utils.setViewParams(findViewById(R.id.exo_bottom_bar), paddingLeft, 0, paddingRight, bottomBarPaddingBottom,
                        marginLeft, 0, marginRight, 0);

                Utils.setViewParams(findViewById(R.id.exo_progress), windowInsets.getSystemWindowInsetLeft(), 0, windowInsets.getSystemWindowInsetRight(), 0,
                        0, 0, 0, getResources().getDimensionPixelSize(R.dimen.exo_styled_progress_margin_bottom) + progressBarMarginBottom);

                // Keep the Skip pill above the seek bar and clear of the nav-bar inset. It floats on the
                // full-screen coordinator (not the controller), so a fixed bottom offset overlapped the
                // progress bar on tablets — derive it from the seek bar's own geometry (its top sits at
                // insetBottom + progress margin + progress layout height above the screen bottom).
                if (skipButtonContainer != null) {
                    final CoordinatorLayout.LayoutParams skipLp = (CoordinatorLayout.LayoutParams) skipButtonContainer.getLayoutParams();
                    skipLp.bottomMargin = windowInsets.getSystemWindowInsetBottom()
                            + getResources().getDimensionPixelSize(R.dimen.exo_styled_progress_margin_bottom)
                            + getResources().getDimensionPixelSize(R.dimen.exo_styled_progress_layout_height)
                            + Utils.dpToPx(10);
                    skipLp.rightMargin = Utils.dpToPx(24) + insetRight;
                    skipButtonContainer.setLayoutParams(skipLp);
                }

                Utils.setViewMargins(findViewById(R.id.exo_error_message), 0, windowInsets.getSystemWindowInsetTop() / 2, 0, getResources().getDimensionPixelSize(R.dimen.exo_error_message_margin_bottom) + windowInsets.getSystemWindowInsetBottom() / 2);

                windowInsets.consumeSystemWindowInsets();
            }
            return windowInsets;
        });
        timeBar.setAdMarkerColor(Color.argb(0x00, 0xFF, 0xFF, 0xFF));
        timeBar.setPlayedAdMarkerColor(Color.argb(0x98, 0xFF, 0xFF, 0xFF));

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
        if (mPrefs.brightness >= 0) {
            mBrightnessControl.currentBrightnessLevel = mPrefs.brightness;
            mBrightnessControl.setScreenBrightness(mBrightnessControl.levelToBrightness(mBrightnessControl.currentBrightnessLevel));
        }
        playerView.setBrightnessControl(mBrightnessControl);

        final LinearLayout exoBasicControls = playerView.findViewById(R.id.exo_basic_controls);
        final ImageButton exoSubtitle = exoBasicControls.findViewById(R.id.exo_subtitle);
        exoBasicControls.removeView(exoSubtitle);

        exoSettings = exoBasicControls.findViewById(R.id.exo_settings);
        exoBasicControls.removeView(exoSettings);
        final ImageButton exoRepeat = exoBasicControls.findViewById(R.id.exo_repeat_toggle);
        exoBasicControls.removeView(exoRepeat);
        //exoBasicControls.setVisibility(View.GONE);

        exoSettings.setOnLongClickListener(view -> {
            //askForScope(false, false);
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivityForResult(intent, REQUEST_SETTINGS);
            return true;
        });

        exoSubtitle.setOnLongClickListener(v -> {
            enableRotation();
            safelyStartActivityForResult(new Intent(Settings.ACTION_CAPTIONING_SETTINGS), REQUEST_SYSTEM_CAPTIONS);
            return true;
        });

        updateButtons(false);

        final HorizontalScrollView horizontalScrollView = (HorizontalScrollView) getLayoutInflater().inflate(R.layout.controls, null);
        final LinearLayout controls = horizontalScrollView.findViewById(R.id.controls);

        controls.addView(buttonOpen);
        controls.addView(buttonPlaylist);
        controls.addView(exoSubtitle);
        controls.addView(buttonAspectRatio);
        if (Utils.isPiPSupported(this) && buttonPiP != null) {
            controls.addView(buttonPiP);
        }
        if (mPrefs.repeatToggle) {
            controls.addView(exoRepeat);
        }
        if (!isTvBox) {
            controls.addView(buttonRotation);
        }
        controls.addView(exoSettings);

        exoBasicControls.addView(horizontalScrollView);

        if (Build.VERSION.SDK_INT > 23) {
            horizontalScrollView.setOnScrollChangeListener((view, i, i1, i2, i3) -> resetHideCallbacks());
        }

        playerView.setControllerVisibilityListener(new PlayerView.ControllerVisibilityListener() {
            @Override
            public void onVisibilityChanged(int visibility) {
                controllerVisible = visibility == View.VISIBLE;
                controllerVisibleFully = playerView.isControllerFullyVisible();

                if (controllerVisible) {
                    updateMediaInfo();
                    startEndsAtUpdates();
                } else {
                    stopEndsAtUpdates();
                }
                updateOverlayClock();

                if (PlayerActivity.restoreControllerTimeout) {
                    restoreControllerTimeout = false;
                    if (player == null || !player.isPlaying()) {
                        playerView.setControllerShowTimeoutMs(-1);
                    } else {
                        playerView.setControllerShowTimeoutMs(PlayerActivity.CONTROLLER_TIMEOUT);
                    }
                }

                // https://developer.android.com/training/system-ui/immersive
                Utils.toggleSystemUi(PlayerActivity.this, playerView, visibility == View.VISIBLE);
                if (visibility == View.VISIBLE) {
                    // Because when using dpad controls, focus resets to first item in bottom controls bar
                    findViewById(R.id.exo_play_pause).requestFocus();
                }

                if (controllerVisible && playerView.isControllerFullyVisible()) {
                    if (mPrefs.firstRun) {
                        TapTargetView.showFor(PlayerActivity.this,
                                TapTarget.forView(buttonOpen, getString(R.string.onboarding_open_title), getString(R.string.onboarding_open_description))
                                        .outerCircleColor(R.color.brand)
                                        .targetCircleColor(R.color.white)
                                        .titleTextSize(22)
                                        .titleTextColor(R.color.white)
                                        .descriptionTextSize(14)
                                        .cancelable(true),
                                new TapTargetView.Listener() {
                                    @Override
                                    public void onTargetClick(TapTargetView view) {
                                        super.onTargetClick(view);
                                        buttonOpen.performClick();
                                    }
                                });
                        // TODO: Explain gestures?
                        //  "Use vertical and horizontal gestures to change brightness, volume and seek in video"
                        mPrefs.markFirstRun();
                    }
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
    }

    @Override
    public void onStart() {
        super.onStart();
        alive = true;
        if (!(isTvBox && Build.VERSION.SDK_INT >= 31)) {
            updateSubtitleStyle(this);
        }
        if (Build.VERSION.SDK_INT >= 31) {
            playerView.removeCallbacks(barsHider);
            Utils.toggleSystemUi(this, playerView, true);
        }
        initializePlayer();
        updateButtonRotation();
    }

    @Override
    public void onResume() {
        super.onResume();
        restorePlayStateAllowed = true;
        if (isTvBox && Build.VERSION.SDK_INT >= 31) {
            updateSubtitleStyle(this);
        }
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
        if (Build.VERSION.SDK_INT >= 31) {
            playerView.removeCallbacks(barsHider);
        }
        playerView.setCustomErrorMessage(null);
        releasePlayer(false);
    }

    @SuppressLint("GestureBackNavigation")
    @Override
    public void onBackPressed() {
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
                        if (mPrefs.persistentMode) {
                            intent.putExtra(API_POSITION, (int) mPrefs.nonPersitentPosition);
                        } else {
                            intent.putExtra(API_POSITION, (int) player.getCurrentPosition());
                        }
                    }
                }
            }
            setResult(Activity.RESULT_OK, intent);
        }

        super.finish();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        if (intent != null) {
            final String action = intent.getAction();
            final String type = intent.getType();
            final Uri uri = intent.getData();

            if (Intent.ACTION_VIEW.equals(action) && uri != null) {
                if (SubtitleUtils.isSubtitle(uri, type)) {
                    handleSubtitles(uri);
                } else {
                    mPrefs.updateMedia(this, uri, type);
                    searchSubtitles();
                }
                focusPlay = true;
                initializePlayer();
            } else if (Intent.ACTION_SEND.equals(action) && "text/plain".equals(type)) {
                String text = intent.getStringExtra(Intent.EXTRA_TEXT);
                if (text != null) {
                    final Uri parsedUri = Uri.parse(text);
                    if (parsedUri.isAbsolute()) {
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
                    if (player == null)
                        break;
                    playerView.removeCallbacks(playerView.textClearRunnable);
                    long pos = player.getCurrentPosition();
                    if (playerView.keySeekStart == -1) {
                        playerView.keySeekStart = pos;
                    }
                    long seekTo = pos - 10_000;
                    if (seekTo < 0)
                        seekTo = 0;
                    player.setSeekParameters(SeekParameters.PREVIOUS_SYNC);
                    player.seekTo(seekTo);
                    final String message = Utils.formatMilisSign(seekTo - playerView.keySeekStart) + "\n" + Utils.formatMilis(seekTo);
                    playerView.setCustomErrorMessage(message);
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_BUTTON_R2:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                if (!controllerVisibleFully || keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) {
                    if (player == null)
                        break;
                    playerView.removeCallbacks(playerView.textClearRunnable);
                    long pos = player.getCurrentPosition();
                    if (playerView.keySeekStart == -1) {
                        playerView.keySeekStart = pos;
                    }
                    long seekTo = pos + 10_000;
                    long seekMax = player.getDuration();
                    if (seekMax != C.TIME_UNSET && seekTo > seekMax)
                        seekTo = seekMax;
                    PlayerActivity.player.setSeekParameters(SeekParameters.NEXT_SYNC);
                    player.seekTo(seekTo);
                    final String message = Utils.formatMilisSign(seekTo - playerView.keySeekStart) + "\n" + Utils.formatMilis(seekTo);
                    playerView.setCustomErrorMessage(message);
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_BACK:
                if (isTvBox) {
                    if (controllerVisible && player != null && player.isPlaying()) {
                        playerView.hideController();
                        return true;
                    } else {
                        onBackPressed();
                    }
                }
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
                break;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
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

        if (isTvBox && !controllerVisibleFully) {
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

        if (isInPictureInPictureMode) {
            // On Android TV it is required to hide controller in this PIP change callback
            playerView.hideController();
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
            if (mPrefs.resizeMode == AspectRatioFrameLayout.RESIZE_MODE_ZOOM) {
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
                else
                    playerView.showController();
            }
        }
    }

    void resetApiAccess() {
        apiAccess = false;
        apiAccessPartial = false;
        apiTitle = null;
        apiThumbnailUri = null;
        apiSegments = null;
        apiHeaders = null;
        apiMediaItems.clear();
        apiPlaylistSegments.clear();
        apiPlaylistStartIndex = 0;
        apiSeason = -1;
        apiEpisode = -1;
        apiSubs.clear();
        mPrefs.setPersistent(true);
        if (skipManager != null) {
            skipManager.clear();
        hideSkipButton();
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
        hideSkipNotification();
        final String json = currentSegmentsJson();
        skipManager.setSource(json != null && !json.isEmpty() ? new IntentSegmentsSource(json) : null);
        // Source (re)set → the manager holds no segments until rebuildSkip() runs against the new
        // duration. Drop any highlights from the previous item right now so switching episodes never
        // leaves stale timecodes on the bar; the new segments (intent or online) repaint on rebuild.
        if (timeBar != null) {
            timeBar.clearSkipHighlights();
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

    private void rebuildSkip() {
        if (skipManager == null) {
            return;
        }
        double durationSec = 0;
        if (player != null) {
            final long durationMs = player.getDuration();
            if (durationMs != C.TIME_UNSET && durationMs > 0) {
                durationSec = durationMs / 1000.0;
            }
        }
        skipManager.rebuild(durationSec);
        updateSkipHighlights();
    }

    // Online skip-segment lookup (FIND_INTO.MD): when the current item has no intent-provided segments,
    // fetch them by imdb/season/episode and feed the result through the same SkipManager path.
        if (player == null || skipManager == null) {
        if (player == null || skipManager == null || segments == null || segments.isEmpty()) {
            return;
        }
        // Ignore if the media item changed since the fetch started, or intent segments have appeared.
        if (player.getCurrentMediaItemIndex() != targetIndex || skipManager.hasSegments()) {
        rebuildSkip();
                return season != null ? season : -1;
            }
            return -1;
        }
        return apiSeason;
                return episode != null ? episode : -1;
            }
            return -1;
        }
        return apiEpisode;
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
        for (int i = 0; i < count; i++) {
            final SkipSegment segment = segments.get(i);
            starts[i] = segment.startMs();
            ends[i] = segment.endMs();
            colors[i] = segment.type == SkipSegment.Type.AD ? AD_HIGHLIGHT_COLOR : SKIP_HIGHLIGHT_COLOR;
        }
        timeBar.setSkipHighlights(starts, ends, colors, durationMs);
    }

    private void skipTick() {
        if (player == null || skipManager == null || !mPrefs.skipEnabled) {
            hideSkipButton();
            return;
        }
        final double posSec = player.getCurrentPosition() / 1000.0;
        final SkipSegment segment = skipManager.activeSegment(posSec);
        if (segment == null) {
            hideSkipButton();
            return;
        }
        // Ad segments are always skipped silently; skip segments follow the button/auto preference.
        final boolean auto = segment.type == SkipSegment.Type.AD
                || Prefs.SKIP_MODE_AUTO.equals(mPrefs.skipMode);
        if (auto) {
            segment.skipped = true;
            hideSkipButton();
            skipSeekTo(segment.endMs());
            showSkipNotification();
        } else {
            updateSkipButtonProgress(segment);
            showSkipButton(segment);
        }
    }

    /** Sizes the coral fill to the fraction of the segment still remaining (1 at start, 0 at the end). */
    private void updateSkipButtonProgress(SkipSegment segment) {
        if (skipButtonProgress == null || player == null || segment == null)
            return;
        final long totalMs = segment.endMs() - segment.startMs();
        if (totalMs <= 0) {
            skipButtonProgress.setLevel(0);
            return;
        }
        final long remainingMs = segment.endMs() - player.getCurrentPosition();
        final double fraction = Math.max(0, Math.min(1, remainingMs / (double) totalMs));
        skipButtonProgress.setLevel((int) Math.round(fraction * 10000));
    }

    private void skipSeekTo(long positionMs) {
        if (player == null)
            return;
        // Exact seek so playback resumes precisely at the segment end, not at an earlier keyframe.
        player.setSeekParameters(SeekParameters.EXACT);
        player.seekTo(positionMs);
    }

    private void showSkipButton(SkipSegment segment) {
        pendingSkip = segment;
        if (skipButtonContainer != null && skipButtonContainer.getVisibility() != View.VISIBLE) {
            skipButtonContainer.setVisibility(View.VISIBLE);
            if (isTvBox) {
                buttonSkip.requestFocus();
            }
        }
    }

    private void hideSkipButton() {
        pendingSkip = null;
        if (skipButtonContainer != null) {
            skipButtonContainer.setVisibility(View.GONE);
        }
    }

    private void showSkipNotification() {
        if (notificationSkip == null) {
            return;
        }
        notificationSkip.setVisibility(View.VISIBLE);
        playerView.removeCallbacks(skipNotificationHider);
        playerView.postDelayed(skipNotificationHider, 5000);
    }

    private void hideSkipNotification() {
        if (notificationSkip == null) {
            return;
        }
        if (playerView != null) {
            playerView.removeCallbacks(skipNotificationHider);
        }
        if (notificationSkip.getVisibility() != View.GONE) {
            notificationSkip.setVisibility(View.GONE);
        }
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        // Any touch or key dispatched to the player dismisses the auto-skip notification.
        hideSkipNotification();
    }

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

        apiMediaItems.clear();
        apiPlaylistSegments.clear();
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
            apiMediaItems.add(new MediaItem.Builder()
                    .setUri(uri)
                    .setMediaMetadata(metadataBuilder.build())
                    .build());
            // Keep segments aligned by index with apiMediaItems (null when absent)
            apiPlaylistSegments.add(segments != null && i < segments.length ? segments[i] : null);
            // Episode metadata, aligned by index (null when absent). Stored, not yet used.
                    && imdbIds[i] != null && !imdbIds[i].isEmpty() ? imdbIds[i] : null);
        }
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
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, isTvBox ? 14 : 12);
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
        String container = video != null ? containerFromMime(video.containerMimeType) : null;
        if (container == null) {
            container = containerFromCurrentUri();
        }
        setInfoLine(videoInfoView, buildVideoInfo(video, container));
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

    private static String buildVideoInfo(Format video, String container) {
        if (video == null) {
            return null;
        }
        final StringBuilder b = new StringBuilder();
        appendField(b, container);
        if (video.width > 0 && video.height > 0) {
            appendField(b, video.width + "×" + video.height);
        }
        appendField(b, codecName(video));
        appendField(b, formatFrameRate(video.frameRate));
        appendField(b, hdrName(video.colorInfo));
        appendField(b, Utils.formatBitrate(bestBitrate(video)));
        return b.toString();
    }

    private static String formatFrameRate(float fps) {
        if (fps <= 0) {
            return null;
        }
        return Math.round(fps) + " fps";
    }

    private static String containerFromMime(String mime) {
        if (mime == null) {
            return null;
        }
        switch (mime) {
            case "video/mp4":
            case "application/mp4": return "MP4";
            case "video/x-matroska": return "MKV";
            case "video/webm": return "WebM";
            case "video/avi":
            case "video/x-msvideo": return "AVI";
            case "video/mp2t": return "TS";
            case "video/quicktime": return "MOV";
            case "video/mpeg": return "MPEG";
            case "video/3gpp": return "3GP";
            case "application/x-mpegURL":
            case "application/vnd.apple.mpegurl": return "HLS";
            default: return null;
        }
    }

    private String containerFromCurrentUri() {
        Uri uri = null;
        final MediaItem item = player != null ? player.getCurrentMediaItem() : null;
        if (item != null && item.localConfiguration != null) {
            uri = item.localConfiguration.uri;
        }
        if (uri == null) {
            uri = mPrefs.mediaUri;
        }
        if (uri == null) {
            return null;
        }
        final String path = uri.getLastPathSegment();
        if (path == null) {
            return null;
        }
        final int dot = path.lastIndexOf('.');
        if (dot < 0 || dot >= path.length() - 1) {
            return null;
        }
        switch (path.substring(dot + 1).toLowerCase(Locale.US)) {
            case "mp4": case "m4v": return "MP4";
            case "mkv": return "MKV";
            case "webm": return "WebM";
            case "avi": return "AVI";
            case "ts": case "m2ts": return "TS";
            case "mov": return "MOV";
            case "m3u8": return "HLS";
            case "flv": return "FLV";
            case "wmv": return "WMV";
            case "mpg": case "mpeg": return "MPEG";
            case "3gp": return "3GP";
            default: return null;
        }
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
    private void onContainerMetadata(java.util.List<TrackMetadata> tracks) {
        containerTracks.clear();
        containerTracks.addAll(tracks);
        resolveTrackNames();
        updateMediaInfo();
    }

    /**
     * Maps container track names onto the player's current tracks by {@code Format.id} (the tkhd
     * trackId / MKV TrackNumber), falling back to order within each type, and stores the result in
     * {@link #resolvedTrackNames} (shared live with {@link #trackNameProvider}).
     */
    private void resolveTrackNames() {
        resolvedTrackNames.clear();
        if (player == null || containerTracks.isEmpty()) {
            return;
        }
        resolveNamesForType(C.TRACK_TYPE_AUDIO, TrackMetadata.Type.AUDIO);
        resolveNamesForType(C.TRACK_TYPE_TEXT, TrackMetadata.Type.SUBTITLE);
    }

    private void resolveNamesForType(int trackType, TrackMetadata.Type metaType) {
        final java.util.List<TrackMetadata> ordered = new java.util.ArrayList<>();
        for (TrackMetadata t : containerTracks) {
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
                        for (TrackMetadata t : containerTracks) {
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

    private static int bestBitrate(Format format) {
        return format.averageBitrate != Format.NO_VALUE ? format.averageBitrate : format.peakBitrate;
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
        endsAtView.setText(getString(R.string.time_ends_at, time));
        endsAtView.setVisibility(View.VISIBLE);
    }

    // When the controls are visible the in-header clock is shown; this floating clock only covers the
    // controls-hidden state, and then only when the "show clock" preference is on.
    void updateOverlayClock() {
        if (overlayClock == null) {
            return;
        }
        final boolean show = !controllerVisible && mPrefs.showClock;
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

    private void copyLaunchIntentToClipboard() {
        final String report = Utils.buildIntentReport(getIntent());
        final android.content.ClipboardManager clipboard =
                (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("intent", report));
            android.widget.Toast.makeText(this, R.string.intent_copied, android.widget.Toast.LENGTH_SHORT).show();
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
        badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
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
        if (player == null || player.getMediaItemCount() <= 1) {
            return;
        }
        final int count = player.getMediaItemCount();
        final int current = player.getCurrentMediaItemIndex();
        final int radius = Utils.dpToPx(4);
        final View[] currentRow = new View[1];

        final LinearLayout listLayout = new LinearLayout(this);
        listLayout.setOrientation(LinearLayout.VERTICAL);
        final int listPad = Utils.dpToPx(10);
        listLayout.setPadding(listPad, listPad, listPad, listPad);

        final TextView header = new TextView(this);
        header.setText(getString(R.string.playlist));
        header.setTextColor(Color.WHITE);
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
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
            final MediaItem item = player.getMediaItemAt(i);
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
            // Rounded row: subtle fill for the current item, plus a rounded ripple for touch/D-pad focus.
            final GradientDrawable rowContent = new GradientDrawable();
            rowContent.setCornerRadius(Utils.dpToPx(8));
            rowContent.setColor(isCurrent ? 0x24FFFFFF : Color.TRANSPARENT);
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
                number.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
                box.addView(number);
            }
            row.addView(box);

            final TextView titleText = new TextView(this);
            titleText.setText(title);
            titleText.setTextColor(isCurrent ? 0xFFFFFFFF : 0xFFDDDDDD);
            titleText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            titleText.setMaxLines(2);
            titleText.setEllipsize(TextUtils.TruncateAt.END);
            if (isCurrent) {
                titleText.setTypeface(Typeface.DEFAULT_BOLD);
            }
            final LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            titleLp.gravity = Gravity.CENTER_VERTICAL;
            titleText.setLayoutParams(titleLp);
            row.addView(titleText);

            row.setOnClickListener(v -> {
                if (player != null) {
                    player.seekToDefaultPosition(index);
                    player.setPlayWhenReady(true);
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
        int padTop = 0;
        int padBottom = 0;
        final WindowInsets rootInsets = coordinatorLayout.getRootWindowInsets();
        if (rootInsets != null) {
            padTop = rootInsets.getSystemWindowInsetTop();
            padBottom = rootInsets.getSystemWindowInsetBottom();
        }
        scrollView.setPadding(0, padTop, 0, padBottom);

        if (playlistDialog != null) {
            playlistDialog.dismiss();
        }
        playlistDialog = new android.app.Dialog(this, android.R.style.Theme_Translucent_NoTitleBar);
        playlistDialog.setContentView(scrollView);
        playlistDialog.setCanceledOnTouchOutside(true);
        final Window window = playlistDialog.getWindow();
        if (window != null) {
            // Draw the dialog full-screen and edge-to-edge (content under the system bars) so its size and
            // content position never change while it is open — the fixed padding above is then the ONLY inset.
            // Without this the decor adds its own status-bar inset on top of that padding, then settles to one,
            // making the list visibly slide up ("jump"). The status bar itself stays visible.
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
            if (Build.VERSION.SDK_INT >= 30) {
                window.setDecorFitsSystemWindows(false);
            } else {
                window.getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            }
            window.setLayout(Utils.dpToPx(360), ViewGroup.LayoutParams.MATCH_PARENT);
            window.setGravity(Gravity.END);
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0xF0141414));
        }
        // Hide the player's overlay (header + bottom controls) so only the playlist panel is shown.
        playerView.hideController();
        playlistDialog.show();
        if (currentRow[0] != null) {
            currentRow[0].post(() -> currentRow[0].requestFocus());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        try {
            if (restoreOrientationLock) {
                Settings.System.putInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0);
                restoreOrientationLock = false;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

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
            mPrefs.loadUserPreferences();
            updateSubtitleStyle(this);
            updateOverlayClock();
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

    public void initializePlayer() {
        boolean isNetworkUri = Utils.isSupportedNetworkUri(mPrefs.mediaUri);
        haveMedia = mPrefs.mediaUri != null;

        // Fresh media — drop any container track names so the tap re-parses for this item.
        containerTracks.clear();
        resolvedTrackNames.clear();

        if (player != null) {
            player.removeListener(playerListener);
            player.clearMediaItems();
            player.release();
            player = null;
        }

        trackSelector = new DefaultTrackSelector(this);
        trackSelector.setParameters(trackSelector.buildUponParameters()
                .setAllowInvalidateSelectionsOnRendererCapabilitiesChange(true));
        if (mPrefs.tunneling) {
            trackSelector.setParameters(trackSelector.buildUponParameters()
                    .setTunnelingEnabled(true)
            );
        }
        switch (mPrefs.languageAudio) {
            case Prefs.TRACK_DEFAULT:
                break;
            case Prefs.TRACK_DEVICE:
                trackSelector.setParameters(trackSelector.buildUponParameters()
                        .setPreferredAudioLanguages(Utils.getDeviceLanguages())
                );
                break;
            default:
                trackSelector.setParameters(trackSelector.buildUponParameters()
                        .setPreferredAudioLanguages(mPrefs.languageAudio)
                );
        }
        final CaptioningManager captioningManager = (CaptioningManager) getSystemService(Context.CAPTIONING_SERVICE);
        if (!captioningManager.isEnabled()) {
            trackSelector.setParameters(trackSelector.buildUponParameters()
                    .setIgnoredTextSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            );
        }
        Locale locale = captioningManager.getLocale();
        if (locale != null) {
            trackSelector.setParameters(trackSelector.buildUponParameters()
                    .setPreferredTextLanguage(locale.getISO3Language())
            );
        }
        // https://github.com/google/ExoPlayer/issues/8571
        DefaultExtractorsFactory extractorsFactory = new DefaultExtractorsFactory()
                .setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS)
                .setTsExtractorTimestampSearchBytes(1500 * TsExtractor.TS_PACKET_SIZE);
        @SuppressLint("WrongConstant") RenderersFactory renderersFactory = new DefaultRenderersFactory(this)
                .setExtensionRendererMode(mPrefs.decoderPriority)
                .setMapDV7ToHevc(mPrefs.mapDV7ToHevc);

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

            if (!headers.isEmpty() || userAgent != null) {
                DefaultHttpDataSource.Factory defaultHttpDataSourceFactory = new DefaultHttpDataSource.Factory()
                        .setAllowCrossProtocolRedirects(true);
                if (userAgent != null) {
                    defaultHttpDataSourceFactory.setUserAgent(userAgent);
                }
                if (!headers.isEmpty()) {
                    defaultHttpDataSourceFactory.setDefaultRequestProperties(headers);
                }
                upstreamFactory = new DefaultDataSource.Factory(this, defaultHttpDataSourceFactory);
            }
        }

        final androidx.media3.datasource.DataSource.Factory dataSourceFactory = new TrackNameParsingDataSource.Factory(upstreamFactory, trackNameListener);
        playerBuilder.setMediaSourceFactory(
                new DefaultMediaSourceFactory(this, extractorsFactory).setDataSourceFactory(dataSourceFactory));

        player = playerBuilder.build();

        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build();
        player.setAudioAttributes(audioAttributes, true);

        if (mPrefs.skipSilence) {
            player.setSkipSilenceEnabled(true);
        }

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

        locked = false;

        if (haveMedia) {
            if (isNetworkUri) {
                timeBar.setBufferedColor(DefaultTimeBar.DEFAULT_BUFFERED_COLOR);
            } else {
                // https://github.com/google/ExoPlayer/issues/5765
                timeBar.setBufferedColor(0x33FFFFFF);
            }

            playerView.setResizeMode(mPrefs.resizeMode);

            if (mPrefs.resizeMode == AspectRatioFrameLayout.RESIZE_MODE_ZOOM) {
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
            if (apiAccess && apiSubs.size() > 0) {
                mediaItemBuilder.setSubtitleConfigurations(apiSubs);
            } else if (mPrefs.subtitleUri != null && Utils.fileExists(this, mPrefs.subtitleUri)) {
                MediaItem.SubtitleConfiguration subtitle = SubtitleUtils.buildSubtitle(this, mPrefs.subtitleUri, null, true);
                mediaItemBuilder.setSubtitleConfigurations(Collections.singletonList(subtitle));
            }
            if (!apiMediaItems.isEmpty()) {
                player.setMediaItems(new ArrayList<>(apiMediaItems), apiPlaylistStartIndex, mPrefs.getPosition());
            } else {
                player.setMediaItem(mediaItemBuilder.build(), mPrefs.getPosition());
            }

            try {
                if (loudnessEnhancer != null) {
                    loudnessEnhancer.release();
                }
                loudnessEnhancer = new LoudnessEnhancer(player.getAudioSessionId());
            } catch (Exception e) {
                e.printStackTrace();
            }

            notifyAudioSessionUpdate(true);

            videoLoading = true;

            updateLoading(true);

            if (mPrefs.getPosition() == 0L || apiAccess || apiAccessPartial) {
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
        }

        player.addListener(playerListener);
        player.prepare();

        if (restorePlayState) {
            restorePlayState = false;
            playerView.showController();
            playerView.setControllerShowTimeoutMs(PlayerActivity.CONTROLLER_TIMEOUT);
            player.setPlayWhenReady(true);
        }
    }

    private void savePlayer() {
        if (player != null) {
            mPrefs.updateBrightness(mBrightnessControl.currentBrightnessLevel);
            mPrefs.updateOrientation();

            if (haveMedia) {
                // Prevent overwriting temporarily inaccessible media position
                if (player.isCurrentMediaItemSeekable()) {
                    mPrefs.updatePosition(player.getCurrentPosition());
                }
                mPrefs.updateMeta(getSelectedTrack(C.TRACK_TYPE_AUDIO),
                        getSelectedTrack(C.TRACK_TYPE_TEXT),
                        playerView.getResizeMode(),
                        playerView.getVideoSurfaceView().getScaleX(),
                        player.getPlaybackParameters().speed);
            }
        }
    }

    public void releasePlayer() {
        releasePlayer(true);
    }

    public void releasePlayer(boolean save) {
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
            player.clearMediaItems();
            player.release();
            player = null;
        }
        stopSkipPolling();
        hideSkipButton();
        hideSkipNotification();
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
        if (buttonPlaylist != null) {
            buttonPlaylist.setVisibility(View.GONE);
        }
        updateButtons(false);
    }

    private class PlayerListener implements Player.Listener {
        @Override
        public void onAudioSessionIdChanged(int audioSessionId) {
            try {
                if (loudnessEnhancer != null) {
                    loudnessEnhancer.release();
                }
                loudnessEnhancer = new LoudnessEnhancer(audioSessionId);
            } catch (Exception e) {
                e.printStackTrace();
            }
            notifyAudioSessionUpdate(true);
        }

        @Override
        public void onMediaItemTransition(MediaItem mediaItem, int reason) {
            updateTopInfo();
            hideSkipButton();
            setupSkipSource();
        }

        @Override
        public void onEvents(Player player, Player.Events events) {
            // Media3 re-enables/brightens the prev/next arrows on navigation events (timeline, position
            // discontinuity, available commands) — exactly what fires while switching episodes. Re-assert
            // the disabled look after that update (deferred, so it wins) while the video is loading.
            if (episodeNavLoading && playerView != null) {
                playerView.post(() -> {
                    if (episodeNavLoading) {
                        applyEpisodeNavEnabled(false);
                    }
                });
            }
        }

        @Override
        public void onTracksChanged(Tracks tracks) {
            // Tracks are now known — (re)map any container names onto them, then refresh the header.
            resolveTrackNames();
            updateMediaInfo();
        }

        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
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

            if (!isPlaying) {
                PlayerActivity.locked = false;
            }

            if (isPlaying) {
                startSkipPolling();
            } else {
                stopSkipPolling();
            }
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

                // Ready — hide the spinner and re-enable the episode arrows. Done unconditionally (not only on
                // the initial open) so episode switches, which don't set videoLoading, are also cleared.
                updateLoading(false);
                setEpisodeNavLoading(false);

                if (!skipBuilt) {
                    rebuildSkip();
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
                                        if (play) {
                                            play = false;
                                            displayManager.unregisterDisplayListener(this);
                                            if (player != null) {
                                                player.play();
                                            }
                                            if (playerView != null) {
                                                playerView.hideController();
                                            }
                                        }
                                    }
                                };
                            }
                            displayManager.registerDisplayListener(displayListener, null);
                        }
                        switched = Utils.switchFrameRate(PlayerActivity.this, mPrefs.mediaUri, play);
                    }
                    if (!switched) {
                        if (displayManager != null) {
                            displayManager.unregisterDisplayListener(displayListener);
                        }
                        if (play) {
                            play = false;
                            player.play();
                            playerView.hideController();
                        }
                    }

                    if (mPrefs.speed <= 0.99f || mPrefs.speed >= 1.01f) {
                        player.setPlaybackSpeed(mPrefs.speed);
                    }
                    if (!apiAccess) {
                        setSelectedTracks(mPrefs.subtitleTrackId, mPrefs.audioTrackId);
                    }
                }
            } else if (state == Player.STATE_BUFFERING) {
                // Buffering (e.g. switching episodes) — show the spinner in place of play and disable the arrows.
                updateLoading(true);
                setEpisodeNavLoading(true);
            } else if (state == Player.STATE_ENDED) {
                playbackFinished = true;
                if (apiAccess) {
                    finish();
                }
            }
        }

        @Override
        public void onPlayerError(PlaybackException error) {
            updateLoading(false);
            if (error instanceof ExoPlaybackException) {
                final ExoPlaybackException exoPlaybackException = (ExoPlaybackException) error;
                if (exoPlaybackException.type == ExoPlaybackException.TYPE_SOURCE) {
                    releasePlayer(false);
                    return;
                }
                if (controllerVisible && controllerVisibleFully) {
                    showError(exoPlaybackException);
                } else {
                    errorToShow = exoPlaybackException;
                }
            }
        }
    }

    private void enableRotation() {
        try {
            if (Settings.System.getInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION) == 0) {
                Settings.System.putInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 1);
                restoreOrientationLock = true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    boolean useMediaStore() {
        final int targetSdkVersion = getApplicationContext().getApplicationInfo().targetSdkVersion;
        return (isTvBox && Build.VERSION.SDK_INT >= 30 && targetSdkVersion >= 30 && mPrefs.fileAccess.equals("auto")) || mPrefs.fileAccess.equals("mediastore");
    }

    private void openFile(Uri pickerInitialUri) {
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
        if ((id == null && trackType == C.TRACK_TYPE_AUDIO ) || player == null) {
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

        TrackSelectionParameters.Builder overridesBuilder = new TrackSelectionParameters.Builder(this);
        TrackSelectionOverride trackSelectionOverride = null;
        final List<Integer> tracks = new ArrayList<>(); tracks.add(0);
        if (subtitleGroup != null) {
            trackSelectionOverride = new TrackSelectionOverride(subtitleGroup, tracks);
            overridesBuilder.addOverride(trackSelectionOverride);
        }
        if (audioGroup != null) {
            trackSelectionOverride = new TrackSelectionOverride(audioGroup, tracks);
            overridesBuilder.addOverride(trackSelectionOverride);
        }

        if (player != null) {
            TrackSelectionParameters.Builder trackSelectionParametersBuilder = player.getTrackSelectionParameters().buildUpon();
            if (trackSelectionOverride != null) {
                trackSelectionParametersBuilder.setOverrideForType(trackSelectionOverride);
            }
            player.setTrackSelectionParameters(trackSelectionParametersBuilder.build());
        }
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
    }

    void showError(ExoPlaybackException error) {
        final String errorGeneral = error.getLocalizedMessage();
        String errorDetailed;

        switch (error.type) {
            case ExoPlaybackException.TYPE_SOURCE:
                errorDetailed = error.getSourceException().getLocalizedMessage();
                break;
            case ExoPlaybackException.TYPE_RENDERER:
                errorDetailed = error.getRendererException().getLocalizedMessage();
                break;
            case ExoPlaybackException.TYPE_UNEXPECTED:
                errorDetailed = error.getUnexpectedException().getLocalizedMessage();
                break;
            case ExoPlaybackException.TYPE_REMOTE:
            default:
                errorDetailed = errorGeneral;
                break;
        }

        showSnack(errorGeneral, errorDetailed);
    }

    void showSnack(final String textPrimary, final String textSecondary) {
        snackbar = Snackbar.make(coordinatorLayout, textPrimary, Snackbar.LENGTH_LONG);
        if (textSecondary != null) {
            snackbar.setAction(R.string.error_details, v -> {
                final AlertDialog.Builder builder = new AlertDialog.Builder(PlayerActivity.this);
                builder.setMessage(textSecondary);
                builder.setPositiveButton(android.R.string.ok, (dialogInterface, i) -> dialogInterface.dismiss());
                final AlertDialog dialog = builder.create();
                dialog.show();
            });
        }
        snackbar.setAnchorView(R.id.exo_bottom_bar);
        snackbar.show();
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

    void updateSubtitleStyle(final Context context) {
        final CaptioningManager captioningManager = (CaptioningManager) getSystemService(Context.CAPTIONING_SERVICE);
        final SubtitleView subtitleView = playerView.getSubtitleView();
        final boolean isTablet = Utils.isTablet(context);
        subtitlesScale = SubtitleUtils.normalizeFontScale(captioningManager.getFontScale(), isTvBox || isTablet);
        if (subtitleView != null) {
            final CaptioningManager.CaptionStyle userStyle = captioningManager.getUserStyle();
            final CaptionStyleCompat userStyleCompat = CaptionStyleCompat.createFromCaptionStyle(userStyle);
            final CaptionStyleCompat captionStyle = new CaptionStyleCompat(
                    userStyle.hasForegroundColor() ? userStyleCompat.foregroundColor : Color.WHITE,
                    userStyle.hasBackgroundColor() ? userStyleCompat.backgroundColor : Color.TRANSPARENT,
                    userStyle.hasWindowColor() ? userStyleCompat.windowColor : Color.TRANSPARENT,
                    userStyle.hasEdgeType() ? userStyleCompat.edgeType : CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                    userStyle.hasEdgeColor() ? userStyleCompat.edgeColor : Color.BLACK,
                    Typeface.create(userStyleCompat.typeface != null ? userStyleCompat.typeface : Typeface.DEFAULT,
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

    private void updateLoading(final boolean enableLoading) {
        if (enableLoading) {
            exoPlayPause.setVisibility(View.GONE);
            loadingProgressBar.setVisibility(View.VISIBLE);
        } else {
            loadingProgressBar.setVisibility(View.GONE);
            exoPlayPause.setVisibility(View.VISIBLE);
            if (focusPlay) {
                focusPlay = false;
                exoPlayPause.requestFocus();
            }
        }
    }

    // Shrink the built-in prev/next episode arrows (Media3 defaults render them as large as play/pause) and
    // take over their click handling so we can gate them while a video is loading. Media3 keeps updating the
    // buttons' enabled state on player events, so an OnClickListener guard — not setEnabled — is the reliable gate.
    private void setupEpisodeNavButtons() {
        final int size = Utils.dpToPx(40);
        final int padding = Utils.dpToPx(8);
        setupEpisodeNavButton(exoPrev, size, padding);
        setupEpisodeNavButton(exoNext, size, padding);
        if (exoPrev != null) {
            exoPrev.setOnClickListener(v -> {
                if (!episodeNavLoading && player != null) {
                    player.seekToPrevious();
                    resetHideCallbacks();
                }
            });
        }
        if (exoNext != null) {
            exoNext.setOnClickListener(v -> {
                if (!episodeNavLoading && player != null) {
                    player.seekToNext();
                    resetHideCallbacks();
                }
            });
        }
    }

    private void setupEpisodeNavButton(final ImageButton button, final int size, final int padding) {
        if (button == null) {
            return;
        }
        final ViewGroup.LayoutParams lp = button.getLayoutParams();
        lp.width = size;
        lp.height = size;
        button.setLayoutParams(lp);
        button.setPadding(padding, padding, padding, padding);
        button.setScaleType(ImageView.ScaleType.FIT_CENTER);
    }

    // Grey out and disable the prev/next episode arrows while a video is loading, using the same disabled
    // styling (enabled state + opacity) as the other control buttons via Utils.setButtonEnabled.
    private void setEpisodeNavLoading(final boolean loading) {
        episodeNavLoading = loading;
        applyEpisodeNavEnabled(!loading);
    }

    private void applyEpisodeNavEnabled(final boolean enabled) {
        if (exoPrev != null) {
            Utils.setButtonEnabled(this, exoPrev, enabled);
        }
        if (exoNext != null) {
            Utils.setButtonEnabled(this, exoNext, enabled);
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
        final int deleteVisible = (visible && haveMedia && Utils.isDeletable(this, mPrefs.mediaUri)) ? View.VISIBLE : View.INVISIBLE;
        final int nextVisible = (visible && haveMedia && (nextUri != null || (mPrefs.askScope && !isTvBox))) ? View.VISIBLE : View.INVISIBLE;
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
                haveMedia = false;
                setEndControlsVisible(false);
                playerView.setControllerShowTimeoutMs(-1);
            } else {
                skipToNext();
            }
        });
        builder.setNegativeButton(android.R.string.cancel, (dialog, which) -> {});
        final AlertDialog dialog = builder.create();
        dialog.show();
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
        if (isTvBox) {
            Utils.setButtonEnabled(this, exoSettings, true);
        } else {
            Utils.setButtonEnabled(this, exoSettings, enable);
        }
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