package io.github.sumyabazar1.weather;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Open-Meteo, which needs no key: the place search, the weather now and for the next days, and the US air
 * quality index. The only thing sent is the place name typed in the search, then that place's latitude and
 * longitude. Every call blocks, so WeatherActivity makes them off the UI thread.
 */
final class Forecast {
    static final long REFRESH_MS = 30 * 60_000L;
    static final long RETRY_MS = 5 * 60_000L;
    private static final int TIMEOUT_MS = 8_000;
    /** Days after today the forecast row can show (options.xml, forecast_days). */
    static final int MIN_DAYS = 3, MAX_DAYS = 7;

    private Forecast() { }

    /** A place from the search: its name, region and country for the list, and where it is. */
    static final class Place {
        final String name;
        final String detail;
        final double latitude;
        final double longitude;

        Place(String name, String detail, double latitude, double longitude) {
            this.name = name;
            this.detail = detail;
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }

    /**
     * The weather now: degrees Celsius as read (air and feels-like; {@link #degrees} rounds them for the page), the
     * WMO weather code, day or night, the relative humidity in % and the wind speed in km/h.
     */
    static final class Report {
        final double tempC;
        final double feelsC;
        /** The WMO code, rain turned into snow where the air is cold enough for it (see {@link #snowIfCold}). */
        final int code;
        final boolean day;
        /** The next days, tomorrow first. */
        final List<Day> days;
        /** US AQI, or -1 when the air quality read failed. */
        final int aqi;
        final int humidity;
        final int windKmh;
        /** Hour by hour, local time, as far as the source goes (Open-Meteo: every hour; MET Norway: hourly, then 6-hourly). */
        List<Hour> hours = new ArrayList<>();

        Report(double tempC, double feelsC, int code, boolean day, List<Day> days, int aqi, int humidity, int windKmh) {
            this.tempC = tempC;
            this.feelsC = feelsC;
            this.code = code;
            this.day = day;
            this.days = days;
            this.aqi = aqi;
            this.humidity = humidity;
            this.windKmh = windKmh;
        }
    }

    /** One forecast day: its date, the WMO code and the high and low in degrees Celsius as read. */
    static final class Day {
        final LocalDate date;
        final int code;
        final double maxC;
        final double minC;

        Day(LocalDate date, int code, double maxC, double minC) {
            this.date = date;
            this.code = code;
            this.maxC = maxC;
            this.minC = minC;
        }
    }

    /** One hour of the forecast: local time, degrees Celsius, WMO code, day or night, chance of precipitation (or -1). */
    static final class Hour {
        final java.time.LocalDateTime time;
        final double tempC;
        final int code;
        final boolean day;
        final int precip;

        Hour(java.time.LocalDateTime time, double tempC, int code, boolean day, int precip) {
            this.time = time;
            this.tempC = tempC;
            this.code = code;
            this.day = day;
            this.precip = precip;
        }
    }

    /**
     * The hours for the strip: {@code count} steps of {@code step} hours from {@code from}, each the reading at that
     * hour or the nearest one within half a step (MET Norway goes 6-hourly after two and a half days). A step with
     * nothing near is left out.
     */
    static List<Hour> strip(List<Hour> all, java.time.LocalDateTime from, int count, int step) {
        List<Hour> out = new ArrayList<>();
        for (int k = 0; k < count; k++) {
            java.time.LocalDateTime at = from.plusHours((long) k * step);
            Hour best = null;
            long bestGap = Long.MAX_VALUE;
            for (Hour h : all) {
                long gap = Math.abs(java.time.Duration.between(at, h.time).toMinutes());
                if (gap < bestGap) {
                    bestGap = gap;
                    best = h;
                }
            }
            if (best != null && bestGap <= step * 30L && (out.isEmpty() || out.get(out.size() - 1) != best)) out.add(best);
        }
        return out;
    }

    /** Up to five places matching what was typed, best match first. Empty when nothing matches. */
    static List<Place> search(String name) throws IOException, JSONException {
        JSONObject reply = get("https://geocoding-api.open-meteo.com/v1/search?count=5&format=json&name="
                + URLEncoder.encode(name.trim(), "UTF-8") + "&language=" + Locale.getDefault().getLanguage());
        List<Place> out = new ArrayList<>();
        JSONArray results = reply.optJSONArray("results");
        if (results == null) return out;
        for (int i = 0; i < results.length(); i++) {
            JSONObject r = results.getJSONObject(i);
            StringBuilder detail = new StringBuilder(r.optString("admin1"));
            String country = r.optString("country");
            if (!country.isEmpty()) detail.append(detail.length() > 0 ? ", " : "").append(country);
            out.add(new Place(r.getString("name"), detail.toString(), r.getDouble("latitude"), r.getDouble("longitude")));
        }
        return out;
    }

