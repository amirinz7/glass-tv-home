package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Selangor FC and Manchester United from TheSportsDB (free key): the last result, the next fixture
 * and the team's place in its league table. Cached for a few hours so the free quota is never an issue.
 */
final class SportsNow {
    private static final long FRESH_MS = 3 * 60 * 60_000L;
    private static final long RETRY_MS = 10 * 60_000L;
    private static final int FORMAT = 8;
    private static final String BASE = "https://www.thesportsdb.com/api/v1/json/123/";
    /** TheSportsDB team ids: Selangor FC, Manchester United. */
    private static final String[][] TEAMS = {{"139334", "Selangor FC"}, {"133612", "Man United"}};
    private static volatile boolean loading;

    /** One team's card: everything is already worded, so the screen only has to show it. */
    static final class Team {
        final String name, league, table, record, last, next, badge, stadium;
        /** W, D, L or empty: colours the last result. */
        final String outcome;
        Team(String name, String league, String table, String record, String last, String outcome, String next, String badge, String stadium) {
            this.name = name; this.league = league; this.table = table; this.record = record;
            this.last = last; this.outcome = outcome; this.next = next; this.badge = badge; this.stadium = stadium;
        }
    }

    interface Callback { void done(List<Team> teams); }

    private SportsNow() {}

