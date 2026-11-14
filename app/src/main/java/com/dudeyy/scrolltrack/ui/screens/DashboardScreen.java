package com.dudeyy.scrolltrack.ui.screens;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.dudeyy.scrolltrack.R;
import com.dudeyy.scrolltrack.data.prefs.Prefs;
import com.dudeyy.scrolltrack.data.repo.StatsRepository;
import com.dudeyy.scrolltrack.tracking.PermissionChecks;
import com.dudeyy.scrolltrack.tracking.TrackingService;
import com.dudeyy.scrolltrack.tracking.TrackingState;
import com.dudeyy.scrolltrack.ui.ScreenHost;
import com.dudeyy.scrolltrack.ui.Theme;
import com.dudeyy.scrolltrack.ui.components.ProgressRingView;
import com.dudeyy.scrolltrack.ui.components.UiKit;
import com.dudeyy.scrolltrack.ui.components.WeekBarChartView;
import com.dudeyy.scrolltrack.util.SwipeTitles;
import com.dudeyy.scrolltrack.util.TimeUtils;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Main dashboard: hero count, secondary stats, chart, quick actions. */
public class DashboardScreen extends ScrollView {

    private final ScreenHost host;
    private final StatsRepository repo;
    private final Prefs prefs;

    private LinearLayout statusSlot;
    private TextView dailyTitle;
    private String currentTitleText;
    private TextView heroNumber;
    private TextView deltaView;
    private FrameLayout ringSlot;
    private TextView targetCaption;
    private LinearLayout swipeWarnSlot;
    private final TextView[] statValues = new TextView[2];
    private final TextView[] statCaptions = new TextView[2];
    private TextView recordValue;
    private TextView recordSubtitle;
    private WeekBarChartView chart;
    private TextView chartAvg;
    private TextView chartTitle;
    private TextView pauseBtn;
    private boolean weekMode = false;

    private final TrackingService.TrackingListener trackingListener = new TrackingService.TrackingListener() {
        @Override public void onStateChanged(TrackingState state) { refreshStatus(); refreshPauseBtn(); }
        @Override public void onTodaySwipesChanged(int swipes) {
            if (heroNumber != null) heroNumber.setText(String.valueOf(swipes));
            updateDailyTitle(swipes);
        }
        @Override public void onSwipeCountingChanged(boolean available) { refreshSwipeWarning(); }
    };
    private final Prefs.Listener prefsListener = this::refresh;

    public DashboardScreen(Context ctx, ScreenHost host) {
        super(ctx);
        this.host = host;
        this.repo = StatsRepository.get(ctx);
        this.prefs = Prefs.get(ctx);
        setFillViewport(true);
        build();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        TrackingService.addListener(trackingListener);
        prefs.addListener(prefsListener);
        refresh();
    }

    @Override
    protected void onDetachedFromWindow() {
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

        root.addView(buildHeader(ctx));
        root.addView(UiKit.vspace(ctx, 14));
        swipeWarnSlot = new LinearLayout(ctx);
        swipeWarnSlot.setOrientation(LinearLayout.VERTICAL);
        root.addView(swipeWarnSlot);
        root.addView(buildDailyTitle(ctx));
        root.addView(UiKit.vspace(ctx, 12));
        root.addView(buildHero(ctx));
        root.addView(UiKit.vspace(ctx, 12));
        root.addView(buildStatGrid(ctx));
        root.addView(UiKit.vspace(ctx, 12));
        root.addView(buildHonestyNote(ctx));
        root.addView(UiKit.vspace(ctx, 12));
        root.addView(buildChartCard(ctx));
        root.addView(UiKit.vspace(ctx, 12));
        root.addView(buildQuickActions(ctx));
    }

    // ---------- header ----------

