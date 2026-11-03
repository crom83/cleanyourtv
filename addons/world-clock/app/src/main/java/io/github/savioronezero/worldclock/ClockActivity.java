package io.github.savioronezero.worldclock;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Offline foreground screen. The system supplies the clock, time zones and keyboard. */
public final class ClockActivity extends Activity {
    private static final int INK = Color.rgb(237, 245, 252), MUTED = Color.rgb(169, 190, 210);
    private static final int ACCENT = Color.rgb(131, 214, 222);
    /** Secondary text on the coloured skies: white, a little see-through. */
    private static final int SOFT = 0xCCFFFFFF;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<ClockViews> clocks = new ArrayList<>();
    private final List<View> focusNodes = new ArrayList<>();
    private List<String> ids, catalogue;
    private SharedPreferences preferences;
    private AlertDialog dialog;
    private int selected, format;
    private boolean resumed;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!resumed) return;
            refresh();
            handler.postDelayed(this, ClockModel.nextMinute(System.currentTimeMillis()));
        }
    };
    private final BroadcastReceiver changedTime = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (resumed) {
                handler.removeCallbacks(tick);
                tick.run();
            }
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = ClockPreferences.store(this);
        ClockPreferences saved = new ClockPreferences(preferences);
        format = saved.format;
        ids = saved.ids;
        catalogue = ClockModel.catalogue();
        selected = state == null ? 0 : Math.max(0, Math.min(ids.size() - 1, state.getInt("selected", 0)));
        render();
    }

    @Override protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        filter.addAction(Intent.ACTION_DATE_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(changedTime, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(changedTime, filter);
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        handler.removeCallbacks(tick);
        tick.run();
    }

    @Override protected void onPause() {
        resumed = false;
        handler.removeCallbacks(tick);
        if (dialog != null) dialog.dismiss();
        super.onPause();
    }

    @Override protected void onStop() {
        unregisterReceiver(changedTime);
        super.onStop();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(tick);
        super.onDestroy();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putInt("selected", selected);
        super.onSaveInstanceState(state);
    }

    private void save() {
        preferences.edit().putString("zones_v1", new JSONArray(ids).toString())
                .putInt("format_v1", format).apply();
    }

    private void render() {
        clocks.clear(); focusNodes.clear();
        LinearLayout root = column();
        root.setClipChildren(false);   // a focused card grows a little past its row
        root.setClipToPadding(false);
        root.setPadding(dp(36), dp(22), dp(36), dp(20));
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(8, 19, 34), Color.rgb(18, 37, 55)}));
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(getString(R.string.title), 28, INK);
        title.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        toolbar.addView(title, new LinearLayout.LayoutParams(0, dp(54), 1));
        Button add = button(getString(R.string.add_city));
        add.setOnClickListener(v -> {
            if (ids.size() == ClockModel.LIMIT) Toast.makeText(this, R.string.city_limit, Toast.LENGTH_SHORT).show();
            else chooseCity(-1);
        });
        toolbar.addView(add, buttonParams()); focusNodes.add(add);
        Button clockFormat = button(getString(R.string.clock_format));
        clockFormat.setOnClickListener(v -> chooseFormat());
        toolbar.addView(clockFormat, buttonParams()); focusNodes.add(clockFormat);
        Button screensaver = button(getString(R.string.screensaver));
        screensaver.setOnClickListener(v -> screensaverOptions());
        toolbar.addView(screensaver, buttonParams()); focusNodes.add(screensaver);
        root.addView(toolbar);
        LinearLayout grid = column();
        grid.setClipChildren(false);
        LinearLayout.LayoutParams gridTop = new LinearLayout.LayoutParams(-1, 0, 1);
        gridTop.topMargin = dp(18);
        root.addView(grid, gridTop);
        int columns = Math.min(3, ids.size());
        for (int start = 0; start < ids.size(); start += columns) {
            LinearLayout row = new LinearLayout(this);
            row.setClipChildren(false);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, 0, 1);
            if (start > 0) rowParams.topMargin = dp(14);
            grid.addView(row, rowParams);
            for (int col = 0; col < columns; col++) {
                final int index = start + col;
                LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(0, -1, 1);
                if (col > 0) cardParams.leftMargin = dp(14);
                if (index >= ids.size()) { row.addView(new View(this), cardParams); continue; }
                ClockViews card = new ClockViews(ids.get(index));
                clocks.add(card);
                row.addView(card.root, cardParams);
                focusNodes.add(card.root);
                card.root.setOnFocusChangeListener((v, focused) -> {
                    if (focused) selected = index;
                    card.paint();
                    v.animate().scaleX(focused ? 1.03f : 1f).scaleY(focused ? 1.03f : 1f).setDuration(150).start();
                });
                card.root.setOnClickListener(v -> cityOptions(index));
            }
        }
        TextView help = text(getString(R.string.controls), 14, MUTED);
        help.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams helpParams = new LinearLayout.LayoutParams(-1, dp(42));
        helpParams.topMargin = dp(10);
        root.addView(help, helpParams);
        setContentView(root);
        for (View node : focusNodes) node.setId(View.generateViewId());
        for (int i = 0; i < focusNodes.size(); i++) {
            View node = focusNodes.get(i);
            node.setNextFocusLeftId(target(i, ClockNavigation.LEFT));
            node.setNextFocusRightId(target(i, ClockNavigation.RIGHT));
            node.setNextFocusUpId(target(i, ClockNavigation.UP));
            node.setNextFocusDownId(target(i, ClockNavigation.DOWN));
        }
        refresh();
        focusNodes.get(ClockNavigation.TOOLBAR + Math.max(0, Math.min(selected, ids.size() - 1))).requestFocus();
    }

    private int target(int node, int direction) {
        boolean rtl = getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        return focusNodes.get(ClockNavigation.next(node, ids.size(), direction, rtl)).getId();
    }

    private void refresh() {
        // One instant for every city: clocks cannot disagree when the minute/date rolls over mid-refresh.
        Instant instant = Instant.now();
        ZoneId local = ZoneId.systemDefault();
        Locale locale = Locale.getDefault();
        boolean twentyFour = format == 2 || (format == 0 && android.text.format.DateFormat.is24HourFormat(this));
        for (ClockViews views : clocks) {
            ClockModel.Reading reading = ClockModel.at(views.id, instant, local, twentyFour, locale);
            if (reading.period.isEmpty()) views.time.setText(reading.time);
            else {
                SpannableString display = new SpannableString(reading.time + " " + reading.period);
                display.setSpan(new RelativeSizeSpan(0.4f), reading.time.length() + 1, display.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                views.time.setText(display);
            }
            views.date.setText(reading.date);
            // How far from here, or "here" on the TV's own clock; the UTC offset when it says nothing else
            String diff = ClockModel.difference(reading.minutesFromLocal);
            views.offset.setText(ClockModel.LOCAL.equals(views.id) ? getString(R.string.here)
                    : diff.isEmpty() ? getString(R.string.same_time, reading.offset)
                    : getString(R.string.hours_from_here, diff));
            // Yesterday or tomorrow only where the day differs; the line keeps its height so cards line up
            views.day.setText(reading.dayDifference == 1 ? getString(R.string.tomorrow)
                    : reading.dayDifference == -1 ? getString(R.string.yesterday)
                    : reading.dayDifference == 0 ? " "
                    : getResources().getQuantityString(R.plurals.day_difference,
                            Math.abs(reading.dayDifference), reading.dayDifference));
            views.sky = DayLight.colours(reading.hour);
            views.glyph.set(DayLight.sunUp(reading.hour), views.sky[0]);
            views.paint();
        }
    }

    private void cityOptions(int index) {
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        labels.add(getString(R.string.change_city)); actions.add(() -> chooseCity(index));
        if (index > 0) {
            labels.add(getString(R.string.move_earlier));
            actions.add(() -> { ids = ClockModel.move(ids, index, index - 1); selected = index - 1; save(); render(); });
        }
        if (index + 1 < ids.size()) {
            labels.add(getString(R.string.move_later));
            actions.add(() -> { ids = ClockModel.move(ids, index, index + 1); selected = index + 1; save(); render(); });
        }
        if (ids.size() > 1) {
            labels.add(getString(R.string.remove_city));
            actions.add(() -> { ids = ClockModel.remove(ids, index); selected = Math.min(index, ids.size() - 1); save(); render(); });
        }
        show(new AlertDialog.Builder(this).setTitle(cityLabel(ids.get(index)))
                .setItems(labels.toArray(new String[0]), (d, which) -> actions.get(which).run()).create());
    }

    /** The city in the TV's language ("Лондон"), from the system's own zone names; the TV's own zone for local. */
    private String cityLabel(String id) {
        return TimeZoneNamesCache.label(this, id);
    }

    private void chooseFormat() {
        show(new AlertDialog.Builder(this).setTitle(R.string.clock_format)
                .setSingleChoiceItems(new String[]{getString(R.string.format_system), getString(R.string.format_12),
                        getString(R.string.format_24)}, format, (d, which) -> {
                    format = which; save(); refresh(); d.dismiss();
                }).setNegativeButton(R.string.close, null).create());
    }

    private void screensaverOptions() {
        ClockPreferences options = new ClockPreferences(preferences);
        String[] brightness = {getString(R.string.dim), getString(R.string.medium), getString(R.string.bright)};
        String[] backgrounds = {getString(R.string.gradient), getString(R.string.starfield),
                getString(R.string.world_map), getString(R.string.plain)};
        String[] labels = {getString(R.string.preview),
                getString(R.string.background_option, backgrounds[options.background]),
                getString(R.string.motion_option, getString(options.motion ? R.string.motion_on : R.string.motion_reduced)),
                getString(R.string.brightness_option, brightness[options.brightness]),
                getString(R.string.tv_screensaver_settings)};
        show(new AlertDialog.Builder(this).setTitle(R.string.screensaver)
                .setItems(labels, (d, which) -> {
                    if (which == 0) startActivity(new Intent(this, ClockPreviewActivity.class));
                    else if (which == 1) {
                        show(new AlertDialog.Builder(this).setTitle(R.string.background)
                                .setSingleChoiceItems(backgrounds, options.background, (choice, value) -> {
                                    preferences.edit().putInt("dream_background_v1", value).apply();
                                    choice.dismiss();
                                }).setNegativeButton(R.string.close, null).create());
                    } else if (which == 2) {
                        preferences.edit().putBoolean("dream_motion_v1", !options.motion).apply();
                        screensaverOptions();
                    } else if (which == 3) {
                        show(new AlertDialog.Builder(this).setTitle(R.string.clock_brightness)
                                .setSingleChoiceItems(brightness, options.brightness, (choice, value) -> {
                                    preferences.edit().putInt("dream_brightness_v1", value).apply();
                                    choice.dismiss();
                                }).setNegativeButton(R.string.close, null).create());
                    } else {
                        try { startActivity(new Intent(android.provider.Settings.ACTION_DREAM_SETTINGS)); }
                        catch (android.content.ActivityNotFoundException | SecurityException unavailable) {
                            Toast.makeText(this, R.string.screensaver_unavailable, Toast.LENGTH_LONG).show();
                        }
                    }
                }).setNegativeButton(R.string.close, null).create());
    }

    private void chooseCity(int replacing) {
        LinearLayout panel = column(); panel.setPadding(dp(24), dp(8), dp(24), 0);
        EditText search = new EditText(this);
        search.setSingleLine(true); search.setHint(R.string.search_city);
        search.setImeOptions(EditorInfo.IME_ACTION_DONE);
        panel.addView(search, new LinearLayout.LayoutParams(-1, dp(52)));
        panel.addView(text(getString(R.string.search_hint), 13, MUTED));
        TextView empty = text(getString(R.string.no_cities), 18, INK);
        empty.setPadding(0, dp(24), 0, dp(24));
        panel.addView(empty);
        ListView list = new ListView(this); list.setId(View.generateViewId());
        list.setItemsCanFocus(false);
        panel.addView(list, new LinearLayout.LayoutParams(-1, dp(230)));
        list.setEmptyView(empty);
        final List<String> matches = new ArrayList<>();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        list.setAdapter(adapter);
        Runnable filter = () -> {
            matches.clear();
            matches.addAll(ClockModel.search(catalogue, search.getText().toString(), ids, replacing,
                    id -> cityLabel(id) + " " + TimeZoneNamesCache.country(id)));
            adapter.setNotifyOnChange(false);
            adapter.clear();
            for (String id : matches) {
                // "Пекин · Китай": the country where the system knows it, else the zone's area
                String where = TimeZoneNamesCache.countryName(id, Locale.getDefault());
                adapter.add(cityLabel(id) + "  ·  " + (where.isEmpty() ? ClockModel.area(id) : where));
            }
            adapter.notifyDataSetChanged();
            if (!matches.isEmpty()) list.setSelection(0);
        };
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filter.run(); }
            @Override public void afterTextChanged(Editable text) {}
        });
        search.setOnKeyListener((v, key, event) -> {
            if (key == KeyEvent.KEYCODE_DPAD_DOWN && event.getAction() == KeyEvent.ACTION_DOWN && !matches.isEmpty()) {
                list.requestFocus(); list.setSelection(0); return true;
            }
            return false;
        });
        search.setOnEditorActionListener((v, action, event) -> {
            if (action != EditorInfo.IME_ACTION_DONE) return false;
            InputMethodManager ime = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (ime != null) ime.hideSoftInputFromWindow(search.getWindowToken(), 0);
            if (!matches.isEmpty()) { list.requestFocus(); list.setSelection(0); }
            return true;
        });
        AlertDialog picker = new AlertDialog.Builder(this).setTitle(replacing < 0 ? R.string.add_city : R.string.change_city)
                .setView(panel).setNegativeButton(R.string.close, null).create();
        list.setOnItemClickListener((parent, view, position, itemId) -> {
            String id = matches.get(position);
            List<String> updated = replacing < 0 ? ClockModel.add(ids, id) : ClockModel.replace(ids, replacing, id);
            if (updated.equals(ids)) {
                if (replacing >= 0 && id.equals(ids.get(replacing))) { picker.dismiss(); return; }
                Toast.makeText(this, R.string.already_shown, Toast.LENGTH_SHORT).show(); return;
            }
            ids = updated;
            selected = replacing < 0 ? ids.size() - 1 : replacing;
            save(); picker.dismiss(); render();
        });
        filter.run();
        show(picker);
        if (picker.getWindow() != null) picker.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        search.requestFocus();
    }

    private void show(AlertDialog next) {
        if (dialog != null) dialog.dismiss();
        dialog = next;
        next.setOnDismissListener(d -> { if (dialog == next) dialog = null; });
        next.show();
    }

    /** One city: its sky as the background, the sun or moon in the corner, the time and how far it is from here. */
    private final class ClockViews {
        final String id;
        final FrameLayout root;
        final TextView time, date, offset, day;
        final SkyGlyph glyph;
        int[] sky = DayLight.colours(12);
        ClockViews(String id) {
            this.id = id;
            root = new FrameLayout(ClockActivity.this);
            root.setFocusable(true); root.setClickable(true);
            LinearLayout column = column(); column.setGravity(Gravity.CENTER);
            column.setPadding(dp(14), dp(16), dp(14), dp(14));
            root.addView(column, new FrameLayout.LayoutParams(-1, -1));
            glyph = new SkyGlyph(ClockActivity.this);
            FrameLayout.LayoutParams glyphParams = new FrameLayout.LayoutParams(dp(30), dp(30), Gravity.TOP | Gravity.END);
            glyphParams.setMargins(0, dp(12), dp(12), 0);
            root.addView(glyph, glyphParams);
            TextView city = text(cityLabel(id), 22, INK);
            city.setMaxLines(1); city.setGravity(Gravity.CENTER);
            city.setPadding(dp(30), 0, dp(30), 0);
            city.setAutoSizeTextTypeUniformWithConfiguration(14, 22, 1, TypedValue.COMPLEX_UNIT_SP);
            column.addView(city, new LinearLayout.LayoutParams(-1, dp(32)));
            time = text("", 64, INK); time.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
            time.setGravity(Gravity.CENTER); time.setMaxLines(1); time.setTextDirection(View.TEXT_DIRECTION_LTR);
            time.setAutoSizeTextTypeUniformWithConfiguration(18, 64, 2, TypedValue.COMPLEX_UNIT_SP);
            time.setShadowLayer(dp(8), 0, dp(1), 0x40000000);
            column.addView(time, new LinearLayout.LayoutParams(-1, 0, 1));
            date = text("", 17, INK); date.setGravity(Gravity.CENTER); column.addView(date);
            offset = text("", 13, SOFT); offset.setGravity(Gravity.CENTER); column.addView(offset);
            day = text(" ", 13, ACCENT); day.setGravity(Gravity.CENTER); column.addView(day);
            paint();
        }

        /** The sky, rounded, with a white outline while focused. */
        void paint() {
            GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, sky);
            bg.setCornerRadius(dp(18));
            if (root.isFocused()) bg.setStroke(dp(3), INK);
            else bg.setStroke(dp(1), 0x26FFFFFF);
            root.setBackground(bg);
        }
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private TextView text(String value, int size, int color) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(color);
        v.setGravity(Gravity.CENTER_VERTICAL); return v;
    }
    private Button button(String label) {
        Button v = new Button(this); v.setText(label); v.setTextSize(14); v.setAllCaps(false); v.setTextColor(INK);
        v.setPadding(dp(16), 0, dp(16), 0); v.setBackground(focusBackground()); return v;
    }
    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, dp(48)); p.leftMargin = dp(10); return p;
    }
    private StateListDrawable focusBackground() {
        StateListDrawable state = new StateListDrawable();
        state.addState(new int[]{android.R.attr.state_focused}, shape(Color.rgb(28, 62, 80), ACCENT));
        state.addState(new int[]{}, shape(Color.rgb(19, 39, 57), Color.rgb(44, 64, 83)));
        return state;
    }
    private GradientDrawable shape(int fill, int border) {
        GradientDrawable shape = new GradientDrawable(); shape.setColor(fill); shape.setCornerRadius(dp(14));
        shape.setStroke(dp(2), border); return shape;
    }
}
