package com.dudeyy.scrolltrack.ui.components;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import com.dudeyy.scrolltrack.ui.Theme;

/**
 * A year at a glance: 365 dots, one per day. Dots filled in lime represent the
 * days per year projected to be spent scrolling. Fills in with a soft sweep
 * the first time a value is set.
 */
public class YearDotsView extends View {
    private static final int TOTAL_DOTS = 365;

    private final Paint filledPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint emptyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private double filledDays = 0;
    private float reveal = 1f;
    private ValueAnimator animator;

    public YearDotsView(Context ctx) {
        super(ctx);
        filledPaint.setColor(Theme.lime(ctx));
        emptyPaint.setColor(Theme.surface2(ctx));
    }

    /** @param days projected full days per year spent scrolling (0..365). */
    public void setDays(double days, boolean animate) {
        filledDays = Math.max(0, Math.min(TOTAL_DOTS, days));
        if (animator != null) animator.cancel();
        if (animate) {
            reveal = 0f;
            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(900);
            animator.setInterpolator(new DecelerateInterpolator());
            animator.addUpdateListener(a -> {
                reveal = (float) a.getAnimatedValue();
                invalidate();
            });
            animator.start();
        } else {
            reveal = 1f;
        }
        requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (width <= 0) width = Theme.dp(getContext(), 320);
        int pitch = Theme.dp(getContext(), 12);
        int cols = Math.max(10, width / pitch);
        int rows = (int) Math.ceil(TOTAL_DOTS / (double) cols);
        setMeasuredDimension(width, rows * pitch);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int pitch = Theme.dp(getContext(), 12);
        int cols = Math.max(10, width / pitch);
        float radius = pitch * 0.30f;
        int visibleFilled = (int) Math.round(filledDays * reveal);
        for (int i = 0; i < TOTAL_DOTS; i++) {
            int col = i % cols;
            int row = i / cols;
            float cx = col * pitch + pitch / 2f;
            float cy = row * pitch + pitch / 2f;
            canvas.drawCircle(cx, cy, radius, i < visibleFilled ? filledPaint : emptyPaint);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        super.onDetachedFromWindow();
    }
}
