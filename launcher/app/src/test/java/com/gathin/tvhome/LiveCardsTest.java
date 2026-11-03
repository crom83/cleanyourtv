package com.gathin.tvhome;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LiveCardsTest {
    @Test
    public void shortWordsAndNumbersPass() {
        assertTrue(LiveCards.checkText("12°"));
        assertTrue(LiveCards.checkText("5"));
        assertTrue(LiveCards.checkText("-3° · 80%"));
        assertTrue(LiveCards.checkText("Облачно"));
    }

    @Test
    public void anythingElseIsDropped() {
        assertFalse(LiveCards.checkText(null));
        assertFalse(LiveCards.checkText(""));
        assertFalse(LiveCards.checkText("   "));
        assertFalse(LiveCards.checkText("This is far too long"));
        assertFalse(LiveCards.checkText("5‮"));     // bidi override
        assertFalse(LiveCards.checkText("a\nb"));
        assertFalse(LiveCards.checkText("💡5"));
        assertFalse(LiveCards.checkText("<b>5</b>"));
    }

    @Test
    public void rereadLandsJustAfterTheMinuteTurns() {
        org.junit.Assert.assertEquals(60_300, LiveCards.delayToNextMinute(120_000));
        org.junit.Assert.assertEquals(301, LiveCards.delayToNextMinute(179_999));   // one ms before the turn
        org.junit.Assert.assertEquals(15_300, LiveCards.delayToNextMinute(45_000));
    }
}
