package io.github.sumyabazar1.weather;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

/**
 * An animated weather icon drawn on a Canvas, with no pictures: a drifting cloud, rain falling from it, snow
 * swaying down, a sun turning its rays, lightning flashing. The kind comes from {@link Forecast#icon}. It draws
 * about 30 frames a second and only while it is on screen, so it stays light on a 1 GB TV.
 */
public final class WeatherIcon extends View {
    static final int SUN = 0, MOON = 1, PARTLY_DAY = 2, PARTLY_NIGHT = 3, CLOUD = 4, FOG = 5, DRIZZLE = 6,
            LIGHT_RAIN = 7, RAIN = 8, HEAVY_RAIN = 9, SLEET = 10, LIGHT_SNOW = 11, SNOW = 12, THUNDER = 13;

    private static final long FRAME_MS = 33;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Path bite = new Path();
    private final RectF oval = new RectF();
    private int kind = CLOUD;
    private final long start = SystemClock.uptimeMillis();

    public WeatherIcon(Context context) {
        this(context, null);
    }

    public WeatherIcon(Context context, AttributeSet attrs) {
        super(context, attrs);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
    }

    void setKind(int kind) {
        this.kind = kind;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float s = Math.min(getWidth(), getHeight());
        if (s <= 0) return;
        float t = (SystemClock.uptimeMillis() - start) / 1000f;
        c.save();
        c.translate((getWidth() - s) / 2f, (getHeight() - s) / 2f);
        switch (kind) {
            case SUN:
                sun(c, s, s * 0.5f, s * 0.5f, s * 0.2f, t);
                break;
            case MOON:
                moon(c, s, s * 0.5f, s * 0.5f, s * 0.26f, t);
                break;
            case PARTLY_DAY:
                sun(c, s, s * 0.36f, s * 0.36f, s * 0.15f, t);
                cloud(c, s, 0.1f, 0.18f, 0xFFF1F4F7, t);
                break;
            case PARTLY_NIGHT:
                moon(c, s, s * 0.36f, s * 0.34f, s * 0.18f, t);
                cloud(c, s, 0.1f, 0.18f, 0xFFD5DCE3, t);
                break;
            case FOG:
                fog(c, s, t);
                break;
            case DRIZZLE:
                cloud(c, s, 0f, 0f, 0xFFD5DCE3, t);
                drops(c, s, t, 4, 0.6f, true);
                break;
            case LIGHT_RAIN:
                cloud(c, s, 0f, 0f, 0xFFC5CED8, t);
                drops(c, s, t, 3, 1f, false);
                break;
            case RAIN:
                cloud(c, s, 0f, 0f, 0xFFAEB9C5, t);
                drops(c, s, t, 5, 1.3f, false);
                break;
            case HEAVY_RAIN:
                cloud(c, s, 0f, 0f, 0xFF96A3B1, t);
                drops(c, s, t, 8, 1.7f, false);
                break;
            case SLEET:
                cloud(c, s, 0f, 0f, 0xFFC5CED8, t);
                drops(c, s, t, 3, 1.1f, false);
                flakes(c, s, t, 2, 0.5f);
                break;
            case LIGHT_SNOW:
                cloud(c, s, 0f, 0f, 0xFFE3E8EE, t);
                flakes(c, s, t, 3, 0f);
                break;
            case SNOW:
                cloud(c, s, 0f, 0f, 0xFFD5DCE3, t);
                flakes(c, s, t, 6, 0f);
                break;
            case THUNDER:
                cloud(c, s, 0f, 0f, 0xFF7D8A99, t);
                drops(c, s, t, 4, 1.4f, false);
                bolt(c, s, t);
                break;
            default:
                cloud(c, s, 0f, 0f, 0xFFF1F4F7, t);
                break;
        }
        c.restore();
        postInvalidateDelayed(FRAME_MS);   // stops by itself once the view leaves the window
    }