    private View buildHeader(Context ctx) {
        LinearLayout h = UiKit.row(ctx);

        ImageView logo = new ImageView(ctx);
        logo.setImageResource(R.drawable.ic_launcher);
        int ls = Theme.dp(ctx, 42);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(ls, ls);
        logo.setLayoutParams(llp);
        GradientDrawable ld = new GradientDrawable();
        ld.setCornerRadius(Theme.dp(ctx, 12));
        ld.setColor(Theme.surface2(ctx));
        logo.setBackground(ld);
        logo.setClipToOutline(true);
        logo.setPadding(Theme.dp(ctx, 5), Theme.dp(ctx, 5), Theme.dp(ctx, 5), Theme.dp(ctx, 5));
        h.addView(logo);
        h.addView(UiKit.spacer(ctx, 12));

        LinearLayout titles = new LinearLayout(ctx);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView name = UiKit.tv(ctx, "ScrollTrack", Theme.SIZE_HEADLINE, Theme.text(ctx), Theme.display(ctx));
        name.setTypeface(Theme.display(ctx), Typeface.BOLD);
        name.setLetterSpacing(Theme.TRACKING_HEADLINE);
        titles.addView(name);
        titles.addView(UiKit.tv(ctx, TimeUtils.fullDate(LocalDate.now()), 13, Theme.text2(ctx), Theme.body(ctx)));
        h.addView(titles);

        View spring = new View(ctx);
        spring.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        h.addView(spring);

        statusSlot = new LinearLayout(ctx);
        statusSlot.setOrientation(LinearLayout.VERTICAL);
        h.addView(statusSlot);

        h.addView(UiKit.spacer(ctx, 10));
        ImageView gear = new ImageView(ctx);
        gear.setImageResource(R.drawable.ic_gear);
        gear.setColorFilter(Theme.text2(ctx));
        int gs = Theme.dp(ctx, 48);
        gear.setLayoutParams(new LinearLayout.LayoutParams(gs, gs));
        gear.setPadding(Theme.dp(ctx, 12), Theme.dp(ctx, 12), Theme.dp(ctx, 12), Theme.dp(ctx, 12));
        gear.setBackground(UiKit.roundedBg(ctx, Theme.surface2(ctx), Theme.RADIUS_PILL));
        gear.setOnClickListener(v -> host.goToTab(ScreenHost.TAB_SETTINGS));
        h.addView(gear);
        return h;
    }

    private void refreshStatus() {
        Context ctx = getContext();
        TrackingState s = TrackingService.getState();
        statusSlot.removeAllViews();
        statusSlot.addView(UiKit.statusPill(ctx, UiKit.statusColor(ctx, s), UiKit.statusLabel(s)));
    }

    // ---------- daily swipe title ----------

