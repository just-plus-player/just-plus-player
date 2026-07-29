package com.brouken.player;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.res.Resources;
import android.provider.Settings;
import android.view.WindowManager;

class BrightnessControl {

    private static final int FADE_DURATION = 300;

    private final Activity activity;
    private ValueAnimator fade;

    /** 0-100, or -1 for system/auto brightness. Float so the absolute gesture keeps sub-percent precision. */
    public float percent = -1;

    public BrightnessControl(Activity activity) {
        this.activity = activity;
    }

    public float getScreenBrightness() {
        return activity.getWindow().getAttributes().screenBrightness;
    }

    public void setScreenBrightness(final float brightness) {
        WindowManager.LayoutParams lp = activity.getWindow().getAttributes();
        lp.screenBrightness = brightness;
        activity.getWindow().setAttributes(lp);
    }

    /**
     * Hands the window our own brightness while media is on screen, and back to the system whenever it is
     * not: the empty state is ordinary UI and has no business dimming the device, and the level left over
     * from the last video is exactly what made it look like the player had a brightness of its own. Faded,
     * because both switches happen right under the user's eyes.
     */
    public void setActive(final boolean active, final boolean animate) {
        fadeTo(active && percent >= 0
                ? percentToBrightness(percent)
                : WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE, animate);
    }

    private void fadeTo(final float target, final boolean animate) {
        cancelFade();

        // BRIGHTNESS_OVERRIDE_NONE is a flag rather than a level, so the fade runs against the brightness
        // the system would show and the flag itself is only set once the screen is already there.
        final float from = resolve(getScreenBrightness());
        final float to = resolve(target);

        if (!animate || Math.abs(to - from) < 0.01f) {
            setScreenBrightness(target);
            return;
        }

        fade = ValueAnimator.ofFloat(from, to);
        fade.setDuration(FADE_DURATION);
        fade.addUpdateListener(animation -> setScreenBrightness((float) animation.getAnimatedValue()));
        fade.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                // Whoever cancelled us is setting its own brightness right now; committing ours would fight it.
                if (!cancelled)
                    setScreenBrightness(target);
            }
        });
        fade.start();
    }

    private void cancelFade() {
        if (fade != null) {
            fade.cancel();
            fade = null;
        }
    }

    public void changeBrightness(final CustomPlayerView playerView, final float delta, final boolean canSetAuto) {
        // The gesture is direct feedback on a finger: it wins over any fade still running.
        cancelFade();

        final float newPercent = (percent < 0 ? systemPercent() : percent) + delta;

        if (canSetAuto && newPercent < 0)
            percent = -1;
        else
            percent = Math.max(0f, Math.min(100f, newPercent));

        if (percent < 0)
            setScreenBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE);
        else
            setScreenBrightness(percentToBrightness(percent));

        playerView.showBrightness(Math.round(percent), percent < 0);
    }

    float percentToBrightness(final float percent) {
        final double d = 0.064 + 0.936 / 100 * percent;
        return (float) (d * d);
    }

    private float brightnessToPercent(final float brightness) {
        final double d = Math.sqrt(Math.max(0f, brightness));
        return (float) Math.max(0, Math.min(100, (d - 0.064) / 0.936 * 100));
    }

    private float resolve(final float brightness) {
        return brightness < 0 ? systemBrightness() : brightness;
    }

    /**
     * Device brightness on the window's 0-1 scale. With auto-brightness on this is the slider position
     * rather than the light the screen actually emits, which is all Android exposes — close enough for a
     * fade to start from, and the fade itself covers the error.
     */
    private float systemBrightness() {
        final int max = systemBrightnessMax();
        final int raw = Settings.System.getInt(activity.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, -1);
        // Out of range means the ROM stores brightness on a scale we cannot normalize; start from the middle
        if (raw < 0 || raw > max)
            return percentToBrightness(50f);
        return (float) raw / max;
    }

    /**
     * Device brightness on our percent scale, used as the starting point when the player has no brightness
     * of its own yet, so the first gesture continues from what is already on screen instead of from zero.
     */
    private float systemPercent() {
        return brightnessToPercent(systemBrightness());
    }

    /** The scale system brightness is stored on. OEMs widen it well past the classic 0-255. */
    private int systemBrightnessMax() {
        final Resources res = Resources.getSystem();
        final int id = res.getIdentifier("config_screenBrightnessSettingMaximum", "integer", "android");
        if (id != 0) {
            final int max = res.getInteger(id);
            if (max > 0)
                return max;
        }
        return 255;
    }
}
