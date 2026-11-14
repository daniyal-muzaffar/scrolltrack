package com.dudeyy.scrolltrack.ui.screens;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.dudeyy.scrolltrack.data.db.Session;
import com.dudeyy.scrolltrack.data.prefs.Prefs;
import com.dudeyy.scrolltrack.data.repo.StatsRepository;
import com.dudeyy.scrolltrack.ui.ScreenHost;
import com.dudeyy.scrolltrack.ui.Theme;
import com.dudeyy.scrolltrack.ui.components.UiKit;
import com.dudeyy.scrolltrack.util.TimeUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Session history with Today / 7-day / 30-day filters and per-session details. */
public class HistoryScreen extends ScrollView {

    private final ScreenHost host;
    private final StatsRepository repo;
    private final Prefs prefs;

    private int filterDays = 1; // 1, 7, 30
    private LinearLayout listSlot;
    private final Prefs.Listener prefsListener = this::refresh;

    public HistoryScreen(Context ctx, ScreenHost host) {
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

        TextView title = UiKit.screenTitle(ctx, "Session history");
        root.addView(title);
        root.addView(UiKit.tv(ctx, "Every Instagram session ScrollTrack observed.",
                13, Theme.text2(ctx), Theme.body(ctx)));
        root.addView(UiKit.vspace(ctx, 14));

        root.addView(UiKit.segmentedControl(ctx, new String[]{"Today", "7 days", "30 days"},
                0, idx -> setFilter(idx == 0 ? 1 : idx == 1 ? 7 : 30)));
        root.addView(UiKit.vspace(ctx, 12));

        listSlot = new LinearLayout(ctx);
        listSlot.setOrientation(LinearLayout.VERTICAL);
        root.addView(listSlot);
    }

    private void setFilter(int days) {
        if (filterDays == days) return;
        filterDays = days;
        refresh();
    }

    public void refresh() {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(filterDays - 1);
        long from = TimeUtils.startOfDayTs(start);
        long to = TimeUtils.endOfDayTs(end);
        repo.async(() -> repo.loadSessions(from, to), sessions -> {
            render(sessions != null ? sessions : new ArrayList<>(), start, end);
        });
    }

    private void render(List<Session> sessions, LocalDate start, LocalDate end) {
        Context ctx = getContext();
        listSlot.removeAllViews();
        if (sessions.isEmpty()) {
            LinearLayout empty = UiKit.card(ctx);
            TextView t = UiKit.cardTitle(ctx, "No sessions recorded");
            t.setGravity(Gravity.CENTER);
            empty.addView(t);
            TextView s = UiKit.tv(ctx,
                    filterDays == 1 ? "No Instagram sessions today yet. Sessions appear here after you open Instagram."
                            : "No Instagram sessions in this period.",
                    13, Theme.text2(ctx), Theme.body(ctx));
            s.setGravity(Gravity.CENTER);
            empty.addView(UiKit.vspace(ctx, 6));
            empty.addView(s);
            listSlot.addView(empty);
            return;
        }

        // Group by local day, newest first.
        Map<String, List<Session>> byDay = new LinkedHashMap<>();
        for (Session s : sessions) {
            String day = TimeUtils.dayKey(s.startTime);
            if (!byDay.containsKey(day)) byDay.put(day, new ArrayList<>());
            byDay.get(day).add(s);
        }

        boolean first = true;
        for (Map.Entry<String, List<Session>> e : byDay.entrySet()) {
            if (!first) listSlot.addView(UiKit.vspace(ctx, 14));
            first = false;
            List<Session> daySessions = e.getValue();
            LocalDate d = daySessions.get(0).startTime == 0 ? LocalDate.now()
                    : java.time.Instant.ofEpochMilli(daySessions.get(0).startTime)
                            .atZone(java.time.ZoneId.systemDefault()).toLocalDate();
            int daySwipes = 0;
            for (Session s : daySessions) daySwipes += s.swipeCount;

            LinearLayout head = UiKit.row(ctx);
            TextView dt = UiKit.tv(ctx, TimeUtils.fullDate(d), 15, Theme.text(ctx), Theme.body(ctx));
            dt.setTypeface(Theme.body(ctx), Typeface.BOLD);
            dt.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            head.addView(dt);
            head.addView(UiKit.tv(ctx, daySwipes + " swipes", 13, Theme.text2(ctx), Theme.body(ctx)));
            listSlot.addView(head);
            listSlot.addView(UiKit.vspace(ctx, 8));

            LinearLayout card = UiKit.card(ctx);
            int p = Theme.dp(ctx, 8);
            card.setPadding(p, p, p, p);
            boolean firstRow = true;
            for (Session s : daySessions) {
                if (!firstRow) card.addView(UiKit.dividerInset(ctx));
                firstRow = false;
                card.addView(sessionRow(ctx, s));
            }
            listSlot.addView(card);
        }
    }

