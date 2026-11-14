package com.dudeyy.scrolltrack.ui.components;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import com.dudeyy.scrolltrack.ui.Theme;

/**
 * A slim proportion bar: lime fill over a muted track. Used to show what share
 * of a time period is projected to be spent scrolling.
 */
public class ProportionBarView extends View {
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private double fraction = 0;
    private float shown = 0f;
    private ValueAnimator animator;

    public ProportionBarView(Context ctx) {
        super(ctx);
        trackPaint.setColor(Theme.surface2(ctx));
        fillPaint.setColor(Theme.lime(ctx));
    }

    /** @param f share of the period spent scrolling (0..1). */
    public void setFraction(double f, boolean animate) {
        fraction = Math.max(0, Math.min(1, f));
        if (animator != null) animator.cancel();
        if (animate) {
            final float from = shown;
            final float to = (float) fraction;
            animator = ValueAnimator.ofFloat(from, to);
            animator.setDuration(700);
            animator.setInterpolator(new DecelerateInterpolator());
            animator.addUpdateListener(a -> {
                shown = (float) a.getAnimatedValue();
                invalidate();
            });
            animator.start();
        } else {
            shown = (float) fraction;
            invalidate();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (width <= 0) width = Theme.dp(getContext(), 320);
        setMeasuredDimension(width, Theme.dp(getContext(), 10));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float h = getHeight();
        float r = h / 2f;
        rect.set(0, 0, getWidth(), h);
        canvas.drawRoundRect(rect, r, r, trackPaint);
        if (shown > 0f) {
            float w = Math.max(h, getWidth() * shown);
            canvas.save();
            canvas.clipRect(0, 0, getWidth() * shown, h);
            rect.set(0, 0, w, h);
            canvas.drawRoundRect(rect, r, r, fillPaint);
            canvas.restore();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        super.onDetachedFromWindow();
    }
}
