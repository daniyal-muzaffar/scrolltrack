package com.dudeyy.scrolltrack.ui.screens;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.dudeyy.scrolltrack.data.prefs.Prefs;
import com.dudeyy.scrolltrack.data.repo.StatsRepository;
import com.dudeyy.scrolltrack.tracking.PermissionChecks;
import com.dudeyy.scrolltrack.tracking.TrackingService;
import com.dudeyy.scrolltrack.tracking.TrackingState;
import com.dudeyy.scrolltrack.ui.ScreenHost;
import com.dudeyy.scrolltrack.ui.Theme;
import com.dudeyy.scrolltrack.ui.components.ToggleView;
import com.dudeyy.scrolltrack.ui.components.UiKit;

import java.io.File;

/** Settings: tracking setup, appearance, data management, about. */
public class SettingsScreen extends ScrollView {

    private final ScreenHost host;
    private final StatsRepository repo;
    private final Prefs prefs;

    private LinearLayout trackingCard;
    private LinearLayout themeCard;
    private LinearLayout updateCard;
    private LinearLayout statsCard;
    private final TrackingService.TrackingListener trackingListener = new TrackingService.TrackingListener() {
        @Override public void onStateChanged(TrackingState state) { renderTrackingCard(); }
        @Override public void onTodaySwipesChanged(int swipes) { }
        @Override public void onSwipeCountingChanged(boolean available) { renderTrackingCard(); }
    };
    private final Prefs.Listener prefsListener = this::refresh;

    public SettingsScreen(Context ctx, ScreenHost host) {
        super(ctx);
        this.host = host;
        this.repo = StatsRepository.get(ctx);
        this.prefs = Prefs.get(ctx);
        setFillViewport(true);
        build();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        TrackingService.addListener(trackingListener);
        prefs.addListener(prefsListener);
        refresh();
    }

    @Override protected void onDetachedFromWindow() {
        TrackingService.removeListener(trackingListener);
        prefs.removeListener(prefsListener);
        super.onDetachedFromWindow();
    }

