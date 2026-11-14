package com.dudeyy.scrolltrack.util;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Lightweight debug log for tuning the counting and time fixes against a real
 * Instagram version. Written to the app's files dir
 * ({@code Android/data/com.dudeyy.scrolltrack/files/scrolltrack-debug.log}).
 *
 * Only structural facts are logged: event types, surface verdicts, count
 * decisions with reasons, session open/close reasons, and aggregate
 * durations. It never logs node text, usernames, messages, package names
 * other than Instagram, or anything personal.
 *
 * All ScrollTrack builds are debug-signed; set {@link #ENABLED} to false to
 * silence the file log (Logcat lines still go out at debug level).
 */
public final class DebugLog {

    public static final boolean ENABLED = true;

    private static final String FILE_NAME = "scrolltrack-debug.log";
    private static final long MAX_BYTES = 200 * 1024; // rotate by truncating

    private DebugLog() {}

    public static synchronized void log(Context ctx, String tag, String message) {
        String line = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date())
                + " " + tag + ": " + message;
        Log.d("ScrollTrack", line);
        if (!ENABLED || ctx == null) return;
        try {
            File f = new File(ctx.getFilesDir(), FILE_NAME);
            if (f.exists() && f.length() > MAX_BYTES) {
                if (!f.delete()) return;
            }
            FileOutputStream out = new FileOutputStream(f, true);
            try {
                out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
            } finally {
                out.close();
            }
        } catch (IOException ignored) { }
    }
}
