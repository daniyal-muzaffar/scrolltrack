package com.dudeyy.scrolltrack.tracking;

/** The visible tracking state machine. Never claims "active" unless the service is truly running. */
public enum TrackingState {
    /** Instagram is in the foreground and an open session exists. */
    ACTIVE,
    /** Permissions granted and tracking enabled, but Instagram is not in the foreground. */
    WAITING,
    /** Usage Access permission missing (or revoked). */
    PERMISSION_REQUIRED,
    /** User turned tracking off in settings. */
    PAUSED,
    /** Instagram is not installed on this device. */
    UNAVAILABLE;

    public boolean isHealthy() { return this == ACTIVE || this == WAITING; }
}