    private void build() {
        Context ctx = getContext();
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        int hp = Theme.dp(ctx, Theme.MARGIN_H);
        root.setPadding(hp, Theme.dp(ctx, 16), hp, Theme.dp(ctx, Theme.BOTTOM_CLEARANCE));
        addView(root, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        TextView title = UiKit.screenTitle(ctx, "Settings");
        root.addView(title);
        root.addView(UiKit.vspace(ctx, 14));

        root.addView(sectionLabel(ctx, "Tracking"));
        trackingCard = UiKit.card(ctx);
        root.addView(trackingCard);
        root.addView(UiKit.vspace(ctx, 18));

        root.addView(sectionLabel(ctx, "Appearance"));
        themeCard = UiKit.card(ctx);
        root.addView(themeCard);
        root.addView(UiKit.vspace(ctx, 18));

        root.addView(sectionLabel(ctx, "App updates"));
        updateCard = UiKit.card(ctx);
        root.addView(updateCard);
        root.addView(UiKit.vspace(ctx, 18));

        root.addView(sectionLabel(ctx, "Anonymous stats"));
        statsCard = UiKit.card(ctx);
        root.addView(statsCard);
        root.addView(UiKit.vspace(ctx, 18));

        root.addView(sectionLabel(ctx, "Data"));
        root.addView(buildDataCard(ctx));
        root.addView(UiKit.vspace(ctx, 18));

        root.addView(sectionLabel(ctx, "About"));
        root.addView(buildAboutCard(ctx));
    }

    private TextView sectionLabel(Context ctx, String text) {
        TextView t = UiKit.caption(ctx, text);
        t.setPadding(0, 0, 0, Theme.dp(ctx, 8));
        return t;
    }

    // ---------- tracking ----------

    private void renderTrackingCard() {
        Context ctx = getContext();
        trackingCard.removeAllViews();

        TrackingState s = TrackingService.getState();
        LinearLayout statusRow = UiKit.row(ctx);
        statusRow.setPadding(0, Theme.dp(ctx, 8), 0, Theme.dp(ctx, 8));
        LinearLayout texts = new LinearLayout(ctx);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView t = UiKit.tv(ctx, "Tracking status", 15, Theme.text(ctx), Theme.body(ctx));
        t.setTypeface(Theme.body(ctx), Typeface.BOLD);
        texts.addView(t);
        String sub;
        switch (s) {
            case ACTIVE: sub = "Active — Instagram is in the foreground."; break;
            case WAITING: sub = "On — waiting for Instagram."; break;
            case PAUSED: sub = "Paused by you."; break;
            case PERMISSION_REQUIRED: sub = "Needs Usage Access permission."; break;
            default: sub = "Instagram is not installed.";
        }
        texts.addView(UiKit.tv(ctx, sub, 13, Theme.text2(ctx), Theme.body(ctx)));
        statusRow.addView(texts);
        statusRow.addView(UiKit.statusPill(ctx, UiKit.statusColor(ctx, s), UiKit.statusLabel(s)));
        trackingCard.addView(statusRow);
        trackingCard.addView(UiKit.dividerInset(ctx));

        ToggleView enable = new ToggleView(ctx);
        enable.setCheckedSilent(prefs.isTrackingEnabled());
        trackingCard.addView(UiKit.settingRow(ctx, "Enable tracking",
                "Master switch for all monitoring.", enable, null));
        enable.setOnCheckedChangeListener(c -> {
            prefs.setTrackingEnabled(c);
            if (c) TrackingService.start(ctx);
            else {
                Intent i = new Intent(ctx, TrackingService.class);
                i.setAction(TrackingService.ACTION_PAUSE);
                try { ctx.startService(i); } catch (IllegalStateException ignored) { }
            }
        });
        trackingCard.addView(UiKit.dividerInset(ctx));

        boolean a11y = PermissionChecks.isAccessibilityEnabled(ctx);
        trackingCard.addView(UiKit.settingRow(ctx, "Accessibility service",
                a11y ? "Enabled — swipe gestures are counted."
                        : "Required for per-swipe counting. Opens system settings.",
                statusChip(ctx, a11y), v -> openSettings(ctx, Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        trackingCard.addView(UiKit.dividerInset(ctx));

        boolean usage = PermissionChecks.hasUsageAccess(ctx);
        trackingCard.addView(UiKit.settingRow(ctx, "Usage access",
                usage ? "Granted — Instagram sessions are detected."
                        : "Required to detect Instagram sessions. Opens system settings.",
                statusChip(ctx, usage), v -> openSettings(ctx, Settings.ACTION_USAGE_ACCESS_SETTINGS)));
        trackingCard.addView(UiKit.dividerInset(ctx));

        boolean batt = PermissionChecks.isBatteryOptimizationIgnored(ctx);
        trackingCard.addView(UiKit.settingRow(ctx, "Battery optimization",
                batt ? "Unrestricted — tracking survives in the background."
                        : "Recommended: allow unrestricted battery use so tracking isn't killed.",
                statusChip(ctx, batt), v -> {
                    try {
                        Intent i = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                        ctx.startActivity(i);
                    } catch (Exception ignored) { }
                }));
        trackingCard.addView(UiKit.dividerInset(ctx));

        long lastGesture = TrackingService.getLastGestureSeenTs();
        boolean seen = lastGesture > 0;
        trackingCard.addView(UiKit.settingRow(ctx, "Gesture detection",
                seen ? "Last scroll gesture seen " + com.dudeyy.scrolltrack.util.TimeUtils.clock(lastGesture)
                        + ". If this doesn't update while you swipe through Reels, detection isn't working."
                        : "No scroll gestures detected yet. Open Instagram and swipe through Reels, then come back here.",
                UiKit.statusPill(ctx, seen ? Theme.lime(ctx) : Theme.text3(ctx), seen ? "Working" : "No data"),
                null));
        trackingCard.addView(UiKit.dividerInset(ctx));

        boolean ov = prefs.isOverlayPillEnabled();
        ToggleView ovToggle = new ToggleView(ctx);
        ovToggle.setCheckedSilent(ov);
        ovToggle.setOnCheckedChangeListener(checked -> {
            if (checked && !android.provider.Settings.canDrawOverlays(ctx)) {
                android.widget.Toast.makeText(ctx, "Grant \"Display over other apps\", then turn this on again.",
                        android.widget.Toast.LENGTH_LONG).show();
                try {
                    Intent i = new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            android.net.Uri.parse("package:" + ctx.getPackageName()));
                    ctx.startActivity(i);
                } catch (Exception ignored) { }
                return;
            }
            prefs.setOverlayPillEnabled(checked);
            refresh();
        });
        trackingCard.addView(UiKit.settingRow(ctx, "Floating counter pill",
                "A small live counter near the top of your screen while you scroll Reels. Drag it anywhere; tap it to open ScrollTrack.",
                ovToggle, null));
    }

    private View statusChip(Context ctx, boolean ok) {
        return UiKit.statusPill(ctx, ok ? Theme.lime(ctx) : Theme.amber(ctx), ok ? "On" : "Setup");
    }

    private void openSettings(Context ctx, String action) {
        try { ctx.startActivity(new Intent(action)); } catch (Exception ignored) { }
    }

    // ---------- appearance ----------

    private void renderThemeCard() {
        Context ctx = getContext();
        themeCard.removeAllViews();
        String cur = prefs.getTheme();
        String[][] opts = {{"system", "System default", "Follow your phone's theme."},
                {"dark", "Dark", "Pure black, always."},
                {"light", "Light", "Soft neutral, always."}};
        for (int i = 0; i < opts.length; i++) {
            final String val = opts[i][0];
            boolean sel = val.equals(cur);
            TextView radio = UiKit.tv(ctx, sel ? "●" : "○", 18, sel ? Theme.lime(ctx) : Theme.text3(ctx), Theme.body(ctx));
            LinearLayout row = UiKit.settingRow(ctx, opts[i][1], opts[i][2], radio,
                    v -> {
                        prefs.setTheme(val);
                        host.activityContext().recreate();
                    });
            themeCard.addView(row);
            if (i < opts.length - 1) themeCard.addView(UiKit.dividerInset(ctx));
        }
    }

    // ---------- app updates ----------

    private void renderUpdateCard() {
        Context ctx = getContext();
        updateCard.removeAllViews();

        ToggleView auto = new ToggleView(ctx);
        auto.setCheckedSilent(prefs.isAutoUpdateCheck());
        updateCard.addView(UiKit.settingRow(ctx, "Check automatically",
                "Check once a day when ScrollTrack opens. The check only fetches a small version file — nothing about you is sent.",
                auto, null));
        auto.setOnCheckedChangeListener(c -> prefs.setAutoUpdateCheck(c));
        updateCard.addView(UiKit.dividerInset(ctx));

        updateCard.addView(UiKit.settingRow(ctx, "Check for updates",
                "See if a newer version is available right now.", UiKit.chevron(ctx),
                v -> {
                    android.app.Activity act = host.activityContext();
                    com.dudeyy.scrolltrack.util.AppUpdater.checkForUpdates(ctx, true,
                            (info, reachable) -> {
                                if (!reachable) {
                                    android.widget.Toast.makeText(ctx,
                                            "Couldn't reach the update server — check your connection.",
                                            android.widget.Toast.LENGTH_LONG).show();
                                } else if (info == null) {
                                    android.widget.Toast.makeText(ctx,
                                            "You're on the latest version.",
                                            android.widget.Toast.LENGTH_SHORT).show();
                                } else {
                                    com.dudeyy.scrolltrack.util.AppUpdater.promptUpdate(act, info);
                                }
                            });
                }));
    }

    // ---------- anonymous stats ----------

    private void renderStatsCard() {
        Context ctx = getContext();
        statsCard.removeAllViews();

        ToggleView share = new ToggleView(ctx);
        share.setCheckedSilent(prefs.isAnonymousStatsEnabled());
        statsCard.addView(UiKit.settingRow(ctx, "Share anonymous usage stats",
                "Once a day, sends only a random ID, the date, today's swipe total and Instagram minutes. No names, sessions or content. Off by default — turning it off stops uploads.",
                share, null));
        share.setOnCheckedChangeListener(c -> {
            prefs.setAnonymousStatsEnabled(c);
            if (c) com.dudeyy.scrolltrack.util.StatsUploader.uploadNow(ctx);
            refresh();
        });
        statsCard.addView(UiKit.dividerInset(ctx));

        String lastDay = prefs.getLastStatsUploadDay();
        String status = lastDay.isEmpty()
                ? "Nothing shared yet."
                : "Last shared for " + lastDay + " ("
                        + prefs.getLastStatsUploadSwipes() + " swipes, "
                        + prefs.getLastStatsUploadMinutes() + " min).";
        statsCard.addView(UiKit.settingRow(ctx, "What exactly is sent",
                status + " Tap to see the full payload.", UiKit.chevron(ctx),
                v -> UiKit.infoDialog(ctx, "What exactly is sent",
                        "One row per day, and only while the toggle above is on:\n\n" +
                        "{\"p_install_id\": \"<random ID made up on this phone>\",\n" +
                        " \"p_day\": \"2026-11-15\",\n" +
                        " \"p_swipes\": 623,\n" +
                        " \"p_minutes\": 151,\n" +
                        " \"p_app_version\": \"1.5.0\"}\n\n" +
                        "The random ID cannot be linked to you or this device. " +
                        "No session times, no names, no content — just the day's totals.")));
    }

    // ---------- data ----------

    private View buildDataCard(Context ctx) {
        LinearLayout card = UiKit.card(ctx);
        card.addView(UiKit.settingRow(ctx, "Export history",
                "Save all sessions and daily stats as CSV.", UiKit.chevron(ctx),
                v -> exportCsv()));
        card.addView(UiKit.dividerInset(ctx));
        card.addView(UiKit.settingRow(ctx, "Clear all history",
                "Permanently delete every session and statistic.", null,
                v -> UiKit.confirmDialog(ctx, "Clear all history?",
                        "This permanently deletes all recorded sessions and statistics. This cannot be undone.",
                        "Delete everything", () -> repo.async(() -> {
                            repo.clearAll();
                            return true;
                        }, ok -> refresh())), Theme.red(ctx)));
        return card;
    }

    private void exportCsv() {
        Context ctx = getContext();
        repo.async(() -> {
            File f = repo.exportCsv();
            return f;
        }, f -> {
            if (f == null) {
                UiKit.infoDialog(ctx, "Export failed", "Could not write the CSV file.");
                return;
            }
            Uri uri = Uri.parse("content://com.dudeyy.scrolltrack.csv/" + f.getName());
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/csv");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try {
                ctx.startActivity(Intent.createChooser(share, "Export ScrollTrack history"));
            } catch (Exception e) {
                UiKit.infoDialog(ctx, "Export failed", "No app available to share the file.");
            }
        });
    }

    // ---------- about ----------

    private View buildAboutCard(Context ctx) {
        LinearLayout card = UiKit.card(ctx);
        card.addView(UiKit.settingRow(ctx, "Privacy",
                "What ScrollTrack collects — and what it never touches.", UiKit.chevron(ctx),
                v -> UiKit.infoDialog(ctx, "Privacy",
                        "Everything stays on this device. ScrollTrack stores:\n\n" +
                        "• Detected upward swipe gestures (counts + timestamps)\n" +
                        "• Instagram session start/end times and durations\n" +
                        "• Your limits and preferences\n\n" +
                        "It never collects passwords, messages, contacts, or screen content. " +
                        "The accessibility service only observes scroll gestures inside Instagram — " +
                        "it cannot read text or see what you're watching. There are no accounts, " +
                        "no ads, and no analytics by default.\n\n" +
                        "One optional exception: if you turn on \"Share anonymous usage stats\" " +
                        "in Settings, the app sends one anonymous row a day — a random ID, the " +
                        "date, and that day's swipe and minute totals — to the developer's stats " +
                        "server. Nothing else, and turning it off stops uploads immediately. " +
                        "Delete everything anytime with \"Clear all history\".")));
        card.addView(UiKit.dividerInset(ctx));
        card.addView(UiKit.settingRow(ctx, "How counting works",
                "Gestures, sessions, and honest limitations.", UiKit.chevron(ctx),
                v -> UiKit.infoDialog(ctx, "How counting works",
                        "Swipe detection: while Instagram is in the foreground, the accessibility " +
                        "service observes upward scroll gestures. Rapid scroll events are grouped " +
                        "into single swipes using travel-distance and timing thresholds.\n\n" +
                        "Sessions: Android's UsageStats reports when Instagram enters/leaves the " +
                        "foreground. Each continuous foreground period becomes one session.\n\n" +
                        "Limitation: Android does not expose when a Reel changes, so confirmed " +
                        "per-Reel transitions can't be measured — ScrollTrack reports detected " +
                        "gestures and labels them honestly. One swipe usually advances one Reel.")));
        card.addView(UiKit.dividerInset(ctx));
        String version;
        try {
            version = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionName;
        } catch (Exception e) {
            version = "1.0.0";
        }
        LinearLayout row = UiKit.settingRow(ctx, "App version", null,
                UiKit.tv(ctx, "ScrollTrack " + version, 13, Theme.text2(ctx), Theme.body(ctx)), null);
        card.addView(row);
        return card;
    }

    public void refresh() {
        renderTrackingCard();
        renderThemeCard();
        renderUpdateCard();
        renderStatsCard();
    }
}
