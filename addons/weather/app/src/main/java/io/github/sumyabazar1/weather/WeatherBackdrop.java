package io.github.sumyabazar1.weather;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

/**
 * A full-screen animated layer over the weather's gradient: rain streaks, snow, drifting clouds, stars at night,
 * a sun glow, fog bands and lightning flashes, chosen by the same kind as {@link WeatherIcon}. Everything is
 * faint so the text stays readable, the particles are worked out from the clock (nothing is allocated per frame),
 * and it draws about 20 frames a second only while it is on screen.
 */
public final class WeatherBackdrop extends View {
    private static final long FRAME_MS = 50;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** A soft puff of unit radius: white in the middle, fading to nothing at the edge; scaled per puff. */
    private final Paint puff = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int puffAlpha = -1;
    private int kind = -1;
    private int glowFor = -1;
    private final long start = SystemClock.uptimeMillis();

    public WeatherBackdrop(Context context) {
        this(context, null);
    }

    public WeatherBackdrop(Context context, AttributeSet attrs) {
        super(context, attrs);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
    }

    void setKind(int kind) {
        this.kind = kind;
        invalidate();
    }

    /** A stable pseudo-random 0..1 for particle i and salt k. */
    private static float rnd(int i, int k) {
        int h = i * 374761393 + k * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535f;
    }

    @Override
    protected void onDraw(Canvas c) {
        if (kind < 0) return;
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float t = (SystemClock.uptimeMillis() - start) / 1000f;
        switch (kind) {
            case WeatherIcon.SUN:
                sunGlow(c, w, h, t);
                break;
            case WeatherIcon.MOON:
                stars(c, w, h, t, 60);
                break;
            case WeatherIcon.PARTLY_DAY:
                sunGlow(c, w, h, t);
                clouds(c, w, h, t, 3, 0x14FFFFFF);
                break;
            case WeatherIcon.PARTLY_NIGHT:
                stars(c, w, h, t, 35);
                clouds(c, w, h, t, 3, 0x10FFFFFF);
                break;
            case WeatherIcon.CLOUD:
                clouds(c, w, h, t, 5, 0x12FFFFFF);
                break;
            case WeatherIcon.FOG:
                fog(c, w, h, t);
                break;
            case WeatherIcon.DRIZZLE:
                clouds(c, w, h, t, 3, 0x0EFFFFFF);
                rain(c, w, h, t, 40, 0.45f, 0.5f);
                break;
            case WeatherIcon.LIGHT_RAIN:
                clouds(c, w, h, t, 3, 0x0EFFFFFF);
                rain(c, w, h, t, 45, 0.8f, 1f);
                break;
            case WeatherIcon.RAIN:
                clouds(c, w, h, t, 3, 0x0CFFFFFF);
                rain(c, w, h, t, 80, 1.1f, 1.2f);
                break;
            case WeatherIcon.HEAVY_RAIN:
                rain(c, w, h, t, 130, 1.5f, 1.4f);
                break;
            case WeatherIcon.SLEET:
                rain(c, w, h, t, 40, 0.9f, 1f);
                snow(c, w, h, t, 30);
                break;
            case WeatherIcon.LIGHT_SNOW:
                snow(c, w, h, t, 40);
                break;
            case WeatherIcon.SNOW:
                snow(c, w, h, t, 90);
                break;
            case WeatherIcon.THUNDER:
                rain(c, w, h, t, 90, 1.3f, 1.3f);
                flash(c, w, h, t);
                break;
            default:
                break;
        }
        postInvalidateDelayed(FRAME_MS);
    }

