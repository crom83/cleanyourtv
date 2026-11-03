package com.lionlev.tv.capacity;

import static org.junit.Assert.assertEquals;

import java.util.Locale;

import org.junit.Test;

public class LevelsTest {
    @Test
    public void colourFollowsHowMuchIsLeft() {
        assertEquals(Levels.GREEN, Levels.colour(1f));
        assertEquals(Levels.GREEN, Levels.colour(0.30f));
        assertEquals(Levels.AMBER, Levels.colour(0.29f));
        assertEquals(Levels.AMBER, Levels.colour(0.15f));
        assertEquals(Levels.RED, Levels.colour(0.14f));
        assertEquals(Levels.RED, Levels.colour(0f));
    }

    @Test
    public void shareIsUnknownWithoutBothNumbers() {
        assertEquals(0.5f, Levels.share(466, 932), 1e-6);
        assertEquals(1f, Levels.share(1000, 932), 1e-6);
        assertEquals(-1f, Levels.share(-1, 932), 1e-6);
        assertEquals(-1f, Levels.share(100, 0), 1e-6);
    }

    @Test
    public void gigabytesUseTheLanguagesDecimalMark() {
        long gb = 1024L * 1024 * 1024;
        assertEquals("1.8", Levels.gb(gb * 18 / 10, Locale.US));
        assertEquals("1,8", Levels.gb(gb * 18 / 10, new Locale("ru")));
        assertEquals("0.0", Levels.gb(0, Locale.US));
    }

    @Test
    public void percentIsWholeAndClamped() {
        assertEquals(37, Levels.percent(0.369f));
        assertEquals(0, Levels.percent(-0.2f));
        assertEquals(100, Levels.percent(1.5f));
    }
}
