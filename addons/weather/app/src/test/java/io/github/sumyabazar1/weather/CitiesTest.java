package io.github.sumyabazar1.weather;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class CitiesTest {
    private static Cities.City city(String name, double lat, double lon) {
        return new Cities.City(name, lat, lon);
    }

    @Test
    public void formatThenParseKeepsEveryCity() {
        List<Cities.City> cities = new ArrayList<>();
        cities.add(city("Oslo", 59.91273, 10.74609));
        cities.add(city("Ulaanbaatar", 47.90771, 106.88324));
        List<Cities.City> back = Cities.parse(Cities.format(cities));
        assertEquals(2, back.size());
        assertEquals("Ulaanbaatar", back.get(1).name);
        assertEquals(106.88324, back.get(1).longitude, 1e-9);
    }

    @Test
    public void parseSkipsEmptyAndDamagedLines() {
        assertTrue(Cities.parse(null).isEmpty());
        assertTrue(Cities.parse("").isEmpty());
        List<Cities.City> c = Cities.parse("Oslo\t59.9\t10.7\nbroken\nX\tnot\t1\n\t1\t2\nBergen\t60.4\t5.3");
        assertEquals(2, c.size());
        assertEquals("Bergen", c.get(1).name);
    }

    @Test
    public void tabsAndNewlinesInANameDoNotBreakTheList() {
        List<Cities.City> cities = new ArrayList<>();
        cities.add(city("New\tYork\n", 40.7, -74.0));
        assertEquals(1, Cities.parse(Cities.format(cities)).size());
    }

    @Test
    public void addingASavedPlaceFindsItInsteadOfAddingAgain() {
        List<Cities.City> cities = new ArrayList<>();
        assertEquals(0, Cities.add(cities, city("Oslo", 59.913, 10.746)));
        assertEquals(1, Cities.add(cities, city("Bergen", 60.39, 5.32)));
        assertEquals(0, Cities.add(cities, city("Осло", 59.92, 10.75)));
        assertEquals(2, cities.size());
    }

    @Test
    public void stepGoesRoundAtBothEnds() {
        assertEquals(1, Cities.step(0, 1, 3));
        assertEquals(0, Cities.step(2, 1, 3));
        assertEquals(2, Cities.step(0, -1, 3));
        assertEquals(0, Cities.step(0, 1, 1));
        assertEquals(0, Cities.step(5, 1, 0));
    }

    @Test
    public void removingKeepsTheShownCityOnScreen() {
        assertEquals(1, Cities.afterRemove(2, 0, 2)); // a city before it went away
        assertEquals(1, Cities.afterRemove(1, 2, 2)); // one after it
        assertEquals(1, Cities.afterRemove(2, 2, 2)); // the shown, last one: the new last
        assertEquals(0, Cities.afterRemove(0, 0, 1)); // the shown, first one: the next
    }
}
