package io.github.savioronezero.worldclock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import org.junit.Test;

public class CityTest {
    @Test
    public void londonSunriseAndSunsetInNovember() {
        SunTimes s = SunTimes.on(LocalDate.of(2026, 11, 4), 51.5, -0.1, ZoneId.of("Europe/London"));
        assertEquals(SunTimes.NORMAL, s.kind);
        assertTrue(Math.abs(s.rise.toSecondOfDay() - LocalTime.of(7, 0).toSecondOfDay()) < 15 * 60);
        assertTrue(Math.abs(s.set.toSecondOfDay() - LocalTime.of(16, 25).toSecondOfDay()) < 15 * 60);
    }

    @Test
    public void polarDaysAndNights() {
        assertEquals(SunTimes.POLAR_DAY, SunTimes.on(LocalDate.of(2026, 6, 21), 78.2, 15.6, ZoneId.of("Arctic/Longyearbyen")).kind);
        assertEquals(SunTimes.POLAR_NIGHT, SunTimes.on(LocalDate.of(2026, 12, 21), 78.2, 15.6, ZoneId.of("Arctic/Longyearbyen")).kind);
    }

    @Test
    public void photoTitlesAndFileNames() {
        assertEquals("Washington, D.C.", CityPhoto.title("America/New_York#Washington"));
        assertEquals("New York", CityPhoto.title("America/New_York"));
        assertEquals("Big Ben.jpg", CityPhoto.fileName("https://upload.wikimedia.org/wikipedia/commons/a/ab/Big_Ben.jpg"));
        assertEquals("Big Ben.jpg", CityPhoto.fileName(
                "https://upload.wikimedia.org/wikipedia/commons/thumb/a/ab/Big_Ben.jpg/320px-Big_Ben.jpg"));
        assertEquals("London Skyline (125508655).jpeg", CityPhoto.fileName("https://upload.wikimedia.org/wikipedia/commons/"
                + "6/67/London_Skyline_%28125508655%29.jpeg?utm_source=en.wikipedia.org&utm_campaign=api"));
        assertEquals("View of Tbilisi.jpg", CityPhoto.fileName("https://thumb.wikimedia.org/wikipedia/commons/thumb/4/45/"
                + "View_of_Tbilisi.jpg/3840px-View_of_Tbilisi.jpg?utm_source=en.wikipedia.org"));
        assertNull(CityPhoto.fileName("https://example.com/x.jpg"));
    }
}
