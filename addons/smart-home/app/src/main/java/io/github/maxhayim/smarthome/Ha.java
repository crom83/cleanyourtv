package io.github.maxhayim.smarthome;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Home Assistant's REST API (https://developers.home-assistant.io/docs/api/rest): what GET /api/states returns,
 * turned into the rows this app shows, and the service calls its buttons make. No Android types and no network,
 * so the JVM tests cover it against the documented responses.
 */
final class Ha {
    /** At most this many rows in the list, so a large installation can't make the screen slow. */
    static final int MAX_ENTITIES = 80;
    /** At most this many fuel-price sensors in the glance header. */
    static final int MAX_PRICES = 6;

    enum Kind { WEATHER, PRICE, SCENE, TOGGLE, CLIMATE, SENSOR, OPENING, LOCK }

    static final class Entity {
        final String id;
        final Kind kind;
        final String name;
        final String state;
        /** Lights: 0 to 100, or -1. */
        final int brightness;
        final double current;
        final double target;
        final String unit;
        /** An RGB light's current colour (0xFFRRGGBB), or 0: off, in a white mode, or not an RGB light. */
        int color;
        /** The light takes a colour (supported_color_modes has rgb, rgbw, rgbww, hs or xy). */
        boolean colorable;
        /** The light has a white mode (supported_color_modes has white). */
        boolean whiteMode;

        Entity(String id, Kind kind, String name, String state, int brightness, double current, double target, String unit) {
            this.id = id;
            this.kind = kind;
            this.name = name;
            this.state = state;
            this.brightness = brightness;
            this.current = current;
            this.target = target;
            this.unit = unit;
        }

        String domain() {
            int dot = id.indexOf('.');
            return dot < 0 ? "" : id.substring(0, dot);
        }

        boolean available() {
            return !"unavailable".equals(state) && !"unknown".equals(state);
        }

        /** What the row shows on the right, in English (tests and the default). */
        String value() {
            return value(ENGLISH);
        }

        /** What the row shows on the right, in the TV's language. */
        String value(Lang lng) {
            if (!available()) return lng.unavailable();
            switch (kind) {
                case WEATHER:
                    return Double.isNaN(current) ? capital(state) : number(current) + "°";
                case PRICE:
                    try {
                        return number(Double.parseDouble(state)) + (unit.isEmpty() ? "" : " " + unit);
                    } catch (NumberFormatException e) {
                        return state;
                    }
                case SCENE:
                    return lng.run();
                case TOGGLE:
                    boolean on = "on".equals(state);
                    return on && brightness >= 0 ? lng.onAt(brightness) : on ? lng.on() : lng.off();
                case CLIMATE:
                    String now = Double.isNaN(current) ? "" : number(current) + unit;
                    String set = Double.isNaN(target) ? "" : number(target) + unit;
                    return now.isEmpty() ? set : set.isEmpty() ? now : now + " → " + set;
                case SENSOR:
                    try {
                        return number(Double.parseDouble(state)) + (unit.isEmpty() ? "" : " " + unit).replace(" °", "°");
                    } catch (NumberFormatException e) {
                        return state;
                    }
                case OPENING:
                    return "on".equals(state) ? lng.open() : lng.closed();
                case LOCK:
                    return "locked".equals(state) ? lng.locked() : "unlocked".equals(state) ? lng.unlocked() : capital(state);
                default:
                    return state;
            }
        }
    }

    /**
     * The language of what the screen shows for states, sections and errors. {@link #ENGLISH} is the default and what the
     * JVM tests read; the app passes its string resources ({@code Texts}), so the TV's language is used.
     */
    interface Lang {
        String unavailable();
        String run();
        String on();
        String off();
        /** On, with brightness 0 to 100. */
        String onAt(int brightness);
        String open();
        String closed();
        String locked();
        String unlocked();
        String section(Kind k);
        String refusedToken();
        String notTheApi();
        String unreachable();
        String httpError(int code);
    }

