package com.brouken.player;

import static android.content.Context.UI_MODE_SERVICE;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.UiModeManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.LocaleList;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.util.Log;
import android.util.Rational;
import android.view.Display;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.core.text.HtmlCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.annotation.OptIn;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;

import com.obsez.android.lib.filechooser.ChooserDialog;
import com.sigpwned.chardet4j.Chardet;
import com.sigpwned.chardet4j.io.DecodedInputStreamReader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;

class Utils {

    public static final String FEATURE_FIRE_TV = "amazon.hardware.fire_tv";

    public static final String[] supportedExtensionsVideo = new String[] { "3gp", "avi", "m4v", "mkv", "mov", "mp4", "ts", "webm" };
    public static final String[] supportedExtensionsSubtitle = new String[] { "srt", "ssa", "ass", "vtt", "ttml", "dfxp", "xml" };

    public static final String[] supportedMimeTypesVideo = new String[] {
            // Local mime types on Android:
            MimeTypes.VIDEO_MATROSKA, // .mkv
            MimeTypes.VIDEO_MP4, // .mp4, .m4v
            MimeTypes.VIDEO_WEBM, // .webm
            "video/quicktime", // .mov
            "video/mp2ts", // .ts, but also incompatible .m2ts
            MimeTypes.VIDEO_H263, // .3gp
            "video/avi", // .avi
            "video/x-msvideo", // .avi, older mime table
            // For remote storages:
            "video/x-m4v", // .m4v
    };
    public static final String[] supportedMimeTypesSubtitle = new String[] {
            MimeTypes.APPLICATION_SUBRIP,
            MimeTypes.TEXT_SSA,
            MimeTypes.TEXT_VTT,
            MimeTypes.APPLICATION_TTML,
            "text/*",
            "application/octet-stream"
    };

    public static int dpToPx(int dp) {
        return (int) (dp * Resources.getSystem().getDisplayMetrics().density);
    }

