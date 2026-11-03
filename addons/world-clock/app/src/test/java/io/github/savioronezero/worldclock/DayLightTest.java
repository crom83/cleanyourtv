package io.github.savioronezero.worldclock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DayLightTest {
    @Test
    public void skyBlendsBetweenKeyHoursAndWrapsAtMidnight() {
        assertEquals(0xFF0B1530, DayLight.colours(0)[0]);
        assertEquals(0xFF0B1530, DayLight.colours(24)[0]);
        assertEquals(0xFF24548A, DayLight.colours(12)[0]);
        int[] dusk = DayLight.colours(17.25);   // halfway from day to dusk
        assertEquals(DayLight.mix(0xFF24548A, 0xFF3B2F63, 0.5), dusk[0]);
        assertEquals(DayLight.colours(-1)[0], DayLight.colours(23)[0]);
    }

    @Test
    public void sunFromSunriseToSunset() {
        assertFalse(DayLight.sunUp(6));
        assertTrue(DayLight.sunUp(6.5));
        assertTrue(DayLight.sunUp(18.4));
        assertFalse(DayLight.sunUp(18.5));
    }

    @Test
    public void differenceFromHere() {
        assertEquals("", ClockModel.difference(0));
        assertEquals("+7", ClockModel.difference(420));
        assertEquals("−4", ClockModel.difference(-240));
        assertEquals("+5:30", ClockModel.difference(330));
        assertEquals("−3:30", ClockModel.difference(-210));
    }

    @Test
    public void tileLineDropsTheCityWhenTooLong() {
        assertEquals("Лондон 21:10", LiveCardProvider.line("Лондон", "21:10"));
        assertEquals("21:10", LiveCardProvider.line("Сан-Франциско", "21:10"));
    }
}
