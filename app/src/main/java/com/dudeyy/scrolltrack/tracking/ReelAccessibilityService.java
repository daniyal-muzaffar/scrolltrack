package com.dudeyy.scrolltrack.tracking;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Build;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.dudeyy.scrolltrack.data.db.DbHelper;
import com.dudeyy.scrolltrack.util.DebugLog;
import com.dudeyy.scrolltrack.util.ReelsDetection;
import com.dudeyy.scrolltrack.util.ReelsDetection.Surface;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Forwards scroll events from the Instagram app to {@link TrackingService},
 * which owns all counting logic, and reports which surface Instagram is
 * showing (Reels or not).
 *
 * Counting model: one increment per completed advance to a new Reel, detected
 * from the pager's page index ({@code fromIndex}). Feed, Stories, Explore,
 * comments and DMs are a different surface and never count.
 *
 * Scope is deliberately narrow: the service config restricts events to
 * {@code com.instagram.android} only. The service can retrieve window
 * content (needed to identify the Reels viewer), but it never reads node
 * text, usernames, messages or any content — only view IDs, class names and
 * the static "Reels" tab label — and never stores or logs what it sees.
 */
public class ReelAccessibilityService extends AccessibilityService {

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        TrackingService.notifyAccessibilityAlive(this);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        String pkg = String.valueOf(event.getPackageName());
        // packageNames restricts this service to Instagram; anything else
        // cannot arrive here. Guard anyway.
        if (!DbHelper.INSTAGRAM_PACKAGE.equals(pkg)) return;

        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            // Instagram opened or navigated: make sure the tracker is alive
            // (also self-heals after the app was force-stopped) and re-check
            // which surface is showing, even without a scroll.
            TrackingService.ensureRunning(this);
            TrackingService.notifyWindowChanged(this, pkg);
            reportSurface();
            return;
        }

        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            if (!TrackingService.isGesturesAccepted()) return;

            int dx = 0;
            int dy = 0;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                dx = event.getScrollDeltaX();
                dy = event.getScrollDeltaY();
            }
            int scrollY = event.getScrollY();

            // Drop clearly-horizontal scrolls, but only when we actually have
            // delta data — with no data we must not filter, or nothing is counted.
            if ((dx != 0 || dy != 0) && Math.abs(dx) > Math.abs(dy)) return;

            TrackingService.noteGestureSeen();
            Surface surface = checkSurfaceThrottled(event);
            Intent intent = new Intent(this, TrackingService.class);
            intent.setAction(TrackingService.ACTION_GESTURE);
            intent.putExtra(TrackingService.EXTRA_DELTA_Y, dy);
            intent.putExtra(TrackingService.EXTRA_SCROLL_Y, scrollY);
            intent.putExtra(TrackingService.EXTRA_EVENT_TIME, event.getEventTime());
            intent.putExtra(TrackingService.EXTRA_FROM_INDEX, event.getFromIndex());
            intent.putExtra(TrackingService.EXTRA_TO_INDEX, event.getToIndex());
            intent.putExtra(TrackingService.EXTRA_ITEM_COUNT, event.getItemCount());
            intent.putExtra(TrackingService.EXTRA_SURFACE, ReelsDetection.codeOf(surface));
            try {
                startService(intent);
            } catch (IllegalStateException ignored) {
                // Service not running; the gesture is dropped rather than fabricated.
            }
        }
    }

    // ---------- Reels-surface detection ----------

    private long lastSurfaceCheckTs = 0;
    private Surface cachedSurface = Surface.UNKNOWN;

    /** Surface verdict for a window change, sent even without a scroll. */
    private void reportSurface() {
        Surface surface = checkSurfaceThrottled(null);
        Intent intent = new Intent(this, TrackingService.class);
        intent.setAction(TrackingService.ACTION_SURFACE);
        intent.putExtra(TrackingService.EXTRA_SURFACE, ReelsDetection.codeOf(surface));
        intent.putExtra(TrackingService.EXTRA_EVENT_TIME, System.currentTimeMillis());
        try {
            startService(intent);
        } catch (IllegalStateException ignored) { }
    }

    /** Throttled surface check; reuses the cached verdict within the interval. */
    private Surface checkSurfaceThrottled(AccessibilityEvent event) {
        long now = System.currentTimeMillis();
        if (now - lastSurfaceCheckTs < ReelsDetection.CHECK_MIN_INTERVAL_MS) {
            return cachedSurface;
        }
        lastSurfaceCheckTs = now;
        Surface next = detectSurface(event);
        if (next != cachedSurface) {
            DebugLog.log(this, "surface", "surface " + cachedSurface + " -> " + next);
            cachedSurface = next;
        }
        return cachedSurface;
    }

    /**
     * Detects the Reels surface. Fast path: the scrolled view itself carries
     * the pager id. Otherwise walks the active window's node tree (capped)
     * looking for the Reels pager or the selected Reels tab. Returns UNKNOWN
     * when the tree cannot be inspected — the caller then falls back to the
     * previous burst counting instead of silently counting zero.
     */
    private Surface detectSurface(AccessibilityEvent event) {
        // Fast path: the scroll source is the pager itself.
        if (event != null) {
            try {
                AccessibilityNodeInfo source = event.getSource();
                if (source != null) {
                    try {
                        if (ReelsDetection.isPagerId(source.getViewIdResourceName())) {
                            return Surface.REELS;
                        }
                    } finally {
                        source.recycle();
                    }
                }
            } catch (Exception ignored) { }
        }
        AccessibilityNodeInfo root = null;
        try {
            root = getRootInActiveWindow();
        } catch (Exception ignored) { }
        if (root == null) return Surface.UNKNOWN;
        try {
            return walkForReels(root) ? Surface.REELS : Surface.NOT_REELS;
        } finally {
            root.recycle();
        }
    }

    /** Breadth-first walk (capped) for a Reels pager or the selected Reels tab. */
    private boolean walkForReels(AccessibilityNodeInfo root) {
        Deque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        int visited = 0;
        try {
            while (!queue.isEmpty() && visited < ReelsDetection.MAX_NODES_PER_WALK) {
                AccessibilityNodeInfo node = queue.poll();
                if (node == null) continue;
                visited++;
                try {
                    if (ReelsDetection.isPagerId(node.getViewIdResourceName())) return true;
                    if (node.isSelected()
                            && ReelsDetection.isReelsTabDescription(node.getContentDescription())) {
                        return true;
                    }
                    int children = node.getChildCount();
                    for (int i = 0; i < children; i++) {
                        AccessibilityNodeInfo child = node.getChild(i);
                        if (child != null) queue.add(child);
                    }
                } catch (Exception ignored) {
                    // A node that cannot be read is skipped, not fatal.
                } finally {
                    if (node != root) node.recycle();
                }
            }
            return false;
        } finally {
            // Recycle anything still queued after an early return (root is
            // recycled by the caller).
            AccessibilityNodeInfo leftover;
            while ((leftover = queue.poll()) != null) {
                if (leftover != root) leftover.recycle();
            }
        }
    }

    @Override
    public void onInterrupt() { }

    @Override
    public void onDestroy() {
        TrackingService.notifyAccessibilityGone();
        super.onDestroy();
    }
}
