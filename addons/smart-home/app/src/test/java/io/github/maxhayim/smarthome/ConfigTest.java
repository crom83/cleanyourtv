package io.github.maxhayim.smarthome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class ConfigTest {
    @Test
    public void glanceFromJsonArrayKeepsOrderAndDropsBlanks() throws Exception {
        JSONArray a = new JSONArray();
        a.put("weather.home");
        a.put(" ");
        a.put("sensor.station_e10");
        a.put("weather.home");
        List<String> ids = Config.parseGlance(a);
        assertEquals(Arrays.asList("weather.home", "sensor.station_e10"), ids);
    }

    @Test
    public void excludeGlob() {
        List<String> p = Arrays.asList("*ov31gl*", "switch.*_power_outage_memory", "light.exact");
        assertTrue(Ha.excluded("switch.xiaomi_ru_1_ov31gl_alarm_p_4_1", p));
        assertTrue(Ha.excluded("switch.socket_power_outage_memory", p));
        assertTrue(Ha.excluded("light.exact", p));
        assertTrue(!Ha.excluded("light.exact_2", p));
        assertTrue(!Ha.excluded("switch.socket", p));
        assertTrue(!Ha.excluded("sensor.x", Arrays.asList()));
    }

    @Test
    public void hideUnavailableDropsOnlyUnavailableRows() throws Exception {
        String json = "[{\"entity_id\":\"light.a\",\"state\":\"on\",\"attributes\":{\"friendly_name\":\"A\"}},"
                + "{\"entity_id\":\"switch.b\",\"state\":\"unavailable\",\"attributes\":{\"friendly_name\":\"B\"}}]";
        assertEquals(2, Ha.parse(json, Arrays.asList(), Arrays.asList(), false).size());
        List<Ha.Entity> kept = Ha.parse(json, Arrays.asList(), Arrays.asList(), true);
        assertEquals(1, kept.size());
        assertEquals("light.a", kept.get(0).id);
    }

    @Test
    public void areasAndRooms() throws Exception {
        java.util.Map<String, String> areas = Ha.parseAreas("light.a\tКухня\nsensor.t\tКухня\nswitch.b\t\nlight.c\tВанная\n\n");
        assertEquals(3, areas.size());
        String json = "["
                + "{\"entity_id\":\"light.a\",\"state\":\"on\",\"attributes\":{\"friendly_name\":\"A\",\"brightness\":128}},"
                + "{\"entity_id\":\"light.c\",\"state\":\"off\",\"attributes\":{\"friendly_name\":\"C\"}},"
                + "{\"entity_id\":\"switch.b\",\"state\":\"off\",\"attributes\":{\"friendly_name\":\"B\"}},"
                + "{\"entity_id\":\"sensor.t\",\"state\":\"22.13\",\"attributes\":{\"device_class\":\"temperature\",\"unit_of_measurement\":\"°C\"}},"
                + "{\"entity_id\":\"sensor.h\",\"state\":\"62.8\",\"attributes\":{\"device_class\":\"humidity\",\"unit_of_measurement\":\"%\"}}]";
        areas.put("sensor.h", "Кухня");
        List<Ha.Room> rooms = Ha.rooms(Ha.parse(json), areas, "Другое");
        assertEquals(Arrays.asList("Ванная", "Кухня", "Другое"),
                Arrays.asList(rooms.get(0).name, rooms.get(1).name, rooms.get(2).name));
        assertEquals("22.1° · 63%", rooms.get(1).summary());
        assertEquals(1, rooms.get(1).tiles.size());
        assertEquals("On · 50 %", rooms.get(1).tiles.get(0).value());
        assertTrue(Ha.areasCall().body.contains("area_name"));
    }

    @Test
    public void rgbLightColor() throws Exception {
        String json = "[{\"entity_id\":\"light.c\",\"state\":\"on\",\"attributes\":{\"color_mode\":\"rgb\",\"rgb_color\":[255,103,3],\"brightness\":84}},"
                + "{\"entity_id\":\"light.w\",\"state\":\"on\",\"attributes\":{\"color_mode\":\"white\",\"rgb_color\":[255,255,255]}},"
                + "{\"entity_id\":\"light.o\",\"state\":\"off\",\"attributes\":{\"color_mode\":null,\"rgb_color\":null}}]";
        List<Ha.Entity> l = Ha.parse(json);
        assertEquals(0xFFFF6703, l.get(0).color);
        assertEquals(0, l.get(1).color);
        assertEquals(0, l.get(2).color);
    }

    @Test
    public void locationName() {
        assertEquals("Home", Ha.locationName("{\"location_name\":\" Home \",\"time_zone\":\"Europe/Berlin\"}"));
        assertEquals("", Ha.locationName("not json"));
        assertEquals("", Ha.locationName("{}"));
    }

    @Test
    public void pendingUpdates() {
        String json = "[{\"entity_id\":\"update.z2m\",\"state\":\"on\",\"attributes\":{\"title\":\"Zigbee2MQTT\",\"installed_version\":\"2.14.2\",\"latest_version\":\"2.14.3\"}},"
                + "{\"entity_id\":\"update.core\",\"state\":\"off\",\"attributes\":{\"title\":\"Core\"}},"
                + "{\"entity_id\":\"update.slzb\",\"state\":\"on\",\"attributes\":{\"title\":null,\"friendly_name\":\"SLZB-06 Firmware\",\"installed_version\":\"1\",\"latest_version\":\"2\"}}]";
        List<Ha.Update> u = Ha.updates(json);
        assertEquals(2, u.size());
        assertEquals("SLZB-06 Firmware", u.get(0).title);
        assertEquals("2.14.3", u.get(1).latest);
    }

    @Test
    public void installUpdate() throws Exception {
        String json = "[{\"entity_id\":\"update.a\",\"state\":\"on\",\"attributes\":{\"title\":\"A\",\"supported_features\":29,\"in_progress\":false}},"
                + "{\"entity_id\":\"update.b\",\"state\":\"on\",\"attributes\":{\"title\":\"B\",\"supported_features\":21,\"in_progress\":true,\"update_percentage\":45}}]";
        List<Ha.Update> u = Ha.updates(json);
        assertTrue(u.get(0).canInstall() && u.get(0).canBackup() && !u.get(0).inProgress);
        assertEquals("/api/services/update/install", Ha.install(u.get(0)).path);
        assertEquals("/api/services/homeassistant/restart", Ha.restart().path);
        String states = "[{\"entity_id\":\"update.tuya_local_update\",\"state\":\"off\",\"attributes\":{\"title\":null,"
                + "\"friendly_name\":\"Tuya Local Update\",\"release_summary\":"
                + "\"<ha-alert alert-type='error'>Restart of Home Assistant required</ha-alert>\"}},"
                + "{\"entity_id\":\"update.hacs_update\",\"state\":\"off\",\"attributes\":{\"release_summary\":null}},"
                + "{\"entity_id\":\"sensor.x\",\"state\":\"1\",\"attributes\":{\"release_summary\":\"restart required\"}}]";
        assertEquals(java.util.Collections.singletonList("Tuya Local"), Ha.restartPending(states));
        assertTrue(Ha.restartPending("[]").isEmpty());
        assertTrue(Ha.running("{\"location_name\":\"Home\",\"state\":\"RUNNING\"}"));
        assertTrue(!Ha.running("{\"state\":\"STARTING\"}"));
        assertTrue(!Ha.running("502 Bad Gateway"));
        java.util.List<Ha.Entity> lights = Ha.parse("[{\"entity_id\":\"light.a\",\"state\":\"on\",\"attributes\":{}},"
                + "{\"entity_id\":\"light.b\",\"state\":\"off\",\"attributes\":{}},"
                + "{\"entity_id\":\"switch.c\",\"state\":\"on\",\"attributes\":{}},"
                + "{\"entity_id\":\"light.d\",\"state\":\"on\",\"attributes\":{}}]",
                java.util.Collections.emptyList(), java.util.Collections.singletonList("light.d"), false);
        assertEquals(1, Ha.lightsOn(lights));
        assertEquals(0, Ha.restartProgress(0));
        assertEquals(500, Ha.restartProgress(30_000));
        assertEquals(900, Ha.restartProgress(54_000));
        assertTrue(Ha.restartProgress(90_000) > 900 && Ha.restartProgress(90_000) < 990);
        assertTrue(Ha.restartProgress(600_000) < 1000);
        assertTrue(Ha.restartPending("not json").isEmpty());
        assertTrue(Ha.install(u.get(0)).body.contains("\"backup\":true"));
        assertTrue(!Ha.install(u.get(1)).body.contains("backup"));
        assertTrue(u.get(1).inProgress);
        assertEquals(45, u.get(1).percent);
    }

    @Test
    public void rgbControl() throws Exception {
        String json = "[{\"entity_id\":\"light.c\",\"state\":\"on\",\"attributes\":{\"supported_color_modes\":[\"rgb\",\"white\"],"
                + "\"color_mode\":\"rgb\",\"rgb_color\":[0,20,250],\"brightness\":128}},"
                + "{\"entity_id\":\"light.d\",\"state\":\"on\",\"attributes\":{\"supported_color_modes\":[\"brightness\"],\"brightness\":255}}]";
        List<Ha.Entity> l = Ha.parse(json);
        Ha.Entity c = l.get(0);
        assertTrue(c.colorable && c.whiteMode);
        assertTrue(!l.get(1).colorable);
        assertEquals(9, Ha.nearest(c));   // blue
        assertEquals(12, Ha.PALETTE.length);
        assertTrue(Ha.color(c, 0xFF6400).body.contains("\"rgb_color\":[255,100,0]"));
        assertTrue(Ha.color(c, 0xFFFFFF).body.contains("\"white\":128"));
    }

    @Test
    public void glanceFromCommaString() {
        assertEquals(Arrays.asList("weather.home", "sensor.e10"),
                Config.parseGlance("weather.home, sensor.e10"));
        assertTrue(Config.parseGlance("").isEmpty());
        assertTrue(Config.parseGlance(null).isEmpty());
    }
}
