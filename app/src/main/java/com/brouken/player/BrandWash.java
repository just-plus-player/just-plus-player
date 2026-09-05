package com.brouken.player;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.core.content.ContextCompat;

import com.google.android.material.color.MaterialColors;

/**
 * The ground under the About block: the app's own container red and its plum, pooled at one corner
 * and drifting to the other and back. The row it fills is already clipped to the card's corners by
 * {@link SettingsActivity} (GroupCards.clip), so this draws a plain rectangle.
 *
 * <p>Dark in both appearances on purpose. It is the ground the empty state and the error screen
 * already give this app when it speaks for itself, and it is what lets the mark and white lettering
 * sit at 8:1 or better whichever theme is in force — the alternative, a pale container in light and
 * a dark one in dark, is two different blocks.
 *
 * <p>The drift is cancelled when the view leaves the window: an infinite animator that outlives its
 * view keeps drawing, and a settings screen is not somewhere to spend a frame a second forever. It
 * never starts at all where motion is not wanted.
 */
public class BrandWash extends View {

    /** One sweep, there and back. Slow enough to read as light rather than as something happening. */
    private static final long PERIOD_MS = 5000;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();
    private final int near;
    private final int far;

    private RadialGradient pool;
    private ValueAnimator drift;
    /** Where a still wash sits — a quarter along, so it is off the corner either way. */
    private float fraction = 0.25f;

    public BrandWash(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        near = MaterialColors.getColor(context, R.attr.colorPrimaryContainer,
                ContextCompat.getColor(context, R.color.brand_container));
        // The theme's own dark ground, not a fixed plum: the plum was the coral world's ground and
        // reads as somebody else's colour under Nord or Forest.
        far = MaterialColors.getColor(context, R.attr.accentGround,
                ContextCompat.getColor(context, R.color.black));
    }

    @Override
    protected void onSizeChanged(final int w, final int h, final int oldW, final int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        // Built once per size and moved by a matrix afterwards: the centre changes every frame, and a
        // new shader every frame is an allocation every frame.
        pool = new RadialGradient(0, 0, Math.max(Math.max(w, h) * 1.1f, 1f), near, far,
                Shader.TileMode.CLAMP);
        paint.setShader(pool);
    }

    @Override
    protected void onDraw(final Canvas canvas) {
        if (pool == null) {
            return;
        }
        // The pool travels from the end edge at the top to the start edge at the bottom, which is the
        // 225-degree run the mark and the CTA button already use, turned into a moving centre.
        matrix.setTranslate(getWidth() - getWidth() * fraction, getHeight() * fraction);
        pool.setLocalMatrix(matrix);
        canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (Utils.isReducedMotion(getContext())) {
            return;
        }
        drift = ValueAnimator.ofFloat(0f, 1f);
        drift.setDuration(PERIOD_MS);
        drift.setRepeatCount(ValueAnimator.INFINITE);
        drift.setRepeatMode(ValueAnimator.REVERSE);
        drift.setInterpolator(new LinearInterpolator());
        drift.addUpdateListener(animation -> {
            fraction = (float) animation.getAnimatedValue();
            invalidate();
        });
        drift.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (drift != null) {
            drift.cancel();
            drift = null;
        }
        super.onDetachedFromWindow();
    }
}
