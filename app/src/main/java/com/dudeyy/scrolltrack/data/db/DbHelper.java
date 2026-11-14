package com.dudeyy.scrolltrack.data.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * SQLite storage for sessions and per-day aggregates.
 * All methods are synchronous — callers must stay off the main thread.
 */
public class DbHelper extends SQLiteOpenHelper {
    public static final String INSTAGRAM_PACKAGE = "com.instagram.android";
    private static final String DB_NAME = "scrolltrack.db";
    private static final int DB_VERSION = 2;

    private static volatile DbHelper instance;

    public static DbHelper get(Context ctx) {
        if (instance == null) {
            synchronized (DbHelper.class) {
                if (instance == null) instance = new DbHelper(ctx.getApplicationContext());
            }
        }
        return instance;
    }

    private DbHelper(Context ctx) { super(ctx, DB_NAME, null, DB_VERSION); }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE sessions (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "startTime INTEGER NOT NULL," +
                "endTime INTEGER NOT NULL DEFAULT 0," +
                "swipeCount INTEGER NOT NULL DEFAULT 0," +
                "packageName TEXT NOT NULL DEFAULT 'com.instagram.android'," +
                "endedCleanly INTEGER NOT NULL DEFAULT 0," +
                "quality TEXT NOT NULL DEFAULT 'full'," +
                "scrollingDurationMs INTEGER NOT NULL DEFAULT 0," +
                "lastSeenMs INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX idx_sessions_start ON sessions(startTime)");
        db.execSQL("CREATE TABLE day_stats (" +
                "day TEXT PRIMARY KEY," +
                "swipeCount INTEGER NOT NULL DEFAULT 0," +
                "sessionCount INTEGER NOT NULL DEFAULT 0," +
                "totalDurationMs INTEGER NOT NULL DEFAULT 0," +
                "longestSessionMs INTEGER NOT NULL DEFAULT 0," +
                "notified50 INTEGER NOT NULL DEFAULT 0," +
                "notified80 INTEGER NOT NULL DEFAULT 0," +
                "notified100 INTEGER NOT NULL DEFAULT 0," +
                "scrollingDurationMs INTEGER NOT NULL DEFAULT 0)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Migrations must never drop tables: all history is user data.
        // v1 -> v2: measured Reels (scrolling) time + session heartbeat.
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE sessions ADD COLUMN scrollingDurationMs INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE sessions ADD COLUMN lastSeenMs INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE day_stats ADD COLUMN scrollingDurationMs INTEGER NOT NULL DEFAULT 0");
        }
    }

    // ---------- sessions ----------

    public long insertSession(Session s) {
        ContentValues v = new ContentValues();
        v.put("startTime", s.startTime);
        v.put("endTime", s.endTime);
        v.put("swipeCount", s.swipeCount);
        v.put("packageName", s.packageName);
        v.put("endedCleanly", s.endedCleanly ? 1 : 0);
        v.put("quality", s.quality);
        v.put("scrollingDurationMs", s.scrollingDurationMs);
        v.put("lastSeenMs", s.lastSeenMs);
        return getWritableDatabase().insert("sessions", null, v);
    }

    public void updateSession(Session s) {
        ContentValues v = new ContentValues();
        v.put("startTime", s.startTime);
        v.put("endTime", s.endTime);
        v.put("swipeCount", s.swipeCount);
        v.put("endedCleanly", s.endedCleanly ? 1 : 0);
        v.put("quality", s.quality);
        v.put("scrollingDurationMs", s.scrollingDurationMs);
        v.put("lastSeenMs", s.lastSeenMs);
        getWritableDatabase().update("sessions", v, "id=?", new String[]{String.valueOf(s.id)});
    }

    private Session readSession(Cursor c) {
        Session s = new Session();
        s.id = c.getLong(0);
        s.startTime = c.getLong(1);
        s.endTime = c.getLong(2);
        s.swipeCount = c.getInt(3);
        s.packageName = c.getString(4);
        s.endedCleanly = c.getInt(5) == 1;
        s.quality = c.getString(6);
        // v2 columns are appended at the end; guard for a pre-migration row.
        if (c.getColumnCount() > 7) s.scrollingDurationMs = c.getLong(7);
        if (c.getColumnCount() > 8) s.lastSeenMs = c.getLong(8);
        return s;
    }

    public Session getOpenSession() {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT * FROM sessions WHERE endTime=0 ORDER BY startTime DESC LIMIT 1", null)) {
            if (c.moveToFirst()) return readSession(c);
        }
        return null;
    }

    public List<Session> getAllOpenSessions() {
        List<Session> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT * FROM sessions WHERE endTime=0 ORDER BY startTime DESC", null)) {
            while (c.moveToNext()) out.add(readSession(c));
        }
        return out;
    }

    public Session getSession(long id) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT * FROM sessions WHERE id=? LIMIT 1", new String[]{String.valueOf(id)})) {
            if (c.moveToFirst()) return readSession(c);
        }
        return null;
    }

    public List<Session> getSessionsBetween(long from, long to) {
        List<Session> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT * FROM sessions WHERE startTime>=? AND startTime<? ORDER BY startTime DESC",
                new String[]{String.valueOf(from), String.valueOf(to)})) {
            while (c.moveToNext()) out.add(readSession(c));
        }
        return out;
    }

    // ---------- day stats ----------

    private DayStat readDayStat(Cursor c) {
        DayStat d = new DayStat(c.getString(0));
        d.swipeCount = c.getInt(1);
        d.sessionCount = c.getInt(2);
        d.totalDurationMs = c.getLong(3);
        d.longestSessionMs = c.getLong(4);
        d.notified50 = c.getInt(5) == 1;
        d.notified80 = c.getInt(6) == 1;
        d.notified100 = c.getInt(7) == 1;
        // v2 column appended at the end; guard for a pre-migration row.
        if (c.getColumnCount() > 8) d.scrollingDurationMs = c.getLong(8);
        return d;
    }

    public void insertDayStatIgnore(DayStat d) {
        ContentValues v = new ContentValues();
        v.put("day", d.day);
        getWritableDatabase().insertWithOnConflict("day_stats", null, v, SQLiteDatabase.CONFLICT_IGNORE);
    }

    public void updateDayStat(DayStat d) {
        ContentValues v = new ContentValues();
        v.put("swipeCount", d.swipeCount);
        v.put("sessionCount", d.sessionCount);
        v.put("totalDurationMs", d.totalDurationMs);
        v.put("longestSessionMs", d.longestSessionMs);
        v.put("notified50", d.notified50 ? 1 : 0);
        v.put("notified80", d.notified80 ? 1 : 0);
        v.put("notified100", d.notified100 ? 1 : 0);
        v.put("scrollingDurationMs", d.scrollingDurationMs);
        getWritableDatabase().update("day_stats", v, "day=?", new String[]{d.day});
    }

    public DayStat getDayStat(String day) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT * FROM day_stats WHERE day=? LIMIT 1", new String[]{day})) {
            if (c.moveToFirst()) return readDayStat(c);
        }
        return null;
    }

    public List<DayStat> getDayStats(String fromDay, String toDay) {
        List<DayStat> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT * FROM day_stats WHERE day>=? AND day<=? ORDER BY day ASC",
                new String[]{fromDay, toDay})) {
            while (c.moveToNext()) out.add(readDayStat(c));
        }
        return out;
    }

    /**
     * The day with the highest recorded swipe count, or null when no day has
     * any swipes. Ties resolve to the earliest day — the day the record was
     * first set.
     */
    public DayStat getHighestSwipeDay() {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT * FROM day_stats WHERE swipeCount > 0 " +
                        "ORDER BY swipeCount DESC, day ASC LIMIT 1", null)) {
            if (c.moveToFirst()) return readDayStat(c);
        }
        return null;
    }

    public DayStat ensureDayStat(String day) {
        insertDayStatIgnore(new DayStat(day));
        DayStat d = getDayStat(day);
        return d != null ? d : new DayStat(day);
    }

    // ---------- life impact aggregates ----------

    /** Total recorded Instagram foreground time across all days. */
    public long sumTotalDurationMs() {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(totalDurationMs),0) FROM day_stats", null)) {
            if (c.moveToFirst()) return c.getLong(0);
        }
        return 0;
    }

    /** Number of days on which any usage was actually recorded. */
    public int countDaysWithDuration() {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM day_stats WHERE totalDurationMs > 0", null)) {
            if (c.moveToFirst()) return c.getInt(0);
        }
        return 0;
    }

    /**
     * Total scrolling (Reels) time. Per day, prefers the measured value
     * (time on the Reels surface with recent activity, recorded since v2);
     * days recorded before that have no measured value, so they fall back to
     * the old estimate: time in sessions where at least one swipe was
     * detected. That estimate can include pauses, so it is an upper estimate.
     */
    public long sumScrollingDurationMs() {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(" +
                        "CASE WHEN ds.scrollingDurationMs > 0 THEN ds.scrollingDurationMs " +
                        "ELSE COALESCE((SELECT SUM(s.endTime - s.startTime) FROM sessions s " +
                        "WHERE s.endTime > 0 AND s.swipeCount > 0 " +
                        "AND strftime('%Y-%m-%d', s.startTime / 1000, 'unixepoch', 'localtime') = ds.day), 0) " +
                        "END), 0) FROM day_stats ds", null)) {
            if (c.moveToFirst()) return c.getLong(0);
        }
        return 0;
    }

    /** Start time of the earliest finished session, or 0 if none exists. */
    public long earliestSessionStart() {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT COALESCE(MIN(startTime),0) FROM sessions WHERE endTime > 0", null)) {
            if (c.moveToFirst()) return c.getLong(0);
        }
        return 0;
    }

    // ---------- maintenance ----------

    public void clearAll() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("sessions", null, null);
        db.delete("day_stats", null, null);
    }
}
