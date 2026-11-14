package com.dudeyy.scrolltrack.ui;

import android.app.Activity;

/** Navigation actions a screen can request from MainActivity. */
public interface ScreenHost {
    int TAB_DASHBOARD = 0;
    int TAB_HISTORY = 1;
    int TAB_LIFE_IMPACT = 2;
    int TAB_LIMITS = 3;
    int TAB_SETTINGS = 4;

    void goToTab(int tab);
    Activity activityContext();
    void requestNotificationPermission();
}