    /** Days after today to show: the option, kept between {@link #MIN_DAYS} and {@link #MAX_DAYS}. */
    static int forecastDays(int wanted) {
        return Math.max(MIN_DAYS, Math.min(MAX_DAYS, wanted));
    }

    /**
     * The weather and air quality at a place, with this many days after today. The air quality is a separate
     * service: if it fails, aqi is -1.
     */
    static Report fetch(double latitude, double longitude, int days) throws IOException, JSONException {
        try {
            return fetchOpenMeteo(latitude, longitude, days);
        } catch (IOException | JSONException e) {
            // Fallback: Open-Meteo is sometimes overloaded (HTTP 503).
            return fetchMetNo(latitude, longitude, days);
        }
    }

    private static Report fetchOpenMeteo(double latitude, double longitude, int days) throws IOException, JSONException {
        String where = String.format(Locale.ROOT, "latitude=%.4f&longitude=%.4f", latitude, longitude);
        // DWD's ICON model: Open-Meteo's default blend called light snow over Ulaanbaatar "overcast" while ICON,
        // and Google, had snow falling.
        JSONObject reply = get("https://api.open-meteo.com/v1/forecast?" + where + "&models=icon_seamless"
                + "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,is_day,wind_speed_10m"
                + "&daily=weather_code,temperature_2m_max,temperature_2m_min"
                + "&hourly=temperature_2m,relative_humidity_2m,weather_code,precipitation_probability,is_day"
                + "&timezone=auto&forecast_days=" + (forecastDays(days) + 1));
        JSONObject current = reply.getJSONObject("current");
        double temp = current.getDouble("temperature_2m");
        double humidity = current.getDouble("relative_humidity_2m");
        int code = snowIfCold(current.getInt("weather_code"), temp, humidity);
        Report r = new Report(temp, current.getDouble("apparent_temperature"),
                code, current.getInt("is_day") == 1,
                days(reply.getJSONObject("daily")), aqiOrUnknown(where),
                (int) Math.round(humidity), (int) Math.round(current.optDouble("wind_speed_10m", 0)));
        JSONObject hourly = reply.optJSONObject("hourly");
        if (hourly != null) r.hours = hours(hourly);
        return r;
    }

    /** Open-Meteo's hourly block, local time (timezone=auto). */
    static List<Hour> hours(JSONObject hourly) throws JSONException {
        JSONArray time = hourly.getJSONArray("time");
        JSONArray temp = hourly.getJSONArray("temperature_2m");
        JSONArray code = hourly.getJSONArray("weather_code");
        JSONArray rh = hourly.optJSONArray("relative_humidity_2m");
        JSONArray pop = hourly.optJSONArray("precipitation_probability");
        JSONArray isDay = hourly.optJSONArray("is_day");
        List<Hour> out = new ArrayList<>();
        for (int i = 0; i < time.length(); i++) {
            if (temp.isNull(i) || code.isNull(i)) continue;
            double t = temp.getDouble(i);
            double h = rh == null || rh.isNull(i) ? 100 : rh.getDouble(i);
            out.add(new Hour(java.time.LocalDateTime.parse(time.getString(i)), t, snowIfCold(code.getInt(i), t, h),
                    isDay == null || isDay.optInt(i, 1) == 1, pop == null || pop.isNull(i) ? -1 : pop.getInt(i)));
        }
        return out;
    }

    /**
     * The models type precipitation by the air temperature, and around freezing they call rain what reaches the
     * ground as snow (ICON and GFS: "light rain" at 1 °C in a Ulaanbaatar blizzard). The wet-bulb temperature,
     * which counts how dry the air is, decides better: at 1 °C or below, rain and drizzle become snow of the same
     * strength. Freezing rain and drizzle stay, as they are what falls.
     */
    static int snowIfCold(int code, double tempC, double humidity) {
        if (wetBulb(tempC, humidity) > 1.0) return code;
        switch (code) {
            case 51: case 53: case 61: case 80: return 71;
            case 55: case 63: case 81: return 73;
            case 65: case 82: return 75;
            default: return code;
        }
    }

