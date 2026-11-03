package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Malaysia's public holidays (Google's public calendar) and the owner's own Google Calendar, read as iCal.
 * The owner's calendar is only ever read, through the secret iCal address they paste in; no sign-in, nothing is written back.
 * Each source can be switched on or off, and the feeds are kept on the TV so the page opens at once.
 */
final class CalendarData {
    private CalendarData() {}

    static final String HOLIDAY_URL = "https://calendar.google.com/calendar/ical/en.malaysia%23holiday%40group.v.calendar.google.com/public/basic.ics";
    private static final ZoneId KL = ZoneId.of("Asia/Kuala_Lumpur");
    private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter BASIC_DT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
    private static final long HOLIDAY_FRESH_MS = 3L * 24 * 3600_000L;
    private static final long MY_FRESH_MS = 3L * 3600_000L;

    static final class Item implements Comparable<Item> {
        final LocalDate date;
        final String title;
        final boolean holiday;
        final String time;      // "HH:mm" or "" for all day
        /** The colour of the calendar it came from. */
        final int color;
        /** The whole event: first and last day (inclusive), when it ends ("HH:mm" or ""), and one id for all its days. */
        final LocalDate evStart, evEnd;
        final String endTime;
        final int id;

        Item(LocalDate date, String title, boolean holiday, String time) {
            this(date, title, holiday, time, 0, date, date, "", 0);
        }

        Item(LocalDate date, String title, boolean holiday, String time, int color, LocalDate evStart, LocalDate evEnd, String endTime, int id) {
            this.date = date; this.title = title; this.holiday = holiday; this.time = time == null ? "" : time; this.color = color;
            this.evStart = evStart; this.evEnd = evEnd; this.endTime = endTime == null ? "" : endTime; this.id = id;
        }

        @Override
        public int compareTo(Item o) {
            int c = date.compareTo(o.date);
            if (c != 0) return c;
            if (holiday != o.holiday) return holiday ? -1 : 1;
            return time.compareTo(o.time);
        }
    }

