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
        if (!want.equals(got)) { failures++; System.out.println("FAIL " + what + ": want " + want + ", got " + got); }
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
        // Alex' weekends: 30 Oct, then every 14 days (13 Nov, 27 Nov ...)
        String[][] cases = { {"2026-10-30", "true"}, {"2026-10-31", "true"}, {"2026-11-01", "true"}, {"2026-11-02", "false"},
            {"2026-11-04", "true"}, {"2026-11-05", "true"}, {"2026-11-06", "false"}, {"2026-11-08", "false"},
            {"2026-11-13", "true"}, {"2026-11-15", "true"}, {"2026-11-20", "false"}, {"2026-11-27", "true"} };
        for (String[] k : cases) {
            List<String> urls = c.activeUrls(at(k[0], "12:00"));
            eq("max shown " + k[0], Boolean.parseBoolean(k[1]), urls.contains("https://claude.ai/artifact/KiQpc6K2kUgfxcV85DhPoX"));
            eq("alex shown " + k[0], true, urls.contains("https://claude.ai/artifact/18FB2Uw8GYtvVSfGyvVSzu"));
        }
        eq("madplan off by default", false, c.activeUrls(at("2026-11-04", "12:00")).contains("https://claude.ai/artifact/VJGXEVNnJNJfAyL3GiSCVE"));
    }

    static void skipAndDim() {
        Config c = new Config();
        c.pages = Config.defaults();
        Calendar wed = at("2026-11-04", "12:00");
        c.skipDay = Config.dayKey(wed);
        c.skipped.add("https://claude.ai/artifact/KiQpc6K2kUgfxcV85DhPoX");
        eq("skipped page hidden today", false, c.activeUrls(wed).contains("https://claude.ai/artifact/KiQpc6K2kUgfxcV85DhPoX"));
        eq("skip ends next day", true, c.activeUrls(at("2026-11-05", "12:00")).contains("https://claude.ai/artifact/KiQpc6K2kUgfxcV85DhPoX"));
        c.dimOn = true; c.dimFrom = 20 * 60; c.dimTo = 6 * 60;
        eq("dim 21:00", true, c.isDim(at("2026-11-04", "21:00")));
        eq("dim 05:00", true, c.isDim(at("2026-11-04", "05:00")));
        eq("no dim 12:00", false, c.isDim(at("2026-11-04", "12:00")));
        c.dimOn = false;
        eq("dim off", false, c.isDim(at("2026-11-04", "21:00")));
    }

    public static void main(String[] args) {
        windows(); wrapAndNext(); roundTrip(); maxRota(); skipAndDim();
        if (failures > 0) { System.out.println(failures + " failed"); System.exit(1); }
        System.out.println("ok: all kiosk logic tests passed");
    }
}
