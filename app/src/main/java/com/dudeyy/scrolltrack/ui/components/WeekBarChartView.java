package com.dudeyy.scrolltrack.ui.components;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import com.dudeyy.scrolltrack.ui.Theme;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Custom 7-day / 8-week bar chart with average line, drawn on canvas. */
public class WeekBarChartView extends View {

    public static class Bar {
        public final String label;
        public final int value;
        public final boolean highlight;
        public Bar(String label, int value, boolean highlight) {
            this.label = label; this.value = value; this.highlight = highlight;
        }
    }

    private List<Bar> bars = new ArrayList<>();
    private float average;
    private float reveal = 1f; // 0..1 bar-height animation
    private ValueAnimator animator;

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barDimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint avgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    public WeekBarChartView(Context ctx) { super(ctx); init(ctx); }
    public WeekBarChartView(Context ctx, AttributeSet a) { super(ctx, a); init(ctx); }

    private void init(Context ctx) {
        barPaint.setColor(Theme.limeFill(ctx));
        barDimPaint.setColor(Theme.barDim(ctx));
        textPaint.setColor(Theme.text3(ctx));
        textPaint.setTextSize(Theme.sp(ctx, 11));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Theme.body(ctx));
        valuePaint.setColor(Theme.text2(ctx));
        valuePaint.setTextSize(Theme.sp(ctx, 11));
        valuePaint.setTextAlign(Paint.Align.CENTER);
        valuePaint.setTypeface(Theme.body(ctx));
        avgPaint.setColor(Theme.lime(ctx));
        avgPaint.setStyle(Paint.Style.STROKE);
        avgPaint.setStrokeWidth(Theme.dp(ctx, 1.2f));
        avgPaint.setPathEffect(new DashPathEffect(new float[]{Theme.dp(ctx, 5), Theme.dp(ctx, 4)}, 0));
    }

    public void setData(List<Bar> bars, float average, boolean animate) {
        this.bars = bars != null ? bars : new ArrayList<>();
        this.average = average;
        if (animator != null) animator.cancel();
        if (!animate) {
            reveal = 1f;
            invalidate();
            return;
        }
        reveal = 0f;
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(650);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> {
            reveal = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onMeasure(int wms, int hms) {
        int w = MeasureSpec.getSize(wms);
        int h = Theme.dp(getContext(), 190);
        setMeasuredDimension(w, resolveSize(h, hms));
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        if (bars.isEmpty()) return;
        float padL = Theme.dp(getContext(), 4);
        float padR = Theme.dp(getContext(), 4);
        float topPad = Theme.dp(getContext(), 26);
        float labelPad = Theme.dp(getContext(), 22);
        float plotH = getHeight() - topPad - labelPad;
        float plotW = getWidth() - padL - padR;
        int n = bars.size();
        float slot = plotW / n;
        float barW = Math.min(Theme.dp(getContext(), 30), slot * 0.52f);

        int maxV = 1;
        for (Bar b : bars) maxV = Math.max(maxV, b.value);

        float baseY = topPad + plotH;
        for (int i = 0; i < n; i++) {
            Bar b = bars.get(i);
            float cx = padL + slot * i + slot / 2f;
            float frac = maxV == 0 ? 0 : (b.value / (float) maxV);
            float h = Math.max(Theme.dp(getContext(), 4), plotH * frac * reveal);
            float top = baseY - h;
            rect.set(cx - barW / 2f, top, cx + barW / 2f, baseY);
            float rad = Math.min(barW / 2f, Theme.dp(getContext(), 7));
            c.drawRoundRect(rect, rad, rad, b.highlight ? barPaint : barDimPaint);
            if (b.value > 0 && reveal > 0.85f) {
                c.drawText(String.valueOf(b.value), cx, top - Theme.dp(getContext(), 6), valuePaint);
            }
            c.drawText(b.label, cx, getHeight() - Theme.dp(getContext(), 4), textPaint);
        }

        // average line
        if (average > 0.05f && maxV > 0) {
            float y = baseY - plotH * Math.min(1f, average / maxV) * reveal;
            android.graphics.Path p = new android.graphics.Path();
            p.moveTo(padL, y);
            p.lineTo(padL + plotW, y);
            c.drawPath(p, avgPaint);
            String avgLabel = String.format(Locale.getDefault(), "avg %.0f", average);
            Paint lp = new Paint(valuePaint);
            lp.setTextAlign(Paint.Align.RIGHT);
            lp.setColor(Theme.lime(getContext()));
            c.drawText(avgLabel, padL + plotW, y - Theme.dp(getContext(), 5), lp);
        }
    }
}
