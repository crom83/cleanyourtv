package io.github.maxhayim.smarthome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Shader;
import android.view.View;
import android.widget.ScrollView;

/**
 * A full-screen scroll view whose content slides under the header and fades out there, like the launcher's
 * HeaderFade. The top {@link #inset} pixels sit under the header: a DST_OUT gradient erases the content there
 * (almost fully under the title, easing off toward the header's bottom edge), and focus scrolling keeps the
 * selected item below the header.
 */
final class FadeScrollView extends ScrollView {
    private final Paint paint = new Paint();
    private int inset;
    private int shaderFor = -1;

    FadeScrollView(Context context) {
        super(context);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        setLayerType(View.LAYER_TYPE_HARDWARE, null);   // so DST_OUT erases this layer only, not the window
        setClipToPadding(false);
    }

    /** The header's height: the content's top padding and the fade zone. */
    void setInset(int px) {
        if (px == inset) return;
        inset = px;
        setPadding(getPaddingLeft(), px, getPaddingRight(), getPaddingBottom());
        invalidate();
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        if (inset <= 0) return;
        float extra = inset * 0.15f;
        int end = Math.round(inset + extra);
        if (shaderFor != end) {
            paint.setShader(new LinearGradient(0, 0, 0, end,
                    new int[]{0xFF000000, 0xFF000000, 0xD9000000, 0x00000000},
                    new float[]{0f, 0.35f, 0.75f, 1f}, Shader.TileMode.CLAMP));
            shaderFor = end;
        }
        int y = getScrollY();
        canvas.save();
        canvas.translate(0, y);
        canvas.drawRect(0, 0, getWidth(), end, paint);
        canvas.restore();
    }

    /** The focused item must not hide under the header. */
    @Override
    protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) {
        Rect r = new Rect(rect);
        r.top -= inset;
        if (r.top < 0) r.top = 0;
        return super.computeScrollDeltaToGetChildRectOnScreen(r);
    }
}