    /** A warm glow in the top right that breathes slowly. */
    private void sunGlow(Canvas c, float w, float h, float t) {
        float r = h * 0.9f;
        if (glowFor != (int) r) {
            glow.setShader(new RadialGradient(0, 0, r, new int[]{0x55FFD54F, 0x22FFB74D, 0x00FFB74D},
                    new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
            glowFor = (int) r;
        }
        float scale = 1f + 0.06f * (float) Math.sin(t * 0.6);
        c.save();
        c.translate(w * 0.86f, h * 0.08f);
        c.scale(scale, scale);
        c.drawCircle(0, 0, r, glow);
        c.restore();
    }

    /** Stars that twinkle at their own pace, mostly in the upper part of the screen. */
    private void stars(Canvas c, float w, float h, float t, int n) {
        for (int i = 0; i < n; i++) {
            float x = rnd(i, 1) * w, y = rnd(i, 2) * rnd(i, 3) * h * 0.9f;
            float tw = 0.5f + 0.5f * (float) Math.sin(t * (0.8f + rnd(i, 4) * 1.6f) + i);
            fill.setColor((Math.round(40 + 150 * tw) << 24) | 0xFFFFFF);
            c.drawCircle(x, y, 1.2f + rnd(i, 5) * 1.8f, fill);
        }
    }

    /**
     * Large soft clouds drifting slowly to the right and wrapping around. Each is a few puffs that fade out
     * toward their edges, so overlapping puffs blend instead of showing circles.
     */
    private void clouds(Canvas c, float w, float h, float t, int n, int colour) {
        int a = colour >>> 24;
        if (a != puffAlpha) {
            puff.setShader(new RadialGradient(0, 0, 1, new int[]{(a << 24) | 0xFFFFFF, ((a / 2) << 24) | 0xFFFFFF,
                    0x00FFFFFF}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            puffAlpha = a;
        }
        for (int i = 0; i < n; i++) {
            float size = h * (0.2f + rnd(i, 7) * 0.14f);
            float span = w + size * 5;
            float x = (rnd(i, 8) * span + t * (8 + rnd(i, 9) * 10)) % span - size * 2.5f;
            float y = h * (0.05f + rnd(i, 10) * 0.4f);
            drawPuff(c, x, y, size);
            drawPuff(c, x + size * 0.95f, y + size * 0.12f, size * 0.8f);
            drawPuff(c, x - size * 0.9f, y + size * 0.18f, size * 0.7f);
        }
    }

    private void drawPuff(Canvas c, float x, float y, float r) {
        c.save();
        c.translate(x, y);
        c.scale(r, r);
        c.drawCircle(0, 0, 1, puff);
        c.restore();
    }

    /** Slanted rain streaks across the whole screen. */
    private void rain(Canvas c, float w, float h, float t, int n, float speed, float length) {
        stroke.setStrokeWidth(Math.max(1.5f, h * 0.0025f));
        float len = h * 0.045f * length;
        for (int i = 0; i < n; i++) {
            float phase = (t * speed * (0.8f + rnd(i, 11) * 0.4f) + rnd(i, 12)) % 1f;
            float y = phase * (h + len) - len;
            float x = rnd(i, 13) * (w + h * 0.2f) - phase * h * 0.2f;
            stroke.setColor((Math.round(35 + 45 * rnd(i, 14)) << 24) | 0xB3E5FC);
            c.drawLine(x, y, x - len * 0.2f, y + len, stroke);
        }
    }

    /** Snowflakes falling slowly and swaying. */
    private void snow(Canvas c, float w, float h, float t, int n) {
        for (int i = 0; i < n; i++) {
            float phase = (t * (0.05f + rnd(i, 15) * 0.06f) + rnd(i, 16)) % 1f;
            float y = phase * (h + 20) - 10;
            float x = rnd(i, 17) * w + (float) Math.sin(t * (0.6f + rnd(i, 18)) + i) * h * 0.02f;
            fill.setColor((Math.round(70 + 110 * rnd(i, 19)) << 24) | 0xFFFFFF);
            c.drawCircle(x, y, 1.5f + rnd(i, 20) * 3f, fill);
        }
    }

    /** Fog: wide soft veils (stretched puffs) drifting slowly past each other, densest low on the screen. */
    private void fog(Canvas c, float w, float h, float t) {
        int a = 0x1C;
        if (a != puffAlpha) {
            puff.setShader(new RadialGradient(0, 0, 1, new int[]{(a << 24) | 0xFFFFFF, ((a / 2) << 24) | 0xFFFFFF,
                    0x00FFFFFF}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            puffAlpha = a;
        }
        for (int i = 0; i < 6; i++) {
            float y = h * (0.2f + i * 0.14f);
            float x = w * (0.15f + rnd(i, 21) * 0.7f) + w * 0.12f * (float) Math.sin(t * (0.1f + i * 0.03f) + i * 1.3f);
            c.save();
            c.translate(x, y);
            c.scale(w * (0.45f + rnd(i, 22) * 0.25f), h * (0.12f + rnd(i, 23) * 0.06f));
            c.drawCircle(0, 0, 1, puff);
            c.restore();
        }
    }

    /** Now and then the whole screen flashes, twice in quick succession. */
    private void flash(Canvas c, float w, float h, float t) {
        float cycle = t % 7.3f;
        float a = cycle < 0.08f ? 0.22f : cycle < 0.16f ? 0.05f : cycle < 0.24f ? 0.16f : 0f;
        if (a <= 0) return;
        fill.setColor((Math.round(255 * a) << 24) | 0xE8EAF6);
        c.drawRect(0, 0, w, h, fill);
    }
}
