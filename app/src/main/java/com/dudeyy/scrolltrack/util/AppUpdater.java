package com.dudeyy.scrolltrack.util;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.dudeyy.scrolltrack.data.prefs.Prefs;
import com.dudeyy.scrolltrack.ui.Theme;
import com.dudeyy.scrolltrack.ui.components.UiKit;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * In-app updater (added 2026-11-14 at the user's request, reversing the
 * earlier "no in-app updater, manual sideloads only" rule).
 *
 * How it works: on launch (at most once a day, only when auto-check is on)
 * the app fetches a tiny version.json from the update URL. If it names a
 * higher versionCode than the installed build, a dialog offers Update /
 * Later. Update downloads the APK into the app cache and hands it to the
 * system installer, which always asks the user — installs can never be
 * silent. The update is signed with the same key as the installed build,
 * so history, records and settings survive the upgrade.
 *
 * Privacy: the check is a plain GET of a static file; nothing about the
 * user or device is sent. No analytics, no accounts.
 */
public final class AppUpdater {
    private AppUpdater() {}

    /** Version file. Format: {"versionCode":17,"versionName":"1.4.0",
     *  "apkUrl":"https://...","notes":"..."} — hosted on GitHub. */
    public static final String UPDATE_URL =
            "https://raw.githubusercontent.com/daniyal-muzaffar/scrolltrack/main/version.json";

    public static final long CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000;
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 8_000;
    private static final int MAX_JSON_BYTES = 64 * 1024;

        // ---------- Android side ----------

    public interface CheckCallback {
        /** info is null when there is no newer version; reachable is false
         *  when the server could not be reached or the file was unusable. */
        void onChecked(UpdateCheck.UpdateInfo info, boolean reachable);
    }

