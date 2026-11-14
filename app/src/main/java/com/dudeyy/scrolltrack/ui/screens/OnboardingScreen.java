package com.dudeyy.scrolltrack.ui.screens;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.dudeyy.scrolltrack.R;
import com.dudeyy.scrolltrack.data.prefs.Prefs;
import com.dudeyy.scrolltrack.tracking.PermissionChecks;
import com.dudeyy.scrolltrack.ui.Theme;
import com.dudeyy.scrolltrack.ui.components.UiKit;

/** 5-page onboarding: what it does, what it tracks, two permissions, verify. */
public class OnboardingScreen extends LinearLayout {

    public interface OnboardingDone { void onDone(); }

    private final OnboardingDone doneCallback;
    private final Prefs prefs;
    private int page = 0;
    private static final int PAGES = 5;

    private FrameLayout pageSlot;
    private LinearLayout dots;

    public OnboardingScreen(Context ctx, OnboardingDone done) {
        super(ctx);
        this.doneCallback = done;
        this.prefs = Prefs.get(ctx);
        setOrientation(VERTICAL);
        setBackgroundColor(Theme.bg(ctx));
        build();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        renderPage();
    }

    private void build() {
        Context ctx = getContext();
        int hp = Theme.dp(ctx, 24);
        setPadding(hp, Theme.dp(ctx, 32), hp, Theme.dp(ctx, 24));

        TextView skip = UiKit.tv(ctx, "Skip", 14, Theme.lime(ctx), Theme.body(ctx));
        skip.setGravity(Gravity.END);
        skip.setOnClickListener(v -> finish());
        addView(skip);

        pageSlot = new FrameLayout(ctx);
        pageSlot.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
        addView(pageSlot);

        dots = UiKit.row(ctx);
        dots.setGravity(Gravity.CENTER);
        addView(dots);
        addView(UiKit.vspace(ctx, 16));

        LinearLayout nav = UiKit.row(ctx);
        TextView back = UiKit.quietButton(ctx, "Back", v -> {
            if (page > 0) { page--; renderPage(); }
        });
        back.setId(1001);
        View spring = new View(ctx);
        spring.setLayoutParams(new LayoutParams(0, 1, 1f));
        TextView next = UiKit.primaryButton(ctx, "Next", v -> {
            if (page < PAGES - 1) { page++; renderPage(); }
            else finish();
        });
        next.setId(1002);
        nav.addView(back);
        nav.addView(spring);
        nav.addView(next);
        addView(nav);
    }

    private void renderPage() {
        Context ctx = getContext();
        pageSlot.removeAllViews();
        dots.removeAllViews();
        for (int i = 0; i < PAGES; i++) {
            View d = new View(ctx);
            int s = Theme.dp(ctx, i == page ? 22 : 8);
            d.setLayoutParams(new LayoutParams(s, Theme.dp(ctx, 8)));
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setCornerRadius(Theme.dp(ctx, 4));
            bg.setColor(i == page ? Theme.lime(ctx) : Theme.outline(ctx));
            d.setBackground(bg);
            dots.addView(d);
            if (i < PAGES - 1) dots.addView(UiKit.spacer(ctx, 6));
        }
        TextView back = findViewById(1001);
        TextView next = findViewById(1002);
        back.setVisibility(page == 0 ? INVISIBLE : VISIBLE);
        next.setText(page == PAGES - 1 ? "Start tracking" : "Next");

        switch (page) {
            case 0: pageSlot.addView(pageWelcome(ctx)); break;
            case 1: pageSlot.addView(pageWhatWeTrack(ctx)); break;
            case 2: pageSlot.addView(pageAccessibility(ctx)); break;
            case 3: pageSlot.addView(pageUsage(ctx)); break;
            default: pageSlot.addView(pageVerify(ctx)); break;
        }
    }

