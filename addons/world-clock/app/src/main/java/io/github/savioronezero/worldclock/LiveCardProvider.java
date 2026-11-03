package io.github.savioronezero.worldclock;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

/**
 * The line on this app's Clean Your TV tile (the launcher's LiveCards): the first clock that isn't the TV's own,
 * as "Лондон 21:10" (just the time when the name would make it too long), with the sun or the moon of that city.
 * Worked out when the launcher asks, which it does only while Home is on screen; nothing runs in between.
 */
public class LiveCardProvider extends ContentProvider {
    /** The launcher shows at most 12 characters. */
    static final int MAX = 12;

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
        Context c = getContext();
        MatrixCursor out = new MatrixCursor(new String[] {"text", "icon"});
        if (c == null || !"card".equals(uri.getLastPathSegment())) return out;
        List<String> ids = new ClockPreferences(ClockPreferences.store(c)).ids;
        String id = ids.get(0);
        for (String other : ids) {
            if (!ClockModel.LOCAL.equals(other)) {
                id = other;
                break;
            }
        }
        boolean twentyFour = android.text.format.DateFormat.is24HourFormat(c);
        ClockModel.Reading r = ClockModel.at(id, Instant.now(), ZoneId.systemDefault(), twentyFour, Locale.getDefault());
        String time = r.period.isEmpty() ? r.time : r.time + " " + r.period;
        out.addRow(new Object[] {line(TimeZoneNamesCache.label(c, id), time), glyph(DayLight.sunUp(r.hour))});
        return out;
    }

    /** "City time" when it fits in {@link #MAX} characters, otherwise only the time. */
    static String line(String city, String time) {
        String both = city + " " + time;
        return both.length() <= MAX ? both : time;
    }

    /** A 48 px sun or crescent moon, the same shapes as on the app's cards. */
    private static byte[] glyph(boolean sun) {
        int size = 48;
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        if (sun) {
            p.setColor(0xFFFFD54F);
            c.drawCircle(24, 24, 13, p);
        } else {
            p.setColor(0xFFF1E6C8);
            c.drawCircle(24, 24, 15, p);
            p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
            c.drawCircle(31, 19, 13, p);
        }
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
