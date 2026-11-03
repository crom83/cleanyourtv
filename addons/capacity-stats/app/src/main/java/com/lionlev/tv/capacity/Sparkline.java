package com.lionlev.tv.capacity;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * Free memory over the last minute, newest on the right: a thin line with a soft fill under it. The scale follows
 * the readings (their range plus a margin of at least {@link #MIN_SPAN_MB} MB), so a change of a few tens of MB
 * shows, while a steady line stays flat in the middle. Nothing is drawn until two readings are in.
 */
public final class Sparkline extends View {
    static final int POINTS = 30;
    static final float MIN_SPAN_MB = 60;
    private final float[] values = new float[POINTS];
    private int count;
    private float total = 1;
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Path area = new Path();
    private int colour = Levels.GREEN;

    public Sparkline(Context c, AttributeSet a) {
        super(c, a);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(2 * getResources().getDisplayMetrics().density);
        line.setStrokeJoin(Paint.Join.ROUND);
    }

    void add(long freeMb, long totalMb, int colour) {
        if (freeMb < 0 || totalMb <= 0) return;
        total = totalMb;
        if (count == POINTS) System.arraycopy(values, 1, values, 0, POINTS - 1);
        else count++;
        values[count - 1] = freeMb;
        if (this.colour != colour || fill.getShader() == null) {
            this.colour = colour;
            fill.setShader(null);   // rebuilt for the new colour in onDraw
        }
        invalidate();
    }

    /** The drawn range: the readings' range widened to at least MIN_SPAN_MB, centred, kept within 0..total. */
    static float[] range(float lo, float hi, float total) {
        float span = Math.max(MIN_SPAN_MB, (hi - lo) * 1.4f);
        float mid = (lo + hi) / 2;
        float a = mid - span / 2, b = mid + span / 2;
        if (a < 0) { b -= a; a = 0; }
        if (b > total) { a = Math.max(0, a - (b - total)); b = total; }
        if (b - a < 1) b = a + 1;
        return new float[] {a, b};
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        fill.setShader(null);   // the gradient follows the height
    }

    @Override
    protected void onDraw(Canvas c) {
        if (count < 2) return;
        float w = getWidth(), h = getHeight(), pad = line.getStrokeWidth();
        if (fill.getShader() == null) {
            fill.setShader(new LinearGradient(0, 0, 0, h, (colour & 0x00FFFFFF) | 0x55000000,
                    colour & 0x00FFFFFF, Shader.TileMode.CLAMP));
        }
        line.setColor(colour);
        path.reset();
        area.reset();
        float lo = values[0], hi = values[0];
        for (int i = 1; i < count; i++) {
            lo = Math.min(lo, values[i]);
            hi = Math.max(hi, values[i]);
        }
        float[] range = range(lo, hi, total);
        lo = range[0];
        hi = range[1];
        float step = w / (POINTS - 1);
        float x0 = w - (count - 1) * step;
        for (int i = 0; i < count; i++) {
            float x = x0 + i * step;
            float y = pad + (h - 2 * pad) * (1 - (values[i] - lo) / (hi - lo));
            if (i == 0) {
                path.moveTo(x, y);
                area.moveTo(x, h);
                area.lineTo(x, y);
            } else {
                path.lineTo(x, y);
                area.lineTo(x, y);
            }
        }
        area.lineTo(w, h);
        area.close();
        c.drawPath(area, fill);
        c.drawPath(path, line);
    }
}