    private View buildDailyTitle(Context ctx) {
        dailyTitle = UiKit.tv(ctx, "", Theme.SIZE_TITLE_2, Theme.text(ctx), Theme.display(ctx));
        dailyTitle.setTypeface(Theme.display(ctx), Typeface.BOLD);
        dailyTitle.setLetterSpacing(Theme.TRACKING_HEADLINE);
        dailyTitle.setGravity(Gravity.CENTER);
        dailyTitle.setSingleLine(true);
        // Long titles shrink to fit one line instead of clipping or wrapping.
        dailyTitle.setAutoSizeTextTypeUniformWithConfiguration(
                15, 22, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        dailyTitle.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        dailyTitle.setPadding(Theme.dp(ctx, 4), Theme.dp(ctx, 2), Theme.dp(ctx, 4), 0);
        updateDailyTitle(0);
        return dailyTitle;
    }

    /** Title follows today's recorded swipe count only. Animates on change. */
    private void updateDailyTitle(int swipesToday) {
        if (dailyTitle == null) return;
        final String next = SwipeTitles.forCount(swipesToday);
        if (next.equals(currentTitleText)) return;
        final boolean firstSet = currentTitleText == null;
        currentTitleText = next;
        dailyTitle.animate().cancel();
        if (firstSet) {
            dailyTitle.setText(next);
            dailyTitle.setAlpha(1f);
            dailyTitle.setScaleX(1f);
            dailyTitle.setScaleY(1f);
            return;
        }
        dailyTitle.animate()
                .alpha(0f).scaleX(0.96f).scaleY(0.96f)
                .setDuration(130)
                .withEndAction(() -> {
                    dailyTitle.setText(next);
                    dailyTitle.animate()
                            .alpha(1f).scaleX(1f).scaleY(1f)
                            .setDuration(190)
                            .start();
                })
                .start();
    }

    // ---------- hero ----------

    private View buildHero(Context ctx) {
        LinearLayout card = UiKit.card(ctx);

        card.addView(UiKit.caption(ctx, "Today · detected upward swipes"));
        card.addView(UiKit.vspace(ctx, 6));

        LinearLayout row = UiKit.row(ctx);
        LinearLayout left = new LinearLayout(ctx);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        heroNumber = UiKit.heroNumber(ctx, "0");
        left.addView(heroNumber);
        deltaView = UiKit.tv(ctx, "", 13, Theme.text2(ctx), Theme.body(ctx));
        left.addView(deltaView);
        row.addView(left);

        ringSlot = new FrameLayout(ctx);
        int rs = Theme.dp(ctx, 104);
        ringSlot.setLayoutParams(new LinearLayout.LayoutParams(rs, rs));
        row.addView(ringSlot);
        card.addView(row);
        card.addView(UiKit.vspace(ctx, 4));
        targetCaption = UiKit.tv(ctx, "", 13, Theme.text2(ctx), Theme.body(ctx));
        card.addView(targetCaption);
        return card;
    }

    private void refreshSwipeWarning() {
        Context ctx = getContext();
        swipeWarnSlot.removeAllViews();
        boolean a11y = TrackingService.isSwipeCountingAvailable();
        boolean usage = PermissionChecks.hasUsageAccess(ctx);
        if (!a11y && usage && prefs.isTrackingEnabled()) {
            LinearLayout warn = UiKit.card(ctx);
            GradientDrawable bg = UiKit.roundedBg(ctx, Theme.limeSoft(ctx), Theme.RADIUS_CARD);
            warn.setBackground(bg);
            LinearLayout r = UiKit.row(ctx);
            TextView t = UiKit.tv(ctx, "Swipe counting is off — enable the accessibility service for per-swipe stats. Time tracking still works.", 13, Theme.text(ctx), Theme.body(ctx));
            t.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            r.addView(t);
            TextView fix = UiKit.quietButton(ctx, "Fix", v -> host.goToTab(ScreenHost.TAB_SETTINGS));
            r.addView(fix);
            warn.addView(r);
            swipeWarnSlot.addView(warn);
            swipeWarnSlot.addView(UiKit.vspace(ctx, 12));
        }
    }

    // ---------- stat grid ----------

    private View buildStatGrid(Context ctx) {
        LinearLayout grid = new LinearLayout(ctx);
        grid.setOrientation(LinearLayout.VERTICAL);

        // Top row: today's Instagram time and session count.
        String[] caps = {"Instagram time", "Sessions"};
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int c = 0; c < 2; c++) {
            LinearLayout card = UiKit.card(ctx);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            int m = Theme.dp(ctx, 6);
            if (c == 0) lp.rightMargin = m; else lp.leftMargin = m;
            card.setLayoutParams(lp);
            int p = Theme.dp(ctx, 16);
            card.setPadding(p, p, p, p);
            TextView v = UiKit.tv(ctx, "–", Theme.SIZE_TITLE_2, Theme.text(ctx), Theme.display(ctx));
            v.setTypeface(Theme.display(ctx), Typeface.BOLD);
            v.setLetterSpacing(Theme.TRACKING_HEADLINE);
            statValues[c] = v;
            card.addView(v);
            TextView cap = UiKit.tv(ctx, caps[c], 12, Theme.text2(ctx), Theme.body(ctx));
            statCaptions[c] = cap;
            card.addView(cap);
            row.addView(card);
        }
        grid.addView(row);
        grid.addView(UiKit.vspace(ctx, 12));

        // Full-width all-time record card.
        LinearLayout record = UiKit.card(ctx);
        int rp = Theme.dp(ctx, 16);
        record.setPadding(rp, rp, rp, rp);
        recordValue = UiKit.tv(ctx, "–", 28, Theme.lime(ctx), Theme.display(ctx));
        recordValue.setTypeface(Theme.display(ctx), Typeface.BOLD);
        record.addView(recordValue);
        record.addView(UiKit.tv(ctx, "Highest Daily Count", 12, Theme.text2(ctx), Theme.body(ctx)));
        recordSubtitle = UiKit.tv(ctx, "All-time record", 12, Theme.text3(ctx), Theme.body(ctx));
        record.addView(recordSubtitle);
        grid.addView(record);
        return grid;
    }