    public static int installedVersionCode(Context ctx) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return (int) ctx.getPackageManager()
                        .getPackageInfo(ctx.getPackageName(), 0).getLongVersionCode();
            }
            @SuppressWarnings("deprecation")
            int code = ctx.getPackageManager()
                    .getPackageInfo(ctx.getPackageName(), 0).versionCode;
            return code;
        } catch (Exception e) {
            return 0;
        }
    }

    /** Automatic launch check: throttled to once a day, honors the auto toggle
     *  and the version the user dismissed with Later. */
    public static void maybeCheckOnLaunch(Activity activity) {
        Prefs prefs = Prefs.get(activity);
        if (!prefs.isAutoUpdateCheck()) return;
        long now = System.currentTimeMillis();
        if (now - prefs.getLastUpdateCheckMs() < CHECK_INTERVAL_MS) return;
        checkForUpdates(activity, false, (info, reachable) -> {
            if (!reachable || info == null) return;
            if (info.versionCode == prefs.getDismissedUpdateCode()) return;
            promptUpdate(activity, info);
        });
    }

    /** Fetches version.json on a worker thread; result arrives on the main thread. */
    public static void checkForUpdates(Context ctx, boolean manual, CheckCallback cb) {
        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            UpdateCheck.UpdateInfo info = null;
            boolean reachable = false;
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(UPDATE_URL).openConnection();
                conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
                conn.setReadTimeout(READ_TIMEOUT_MS);
                conn.setRequestMethod("GET");
                if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    String body = readUpTo(conn.getInputStream(), MAX_JSON_BYTES);
                    info = UpdateCheck.parseVersionJson(body);
                    reachable = info != null;
                }
            } catch (Exception ignored) {
            } finally {
                if (conn != null) conn.disconnect();
            }
            if (!manual) Prefs.get(ctx).setLastUpdateCheckMs(System.currentTimeMillis());
            final UpdateCheck.UpdateInfo result = UpdateCheck.isNewer(info, installedVersionCode(ctx)) ? info : null;
            final boolean ok = reachable;
            main.post(() -> cb.onChecked(result, ok));
        }).start();
    }

    private static String readUpTo(InputStream in, int maxBytes) throws Exception {
        Reader r = new InputStreamReader(new BufferedInputStream(in), StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[4096];
        int n;
        while ((n = r.read(buf)) != -1) {
            if (sb.length() + n > maxBytes) break;
            sb.append(buf, 0, n);
        }
        return sb.toString();
    }

    /** "Update available" dialog. Later records the version so it is not
     *  offered again; a manual check still shows it. */
    public static void promptUpdate(Activity activity, UpdateCheck.UpdateInfo info) {
        String msg = "ScrollTrack " + info.versionName + " is available."
                + (info.notes.isEmpty() ? "" : "\n\n" + info.notes)
                + "\n\nYour history, records and settings stay exactly as they are.";
        AlertDialog d = UiKit.dialogBuilder(activity)
                .setTitle("Update available")
                .setMessage(msg)
                .setPositiveButton("Update", (di, w) -> downloadAndInstall(activity, info))
                .setNegativeButton("Later", (di, w) ->
                        Prefs.get(activity).setDismissedUpdateCode(info.versionCode))
                .create();
        d.show();
        UiKit.styleDialogButtons(activity, d);
    }

    /** Downloads the APK to the app cache, then hands it to the system
     *  installer (which always asks the user). */
    public static void downloadAndInstall(Activity activity, UpdateCheck.UpdateInfo info) {
        Context ctx = activity.getApplicationContext();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !ctx.getPackageManager().canRequestPackageInstalls()) {
            Toast.makeText(ctx, "Allow ScrollTrack to install apps, then tap Update again.",
                    Toast.LENGTH_LONG).show();
            try {
                Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + ctx.getPackageName()));
                activity.startActivity(i);
            } catch (Exception ignored) { }
            return;
        }

        // Progress dialog (custom view, no deprecated ProgressDialog).
        LinearLayout body = new LinearLayout(ctx);
        body.setOrientation(LinearLayout.VERTICAL);
        int p = Theme.dp(ctx, 20);
        body.setPadding(p, p, p, p);
        ProgressBar bar = new ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        body.addView(bar);
        TextView pct = UiKit.tv(ctx, "Starting download…", 13, Theme.text2(ctx), Theme.body(ctx));
        body.addView(pct);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        AlertDialog progress = UiKit.dialogBuilder(activity)
                .setTitle("Downloading update")
                .setView(body)
                .setNegativeButton("Cancel", (di, w) -> cancelled.set(true))
                .create();
        progress.setCancelable(false);
        progress.show();
        UiKit.styleDialogButtons(activity, progress);

        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            File out = new File(ctx.getCacheDir(),
                    "scrolltrack-update-" + info.versionCode + ".apk");
            deleteStaleUpdates(ctx, info.versionCode);
            boolean ok = false;
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(info.apkUrl).openConnection();
                conn.setConnectTimeout(10_000);
                conn.setReadTimeout(30_000);
                conn.setRequestMethod("GET");
                int total = conn.getContentLength();
                if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    try (InputStream in = new BufferedInputStream(conn.getInputStream());
                         FileOutputStream fos = new FileOutputStream(out)) {
                        byte[] buf = new byte[8192];
                        int n;
                        long done = 0;
                        while ((n = in.read(buf)) != -1) {
                            if (cancelled.get()) break;
                            fos.write(buf, 0, n);
                            done += n;
                            if (total > 0) {
                                int percent = (int) (done * 100 / total);
                                main.post(() -> {
                                    bar.setProgress(percent);
                                    pct.setText("Downloading… " + percent + "%");
                                });
                            }
                        }
                        ok = looksLikeApk(out);
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (conn != null) conn.disconnect();
            }
            final boolean success = ok && !cancelled.get();
            main.post(() -> {
                progress.dismiss();
                if (!success) {
                    if (!cancelled.get()) {
                        Toast.makeText(ctx, "Download failed — check your connection and try again.",
                                Toast.LENGTH_LONG).show();
                    }
                    //noinspection ResultOfMethodCallIgnored
                    out.delete();
                    return;
                }
                installApk(activity, out);
            });
        }).start();
    }

    /** A real APK is a zip: it starts with the "PK" magic. Anything else
     *  (e.g. an HTML error page from the host) is rejected. */
    static boolean looksLikeApk(File f) {
        if (f == null || !f.exists() || f.length() < 4) return false;
        try (InputStream in = new BufferedInputStream(new java.io.FileInputStream(f))) {
            return in.read() == 'P' && in.read() == 'K';
        } catch (Exception e) {
            return false;
        }
    }

    private static void deleteStaleUpdates(Context ctx, int keepCode) {
        File[] files = ctx.getCacheDir().listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.getName().startsWith("scrolltrack-update-")
                    && !f.getName().equals("scrolltrack-update-" + keepCode + ".apk")) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        }
    }

    private static void installApk(Activity activity, File apk) {
        Uri uri = Uri.parse("content://com.dudeyy.scrolltrack.csv/" + apk.getName());
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(uri, "application/vnd.android.package-archive");
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            activity.startActivity(i);
        } catch (Exception e) {
            Toast.makeText(activity, "Couldn't open the installer — try again.",
                    Toast.LENGTH_LONG).show();
        }
    }
}
