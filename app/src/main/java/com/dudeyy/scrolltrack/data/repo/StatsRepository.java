package com.dudeyy.scrolltrack.data.repo;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.dudeyy.scrolltrack.data.db.DayStat;
import com.dudeyy.scrolltrack.data.db.DbHelper;
import com.dudeyy.scrolltrack.data.db.Session;
import com.dudeyy.scrolltrack.data.prefs.Prefs;
import com.dudeyy.scrolltrack.util.TimeUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Synchronous DB access + a shared background executor with main-thread callbacks. */
public class StatsRepository {
    private static volatile StatsRepository instance;

    public static StatsRepository get(Context ctx) {
        if (instance == null) {
            synchronized (StatsRepository.class) {
                if (instance == null) instance = new StatsRepository(ctx.getApplicationContext());
            }
        }
        return instance;
    }

    public interface Callback<T> { void onResult(T result); }

    private final DbHelper db;
    private final Prefs prefs;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Context appContext;

    private StatsRepository(Context ctx) {
        appContext = ctx;
        db = DbHelper.get(ctx);
        prefs = Prefs.get(ctx);
    }

    public DbHelper db() { return db; }
    public Prefs prefs() { return prefs; }

    public <T> void async(BackgroundOp<T> op, Callback<T> cb) {
        io.execute(() -> {
            T result;
            try {
                result = op.run();
            } catch (Exception e) {
                result = null;
            }
            final T r = result;
            main.post(() -> cb.onResult(r));
        });
    }

    public interface BackgroundOp<T> { T run() throws Exception; }

    // ---------- dashboard models ----------

    public static class TodaySummary {
        public String day;
        public int swipes;
        public int sessions;
        public long totalDurationMs;
        public long longestSessionMs;
        public int yesterdaySwipes;
        public Integer target;
    }

    public TodaySummary loadToday() {
        LocalDate today = LocalDate.now();
        DayStat t = db.getDayStat(TimeUtils.dayKey(today));
        DayStat y = db.getDayStat(TimeUtils.dayKey(today.minusDays(1)));
        TodaySummary s = new TodaySummary();
        s.day = TimeUtils.dayKey(today);
        s.swipes = t != null ? t.swipeCount : 0;
        s.sessions = t != null ? t.sessionCount : 0;
        s.totalDurationMs = t != null ? t.totalDurationMs : 0;
        s.longestSessionMs = t != null ? t.longestSessionMs : 0;
        s.yesterdaySwipes = y != null ? y.swipeCount : 0;
        s.target = prefs.getDailyTarget();
        // Include the currently open session's in-progress duration in "today".
        Session open = db.getOpenSession();
        if (open != null && TimeUtils.dayKey(open.startTime).equals(s.day)) {
            s.totalDurationMs += open.durationMs();
        }
        return s;
    }

    /** The all-time highest daily swipe count and the day it was set
     *  (earliest day on ties), or null when nothing has been recorded. */
    public DayStat loadRecord() {
        return db.getHighestSwipeDay();
    }

    // ---------- life impact ----------

    /** Raw recorded totals the Life Impact screen projects from. Nothing here
     *  is estimated — projections are computed by the screen from these sums. */
    public static class ImpactData {
        /** Days on which usage was recorded (the projection denominator). */
        public int daysTracked;
        /** All recorded Instagram foreground time. */
        public long totalInstagramMs;
        /** Time in sessions where swipes were detected (upper estimate of
         *  Reels-only time — sessions can include pauses). */
        public long scrollingMs;
        /** Epoch ms of the earliest recorded session, 0 if none. */
        public long firstDayTs;
    }

    public ImpactData loadImpact() {
        ImpactData d = new ImpactData();
        d.daysTracked = db.countDaysWithDuration();
        d.totalInstagramMs = db.sumTotalDurationMs();
        d.scrollingMs = db.sumScrollingDurationMs();
        d.firstDayTs = db.earliestSessionStart();
        return d;
    }

    public static class WeekBar {
        public LocalDate day;
        public String label;
        public int swipes;
    }

    public List<WeekBar> loadBars(LocalDate endDay, int days) {
        LocalDate startDay = endDay.minusDays(days - 1);
        Map<String, DayStat> byDay = new HashMap<>();
        for (DayStat d : db.getDayStats(TimeUtils.dayKey(startDay), TimeUtils.dayKey(endDay))) {
            byDay.put(d.day, d);
        }
        List<WeekBar> out = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            LocalDate d = startDay.plusDays(i);
            DayStat stat = byDay.get(TimeUtils.dayKey(d));
            WeekBar b = new WeekBar();
            b.day = d;
            b.label = TimeUtils.shortDayLabel(d);
            b.swipes = stat != null ? stat.swipeCount : 0;
            out.add(b);
        }
        return out;
    }

    public List<Session> loadSessions(long fromTs, long toTs) {
        return db.getSessionsBetween(fromTs, toTs);
    }

    /** Last 8 full weeks (Sun–Sat ending today) as summed bars. */
    public List<WeekBar> loadWeekBars() {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(55);
        Map<String, DayStat> byDay = new HashMap<>();
        for (DayStat d : db.getDayStats(TimeUtils.dayKey(start), TimeUtils.dayKey(end))) {
            byDay.put(d.day, d);
        }
        List<WeekBar> out = new ArrayList<>();
        for (int w = 7; w >= 0; w--) {
            LocalDate weekEnd = end.minusDays(w * 7L);
            LocalDate weekStart = weekEnd.minusDays(6);
            int sum = 0;
            for (int i = 0; i < 7; i++) {
                DayStat d = byDay.get(TimeUtils.dayKey(weekStart.plusDays(i)));
                if (d != null) sum += d.swipeCount;
            }
            WeekBar b = new WeekBar();
            b.day = weekStart;
            b.label = TimeUtils.shortDate(TimeUtils.startOfDayTs(weekStart));
            b.swipes = sum;
            out.add(b);
        }
        return out;
    }

    public void clearAll() {
        db.clearAll();
    }

    /** Writes all sessions + day stats to a CSV in the cache dir. */
    public File exportCsv() throws Exception {
        List<Session> sessions = db.getSessionsBetween(0, Long.MAX_VALUE);
        List<DayStat> stats = db.getDayStats("0000-00-00", "9999-99-99");
        File file = new File(appContext.getCacheDir(), "scrolltrack-export.csv");
        try (BufferedWriter w = new BufferedWriter(new FileWriter(file))) {
            w.write("type,id_or_day,start,end,duration_min,swipes,quality_or_sessions,ended_cleanly\n");
            for (Session s : sessions) {
                w.write(String.format(Locale.US,
                        "session,%d,%s,%s,%.1f,%d,%s,%b\n",
                        s.id, TimeUtils.iso(s.startTime),
                        s.endTime == 0 ? "" : TimeUtils.iso(s.endTime),
                        s.durationMs() / 60000.0, s.swipeCount, s.quality, s.endedCleanly));
            }
            for (DayStat d : stats) {
                w.write(String.format(Locale.US,
                        "day,%s,,,,%d,sessions=%d,duration_min=%.1f\n",
                        d.day, d.swipeCount, d.sessionCount, d.totalDurationMs / 60000.0));
            }
        }
        return file;
    }
}
