package io.github.maxhayim.smarthome;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The line on this app's Clean Your TV tile (the launcher's LiveCards): how many lights are on, with a bulb. The
 * launcher asks only while Home is on screen; the answer is the last known count at once, and a fresh read of
 * Home Assistant when that is older than {@link #FRESH_MS}, announced with notifyChange when it lands. The app's
 * own screen also publishes the count after each read, so the tile is right when you come back to Home.
 */
public class LiveCardProvider extends ContentProvider {
    static final long FRESH_MS = 30_000;
    private static final String PREFS = "live_card";
    private static final AtomicBoolean fetching = new AtomicBoolean();
    private static byte[] bulbOn, bulbOff;

    static Uri uri(Context c) {
        return Uri.parse("content://" + c.getPackageName() + ".card/card");
    }

    /** Saves the count and tells the launcher, if it is watching. */
    static void publish(Context c, int lightsOn) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        boolean same = p.getInt("on", -1) == lightsOn;
        p.edit().putInt("on", lightsOn).putLong("at", System.currentTimeMillis()).apply();
        if (!same) c.getContentResolver().notifyChange(uri(c), null);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
        Context c = getContext();
        MatrixCursor out = new MatrixCursor(new String[] {"text", "icon"});
        if (c == null || !"card".equals(uri.getLastPathSegment())) return out;
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int on = p.getInt("on", -1);
        if (System.currentTimeMillis() - p.getLong("at", 0) > FRESH_MS) refresh(c);
        if (on >= 0) out.addRow(new Object[] {String.valueOf(on), bulb(c, on > 0)});
        return out;
    }

    /** One read of Home Assistant on its own thread; a read already running is enough. */
    private static void refresh(Context c) {
        Config config = Config.load(c);
        if (!config.complete() || !fetching.compareAndSet(false, true)) return;
        Context app = c.getApplicationContext();
        new Thread(() -> {
            try {
                HaClient.Reply r = new HaClient(config.url, config.token).get("/api/states");
                if (r.ok()) {
                    List<Ha.Entity> found = Ha.parse(r.body, config.glance, config.exclude, config.hideUnavailable);
                    publish(app, Ha.lightsOn(found));
                }
            } catch (Exception ignored) {
                // Offline or unreadable: the tile keeps the last count.
            } finally {
                fetching.set(false);
            }
        }, "live-card").start();
    }

    /** The tile's bulb as a small PNG: yellow when any light is on, grey when none is. */
    private static synchronized byte[] bulb(Context c, boolean lit) {
        byte[] cached = lit ? bulbOn : bulbOff;
        if (cached != null) return cached;
        int size = 48;
        Drawable d = c.getDrawable(R.drawable.ic_tile_light).mutate();
        d.setTint(lit ? 0xFFFFC94D : 0xFF8B949E);
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        d.setBounds(0, 0, size, size);
        d.draw(new Canvas(b));
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        b.compress(Bitmap.CompressFormat.PNG, 100, png);
        b.recycle();
        byte[] bytes = png.toByteArray();
        if (lit) bulbOn = bytes;
        else bulbOff = bytes;
        return bytes;
    }

    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
    @Override public int delete(Uri uri, String s, String[] a) { return 0; }
}
