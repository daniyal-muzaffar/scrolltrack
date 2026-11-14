package com.dudeyy.scrolltrack.ui.screens;

import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Build;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.dudeyy.scrolltrack.data.db.DayStat;
import com.dudeyy.scrolltrack.data.db.DbHelper;
import com.dudeyy.scrolltrack.data.prefs.Prefs;
import com.dudeyy.scrolltrack.data.repo.StatsRepository;
import com.dudeyy.scrolltrack.tracking.PermissionChecks;
import com.dudeyy.scrolltrack.ui.ScreenHost;
import com.dudeyy.scrolltrack.ui.Theme;
import com.dudeyy.scrolltrack.ui.components.ProgressRingView;
import com.dudeyy.scrolltrack.ui.components.ToggleView;
import com.dudeyy.scrolltrack.ui.components.UiKit;
import com.dudeyy.scrolltrack.util.TimeUtils;


/**
 * Daily limits and digital wellbeing. Everything here is optional and off by
 * default — the app counts fully even with no limit set, and never blocks Instagram.
 */
public class LimitsScreen extends ScrollView {

    private final ScreenHost host;
    private final StatsRepository repo;
    private final Prefs prefs;

    private LinearLayout targetCard;
    private LinearLayout thresholdCard;
    private LinearLayout goalCard;
    private TextView notifNote;
    private TextView quietRangeBtn;
    private final Prefs.Listener prefsListener = this::refresh;

    public LimitsScreen(Context ctx, ScreenHost host) {
        super(ctx);
        this.host = host;
        this.repo = StatsRepository.get(ctx);
        this.prefs = Prefs.get(ctx);
        setFillViewport(true);
        build();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        prefs.addListener(prefsListener);
        refresh();
        maybeRequestNotificationPermission();
    }

    @Override protected void onDetachedFromWindow() {
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

        TextView title = UiKit.screenTitle(ctx, "Limits & wellbeing");
        root.addView(title);
        root.addView(UiKit.tv(ctx, "Optional guardrails. ScrollTrack never blocks Instagram — warnings are just information.",
                13, Theme.text2(ctx), Theme.body(ctx)));
        root.addView(UiKit.vspace(ctx, 14));

        targetCard = UiKit.card(ctx);
        root.addView(targetCard);
        root.addView(UiKit.vspace(ctx, 12));

        goalCard = UiKit.card(ctx);
        root.addView(goalCard);
        root.addView(UiKit.vspace(ctx, 12));

        thresholdCard = UiKit.card(ctx);
        root.addView(thresholdCard);
        root.addView(UiKit.vspace(ctx, 12));

        root.addView(buildReminderCard(ctx));
        root.addView(UiKit.vspace(ctx, 12));
        root.addView(buildQuietCard(ctx));
        root.addView(UiKit.vspace(ctx, 12));
        root.addView(buildNotifCard(ctx));
    }

    // ---------- target ----------

    private void renderTargetCard() {
        Context ctx = getContext();
        targetCard.removeAllViews();
        Integer target = prefs.getDailyTarget();

        TextView h = UiKit.cardTitle(ctx, "Daily swipe target");
        targetCard.addView(h);
        targetCard.addView(UiKit.tv(ctx, "A gentle ceiling for detected upward swipes per day.",
                13, Theme.text2(ctx), Theme.body(ctx)));
        targetCard.addView(UiKit.vspace(ctx, 10));

        ToggleView noLimit = new ToggleView(ctx);
        noLimit.setCheckedSilent(target == null);
        LinearLayout row = UiKit.settingRow(ctx, "No limit",
                "Track without any target. Recommended if you just want awareness.",
                noLimit, null);
        noLimit.setOnCheckedChangeListener(c -> {
            if (c) prefs.setDailyTarget(null);
            else UiKit.numberDialog(ctx, "Daily swipe target", "e.g. 300",
                    target != null ? target : 300, v -> prefs.setDailyTarget(clamp(v, 10, 100000)));
        });
        targetCard.addView(row);

        if (target != null) {
            targetCard.addView(UiKit.dividerInset(ctx));
            LinearLayout er = UiKit.row(ctx);
            er.setPadding(0, Theme.dp(ctx, 12), 0, Theme.dp(ctx, 4));
            TextView val = UiKit.tv(ctx, String.valueOf(target), 28, Theme.lime(ctx), Theme.display(ctx));
            val.setTypeface(Theme.display(ctx), Typeface.BOLD);
            val.setLetterSpacing(Theme.TRACKING_HEADLINE);
            val.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            er.addView(val);
            er.addView(UiKit.quietButton(ctx, "Change", v ->
                    UiKit.numberDialog(ctx, "Daily swipe target", "e.g. 300", target,
                            nv -> prefs.setDailyTarget(clamp(nv, 10, 100000)))));
            targetCard.addView(er);
        }
    }

    private int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    // ---------- goal completion ----------

