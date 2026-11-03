package io.github.savioronezero.worldclock;

import java.text.Normalizer;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Clock calculations and small list edits: no Android objects, fixed offsets or background work. */
final class ClockModel {
    static final String LOCAL = "@local";
    static final int LIMIT = 6;
    static final List<String> DEFAULTS = Collections.unmodifiableList(
            Arrays.asList(LOCAL, "Europe/London", "Australia/Sydney"));

    /**
     * Cities without a time zone of their own (Beijing keeps Shanghai's time): zone, English and Russian name,
     * latitude and longitude. A clock for one is saved as "Asia/Shanghai#Beijing".
     */
    static final String[][] ALIASES = {
            {"Asia/Shanghai", "Beijing", "Пекин", "39.9", "116.4"},
            {"Asia/Shanghai", "Guangzhou", "Гуанчжоу", "23.1", "113.3"},
            {"Asia/Shanghai", "Shenzhen", "Шэньчжэнь", "22.5", "114.1"},
            {"Asia/Kolkata", "New Delhi", "Нью-Дели", "28.6", "77.2"},
            {"Asia/Kolkata", "Mumbai", "Мумбаи", "19.1", "72.9"},
            {"Asia/Kolkata", "Bengaluru", "Бангалор", "13.0", "77.6"},
            {"America/New_York", "Washington", "Вашингтон", "38.9", "-77.0"},
            {"America/New_York", "Boston", "Бостон", "42.4", "-71.1"},
            {"America/New_York", "Miami", "Майами", "25.8", "-80.2"},
            {"America/Chicago", "Houston", "Хьюстон", "29.8", "-95.4"},
            {"America/Los_Angeles", "San Francisco", "Сан-Франциско", "37.8", "-122.4"},
            {"America/Los_Angeles", "Seattle", "Сиэтл", "47.6", "-122.3"},
            {"America/Los_Angeles", "Las Vegas", "Лас-Вегас", "36.2", "-115.1"},
            {"America/Toronto", "Ottawa", "Оттава", "45.4", "-75.7"},
            {"America/Toronto", "Montreal", "Монреаль", "45.5", "-73.6"},
            {"Australia/Sydney", "Canberra", "Канберра", "-35.3", "149.1"},
            {"America/Sao_Paulo", "Brasilia", "Бразилиа", "-15.8", "-47.9"},
            {"America/Sao_Paulo", "Rio de Janeiro", "Рио-де-Жанейро", "-22.9", "-43.2"},
            {"Europe/Istanbul", "Ankara", "Анкара", "39.9", "32.9"},
            {"Asia/Dubai", "Abu Dhabi", "Абу-Даби", "24.5", "54.4"},
            {"Asia/Karachi", "Islamabad", "Исламабад", "33.7", "73.1"},
            {"Pacific/Auckland", "Wellington", "Веллингтон", "-41.3", "174.8"},
            {"Asia/Ho_Chi_Minh", "Hanoi", "Ханой", "21.0", "105.8"},
            {"Europe/Zurich", "Bern", "Берн", "46.9", "7.4"},
            {"Europe/Zurich", "Geneva", "Женева", "46.2", "6.1"},
            {"Europe/Moscow", "Saint Petersburg", "Санкт-Петербург", "59.9", "30.3"},
            {"Europe/Berlin", "Munich", "Мюнхен", "48.1", "11.6"},
            {"Europe/Madrid", "Barcelona", "Барселона", "41.4", "2.2"},
            {"Europe/Rome", "Milan", "Милан", "45.5", "9.2"},
            {"Asia/Tokyo", "Osaka", "Осака", "34.7", "135.5"},
            {"Asia/Tokyo", "Kyoto", "Киото", "35.0", "135.8"},
            {"Asia/Riyadh", "Mecca", "Мекка", "21.4", "39.8"},
            {"Asia/Jerusalem", "Tel Aviv", "Тель-Авив", "32.1", "34.8"}
    };

    /** The time zone behind a clock id: the id itself, or the zone part of "Zone#City". */
    static String zone(String id) {
        int hash = id == null ? -1 : id.indexOf('#');
        return hash < 0 ? id : id.substring(0, hash);
    }

    /** The alias row for "Zone#City", or null. */
    static String[] alias(String id) {
        int hash = id == null ? -1 : id.indexOf('#');
        if (hash < 0) return null;
        String zone = id.substring(0, hash), name = id.substring(hash + 1);
        for (String[] a : ALIASES) if (a[0].equals(zone) && a[1].equals(name)) return a;
        return null;
    }

    static boolean valid(String id) {
        if (LOCAL.equals(id)) return true;
        if (id != null && id.indexOf('#') >= 0) return alias(id) != null && valid(zone(id));
        if (id == null || id.length() > 100) return false;
        // Only platform-named zones: malformed IDs cannot silently become GMT as with TimeZone.getTimeZone.
        return "UTC".equals(id) || ZoneId.getAvailableZoneIds().contains(id);
    }

    static List<String> restore(List<String> saved) {
        List<String> result = new ArrayList<>();
        if (saved != null) for (String id : saved) {
            if (valid(id) && !result.contains(id)) result.add(id);
            if (result.size() == LIMIT) break;
        }
        return result.isEmpty() ? new ArrayList<>(DEFAULTS) : result;
    }

    static List<String> add(List<String> ids, String id) {
        List<String> result = new ArrayList<>(ids);
        if (valid(id) && !result.contains(id) && result.size() < LIMIT) result.add(id);
        return result;
    }

