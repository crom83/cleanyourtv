package io.github.sumyabazar1.weather;

import java.util.ArrayList;
import java.util.List;

/**
 * The saved cities, in the order CH+/CH- and Left/Right step through them. Kept in preferences as one line per
 * city, "name\tlatitude\tlongitude"; free of Android so it can be tested on the JVM.
 */
final class Cities {
    static final class City {
        final String name;
        final double latitude, longitude;

        City(String name, double latitude, double longitude) {
            this.name = name;
            this.latitude = latitude;
            this.longitude = longitude;
        }

        /** Two search results this close (about 5 km) are the same place. */
        boolean samePlace(City other) {
            return Math.abs(latitude - other.latitude) < 0.05 && Math.abs(longitude - other.longitude) < 0.05;
        }
    }

    private Cities() {}

    static List<City> parse(String saved) {
        List<City> cities = new ArrayList<>();
        if (saved == null) return cities;
        for (String line : saved.split("\n")) {
            String[] f = line.split("\t");
            if (f.length != 3 || f[0].trim().isEmpty()) continue;
            try {
                cities.add(new City(f[0], Double.parseDouble(f[1]), Double.parseDouble(f[2])));
            } catch (NumberFormatException ignored) {
                // a damaged line is skipped, the others stay
            }
        }
        return cities;
    }

    static String format(List<City> cities) {
        StringBuilder out = new StringBuilder();
        for (City c : cities) {
            if (out.length() > 0) out.append('\n');
            out.append(clean(c.name)).append('\t').append(c.latitude).append('\t').append(c.longitude);
        }
        return out.toString();
    }

    private static String clean(String name) {
        return name.replace('\t', ' ').replace('\n', ' ');
    }

    /** Adds the city at the end, or finds it if it is already saved; returns its position. */
    static int add(List<City> cities, City city) {
        for (int i = 0; i < cities.size(); i++) {
            if (cities.get(i).samePlace(city)) return i;
        }
        cities.add(city);
        return cities.size() - 1;
    }

    /** The position after stepping by {@code d} (+1 / -1), going round at both ends. */
    static int step(int index, int d, int size) {
        if (size <= 0) return 0;
        return Math.floorMod(index + d, size);
    }

    /** Which position the shown city has after removing {@code removed}, so the same city stays on screen. */
    static int afterRemove(int current, int removed, int sizeAfter) {
        if (sizeAfter <= 0) return 0;
        if (removed < current) return current - 1;
        return Math.min(current, sizeAfter - 1);
    }
}
