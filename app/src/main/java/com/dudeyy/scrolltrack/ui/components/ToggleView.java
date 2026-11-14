package com.dudeyy.scrolltrack.ui.components;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import com.dudeyy.scrolltrack.ui.Theme;

/** Premium custom switch: animated thumb on a rounded track. */
public class ToggleView extends View {
    private boolean checked;
    private float anim = 0f; // 0 = off, 1 = on
    private OnCheckedChangeListener listener;
    private ValueAnimator animator;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public interface OnCheckedChangeListener { void onCheckedChanged(boolean checked); }

    public ToggleView(Context ctx) { super(ctx); init(); }
    public ToggleView(Context ctx, AttributeSet a) { super(ctx, a); init(); }

    private void init() {
        setClickable(true);
        setFocusable(true);
        setOnClickListener(v -> setChecked(!checked, true));
    }

    public void setOnCheckedChangeListener(OnCheckedChangeListener l) { listener = l; }

    public boolean isChecked() { return checked; }

    public void setChecked(boolean c, boolean notify) {
        if (checked == c && (anim == (c ? 1f : 0f))) return;
        checked = c;
        if (animator != null) animator.cancel();
        animator = ValueAnimator.ofFloat(anim, c ? 1f : 0f);
        animator.setDuration(220);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> {
            anim = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
        if (notify && listener != null) listener.onCheckedChanged(c);
    }

    public void setCheckedSilent(boolean c) {
        checked = c;
        anim = c ? 1f : 0f;
        invalidate();
    }

    @Override
    protected void onMeasure(int wms, int hms) {
        setMeasuredDimension(Theme.dp(getContext(), 52), Theme.dp(getContext(), 30));
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        Context ctx = getContext();
        float w = getWidth(), h = getHeight();
        int trackOn = Theme.lime(ctx);
        int trackOff = Theme.switchOff(ctx);
        trackPaint.setColor(blend(trackOff, trackOn, anim));
        c.drawRoundRect(0, 0, w, h, h / 2f, h / 2f, trackPaint);
        float pad = Theme.dp(ctx, 3);
        float r = (h - pad * 2) / 2f;
        float cx = pad + r + (w - pad * 2 - r * 2) * anim;
        thumbPaint.setColor(anim > 0.5f ? Theme.onLime(ctx) : 0xFFFFFFFF);
        c.drawCircle(cx, h / 2f, r, thumbPaint);
    }

    private static int blend(int a, int b, float t) {
        return android.graphics.Color.argb(
                (int) (android.graphics.Color.alpha(a) + (android.graphics.Color.alpha(b) - android.graphics.Color.alpha(a)) * t),
                (int) (android.graphics.Color.red(a) + (android.graphics.Color.red(b) - android.graphics.Color.red(a)) * t),
                (int) (android.graphics.Color.green(a) + (android.graphics.Color.green(b) - android.graphics.Color.green(a)) * t),
                (int) (android.graphics.Color.blue(a) + (android.graphics.Color.blue(b) - android.graphics.Color.blue(a)) * t));
    }
}
