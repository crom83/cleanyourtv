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
}
