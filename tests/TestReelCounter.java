import com.dudeyy.scrolltrack.util.ReelCounter;
import com.dudeyy.scrolltrack.util.ReelsDetection.Surface;

/**
 * JVM tests for the counting state machine, using simulated event sequences.
 * Run with run_tests.sh. Exits non-zero on the first failure.
 */
public class TestReelCounter {

    private static int failures = 0;

    private static ReelCounter.ScrollEvent ev(int from, int dy, int sy, long t, Surface s) {
        return new ReelCounter.ScrollEvent(from, from, -1, dy, sy, t, s);
    }

    private static void check(String name, int got, int want) {
        if (got != want) {
            System.out.println("FAIL " + name + ": got " + got + ", want " + want);
            failures++;
        } else {
            System.out.println("ok   " + name + " = " + got);
        }
    }

    /** Feeds events and flushes; returns total counted. */
    private static int run(ReelCounter c, ReelCounter.ScrollEvent... events) {
        int n = 0;
        long lastT = 0;
        for (ReelCounter.ScrollEvent e : events) {
            if (c.feed(e).counted) n++;
            lastT = e.eventTime;
        }
        if (c.flush(lastT + 5000).counted) n++;
        return n;
    }

    public static void main(String[] args) {
        // 1. One swipe producing 8 raw events -> exactly 1.
        {
            ReelCounter c = new ReelCounter();
            long t = 10000;
            check("one swipe, 8 raw events",
                    run(c,
                            ev(0, 0, 0, t, Surface.REELS),
                            ev(0, 5, 0, t + 40, Surface.REELS),
                            ev(0, 8, 0, t + 80, Surface.REELS),
                            ev(1, 9, 0, t + 120, Surface.REELS),
                            ev(1, 4, 0, t + 160, Surface.REELS),
                            ev(1, 2, 0, t + 200, Surface.REELS),
                            ev(1, 0, 0, t + 240, Surface.REELS),
                            ev(1, 0, 0, t + 280, Surface.REELS)),
                    1);
        }
        // 2. Ten fast swipes in a row -> 10 (350 ms apart, above the 300 ms floor).
        {
            ReelCounter c = new ReelCounter();
            long t = 20000;
            ReelCounter.ScrollEvent[] seq = new ReelCounter.ScrollEvent[31];
            seq[0] = ev(0, 0, 0, t, Surface.REELS);
            for (int i = 1; i <= 10; i++) {
                seq[3 * i - 2] = ev(i - 1, 6, 0, t + i * 350 - 60, Surface.REELS);
                seq[3 * i - 1] = ev(i, 8, 0, t + i * 350, Surface.REELS);
                seq[3 * i] = ev(i, 0, 0, t + i * 350 + 60, Surface.REELS);
            }
            check("10 fast swipes", run(c, seq), 10);
        }
        // 3. Bounce at the end of the list -> 0 (index never changes).
        {
            ReelCounter c = new ReelCounter();
            long t = 40000;
            check("bounce at end of list",
                    run(c,
                            ev(5, 0, 0, t, Surface.REELS),
                            ev(5, 12, 0, t + 50, Surface.REELS),
                            ev(5, -9, 0, t + 110, Surface.REELS),
                            ev(5, 6, 0, t + 170, Surface.REELS),
                            ev(5, -8, 0, t + 230, Surface.REELS),
                            ev(5, 0, 0, t + 290, Surface.REELS)),
                    0);
        }
        // 4. Swipe back down -> 0.
        {
            ReelCounter c = new ReelCounter();
            long t = 50000;
            check("swipe back down",
                    run(c,
                            ev(2, 0, 0, t, Surface.REELS),
                            ev(2, 4, 0, t + 40, Surface.REELS),
                            ev(1, -6, 0, t + 400, Surface.REELS),
                            ev(1, -3, 0, t + 440, Surface.REELS)),
                    0);
        }
        // 5. Feed / Stories / comments / DM scrolling -> 0.
        {
            ReelCounter c = new ReelCounter();
            long t = 60000;
            check("feed scrolling (not Reels)",
                    run(c,
                            ev(0, 9, 0, t, Surface.NOT_REELS),
                            ev(1, 9, 0, t + 50, Surface.NOT_REELS),
                            ev(2, 9, 0, t + 100, Surface.NOT_REELS),
                            ev(3, 9, 0, t + 150, Surface.NOT_REELS),
                            ev(4, 9, 0, t + 200, Surface.NOT_REELS),
                            ev(5, 9, 0, t + 250, Surface.NOT_REELS)),
                    0);
        }
        // 6. Surface unknown -> fallback counts, without double counting.
        {
            ReelCounter c = new ReelCounter();
            long t = 70000;
            check("fallback: one burst",
                    run(c,
                            ev(-1, 7, 0, t, Surface.UNKNOWN),
                            ev(-1, 8, 0, t + 60, Surface.UNKNOWN),
                            ev(-1, 6, 0, t + 120, Surface.UNKNOWN),
                            ev(-1, 3, 0, t + 180, Surface.UNKNOWN)),
                    1);
        }
        {
            ReelCounter c = new ReelCounter();
            long t = 80000;
            check("fallback: two separate bursts",
                    run(c,
                            ev(-1, 7, 0, t, Surface.UNKNOWN),
                            ev(-1, 8, 0, t + 60, Surface.UNKNOWN),
                            ev(-1, 6, 0, t + 120, Surface.UNKNOWN),
                            ev(-1, 7, 0, t + 2000, Surface.UNKNOWN),
                            ev(-1, 8, 0, t + 2060, Surface.UNKNOWN),
                            ev(-1, 5, 0, t + 2120, Surface.UNKNOWN)),
                    2);
        }
        {
            ReelCounter c = new ReelCounter();
            long t = 90000;
            check("fallback: lone event is noise",
                    run(c, ev(-1, 9, 0, t, Surface.UNKNOWN)), 0);
        }
        {
            ReelCounter c = new ReelCounter();
            long t = 100000;
            check("fallback: bounce burst (net zero with signal)",
                    run(c,
                            ev(-1, 10, 0, t, Surface.UNKNOWN),
                            ev(-1, -10, 0, t + 60, Surface.UNKNOWN),
                            ev(-1, 8, 0, t + 120, Surface.UNKNOWN),
                            ev(-1, -8, 0, t + 180, Surface.UNKNOWN)),
                    0);
        }
        {
            ReelCounter c = new ReelCounter();
            long t = 110000;
            check("fallback: two bursts 100ms apart merge into one",
                    run(c,
                            ev(-1, 7, 0, t, Surface.UNKNOWN),
                            ev(-1, 8, 0, t + 60, Surface.UNKNOWN),
                            ev(-1, 7, 0, t + 160, Surface.UNKNOWN),
                            ev(-1, 8, 0, t + 220, Surface.UNKNOWN)),
                    1);
        }
        // 7. Auto-advance to the next Reel (index step, no gesture burst) -> 1.
        {
            ReelCounter c = new ReelCounter();
            long t = 120000;
            check("auto-advance",
                    run(c,
                            ev(3, 0, 0, t, Surface.REELS),
                            ev(4, 0, 0, t + 3000, Surface.REELS)),
                    1);
        }
        // 8. Replay / loop (index unchanged) -> 0.
        {
            ReelCounter c = new ReelCounter();
            long t = 130000;
            check("replay (same page)",
                    run(c,
                            ev(4, 0, 0, t, Surface.REELS),
                            ev(4, 5, 0, t + 2000, Surface.REELS),
                            ev(4, 3, 0, t + 4000, Surface.REELS)),
                    0);
        }
        // 9. Reels surface without index data -> burst fallback still counts once.
        {
            ReelCounter c = new ReelCounter();
            long t = 140000;
            check("Reels, no index data",
                    run(c,
                            ev(-1, 7, 0, t, Surface.REELS),
                            ev(-1, 8, 0, t + 60, Surface.REELS),
                            ev(-1, 6, 0, t + 120, Surface.REELS)),
                    1);
        }
        // 10. Two advances 150 ms apart -> second dropped (debounce); third counts.
        {
            ReelCounter c = new ReelCounter();
            long t = 150000;
            check("debounce: 150ms-apart advances",
                    run(c,
                            ev(0, 0, 0, t, Surface.REELS),
                            ev(1, 8, 0, t + 350, Surface.REELS),
                            ev(2, 8, 0, t + 500, Surface.REELS),
                            ev(3, 8, 0, t + 900, Surface.REELS)),
                    2);
        }
        // 11. Surface change mid-stream resets the anchor; feed after leaving
        //     Reels never counts, and returning to Reels re-anchors silently.
        {
            ReelCounter c = new ReelCounter();
            long t = 160000;
            check("leave and return to Reels",
                    run(c,
                            ev(0, 0, 0, t, Surface.REELS),
                            ev(1, 8, 0, t + 350, Surface.REELS),
                            ev(1, 9, 0, t + 1000, Surface.NOT_REELS),
                            ev(2, 9, 0, t + 1050, Surface.NOT_REELS),
                            ev(7, 0, 0, t + 5000, Surface.REELS),
                            ev(8, 8, 0, t + 5350, Surface.REELS)),
                    2);
        }

        if (failures > 0) {
            System.out.println("REEL COUNTER TESTS FAILED: " + failures);
            System.exit(1);
        }
        System.out.println("REEL COUNTER TESTS PASSED");
    }
}