    private void renderGoalCard() {
        Context ctx = getContext();
        goalCard.removeAllViews();
        Integer target = prefs.getDailyTarget();
        TextView h = UiKit.cardTitle(ctx, "Today's progress");
        goalCard.addView(h);
        goalCard.addView(UiKit.vspace(ctx, 8));

        if (target == null) {
            goalCard.addView(UiKit.tv(ctx, "No daily limit set — tracking works fully without one. " +
                    "Enable a target above if you want gentle warnings.", 13, Theme.text2(ctx), Theme.body(ctx)));
            return;
        }

        TextView loading = UiKit.tv(ctx, "Loading…", 13, Theme.text2(ctx), Theme.body(ctx));
        goalCard.addView(loading);
        repo.async(() -> {
            DayStat d = DbHelper.get(ctx).getDayStat(TimeUtils.todayKey());
            return d != null ? d.swipeCount : 0;
        }, swipesObj -> {
            int swipes = swipesObj != null ? swipesObj : 0;
            renderGoalProgress(ctx, target, swipes);
        });
        return;
    }

    private void renderGoalProgress(Context ctx, int target, int swipes) {
        // Replace the loading indicator with the real progress.
        goalCard.removeAllViews();
        TextView h = UiKit.cardTitle(ctx, "Today's progress");
        goalCard.addView(h);
        goalCard.addView(UiKit.vspace(ctx, 8));
        float p = Math.min(1f, swipes / (float) target);

        LinearLayout row = UiKit.row(ctx);
        ProgressRingView ring = new ProgressRingView(ctx);
        int rs = Theme.dp(ctx, 72);
        ring.setLayoutParams(new LinearLayout.LayoutParams(rs, rs));
        ring.setProgress(p, true);
        row.addView(ring);
        row.addView(UiKit.spacer(ctx, 14));
        LinearLayout texts = new LinearLayout(ctx);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView big = UiKit.tv(ctx, swipes + " / " + target, 22, Theme.text(ctx), Theme.display(ctx));
        big.setTypeface(Theme.display(ctx), Typeface.BOLD);
        texts.addView(big);
        String msg;
        int mc;
        if (p >= 1f) { msg = "Target reached — nice awareness."; mc = Theme.amber(ctx); }
        else if (p >= 0.8f) { msg = "Close to your target."; mc = Theme.amber(ctx); }
        else { msg = "On track."; mc = Theme.lime(ctx); }
        TextView m = UiKit.tv(ctx, msg + " " + Math.round(p * 100) + "% used.", 13, mc, Theme.body(ctx));
        texts.addView(m);
        row.addView(texts);
        goalCard.addView(row);
    }

    // ---------- thresholds ----------

    private void renderThresholdCard() {
        Context ctx = getContext();
        thresholdCard.removeAllViews();
        Integer target = prefs.getDailyTarget();
        boolean hasTarget = target != null;

        TextView h = UiKit.cardTitle(ctx, "Warning thresholds");
        thresholdCard.addView(h);
        thresholdCard.addView(UiKit.tv(ctx, "Get notified as you approach your target. Each fires at most once per day.",
                13, Theme.text2(ctx), Theme.body(ctx)));
        thresholdCard.addView(UiKit.vspace(ctx, 6));

        addThresholdRow(ctx, "50% of target", prefs.isWarn50(), hasTarget, prefs::setWarn50);
        thresholdCard.addView(UiKit.dividerInset(ctx));
        addThresholdRow(ctx, "80% of target", prefs.isWarn80(), hasTarget, prefs::setWarn80);
        thresholdCard.addView(UiKit.dividerInset(ctx));
        addThresholdRow(ctx, "100% of target", prefs.isWarn100(), hasTarget, prefs::setWarn100);

        if (!hasTarget) {
            thresholdCard.addView(UiKit.vspace(ctx, 8));
            thresholdCard.addView(UiKit.tv(ctx, "Set a daily target above to enable warnings.",
                    12, Theme.text3(ctx), Theme.body(ctx)));
        }
    }

    private interface BoolSetter { void set(boolean v); }

    private void addThresholdRow(Context ctx, String label, boolean value, boolean enabled, BoolSetter setter) {
        ToggleView t = new ToggleView(ctx);
        t.setCheckedSilent(value);
        t.setEnabled(enabled);
        t.setAlpha(enabled ? 1f : 0.4f);
        LinearLayout row = UiKit.settingRow(ctx, label, null, t, null);
        row.setAlpha(enabled ? 1f : 0.5f);
        t.setOnCheckedChangeListener(c -> { if (enabled) setter.set(c); else t.setCheckedSilent(value); });
        thresholdCard.addView(row);
    }

    // ---------- session reminder ----------

