package io.github.sumyabazar1.weather;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.view.View;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The line on this app's Clean Your TV tile (the launcher's LiveCards): the temperature now in the first saved
 * city, with its weather icon. The launcher asks only while Home is on screen; the answer is the last known
 * reading at once, and a fresh forecast when that is older than {@link Forecast#REFRESH_MS}, announced with
 * notifyChange when it lands. The weather screen also publishes what it fetched for that city.
 */
public class LiveCardProvider extends ContentProvider {
    private static final String PREFS = "live_card";
    private static final AtomicBoolean fetching = new AtomicBoolean();

    static Uri uri(Context c) {
        return Uri.parse("content://" + c.getPackageName() + ".card/card");
    }

    /** The first saved city: the one the tile is about. Null before any city is chosen. */
    private static Cities.City home(Context c) {
        SharedPreferences place = c.getSharedPreferences(WeatherActivity.PREFS, Context.MODE_PRIVATE);
        List<Cities.City> cities = Cities.parse(place.getString("cities", null));
        if (!cities.isEmpty()) return cities.get(0);
        if (!place.contains("name")) return null;
        return new Cities.City(place.getString("name", ""), Double.longBitsToDouble(place.getLong("lat", 0)),
                Double.longBitsToDouble(place.getLong("lon", 0)));
    }

    private static String key(Cities.City city) {
        return city.name + "@" + city.latitude + "," + city.longitude;
    }

    /** Saves a reading if it is for the first city, and tells the launcher. */
    static void publish(Context c, Cities.City city, Forecast.Report r) {
        Cities.City home = home(c);
        if (home == null || city == null || !home.samePlace(city)) return;
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("key", key(home))
                .putLong("temp", Double.doubleToRawLongBits(r.tempC))
                .putInt("code", r.code)
                .putBoolean("day", r.day)
                .putLong("at", System.currentTimeMillis())
                .apply();
        changed(c);
    }

    /** Units or the order of cities changed: the launcher reads the line again. */
    static void changed(Context c) {
        c.getContentResolver().notifyChange(uri(c), null);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
        Context c = getContext();
        MatrixCursor out = new MatrixCursor(new String[] {"text", "icon"});
        if (c == null || !"card".equals(uri.getLastPathSegment())) return out;
        Cities.City home = home(c);
        if (home == null) return out;
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        boolean known = key(home).equals(p.getString("key", ""));
        if (!known || System.currentTimeMillis() - p.getLong("at", 0) > Forecast.REFRESH_MS) refresh(c, home);
        if (!known) return out;   // another first city now: nothing until its forecast is in
        SharedPreferences options = c.getSharedPreferences("options", Context.MODE_PRIVATE);
        boolean fahrenheit = options.getBoolean("fahrenheit",
                "f".equalsIgnoreCase(c.getString(R.string.temperature_unit).trim()));
        double temp = Double.longBitsToDouble(p.getLong("temp", 0));
        int code = p.getInt("code", 0);
        boolean day = p.getBoolean("day", true);
        out.addRow(new Object[] {Forecast.degrees(temp, fahrenheit) + "°", icon(c, Forecast.icon(code, day))});
        return out;
    }

    /** One forecast on its own thread; a fetch already running is enough. */
    private static void refresh(Context c, Cities.City home) {
        if (!fetching.compareAndSet(false, true)) return;
        Context app = c.getApplicationContext();
        new Thread(() -> {
            try {
                publish(app, home, Forecast.fetch(home.latitude, home.longitude, Forecast.MIN_DAYS));
            } catch (Exception ignored) {
                // Offline: the tile keeps the last reading.
            } finally {
                fetching.set(false);
            }
        }, "live-card").start();
    }

    /** One still frame of the app's own weather icon, as a small PNG. */
    private static byte[] icon(Context c, int kind) {
        int size = 48;
        WeatherIcon view = new WeatherIcon(c, null);
        view.setKind(kind);
        view.measure(View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, size, size);
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(b));
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        b.compress(Bitmap.CompressFormat.PNG, 100, png);
        b.recycle();
        return png.toByteArray();
    }

    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
    @Override public int delete(Uri uri, String s, String[] a) { return 0; }
}
