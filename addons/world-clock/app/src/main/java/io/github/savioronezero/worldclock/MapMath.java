package io.github.savioronezero.worldclock;

/** The map's projection (Equal Earth) and where the sun is overhead; no Android, so it is tested on the JVM. */
final class MapMath {
    private static final double A1 = 1.340264, A2 = -0.081106, A3 = 0.000893, A4 = 0.003796;
    private static final double M = Math.sqrt(3) / 2;
    /** Half the projected width and height of the whole world. */
    static final double HALF_W = 2.7066, HALF_H = 1.3173;

    private MapMath() {}

    /** Longitude and latitude in degrees to Equal Earth x and y, with y growing downwards like the screen. */
    static void project(double lon, double lat, float[] out) {
        double lam = Math.toRadians(lon), phi = Math.toRadians(lat);
        double theta = Math.asin(M * Math.sin(phi));
        double t2 = theta * theta, t6 = t2 * t2 * t2;
        out[0] = (float) (2 * Math.sqrt(3) * lam * Math.cos(theta) / (3 * (A1 + 3 * A2 * t2 + t6 * (7 * A3 + 9 * A4 * t2))));
        out[1] = (float) -(theta * (A1 + A2 * t2 + t6 * (A3 + A4 * t2)));
    }

    /** {latitude, longitude} in degrees where the sun is straight overhead at this moment (to about a degree). */
    static double[] subsolar(long epochMillis) {
        double days = epochMillis / 86_400_000.0 - 10_957.5;   // since 2000-01-01 12:00 UTC
        double meanLon = Math.toRadians((280.460 + 0.9856474 * days) % 360);
        double meanAnomaly = Math.toRadians((357.528 + 0.9856003 * days) % 360);
        double eclipticLon = meanLon + Math.toRadians(1.915) * Math.sin(meanAnomaly)
                + Math.toRadians(0.020) * Math.sin(2 * meanAnomaly);
        double obliquity = Math.toRadians(23.439 - 0.0000004 * days);
        double declination = Math.asin(Math.sin(obliquity) * Math.sin(eclipticLon));
        double rightAscension = Math.atan2(Math.cos(obliquity) * Math.sin(eclipticLon), Math.cos(eclipticLon));
        double gmst = Math.toRadians((280.46061837 + 360.98564736629 * days) % 360);
        double lon = Math.toDegrees(rightAscension - gmst);
        lon = ((lon + 180) % 360 + 360) % 360 - 180;
        return new double[] {Math.toDegrees(declination), lon};
    }

    /**
     * The latitude of the day/night line at a longitude; night lies south of it when the sun is north of the
     * equator, and north of it otherwise.
     */
    static double terminator(double lon, double sunLat, double sunLon) {
        double dec = Math.toRadians(Math.abs(sunLat) < 0.05 ? (sunLat < 0 ? -0.05 : 0.05) : sunLat);
        double h = Math.toRadians(lon - sunLon);
        return Math.toDegrees(Math.atan(-Math.cos(h) / Math.tan(dec)));
    }
}
