package com.lionlev.tv.capacity;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;

import java.io.ByteArrayOutputStream;

/**
 * The line on this app's Clean Your TV tile (the launcher's LiveCards): free memory, with a small ring in the
 * colour of how much is left. Read from /proc/meminfo when the launcher asks, which it does only while Home is
 * on screen; no network and nothing kept.
 */
public class LiveCardProvider extends ContentProvider {
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
        Context c = getContext();
        MatrixCursor out = new MatrixCursor(new String[] {"text", "icon"});
        if (c == null || !"card".equals(uri.getLastPathSegment())) return out;
        StatsActivity.Reading r = StatsActivity.read(c);
        float share = Levels.share(r.memFreeMb, r.memTotalMb);
        if (share < 0) return out;
        out.addRow(new Object[] {c.getString(R.string.live_line, r.memFreeMb), ring(share)});
        return out;
    }

    /** A 48 px ring: dim track and the free share as an arc, like the gauge on the app's screen. */
    private static byte[] ring(float share) {
        int size = 48;
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(7);
        p.setStrokeCap(Paint.Cap.ROUND);
        RectF box = new RectF(6, 6, size - 6, size - 6);
        p.setColor(0x40FFFFFF);
        c.drawArc(box, 0, 360, false, p);
        p.setColor(Levels.colour(share));
        c.drawArc(box, -90, Math.max(4, 360 * share), false, p);
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