    private View buildReminderCard(Context ctx) {
        LinearLayout card = UiKit.card(ctx);
        TextView h = UiKit.cardTitle(ctx, "Session reminder");
        card.addView(h);
        card.addView(UiKit.tv(ctx, "Nudge me when a single Instagram session runs long.",
                13, Theme.text2(ctx), Theme.body(ctx)));
        card.addView(UiKit.vspace(ctx, 10));
        Integer cur = prefs.getSessionReminderMin();
        int[] opts = {-1, 15, 30, 60};
        String[] labels = {"Off", "15 min", "30 min", "60 min"};
        int sel = 0;
        for (int i = 0; i < opts.length; i++) {
            if ((cur == null && opts[i] == -1) || (cur != null && cur == opts[i])) sel = i;
        }
        card.addView(UiKit.segmentedControl(ctx, labels, sel, idx -> {
            int opt = opts[idx];
            prefs.setSessionReminderMin(opt == -1 ? null : opt);
            refresh();
        }));
        return card;
    }

    // ---------- quiet hours ----------

    private View buildQuietCard(Context ctx) {
        LinearLayout card = UiKit.card(ctx);
        ToggleView t = new ToggleView(ctx);
        t.setCheckedSilent(prefs.isQuietHoursEnabled());
        LinearLayout row = UiKit.settingRow(ctx, "Quiet hours",
                "Pause wellbeing notifications during these hours.",
                t, null);
        t.setOnCheckedChangeListener(c -> {
            prefs.setQuietHoursEnabled(c);
            refresh();
        });
        card.addView(row);
        if (prefs.isQuietHoursEnabled()) {
            card.addView(UiKit.dividerInset(ctx));
            LinearLayout r2 = UiKit.row(ctx);
            r2.setPadding(0, Theme.dp(ctx, 12), 0, Theme.dp(ctx, 4));
            quietRangeBtn = UiKit.tv(ctx, "", 15, Theme.lime(ctx), Theme.body(ctx));
            quietRangeBtn.setTypeface(Theme.body(ctx), Typeface.BOLD);
            quietRangeBtn.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            r2.addView(quietRangeBtn);
            r2.addView(UiKit.quietButton(ctx, "Change", v -> pickQuietHours()));
            card.addView(r2);
            updateQuietLabel();
        }
        return card;
    }

    private void updateQuietLabel() {
        if (quietRangeBtn == null) return;
        quietRangeBtn.setText(TimeUtils.formatMinutesLabel(prefs.getQuietStartMin()) + " – " +
                TimeUtils.formatMinutesLabel(prefs.getQuietEndMin()));
    }

    private void pickQuietHours() {
        Context ctx = getContext();
        int start = prefs.getQuietStartMin();
        TimePickerDialog d = new TimePickerDialog(ctx, (v, h, m) -> {
            int s = h * 60 + m;
            int end = prefs.getQuietEndMin();
            TimePickerDialog d2 = new TimePickerDialog(ctx, (v2, h2, m2) -> {
                prefs.setQuietRange(s, h2 * 60 + m2);
                refresh();
            }, end / 60, end % 60, false);
            d2.setTitle("Quiet hours end");
            d2.show();
        }, start / 60, start % 60, false);
        d.setTitle("Quiet hours start");
        d.show();
    }

    // ---------- notifications ----------

    private View buildNotifCard(Context ctx) {
        LinearLayout card = UiKit.card(ctx);
        ToggleView t = new ToggleView(ctx);
        t.setCheckedSilent(prefs.isNotificationsEnabled());
        card.addView(UiKit.settingRow(ctx, "Wellbeing notifications",
                "Allow threshold and reminder notifications.", t, null));
        t.setOnCheckedChangeListener(c -> {
            prefs.setNotificationsEnabled(c);
            if (c) maybeRequestNotificationPermission();
            refresh();
        });
        card.addView(UiKit.dividerInset(ctx));
        notifNote = UiKit.tv(ctx, "", 13, Theme.text2(ctx), Theme.body(ctx));
        LinearLayout r = UiKit.row(ctx);
        r.setPadding(0, Theme.dp(ctx, 12), 0, Theme.dp(ctx, 4));
        notifNote.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        r.addView(notifNote);
        r.addView(UiKit.quietButton(ctx, "System settings", v -> {
            Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            i.putExtra(Settings.EXTRA_APP_PACKAGE, ctx.getPackageName());
            try { ctx.startActivity(i); } catch (Exception ignored) { }
        }));
        card.addView(r);
        return card;
    }

    private void maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        if (!prefs.isNotificationsEnabled()) return;
        if (PermissionChecks.areNotificationsGranted(getContext())) return;
        try {
            host.requestNotificationPermission();
        } catch (Exception ignored) { }
    }

    // ---------- refresh ----------

    public void refresh() {
        renderTargetCard();
        renderGoalCard();
        renderThresholdCard();
        if (notifNote != null) {
            boolean granted = PermissionChecks.areNotificationsGranted(getContext());
            notifNote.setText(granted
                    ? "System notification permission is granted."
                    : "System notification permission is not granted — enable it to receive wellbeing alerts.");
            notifNote.setTextColor(granted ? Theme.text2(getContext()) : Theme.amber(getContext()));
        }
    }
}