    /** Stull (2011), from the air temperature in °C and the relative humidity in %; within 1 °C at sea level. */
    static double wetBulb(double t, double rh) {
        return t * Math.atan(0.151977 * Math.sqrt(rh + 8.313659)) + Math.atan(t + rh) - Math.atan(rh - 1.676331)
                + 0.00391838 * Math.pow(rh, 1.5) * Math.atan(0.023101 * rh) - 4.686035;
    }

    /** Tomorrow and the days after it; today (index 0) is the current reading. */
    private static List<Day> days(JSONObject daily) throws JSONException {
        JSONArray dates = daily.getJSONArray("time");
        JSONArray codes = daily.getJSONArray("weather_code");
        JSONArray max = daily.getJSONArray("temperature_2m_max");
        JSONArray min = daily.getJSONArray("temperature_2m_min");
        List<Day> out = new ArrayList<>();
        for (int i = 1; i < dates.length(); i++) {
            // With forecast_days 7 the last day can come back without values: stop there instead of failing the page.
            if (codes.isNull(i) || max.isNull(i) || min.isNull(i)) break;
            // A day whose high stays near freezing gets the same rain-to-snow correction, with the high and
            // saturated air, which is the warm end of the day's precipitation.
            out.add(new Day(LocalDate.parse(dates.getString(i)), snowIfCold(codes.getInt(i), max.getDouble(i), 100),
                    max.getDouble(i), min.getDouble(i)));
        }
        return out;
    }

    private static int aqiOrUnknown(String where) {
        try {
            JSONObject current = get("https://air-quality-api.open-meteo.com/v1/air-quality?" + where
                    + "&current=us_aqi").getJSONObject("current");
            return current.isNull("us_aqi") ? -1 : current.getInt("us_aqi");
        } catch (IOException | JSONException e) {
            return -1;
        }
    }

    private static JSONObject get(String url) throws IOException, JSONException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        connection.setRequestProperty("User-Agent", "cleanyourtv-weather/1.0 github.com/Gathin23/cleanyourtv");
        try (InputStream in = connection.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            for (int n; (n = in.read(buffer)) != -1; ) out.write(buffer, 0, n);
            return new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
        } finally {
            connection.disconnect();
        }
    }

    /** Whole degrees for the page: Celsius as read, or Fahrenheit converted before rounding (options.xml temperature_unit). */
    static int degrees(double celsius, boolean fahrenheit) {
        return (int) Math.round(fahrenheit ? celsius * 9 / 5 + 32 : celsius);
    }

    /** Whole miles per hour from km/h. */
    static int mph(int kmh) {
        return (int) Math.round(kmh / 1.609344);
    }

    // ---- MET Norway (api.met.no), the fallback source -----------------------------------------------