    /** A sun with eight rays that turn slowly and breathe a little. */
    private void sun(Canvas c, float s, float cx, float cy, float r, float t) {
        fill.setColor(0xFFFFD54F);
        c.drawCircle(cx, cy, r, fill);
        stroke.setColor(0xFFFFD54F);
        stroke.setStrokeWidth(s * 0.045f);
        float pulse = 1f + 0.08f * (float) Math.sin(t * 2.0);
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(i * 45 + t * 18);
            float r1 = r * 1.35f, r2 = r * 1.75f * pulse;
            c.drawLine(cx + (float) Math.cos(a) * r1, cy + (float) Math.sin(a) * r1,
                    cx + (float) Math.cos(a) * r2, cy + (float) Math.sin(a) * r2, stroke);
        }
    }

    /** A crescent moon that bobs gently. */
    private void moon(Canvas c, float s, float cx, float cy, float r, float t) {
        float dy = s * 0.015f * (float) Math.sin(t * 1.2);
        path.reset();
        path.addCircle(cx, cy + dy, r, Path.Direction.CW);
        bite.reset();
        bite.addCircle(cx + r * 0.55f, cy + dy - r * 0.35f, r * 0.85f, Path.Direction.CW);
        path.op(bite, Path.Op.DIFFERENCE);
        fill.setColor(0xFFFFF3C4);
        c.drawPath(path, fill);
    }

    /** A cloud of three puffs on a rounded base, drifting a little from side to side. dx/dy move it down-right. */
    private void cloud(Canvas c, float s, float dx, float dy, int colour, float t) {
        float x = s * (dx + 0.03f * (float) Math.sin(t * 0.8));
        float y = s * dy;
        fill.setColor(colour);
        c.drawCircle(x + s * 0.34f, y + s * 0.44f, s * 0.15f, fill);
        c.drawCircle(x + s * 0.52f, y + s * 0.36f, s * 0.2f, fill);
        c.drawCircle(x + s * 0.68f, y + s * 0.46f, s * 0.13f, fill);
        oval.set(x + s * 0.19f, y + s * 0.42f, x + s * 0.81f, y + s * 0.59f);
        c.drawRoundRect(oval, s * 0.085f, s * 0.085f, fill);
    }

    /** Rain (slanted lines) or drizzle (dots) falling from under the cloud and fading out. */
    private void drops(Canvas c, float s, float t, int n, float speed, boolean dots) {
        stroke.setStrokeWidth(s * (dots ? 0.035f : 0.03f));
        for (int i = 0; i < n; i++) {
            float phase = (t * speed + i * 0.37f) % 1f;
            float x = s * (0.3f + 0.4f * (i + 0.5f) / n) - phase * s * 0.05f;
            float y = s * 0.62f + phase * s * 0.3f;
            int alpha = Math.round(255 * (1f - phase));
            stroke.setColor((alpha << 24) | 0x81D4FA);
            if (dots) c.drawPoint(x, y, stroke);
            else c.drawLine(x, y, x - s * 0.03f, y + s * 0.08f, stroke);
        }
    }

    /** Snowflakes swaying down. */
    private void flakes(Canvas c, float s, float t, int n, float offset) {
        for (int i = 0; i < n; i++) {
            float phase = (t * 0.35f + i * 0.29f + offset) % 1f;
            float x = s * (0.28f + 0.44f * (i + 0.5f) / n) + s * 0.03f * (float) Math.sin(t * 2 + i);
            float y = s * 0.62f + phase * s * 0.32f;
            int alpha = Math.round(255 * (1f - phase * 0.8f));
            fill.setColor((alpha << 24) | 0xFFFFFF);
            c.drawCircle(x, y, s * 0.028f, fill);
        }
    }

    /** A lightning bolt that flashes every few seconds. */
    private void bolt(Canvas c, float s, float t) {
        float cycle = t % 2.6f;
        float flash = cycle < 0.12f ? 1f : cycle < 0.22f ? 0.3f : cycle < 0.32f ? 1f : 0.25f;
        path.reset();
        path.moveTo(s * 0.54f, s * 0.5f);
        path.lineTo(s * 0.44f, s * 0.7f);
        path.lineTo(s * 0.52f, s * 0.7f);
        path.lineTo(s * 0.46f, s * 0.9f);
        path.lineTo(s * 0.62f, s * 0.64f);
        path.lineTo(s * 0.54f, s * 0.64f);
        path.lineTo(s * 0.6f, s * 0.5f);
        path.close();
        fill.setColor((Math.round(255 * flash) << 24) | 0xFFE082);
        c.drawPath(path, fill);
    }

    /** Fog: three soft bands sliding past each other. */
    private void fog(Canvas c, float s, float t) {
        stroke.setStrokeWidth(s * 0.06f);
        for (int i = 0; i < 3; i++) {
            float shift = s * 0.06f * (float) Math.sin(t * 0.9 + i * 1.7);
            float y = s * (0.36f + i * 0.14f);
            stroke.setColor(i == 1 ? 0xCCECEFF1 : 0x99ECEFF1);
            c.drawLine(s * 0.22f + shift, y, s * 0.78f + shift, y, stroke);
        }
    }
}