    // ---------- honesty note ----------

    private View buildHonestyNote(Context ctx) {
        TextView t = UiKit.tv(ctx,
                "ⓘ  Counts upward swipe gestures while Instagram is in front. Android doesn't expose per-Reel changes, so gestures — not confirmed Reels — are the measured signal. Tap to learn more.",
                12, Theme.text3(ctx), Theme.body(ctx));
        t.setOnClickListener(v -> UiKit.infoDialog(ctx, "How counting works",
                "When Instagram is in the foreground, ScrollTrack's accessibility service observes upward scroll gestures and groups rapid scroll events into single swipes.\n\n" +
                "Android does not tell apps when a Reel changes, so ScrollTrack honestly reports detected gestures. One swipe usually advances one Reel.\n\n" +
                "Session times come from Android's UsageStats: total Instagram foreground time is shown separately and is never presented as Reels-only watch time."));
        t.setPadding(Theme.dp(ctx, 4), 0, Theme.dp(ctx, 4), 0);
        return t;
    }

    // ---------- chart ----------

    private View buildChartCard(Context ctx) {
        LinearLayout card = UiKit.card(ctx);
        LinearLayout head = UiKit.row(ctx);
        chartTitle = UiKit.cardTitle(ctx, "This week");
        chartTitle.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(chartTitle);
        head.addView(UiKit.segmentedControl(ctx, new String[]{"Day", "Week"},
                weekMode ? 1 : 0, idx -> setWeekMode(idx == 1)));
        card.addView(head);
        card.addView(UiKit.vspace(ctx, 8));
        chart = new WeekBarChartView(ctx);
        card.addView(chart);
        chartAvg = UiKit.tv(ctx, "", 13, Theme.text2(ctx), Theme.body(ctx));
        chartAvg.setGravity(Gravity.END);
        card.addView(chartAvg);
        return card;
    }

    private void setWeekMode(boolean week) {
        if (weekMode == week) return;
        weekMode = week;
        chartTitle.setText(week ? "Last 8 weeks" : "This week");
        loadChart();
    }

    private void loadChart() {
        repo.async(() -> weekMode ? repo.loadWeekBars() : repo.loadBars(LocalDate.now(), 7), bars -> {
            List<WeekBarChartView.Bar> vb = new ArrayList<>();
            LocalDate today = LocalDate.now();
            int sum = 0;
            for (StatsRepository.WeekBar b : bars) {
                sum += b.swipes;
                boolean hl = !weekMode && b.day.equals(today);
                vb.add(new WeekBarChartView.Bar(b.label, b.swipes, hl));
            }
            float avg = bars.isEmpty() ? 0 : sum / (float) bars.size();
            chart.setData(vb, avg, true);
            chartAvg.setText(weekMode
                    ? String.format(Locale.getDefault(), "Weekly average: %.0f swipes/week", avg)
                    : String.format(Locale.getDefault(), "Daily average: %.0f swipes/day", avg));
        });
    }

    // ---------- quick actions ----------

