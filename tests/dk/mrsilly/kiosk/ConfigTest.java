package dk.mrsilly.kiosk;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;

/** Plain-JVM tests of the logic in Config (no Android needed): `./test.sh`. */
public class ConfigTest {
    private static final TimeZone CPH = TimeZone.getTimeZone("Europe/Copenhagen");
    private static int failures = 0;

    private static Calendar at(String date, String time) {
        String[] d = date.split("-"), t = time.split(":");
        Calendar c = new GregorianCalendar(CPH);
        c.clear();
        c.set(Integer.parseInt(d[0]), Integer.parseInt(d[1]) - 1, Integer.parseInt(d[2]), Integer.parseInt(t[0]), Integer.parseInt(t[1]));
        return c;
    }

    private static void eq(String what, Object want, Object got) {
        if (!java.util.Objects.equals(want, got)) { failures++; System.out.println("FAIL " + what + ": want " + want + ", got " + got); }
    }

    private static Config defaults() {
        Config c = new Config();
        c.windows = Config.parseWindows(Config.DEFAULT_WINDOWS);
        return c;
    }

    static void windows() {
        Config c = defaults();
        eq("mon 05:59", false, c.isActive(at("2026-11-02", "05:59")));
        eq("mon 06:00", true, c.isActive(at("2026-11-02", "06:00")));
        eq("mon 08:14", true, c.isActive(at("2026-11-02", "08:14")));
        eq("mon 08:15", false, c.isActive(at("2026-11-02", "08:15")));
        eq("mon 12:00", false, c.isActive(at("2026-11-02", "12:00")));
        eq("mon 15:00", true, c.isActive(at("2026-11-02", "15:00")));
        eq("mon 22:00", false, c.isActive(at("2026-11-02", "22:00")));
        eq("sat 06:30", false, c.isActive(at("2026-11-07", "06:30")));
        eq("sat 12:00", true, c.isActive(at("2026-11-07", "12:00")));
        eq("sun 21:59", true, c.isActive(at("2026-11-08", "21:59")));
        eq("no windows = always on", true, new Config().isActive(at("2026-11-02", "03:00")));
    }

    static void wrapAndNext() {
        Config c = new Config();
        c.windows = Config.parseWindows("1320-120-16");   // Friday 22:00 - 02:00
        eq("fri 23:00", true, c.isActive(at("2026-11-06", "23:00")));
        eq("sat 01:00 (still Friday's)", true, c.isActive(at("2026-11-07", "01:00")));
        eq("sat 03:00", false, c.isActive(at("2026-11-07", "03:00")));
        eq("sun 01:00", false, c.isActive(at("2026-11-08", "01:00")));
        Config d = defaults();
        Calendar n = new GregorianCalendar(CPH);
        n.setTimeInMillis(d.nextStart(at("2026-11-02", "12:00")));
        eq("next after mon 12:00", "2026-11-02 15:00", String.format("%tF %<tR", n));
        n.setTimeInMillis(d.nextStart(at("2026-11-07", "12:00")));
        eq("next after sat 12:00", "2026-11-08 07:00", String.format("%tF %<tR", n));
        n.setTimeInMillis(d.nextStart(at("2026-11-02", "23:00")));
        eq("next after mon 23:00", "2026-11-03 06:00", String.format("%tF %<tR", n));
    }

    static void roundTrip() {
        Config c = defaults();
        eq("windows round trip", Config.DEFAULT_WINDOWS, c.windowsToString());
    }

