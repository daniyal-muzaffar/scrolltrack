package com.dudeyy.scrolltrack.util;

/**
 * Daily Swipe Title: a headline for today's detected upward swipe count.
 * Ranges are inclusive and partition every possible count exactly once.
 * Titles are user-specified verbatim — do not reword them.
 */
public final class SwipeTitles {

    private SwipeTitles() {}

    public static String forCount(int swipesToday) {
        if (swipesToday <= 150) return "LOCKED IN";
        if (swipesToday <= 350) return "Don't waste your potential";
        if (swipesToday <= 600) return "Israel is watching";
        if (swipesToday <= 800) return "Tel aviv impressed";
        return "you're done lil bro💔🥀";
    }
}
