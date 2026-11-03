# Weather

The weather for one city, full screen on the TV: now (temperature, feels-like, conditions, the US air quality
index as a coloured pill), the next 24 hours every 3 hours, and the next days; OK on a day opens its hours. Each
condition has an animated icon and an animated background (rain streaks, snow, drifting clouds, stars at night). For the [Clean Your TV](../../README.md) launcher it declares a
[module card](../../docs/modules.md), so once you answer **Show card** its tile reads "Weather" with a
sky-blue glow; on any other Android TV home it is an ordinary app.

![The weather page](https://raw.githubusercontent.com/Sumyabazar1/cleanyourtv/pr-assets/addon-weather/addon-3-weather.png)

- **The city** is chosen once: type its name with the remote's keyboard (or voice), pick it from the matches,
  and it is kept. **City**, the first row of the settings (the gear, top right), chooses another.
- **Data** comes from [Open-Meteo](https://open-meteo.com/), which needs no account or key: the weather from
  DWD's ICON model (Open-Meteo's default blend called light snow over Ulaanbaatar "overcast"; ICON matched
  what was falling), and the air quality from the Copernicus (CAMS) model, shown on the US AQI scale.
- **Snow, not rain, near freezing.** The models type precipitation by the air temperature and call snow
  "light rain" at 1 °C (ICON and GFS did, in a Ulaanbaatar blizzard). The app decides by the wet-bulb
  temperature instead, which counts how dry the air is: at 1 °C or below, rain and drizzle are shown as snow.
- **When Open-Meteo fails** (it answers HTTP 503 when overloaded), the forecast comes from
  [MET Norway](https://api.met.no/) instead, turned into the same codes and days; then there is no chance of
  rain in the hours, and past two and a half days MET has a reading every 6 hours.
- It reads Open-Meteo only while the page is on screen, every 30 minutes, or 5 minutes after a failure.
- **Animated, light on the TV.** The icons and the background are drawn on a Canvas from the weather code, with
  no pictures: about 30 frames a second for the icons and 20 for the background, only while on screen, and
  nothing allocated per frame.
- Degrees Celsius by default (Fahrenheit is an option below); English, French, Spanish, Italian, Korean, Turkish or Russian words, with the TV's language; day names follow the TV's language.

## Options

Open **Weather settings** with the gear in the top right corner; its first row, **City**, changes the city. Use Up/Down and OK to change
units, forecast length, both units, humidity and wind. Back cancels a unit/day picker; **Done** or Back
closes settings. Choices are saved on this TV and survive closing the app and restarting the TV.

Display changes use the current reading, without another network request or changing its update time.
Changing the forecast length fetches once when settings closes. **Reset weather settings** restores the
build defaults without changing the city.

`app/src/main/res/values/options.xml` still sets the defaults for a fresh installation and for Reset.
Saved choices override these defaults. With no saved choices the page is as above.

| Option | Default | What it does |
|---|---|---|
| `forecast_days` | `3` | Days in the forecast row after today, 3 to 7. Above 3 the day cards share the row, with short day names, so a week fits on screen. |
| `show_humidity` | `false` | The relative humidity now, after "Feels like". |
| `show_wind` | `false` | The wind speed now in km/h, after "Feels like" (mph with `temperature_unit` `f`). |
| `temperature_unit` | `c` | `f` shows degrees Fahrenheit for the temperature now, feels-like and the days' highs and lows, converted from the reading before rounding. |
| `show_both_units` | `false` | The temperature now in the other unit too, at the start of the "Feels like" line ("22°C · Feels like 70°"). |

![A week, with humidity and wind](https://raw.githubusercontent.com/majnioui/cleanyourtv-contrib/pr-assets/addon-weather-week/weather-options.png)

## Hours

Under the current weather, a strip shows the next 24 hours every 3 hours: time, icon, temperature and, when rain
is likely (20 % or more), its chance. OK on a forecast day opens that day on its own screen: its icon, high and
low and words over its animated background, then every hour of it in two rows (00:00 to 11:00, 12:00 to 23:00).
Back returns. MET Norway, the fallback source, gives no chance of rain for most of the world, so then there are no
percentages, and past about two and a half days it has a reading every 6 hours only.

## Preview every look

With the weather on screen, **CH+ / CH-** on the remote step through all 14 looks (sunny, clear night, partly
cloudy by day and night, cloudy, fog, drizzle, light rain, rain, heavy rain, freezing rain, light snow, snow,
thunderstorm): the main icon and the animated background change, and the words read "Heavy rain · 10/14". After
the last one it is back to the real weather. Handy for checking how a look reads on your TV.

## Permissions

| Permission | Why |
|---|---|
| `INTERNET` | Open-Meteo: the place search, the weather and the air quality. |

That is all. What is sent: the city name you type in the search, then that place's latitude and longitude. No
account, no telemetry, no ads, no background service, no location permission. The chosen city stays in the
app's own storage (backups are off).

## Build and install

JDK 17 and Android SDK platform 34 with build-tools 34.0.0, as for the launcher:

```bash
cd addons/weather && ./gradlew assembleRelease
TV_HOST=<tv ip> ../../tools/venv/bin/python -I ../../tools/tv.py --push app/build/outputs/apk/release/app-release.apk /data/local/tmp/addon.apk
TV_HOST=<tv ip> ../../tools/venv/bin/python -I ../../tools/tv.py 'pm install -r /data/local/tmp/addon.apk; rm -f /data/local/tmp/addon.apk'
```

Press Home: the launcher asks once whether to show the card. Change the answer later by holding OK on
Settings, then Module cards.

The release build is signed with the debug key so it installs over ADB like the launcher. Sign your own builds
with your own key if you hand them out; the launcher pins its Show answer to the signing key.

## Tested on

Hisense SmartTV 4K FFM (lushan, MediaTek MT9676), Google TV 12 / Android 12: city search with the TV keyboard,
the weather page, the card prompt, and the card shown in the launcher's dock.

The options: Xiaomi TV (MiTV-MOOR2), Google TV 11, with `forecast_days` 7, `show_humidity` and `show_wind` on.

Animated icons and background (all 14 looks through CH+ / CH-), the hours strip, the day screen, the gear and the
MET Norway fallback, in Russian: TCL G13 (Google TV 14, MediaTek MT5867, 1 GB) and TCL BeyondTV (Android TV 11,
Realtek). The JVM tests cover the icon for each code and picking the hours.

## Maintainer

[@Sumyabazar1](https://github.com/Sumyabazar1)
