package com.dudeyy.scrolltrack.util;

/**
 * Single place for everything that identifies the Instagram Reels surface.
 *
 * Instagram changes its view structure between versions, so detection uses a
 * small set of indicators with a graceful fallback — never one hardcoded ID.
 * If a future Instagram update breaks detection, update the hints here only.
 *
 * Privacy: detection uses view IDs, class names and the static "Reels" tab
 * label only. It never reads node text, usernames, messages or any content,
 * and never stores or logs what it sees in the tree.
 */
public final class ReelsDetection {

    private ReelsDetection() {}

    /** What the surface check concluded. */
    public enum Surface {
        /** On the Reels viewer. */
        REELS,
        /** The tree is visible and this is not Reels (feed, Stories, DMs, ...). */
        NOT_REELS,
        /** Could not inspect the tree (no root, service not ready) — caller falls back. */
        UNKNOWN
    }

    /** Resource-id substrings (lowercase) that identify the Reels viewer pager. */
    public static final String[] PAGER_ID_HINTS = {
            "clips_viewer",
            "reels_viewer",
            "reel_viewer",
            "clipsviewer",
            "reelsviewer"
    };

    /** Class-name hint for pager containers (used only together with an id hint). */
    public static final String VIEWPAGER_CLASS_HINT = "viewpager";

    /** Content-description of the bottom-nav Reels tab (matched case-insensitively). */
    public static final String REELS_TAB_DESCRIPTION = "reels";

    /** Intent-extra codes for {@link Surface}. */
    public static final int CODE_UNKNOWN = 0;
    public static final int CODE_REELS = 1;
    public static final int CODE_NOT_REELS = 2;

    public static int codeOf(Surface s) {
        switch (s) {
            case REELS: return CODE_REELS;
            case NOT_REELS: return CODE_NOT_REELS;
            default: return CODE_UNKNOWN;
        }
    }

    public static Surface fromCode(int code) {
        switch (code) {
            case CODE_REELS: return Surface.REELS;
            case CODE_NOT_REELS: return Surface.NOT_REELS;
            default: return Surface.UNKNOWN;
        }
    }

    /** Minimum time between node-tree walks (ms) — walks are throttled. */
    public static final long CHECK_MIN_INTERVAL_MS = 1000;

    /** Safety cap on nodes visited in one tree walk, to bound main-thread work. */
    public static final int MAX_NODES_PER_WALK = 400;

    /** True if the given resource id looks like the Reels viewer pager. */
    public static boolean isPagerId(String viewIdResourceName) {
        if (viewIdResourceName == null) return false;
        String id = viewIdResourceName.toLowerCase(java.util.Locale.US);
        for (String hint : PAGER_ID_HINTS) {
            if (id.contains(hint)) return true;
        }
        return false;
    }

    /** True if the given content description looks like the Reels tab label. */
    public static boolean isReelsTabDescription(CharSequence description) {
        if (description == null) return false;
        return description.toString().toLowerCase(java.util.Locale.US)
                .contains(REELS_TAB_DESCRIPTION);
    }
}
