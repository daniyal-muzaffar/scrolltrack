package com.dudeyy.scrolltrack.ui;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;
import android.view.Window;

import com.dudeyy.scrolltrack.R;
import com.dudeyy.scrolltrack.data.prefs.Prefs;

/**
 * Design tokens from the Apple design.md (apple.com + HIG, iOS 26 era).
 *
 * Dark is the default. Light is kept for the theme setting. One accent
 * color only: lime takes the role Apple's blue tint plays (key numbers,
 * active states, primary actions, chart highlights) — no second accent.
 *
 * Font: SF Pro is not available on Android. Inter (already bundled) is the
 * closest free equivalent and is used for both display and body; the SF Pro
 * type scale and letter spacing are applied on top of it.
 *
 * Blur: Android cannot do true backdrop blur here, so the floating tab bar
 * uses a translucent surface (~90%) with a hairline border — the closest
 * honest approximation, no fake or heavy effects.
 */
public final class Theme {
    private Theme() {}

    // ---- radii (dp) ----
    public static final int RADIUS_CARD = 18;
    public static final int RADIUS_PILL = 999;
    public static final int RADIUS_BUTTON = 12;
    public static final int RADIUS_SEGMENT = 9;

    // ---- type scale (sp, SF Pro) ----
    public static final float SIZE_LARGE_TITLE = 34;
    public static final float SIZE_TITLE_1 = 28;
    public static final float SIZE_TITLE_2 = 22;
    public static final float SIZE_TITLE_3 = 20;
    public static final float SIZE_HEADLINE = 17;
    public static final float SIZE_BODY = 17;
    public static final float SIZE_CALLOUT = 16;
    public static final float SIZE_SUBHEAD = 15;
    public static final float SIZE_FOOTNOTE = 13;
    public static final float SIZE_CAPTION = 12;
    public static final float SIZE_CAPTION_2 = 11;

    // ---- letter spacing (em) ----
    public static final float TRACKING_BODY = -0.022f;
    public static final float TRACKING_HEADLINE = -0.005f;
    public static final float TRACKING_CAPTION = -0.01f;

    // ---- spacing (dp, 4pt base) ----
    public static final int MARGIN_H = 16;
    public static final int GAP_SECTION = 12;
    public static final int BOTTOM_CLEARANCE = 96; // clears the floating tab bar

    private static Typeface display;
    private static Typeface body;

    public static void init(Context ctx) {
        if (display == null) {
            try {
                Resources res = ctx.getResources();
                display = res.getFont(R.font.display);
                body = res.getFont(R.font.body);
            } catch (Exception ignored) { }
        }
    }

    public static Typeface display(Context ctx) {
        init(ctx);
        return display != null ? display : Typeface.DEFAULT;
    }

    public static Typeface body(Context ctx) {
        init(ctx);
        return body != null ? body : Typeface.DEFAULT;
    }

    public static boolean isDark(Context ctx) {
        String t = Prefs.get(ctx).getTheme();
        if ("dark".equals(t)) return true;
        if ("light".equals(t)) return false;
        int night = ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return night != Configuration.UI_MODE_NIGHT_NO;
    }

    public static int styleFor(Context ctx) {
        return isDark(ctx) ? R.style.Theme_ScrollTrack : R.style.Theme_ScrollTrack_Light;
    }

    // ---- palette (grouped backgrounds, Apple) ----
    public static int bg(Context ctx) { return isDark(ctx) ? 0xFF000000 : 0xFFF2F2F7; }
    public static int surface(Context ctx) { return isDark(ctx) ? 0xFF1C1C1E : 0xFFFFFFFF; }
    public static int surface2(Context ctx) { return isDark(ctx) ? 0xFF2C2C2E : 0xFFE5E5EA; }
    /** Hairline separator: 12% black on light, 12% white on dark. */
    public static int outline(Context ctx) { return isDark(ctx) ? 0x1FFFFFFF : 0x1F000000; }
    public static int text(Context ctx) { return isDark(ctx) ? 0xFFF5F5F7 : 0xFF1D1D1F; }
    public static int text2(Context ctx) { return isDark(ctx) ? 0xFFA1A1A6 : 0xFF6E6E73; }
    public static int text3(Context ctx) { return isDark(ctx) ? 0xFF8E8E93 : 0xFF86868B; }
    /** The single accent. Dark: lime; light: darker lime for contrast. */
    public static int lime(Context ctx) { return isDark(ctx) ? 0xFFC6F135 : 0xFF5F8F16; }
    public static int limeFill(Context ctx) { return lime(ctx); }
    public static int onLime(Context ctx) { return 0xFF0B0E0C; }
    public static int limeSoft(Context ctx) {
        return isDark(ctx) ? 0x26C6F135 : 0x1F5F8F16;
    }
    /** System orange / red (Apple), for warnings and destructive actions. */
    public static int amber(Context ctx) { return isDark(ctx) ? 0xFFFF9F0A : 0xFFFF9500; }
    public static int red(Context ctx) { return isDark(ctx) ? 0xFFFF453A : 0xFFFF3B30; }
    /** Muted chart bars and the switch "off" track. */
    public static int barDim(Context ctx) { return isDark(ctx) ? 0xFF3A3A3C : 0xFFE5E5EA; }
    public static int switchOff(Context ctx) { return isDark(ctx) ? 0xFF3A3A3C : 0xFFE5E5EA; }
    /** Translucent surface for the floating tab bar (Liquid Glass approximation). */
    public static int navBg(Context ctx) { return isDark(ctx) ? 0xE61C1C1E : 0xE6FFFFFF; }

    // ---- metrics ----
    public static int dp(Context ctx, float dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                ctx.getResources().getDisplayMetrics());
    }

    public static float sp(Context ctx, float sp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp,
                ctx.getResources().getDisplayMetrics());
    }

    /** Applies status/nav bar colors for the active theme. */
    public static void applyChrome(Activity activity) {
        boolean dark = isDark(activity);
        Window w = activity.getWindow();
        int bg = bg(activity);
        w.setStatusBarColor(bg);
        w.setNavigationBarColor(bg);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            int app = dark ? 0 : WindowInsetsController_APPEARANCE_LIGHT_STATUS_BARS;
            w.getInsetsController().setSystemBarsAppearance(
                    app, WindowInsetsController_APPEARANCE_LIGHT_STATUS_BARS);
        } else {
            @SuppressWarnings("deprecation")
            int vis = activity.getWindow().getDecorView().getSystemUiVisibility();
            @SuppressWarnings("deprecation")
            int flag = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            activity.getWindow().getDecorView().setSystemUiVisibility(dark ? (vis & ~flag) : (vis | flag));
        }
    }

    // android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS == 8 (avoid lint dependency)
    private static final int WindowInsetsController_APPEARANCE_LIGHT_STATUS_BARS = 8;

    public static int parseColorSafe(String s, int fallback) {
        try { return Color.parseColor(s); } catch (Exception e) { return fallback; }
    }
}
