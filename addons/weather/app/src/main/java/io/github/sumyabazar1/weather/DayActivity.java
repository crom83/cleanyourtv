package io.github.sumyabazar1.weather;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * One forecast day on its own screen, opened with OK on its card: the day's icon, high and low and words over its
 * animated background, then every hour of it in two rows (00:00 to 11:00, 12:00 to 23:00). Back returns. It reads
 * the forecast the main screen already has ({@link WeatherActivity#current}), so it makes no network request.
 */
public final class DayActivity extends Activity {
    static final String EXTRA_DATE = "date";
    private static final int PER_ROW = 12;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Forecast.Report r = WeatherActivity.current;
        String extra = getIntent().getStringExtra(EXTRA_DATE);
        if (r == null || extra == null) {
            finish();
            return;
        }
        LocalDate date = LocalDate.parse(extra);
        Forecast.Day day = null;
        for (Forecast.Day d : r.days) if (d.date.equals(date)) day = d;
        if (day == null) {
            finish();
            return;
        }
        setContentView(R.layout.activity_day);
        SharedPreferences options = getSharedPreferences("options", MODE_PRIVATE);
        boolean fahrenheit = options.getBoolean("fahrenheit",
                "f".equalsIgnoreCase(getString(R.string.temperature_unit).trim()));
        String city = getSharedPreferences(WeatherActivity.PREFS, MODE_PRIVATE).getString("name", "");

        findViewById(R.id.dayRoot).setBackground(Forecast.backdrop(day.code, true));
        ((WeatherBackdrop) findViewById(R.id.dayBackdrop)).setKind(Forecast.icon(day.code, true));
        ((TextView) findViewById(R.id.dayPlace)).setText(getString(R.string.place_date, city,
                date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()))));
        ((WeatherIcon) findViewById(R.id.dayBigIcon)).setKind(Forecast.icon(day.code, true));
        ((TextView) findViewById(R.id.dayHighLow)).setText(getString(R.string.high_low,
                Forecast.degrees(day.maxC, fahrenheit), Forecast.degrees(day.minC, fahrenheit)));
        ((TextView) findViewById(R.id.dayWords)).setText(Forecast.sky(day.code, true));

        List<Forecast.Hour> hours = Forecast.strip(r.hours, date.atStartOfDay(), 24, 1);
        boolean anyRain = false;
        for (Forecast.Hour h : hours) anyRain |= h.precip >= 20;
        DateTimeFormatter clock = DateTimeFormatter.ofPattern(DateFormat.is24HourFormat(this) ? "HH:mm" : "h a",
                Locale.getDefault());
        LinearLayout[] rows = {findViewById(R.id.dayHours1), findViewById(R.id.dayHours2)};
        LayoutInflater inflater = getLayoutInflater();
        for (Forecast.Hour h : hours) {
            // Morning row and afternoon row by the hour itself, so a 6-hourly tail (MET Norway) still lands right
            LinearLayout row = rows[h.time.getHour() < PER_ROW ? 0 : 1];
            View cell = inflater.inflate(R.layout.item_hour, row, false);
            ((TextView) cell.findViewById(R.id.hourTime)).setText(h.time.format(clock));
            ((WeatherIcon) cell.findViewById(R.id.hourIcon)).setKind(Forecast.icon(h.code, h.day));
            ((TextView) cell.findViewById(R.id.hourTemp)).setText(Forecast.degrees(h.tempC, fahrenheit) + "°");
            TextView rain = cell.findViewById(R.id.hourRain);
            rain.setText(h.precip >= 20 ? getString(R.string.hour_rain, h.precip) : " ");
            rain.setVisibility(anyRain ? View.VISIBLE : View.GONE);
            row.addView(cell, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }
        // A row with fewer than 12 readings keeps its cells at the same width as a full one
        for (LinearLayout row : rows) row.setWeightSum(PER_ROW);
    }
}
