import com.dudeyy.scrolltrack.util.DayAttribution;
import com.dudeyy.scrolltrack.util.DayAttribution.DayPortion;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * JVM tests for midnight splitting and idle-cutoff scrolling accrual.
 * Run with run_tests.sh. Exits non-zero on the first failure.
 */
public class TestDayAttribution {

    private static int failures = 0;

    private static long ts(int y, int mo, int d, int h, int mi) {
        return LocalDateTime.of(y, mo, d, h, mi)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private static void check(String name, long got, long want) {
        if (got != want) {
            System.out.println("FAIL " + name + ": got " + got + ", want " + want);
            failures++;
        } else {
            System.out.println("ok   " + name + " = " + got);
        }
    }

    private static void checkSplit(String name, long start, long end,
                                   String[] days, long[] durations) {
        List<DayPortion> parts = DayAttribution.split(start, end);
        boolean ok = parts.size() == days.length;
        long sum = 0;
        for (DayPortion p : parts) sum += p.durationMs;
        if (ok) {
            for (int i = 0; i < days.length; i++) {
                if (!parts.get(i).day.equals(days[i])
                        || parts.get(i).durationMs != durations[i]) ok = false;
            }
        }
        if (!ok || sum != Math.max(0, end - start)) {
            System.out.println("FAIL " + name + ": got " + describe(parts)
                    + ", want days=" + java.util.Arrays.toString(days)
                    + " durations=" + java.util.Arrays.toString(durations));
            failures++;
        } else {
            System.out.println("ok   " + name + " -> " + describe(parts));
        }
    }

    private static String describe(List<DayPortion> parts) {
        StringBuilder sb = new StringBuilder("[");
        for (DayPortion p : parts) sb.append(p.day).append(":").append(p.durationMs).append(" ");
        return sb.append("]").toString();
    }

    public static void main(String[] args) {
        long H = 3_600_000L;
        long M = 60_000L;

        // 1. Session across midnight -> split correctly across two days.
        checkSplit("23:30-00:30 splits 30m/30m",
                ts(2026, 10, 20, 23, 30), ts(2026, 10, 21, 0, 30),
                new String[]{"2026-10-20", "2026-10-21"},
                new long[]{30 * M, 30 * M});
        // 2. Same-day session -> one portion.
        checkSplit("10:00-10:45 single day",
                ts(2026, 10, 20, 10, 0), ts(2026, 10, 20, 10, 45),
                new String[]{"2026-10-20"}, new long[]{45 * M});
        // 3. Ending exactly at midnight -> all on the first day.
        checkSplit("23:00-00:00 exact boundary",
                ts(2026, 10, 20, 23, 0), ts(2026, 10, 21, 0, 0),
                new String[]{"2026-10-20"}, new long[]{H});
        // 4. Three-day span -> 2h / 24h / 2h.
        checkSplit("22:00 day1 to 02:00 day3",
                ts(2026, 10, 20, 22, 0), ts(2026, 10, 23, 2, 0),
                new String[]{"2026-10-20", "2026-10-21", "2026-10-22", "2026-10-23"},
                new long[]{2 * H, 24 * H, 24 * H, 2 * H});
        // 5. Empty / backwards interval -> no portions.
        checkSplit("end before start",
                ts(2026, 10, 20, 10, 0), ts(2026, 10, 20, 9, 0),
                new String[]{}, new long[]{});
        checkSplit("zero-length",
                ts(2026, 10, 20, 10, 0), ts(2026, 10, 20, 10, 0),
                new String[]{}, new long[]{});

        // 6. Scrolling accrual: recent activity -> full window.
        check("active window accrues fully",
                DayAttribution.scrollingMsForWindow(0, 30_000, 0, 45_000), 30_000);
        // 7. Idle beyond cutoff -> nothing accrues.
        check("idle window accrues zero",
                DayAttribution.scrollingMsForWindow(50_000, 80_000, 0, 45_000), 0);
        // 8. Cutoff falls inside the window -> partial share only.
        check("cutoff mid-window accrues partial",
                DayAttribution.scrollingMsForWindow(40_000, 80_000, 0, 45_000), 5_000);
        // 9. Activity just before the window -> full window.
        check("activity before window accrues fully",
                DayAttribution.scrollingMsForWindow(10_000, 40_000, 5_000, 45_000), 30_000);
        // 10. Orphan recovery input: closing at lastSeen bounds the session
        //     to the heartbeat instead of "now" (90 s, not hours).
        checkSplit("orphan closed at lastSeen (90s, not inflated)",
                ts(2026, 10, 20, 22, 0), ts(2026, 10, 20, 22, 0) + 90_000,
                new String[]{"2026-10-20"}, new long[]{90_000});

        if (failures > 0) {
            System.out.println("DAY ATTRIBUTION TESTS FAILED: " + failures);
            System.exit(1);
        }
        System.out.println("DAY ATTRIBUTION TESTS PASSED");
    }
}
