package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.gathin.tvhome.qrcode.QrBitmapUtil;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.chrono.HijrahDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Calendar in the glass style. The arrow keys walk from day to day (across months too); the day picked shows what
 * is on it. Malaysia's holidays and the owner's own Google Calendar (read only, through a secret iCal address).
 */
public class CalendarActivity extends Activity {

    private float d;
    private FrameLayout root;
    private LinearLayout page;
    private LocalDate sel = LocalDate.now();
    private YearMonth shown = YearMonth.now();
    private final List<CalendarData.Item> items = new ArrayList<>();
    private final Map<LocalDate, View> cells = new HashMap<>();
    private LinearLayout detail;
    private TextView gear, btnPrev, btnNext, btnToday;
    private TextView[] headBtns;
    private Runnable[] headActs;
    /** Which header button has the arrow-key focus, or -1 while the arrows are on the days. */
    private int headIdx = -1;
    private TextView title;
    private LinkServer server;
    private AlertDialog qrDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        d = getResources().getDisplayMetrics().density;
        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0B1220);
        root.addView(GlassPage.wallpaper(this), new FrameLayout.LayoutParams(-1, -1));
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.HORIZONTAL);
        page.setClipChildren(false);
        page.setPadding((int) (40 * d), (int) (22 * d), (int) (40 * d), (int) (22 * d));
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        render();
        CalendarData.refresh(this, false, () -> runOnUiThread(() -> { if (!isDestroyed()) render(); }));
    }

    @Override
    protected void onDestroy() {
        stopServer();
        super.onDestroy();
    }

    private void stopServer() {
        if (server != null) { server.stop(); server = null; }
    }

    // ---------------------------------------------------------------- helpers

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        t.setIncludeFontPadding(false);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private TextView headButton(String label) {
        TextView t = text(label, 15f, 0xFFFFFFFF, true);
        t.setPadding((int) (10 * d), (int) (6 * d), (int) (10 * d), (int) (6 * d));
        t.setFocusable(false);
        t.setClickable(true);
        GlassPage.glass(t, d, 16, false);
        t.setOnFocusChangeListener((v, has) -> GlassPage.glass(v, d, 16, has));
        return t;
    }

    private void enterHeader(int i) {
        headIdx = Math.max(0, Math.min(headBtns.length - 1, i));
        for (TextView b : headBtns) b.setFocusable(true);
        headBtns[headIdx].requestFocus();
    }

    private void leaveHeader() {
        headIdx = -1;
        for (TextView b : headBtns) { b.clearFocus(); b.setFocusable(false); }
    }

    private void moveMonth(int delta) {
        select(sel.plusMonths(delta));
    }

    /** 15:00 -> 3pm, 11:30 -> 11:30am. */
    private static String shortTime(String hhmm) {
        if (hhmm == null || hhmm.length() < 5) return "";
        int h = Integer.parseInt(hhmm.substring(0, 2)), m = Integer.parseInt(hhmm.substring(3, 5));
        String ap = h >= 12 ? "pm" : "am";
        int h12 = h % 12 == 0 ? 12 : h % 12;
        return h12 + (m == 0 ? "" : ":" + (m < 10 ? "0" : "") + m) + ap;
    }

    /** When an event is on, in words: the days and the hours, as the calendar has them. */
    private static String whenText(CalendarData.Item it) {
        DateTimeFormatter f = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
        boolean multi = !it.evStart.equals(it.evEnd);
        if (it.time.isEmpty()) return multi ? it.evStart.format(f) + " - " + it.evEnd.format(f) : "All day";
        String a = shortTime(it.time), b = shortTime(it.endTime);
        if (!multi) return b.isEmpty() ? a : a + " - " + b;
        return it.evStart.format(f) + " " + a + " - " + it.evEnd.format(f) + (b.isEmpty() ? "" : " " + b);
    }

    private static final class Seg {
        final CalendarData.Item item; int c0, c1;
        Seg(CalendarData.Item it, int c) { item = it; c0 = c; c1 = c; }
    }

    /**
     * Over one week row: events that last all day or more than one day are drawn as a bar from their first to their last day
     * (cut square at the edge when they go on into the next or came from the last week); events that overlap go on the next line.
     * Timed events of one day are a dot and a line of text. At most three lines a day; the rest is "+n more".
     */
    private void addWeekBars(FrameLayout weekF, LocalDate weekStart) {
        final int lines = 3;
        final int lineH = (int) (14 * d);
        LinearLayout ov = new LinearLayout(this);
        ov.setOrientation(LinearLayout.VERTICAL);
        ov.setPadding(0, (int) (26 * d), 0, 0);
        weekF.addView(ov, new FrameLayout.LayoutParams(-1, -1));

        java.util.LinkedHashMap<Integer, Seg> segs = new java.util.LinkedHashMap<>();
        List<List<CalendarData.Item>> timed = new ArrayList<>();
        for (int c = 0; c < 7; c++) {
            timed.add(new ArrayList<>());
            LocalDate day = weekStart.plusDays(c);
            for (CalendarData.Item it : items) {
                if (!it.date.equals(day)) continue;
                boolean bar = it.time.isEmpty() || !it.evStart.equals(it.evEnd);
                if (bar) {
                    Seg sg = segs.get(it.id);
                    if (sg == null) segs.put(it.id, new Seg(it, c)); else sg.c1 = c;
                } else {
                    timed.get(c).add(it);
                }
            }
        }
        List<Seg> order = new ArrayList<>(segs.values());
        java.util.Collections.sort(order, (x, y) -> {
            if (x.c0 != y.c0) return x.c0 - y.c0;
            if ((x.c1 - x.c0) != (y.c1 - y.c0)) return (y.c1 - y.c0) - (x.c1 - x.c0);
            if (x.item.holiday != y.item.holiday) return x.item.holiday ? -1 : 1;
            return x.item.title.compareTo(y.item.title);
        });
        // Put each bar on the first line where it does not touch another.
        List<List<Seg>> lanes = new ArrayList<>();
        int[] hidden = new int[7];
        for (Seg sg : order) {
            int placed = -1;
            for (int ln = 0; ln < lanes.size() && placed < 0; ln++) {
                boolean free = true;
                for (Seg o : lanes.get(ln)) if (!(sg.c1 < o.c0 || sg.c0 > o.c1)) { free = false; break; }
                if (free) placed = ln;
            }
            if (placed < 0) { lanes.add(new ArrayList<>()); placed = lanes.size() - 1; }
            lanes.get(placed).add(sg);
            if (placed >= lines) for (int c = sg.c0; c <= sg.c1; c++) hidden[c]++;
        }
        int used = Math.min(lines, lanes.size());
        for (int ln = 0; ln < used; ln++) {
            LinearLayout row = new LinearLayout(this);
            List<Seg> in = lanes.get(ln);
            java.util.Collections.sort(in, (x, y) -> x.c0 - y.c0);
            int cursor = 0;
            for (Seg sg : in) {
                if (sg.c0 > cursor) row.addView(new View(this), new LinearLayout.LayoutParams(0, lineH, sg.c0 - cursor));
                row.addView(barView(sg, weekStart, lineH));
                cursor = sg.c1 + 1;
            }
            if (cursor < 7) row.addView(new View(this), new LinearLayout.LayoutParams(0, lineH, 7 - cursor));
            ov.addView(row, new LinearLayout.LayoutParams(-1, lineH + (int) (1 * d)));
        }
        int remaining = lines - used;
        for (int r = 0; r < remaining; r++) {
            LinearLayout row = new LinearLayout(this);
            boolean any = false;
            for (int c = 0; c < 7; c++) {
                List<CalendarData.Item> t = timed.get(c);
                int extra = hidden[c];
                int fit = (t.size() + extra > remaining) ? remaining - 1 : t.size();
                TextView cellTv = null;
                if (r < fit) {
                    CalendarData.Item it = t.get(r);
                    android.text.SpannableString sp = new android.text.SpannableString("\u25CF " + shortTime(it.time) + " " + it.title);
                    sp.setSpan(new android.text.style.ForegroundColorSpan(it.color != 0 ? it.color : 0xFF64B5F6), 0, 1, 0);
                    cellTv = noteView(sp);
                } else if (r == fit && t.size() + extra > remaining) {
                    cellTv = noteView(new android.text.SpannableString("+" + (t.size() - fit + extra) + " more"));
                }
                if (cellTv != null) { row.addView(cellTv, new LinearLayout.LayoutParams(0, lineH, 1f)); any = true; }
                else row.addView(new View(this), new LinearLayout.LayoutParams(0, lineH, 1f));
            }
            if (any) ov.addView(row, new LinearLayout.LayoutParams(-1, lineH + (int) (1 * d)));
        }
    }

    private TextView noteView(CharSequence cs) {
        TextView t = new TextView(this);
        t.setText(cs);
        t.setTextSize(8.5f);
        t.setTextColor(0xCCFFFFFF);
        t.setIncludeFontPadding(false);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        t.setPadding((int) (5 * d), 0, (int) (2 * d), 0);
        return t;
    }

    private TextView barView(Seg sg, LocalDate weekStart, int lineH) {
        CalendarData.Item it = sg.item;
        boolean cutL = it.evStart.isBefore(weekStart);
        boolean cutR = it.evEnd.isAfter(weekStart.plusDays(6));
        int col = it.color != 0 ? it.color : 0xFF64B5F6;
        TextView t = noteView((it.time.isEmpty() ? "" : shortTime(it.time) + " ") + it.title);
        double lum = 0.299 * ((col >> 16) & 255) + 0.587 * ((col >> 8) & 255) + 0.114 * (col & 255);
        t.setTextColor(lum > 170 ? 0xFF10151F : 0xFFFFFFFF);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding((int) (6 * d), 0, (int) (4 * d), 0);
        GradientDrawable g = new GradientDrawable();
        float r = 5 * d, z = 0;
        g.setCornerRadii(new float[]{cutL ? z : r, cutL ? z : r, cutR ? z : r, cutR ? z : r, cutR ? z : r, cutR ? z : r, cutL ? z : r, cutL ? z : r});
        g.setColor(col);
        t.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, lineH, sg.c1 - sg.c0 + 1);
        lp.setMargins(cutL ? 0 : (int) (3 * d), 0, cutR ? 0 : (int) (3 * d), 0);
        t.setLayoutParams(lp);
        return t;
    }

    /** One short line under a day number: the event title, cut with ... when it does not fit. */
    private TextView dayNote(String s, int color, boolean inMonth) {
        TextView t = text(s, 9f, inMonth ? color : (color & 0x00FFFFFF) | 0x55000000, false);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setPadding((int) (3 * d), 0, (int) (3 * d), 0);
        return t;
    }

    private LinearLayout card(float radiusDp) {
        final LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setClipChildren(false);
        GlassPage.glass(c, d, radiusDp, false);
        return c;
    }

    private static String hijriDay(LocalDate day) {
        try {
            return String.valueOf(HijrahDate.from(day).get(ChronoField.DAY_OF_MONTH));
        } catch (Throwable t) {
            return "";
        }
    }

    private static String hijriFull(LocalDate day) {
        try {
            HijrahDate h = HijrahDate.from(day);
            return h.get(ChronoField.DAY_OF_MONTH) + " " + hijriMonth(h.get(ChronoField.MONTH_OF_YEAR)) + " " + h.get(ChronoField.YEAR) + "H";
        } catch (Throwable t) {
            return "";
        }
    }

    private static String hijriMonth(int m) {
        String[] n = {"Muharram", "Safar", "Rabiulawal", "Rabiulakhir", "Jamadilawal", "Jamadilakhir", "Rejab", "Syaaban", "Ramadan", "Syawal", "Zulkaedah", "Zulhijjah"};
        return n[Math.max(0, Math.min(11, m - 1))];
    }

    // ---------------------------------------------------------------- page

    private void render() {
        page.removeAllViews();
        cells.clear();
        LocalDate first = shown.atDay(1);
        LocalDate gridStart = first.minusDays(first.getDayOfWeek().getValue() % 7);     // Sunday first
        items.clear();
        items.addAll(CalendarData.items(this, gridStart.minusDays(1), LocalDate.now().plusDays(400)));
        final LocalDate today = LocalDate.now();

        // ----- left: the month
        LinearLayout left = card(22);
        left.setPadding((int) (16 * d), (int) (12 * d), (int) (16 * d), (int) (12 * d));
        page.addView(left, new LinearLayout.LayoutParams(0, -1, 1.5f));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        btnPrev = headButton("<");
        btnNext = headButton(">");
        btnToday = headButton("Today");
        gear = headButton("Settings");
        headBtns = new TextView[]{btnPrev, btnToday, btnNext, gear};
        headActs = new Runnable[]{() -> moveMonth(-1), () -> select(LocalDate.now()), () -> moveMonth(1), this::showSettings};
        for (int q = 0; q < headBtns.length; q++) {
            final int qi = q;
            headBtns[q].setOnClickListener(v -> headActs[qi].run());
        }
        title = text(shown.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)), 22f, 0xFFFFFFFF, true);
        head.addView(btnPrev, new LinearLayout.LayoutParams((int) (44 * d), -2));
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        head.addView(btnNext, new LinearLayout.LayoutParams((int) (44 * d), -2));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams((int) (80 * d), -2);
        tlp.leftMargin = (int) (14 * d);
        head.addView(btnToday, tlp);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams((int) (100 * d), -2);
        glp.leftMargin = (int) (8 * d);
        head.addView(gear, glp);
        left.addView(head, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout names = new LinearLayout(this);
        String[] dn = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
        for (String n : dn) names.addView(text(n, 11f, 0xB3FFFFFF, true), new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-1, -2);
        nlp.topMargin = (int) (8 * d);
        nlp.bottomMargin = (int) (4 * d);
        left.addView(names, nlp);

        for (int w = 0; w < 6; w++) {
            FrameLayout weekF = new FrameLayout(this);
            weekF.setClipChildren(false);
            left.addView(weekF, new LinearLayout.LayoutParams(-1, 0, 1f));
            LinearLayout week = new LinearLayout(this);
            week.setClipChildren(false);
            weekF.addView(week, new FrameLayout.LayoutParams(-1, -1));
            for (int k = 0; k < 7; k++) {
                final LocalDate day = gridStart.plusDays(w * 7 + k);
                boolean inMonth = day.getMonth() == shown.getMonth();
                boolean holiday = false;
                for (CalendarData.Item it : items) {
                    if (it.date.equals(day) && it.holiday) { holiday = true; break; }
                }
                LinearLayout cell = new LinearLayout(this);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
                cell.setPadding(0, (int) (3 * d), 0, 0);
                boolean isToday = day.equals(today);
                LinearLayout top = new LinearLayout(this);
                top.setGravity(Gravity.CENTER);
                TextView num = text(String.valueOf(day.getDayOfMonth()), 13f,
                        isToday ? 0xFF10151F : !inMonth ? 0x55FFFFFF : holiday ? CalendarData.HOLIDAY_GREEN : 0xFFFFFFFF, inMonth || isToday);
                if (isToday) {
                    GradientDrawable dot = new GradientDrawable();
                    dot.setShape(GradientDrawable.OVAL);
                    dot.setColor(0xFFFFB300);
                    num.setBackground(dot);
                    top.addView(num, new LinearLayout.LayoutParams((int) (22 * d), (int) (22 * d)));
                } else {
                    top.addView(num, new LinearLayout.LayoutParams(-2, (int) (22 * d)));
                }
                cell.addView(top, new LinearLayout.LayoutParams(-1, -2));
                cell.setOnClickListener(v -> select(day));
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, -1, 1f);
                clp.setMargins((int) (2 * d), (int) (2 * d), (int) (2 * d), (int) (2 * d));
                week.addView(cell, clp);
                cells.put(day, cell);
                styleCell(day);
            }
            addWeekBars(weekF, gridStart.plusDays(w * 7));
            if (w < 5) {
                View line = new View(this);
                line.setBackgroundColor(0x1AFFFFFF);
                left.addView(line, new LinearLayout.LayoutParams(-1, Math.max(1, (int) (1 * d))));
            }
        }
        if (headIdx >= 0) enterHeader(headIdx);

        // ----- right: the chosen day, then what is coming
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setClipChildren(false);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(0, -1, 1f);
        rlp.leftMargin = (int) (14 * d);
        page.addView(right, rlp);

        detail = card(22);
        detail.setPadding((int) (16 * d), (int) (12 * d), (int) (16 * d), (int) (12 * d));
        right.addView(detail, new LinearLayout.LayoutParams(-1, 0, 1.1f));
        fillDetail();

        right.addView(upcomingCard("HOLIDAYS COMING UP", true, 4, today), upLp(0.85f));
        right.addView(upcomingCard("GOOGLE CALENDAR", false, 4, today), upLp(0.85f));
    }

    private LinearLayout.LayoutParams upLp(float weight) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 0, weight);
        lp.topMargin = (int) (10 * d);
        return lp;
    }

    /** A selected day wears a glass tile; today an amber ring; the rest nothing. */
    private void styleCell(LocalDate day) {
        View cell = cells.get(day);
        if (cell == null) return;
        if (day.equals(sel)) {
            // Only the chosen day is marked: a soft light fill, nothing else.
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(8 * d);
            g.setColor(0x26FFFFFF);
            cell.setBackground(g);
        } else {
            cell.setBackground(null);
        }
    }

    private void select(LocalDate day) {
        LocalDate old = sel;
        sel = day;
        if (!YearMonth.from(day).equals(shown)) {
            shown = YearMonth.from(day);
            render();
            return;
        }
        styleCell(old);
        styleCell(sel);
        fillDetail();
    }

    private void fillDetail() {
        detail.removeAllViews();
        TextView t = text(sel.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH)), 15f, 0xFFFFFFFF, true);
        t.setGravity(Gravity.START);
        detail.addView(t, new LinearLayout.LayoutParams(-1, -2));
        TextView h = text(hijriFull(sel) + " (approx.)", 11.5f, 0xB3FFFFFF, false);
        h.setGravity(Gravity.START);
        h.setPadding(0, (int) (2 * d), 0, (int) (8 * d));
        detail.addView(h, new LinearLayout.LayoutParams(-1, -2));
        int n = 0;
        for (CalendarData.Item it : items) {
            if (!it.date.equals(sel)) continue;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(text("●", 10f, it.color != 0 ? it.color : (it.holiday ? CalendarData.HOLIDAY_GREEN : 0xFF64B5F6), false), new LinearLayout.LayoutParams((int) (16 * d), -2));
            TextView tt = text(it.title + (it.holiday ? "  (holiday)" : "") + "\n" + whenText(it), 13.5f, 0xFFFFFFFF, true);
            tt.setGravity(Gravity.START);
            tt.setMaxLines(2);
            tt.setEllipsize(android.text.TextUtils.TruncateAt.END);
            row.addView(tt, new LinearLayout.LayoutParams(0, -2, 1f));
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
            rl.bottomMargin = (int) (5 * d);
            detail.addView(row, rl);
            n++;
        }
        if (n == 0) {
            TextView none = text(CalendarData.hasHolidayData(this) || !CalendarData.holidaysOn(this)
                    ? "No holiday or event on this day." : "Holidays are not loaded yet. Check the internet connection.", 12.5f, 0xCCFFFFFF, false);
            none.setGravity(Gravity.START);
            detail.addView(none, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private LinearLayout upcomingCard(String heading, boolean holidays, int max, LocalDate today) {
        LinearLayout c = card(22);
        c.setPadding((int) (14 * d), (int) (8 * d), (int) (14 * d), (int) (8 * d));
        TextView ut = text(heading, 10.5f, 0xB3FFFFFF, true);
        ut.setLetterSpacing(0.06f);
        ut.setGravity(Gravity.START);
        c.addView(ut, new LinearLayout.LayoutParams(-1, -2));
        int shownCount = 0;
        for (CalendarData.Item it : items) {
            if (it.holiday != holidays || it.date.isBefore(today)) continue;
            if (shownCount >= max) break;
            c.addView(upRow(it, today), new LinearLayout.LayoutParams(-1, 0, 1f));
            shownCount++;
        }
        if (shownCount == 0) {
            String msg;
            if (holidays) msg = !CalendarData.holidaysOn(this) ? "Malaysia holidays are off (Settings)." : "Nothing loaded yet.";
            else if (CalendarData.myUrl(this).isEmpty()) msg = "Not linked. Open Settings, then link with the phone QR.";
            else if (!CalendarData.myOn(this)) msg = "Your calendar is switched off (Settings).";
            else msg = "Nothing coming up.";
            TextView none = text(msg, 12f, 0xCCFFFFFF, false);
            none.setGravity(Gravity.START);
            c.addView(none, new LinearLayout.LayoutParams(-1, 0, 1f));
        }
        return c;
    }

    private View upRow(CalendarData.Item it, LocalDate today) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView dt = text(it.date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)), 11f, 0xB3FFFFFF, true);
        dt.setGravity(Gravity.START);
        row.addView(dt, new LinearLayout.LayoutParams((int) (66 * d), -2));
        TextView ti = text((it.time.isEmpty() ? "" : it.time + "  ") + it.title, 12f, 0xFFFFFFFF, true);
        ti.setGravity(Gravity.START);
        ti.setSingleLine(true);
        ti.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(ti, new LinearLayout.LayoutParams(0, -2, 1f));
        long n = ChronoUnit.DAYS.between(today, it.date);
        TextView when = text(n == 0 ? "Today" : n == 1 ? "Tomorrow" : n + " d", 11f, n <= 1 ? 0xFFFFB300 : 0xCCFFFFFF, true);
        when.setGravity(Gravity.END);
        row.addView(when, new LinearLayout.LayoutParams((int) (56 * d), -2));
        return row;
    }

    // ---------------------------------------------------------------- keys

    /**
     * Arrows pick the day: left and right by one day, up and down by a week. Up from the top week goes to the buttons
     * (previous month, Today, next month, Settings); left and right move between them, down goes back to the days, OK presses.
     * Channel up/down, page up/down and rewind/forward change the month from anywhere.
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        if (k == KeyEvent.KEYCODE_PAGE_UP || k == KeyEvent.KEYCODE_CHANNEL_UP || k == KeyEvent.KEYCODE_MEDIA_REWIND || k == KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
            if (down) moveMonth(-1);
            return true;
        }
        if (k == KeyEvent.KEYCODE_PAGE_DOWN || k == KeyEvent.KEYCODE_CHANNEL_DOWN || k == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD || k == KeyEvent.KEYCODE_MEDIA_NEXT) {
            if (down) moveMonth(1);
            return true;
        }
        boolean arrow = k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT
                || k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN;
        if (headIdx >= 0) {
            if (arrow) {
                if (down) {
                    if (k == KeyEvent.KEYCODE_DPAD_LEFT) enterHeader(headIdx - 1);
                    else if (k == KeyEvent.KEYCODE_DPAD_RIGHT) enterHeader(headIdx + 1);
                    else if (k == KeyEvent.KEYCODE_DPAD_DOWN) leaveHeader();
                }
                return true;
            }
            if ((k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER) && e.getAction() == KeyEvent.ACTION_UP) {
                headActs[headIdx].run();
                return true;
            }
            return super.dispatchKeyEvent(e);
        }
        if (arrow) {
            if (down) {
                LocalDate first = shown.atDay(1);
                LocalDate gridStart = first.minusDays(first.getDayOfWeek().getValue() % 7);
                if (k == KeyEvent.KEYCODE_DPAD_LEFT) select(sel.minusDays(1));
                else if (k == KeyEvent.KEYCODE_DPAD_RIGHT) select(sel.plusDays(1));
                else if (k == KeyEvent.KEYCODE_DPAD_DOWN) select(sel.plusDays(7));
                else if (!sel.isBefore(gridStart) && sel.isBefore(gridStart.plusDays(7))) enterHeader(1);
                else select(sel.minusDays(7));
            }
            return true;
        }
        if ((k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER) && e.getAction() == KeyEvent.ACTION_UP) {
            showDay(sel);
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    // ---------------------------------------------------------------- dialogs

    private void showDay(LocalDate day) {
        StringBuilder sb = new StringBuilder();
        for (CalendarData.Item it : items) {
            if (!it.date.equals(day)) continue;
            sb.append(it.holiday ? "Holiday: " : "").append(it.title).append("  (").append(whenText(it)).append(")\n");
        }
        if (sb.length() == 0) sb.append("Nothing on this day.");
        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle(day.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH)))
                .setMessage(sb.toString().trim() + "\n" + hijriFull(day) + " (approx.)")
                .setPositiveButton("Close", null)
                .create();
        dlg.show();
        glassDialog(dlg);
    }

    private void glassDialog(android.app.Dialog dlg) {
        try {
            android.view.Window w = dlg.getWindow();
            if (w == null) return;
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
            View panel = null;
            int id = getResources().getIdentifier("parentPanel", "id", "android");
            if (id != 0) panel = w.findViewById(id);
            if (panel == null) panel = w.findViewById(android.R.id.content);
            if (panel != null) GlassPage.glass(panel, d, 26, false);
        } catch (Throwable ignored) { }
    }

    /** The calendars and the Google link, kept off the page itself. */
    private void showSettings() {
        final boolean hol = CalendarData.holidaysOn(this);
        final boolean my = CalendarData.myOn(this);
        final boolean linked = !CalendarData.myUrl(this).isEmpty();
        final List<String> rows = new ArrayList<>();
        final List<Runnable> acts = new ArrayList<>();
        rows.add("Malaysia holidays:  " + (hol ? "On" : "Off"));
        acts.add(() -> {
            CalendarData.setHolidaysOn(this, !hol);
            CalendarData.refresh(this, false, () -> runOnUiThread(() -> { if (!isDestroyed()) render(); }));
            render();
            showSettings();
        });
        for (final CalendarData.Source src : CalendarData.sources(this)) {
            rows.add(src.name + ":  " + (src.on ? "On" : "Off"));
            acts.add(() -> { CalendarData.setSourceOn(this, src.url, !src.on); render(); showSettings(); });
            int ci = 0;
            for (int q = 0; q < CalendarData.PALETTE.length; q++) if (CalendarData.PALETTE[q] == src.color) ci = q;
            final int next = (ci + 1) % CalendarData.PALETTE.length;
            rows.add("     Colour of " + src.name + ":  " + CalendarData.PALETTE_NAMES[ci] + "  (press to change)");
            acts.add(() -> { CalendarData.setSourceColor(this, src.url, CalendarData.PALETTE[next]); render(); showSettings(); });
        }
        rows.add(linked ? "Change link with the phone (QR)" : "Add link with the phone (QR)");
        acts.add(this::showLinkQr);
        rows.add("Type the link here");
        acts.add(this::editLink);
        if (linked) {
            rows.add("Remove the link");
            acts.add(() -> { CalendarData.setMyUrl(this, ""); render(); });
        }
        rows.add("Refresh now");
        acts.add(() -> {
            Toast.makeText(this, "Refreshing...", Toast.LENGTH_SHORT).show();
            CalendarData.refresh(this, true, () -> runOnUiThread(() -> { if (!isDestroyed()) render(); }));
        });
        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("Calendar settings")
                .setItems(rows.toArray(new String[0]), (x, which) -> acts.get(which).run())
                .setNegativeButton("Close", null)
                .create();
        dlg.show();
        glassDialog(dlg);
    }

    /** A QR code: the phone opens a page on the home network, pastes the secret address, and the TV saves it. */
    private void showLinkQr() {
        stopServer();
        String ip = WallpaperUploadServer.getLocalIpAddress();
        server = new LinkServer(this, 8090, () -> runOnUiThread(() -> {
            Toast.makeText(this, "Google Calendar linked", Toast.LENGTH_LONG).show();
            if (qrDialog != null) qrDialog.dismiss();
            if (!isDestroyed()) render();
        }));
        if (!server.start()) {
            server = null;
            Toast.makeText(this, "Could not start the link page on the TV.", Toast.LENGTH_LONG).show();
            return;
        }
        String url = "http://" + ip + ":" + server.getPort();
        Bitmap qr = QrBitmapUtil.createQrBitmap(url, (int) (240 * d));
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding((int) (24 * d), (int) (10 * d), (int) (24 * d), (int) (4 * d));
        TextView help = text("On the phone (same Wi-Fi as the TV) scan this, or type the address below into the phone browser. The page explains where to find your Google Calendar secret address.", 13f, 0xE6FFFFFF, false);
        help.setPadding(0, 0, 0, (int) (10 * d));
        box.addView(help);
        FrameLayout white = new FrameLayout(this);
        white.setBackgroundColor(0xFFFFFFFF);
        int pad = (int) (10 * d);
        white.setPadding(pad, pad, pad, pad);
        ImageView iv = new ImageView(this);
        if (qr != null) iv.setImageBitmap(qr);
        white.addView(iv, new FrameLayout.LayoutParams((int) (200 * d), (int) (200 * d)));
        box.addView(white, new LinearLayout.LayoutParams(-2, -2));
        TextView link = text(url, 14f, 0xFFFFB347, true);
        link.setPadding(0, (int) (8 * d), 0, 0);
        box.addView(link);
        qrDialog = new AlertDialog.Builder(this)
                .setTitle("Link Google Calendar")
                .setView(box)
                .setNegativeButton("Close", null)
                .create();
        qrDialog.setOnDismissListener(x -> stopServer());
        qrDialog.show();
        glassDialog(qrDialog);
    }

    /** Paste the secret iCal address (Google Calendar > Settings > the calendar > Integrate calendar). Empty removes the link. */
    private void editLink() {
        final EditText in = new EditText(this);
        in.setSingleLine(true);
        in.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        in.setHint("https://calendar.google.com/calendar/ical/.../basic.ics");
        in.setText(CalendarData.myUrl(this).replace('\n', ' '));
        in.setTextColor(0xFFFFFFFF);
        in.setHintTextColor(0x80FFFFFF);
        in.setTextSize(14f);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding((int) (24 * d), (int) (8 * d), (int) (24 * d), 0);
        TextView help = text("Google Calendar, Settings, your calendar, Integrate calendar: copy \"Secret address in iCal format\". It is read only; keep it private.", 12.5f, 0xCCFFFFFF, false);
        help.setGravity(Gravity.START);
        help.setPadding(0, 0, 0, (int) (10 * d));
        box.addView(help);
        box.addView(in, new LinearLayout.LayoutParams(-1, -2));
        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("Google Calendar link")
                .setView(box)
                .setPositiveButton("Save", (x, w) -> {
                    boolean ok = CalendarData.setMyUrl(this, in.getText().toString());
                    if (!ok) { Toast.makeText(this, "That is not a web address.", Toast.LENGTH_SHORT).show(); return; }
                    CalendarData.setMyOn(this, true);
                    CalendarData.refresh(this, true, () -> runOnUiThread(() -> { if (!isDestroyed()) render(); }));
                    render();
                })
                .setNegativeButton("Cancel", null)
                .create();
        dlg.show();
        glassDialog(dlg);
    }
}
