package com.brouken.player;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.animation.PathInterpolator;
import android.view.MotionEvent;

import androidx.annotation.Nullable;
import androidx.media3.ui.DefaultTimeBar;
import androidx.media3.ui.TimeBar;

import java.lang.reflect.Field;

class CustomDefaultTimeBar extends DefaultTimeBar {

    Rect scrubberBar;
    private Rect progressBar;
    // The bar's own height, and the field Media3 keeps it in: on a remote the bar has to say it holds the
    // focus, and it has nothing else to say it with — no plate to draw a contour on, and a full-width line
    // in white would be the loudest thing on the screen. So the rail itself doubles, the way a television
    // player's does, and the scrubber is already at its dragged size while focused.
    private Field barHeightField;
    private int barHeightRest;
    private ValueAnimator barHeightAnimator;
    private static final long BAR_HEIGHT_DURATION_MS = 150;
    private boolean scrubbing;
    private int scrubbingStartX;
    private boolean scrubbingNow;
    private int playheadLeft;
    private int playheadRight;

    private final Paint skipPaint = new Paint();
    private long[] skipStartsMs;
    private long[] skipEndsMs;
    private int[] skipColors;
    private int[] skipFillColors;
    private long skipDurationMs;

    public CustomDefaultTimeBar(Context context) {
        this(context, null);
    }

    public CustomDefaultTimeBar(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CustomDefaultTimeBar(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, attrs);
    }

    public CustomDefaultTimeBar(Context context, @Nullable AttributeSet attrs, int defStyleAttr, @Nullable AttributeSet timebarAttrs) {
        this(context, attrs, defStyleAttr, timebarAttrs, 0);
    }

    public CustomDefaultTimeBar(Context context, @Nullable AttributeSet attrs, int defStyleAttr, @Nullable AttributeSet timebarAttrs, int defStyleRes) {
        super(context, attrs, defStyleAttr, timebarAttrs, defStyleRes);
        try {
            Field field = DefaultTimeBar.class.getDeclaredField("scrubberBar");
            field.setAccessible(true);
            scrubberBar = (Rect) field.get(this);
            Field progressField = DefaultTimeBar.class.getDeclaredField("progressBar");
            progressField.setAccessible(true);
            progressBar = (Rect) progressField.get(this);
            barHeightField = DefaultTimeBar.class.getDeclaredField("barHeight");
            barHeightField.setAccessible(true);
            barHeightRest = barHeightField.getInt(this);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            barHeightField = null;
            e.printStackTrace();
        }
        // The scrubber grows while the bar is being dragged (DefaultTimeBar.drawPlayhead), and the base
        // class keeps that flag private — this listener spans exactly the same window.
        addListener(new OnScrubListener() {
            @Override
            public void onScrubStart(TimeBar timeBar, long position) {
                scrubbingNow = true;
            }

            @Override
            public void onScrubMove(TimeBar timeBar, long position) {
            }

            @Override
            public void onScrubStop(TimeBar timeBar, long position, boolean canceled) {
                scrubbingNow = false;
            }
        });
    }

    /**
     * Highlight skip/ad segment ranges on the progress bar so the user sees them in advance.
     * Arrays are parallel: {@code edgeColors} paints the crisp boundary hairlines, {@code fillColors}
     * the soft band across the whole segment. Both carry per-segment (translucent) ARGB.
     */
    void setSkipHighlights(long[] startsMs, long[] endsMs, int[] edgeColors, int[] fillColors, long durationMs) {
        this.skipStartsMs = startsMs;
        this.skipEndsMs = endsMs;
        this.skipColors = edgeColors;
        this.skipFillColors = fillColors;
        this.skipDurationMs = durationMs;
        invalidate();
    }

    void clearSkipHighlights() {
        this.skipStartsMs = null;
        this.skipEndsMs = null;
        this.skipColors = null;
        this.skipFillColors = null;
        this.skipDurationMs = 0;
        invalidate();
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (skipStartsMs == null || skipDurationMs <= 0 || progressBar == null) {
            return;
        }
        final int barLeft = progressBar.left;
        final int barWidth = progressBar.width();
        if (barWidth <= 0) {
            return;
        }
        // Media3 paints the playhead inside super.onDraw above, so everything below lands on top of it —
        // and the fill is opaque, which swallowed the coral dot whole while it sat inside a segment. Keep
        // the dot's own patch clear instead, so it stays the frontmost mark on the bar: it is what the
        // viewer is tracking. Bounds as in DefaultTimeBar.drawPlayhead.
        if (scrubberBar != null) {
            final int radius = playheadRadius();
            final int playheadX = Math.min(Math.max(scrubberBar.right, scrubberBar.left), progressBar.right);
            playheadLeft = playheadX - radius;
            playheadRight = playheadX + radius;
        } else {
            playheadLeft = 0;
            playheadRight = 0;
        }
        // Each segment gets a soft fill band across its whole width, plus ~1.5dp crisp hairlines on the
        // boundaries (chapter-divider style). The band demarcates the region while the edges frame it,
        // both staying lighter in weight than the coral scrubber.
        final int hairWidth = Math.max(2, Utils.dpToPx(3) / 2);
        for (int i = 0; i < skipStartsMs.length; i++) {
            float startFraction = clamp((float) skipStartsMs[i] / skipDurationMs);
            float endFraction = clamp((float) skipEndsMs[i] / skipDurationMs);
            int left = barLeft + Math.round(barWidth * startFraction);
            int right = barLeft + Math.round(barWidth * endFraction);
            if (right < left) {
                right = left;
            }
            if (skipFillColors != null && right > left) {
                skipPaint.setColor(skipFillColors[i]);
                drawBand(canvas, left, right);
            }
            skipPaint.setColor(skipColors[i]);
            drawBand(canvas, left, left + hairWidth);
            // Second hairline at the segment end, only when there's room for it to read as a separate edge.
            if (right - left > hairWidth * 2) {
                drawBand(canvas, right - hairWidth, right);
            }
        }
    }

