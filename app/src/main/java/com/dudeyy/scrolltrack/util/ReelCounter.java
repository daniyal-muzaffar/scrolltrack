package com.dudeyy.scrolltrack.util;

import com.dudeyy.scrolltrack.util.ReelsDetection.Surface;

/**
 * Pure counting state machine: one increment per completed advance to a new
 * Reel, not per raw scroll event. No Android dependencies — unit-testable
 * with simulated event sequences.
 *
 * Primary signal (surface == REELS): the pager's page index
 * ({@code fromIndex}). A swipe up moves it to a higher page; a swipe back
 * down moves it lower; a bounce or pull-to-refresh leaves it unchanged.
 *
 * Fallback (surface == UNKNOWN, or no index data on the Reels surface):
 * the previous burst-cluster logic — a burst of scroll events counts once,
 * but only when the burst shows real upward travel. A burst with directional
 * data that sums to zero (a bounce) counts zero; a burst with no directional
 * data at all is counted best-effort, as before.
 *
 * A short minimum interval between counts stops one swipe counting twice,
 * while fast consecutive swipes still count separately.
 */
public class ReelCounter {

    /** Minimum time between two accepted counts (ms). */
    public static final long MIN_COUNT_INTERVAL_MS = 300;
    /** Gap that closes a fallback gesture cluster (ms). */
    private static final long CLUSTER_GAP_MS = 800;
    /** Hard cap on one fallback cluster (ms). */
    private static final long CLUSTER_MAX_MS = 1500;
    /** A real fling emits a burst; a lone event is noise. */
    private static final int MIN_EVENTS_PER_SWIPE = 2;

    /** One scroll event, as observed by the accessibility service. */
    public static final class ScrollEvent {
        public final int fromIndex;   // -1 when the event carries no index
        public final int toIndex;
        public final int itemCount;
        public final int dy;          // scroll delta Y, 0 when unavailable
        public final int scrollY;     // scroll position, 0 when unavailable
        public final long eventTime;  // event time in ms
        public final Surface surface;

        public ScrollEvent(int fromIndex, int toIndex, int itemCount,
                           int dy, int scrollY, long eventTime, Surface surface) {
            this.fromIndex = fromIndex;
            this.toIndex = toIndex;
            this.itemCount = itemCount;
            this.dy = dy;
            this.scrollY = scrollY;
            this.eventTime = eventTime;
            this.surface = surface;
        }
    }

    /** Outcome of feeding an event (or flushing a cluster). */
    public static final class Result {
        public final boolean counted;
        /** Short reason code for the debug log (e.g. "page_advance", "bounce"). */
        public final String reason;
        /** True when this decision is worth one debug-log line. */
        public final boolean loggable;

        public Result(boolean counted, String reason, boolean loggable) {
            this.counted = counted;
            this.reason = reason;
            this.loggable = loggable;
        }
    }

    private int lastPageIndex = -1; // page the counter is anchored on; -1 = none yet
    private long lastCountTs = 0;

    // Fallback burst cluster.
    private int clusterEvents;
    private long clusterStartTs;
    private long clusterLastTs;
    private long clusterDySum;
    private int clusterSyFirst;
    private int clusterSyLast;
    private boolean clusterHasDirectionalData;

    public void reset() {
        lastPageIndex = -1;
        lastCountTs = 0;
        resetCluster();
    }

    public Result feed(ScrollEvent e) {
        if (e.surface == Surface.NOT_REELS) {
            resetCluster();
            lastPageIndex = -1;
            return new Result(false, "not_reels", false);
        }
        if (e.surface == Surface.REELS && e.fromIndex >= 0) {
            resetCluster();
            return feedPageIndex(e);
        }
        // UNKNOWN surface, or REELS without index data: burst-cluster fallback.
        return feedCluster(e);
    }

    private Result feedPageIndex(ScrollEvent e) {
        if (lastPageIndex < 0) {
            // First page seen — anchor, never count the page we started on.
            lastPageIndex = e.fromIndex;
            return new Result(false, "anchor_page", false);
        }
        if (e.fromIndex > lastPageIndex) {
            int advanced = e.fromIndex - lastPageIndex;
            lastPageIndex = e.fromIndex;
            if (lastCountTs == 0 || e.eventTime - lastCountTs >= MIN_COUNT_INTERVAL_MS) {
                lastCountTs = e.eventTime;
                return new Result(true, "page_advance" + (advanced > 1 ? "x" + advanced : ""), true);
            }
            return new Result(false, "too_fast", true);
        }
        if (e.fromIndex < lastPageIndex) {
            lastPageIndex = e.fromIndex;
            return new Result(false, "went_back", true);
        }
        return new Result(false, "same_page", false);
    }

    private Result feedCluster(ScrollEvent e) {
        if (clusterEvents > 0 && e.eventTime - clusterLastTs > CLUSTER_GAP_MS) {
            Result r = evaluateCluster();
            resetCluster();
            if (r.counted || r.loggable) {
                // A cluster just closed; the new event starts the next one.
                feedCluster(e);
                return r;
            }
        }
        if (clusterEvents == 0) {
            clusterStartTs = e.eventTime;
            clusterSyFirst = e.scrollY;
            clusterHasDirectionalData = false;
        }
        clusterEvents++;
        clusterLastTs = e.eventTime;
        clusterDySum += e.dy;
        if (e.dy != 0) clusterHasDirectionalData = true;
        if (e.scrollY != clusterSyLast) clusterHasDirectionalData = true;
        clusterSyLast = e.scrollY;
        if (e.eventTime - clusterStartTs > CLUSTER_MAX_MS) {
            Result r = evaluateCluster();
            resetCluster();
            return r;
        }
        return new Result(false, "cluster_open", false);
    }

    /**
     * Close any open fallback cluster (called periodically and when a session
     * ends). Returns the cluster's decision.
     */
    public Result flush(long nowTs) {
        if (clusterEvents == 0) return new Result(false, "no_cluster", false);
        if (nowTs - clusterLastTs <= CLUSTER_GAP_MS) {
            return new Result(false, "cluster_open", false);
        }
        Result r = evaluateCluster();
        resetCluster();
        return r;
    }

    private Result evaluateCluster() {
        if (clusterEvents < MIN_EVENTS_PER_SWIPE) {
            return new Result(false, "too_few_events", true);
        }
        long net = clusterDySum;
        if (clusterHasDirectionalData) net += (long) clusterSyLast - clusterSyFirst;
        if (!clusterHasDirectionalData) {
            // No directional signal at all (typical pager burst): count best-effort.
            return acceptCluster("burst_no_signal");
        }
        if (net > 0) return acceptCluster("burst_upward");
        // Directional data that sums to zero or negative: bounce, refresh, or down.
        return new Result(false, net == 0 ? "bounce" : "downward", true);
    }

    private Result acceptCluster(String reason) {
        long now = clusterLastTs;
        if (lastCountTs != 0 && now - lastCountTs < MIN_COUNT_INTERVAL_MS) {
            return new Result(false, "too_fast", true);
        }
        lastCountTs = now;
        return new Result(true, reason, true);
    }

    private void resetCluster() {
        clusterEvents = 0;
        clusterStartTs = 0;
        clusterLastTs = 0;
        clusterDySum = 0;
        clusterSyFirst = 0;
        clusterSyLast = 0;
        clusterHasDirectionalData = false;
    }
}