    /** MET Norway's forecast as the same Report: WMO codes, km/h, days by the TV's time zone. */
    private static Report fetchMetNo(double latitude, double longitude, int days) throws IOException, JSONException {
        JSONArray series = get(String.format(Locale.ROOT,
                "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=%.4f&lon=%.4f", latitude, longitude))
                .getJSONObject("properties").getJSONArray("timeseries");
        java.time.ZoneId zone = java.time.ZoneId.systemDefault();
        JSONObject first = series.getJSONObject(0).getJSONObject("data");
        JSONObject now = first.getJSONObject("instant").getJSONObject("details");
        double temp = now.getDouble("air_temperature");
        double humidity = now.optDouble("relative_humidity", 70);
        double windMs = now.optDouble("wind_speed", 0);
        String symbol = symbolOf(first, "next_1_hours");
        if (symbol == null) symbol = symbolOf(first, "next_6_hours");
        java.time.ZonedDateTime at = java.time.Instant.parse(series.getJSONObject(0).getString("time")).atZone(zone);
        boolean day = symbol != null && (symbol.endsWith("_day") || symbol.endsWith("_polartwilight"))
                || symbol != null && !symbol.contains("_") && at.getHour() >= 7 && at.getHour() < 19;
        int code = snowIfCold(wmo(symbol), temp, humidity);

        LocalDate today = at.toLocalDate();
        int wanted = forecastDays(days);
        java.util.TreeMap<LocalDate, double[]> range = new java.util.TreeMap<>(); // min, max, number of readings
        java.util.Map<LocalDate, String> noon = new java.util.HashMap<>();
        java.util.Map<LocalDate, Integer> noonGap = new java.util.HashMap<>();
        List<Hour> hourList = new ArrayList<>();
        for (int i = 0; i < series.length(); i++) {
            JSONObject entry = series.getJSONObject(i);
            java.time.ZonedDateTime t = java.time.Instant.parse(entry.getString("time")).atZone(zone);
            LocalDate date = t.toLocalDate();
            JSONObject hd = entry.getJSONObject("data");
            String hs = symbolOf(hd, "next_1_hours");
            if (hs == null) hs = symbolOf(hd, "next_6_hours");
            if (hs != null) {
                JSONObject det = hd.getJSONObject("instant").getJSONObject("details");
                double ht = det.getDouble("air_temperature");
                hourList.add(new Hour(t.toLocalDateTime(), ht, snowIfCold(wmo(hs), ht, det.optDouble("relative_humidity", 100)),
                        !hs.endsWith("_night"), -1));
            }
            if (!date.isAfter(today) || date.isAfter(today.plusDays(wanted))) continue;
            JSONObject data = entry.getJSONObject("data");
            double v = data.getJSONObject("instant").getJSONObject("details").getDouble("air_temperature");
            double[] r = range.get(date);
            if (r == null) range.put(date, new double[]{v, v, 1});
            else { r[0] = Math.min(r[0], v); r[1] = Math.max(r[1], v); r[2]++; }
            String s6 = symbolOf(data, "next_6_hours");
            int gap = Math.abs(t.getHour() - 12);
            if (s6 != null && (!noonGap.containsKey(date) || gap < noonGap.get(date))) {
                noon.put(date, s6);
                noonGap.put(date, gap);
            }
        }
        List<Day> out = new ArrayList<>();
        for (java.util.Map.Entry<LocalDate, double[]> e : range.entrySet()) {
            double[] r = e.getValue();
            if (r[2] < 3) break; // the forecast's tail without a full day
            out.add(new Day(e.getKey(), snowIfCold(wmo(noon.get(e.getKey())), r[1], 100), r[1], r[0]));
        }
        String where = String.format(Locale.ROOT, "latitude=%.4f&longitude=%.4f", latitude, longitude);
        Report rep = new Report(temp, apparent(temp, humidity, windMs), code, day, out, aqiOrUnknown(where),
                (int) Math.round(humidity), (int) Math.round(windMs * 3.6));
        rep.hours = hourList;
        return rep;
    }

    private static String symbolOf(JSONObject data, String period) {
        JSONObject p = data.optJSONObject(period);
        JSONObject summary = p == null ? null : p.optJSONObject("summary");
        return summary == null ? null : summary.optString("symbol_code", null);
    }

    /** Feels-like temperature by the Australian BoM formula (as Open-Meteo works it out). */
    static double apparent(double t, double rh, double windMs) {
        double e = rh / 100 * 6.105 * Math.exp(17.27 * t / (237.7 + t));
        return t + 0.33 * e - 0.70 * windMs - 4.00;
    }

    /** MET Norway's symbol_code as the WMO code the rest of the app understands. */
    static int wmo(String symbol) {
        if (symbol == null) return 3;
        String s = symbol.replaceAll("_(day|night|polartwilight)$", "");
        if (s.contains("thunder")) return 95;
        switch (s) {
            case "clearsky": return 0;
            case "fair": return 1;
            case "partlycloudy": return 2;
            case "cloudy": return 3;
            case "fog": return 45;
            case "lightrain": case "lightsleet": return 61;
            case "rain": case "sleet": return 63;
            case "heavyrain": case "heavysleet": return 65;
            case "lightrainshowers": case "lightsleetshowers": return 80;
            case "rainshowers": case "sleetshowers": return 81;
            case "heavyrainshowers": case "heavysleetshowers": return 82;
            case "lightsnow": return 71;
            case "snow": return 73;
            case "heavysnow": return 75;
            case "lightsnowshowers": return 85;
            case "snowshowers": case "heavysnowshowers": return 86;
            default: return 3;
        }
    }

    // ---- words and colours -------------------------------------------------------------------------

