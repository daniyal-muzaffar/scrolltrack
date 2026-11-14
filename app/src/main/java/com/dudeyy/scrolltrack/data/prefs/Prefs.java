package com.dudeyy.scrolltrack.data.prefs;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/**
 * All user preferences. Limits, warnings and reminders are optional and
 * disabled by default — the app counts and reports fully without them.
 */
public class Prefs {
    public static final int NO_LIMIT = -1;
    public static final int REMINDER_OFF = -1;

    private static final String NAME = "scrolltrack_prefs";
    private final SharedPreferences sp;

    public interface Listener { void onPrefsChanged(); }
    private final Set<Listener> listeners = new HashSet<>();

    private static volatile Prefs instance;
    public static Prefs get(Context ctx) {
        if (instance == null) {
            synchronized (Prefs.class) {
                if (instance == null) instance = new Prefs(ctx.getApplicationContext());
            }
        }
        return instance;
    }

    private Prefs(Context ctx) {
        sp = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE);
        sp.registerOnSharedPreferenceChangeListener((s, k) -> {
            for (Listener l : new HashSet<>(listeners)) l.onPrefsChanged();
        });
    }

    public void addListener(Listener l) { listeners.add(l); }
    public void removeListener(Listener l) { listeners.remove(l); }

    public boolean isOnboardingDone() { return sp.getBoolean("onboarding_done", false); }
    public void setOnboardingDone(boolean v) { sp.edit().putBoolean("onboarding_done", v).apply(); }

    public boolean isTrackingEnabled() { return sp.getBoolean("tracking_enabled", true); }
    public void setTrackingEnabled(boolean v) { sp.edit().putBoolean("tracking_enabled", v).apply(); }

    /** Floating counter pill. Off by default — purely opt-in. */
    public boolean isOverlayPillEnabled() { return sp.getBoolean("overlay_pill", false); }
    public void setOverlayPillEnabled(boolean v) { sp.edit().putBoolean("overlay_pill", v).apply(); }
    public int getOverlayX(int def) { return sp.getInt("overlay_x", def); }
    public void setOverlayX(int v) { sp.edit().putInt("overlay_x", v).apply(); }
    public int getOverlayY(int def) { return sp.getInt("overlay_y", def); }
    public void setOverlayY(int v) { sp.edit().putInt("overlay_y", v).apply(); }

    /** null = no limit (default) */
    public Integer getDailyTarget() {
        int v = sp.getInt("daily_target", NO_LIMIT);
        return v == NO_LIMIT ? null : v;
    }
    public void setDailyTarget(Integer v) {
        sp.edit().putInt("daily_target", v == null ? NO_LIMIT : v).apply();
    }

    public boolean isWarn50() { return sp.getBoolean("warn_50", true); }
    public void setWarn50(boolean v) { sp.edit().putBoolean("warn_50", v).apply(); }
    public boolean isWarn80() { return sp.getBoolean("warn_80", true); }
    public void setWarn80(boolean v) { sp.edit().putBoolean("warn_80", v).apply(); }
    public boolean isWarn100() { return sp.getBoolean("warn_100", true); }
    public void setWarn100(boolean v) { sp.edit().putBoolean("warn_100", v).apply(); }

    public boolean isNotificationsEnabled() { return sp.getBoolean("notifications_enabled", true); }
    public void setNotificationsEnabled(boolean v) { sp.edit().putBoolean("notifications_enabled", v).apply(); }

    /** null = disabled (default) */
    public Integer getSessionReminderMin() {
        int v = sp.getInt("session_reminder_min", REMINDER_OFF);
        return v == REMINDER_OFF ? null : v;
    }
    public void setSessionReminderMin(Integer v) {
        sp.edit().putInt("session_reminder_min", v == null ? REMINDER_OFF : v).apply();
    }

    public boolean isQuietHoursEnabled() { return sp.getBoolean("quiet_hours_enabled", false); }
    public void setQuietHoursEnabled(boolean v) { sp.edit().putBoolean("quiet_hours_enabled", v).apply(); }
    public int getQuietStartMin() { return sp.getInt("quiet_start_min", 23 * 60); }
    public int getQuietEndMin() { return sp.getInt("quiet_end_min", 7 * 60); }
    public void setQuietRange(int startMin, int endMin) {
        sp.edit().putInt("quiet_start_min", startMin).putInt("quiet_end_min", endMin).apply();
    }

    /** "system" (default), "dark", "light" */
    public String getTheme() { return sp.getString("theme", "system"); }

    /** In-app updates. Auto-check is on by default; the check is a plain GET
     *  of a static version file, at most once a day. */
    public boolean isAutoUpdateCheck() { return sp.getBoolean("auto_update_check", true); }
    public void setAutoUpdateCheck(boolean v) { sp.edit().putBoolean("auto_update_check", v).apply(); }
    public long getLastUpdateCheckMs() { return sp.getLong("last_update_check_ms", 0); }
    public void setLastUpdateCheckMs(long v) { sp.edit().putLong("last_update_check_ms", v).apply(); }
    public int getDismissedUpdateCode() { return sp.getInt("dismissed_update_code", 0); }
    public void setDismissedUpdateCode(int v) { sp.edit().putInt("dismissed_update_code", v).apply(); }
    public void setTheme(String v) { sp.edit().putString("theme", v).apply(); }
}
