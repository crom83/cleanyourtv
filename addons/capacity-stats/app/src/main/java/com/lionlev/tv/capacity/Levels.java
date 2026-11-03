package com.lionlev.tv.capacity;

/** How much is left, as a colour: green with room to spare, amber when it gets tight, red when nearly full. */
final class Levels {
    static final int GREEN = 0xFF74A987, AMBER = 0xFFE8B04B, RED = 0xFFE5686A;

    private Levels() {}

    /** The free share (0..1) as one of the three colours: over 30 % green, 15 to 30 % amber, under 15 % red. */
    static int colour(float freeShare) {
        if (freeShare >= 0.30f) return GREEN;
        if (freeShare >= 0.15f) return AMBER;
        return RED;
    }

    /** The free share of a total, or -1 when either is unknown. */
    static float share(long free, long total) {
        if (free < 0 || total <= 0) return -1f;
        return Math.min(1f, (float) free / total);
    }

    /** Gigabytes with one decimal, in the language's own decimal mark: "1,8" in Russian, "1.8" in English. */
    static String gb(long bytes, java.util.Locale locale) {
        return String.format(locale, "%.1f", bytes / (1024.0 * 1024 * 1024));
    }
}