    static final Lang ENGLISH = new Lang() {
        public String unavailable() { return "Unavailable"; }
        public String run() { return "Run"; }
        public String on() { return "On"; }
        public String off() { return "Off"; }
        public String onAt(int b) { return "On · " + b + " %"; }
        public String open() { return "Open"; }
        public String closed() { return "Closed"; }
        public String locked() { return "Locked"; }
        public String unlocked() { return "Unlocked"; }
        public String section(Kind k) {
            switch (k) {
                case SCENE: return "Scenes";
                case TOGGLE: return "Lights and switches";
                case CLIMATE: return "Climate";
                case SENSOR: return "Sensors";
                case OPENING: return "Doors and windows";
                default: return "Locks";
            }
        }
        public String refusedToken() { return "Home Assistant refused the access token."; }
        public String notTheApi() { return "That address answers, but it isn't Home Assistant's API."; }
        public String unreachable() { return "Can't reach Home Assistant at that address."; }
        public String httpError(int code) { return "Home Assistant answered with error " + code + "."; }
    };

    /**
     * What the glance header shows: one weather entity and up to {@link #MAX_PRICES} fuel prices.
     * {@link #icon} names the weather drawable in the header.
     */
    static final class Glance {
        final Entity weather;
        final List<Entity> prices;
        /** One line, for example "21° · E10 1.69 €/L · Diesel 1.55 €/L". Empty when nothing to show. */
        final String text;
        /** clear, cloudy, rain, snow, storm, fog, night, or empty. */
        final String icon;

        Glance(Entity weather, List<Entity> prices) {
            this.weather = weather;
            this.prices = Collections.unmodifiableList(prices);
            this.icon = weather == null || !weather.available() ? "" : weatherIcon(weather.state);
            this.text = line(weather, prices);
        }

        boolean empty() {
            return text.isEmpty();
        }

        private static String line(Entity weather, List<Entity> prices) {
            StringBuilder b = new StringBuilder();
            if (weather != null && weather.available() && !Double.isNaN(weather.current)) {
                b.append(number(weather.current)).append('°');
            }
            for (Entity p : prices) {
                if (!p.available()) continue;
                if (b.length() > 0) b.append(" · ");
                b.append(shortName(p.name));
                try {
                    b.append(' ').append(number(Double.parseDouble(p.state)));
                } catch (NumberFormatException e) {
                    b.append(' ').append(p.state);
                }
                if (p.unit != null && !p.unit.isEmpty()) b.append(' ').append(p.unit);
            }
            return b.toString();
        }

        /** First word of the friendly name, capped so the header line stays short. */
        private static String shortName(String name) {
            String n = name == null ? "" : name.trim();
            int space = n.indexOf(' ');
            if (space > 0) n = n.substring(0, space);
            return n.length() > 12 ? n.substring(0, 12) : n;
        }
    }

    /** One service call: POST path with body. */
    static final class Call {
        final String path;
        final String body;

        Call(String path, String body) {
            this.path = path;
            this.body = body;
        }
    }

    private Ha() {
    }

    /** The rows, sorted into sections (scenes, lights and switches, climate, sensors, doors and windows, locks). */
    static List<Entity> parse(String json) throws JSONException {
        return parse(json, Collections.emptyList());
    }

    /**
     * The same, with the glance header's entities (picked by {@code glanceIds} as in {@link #glance}) kept ahead of
     * the cap: one weather, up to {@link #MAX_PRICES} prices, then up to {@link #MAX_ENTITIES} list rows. Many price
     * or weather entities can't push the switches out.
     */
    static List<Entity> parse(String json, List<String> glanceIds) throws JSONException {
        return parse(json, glanceIds, Collections.emptyList(), false);
    }

