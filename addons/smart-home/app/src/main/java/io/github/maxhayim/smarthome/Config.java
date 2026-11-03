package io.github.maxhayim.smarthome;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The server address, token and optional glance entity ids, in the app's own storage (backups are off). They
 * arrive typed on the TV, or as config.json pushed by install.sh, which is read once and deleted.
 */
final class Config {
    static final String PUSHED = "config.json";

    final String url;
    final String token;
    /** Entity ids for the glance header, in order; empty means auto-pick weather and fuel prices. */
    final List<String> glance;
    /** entity_id patterns not to show (* is any characters), for example "*_power_outage_memory". */
    final List<String> exclude;
    /** Hide unavailable (unpowered) devices until they come back. */
    final boolean hideUnavailable;
    /** update.* patterns that are shown but can't be installed from the TV. */
    List<String> noInstall = Collections.emptyList();

    Config(String url, String token, List<String> glance) {
        this(url, token, glance, null, false);
    }

    Config(String url, String token, List<String> glance, List<String> exclude, boolean hideUnavailable) {
        this.hideUnavailable = hideUnavailable;
        this.url = url;
        this.token = token;
        this.glance = Collections.unmodifiableList(glance == null ? Collections.emptyList() : glance);
        this.exclude = Collections.unmodifiableList(exclude == null ? Collections.emptyList() : exclude);
    }

    boolean complete() {
        return !url.isEmpty() && !token.isEmpty();
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("server", Context.MODE_PRIVATE);
    }

    static Config load(Context c) {
        SharedPreferences p = prefs(c);
        Config cfg = new Config(p.getString("url", ""), p.getString("token", ""), readGlance(p.getString("glance", "")),
                readGlance(p.getString("exclude", "")), p.getBoolean("hide_unavailable", false));
        cfg.noInstall = Collections.unmodifiableList(readGlance(p.getString("no_install", "")));
        return cfg;
    }

    static void saveUrl(Context c, String url) {
        prefs(c).edit().putString("url", Ha.normalizeUrl(url)).apply();
    }

    static void saveToken(Context c, String token) {
        prefs(c).edit().putString("token", token == null ? "" : token.trim()).apply();
    }

    static void saveGlance(Context c, List<String> ids) {
        prefs(c).edit().putString("glance", joinGlance(ids)).apply();
    }

    static void saveExclude(Context c, List<String> patterns) {
        prefs(c).edit().putString("exclude", joinGlance(patterns)).apply();
    }

    /** Takes a pushed config.json ({"url": ..., "token": ..., "glance": [...]}) if there is one, and deletes it. */
    static boolean importPushed(Context c) {
        File dir = c.getExternalFilesDir(null);
        File f = dir == null ? null : new File(dir, PUSHED);
        if (f == null || !f.isFile()) return false;
        try {
            JSONObject o = new JSONObject(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
            if (o.has("url")) saveUrl(c, o.optString("url"));
            if (o.has("token")) saveToken(c, o.optString("token"));
            if (o.has("glance")) saveGlance(c, parseGlance(o.opt("glance")));
            if (o.has("exclude")) saveExclude(c, parseGlance(o.opt("exclude")));
            if (o.has("no_install")) prefs(c).edit().putString("no_install", joinGlance(parseGlance(o.opt("no_install")))).apply();
            if (o.has("hide_unavailable")) prefs(c).edit().putBoolean("hide_unavailable", o.optBoolean("hide_unavailable")).apply();
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }

    static List<String> parseGlance(Object raw) {
        List<String> out = new ArrayList<>();
        if (raw instanceof JSONArray) {
            JSONArray a = (JSONArray) raw;
            for (int i = 0; i < a.length(); i++) {
                String id = a.optString(i, "").trim();
                if (!id.isEmpty() && !out.contains(id)) out.add(id);
            }
        } else if (raw instanceof String) {
            out.addAll(readGlance((String) raw));
        }
        return out;
    }

    private static List<String> readGlance(String joined) {
        List<String> out = new ArrayList<>();
        if (joined == null || joined.isEmpty()) return out;
        for (String part : joined.split(",")) {
            String id = part.trim();
            if (!id.isEmpty() && !out.contains(id)) out.add(id);
        }
        return out;
    }

    private static String joinGlance(List<String> ids) {
        if (ids == null || ids.isEmpty()) return "";
        StringBuilder b = new StringBuilder();
        for (String id : ids) {
            if (id == null || id.trim().isEmpty()) continue;
            if (b.length() > 0) b.append(',');
            b.append(id.trim());
        }
        return b.toString();
    }
}