    static void maxRota() {
        Config c = new Config();
        c.pages = Config.defaults();
        String max = "https://claude.ai/artifact/KiQpc6K2kUgfxcV85DhPoX", laura = "https://claude.ai/artifact/JT6RpxNhYivhfSDzp94NTo", stella = "https://claude.ai/artifact/9rZRLiR3fGSjjZoXP1dPx7";
        // Alex' weekends: Fri 30 Oct, 13 Nov, 27 Nov. The kids' pages show in the MORNING when they woke at Alex' and in the AFTERNOON when they are with him.
        // {date, morning (07:00), afternoon (16:00)} - checked against the real calendar of 1-6 Nov (Mon 2 morning Alex, afternoon Karen; Wed afternoon Alex; Fri morning Alex).
        String[][] cases = { {"2026-10-30", "true", "true"}, {"2026-10-31", "true", "true"}, {"2026-11-01", "true", "true"}, {"2026-11-02", "true", "false"},
            {"2026-11-03", "false", "false"}, {"2026-11-04", "false", "true"}, {"2026-11-05", "true", "true"}, {"2026-11-06", "true", "false"},
            {"2026-11-07", "false", "false"}, {"2026-11-08", "false", "false"}, {"2026-11-09", "false", "false"},
            {"2026-11-13", "true", "true"}, {"2026-11-15", "true", "true"}, {"2026-11-16", "true", "false"}, {"2026-11-18", "false", "true"}, {"2026-11-20", "true", "false"}, {"2026-11-27", "true", "true"} };
        for (String[] k : cases) {
            for (int part = 0; part < 2; part++) {
                boolean want = Boolean.parseBoolean(k[1 + part]);
                List<String> urls = c.activeUrls(at(k[0], part == 0 ? "07:00" : "16:00"));
                String when = k[0] + (part == 0 ? " morning" : " afternoon");
                eq("max shown " + when, want, urls.contains(max));
                eq("laura follows max " + when, want, urls.contains(laura));
                eq("stella follows max " + when, want, urls.contains(stella));
                eq("alex shown " + when, true, urls.contains("https://claude.ai/artifact/18FB2Uw8GYtvVSfGyvVSzu"));
            }
        }
        eq("old saved kid default migrates", Config.KID_DAYS, Config.migrateDays(Config.WED_TO_SUN, true));
        eq("other saved days stay", Config.WEEKDAYS, Config.migrateDays(Config.WEEKDAYS, true));
        eq("madplan off by default", false, c.activeUrls(at("2026-11-04", "16:00")).contains("https://claude.ai/artifact/VJGXEVNnJNJfAyL3GiSCVE"));
    }

    static void skipAndDim() {
        Config c = new Config();
        c.pages = Config.defaults();
        Calendar wed = at("2026-11-05", "16:00");
        c.skipDay = Config.dayKey(wed);
        c.skipped.add("https://claude.ai/artifact/KiQpc6K2kUgfxcV85DhPoX");
        eq("skipped page hidden today", false, c.activeUrls(wed).contains("https://claude.ai/artifact/KiQpc6K2kUgfxcV85DhPoX"));
        eq("skip ends next day", true, c.activeUrls(at("2026-11-06", "07:00")).contains("https://claude.ai/artifact/KiQpc6K2kUgfxcV85DhPoX"));
        c.skipDay = ""; c.skipped.clear();
        Calendar fri = at("2026-11-06", "07:00");
        c.skipUntil.put("https://claude.ai/artifact/KiQpc6K2kUgfxcV85DhPoX", at("2026-11-06", "12:00").getTimeInMillis());
        eq("skipUntil hides before the time", false, c.activeUrls(fri).contains("https://claude.ai/artifact/KiQpc6K2kUgfxcV85DhPoX"));
        c.skipUntil.put("https://claude.ai/artifact/KiQpc6K2kUgfxcV85DhPoX", at("2026-11-05", "12:00").getTimeInMillis());
        eq("skipUntil ends", true, c.activeUrls(fri).contains("https://claude.ai/artifact/KiQpc6K2kUgfxcV85DhPoX"));
        c.dimOn = true; c.dimFrom = 20 * 60; c.dimTo = 6 * 60;
        eq("dim 21:00", true, c.isDim(at("2026-11-04", "21:00")));
        eq("dim 05:00", true, c.isDim(at("2026-11-04", "05:00")));
        eq("no dim 12:00", false, c.isDim(at("2026-11-04", "12:00")));
        c.dimOn = false;
        eq("dim off", false, c.isDim(at("2026-11-04", "21:00")));
    }

    static void reporter() {
        String ok = "{\"v\":1,\"d\":\"2026-11-06\",\"c\":\"Laura\",\"t\":\"🫧 Tømme opper\",\"on\":true,\"at\":1793000000000}";
        eq("good report accepted", ok, Reporter.check(ok));
        eq("unknown child refused", null, Reporter.check(ok.replace("Laura", "Peter")));
        eq("extra key refused", null, Reporter.check(ok.replace("}", ",\"x\":1}")));
        eq("quote in text refused", null, Reporter.check(ok.replace("Tømme", "T\\\"ømme")));
        eq("not json refused", null, Reporter.check("hello"));
        eq("null refused", null, Reporter.check(null));
        List<String> q = new java.util.ArrayList<>();
        for (int i = 0; i < Reporter.MAX_QUEUE + 5; i++) q = Reporter.push(q, "e" + i);
        eq("queue capped", Reporter.MAX_QUEUE, q.size());
        eq("oldest dropped", "e5", q.get(0));
    }

    public static void main(String[] args) {
        windows(); wrapAndNext(); roundTrip(); maxRota(); skipAndDim(); reporter();
        if (failures > 0) { System.out.println(failures + " failed"); System.exit(1); }
        System.out.println("ok: all kiosk logic tests passed");
    }
}