    static List<String> replace(List<String> ids, int index, String id) {
        List<String> result = new ArrayList<>(ids);
        if (index >= 0 && index < result.size() && valid(id)
                && (id.equals(result.get(index)) || !result.contains(id))) result.set(index, id);
        return result;
    }

    static List<String> remove(List<String> ids, int index) {
        List<String> result = new ArrayList<>(ids);
        if (result.size() > 1 && index >= 0 && index < result.size()) result.remove(index);
        return result;
    }

    static List<String> move(List<String> ids, int from, int to) {
        List<String> result = new ArrayList<>(ids);
        if (from >= 0 && from < result.size() && to >= 0 && to < result.size()) {
            String id = result.remove(from);
            result.add(to, id);
        }
        return result;
    }

    static String city(String id) {
        if (LOCAL.equals(id)) return "Local";
        String[] alias = alias(id);
        if (alias != null) return alias[1];
        String name = id.substring(id.lastIndexOf('/') + 1).replace('_', ' ');
        // Older Android/ICU catalogues use the legacy spelling; keep the familiar city searchable.
        return "Katmandu".equals(name) ? "Kathmandu" : name;
    }

    static String area(String id) {
        id = zone(id);
        int slash = id.lastIndexOf('/');
        return slash < 0 ? id : id.substring(0, slash).replace('_', ' ').replace('/', ' ');
    }

    private static String normalized(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replace('_', ' ').trim();
    }

    static List<String> search(List<String> catalogue, String query, List<String> existing, int replacing) {
        return search(catalogue, query, existing, replacing, id -> "");
    }

    /** As above, also matching {@code names}' extra name for each zone (the city in the TV's language). */
    static List<String> search(List<String> catalogue, String query, List<String> existing, int replacing,
                               java.util.function.Function<String, String> names) {
        String q = normalized(query);
        String[] terms = q.isEmpty() ? new String[0] : q.split("\\s+");
        List<String> matches = new ArrayList<>();
        for (String id : catalogue) {
            if (existing.contains(id) && (replacing < 0 || !id.equals(existing.get(replacing)))) continue;
            String[] alias = alias(id);
            String text = normalized(city(id) + " " + (alias != null ? alias[2] : id.replace('/', ' ').replace('#', ' '))
                    + " " + names.apply(id));
            boolean match = true;
            for (String term : terms) if (!text.contains(term)
                    && !text.replace(" ", "").contains(term)) { match = false; break; }
            if (match) matches.add(id);
        }
        return matches;
    }

    static List<String> catalogue() {
        Set<String> found = new HashSet<>();
        found.add(LOCAL); found.add("UTC");
        Set<String> available = ZoneId.getAvailableZoneIds();
        for (String id : available) {
            if (id.equals("Asia/Katmandu") && available.contains("Asia/Kathmandu")) continue;
            if (id.contains("/") && !id.startsWith("Etc/") && !id.startsWith("SystemV/")
                    && !id.startsWith("US/") && !id.startsWith("Canada/")
                    && !id.startsWith("Brazil/") && !id.startsWith("Mexico/")) found.add(id);
        }
        for (String[] a : ALIASES) if (available.contains(a[0])) found.add(a[0] + "#" + a[1]);
        List<String> result = new ArrayList<>(found);
        result.sort((a, b) -> {
            if (a.equals(LOCAL)) return b.equals(LOCAL) ? 0 : -1;
            if (b.equals(LOCAL)) return 1;
            int name = city(a).compareToIgnoreCase(city(b));
            return name == 0 ? a.compareTo(b) : name;
        });
        return result;
    }

    static final class Reading {
        final String time, period, date, offset;
        final int dayDifference;
        /** The hour of day in that city, with minutes as a fraction: what its sky looks like. */
        final double hour;
        /** That city's offset from local time, in minutes (negative behind). */
        final int minutesFromLocal;
        Reading(String time, String period, String date, String offset, int dayDifference, double hour,
                int minutesFromLocal) {
            this.time = time; this.period = period; this.date = date;
            this.offset = offset; this.dayDifference = dayDifference;
            this.hour = hour; this.minutesFromLocal = minutesFromLocal;
        }
    }

    /** "+7 h", "-4 h", "+5:30": a difference from local time; empty when there is none. */
    static String difference(int minutes) {
        if (minutes == 0) return "";
        String sign = minutes > 0 ? "+" : "\u2212";
        int m = Math.abs(minutes);
        return m % 60 == 0 ? sign + (m / 60) : sign + (m / 60) + ":" + String.format(Locale.ROOT, "%02d", m % 60);
    }

    static Reading at(String id, Instant instant, ZoneId local, boolean twentyFour, Locale locale) {
        ZonedDateTime time = instant.atZone(LOCAL.equals(id) ? local : ZoneId.of(zone(id)));
        String offset = time.getOffset().getId();
        return new Reading(time.format(DateTimeFormatter.ofPattern(twentyFour ? "HH:mm" : "h:mm", locale)),
                twentyFour ? "" : time.format(DateTimeFormatter.ofPattern("a", locale)),
                time.format(DateTimeFormatter.ofPattern("EEE, d MMM", locale)),
                "Z".equals(offset) ? "UTC" : "UTC" + offset,
                (int) ChronoUnit.DAYS.between(instant.atZone(local).toLocalDate(), time.toLocalDate()),
                time.getHour() + time.getMinute() / 60.0,
                (time.getOffset().getTotalSeconds() - instant.atZone(local).getOffset().getTotalSeconds()) / 60);
    }

    static long nextMinute(long epochMillis) { return 60_000L - Math.floorMod(epochMillis, 60_000L); }
}
