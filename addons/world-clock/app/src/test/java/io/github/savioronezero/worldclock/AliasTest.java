package io.github.savioronezero.worldclock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.junit.Test;

public class AliasTest {
    @Test
    public void beijingKeepsShanghaisTime() {
        String id = "Asia/Shanghai#Beijing";
        assertTrue(ClockModel.valid(id));
        assertEquals("Asia/Shanghai", ClockModel.zone(id));
        assertEquals("Beijing", ClockModel.city(id));
        assertEquals("Asia", ClockModel.area(id));
        ClockModel.Reading r = ClockModel.at(id, Instant.parse("2026-11-04T00:00:00Z"), ZoneId.of("UTC"), true, Locale.US);
        assertEquals("08:00", r.time);
    }

    @Test
    public void unknownAliasesAreRefused() {
        assertFalse(ClockModel.valid("Asia/Shanghai#Atlantis"));
        assertFalse(ClockModel.valid("Nowhere/Place#Beijing"));
        assertNull(ClockModel.alias("Asia/Shanghai"));
    }

    @Test
    public void searchFindsTheCityInEitherLanguage() {
        List<String> all = ClockModel.catalogue();
        assertTrue(all.contains("Asia/Shanghai#Beijing"));
        assertTrue(ClockModel.search(all, "beijing", Collections.emptyList(), -1).contains("Asia/Shanghai#Beijing"));
        assertTrue(ClockModel.search(all, "Пекин", Collections.emptyList(), -1).contains("Asia/Shanghai#Beijing"));
        assertTrue(ClockModel.search(all, "petersburg", Collections.emptyList(), -1).contains("Europe/Moscow#Saint Petersburg"));
    }
}
