package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Prayer times in the iOS glass style on a sky that follows the hour: a ring counting down to the next
 * prayer in seconds, today's six times, the next seven days and the coming Islamic dates. One screen, no scroll.
 */
public class PrayerActivity extends Activity {

    private float d;
    private FrameLayout root;
    private LinearLayout page;
    private WeatherBackgroundView front;
    private FrameLayout bgHolder;
    private boolean mosqueShown;

    /** A mosque picture when one is kept, else the home wallpaper. */
    private void showBackground() {
        if (mosqueShown) return;
        android.graphics.Bitmap m = MosquePhotos.random(this);
        bgHolder.removeAllViews();
        if (m != null) {
            mosqueShown = true;
            bgHolder.addView(GlassPage.picture(this, m, false, 0xA6000000), new FrameLayout.LayoutParams(-1, -1));
        } else {
            bgHolder.addView(GlassPage.wallpaper(this), new FrameLayout.LayoutParams(-1, -1));
        }
    }
    private Ring ring;
    private long targetMs, prevMs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            long now = System.currentTimeMillis();
            if (now >= targetMs || ring == null) {
                build();
            } else {
                ring.update(now);
            }
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        d = getResources().getDisplayMetrics().density;
        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0B1220);
        bgHolder = new FrameLayout(this);
        root.addView(bgHolder, new FrameLayout.LayoutParams(-1, -1));
        showBackground();
        // One more mosque picture is kept each time this page opens (Malaysia first, then the world).
        MosquePhotos.fetchNext(this, () -> { if (!isDestroyed()) showBackground(); });
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setClipChildren(false);
        page.setPadding((int) (40 * d), (int) (14 * d), (int) (40 * d), (int) (16 * d));
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));
        front = new WeatherBackgroundView(this);
        front.setOverlayMode(true);
        front.setFocusable(false);
        root.addView(front, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        front.startAnimation();
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
        front.stopAnimation();
    }

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        t.setIncludeFontPadding(false);
        t.setSingleLine(true);
        return t;
    }

    /** Centred in its box, and shrunk rather than cut when the box is narrow. */
    private TextView fit(TextView t, int minSp, float maxSp) {
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setAutoSizeTextTypeUniformWithConfiguration(minSp, (int) maxSp, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        return t;
    }

    private void glass(View v, boolean strong) {
        GlassPage.glass(v, d, 22, strong);
    }

    private LinearLayout card(boolean focusable) {
        final LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setClipChildren(false);
        c.setFocusable(focusable);
        glass(c, false);
        if (focusable) {
            c.setOnFocusChangeListener((v, has) -> {
                glass(c, has);
                GlassPage.lift(c, d, has, 1.03f);
            });
        }
        return c;
    }

    /** The countdown ring: how much of the wait for the next prayer has passed, and the time left in seconds. */
    private final class Ring extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final String name, at;
        private String left = "";
        private float progress;

        Ring(String name, String at) {
            super(PrayerActivity.this);
            this.name = name;
            this.at = at;
            update(System.currentTimeMillis());
        }

        void update(long now) {
            long total = Math.max(1, targetMs - prevMs);
            progress = Math.max(0f, Math.min(1f, (now - prevMs) / (float) total));
            long sec = Math.max(0, (targetMs - now) / 1000);
            left = String.format(Locale.ROOT, "%d:%02d:%02d", sec / 3600, (sec % 3600) / 60, sec % 60);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            float size = Math.min(w, h), stroke = 7 * d;
            RectF r = new RectF(w / 2 - size / 2 + stroke, h / 2 - size / 2 + stroke, w / 2 + size / 2 - stroke, h / 2 + size / 2 - stroke);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(stroke);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(0x33FFFFFF);
            c.drawArc(r, 0, 360, false, p);
            p.setColor(0xFFFFB300);
            c.drawArc(r, -90, 360 * progress, false, p);
            float k = size / (190 * d);
            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);
            p.setColor(0xFFFFB300);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextSize(15 * d * k);
            c.drawText(name.toUpperCase(Locale.ROOT), w / 2, h / 2 - 28 * d * k, p);
            p.setColor(0xFFFFFFFF);
            p.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
            p.setTextSize(36 * d * k);
            c.drawText(left, w / 2, h / 2 + 12 * d * k, p);
            p.setTypeface(Typeface.DEFAULT);
            p.setColor(0xCCFFFFFF);
            p.setTextSize(14 * d * k);
            c.drawText("at " + at, w / 2, h / 2 + 40 * d * k, p);
        }
    }

    private static final class Ev {
        final String name; final int month, day;
        Ev(String n, int m, int d) { name = n; month = m; day = d; }
    }

    private static final Ev[] EVENTS = {
            new Ev("Awal Muharram", 1, 1),
            new Ev("Ashura", 1, 10),
            new Ev("Maulidur Rasul", 3, 12),
            new Ev("Isra and Mi'raj", 7, 27),
            new Ev("Nisfu Syaaban", 8, 15),
            new Ev("Ramadan begins (fasting)", 9, 1),
            new Ev("Nuzul Quran", 9, 17),
            new Ev("Laylat al-Qadr (27th night)", 9, 27),
            new Ev("Hari Raya Aidilfitri", 10, 1),
            new Ev("Arafah", 12, 9),
            new Ev("Hari Raya Haji", 12, 10),
    };

    /** Days from today to the next time the Hijri date (month, day) falls, or -1. */
    private static long daysTo(int month, int day, java.time.LocalDate today, java.time.LocalDate[] out) {
        try {
            int hy = java.time.chrono.HijrahDate.from(today).get(java.time.temporal.ChronoField.YEAR);
            for (int y = hy; y <= hy + 1; y++) {
                java.time.LocalDate g = java.time.LocalDate.from(java.time.chrono.HijrahDate.of(y, month, day));
                if (!g.isBefore(today)) { out[0] = g; return java.time.temporal.ChronoUnit.DAYS.between(today, g); }
            }
        } catch (Throwable ignored) { }
        return -1;
    }

    private void build() {
        page.removeAllViews();
        PrayerTimes.PrayerItem[] items = PrayerTimes.getPrayerItems(this);
        long[] info = PrayerTimes.nextInfo(this);
        targetMs = info[1];
        prevMs = info[2];
        String[] names = AppLanguage.getPrayerNames(this);
        String nextName = names[(int) info[0]];
        String nextTime = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(targetMs));

        // The sky follows the hour: stars before sunrise and after Maghrib, sun in between.
        long[] today = PrayerTimes.timesFor(this, Calendar.getInstance());
        long nowMs = System.currentTimeMillis();
        boolean night = nowMs < today[1] || nowMs >= today[4];
        WeatherBackgroundView.ConditionType ct = night ? WeatherBackgroundView.ConditionType.NIGHT : WeatherBackgroundView.ConditionType.SUNNY;
        // The weather outside is shown over the picture: rain, haze, snow or cloud, else stars or sun.
        HomeWeather.Line wl = HomeWeather.last(this);
        if (wl != null && wl.weather != null) {
            String wt = wl.weather.toLowerCase(Locale.ROOT);
            int cut = wt.indexOf('\u00B7');
            if (cut > 0) wt = wt.substring(0, cut);
            if (wt.contains("thunder") || wt.contains("ribut") || wt.contains("petir")) ct = WeatherBackgroundView.ConditionType.THUNDERSTORM;
            else if (wt.contains("heavy") || wt.contains("lebat") || wt.contains("showers")) ct = WeatherBackgroundView.ConditionType.RAIN;
            else if (wt.contains("rain") || wt.contains("drizzle") || wt.contains("hujan") || wt.contains("renyai") || wt.contains("gerimis")) ct = WeatherBackgroundView.ConditionType.DRIZZLE;
            else if (wt.contains("snow")) ct = WeatherBackgroundView.ConditionType.SNOW;
            else if (wt.contains("haze") || wt.contains("fog") || wt.contains("kabus")) ct = WeatherBackgroundView.ConditionType.FOG;
            else if (!night && (wt.contains("cloud") || wt.contains("mendung"))) ct = WeatherBackgroundView.ConditionType.CLOUDY;
        }
        front.setCondition(ct);

        Location.Zone z = Location.chosen(this);
        String place = (z != null && z.town != null) ? z.town : PrayerTimes.getShortZoneName(this);

        // ----- top, in the middle: place, dates and the countdown
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setGravity(Gravity.CENTER_HORIZONTAL);
        top.setClipChildren(false);
        page.addView(top, new LinearLayout.LayoutParams(-1, -2));
        TextView title = text("PRAYER TIMES", 11f, 0xB3FFFFFF, true);
        title.setLetterSpacing(0.08f);
        top.addView(title, new LinearLayout.LayoutParams(-2, -2));
        TextView placeView = text(place, 24f, 0xFFFFFFFF, true);
        top.addView(placeView, new LinearLayout.LayoutParams(-2, -2));
        String hijri = PrayerTimes.getHijriDate(this);
        String greg = new SimpleDateFormat("EEEE, d MMMM yyyy", Locale.ENGLISH).format(new Date());
        TextView dates = text((hijri == null || hijri.isEmpty() ? "" : hijri + "  |  ") + greg, 12.5f, 0xCCFFFFFF, false);
        dates.setPadding(0, (int) (2 * d), 0, 0);
        top.addView(dates, new LinearLayout.LayoutParams(-2, -2));
        ring = new Ring(nextName, nextTime);
        LinearLayout.LayoutParams rgl = new LinearLayout.LayoutParams((int) (150 * d), (int) (150 * d));
        rgl.topMargin = (int) (4 * d);
        top.addView(ring, rgl);

        // ----- today's seven times in one box
        final LinearLayout times = card(true);
        times.setOrientation(LinearLayout.HORIZONTAL);
        times.setGravity(Gravity.CENTER_VERTICAL);
        times.setPadding((int) (10 * d), (int) (6 * d), (int) (10 * d), (int) (6 * d));
        String[] cardNames = {"Subuh", "Syuruk", "Dhuha", "Zohor", "Asar", "Maghrib", "Isyak"};
        SimpleDateFormat hm = new SimpleDateFormat("HH:mm", Locale.getDefault());
        long dhuha = today[1] + 28 * 60_000L;
        for (int k = 0; k < 7; k++) {
            int src = k < 2 ? k : k - 1;           // index into the six real times; Dhuha (k == 2) is computed
            boolean isDhuha = k == 2;
            final boolean next = !isDhuha && items != null && items[src].isNext;
            final boolean past = isDhuha ? nowMs > dhuha : items != null && items[src].isPast;
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER);
            col.setAlpha(past && !next ? 0.6f : 1f);
            col.addView(fit(text(cardNames[k].toUpperCase(Locale.ROOT), 10.5f, next ? 0xFFFFB300 : 0xB3FFFFFF, true), 8, 10.5f),
                    new LinearLayout.LayoutParams(-1, -2));
            TextView t = text(isDhuha ? hm.format(new Date(dhuha)) : items[src].timeStr, 19f, next ? 0xFFFFB300 : 0xFFFFFFFF, next);
            t.setPadding(0, (int) (3 * d), 0, 0);
            col.addView(fit(t, 11, 19f), new LinearLayout.LayoutParams(-1, -2));
            times.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2);
        tlp.topMargin = (int) (6 * d);
        page.addView(times, tlp);

        // ----- below: Islamic dates and the next seven days
        LinearLayout lower = new LinearLayout(this);
        lower.setOrientation(LinearLayout.HORIZONTAL);
        lower.setClipChildren(false);
        LinearLayout.LayoutParams lwp = new LinearLayout.LayoutParams(-1, 0, 1f);
        lwp.topMargin = (int) (8 * d);
        page.addView(lower, lwp);

        LinearLayout events = card(true);
        events.setPadding((int) (14 * d), (int) (8 * d), (int) (14 * d), (int) (8 * d));
        TextView et = text("ISLAMIC DATES", 10.5f, 0xB3FFFFFF, true);
        et.setLetterSpacing(0.06f);
        events.addView(et);
        java.time.LocalDate day0 = java.time.LocalDate.now();
        java.util.List<Object[]> list = new java.util.ArrayList<>();
        for (Ev e : EVENTS) {
            java.time.LocalDate[] on = new java.time.LocalDate[1];
            long n = daysTo(e.month, e.day, day0, on);
            if (n >= 0) list.add(new Object[]{e.name, on[0], n});
        }
        java.util.Collections.sort(list, (a, b) -> Long.compare((Long) a[2], (Long) b[2]));
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
        for (int i = 0; i < Math.min(8, list.size()); i++) {
            Object[] o = list.get(i);
            long n = (Long) o[2];
            LinearLayout line = new LinearLayout(this);
            line.setGravity(Gravity.CENTER_VERTICAL);
            line.addView(text((String) o[0], 12f, i == 0 ? 0xFFFFB300 : 0xFFFFFFFF, true), new LinearLayout.LayoutParams(0, -2, 1f));
            line.addView(text(fmt.format((java.time.LocalDate) o[1]), 10.5f, 0xB3FFFFFF, false), new LinearLayout.LayoutParams(-2, -2));
            TextView c = text(n == 0 ? "Today" : n == 1 ? "Tomorrow" : n + " days", 11.5f, i == 0 ? 0xFFFFB300 : 0xFFFFFFFF, true);
            c.setGravity(Gravity.END);
            line.addView(c, new LinearLayout.LayoutParams((int) (64 * d), -2));
            events.addView(line, new LinearLayout.LayoutParams(-1, 0, 1f));
        }
        lower.addView(events, new LinearLayout.LayoutParams(0, -1, 1f));

        LinearLayout week = card(true);
        week.setPadding((int) (14 * d), (int) (8 * d), (int) (14 * d), (int) (8 * d));
        TextView wt = text("NEXT 7 DAYS", 10.5f, 0xB3FFFFFF, true);
        wt.setLetterSpacing(0.06f);
        week.addView(wt);
        week.addView(weekLine("", new String[]{"Subuh", "Syuruk", "Dhuha", "Zohor", "Asar", "Maghrib", "Isyak"}, true, false));
        SimpleDateFormat tf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        SimpleDateFormat df = new SimpleDateFormat("EEE d MMM", Locale.ENGLISH);
        for (int i = 0; i < 7; i++) {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_MONTH, i);
            long[] t = PrayerTimes.timesFor(this, cal);
            String[] v = {tf.format(new Date(t[0])), tf.format(new Date(t[1])), tf.format(new Date(t[1] + 28 * 60_000L)), tf.format(new Date(t[2])), tf.format(new Date(t[3])), tf.format(new Date(t[4])), tf.format(new Date(t[5]))};
            week.addView(weekLine(i == 0 ? "Today" : df.format(cal.getTime()), v, false, i == 0));
        }
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(0, -1, 1.3f);
        wlp.leftMargin = (int) (10 * d);
        lower.addView(week, wlp);

        times.post(times::requestFocus);
    }

    private View weekLine(String day, String[] v, boolean header, boolean today) {
        LinearLayout line = new LinearLayout(this);
        line.setGravity(Gravity.CENTER_VERTICAL);
        int color = header ? 0x99FFFFFF : today ? 0xFFFFB300 : 0xFFFFFFFF;
        line.addView(text(day, 11.5f, color, header || today), new LinearLayout.LayoutParams((int) (74 * d), -2));
        for (String s : v) {
            TextView t = text(s, header ? 9.5f : 11.5f, color, header || today);
            t.setGravity(Gravity.CENTER);
            line.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        line.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));
        return line;
    }
}
