package dk.mrsilly.kiosk;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/** Settings: pages (name, url, on/off, weekdays), seconds per page, and the hours the screen stays on. */
final class Config {
    static final int ALL_DAYS = 127;          // bit 0 = Monday ... bit 6 = Sunday
    static final int WED_TO_SUN = 4 + 8 + 16 + 32 + 64;   // the old kid default (1 Nov 2026): wrong, the kids arrive Wed afternoon
    /** The mornings the children wake at Alex': Thu, Fri, and (Alex' weekends only, see Page.alt) Sat, Sun and the Monday after. */
    static final int KID_DAYS = 1 + 8 + 16 + 32 + 64;
    /** Epoch day of a Friday that starts one of Alex' weekends (30 Oct 2026); they repeat every 14 days. */
    static final long WEEKEND_ANCHOR = 20756;

    static final class Page {
        String name;
        String url;
        boolean on;
        int days;
        boolean alt;   // Sat, Sun and the Monday after only count in Alex' weekends (every other week); Fri is every week
        Page(String name, String url, boolean on, int days, boolean alt) {
            this.name = name; this.url = url; this.on = on; this.days = days; this.alt = alt;
        }
    }

    List<Page> pages = new ArrayList<>();
    int seconds = 60;
    static final int WEEKDAYS = 31, WEEKEND = 96;
    static final String DEFAULT_WINDOWS = "360-495-31,900-1320-31,420-1320-96";   // Mon-Fri 6:00-8:15 + 15:00-22:00, Sat-Sun 7:00-22:00

    /** On-windows as {startMinute, endMinute, weekdayMask}; the window belongs to the day it starts; start > end wraps past midnight. */
    List<int[]> windows = new ArrayList<>();
    String skipDay = "";                       // pages that asked to be skipped today (see KioskBridge)
    Set<String> skipped = new HashSet<>();
    int orientation = 1;                       // 0 = follow the tablet, 1 = landscape, 2 = portrait
    String reportToken = "";                   // GitHub token for the children's tick reports (see Reporter)
    boolean pin = false;                       // lock the app in place (screen pinning)
    boolean dimOn = true;
    int dimFrom = 20 * 60, dimTo = 6 * 60, dimLevel = 25;   // dim between dimFrom and dimTo to dimLevel % brightness

