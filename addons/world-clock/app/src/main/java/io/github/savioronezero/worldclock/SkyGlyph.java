package io.github.savioronezero.worldclock;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

/** A small sun with a soft glow, or a crescent moon, in a card's corner. Drawn once per change, never animated. */
final class SkyGlyph extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean sun = true;
    private int cutColour = 0xFF0B1530;

    SkyGlyph(Context c) {
        super(c);
    }

    /** The sun or the moon; the moon's shadow takes the sky colour behind it so it reads as a crescent. */
    void set(boolean sun, int skyTop) {
        if (this.sun == sun && cutColour == skyTop) return;
        this.sun = sun;
        cutColour = skyTop;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float s = Math.min(getWidth(), getHeight());
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        if (sun) {
            glow.setShader(new RadialGradient(cx, cy, s / 2, 0x66FFD54F, 0x00FFD54F, Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, s / 2, glow);
            paint.setColor(0xFFFFD54F);
            c.drawCircle(cx, cy, s * 0.24f, paint);
        } else {
            paint.setColor(0xFFF1E6C8);
            c.drawCircle(cx, cy, s * 0.30f, paint);
            paint.setColor(cutColour);
            c.drawCircle(cx + s * 0.14f, cy - s * 0.10f, s * 0.26f, paint);
        }
    }
}
