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
    public void historyScaleFollowsTheReadings() {
        float[] flat = Sparkline.range(344, 344, 932);
        assertEquals(314f, flat[0], 0.01);
        assertEquals(374f, flat[1], 0.01);
        float[] wide = Sparkline.range(200, 400, 932);
        assertEquals(160f, wide[0], 0.01);
        assertEquals(440f, wide[1], 0.01);
        float[] low = Sparkline.range(10, 10, 932);
        assertEquals(0f, low[0], 0.01);
        assertEquals(60f, low[1], 0.01);
    }
}