    /** Paints a bar-height band, leaving the playhead's patch clear so the scrubber stays in front of it. */
    private void drawBand(Canvas canvas, int left, int right) {
        if (right <= left) {
            return;
        }
        if (right > playheadLeft && left < playheadRight) {
            if (left < playheadLeft) {
                canvas.drawRect(left, progressBar.top, playheadLeft, progressBar.bottom, skipPaint);
            }
            if (right > playheadRight) {
                canvas.drawRect(playheadRight, progressBar.top, right, progressBar.bottom, skipPaint);
            }
            return;
        }
        canvas.drawRect(left, progressBar.top, right, progressBar.bottom, skipPaint);
    }

    @Override
    protected void onFocusChanged(final boolean gainFocus, final int direction,
                                  @Nullable final Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        if (barHeightField == null) {
            return;
        }
        if (barHeightAnimator != null) {
            barHeightAnimator.cancel();
        }
        // The one place in the player that animates a focus change, and for the reason the rest do not: the
        // mark here *is* a size, and a full-width line snapping from 2dp to 4dp reads as a flicker rather
        // than a state. Material's short duration with its standard easing, so the bar grows in the time a
        // remote step takes anyway.
        final int from = currentBarHeight();
        final int to = gainFocus ? barHeightRest * 2 : barHeightRest;
        if (from == to) {
            return;
        }
        barHeightAnimator = ValueAnimator.ofInt(from, to);
        barHeightAnimator.setDuration(BAR_HEIGHT_DURATION_MS);
        barHeightAnimator.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
        barHeightAnimator.addUpdateListener(animation -> setBarHeight((int) animation.getAnimatedValue()));
        barHeightAnimator.start();
    }

    private int currentBarHeight() {
        try {
            return barHeightField.getInt(this);
        } catch (IllegalAccessException e) {
            barHeightField = null;
            return barHeightRest;
        }
    }

    /** Media3 lays the three bars out from this height, so the rects follow on the next pass. */
    private void setBarHeight(final int height) {
        if (barHeightField == null) {
            return;
        }
        try {
            barHeightField.setInt(this, height);
            requestLayout();
        } catch (IllegalAccessException e) {
            barHeightField = null;
        }
    }

    /** The radius Media3 is currently drawing the scrubber with — same rule as DefaultTimeBar.drawPlayhead. */
    private int playheadRadius() {
        if (scrubbingNow || isFocused()) {
            return getResources().getDimensionPixelSize(R.dimen.exo_styled_progress_dragged_thumb_size) / 2;
        }
        // Disabled (an unseekable stream): the base class draws no dot, so nothing has to be kept clear.
        return isEnabled()
                ? getResources().getDimensionPixelSize(R.dimen.exo_styled_progress_enabled_thumb_size) / 2
                : 0;
    }

    private static float clamp(float value) {
        return value < 0 ? 0 : (value > 1 ? 1 : value);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN && scrubberBar != null) {
            scrubbing = false;
            scrubbingStartX = (int)event.getX();
            final int distanceFromScrubber = Math.abs(scrubberBar.right - scrubbingStartX);
            if (distanceFromScrubber > Utils.dpToPx(24))
                return true;
            else
                scrubbing = true;
        }
        // The DOWN was swallowed above, so the base class is not scrubbing. Start it now, either
        // because the finger has moved far enough to be a deliberate drag, or because the finger
        // was lifted without moving at all — a tap, which seeks to the touched point.
        if (!scrubbing && scrubberBar != null
                && (event.getAction() == MotionEvent.ACTION_MOVE || event.getAction() == MotionEvent.ACTION_UP)) {
            final int distanceFromStart = Math.abs(((int)event.getX()) - scrubbingStartX);
            if (event.getAction() == MotionEvent.ACTION_MOVE && distanceFromStart <= Utils.dpToPx(6)) {
                return true;
            }
            scrubbing = true;
            startScrubbingAt(event);
        }
        return super.onTouchEvent(event);
    }

    /**
     * Hands the base class the ACTION_DOWN it never received, so it positions the scrubber itself.
     * The press is clamped onto the bar because the finger may already have left it — dragging off
     * the bar is how fine scrubbing is started — and a press outside the bar would be ignored.
     */
    private void startScrubbingAt(MotionEvent event) {
        final MotionEvent down = MotionEvent.obtainNoHistory(event);
        down.setAction(MotionEvent.ACTION_DOWN);
        if (progressBar != null) {
            final float x = Math.min(Math.max(event.getX(), progressBar.left), progressBar.right - 1);
            down.setLocation(x, progressBar.centerY());
        }
        super.onTouchEvent(down);
        down.recycle();
    }
}
