package com.gathin.tvhome;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Weather page in the iOS glass style: place, big temperature and the day's range on top, then glass tiles
 * (hourly, 7-day forecast, feels like, UV, wind, sunset, air quality, rain, humidity, pressure, moon, radar).
 * The animated sky (rain, lightning, stars) fills the whole screen behind the tiles.
 */
public class WeatherActivity extends Activity {

    private WeatherBackgroundView front;
    private FrameLayout bgHolder;
    private FrameLayout root;
    private LinearLayout page;
    private float d;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        d = getResources().getDisplayMetrics().density;
        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0B1220);
        bgHolder = new FrameLayout(this);
        root.addView(bgHolder, new FrameLayout.LayoutParams(-1, -1));
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setClipChildren(false);
        page.setPadding((int) (40 * d), (int) (20 * d), (int) (40 * d), (int) (18 * d));
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));
        // The rain, stars and sun are drawn again in front of the tiles.
        front = new WeatherBackgroundView(this);
        front.setOverlayMode(true);
        front.setFocusable(false);
        front.setClickable(false);
        root.addView(front, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        populateUi();
        WeatherAirQuality.syncIfDue(this, this::populateUi);
    }

    @Override
    protected void onResume() {
        super.onResume();
        front.startAnimation();
    }

    @Override
    protected void onPause() {
        super.onPause();
        front.stopAnimation();
    }

    // ---------------------------------------------------------------- building blocks

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        t.setIncludeFontPadding(false);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        return t;
    }

    /** A focusable glass tile; pressing OK opens its details. */
    private LinearLayout card(String title, final String detailTitle, final Runnable details) {
        final LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setClipChildren(false);
        c.setPadding((int) (12 * d), (int) (7 * d), (int) (12 * d), (int) (7 * d));
        c.setFocusable(true);
        c.setClickable(true);
        setCardLook(c, false);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.setOnFocusChangeListener((v, has) -> {
            setCardLook(c, has);
            GlassPage.lift(c, d, has, 1.03f);
        });
        if (details != null) c.setOnClickListener(v -> details.run());
        if (title != null) {
            TextView t = text(title.toUpperCase(Locale.ROOT), 10.5f, 0xB3FFFFFF, true);
            t.setLetterSpacing(0.06f);
            t.setPadding(0, 0, 0, (int) (4 * d));
            c.addView(t);
        }
        return c;
    }

    private void setCardLook(View v, boolean focused) {
        GlassPage.glass(v, d, 22, focused);
    }

    private LinearLayout column(LinearLayout parent, float weight, int leftMargin) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setClipChildren(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, weight);
        lp.leftMargin = leftMargin;
        parent.addView(c, lp);
        return c;
    }

    /** A row of tiles filling a share of the column's height. */
    private LinearLayout row(LinearLayout col, float weight, boolean first) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setClipChildren(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 0, weight);
        if (!first) lp.topMargin = (int) (10 * d);
        col.addView(r, lp);
        return r;
    }

    private void put(LinearLayout row, View v, float weight, boolean first) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, weight);
        if (!first) lp.leftMargin = (int) (10 * d);
        row.addView(v, lp);
    }

    private void sideAdd(LinearLayout side, View v, boolean first) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 0, 1f);
        if (!first) lp.topMargin = (int) (8 * d);
        side.addView(v, lp);
    }

    private static String word(String s) {
        return s == null || s.isEmpty() ? "" : s;
    }

    // ---------------------------------------------------------------- details

    private void showDetail(String title, View content) {
        final android.app.Dialog dlg = new android.app.Dialog(this, android.R.style.Theme_Translucent_NoTitleBar);
        FrameLayout shell = new FrameLayout(this);
        shell.setBackgroundColor(0x99000000);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding((int) (26 * d), (int) (20 * d), (int) (26 * d), (int) (20 * d));
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xE62A3550, 0xE6141B2D});
        g.setCornerRadius(28 * d);
        g.setStroke((int) (1.2f * d), 0x80FFFFFF);
        panel.setBackground(g);
        TextView t = text(title.toUpperCase(Locale.ROOT), 14f, 0xB3FFFFFF, true);
        t.setLetterSpacing(0.08f);
        t.setPadding(0, 0, 0, (int) (12 * d));
        panel.addView(t);
        panel.addView(content, new LinearLayout.LayoutParams(-1, -2));
        TextView close = text("Back", 15f, 0xFF000000, true);
        close.setGravity(Gravity.CENTER);
        close.setPadding((int) (28 * d), (int) (9 * d), (int) (28 * d), (int) (9 * d));
        GradientDrawable cg = new GradientDrawable();
        cg.setCornerRadius(20 * d);
        cg.setColor(0xFFFFFFFF);
        close.setBackground(cg);
        close.setFocusable(true);
        close.setClickable(true);
        close.setOnClickListener(v -> dlg.dismiss());
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-2, -2);
        cl.topMargin = (int) (14 * d);
        cl.gravity = Gravity.CENTER_HORIZONTAL;
        panel.addView(close, cl);
        FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams((int) (680 * d), -2);
        pl.gravity = Gravity.CENTER;
        shell.addView(panel, pl);
        dlg.setContentView(shell);
        dlg.show(); GlassPage.glassDialog(dlg);
        if (dlg.getWindow() != null) {
            dlg.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
        close.requestFocus();
    }

    private View body(String... lines) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < lines.length; i++) {
            TextView t = text(lines[i], i == 0 ? 34f : 15f, 0xFFFFFFFF, i == 0);
            t.setSingleLine(false);
            t.setPadding(0, i == 0 ? 0 : (int) (8 * d), 0, 0);
            box.addView(t);
        }
        return box;
    }

    private View bodyWith(View big, String... lines) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(big, new LinearLayout.LayoutParams(-1, (int) (170 * d)));
        for (String l : lines) {
            TextView t = text(l, 15f, 0xFFFFFFFF, false);
            t.setSingleLine(false);
            t.setPadding(0, (int) (8 * d), 0, 0);
            box.addView(t);
        }
        return box;
    }

    private static String wmo(int code) {
        if (code == 0) return "Clear";
        if (code <= 2) return "Partly cloudy";
        if (code == 3) return "Overcast";
        if (code == 45 || code == 48) return "Fog";
        if (code >= 51 && code <= 57) return "Drizzle";
        if (code >= 61 && code <= 67) return "Rain";
        if (code >= 80 && code <= 82) return "Showers";
        if (code >= 95) return "Thunderstorm";
        return "Cloudy";
    }

    private void hourlyDetail(WeatherAirQuality.FullWeather w) {
        List<WeatherAirQuality.HourlyItem> hl = hoursOf(w);
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        int per = 6;
        for (int i = 0; i < hl.size(); i += per) {
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setPadding(0, (int) (5 * d), 0, (int) (5 * d));
            for (int k = i; k < Math.min(hl.size(), i + per); k++) {
                WeatherAirQuality.HourlyItem h = hl.get(k);
                LinearLayout col = new LinearLayout(this);
                col.setOrientation(LinearLayout.VERTICAL);
                col.setGravity(Gravity.CENTER_HORIZONTAL);
                col.addView(text(h.time, 12.5f, 0xB3FFFFFF, true));
                col.addView(text(h.temp + "\u00B0", 19f, 0xFFFFFFFF, true));
                col.addView(text(h.rainChance > 0 ? "Rain " + h.rainChance + "%" : "Dry", 12f, h.rainChance > 0 ? 0xFF7DD3FC : 0x99FFFFFF, false));
                line.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
            }
            grid.addView(line);
        }
        showDetail("Conditions hour by hour", grid);
    }

    private List<WeatherAirQuality.HourlyItem> hoursOf(WeatherAirQuality.FullWeather w) {
        // Hour by hour from now; the day's 3-hour curve only when that is all there is.
        if (w.hourly != null && w.hourly.size() >= 6) return w.hourly;
        return (!w.daily.isEmpty() && w.daily.get(0).hourly != null && !w.daily.get(0).hourly.isEmpty())
                ? w.daily.get(0).hourly : w.hourly;
    }

    private void daysDetail(WeatherAirQuality.FullWeather w) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < Math.min(7, w.daily.size()); i++) {
            WeatherAirQuality.DailyItem it = w.daily.get(i);
            LinearLayout line = new LinearLayout(this);
            line.setGravity(Gravity.CENTER_VERTICAL);
            line.setPadding(0, (int) (6 * d), 0, (int) (6 * d));
            line.addView(text(i == 0 ? "Today" : it.day, 16f, 0xFFFFFFFF, true), new LinearLayout.LayoutParams((int) (100 * d), -2));
            line.addView(text(wmo(it.code), 15f, 0xCCFFFFFF, false), new LinearLayout.LayoutParams(0, -2, 1f));
            line.addView(text("Rain " + it.rainChance + "%", 14f, 0xFF7DD3FC, true), new LinearLayout.LayoutParams((int) (100 * d), -2));
            line.addView(text(it.minTemp + "\u00B0 / " + it.maxTemp + "\u00B0", 16f, 0xFFFFFFFF, true), new LinearLayout.LayoutParams((int) (100 * d), -2));
            box.addView(line);
        }
        showDetail("7-day forecast", box);
    }

    // ---------------------------------------------------------------- content

    /** Only the colours of today's sky sit behind the glass: no photograph. */
    private void setSky(WeatherBackgroundView.ConditionType ct) {
        int top, bottom;
        switch (ct) {
            case RAIN: top = 0xFF263545; bottom = 0xFF4B5C6D; break;
            case DRIZZLE: top = 0xFF35475A; bottom = 0xFF6A7C8D; break;
            case THUNDERSTORM: top = 0xFF161A2B; bottom = 0xFF3A3354; break;
            case CLOUDY: top = 0xFF52667A; bottom = 0xFF8D9CAB; break;
            case FOG: top = 0xFF55657A; bottom = 0xFF98A6B3; break;
            case SNOW: top = 0xFF52678A; bottom = 0xFFB9C7DB; break;
            case NIGHT: top = 0xFF081028; bottom = 0xFF1D2C54; break;
            default: top = 0xFF2F7FD6; bottom = 0xFF8FCBF2; break;
        }
        bgHolder.removeAllViews();
        bgHolder.addView(GlassPage.sky(this, top, bottom), new FrameLayout.LayoutParams(-1, -1));
    }

    private LinearLayout.LayoutParams rowLp(float weight, boolean first) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 0, weight);
        if (!first) lp.topMargin = (int) (8 * d);
        return lp;
    }

    private void populateUi() {
        final WeatherAirQuality.FullWeather w = WeatherAirQuality.getFullWeather(this);
        WeatherBackgroundView.ConditionType ct = determineCondition(w);
        front.setCondition(ct);
        setSky(ct);

        page.removeAllViews();
        Location.Zone chosen = Location.chosen(this);
        String place0 = (chosen != null) ? chosen.town : w.location;
        if (place0 == null || place0.isEmpty()) place0 = "Putrajaya";
        final String place = place0.substring(0, 1).toUpperCase(Locale.ROOT) + place0.substring(1);

        // Top, in the middle: place, temperature, sky, range and how it feels
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setGravity(Gravity.CENTER_HORIZONTAL);
        head.addView(center(text(place, 22f, 0xFFFFFFFF, true)));
        TextView temp = text(w.temp + "°C", 52f, 0xFFFFFFFF, false);
        temp.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        head.addView(center(temp));
        head.addView(center(text(word(w.condition) + "   |   H " + w.highTemp + "°   L " + w.lowTemp + "°   |   Feels like " + w.apparentTemp + "°C", 14f, 0xFFFFFFFF, true)));
        // Refresh: fetches the weather again right now and redraws the sky.
        final TextView refresh = text("Refresh", 13f, 0xFFFFFFFF, true);
        refresh.setPadding((int) (22 * d), (int) (7 * d), (int) (22 * d), (int) (7 * d));
        refresh.setFocusable(true);
        refresh.setClickable(true);
        GlassPage.glass(refresh, d, 20, false);
        refresh.setOnFocusChangeListener((v, has) -> {
            GlassPage.glass(v, d, 20, has);
            GlassPage.lift(v, d, has, 1.06f);
        });
        refresh.setOnClickListener(v -> {
            refresh.setText("Refreshing...");
            // The home screen reads its own copy: clear it so Home fetches the sky again too.
            HomeWeather.forget(WeatherActivity.this);
            WeatherAirQuality.sync(WeatherActivity.this, () -> runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed()) populateUi();
            }));
        });
        LinearLayout.LayoutParams rfLp = new LinearLayout.LayoutParams(-2, -2);
        rfLp.topMargin = (int) (8 * d);
        rfLp.gravity = Gravity.CENTER_HORIZONTAL;
        head.addView(refresh, rfLp);
        page.addView(head, new LinearLayout.LayoutParams(-1, -2));

        // Every hour, close together
        LinearLayout hourly = card(null, "Conditions", () -> hourlyDetail(w));
        hourly.setPadding((int) (10 * d), (int) (6 * d), (int) (10 * d), (int) (6 * d));
        LinearLayout strip = new LinearLayout(this);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        List<WeatherAirQuality.HourlyItem> hl = hoursOf(w);
        int startAt = 0;
        int shown = 0;
        for (int i = startAt; hl != null && i < hl.size() && shown < 24; i++) {
            WeatherAirQuality.HourlyItem h = hl.get(i);
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            TextView tt = text(shown == 0 ? "Now" : h.time, 9.5f, 0xCCFFFFFF, true);
            tt.setSingleLine(true);
            col.addView(tt);
            TextView rain = text(h.rainChance > 0 ? h.rainChance + "%" : " ", 9f, 0xFF7DD3FC, true);
            rain.setPadding(0, (int) (2 * d), 0, (int) (2 * d));
            col.addView(rain);
            col.addView(text(h.temp + "°", 13f, 0xFFFFFFFF, true));
            strip.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
            shown++;
        }
        hourly.addView(strip);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-1, 0, 0.62f);
        hlp.topMargin = (int) (6 * d);
        page.addView(hourly, hlp);

        // Below: the 7 days on the left, the tiles on the right
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        body.setClipChildren(false);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, 0, 2.6f);
        blp.topMargin = (int) (8 * d);
        page.addView(body, blp);

        LinearLayout days = card("7-Day Forecast", "7-day forecast", () -> daysDetail(w));
        int lo = 99, hi = -99;
        for (WeatherAirQuality.DailyItem it : w.daily) { lo = Math.min(lo, it.minTemp); hi = Math.max(hi, it.maxTemp); }
        for (int i = 0; i < Math.min(7, w.daily.size()); i++) {
            WeatherAirQuality.DailyItem it = w.daily.get(i);
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setGravity(Gravity.CENTER_VERTICAL);
            Calendar dcal = Calendar.getInstance();
            dcal.add(Calendar.DAY_OF_MONTH, i);
            LinearLayout dayCol = new LinearLayout(this);
            dayCol.setOrientation(LinearLayout.VERTICAL);
            dayCol.addView(text(i == 0 ? "Today" : new java.text.SimpleDateFormat("EEE", Locale.ENGLISH).format(dcal.getTime()), 13f, 0xFFFFFFFF, true));
            dayCol.addView(text(new java.text.SimpleDateFormat("d MMM", Locale.ENGLISH).format(dcal.getTime()), 9.5f, 0x99FFFFFF, false));
            line.addView(dayCol, new LinearLayout.LayoutParams((int) (56 * d), -2));
            line.addView(text(it.rainChance >= 20 ? it.rainChance + "%" : "", 10.5f, 0xFF7DD3FC, true), new LinearLayout.LayoutParams((int) (32 * d), -2));
            line.addView(text(it.minTemp + "°", 13f, 0x99FFFFFF, false), new LinearLayout.LayoutParams((int) (30 * d), -2));
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(0, (int) (5 * d), 1f);
            bl.leftMargin = (int) (4 * d);
            bl.rightMargin = (int) (6 * d);
            line.addView(new RangeBar(lo, hi, it.minTemp, it.maxTemp, i == 0 ? w.temp : Integer.MIN_VALUE), bl);
            line.addView(text(it.maxTemp + "°", 13f, 0xFFFFFFFF, true), new LinearLayout.LayoutParams((int) (32 * d), -2));
            days.addView(line, new LinearLayout.LayoutParams(-1, 0, 1f));
        }
        body.addView(days, new LinearLayout.LayoutParams(0, -1, 1f));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setClipChildren(false);
        LinearLayout.LayoutParams rgt = new LinearLayout.LayoutParams(0, -1, 2.1f);
        rgt.leftMargin = (int) (10 * d);
        body.addView(right, rgt);

        LinearLayout r1 = new LinearLayout(this);
        r1.setOrientation(LinearLayout.HORIZONTAL);
        r1.setClipChildren(false);
        right.addView(r1, rowLp(1f, true));
        LinearLayout r2 = new LinearLayout(this);
        r2.setOrientation(LinearLayout.HORIZONTAL);
        r2.setClipChildren(false);
        right.addView(r2, rowLp(1f, false));

        // Row one: rain, feels like, UV, humidity
        LinearLayout precip = card("Precipitation", "Precipitation", () ->
                showDetail("Precipitation", body(w.rainChanceToday + "%", hourlySummary(w), peakRain(hl))));
        precip.addView(text(w.rainChanceToday + "%   " + (w.rainChanceToday >= 50 ? "likely today" : "chance today"), 15f, 0xFFFFFFFF, true));
        TextView pk = text(peakRain(hl), 10.5f, 0xCCFFFFFF, false);
        pk.setSingleLine(true);
        precip.addView(pk);
        precip.addView(new RainBars(hl), new LinearLayout.LayoutParams(-1, 0, 1f));
        put(r1, precip, 1.3f, true);

        String feelsText = w.apparentTemp > w.temp ? "Feels warmer than the air."
                : w.apparentTemp < w.temp ? "Feels cooler than the air." : "Feels like the air.";
        LinearLayout feels = card("Feels Like", "Feels Like", () ->
                showDetail("Feels like", body(w.apparentTemp + "°C", feelsText, "Actual temperature: " + w.temp + "°C. Humidity " + w.humidity + "% and wind " + w.windSpeed + " km/h change how warm it feels.")));
        feels.addView(text(w.apparentTemp + "°C", 22f, 0xFFFFFFFF, false));
        TextView fs = text(feelsText, 11.5f, 0xCCFFFFFF, false);
        fs.setSingleLine(false);
        feels.addView(fs);
        put(r1, feels, 1f, false);

        String uvWord = w.uvIndex <= 2 ? "Low" : w.uvIndex <= 5 ? "Moderate" : w.uvIndex <= 7 ? "High" : "Very high";
        LinearLayout uv = card("UV", "UV Index", () ->
                showDetail("UV index", body(w.uvIndex + "  " + uvWord, w.uvIndex <= 2 ? "No protection needed."
                        : w.uvIndex <= 5 ? "Wear sunscreen if you stay outside." : "Cover up, use sunscreen and avoid the midday sun.")));
        uv.addView(text(String.valueOf(w.uvIndex), 22f, 0xFFFFFFFF, false));
        TextView uw = text(uvWord, 12.5f, 0xFFFFFFFF, true);
        uw.setSingleLine(false);
        uv.addView(uw);
        put(r1, uv, 1f, false);

        String humWord = w.humidity >= 80 ? "Very humid." : w.humidity >= 60 ? "Humid." : "Comfortable.";
        LinearLayout hum = card("Humidity", "Humidity", () ->
                showDetail("Humidity", body(w.humidity + "%", humWord, "The air holds " + w.humidity + "% of the moisture it could at this temperature.")));
        hum.addView(text(w.humidity + "%", 22f, 0xFFFFFFFF, false));
        hum.addView(text(humWord, 11.5f, 0xCCFFFFFF, false));
        put(r1, hum, 1f, false);

        // Row two: wind, sunset, air quality, pressure, moon
        LinearLayout wind = card("Wind", "Wind", () -> {
            WindCompassView big = new WindCompassView(this);
            big.setWind(w.windSpeed, w.windSpeed + 10, 180f);
            showDetail("Wind", bodyWith(big, w.windSpeed + " km/h, gusts up to " + (w.windSpeed + 10) + " km/h."));
        });
        WindCompassView wc = new WindCompassView(this);
        wc.setWind(w.windSpeed, w.windSpeed + 10, 180f);
        wind.addView(wc, new LinearLayout.LayoutParams(-1, 0, 1f));
        wind.addView(text(w.windSpeed + " km/h", 12.5f, 0xFFFFFFFF, true));
        put(r2, wind, 1f, true);

        LinearLayout sun = card("Sunset", "Sunset", () -> {
            SunArcView big = new SunArcView(this);
            big.setTimes(w.sunrise, w.sunset);
            showDetail("Sunrise and sunset", bodyWith(big, "Sunrise: " + word(w.sunrise), "Sunset: " + word(w.sunset)));
        });
        sun.addView(text(word(w.sunset), 18f, 0xFFFFFFFF, false));
        SunArcView sa = new SunArcView(this);
        sa.setTimes(w.sunrise, w.sunset);
        sun.addView(sa, new LinearLayout.LayoutParams(-1, 0, 1f));
        put(r2, sun, 1f, false);

        LinearLayout air = card("Air Quality", "Air Quality", () -> {
            AqiSpectrumBarView big = new AqiSpectrumBarView(this);
            big.setAqi(w.aqi);
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.addView(text(w.aqi + "  " + word(w.aqiStatus), 30f, 0xFFFFFFFF, true));
            box.addView(big, new LinearLayout.LayoutParams(-1, (int) (18 * d)));
            TextView t = text("PM2.5: " + String.format(Locale.ROOT, "%.1f", w.pm25) + "   PM10: " + String.format(Locale.ROOT, "%.1f", w.pm10)
                    + (w.stationPlace != null && !w.stationPlace.isEmpty() ? "\nStation: " + w.stationPlace : ""), 15f, 0xFFFFFFFF, false);
            t.setSingleLine(false);
            t.setPadding(0, (int) (10 * d), 0, 0);
            box.addView(t);
            showDetail("Air quality", box);
        });
        air.addView(text(String.valueOf(w.aqi), 22f, 0xFFFFFFFF, false));
        air.addView(text(word(w.aqiStatus), 12.5f, w.aqiColor != 0 ? w.aqiColor : 0xFFFFFFFF, true));
        AqiSpectrumBarView ab = new AqiSpectrumBarView(this);
        ab.setAqi(w.aqi);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-1, (int) (10 * d));
        alp.topMargin = (int) (8 * d);
        air.addView(ab, alp);
        put(r2, air, 1f, false);

        final int hpa = w.pressure > 0 ? w.pressure : 1010;
        LinearLayout pres = card("Pressure", "Pressure", () -> {
            PressureGaugeView big = new PressureGaugeView(this);
            big.setPressure(hpa);
            showDetail("Pressure", bodyWith(big, hpa + " hPa", hpa < 1008 ? "Low pressure: unsettled weather is more likely." : hpa > 1018 ? "High pressure: settled weather." : "Normal pressure."));
        });
        PressureGaugeView pg = new PressureGaugeView(this);
        pg.setPressure(hpa);
        pres.addView(pg, new LinearLayout.LayoutParams(-1, 0, 1f));
        pres.addView(text(hpa + " hPa", 12.5f, 0xFFFFFFFF, true));
        put(r2, pres, 1f, false);

        LinearLayout moon = card("Moon", "Moon", () -> {
            MoonPhaseView big = new MoonPhaseView(this);
            showDetail("Moon", bodyWith(big, moonName()));
        });
        MoonPhaseView mv = new MoonPhaseView(this);
        moon.addView(mv, new LinearLayout.LayoutParams(-1, 0, 1f));
        moon.addView(text(moonName(), 12.5f, 0xFFFFFFFF, true));
        put(r2, moon, 1f, false);

        hourly.post(hourly::requestFocus);
    }

    private TextView center(TextView t) {
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setShadowLayer(6 * d, 0, 1 * d, 0x66000000);
        return t;
    }

    private String peakRain(List<WeatherAirQuality.HourlyItem> hl) {
        int best = 0;
        String at = "";
        for (int i = 0; hl != null && i < hl.size() && i < 24; i++) {
            if (hl.get(i).rainChance > best) { best = hl.get(i).rainChance; at = hl.get(i).time; }
        }
        return best < 10 ? "No rain expected in the next 24 hours." : "Most likely " + best + "% around " + at + ".";
    }

    /** The chance of rain hour by hour over the next day, as bars. */
    private final class RainBars extends View {
        private final List<WeatherAirQuality.HourlyItem> hl;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        RainBars(List<WeatherAirQuality.HourlyItem> hl) {
            super(WeatherActivity.this);
            this.hl = hl;
        }

        @Override
        protected void onDraw(Canvas c) {
            int n = Math.min(24, hl == null ? 0 : hl.size());
            if (n == 0) return;
            float w = getWidth(), h = getHeight();
            float slot = w / n;
            p.setColor(0x22FFFFFF);
            c.drawRoundRect(new RectF(0, h - 2 * d, w, h), d, d, p);
            for (int i = 0; i < n; i++) {
                float v = Math.max(0.04f, hl.get(i).rainChance / 100f);
                p.setColor(hl.get(i).rainChance >= 50 ? 0xFFFFB347 : 0xAA7DD3FC);
                float bh = (h - 4 * d) * v;
                c.drawRoundRect(new RectF(i * slot + slot * 0.18f, h - 3 * d - bh, (i + 1) * slot - slot * 0.18f, h - 3 * d), 2 * d, 2 * d, p);
            }
        }
    }

    private String hourlySummary(WeatherAirQuality.FullWeather w) {
        return w.rainChanceToday >= 50 ? "Rain is likely today, with a " + w.rainChanceToday + "% chance."
                : w.rainChanceToday >= 20 ? "A chance of showers today (" + w.rainChanceToday + "%)."
                : "Mostly dry today.";
    }

    private String moonName() {
        // Days since a known new moon (6 Jan 2000), on the 29.53-day cycle.
        double days = (System.currentTimeMillis() - 947182440000L) / 86400000.0;
        double ph = (days % 29.530588853 + 29.530588853) % 29.530588853;
        if (ph < 1.85) return "New Moon";
        if (ph < 5.54) return "Waxing Crescent";
        if (ph < 9.23) return "First Quarter";
        if (ph < 12.92) return "Waxing Gibbous";
        if (ph < 16.61) return "Full Moon";
        if (ph < 20.30) return "Waning Gibbous";
        if (ph < 23.99) return "Last Quarter";
        if (ph < 27.68) return "Waning Crescent";
        return "New Moon";
    }

    /** The coloured range of one day against the week's lowest and highest. */
    private final class RangeBar extends View {
        private final int lo, hi, mn, mx, now;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        RangeBar(int lo, int hi, int mn, int mx, int now) {
            super(WeatherActivity.this);
            this.lo = lo; this.hi = Math.max(hi, lo + 1); this.mn = mn; this.mx = mx; this.now = now;
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            p.setColor(0x33FFFFFF);
            c.drawRoundRect(new RectF(0, 0, w, h), h / 2, h / 2, p);
            float span = hi - lo;
            float x0 = w * (mn - lo) / span, x1 = w * (mx - lo) / span;
            if (x1 - x0 < h) x1 = x0 + h;
            p.setShader(new android.graphics.LinearGradient(x0, 0, x1, 0, 0xFF7DD3FC, 0xFFFBBF24, android.graphics.Shader.TileMode.CLAMP));
            c.drawRoundRect(new RectF(x0, 0, x1, h), h / 2, h / 2, p);
            p.setShader(null);
            if (now != Integer.MIN_VALUE) {
                float cx = Math.max(h / 2, Math.min(w - h / 2, w * (now - lo) / span));
                p.setColor(0xFFFFFFFF);
                c.drawCircle(cx, h / 2, h * 0.85f, p);
            }
        }
    }

    private WeatherBackgroundView.ConditionType determineCondition(WeatherAirQuality.FullWeather w) {
        SharedPreferences p = getSharedPreferences("app_weather_prefs", MODE_PRIVATE);
        String mode = p.getString("atmosphere_mode", "AUTO");
        if ("SUNNY".equalsIgnoreCase(mode)) return WeatherBackgroundView.ConditionType.SUNNY;
        if ("RAIN".equalsIgnoreCase(mode)) return WeatherBackgroundView.ConditionType.RAIN;
        if ("THUNDERSTORM".equalsIgnoreCase(mode)) return WeatherBackgroundView.ConditionType.THUNDERSTORM;
        if ("CLOUDY".equalsIgnoreCase(mode)) return WeatherBackgroundView.ConditionType.CLOUDY;
        if ("NIGHT".equalsIgnoreCase(mode)) return WeatherBackgroundView.ConditionType.NIGHT;

        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        boolean isNight = (hour < 7 || hour >= 19);
        String cond = (w.condition != null) ? w.condition.toLowerCase(Locale.ROOT) : "";
        if (cond.contains("snow")) return WeatherBackgroundView.ConditionType.SNOW;
        if (cond.contains("fog") || cond.contains("haze") || cond.contains("mist") || cond.contains("kabus")) return WeatherBackgroundView.ConditionType.FOG;

        if (cond.contains("thunder") || cond.contains("storm") || cond.contains("ribut")) {
            return WeatherBackgroundView.ConditionType.THUNDERSTORM;
        }
        if (cond.contains("heavy") || cond.contains("lebat")
                || (cond.contains("hujan") && !cond.contains("renyai") && !cond.contains("gerimis"))
                || (cond.contains("rain") && !cond.contains("drizzle") && !cond.contains("light"))) {
            return WeatherBackgroundView.ConditionType.RAIN;
        }
        if (cond.contains("drizzle") || cond.contains("gerimis") || cond.contains("renyai") || cond.contains("light rain")) {
            return WeatherBackgroundView.ConditionType.DRIZZLE;
        }
        if (isNight) return WeatherBackgroundView.ConditionType.NIGHT;
        if (cond.contains("cloud") || cond.contains("mendung") || cond.contains("berawan") || cond.contains("overcast")) {
            return WeatherBackgroundView.ConditionType.CLOUDY;
        }
        return WeatherBackgroundView.ConditionType.SUNNY;
    }
}