    static Config load(Context c) {
        Config cfg = new Config();
        SharedPreferences p = c.getSharedPreferences("kiosk", Context.MODE_PRIVATE);
        cfg.seconds = p.getInt("seconds", 60);
        cfg.skipDay = p.getString("skipDay", "");
        cfg.skipped = new HashSet<>(p.getStringSet("skipUrls", new HashSet<String>()));
        cfg.orientation = p.getInt("orient", 1);
        cfg.pin = p.getBoolean("pin", false);
        cfg.reportToken = p.getString("reportToken", "");
        cfg.dimOn = p.getBoolean("dimOn", true);
        cfg.dimFrom = p.getInt("dimFrom", 20 * 60);
        cfg.dimTo = p.getInt("dimTo", 6 * 60);
        cfg.dimLevel = p.getInt("dimLevel", 25);
        cfg.windows = parseWindows(p.getString("windows2", DEFAULT_WINDOWS));
        if (!p.contains("pages")) { cfg.pages = defaults(); return cfg; }
        try {
            JSONArray a = new JSONArray(p.getString("pages", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                cfg.pages.add(new Page(o.optString("name", ""), o.getString("url"), o.optBoolean("on", true),
                    migrateDays(o.optInt("days", ALL_DAYS), o.optBoolean("alt", false)), o.optBoolean("alt", false)));
            }
        } catch (Exception ignored) { }
        return cfg;
    }

    /** Pages saved with the first kid default (Wed-Sun, alt) move to the mornings-at-Alex' days. */
    static int migrateDays(int days, boolean alt) {
        return alt && days == WED_TO_SUN ? KID_DAYS : days;
    }

    /** What a fresh install shows; the kids' pages show the mornings they wake at Alex' (Thu, Fri, plus Alex' weekends and the Monday after); Laura and Stella follow the same rota. */
    static List<Page> defaults() {
        List<Page> d = new ArrayList<>();
        d.add(new Page("Alex", "https://claude.ai/artifact/18FB2Uw8GYtvVSfGyvVSzu", true, ALL_DAYS, false));
        d.add(new Page("Max", "https://claude.ai/artifact/KiQpc6K2kUgfxcV85DhPoX", true, KID_DAYS, true));
        d.add(new Page("Laura", "https://claude.ai/artifact/JT6RpxNhYivhfSDzp94NTo", true, KID_DAYS, true));
        d.add(new Page("Stella", "https://claude.ai/artifact/9rZRLiR3fGSjjZoXP1dPx7", true, KID_DAYS, true));
        d.add(new Page("Madplan", "https://claude.ai/artifact/VJGXEVNnJNJfAyL3GiSCVE", false, ALL_DAYS, false));
        d.add(new Page("To uger", "https://claude.ai/artifact/7h3TUHwp5t9pPFVh4MvtZ8", false, ALL_DAYS, false));
        return d;
    }

    static String dayKey(Calendar c) {
        return c.get(Calendar.YEAR) + "-" + c.get(Calendar.MONTH) + "-" + c.get(Calendar.DAY_OF_MONTH);
    }

    /** A page asked to be left out for the rest of today. Kept separate from save() so settings edits don't clobber it. */
    static void skipToday(Context c, String url, Calendar now) {
        SharedPreferences p = c.getSharedPreferences("kiosk", Context.MODE_PRIVATE);
        Set<String> set = new HashSet<>();
        if (dayKey(now).equals(p.getString("skipDay", ""))) set.addAll(p.getStringSet("skipUrls", new HashSet<String>()));
        set.add(url.trim());
        p.edit().putString("skipDay", dayKey(now)).putStringSet("skipUrls", set).apply();
    }

    void save(Context c) {
        JSONArray a = new JSONArray();
        try {
            for (Page pg : pages) {
                a.put(new JSONObject().put("name", pg.name).put("url", pg.url).put("on", pg.on)
                    .put("days", pg.days).put("alt", pg.alt));
            }
        } catch (Exception ignored) { }
        c.getSharedPreferences("kiosk", Context.MODE_PRIVATE).edit()
            .putString("pages", a.toString())
            .putInt("seconds", seconds).putString("windows2", windowsToString())
            .putInt("orient", orientation).putString("reportToken", reportToken).putBoolean("pin", pin).putBoolean("dimOn", dimOn).putInt("dimFrom", dimFrom).putInt("dimTo", dimTo).putInt("dimLevel", dimLevel).apply();
    }

    boolean hasAnyPage() {
        for (Page pg : pages) if (pg.on && pg.url.trim().length() > 0) return true;
        return false;
    }

    /** URLs that are switched on and allowed today. */
    List<String> activeUrls(Calendar now) {
        List<String> out = new ArrayList<>();
        int dow = (now.get(Calendar.DAY_OF_WEEK) + 5) % 7;   // Mon = 0 ... Sun = 6
        long epochDay = (now.getTimeInMillis() + now.get(Calendar.ZONE_OFFSET) + now.get(Calendar.DST_OFFSET)) / 86400000L;
        for (Page pg : pages) {
            if (!pg.on || pg.url.trim().isEmpty() || (pg.days & (1 << dow)) == 0) continue;
            if (dayKey(now).equals(skipDay) && skipped.contains(pg.url.trim())) continue;
            if (pg.alt && (dow >= 5 || dow == 0)) {      // Sat, Sun, and the Monday after the weekend
                long friday = dow == 0 ? epochDay - 3 : epochDay - (dow - 4);
                if (Math.floorMod((friday - WEEKEND_ANCHOR) / 7, 2L) != 0) continue;
            }
            out.add(pg.url.trim());
        }
        return out;
    }

    static List<int[]> parseWindows(String str) {
        List<int[]> out = new ArrayList<>();
        for (String part : str.split(",")) {
            String[] x = part.trim().split("-");
            if (x.length != 3) continue;
            try { out.add(new int[] { Integer.parseInt(x[0]), Integer.parseInt(x[1]), Integer.parseInt(x[2]) }); }
            catch (NumberFormatException ignored) { }
        }
        return out;
    }

    String windowsToString() {
        StringBuilder sb = new StringBuilder();
        for (int[] w : windows) sb.append(sb.length() > 0 ? "," : "").append(w[0]).append('-').append(w[1]).append('-').append(w[2]);
        return sb.toString();
    }

    static int minuteOfDay(Calendar c) {
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
    }

    static int weekday(Calendar c) {            // Mon = 0 ... Sun = 6
        return (c.get(Calendar.DAY_OF_WEEK) + 5) % 7;
    }

    /** True when the screen should be on at `now`. No windows at all means always on. */
    boolean isActive(Calendar now) {
        if (windows.isEmpty()) return true;
        int m = minuteOfDay(now), dow = weekday(now), prev = (dow + 6) % 7;
        for (int[] w : windows) {
            boolean today = (w[2] & (1 << dow)) != 0, yesterday = (w[2] & (1 << prev)) != 0;
            if (w[0] == w[1]) { if (today) return true; }
            else if (w[0] < w[1]) { if (today && m >= w[0] && m < w[1]) return true; }
            else if ((today && m >= w[0]) || (yesterday && m < w[1])) return true;
        }
        return false;
    }

    boolean isDim(Calendar now) {
        if (!dimOn) return false;
        int m = minuteOfDay(now);
        return dimFrom == dimTo ? true : dimFrom < dimTo ? (m >= dimFrom && m < dimTo) : (m >= dimFrom || m < dimTo);
    }

    /** Milliseconds timestamp of the next window start after `now` (looks a week ahead). */
    long nextStart(Calendar now) {
        long best = -1;
        for (int offset = 0; offset <= 7; offset++) {
            Calendar day = (Calendar) now.clone();
            day.add(Calendar.DAY_OF_YEAR, offset);
            int dow = weekday(day);
            for (int[] w : windows) {
                if ((w[2] & (1 << dow)) == 0) continue;
                day.set(Calendar.HOUR_OF_DAY, w[0] / 60); day.set(Calendar.MINUTE, w[0] % 60);
                day.set(Calendar.SECOND, 0); day.set(Calendar.MILLISECOND, 0);
                long t = day.getTimeInMillis();
                if (t > now.getTimeInMillis() && (best < 0 || t < best)) best = t;
            }
        }
        return best >= 0 ? best : now.getTimeInMillis() + 3600000L;
    }
}