    static void load(Context ctx, Callback cb) {
        SharedPreferences p = ctx.getApplicationContext().getSharedPreferences("sports", Context.MODE_PRIVATE);
        futsalCached = p.getString("futsal_id", null);
        final List<Team> cached = p.getInt("teams_format", 0) == FORMAT ? parse(p.getString("teams", null)) : new ArrayList<Team>();
        long now = System.currentTimeMillis();
        boolean isFresh = !cached.isEmpty() && now - p.getLong("teams_at", 0) < FRESH_MS;
        boolean cooling = now - p.getLong("teams_try", 0) < RETRY_MS;
        if (isFresh || cooling) {
            cb.done(cached);
            return;
        }
        synchronized (SportsNow.class) {
            if (loading) {
                cb.done(cached);
                return;
            }
            loading = true;
        }
        p.edit().putLong("teams_try", now).apply();
        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            List<Team> result = cached;
            try {
                List<Team> got = new ArrayList<>();
                for (String[] t : TEAMS) {
                    try { got.add(build(t[0], t[1])); }
                    catch (Exception e) { android.util.Log.w("TvHome.Sports", t[1] + " failed: " + e); }
                }
                try {
                    String fid = futsalId();
                    if (fid != null) {
                        p.edit().putString("futsal_id", fid).apply();
                        got.add(build(fid, "Selangor Futsal"));
                    }
                } catch (Exception e) { android.util.Log.w("TvHome.Sports", "futsal failed: " + e); }
                try { got.add(famCard()); }
                catch (Exception e) { android.util.Log.w("TvHome.Sports", "Malaysia failed: " + e); }
                if (!got.isEmpty()) {
                    result = got;
                    p.edit().putString("teams", toJson(got)).putInt("teams_format", FORMAT)
                            .putLong("teams_at", System.currentTimeMillis()).apply();
                }
            } catch (Exception e) {
                android.util.Log.w("TvHome.Sports", "refresh failed: " + e.getClass().getSimpleName());
            } finally {
                loading = false;
            }
            final List<Team> out = result;
            main.post(() -> cb.done(out));
        }, "football").start();
    }

    private static Team build(String id, String shortName) throws Exception {
        String leagueId = "", leagueName = "", badge = "", stadium = "";
        try {
            JSONArray teams = get(BASE + "lookupteam.php?id=" + id).optJSONArray("teams");
            if (teams != null && teams.length() > 0) {
                leagueId = clean(teams.getJSONObject(0).optString("idLeague", ""));
                leagueName = clean(teams.getJSONObject(0).optString("strLeague", ""));
                badge = clean(teams.getJSONObject(0).optString("strBadge", teams.getJSONObject(0).optString("strTeamBadge", "")));
                stadium = clean(teams.getJSONObject(0).optString("strStadium", ""));
            }
        } catch (Exception e) { android.util.Log.w("TvHome.Sports", "team lookup failed: " + e); }

        String last = "", outcome = "", next = "";
        try {
            JSONObject best = null;
            JSONArray results = get(BASE + "eventslast.php?id=" + id).optJSONArray("results");
            for (int i = 0; results != null && i < results.length(); i++) {
                JSONObject e = results.getJSONObject(i);
                if (clean(e.optString("intHomeScore", "")).isEmpty()) continue;
                if (best == null || clean(e.optString("dateEvent", "")).compareTo(clean(best.optString("dateEvent", ""))) > 0) best = e;
            }
            if (best != null) {
                String home = clean(best.optString("strHomeTeam", "")), away = clean(best.optString("strAwayTeam", ""));
                int hs = Integer.parseInt(clean(best.optString("intHomeScore", "0")));
                int as = Integer.parseInt(clean(best.optString("intAwayScore", "0")));
                boolean isHome = clean(best.optString("idHomeTeam", "")).equals(id);
                int mine = isHome ? hs : as, theirs = isHome ? as : hs;
                outcome = mine > theirs ? "W" : mine == theirs ? "D" : "L";
                last = outcome + " " + mine + "–" + theirs + " " + (isHome ? "vs " + away : "at " + home);
                if (leagueName.isEmpty()) leagueName = clean(best.optString("strLeague", ""));
                if (leagueId.isEmpty()) leagueId = clean(best.optString("idLeague", ""));
            }
        } catch (Exception e) { android.util.Log.w("TvHome.Sports", "last result failed: " + e); }

        try {
            JSONObject soonest = null;
            JSONArray events = get(BASE + "eventsnext.php?id=" + id).optJSONArray("events");
            for (int i = 0; events != null && i < events.length(); i++) {
                JSONObject e = events.getJSONObject(i);
                if (soonest == null || clean(e.optString("dateEvent", "")).compareTo(clean(soonest.optString("dateEvent", ""))) < 0) soonest = e;
            }
            if (soonest != null) {
                String home = clean(soonest.optString("strHomeTeam", "")), away = clean(soonest.optString("strAwayTeam", ""));
                boolean isHome = clean(soonest.optString("idHomeTeam", "")).equals(id);
                next = kickoff(soonest) + " · " + (isHome ? "vs " + away : "at " + home);
                String venue = clean(soonest.optString("strVenue", ""));
                if (!venue.isEmpty()) stadium = venue;
                else if (!isHome) stadium = "";
            }
        } catch (Exception e) { android.util.Log.w("TvHome.Sports", "next fixture failed: " + e); }

        String table = "", record = "";
        if (!leagueId.isEmpty()) {
            for (String season : seasons()) {
                try {
                    JSONArray rows = get(BASE + "lookuptable.php?l=" + leagueId + "&s=" + season).optJSONArray("table");
                    if (rows == null) continue;
                    for (int i = 0; i < rows.length(); i++) {
                        JSONObject r = rows.getJSONObject(i);
                        if (!id.equals(clean(r.optString("idTeam", "")))) continue;
                        table = "#" + r.optInt("intRank") + " · " + r.optInt("intPoints") + " pts";
                        int gd = r.optInt("intGoalDifference");
                        record = "P" + r.optInt("intPlayed") + "  W" + r.optInt("intWin") + "  D" + r.optInt("intDraw")
                                + "  L" + r.optInt("intLoss") + "  GD " + (gd > 0 ? "+" : "") + gd;
                        break;
                    }
                    if (!table.isEmpty()) break;
                } catch (Exception e) { android.util.Log.w("TvHome.Sports", "table " + season + " failed: " + e); }
            }
        }
        // ESPN knows the whole 2026-27 fixture list: use it for the latest result and the next match, with its date.
        String[] esp = espnFor(id);
        if (esp != null) {
            try {
                List<String[]> all = espnSchedule(esp);
                String nowIso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).format(new Date(System.currentTimeMillis() - 3L * 3600_000L));
                String[] lastM = null, nextM = null;
                for (String[] m : all) {
                    if (!m[2].isEmpty()) lastM = m;
                    else if (nextM == null && m.length > 4 && m[4].compareTo(nowIso) >= 0) nextM = m;
                }
                if (lastM != null) { last = lastM[2] + " " + lastM[1]; outcome = lastM[2].substring(0, 1); }
                if (nextM != null) {
                    next = nextM[0] + " · " + nextM[1];
                    if (!nextM[3].isEmpty()) stadium = nextM[3];
                }
            } catch (Exception ex) { android.util.Log.w("TvHome.Sports", "espn card failed: " + ex); }
            try {
                Standing st = standings(id);
                for (String[] r : st.rows) {
                    if (r[10].equals(esp[2])) {
                        table = "#" + r[0] + " · " + r[9] + " pts";
                        record = "P" + r[2] + "  W" + r[3] + "  D" + r[4] + "  L" + r[5] + "  GD " + r[8];
                    }
                }
            } catch (Exception ignored) { }
        }
        if ("139334".equals(id)) {
            try {
                String nowIso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).format(new Date(System.currentTimeMillis() - 3L * 3600_000L));
                String lastAll = "", outAll = "", nextAll = "", venueAll = "";
                for (String[] r : schedule(id)) {
                    if (r.length < 5) continue;
                    if (!r[2].isEmpty()) { lastAll = r[2] + " " + r[1]; outAll = r[2].substring(0, 1); }
                    else if (nextAll.isEmpty() && r[4].compareTo(nowIso) >= 0) { nextAll = r[0] + " · " + r[1]; venueAll = r[3]; }
                }
                if (!lastAll.isEmpty()) { last = lastAll; outcome = outAll; }
                if (!nextAll.isEmpty()) { next = nextAll; if (!venueAll.isEmpty()) stadium = venueAll; }
            } catch (Exception ex) { android.util.Log.w("TvHome.Sports", "all competitions card failed: " + ex); }
        }
        return new Team(shortName, leagueName, table, record, last, outcome, next, badge, stadium);
    }

    /** A league table: name and one row per club: rank, club, P, W, D, L, GF, GA, GD, Pts, team id, badge. Blocking. */
    static final class Standing {
        String league = "";
        /** The season the table is for, e.g. "2026-27". */
        String season = "";
        /** The id of the club being viewed, as it appears in the rows (column 10). */
        String mine = "";
        final List<String[]> rows = new ArrayList<>();
    }

    private static final String ESPN_V2 = "https://site.api.espn.com/apis/v2/sports/soccer/";
    private static final String ESPN_SITE = "https://site.api.espn.com/apis/site/v2/sports/soccer/";
    /** TheSportsDB team id, ESPN league, ESPN team id. */
    private static final String[][] ESPN = {{"139334", "mys.1", "17739"}, {"133612", "eng.1", "360"}};

    private static String[] espnFor(String id) {
        for (String[] e : ESPN) if (e[0].equals(id)) return e;
        return null;
    }

    private static double stat(java.util.Map<String, Double> m, double fallback, String... names) {
        for (String n : names) if (m.containsKey(n)) return m.get(n);
        return fallback;
    }

    /** The whole league table from ESPN: every club with P W D L GF GA GD Pts. */
    private static Standing espnStandings(String[] e) throws Exception {
        Standing st = new Standing();
        st.mine = e[2];
        JSONObject o = null;
        for (String q : new String[]{"?season=" + seasonYear(), "", "?season=" + (seasonYear() - 1)}) {
            try {
                JSONObject tryO = get(ESPN_V2 + e[1] + "/standings" + q);
                JSONArray ch = tryO.optJSONArray("children");
                boolean has = ch != null && ch.length() > 0 && ch.getJSONObject(0).optJSONObject("standings") != null
                        && ch.getJSONObject(0).getJSONObject("standings").optJSONArray("entries") != null
                        && ch.getJSONObject(0).getJSONObject("standings").getJSONArray("entries").length() > 0;
                if (!has && tryO.optJSONObject("standings") != null) has = true;
                if (o == null) o = tryO;
                if (has) { o = tryO; break; }
            } catch (Exception ex) { android.util.Log.w("TvHome.Sports", "standings " + q + " failed: " + ex); }
        }
        if (o == null) throw new Exception("no standings");
        JSONObject std = null;
        JSONArray children = o.optJSONArray("children");
        if (children != null && children.length() > 0) {
            st.league = clean(children.getJSONObject(0).optString("name", ""));
            std = children.getJSONObject(0).optJSONObject("standings");
        }
        if (std == null) std = o.optJSONObject("standings");
        if (std == null) return st;
        st.season = clean(std.optString("seasonDisplayName", std.optString("season", "")));
        if (st.league.isEmpty()) st.league = clean(o.optString("name", ""));
        // A league can come as several groups: take every group's clubs, each once.
        JSONArray entries = new JSONArray();
        java.util.Set<String> seen = new java.util.HashSet<>();
        int groups = 0;
        java.util.List<JSONObject> stds = new ArrayList<>();
        stds.add(std);
        for (int g = 1; children != null && g < children.length(); g++) {
            JSONObject other = children.getJSONObject(g).optJSONObject("standings");
            if (other != null) stds.add(other);
        }
        for (JSONObject one : stds) {
            JSONArray part = one.optJSONArray("entries");
            if (part == null || part.length() == 0) continue;
            groups++;
            for (int q = 0; q < part.length(); q++) {
                JSONObject en = part.getJSONObject(q);
                JSONObject tm = en.optJSONObject("team");
                String tid = tm == null ? "" : clean(tm.optString("id", ""));
                if (!tid.isEmpty() && !seen.add(tid)) continue;
                entries.put(en);
            }
        }
        final boolean grouped = groups > 1;
        for (int i = 0; entries != null && i < entries.length(); i++) {
            JSONObject en = entries.getJSONObject(i);
            JSONObject team = en.optJSONObject("team");
            if (team == null) continue;
            java.util.Map<String, Double> m = new java.util.HashMap<>();
            JSONArray stats = en.optJSONArray("stats");
            for (int k = 0; stats != null && k < stats.length(); k++) {
                JSONObject sj = stats.getJSONObject(k);
                double v = sj.optDouble("value", Double.NaN);
                if (Double.isNaN(v)) continue;
                m.put(sj.optString("name", ""), v);
                m.put(sj.optString("abbreviation", ""), v);
            }
            String badge = "";
            JSONArray logos = team.optJSONArray("logos");
            if (logos != null && logos.length() > 0) badge = clean(logos.getJSONObject(0).optString("href", ""));
            st.rows.add(new String[]{
                    String.valueOf((int) stat(m, i + 1, "rank")), clean(team.optString("displayName", team.optString("name", ""))),
                    String.valueOf((int) stat(m, 0, "gamesPlayed", "GP")), String.valueOf((int) stat(m, 0, "wins", "W")),
                    String.valueOf((int) stat(m, 0, "ties", "draws", "D")), String.valueOf((int) stat(m, 0, "losses", "L")),
                    String.valueOf((int) stat(m, 0, "pointsFor", "F")), String.valueOf((int) stat(m, 0, "pointsAgainst", "A")),
                    String.valueOf((int) stat(m, 0, "pointDifferential", "GD")), String.valueOf((int) stat(m, 0, "points", "P")),
                    clean(team.optString("id", "")), badge});
        }
        java.util.Collections.sort(st.rows, (x, y) -> {
            if (grouped) {
                int pts = Integer.parseInt(y[9]) - Integer.parseInt(x[9]);
                return pts != 0 ? pts : Integer.parseInt(y[8]) - Integer.parseInt(x[8]);
            }
            int c = Integer.parseInt(x[0]) - Integer.parseInt(y[0]);
            return c != 0 ? c : Integer.parseInt(y[9]) - Integer.parseInt(x[9]);
        });
        if (grouped) for (int i = 0; i < st.rows.size(); i++) st.rows.get(i)[0] = String.valueOf(i + 1);
        return st;
    }

    static Standing standings(String teamId) {
        if (FAM_ID.equals(teamId)) { Standing none = new Standing(); none.mine = teamId; none.league = "Malaysia men's national team"; return none; }
        String[] e = espnFor(teamId);
        if (e != null) {
            try {
                Standing st = espnStandings(e);
                // ESPN can be on last season, or know only some of the clubs: look at the other source too and keep the fuller table.
                boolean current = st.season.isEmpty() || st.season.contains(String.valueOf(seasonYear()));
                if (!st.rows.isEmpty() && current && st.rows.size() >= 10) return st;
                Standing other = sportsDbStandings(teamId);
                other.mine = teamId;
                boolean otherCurrent = other.season.isEmpty() || other.season.contains(String.valueOf(seasonYear()));
                if (!other.rows.isEmpty() && otherCurrent && (other.rows.size() > st.rows.size() || !current)) return other;
                if (!st.rows.isEmpty()) return st;
            } catch (Exception ex) { android.util.Log.w("TvHome.Sports", "espn table failed: " + ex); }
        }
        Standing st = sportsDbStandings(teamId);
        st.mine = teamId;
        return st;
    }

    private static Standing sportsDbStandings(String teamId) {
        Standing st = new Standing();
        try {
            JSONArray teams = get(BASE + "lookupteam.php?id=" + teamId).optJSONArray("teams");
            if (teams == null || teams.length() == 0) return st;
            String leagueId = clean(teams.getJSONObject(0).optString("idLeague", ""));
            st.league = clean(teams.getJSONObject(0).optString("strLeague", ""));
            if (leagueId.isEmpty()) return st;
            for (String season : seasons()) {
                JSONArray rows = null;
                try { rows = get(BASE + "lookuptable.php?l=" + leagueId + "&s=" + season).optJSONArray("table"); } catch (Exception ignored) { }
                if (rows == null || rows.length() == 0) continue;
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject r = rows.getJSONObject(i);
                    st.rows.add(new String[]{
                            String.valueOf(r.optInt("intRank")), clean(r.optString("strTeam", "")),
                            String.valueOf(r.optInt("intPlayed")), String.valueOf(r.optInt("intWin")),
                            String.valueOf(r.optInt("intDraw")), String.valueOf(r.optInt("intLoss")),
                            String.valueOf(r.optInt("intGoalsFor")), String.valueOf(r.optInt("intGoalsAgainst")),
                            String.valueOf(r.optInt("intGoalDifference")), String.valueOf(r.optInt("intPoints")),
                            clean(r.optString("idTeam", "")), clean(r.optString("strBadge", r.optString("strTeamBadge", "")))});
                }
                java.util.Collections.sort(st.rows, (x, y) -> Integer.parseInt(x[0]) - Integer.parseInt(y[0]));
                st.season = season;
                break;
            }
        } catch (Exception e) { android.util.Log.w("TvHome.Sports", "standings failed: " + e); }
        return st;
    }

    /** Id of a team by its short name, or null. */
    /** The club crest (ESPN) for a TheSportsDB team id, or "" when unknown. */
    static String logoUrl(String tsdbId) {
        String[] e = espnFor(tsdbId);
        return e == null ? "" : "https://a.espncdn.com/i/teamlogos/soccer/500/" + e[2] + ".png";
    }

    static String idFor(String shortName) {
        if ("Malaysia".equalsIgnoreCase(shortName)) return FAM_ID;
        if ("Selangor Futsal".equalsIgnoreCase(shortName)) return futsalCached;
        for (String[] t : TEAMS) if (t[1].equalsIgnoreCase(shortName)) return t[0];
        String low = shortName == null ? "" : shortName.toLowerCase(Locale.ROOT);
        for (String[] t : TEAMS) if (low.contains(t[1].toLowerCase(Locale.ROOT).split(" ")[0])) return t[0];
        return null;
    }

    private static String isoKickoff(String iso) {
        String[] patterns = {"yyyy-MM-dd'T'HH:mm'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'"};
        for (String pat : patterns) {
            try {
                SimpleDateFormat in = new SimpleDateFormat(pat, Locale.US);
                in.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date d = in.parse(iso);
                SimpleDateFormat out = new SimpleDateFormat("EEE d MMM, HH:mm", Locale.ENGLISH);
                out.setTimeZone(TimeZone.getTimeZone("Asia/Kuala_Lumpur"));
                return out.format(d);
            } catch (Exception ignored) { }
        }
        return iso;
    }

    private static String espnScore(JSONObject competitor) {
        Object sc = competitor.opt("score");
        String v = "";
        if (sc instanceof JSONObject) v = clean(((JSONObject) sc).optString("displayValue", ((JSONObject) sc).optString("value", "")));
        else if (sc != null) v = clean(String.valueOf(sc));
        if (v.endsWith(".0")) v = v.substring(0, v.length() - 2);
        return v;
    }

    /** Every match of the club's season from ESPN, played and still to come, oldest first. */
    private static List<String[]> espnSchedule(String[] e) throws Exception {
        java.util.Map<String, String[]> byDate = new java.util.TreeMap<>();
        // Played matches and the ones still to come are separate lists on ESPN: read both for the 2026-27 season.
        JSONArray events = new JSONArray();
        for (String extra : new String[]{"", "&fixture=true"}) {
            try {
                JSONArray part = get(ESPN_SITE + e[1] + "/teams/" + e[2] + "/schedule?season=" + seasonYear() + extra).optJSONArray("events");
                for (int q = 0; part != null && q < part.length(); q++) events.put(part.get(q));
            } catch (Exception ex) { android.util.Log.w("TvHome.Sports", "espn schedule part failed: " + ex); }
        }
        // The club's own list can stop short (fixtures are announced in batches): the league's whole season by date adds the rest.
        try {
            int y = seasonYear();
            JSONArray board = get(ESPN_SITE + e[1] + "/scoreboard?dates=" + y + "0701-" + (y + 1) + "0630&limit=1000").optJSONArray("events");
            for (int q = 0; board != null && q < board.length(); q++) events.put(board.get(q));
        } catch (Exception ex) { android.util.Log.w("TvHome.Sports", "espn season board failed: " + ex); }
        for (int i = 0; events != null && i < events.length(); i++) {
            JSONObject ev = events.getJSONObject(i);
            JSONArray comps = ev.optJSONArray("competitions");
            if (comps == null || comps.length() == 0) continue;
            JSONObject c = comps.getJSONObject(0);
            JSONArray cs = c.optJSONArray("competitors");
            JSONObject me = null, opp = null;
            for (int k = 0; cs != null && k < cs.length(); k++) {
                JSONObject t = cs.getJSONObject(k);
                JSONObject team = t.optJSONObject("team");
                if (team != null && e[2].equals(clean(team.optString("id", "")))) me = t; else opp = t;
            }
            if (me == null || opp == null) continue;
            boolean home = "home".equalsIgnoreCase(me.optString("homeAway", ""));
            String oppName = "";
            JSONObject oppTeam = opp.optJSONObject("team");
            if (oppTeam != null) oppName = clean(oppTeam.optString("displayName", oppTeam.optString("name", "")));
            boolean done = false;
            JSONObject status = c.optJSONObject("status");
            if (status != null && status.optJSONObject("type") != null) done = status.getJSONObject("type").optBoolean("completed", false);
            String res = "";
            String a = espnScore(me), b = espnScore(opp);
            if (done && !a.isEmpty() && !b.isEmpty()) {
                try {
                    int x = Integer.parseInt(a), y = Integer.parseInt(b);
                    res = (x > y ? "W " : x == y ? "D " : "L ") + x + "–" + y;
                } catch (Exception ignored) { }
            }
            String venue = "";
            if (c.optJSONObject("venue") != null) venue = clean(c.getJSONObject("venue").optString("fullName", ""));
            String date = clean(ev.optString("date", ""));
            byDate.put(date + clean(ev.optString("id", "")), new String[]{isoKickoff(date), (home ? "vs " : "at ") + oppName, res, venue, date});
        }
        return new ArrayList<>(byDate.values());
    }

    /** One line per match of the season, oldest first: "Sat 9 Aug, 20:00  ·  vs Arsenal  ·  2–1" (blocking: call off the main thread). */
    static List<String[]> schedule(String id) {
        if (FAM_ID.equals(id)) return mergeByDay(famSchedule(), sofaSchedule("Malaysia", "malaysia", "Malaysia"));
        if ("139334".equals(id)) return mergeByDay(withAllCompetitions(id, scheduleLeague(id)), sofaSchedule("Selangor", "selangor", "Malaysia"));
        return scheduleLeague(id);
    }

    private static List<String[]> scheduleLeague(String id) {
        String[] esp = espnFor(id);
        if (esp != null) {
            try {
                List<String[]> all = espnSchedule(esp);
                // A whole season is at least twenty matches: when ESPN has fewer, the other source may know more.
                if (all.size() >= 20) return all;
                List<String[]> other = sportsDbSchedule(id);
                return other.size() > all.size() ? other : all;
            } catch (Exception ex) { android.util.Log.w("TvHome.Sports", "espn schedule failed: " + ex); }
        }
        return sportsDbSchedule(id);
    }

    private static List<String[]> sportsDbSchedule(String id) {
        List<String[]> rows = new ArrayList<>();
        java.util.Map<String, JSONObject> byEvent = new java.util.TreeMap<>();
        try {
            String leagueId = "";
            JSONArray teams = get(BASE + "lookupteam.php?id=" + id).optJSONArray("teams");
            if (teams != null && teams.length() > 0) leagueId = clean(teams.getJSONObject(0).optString("idLeague", ""));
            if (!leagueId.isEmpty()) {
                for (String season : seasons()) {
                    JSONArray ev = null;
                    try { ev = get(BASE + "eventsseason.php?id=" + leagueId + "&s=" + season).optJSONArray("events"); } catch (Exception ignored) { }
                    if (ev == null) continue;
                    for (int i = 0; i < ev.length(); i++) {
                        JSONObject e = ev.getJSONObject(i);
                        if (id.equals(clean(e.optString("idHomeTeam", ""))) || id.equals(clean(e.optString("idAwayTeam", ""))))
                            byEvent.put(clean(e.optString("dateEvent", "")) + clean(e.optString("idEvent", "")), e);
                    }
                    if (!byEvent.isEmpty()) break;
                }
            }
        } catch (Exception e) { android.util.Log.w("TvHome.Sports", "season failed: " + e); }
        // The free key may cap the season list: always add the last and next matches too.
        try {
            for (String path : new String[]{"eventslast.php?id=", "eventsnext.php?id="}) {
                JSONObject o = get(BASE + path + id);
                JSONArray ev = o.optJSONArray(path.startsWith("eventslast") ? "results" : "events");
                for (int i = 0; ev != null && i < ev.length(); i++) {
                    JSONObject e = ev.getJSONObject(i);
                    byEvent.put(clean(e.optString("dateEvent", "")) + clean(e.optString("idEvent", "")), e);
                }
            }
        } catch (Exception e) { android.util.Log.w("TvHome.Sports", "last/next failed: " + e); }
        for (JSONObject e : byEvent.values()) {
            String home = clean(e.optString("strHomeTeam", "")), away = clean(e.optString("strAwayTeam", ""));
            boolean isHome = clean(e.optString("idHomeTeam", "")).equals(id);
            String hs = clean(e.optString("intHomeScore", "")), as = clean(e.optString("intAwayScore", ""));
            String score = hs.isEmpty() || as.isEmpty() ? "" : (isHome ? hs + "–" + as : as + "–" + hs);
            String res = "";
            if (!score.isEmpty()) {
                try {
                    int mine = Integer.parseInt(score.split("–")[0]), theirs = Integer.parseInt(score.split("–")[1]);
                    res = mine > theirs ? "W " : mine == theirs ? "D " : "L ";
                } catch (Exception ignored) { }
            }
            rows.add(new String[]{kickoff(e), (isHome ? "vs " + away : "at " + home), res + score,
                    clean(e.optString("strVenue", ""))});
        }
        return rows;
    }

    /** Season names to try for the league table: European style (Aug to May) first, then calendar year. */
    /** The season is named by the year it starts in: 2026 for 2026-27 (from July onward). */
    static int seasonYear() {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kuala_Lumpur"));
        return c.get(Calendar.MONTH) >= Calendar.JULY ? c.get(Calendar.YEAR) : c.get(Calendar.YEAR) - 1;
    }

    private static List<String> seasons() {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kuala_Lumpur"));
        int y = c.get(Calendar.YEAR), m = c.get(Calendar.MONTH);
        String european = m >= Calendar.JULY ? y + "-" + (y + 1) : (y - 1) + "-" + y;
        List<String> list = new ArrayList<>();
        list.add(european);
        list.add(String.valueOf(y));
        list.add(m >= Calendar.JULY ? (y - 1) + "-" + y : (y - 2) + "-" + (y - 1));
        return list;
    }

    /** The kickoff in Malaysia time, 24-hour, or "Date TBA" when the source has no date yet. */
    private static String kickoff(JSONObject e) {
        String ts = clean(e.optString("strTimestamp", ""));
        String d = clean(e.optString("dateEvent", ""));
        String t = clean(e.optString("strTime", ""));
        try {
            Date when = null;
            if (!ts.isEmpty()) {
                SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
                in.setTimeZone(TimeZone.getTimeZone("UTC"));
                when = in.parse(ts.length() > 19 ? ts.substring(0, 19) : ts);
            } else if (!d.isEmpty() && !t.isEmpty()) {
                SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
                in.setTimeZone(TimeZone.getTimeZone("UTC"));
                when = in.parse(d + " " + t);
            }
            if (when != null) {
                SimpleDateFormat out = new SimpleDateFormat("EEE d MMM, HH:mm", Locale.ENGLISH);
                out.setTimeZone(TimeZone.getTimeZone("Asia/Kuala_Lumpur"));
                return out.format(when);
            }
            if (!d.isEmpty()) {
                Date only = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(d);
                return new SimpleDateFormat("EEE d MMM", Locale.ENGLISH).format(only) + ", time TBA";
            }
        } catch (Exception ignored) {
        }
        return "Date TBA";
    }

    private static String clean(String s) {
        if (s == null) return "";
        String t = s.trim();
        return t.equalsIgnoreCase("null") ? "" : t;
    }

    private static String toJson(List<Team> list) throws Exception {
        JSONArray a = new JSONArray();
        for (Team t : list) {
            a.put(new JSONObject().put("n", t.name).put("l", t.league).put("t", t.table).put("r", t.record)
                    .put("s", t.last).put("o", t.outcome).put("x", t.next).put("b", t.badge).put("v", t.stadium));
        }
        return a.toString();
    }

    private static List<Team> parse(String s) {
        List<Team> out = new ArrayList<>();
        if (s == null) return out;
        try {
            JSONArray a = new JSONArray(s);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                out.add(new Team(o.getString("n"), o.optString("l"), o.optString("t"), o.optString("r"),
                        o.optString("s"), o.optString("o"), o.optString("x"), o.optString("b"), o.optString("v")));
            }
        } catch (Exception e) {
            out.clear();
        }
        return out;
    }

    // ---------- Sofascore (unofficial, no key): every competition a team plays ----------

    private static final String SOFA = "https://api.sofascore.com/api/v1/";

    private static JSONObject getSofa(String path) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(SOFA + path).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(10000);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36");
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("Origin", "https://www.sofascore.com");
        c.setRequestProperty("Referer", "https://www.sofascore.com/");
        try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"))) {
            StringBuilder b = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) b.append(line);
            return new JSONObject(b.toString());
        } finally {
            c.disconnect();
        }
    }

    /** Sofascore's id for a football team: searched by name once, then remembered. {@code mustHave} is a word the name must contain. */
    private static String sofaId(String query, String mustHave, String country) {
        try {
            JSONArray res = getSofa("search/teams?q=" + java.net.URLEncoder.encode(query, "UTF-8")).optJSONArray("results");
            String best = null;
            for (int i = 0; res != null && i < res.length(); i++) {
                JSONObject e = res.getJSONObject(i).optJSONObject("entity");
                if (e == null) continue;
                String name = clean(e.optString("name", ""));
                JSONObject sport = e.optJSONObject("sport");
                JSONObject ctry = e.optJSONObject("country");
                if (sport != null && !"football".equalsIgnoreCase(sport.optString("slug", "football"))) continue;
                if (ctry != null && !country.equalsIgnoreCase(clean(ctry.optString("name", "")))) continue;
                String low = name.toLowerCase(Locale.ROOT);
                if (!low.contains(mustHave.toLowerCase(Locale.ROOT))) continue;
                if (low.contains("u23") || low.contains("u21") || low.contains("u19") || low.contains(" ii") || low.contains("women") || low.contains("futsal")) continue;
                best = String.valueOf(e.optLong("id"));
                break;
            }
            return best;
        } catch (Exception e) {
            android.util.Log.w("TvHome.Sports", "sofascore search failed: " + e);
            return null;
        }
    }

    private static final java.util.Map<String, String> SOFA_IDS = new java.util.HashMap<>();

    /** The team's matches in every competition: rows like the other schedules, with the competition's name after the opponent. */
    private static List<String[]> sofaSchedule(String query, String mustHave, String country) {
        List<String[]> rows = new ArrayList<>();
        try {
            String id = SOFA_IDS.get(query);
            if (id == null) { id = sofaId(query, mustHave, country); if (id != null) SOFA_IDS.put(query, id); }
            if (id == null) return rows;
            SimpleDateFormat show = new SimpleDateFormat("EEE d MMM, HH:mm", Locale.ENGLISH);
            show.setTimeZone(TimeZone.getTimeZone("Asia/Kuala_Lumpur"));
            SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US);
            iso.setTimeZone(TimeZone.getTimeZone("UTC"));
            for (String dir : new String[]{"last", "next"}) {
                for (int page = 0; page < 3; page++) {
                    JSONArray ev;
                    try { ev = getSofa("team/" + id + "/events/" + dir + "/" + page).optJSONArray("events"); }
                    catch (Exception e) { break; }
                    if (ev == null || ev.length() == 0) break;
                    for (int i = 0; i < ev.length(); i++) {
                        JSONObject m = ev.getJSONObject(i);
                        JSONObject h = m.optJSONObject("homeTeam"), a = m.optJSONObject("awayTeam");
                        if (h == null || a == null) continue;
                        boolean isHome = id.equals(String.valueOf(h.optLong("id")));
                        String opp = clean((isHome ? a : h).optString("name", ""));
                        JSONObject tour = m.optJSONObject("tournament");
                        String comp = tour == null ? "" : clean(tour.optString("name", ""));
                        long ts = m.optLong("startTimestamp", 0) * 1000L;
                        if (ts == 0) continue;
                        String res = "";
                        JSONObject st = m.optJSONObject("status");
                        if (st != null && "finished".equalsIgnoreCase(st.optString("type", ""))) {
                            JSONObject hs = m.optJSONObject("homeScore"), as = m.optJSONObject("awayScore");
                            if (hs != null && as != null) {
                                int x = isHome ? hs.optInt("current") : as.optInt("current");
                                int y = isHome ? as.optInt("current") : hs.optInt("current");
                                res = (x > y ? "W " : x == y ? "D " : "L ") + x + "–" + y;
                            }
                        }
                        Date when = new Date(ts);
                        rows.add(new String[]{show.format(when), (isHome ? "vs " : "at ") + opp + (comp.isEmpty() ? "" : "  ·  " + comp), res, "", iso.format(when)});
                    }
                }
            }
        } catch (Exception e) { android.util.Log.w("TvHome.Sports", "sofascore failed: " + e); }
        return rows;
    }

    /** Adds the matches of {@code extra} whose day is not already in {@code base}, oldest first. */
    private static List<String[]> mergeByDay(List<String[]> base, List<String[]> extra) {
        List<String[]> out = new ArrayList<>(base);
        java.util.Set<String> days = new java.util.HashSet<>();
        for (String[] r : base) if (r.length > 4 && r[4].length() >= 10) days.add(r[4].substring(0, 10));
        for (String[] r : extra) {
            String day = r[4].substring(0, 10);
            if (days.add(day)) out.add(r);
        }
        java.util.Collections.sort(out, (x, y) -> (x.length > 4 ? x[4] : "").compareTo(y.length > 4 ? y[4] : ""));
        return out;
    }

    // ---------- Selangor FC: every competition (cups included) ----------

    /** The league list plus every other competition TheSportsDB knows (Piala Malaysia, Piala FA, Sumbangsih, AFC...), tagged with its name. */
    private static List<String[]> withAllCompetitions(String id, List<String[]> league) {
        List<String[]> out = new ArrayList<>(league);
        java.util.Set<String> days = new java.util.HashSet<>();
        for (String[] r : league) if (r.length > 4 && r[4].length() >= 10) days.add(r[4].substring(0, 10));
        try {
            for (String path : new String[]{"eventslast.php?id=", "eventsnext.php?id="}) {
                JSONObject o = get(BASE + path + id);
                JSONArray ev = o.optJSONArray(path.startsWith("eventslast") ? "results" : "events");
                for (int i = 0; ev != null && i < ev.length(); i++) {
                    JSONObject e = ev.getJSONObject(i);
                    String day = clean(e.optString("dateEvent", ""));
                    if (day.length() < 10 || days.contains(day)) continue;
                    days.add(day);
                    boolean isHome = clean(e.optString("idHomeTeam", "")).equals(id);
                    String home = clean(e.optString("strHomeTeam", "")), away = clean(e.optString("strAwayTeam", ""));
                    String hs = clean(e.optString("intHomeScore", "")), as = clean(e.optString("intAwayScore", ""));
                    String res = "";
                    if (!hs.isEmpty() && !as.isEmpty()) {
                        try {
                            int a = Integer.parseInt(isHome ? hs : as), b = Integer.parseInt(isHome ? as : hs);
                            res = (a > b ? "W " : a == b ? "D " : "L ") + a + "–" + b;
                        } catch (Exception ignored) { }
                    }
                    String comp = clean(e.optString("strLeague", ""));
                    String t = clean(e.optString("strTime", ""));
                    String iso = day + "T" + (t.length() >= 5 ? t.substring(0, 5) : "00:00");
                    out.add(new String[]{kickoff(e), (isHome ? "vs " + away : "at " + home) + (comp.isEmpty() ? "" : "  ·  " + comp),
                            res, clean(e.optString("strVenue", "")), iso});
                }
            }
        } catch (Exception ex) { android.util.Log.w("TvHome.Sports", "other competitions failed: " + ex); }
        java.util.Collections.sort(out, (x, y) -> (x.length > 4 ? x[4] : "").compareTo(y.length > 4 ? y[4] : ""));
        return out;
    }

    private static volatile String futsalCached;

    /** TheSportsDB id of Selangor's futsal team, or null when it is not listed. */
    private static String futsalId() {
        try {
            JSONArray teams = get(BASE + "searchteams.php?t=Selangor%20Futsal").optJSONArray("teams");
            for (int i = 0; teams != null && i < teams.length(); i++) {
                JSONObject t = teams.getJSONObject(i);
                if (clean(t.optString("strSport", "")).toLowerCase(Locale.ROOT).contains("futsal")
                        || clean(t.optString("strTeam", "")).toLowerCase(Locale.ROOT).contains("futsal")) {
                    futsalCached = clean(t.optString("idTeam", ""));
                    return futsalCached.isEmpty() ? null : futsalCached;
                }
            }
        } catch (Exception e) { android.util.Log.w("TvHome.Sports", "futsal search failed: " + e); }
        return futsalCached;
    }

    // ---------- Malaysia men's national team (fam.org.my, men's pages only) ----------

    static final String FAM_ID = "fam-my-men";
    private static final String FAM_CAL = "https://fam.org.my/men-team/calendar";
    private static final String FAM_RES = "https://fam.org.my/men-team/results";

    private static String getText(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(10000);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120 Safari/537.36");
        try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"))) {
            StringBuilder b = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) b.append(line).append('\n');
            String h = b.toString();
            h = h.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ");
            h = h.replaceAll("(?s)<[^>]+>", "\n");
            h = h.replace("&nbsp;", " ").replace("&amp;", "&").replace("&#039;", "'").replace("&#8211;", "-");
            return h.replaceAll("[ \\t\\u00a0]+", " ").replaceAll("\\n\\s*\\n+", "\n");
        } finally {
            c.disconnect();
        }
    }

    private static final java.util.regex.Pattern FAM_DATE = java.util.regex.Pattern.compile(
            "(?i)(?:Mon|Tues?|Wednes?|Thurs?|Fri|Satur?|Sun)[a-z]*day\\s+(\\d{1,2})(?:st|nd|rd|th)?\\s+([A-Za-z]+)\\s+(\\d{4})");
    private static final java.util.regex.Pattern FAM_TIME = java.util.regex.Pattern.compile("(?i)(\\d{1,2}):(\\d{2})\\s*([AP]M)");
    private static final java.util.regex.Pattern FAM_SCORE = java.util.regex.Pattern.compile("(?m)^\\s*(\\d{1,2})\\s*[-–:]\\s*(\\d{1,2})\\s*$");
    private static final java.util.regex.Pattern FAM_VS = java.util.regex.Pattern.compile(
            "([A-Z][A-Z .'&-]{2,}?)\\s+(?:vs\\.?|v)\\s+([A-Z][A-Z .'&-]{2,})");

    /** Reads the match blocks of one FAM men's page: {display, "vs X"/"at X", result, venue, iso}. Best effort: the page has no feed. */
    private static List<String[]> famParse(String url) throws Exception {
        String text = getText(url);
        List<String[]> out = new ArrayList<>();
        java.util.regex.Matcher m = FAM_DATE.matcher(text);
        List<int[]> spans = new ArrayList<>();
        List<String> dates = new ArrayList<>();
        while (m.find()) {
            spans.add(new int[]{m.end(), 0});
            dates.add(m.group(1) + " " + m.group(2) + " " + m.group(3));
        }
        SimpleDateFormat dp = new SimpleDateFormat("d MMMM yyyy", Locale.US);
        dp.setTimeZone(TimeZone.getTimeZone("Asia/Kuala_Lumpur"));
        for (int i = 0; i < spans.size(); i++) {
            int end = i + 1 < spans.size() ? text.indexOf('\n', spans.get(i + 1)[0] - 60) : text.length();
            int from = spans.get(i)[0];
            String block = text.substring(from, end > from ? Math.min(end, from + 900) : Math.min(text.length(), from + 900));
            if (!block.toUpperCase(Locale.ROOT).contains("MALAYSIA")) continue;
            Date d = dp.parse(dates.get(i));
            if (d == null) continue;
            long ms = d.getTime();
            java.util.regex.Matcher tm = FAM_TIME.matcher(block);
            if (tm.find()) {
                int hh = Integer.parseInt(tm.group(1)) % 12 + (tm.group(3).equalsIgnoreCase("PM") ? 12 : 0);
                ms += (hh * 60L + Integer.parseInt(tm.group(2))) * 60_000L;
            }
            String a = null, b = null;
            java.util.regex.Matcher vm = FAM_VS.matcher(block);
            if (vm.find()) { a = vm.group(1).trim(); b = vm.group(2).trim(); }
            else {
                List<String> names = new ArrayList<>();
                for (String ln : block.split("\n")) {
                    String t = ln.trim();
                    if (t.length() < 3 || !t.equals(t.toUpperCase(Locale.ROOT)) || !t.matches("[A-Z][A-Z .'&-]+")) continue;
                    if (t.contains("TIME") || t.contains("MATCH") || t.contains("STADIUM") || t.contains("INTERNATIONAL")
                            || t.contains("FRIENDL") || t.contains("ARENA") || t.contains("TIER")) continue;
                    if (!names.contains(t)) names.add(t);
                    if (names.size() == 2) break;
                }
                if (names.size() == 2) { a = names.get(0); b = names.get(1); }
            }
            if (a == null) continue;
            boolean home = a.contains("MALAYSIA");
            String opp = titleCase(home ? b : a);
            String venue = "";
            for (String ln : block.split("\n")) {
                String t = ln.trim();
                if (t.matches("(?i).*(STADIUM|STADIUM|ARENA|BUKIT JALIL|STADION).*") && t.length() < 90) { venue = titleCase(t); break; }
            }
            String res = "";
            java.util.regex.Matcher sm = FAM_SCORE.matcher(block);
            if (sm.find()) {
                int x = Integer.parseInt(sm.group(home ? 1 : 2)), y = Integer.parseInt(sm.group(home ? 2 : 1));
                res = (x > y ? "W " : x == y ? "D " : "L ") + x + "–" + y;
            }
            SimpleDateFormat show = new SimpleDateFormat("EEE d MMM, HH:mm", Locale.ENGLISH);
            show.setTimeZone(TimeZone.getTimeZone("Asia/Kuala_Lumpur"));
            SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US);
            iso.setTimeZone(TimeZone.getTimeZone("UTC"));
            out.add(new String[]{show.format(new Date(ms)), (home ? "vs " : "at ") + opp, res, venue, iso.format(new Date(ms))});
        }
        return out;
    }

    private static String titleCase(String s) {
        StringBuilder b = new StringBuilder();
        for (String w : s.toLowerCase(Locale.ROOT).split(" ")) {
            if (w.isEmpty()) continue;
            b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
        }
        return b.toString().trim();
    }

    /** TheSportsDB id of the Malaysia men's national team: the fallback when the FAM pages cannot be read. */
    private static final String MALAYSIA_TSDB = "140138";

    private static List<String[]> famSchedule() {
        java.util.Map<String, String[]> byIso = new java.util.TreeMap<>();
        for (String u : new String[]{FAM_RES, FAM_CAL}) {
            try { for (String[] r : famParse(u)) byIso.put(r[4] + r[1], r); }
            catch (Exception e) { android.util.Log.w("TvHome.Sports", "FAM " + u + " failed: " + e); }
        }
        if (byIso.isEmpty()) return withAllCompetitions(MALAYSIA_TSDB, sportsDbSchedule(MALAYSIA_TSDB));
        return new ArrayList<>(byIso.values());
    }

    private static Team famCard() {
        String badge = "";
        try { badge = build(MALAYSIA_TSDB, "Malaysia").badge; } catch (Exception e) { android.util.Log.w("TvHome.Sports", "Malaysia badge failed: " + e); }
        List<String[]> all = famSchedule();
        if (all.isEmpty()) throw new IllegalStateException("no Malaysia matches");
        String nowIso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).format(new Date(System.currentTimeMillis() - 3L * 3600_000L));
        String last = "", outcome = "", next = "", venue = "";
        for (String[] r : all) {
            if (!r[2].isEmpty()) { last = r[2] + " " + r[1]; outcome = r[2].substring(0, 1); }
            else if (next.isEmpty() && r[4].compareTo(nowIso) >= 0) { next = r[0] + " · " + r[1]; venue = r[3]; }
        }
        return new Team("Malaysia", "Men's national team", "", "", last, outcome, next, badge, venue);
    }

    private static JSONObject get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"))) {
            StringBuilder b = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) b.append(line);
            return new JSONObject(b.toString());
        } finally {
            c.disconnect();
        }
    }
}