    static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("calendar", Context.MODE_PRIVATE);
    }

    static boolean holidaysOn(Context c) { return prefs(c).getBoolean("holidays_on", true); }
    static boolean myOn(Context c) { return prefs(c).getBoolean("my_on", true); }
    /** The addresses of the owner's calendars, one per line. */
    static String myUrl(Context c) { return prefs(c).getString("my_url", ""); }
    static String holidayUrl(Context c) {
        String u = prefs(c).getString("holiday_url", "");
        return u.isEmpty() ? HOLIDAY_URL : u;
    }
    static final int HOLIDAY_GREEN = 0xFF7BE495;
    static final int[] PALETTE = {0xFFFF6B6B, 0xFF64B5F6, 0xFF7BE495, 0xFFFFB74D, 0xFFC792EA, 0xFFFFE066};
    static final String[] PALETTE_NAMES = {"Red", "Blue", "Green", "Orange", "Purple", "Yellow"};

    /** One linked calendar: what to call it, whether it shows, and its colour. */
    static final class Source {
        String url, name; boolean on; int color;
    }

    private static String keyOf(String url) { return Integer.toHexString(url.hashCode()); }

    static List<Source> sources(Context c) {
        List<Source> out = new ArrayList<>();
        SharedPreferences p = prefs(c);
        List<String> urls = myUrls(c);
        for (int i = 0; i < urls.size(); i++) {
            Source s = new Source();
            s.url = urls.get(i);
            String k = keyOf(s.url);
            String name = p.getString("name_" + k, "");
            if (name.isEmpty() && s.url.startsWith("file://")) {
                String f = s.url.substring(s.url.lastIndexOf('/') + 1).replaceAll("\\.[A-Za-z]+$", "");
                name = f.isEmpty() ? "" : Character.toUpperCase(f.charAt(0)) + f.substring(1);
            }
            s.name = name.isEmpty() ? "Calendar " + (i + 1) : name;
            s.on = p.getBoolean("on_" + k, true);
            String low = s.name.toLowerCase(Locale.ROOT);
            int def = low.contains("olive") ? PALETTE[0] : low.contains("birthday") ? PALETTE[1] : PALETTE[3 + (i % 3)];
            s.color = p.getInt("col_" + k, def);
            out.add(s);
        }
        return out;
    }

    static void setSourceOn(Context c, String url, boolean on) { prefs(c).edit().putBoolean("on_" + keyOf(url), on).apply(); }
    static void setSourceColor(Context c, String url, int color) { prefs(c).edit().putInt("col_" + keyOf(url), color).apply(); }

    private static List<String> myUrls(Context c) {
        List<String> out = new ArrayList<>();
        for (String u : myUrl(c).split("\\s+")) if (!u.isEmpty()) out.add(u);
        return out;
    }

    static void setHolidaysOn(Context c, boolean on) { prefs(c).edit().putBoolean("holidays_on", on).apply(); }
    static void setMyOn(Context c, boolean on) { prefs(c).edit().putBoolean("my_on", on).apply(); }

    /**
     * Saves the secret iCal addresses (several may be given, separated by spaces, commas or new lines; webcal:// is accepted).
     * An address of a holiday calendar replaces the built-in Malaysia holidays. An empty value unlinks everything.
     */
    static boolean setMyUrl(Context c, String url) {
        String all = url == null ? "" : url.trim();
        List<String> mine = new ArrayList<>();
        String holiday = "";
        for (String raw : all.split("[\\s,]+")) {
            String u = raw.trim();
            if (u.isEmpty()) continue;
            if (u.startsWith("webcal://")) u = "https://" + u.substring(9);
            if (!u.startsWith("https://") && !u.startsWith("http://") && !u.startsWith("file:///sdcard/")) return false;
            if (u.contains("%23holiday") || u.contains("#holiday")) holiday = u; else if (!mine.contains(u)) mine.add(u);
        }
        SharedPreferences.Editor e = prefs(c).edit();
        e.putString("my_url", String.join("\n", mine)).putLong("my_at", 0).putLong("hol_at", 0);
        if (all.isEmpty()) e.putString("holiday_url", "");
        else if (!holiday.isEmpty()) e.putString("holiday_url", holiday);
        e.apply();
        for (int i = 0; i < 12; i++) new File(c.getFilesDir(), "cal_my_" + i + ".ics").delete();
        new File(c.getFilesDir(), "cal_my.ics").delete();
        return true;
    }

    private static void addColoured(String ics, boolean holiday, int color, LocalDate from, LocalDate to, List<Item> out) {
        List<Item> tmp = new ArrayList<>();
        parse(ics, holiday, from, to, tmp);
        for (Item it : tmp) out.add(new Item(it.date, it.title, it.holiday, it.time, color, it.evStart, it.evEnd, it.endTime, it.id));
    }

    /** Everything on the switched-on calendars from {@code from} to {@code to}, oldest first. Reads files only. */
    static List<Item> items(Context c, LocalDate from, LocalDate to) {
        List<Item> out = new ArrayList<>();
        if (holidaysOn(c)) addColoured(read(new File(c.getFilesDir(), "cal_holidays.ics")), true, HOLIDAY_GREEN, from, to, out);
        if (myOn(c)) {
            List<Source> src = sources(c);
            for (int i = 0; i < src.size(); i++) {
                if (src.get(i).on) addColoured(read(new File(c.getFilesDir(), "cal_my_" + i + ".ics")), false, src.get(i).color, from, to, out);
            }
        }
        Collections.sort(out);
        return out;
    }

    static boolean hasHolidayData(Context c) {
        return new File(c.getFilesDir(), "cal_holidays.ics").length() > 0;
    }

    /** Fetches whatever is stale (or everything when forced), then calls {@code done} on the fetching thread. */
    static void refresh(Context ctx, boolean force, Runnable done) {
        final Context c = ctx.getApplicationContext();
        new Thread(() -> {
            long now = System.currentTimeMillis();
            SharedPreferences p = prefs(c);
            try {
                if (holidaysOn(c) && (force || now - p.getLong("hol_at", 0) > HOLIDAY_FRESH_MS || !hasHolidayData(c))) {
                    if (download(holidayUrl(c), new File(c.getFilesDir(), "cal_holidays.ics"))) p.edit().putLong("hol_at", now).apply();
                }
                List<String> urls = myUrls(c);
                if (myOn(c) && !urls.isEmpty() && (force || now - p.getLong("my_at", 0) > MY_FRESH_MS)) {
                    boolean any = false;
                    for (int i = 0; i < urls.size() && i < 12; i++) {
                        String my = urls.get(i);
                        File dst = new File(c.getFilesDir(), "cal_my_" + i + ".ics");
                        boolean got = false;
                        if (my.startsWith("file:///sdcard/")) {
                            // A calendar file copied to the TV from the computer (tools/calendar-file.sh): a snapshot, not live.
                            File src = new File(my.substring(7));
                            if (src.isFile()) {
                                try (java.io.InputStream in = new java.io.FileInputStream(src); java.io.OutputStream out = new java.io.FileOutputStream(dst)) {
                                    byte[] buf = new byte[8192]; int n;
                                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                                    got = true;
                                }
                            }
                        } else got = download(my, dst);
                        if (got && !my.startsWith("file://")) {
                            java.util.regex.Matcher nm = java.util.regex.Pattern.compile("(?m)^X-WR-CALNAME:(.*)$").matcher(read(dst));
                            if (nm.find()) p.edit().putString("name_" + keyOf(my), nm.group(1).trim()).apply();
                        }
                        any |= got;
                    }
                    if (any) p.edit().putLong("my_at", now).apply();
                }
            } catch (Exception e) {
                android.util.Log.w("TvHome.Calendar", "refresh failed: " + e);
            }
            if (done != null) done.run();
        }, "calendar").start();
    }

    private static boolean download(String url, File dest) {
        try {
            HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
            h.setConnectTimeout(10_000);
            h.setReadTimeout(20_000);
            h.setRequestProperty("User-Agent", "GlassTV-Home");
            try (InputStream in = h.getInputStream(); ByteArrayOutputStream bo = new ByteArrayOutputStream()) {
                byte[] b = new byte[16384];
                int r;
                while ((r = in.read(b)) > 0) bo.write(b, 0, r);
                byte[] data = bo.toByteArray();
                if (data.length < 20 || !new String(data, 0, Math.min(40, data.length), StandardCharsets.UTF_8).contains("BEGIN:VCALENDAR")) return false;
                try (FileOutputStream out = new FileOutputStream(dest)) { out.write(data); }
                return true;
            } finally {
                h.disconnect();
            }
        } catch (Exception e) {
            android.util.Log.w("TvHome.Calendar", "download failed: " + e);
            return false;
        }
    }

    private static String read(File f) {
        if (!f.exists()) return "";
        try (java.io.FileInputStream in = new java.io.FileInputStream(f); ByteArrayOutputStream bo = new ByteArrayOutputStream()) {
            byte[] b = new byte[16384];
            int r;
            while ((r = in.read(b)) > 0) bo.write(b, 0, r);
            return new String(bo.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    // ---------------------------------------------------------------- iCal

    private static final class Dt {
        LocalDate date;
        LocalTime time;
    }

    private static Dt dt(String params, String value) {
        try {
            Dt r = new Dt();
            if (value.length() == 8) { r.date = LocalDate.parse(value, BASIC_DATE); return r; }
            boolean utc = value.endsWith("Z");
            LocalDateTime ldt = LocalDateTime.parse(utc ? value.substring(0, value.length() - 1) : value, BASIC_DT);
            ZoneId zone = utc ? ZoneOffset.UTC : tz(params);
            ZonedDateTime z = ldt.atZone(zone).withZoneSameInstant(KL);
            r.date = z.toLocalDate();
            r.time = z.toLocalTime();
            return r;
        } catch (Exception e) {
            return null;
        }
    }

    private static ZoneId tz(String params) {
        if (params != null) {
            int i = params.indexOf("TZID=");
            if (i >= 0) {
                String id = params.substring(i + 5);
                int end = id.indexOf(';');
                if (end >= 0) id = id.substring(0, end);
                try { return ZoneId.of(id.replace("\"", "")); } catch (Exception ignored) { }
            }
        }
        return KL;
    }

    private static String unescape(String s) {
        return s.replace("\\n", " ").replace("\\N", " ").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\").trim();
    }

    private static void parse(String ics, boolean holiday, LocalDate from, LocalDate to, List<Item> out) {
        if (ics.isEmpty()) return;
        // Unfold: a line starting with a space or tab continues the one before.
        List<String> lines = new ArrayList<>();
        for (String raw : ics.split("\r?\n")) {
            if ((raw.startsWith(" ") || raw.startsWith("\t")) && !lines.isEmpty()) {
                lines.set(lines.size() - 1, lines.get(lines.size() - 1) + raw.substring(1));
            } else {
                lines.add(raw);
            }
        }
        boolean inEvent = false;
        String summary = "", rrule = "", status = "";
        Dt start = null, end = null;
        Set<LocalDate> exdates = new HashSet<>();
        for (String line : lines) {
            if (line.equals("BEGIN:VEVENT")) {
                inEvent = true; summary = ""; rrule = ""; status = ""; start = null; end = null; exdates.clear();
                continue;
            }
            if (line.equals("END:VEVENT")) {
                if (inEvent && start != null && !"CANCELLED".equals(status)) {
                    expand(summary, holiday, start, end, rrule, exdates, from, to, out);
                }
                inEvent = false;
                continue;
            }
            if (!inEvent) continue;
            int colon = line.indexOf(':');
            if (colon < 0) continue;
            String left = line.substring(0, colon), value = line.substring(colon + 1);
            int semi = left.indexOf(';');
            String name = semi >= 0 ? left.substring(0, semi) : left;
            String params = semi >= 0 ? left.substring(semi + 1) : "";
            switch (name) {
                case "SUMMARY": summary = unescape(value); break;
                case "DTSTART": start = dt(params, value); break;
                case "DTEND": end = dt(params, value); break;
                case "RRULE": rrule = value; break;
                case "STATUS": status = value; break;
                case "EXDATE":
                    for (String v : value.split(",")) {
                        Dt e = dt(params, v.trim());
                        if (e != null) exdates.add(e.date);
                    }
                    break;
                default: break;
            }
        }
    }

    private static void expand(String title, boolean holiday, Dt start, Dt end, String rrule, Set<LocalDate> ex,
                               LocalDate from, LocalDate to, List<Item> out) {
        String time = start.time == null ? "" : start.time.toString().substring(0, 5);
        int span = 1;
        String endTime = "";
        if (start.time == null && end != null && end.date != null) {
            span = (int) Math.max(1, Math.min(14, ChronoUnit.DAYS.between(start.date, end.date)));
        } else if (start.time != null && end != null && end.date != null) {
            // A timed event that runs over several days (3pm on the 3rd to 10am on the 5th) is one bar over those days.
            long dd = ChronoUnit.DAYS.between(start.date, end.date);
            if (end.time != null) {
                endTime = end.time.toString().substring(0, 5);
                if (end.time.equals(LocalTime.MIDNIGHT) && dd > 0) dd -= 1;
            }
            span = (int) Math.max(1, Math.min(14, dd + 1));
        }
        if (rrule.isEmpty()) {
            add(out, title, holiday, start.date, span, time, endTime, from, to, ex);
            return;
        }
        Map<String, String> r = new HashMap<>();
        for (String part : rrule.split(";")) {
            int eq = part.indexOf('=');
            if (eq > 0) r.put(part.substring(0, eq), part.substring(eq + 1));
        }
        String freq = r.containsKey("FREQ") ? r.get("FREQ") : "";
        int interval = 1;
        try { interval = Math.max(1, Integer.parseInt(r.containsKey("INTERVAL") ? r.get("INTERVAL") : "1")); } catch (Exception ignored) { }
        int count = -1;
        try { if (r.containsKey("COUNT")) count = Integer.parseInt(r.get("COUNT")); } catch (Exception ignored) { }
        LocalDate until = null;
        if (r.containsKey("UNTIL")) {
            Dt u = dt("", r.get("UNTIL").length() > 8 ? r.get("UNTIL") : r.get("UNTIL"));
            if (u != null) until = u.date;
        }
        Set<DayOfWeek> days = new HashSet<>();
        if (r.containsKey("BYDAY")) {
            for (String tok : r.get("BYDAY").split(",")) {
                String code = tok.replaceAll("[^A-Z]", "");
                switch (code) {
                    case "MO": days.add(DayOfWeek.MONDAY); break;
                    case "TU": days.add(DayOfWeek.TUESDAY); break;
                    case "WE": days.add(DayOfWeek.WEDNESDAY); break;
                    case "TH": days.add(DayOfWeek.THURSDAY); break;
                    case "FR": days.add(DayOfWeek.FRIDAY); break;
                    case "SA": days.add(DayOfWeek.SATURDAY); break;
                    case "SU": days.add(DayOfWeek.SUNDAY); break;
                    default: break;
                }
            }
        }
        if (days.isEmpty()) days.add(start.date.getDayOfWeek());
        LocalDate s = start.date;
        LocalDate stop = until != null && until.isBefore(to) ? until : to;
        int seen = 0;
        LocalDate weekZero = s.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        for (LocalDate d = s; !d.isAfter(stop); d = d.plusDays(1)) {
            boolean match;
            switch (freq) {
                case "DAILY": match = ChronoUnit.DAYS.between(s, d) % interval == 0; break;
                case "WEEKLY": {
                    long weeks = ChronoUnit.WEEKS.between(weekZero, d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
                    match = weeks % interval == 0 && days.contains(d.getDayOfWeek());
                    break;
                }
                case "MONTHLY": match = ChronoUnit.MONTHS.between(s.withDayOfMonth(1), d.withDayOfMonth(1)) % interval == 0 && d.getDayOfMonth() == s.getDayOfMonth(); break;
                case "YEARLY": match = ChronoUnit.YEARS.between(s.withDayOfYear(1), d.withDayOfYear(1)) % interval == 0 && d.getMonthValue() == s.getMonthValue() && d.getDayOfMonth() == s.getDayOfMonth(); break;
                default: match = false; break;
            }
            if (!match) continue;
            seen++;
            if (count > 0 && seen > count) break;
            add(out, title, holiday, d, span, time, endTime, from, to, ex);
        }
    }

    private static int nextId;

    private static void add(List<Item> out, String title, boolean holiday, LocalDate first, int span, String time, String endTime,
                            LocalDate from, LocalDate to, Set<LocalDate> ex) {
        final int id = ++nextId;
        final LocalDate last = first.plusDays(span - 1);
        for (int k = 0; k < span; k++) {
            LocalDate d = first.plusDays(k);
            if (d.isBefore(from) || d.isAfter(to) || ex.contains(d)) continue;
            out.add(new Item(d, title, holiday, time, 0, first, last, endTime, id));
        }
    }
}
