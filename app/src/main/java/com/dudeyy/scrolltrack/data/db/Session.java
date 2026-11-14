package com.dudeyy.scrolltrack.data.db;

/**
 * One continuous Instagram foreground session.
 * endTime == 0 while the session is still open.
 */
public class Session {
    public long id;
    public long startTime;
    public long endTime; // 0 = open
    public int swipeCount;
    /** Measured time on the Reels surface with recent activity (ms). */
    public long scrollingDurationMs;
    /** Last heartbeat while the session was open (ms epoch); 0 if never. Used
     *  to close orphaned sessions at the last known time instead of "now". */
    public long lastSeenMs;
    public String packageName = "com.instagram.android";
    public boolean endedCleanly;
    /** "full" = gesture + time tracking, "time_only" = accessibility off, time via usage stats */
    public String quality = "full";

    public long durationMs() { return (endTime == 0 ? System.currentTimeMillis() : endTime) - startTime; }
    public boolean isOpen() { return endTime == 0; }
}
