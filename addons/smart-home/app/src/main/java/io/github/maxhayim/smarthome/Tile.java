package io.github.maxhayim.smarthome;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * A device tile in a room's grid: icon, name, state and, for a dimmable light, a brightness bar. OK toggles,
 * holding OK opens the slider, CH+ / CH- change the brightness at once. A tile that is on glows warm (or in
 * an RGB light's colour); the focused one gets an outline and grows a little.
 */
final class Tile extends LinearLayout {
    private static final int OFF_BG = 0xFF1C2128;
    private static final int ON_BG = 0xFF3D3420;
    private static final int OFF_ICON = 0xFF8B949E;
    private static final int ON_ICON = 0xFFFFC94D;

    private final GradientDrawable bg = new GradientDrawable();
    private final float dp;

    Tile(Context context, Ha.Entity e, Ha.Lang lng, Runnable onClick, Runnable onLong, Row.Step onStep) {
        super(context);
        dp = context.getResources().getDisplayMetrics().density;
        setTag(e.id);
        setOrientation(VERTICAL);
        int p = Math.round(12 * dp);
        setPadding(p, p, p, Math.round(10 * dp));
        boolean on = "on".equals(e.state) && e.kind == Ha.Kind.TOGGLE
                || "locked".equals(e.state) && e.kind == Ha.Kind.LOCK;
        // An RGB light: tile, icon and bar in the lamp's current colour
        int tint = on && e.color != 0 ? e.color : 0;
        int iconColor = tint != 0 ? mix(tint, 0xFFFFFFFF, 0.8f) : on ? ON_ICON : OFF_ICON;
        int barColor = tint != 0 ? tint : ON_ICON;
        bg.setCornerRadius(14 * dp);
        bg.setColor(tint != 0 ? mix(tint, OFF_BG, 0.32f) : on ? ON_BG : OFF_BG);
        setBackground(bg);
        if (!e.available()) setAlpha(0.45f);

        boolean active = onClick != null || onStep != null || onLong != null;
        setFocusable(active);
        setClickable(onClick != null);
        if (onClick != null) setOnClickListener(v -> onClick.run());
        if (onLong != null) setOnLongClickListener(v -> {
            onLong.run();
            return true;
        });
        if (onStep != null) {
            setOnKeyListener((v, code, ev) -> {
                int d = code == KeyEvent.KEYCODE_CHANNEL_UP || code == KeyEvent.KEYCODE_PAGE_UP ? 1
                        : code == KeyEvent.KEYCODE_CHANNEL_DOWN || code == KeyEvent.KEYCODE_PAGE_DOWN ? -1 : 0;
                if (d == 0) return false;
                if (ev.getAction() == KeyEvent.ACTION_DOWN) onStep.step(d);
                return true;
            });
        }
        setOnFocusChangeListener((v, has) -> {
            bg.setStroke(has ? Math.round(2 * dp) : 0, 0xFFFFFFFF);
            animate().scaleX(has ? 1.06f : 1f).scaleY(has ? 1.06f : 1f).setDuration(120).start();
        });

        ImageView icon = new ImageView(context);
        icon.setImageResource(icon(e));
        icon.setColorFilter(iconColor);
        addView(icon, new LayoutParams(Math.round(30 * dp), Math.round(30 * dp)));

        View space = new View(context);
        addView(space, new LayoutParams(1, 0, 1f));

        TextView name = new TextView(context);
        name.setText(e.name);
        name.setTextSize(15);
        name.setTextColor(0xFFFFFFFF);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        addView(name);

        TextView state = new TextView(context);
        state.setText(e.value(lng));
        state.setTextSize(13);
        state.setTextColor(tint != 0 ? mix(tint, 0xFFFFFFFF, 0.6f) : on ? 0xFFE8D9A8 : 0xFF8B949E);
        addView(state);

        if (e.kind == Ha.Kind.TOGGLE && e.brightness >= 0) {
            LinearLayout track = new LinearLayout(context);
            track.setBackgroundColor(0x33FFFFFF);
            View fill = new View(context);
            fill.setBackgroundColor(barColor);
            float f = on ? Math.max(0f, Math.min(1f, e.brightness / 100f)) : 0f;
            track.addView(fill, new LayoutParams(0, LayoutParams.MATCH_PARENT, f));
            track.addView(new View(context), new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f - f));
            FrameLayout holder = new FrameLayout(context);
            holder.setPadding(0, Math.round(6 * dp), 0, 0);
            holder.addView(track, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, Math.round(4 * dp), Gravity.BOTTOM));
            addView(holder);
        }
    }

    /** Mixes two colours; f is the share of the first. */
    static int mix(int a, int b, float f) {
        int r = Math.round(((a >> 16) & 0xFF) * f + ((b >> 16) & 0xFF) * (1 - f));
        int g = Math.round(((a >> 8) & 0xFF) * f + ((b >> 8) & 0xFF) * (1 - f));
        int bl = Math.round((a & 0xFF) * f + (b & 0xFF) * (1 - f));
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private static int icon(Ha.Entity e) {
        switch (e.kind) {
            case SCENE: return R.drawable.ic_tile_scene;
            case CLIMATE: return R.drawable.ic_tile_climate;
            case LOCK: return R.drawable.ic_tile_lock;
            case OPENING: return R.drawable.ic_tile_door;
            default: return e.domain().equals("light") ? R.drawable.ic_tile_light : R.drawable.ic_tile_power;
        }
    }
}
