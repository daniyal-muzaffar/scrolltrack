package com.dudeyy.scrolltrack.util;

/**
 * Pure, JVM-testable core of the opt-in anonymous usage stats (v1.5.0).
 * No Android imports — the network call lives in {@link StatsUploader}.
 *
 * What is sent, and only when the user turns the toggle on in Settings
 * (off by default): one call per install per day to the submit_stats
 * RPC —
 *   {"p_install_id":"<uuid>","p_day":"2026-11-15","p_swipes":623,
 *    "p_minutes":151,"p_app_version":"1.5.0"}
 * No names, no session times, no content, no device identifiers. The
 * install ID is a random UUID generated on the phone; it cannot be linked
 * to a person or a device. The function upserts by (install_id, day), so
 * re-sending a day just refreshes that day's totals. The app never
 * touches the table directly: anon has no table access at all, only
 * EXECUTE on submit_stats (and on the aggregate stats_summary).
 *
 * Data honesty: only the totals the app actually recorded are sent, and
 * only for days the user had tracking on. A day with no data sends
 * swipes 0 / minutes 0 — never an estimate.
 */
public final class AnonymousStats {
    private AnonymousStats() {}

    /** Upload endpoint: the submit_stats RPC. The app never touches the
     *  table directly — anon has no table access, only EXECUTE on this
     *  function (and on the aggregate stats_summary). Project
     *  scrolltrack-stats (Mumbai, free tier), created 2026-11-15. */
    public static final String ENDPOINT =
            "https://acjlwdtsympfbbmqlgnv.supabase.co/rest/v1/rpc/submit_stats";

    /** Supabase anon public key. Public by design (it ships in every
     *  Supabase client app); it grants nothing but EXECUTE on the two
     *  RPCs. Never put the service_role key here. */
    public static final String ANON_KEY =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImFjamx3ZHRzeW1wZmJibXFsZ252Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTQ3MjYxNTYsImV4cCI6MjExMDMwMjE1Nn0.hoOhyGHUEO-J8o6mZ00TVKnXqUkxMzLlHvMSFfijKKE";

    public static final class StatsPayload {
        public final String installId;
        /** yyyy-MM-dd */
        public final String day;
        public final int swipes;
        public final int minutes;
        public final String appVersion;
        public StatsPayload(String installId, String day, int swipes,
                            int minutes, String appVersion) {
            this.installId = installId;
            this.day = day;
            this.swipes = swipes;
            this.minutes = minutes;
            this.appVersion = appVersion;
        }
    }

    public static boolean isEndpointConfigured() {
        return ENDPOINT != null && !ENDPOINT.isEmpty();
    }

    /** Validates the inputs; returns null when anything is unusable. */
    public static StatsPayload buildPayload(String installId, String day,
                                            int swipes, int minutes,
                                            String appVersion) {
        if (installId == null || installId.isEmpty()) return null;
        if (!isValidDay(day)) return null;
        if (swipes < 0 || minutes < 0) return null;
        if (appVersion == null || appVersion.isEmpty()) return null;
        return new StatsPayload(installId, day, swipes, minutes, appVersion);
    }

    /** yyyy-MM-dd, nothing else. */
    static boolean isValidDay(String day) {
        if (day == null || day.length() != 10) return false;
        if (day.charAt(4) != '-' || day.charAt(7) != '-') return false;
        for (int i = 0; i < 10; i++) {
            if (i == 4 || i == 7) continue;
            if (!Character.isDigit(day.charAt(i))) return false;
        }
        return true;
    }

    /** The exact JSON the uploader sends: the submit_stats RPC arguments.
     *  Field order is fixed so the payload is stable and easy to check
     *  in tests and on the server. */
    public static String toJson(StatsPayload p) {
        return "{\"p_install_id\":\"" + escape(p.installId)
                + "\",\"p_day\":\"" + escape(p.day)
                + "\",\"p_swipes\":" + p.swipes
                + ",\"p_minutes\":" + p.minutes
                + ",\"p_app_version\":\"" + escape(p.appVersion) + "\"}";
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** Upload again when opted in and (never uploaded for this day, or
     *  today's totals changed since the last upload). lastDay "" with
     *  lastSwipes/lastMinutes -1 means "never uploaded". */
    public static boolean shouldUpload(boolean optedIn,
                                       String lastDay, int lastSwipes, int lastMinutes,
                                       String today, int swipes, int minutes) {
        if (!optedIn) return false;
        if (lastDay == null || !lastDay.equals(today)) return true;
        return lastSwipes != swipes || lastMinutes != minutes;
    }
}
