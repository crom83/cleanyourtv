package io.github.savioronezero.worldclock;

import android.content.Context;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

/** Where a clock's city is: IANA zone1970.tab coordinates (public domain), or an alias's own; read once. */
final class Places {
    private static Map<String, double[]> zones;

    private Places() {}

    /** {latitude, longitude} for a clock id (local, zone or "Zone#City"), or null when unknown. */
    static synchronized double[] of(Context c, String id) {
        String[] alias = ClockModel.alias(id);
        if (alias != null) return new double[] {Double.parseDouble(alias[3]), Double.parseDouble(alias[4])};
        if (zones == null) zones = load(c);
        return zones.get(ClockModel.LOCAL.equals(id) ? ZoneId.systemDefault().getId() : ClockModel.zone(id));
    }

    private static Map<String, double[]> load(Context context) {
        Map<String, double[]> out = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.getResources().openRawResource(R.raw.zone_places), StandardCharsets.US_ASCII))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] f = line.split(" ");
                if (f.length == 3) out.put(f[0], new double[] {Double.parseDouble(f[1]), Double.parseDouble(f[2])});
            }
        } catch (IOException | RuntimeException ignored) {
            // no places: no lights on the map and no sunrise line, everything else still works
        }
        return out;
    }
}
