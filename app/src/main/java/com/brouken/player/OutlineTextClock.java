package com.brouken.player;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.Layout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.widget.TextClock;

/**
 * A {@link TextClock} that paints a thin outline behind the text so it stays legible on top of any
 * video frame. Used for the persistent "clock over video" overlay.
 *
 * <p>The stroke pass draws the text layout manually (no {@code setTextColor}), so it does not trigger
 * a per-frame invalidation loop.
 */
public class OutlineTextClock extends TextClock {

    private final int strokeWidth;

    public OutlineTextClock(Context context) {
        this(context, null);
    }

    public OutlineTextClock(Context context, AttributeSet attrs) {
        super(context, attrs);
        strokeWidth = Utils.dpToPx(1);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final Layout layout = getLayout();
        if (layout == null) {
            super.onDraw(canvas);
            return;
        }

        final TextPaint paint = getPaint();
        final Paint.Style prevStyle = paint.getStyle();
        final float prevStrokeWidth = paint.getStrokeWidth();
        final int prevColor = paint.getColor();

        // Outline pass — draw the layout ourselves with a black stroke.
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(strokeWidth);
        paint.setColor(Color.BLACK);
        canvas.save();
        canvas.translate(getTotalPaddingLeft(), getTotalPaddingTop());
        layout.draw(canvas);
        canvas.restore();

        // Restore paint and let TextView draw the normal fill on top.
        paint.setStyle(prevStyle);
        paint.setStrokeWidth(prevStrokeWidth);
        paint.setColor(prevColor);
        super.onDraw(canvas);
    }
}
