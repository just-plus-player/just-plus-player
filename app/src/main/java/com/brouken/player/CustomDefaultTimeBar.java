package com.brouken.player;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.annotation.Nullable;
import androidx.media3.ui.DefaultTimeBar;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

class CustomDefaultTimeBar extends DefaultTimeBar {

    Rect scrubberBar;
    private Rect progressBar;
    private boolean scrubbing;
    private int scrubbingStartX;

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
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
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
                canvas.drawRect(left, progressBar.top, right, progressBar.bottom, skipPaint);
            }
            skipPaint.setColor(skipColors[i]);
            canvas.drawRect(left, progressBar.top, left + hairWidth, progressBar.bottom, skipPaint);
            // Second hairline at the segment end, only when there's room for it to read as a separate edge.
            if (right - left > hairWidth * 2) {
                canvas.drawRect(right - hairWidth, progressBar.top, right, progressBar.bottom, skipPaint);
            }
        }
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
        if (!scrubbing && event.getAction() == MotionEvent.ACTION_MOVE && scrubberBar != null) {
            final int distanceFromStart = Math.abs(((int)event.getX()) - scrubbingStartX);
            if (distanceFromStart > Utils.dpToPx(6)) {
                scrubbing = true;
                try {
                    final Method method = DefaultTimeBar.class.getDeclaredMethod("startScrubbing", long.class);
                    method.setAccessible(true);
                    method.invoke(this, (long) 0);
                } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                    e.printStackTrace();
                }
            } else {
                return true;
            }
        }
        return super.onTouchEvent(event);
    }
}
