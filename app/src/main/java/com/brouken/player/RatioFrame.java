package com.brouken.player;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

/**
 * A frame as wide as it is given and 16:9 tall.
 *
 * <p>For the tile, whose width is whatever a column of the grid turns out to be and whose still has
 * to keep the shape of a screen. A row's frame is a fixed 96 x 54 and needs none of this; a tile's
 * cannot be, because the number of columns changes with the window.
 */
public final class RatioFrame extends FrameLayout {

    public RatioFrame(final Context context) {
        super(context);
    }

    public RatioFrame(final Context context, @Nullable final AttributeSet attrs) {
        super(context, attrs);
    }

    public RatioFrame(final Context context, @Nullable final AttributeSet attrs,
                      final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onMeasure(final int widthSpec, final int heightSpec) {
        final int width = MeasureSpec.getSize(widthSpec);
        super.onMeasure(widthSpec,
                MeasureSpec.makeMeasureSpec(width * 9 / 16, MeasureSpec.EXACTLY));
    }
}