    private View buildQuickActions(Context ctx) {
        LinearLayout card = UiKit.card(ctx);
        TextView title = UiKit.cardTitle(ctx, "Quick actions");
        card.addView(title);
        card.addView(UiKit.vspace(ctx, 10));

        pauseBtn = UiKit.quietButton(ctx, "Pause tracking", v -> toggleTracking());
        TextView setupBtn = UiKit.quietButton(ctx, "Tracking setup", v -> host.goToTab(ScreenHost.TAB_SETTINGS));
        TextView histBtn = UiKit.quietButton(ctx, "Session history", v -> host.goToTab(ScreenHost.TAB_HISTORY));
        TextView limitBtn = UiKit.quietButton(ctx, "Daily limit", v -> host.goToTab(ScreenHost.TAB_LIMITS));

        for (int r = 0; r < 2; r++) {
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            TextView a = r == 0 ? pauseBtn : histBtn;
            TextView b = r == 0 ? setupBtn : limitBtn;
            for (TextView btn : new TextView[]{a, b}) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                int m = Theme.dp(ctx, 6);
                if (btn == a) lp.rightMargin = m; else lp.leftMargin = m;
                btn.setLayoutParams(lp);
                row.addView(btn);
            }
            card.addView(row);
            if (r == 0) card.addView(UiKit.vspace(ctx, 10));
        }
        return card;
    }

    private void toggleTracking() {
        Context ctx = getContext();
        if (TrackingService.getState() == TrackingState.PAUSED || !prefs.isTrackingEnabled()) {
            prefs.setTrackingEnabled(true);
            TrackingService.start(ctx);
        } else {
            Intent i = new Intent(ctx, TrackingService.class);
            i.setAction(TrackingService.ACTION_PAUSE);
            try { ctx.startService(i); } catch (IllegalStateException ignored) { }
        }
    }

    private void refreshPauseBtn() {
        if (pauseBtn == null) return;
        boolean paused = TrackingService.getState() == TrackingState.PAUSED || !prefs.isTrackingEnabled();
        pauseBtn.setText(paused ? "Resume tracking" : "Pause tracking");
    }

    // ---------- refresh ----------

    public void refresh() {
        refreshStatus();
        refreshSwipeWarning();
        refreshPauseBtn();
        loadChart();
        repo.async(repo::loadToday, s -> {
            if (s == null) return;
            heroNumber.setText(String.valueOf(s.swipes));
            updateDailyTitle(s.swipes);
            // delta vs yesterday
            String delta;
            int dc = Theme.text2(getContext());
            if (s.yesterdaySwipes == 0 && s.swipes == 0) {
                delta = "No scrolls recorded yet";
            } else if (s.yesterdaySwipes == 0) {
                delta = "▲ " + s.swipes + " vs yesterday";
                dc = Theme.lime(getContext());
            } else {
                int diff = s.swipes - s.yesterdaySwipes;
                int pct = Math.round(diff * 100f / s.yesterdaySwipes);
                if (diff > 0) { delta = "▲ " + pct + "% vs yesterday"; dc = Theme.amber(getContext()); }
                else if (diff < 0) { delta = "▼ " + Math.abs(pct) + "% vs yesterday"; dc = Theme.lime(getContext()); }
                else { delta = "Same as yesterday"; }
            }
            deltaView.setText(delta);
            deltaView.setTextColor(dc);

            // target ring
            ringSlot.removeAllViews();
            if (s.target != null && s.target > 0) {
                ProgressRingView ring = new ProgressRingView(getContext());
                ring.setLayoutParams(new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                float p = Math.min(1f, s.swipes / (float) s.target);
                ring.setProgress(p, true);
                ringSlot.addView(ring);
                TextView pct = UiKit.tv(getContext(), Math.round(p * 100) + "%", 20,
                        Theme.text(getContext()), Theme.display(getContext()));
                pct.setTypeface(Theme.display(getContext()), Typeface.BOLD);
                pct.setGravity(Gravity.CENTER);
                ringSlot.addView(pct, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                targetCaption.setText("of " + s.target + " daily target");
            } else {
                TextView no = UiKit.tv(getContext(), "No daily limit set", 13,
                        Theme.text3(getContext()), Theme.body(getContext()));
                no.setGravity(Gravity.CENTER);
                ringSlot.addView(no, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                targetCaption.setText("Limits are optional — tracking works fully without one.");
            }

            // secondary stats
            statValues[0].setText(TimeUtils.formatDuration(s.totalDurationMs));
            statCaptions[0].setText("Instagram time · total app time");
            statValues[1].setText(String.valueOf(s.sessions));
        });
        refreshRecord();
    }

    /** All-time highest daily swipe count, straight from the stored day records. */
    private void refreshRecord() {
        repo.async(repo::loadRecord, r -> {
            if (recordValue == null) return;
            if (r == null || r.swipeCount <= 0) {
                recordValue.setText("–");
                recordSubtitle.setText("All-time record · no record yet");
                return;
            }
            NumberFormat nf = NumberFormat.getInstance(Locale.US);
            recordValue.setText(nf.format(r.swipeCount));
            String when;
            try {
                LocalDate d = LocalDate.parse(r.day);
                when = d.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US));
            } catch (Exception e) {
                when = null;
            }
            recordSubtitle.setText(when != null
                    ? "All-time record · set on " + when
                    : "All-time record");
        });
    }
}