    /** The same, without entities whose entity_id matches one of the {@code exclude} patterns. */
    static List<Entity> parse(String json, List<String> glanceIds, List<String> exclude, boolean hideUnavailable)
            throws JSONException {
        JSONArray all = new JSONArray(json);
        List<Entity> out = new ArrayList<>();
        for (int i = 0; i < all.length(); i++) {
            JSONObject o = all.optJSONObject(i);
            if (o == null) continue;
            Entity e = entity(o);
            if (e != null && !excluded(e.id, exclude)) out.add(e);
        }
        out.sort((a, b) -> a.kind != b.kind ? a.kind.compareTo(b.kind) : a.name.compareToIgnoreCase(b.name));
        Glance header = glance(out, glanceIds);
        List<Entity> kept = new ArrayList<>();
        if (header.weather != null) kept.add(header.weather);
        kept.addAll(header.prices);
        int rows = 0;
        for (Entity e : out) {
            if (!inList(e)) continue;
            if (hideUnavailable && !e.available()) continue;
            if (rows++ == MAX_ENTITIES) break;
            kept.add(e);
        }
        return kept;
    }

    /**
     * Weather and fuel prices for the glance header. With {@code ids} empty: the first available weather
     * entity and up to {@link #MAX_PRICES} price sensors. With ids set: those entities, in that order.
     */
    static Glance glance(List<Entity> all, List<String> ids) {
        if (ids != null && !ids.isEmpty()) {
            Entity weather = null;
            List<Entity> prices = new ArrayList<>();
            for (String id : ids) {
                Entity e = find(all, id);
                if (e == null) continue;
                if (e.kind == Kind.WEATHER && weather == null) weather = e;
                else if (e.kind == Kind.PRICE && prices.size() < MAX_PRICES) prices.add(e);
            }
            return new Glance(weather, prices);
        }
        Entity weather = null;
        List<Entity> prices = new ArrayList<>();
        for (Entity e : all) {
            if (e.kind == Kind.WEATHER && weather == null && e.available()) weather = e;
            else if (e.kind == Kind.PRICE && prices.size() < MAX_PRICES) prices.add(e);
        }
        if (weather == null) {
            for (Entity e : all) {
                if (e.kind == Kind.WEATHER) {
                    weather = e;
                    break;
                }
            }
        }
        return new Glance(weather, prices);
    }

    /** A pattern with * (any characters), ignoring case; without * it is an exact entity_id. */
    static boolean excluded(String id, List<String> patterns) {
        if (patterns == null || id == null) return false;
        for (String p : patterns) {
            StringBuilder re = new StringBuilder("(?i)");
            for (String part : p.split("\\*", -1)) {
                if (re.length() > 4) re.append(".*");
                re.append(java.util.regex.Pattern.quote(part));
            }
            if (id.matches(re.toString())) return true;
        }
        return false;
    }

    /** Rows for the remote list: weather and prices live in the glance header only. */
    static boolean inList(Entity e) {
        return e.kind != Kind.WEATHER && e.kind != Kind.PRICE;
    }

    /**
     * Closed vocabulary for the Home line and the in-app icon: clear, cloudy, rain, snow, storm, fog, night.
     * Unknown Home Assistant states map to cloudy.
     */
    static String weatherIcon(String state) {
        if (state == null) return "cloudy";
        switch (state) {
            case "sunny":
            case "clear":
                return "clear";
            case "clear-night":
                return "night";
            case "cloudy":
            case "partlycloudy":
            case "windy":
            case "windy-variant":
                return "cloudy";
            case "fog":
                return "fog";
            case "rainy":
            case "pouring":
            case "hail":
                return "rain";
            case "snowy":
            case "snowy-rainy":
                return "snow";
            case "lightning":
            case "lightning-rainy":
                return "storm";
            default:
                return "cloudy";
        }
    }

    static boolean priceUnit(String unit) {
        if (unit == null) return false;
        String u = unit.trim().toUpperCase(Locale.ROOT).replace(" ", "");
        return u.equals("€") || u.equals("EUR") || u.equals("€/L") || u.equals("EUR/L")
                || u.equals("€/LITRE") || u.equals("EUR/LITRE");
    }

