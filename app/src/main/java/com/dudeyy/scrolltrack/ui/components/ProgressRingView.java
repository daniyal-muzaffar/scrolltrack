package com.dudeyy.scrolltrack.ui.components;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import com.dudeyy.scrolltrack.ui.Theme;

/** Thin progress ring drawn on canvas. */
public class ProgressRingView extends View {
    private float progress; // 0..1
    private float animated = 0f;
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private ValueAnimator animator;

    public ProgressRingView(Context ctx) { super(ctx); init(ctx); }
    public ProgressRingView(Context ctx, AttributeSet a) { super(ctx, a); init(ctx); }

    private void init(Context ctx) {
        float w = Theme.dp(ctx, 10);
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(w);
        trackPaint.setColor(Theme.surface2(ctx));
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        progPaint.setStyle(Paint.Style.STROKE);
        progPaint.setStrokeWidth(w);
        progPaint.setColor(Theme.lime(ctx));
        progPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setProgress(float p, boolean animate) {
        progress = Math.max(0f, Math.min(1f, p));
        if (animator != null) animator.cancel();
        if (!animate) {
            animated = progress;
            invalidate();
            return;
        }
        animator = ValueAnimator.ofFloat(animated, progress);
        animator.setDuration(700);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> {
            animated = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float pad = Theme.dp(getContext(), 8);
        float r = Math.min(getWidth(), getHeight()) / 2f - pad;
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        c.drawCircle(cx, cy, r, trackPaint);
        if (animated > 0.001f) {
            c.drawArc(cx - r, cy - r, cx + r, cy + r, -90, 360 * animated, false, progPaint);
        }
    }
}
