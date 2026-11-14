package com.dudeyy.scrolltrack.tracking;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;

import com.dudeyy.scrolltrack.data.db.DayStat;
import com.dudeyy.scrolltrack.data.db.DbHelper;
import com.dudeyy.scrolltrack.data.db.Session;
import com.dudeyy.scrolltrack.data.prefs.Prefs;
import com.dudeyy.scrolltrack.util.DayAttribution;
import com.dudeyy.scrolltrack.util.DebugLog;
import com.dudeyy.scrolltrack.util.ReelCounter;
import com.dudeyy.scrolltrack.util.ReelsDetection;
import com.dudeyy.scrolltrack.util.TimeUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Owns the tracking state machine, Instagram foreground detection (via UsageStats),
 * Reels counting, session persistence, day aggregates, threshold alerts
 * and midnight rollover. Runs as a foreground service while tracking is enabled.
 *
 * Honest counting model: Android does not expose per-Reel content changes, so
 * the measured signal is *completed advances to a new Reel* — the Reels
 * viewer's page index, gated on the Reels surface, with the previous
 * burst-cluster counting kept as a fallback when the surface cannot be
 * determined. The UI labels counts exactly that way.
 *
 * Two time metrics are kept strictly separate:
 *  - Instagram time: total foreground time of Instagram (event-based sessions).
 *  - Scrolling/Reels time: time on the Reels surface with recent activity
 *    (stops accruing {@link #IDLE_CUTOFF_MS} after the last interaction).
 */
public class TrackingService extends Service {

    public static final String ACTION_START = "com.dudeyy.scrolltrack.action.START";
    public static final String ACTION_PAUSE = "com.dudeyy.scrolltrack.action.PAUSE";
    public static final String ACTION_RESUME = "com.dudeyy.scrolltrack.action.RESUME";
    public static final String ACTION_GESTURE = "com.dudeyy.scrolltrack.action.GESTURE";
    public static final String ACTION_SURFACE = "com.dudeyy.scrolltrack.action.SURFACE";
    public static final String ACTION_ROLLOVER = "com.dudeyy.scrolltrack.action.ROLLOVER";
    public static final String ACTION_WINDOW_HINT = "com.dudeyy.scrolltrack.action.WINDOW_HINT";
    public static final String ACTION_REFRESH = "com.dudeyy.scrolltrack.action.REFRESH";
    public static final String EXTRA_DELTA_Y = "extra_delta_y";
    public static final String EXTRA_SCROLL_Y = "extra_scroll_y";
    public static final String EXTRA_EVENT_TIME = "extra_event_time";
    public static final String EXTRA_PACKAGE = "extra_package";
    public static final String EXTRA_FROM_INDEX = "extra_from_index";
    public static final String EXTRA_TO_INDEX = "extra_to_index";
    public static final String EXTRA_ITEM_COUNT = "extra_item_count";
    public static final String EXTRA_SURFACE = "extra_surface";

    private static final String IG = DbHelper.INSTAGRAM_PACKAGE;

    private static final long POLL_MS = 3000;
    /** Scrolling time stops accruing this long after the last Reels interaction. */
    private static final long IDLE_CUTOFF_MS = 45_000;
    /** How often the open session's progress is persisted (heartbeat). */
    private static final long HEARTBEAT_MS = 15_000;

    /** UI observer. Callbacks arrive on the main thread. */
    public interface TrackingListener {
        void onStateChanged(TrackingState state);
        void onTodaySwipesChanged(int swipes);
        void onSwipeCountingChanged(boolean available);
    }

    private static volatile TrackingState state = TrackingState.WAITING;
    private static volatile int todaySwipes = 0;
    private static volatile boolean swipeCountingAvailable = false;
    private static volatile boolean gesturesAccepted = false;
    private static volatile boolean a11yAlive = false;
    private static volatile boolean running = false;
    private static volatile long lastWindowHintTs = 0;
    /** Last window-state change seen for Instagram (a11y). */
    private static volatile long lastIgWindowTs = 0;
    /** Last time the accessibility service saw a scroll event in Instagram (any thread). */
    private static volatile long lastGestureSeenTs = 0;

    public static long getLastGestureSeenTs() { return lastGestureSeenTs; }

    /** Called by the accessibility service for every forwarded scroll event. */
    public static void noteGestureSeen() {
        lastGestureSeenTs = System.currentTimeMillis();
    }

    private static final Set<TrackingListener> listeners = new CopyOnWriteArraySet<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public static TrackingState getState() { return state; }
    public static int getTodaySwipes() { return todaySwipes; }
    public static boolean isSwipeCountingAvailable() { return swipeCountingAvailable; }
    public static boolean isGesturesAccepted() { return gesturesAccepted; }
    public static boolean isRunning() { return running; }

    public static void addListener(TrackingListener l) {
        listeners.add(l);
        MAIN.post(() -> {
            l.onStateChanged(state);
            l.onTodaySwipesChanged(todaySwipes);
            l.onSwipeCountingChanged(swipeCountingAvailable);
        });
    }
    public static void removeListener(TrackingListener l) { listeners.remove(l); }

    private static void emitState(TrackingState s) {
        if (state == s) return;
        state = s;
        for (TrackingListener l : listeners) MAIN.post(() -> l.onStateChanged(s));
    }
    private static void emitSwipes(int n) {
        todaySwipes = n;
        for (TrackingListener l : listeners) MAIN.post(() -> l.onTodaySwipesChanged(n));
    }
    private static void emitSwipeCounting(boolean v) {
        if (swipeCountingAvailable == v) return;
        swipeCountingAvailable = v;
        for (TrackingListener l : listeners) MAIN.post(() -> l.onSwipeCountingChanged(v));
    }

    public static void start(Context context) {
        Intent intent = new Intent(context, TrackingService.class);
        intent.setAction(ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void notifyAccessibilityAlive(Context context) {
        a11yAlive = true;
        poke(context, ACTION_REFRESH);
    }

    public static void notifyAccessibilityGone() { a11yAlive = false; }

    /**
     * Makes sure the tracking service is running, restarting it if it isn't
     * (e.g. after the user force-stopped the app from the control panel).
     * Called from the accessibility service when Instagram opens. If the
     * platform refuses a background start, posts a tap-to-resume notification
     * instead of failing silently.
     */
    public static void ensureRunning(Context context) {
        if (running) {
            poke(context, ACTION_REFRESH);
            return;
        }
        try {
            start(context);
        } catch (Exception e) {
            notifyStopped(context);
        }
    }

    public static void notifyStopped(Context context) {
        try {
            NotificationHelper.ensureChannels(context);
            NotificationManager nm =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.notify(NotificationHelper.NOTIF_ID_RESUME,
                    NotificationHelper.stoppedNotification(context));
        } catch (Exception ignored) { }
    }

    public static void notifyWindowChanged(Context context, String pkg) {
        long now = System.currentTimeMillis();
        if (IG.equals(pkg)) {
            lastWindowHintTs = now;
            lastIgWindowTs = now;
        }
        poke(context, ACTION_WINDOW_HINT);
    }

    private static void poke(Context context, String action) {
        if (!running) return;
        try {
            Intent i = new Intent(context, TrackingService.class);
            i.setAction(action);
            context.startService(i);
        } catch (IllegalStateException ignored) { }
    }

    public static void scheduleMidnightAlarm(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, MidnightReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        long nextMidnight = TimeUtils.startOfDayTs(LocalDate.now().plusDays(1)) + 60_000;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextMidnight, pi);
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextMidnight, pi);
            }
        } catch (SecurityException ignored) { }
    }

    // ---------- instance ----------

    private HandlerThread thread;
    private Handler worker;
    private DbHelper db;
    private Prefs prefs;

    private Session openSession;
    private boolean fgHint;
    private long lastFgSeenTs;
    private long lastPollEndTs;
    private String lastDayKey = TimeUtils.todayKey();
    private long reminderFiredSessionId = -1;

    /** One increment per completed advance to a new Reel (page-index primary,
     *  burst-cluster fallback). */
    private final ReelCounter reelCounter = new ReelCounter();
    /** Last Reels surface reported by the accessibility service. */
    private ReelsDetection.Surface lastSurface = ReelsDetection.Surface.UNKNOWN;
    private long lastSurfaceTs = 0;
    /** Last scroll/navigation activity on the Reels surface (ms epoch). */
    private long lastReelsActivityTs = 0;
    /** Start of the not-yet-accrued scrolling-time window. */
    private long lastAccrualTs = 0;
    private long lastHeartbeatTs = 0;
    private BroadcastReceiver screenReceiver;

    private String lastNotifText = "";
    private long lastNotifTs;
    private OverlayPill overlay;
    /** Set once pollOnce() has evaluated the real world at least once. */
    private boolean hasPolledOnce;

    private final Runnable pollRunnable = new Runnable() {
        @Override public void run() {
            try {
                pollOnce();
            } catch (Exception ignored) { }
            worker.postDelayed(this, POLL_MS);
        }
    };

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        db = DbHelper.get(this);
        prefs = Prefs.get(this);
        a11yAlive = PermissionChecks.isAccessibilityEnabled(this);
        NotificationHelper.ensureChannels(this);
        overlay = new OverlayPill(this);
        thread = new HandlerThread("TrackingThread");
        thread.start();
        worker = new Handler(thread.getLooper());
        // Screen off ends the session at the lock time: UsageStats emits no
        // background event for it, so without this a locked phone keeps the
        // session (and its time) running indefinitely.
        screenReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                if (Intent.ACTION_SCREEN_OFF.equals(i.getAction())) {
                    worker.post(() -> {
                        if (openSession != null) {
                            DebugLog.log(TrackingService.this, "session",
                                    "screen off — closing session");
                            closeSession(true);
                        }
                    });
                }
            }
        };
        registerReceiver(screenReceiver, new IntentFilter(Intent.ACTION_SCREEN_OFF));
        worker.post(() -> {
            cleanupOrphanSessions();
            refreshTodaySwipes();
            lastDayKey = TimeUtils.todayKey();
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_PAUSE.equals(action)) {
            worker.post(() -> {
                prefs.setTrackingEnabled(false);
                closeSession(true);
                recomputeState();
                stopSelf();
            });
            return START_NOT_STICKY;
        }
        if (ACTION_RESUME.equals(action)) {
            worker.post(() -> {
                prefs.setTrackingEnabled(true);
                startForegroundLocked();
                startPolling();
                recomputeState();
            });
            return START_STICKY;
        }
        if (ACTION_GESTURE.equals(action) && intent != null) {
            final int dy = intent.getIntExtra(EXTRA_DELTA_Y, 0);
            final int sy = intent.getIntExtra(EXTRA_SCROLL_Y, 0);
            final long ts = intent.getLongExtra(EXTRA_EVENT_TIME, System.currentTimeMillis());
            final int fromIndex = intent.getIntExtra(EXTRA_FROM_INDEX, -1);
            final int toIndex = intent.getIntExtra(EXTRA_TO_INDEX, -1);
            final int itemCount = intent.getIntExtra(EXTRA_ITEM_COUNT, -1);
            final ReelsDetection.Surface surface = ReelsDetection.fromCode(
                    intent.getIntExtra(EXTRA_SURFACE, ReelsDetection.CODE_UNKNOWN));
            worker.post(() -> {
                if (!prefs.isTrackingEnabled()) { stopSelf(); return; }
                onScrollEvent(dy, sy, ts, fromIndex, toIndex, itemCount, surface);
            });
            return START_STICKY;
        }
        if (ACTION_SURFACE.equals(action) && intent != null) {
            final ReelsDetection.Surface surface = ReelsDetection.fromCode(
                    intent.getIntExtra(EXTRA_SURFACE, ReelsDetection.CODE_UNKNOWN));
            final long ts = intent.getLongExtra(EXTRA_EVENT_TIME, System.currentTimeMillis());
            worker.post(() -> noteSurface(surface, ts));
            return START_STICKY;
        }
        worker.post(() -> {
            if (ACTION_ROLLOVER.equals(action)) handleRollover();
            if (!prefs.isTrackingEnabled()) { stopSelf(); return; }
            startForegroundLocked();
            startPolling();
            if (ACTION_WINDOW_HINT.equals(action) || ACTION_REFRESH.equals(action)) pollOnce();
            recomputeState();
            scheduleMidnightAlarm(this);
        });
        return START_STICKY;
    }

    // ---------- foreground service ----------

    private void startForegroundLocked() {
        try {
            // dataSync exists on every API level that has typed foreground services
            // (29+), so it can be passed explicitly everywhere the 3-arg overload exists.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NotificationHelper.NOTIF_ID_FOREGROUND,
                        NotificationHelper.foregroundNotification(this, statusLine(), false),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(NotificationHelper.NOTIF_ID_FOREGROUND,
                        NotificationHelper.foregroundNotification(this, statusLine(), false));
            }
        } catch (Exception ignored) { }
    }

    // ---------- polling / foreground detection ----------

    private void startPolling() {
        worker.removeCallbacks(pollRunnable);
        worker.post(() -> {
            seedForegroundHint();
            worker.post(pollRunnable);
        });
    }

    private void seedForegroundHint() {
        if (!PermissionChecks.hasUsageAccess(this)) return;
        try {
            UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
            long now = System.currentTimeMillis();
            List<UsageStats> stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 60_000, now);
            UsageStats top = null;
            for (UsageStats s : stats) {
                if (top == null || s.getLastTimeUsed() > top.getLastTimeUsed()) top = s;
            }
            if (top != null && IG.equals(top.getPackageName()) && now - top.getLastTimeUsed() < 15_000) {
                fgHint = true;
                lastFgSeenTs = now;
            }
        } catch (Exception ignored) { }
    }

    private void pollOnce() {
        long now = System.currentTimeMillis();
        if (!PermissionChecks.hasUsageAccess(this)) {
            // Permission revoked (or never granted): we can no longer tell whether
            // Instagram is in the foreground, so close any open session rather than
            // letting it run forever, and surface PERMISSION_REQUIRED.
            if (openSession != null) closeSession(true);
            fgHint = false;
        } else {
            try {
                UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
                // Signal 1 (authoritative): foreground/background transitions from usage events.
                long from = lastPollEndTs == 0 ? now - 20_000 : lastPollEndTs - 5_000;
                UsageEvents events = usm.queryEvents(Math.max(0, from), now);
                UsageEvents.Event e = new UsageEvents.Event();
                long igFgTs = 0;
                long otherFgTs = 0;
                while (events.hasNextEvent()) {
                    events.getNextEvent(e);
                    if (e.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                        if (IG.equals(e.getPackageName())) {
                            igFgTs = Math.max(igFgTs, e.getTimeStamp());
                        } else {
                            otherFgTs = Math.max(otherFgTs, e.getTimeStamp());
                        }
                    } else if (e.getEventType() == UsageEvents.Event.MOVE_TO_BACKGROUND
                            && IG.equals(e.getPackageName())) {
                        fgHint = false;
                    }
                }
                // Whoever came to the foreground last wins. Another app taking over
                // is definitive proof Instagram is backgrounded — this closes the
                // session and hides the pill promptly when the user leaves Instagram.
                if (igFgTs > 0 || otherFgTs > 0) {
                    fgHint = igFgTs >= otherFgTs;
                    if (fgHint) lastFgSeenTs = Math.max(lastFgSeenTs, igFgTs);
                }
                lastPollEndTs = now;
                // Signal 2 (fallback): most-recently-used package. If another app clearly
                // took over after Instagram, Instagram is in the background even if we
                // missed the transition event. Never closes on staleness alone.
                List<UsageStats> stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 120_000, now);
                UsageStats top = null;
                long igTime = 0;
                for (UsageStats s : stats) {
                    if (top == null || s.getLastTimeUsed() > top.getLastTimeUsed()) top = s;
                    if (IG.equals(s.getPackageName())) igTime = s.getLastTimeUsed();
                }
                if (top != null && now - top.getLastTimeUsed() < 20_000) {
                    if (IG.equals(top.getPackageName())) {
                        fgHint = true;
                        lastFgSeenTs = Math.max(lastFgSeenTs, top.getLastTimeUsed());
                    } else if (top.getLastTimeUsed() > igTime + 15_000) {
                        fgHint = false;
                    }
                }
            } catch (Exception ignored) { }
        }

        // Close a dangling fallback cluster.
        ReelCounter.Result flushed = reelCounter.flush(now);
        if (flushed.counted) countSwipe();
        if (flushed.loggable) {
            DebugLog.log(this, "count", "flush counted=" + flushed.counted
                    + " reason=" + flushed.reason);
        }

        // A recent Instagram window hint is strong evidence Instagram is in the
        // foreground right now.
        if (now - lastWindowHintTs < 10_000) {
            fgHint = true;
            lastFgSeenTs = now;
        }

        if (fgHint && openSession == null) {
            openSession();
        } else if (!fgHint && openSession != null) {
            closeSession(true);
        }

        accrueScrolling(now);

        checkSessionReminder(now);
        handleRolloverIfNeeded();
        hasPolledOnce = true;
        recomputeState();
        maybeUpdateNotification(now, false);
        gesturesAccepted = openSession != null;
        boolean a11y = a11yAlive || PermissionChecks.isAccessibilityEnabled(this);
        if (a11y) a11yAlive = true;
        emitSwipeCounting(a11y);
    }

    // ---------- sessions ----------

    private void openSession() {
        long now = System.currentTimeMillis();
        // Defensive: never leave two open sessions in the database (prevents
        // overlapping sessions inflating totals after a crash or a race).
        Session existing = db.getOpenSession();
        if (existing != null) {
            DebugLog.log(this, "session", "openSession: closing stale open session "
                    + existing.id + " first");
            creditSessionClosed(existing, existing.lastSeenMs > existing.startTime
                    ? existing.lastSeenMs : existing.startTime, false);
        }
        Session s = new Session();
        s.startTime = now;
        s.endTime = 0;
        s.quality = PermissionChecks.isAccessibilityEnabled(this) ? "full" : "time_only";
        s.id = db.insertSession(s);
        openSession = s;
        lastAccrualTs = now;
        lastHeartbeatTs = now;
        lastReelsActivityTs = 0;
        db.ensureDayStat(TimeUtils.todayKey());
        reminderFiredSessionId = -1;
        DebugLog.log(this, "session", "opened session " + s.id + " quality=" + s.quality);
    }

    private void closeSession(boolean cleanly) {
        Session s = openSession;
        if (s == null) return;
        // A swipe still in flight (open fallback cluster) counts if valid.
        ReelCounter.Result r = reelCounter.flush(System.currentTimeMillis());
        if (r.counted) countSwipe();
        if (r.loggable) {
            DebugLog.log(this, "count", "close flush counted=" + r.counted
                    + " reason=" + r.reason);
        }
        creditSessionClosed(s, System.currentTimeMillis(), cleanly);
        openSession = null;
        gesturesAccepted = false;
        reelCounter.reset();
        lastSurface = ReelsDetection.Surface.UNKNOWN;
        lastAccrualTs = 0;
    }

    /**
     * Persists a finished session and credits its time to the days it spans.
     * The session itself is attributed to the day it started (session count
     * and longest session, full duration); its time — total and Reels — is
     * split at local midnight so each day gets exactly its own portion.
     */
    private void creditSessionClosed(Session s, long endTs, boolean cleanly) {
        endTs = Math.max(endTs, s.startTime);
        s.endTime = endTs;
        s.endedCleanly = cleanly;
        db.updateSession(s);
        long dur = endTs - s.startTime;
        long scroll = s.scrollingDurationMs;
        DayStat startStat = db.ensureDayStat(TimeUtils.dayKey(s.startTime));
        startStat.sessionCount += 1;
        startStat.longestSessionMs = Math.max(startStat.longestSessionMs, dur);
        db.updateDayStat(startStat);
        List<DayAttribution.DayPortion> parts = DayAttribution.split(s.startTime, endTs);
        long remainingScroll = scroll;
        for (int i = 0; i < parts.size(); i++) {
            DayAttribution.DayPortion p = parts.get(i);
            DayStat d = db.ensureDayStat(p.day);
            d.totalDurationMs += p.durationMs;
            if (scroll > 0 && dur > 0) {
                // Prorate scrolling time by each day's share; the last day
                // takes the rounding remainder so nothing is lost.
                long share = (i == parts.size() - 1)
                        ? remainingScroll : scroll * p.durationMs / dur;
                remainingScroll -= share;
                d.scrollingDurationMs += share;
            }
            db.updateDayStat(d);
        }
        DebugLog.log(this, "session", "closed session " + s.id
                + " dur=" + dur + "ms reels=" + scroll + "ms cleanly=" + cleanly);
    }

    /**
     * Orphaned open sessions (process killed, reboot) are closed at their last
     * heartbeat — not at "now" — so their time is recovered, not lost and not
     * inflated. A session with no heartbeat at all closes at its start time.
     */
    private void cleanupOrphanSessions() {
        for (Session s : db.getAllOpenSessions()) {
            long endTs = s.lastSeenMs > s.startTime ? s.lastSeenMs : s.startTime;
            DebugLog.log(this, "session", "orphan session " + s.id
                    + " recovered, closed at lastSeen=" + endTs);
            creditSessionClosed(s, endTs, false);
        }
    }

    // ---------- gestures ----------

    /** A scroll event from Instagram. Counts at most one advance per new Reel. */
    private void onScrollEvent(int dy, int sy, long ts, int fromIndex, int toIndex,
                               int itemCount, ReelsDetection.Surface surface) {
        // Any scroll inside Instagram is proof it's on screen — refresh the
        // window evidence so the session stays open mid-scroll.
        if (ts > lastIgWindowTs) lastIgWindowTs = ts;
        if (openSession == null) return;
        noteSurface(surface, ts);
        if (surface == ReelsDetection.Surface.REELS) lastReelsActivityTs = ts;
        ReelCounter.Result r = reelCounter.feed(
                new ReelCounter.ScrollEvent(fromIndex, toIndex, itemCount, dy, sy, ts, surface));
        if (r.counted) countSwipe();
        if (r.loggable) {
            DebugLog.log(this, "count", "surface=" + surface + " from=" + fromIndex
                    + " to=" + toIndex + " counted=" + r.counted + " reason=" + r.reason);
        }
    }

    /** Records the latest Reels-surface verdict; navigating onto Reels counts
     *  as activity (the user just tapped there). */
    private void noteSurface(ReelsDetection.Surface surface, long ts) {
        if (surface != lastSurface) {
            DebugLog.log(this, "surface", "surface " + lastSurface + " -> " + surface);
            if (surface == ReelsDetection.Surface.REELS) lastReelsActivityTs = ts;
            lastSurface = surface;
        }
        lastSurfaceTs = ts;
    }

    /**
     * Accrues measured Reels (scrolling) time for the open session: only while
     * the surface is Reels and only up to the idle cutoff after the last
     * interaction. Progress is persisted with the heartbeat so a killed
     * process loses at most ~15 s.
     */
    private void accrueScrolling(long now) {
        if (openSession == null) {
            lastAccrualTs = 0;
            return;
        }
        if (lastAccrualTs == 0) lastAccrualTs = openSession.startTime;
        long add = 0;
        if (lastSurface == ReelsDetection.Surface.REELS) {
            add = DayAttribution.scrollingMsForWindow(
                    lastAccrualTs, now, lastReelsActivityTs, IDLE_CUTOFF_MS);
        }
        lastAccrualTs = now;
        if (add > 0) openSession.scrollingDurationMs += add;
        if (now - lastHeartbeatTs >= HEARTBEAT_MS) {
            lastHeartbeatTs = now;
            openSession.lastSeenMs = now;
            db.updateSession(openSession);
        }
    }

    private void countSwipe() {
        Session s = openSession;
        if (s == null) return;
        s.swipeCount += 1;
        db.updateSession(s);

        String day = TimeUtils.todayKey();
        DayStat stat = db.ensureDayStat(day);
        stat.swipeCount += 1;
        db.updateDayStat(stat);
        emitSwipes(stat.swipeCount);
        if (overlay != null && overlay.isShowing()) overlay.update(stat.swipeCount);
        checkThresholds(stat.swipeCount, stat);
    }

    private void refreshTodaySwipes() {
        DayStat d = db.getDayStat(TimeUtils.todayKey());
        emitSwipes(d != null ? d.swipeCount : 0);
    }

    // ---------- thresholds & reminders ----------

    private boolean notificationsAllowed() {
        if (!prefs.isNotificationsEnabled()) return false;
        if (!PermissionChecks.areNotificationsGranted(this)) return false;
        if (prefs.isQuietHoursEnabled()) {
            int nowMin = TimeUtils.minutesSinceMidnight();
            int start = prefs.getQuietStartMin();
            int end = prefs.getQuietEndMin();
            boolean inQuiet = start <= end
                    ? (nowMin >= start && nowMin < end)
                    : (nowMin >= start || nowMin < end);
            if (inQuiet) return false;
        }
        return true;
    }

    private void checkThresholds(int swipes, DayStat stat) {
        Integer target = prefs.getDailyTarget();
        if (target == null || target <= 0 || !notificationsAllowed()) return;
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        DayStat current = db.getDayStat(TimeUtils.todayKey());
        if (current == null) current = stat;
        boolean dirty = false;
        if (swipes >= target && !current.notified100 && prefs.isWarn100()) {
            try { nm.notify(2100, NotificationHelper.thresholdNotification(this, 100, swipes, target)); }
            catch (Exception ignored) { }
            current.notified100 = true; dirty = true;
        }
        if (swipes >= target * 80 / 100 && !current.notified80 && prefs.isWarn80()) {
            try { nm.notify(2080, NotificationHelper.thresholdNotification(this, 80, swipes, target)); }
            catch (Exception ignored) { }
            current.notified80 = true; dirty = true;
        }
        if (swipes >= target * 50 / 100 && !current.notified50 && prefs.isWarn50()) {
            try { nm.notify(2050, NotificationHelper.thresholdNotification(this, 50, swipes, target)); }
            catch (Exception ignored) { }
            current.notified50 = true; dirty = true;
        }
        if (dirty) db.updateDayStat(current);
    }

    private void checkSessionReminder(long now) {
        Session s = openSession;
        if (s == null || reminderFiredSessionId == s.id) return;
        Integer reminderMin = prefs.getSessionReminderMin();
        if (reminderMin == null || reminderMin <= 0) return;
        if (now - s.startTime >= reminderMin * 60_000L && notificationsAllowed()) {
            reminderFiredSessionId = s.id;
            try {
                NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                nm.notify(2004, NotificationHelper.sessionReminderNotification(this, reminderMin));
            } catch (Exception ignored) { }
        }
    }

    // ---------- rollover ----------

    private void handleRolloverIfNeeded() {
        if (!TimeUtils.todayKey().equals(lastDayKey)) handleRollover();
    }

    private void handleRollover() {
        lastDayKey = TimeUtils.todayKey();
        db.ensureDayStat(lastDayKey);
        refreshTodaySwipes();
        reconcileUsageStats();
        scheduleMidnightAlarm(this);
    }

    /**
     * Sanity check, once a day: compare yesterday's event-based Instagram
     * time with the UsageStats aggregate. The event-based sessions are the
     * source of truth — a large difference is logged for investigation, never
     * silently "corrected" into the data.
     */
    private void reconcileUsageStats() {
        try {
            if (!PermissionChecks.hasUsageAccess(this)) return;
            LocalDate yesterday = LocalDate.now().minusDays(1);
            long from = TimeUtils.startOfDayTs(yesterday);
            long to = TimeUtils.startOfDayTs(yesterday.plusDays(1));
            UsageStatsManager usm =
                    (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
            long usTotal = 0;
            List<UsageStats> stats =
                    usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, from, to);
            if (stats != null) {
                for (UsageStats s : stats) {
                    if (IG.equals(s.getPackageName())) {
                        usTotal = s.getTotalTimeInForeground();
                    }
                }
            }
            DayStat d = db.getDayStat(TimeUtils.dayKey(yesterday));
            long ours = d != null ? d.totalDurationMs : 0;
            long diff = Math.abs(usTotal - ours);
            long threshold = Math.max(5 * 60_000, ours / 4);
            if (diff > threshold) {
                DebugLog.log(this, "reconcile", "yesterday usageStats=" + usTotal
                        + "ms eventBased=" + ours + "ms diff=" + diff
                        + "ms (event-based data kept)");
            }
        } catch (Exception ignored) { }
    }

    // ---------- state ----------

    private void recomputeState() {
        TrackingState next;
        if (!PermissionChecks.isInstagramInstalled(this)) next = TrackingState.UNAVAILABLE;
        else if (!prefs.isTrackingEnabled()) next = TrackingState.PAUSED;
        else if (!PermissionChecks.hasUsageAccess(this)) next = TrackingState.PERMISSION_REQUIRED;
        else if (openSession != null) next = TrackingState.ACTIVE;
        else next = TrackingState.WAITING;
        if (next != state) {
            emitState(next);
            maybeUpdateNotification(System.currentTimeMillis(), true);
        }
        updateOverlay();
        maybeIdleStop();
    }

    /**
     * When Instagram isn't in use and the accessibility service can restart us
     * the next time Instagram opens, stop the foreground service entirely so
     * the app leaves no trace (no persistent notification, no pill). The
     * accessibility service itself stays alive — it's system-managed and
     * notification-free. Without the accessibility service we must keep
     * polling UsageStats, so the service stays up in that case.
     */
    private void maybeIdleStop() {
        // Never stop before the first poll has evaluated the real world —
        // otherwise the service kills itself on startup before it can notice
        // that Instagram is already open.
        if (!hasPolledOnce) return;
        if (state == TrackingState.WAITING
                && prefs.isTrackingEnabled()
                && (a11yAlive || PermissionChecks.isAccessibilityEnabled(this))) {
            stopSelf();
        }
    }

    /** Floating pill is visible only during an active Instagram session. */
    private void updateOverlay() {
        if (overlay == null) return;
        boolean want = prefs.isOverlayPillEnabled()
                && state == TrackingState.ACTIVE
                && overlay.canShow();
        if (want) overlay.show(todaySwipes);
        else overlay.hide();
    }

    private String statusLine() {
        switch (state) {
            case ACTIVE: return "Tracking reels • " + todaySwipes + " swipes today";
            case WAITING: return "Waiting for Instagram • " + todaySwipes + " swipes today";
            case PERMISSION_REQUIRED: return "Permission required to track";
            case PAUSED: return "Tracking paused";
            default: return "Instagram not installed";
        }
    }

    private void maybeUpdateNotification(long now, boolean force) {
        String text = statusLine();
        if (!force && text.equals(lastNotifText) && now - lastNotifTs < 20_000) return;
        lastNotifText = text;
        lastNotifTs = now;
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            nm.notify(NotificationHelper.NOTIF_ID_FOREGROUND,
                    NotificationHelper.foregroundNotification(this, text, false));
        } catch (Exception ignored) { }
    }

    @Override
    public void onDestroy() {
        gesturesAccepted = false;
        running = false;
        if (screenReceiver != null) {
            try { unregisterReceiver(screenReceiver); } catch (Exception ignored) { }
            screenReceiver = null;
        }
        if (overlay != null) overlay.hide();
        if (worker != null) {
            worker.removeCallbacks(pollRunnable);
            // Safety net: never leave a session open in the database because
            // the service went away (the heartbeat bounds any orphan to ~15 s
            // anyway). Runs on the worker thread before it quits.
            worker.post(() -> { if (openSession != null) closeSession(true); });
        }
        if (thread != null) thread.quitSafely();
        super.onDestroy();
    }
}