    static Entity entity(JSONObject o) {
        String id = o.optString("entity_id", "");
        String state = o.optString("state", "");
        JSONObject a = o.optJSONObject("attributes");
        if (a == null) a = new JSONObject();
        int dot = id.indexOf('.');
        if (dot <= 0) return null;
        String domain = id.substring(0, dot);
        String name = a.optString("friendly_name", id.substring(dot + 1).replace('_', ' '));
        String deviceClass = a.optString("device_class", "");
        String unit = a.optString("unit_of_measurement", "");
        switch (domain) {
            case "weather":
                return new Entity(id, Kind.WEATHER, name, state, -1, a.optDouble("temperature", Double.NaN),
                        Double.NaN, "°");
            case "scene":
                return new Entity(id, Kind.SCENE, name, state, -1, Double.NaN, Double.NaN, "");
            case "light":
                int b = a.has("brightness") && !a.isNull("brightness") ? (int) Math.round(a.optDouble("brightness", 0) * 100 / 255) : -1;
                Entity light = new Entity(id, Kind.TOGGLE, name, state, b, Double.NaN, Double.NaN, "");
                light.color = rgb(state, a);
                JSONArray modes = a.optJSONArray("supported_color_modes");
                if (modes != null) {
                    for (int i = 0; i < modes.length(); i++) {
                        String m = modes.optString(i);
                        if (m.equals("rgb") || m.equals("rgbw") || m.equals("rgbww") || m.equals("hs") || m.equals("xy")) {
                            light.colorable = true;
                        }
                        if (m.equals("white")) light.whiteMode = true;
                    }
                }
                return light;
            case "switch":
            case "fan":
            case "input_boolean":
                return new Entity(id, Kind.TOGGLE, name, state, -1, Double.NaN, Double.NaN, "");
            case "climate":
                return new Entity(id, Kind.CLIMATE, name, state, -1, a.optDouble("current_temperature", Double.NaN),
                        a.optDouble("temperature", Double.NaN), "°");
            case "sensor":
                if (priceUnit(unit)) {
                    return new Entity(id, Kind.PRICE, name, state, -1, Double.NaN, Double.NaN, unit);
                }
                if (!deviceClass.equals("temperature") && !deviceClass.equals("humidity")) return null;
                return new Entity(id, Kind.SENSOR, name, state, -1, Double.NaN, Double.NaN, unit);
            case "binary_sensor":
                if (!deviceClass.equals("door") && !deviceClass.equals("window") && !deviceClass.equals("garage_door")
                        && !deviceClass.equals("opening")) return null;
                return new Entity(id, Kind.OPENING, name, state, -1, Double.NaN, Double.NaN, "");
            case "lock":
                return new Entity(id, Kind.LOCK, name, state, -1, Double.NaN, Double.NaN, "");
            default:
                return null;
        }
    }

    /** The colour from rgb_color, only while the light is on in a colour mode (not white or color_temp). */
    static int rgb(String state, JSONObject a) {
        if (!"on".equals(state)) return 0;
        String mode = a.optString("color_mode", "");
        if (mode.equals("white") || mode.equals("color_temp") || mode.equals("brightness") || mode.equals("onoff")) return 0;
        JSONArray c = a.optJSONArray("rgb_color");
        if (c == null || c.length() != 3) return 0;
        int r = c.optInt(0, -1), g = c.optInt(1, -1), b = c.optInt(2, -1);
        if (r < 0 || g < 0 || b < 0) return 0;
        return 0xFF000000 | (Math.min(r, 255) << 16) | (Math.min(g, 255) << 8) | Math.min(b, 255);
    }

