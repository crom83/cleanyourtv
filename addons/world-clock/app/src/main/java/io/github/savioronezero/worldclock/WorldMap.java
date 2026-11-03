package io.github.savioronezero.worldclock;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The screensaver's world map: every country from Natural Earth (public domain), the countries of the saved clocks
 * lit up, a warm light on each city (IANA zone1970.tab coordinates, public domain) and night's shadow where the sun
 * has set. Parsed once into paths in the Equal Earth projection; drawn when the minute changes, not per frame.
 */
final class WorldMap {
    private static final int OCEAN = 0xFF08101A, LAND = 0xFF192C3B, LIT = 0xFF2E6670, LIT_EDGE = 0xFF5FB3BC,
            BORDER = 0xFF0E1C27, NIGHT = 0x8C000000, CITY = 0xFFFFD27A;
    private final Map<String, Path> countries = new HashMap<>();
    private final Path outline = new Path();
    private final Set<String> lit = new HashSet<>();
    private final List<float[]> cities = new ArrayList<>();
    private final Path night = new Path();
    private final float[] p = new float[2];
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);

    WorldMap(Context context, List<String> ids) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.getResources().openRawResource(R.raw.world_countries), StandardCharsets.US_ASCII))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.length() < 4) continue;
                String code = line.substring(0, 2);
                Path shape = countries.get(code);
                if (shape == null) {
                    shape = new Path();
                    shape.setFillType(Path.FillType.EVEN_ODD);   // lakes and enclaves stay holes
                    countries.put(code, shape);
                }
                boolean first = true;
                for (String point : line.substring(3).split(" ")) {
                    int comma = point.indexOf(',');
                    MapMath.project(Double.parseDouble(point.substring(0, comma)),
                            Double.parseDouble(point.substring(comma + 1)), p);
                    if (first) { shape.moveTo(p[0], p[1]); first = false; }
                    else shape.lineTo(p[0], p[1]);
                }
                shape.close();
            }
        } catch (IOException | RuntimeException unavailable) {
            countries.clear();   // a damaged optional background must never take the clocks down
        }
        // The world's edge: both meridians at 180 degrees, pole to pole
        for (int lat = -90; lat <= 90; lat += 5) {
            MapMath.project(-180, lat, p);
            if (lat == -90) outline.moveTo(p[0], p[1]); else outline.lineTo(p[0], p[1]);
        }
        for (int lat = 90; lat >= -90; lat -= 5) {
            MapMath.project(180, lat, p);
            outline.lineTo(p[0], p[1]);
        }
        outline.close();
        Map<String, double[]> places = places(context);
        for (String id : ids) {
            String zone = ClockModel.LOCAL.equals(id) ? ZoneId.systemDefault().getId() : id;
            try {
                String region = android.icu.util.TimeZone.getRegion(zone);
                if (region != null && region.length() == 2) lit.add(region);
            } catch (RuntimeException ignored) {
                // UTC and the like belong to no country
            }
            double[] at = places.get(zone);
            if (at != null) {
                float[] xy = new float[2];
                MapMath.project(at[1], at[0], xy);
                cities.add(xy);
            }
        }
    }

    /** Zone id to {latitude, longitude}, from the bundled table. */
    private static Map<String, double[]> places(Context context) {
        Map<String, double[]> out = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.getResources().openRawResource(R.raw.zone_places), StandardCharsets.US_ASCII))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] f = line.split(" ");
                if (f.length == 3) out.put(f[0], new double[] {Double.parseDouble(f[1]), Double.parseDouble(f[2])});
            }
        } catch (IOException | RuntimeException ignored) {
            // no lights on cities, the map still draws
        }
        return out;
    }

    void draw(Canvas canvas, Paint paint, int width, int height) {
        float scale = (float) Math.min(width * 0.94 / (2 * MapMath.HALF_W), height * 0.90 / (2 * MapMath.HALF_H));
        canvas.save();
        canvas.translate(width / 2f, height / 2f);
        canvas.scale(scale, scale);
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(OCEAN);
        canvas.drawPath(outline, paint);
        for (Map.Entry<String, Path> c : countries.entrySet()) {
            paint.setColor(lit.contains(c.getKey()) ? LIT : LAND);
            canvas.drawPath(c.getValue(), paint);
        }
        // Thin borders between countries
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.2f / scale);
        paint.setColor(BORDER);
        for (Path c : countries.values()) canvas.drawPath(c, paint);
        paint.setStyle(Paint.Style.FILL);
        // Night where the sun has set, clipped to the world's edge
        nightPath(System.currentTimeMillis());
        canvas.save();
        canvas.clipPath(outline);
        paint.setColor(NIGHT);
        canvas.drawPath(night, paint);
        canvas.restore();
        // The lit countries' edge over the night too, so they still show where it is dark
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.6f / scale);
        paint.setColor(LIT_EDGE);
        for (String code : lit) {
            Path c = countries.get(code);
            if (c != null) canvas.drawPath(c, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        // A warm light on each city
        float r = 9f / scale;
        for (float[] c : cities) {
            glow.setShader(new RadialGradient(c[0], c[1], r * 2.6f, 0x99FFD27A, 0x00FFD27A, Shader.TileMode.CLAMP));
            canvas.drawCircle(c[0], c[1], r * 2.6f, glow);
            paint.setColor(CITY);
            canvas.drawCircle(c[0], c[1], r * 0.45f, paint);
        }
        canvas.restore();
    }

    /** The night side as one path: the day/night line from west to east, then round by the dark pole. */
    private void nightPath(long now) {
        double[] sun = MapMath.subsolar(now);
        night.reset();
        boolean first = true;
        for (int lon = -180; lon <= 180; lon += 3) {
            MapMath.project(lon, MapMath.terminator(lon, sun[0], sun[1]), p);
            if (first) { night.moveTo(p[0], p[1]); first = false; }
            else night.lineTo(p[0], p[1]);
        }
        double pole = sun[0] > 0 ? -90 : 90;
        MapMath.project(180, pole, p);
        night.lineTo(p[0], p[1]);
        MapMath.project(-180, pole, p);
        night.lineTo(p[0], p[1]);
        night.close();
    }
}