    /** The words for a WMO weather code, as fine as the code goes ("Light snow", not "Snow"). */
    static int sky(int code, boolean day) {
        switch (code) {
            case 0: return day ? R.string.sky_sunny : R.string.sky_clear;
            case 1: return day ? R.string.sky_mostly_sunny : R.string.sky_mostly_clear;
            case 2: return R.string.sky_partly_cloudy;
            case 3: return R.string.sky_cloudy;
            case 45: case 48: return R.string.sky_fog;
            case 51: case 53: case 55: return R.string.sky_drizzle;
            case 56: case 57: return R.string.sky_freezing_drizzle;
            case 61: return R.string.sky_light_rain;
            case 63: return R.string.sky_rain;
            case 65: return R.string.sky_heavy_rain;
            case 66: case 67: return R.string.sky_freezing_rain;
            case 71: return R.string.sky_light_snow;
            case 73: return R.string.sky_snow;
            case 75: return R.string.sky_heavy_snow;
            case 77: return R.string.sky_snow_grains;
            case 80: case 81: case 82: return R.string.sky_showers;
            case 85: case 86: return R.string.sky_snow_showers;
            default: return code >= 95 ? R.string.sky_thunderstorm : R.string.sky_cloudy;
        }
    }

    /** The animated icon for a WMO weather code (a {@link WeatherIcon} kind), matching {@link #sky}. */
    static int icon(int code, boolean day) {
        switch (code) {
            case 0: case 1: return day ? WeatherIcon.SUN : WeatherIcon.MOON;
            case 2: return day ? WeatherIcon.PARTLY_DAY : WeatherIcon.PARTLY_NIGHT;
            case 3: return WeatherIcon.CLOUD;
            case 45: case 48: return WeatherIcon.FOG;
            case 51: case 53: case 55: return WeatherIcon.DRIZZLE;
            case 56: case 57: case 66: case 67: return WeatherIcon.SLEET;
            case 61: case 80: return WeatherIcon.LIGHT_RAIN;
            case 63: case 81: return WeatherIcon.RAIN;
            case 65: case 82: return WeatherIcon.HEAVY_RAIN;
            case 71: case 85: return WeatherIcon.LIGHT_SNOW;
            case 73: case 75: case 77: case 86: return WeatherIcon.SNOW;
            default: return code >= 95 ? WeatherIcon.THUNDER : WeatherIcon.CLOUD;
        }
    }

    /** US AQI bands: upper bound, words, pill colour. */
    private static final int[] AQI_MAX = {50, 100, 150, 200, 300, Integer.MAX_VALUE};
    private static final int[] AQI_LABEL = {R.string.aqi_good, R.string.aqi_moderate, R.string.aqi_sensitive,
            R.string.aqi_unhealthy, R.string.aqi_very_unhealthy, R.string.aqi_hazardous};
    private static final int[] AQI_COLOUR = {0xE643A047, 0xE6FBC02D, 0xE6FB8C00, 0xE6E53935, 0xE68E24AA, 0xE67E0023};

    static int aqiBand(int aqi) {
        int band = 0;
        while (aqi > AQI_MAX[band]) band++;
        return band;
    }

    static int aqiLabel(int aqi) {
        return AQI_LABEL[aqiBand(aqi)];
    }

    static int aqiColour(int aqi) {
        return AQI_COLOUR[aqiBand(aqi)];
    }

    /** Dark text on the yellow band, white on the others. */
    static int aqiTextColour(int aqi) {
        return aqiBand(aqi) == 1 ? 0xFF1A1A1A : 0xFFFFFFFF;
    }

    /** A full-screen gradient in the weather's colours, dark at the bottom. */
    static Drawable backdrop(int code, boolean day) {
        int[] colours;
        if (code <= 1) {
            colours = day ? new int[]{0xFF3A86CC, 0xFF0F2540} : new int[]{0xFF0D1B3E, 0xFF020308};
        } else if (code == 45 || code == 48) {
            colours = day ? new int[]{0xFF5A6068, 0xFF101113} : new int[]{0xFF33373C, 0xFF0A0A0B};
        } else if ((code >= 71 && code <= 77) || code == 85 || code == 86) {
            colours = day ? new int[]{0xFF6F8497, 0xFF101820} : new int[]{0xFF3E4C59, 0xFF0A0F14};
        } else if (code >= 95) {
            colours = new int[]{0xFF2D2140, 0xFF07050C};
        } else if (code >= 51) {
            colours = day ? new int[]{0xFF1E2C3D, 0xFF06090E} : new int[]{0xFF121B26, 0xFF030507};
        } else {
            colours = day ? new int[]{0xFF3B4654, 0xFF0C0F14} : new int[]{0xFF242B33, 0xFF07090B};
        }
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colours);
    }
}
