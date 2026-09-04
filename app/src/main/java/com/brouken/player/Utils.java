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
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
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
import android.view.KeyEvent;
import android.os.SystemClock;
import android.util.Log;
import android.util.Rational;
import android.util.StateSet;
import android.view.Display;
import android.view.LayoutInflater;
import android.util.TypedValue;
import android.view.MotionEvent;
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

import com.google.android.material.button.MaterialButton;
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
     * The one window recipe every player panel uses: a Material 3 card over a dimmed picture,
     * dismissible by a tap outside. Docked to the end edge and centred vertically, or docked to the
     * bottom edge on a tall narrow window — see the anchor rule below.
     *
     * <p>Not {@code SideSheetDialog} or {@code BottomSheetDialog}, deliberately. Both are fullscreen
     * windows, and a fullscreen dialog window makes OxygenOS treat the panel as immersive and apply its
     * two-swipe back-gesture guard, where a plain window closes on one back. The scrim comes from the
     * window's own dim instead, and the window stays a hair narrower than the screen so it never becomes
     * a fullscreen one. That is also why a bottom-docked panel here has no drag handle and no
     * swipe-to-dismiss: it is a plain window, and a handle that promised a drag it cannot perform would
     * be worse than no handle. It closes on a tap outside and on back, like every other panel.
     *
     * <p>Every panel in the player is this one shape, this one size and this one place, whichever button
     * opened it: a panel that arrives from a different edge or at a different width depending on the
     * press reads as several different panels, and the viewer has to learn each of them. What varies is
     * the window, not the content. A compact-width window — a phone held upright — takes the bottom
     * edge, which is what Material's size classes ask for on that class of window and where the thumb
     * already is. Everything wider takes the end edge: a phone or tablet held sideways is a
     * compact-height window, where a sheet from the bottom has less room than this card has and the
     * strip of picture beside it is worth keeping, and a television has no bottom sheet in its own
     * component set, an unreserved overscan strip along that edge, and every piece of its chrome there
     * already.
     *
     * <p>The one thing that follows from the edge rather than being chosen is the width, and the shape:
     * a sheet docked to the bottom is that edge's width, capped at the 640dp Material states for a
     * sheet, with the two corners against the edge square and the navigation bar's inset carried inside
     * it; a sheet at the end edge is inset from every side with 16dp corners all round. See
     * {@link UiMetrics#panelWidthPx}.
     *
     * <p>The system bars are a margin here, not padding. A flat fill could run under the status bar
     * unnoticed; a card with a visible corner cannot, so what used to inset the content now insets the
     * card, and the content keeps only the padding its own design asks for. The heights are read
     * IGNORING VISIBILITY: a panel opened from another panel arrives with the bars already hidden, and
     * {@code getInsets()} would report zero and put the card's corner under the cutout.
     */
    public static void pickerWindow(final Activity activity, final UiMetrics ui, final Dialog dialog,
                                    final View content) {
        final Configuration cfg = activity.getResources().getConfiguration();
        // One edge for every panel, and the window is what picks it — never which button was pressed:
        // a panel that arrives from a different edge depending on what opened it reads as several
        // different panels. A compact-width window, which is a phone held upright, gets the bottom edge,
        // where the thumb is and where Material puts a sheet on that class of window. Everything wider
        // gets the end edge: there the strip of picture beside the panel is worth having, and a remote's
        // focus travels along one side of the screen instead of across the bottom of it.
        final boolean bottom = cfg.screenWidthDp < 600;
        // And one width, which follows the edge rather than the content — see UiMetrics.panelWidthPx.
        final int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
        final int panelWidth = ui.panelWidthPx(cfg);
        // Material's own margin for a detached sheet is 16dp; 8dp here, because on a phone held sideways
        // the panel is what the viewer came for and the strip of video beside it is not worth the room.
        // A television wants its overscan instead — the card has an edge to lose, where the full-bleed
        // fill before it had none.
        final int hMargin = Math.max(dpToPx(8), ui.overscanH());
        final int vMargin = Math.max(dpToPx(8), ui.overscanV());
        // What actually blocks pixels while a panel is open, and nothing more. applyPickerBars hides the
        // status bar and shows the navigation bar, so reserving room for the status bar costs the card
        // 24dp of height for a bar that is not on screen — which is what clipped the last row of a long
        // menu while a fifth of the window stood empty. The cutout is there whether or not any bar is,
        // and the navigation inset is read ignoring visibility because a panel opened from another panel
        // arrives with the bars already turned off. Per edge, because a cutout on the left of a
        // sideways phone says nothing about the right edge this panel is docked to.
        int insetTop = 0;
        int insetBottom = 0;
        int insetEnd = 0;
        final WindowInsets insets = activity.getWindow().getDecorView().getRootWindowInsets();
        if (insets != null) {
            if (Build.VERSION.SDK_INT >= 30) {
                final android.graphics.Insets blocked = android.graphics.Insets.max(
                        insets.getInsetsIgnoringVisibility(WindowInsets.Type.displayCutout()),
                        insets.getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars()));
                insetTop = blocked.top;
                insetBottom = blocked.bottom;
                insetEnd = cfg.getLayoutDirection()
                        == View.LAYOUT_DIRECTION_RTL ? blocked.left : blocked.right;
            } else {
                // No per-type insets before 30, and no way to ask about a hidden bar either.
                insetBottom = insets.getSystemWindowInsetBottom();
            }
        }

        // Docked at the bottom means docked: the two corners against the edge go square, the way
        // Material draws a bottom sheet, so the card reads as attached rather than as a card that
        // happens to be low. And it takes the radius Material gives that shape — 28dp, its extra-large
        // corner — where a sheet at the end edge takes the large one, 16dp. Two shapes, two numbers,
        // both Material's own.
        final int corner = dpToPx(bottom ? 28 : 16);
        final ShapeAppearanceModel.Builder shape = ShapeAppearanceModel.builder();
        if (bottom) {
            shape.setTopLeftCornerSize(corner).setTopRightCornerSize(corner)
                    .setBottomLeftCornerSize(0).setBottomRightCornerSize(0);
        } else {
            shape.setAllCornerSizes(corner);
        }
        final MaterialShapeDrawable card = new MaterialShapeDrawable(shape.build());
        // From the content's own theme, not the player's: the panels follow the appearance choice, so
        // the card is light when the app is light and black under AMOLED.
        card.setFillColor(ColorStateList.valueOf(MaterialColors.getColor(
                content, R.attr.colorSurface, ContextCompat.getColor(activity, R.color.sheet_surface))));
        content.setBackground(card);
        content.setClipToOutline(true); // so a full-bleed row's ripple stops at the rounded end

        // The blocked edges are the host's padding, not the card's margin: FrameLayout treats a margin
        // under CENTER_VERTICAL as a shift rather than a bound, so an uneven pair walks the card off the
        // top of the screen. Padding bounds it, and the margin left on the card is even.
        final FrameLayout host = new FrameLayout(activity);
        // A sheet must not reach the top edge; the panels hide the status bar, so without a floor here a
        // long playlist would grow into a full-screen dialog that arrived from the bottom.
        //
        // At the bottom the inset goes inside the sheet instead of under it, which is what Material's own
        // bottom sheet does (paddingBottomSystemWindowInsets): the surface reaches the screen's edge and
        // the rows stop above the navigation bar. Held as a margin it left a strip of video between the
        // sheet and the edge, and a sheet with a gap under it is a card again.
        host.setPadding(0, bottom ? Math.max(insetTop, dpToPx(56)) : insetTop, 0,
                bottom ? 0 : insetBottom);
        if (bottom && insetBottom > 0) {
            content.setPadding(content.getPaddingLeft(), content.getPaddingTop(),
                    content.getPaddingRight(), content.getPaddingBottom() + insetBottom);
        }
        final FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                panelWidth, ViewGroup.LayoutParams.WRAP_CONTENT,
                bottom ? Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL
                        : Gravity.END | Gravity.CENTER_VERTICAL);
        if (bottom) {
            // No side margins at all, and none at the bottom: a docked sheet is the width it is given
            // and sits on the edge, above whatever the navigation bar left of it (that inset is the
            // host's padding). A margin here is what made the shape read as a card glued to the bottom
            // rather than as a sheet — detached at the sides, square where it met the screen.
            lp.setMargins(0, vMargin, 0, 0);
        } else {
            // Horizontal gravity is END, where a margin is a bound and the blocked edge can simply be
            // added.
            lp.setMargins(hMargin, vMargin, hMargin + insetEnd, vMargin);
        }
        host.addView(content, lp);

        // The panels build a close button; this is where it learns what it closes. One place decides,
        // and a panel that forgot to carry one simply has none.
        final View close = content.findViewById(R.id.picker_close);
        if (close != null) {
            close.setOnClickListener(v -> dialog.cancel());
        }

        dialog.setContentView(host);
        // setCanceledOnTouchOutside measures "outside" against the window, and a sheet docked to the
        // bottom is given a window as wide as the screen and as tall as it — so on a phone held upright
        // there was no outside to tap, and only the sideways panel, whose window is the width of the
        // card, could be dismissed that way. The host is what fills the rest of the screen, so it is
        // what answers: a press landing beyond the card's own bounds closes the panel, whichever edge
        // the card is docked to. The card's children keep every press that reaches them.
        dialog.setCanceledOnTouchOutside(true);
        host.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() != MotionEvent.ACTION_DOWN
                    || (event.getX() >= content.getLeft() && event.getX() <= content.getRight()
                        && event.getY() >= content.getTop() && event.getY() <= content.getBottom())) {
                return false;
            }
            dialog.cancel();
            return true;
        });
        final Window window = dialog.getWindow();
        if (window == null) {
            return;
        }
        // The window carries the card plus its margins, so the card itself keeps the width the panel was
        // designed at. Capped short of the screen so the window never becomes a fullscreen one.
        window.setLayout(
                bottom ? screenWidth - dpToPx(8)
                        : Math.min(screenWidth - dpToPx(8), panelWidth + 2 * hMargin + insetEnd),
                ViewGroup.LayoutParams.MATCH_PARENT);
        window.setGravity(bottom ? Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL : Gravity.END);
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        // The picture behind a modal sheet steps back rather than competing with it.
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setDimAmount(0.4f);
    }

    /**
     * The background every row inside a player panel wears: a rounded fill, a ripple clipped to it, and
     * the D-pad focus ring.
     *
     * <p>The ring is the whole point. A remote used to be shown focus as a white wash, and over a row
     * lit with the accent the wash took the accent with it — measured 2.21:1 for the label on a focused
     * current row, against the 4.5 it wants. An edge says the same thing and destroys nothing.
     *
     * @param fill the row's own colour, or {@link Color#TRANSPARENT} for a row that is not the current one
     */
    public static Drawable pickerRow(final Context ctx, final int fill) {
        return pickerRow(ctx, fill, false);
    }

    /**
     * The same tile, optionally with an edge at rest.
     *
     * <p>A row inside a list does not want one — the card it sits in is its frame, and twenty rows each
     * boxed is a grid, not a list. A key on a keypad has no such frame: without an edge it is a digit
     * printed on the panel, and twelve of them read as a table of numbers rather than as twelve things
     * to press. So the key keeps a hairline in the surface's outline colour, and the focus ring is that
     * same edge widened — the signal is a change of shape, which the eye catches without being aimed
     * at it, rather than an edge appearing out of nothing.
     *
     * @param outlined true to draw the resting hairline
     */
    public static Drawable pickerRow(final Context ctx, final int fill, final boolean outlined) {
        final int corner = dpToPx(8);
        final GradientDrawable content = new GradientDrawable();
        content.setCornerRadius(corner);
        content.setColor(fill);
        content.setStroke(ctx.getResources().getDimensionPixelSize(R.dimen.focus_ring_width),
                ContextCompat.getColorStateList(ctx, R.color.focus_ring));
        Drawable layer = content;
        if (outlined) {
            // Two widths, so two drawables: a GradientDrawable's stroke width is not state-dependent,
            // and a hairline that only changes colour is the weakest focus event this app has —
            // measured 2.76:1 where a ring appearing reads 16.30:1.
            final GradientDrawable rest = new GradientDrawable();
            rest.setCornerRadius(corner);
            rest.setColor(fill);
            rest.setStroke(dpToPx(1), ContextCompat.getColorStateList(ctx, R.color.focus_ring_outlined));
            final StateListDrawable states = new StateListDrawable();
            states.addState(new int[]{android.R.attr.state_focused}, content);
            states.addState(StateSet.WILD_CARD, rest);
            layer = states;
        }
        final GradientDrawable mask = new GradientDrawable();
        mask.setCornerRadius(corner);
        mask.setColor(Color.WHITE);
        // Press only: a RippleDrawable washes on focus as well, and over the fill of the current row
        // that wash lifted the accent to #DB5F54 from #D6493C — the very thing the ring exists to
        // avoid. The edge says "here" and leaves the colour underneath it alone.
        final int press = MaterialColors.getColor(ctx, R.attr.colorControlHighlight,
                ContextCompat.getColor(ctx, R.color.ripple_chrome));
        return new RippleDrawable(new ColorStateList(
                new int[][]{{android.R.attr.state_focused, -android.R.attr.state_pressed}, {}},
                new int[]{Color.TRANSPARENT, press}),
                layer, mask);
    }

    /**
     * The one control every player panel carries: a close button for its header.
     *
     * <p>It exists for the panel that fills the screen. A playlist grows until 56dp of picture is all
     * that is left above it, and then the only ways out are the system Back and a press on that strip —
     * one of which is a gesture on most phones now, and the other of which looks like nothing. Material
     * gives a surface that has taken the screen an explicit close, and this is it.
     *
     * <p>No listener here: {@link #pickerWindow} finds it by id and wires it to the dialog, so what
     * closing means is decided in the one place that already knows.
     */
    public static com.google.android.material.button.MaterialButton pickerClose(
            final Context ctx, final UiMetrics ui) {
        final com.google.android.material.button.MaterialButton button =
                iconButton(ctx, ui, R.drawable.ic_close_24dp, R.string.error_close);
        button.setId(R.id.picker_close);
        return button;
    }

    /** A 48dp glyph on nothing, in the colour a panel gives its quieter text. */
    static com.google.android.material.button.MaterialButton iconButton(
            final Context ctx, final UiMetrics ui, final int icon, final int description) {
        return iconButton(ctx, ui, icon, ctx.getString(description), false);
    }

    static com.google.android.material.button.MaterialButton iconButton(
            final Context ctx, final UiMetrics ui, final int icon, final int description,
            final boolean outlined) {
        return iconButton(ctx, ui, icon, ctx.getString(description), outlined);
    }

    /**
     * The same glyph, with or without an edge of its own.
     *
     * <p>The box is padding plus glyph, never a minimum width around a smaller one. MaterialButton lays
     * its icon against {@code paddingStart} and does not move it when the view is stretched by
     * {@code minWidth}, so a 24dp glyph in a 40dp button widened to 48 sits 4dp left of the middle —
     * measured on the ± of the offset panel, and 2dp on the panels' close button, which had been that
     * way unseen for as long as those buttons drew no background. Padding is symmetric, so the glyph is
     * centred by construction whatever the box is.
     *
     * @param outlined true for a control that stands alone — a ± beside a number, a backspace — and so
     *                 has to say where it begins; false for one of a row inside a header or a bar,
     *                 which is already a frame and does not want a second one drawn inside it
     */
    static com.google.android.material.button.MaterialButton iconButton(
            final Context ctx, final UiMetrics ui, final int icon, final CharSequence description,
            final boolean outlined) {
        final com.google.android.material.button.MaterialButton button =
                new com.google.android.material.button.MaterialButton(ctx, null, outlined
                        ? com.google.android.material.R.attr.materialIconButtonOutlinedStyle
                        : com.google.android.material.R.attr.materialIconButtonStyle);
        final int glyph = ui.dpS(24);
        final int box = ui.dpS(48); // the platform's floor for anything a finger has to hit
        button.setIconResource(icon);
        button.setIconSize(glyph);
        button.setIconTint(ColorStateList.valueOf(MaterialColors.getColor(ctx,
                R.attr.colorOnSurfaceVariant, ContextCompat.getColor(ctx, R.color.ink_secondary))));
        button.setContentDescription(description);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        final int pad = (box - glyph) / 2;
        button.setPadding(pad, pad, pad, pad);
        // Kept as a floor, not as the thing that makes the box: see the note above.
        button.setMinWidth(box);
        button.setMinHeight(box);
        button.setMinimumWidth(box);
        button.setMinimumHeight(box);
        focusRing(button);
        return button;
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
        keyboardResizes(dialog);
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
     * A panel's title row: whatever names the panel, then the close button at the end of the same line.
     *
     * @param title the panel's own header view, or null for a panel whose groups name themselves — the
     *              row is then the close button alone, which is still the line every panel starts with
     */
    public static LinearLayout pickerHeader(final Context ctx, final UiMetrics ui, final View title) {
        return pickerHeader(ctx, ui, title, null);
    }

    /**
     * The same, for a panel that is one step of several: a back arrow at the start of the line, where
     * every toolbar in Android puts one, and the close button still at the end. Back goes one step,
     * close leaves the whole thing — two different questions, so two different controls.
     *
     * @param back what the arrow does, or null for a panel nothing came before
     */
    public static LinearLayout pickerHeader(final Context ctx, final UiMetrics ui, final View title,
                                            final Runnable back) {
        final LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        if (back != null) {
            final com.google.android.material.button.MaterialButton arrow =
                    iconButton(ctx, ui, R.drawable.ic_arrow_back_24dp, R.string.back);
            arrow.setOnClickListener(v -> back.run());
            row.addView(arrow);
        }
        // Weight on whatever is to the left of it, so the close button sits at the end whether the row
        // holds a title, a title and a count, or nothing at all.
        row.addView(title == null ? new View(ctx) : title,
                new LinearLayout.LayoutParams(0, title == null
                        ? 1 : ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(pickerClose(ctx, ui));
        return row;
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
        final int ring = Math.max(rest,
                button.getResources().getDimensionPixelSize(R.dimen.focus_ring_width));
        // The edge is the whole signal: Material's own focus state layer goes, or a focused segment
        // that is also the chosen one gets its accent painted over in the dark colour of the text on
        // it. The press ripple stays exactly as it was.
        button.setRippleColor(ContextCompat.getColorStateList(button.getContext(),
                R.color.ripple_button));
        button.setOnFocusChangeListener((v, focused) -> {
            button.setStrokeWidth(focused ? ring : rest);
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
     * Keeps a dialog that carries a text field where it first appeared, whatever the keyboard does to
     * the bars around it.
     *
     * <p>The player hides the status bar, and a dialog over it brings the bar back — until the keyboard
     * comes up, when the system reconsiders and hides it again. A dialog's frame is laid out below the
     * bars that are visible, so that hide moves the frame up by a status bar, in the same breath as the
     * keyboard shrinks it. On the owner's phone the picture stayed where it was while the touch frame
     * moved: a press measured 795 on a row whose own {@code getLocationOnScreen} said 898, and the row
     * below took the press meant for the one above. The 110px gap was the height of the bar.
     *
     * <p>So the frame is inset by the bars whether they show or not, and pinned to the top: the keyboard
     * can only make the window shorter, results can only make it taller, and the top edge never has a
     * reason to move.
     */
    static void keyboardResizes(final android.app.Dialog dialog) {
        final Window window = dialog.getWindow();
        if (window == null) {
            return;
        }
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        final WindowManager.LayoutParams lp = window.getAttributes();
        // A window already docked to an edge keeps the edge it was docked to; only a centred one has a
        // reason to move, and the top is the one place a keyboard cannot push it away from.
        if (lp.gravity == Gravity.NO_GRAVITY || lp.gravity == (Gravity.CENTER)) {
            lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        }
        if (Build.VERSION.SDK_INT >= 30) {
            // Naming the types takes over from the system, which would otherwise add the keyboard for
            // ADJUST_RESIZE by itself — so it is named here too.
            lp.setFitInsetsTypes(lp.getFitInsetsTypes() | WindowInsets.Type.ime());
            lp.setFitInsetsIgnoringVisibility(true);
        }
        window.setAttributes(lp);
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
        // The accent the dark panel shares with the light one measures 3.3:1 on its card: enough for
        // the 2dp outline, not for the 12sp label riding it. The label alone takes the brighter coral.
        if (!MaterialColors.isColorLight(MaterialColors.getColor(field, R.attr.colorSurface))) {
            field.setHintTextColor(ColorStateList.valueOf(
                    ContextCompat.getColor(fields.getContext(), R.color.brand)));
        }
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
