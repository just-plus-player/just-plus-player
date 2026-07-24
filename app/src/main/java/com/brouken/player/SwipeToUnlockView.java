package com.brouken.player;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

// Swipe-to-unlock bar shown while the screen is locked, ported from VLC's SwipeToUnlockView. A lock icon
// pinned to the left is dragged to the right edge to unlock. Touch only (the lock feature is not offered on
// TV). It is the only affordance for leaving the locked state.
public class SwipeToUnlockView extends FrameLayout {

    private static final long ANIMATE_BACK_MS = 250;

    private final ImageView swipeIcon;
    private final TextView swipeText;

    private boolean unlocking;

    // Callbacks: fired while dragging starts/stops (to hold/release the overlay auto-hide) and on unlock.
    private Runnable onStartTouching;
    private Runnable onStopTouching;
    private Runnable onUnlock;

    public SwipeToUnlockView(Context context) {
        super(context);

        final GradientDrawable pill = new GradientDrawable();
        pill.setColor(ContextCompat.getColor(context, R.color.ui_controls_background));
        pill.setCornerRadius(Utils.dpToPx(24));
        setBackground(pill);
        setClipToOutline(true);
        final int padH = Utils.dpToPx(10);
        final int padV = Utils.dpToPx(8);
        setPadding(padH, padV, padH, padV);

        final int iconSize = Utils.dpToPx(28);

        // Single line, centered, and inset on both sides so it clears the resting icon and never wraps.
        swipeText = new TextView(context);
        swipeText.setTextColor(0xFFFFFFFF);
        swipeText.setTextSize(13);
        swipeText.setSingleLine(true);
        swipeText.setMaxLines(1);
        swipeText.setEllipsize(TextUtils.TruncateAt.END);
        swipeText.setGravity(Gravity.CENTER);
        swipeText.setText(R.string.swipe_unlock);
        final int textInset = iconSize + Utils.dpToPx(6);
        final LayoutParams textLp = new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        textLp.gravity = Gravity.CENTER;
        textLp.leftMargin = textInset;
        textLp.rightMargin = textInset;
        addView(swipeText, textLp);

        swipeIcon = new ImageView(context);
        swipeIcon.setImageResource(R.drawable.ic_lock_24dp);
        swipeIcon.setImageTintList(ContextCompat.getColorStateList(context, R.color.control_icon_tint));
        final LayoutParams iconLp = new LayoutParams(iconSize, iconSize);
        iconLp.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
        addView(swipeIcon, iconLp);
    }

    public void setOnStartTouchingListener(Runnable r) {
        onStartTouching = r;
    }

    public void setOnStopTouchingListener(Runnable r) {
        onStopTouching = r;
    }

    public void setOnUnlockListener(Runnable r) {
        onUnlock = r;
    }

    // Distance the icon can travel from its left resting spot to the right inner edge.
    private float getMaxTranslation() {
        final float span = getWidth() - getPaddingLeft() - getPaddingRight() - swipeIcon.getWidth();
        return span > 0 ? span : 0;
    }

    private void playStep(float tx) {
        final float max = getMaxTranslation();
        swipeIcon.setTranslationX(tx);
        swipeText.setAlpha(max > 0 ? 1f - tx / max : 1f);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (unlocking) {
            return false;
        }
        final float max = getMaxTranslation();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (onStartTouching != null) {
                    onStartTouching.run();
                }
                return true;
            case MotionEvent.ACTION_MOVE: {
                float tx = event.getX() - getPaddingLeft() - swipeIcon.getWidth() / 2f;
                if (tx < 0) {
                    tx = 0;
                } else if (tx > max) {
                    tx = max;
                }
                if (max > 0 && tx >= max - Utils.dpToPx(2)) {
                    unlock();
                } else {
                    playStep(tx);
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                animateBack();
                if (onStopTouching != null) {
                    onStopTouching.run();
                }
                return true;
        }
        return super.onTouchEvent(event);
    }

    private void animateBack() {
        final float from = swipeIcon.getTranslationX();
        if (from <= 0) {
            playStep(0);
            return;
        }
        final ValueAnimator animator = ValueAnimator.ofFloat(from, 0);
        animator.setDuration(ANIMATE_BACK_MS);
        animator.addUpdateListener(a -> playStep((float) a.getAnimatedValue()));
        animator.start();
    }

    private void unlock() {
        unlocking = true;
        if (onUnlock != null) {
            onUnlock.run();
        }
        setVisibility(GONE);
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        if (visibility == VISIBLE) {
            unlocking = false;
            playStep(0);
        }
    }
}
