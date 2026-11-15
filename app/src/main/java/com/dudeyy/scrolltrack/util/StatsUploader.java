package com.dudeyy.scrolltrack.util;

import android.content.Context;

import com.dudeyy.scrolltrack.data.prefs.Prefs;
import com.dudeyy.scrolltrack.data.repo.StatsRepository;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Android side of the opt-in anonymous usage stats (v1.5.0).
 *
 * The toggle in Settings ("Share anonymous usage stats") is off by
 * default. When on, the app POSTs one small JSON row per day to the
 * stats endpoint — a random install ID, the date, and that day's
 * recorded swipe total and Instagram minutes. Nothing else. The server
 * upserts by (install_id, day), so re-sending a day just refreshes it.
 *
 * Failures are silent: stats must never nag the user, and a failed
 * upload is simply retried on the next launch when the totals changed.
 * Turning the toggle off stops uploads immediately; nothing is queued
 * or sent afterwards.
 */
public final class StatsUploader {
    private StatsUploader() {}

    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 8_000;

    /** Launch hook: uploads today's totals when the user opted in and
     *  they changed since the last upload. No-op when the endpoint is
     *  not configured (no backend chosen yet) or the toggle is off. */
    public static void maybeUploadOnLaunch(Context ctx) {
        if (!AnonymousStats.isEndpointConfigured()) return;
        if (!Prefs.get(ctx).isAnonymousStatsEnabled()) return;
        uploadNow(ctx.getApplicationContext());
    }

    /** Uploads today's totals right now (used when the toggle is
     *  switched on, and on launch). Runs on a worker thread. */
    public static void uploadNow(Context ctx) {
        if (!AnonymousStats.isEndpointConfigured()) return;
        final Context appCtx = ctx.getApplicationContext();
        new Thread(() -> {
            try {
                Prefs prefs = Prefs.get(appCtx);
                if (!prefs.isAnonymousStatsEnabled()) return;
                StatsRepository.TodaySummary today =
                        StatsRepository.get(appCtx).loadToday();
                String day = today.day;
                int minutes = (int) (today.totalDurationMs / 60000);
                if (!AnonymousStats.shouldUpload(true,
                        prefs.getLastStatsUploadDay(),
                        prefs.getLastStatsUploadSwipes(),
                        prefs.getLastStatsUploadMinutes(),
                        day, today.swipes, minutes)) return;
                AnonymousStats.StatsPayload payload = AnonymousStats.buildPayload(
                        prefs.getInstallId(), day, today.swipes, minutes,
                        AppUpdater.installedVersionName(appCtx));
                if (payload == null) return;
                if (post(AnonymousStats.ENDPOINT, AnonymousStats.toJson(payload))) {
                    prefs.setLastStatsUpload(day, today.swipes, minutes);
                }
            } catch (Exception ignored) {
                // Stats must never crash or nag the user.
            }
        }).start();
    }

    /** POSTs the JSON body; true only on a 2xx response. The Supabase
     *  anon key is sent as apikey + Authorization (it grants nothing
     *  but EXECUTE on the RPCs). */
    static boolean post(String endpoint, String json) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(endpoint).openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            if (AnonymousStats.ANON_KEY != null && !AnonymousStats.ANON_KEY.isEmpty()) {
                conn.setRequestProperty("apikey", AnonymousStats.ANON_KEY);
                conn.setRequestProperty("Authorization", "Bearer " + AnonymousStats.ANON_KEY);
            }
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(body.length);
            try (OutputStream out = conn.getOutputStream()) {
                out.write(body);
            }
            int code = conn.getResponseCode();
            return code >= 200 && code < 300;
        } catch (Exception ignored) {
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
