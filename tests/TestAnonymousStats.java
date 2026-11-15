import com.dudeyy.scrolltrack.util.AnonymousStats;

/**
 * JVM tests for the opt-in anonymous stats core (v1.5.0).
 * The endpoint is the live Supabase project (scrolltrack-stats);
 * isEndpointConfigured() must be true, and the URL must carry the
 * on_conflict upsert parameter the uploader relies on.
 */
public class TestAnonymousStats {
    private static int passed = 0;

    private static void check(boolean cond, String name) {
        if (!cond) throw new AssertionError("FAILED: " + name);
        passed++;
        System.out.println("  ok: " + name);
    }

    public static void main(String[] args) {
        System.out.println("== AnonymousStats payload ==");
        AnonymousStats.StatsPayload p = AnonymousStats.buildPayload(
                "11111111-2222-3333-4444-555555555555", "2026-11-15", 623, 151, "1.5.0");
        check(p != null, "valid payload builds");
        check(p.installId.equals("11111111-2222-3333-4444-555555555555"), "install id kept");
        check(p.day.equals("2026-11-15") && p.swipes == 623 && p.minutes == 151
                && p.appVersion.equals("1.5.0"), "fields kept");

        check(AnonymousStats.toJson(p).equals(
                "{\"p_install_id\":\"11111111-2222-3333-4444-555555555555\",\"p_day\":\"2026-11-15\","
                + "\"p_swipes\":623,\"p_minutes\":151,\"p_app_version\":\"1.5.0\"}"),
                "exact JSON shape (submit_stats RPC arguments)");

        System.out.println("== buildPayload rejects bad input ==");
        check(AnonymousStats.buildPayload(null, "2026-11-15", 1, 1, "1.5.0") == null, "null install id rejected");
        check(AnonymousStats.buildPayload("", "2026-11-15", 1, 1, "1.5.0") == null, "empty install id rejected");
        check(AnonymousStats.buildPayload("x", "2026-1-5", 1, 1, "1.5.0") == null, "short day rejected");
        check(AnonymousStats.buildPayload("x", "20261115", 1, 1, "1.5.0") == null, "undashed day rejected");
        check(AnonymousStats.buildPayload("x", "2026/11/15", 1, 1, "1.5.0") == null, "slashed day rejected");
        check(AnonymousStats.buildPayload("x", null, 1, 1, "1.5.0") == null, "null day rejected");
        check(AnonymousStats.buildPayload("x", "2026-11-15", -1, 1, "1.5.0") == null, "negative swipes rejected");
        check(AnonymousStats.buildPayload("x", "2026-11-15", 1, -1, "1.5.0") == null, "negative minutes rejected");
        check(AnonymousStats.buildPayload("x", "2026-11-15", 1, 1, "") == null, "empty version rejected");
        check(AnonymousStats.buildPayload("x", "2026-11-15", 0, 0, "1.5.0") != null, "zero totals are valid (a real empty day)");

        System.out.println("== shouldUpload decision ==");
        check(!AnonymousStats.shouldUpload(false, "", -1, -1, "2026-11-15", 10, 5), "opted out never uploads");
        check(AnonymousStats.shouldUpload(true, "", -1, -1, "2026-11-15", 10, 5), "opted in, never uploaded -> upload");
        check(!AnonymousStats.shouldUpload(true, "2026-11-15", 10, 5, "2026-11-15", 10, 5), "same day, same totals -> skip");
        check(AnonymousStats.shouldUpload(true, "2026-11-15", 10, 5, "2026-11-15", 12, 5), "same day, more swipes -> upload");
        check(AnonymousStats.shouldUpload(true, "2026-11-15", 10, 5, "2026-11-15", 10, 6), "same day, more minutes -> upload");
        check(AnonymousStats.shouldUpload(true, "2026-11-14", 900, 200, "2026-11-15", 0, 0), "new day -> upload");
        check(AnonymousStats.shouldUpload(true, null, -1, -1, "2026-11-15", 0, 0), "null last day -> upload");

        System.out.println("== endpoint configured ==");
        check(AnonymousStats.isEndpointConfigured(), "endpoint configured (Supabase project live)");
        check(AnonymousStats.ENDPOINT.equals(
                "https://acjlwdtsympfbbmqlgnv.supabase.co/rest/v1/rpc/submit_stats"),
                "endpoint is the submit_stats RPC, not the table");
        check(AnonymousStats.ANON_KEY != null && !AnonymousStats.ANON_KEY.isEmpty(),
                "anon key present");

        System.out.println("ANONYMOUS STATS TESTS PASSED (" + passed + " checks)");
    }
}
