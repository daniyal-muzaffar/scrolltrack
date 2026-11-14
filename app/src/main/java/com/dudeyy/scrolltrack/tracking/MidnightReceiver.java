package com.dudeyy.scrolltrack.tracking;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.dudeyy.scrolltrack.data.prefs.Prefs;

/** Fires at local midnight so day aggregates roll over even if the app is idle. */
public class MidnightReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        final PendingResult pending = goAsync();
        new Thread(() -> {
            try {
                // Always reschedule the next midnight, even if tracking is off.
                TrackingService.scheduleMidnightAlarm(context);
                if (Prefs.get(context).isTrackingEnabled()) {
                    Intent rollover = new Intent(context, TrackingService.class);
                    rollover.setAction(TrackingService.ACTION_ROLLOVER);
                    try {
                        context.startService(rollover);
                    } catch (IllegalStateException ignored) { }
                }
            } finally {
                pending.finish();
            }
        }).start();
    }
}
