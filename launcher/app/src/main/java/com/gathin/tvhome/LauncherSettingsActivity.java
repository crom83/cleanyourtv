package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Remote-friendly settings owned by the launcher, in two panes like the TV's own settings (local patch): sections
 * on the left, the focused section's rows on the right. Numbers change with Left/Right in their row, on/off
 * choices are switches, and a row with › opens a list. Back goes from the rows to the sections, then out.
 */
public final class LauncherSettingsActivity extends Activity {
    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));   // locale
    }

    static final String EXTRA_REORDER = "com.gathin.tvhome.extra.REORDER_FROM_SETTINGS";
    private static final int GRID = 0, LOOK = 1, SCREENSAVER = 2, SYSTEM = 3;
    private static final int[] COLUMNS = {4, 5, 6, 7};
    /** Tiles in the dock (dock_columns): 0 means as in a row, then 3 to 6. */
    private static final int[] DOCK_COLUMNS = {0, 3, 4, 5, 6};
    private static final int[] ROWS = {0, 1, 2, 3};
    private static final int[] DOCK_OPACITIES = {100, 70, 50, 30};
    private static final String[] CLOCK_FORMATS = {"system", "12", "24"};

    private SharedPreferences choices;
    private Config config;
    private int columns, rowsBelowDock, dockColumns, dockOpacity;
    private String clockFormat;
    private boolean animations, advancedEffects, liveCards;

    private LinearLayout sections, items;
    private TextView paneTitle;
    private ScrollView pane;
    private final List<TextView> sectionRows = new ArrayList<>();
    private int current = -1;
    /** While the right pane is rebuilt, focus passing over a section must not switch to it. */
    private boolean rebuilding;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_launcher_settings);
        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        config = Config.load(this);
        readChoices(config);
        sections = findViewById(R.id.sections);
        items = findViewById(R.id.items);
        paneTitle = findViewById(R.id.paneTitle);
        pane = findViewById(R.id.pane);
        // Mirror text alignment without globally opting the launcher into RTL.
        if (getResources().getBoolean(R.bool.rtl_menu_bar))
            findViewById(android.R.id.content).setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        int[] names = {R.string.settings_section_grid, R.string.settings_section_look,
                R.string.screensaver_title, R.string.settings_section_system};
        for (int i = 0; i < names.length; i++) {
            final int section = i;
            TextView row = new TextView(this);
            row.setText(names[i]);
            row.setTextSize(18);
            row.setTextColor(0xFFFFFFFF);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(18), 0, dp(18), 0);
            row.setBackgroundResource(R.drawable.settings_choice_bg);
            row.setFocusable(true);
            row.setId(View.generateViewId());
            row.setOnFocusChangeListener((v, focused) -> {
                if (focused && !rebuilding) show(section);
            });
            row.setOnClickListener(v -> focusFirstItem());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(52));
            lp.bottomMargin = dp(8);
            sections.addView(row, lp);
            sectionRows.add(row);
        }
        sectionRows.get(0).setNextFocusUpId(sectionRows.get(0).getId());
        sectionRows.get(sectionRows.size() - 1).setNextFocusDownId(sectionRows.get(sectionRows.size() - 1).getId());
        show(GRID);
        sectionRows.get(GRID).requestFocus();
    }

    private void readChoices(Config c) {
        columns = c.columns;
        rowsBelowDock = c.rowsBelowDock;
        dockOpacity = c.dockOpacity;
        clockFormat = c.clockFormat;
        animations = c.animations;
        advancedEffects = c.animations && c.advancedEffects;
        dockColumns = choices.getInt(MainActivity.DOCK_COLUMNS_KEY, getResources().getInteger(R.integer.dock_columns));
        liveCards = choices.getBoolean(MainActivity.LIVE_CARDS_KEY, getResources().getBoolean(R.bool.live_cards));
    }

    /** Back from the rows goes to their section; from the sections it leaves. */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && current >= 0 && items.findFocus() != null) {
            sectionRows.get(current).requestFocus();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void focusFirstItem() {
        for (int i = 0; i < items.getChildCount(); i++) {
            View v = items.getChildAt(i);
            if (v.isFocusable() && v.isEnabled()) {
                v.requestFocus();
                return;
            }
        }
    }

    /** Builds the right pane for a section; the section's row stays marked while focus is on the right. */
    private void show(int section) {
        if (section == current) return;
        current = section;
        rebuild();
    }

    /** Rebuilds the current section's rows, keeping focus on the row it was on. */
    private void rebuild() {
        View focused = items.findFocus();
        int keep = focused == null ? -1 : items.indexOfChild(rowOf(focused));
        // Removing the focused row hands focus to the first section for a moment; that must not open it
        rebuilding = true;
        if (focused != null) sectionRows.get(current).requestFocus();
        items.removeAllViews();
        for (int i = 0; i < sectionRows.size(); i++) sectionRows.get(i).setSelected(i == current);
        switch (current) {
            case GRID: grid(); break;
            case LOOK: look(); break;
            case SCREENSAVER: screensaver(); break;
            default: system(); break;
        }
        paneTitle.setText(sectionRows.get(current).getText());
        View first = null;
        for (int i = 0; i < items.getChildCount(); i++) {
            View v = items.getChildAt(i);
            if (!v.isFocusable()) continue;
            if (v.getId() == View.NO_ID) v.setId(View.generateViewId());
            v.setNextFocusLeftId(sectionRows.get(current).getId());
            if (first == null) first = v;
        }
        sectionRows.get(current).setNextFocusRightId(first == null ? View.NO_ID : first.getId());
        if (keep >= 0 && keep < items.getChildCount() && items.getChildAt(keep).isFocusable()) {
            items.getChildAt(keep).requestFocus();
        } else if (focused != null) {
            focusFirstItem();
        }
        rebuilding = false;
        if (focused == null) pane.scrollTo(0, 0);
    }

    private View rowOf(View v) {
        while (v.getParent() != items && v.getParent() instanceof View) v = (View) v.getParent();
        return v;
    }

    // ---- Sections -------------------------------------------------------------------------------------------

    private void grid() {
        stepper(getString(R.string.columns_label), labels(COLUMNS, null), indexOf(COLUMNS, columns), i -> {
            columns = COLUMNS[i];
            choices.edit().putInt("columns", columns).apply();
            changed();
        });
        stepper(getString(R.string.dock_columns_label), labels(DOCK_COLUMNS, getString(R.string.dock_columns_same)),
                indexOf(DOCK_COLUMNS, dockColumns), i -> {
                    dockColumns = DOCK_COLUMNS[i];
                    choices.edit().putInt(MainActivity.DOCK_COLUMNS_KEY, dockColumns).apply();
                    changed();
                });
        stepper(getString(R.string.rows_below_dock_label), labels(ROWS, null), indexOf(ROWS, rowsBelowDock), i -> {
            rowsBelowDock = ROWS[i];
            choices.edit().putInt(Config.ROWS_BELOW_DOCK_KEY, rowsBelowDock).apply();
            changed();
        });
        String[] opacity = new String[DOCK_OPACITIES.length];
        for (int i = 0; i < opacity.length; i++) opacity[i] = DOCK_OPACITIES[i] + "%";
        stepper(getString(R.string.dock_opacity_label), opacity, indexOf(DOCK_OPACITIES, dockOpacity), i -> {
            dockOpacity = DOCK_OPACITIES[i];
            choices.edit().putInt(Config.DOCK_OPACITY_KEY, dockOpacity).apply();
            changed();
        });
        if (config.launcherSettingsButton) {
            link(getString(R.string.reorder), null, () -> {
                setResult(RESULT_OK, new Intent().putExtra(EXTRA_REORDER, true));
                finish();
            });
        }
    }

    private void look() {
        String[] clocks = {getString(R.string.clock_format_system), getString(R.string.clock_format_12),
                getString(R.string.clock_format_24)};
        int clock = 0;
        for (int i = 0; i < CLOCK_FORMATS.length; i++) if (CLOCK_FORMATS[i].equals(clockFormat)) clock = i;
        stepper(getString(R.string.dream_name), clocks, clock, i -> {
            clockFormat = CLOCK_FORMATS[i];
            choices.edit().putString(Config.CLOCK_FORMAT_KEY, clockFormat).apply();
            changed();
        });
        String wallpaper = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        link(getString(R.string.wallpaper_label), "none".equals(wallpaper) ? getString(R.string.wallpaper_none)
                : wallpaper != null ? wallpaper : getString(R.string.wallpaper_theme_default), this::showWallpaperPicker);
        toggle(getString(R.string.animations_label), animations, true, () -> {
            animations = !animations;
            if (!animations) advancedEffects = false;
            choices.edit().putBoolean("animations", animations).putBoolean("advanced_effects", advancedEffects).apply();
            changed();
            rebuild();
        });
        toggle(getString(R.string.advanced_effects), advancedEffects, animations, () -> {
            if (advancedEffects) {
                setAdvanced(false);
            } else {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.advanced_effects)
                        .setMessage(R.string.advanced_effects_warning)
                        .setPositiveButton(R.string.enable, (dialog, which) -> setAdvanced(true))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
        });
        hint(getString(R.string.advanced_effects_warning));
        if (config.moduleCards) {   // live tiles are a module card's extra
            toggle(getString(R.string.live_cards_label), liveCards, true, () -> {
                liveCards = !liveCards;
                choices.edit().putBoolean(MainActivity.LIVE_CARDS_KEY, liveCards).apply();
                changed();
                rebuild();
            });
            hint(getString(R.string.live_cards_hint));
        }
    }

    private void screensaver() {
        boolean canWrite = Screensaver.canWrite(this);
        link(getString(R.string.screensaver_title), screensaverName(), this::pickScreensaver);
        int now = Screensaver.minutes(this);
        if (canWrite) {
            String[] minutes = new String[Screensaver.MINUTES.length];
            int at = 0;
            for (int i = 0; i < minutes.length; i++) {
                minutes[i] = getString(R.string.minutes_short, Screensaver.MINUTES[i]);
                if (Screensaver.MINUTES[i] <= now) at = i;
            }
            stepper(getString(R.string.screensaver_delay_title), minutes, at, i -> {
                try {
                    Screensaver.setMinutes(this, Screensaver.MINUTES[i]);
                } catch (SecurityException e) {
                    Toast.makeText(this, R.string.screensaver_no_settings, Toast.LENGTH_LONG).show();
                }
            });
        } else {
            link(getString(R.string.screensaver_delay_title), now > 0 ? getString(R.string.minutes_short, now) : null,
                    this::openSystemScreensaver);
        }
        hint(getString(canWrite ? R.string.screensaver_hint : R.string.screensaver_hint_system));
    }

    private void system() {
        if (config.openOnStartChoice) {
            link(getString(R.string.open_on_start_label), openOnStartLabel(), this::showOpenOnStartPicker);
        }
        View follow = link(getString(R.string.follow_theme), null, () -> {
            if (!hasOnTvChoices()) return;
            choices.edit().remove("columns").remove(MainActivity.DOCK_COLUMNS_KEY).remove(Config.ROWS_BELOW_DOCK_KEY)
                    .remove("animations").remove("advanced_effects").remove(Config.WALLPAPER_CHOICE_KEY)
                    .remove(Config.DOCK_OPACITY_KEY).remove(Config.CLOCK_FORMAT_KEY).remove(OpenOnStart.KEY)
                    .remove(MainActivity.LIVE_CARDS_KEY).commit();
            config = Config.load(this);
            readChoices(config);
            changed();
            rebuild();
        });
        follow.setAlpha(hasOnTvChoices() ? 1f : 0.5f);
        hint(getString(R.string.follow_theme_hint));
        link(getString(R.string.home_settings), null, () -> open(new Intent(this, HomeSettingsActivity.class)));
        hint(getString(R.string.home_settings_help));
        link(getString(R.string.tv_settings), null, () -> open(SystemSettings.intent(this, Settings.ACTION_SETTINGS)));
    }

    // ---- Rows -----------------------------------------------------------------------------------------------

    private LinearLayout row(String label) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(20), 0, dp(16), 0);
        row.setBackgroundResource(R.drawable.settings_choice_bg);
        row.setFocusable(true);
        row.setClickable(true);
        TextView name = new TextView(this);
        name.setText(label);
        name.setTextSize(18);
        name.setTextColor(0xFFFFFFFF);
        name.setSingleLine(true);
        row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(54));
        lp.topMargin = dp(8);
        items.addView(row, lp);
        return row;
    }

    private TextView valueText(String value) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(17);
        v.setTextColor(0xCCFFFFFF);
        v.setSingleLine(true);
        return v;
    }

    /**
     * ◀ value ▶: Left and Right step through the values in the row (Left at the first value goes back to the
     * sections, as everywhere else on the right), OK steps forward and wraps around.
     */
    private void stepper(String label, String[] values, int start, IntConsumer onChange) {
        LinearLayout row = row(label);
        TextView left = valueText("◀"), value = valueText(""), right = valueText("▶");
        value.setGravity(Gravity.CENTER);
        value.setMinWidth(dp(96));
        value.setPadding(dp(14), 0, dp(14), 0);
        value.setTextColor(0xFFFFFFFF);
        row.addView(left);
        row.addView(value);
        row.addView(right);
        final int[] at = {Math.max(0, start)};
        Runnable paint = () -> {
            value.setText(values[at[0]]);
            left.setAlpha(at[0] > 0 ? 0.8f : 0.2f);
            right.setAlpha(at[0] < values.length - 1 ? 0.8f : 0.2f);
        };
        paint.run();
        IntConsumer move = to -> {
            at[0] = to;
            paint.run();
            onChange.accept(to);
        };
        row.setOnKeyListener((v, key, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            boolean rtl = v.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
            int d = key == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : key == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 0;
            if (rtl) d = -d;
            if (d == 0) return false;
            int to = at[0] + d;
            if (to < 0) return false;                       // off the first value: back to the sections
            if (to < values.length) move.accept(to);
            return true;                                    // past the last value: stay put
        });
        row.setOnClickListener(v -> move.accept((at[0] + 1) % values.length));
    }

    /** A switch; OK flips it. A disabled one is dimmed and skipped by focus. */
    private void toggle(String label, boolean on, boolean enabled, Runnable onFlip) {
        LinearLayout row = row(label);
        Switch sw = new Switch(this);
        sw.setChecked(on);
        sw.setFocusable(false);
        sw.setClickable(false);
        int[][] states = {{android.R.attr.state_checked}, {}};
        // On: a green thumb on a green track; off: grey on grey (the switch draws its track see-through)
        sw.setThumbTintList(new ColorStateList(states, new int[] {0xFF8FD3A6, 0xFFE6EDF3}));
        sw.setTrackTintList(new ColorStateList(states, new int[] {0xFF74A987, 0xFF9AA4AE}));
        row.addView(sw);
        row.setEnabled(enabled);
        row.setFocusable(enabled);
        row.setAlpha(enabled ? 1f : 0.45f);
        row.setOnClickListener(v -> onFlip.run());
    }

    /** A row that opens something: its current value, then ›. */
    private View link(String label, String value, Runnable onClick) {
        LinearLayout row = row(label);
        if (value != null) {
            TextView v = valueText(value);
            v.setPadding(dp(12), 0, dp(10), 0);
            v.setMaxWidth(dp(260));
            v.setEllipsize(android.text.TextUtils.TruncateAt.END);
            row.addView(v);
        }
        TextView chevron = valueText("›");
        chevron.setTextSize(24);
        chevron.setPadding(dp(4), 0, dp(4), dp(3));
        row.addView(chevron);
        row.setOnClickListener(v -> onClick.run());
        return row;
    }

    private void hint(String text) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(13);
        v.setTextColor(0xFFACB8C1);
        v.setPadding(dp(4), dp(6), dp(4), dp(4));
        items.addView(v);
    }

    private static String[] labels(int[] values, String zero) {
        String[] out = new String[values.length];
        for (int i = 0; i < values.length; i++) out[i] = values[i] == 0 && zero != null ? zero : String.valueOf(values[i]);
        return out;
    }

    private static int indexOf(int[] values, int v) {
        for (int i = 0; i < values.length; i++) if (values[i] == v) return i;
        return 0;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    // ---- Pickers --------------------------------------------------------------------------------------------

    private void showWallpaperPicker() {
        File dir = Config.dir(this);
        List<File> images = new ArrayList<>(WallpaperRotation.images(dir == null ? null : new File(dir, "wallpapers")));
        // A theme with a single wallpaper.jpg offers that picture too (Config maps the choice back to it).
        File single = dir == null ? null : new File(dir, Config.WALLPAPER_NAME);
        if (single != null && single.isFile()) images.add(0, single);
        String[] labels = new String[images.size() + 1];
        for (int i = 0; i < images.size(); i++) labels[i] = images.get(i).getName();
        labels[images.size()] = getString(R.string.wallpaper_none);
        String current = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        int checked = "none".equals(current) ? images.size() : indexOf(images, current);
        new AlertDialog.Builder(this)
                .setTitle(R.string.wallpaper_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    choices.edit().putString(Config.WALLPAPER_CHOICE_KEY,
                            which == images.size() ? "none" : images.get(which).getName()).apply();
                    changed();
                    Toast.makeText(this, R.string.wallpaper_applied, Toast.LENGTH_SHORT).show();
                    rebuild();
                })
                .setNeutralButton(R.string.wallpaper_theme_default, (dialog, which) -> {
                    choices.edit().remove(Config.WALLPAPER_CHOICE_KEY).apply();
                    changed();
                    rebuild();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Off, then every app with a TV entry point by name. Saved at once; it is read on the next boot. */
    private void showOpenOnStartPicker() {
        PackageManager pm = getPackageManager();
        List<String[]> apps = new ArrayList<>();
        Intent tv = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo r : pm.queryIntentActivities(tv, 0)) {
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            boolean seen = false;
            for (String[] a : apps) seen |= a[1].equals(pkg);
            if (!seen) apps.add(new String[] {r.loadLabel(pm).toString(), pkg});
        }
        Collections.sort(apps, (a, b) -> a[0].compareToIgnoreCase(b[0]));
        String[] labels = new String[apps.size() + 1];
        labels[0] = getString(R.string.off);
        String current = choices.getString(OpenOnStart.KEY, "");
        int checked = 0;
        for (int i = 0; i < apps.size(); i++) {
            labels[i + 1] = apps.get(i)[0];
            if (apps.get(i)[1].equals(current)) checked = i + 1;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.open_on_start_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == 0) choices.edit().remove(OpenOnStart.KEY).apply();
                    else {
                        OpenOnStart.skipThisBoot(this);
                        choices.edit().putString(OpenOnStart.KEY, apps.get(which - 1)[1]).apply();
                    }
                    rebuild();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** The picked app's name, its package when it is gone, or Off. */
    private String openOnStartLabel() {
        String pkg = choices.getString(OpenOnStart.KEY, "");
        if (pkg.isEmpty()) return getString(R.string.off);
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    private static int indexOf(List<File> images, String name) {
        if (name == null) return -1;
        for (int i = 0; i < images.size(); i++) if (images.get(i).getName().equals(name)) return i;
        return -1;
    }

    /** The screensaver in use by name, "Off", or its class name when it is not installed any more. */
    private String screensaverName() {
        android.content.ComponentName now = Screensaver.current(this);
        if (now == null) return getString(R.string.screensaver_off);
        for (Screensaver.Dream d : Screensaver.installed(this)) if (d.component.equals(now)) return d.label;
        return now.getShortClassName();
    }

    private void openSystemScreensaver() {
        if (!Screensaver.openSystem(this)) Toast.makeText(this, R.string.screensaver_no_settings, Toast.LENGTH_LONG).show();
    }

    /** Off, then every installed screensaver; the current one is checked. */
    private void pickScreensaver() {
        if (!Screensaver.canWrite(this)) {
            openSystemScreensaver();
            return;
        }
        List<Screensaver.Dream> dreams = Screensaver.installed(this);
        android.content.ComponentName now = Screensaver.current(this);
        String[] labels = new String[dreams.size() + 1];
        labels[0] = getString(R.string.screensaver_off);
        int checked = 0;
        for (int i = 0; i < dreams.size(); i++) {
            labels[i + 1] = dreams.get(i).label;
            if (dreams.get(i).component.equals(now)) checked = i + 1;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.screensaver_title)
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    try {
                        Screensaver.set(this, which == 0 ? null : dreams.get(which - 1).component);
                    } catch (SecurityException e) {
                        Toast.makeText(this, R.string.screensaver_no_settings, Toast.LENGTH_LONG).show();
                    }
                    d.dismiss();
                    rebuild();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /**
     * A choice was saved. Home rebuilds when it next resumes because the change count moved, whether these
     * settings are left with Back or with the remote's Home button (which cancels the activity result).
     */
    private void changed() {
        setResult(RESULT_OK);
        choices.edit().putLong(Config.SETTINGS_REVISION_KEY, choices.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
    }

    private void setAdvanced(boolean enabled) {
        advancedEffects = enabled;
        choices.edit().putBoolean("advanced_effects", enabled).apply();
        changed();
        rebuild();
    }

    private boolean hasOnTvChoices() {
        return choices.contains("columns") || choices.contains(MainActivity.DOCK_COLUMNS_KEY) || choices.contains(Config.ROWS_BELOW_DOCK_KEY)
                || choices.contains("animations") || choices.contains("advanced_effects")
                || choices.contains(Config.WALLPAPER_CHOICE_KEY) || choices.contains(Config.DOCK_OPACITY_KEY)
                || choices.contains(Config.CLOCK_FORMAT_KEY) || choices.contains(OpenOnStart.KEY)
                || choices.contains(MainActivity.LIVE_CARDS_KEY);
    }

    private void open(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        }
    }
}