    private View sessionRow(Context ctx, Session s) {
        LinearLayout r = UiKit.row(ctx);
        int p = Theme.dp(ctx, 12);
        r.setPadding(p, p, p, p);
        r.setOnClickListener(v -> showDetail(ctx, s));

        LinearLayout texts = new LinearLayout(ctx);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        String range = TimeUtils.clock(s.startTime) + " – " +
                (s.isOpen() ? "now" : TimeUtils.clock(s.endTime));
        TextView t = UiKit.tv(ctx, range, 15, Theme.text(ctx), Theme.body(ctx));
        t.setTypeface(Theme.body(ctx), Typeface.BOLD);
        texts.addView(t);

        long mins = Math.max(1, s.durationMs() / 60_000);
        double perMin = s.swipeCount / (double) Math.max(1, s.durationMs() / 60000.0);
        String sub = TimeUtils.formatDuration(s.durationMs()) + " · " + s.swipeCount + " swipes · " +
                String.format(Locale.getDefault(), "%.1f/min", perMin);
        if (s.isOpen()) sub = "In progress · " + s.swipeCount + " swipes";
        texts.addView(UiKit.tv(ctx, sub, 13, Theme.text2(ctx), Theme.body(ctx)));
        r.addView(texts);

        // quality dot
        boolean full = "full".equals(s.quality);
        LinearLayout pill = UiKit.statusPill(ctx,
                full ? Theme.lime(ctx) : Theme.text3(ctx),
                full ? "Gestures" : "Time only");
        r.addView(pill);
        return r;
    }

    private void showDetail(Context ctx, Session s) {
        LinearLayout body = new LinearLayout(ctx);
        body.setOrientation(LinearLayout.VERTICAL);
        int p = Theme.dp(ctx, 4);
        body.setPadding(p, p, p, p);

        addDetailRow(ctx, body, "Started", TimeUtils.clock(s.startTime) + " · " + TimeUtils.shortDate(s.startTime));
        addDetailRow(ctx, body, "Ended", s.isOpen() ? "In progress" :
                TimeUtils.clock(s.endTime) + " · " + TimeUtils.shortDate(s.endTime));
        addDetailRow(ctx, body, "Duration", TimeUtils.formatDurationLong(s.durationMs()));
        if (s.scrollingDurationMs > 0) {
            addDetailRow(ctx, body, "Reels time",
                    TimeUtils.formatDurationLong(s.scrollingDurationMs));
        }
        addDetailRow(ctx, body, "Detected swipes", String.valueOf(s.swipeCount));
        double perMin = s.swipeCount / (double) Math.max(1, s.durationMs() / 60000.0);
        addDetailRow(ctx, body, "Avg pace",
                String.format(Locale.getDefault(), "%.1f swipes/min", perMin));
        addDetailRow(ctx, body, "Tracking quality",
                "full".equals(s.quality)
                        ? "Full — gesture + time tracking"
                        : "Time only — accessibility service was off, so no per-swipe counts");
        if (!s.endedCleanly && !s.isOpen()) {
            addDetailRow(ctx, body, "Note", "This session was closed during app restart or reboot; its end time is approximate.");
        }
        if (s.isOpen()) {
            addDetailRow(ctx, body, "Note", "This session is still open and keeps updating.");
        }

        UiKit.dialogBuilder(ctx)
                .setTitle("Session details")
                .setView(body)
                .setPositiveButton("Close", null)
                .create().show();
    }

    private void addDetailRow(Context ctx, LinearLayout body, String label, String value) {
        body.addView(UiKit.caption(ctx, label));
        TextView v = UiKit.tv(ctx, value, 15, Theme.text(ctx), Theme.body(ctx));
        body.addView(v);
        body.addView(UiKit.vspace(ctx, 10));
    }
}
