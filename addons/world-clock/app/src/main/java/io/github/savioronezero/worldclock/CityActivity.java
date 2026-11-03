package io.github.savioronezero.worldclock;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * One clock full screen: the city's photo from Wikipedia (its sky while the photo loads, or when offline), the time,
 * the date, how far it is from here and today's sunrise and sunset. Left/Right step through the saved clocks.
 */
public final class CityActivity extends Activity {
    static final String EXTRA_IDS = "ids", EXTRA_INDEX = "index";

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private List<String> ids = new ArrayList<>();
    private int index;
    private int loadToken;
    private View root;
    private ImageView photo;
    private TextView country, name, time, date, diff, sun, credit;
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refresh();
            main.postDelayed(this, ClockModel.nextMinute(System.currentTimeMillis()));
        }
    };

    static Intent intent(Activity from, List<String> ids, int index) {
        return new Intent(from, CityActivity.class)
                .putStringArrayListExtra(EXTRA_IDS, new ArrayList<>(ids)).putExtra(EXTRA_INDEX, index);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_city);
        List<String> given = getIntent().getStringArrayListExtra(EXTRA_IDS);
        if (given != null) for (String id : given) if (ClockModel.valid(id)) ids.add(id);
        if (ids.isEmpty()) {
            finish();
            return;
        }
        index = Math.max(0, Math.min(ids.size() - 1, getIntent().getIntExtra(EXTRA_INDEX, 0)));
        root = findViewById(R.id.cityRoot);
        photo = findViewById(R.id.cityPhoto);
        country = findViewById(R.id.cityCountry);
        name = findViewById(R.id.cityName);
        time = findViewById(R.id.cityTime);
        date = findViewById(R.id.cityDate);
        diff = findViewById(R.id.cityDiff);
        sun = findViewById(R.id.citySun);
        credit = findViewById(R.id.cityCredit);
        findViewById(R.id.cityHint).setVisibility(ids.size() > 1 ? View.VISIBLE : View.GONE);
        showCity();
    }

    @Override
    protected void onResume() {
        super.onResume();
        main.removeCallbacks(tick);
        tick.run();
    }

    @Override
    protected void onPause() {
        main.removeCallbacks(tick);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        int d = keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 0;
        if (d != 0 && ids.size() > 1) {
            index = Math.floorMod(index + d, ids.size());
            showCity();
            refresh();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    /** The city's name, country and photo; the time lines are {@link #refresh}. */
    private void showCity() {
        String id = ids.get(index);
        name.setText(TimeZoneNamesCache.label(this, id));
        country.setText(TimeZoneNamesCache.countryName(id, Locale.getDefault()));
        credit.setText("");
        photo.animate().cancel();
        photo.setAlpha(0f);
        photo.setImageDrawable(null);
        int token = ++loadToken;
        io.execute(() -> {
            CityPhoto p = CityPhoto.load(getApplicationContext(), id);
            Bitmap b = p == null ? null : decode(p.image.getPath());
            main.post(() -> {
                if (isDestroyed() || token != loadToken || b == null) return;
                photo.setImageBitmap(b);
                photo.setScaleX(1f);
                photo.setScaleY(1f);
                // In slowly, then a very slow drift closer, so the screen is never quite still
                photo.animate().alpha(1f).setDuration(700).withEndAction(() ->
                        photo.animate().scaleX(1.08f).scaleY(1.08f).setDuration(40_000).start()).start();
                credit.setText(getString(R.string.city_photo_credit, p.credit));
            });
        });
    }

    /** A 1920 px photo decoded at most at screen width, so a big file never costs more memory than the screen. */
    private Bitmap decode(String path) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, o);
        int width = getResources().getDisplayMetrics().widthPixels;
        o.inSampleSize = 1;
        while (o.outWidth / (o.inSampleSize * 2) >= width) o.inSampleSize *= 2;
        o.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(path, o);
    }

    private void refresh() {
        if (ids.isEmpty()) return;
        String id = ids.get(index);
        Instant now = Instant.now();
        ZoneId local = ZoneId.systemDefault();
        Locale locale = Locale.getDefault();
        boolean twentyFour = android.text.format.DateFormat.is24HourFormat(this);
        ClockModel.Reading r = ClockModel.at(id, now, local, twentyFour, locale);
        time.setText(r.period.isEmpty() ? r.time : r.time + " " + r.period);
        ZoneId zone = ClockModel.LOCAL.equals(id) ? local : ZoneId.of(ClockModel.zone(id));
        date.setText(now.atZone(zone).format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale)));
        String d = ClockModel.difference(r.minutesFromLocal);
        String where = ClockModel.LOCAL.equals(id) ? getString(R.string.here)
                : d.isEmpty() ? getString(R.string.same_time, r.offset) : getString(R.string.hours_from_here, d);
        String day = r.dayDifference == 1 ? getString(R.string.tomorrow)
                : r.dayDifference == -1 ? getString(R.string.yesterday) : null;
        diff.setText(day == null ? where : getString(R.string.zone_detail, where, day));
        double[] at = Places.of(this, id);
        if (at == null) {
            sun.setVisibility(View.GONE);
        } else {
            SunTimes s = SunTimes.on(LocalDate.now(zone), at[0], at[1], zone);
            DateTimeFormatter clock = DateTimeFormatter.ofPattern(twentyFour ? "HH:mm" : "h:mm a", locale);
            sun.setText(s.kind == SunTimes.POLAR_DAY ? getString(R.string.city_polar_day)
                    : s.kind == SunTimes.POLAR_NIGHT ? getString(R.string.city_polar_night)
                    : getString(R.string.city_sun, s.rise.format(clock), s.set.format(clock)));
            sun.setVisibility(View.VISIBLE);
        }
        // The sky stands in for the photo until it is in, and when there is none
        int[] sky = DayLight.colours(r.hour);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, sky));
    }
}
