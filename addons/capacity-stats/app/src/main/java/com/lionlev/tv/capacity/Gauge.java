package com.lionlev.tv.capacity;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * A ring gauge: a dim track and an arc for the free share, starting at the top and running clockwise, in the
 * colour of how much is left. A new value glides in rather than jumping. The numbers sit in TextViews over it.
 */
public final class Gauge extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private float shown;   // 0..1, what is drawn now
    private ValueAnimator glide;

    public Gauge(Context c, AttributeSet a) {
        super(c, a);
        float stroke = 14 * getResources().getDisplayMetrics().density;
        for (Paint p : new Paint[] {track, arc}) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(stroke);
            p.setStrokeCap(Paint.Cap.ROUND);
        }
        track.setColor(0x1FFFFFFF);
        arc.setColor(Levels.colour(1));
    }

    /** The free share (0..1); the colour follows it. */
    void setShare(float share) {
        share = Math.max(0f, Math.min(1f, share));
        arc.setColor(Levels.colour(share));
        if (glide != null) glide.cancel();
        glide = ValueAnimator.ofFloat(shown, share);
        glide.setDuration(700);
        glide.setInterpolator(new DecelerateInterpolator());
        glide.addUpdateListener(v -> {
            shown = (float) v.getAnimatedValue();
            invalidate();
        });
        glide.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (glide != null) glide.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas c) {
        float half = track.getStrokeWidth() / 2;
        float size = Math.min(getWidth(), getHeight());
        float left = (getWidth() - size) / 2 + half, top = (getHeight() - size) / 2 + half;
        box.set(left, top, left + size - 2 * half, top + size - 2 * half);
        c.drawArc(box, 0, 360, false, track);
        if (shown > 0.003f) c.drawArc(box, -90, 360 * shown, false, arc);
    }
}
