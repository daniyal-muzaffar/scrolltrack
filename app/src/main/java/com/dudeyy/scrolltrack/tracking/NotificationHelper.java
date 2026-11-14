package com.dudeyy.scrolltrack.tracking;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.dudeyy.scrolltrack.MainActivity;
import com.dudeyy.scrolltrack.R;

/**
 * Builds the notification channel and the foreground-service, threshold and
 * reminder notifications. All notifications are opt-in through Settings.
 */
public final class NotificationHelper {
    private NotificationHelper() {}

    public static final String CHANNEL_TRACKING = "scrolltrack_status";
    public static final String CHANNEL_WELLBEING = "scrolltrack_wellbeing";
    public static final String CHANNEL_RESUME = "scrolltrack_resume";
    public static final int NOTIF_ID_FOREGROUND = 1001;
    public static final int NOTIF_ID_RESUME = 1002;

    public static void ensureChannels(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel tracking = new NotificationChannel(
                CHANNEL_TRACKING, "Tracking status", NotificationManager.IMPORTANCE_LOW);
        tracking.setDescription("Ongoing notification while ScrollTrack monitoring is on");
        NotificationChannel wellbeing = new NotificationChannel(
                CHANNEL_WELLBEING, "Wellbeing alerts", NotificationManager.IMPORTANCE_DEFAULT);
        wellbeing.setDescription("Daily limit and session reminders");
        NotificationChannel resume = new NotificationChannel(
                CHANNEL_RESUME, "Tracking paused", NotificationManager.IMPORTANCE_DEFAULT);
        resume.setDescription("Reminds you to resume tracking after a reboot");
        nm.createNotificationChannels(java.util.Arrays.asList(tracking, wellbeing, resume));
    }

    private static PendingIntent contentIntent(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        return PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static Notification foregroundNotification(Context context, String statusLine, boolean paused) {
        Intent toggle = new Intent(context, TrackingService.class);
        toggle.setAction(paused ? TrackingService.ACTION_RESUME : TrackingService.ACTION_PAUSE);
        PendingIntent togglePending = PendingIntent.getService(context, 1, toggle,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = new Notification.Builder(context, CHANNEL_TRACKING)
                .setSmallIcon(R.drawable.ic_stat_scroll)
                .setContentTitle("ScrollTrack")
                .setContentText(statusLine)
                .setContentIntent(contentIntent(context))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(new Notification.Action.Builder(
                        android.graphics.drawable.Icon.createWithResource(context, R.drawable.ic_stat_scroll),
                        paused ? "Resume" : "Pause", togglePending).build());
        return b.build();
    }

    public static Notification thresholdNotification(Context context, int percent, int swipes, int target) {
        return new Notification.Builder(context, CHANNEL_WELLBEING)
                .setSmallIcon(R.drawable.ic_stat_scroll)
                .setContentTitle("Daily scroll target: " + percent + "%")
                .setContentText("You've logged " + swipes + " of " + target + " swipes today.")
                .setContentIntent(contentIntent(context))
                .setAutoCancel(true)
                .build();
    }

    public static Notification sessionReminderNotification(Context context, int minutes) {
        return new Notification.Builder(context, CHANNEL_WELLBEING)
                .setSmallIcon(R.drawable.ic_stat_scroll)
                .setContentTitle("Still scrolling?")
                .setContentText("This Instagram session has lasted " + minutes + " minutes.")
                .setContentIntent(contentIntent(context))
                .setAutoCancel(true)
                .build();
    }

    public static Notification resumeNeededNotification(Context context) {
        return new Notification.Builder(context, CHANNEL_RESUME)
                .setSmallIcon(R.drawable.ic_stat_scroll)
                .setContentTitle("ScrollTrack paused after reboot")
                .setContentText("Tap to resume scroll tracking.")
                .setContentIntent(contentIntent(context))
                .setAutoCancel(true)
                .build();
    }

    public static Notification stoppedNotification(Context context) {
        return new Notification.Builder(context, CHANNEL_RESUME)
                .setSmallIcon(R.drawable.ic_stat_scroll)
                .setContentTitle("ScrollTrack isn't running")
                .setContentText("Tap to resume scroll tracking.")
                .setContentIntent(contentIntent(context))
                .setAutoCancel(true)
                .build();
    }
}
