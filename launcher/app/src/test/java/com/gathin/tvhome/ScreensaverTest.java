package com.gathin.tvhome;

import static org.junit.Assert.assertNull;

import org.junit.Test;

public class ScreensaverTest {
    @Test
    public void noComponentWhenTheListIsEmpty() {
        assertNull(Screensaver.first(null));
        assertNull(Screensaver.first(""));
        assertNull(Screensaver.first("  "));
        assertNull(Screensaver.first(",com.a/.B"));
    }
}