    /** True when the user has turned animations off system-wide (developer options, accessibility). */
    public static boolean isReducedMotion(Context context) {
        return Settings.Global.getFloat(context.getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f;
    }

    public static float pxToDp(float px) {
        return px / Resources.getSystem().getDisplayMetrics().density;
    }

    public static boolean fileExists(final Context context, final Uri uri) {
        final String scheme = uri.getScheme();
        if (ContentResolver.SCHEME_CONTENT.equals(scheme)) {
            try {
                final InputStream inputStream = context.getContentResolver().openInputStream(uri);
                inputStream.close();
                return true;
            } catch (Exception e) {
                return false;
            }
        } else {
            String path;
            if (ContentResolver.SCHEME_FILE.equals(scheme)) {
                path = uri.getPath();
            } else {
                path = uri.toString();
            }
            final File file = new File(path);
            return file.exists();
        }
    }

    public static void toggleSystemUi(final Activity activity, final CustomPlayerView playerView, final boolean show) {
        if (Build.VERSION.SDK_INT >= 31) {
            Window window = activity.getWindow();
            if (window != null) {
                WindowInsetsController windowInsetsController = window.getInsetsController();
                if (windowInsetsController != null) {
                    if (show) {
                        windowInsetsController.show(WindowInsets.Type.systemBars());
                    } else {
                        windowInsetsController.hide(WindowInsets.Type.systemBars());
                    }
                }
            }
        } else {
            if (show) {
                playerView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
            } else {
                playerView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
            }
        }
    }

    public static String getFileName(Context context, Uri uri) {
        String result = null;
        try {
            if (ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
                try (Cursor cursor = context.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        final int columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                        if (columnIndex > -1)
                            result = cursor.getString(columnIndex);
                    }
                }
            }
            if (result == null) {
                result = uri.getPath();
                int cut = result.lastIndexOf('/');
                if (cut != -1) {
                    result = result.substring(cut + 1);
                }
            }
            if (result.indexOf(".") > 0)
                result = result.substring(0, result.lastIndexOf("."));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    // Some senders pass HTML-escaped text in intent extras (e.g. "В&#039;язниця").
    public static String unescapeHtml(String text) {
        if (text == null || text.indexOf('&') < 0) {
            return text;
        }
        return HtmlCompat.fromHtml(text, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim();
    }

    public static boolean isVolumeMin(final AudioManager audioManager) {
        int min = Build.VERSION.SDK_INT >= 28 ? audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC) : 0;
        return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) == min;
    }

    /**
     * The volume as shown to the user: 0-100% of the system range, plus one 10% step per boost level.
     * The raw stream index is device specific (15, 16, 150 …), so it is never displayed directly.
     */
    public static int getVolumePercent(final Context context, final AudioManager audioManager) {
        if (PlayerActivity.boostLevel > 0)
            return 100 + PlayerActivity.boostLevel * 10;
        if (!PlayerActivity.systemVolume)
            return Math.round(PlayerActivity.playerVolume);
        final int max = getVolume(context, true, audioManager);
        if (max <= 0)
            return 0;
        return Math.round(getVolume(context, false, audioManager) * 100f / max);
    }

    public static boolean canBoostVolume() {
        return PlayerActivity.boostProcessor != null || boostEffectUsable();
    }

    private static boolean boostEffectUsable() {
        try {
            return PlayerActivity.loudnessEnhancer != null && PlayerActivity.loudnessEnhancer.hasControl();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Pushes boostLevel into whichever boost actually works on this device: the LoudnessEnhancer effect
     * where it does (it compresses rather than clips, so it takes far more gain), otherwise the PCM
     * processor. Also called right after a LoudnessEnhancer is created, because boostLevel outlives both
     * the effect and the activity, so a fresh effect starts at zero gain while the level still says
     * otherwise.
     */
    static void applyBoost() {
        boolean applied = false;
        if (PlayerActivity.loudnessEnhancer != null) {
            try {
                PlayerActivity.loudnessEnhancer.setTargetGain(PlayerActivity.boostLevel * 200);
                PlayerActivity.loudnessEnhancer.setEnabled(PlayerActivity.boostLevel > 0);
                applied = true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (PlayerActivity.boostProcessor != null) {
            // Straight amplitude, so the gain matches what the OSD says (200% = twice as loud); clipping
            // rules out the effect's 20 dB ceiling anyway.
            PlayerActivity.boostProcessor.setGain(applied ? 1f : 1f + PlayerActivity.boostLevel * 0.1f);
        }
    }

    /**
     * Hearing warning for the boost zone, shown once per session however the volume was raised — gesture,
     * hardware keys, mouse wheel or joystick all end up here.
     */
    private static void warnAboutBoost(final Context context) {
        if (PlayerActivity.boostWarned || PlayerActivity.boostLevel <= 0)
            return;
        PlayerActivity.boostWarned = true;
        Toast.makeText(context, R.string.volume_high_warning, Toast.LENGTH_SHORT).show();
    }

    /**
     * The player's own attenuation, used instead of the system stream when systemVolume is off. It is a
     * multiplier on top of the system volume, so 100% means "as loud as the device currently is".
     */
    static void applyPlayerVolume() {
        if (PlayerActivity.player != null)
            PlayerActivity.player.setVolume(PlayerActivity.playerVolume / 100f);
    }

    /**
     * Absolute volume set from the vertical gesture: 0-100% maps onto the system range (or onto the
     * player's own attenuation while systemVolume is off), 101-200% leaves that maxed out and adds boost.
     * Displayed value is read back, so it never overstates what was actually applied.
     */
    public static void setVolumePercent(final Context context, final AudioManager audioManager, final CustomPlayerView playerView, final float percent) {
        playerView.removeCallbacks(playerView.textClearRunnable);

        if (PlayerActivity.systemVolume) {
            final int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            final int index = Math.round(Math.min(percent, 100f) / 100f * max);
            if (index != audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) {
                try {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0);
                } catch (RuntimeException e) {
                    // Setting the volume can be denied (Do Not Disturb, device policy)
                    e.printStackTrace();
                }
            }
        } else {
            PlayerActivity.playerVolume = Math.min(percent, 100f);
            applyPlayerVolume();
        }

        PlayerActivity.boostLevel = percent > 100f ? Math.min(10, Math.round((percent - 100f) / 10f)) : 0;
        applyBoost();
        warnAboutBoost(context);

        playerView.showVolume(getVolumePercent(context, audioManager));
    }

    public static void adjustVolume(final Context context, final AudioManager audioManager, final CustomPlayerView playerView, final boolean raise, boolean canBoost, boolean clear) {
        playerView.removeCallbacks(playerView.textClearRunnable);

        if (!canBoostVolume()) {
            canBoost = false;
        }

        int volume = 0;
        final boolean maxedOut;
        if (PlayerActivity.systemVolume) {
            volume = getVolume(context, false, audioManager);
            maxedOut = volume == getVolume(context, true, audioManager);
        } else {
            // Slightly below 100 to absorb float slop from repeated steps, which would otherwise leave
            // the level a hair under maximum and never let boost engage.
            maxedOut = PlayerActivity.playerVolume >= 99.5f;
        }

        // Boost only exists on top of a maxed-out level, so drop it whenever the level is below that:
        // a volume change outside the app, or a level carried over from the other volume mode.
        if (!maxedOut) {
            PlayerActivity.boostLevel = 0;
        }

        if (!maxedOut || (PlayerActivity.boostLevel == 0 && !raise)) {
            applyBoost();
            if (PlayerActivity.systemVolume) {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, raise ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER, AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE);
                final int volumeNew = getVolume(context, false, audioManager);
                // Custom volume step on Samsung devices (Sound Assistant)
                if (raise && volume == volumeNew) {
                    playerView.volumeUpsInRow++;
                } else {
                    playerView.volumeUpsInRow = 0;
                }
                if (playerView.volumeUpsInRow > 4 && !isVolumeMin(audioManager)) {
                    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE | AudioManager.FLAG_SHOW_UI);
                }
            } else {
                // Same step as the system's, so the button feels the same whichever mode is on
                final float step = 100f / Math.max(1, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
                PlayerActivity.playerVolume = Math.max(0f, Math.min(100f, PlayerActivity.playerVolume + (raise ? step : -step)));
                applyPlayerVolume();
            }
        } else {
            if (canBoost && raise && PlayerActivity.boostLevel < 10)
                PlayerActivity.boostLevel++;
            else if (!raise && PlayerActivity.boostLevel > 0)
                PlayerActivity.boostLevel--;

            applyBoost();
        }

        warnAboutBoost(context);
        playerView.showVolume(getVolumePercent(context, audioManager));

        if (clear) {
            playerView.postDelayed(playerView.textClearRunnable, CustomPlayerView.MESSAGE_TIMEOUT_KEY);
        }
    }

    private static int getVolume(final Context context, final boolean max, final AudioManager audioManager) {
        if (Build.VERSION.SDK_INT >= 30 && Build.MANUFACTURER.equalsIgnoreCase("samsung")) {
            try {
                Method method;
                Object result;
                Class<?> clazz = Class.forName("com.samsung.android.media.SemSoundAssistantManager");
                Constructor<?> constructor = clazz.getConstructor(Context.class);
                final Method getMediaVolumeInterval = clazz.getDeclaredMethod("getMediaVolumeInterval");
                result = getMediaVolumeInterval.invoke(constructor.newInstance(context));
                if (result instanceof Integer) {
                    int mediaVolumeInterval = (int) result;
                    if (mediaVolumeInterval < 10) {
                        method = AudioManager.class.getDeclaredMethod("semGetFineVolume", int.class);
                        result = method.invoke(audioManager, AudioManager.STREAM_MUSIC);
                        if (result instanceof Integer) {
                            if (max) {
                                return 150 / mediaVolumeInterval;
                            } else {
                                int fineVolume = (int) result;
                                return fineVolume / mediaVolumeInterval;
                            }
                        }
                    }
                }
            } catch (Exception e) {}
        }
        if (max) {
            return audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        } else {
            return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        }
    }

    public static void setButtonEnabled(final Context context, final ImageButton button, final boolean enabled) {
        button.setEnabled(enabled);
        button.setAlpha(enabled ?
                        (float) context.getResources().getInteger(R.integer.exo_media_button_opacity_percentage_enabled) / 100 :
                        (float) context.getResources().getInteger(R.integer.exo_media_button_opacity_percentage_disabled) / 100
                );
    }

    public static void showText(final CustomPlayerView playerView, final CharSequence text, final long timeout) {
        playerView.removeCallbacks(playerView.textClearRunnable);
        playerView.clearIcon();
        playerView.setCustomErrorMessage(text);
        playerView.postDelayed(playerView.textClearRunnable, timeout);
    }

    public static void showText(final CustomPlayerView playerView, final CharSequence text) {
        showText(playerView, text, 1200);
    }

    public enum Orientation {
        VIDEO(0, R.string.video_orientation_video),
        SYSTEM(1, R.string.video_orientation_system),
        UNSPECIFIED(2, R.string.video_orientation_system);

        public final int value;
        public final int description;

        Orientation(int type, int description) {
            this.value = type;
            this.description = description;
        }
    }

    @SuppressLint("SourceLockedOrientationActivity")
    public static void setOrientation(Activity activity, Orientation orientation) {
        switch (orientation) {
            case VIDEO:
                if (PlayerActivity.player != null) {
                    final Format format = PlayerActivity.player.getVideoFormat();
                    if (format != null && isPortrait(format))
                        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
                    else
                        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                } else {
                    activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                }

                break;
            case SYSTEM:
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                break;
            /*case SENSOR:
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
                break;*/
        }
    }

    public static Orientation getNextOrientation(Orientation orientation) {
        switch (orientation) {
            case VIDEO:
                return Orientation.SYSTEM;
            case SYSTEM:
            default:
                return Orientation.VIDEO;
        }
    }

    public static boolean isRotated(final Format format) {
        return format.rotationDegrees == 90 || format.rotationDegrees == 270;
    }

    public static boolean isPortrait(final Format format) {
        if (isRotated(format)) {
            return format.width > format.height;
        } else {
            return format.height > format.width;
        }
    }

    public static Rational getRational(final Format format) {
        if (isRotated(format))
            return new Rational(format.height, format.width);
        else
            return new Rational(format.width, format.height);
    }

    /**
     * Pads a side-panel's content for the system bars, the way every picker in this app wants it.
     *
     * The status bar is hidden while a picker is open (applyPickerBars), so its height is only breathing
     * room — but breathing room the content genuinely needs, since the window spans the full height and the
     * camera cutout lives up there. Hence the height is read IGNORING VISIBILITY: {@code getInsets()} reports
     * zero for a bar that is currently hidden, and a panel opened from another panel (the skip-offset and
     * sleep-timer panels come off a side menu, which has already turned the bars off) would then get no top
     * padding at all and put its header under the cutout. The playlist panel only ever escaped this by being
     * opened straight off the controls, while the bars were still up.
     *
     * In portrait the status-bar height reads well; landscape is much shorter (and its status-bar inset can
     * include the camera cutout), where that same height looks oversized — use a compact fixed inset there.
     * Pad the bottom for the nav/gesture bar. dp keeps it density/resolution-adaptive.
     *
     * @param insetSource any attached view, used to read the window insets
     * @param target      the view whose padding is set (horizontal padding comes from the caller's own grid)
     */
    public static void padForPickerInsets(final Activity activity, final UiMetrics ui, final View insetSource,
                                         final View target, final int hPad,
                                         final int extraTopPx, final int extraBottomPx) {
        final boolean landscape = activity.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        int padTop = landscape ? ui.pickerTopPadLand() : ui.dp(24);
        int padBottom = ui.overscanV();
        final WindowInsets rootInsets = insetSource.getRootWindowInsets();
        if (rootInsets != null) {
            if (Build.VERSION.SDK_INT >= 30) {
                if (!landscape) {
                    padTop = rootInsets.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars()).top;
                }
                padBottom = rootInsets.getInsetsIgnoringVisibility(
                        WindowInsets.Type.navigationBars()).bottom + ui.overscanV();
            } else {
                // No ignoring-visibility variant before 30, and the legacy inset drops to 0 just the same
                // once the bars are off — so the floor above stands in for it.
                if (!landscape) {
                    padTop = Math.max(padTop, rootInsets.getSystemWindowInsetTop());
                }
                padBottom = Math.max(padBottom, rootInsets.getSystemWindowInsetBottom() + ui.overscanV());
            }
        }
        target.setPadding(hPad, padTop + extraTopPx, hPad, padBottom + extraBottomPx);
    }

    public static String formatMilis(long time) {
        final int totalSeconds = Math.abs((int) time / 1000);
        final int seconds = totalSeconds % 60;
        final int minutes = totalSeconds % 3600 / 60;
        final int hours = totalSeconds / 3600;

        return (hours > 0 ? String.format("%d:%02d:%02d", hours, minutes, seconds) : String.format("%02d:%02d", minutes, seconds));
    }

    public static String formatMilisSign(long time) {
        if (time > -1000 && time < 1000)
            return formatMilis(time);
        else
            return (time < 0 ? "−" : "+") + formatMilis(time);
    }

    public static String formatChannels(int count) {
        switch (count) {
            case 1: return "1.0";
            case 2: return "2.0";
            case 3: return "2.1";
            case 4: return "4.0";
            case 5: return "5.0";
            case 6: return "5.1";
            case 7: return "6.1";
            case 8: return "7.1";
            default: return count + "ch";
        }
    }

    public static String formatBitrate(int bitrate) {
        if (bitrate <= 0) { // Format.NO_VALUE is -1
            return null;
        }
        if (bitrate >= 1_000_000) {
            return String.format(Locale.US, "%.1f Mbps", bitrate / 1_000_000f);
        }
        return (bitrate / 1000) + " kbps";
    }

    public static void log(final String text) {
        if (BuildConfig.DEBUG) {
            Log.d("JustPlayer", text);
        }
    }

    public static void setViewMargins(final View view, int marginLeft, int marginTop, int marginRight, int marginBottom) {
        final FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) view.getLayoutParams();
        layoutParams.setMargins(marginLeft, marginTop, marginRight, marginBottom);
        view.setLayoutParams(layoutParams);
    }

    public static void setViewParams(final View view, int paddingLeft, int paddingTop, int paddingRight, int paddingBottom, int marginLeft, int marginTop, int marginRight, int marginBottom) {
        view.setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom);
        setViewMargins(view, marginLeft, marginTop, marginRight, marginBottom);
    }

