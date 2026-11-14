package com.dudeyy.scrolltrack.tracking;

import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.dudeyy.scrolltrack.MainActivity;
import com.dudeyy.scrolltrack.data.prefs.Prefs;
import com.dudeyy.scrolltrack.ui.Theme;

/**
 * Floating "reels counted" pill shown near the top of the screen while an
 * Instagram session is active. Requires the "Display over other apps"
 * permission; the pill is draggable and a tap opens the app.
 *
 * All WindowManager calls happen on the main thread.
 */
public class OverlayPill {
    private final Context context;
    private final Prefs prefs;
    private final WindowManager wm;
    private final Handler main = new Handler(Looper.getMainLooper());

    private LinearLayout view;
    private TextView countText;
    private WindowManager.LayoutParams params;

    public OverlayPill(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = Prefs.get(this.context);
        this.wm = (WindowManager) this.context.getSystemService(Context.WINDOW_SERVICE);
    }

    public boolean canShow() {
        try {
            return Settings.canDrawOverlays(context);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isShowing() {
        return view != null;
    }

    public void show(final int count) {
        main.post(() -> {
            if (view != null) {
                update(count);
                return;
            }
            if (!canShow()) return;
            try {
                view = buildView();
                params = buildParams();
                wm.addView(view, params);
                update(count);
            } catch (Exception e) {
                view = null;
                params = null;
            }
        });
    }

    public void update(final int count) {
        main.post(() -> {
            if (view != null && countText != null) {
                countText.setText(String.valueOf(count));
            }
        });
    }

    public void hide() {
        main.post(() -> {
            if (view != null) {
                try {
                    wm.removeView(view);
                } catch (Exception ignored) { }
                view = null;
                countText = null;
                params = null;
            }
        });
    }

    private LinearLayout buildView() {
        LinearLayout pill = new LinearLayout(context);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        int padH = dp(14);
        int padV = dp(8);
        pill.setPadding(padH, padV, padH, padV);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(24));
        bg.setColor(Theme.lime(context));
        pill.setBackground(bg);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            pill.setElevation(dp(6));
        }

        TextView dot = new TextView(context);
        dot.setText("●");
        dot.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        dot.setTextColor(Theme.onLime(context));

        countText = new TextView(context);
        countText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        countText.setTypeface(Theme.display(context), android.graphics.Typeface.BOLD);
        countText.setTextColor(Theme.onLime(context));

        TextView label = new TextView(context);
        label.setText(" reels");
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        label.setTypeface(Theme.body(context));
        label.setTextColor(Theme.onLime(context));

        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dotLp.rightMargin = dp(6);
        pill.addView(dot, dotLp);
        pill.addView(countText);
        pill.addView(label);

        pill.setOnTouchListener(new DragTouchListener());
        return pill;
    }

    private WindowManager.LayoutParams buildParams() {
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        int savedX = prefs.getOverlayX(Integer.MIN_VALUE);
        int savedY = prefs.getOverlayY(Integer.MIN_VALUE);
        if (savedX != Integer.MIN_VALUE && savedY != Integer.MIN_VALUE) {
            p.gravity = Gravity.TOP | Gravity.START;
            p.x = savedX;
            p.y = savedY;
        } else {
            p.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            p.y = statusBarHeight() + dp(10);
        }
        return p;
    }

    private int statusBarHeight() {
        try {
            int id = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
            if (id > 0) return context.getResources().getDimensionPixelSize(id);
        } catch (Exception ignored) { }
        return dp(24);
    }

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                context.getResources().getDisplayMetrics()));
    }

    private void openApp() {
        try {
            Intent i = new Intent(context, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            context.startActivity(i);
        } catch (Exception ignored) { }
    }

    private class DragTouchListener implements View.OnTouchListener {
        private float downRawX, downRawY;
        private int downX, downY;
        private boolean dragging;
        private final int slop = ViewConfiguration.get(context).getScaledTouchSlop();

        @Override
        public boolean onTouch(View v, MotionEvent ev) {
            switch (ev.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    // Convert centered gravity to absolute so drag math is stable.
                    if ((params.gravity & Gravity.CENTER_HORIZONTAL) == Gravity.CENTER_HORIZONTAL) {
                        params.gravity = Gravity.TOP | Gravity.START;
                        params.x = Math.max(0, (v.getRootView().getWidth() - v.getWidth()) / 2);
                        try {
                            wm.updateViewLayout(view, params);
                        } catch (Exception ignored) { }
                    }
                    downRawX = ev.getRawX();
                    downRawY = ev.getRawY();
                    downX = params.x;
                    downY = params.y;
                    dragging = false;
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    int nx = downX + Math.round(ev.getRawX() - downRawX);
                    int ny = downY + Math.round(ev.getRawY() - downRawY);
                    if (!dragging && Math.abs(nx - downX) + Math.abs(ny - downY) > slop) {
                        dragging = true;
                    }
                    if (dragging) {
                        params.x = Math.max(0, nx);
                        params.y = Math.max(0, ny);
                        try {
                            wm.updateViewLayout(view, params);
                        } catch (Exception ignored) { }
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                    if (dragging) {
                        prefs.setOverlayX(params.x);
                        prefs.setOverlayY(params.y);
                    } else {
                        v.performClick();
                        openApp();
                    }
                    return true;
                default:
                    return false;
            }
        }
    }
}
