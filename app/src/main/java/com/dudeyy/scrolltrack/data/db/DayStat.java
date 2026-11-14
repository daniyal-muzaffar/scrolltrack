package com.dudeyy.scrolltrack.data.db;

/** Per-local-day aggregate. Notification flags fire each threshold at most once per day. */
public class DayStat {
    public String day; // yyyy-MM-dd in device timezone
    public int swipeCount;
    public int sessionCount;
    public long totalDurationMs;
    public long longestSessionMs;
    /** Measured time on the Reels surface with recent activity (ms). */
    public long scrollingDurationMs;
    public boolean notified50;
    public boolean notified80;
    public boolean notified100;

    public DayStat(String day) { this.day = day; }
}
