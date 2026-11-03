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
 * rows_under_header (локальная доработка): сетка растянута до верха экрана и уходит под верхнюю панель,
 * а не обрезается по линии под ней. Ряды плавно растворяются от низа панели к краю экрана.
 * Панель рисуется поверх сетки (translationZ). Позиция дока не меняется: отступ сетки считается от её низа.
 */
final class HeaderFade extends RecyclerView.ItemDecoration {

    private final Paint paint = new Paint();
    private float fadeEnd = -1f;
    private float shaderEnd = -1f;

    private HeaderFade() {
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
    }

    /** header: верхняя панель; wrapper: рамка сетки, следующая за панелью в том же вертикальном LinearLayout. */
    static void attach(RecyclerView grid, View header, View wrapper) {
        HeaderFade fade = new HeaderFade();
        // Без отдельного слоя DST_OUT стёр бы всё окно, а не только картинку сетки.
        grid.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        grid.addItemDecoration(fade);
        header.setTranslationZ(1f);
        // Под часами теперь бывают плитки: тень отделяет приветствие, часы и дату от них.
        shadowTexts(header.findViewById(R.id.leftBlock), header.getResources().getDisplayMetrics().density);
        header.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            // Рамка сетки начинается от верха экрана, а не под панелью.
            // LinearLayout не растягивает блок с весом на отрицательный отступ, поэтому высота задаётся явно:
            // от верха до низа экрана.
            android.widget.LinearLayout.LayoutParams lp = (android.widget.LinearLayout.LayoutParams) wrapper.getLayoutParams();
            View parent = (View) wrapper.getParent();
            int height = parent.getHeight() - parent.getPaddingBottom();
            if (lp.topMargin != -b || lp.height != height || lp.weight != 0f) {
                lp.topMargin = -b;
                lp.height = height;
                lp.weight = 0f;
                wrapper.post(() -> wrapper.setLayoutParams(lp));
            }
            // Полностью видимы ряды ниже панели, к верху экрана они растворяются.
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
            // В зоне часов и даты плитки почти исчезают (стирается 85%), полностью проявляются у низа панели.
            paint.setShader(new LinearGradient(0f, 0f, 0f, fadeEnd,
                    new int[]{0xFF000000, 0xD9000000, 0x00000000}, new float[]{0f, 0.7f, 1f}, Shader.TileMode.CLAMP));
            shaderEnd = fadeEnd;
        }
        c.drawRect(0f, 0f, parent.getWidth(), fadeEnd, paint);
    }
}
