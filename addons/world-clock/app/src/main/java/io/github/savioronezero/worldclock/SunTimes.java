package io.github.savioronezero.worldclock;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Sunrise and sunset for a place and day (the NOAA sunrise equation, good to a minute or two away from the poles).
 * No Android, tested on the JVM.
 */
final class SunTimes {
    /** The sun never sets that day ({@link #POLAR_DAY}) or never rises ({@link #POLAR_NIGHT}). */
    static final int NORMAL = 0, POLAR_DAY = 1, POLAR_NIGHT = 2;

    final int kind;
    /** Local times in the place's zone; null unless {@link #NORMAL}. */
    final LocalTime rise, set;

    private SunTimes(int kind, LocalTime rise, LocalTime set) {
        this.kind = kind;
        this.rise = rise;
        this.set = set;
    }

    static SunTimes on(LocalDate day, double lat, double lon, ZoneId zone) {
        // Fractional year at local noon, then the equation of time (minutes) and the declination (radians)
        double g = 2 * Math.PI / (day.isLeapYear() ? 366 : 365) * (day.getDayOfYear() - 1);
        double eqTime = 229.18 * (0.000075 + 0.001868 * Math.cos(g) - 0.032077 * Math.sin(g)
                - 0.014615 * Math.cos(2 * g) - 0.040849 * Math.sin(2 * g));
        double decl = 0.006918 - 0.399912 * Math.cos(g) + 0.070257 * Math.sin(g) - 0.006758 * Math.cos(2 * g)
                + 0.000907 * Math.sin(2 * g) - 0.002697 * Math.cos(3 * g) + 0.00148 * Math.sin(3 * g);
        double phi = Math.toRadians(lat);
        double cosH = (Math.cos(Math.toRadians(90.833)) - Math.sin(phi) * Math.sin(decl))
                / (Math.cos(phi) * Math.cos(decl));
        if (cosH < -1) return new SunTimes(POLAR_DAY, null, null);
        if (cosH > 1) return new SunTimes(POLAR_NIGHT, null, null);
        double ha = Math.toDegrees(Math.acos(cosH));
        double riseUtc = 720 - 4 * (lon + ha) - eqTime;   // minutes after UTC midnight
        double setUtc = 720 - 4 * (lon - ha) - eqTime;
        return new SunTimes(NORMAL, local(day, riseUtc, zone), local(day, setUtc, zone));
    }

    private static LocalTime local(LocalDate day, double utcMinutes, ZoneId zone) {
        ZonedDateTime utc = day.atStartOfDay(ZoneId.of("UTC")).plusSeconds(Math.round(utcMinutes * 60));
        return utc.withZoneSameInstant(zone).toLocalTime();
    }
}
