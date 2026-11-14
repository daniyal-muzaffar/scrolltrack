package com.dudeyy.scrolltrack.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure time logic for session attribution. No Android dependencies —
 * unit-testable on the JVM.
 *
 * A session's duration is split at local midnight so each day gets exactly
 * the portion that happened on that day. Scrolling time accrued over a
 * window stops at the idle cutoff after the last activity.
 */
public final class DayAttribution {

    private DayAttribution() {}

    /** One day's share of a session. {@code day} is yyyy-MM-dd, local time. */
    public static final class DayPortion {
        public final String day;
        public final long durationMs;

        public DayPortion(String day, long durationMs) {
            this.day = day;
            this.durationMs = durationMs;
        }
    }

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    /**
     * Split [startMs, endMs) into per-day portions. Returns an empty list
     * when the interval is empty or backwards. Portions sum to
     * {@code endMs - startMs}.
     */
    public static List<DayPortion> split(long startMs, long endMs) {
        List<DayPortion> out = new ArrayList<>();
        if (endMs <= startMs) return out;
        LocalDate day = Instant.ofEpochMilli(startMs).atZone(ZONE).toLocalDate();
        long cursor = startMs;
        while (cursor < endMs) {
            long nextMidnight = day.plusDays(1).atStartOfDay(ZONE)
                    .toInstant().toEpochMilli();
            long segEnd = Math.min(endMs, nextMidnight);
            out.add(new DayPortion(day.format(DAY_FMT), segEnd - cursor));
            day = day.plusDays(1);
            cursor = segEnd;
        }
        return out;
    }

    /**
     * Scrolling time accrued over [windowStart, windowEnd) when the last
     * Reels activity was at {@code lastActivityTs} and accrual stops
     * {@code cutoffMs} after it. Returns 0 when the whole window is past the
     * cutoff, the full window when activity is recent, and a partial share
     * when the cutoff falls inside the window.
     */
    public static long scrollingMsForWindow(long windowStart, long windowEnd,
                                            long lastActivityTs, long cutoffMs) {
        if (windowEnd <= windowStart) return 0;
        long activeUntil = lastActivityTs + cutoffMs;
        long end = Math.min(windowEnd, activeUntil);
        return Math.max(0, end - windowStart);
    }
}