    private LinearLayout pageShell(Context ctx) {
        LinearLayout l = new LinearLayout(ctx);
        l.setOrientation(VERTICAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        return l;
    }

    private View pageWelcome(Context ctx) {
        LinearLayout l = pageShell(ctx);
        ImageView logo = new ImageView(ctx);
        logo.setImageResource(R.drawable.ic_launcher);
        int s = Theme.dp(ctx, 84);
        logo.setLayoutParams(new LayoutParams(s, s));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(Theme.dp(ctx, 22));
        bg.setColor(Theme.surface2(ctx));
        logo.setBackground(bg);
        logo.setPadding(Theme.dp(ctx, 12), Theme.dp(ctx, 12), Theme.dp(ctx, 12), Theme.dp(ctx, 12));
        l.addView(logo);
        l.addView(UiKit.vspace(ctx, 20));
        TextView t = UiKit.tv(ctx, "Know your scroll.", Theme.SIZE_LARGE_TITLE, Theme.text(ctx), Theme.display(ctx));
        t.setTypeface(Theme.display(ctx), Typeface.BOLD);
        t.setLetterSpacing(Theme.TRACKING_HEADLINE);
        t.setGravity(Gravity.CENTER);
        l.addView(t);
        l.addView(UiKit.vspace(ctx, 10));
        TextView s2 = UiKit.tv(ctx,
                "ScrollTrack counts how many times you swipe through Instagram Reels and turns it into honest, private statistics — all on your device.",
                Theme.SIZE_BODY, Theme.text2(ctx), Theme.body(ctx));
        s2.setGravity(Gravity.CENTER);
        l.addView(s2);
        return l;
    }

    private View pageWhatWeTrack(Context ctx) {
        LinearLayout l = pageShell(ctx);
        l.addView(pageTitle(ctx, "What gets tracked"));
        l.addView(UiKit.vspace(ctx, 14));
        l.addView(bullet(ctx, "Upward swipe gestures while Instagram is in front"));
        l.addView(bullet(ctx, "Session start/end times and durations"));
        l.addView(bullet(ctx, "Your limits and preferences"));
        l.addView(UiKit.vspace(ctx, 14));
        l.addView(pageTitle(ctx, "What never gets tracked"));
        l.addView(UiKit.vspace(ctx, 14));
        l.addView(bullet(ctx, "No passwords, messages, or contacts"));
        l.addView(bullet(ctx, "No screen content, screenshots, or recordings"));
        l.addView(bullet(ctx, "Nothing ever leaves your phone"));
        return l;
    }

    private TextView pageTitle(Context ctx, String t) {
        TextView v = UiKit.tv(ctx, t, Theme.SIZE_TITLE_1, Theme.text(ctx), Theme.display(ctx));
        v.setTypeface(Theme.display(ctx), Typeface.BOLD);
        v.setLetterSpacing(Theme.TRACKING_HEADLINE);
        v.setGravity(Gravity.CENTER);
        return v;
    }

    private View bullet(Context ctx, String text) {
        LinearLayout r = UiKit.row(ctx);
        r.setPadding(0, Theme.dp(ctx, 5), 0, Theme.dp(ctx, 5));
        View dot = new View(ctx);
        int s = Theme.dp(ctx, 7);
        dot.setLayoutParams(new LayoutParams(s, s));
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(Theme.lime(ctx));
        dot.setBackground(d);
        r.addView(dot);
        r.addView(UiKit.spacer(ctx, 12));
        r.addView(UiKit.tv(ctx, text, Theme.SIZE_BODY, Theme.text(ctx), Theme.body(ctx)));
        return r;
    }

    private View pageAccessibility(Context ctx) {
        LinearLayout l = pageShell(ctx);
        l.addView(pageTitle(ctx, "Count your swipes"));
        l.addView(UiKit.vspace(ctx, 10));
        TextView s = UiKit.tv(ctx,
                "To count Reels swipes, ScrollTrack uses Android's accessibility service — limited to the Instagram app only. " +
                "It observes scroll gestures, never text or content. Nothing is granted automatically: you enable it in system settings.", Theme.SIZE_BODY, Theme.text2(ctx), Theme.body(ctx));
        s.setGravity(Gravity.CENTER);
        l.addView(s);
        l.addView(UiKit.vspace(ctx, 18));
        boolean on = PermissionChecks.isAccessibilityEnabled(ctx);
        l.addView(UiKit.statusPill(ctx, on ? Theme.lime(ctx) : Theme.amber(ctx),
                on ? "Enabled" : "Not enabled yet"));
        l.addView(UiKit.vspace(ctx, 14));
        if (!on) {
            TextView b = UiKit.primaryButton(ctx, "Open accessibility settings",
                    v -> openSys(ctx, Settings.ACTION_ACCESSIBILITY_SETTINGS));
            l.addView(b);
        }
        return l;
    }

    private View pageUsage(Context ctx) {
        LinearLayout l = pageShell(ctx);
        l.addView(pageTitle(ctx, "Detect your sessions"));
        l.addView(UiKit.vspace(ctx, 10));
        TextView s = UiKit.tv(ctx,
                "To know when Instagram is open, ScrollTrack needs Usage Access — a system permission that reports which app is in the foreground. " +
                "It's read-only and stays on your device. You grant it in system settings.", Theme.SIZE_BODY, Theme.text2(ctx), Theme.body(ctx));
        s.setGravity(Gravity.CENTER);
        l.addView(s);
        l.addView(UiKit.vspace(ctx, 18));
        boolean on = PermissionChecks.hasUsageAccess(ctx);
        l.addView(UiKit.statusPill(ctx, on ? Theme.lime(ctx) : Theme.amber(ctx),
                on ? "Granted" : "Not granted yet"));
        l.addView(UiKit.vspace(ctx, 14));
        if (!on) {
            TextView b = UiKit.primaryButton(ctx, "Open usage access settings",
                    v -> openSys(ctx, Settings.ACTION_USAGE_ACCESS_SETTINGS));
            l.addView(b);
        }
        return l;
    }

    private View pageVerify(Context ctx) {
        LinearLayout l = pageShell(ctx);
        l.addView(pageTitle(ctx, "Check it's working"));
        l.addView(UiKit.vspace(ctx, 10));
        TextView s = UiKit.tv(ctx,
                "1. Open Instagram and scroll through Reels.\n\n" +
                "2. Come back here — the dashboard shows a live status and today's swipe count.\n\n" +
                "The status pill reads \"Tracking\" while Instagram is in front and \"Waiting\" otherwise. " +
                "If something's missing, Settings → Tracking shows exactly what's needed.", Theme.SIZE_BODY, Theme.text2(ctx), Theme.body(ctx));
        l.addView(s);
        l.addView(UiKit.vspace(ctx, 14));
        l.addView(UiKit.tv(ctx, "Limits are optional — tracking works fully without ever setting one.",
                13, Theme.text3(ctx), Theme.body(ctx)));
        return l;
    }

    private void openSys(Context ctx, String action) {
        try { ctx.startActivity(new Intent(action)); } catch (Exception ignored) { }
    }

    private void finish() {
        prefs.setOnboardingDone(true);
        doneCallback.onDone();
    }
}
