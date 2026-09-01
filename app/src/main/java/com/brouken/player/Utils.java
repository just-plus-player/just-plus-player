package com.brouken.player;

import static android.content.Context.UI_MODE_SERVICE;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
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
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.drawable.ColorDrawable;
import android.graphics.Color;
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
import android.view.Gravity;
import android.os.SystemClock;
import android.util.Log;
import android.util.Rational;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.core.text.HtmlCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.annotation.OptIn;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;

import com.google.android.material.textfield.TextInputLayout;
import androidx.core.content.ContextCompat;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;

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
import java.text.Collator;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayDeque;
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
     * The one window recipe every end-docked player panel uses: a Material 3 detached side sheet — a card
     * inset from every edge with 16dp corners all round, over a dimmed picture, dismissible by a tap
     * outside.
     *
     * <p>Not {@code SideSheetDialog}, deliberately. That component is a fullscreen window, and a fullscreen
     * dialog window makes OxygenOS treat the panel as immersive and apply its two-swipe back-gesture guard,
     * where a plain window closes on one back. The scrim comes from the window's own dim instead.
     *
     * <p>The system bars are a margin here, not padding. A flat fill could run under the status bar
     * unnoticed; a card with a visible corner cannot, so what used to inset the content now insets the
     * card, and the content keeps only the padding its own design asks for. The heights are read
     * IGNORING VISIBILITY: a panel opened from another panel arrives with the bars already hidden, and
     * {@code getInsets()} would report zero and put the card's corner under the cutout.
     */
    public static void pickerWindow(final Activity activity, final UiMetrics ui, final Dialog dialog,
                                    final View content) {
        // Material's own margin for a detached sheet is 16dp; 8dp here, because on a phone held sideways
        // the panel is what the viewer came for and the strip of video beside it is not worth the room.
        // A television wants its overscan instead — the card has an edge to lose, where the full-bleed
        // fill before it had none.
        final int hMargin = Math.max(dpToPx(8), ui.overscanH());
        final int vMargin = Math.max(dpToPx(8), ui.overscanV());
        int barTop = 0;
        int barBottom = 0;
        final WindowInsets insets = activity.getWindow().getDecorView().getRootWindowInsets();
        if (insets != null) {
            if (Build.VERSION.SDK_INT >= 30) {
                barTop = insets.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars()).top;
                barBottom = insets.getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars()).bottom;
            } else {
                barTop = insets.getSystemWindowInsetTop();
                barBottom = insets.getSystemWindowInsetBottom();
            }
        }

        final MaterialShapeDrawable card = new MaterialShapeDrawable(
                ShapeAppearanceModel.builder().setAllCornerSizes(dpToPx(16)).build());
        // From the content's own theme, not the player's: the panels follow the appearance choice, so
        // the card is light when the app is light and black under AMOLED.
        card.setFillColor(ColorStateList.valueOf(MaterialColors.getColor(
                content, R.attr.colorSurface, ContextCompat.getColor(activity, R.color.sheet_surface))));
        content.setBackground(card);
        content.setClipToOutline(true); // so a full-bleed row's ripple stops at the rounded end

        final FrameLayout host = new FrameLayout(activity);
        final FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL);
        lp.setMargins(hMargin, barTop + vMargin, hMargin, barBottom + vMargin);
        host.addView(content, lp);

        dialog.setContentView(host);
        dialog.setCanceledOnTouchOutside(true);
        final Window window = dialog.getWindow();
        if (window == null) {
            return;
        }
        // The window carries the card plus its margins, so the card itself keeps the width the panel was
        // designed at. Capped short of the screen so the window never becomes a fullscreen one.
        final int screenW = activity.getResources().getDisplayMetrics().widthPixels;
        window.setLayout(
                Math.min(screenW - dpToPx(8),
                        ui.pickerWidthPx(activity.getResources().getConfiguration()) + 2 * hMargin),
                ViewGroup.LayoutParams.MATCH_PARENT);
        window.setGravity(Gravity.END);
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        // The picture behind a modal sheet steps back rather than competing with it.
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setDimAmount(0.4f);
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

    /**
     * Every line the app traces about itself. Kept in memory in every build, not only a debug one: the
     * subtitle search, the fetchers and the title lookup already say what they asked and what came back,
     * and that is exactly what a report about "it finds nothing for me" has to carry — but Log.d reaches
     * nobody who is not holding an adb cable. The report screen appends recentLog(), so the trace leaves
     * a phone by the Upload button and a TV box by its QR.
     *
     * <p>Bounded, and consecutive duplicates are folded: onTracksChanged fires several times per item,
     * so the same "not searching" line would otherwise push everything useful out of the window.
     */
    private static final int LOG_LINES = 200;
    private static final ArrayDeque<String> LOG = new ArrayDeque<>();
    private static final long LOG_BASE_MS = SystemClock.elapsedRealtime();
    private static String lastLogged;
    private static int lastLoggedRepeats;

    public static void log(final String text) {
        if (BuildConfig.DEBUG) {
            Log.d("JustPlayer", text);
        }
        synchronized (LOG) {
            if (text.equals(lastLogged)) {
                lastLoggedRepeats++;
                // Rewrite the tail rather than grow: the count is the information, the repetition is not.
                LOG.removeLast();
                LOG.addLast(logLine(text) + " (x" + (lastLoggedRepeats + 1) + ")");
                return;
            }
            lastLogged = text;
            lastLoggedRepeats = 0;
            while (LOG.size() >= LOG_LINES) {
                LOG.removeFirst();
            }
            LOG.addLast(logLine(text));
        }
    }

    // Seconds since the process started rather than a wall clock: what a reader needs from this is how
    // long a source took and where a fifteen-second budget ran out, not what time it was.
    private static String logLine(final String text) {
        final long ms = SystemClock.elapsedRealtime() - LOG_BASE_MS;
        return String.format(Locale.US, "%6.2f %s", ms / 1000f, text);
    }

    /** The trace so far, oldest first; empty string when nothing has been traced. */
    public static String recentLog() {
        synchronized (LOG) {
            if (LOG.isEmpty()) {
                return "";
            }
            final StringBuilder sb = new StringBuilder();
            for (String line : LOG) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(line);
            }
            return sb.toString();
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

    /**
     * A context for dialogs raised from the player, themed by the appearance choice instead of by the
     * window that raises them. The player's own theme is dark by design, so a dialog built against it
     * cannot follow that choice at all: this wraps the chosen night mode around the configuration and
     * hands back a DayNight theme carrying the app's colour roles.
     *
     * Every view a dialog builds for itself has to come from this context too, or dark text lands on a
     * light panel.
     */
    public static Context dialogContext(final Context base) {
        String mode = Prefs.getThemeMode(base);
        if (Prefs.THEME_SYSTEM.equals(mode)) {
            if (isTvBox(base)) {
                // A TV box has no system theme worth following, and PlayerActivity makes dark the
                // default there — so on TV a dialog follows the app rather than the box.
                mode = Prefs.THEME_DARK;
            } else {
                final int night = base.getResources().getConfiguration().uiMode
                        & Configuration.UI_MODE_NIGHT_MASK;
                mode = night == Configuration.UI_MODE_NIGHT_YES ? Prefs.THEME_DARK : Prefs.THEME_LIGHT;
            }
        }
        // Wrapped straight around whatever it was handed — an Activity, in every real call — because a
        // ContextThemeWrapper keeps its base's window token and a dialog needs one to exist at all.
        final android.view.ContextThemeWrapper themed = new android.view.ContextThemeWrapper(base,
                Prefs.THEME_LIGHT.equals(mode) ? R.style.Theme_Dialogs_Light
                        : R.style.Theme_Dialogs_Dark);
        // AMOLED is part of the same appearance choice, and a panel is the largest dark surface the app
        // ever puts on screen — the one place the option is worth the most.
        if (!Prefs.THEME_LIGHT.equals(mode) && Prefs.isAmoledBlack(base)) {
            themed.getTheme().applyStyle(R.style.ThemeOverlay_JustPlus_Amoled, true);
        }
        return themed;
    }

    /**
     * The strip a dialog's fields stand in, inset to the dialog's own text margin so a field lines
     * up with the title above it.
     */
    static LinearLayout dialogFields(final Context context) {
        final LinearLayout fields = new LinearLayout(context);
        fields.setOrientation(LinearLayout.VERTICAL);
        final int pad = dpToPx(24);
        fields.setPadding(pad, 0, pad, 0);
        return fields;
    }

    /**
     * Adds a Material text field to {@link #dialogFields} and hands back the editor, which is what
     * the dialog reads and writes; the box around it needs nothing further said to it.
     */
    static EditText textField(final ViewGroup fields, final CharSequence hint) {
        return textField(fields, hint, null);
    }

    /**
     * The same, with an example of what to type. A label has to be a word or two because it shrinks
     * onto the outline and stays there for good; an example is longer and is only worth reading with
     * the field empty and in front of you, which is exactly when a placeholder shows.
     */
    static EditText textField(final ViewGroup fields, final CharSequence hint,
                              final CharSequence placeholder) {
        final TextInputLayout field = (TextInputLayout) LayoutInflater.from(fields.getContext())
                .inflate(R.layout.dialog_text_field, fields, false);
        field.setHint(hint);
        field.setPlaceholderText(placeholder);
        fields.addView(field);
        return field.getEditText();
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

            // A detached decor view answers null. Falling through to the settled path rather than returning:
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
                activity.frameRateSettled();
            }
        });
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
        return convertInputStreamToUTF(context, subtitleUri, inputStream, null);
    }

    /**
     * @param preferredName what to call the cached copy, or null to take the name from the URI. A
     *                      subtitle found online has no useful name in its URL — often none at all,
     *                      just an id — while the caller knows the language, and the name is where
     *                      both the language and the label are read back from.
     */
    public static Uri convertInputStreamToUTF(Context context, Uri subtitleUri, InputStream inputStream,
                                              String preferredName) {
        try {
            DecodedInputStreamReader decodedInputStreamReader = Chardet.decode(inputStream, StandardCharsets.UTF_8);
            Charset charset = decodedInputStreamReader.charset();
            // A subtitle pulled off the network is copied even when it needs no re-encoding. The URI is
            // remembered (Prefs.subtitleUri) and re-read whenever the player is rebuilt — returning from
            // the settings screen does exactly that — and by then a temporary download link may be gone.
            // fileExists() settles it anyway: it answers false for any http URI, so a remembered network
            // subtitle was simply dropped and the track vanished. A local copy is a real file to both.
            final boolean remote = isSupportedNetworkUri(subtitleUri);
            if (!StandardCharsets.UTF_8.equals(charset) || remote) {
                String filename = preferredName;
                if (filename == null) {
                    filename = subtitleUri.getPath();
                    filename = filename.substring(filename.lastIndexOf("/") + 1);
                }
                // The name carries the format and often the language, which is how both are recovered
                // later. The copy is unpacked and re-encoded, so the wrapper extension has to go, and
                // proxied URLs end in an opaque id instead — give those something to parse.
                if (filename.endsWith(".gz")) {
                    filename = filename.substring(0, filename.length() - 3);
                }
                if (!filename.contains(".")) {
                    filename = (filename.isEmpty() ? "subtitle" : filename) + ".srt";
                }
                File file = null;
                boolean success = true;
                try {
                    final BufferedReader bufferedReader = new BufferedReader(decodedInputStreamReader);
                    final char[] buffer = new char[512];
                    // The head decides the name. An index is free to hand over ASS or WebVTT under a
                    // name the caller invented, and since the format is read back off that name, a
                    // mislabelled copy goes to the wrong parser and shows nothing at all — which reads
                    // as a subtitle that was found, switched on, and simply is not there.
                    int num = fill(bufferedReader, buffer);
                    final String head = new String(buffer, 0, num);
                    file = new File(context.getCacheDir(), nameByFormat(filename, head));
                    final BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(file));
                    int pass = 0;
                    while (num > 0) {
                        bufferedWriter.write(buffer, 0, num);
                        pass++;
                        if (pass * buffer.length > 2_000_000) {
                            success = false;
                            break;
                        }
                        num = fill(bufferedReader, buffer);
                    }
                    bufferedWriter.close();
                    bufferedReader.close();
                } catch (IOException e) {
                    // Out of space, most likely. Half a subtitle is worse than none: delete it and
                    // hand back the URL it came from, which still plays for as long as this player
                    // instance lives.
                    success = false;
                    e.printStackTrace();
                }
                if (success && file != null) {
                    trimSubtitleCache(context.getCacheDir());
                    subtitleUri = Uri.fromFile(file);
                } else {
                    if (file != null) {
                        file.delete();
                    }
                    if (!remote) {
                        subtitleUri = null;
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return subtitleUri;
    }

    /**
     * Reads until {@code buffer} is full or the stream ends, and answers how much of it is filled —
     * 0 at the end. {@link java.io.Reader#read(char[])} may hand back fewer characters than asked for
     * and over a decoded network stream routinely hands back one, which is too little to recognise a
     * format header by and makes a count of reads a useless stand-in for a count of characters.
     */
    private static int fill(final BufferedReader reader, final char[] buffer) throws IOException {
        int total = 0;
        while (total < buffer.length) {
            final int read = reader.read(buffer, total, buffer.length - total);
            if (read == -1) {
                break;
            }
            total += read;
        }
        return total;
    }

    /**
     * The extension the content asks for, replacing whatever the name arrived with. Left alone when
     * the head says nothing recognisable: SubRip has no header to go by, so an unremarkable file is
     * taken at its name.
     */
    private static String nameByFormat(final String filename, final String head) {
        final String extension;
        if (head.contains("[Script Info]")) {
            extension = ".ass";
        } else if (head.contains("WEBVTT")) {
            extension = ".vtt";
        } else if (head.contains("<tt ") || head.contains("<tt\n") || head.contains("<?xml")) {
            extension = ".ttml";
        } else {
            return filename;
        }
        if (filename.endsWith(extension)) {
            return filename;
        }
        final int dot = filename.lastIndexOf('.');
        return (dot > 0 ? filename.substring(0, dot) : filename) + extension;
    }

    /** How many downloaded subtitles are kept. Roughly a season at 30-60 KB each. */
    private static final int SUBTITLE_CACHE_KEEP = 20;

    /**
     * Keeps the downloaded-subtitle copies to a fixed number, oldest deleted first.
     *
     * Nothing else bounds them: they are named per title and language, so re-watching overwrites, but
     * every new episode leaves another file behind and nothing ever expires. The system does clear an
     * app's cache under storage pressure, and {@link SubtitleUtils#clearCache} empties it on several
     * unrelated occasions — neither is a plan, and neither runs before the disk is already tight.
     */
    private static void trimSubtitleCache(File cacheDir) {
        // Every extension, not only .srt: the copy is named after what it turned out to be, and one
        // the trim does not recognise would sit in the cache for good.
        final File[] files = cacheDir.listFiles((dir, name) ->
                name.startsWith("subs.") && SubtitleUtils.hasSubtitleExtension(name));
        if (files == null || files.length <= SUBTITLE_CACHE_KEEP) {
            return;
        }
        java.util.Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        for (int i = 0; i < files.length - SUBTITLE_CACHE_KEEP; i++) {
            files[i].delete();
        }
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

    /**
     * Every language that can be preferred, code to label ("Ukrainian [ukr]"), collated by label.
     *
     * <p>Walks a few hundred locales, so it is worth calling once per screen rather than per row —
     * both the settings priority lists and the manual subtitle search ask for the same map.
     */
    public static LinkedHashMap<String, String> allLanguages() {
        final LinkedHashMap<String, String> languages = new LinkedHashMap<>();
        for (final Locale locale : Locale.getAvailableLocales()) {
            try {
                // MissingResourceException: Couldn't find 3-letter language code for zz
                final String key = locale.getISO3Language();
                if (languages.containsKey(key)) {
                    // Hundreds of locales collapse onto the same language here, and the display name
                    // never depends on region or script — resolving it again only burns main-thread
                    // time while the screen opens.
                    continue;
                }
                String language = locale.getDisplayLanguage();
                final int length = language.offsetByCodePoints(0, 1);
                if (!language.isEmpty()) {
                    language = language.substring(0, length).toUpperCase(locale) + language.substring(length);
                }
                languages.put(key, language + " [" + key + "]");
            } catch (MissingResourceException e) {
                e.printStackTrace();
            }
        }
        final Collator collator = Collator.getInstance();
        collator.setStrength(Collator.PRIMARY);
        orderByValue(languages, collator::compare);
        return languages;
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

    /**
     * The best-ranked of {@code wanted} that a track name gives away, or null. Muxers routinely leave a
     * track's language tag empty and write the language into its name instead — "rus", "RUS #01 ENG #03",
     * "rus/eng/por/spa" — where nothing that reads {@link androidx.media3.common.Format#language} can
     * see it.
     *
     * <p>Matched this way round on purpose: the wanted codes are looked for in the name, rather than the
     * name read for whatever language it might hold. Locale hands back any three-letter word as its own
     * ISO-3 code (see {@link #toIso3Language}), so "DUB", "WEB" and "SUB" would each otherwise pass for
     * a language of their own.
     */
    public static String languageInName(final String name, final List<String> wanted) {
        if (name == null || name.isEmpty() || wanted.isEmpty()) {
            return null;
        }
        int best = wanted.size();
        // Three-letter tokens only. Two-letter codes are real languages but collide with ordinary words
        // ("is", "no", "it"), and a release name is largely made of those; a name spelling the language
        // out ("Russian") is not read either.
        for (final String token : name.split("[^A-Za-z]+")) {
            if (token.length() != 3) {
                continue;
            }
            final String language = toIso3Language(token);
            final int rank = language == null ? -1 : wanted.indexOf(language);
            if (rank >= 0 && rank < best) {
                best = rank;
            }
        }
        return best < wanted.size() ? wanted.get(best) : null;
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
