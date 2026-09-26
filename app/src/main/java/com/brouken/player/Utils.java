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
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.graphics.Color;
import android.graphics.Rect;
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
import android.view.KeyEvent;
import android.os.SystemClock;
import android.util.Log;
import android.util.Rational;
import android.util.StateSet;
import android.view.Display;
import android.view.LayoutInflater;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.ViewOutlineProvider;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.annotation.RequiresApi;
import androidx.core.text.HtmlCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.annotation.OptIn;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;

import com.google.android.material.textfield.TextInputLayout;
import androidx.core.graphics.ColorUtils;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.animation.AnimationUtils;
import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;

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

public class Utils {

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
        // A media server hands out titles, not file names, so there is never an extension to take
        // off - not even one that looks exactly like an extension, which is what the test below
        // cannot tell: "American.Psycho.2000.BDRip.AVC.AC3" is a whole title on a NAS. This is where
        // the trimming was first seen doing harm, "04. Троллейный усилитель" arriving in the player
        // as "04" while its siblings in the folder playlist, which come from the listing, kept their
        // whole titles.
        if (DlnaFiles.speaks(uri) && uri.getLastPathSegment() != null) {
            return uri.getLastPathSegment();
        }
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
            final int dot = result.lastIndexOf(".");
            if (dot > 0 && looksLikeExtension(result.substring(dot + 1)))
                result = result.substring(0, dot);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    /**
     * Whether what follows the last dot of a name is an extension, rather than part of the name.
     *
     * <p>Every extension this app opens is short and is letters and digits — mkv, m2ts, srt, ttml —
     * so a tail that is neither is not one. Taking off whatever follows the last dot regardless is
     * what turned "Cube.1997.BluRay.DTS.x264-DON" (a file saved without an extension) into
     * "Cube.1997.BluRay.DTS", and it is why a name whose only dot follows an episode number lost
     * everything after it. Five characters rather than the four the longest known extension has, so
     * that one nobody listed yet is still recognised.
     */
    private static boolean looksLikeExtension(final String tail) {
        if (tail.isEmpty() || tail.length() > 5) {
            return false;
        }
        for (int i = 0; i < tail.length(); i++) {
            final char c = tail.charAt(i);
            // ASCII only: isLetterOrDigit is true of Cyrillic too, and no extension is written in it.
            if (c > 127 || !Character.isLetterOrDigit(c)) {
                return false;
            }
        }
        return true;
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
            return 100 + Math.round(PlayerActivity.boostLevel * 10);
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
                PlayerActivity.loudnessEnhancer.setTargetGain(Math.round(PlayerActivity.boostLevel * 200));
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
        if (context instanceof PlayerActivity) {
            ((PlayerActivity) context).showNotice(context.getString(R.string.volume_high_warning), false,
                    R.drawable.ic_volume_up_24dp);
        }
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

        PlayerActivity.boostLevel = percent > 100f ? Math.min(10f, (percent - 100f) / 10f) : 0f;
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
            PlayerActivity.boostLevel = 0f;
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
            // To the next whole step rather than by one, so a key pressed after a drag left the level
            // at 13.7 lands on 20 and not on 23.7.
            if (canBoost && raise && PlayerActivity.boostLevel < 10)
                PlayerActivity.boostLevel = Math.min(10f, (float) Math.floor(PlayerActivity.boostLevel) + 1f);
            else if (!raise && PlayerActivity.boostLevel > 0)
                PlayerActivity.boostLevel = Math.max(0f, (float) Math.ceil(PlayerActivity.boostLevel) - 1f);

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
     * A 16:9 box that shows a frame of the video inside it, and a quiet glyph while there is none.
     *
     * <p>Glide carries a video decoder, so a local file can be asked for a frame of itself with no
     * extra machinery — which is the whole reason a poster appears here at all: nothing sends artwork
     * for a file on the device, and until now a local video was drawn as an empty box. A frame is
     * taken a second in rather than at zero, because a great many files open on black or on a fade
     * and the first frame of those is a preview of nothing.
     *
     * <p>A remote url is left with the glyph on purpose: asking it for a frame means downloading the
     * video to look at one.
     *
     * @param artwork a picture sent for this item, which wins when there is one
     * @param media   the item itself, asked for a frame when there is no artwork
     * @param glyphRes what stands in the box until a frame arrives, and instead of one for
     *                 anything that has no frame to give - a folder
     */
    static FrameLayout previewBox(final Context ctx, final Uri artwork, final Uri media,
                                  final int cornerPx, final int glyphPx, final int glyphRes) {
        final FrameLayout box = previewBox(ctx, cornerPx, glyphPx);
        bindPreview(box, artwork, media, glyphRes, glyphPx);
        return box;
    }

    /**
     * An empty still, built once and filled as often as it is asked for.
     *
     * <p>Split from {@link #bindPreview} because a list recycles its rows: building a new box, a new
     * poster and a new request on every bind is what made a folder flicker on every arrival - the
     * views were thrown away and made again while the pictures they already held were still good.
     * Glide replaces the request on a view it is already loading into, so one poster can serve one
     * file after another without either showing through.
     */
    /** What a still's stand-in glyph is painted: the secondary ink, well down, on the box's plate. */
    private static ColorStateList glyphTint(final Context ctx) {
        return ColorStateList.valueOf(ColorUtils.setAlphaComponent(
                MaterialColors.getColor(ctx, R.attr.colorOnSurfaceVariant,
                        ContextCompat.getColor(ctx, R.color.ink_secondary)), 0x5C));
    }

    static FrameLayout previewBox(final Context ctx, final int cornerPx, final int glyphPx) {
        // Here because every still in the app is built here, and it is a no-op after the first.
        VideoThumbs.register(ctx);
        final FrameLayout box = new FrameLayout(ctx);
        box.setBackgroundColor(MaterialColors.getColor(ctx, R.attr.colorSurfaceContainerHighest,
                ContextCompat.getColor(ctx, R.color.thumb_box)));
        box.setClipToOutline(true);
        box.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(final View view, final Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerPx);
            }
        });

        // What stands behind a picture that does not fill the frame. A media server's album art is a
        // cover, which is taller than it is wide - 114 x 160 on the server this was measured against -
        // and a cover fitted into a 16:9 still leaves two thirds of the frame flat grey, which is what
        // it looked like. This is the same picture, cropped to fill and turned right down, so the
        // frame carries the film's own colour instead of the furniture's.
        //
        // The blur is the cheapest one there is: the copy is fetched at a dozen pixels across and the
        // view scales it up, which is a bilinear smear and costs no filter, no library and no second
        // decode worth the name.
        final ImageView backdrop = new ImageView(ctx);
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backdrop.setAlpha(0.5f);
        backdrop.setVisibility(View.GONE);
        box.addView(backdrop, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        final ImageView poster = new ImageView(ctx);
        // Fitted, not cropped: a still is 16:9 and fills the frame, and a tall poster keeps its whole
        // picture with the frame's own colour beside it rather than losing its head and its feet.
        poster.setScaleType(ImageView.ScaleType.FIT_CENTER);
        box.addView(poster, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // The glyph is the placeholder as well as the fallback: it stands there while the frame is
        // being decoded and goes when it arrives, so a box is never blank and never reads as a fault.
        final ImageView blank = new ImageView(ctx);
        blank.setImageTintList(glyphTint(ctx));
        final FrameLayout.LayoutParams blankLp = new FrameLayout.LayoutParams(glyphPx, glyphPx);
        blankLp.gravity = Gravity.CENTER;
        box.addView(blank, blankLp);

        // The track and the run, made here and hidden until a row asks for them: a view created on a
        // bind is a view destroyed on the next one.
        final View track = new View(ctx);
        track.setBackgroundColor(0x80000000);
        track.setVisibility(View.GONE);
        box.addView(track, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                dpToPx(4), Gravity.BOTTOM | Gravity.START));

        final View run = new View(ctx);
        run.setVisibility(View.GONE);
        box.addView(run, new FrameLayout.LayoutParams(0, dpToPx(4), Gravity.BOTTOM | Gravity.START));
        return box;
    }

    /**
     * Points an existing still at a file: the glyph it falls back to, the frame it shows, and the two
     * state views reset. Cancels whatever the poster was loading, so a recycled row never finishes the
     * previous file's decode into the new file's box.
     */
    static void bindPreview(final FrameLayout box, final Uri artwork, final Uri media,
                            final int glyphRes, final int glyphPx) {
        // The same file as last time is the common case, not the exception: a list re-lists itself on
        // every arrival - coming back from the player, coming back from Settings - and clearing a
        // poster that already holds the right frame is a blank row for as long as the decode takes.
        // Asked here so nobody has to remember to ask it.
        final Uri wanted = artwork != null ? artwork : media;
        final String key = (wanted == null ? "" : wanted.toString()) + " " + glyphRes;
        if (key.equals(box.getTag())) {
            return;
        }
        box.setTag(key);
        final ImageView backdrop = (ImageView) box.getChildAt(0);
        final ImageView poster = (ImageView) box.getChildAt(1);
        final ImageView blank = (ImageView) box.getChildAt(2);
        final ViewGroup.LayoutParams glyphLp = blank.getLayoutParams();
        if (glyphLp.width != glyphPx) {
            glyphLp.width = glyphPx;
            glyphLp.height = glyphPx;
            blank.setLayoutParams(glyphLp);
        }
        box.getChildAt(3).setVisibility(View.GONE);
        box.getChildAt(4).setVisibility(View.GONE);
        blank.setImageTintList(glyphTint(box.getContext()));
        blank.setImageResource(glyphRes);
        blank.setVisibility(View.VISIBLE);
        poster.setVisibility(View.VISIBLE);
        Glide.with(box.getContext()).clear(poster);
        poster.setImageDrawable(null);
        backdrop.setImageDrawable(null);
        backdrop.setVisibility(View.GONE);

        final Uri source = artwork != null ? artwork : frameSource(media);
        if (source == null) {
            poster.setVisibility(View.GONE);
            return;
        }
        RequestBuilder<Bitmap> request = Glide.with(box.getContext()).asBitmap().load(source);
        if (artwork == null) {
            // A second in, so a film that opens on black is not a black row. Kept for the address the
            // system has a thumbnail for as well: VideoThumbs answers that one first and this is what
            // decodes it if the system cannot.
            request = request.frame(1_000_000L);
        }
        request.listener(new RequestListener<Bitmap>() {
            @Override
            public boolean onLoadFailed(final GlideException e, final Object model,
                                        final Target<Bitmap> target, final boolean firstResource) {
                poster.setVisibility(View.GONE);
                blank.setVisibility(View.VISIBLE);
                return false;
            }

            @Override
            public boolean onResourceReady(final Bitmap resource, final Object model,
                                           final Target<Bitmap> target, final DataSource source,
                                           final boolean firstResource) {
                blank.setVisibility(View.GONE);
                // Out of the picture that just arrived rather than fetched again: a second request per
                // row doubles what a media server is asked for at once, and a small one answers eight
                // rows and sixteen requests by refusing some of them - which is one row in a list
                // wearing the glyph for no reason a viewer could guess at.
                //
                // For every picture and not only a server's: a film shot on a telephone is 9:16 and
                // fitted into a 16:9 still it is the same strip on the same grey. Where the picture
                // does fill the frame - which is most of them - the backdrop is behind it and nothing
                // of it can be seen, and a bitmap of twelve by sixteen is not a cost worth branching
                // on to avoid.
                if (resource.getWidth() > 0 && resource.getHeight() > 0) {
                    backdrop.setImageBitmap(
                            Bitmap.createScaledBitmap(resource, 12, 16, true));
                    backdrop.setVisibility(View.VISIBLE);
                }
                return false;
            }
        }).into(poster);
    }

    /**
     * The track the played run sits on: the whole width of the still, dimmed, 4dp at its foot.
     *
     * <p>Shown on every file's still and not only on the ones with a position, so the run reads as a
     * measure of the file rather than as a mark on the picture - and so that whatever the frame itself
     * happens to show down there (a recording of another player's own seek bar, say) cannot be
     * mistaken for it.
     */
    static void playedTrack(final FrameLayout box) {
        box.getChildAt(3).setVisibility(View.VISIBLE);
    }

    static void playedRun(final FrameLayout box, final float played) {
        final View run = box.getChildAt(4);
        if (played <= 0.02f) {
            run.setVisibility(View.GONE);
            return;
        }
        run.setBackgroundColor(MaterialColors.getColor(box, R.attr.colorPrimary));
        run.setVisibility(View.VISIBLE);
        final ViewGroup.LayoutParams lp = run.getLayoutParams();
        // The width is a fraction of a box that may not have been measured yet, so it is set once it
        // has; on a box that is measured already this is the next frame either way.
        box.post(() -> {
            lp.width = Math.round(box.getWidth() * Math.min(played, 1f));
            run.setLayoutParams(lp);
        });
    }

    /** A uri that can be asked for a frame of itself without fetching it over a network first. */
    static Uri frameSource(final Uri media) {
        final String scheme = media == null ? null : media.getScheme();
        return "file".equals(scheme) || ContentResolver.SCHEME_CONTENT.equals(scheme) ? media : null;
    }





    /**
     * A ripple that reaches the whole control. On the 90dp hero the wash stopped at about seven tenths of
     * the disc — the press lit the middle of the button and never its edge — for two reasons, both fixed
     * here. A RippleDrawable is a LayerDrawable, and its default padding mode nests each layer inside the
     * padding of the one before: an {@link InsetDrawable} reports its inset as padding, so the mask came
     * out inset twice and cut the wash to 50dp of a 70dp disc. And Android's own guess at a masked ripple's
     * radius falls short of the corners, so the radius is stated here instead.
     *
     * <p>The whole diagonal, not half of it. Half is the right answer only for a wave that starts in the
     * middle, and a row's does not: a {@code CompoundButton} puts the hotspot at its button, so on a
     * 922x126 picker row the wave began at the radio and a 466px radius died at x=590 — the press lit the
     * left three fifths of the row and stopped, with the finger held, in the middle of a word. Two people
     * reported that as a rendering artifact. From a corner, only the full diagonal reaches the far one.
     */
    static RippleDrawable coveringRipple(final ColorStateList color, final Drawable content,
                                                 final Drawable mask) {
        final RippleDrawable ripple = new RippleDrawable(color, content, mask) {
            @Override
            protected void onBoundsChange(final Rect bounds) {
                super.onBoundsChange(bounds);
                setRadius((int) Math.ceil(Math.hypot(bounds.width(), bounds.height())));
            }
        };
        ripple.setPaddingMode(LayerDrawable.PADDING_MODE_STACK);
        return ripple;
    }

    /**
     * A ripple colour that shows on press only. A RippleDrawable washes on focus as well, and over the
     * fill of a current row that wash lifted the accent to #DB5F54 from #D6493C — the very thing the
     * focus ring exists to avoid. The edge says "here" and leaves the colour underneath it alone.
     */
    public static ColorStateList pressOnly(final int color) {
        return new ColorStateList(
                new int[][]{{android.R.attr.state_focused, -android.R.attr.state_pressed}, {}},
                new int[]{Color.TRANSPARENT, color});
    }

    /**
     * The D-pad focus ring by itself, in the ink of a Material surface, on a rounded rectangle: for a
     * control whose fill is drawn by something else — a settings row, sliced out of its group's card.
     *
     * @param radii the eight corner radii the row's own outline has, so the ring is that outline
     */
    public static Drawable focusOutline(final Context ctx, final float[] radii) {
        final GradientDrawable ring = new GradientDrawable();
        ring.setCornerRadii(radii);
        ring.setStroke(ctx.getResources().getDimensionPixelSize(R.dimen.focus_ring_width),
                ContextCompat.getColorStateList(ctx, R.color.focus_ring));
        return ring;
    }

    /** A corner radius no box is big enough to show: a rectangle with it is drawn as a circle. */
    public static final float CIRCLE = 10_000f;

    /** A filled shape: a rectangle rounded by {@code radius}, a circle when it is {@link #CIRCLE}. */
    public static GradientDrawable shape(final int color, final float radius) {
        final GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    /** The translucent black plate every control over the picture is drawn on. */
    public static GradientDrawable plate(final Context ctx, final float radius) {
        return shape(ContextCompat.getColor(ctx, R.color.ui_controls_background), radius);
    }

    /**
     * What a control over the picture wears on top of its plate: the press wash, and the D-pad focus
     * contour. Focus is a white line on the control's own shape, drawn on the plate and never on the
     * picture — which is what gives it a floor of 12.63:1 on the brightest frame a video can be, where an
     * accent fill's own edge has 1.31:1. Fill and ink are left alone, so a control that is already on keeps
     * saying so (coral glyph) while the contour says the focus is here.
     *
     * <p>The two marks are not the same size, and deliberately: a press belongs to the <em>whole</em>
     * control, the way it does on the episode discs, so it fills the button's own box (inside a pill the
     * box is square and the pill's clip rounds its ends); the contour is an indicator and sits inside that
     * box, with air against the pill's edge and against the next button.
     *
     * @param radius the contour's corner: {@link #CIRCLE} for a disc, the concentric inner corner for a
     *               tile inside a pill (the pill's corner less the inset, so the two stay parallel)
     * @param inset  how far inside the view the contour sits
     * @param pressRadius the corner of the press wash, {@link #CIRCLE} for a disc, 0 inside a pill
     * @param pressInset  how far inside the view the plate sits, so the wash stops where the plate does
     */
    public static Drawable chromeForeground(final Context ctx, final float radius, final int inset,
                                            final float pressRadius, final int pressInset) {
        // Both marks carry their state in a colour list of their own: a StateListDrawable cannot express
        // "nothing" — a null entry leaves the previously drawn state on screen.
        final GradientDrawable contour = new GradientDrawable();
        contour.setCornerRadius(radius);
        contour.setStroke(ctx.getResources().getDimensionPixelSize(R.dimen.focus_ring_width),
                new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}},
                        new int[]{Color.WHITE, Color.TRANSPARENT}));
        return coveringRipple(pressOnly(ContextCompat.getColor(ctx, R.color.ripple_chrome)),
                new InsetDrawable((Drawable) contour, inset),
                new InsetDrawable((Drawable) shape(Color.WHITE, pressRadius), pressInset));
    }

    /** {@link #chromeForeground} for a round control, where the press and the contour share the disc. */
    public static Drawable chromeForeground(final Context ctx, final int discInset) {
        return chromeForeground(ctx, CIRCLE, discInset, CIRCLE, discInset);
    }





    /**
     * A panel that carries a text field: it opens with the keyboard up, and the keyboard shortens it
     * instead of covering it.
     *
     * <p>Upright the window is resized above the keys and there is nothing more to do. Sideways the
     * keyboard is a window of its own the height of the screen, so the panel's window is never resized
     * and a card centred in it would sit half under the keys. What the keys cover is an inset either
     * way, and held as the host's padding it bounds the card in both — upright that inset is zero
     * inside the resized window, so nothing moves twice.
     */
    static void keyboardPanel(final Dialog dialog, final View content) {
        Dialogs.keyboardResizes(dialog);
        if (Build.VERSION.SDK_INT >= 30) {
            final View host = (View) content.getParent();
            final int hostBottom = host.getPaddingBottom();
            host.setOnApplyWindowInsetsListener((v, insets) -> {
                v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(),
                        hostBottom + insets.getInsets(WindowInsets.Type.ime()).bottom);
                return insets;
            });
        }
        final Window window = dialog.getWindow();
        if (window != null) {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    /**
     * Makes the system's back gesture step back through a chain of panels rather than end it.
     *
     * <p>Back means "undo the last thing I navigated", and in a search that walks title to season to
     * episode that is the previous list, not the whole errand. Only back: a press outside the card and
     * the close button still leave, which is the other question a viewer can be asking.
     *
     * <p>Two ways in, because the manifest opts this app into predictive back: from 33 a dialog is
     * given the gesture through the dispatcher and never sees the key at all, and below that the key
     * is all there is. The registration lives as long as the dialog's window does.
     */
    static void panelBack(final Dialog dialog, final Runnable back) {
        if (Build.VERSION.SDK_INT >= 33) {
            dialog.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, back::run);
            return;
        }
        dialog.setOnKeyListener((d, keyCode, event) -> {
            if (keyCode != KeyEvent.KEYCODE_BACK || event.getAction() != KeyEvent.ACTION_UP) {
                return false;
            }
            back.run();
            return true;
        });
    }



    /**
     * One segment of a picker's toggle group: outlined, 8dp at the corners, at least 48dp tall,
     * lettering that shrinks rather than wraps, and the app's focus ring. Shared by every panel that
     * offers a row of ready-made answers — the sleep timer's durations, the speed panel's rates, the
     * skip panel's modes — so they all say "pick one of these" in one shape.
     */
    public static com.google.android.material.button.MaterialButton pickerSegment(
            final Context ctx, final UiMetrics ui, final CharSequence label) {
        final com.google.android.material.button.MaterialButton button =
                new com.google.android.material.button.MaterialButton(ctx, null,
                        com.google.android.material.R.attr.materialButtonOutlinedStyle);
        // A style is not a theme overlay — passing R.style.Widget_JustPlus_Button_Segment to the
        // constructor would be ignored — so the one thing that style adds arrives here instead, read
        // from the same resource the XML segments use.
        button.setShapeAppearanceModel(ShapeAppearanceModel
                .builder(ctx, R.style.ShapeAppearance_JustPlus_Segment, 0).build());
        button.setId(View.generateViewId()); // a toggle group tracks its buttons by id
        button.setText(label);
        button.setMaxLines(1);
        // Material insets a button by 6dp top and bottom to reach its 48dp touch target from a 36dp
        // box. These panels size their own rows, so the inset only shortens them.
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setMinHeight(ui.dpS(48)); // the platform's floor for anything a finger has to hit
        focusRing(button);
        button.setPadding(ui.dpS(4), button.getPaddingTop(), ui.dpS(4), button.getPaddingBottom());
        // Shrunk rather than wrapped or clipped: the widest label already fills its share of the row at
        // the ordinary size, so a longer language or a system font a notch up used to break one word
        // across two lines and leave the row ragged.
        androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                button, (int) ui.textAction() - 4, (int) ui.textAction(), 1,
                TypedValue.COMPLEX_UNIT_SP);
        return button;
    }

    /**
     * "2,50" -> "2,5", "3,00" -> "3". Against the locale's own decimal separator, which is not a dot
     * everywhere this app is read.
     */
    public static String trimZeros(final String number, final Locale locale) {
        final char point = java.text.DecimalFormatSymbols.getInstance(locale).getDecimalSeparator();
        if (number.indexOf(point) < 0) {
            return number;
        }
        int end = number.length();
        while (end > 0 && number.charAt(end - 1) == '0') {
            end--;
        }
        if (end > 0 && number.charAt(end - 1) == point) {
            end--;
        }
        return number.substring(0, end);
    }

    /**
     * The ink an outlined action letters in: the surface's quieter one, never the accent.
     *
     * <p>Material gives an outlined button {@code colorPrimary}, which is the rule this repository
     * already overrode for a dialog's Cancel — the accent marks the one action that moves things
     * forward, and a second control wearing it makes the two look like equal choices. It reads the same
     * on a panel: Off beside a filled Start, Reset under a coral readout. A segment is left alone, since
     * its own selector already answers checked and unchecked.
     */
    static void quietInk(final MaterialButton button) {
        button.setTextColor(ContextCompat.getColorStateList(button.getContext(),
                R.color.dialog_button_dismissive));
    }

    /**
     * D-pad focus for an outlined Material button: its own border widens to the focus ring's width and
     * goes white, and the button draws over its neighbours while it holds it.
     *
     * <p>Width, because colour alone is the weakest focus event in the app. Where a picker row grows an
     * edge out of nothing — measured 16.30:1 between the same pixels focused and not — an outlined
     * button already has an edge, so focus only recoloured it: grey to white, 2.76:1, under the 3:1 a
     * non-text indicator wants. An edge that thickens as well is a change in shape, which the eye
     * catches without being aimed at it.
     *
     * <p>Z, because in a segmented control the neighbours' borders are drawn over this one's: the group
     * collapses adjacent strokes into shared dividers drawn by whoever comes later, so a focused middle
     * segment was ringed along the top and bottom and left grey down both sides. Lifting it puts it last
     * in the draw order without moving it in the row, which is what the group already does for the
     * segment that is checked.
     */
    static void focusRing(final MaterialButton button) {
        final int rest = button.getStrokeWidth();
        final ColorStateList rested = button.getStrokeColor();
        final int ring = Math.max(rest,
                button.getResources().getDimensionPixelSize(R.dimen.focus_ring_width));
        // The edge is the whole signal: Material's own focus state layer goes, or a focused segment
        // that is also the chosen one gets its accent painted over in the dark colour of the text on
        // it. The press ripple stays exactly as it was.
        button.setRippleColor(ContextCompat.getColorStateList(button.getContext(),
                R.color.ripple_button));
        // The lift casts no shadow. A view with Z above zero is a shadow caster, and HWUI takes the
        // caster's opacity from its outline, which MaterialShapeDrawable reports as fully opaque
        // whatever the fill is - so Skia draws the shadow of an opaque pill and skips the middle,
        // the way it always does for a caster that would hide it. Under a transparent fill there is
        // nothing to hide it: what shows through is the ring of the shadow, eight dark cells around a
        // clean centre, which reads as a square-cornered block behind the label. That is what a
        // television showed on this sheet's Cancel, and it matches to the pixel - the clean centre is
        // the pill inset by its own corner radius. An outline with no alpha stops the shadow before it
        // is drawn (ReorderBarrierDrawables returns on getAlpha() <= 0) and changes nothing else: the
        // shape, the ripple and the stroke are the background's own business, not the outline's.
        button.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(final View view, final Outline outline) {
                ViewOutlineProvider.BACKGROUND.getOutline(view, outline);
                outline.setAlpha(0f);
            }
        });
        button.setOnFocusChangeListener((v, focused) -> {
            button.setStrokeWidth(focused ? ring : rest);
            // R5's contour is colorOnSurface. An outlined button rests on colorOutline, which is the
            // border that makes it outlined; left alone on focus it gave a grey mark where the rule
            // asks for a white one - measured (145,144,150) against text at (226,227,229).
            button.setStrokeColor(focused ? ColorStateList.valueOf(MaterialColors.getColor(
                    button.getContext(), R.attr.colorOnSurface, Color.WHITE)) : rested);
            button.setTranslationZ(focused ? 1f : 0f);
        });
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
     * so the same "not searching" line would otherwise push everything useful out of the window. Sized
     * for a playback session that is recovering — a line per load, state change and recovery rung — so
     * the report still reaches back to what started it.
     *
     * <p>Every line is also a Sentry breadcrumb (a no-op unless Sentry was initialised), so the report
     * and the event carry the same timeline.
     */
    private static final int LOG_LINES = 500;
    private static final ArrayDeque<String> LOG = new ArrayDeque<>();
    private static final long LOG_BASE_MS = SystemClock.elapsedRealtime();
    private static String lastLogged;
    private static int lastLoggedRepeats;

    public static void log(final String text) {
        if (BuildConfig.DEBUG) {
            Log.d("JustPlayer", text);
        }
        io.sentry.Sentry.addBreadcrumb(text);
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

    /**
     * Whether this plays out of local storage rather than off a link, by Media3's own list of schemes
     * (LOCAL_PLAYBACK_SCHEMES in DefaultLoadControl). Not the same question as isSupportedNetworkUri:
     * smb, dav and dlna are neither http nor local, and Media3 already buffers them as streams, so
     * anything that has to agree with its buffering has to ask it this way round.
     */
    public static boolean isLocalPlaybackUri(final Uri uri) {
        final String scheme = uri == null ? null : uri.getScheme();
        if (scheme == null) {
            // A bare path, which is a file by every reading available here.
            return true;
        }
        switch (scheme.toLowerCase()) {
            case "file":
            case "content":
            case "data":
            case "android.resource":
            case "rawresource":
            case "asset":
                return true;
            default:
                return false;
        }
    }

    /**
     * Media that arrives over a network, whichever scheme carries it.
     *
     * <p>Not the same question as {@link #isSupportedNetworkUri}, which asks whether the address is one
     * the player can hand straight to an HTTP data source. A share, a WebDAV server, a media server and
     * a torrent server reach the player as {@code smb://}, {@code dav://}, {@code dlna://} or
     * {@code torr://} and are turned into http inside the data source, so the narrower test calls a
     * torrent a local file - and everything that treats a stream gently (re-reading a bad block, waiting
     * out a loader that is still connected, keeping the clip when a read fails) skipped the very sources
     * that need it most.
     */
    public static boolean isNetworkMedia(final Uri uri) {
        return isSupportedNetworkUri(uri) || NetworkFiles.isNetwork(uri);
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

    // The media URI as a report the person hands over themselves prints it: whole when they turned
    // masking off (Prefs.maskReports), else the route of a network URL and only the scheme of a local one,
    // since a path or file name can identify their library. Sentry never goes through this: it is masked.
    public static String reportUri(final Uri uri, final boolean mask) {
        if (uri == null)
            return null;
        if (!mask)
            return uri.toString();
        return isSupportedNetworkUri(uri) ? uriToReportString(uri) : uri.getScheme() + " (local)";
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

    /**
     * How long a Back press keeps counting for, so the next one leaves instead of asking again. Three
     * seconds, which is what the reference player allows, against the two the player used to. The hint
     * is asked to stay for the same three, so that seeing it means the next press leaves — though
     * anything else with something to say can take the line over in the meantime.
     */
    static final long BACK_CONFIRM_WINDOW_MS = 3_000L;

    /**
     * "No Back has been pressed yet", far enough back that the first one is never mistaken for a second.
     * Not zero: these stamps come from the clock since boot, and a player started on a box that has just
     * come up would read zero as three seconds ago.
     */
    static final long BACK_NOT_PRESSED = -BACK_CONFIRM_WINDOW_MS - 1;

    public static int normRate(float rate) {
        return (int)(rate * 100f);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private static String modeText(final Display.Mode mode) {
        return mode.getPhysicalWidth() + "x" + mode.getPhysicalHeight() + "@" + mode.getRefreshRate();
    }

    /** Whether the display offers a mode at this width that can keep up with the content. */
    @RequiresApi(api = Build.VERSION_CODES.M)
    private static boolean hasRateCapableMode(final Display.Mode[] modes, final int width,
                                              final float frameRate) {
        for (Display.Mode mode : modes) {
            if (mode.getPhysicalWidth() == width
                    && normRate(mode.getRefreshRate()) >= normRate(frameRate)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The display width the picture would rather be shown at, or -1 to leave the frame as it is. Taken
     * from the reference player (DisplaySyncHelper.java:116-124) rule for rule, including the part that
     * looks wrong at first: a 1920- or 1280-wide video asks for exactly that width even on a wider
     * screen, so the television scales it rather than the player. Only those two exact widths do that;
     * anything between them raises the frame and never lowers it.
     */
    static int targetDisplayWidth(final int displayWidth, final int videoWidth) {
        if (videoWidth <= 0) {
            return -1;
        }
        if (videoWidth > 1920 && displayWidth < 3840) {
            return 3840;
        }
        if (videoWidth == 1920 || (videoWidth > 1280 && displayWidth < 1920)) {
            return 1920;
        }
        return videoWidth == 1280 ? 1280 : -1;
    }

    /**
     * Asks the display for the mode this video wants, without anything waiting on the answer. For the
     * moment the container says what it is carrying, which is before the decoder has published a format
     * and well before the first frame: the reference player switches at that point too, between opening
     * the file and building its player, and on the boxes this matters for it is the decoder meeting an
     * already-settled display that they care about. Nothing is armed and nothing is held back — if
     * the mode never changes, the settled path at the end of loading still runs and still starts
     * playback.
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    static void requestFrameRateEarly(final PlayerActivity activity, float frameRate, int videoWidth) {
        activity.runOnUiThread(() ->
                activity.earlyModeSwitchRequested = chooseDisplayMode(activity, frameRate, videoWidth));
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    static void handleFrameRate(final PlayerActivity activity, float frameRate, int videoWidth) {
        activity.runOnUiThread(() -> {
            // Nothing to switch — unless the early request already asked for the very mode this
            // search now finds in place. The display reports a mode as soon as the system accepts it,
            // which is not the same moment the panel has finished changing to it, so a switch that was
            // asked for a second ago is still worth waiting out: the caller's timer and the display's
            // own callback are what that wait is made of.
            if (!chooseDisplayMode(activity, frameRate, videoWidth)
                    && !activity.earlyModeSwitchRequested) {
                activity.frameRateSettled();
            }
        });
    }

    /** @return whether a mode change was actually requested, so the caller knows to wait for it. */
    @RequiresApi(api = Build.VERSION_CODES.M)
    private static boolean chooseDisplayMode(final PlayerActivity activity, float frameRate,
                                             int videoWidth) {
        {
            boolean switchingModes = false;
            activity.resolutionSwitchRequested = false;

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
                    // The resolution the video asks for, when the viewer has asked for that at all and
                    // the video says what it is. Without it the search stays inside the current frame
                    // and only the refresh rate moves, which is what this did before the setting existed.
                    final int targetWidth = activity.mPrefs.displayResolutionMatching
                            ? targetDisplayWidth(activeMode.getPhysicalWidth(), videoWidth) : -1;
                    // Three different things print as -1 otherwise, and the first question asked of this
                    // line is always which of them happened.
                    final String targetText = !activity.mPrefs.displayResolutionMatching ? "off"
                            : targetWidth > 0 ? String.valueOf(targetWidth) : "none for this video";
                    // Not when the frame the video asks for cannot carry its rate: the point of the
                    // whole search is the rate, and 4K at 30 Hz for 60 fps content is a worse picture
                    // than 1080p at 60. Without this the fallback below — top rate at the target
                    // width — would take that trade every time.
                    final boolean switchingResolution = targetWidth > 0
                            && targetWidth != activeMode.getPhysicalWidth()
                            && hasRateCapableMode(supportedModes, targetWidth, frameRate);
                    // Refresh rate >= video FPS
                    List<Display.Mode> modesHigh = new ArrayList<>();
                    // Max refresh rate. No starting point of its own when the frame is changing: the
                    // current mode is not a candidate then, since it is the resolution being left.
                    Display.Mode modeTop = switchingResolution ? null : activeMode;
                    int modesResolutionCount = 0;

                    // Modes at the resolution being aimed at — the current one unless the frame is
                    // changing. Width alone when it is changing, height as well when it is not: that is
                    // the reference's own split between its two mode filters, m1987c and m1988d.
                    for (Display.Mode mode : supportedModes) {
                        final boolean candidate = switchingResolution
                                ? mode.getPhysicalWidth() == targetWidth
                                : mode.getPhysicalWidth() == activeMode.getPhysicalWidth()
                                        && mode.getPhysicalHeight() == activeMode.getPhysicalHeight();
                        if (candidate) {
                            modesResolutionCount++;

                            if (normRate(mode.getRefreshRate()) >= normRate(frameRate))
                                modesHigh.add(mode);

                            if (modeTop == null
                                    || normRate(mode.getRefreshRate()) > normRate(modeTop.getRefreshRate()))
                                modeTop = mode;
                        }
                    }

                    // One mode is enough to be worth taking when it is at another resolution; at the
                    // current one it can only be the mode already running.
                    if (switchingResolution ? modesResolutionCount > 0 : modesResolutionCount > 1) {
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
                        log("display mode: video " + videoWidth + "w @" + frameRate
                                + ", active " + modeText(activeMode) + ", target width " + targetText
                                + ", " + modesResolutionCount + " candidates"
                                + (switchingModes ? ", switching to " + modeText(modeBest)
                                        : ", staying put"));
                        if (switchingModes) {
                            // A different frame is a longer wait than a different rate — the sink
                            // renegotiates — so the caller gives it more time before giving up.
                            activity.resolutionSwitchRequested =
                                    modeBest.getPhysicalWidth() != activeMode.getPhysicalWidth();
                            layoutParams.preferredDisplayModeId = modeBest.getModeId();
                            window.setAttributes(layoutParams);
                        }
                    } else {
                        log("display mode: video " + videoWidth + "w @" + frameRate
                                + ", active " + modeText(activeMode) + ", target width " + targetText
                                + ", " + modesResolutionCount + " candidates, nothing to switch to");
                    }
                } else {
                    log("display mode: video " + videoWidth + "w @" + frameRate + ", active "
                            + modeText(activeMode) + ", the display offers no other mode");
                }
            }

            return switchingModes;
        }
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

    /**
     * Whether a file name ends in a container this app plays. The companion to
     * {@link #supportedMimeTypesVideo}, for the places where a name is all there is to go on: a
     * content provider's mime column is its own opinion, and a fair number of them answer
     * {@code application/octet-stream} for a Matroska file.
     *
     * <p>Deliberately not folded into {@link #isProgressiveContainerUri} below, which asks a different
     * question of a different subject — the suffix of a whole uri path, streaming included — and would
     * change behaviour there by requiring the dot.
     */
    public static boolean hasVideoExtension(final String name) {
        final String lower = name.toLowerCase();
        for (final String extension : supportedExtensionsVideo) {
            if (lower.endsWith('.' + extension)) {
                return true;
            }
        }
        return false;
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

    /**
     * Compares file names the way a viewer reads them: a run of digits counts as one number, so
     * "Episode 2" comes before "Episode 10" instead of after it. Plain {@code compareToIgnoreCase}
     * puts a series in the wrong order from the tenth episode on, which is invisible while only the
     * next file is looked up and plainly wrong once the whole folder is a playlist.
     *
     * <p>Leading zeros do not make a number bigger ("07" and "7" compare equal by value), and case is
     * ignored, as it was before.
     */
    public static int compareNatural(final String a, final String b) {
        int i = 0;
        int j = 0;
        while (i < a.length() && j < b.length()) {
            final char ca = a.charAt(i);
            final char cb = b.charAt(j);
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                // Skip the zeros first: what is left is the number's real length, which orders two
                // runs of digits without parsing them (and so without overflowing on a long one).
                while (i < a.length() - 1 && a.charAt(i) == '0' && Character.isDigit(a.charAt(i + 1))) i++;
                while (j < b.length() - 1 && b.charAt(j) == '0' && Character.isDigit(b.charAt(j + 1))) j++;
                int endA = i;
                int endB = j;
                while (endA < a.length() && Character.isDigit(a.charAt(endA))) endA++;
                while (endB < b.length() && Character.isDigit(b.charAt(endB))) endB++;
                if (endA - i != endB - j) {
                    return (endA - i) - (endB - j);
                }
                while (i < endA) {
                    if (a.charAt(i) != b.charAt(j)) {
                        return a.charAt(i) - b.charAt(j);
                    }
                    i++;
                    j++;
                }
            } else {
                final char la = Character.toLowerCase(ca);
                final char lb = Character.toLowerCase(cb);
                if (la != lb) {
                    return la - lb;
                }
                i++;
                j++;
            }
        }
        return (a.length() - i) - (b.length() - j);
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

    public static boolean switchFrameRate(final PlayerActivity activity, final Uri uri,
                                          final int videoWidth) {
        // preferredDisplayModeId only available on SDK 23+
        // ExoPlayer already uses Surface.setFrameRate() on Android 11+
        if (Build.VERSION.SDK_INT >= 23) {
            if (activity.frameRateSwitchThread != null) {
                activity.frameRateSwitchThread.interrupt();
            }
            activity.frameRateSwitchThread = new Thread(() -> {
                float frameRate = getFrameRate(activity, uri);
                // The rate is the only thing missing here — it is measured off a second
                // extractor because no track published one — so the width still comes from the
                // format, which a Matroska or an MPEG-TS does publish.
                Utils.handleFrameRate(activity, frameRate, videoWidth);
            });
            activity.frameRateSwitchThread.start();
            return true;
        } else {
            return false;
        }
    }

    /**
     * Which of the two doors opens storage on this build. Up to API 29 it is the runtime permission —
     * on 29 by way of the legacy view the manifest asks for, which the legacy flavour keeps wherever
     * it runs because it targets 29. Above that the runtime permission buys nothing and the all-files
     * switch is the only door there is.
     */
    public static boolean permissionOpensStorage(final Context context) {
        return Build.VERSION.SDK_INT < 30 || context.getApplicationInfo().targetSdkVersion <= 29;
    }

    /**
     * Whether storage can actually be read. Asked of the thing that governs it rather than by trying
     * a listing, because a listing is not the test it looks like: measured on a television running
     * API 36 with all-files access refused, {@code /sdcard} listed its top-level folders quite
     * happily and every folder below came back with the directories visible and the files gone — the
     * filtered view scoped storage gives an app for its own files. A browser in that state is worse
     * than one that says it cannot read anything, because it looks like the folders are empty.
     */
    public static boolean canListStorage(final Context context) {
        if (permissionOpensStorage(context)) {
            return context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return Environment.isExternalStorageManager();
    }

    /**
     * Opens the all-files switch, the app's own page for it first and the list of every app that can
     * hold it as the fallback. It hands back no result, so whoever asked has to re-read the state when
     * the viewer comes home rather than wait to be told.
     *
     * @return false when neither screen exists, which is the caller's cue to say so
     */
    public static boolean askForAllFiles(final Context context) {
        // The screen exists on a television too, served by com.android.tv.settings — checked with
        // `cmd package query-activities` on tv_720p.
        try {
            context.startActivity(withTask(context, new Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:" + context.getPackageName()))));
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            try {
                // The same screen one level out: the list of every app that can hold it.
                context.startActivity(withTask(context,
                        new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)));
                return true;
            } catch (Exception second) {
                second.printStackTrace();
                return false;
            }
        }
    }

    /** A screen started from anything but an activity needs a task of its own to stand in. */
    private static Intent withTask(final Context context, final Intent intent) {
        return context instanceof Activity
                ? intent : intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }
}
