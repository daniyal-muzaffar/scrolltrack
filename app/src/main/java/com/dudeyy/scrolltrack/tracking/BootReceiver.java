package com.dudeyy.scrolltrack.tracking;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.dudeyy.scrolltrack.data.prefs.Prefs;

/**
 * Restores tracking after a reboot when the user had it enabled. On Android 12+
 * a background start may be rejected; in that case we post a tap-to-resume
 * notification instead of failing silently.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent != null ? intent.getAction() : "";
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)) return;
        final PendingResult pending = goAsync();
        new Thread(() -> {
            try {
                Prefs prefs = Prefs.get(context);
                if (!prefs.isTrackingEnabled()) return;
                if (!PermissionChecks.isInstagramInstalled(context)) return;
                try {
                    TrackingService.start(context);
                } catch (Exception e) {
                    boolean blocked = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                            && e.getClass().getSimpleName().equals("ForegroundServiceStartNotAllowedException");
                    if (blocked && PermissionChecks.areNotificationsGranted(context)) {
                        NotificationHelper.ensureChannels(context);
                        NotificationManager nm =
                                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                        nm.notify(NotificationHelper.NOTIF_ID_RESUME,
                                NotificationHelper.resumeNeededNotification(context));
                    }
                } finally {
                    TrackingService.scheduleMidnightAlarm(context);
                }
            } finally {
                pending.finish();
            }
        }).start();
    }
}
