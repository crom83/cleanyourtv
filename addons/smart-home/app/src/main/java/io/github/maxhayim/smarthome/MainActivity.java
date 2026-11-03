package io.github.maxhayim.smarthome;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The tile and module card open this: a glance of weather and fuel prices at the top, then your Home
 * Assistant's scenes, lights and switches, climate, sensors, doors and windows, and locks. OK runs a
 * scene or switches a light; Left and Right dim a light or move a thermostat. Locks are shown, never
 * opened. It reads Home Assistant only while this screen is open.
 */
public class MainActivity extends Activity {
    private static final long REFRESH_MS = 10_000;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService net = Executors.newSingleThreadExecutor();
    private LinearLayout rows;
    /** The weather glance, top right, on the title's line. */
    private LinearLayout glanceBox;
    private TextView status;
    private Config config;
    private List<Ha.Entity> entities = new ArrayList<>();
    private String problem;
    /** entity_id to room (Home Assistant area); null until read, and then the plain list is shown. */
    private volatile Map<String, String> areas;
    /** The home's name in Home Assistant, left of the weather. */
    private volatile String location = "";
    private Ha.Lang lng;
    /** The brightness slider (OK held on a light). */
    private AlertDialog slider;
    private Row sliderRow;
    private String sliderId;
    private static final int COLUMNS = 5;
    /** Colour circles per row in the light dialog: 12 colours in two rows. */
    private static final int SWATCHES_PER_ROW = 6;
    private boolean shownEntities;
    /** The second screen, More: updates and the connection. */
    private boolean more;
    private List<Ha.Update> updates = new ArrayList<>();
    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            load();
            main.postDelayed(this, REFRESH_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        lng = new Texts(this);
        // The list scrolls full screen under the header, which sits on top; FadeScrollView fades what passes under it.
        android.widget.FrameLayout page = new android.widget.FrameLayout(this);
        page.setBackgroundColor(0xFF0D1117);
        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextSize(28);
        title.setTextColor(0xFFFFFFFF);
        status = new TextView(this);
        status.setTextSize(15);
        status.setTextColor(0xFF8B949E);
        status.setPadding(0, 0, 0, px(16));
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(title);
        titles.addView(status);
        head.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        glanceBox = new LinearLayout(this);
        glanceBox.setOrientation(LinearLayout.HORIZONTAL);
        glanceBox.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(glanceBox);
        head.setPadding(px(48), px(32), px(48), 0);
        FadeScrollView scroll = new FadeScrollView(this);
        scroll.setPadding(px(48), px(120), px(48), px(24));
        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows);
        scroll.setClipChildren(false);
        scroll.setVerticalScrollBarEnabled(false);
        page.addView(scroll, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        page.addView(head, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        head.addOnLayoutChangeListener((v, l, t, r, bo, ol, ot, or, ob) -> scroll.setInset(bo - t));
        setContentView(page);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Config.importPushed(this);
        config = Config.load(this);
        build();
        if (config.complete()) main.post(refresh);
    }

    @Override
    protected void onPause() {
        main.removeCallbacks(refresh);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        net.shutdownNow();
        super.onDestroy();
    }

    private void load() {
        Config c = config;
        net.execute(() -> {
            HaClient client = new HaClient(c.url, c.token);
            if (areas == null) {
                Ha.Call a = Ha.areasCall();
                HaClient.Reply ar = client.post(a.path, a.body);
                if (ar.ok()) areas = Ha.parseAreas(ar.body);
                HaClient.Reply cr = client.get("/api/config");
                if (cr.ok()) location = Ha.locationName(cr.body);
            }
            HaClient.Reply r = client.get("/api/states");
            List<Ha.Entity> found = null;
            List<Ha.Update> ups = null;
            String why = null;
            if (r.ok()) {
                ups = Ha.updates(r.body);
                try {
                    found = Ha.parse(r.body, c.glance, c.exclude, c.hideUnavailable);
                } catch (JSONException e) {
                    why = getString(R.string.problem_unreadable);
                }
            } else {
                why = Ha.problem(r.code, lng);
            }
            List<Ha.Entity> f = found;
            List<Ha.Update> u = ups;
            String w = why;
            main.post(() -> {
                if (isFinishing()) return;
                problem = w;
                if (f != null) entities = f;
                if (u != null) updates = u;
                build();
            });
        });
    }

    private void send(Ha.Call call) {
        if (call == null) return;
        Config c = config;
        net.execute(() -> {
            HaClient.Reply r = new HaClient(c.url, c.token).post(call.path, call.body);
            main.post(() -> {
                if (!r.ok()) status.setText(Ha.problem(r.code, lng));
                main.postDelayed(this::load, 600);
            });
        });
    }

    /** Rebuilds the list; focus stays on the row it was on. */
    private void build() {
        View had = getCurrentFocus();
        Object focusedTag = had != null && had.getParent() != null ? had.getTag() : null;
        // First build with data: focus the first tile, not the connection row.
        if (!shownEntities && !entities.isEmpty()) {
            shownEntities = true;
            focusedTag = null;
            had = null;
        }
        int focused = had == null ? -1 : rows.indexOfChild(rows.getFocusedChild());
        rows.removeAllViews();
        if (!config.complete()) {
            status.setText(R.string.status_setup);
        } else if (problem != null) {
            status.setText(problem);
        } else {
            status.setText(entities.isEmpty() ? getString(R.string.status_reading) : getString(R.string.status_connected, config.url));
        }
        addGlance(Ha.glance(entities, config.glance));
        if (!config.complete()) more = true;
        Row url = null;
        if (more) {
            url = addMore();
        } else {
            if (areas != null) {
                addRooms();
            } else {
                addList();
            }
            Row extra = add(new Row(this, getString(R.string.more), getString(R.string.more_detail), false,
                    this::openMore, null));
            extra.setTag("more");
            extra.setBadge(updates.isEmpty() ? null : String.valueOf(updates.size()));
            LinearLayout.LayoutParams ep = (LinearLayout.LayoutParams) extra.getLayoutParams();
            ep.topMargin = px(10);
        }
        updateSlider();

        View keep = focusedTag == null ? null : rows.findViewWithTag(focusedTag);
        if (keep == null && focusedTag == null && focused >= 0 && focused < rows.getChildCount()) keep = rows.getChildAt(focused);
        if (keep != null && keep.isFocusable()) keep.requestFocus();
        else if (!config.complete() && url != null) url.requestFocus();
        else {
            View first = firstFocusable(rows);
            if (first != null) first.requestFocus();
        }
    }

    /** The More screen: pending updates and the connection. Returns the address row. */
    private Row addMore() {
        heading(getString(R.string.updates));
        if (updates.isEmpty()) {
            Row none = add(new Row(this, getString(R.string.updates_none), null, false, () -> { }, null));
            none.setTag("upd:none");
        }
        for (Ha.Update u : updates) {
            boolean allowed = u.canInstall() && !Ha.excluded(u.id, config.noInstall);
            String hint = getString(!allowed ? R.string.update_manual
                    : u.inProgress ? R.string.update_installing : R.string.update_ok);
            Row r = add(new Row(this, u.title, hint, false, () -> confirmInstall(u.id), null));
            r.setTag(u.id);
            r.setValue(u.inProgress
                    ? (u.percent >= 0 ? getString(R.string.update_progress_percent, u.percent)
                            : getString(R.string.update_progress))
                    : u.installed + " → " + u.latest);
        }
        heading(getString(R.string.connection));
        Row url = add(new Row(this, getString(R.string.address), getString(R.string.address_hint), false,
                this::editUrl, null));
        url.setTag("conn:url");
        url.setValue(config.url.isEmpty() ? getString(R.string.not_set) : config.url);
        Row token = add(new Row(this, getString(R.string.token), getString(R.string.token_hint), false,
                this::editToken, null));
        token.setTag("conn:token");
        token.setValue(getString(config.token.isEmpty() ? R.string.not_set : R.string.is_set));
        return url;
    }

    /**
     * Installing: Home Assistant answers only once the install has finished, so a timeout (code 0) is not an error;
     * the install is running. Its progress shows on the 10-second refresh.
     */
    private void sendInstall(Ha.Call call) {
        Config c = config;
        status.setText(R.string.install_started);
        net.execute(() -> {
            HaClient.Reply r = new HaClient(c.url, c.token).post(call.path, call.body);
            main.post(() -> {
                if (!r.ok() && r.code != 0) status.setText(Ha.problem(r.code, lng));
                load();
            });
        });
        main.postDelayed(this::load, 2000);
    }

    /** Asks before installing; reads the update's current state by id. */
    private void confirmInstall(String id) {
        Ha.Update u = null;
        for (Ha.Update x : updates) if (x.id.equals(id)) u = x;
        if (u == null || !u.canInstall() || u.inProgress || Ha.excluded(u.id, config.noInstall)) return;
        Ha.Update target = u;
        StringBuilder msg = new StringBuilder();
        msg.append(u.installed).append(" → ").append(u.latest).append("\n\n");
        if (u.canBackup()) {
            msg.append(getString(R.string.install_backup));
        } else {
            msg.append(getString(R.string.install_no_backup));
        }
        if (id.contains("firmware") || id.contains("slzb") || id.contains("zigbee")) {
            msg.append("\n").append(getString(R.string.install_zigbee));
        }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(getString(R.string.install_title, u.title))
                .setMessage(msg.toString())
                .setPositiveButton(R.string.install, (d, w) -> sendInstall(Ha.install(target)))
                .setNegativeButton(R.string.cancel, null)
                .show()
                .getButton(AlertDialog.BUTTON_NEGATIVE).requestFocus();   // Cancel is the default
    }

    private void openMore() {
        more = true;
        rows.removeAllViews();   // so focus doesn't come back to the More row
        build();
        View first = firstFocusable(rows);
        if (first != null) first.requestFocus();
        ((ScrollView) rows.getParent()).scrollTo(0, 0);
    }

    @Override
    public void onBackPressed() {
        if (more && config.complete()) {
            more = false;
            rows.removeAllViews();
            build();
            View m = rows.findViewWithTag("more");
            if (m != null) m.requestFocus();
            return;
        }
        super.onBackPressed();
    }

    private static View firstFocusable(ViewGroup g) {
        for (int i = 0; i < g.getChildCount(); i++) {
            View v = g.getChildAt(i);
            if (v.isFocusable()) return v;
            if (v instanceof ViewGroup) {
                View f = firstFocusable((ViewGroup) v);
                if (f != null) return f;
            }
        }
        return null;
    }

    /**
     * Rooms as blocks (name, a temperature/humidity line, then tiles). Small rooms share a line while COLUMNS
     * slots last; a large room takes the whole line and wraps.
     */
    private void addRooms() {
        int avail = getResources().getDisplayMetrics().widthPixels - px(96) - px(16);
        int gap = px(14);
        int w = (avail - gap * (COLUMNS - 1)) / COLUMNS;
        int h = Math.round(w * 0.72f);
        LinearLayout line = null;
        int used = 0;
        for (Ha.Room room : Ha.rooms(entities, areas, getString(R.string.room_other))) {
            int slots = Math.max(1, Math.min(COLUMNS, room.tiles.size()));
            if (line == null || used + slots > COLUMNS) {
                line = new LinearLayout(this);
                line.setOrientation(LinearLayout.HORIZONTAL);
                line.setClipChildren(false);
                line.setClipToPadding(false);
                line.setPadding(px(8), 0, px(8), px(6));
                rows.addView(line);
                used = 0;
            }
            LinearLayout block = new LinearLayout(this);
            block.setOrientation(LinearLayout.VERTICAL);
            block.setClipChildren(false);
            block.setClipToPadding(false);
            TextView name = new TextView(this);
            name.setText(room.name);
            name.setTextSize(20);
            name.setTextColor(0xFFFFFFFF);
            name.setSingleLine(true);
            name.setPadding(0, px(14), 0, 0);
            block.addView(name);
            TextView sum = new TextView(this);
            String text = room.summary();
            sum.setText(text.isEmpty() ? " " : text);
            sum.setTextSize(14);
            sum.setTextColor(0xFF8B949E);
            sum.setSingleLine(true);
            sum.setPadding(0, 0, 0, px(8));
            block.addView(sum);
            LinearLayout tiles = null;
            for (int i = 0; i < room.tiles.size(); i++) {
                if (i % COLUMNS == 0) {
                    tiles = new LinearLayout(this);
                    tiles.setOrientation(LinearLayout.HORIZONTAL);
                    tiles.setClipChildren(false);
                    tiles.setClipToPadding(false);
                    tiles.setPadding(0, 0, 0, gap);
                    block.addView(tiles);
                }
                Ha.Entity e = room.tiles.get(i);
                Ha.Call press = Ha.press(e);
                boolean steps = Ha.step(e, 1) != null;
                boolean dimmable = steps && e.kind == Ha.Kind.TOGGLE;
                Tile t = new Tile(this, e, lng, press == null ? null : () -> send(Ha.press(e)),
                        dimmable ? () -> openSlider(e) : null, steps ? d -> send(Ha.step(e, d)) : null);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
                if (i % COLUMNS != 0) lp.setMarginStart(gap);
                tiles.addView(t, lp);
            }
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(slots * w + (slots - 1) * gap,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            if (used > 0) bp.setMarginStart(gap);
            line.addView(block, bp);
            used += slots;
        }
        rows.setClipChildren(false);
        rows.setClipToPadding(false);
    }

    /** The brightness slider: a row with a bar, ◀ ▶ by 10 %. */
    private void openSlider(Ha.Entity e) {
        sliderId = e.id;
        sliderRow = new Row(this, e.name, getString(R.string.slider_hint),
                true, () -> send(Ha.press(find(sliderId))), d -> {
                    Ha.Entity cur = find(sliderId);
                    if (cur != null) send(Ha.step(cur, d));
                });
        sliderRow.setMinimumWidth(px(560));
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setClipChildren(false);
        box.setClipToPadding(false);
        box.addView(sliderRow);
        if (e.colorable) addSwatches(box, e);
        if (e.colorable) sliderRow.setDetail(getString(R.string.slider_hint_color));
        slider = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setView(box)
                .setOnDismissListener(d -> {
                    slider = null;
                    sliderRow = null;
                    sliderId = null;
                })
                .show();
        updateSlider();
        sliderRow.requestFocus();
    }

    /** Two rows of colour circles under the slider: ◀ ▶ choose, OK sets it. Returns the circle nearest the lamp's colour. */
    private View addSwatches(LinearLayout box, Ha.Entity e) {
        String[] names = getResources().getStringArray(R.array.palette_names);
        LinearLayout line = null;
        View nearest = null;
        int near = Ha.nearest(e);
        for (int i = 0; i < Ha.PALETTE.length; i++) {
            if (i % SWATCHES_PER_ROW == 0) {
                line = new LinearLayout(this);
                line.setOrientation(LinearLayout.HORIZONTAL);
                line.setGravity(Gravity.CENTER);
                // padding so the focused circle (scaled up, outlined) isn't clipped by its row
                line.setPadding(px(8), px(i == 0 ? 14 : 6), px(8), px(i + SWATCHES_PER_ROW >= Ha.PALETTE.length ? 16 : 6));
                line.setClipChildren(false);
                line.setClipToPadding(false);
                box.addView(line);
            }
            int rgb = Ha.PALETTE[i];
            View dot = new View(this);
            android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
            d.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            d.setColor(0xFF000000 | rgb);
            d.setStroke(px(1), 0x55FFFFFF);
            dot.setBackground(d);
            dot.setFocusable(true);
            dot.setClickable(true);
            if (i < names.length) dot.setContentDescription(names[i]);
            dot.setOnFocusChangeListener((v, has) -> {
                d.setStroke(has ? px(3) : px(1), has ? 0xFFFFFFFF : 0x55FFFFFF);
                v.animate().scaleX(has ? 1.2f : 1f).scaleY(has ? 1.2f : 1f).setDuration(100).start();
            });
            dot.setOnClickListener(v -> {
                Ha.Entity cur = find(sliderId);
                if (cur != null) send(Ha.color(cur, rgb));
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(36), px(36));
            lp.setMargins(px(9), 0, px(9), 0);
            line.addView(dot, lp);
            if (i == near) nearest = dot;
        }
        if (nearest != null) {
            // Down from the slider lands on the lamp's current colour, not the first circle
            nearest.setId(View.generateViewId());
            sliderRow.setNextFocusDownId(nearest.getId());
        }
        return nearest;
    }

    private void updateSlider() {
        if (sliderRow == null) return;
        Ha.Entity e = find(sliderId);
        if (e == null) return;
        boolean on = "on".equals(e.state);
        sliderRow.setValue(e.value(lng));
        sliderRow.setBar(on && e.brightness >= 0 ? e.brightness / 100f : 0f);
    }

    private Ha.Entity find(String id) {
        if (id == null) return null;
        for (Ha.Entity e : entities) if (e.id.equals(id)) return e;
        return null;
    }

    /** The plain list by kind, when the rooms can't be read. */
    private void addList() {
        Ha.Kind section = null;
        for (Ha.Entity e : entities) {
            if (!Ha.inList(e)) continue;
            if (e.kind != section) {
                section = e.kind;
                heading(lng.section(section));
            }
            Ha.Call press = Ha.press(e);
            boolean steps = Ha.step(e, 1) != null;
            boolean bar = e.kind == Ha.Kind.TOGGLE && e.brightness >= 0;
            Row r = add(new Row(this, e.name, null, bar, press == null ? null : () -> send(Ha.press(e)),
                    steps ? d -> send(Ha.step(e, d)) : null));
            r.setValue(e.value(lng));
            if (bar) r.setBar("on".equals(e.state) ? e.brightness / 100f : 0f);
            if (!e.available()) r.setAlpha(0.5f);
            if (e.kind == Ha.Kind.LOCK) r.setDetail(getString(R.string.lock_detail));
            r.setTag(e.id);
        }
    }

    private void addGlance(Ha.Glance g) {
        glanceBox.removeAllViews();
        if (g.empty()) return;
        LinearLayout box = glanceBox;
        box.setPadding(px(16), 0, px(8), 0);
        if (!location.isEmpty()) {
            TextView place = new TextView(this);
            place.setText(location);
            place.setTextSize(20);
            place.setTextColor(0xFF8B949E);
            LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            pp.setMarginEnd(px(18));
            box.addView(place, pp);
        }
        if (!g.icon.isEmpty()) {
            ImageView icon = new ImageView(this);
            icon.setImageResource(weatherDrawable(g.icon));
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(px(44), px(44));
            ip.setMarginEnd(px(14));
            box.addView(icon, ip);
        }
        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        if (g.weather != null && g.weather.available() && !Double.isNaN(g.weather.current)) {
            TextView temp = new TextView(this);
            temp.setText(Ha.number(g.weather.current) + "°");
            temp.setTextSize(34);
            temp.setTextColor(0xFFFFFFFF);
            temp.setIncludeFontPadding(false);
            text.addView(temp);
        }
        if (!g.prices.isEmpty()) {
            TextView prices = new TextView(this);
            StringBuilder line = new StringBuilder();
            for (Ha.Entity p : g.prices) {
                if (line.length() > 0) line.append("   ");
                line.append(p.name).append(' ').append(p.value(lng));
            }
            prices.setText(line.toString());
            prices.setTextSize(16);
            prices.setTextColor(0xFF8B949E);
            prices.setPadding(0, px(4), 0, 0);
            text.addView(prices);
        }
        box.addView(text);
    }

    private static int weatherDrawable(String icon) {
        switch (icon) {
            case "clear": return R.drawable.ic_weather_clear;
            case "rain": return R.drawable.ic_weather_rain;
            case "snow": return R.drawable.ic_weather_snow;
            case "storm": return R.drawable.ic_weather_storm;
            case "fog": return R.drawable.ic_weather_fog;
            case "night": return R.drawable.ic_weather_night;
            default: return R.drawable.ic_weather_cloudy;
        }
    }

    private void editUrl() {
        edit(getString(R.string.address), config.url.isEmpty() ? "http://" : config.url,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI, v -> {
                    String u = Ha.normalizeUrl(v);
                    if (!u.isEmpty() && !Ha.validUrl(u)) {
                        status.setText(getString(R.string.address_bad, u));
                        return;
                    }
                    Config.saveUrl(this, u);
                    reload();
                });
    }

    private void editToken() {
        edit(getString(R.string.token), "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD, v -> {
            if (v.trim().isEmpty()) return;
            Config.saveToken(this, v);
            reload();
        });
    }

    private void reload() {
        config = Config.load(this);
        areas = null;
        problem = null;
        entities = new ArrayList<>();
        main.removeCallbacks(refresh);
        build();
        if (config.complete()) main.post(refresh);
    }

    private interface Done {
        void done(String value);
    }

    private void edit(String title, String start, int type, Done done) {
        EditText field = new EditText(this);
        field.setInputType(type);
        field.setSingleLine(true);
        field.setText(start);
        field.setSelection(start.length());
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(title)
                .setView(field)
                .setPositiveButton(R.string.save, (d, w) -> done.done(field.getText().toString()))
                .setNegativeButton(R.string.cancel, null)
                .show();
        field.requestFocus();
    }

    private void heading(String text) {
        TextView h = new TextView(this);
        h.setText(text);
        h.setTextSize(13);
        h.setTextColor(0xFF41BDF5);
        h.setPadding(px(16), px(18), 0, px(4));
        rows.addView(h);
    }

    private Row add(Row r) {
        rows.addView(r, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return r;
    }

    private int px(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
