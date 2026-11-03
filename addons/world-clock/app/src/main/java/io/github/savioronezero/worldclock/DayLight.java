package io.github.savioronezero.worldclock;

/**
 * The sky behind a clock card at a given local hour: night, dawn, day, dusk and evening, blended smoothly so a
 * card changes a little every minute rather than jumping. Kept dark enough everywhere for white text. No Android.
 */
final class DayLight {
    /** Hour of day, then the gradient's top and bottom colours; the last row repeats the first for midnight. */
    private static final double[] HOURS = {0, 5, 6.5, 8, 16.5, 18, 19.5, 21, 24};
    private static final int[] TOP = {0xFF0B1530, 0xFF101C3E, 0xFF2B3A67, 0xFF24548A, 0xFF24548A, 0xFF3B2F63,
            0xFF1C2350, 0xFF0B1530, 0xFF0B1530};
    private static final int[] BOTTOM = {0xFF04070F, 0xFF1A2346, 0xFFB27A6A, 0xFF4F86BF, 0xFF4F86BF, 0xFFC27552,
            0xFF3B3366, 0xFF04070F, 0xFF04070F};

    private DayLight() {}

    /** {top, bottom} for an hour of day (0 to 24, fractions for minutes). */
    static int[] colours(double hour) {
        double h = ((hour % 24) + 24) % 24;
        int i = 0;
        while (i < HOURS.length - 2 && h >= HOURS[i + 1]) i++;
        double t = (h - HOURS[i]) / (HOURS[i + 1] - HOURS[i]);
        return new int[] {mix(TOP[i], TOP[i + 1], t), mix(BOTTOM[i], BOTTOM[i + 1], t)};
    }

    /** The sun shows from sunrise to sunset as the sky draws them; the moon otherwise. */
    static boolean sunUp(double hour) {
        double h = ((hour % 24) + 24) % 24;
        return h >= 6.5 && h < 18.5;
    }

    static int mix(int a, int b, double t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = (int) Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }
}