    public static boolean isDeletable(final Context context, final Uri uri) {
        try {
            if (ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
                try (Cursor cursor = context.getContentResolver().query(uri, new String[]{DocumentsContract.Document.COLUMN_FLAGS}, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        final int columnIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_FLAGS);
                        if (columnIndex > -1) {
                            int flags = cursor.getInt(columnIndex);
                            return (flags & DocumentsContract.Document.FLAG_SUPPORTS_DELETE) == DocumentsContract.Document.FLAG_SUPPORTS_DELETE;
                        }
                    }
                }
            } else if (ContentResolver.SCHEME_FILE.equals(uri.getScheme())) {
                if (Build.VERSION.SDK_INT >= 23) {
                    boolean hasPermission = context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            == PackageManager.PERMISSION_GRANTED;
                    if (!hasPermission) {
                        return false;
                    }
                }
                final File file = new File(uri.getSchemeSpecificPart());
                return file.canWrite();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public static boolean isSupportedNetworkUri(final Uri uri) {
        if (uri == null)
            return false;
        final String scheme = uri.getScheme();
        if (scheme == null)
            return false;
        return scheme.startsWith("http") || scheme.equals("rtsp");
    }

    // Matches the query/fragment of an http(s)/rtsp URL inside free text so it can be dropped.
    private static final java.util.regex.Pattern URL_QUERY =
            java.util.regex.Pattern.compile("((?:https?|rtsps?)://[^\\s?#]+)[?#][^\\s,)}\\]\"']*");

    // Removes the query string (and fragment) from any http(s)/rtsp URL found in the text, since query
    // strings are where tokens/session ids live. Keeps scheme, host, port and path; leaves the rest intact.
    public static String stripUrlQuery(final String text) {
        if (text == null)
            return null;
        return URL_QUERY.matcher(text).replaceAll("$1");
    }

    // Privacy-safe rendering of a media URI for crash/error reports: keeps scheme, host, port and path
    // (the route) only. Query string, userinfo and fragment are dropped, since query values carry
    // tokens/session ids; request headers are never attached anywhere.
    public static String uriToReportString(final Uri uri) {
        if (uri == null)
            return null;
        final String host = uri.getHost();
        if (host == null)
            return uri.getScheme();
        final StringBuilder sb = new StringBuilder();
        if (uri.getScheme() != null)
            sb.append(uri.getScheme()).append("://");
        sb.append(host);
        if (uri.getPort() != -1)
            sb.append(':').append(uri.getPort());
        if (uri.getEncodedPath() != null)
            sb.append(uri.getEncodedPath());
        return sb.toString();
    }

    public static boolean isTvBox(Context context) {
        final PackageManager pm = context.getPackageManager();

        // TV for sure
        UiModeManager uiModeManager = (UiModeManager) context.getSystemService(UI_MODE_SERVICE);
        if (uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION) {
            return true;
        }

        if (pm.hasSystemFeature(FEATURE_FIRE_TV)) {
            return true;
        }

        // Missing Files app (DocumentsUI) means box (some boxes still have non functional app or stub)
        if (!hasSAFChooser(pm)) {
            return true;
        }

        // Legacy storage no longer works on Android 11 (level 30)
        if (Build.VERSION.SDK_INT < 30) {
            // (Some boxes still report touchscreen feature)
            if (!pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN) && !pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
                return true;
            }

            if (pm.hasSystemFeature("android.hardware.hdmi.cec")) {
                return true;
            }

            if (Build.MANUFACTURER.equalsIgnoreCase("zidoo")) {
                return true;
            }
        }

        // Default: No TV - use SAF
        return false;
    }

    public static boolean hasSAFChooser(final PackageManager pm) {
        final Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("video/*");
        return intent.resolveActivity(pm) != null;
    }

    public static int normRate(float rate) {
        return (int)(rate * 100f);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    static void handleFrameRate(final PlayerActivity activity, float frameRate) {
        activity.runOnUiThread(() -> {
            boolean switchingModes = false;

            // A detached decor view answers null. Falling through to playIfCan rather than returning:
            // the caller has already told the player a switch is pending, so bailing out here left the
            // file on its first frame with no spinner and no error. Unreachable until the rate started
            // coming from Format — before that a stream had no rate at all and never entered this block.
            final Display display = frameRate > 0
                    ? activity.getWindow().getDecorView().getDisplay() : null;
            if (display != null) {
                Display.Mode[] supportedModes = display.getSupportedModes();
                Display.Mode activeMode = display.getMode();

                if (supportedModes.length > 1) {
                    // Refresh rate >= video FPS
                    List<Display.Mode> modesHigh = new ArrayList<>();
                    // Max refresh rate
                    Display.Mode modeTop = activeMode;
                    int modesResolutionCount = 0;

                    // Filter only resolutions same as current
                    for (Display.Mode mode : supportedModes) {
                        if (mode.getPhysicalWidth() == activeMode.getPhysicalWidth() &&
                                mode.getPhysicalHeight() == activeMode.getPhysicalHeight()) {
                            modesResolutionCount++;

                            if (normRate(mode.getRefreshRate()) >= normRate(frameRate))
                                modesHigh.add(mode);

                            if (normRate(mode.getRefreshRate()) > normRate(modeTop.getRefreshRate()))
                                modeTop = mode;
                        }
                    }

                    if (modesResolutionCount > 1) {
                        Display.Mode modeBest = null;

                        for (Display.Mode mode : modesHigh) {
                            // A whole multiple of the content rate, judged on the *relative* error. The
                            // centi-Hz remainder this replaces could not match 23.976 at all, since
                            // normRate truncates it to 2397, which divides neither 4795 (47.952 Hz) nor
                            // 11988 (119.88 Hz). But the tolerance has to stay under the 1/1001 that
                            // separates an NTSC rate from its integer neighbour, or 120 Hz also "matches"
                            // 23.976 content and, being the higher rate, beats the 119.88 mode that is the
                            // exact one. 2e-4 sits between the float noise on these values (~5e-6) and
                            // that 1e-3 gap.
                            final float ratio = mode.getRefreshRate() / frameRate;
                            final int multiple = Math.round(ratio);
                            if (multiple >= 1 && Math.abs(ratio - multiple) < multiple * 0.0002f) {
                                if (modeBest == null || normRate(mode.getRefreshRate()) > normRate(modeBest.getRefreshRate())) {
                                    modeBest = mode;
                                }
                            }
                        }

                        Window window = activity.getWindow();
                        WindowManager.LayoutParams layoutParams = window.getAttributes();

                        if (modeBest == null)
                            modeBest = modeTop;

                        switchingModes = !(modeBest.getModeId() == activeMode.getModeId());
                        if (switchingModes) {
                            layoutParams.preferredDisplayModeId = modeBest.getModeId();
                            window.setAttributes(layoutParams);
                        }
                    }
                }
            }

            if (!switchingModes) {
                playIfCan(activity);
            }
        });
    }

    // Reads activity.play now rather than a value captured before the frame-rate probe ran: that probe takes
    // seconds on a network file, and the user may have left in the meantime (onStop clears it).
    static void playIfCan(final PlayerActivity activity) {
        if (activity.play) {
            // Spending it, so mark it spent: the caller waiting for a display mode uses this flag to tell
            // "playback has not started" from "it started without a mode change", and left set it would
            // let a later display event start playback a second time, on its own.
            activity.play = false;
            if (PlayerActivity.player != null)
                PlayerActivity.player.play();
            if (activity.playerView != null)
                activity.playerView.hideController();
        }
    }

    public static boolean alternativeChooser(PlayerActivity activity, Uri initialUri, boolean video) {
        String startPath;
        if (initialUri != null && (new File(initialUri.getSchemeSpecificPart())).exists()) {
            startPath = initialUri.getSchemeSpecificPart();
        } else {
            startPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).getAbsolutePath();
        }

        final String[] suffixes = (video ? supportedExtensionsVideo : supportedExtensionsSubtitle);

        ChooserDialog chooserDialog = new ChooserDialog(activity, R.style.FileChooserStyle_Dark)
                .withStartFile(startPath)
                .withFilter(false, false, suffixes)
                .withChosenListener(new ChooserDialog.Result() {
                    @Override
                    public void onChoosePath(String path, File pathFile) {
                        activity.releasePlayer();
                        Uri uri = DocumentFile.fromFile(pathFile).getUri();
                        if (video) {
                            // Picking a file ends whatever session was running — the same thing the SAF
                            // chooser does in onActivityResult. This one is a dialog, so that callback
                            // never runs, and without this the launcher's return_result stayed armed
                            // while persistent mode came back on: finish() then reported this file
                            // against the launcher's episode, with a position of -1.
                            activity.resetApiAccess();
                            activity.mPrefs.updateMedia(activity, uri, null);
                            activity.searchSubtitles();
                        } else {
                            // Convert subtitles to UTF-8 if necessary
                            SubtitleUtils.clearCache(activity);
                            uri = Utils.convertToUTF(activity, uri);

                            activity.mPrefs.updateSubtitle(uri);
                        }
                        PlayerActivity.focusPlay = true;
                        activity.initializePlayer();
                    }
                })
                // to handle the back key pressed or clicked outside the dialog:
                .withOnCancelListener(new DialogInterface.OnCancelListener() {
                    public void onCancel(DialogInterface dialog) {
                        dialog.cancel(); // MUST have
                    }
                });
        chooserDialog
                .withOnBackPressedListener(dialog -> chooserDialog.goBack())
                .withOnLastBackPressedListener(dialog -> dialog.cancel());
        chooserDialog.build().show();

        return true;
    }

    public static Uri convertToUTF(PlayerActivity activity, Uri subtitleUri) {
        try {
            String scheme = subtitleUri.getScheme();
            if (scheme != null && scheme.toLowerCase().startsWith("http")) {
                List<Uri> urls = new ArrayList<>();
                urls.add(subtitleUri);
                SubtitleFetcher subtitleFetcher = new SubtitleFetcher(activity, urls);
                subtitleFetcher.start();
                return null;
            } else {
                InputStream inputStream = activity.getContentResolver().openInputStream(subtitleUri);
                return convertInputStreamToUTF(activity, subtitleUri, inputStream);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return subtitleUri;
    }

    public static Uri convertInputStreamToUTF(Context context, Uri subtitleUri, InputStream inputStream) {
        try {
            DecodedInputStreamReader decodedInputStreamReader = Chardet.decode(inputStream, StandardCharsets.UTF_8);
            Charset charset = decodedInputStreamReader.charset();
            if (!StandardCharsets.UTF_8.equals(charset)) {
                String filename = subtitleUri.getPath();
                filename = filename.substring(filename.lastIndexOf("/") + 1);
                final File file = new File(context.getCacheDir(), filename);
                final BufferedReader bufferedReader = new BufferedReader(decodedInputStreamReader);
                final BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(file));
                char[] buffer = new char[512];
                int num;
                int pass = 0;
                boolean success = true;
                while ((num = bufferedReader.read(buffer)) != -1) {
                    bufferedWriter.write(buffer, 0, num);
                    pass++;
                    if (pass * 512 > 2_000_000) {
                        success = false;
                        break;
                    }
                }
                bufferedWriter.close();
                bufferedReader.close();
                if (success) {
                    subtitleUri = Uri.fromFile(file);
                } else {
                    subtitleUri = null;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return subtitleUri;
    }

    public static boolean isPiPSupported(Context context) {
        PackageManager packageManager = context.getPackageManager();
        if (BuildConfig.FLAVOR_distribution.equals("amazon") && packageManager.hasSystemFeature(FEATURE_FIRE_TV)) {
            return false;
        }
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE);
    }

    public static Uri getMoviesFolderUri() {
        Uri uri = null;
        if (Build.VERSION.SDK_INT >= 26) {
            final String authority = "com.android.externalstorage.documents";
            final String documentId = "primary:" + Environment.DIRECTORY_MOVIES;
            uri = DocumentsContract.buildDocumentUri(authority, documentId);
        }
        return uri;
    }

    public static boolean isProgressiveContainerUri(final Uri uri) {
        String path = uri.getPath();
        if (path == null) {
            return false;
        }
        path = path.toLowerCase();
        for (String extension : supportedExtensionsVideo) {
            if (path.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    public static String[] getDeviceLanguages() {
        final List<String> locales = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 24) {
            final LocaleList localeList = Resources.getSystem().getConfiguration().getLocales();
            for (int i = 0; i < localeList.size(); i++) {
                addLanguage(locales, localeList.get(i));
            }
        } else {
            addLanguage(locales, Resources.getSystem().getConfiguration().locale);
        }
        return locales.toArray(new String[0]);
    }

    // The system list often names the same language twice (uk-UA, uk-Cyrl); the audio priority list
    // this seeds must not show it twice.
    private static void addLanguage(final List<String> languages, final Locale locale) {
        final String language = toIso3Language(locale.getLanguage());
        if (language != null && !languages.contains(language)) {
            languages.add(language);
        }
    }

    /**
     * Folds whatever a container declared ("en", "eng", "en-US") onto the ISO-639-2/T code the audio
     * priority list is keyed by, so a stored preference and a track's language can be compared at all.
     * Returns null when there is no usable language.
     *
     * Media3 normalizes first because Locale alone cannot: getISO3Language returns any 3-letter input
     * unchanged, and Matroska muxers routinely tag the bibliographic form ("ger", "fre", "cze"), which
     * would then never match the terminological code ("deu", "fra", "ces") everything else produces.
     */
    @OptIn(markerClass = UnstableApi.class)
    public static String toIso3Language(final String language) {
        if (language == null || language.isEmpty() || "und".equals(language)) {
            return null;
        }
        try {
            // Not new Locale(language): that treats the whole string as the language, so "en-US" would
            // have no 3-letter form at all.
            final String iso3 = Locale.forLanguageTag(
                    Util.normalizeLanguageCode(language.replace('_', '-'))).getISO3Language();
            return iso3.isEmpty() ? null : iso3;
        } catch (MissingResourceException e) {
            return null;
        }
    }

    /** The stored audio priority list ("ukr,eng") as a mutable list, blanks dropped. */
    public static List<String> splitLanguages(final String languages) {
        final List<String> list = new ArrayList<>();
        for (String language : languages.split(",")) {
            language = language.trim();
            if (!language.isEmpty() && !list.contains(language)) {
                list.add(language);
            }
        }
        return list;
    }

    public static ComponentName getSystemComponent(Context context, Intent intent) {
        List<ResolveInfo> resolveInfos = context.getPackageManager().queryIntentActivities(intent, 0);
        if (resolveInfos.size() < 2) {
            return null;
        }
        int systemCount = 0;
        ComponentName componentName = null;
        for (ResolveInfo resolveInfo : resolveInfos) {
            int flags = resolveInfo.activityInfo.applicationInfo.flags;
            boolean system = (flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            if (system) {
                systemCount++;
                componentName = new ComponentName(resolveInfo.activityInfo.packageName, resolveInfo.activityInfo.name);
            }
        }
        if (systemCount == 1) {
            return componentName;
        }
        return null;
    }

    public static float normalizeScaleFactor(float scaleFactor, float min) {
        return Math.max(min, Math.min(scaleFactor, 2.0f));
    }

    public static boolean isTablet(Context context) {
        return context.getResources().getConfiguration().smallestScreenWidthDp >= 720;
    }

    public static <K, V> void orderByValue(LinkedHashMap<K, V> m, final Comparator<? super V> c) {
        List<Map.Entry<K, V>> entries = new ArrayList<>(m.entrySet());
        Collections.sort(entries, (lhs, rhs) -> c.compare(lhs.getValue(), rhs.getValue()));
        m.clear();
        for(Map.Entry<K, V> e : entries) {
            m.put(e.getKey(), e.getValue());
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    public static void scanMediaStorage(Context context) {
        StorageManager storageManager = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
        List<StorageVolume> storageVolumes = storageManager.getStorageVolumes();
        List<String> storagePaths = new ArrayList<>();
        for (StorageVolume volume : storageVolumes) {
            File directory = volume.getDirectory();
            if (directory != null) {
                storagePaths.add(directory.getAbsolutePath());
            }
        }
        MediaScannerConnection.scanFile(context, storagePaths.toArray(new String[0]), new String[]{"*/*"}, null);
    }

    public static float getFrameRate(Context context, Uri videoUri) {
        MediaExtractor mediaExtractor = new MediaExtractor();
        ArrayList<Long> timestamps = new ArrayList<>();
        float frameRate = Format.NO_VALUE;
        int ignoreSamples = 30;
        try {
            mediaExtractor.setDataSource(context, videoUri, null);
            for (int i = 0; i < mediaExtractor.getTrackCount(); i++) {
                MediaFormat format = mediaExtractor.getTrackFormat(i);
                String mimeType = format.getString(MediaFormat.KEY_MIME);
                if (mimeType != null && mimeType.startsWith("video/")) {
                    mediaExtractor.selectTrack(i);
                    while (timestamps.size() < 350 + ignoreSamples) {
                        long timestamp = mediaExtractor.getSampleTime();
                        if (timestamp < 0) {
                            break;
                        }
                        timestamps.add(timestamp);
                        mediaExtractor.advance();
                    }
                    break;
                }
            }
            Collections.sort(timestamps);
            long totalFrameDuration = 0;
            for (int i = 1; i < (timestamps.size() - ignoreSamples); i++) {
                totalFrameDuration += (timestamps.get(i) - timestamps.get(i - 1));
            }
            if (timestamps.size() > 1) {
                float averageFrameDuration = (float) totalFrameDuration / (timestamps.size() - ignoreSamples - 1);
                frameRate = 1_000_000f / averageFrameDuration;
                if (frameRate > 23.95f && frameRate < 23.988f) {
                    frameRate = 24000f / 1001f;
                } else if (frameRate > 23.988 && frameRate < 24.1) {
                    frameRate = 24f;
                } else if (frameRate > 24.9 && frameRate < 25.1) {
                    frameRate = 25f;
                } else if (frameRate > 29.95f && frameRate < 29.985) {
                    frameRate = 30000f / 1001f;
                } else if (frameRate > 29.985 && frameRate < 30.1) {
                    frameRate = 30f;
                } else if (frameRate > 49.9f && frameRate < 50.1) {
                    frameRate = 50f;
                } else if (frameRate > 59.9f && frameRate < 59.97) {
                    frameRate = 60000f / 1001f;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            mediaExtractor.release();
        }
        return frameRate;
    }

    /**
     * A QR code for one short piece of text, or null if it cannot be drawn. Opaque black on white
     * whatever the theme: a code drawn in the dialog's own colours, or on nothing at all, is one no
     * camera will read.
     *
     * @param size the side in pixels; the encoder rounds it down to a whole number of modules
     */
    static android.graphics.Bitmap qrBitmap(final String text, final int size) {
        try {
            final com.google.zxing.common.BitMatrix matrix = new com.google.zxing.qrcode.QRCodeWriter()
                    .encode(text, com.google.zxing.BarcodeFormat.QR_CODE, size, size,
                            Collections.singletonMap(com.google.zxing.EncodeHintType.MARGIN, 2));
            final int width = matrix.getWidth();
            final int height = matrix.getHeight();
            final int[] pixels = new int[width * height];
            for (int y = 0; y < height; y++) {
                final int row = y * width;
                for (int x = 0; x < width; x++) {
                    pixels[row + x] = matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF;
                }
            }
            final android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(
                    width, height, android.graphics.Bitmap.Config.ARGB_8888);
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
            return bitmap;
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean switchFrameRate(final PlayerActivity activity, final Uri uri) {
        // preferredDisplayModeId only available on SDK 23+
        // ExoPlayer already uses Surface.setFrameRate() on Android 11+
        if (Build.VERSION.SDK_INT >= 23) {
            if (activity.frameRateSwitchThread != null) {
                activity.frameRateSwitchThread.interrupt();
            }
            activity.frameRateSwitchThread = new Thread(() -> {
                float frameRate = getFrameRate(activity, uri);
                Utils.handleFrameRate(activity, frameRate);
            });
            activity.frameRateSwitchThread.start();
            return true;
        } else {
            return false;
        }
    }
}