    /** location_name from GET /api/config (the home's name in Home Assistant), or "". */
    static String locationName(String json) {
        try {
            return new JSONObject(json).optString("location_name", "").trim();
        } catch (JSONException | RuntimeException e) {
            return "";
        }
    }

    /**
     * The restart window's bar, 0 to 1000, aimed at a minute: linear to 90 % at 54 s, then creeping on towards 99 %
     * without ever filling, so a slow start still moves and only Home Assistant coming back completes it.
     */
    static int restartProgress(long elapsedMs) {
        double t = Math.max(0, elapsedMs) / 60_000.0;
        double p = t < 0.9 ? t : 0.9 + 0.09 * (1 - Math.exp(-(t - 0.9) * 3));
        return (int) Math.round(p * 1000);
    }

    /** Lights that are on, for the launcher tile: light.* rows in state on, after the owner's exclude list. */
    static int lightsOn(List<Entity> entities) {
        int n = 0;
        for (Entity e : entities) if (e.domain().equals("light") && "on".equals(e.state)) n++;
        return n;
    }

    /** Whether /api/config says Home Assistant is fully up ("state": "RUNNING"), not still starting or stopping. */
    static boolean running(String json) {
        try {
            return "RUNNING".equals(new JSONObject(json).optString("state", ""));
        } catch (JSONException | RuntimeException e) {
            return false;
        }
    }

    /** A pending update: an update.* entity in state on. */
    static final class Update {
        final String id;
        final String title;
        final String installed;
        final String latest;
        /** supported_features: 1 install, 4 progress, 8 backup. */
        int features;
        boolean inProgress;
        /** Install progress 0 to 100, or -1. */
        int percent = -1;

        Update(String id, String title, String installed, String latest) {
            this.id = id;
            this.title = title;
            this.installed = installed;
            this.latest = latest;
        }

        boolean canInstall() {
            return (features & 1) != 0;
        }

        boolean canBackup() {
            return (features & 8) != 0;
        }
    }

    /** The colours offered for an RGB light, as 0xRRGGBB, in two rows of six; the first is white. */
    static final int[] PALETTE = {
            0xFFFFFF, 0xFFB46E, 0xFF0000, 0xFF6400, 0xFFDC00, 0x96FF00,
            0x00FF00, 0x00FFC8, 0x0096FF, 0x0000FF, 0x9600FF, 0xFF0096,
    };

    /** Sets a light's colour. White goes to the light's white mode when it has one. */
    static Call color(Entity e, int rgb) {
        if (rgb == 0xFFFFFF && e.whiteMode) {
            int b = e.brightness > 0 ? Math.round(e.brightness * 255 / 100f) : 255;
            return call("light", "turn_on", e.id, "\"white\":" + b);
        }
        return call("light", "turn_on", e.id, "\"rgb_color\":[" + ((rgb >> 16) & 0xFF) + "," + ((rgb >> 8) & 0xFF) + ","
                + (rgb & 0xFF) + "]");
    }

