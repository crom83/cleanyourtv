package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * rows_under_header (local patch): the grid reaches the top of the screen and slides under the top bar instead of
 * being cut off at a line below it. Rows fade out from the bottom of the bar to the screen's edge. The bar is drawn
 * over the grid (translationZ). The dock stays where it was: the grid's inset is counted from the bar's bottom.
 */
final class HeaderFade extends RecyclerView.ItemDecoration {

    private final Paint paint = new Paint();
    private float fadeEnd = -1f;
    private float shaderEnd = -1f;

    private HeaderFade() {
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
    }

    /** header: the top bar; wrapper: the grid's frame, next to the bar in the same vertical LinearLayout. */
    static void attach(RecyclerView grid, View header, View wrapper) {
        HeaderFade fade = new HeaderFade();
        // Without a layer of its own, DST_OUT would erase the whole window, not just the grid's picture.
        grid.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        grid.addItemDecoration(fade);
        header.setTranslationZ(1f);
        // Tiles can now pass under the clock: a shadow keeps the greeting, clock and date apart from them.
        shadowTexts(header.findViewById(R.id.leftBlock), header.getResources().getDisplayMetrics().density);
        header.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            // The grid's frame starts at the top of the screen, not under the bar.
            // LinearLayout doesn't stretch a weighted child over a negative margin, so the height is set outright:
            // from the top of the screen to the bottom.
            android.widget.LinearLayout.LayoutParams lp = (android.widget.LinearLayout.LayoutParams) wrapper.getLayoutParams();
            View parent = (View) wrapper.getParent();
            int height = parent.getHeight() - parent.getPaddingBottom();
            if (lp.topMargin != -b || lp.height != height || lp.weight != 0f) {
                lp.topMargin = -b;
                lp.height = height;
                lp.weight = 0f;
                wrapper.post(() -> wrapper.setLayoutParams(lp));
            }
            // Rows below the bar are fully visible; towards the top of the screen they fade out.
            fade.fadeEnd = b;
            grid.invalidate();
        });
    }

    private static void shadowTexts(View v, float density) {
        if (v instanceof android.widget.TextView) {
            ((android.widget.TextView) v).setShadowLayer(8 * density, 0f, 1.5f * density, 0xE6000000);
        } else if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) shadowTexts(g.getChildAt(i), density);
        }
    }

    @Override
    public void onDrawOver(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        if (fadeEnd <= 0f) return;
        if (fadeEnd != shaderEnd) {
            // Around the clock and date tiles almost vanish (85% erased) and are fully back at the bar's bottom.
            paint.setShader(new LinearGradient(0f, 0f, 0f, fadeEnd,
                    new int[]{0xFF000000, 0xD9000000, 0x00000000}, new float[]{0f, 0.7f, 1f}, Shader.TileMode.CLAMP));
            shaderEnd = fadeEnd;
        }
        c.drawRect(0f, 0f, parent.getWidth(), fadeEnd, paint);
    }
}
