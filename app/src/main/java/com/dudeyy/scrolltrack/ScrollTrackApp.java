package com.dudeyy.scrolltrack;

import android.app.Application;

/**
 * Application entry point. Installs the crash handler first thing so even
 * startup crashes are captured to a log file the user can share.
 */
public class ScrollTrackApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        CrashHandler.install(this);
    }
}
