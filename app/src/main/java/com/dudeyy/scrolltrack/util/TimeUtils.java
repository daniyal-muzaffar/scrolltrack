package com.dudeyy.scrolltrack.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Device-timezone date/time helpers. API 26+ has java.time built in. */
public final class TimeUtils {
    private TimeUtils() {}

    private static ZoneId zone() { return ZoneId.systemDefault(); }
    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter SHORT_DAY_FMT = DateTimeFormatter.ofPattern("EEE", Locale.getDefault());
    private static final DateTimeFormatter CLOCK_FMT = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault());
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault());
    private static final DateTimeFormatter FULL_DATE_FMT = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault());
    private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    public static String dayKey(LocalDate date) { return date.format(DAY_FMT); }
    public static String dayKey(long ts) {
        return Instant.ofEpochMilli(ts).atZone(zone()).toLocalDate().format(DAY_FMT);
    }
    public static String todayKey() { return dayKey(LocalDate.now()); }

    public static long startOfDayTs(LocalDate date) {
        return date.atStartOfDay(zone()).toInstant().toEpochMilli();
    }
    public static long endOfDayTs(LocalDate date) {
        return date.plusDays(1).atStartOfDay(zone()).toInstant().toEpochMilli() - 1;
    }

    public static String shortDayLabel(LocalDate date) { return date.format(SHORT_DAY_FMT); }
    public static String clock(long ts) { return Instant.ofEpochMilli(ts).atZone(zone()).format(CLOCK_FMT); }
    public static String shortDate(long ts) { return Instant.ofEpochMilli(ts).atZone(zone()).format(DATE_FMT); }
    public static String fullDate(LocalDate date) { return date.format(FULL_DATE_FMT); }
    public static String iso(long ts) { return Instant.ofEpochMilli(ts).atZone(zone()).format(ISO_FMT); }

    public static int minutesSinceMidnight() {
        LocalTime now = LocalTime.now(zone());
        return now.getHour() * 60 + now.getMinute();
    }

    public static String formatDuration(long ms) {
        if (ms < 60_000) return Math.max(1, ms / 1000) + "s";
        long mins = ms / 60_000;
        if (mins < 60) return mins + "m";
        long h = mins / 60, m = mins % 60;
        return m == 0 ? h + "h" : h + "h " + m + "m";
    }

    public static String formatDurationLong(long ms) {
        long mins = ms / 60_000;
        if (mins < 1) return "Less than a minute";
        if (mins < 60) return mins + " min";
        long h = mins / 60, m = mins % 60;
        return m == 0 ? h + " hr" : h + " hr " + m + " min";
    }

    public static String formatMinutesLabel(int min) {
        int h = min / 60, m = min % 60;
        String ampm = h < 12 ? "AM" : "PM";
        int hh = h == 0 ? 12 : (h > 12 ? h - 12 : h);
        return String.format(Locale.getDefault(), "%d:%02d %s", hh, m, ampm);
    }
}
