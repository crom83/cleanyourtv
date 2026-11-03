package io.github.savioronezero.worldclock;

import android.icu.text.TimeZoneNames;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** City names for time zones in one language, from the system's ICU data ("Europe/London" → "Лондон"). */
final class TimeZoneNamesCache {
    private final TimeZoneNames names;
    private final Locale locale;
    private final Map<String, String> cache = new HashMap<>();

    TimeZoneNamesCache(Locale locale) {
        names = TimeZoneNames.getInstance(locale);
        this.locale = locale;
    }

    private static TimeZoneNamesCache shared;

    /** A clock's name: the city in the TV's language, the TV's own zone for local, the id's city as a fallback. */
    static synchronized String label(android.content.Context c, String id) {
        Locale locale = Locale.getDefault();
        if (shared == null || !shared.locale.equals(locale)) shared = new TimeZoneNamesCache(locale);
        String zone = ClockModel.LOCAL.equals(id) ? java.time.ZoneId.systemDefault().getId() : id;
        String name = shared.city(zone);
        if (name != null) return name;
        return ClockModel.LOCAL.equals(id) ? c.getString(R.string.local) : ClockModel.city(id);
    }

    /** Null when the system has no city for the zone (UTC, for one). */
    String city(String zone) {
        if (cache.containsKey(zone)) return cache.get(zone);
        String name = null;
        try {
            name = names.getExemplarLocationName(zone);
        } catch (RuntimeException ignored) {
            // An unusual zone id: fall back to the id's own city.
        }
        if (name != null && name.trim().isEmpty()) name = null;
        cache.put(zone, name);
        return name;
    }
}