    /** The palette entry closest to the light's colour; white when it has none. */
    static int nearest(Entity e) {
        if (e.color == 0) return 0;
        int best = 0;
        long bestD = Long.MAX_VALUE;
        for (int i = 0; i < PALETTE.length; i++) {
            int p = PALETTE[i];
            long dr = ((p >> 16) & 0xFF) - ((e.color >> 16) & 0xFF);
            long dg = ((p >> 8) & 0xFF) - ((e.color >> 8) & 0xFF);
            long db = (p & 0xFF) - (e.color & 0xFF);
            long d = dr * dr + dg * dg + db * db;
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    /** Installs the update, with a backup first where Home Assistant supports one for it. */
    /** Restarts Home Assistant Core; it checks the configuration first and refuses to restart with a broken one. */
    static Call restart() {
        return new Call("/api/services/homeassistant/restart", "{}");
    }

    static Call install(Update u) {
        return call("update", "install", u.id, u.canBackup() ? "\"backup\":true" : null);
    }

    /** Pending updates from /api/states, by title. The exclude patterns don't apply to them. */
    static List<Update> updates(String json) {
        List<Update> out = new ArrayList<>();
        try {
            JSONArray all = new JSONArray(json);
            for (int i = 0; i < all.length(); i++) {
                JSONObject o = all.optJSONObject(i);
                if (o == null) continue;
                String id = o.optString("entity_id", "");
                if (!id.startsWith("update.") || !"on".equals(o.optString("state"))) continue;
                JSONObject a = o.optJSONObject("attributes");
                if (a == null) a = new JSONObject();
                String title = a.optString("title", "");
                if (title.isEmpty() || a.isNull("title")) title = a.optString("friendly_name", id);
                Update u = new Update(id, title, a.optString("installed_version", ""), a.optString("latest_version", ""));
                u.features = a.optInt("supported_features", 0);
                Object p = a.opt("in_progress");
                u.inProgress = Boolean.TRUE.equals(p) || p instanceof Number;
                if (a.has("update_percentage") && !a.isNull("update_percentage")) {
                    u.percent = (int) Math.round(a.optDouble("update_percentage", -1));
                } else if (p instanceof Number) {
                    u.percent = ((Number) p).intValue();
                }
                out.add(u);
            }
        } catch (JSONException e) {
            return out;
        }
        out.sort((x, y) -> x.title.compareToIgnoreCase(y.title));
        return out;
    }

    /**
     * What waits for a restart of Home Assistant: update entities whose release summary says so, as HACS writes
     * "Restart of Home Assistant required" after it installs an integration. Names without a trailing " Update".
     */
    static List<String> restartPending(String json) {
        List<String> out = new ArrayList<>();
        try {
            JSONArray all = new JSONArray(json);
            for (int i = 0; i < all.length(); i++) {
                JSONObject o = all.optJSONObject(i);
                if (o == null || !o.optString("entity_id", "").startsWith("update.")) continue;
                JSONObject a = o.optJSONObject("attributes");
                if (a == null || a.isNull("release_summary")) continue;
                String summary = a.optString("release_summary", "").toLowerCase(java.util.Locale.ROOT);
                if (!summary.contains("restart") || !summary.contains("required")) continue;
                String name = a.isNull("title") ? "" : a.optString("title", "");
                if (name.isEmpty()) name = a.optString("friendly_name", o.optString("entity_id"));
                name = name.replaceFirst("(?i)\\s+update$", "");
                if (!out.contains(name)) out.add(name);
            }
        } catch (JSONException e) {
            return out;
        }
        return out;
    }

    // ---- Rooms (Home Assistant areas) for the tile grid ----

    /** Template for POST /api/template: one "entity_id<TAB>area" line per entity. */
    static final String AREAS_TEMPLATE =
            "{% for s in states %}{{ s.entity_id }}\t{{ area_name(s.entity_id) or '' }}\n{% endfor %}";

    static Call areasCall() {
        return new Call("/api/template", "{\"template\":" + JSONObject.quote(AREAS_TEMPLATE) + "}");
    }

    /** The template's answer as entity_id to area; entities without an area are left out. */
    static Map<String, String> parseAreas(String text) {
        Map<String, String> out = new HashMap<>();
        if (text == null) return out;
        for (String line : text.split("\n")) {
            int tab = line.indexOf('\t');
            if (tab <= 0) continue;
            String id = line.substring(0, tab).trim();
            String area = line.substring(tab + 1).trim();
            if (!id.isEmpty() && !area.isEmpty()) out.put(id, area);
        }
        return out;
    }

    /** A room: tiles (switchable things, scenes, climate, locks, doors) and the sensors for its header line. */
    static final class Room {
        final String name;
        final List<Entity> tiles = new ArrayList<>();
        final List<Entity> sensors = new ArrayList<>();

        Room(String name) {
            this.name = name;
        }

        /** "22.1° · 63%": temperatures, then humidity; unavailable sensors are skipped. */
        String summary() {
            List<String> temps = new ArrayList<>(), hums = new ArrayList<>();
            for (Entity s : sensors) {
                if (!s.available()) continue;
                double v;
                try {
                    v = Double.parseDouble(s.state);
                } catch (NumberFormatException e) {
                    continue;
                }
                if (s.unit.contains("%")) hums.add(number(Math.round(v)) + "%");
                else temps.add(number(Math.round(v * 10) / 10.0) + "°");
            }
            List<String> all = new ArrayList<>(temps);
            all.addAll(hums);
            return String.join(" · ", all);
        }
    }

    /** The list rows by room, rooms in alphabetical order, entities without an area last under {@code other}. */
    static List<Room> rooms(List<Entity> entities, Map<String, String> areas, String other) {
        Map<String, Room> byName = new LinkedHashMap<>();
        Room rest = new Room(other);
        for (Entity e : entities) {
            if (!inList(e)) continue;
            String a = areas == null ? null : areas.get(e.id);
            Room r = a == null ? rest : byName.computeIfAbsent(a, Room::new);
            if (e.kind == Kind.SENSOR) r.sensors.add(e);
            else r.tiles.add(e);
        }
        List<Room> out = new ArrayList<>(byName.values());
        Collator c = Collator.getInstance();
        out.sort((x, y) -> c.compare(x.name, y.name));
        out.add(rest);
        out.removeIf(r -> r.tiles.isEmpty() && r.sensors.isEmpty());
        return out;
    }

    /** OK on a row; null when the row does nothing (sensors, and locks: a TV remote never unlocks a door). */
    static Call press(Entity e) {
        if (!e.available()) return null;
        switch (e.kind) {
            case SCENE:
                return call("scene", "turn_on", e.id, null);
            case TOGGLE:
                return call("homeassistant", "toggle", e.id, null);
            default:
                return null;
        }
    }

    /** Left (-1) or Right (+1) on a row: dims a light by 10 %, or moves a thermostat by half a degree. */
    static Call step(Entity e, int direction) {
        if (!e.available()) return null;
        if (e.kind == Kind.TOGGLE && e.domain().equals("light")) {
            return call("light", "turn_on", e.id, "\"brightness_step_pct\":" + (direction * 10));
        }
        if (e.kind == Kind.CLIMATE && !Double.isNaN(e.target)) {
            return call("climate", "set_temperature", e.id, "\"temperature\":" + number(e.target + direction * 0.5));
        }
        return null;
    }

    private static Call call(String domain, String service, String entity, String extra) {
        String body = "{\"entity_id\":" + JSONObject.quote(entity) + (extra == null ? "" : "," + extra) + "}";
        return new Call("/api/services/" + domain + "/" + service, body);
    }

    static String section(Kind k) {
        return ENGLISH.section(k);
    }

    /** "http://192.168.1.10:8123" from what was typed: a scheme added when missing, trailing slashes dropped. */
    static String normalizeUrl(String typed) {
        if (typed == null) return "";
        String u = typed.trim();
        if (u.isEmpty()) return "";
        if (!u.contains("://")) u = "http://" + u;
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u;
    }

    static boolean validUrl(String url) {
        return url.matches("https?://[^\\s/?#]+(:[0-9]{1,5})?(/[^\\s?#]*)?");
    }

    /** What a failed request means for the person in front of the TV. */
    static String problem(int code) {
        return problem(code, ENGLISH);
    }

    static String problem(int code, Lang lng) {
        if (code == 401 || code == 403) return lng.refusedToken();
        if (code == 404) return lng.notTheApi();
        if (code <= 0) return lng.unreachable();
        return lng.httpError(code);
    }

    static String number(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.1f", v);
    }

    private static String capital(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static Entity find(List<Entity> all, String id) {
        for (Entity e : all) if (e.id.equals(id)) return e;
        return null;
    }
}
