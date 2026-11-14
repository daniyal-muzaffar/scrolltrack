package com.dudeyy.scrolltrack;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.util.Date;

/**
 * Writes uncaught exceptions to a log file the user can share, then chains to
 * the previous handler. Installed first thing in Application.onCreate so even
 * startup crashes are captured.
 *
 * Log location (user-accessible): Android/data/com.dudeyy.scrolltrack/files/scrolltrack-crash.log
 */
public final class CrashHandler implements Thread.UncaughtExceptionHandler {
    private static final String FILE_NAME = "scrolltrack-crash.log";

    private final Context app;
    private final Thread.UncaughtExceptionHandler previous;

    public static void install(Context context) {
        try {
            Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
            if (prev instanceof CrashHandler) return;
            Thread.setDefaultUncaughtExceptionHandler(
                    new CrashHandler(context.getApplicationContext(), prev));
        } catch (Exception ignored) { }
    }

    private CrashHandler(Context app, Thread.UncaughtExceptionHandler previous) {
        this.app = app;
        this.previous = previous;
    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        try {
            StringBuilder sb = new StringBuilder(4096);
            sb.append("=== ").append(new Date())
                    .append(" | thread=").append(thread.getName())
                    .append(" | app=com.dudeyy.scrolltrack ===\n");
            sb.append(Log.getStackTraceString(throwable)).append("\n\n");
            String entry = sb.toString();
            appendTo(new File(app.getExternalFilesDir(null), FILE_NAME), entry);
            appendTo(new File(app.getFilesDir(), FILE_NAME), entry);
        } catch (Exception ignored) { }
        try {
            if (previous != null) {
                previous.uncaughtException(thread, throwable);
            } else {
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(10);
            }
        } catch (Exception ignored) { }
    }

    private static void appendTo(File file, String text) {
        try {
            File parent = file.getParentFile();
            if (parent != null) parent.mkdirs();
            FileWriter w = new FileWriter(file, true);
            w.write(text);
            w.close();
        } catch (Exception ignored) { }
    }
}
