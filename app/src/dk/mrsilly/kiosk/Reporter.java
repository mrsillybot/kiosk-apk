package dk.mrsilly.kiosk;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

/** Writes what the children tick off (KioskBridge.report) as a small file in the lists repo (docs/lists.md), where tick and the rooms read it.
 *  Reports wait in a small queue on the tablet until there is network and a token (Indstillinger). The token is the lists-repo one (Contents: read and write). */
final class Reporter {
    static final int MAX_QUEUE = 200;
    /** Exactly the shape kid.html writes with JSON.stringify; nothing else is accepted or sent. */
    private static final Pattern EVENT = Pattern.compile(
        "\\{\"v\":1,\"d\":\"(\\d{4}-\\d{2}-\\d{2})\",\"c\":\"(Max|Laura|Stella)\",\"t\":\"([^\"\\\\\\p{Cntrl}]{1,80})\",\"on\":(true|false),\"at\":(\\d{10,14})\\}");
    private static boolean flushing = false;

    /** The event string if it is well formed, else null. */
    static String check(String json) {
        return json != null && json.length() <= 400 && EVENT.matcher(json).matches() ? json : null;
    }

    /** Add `queue` to the end of `list`, keeping the newest MAX_QUEUE. */
    static List<String> push(List<String> list, String json) {
        List<String> out = new ArrayList<>(list);
        out.add(json);
        while (out.size() > MAX_QUEUE) out.remove(0);
        return out;
    }

    static synchronized void enqueue(Context c, String json) {
        String ok = check(json);
        if (ok == null) return;
        SharedPreferences p = c.getSharedPreferences("kiosk", Context.MODE_PRIVATE);
        List<String> q = read(p);
        p.edit().putString("reportQueue", new JSONArray(push(q, ok)).toString()).apply();
        flush(c);
    }

    private static List<String> read(SharedPreferences p) {
        List<String> q = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(p.getString("reportQueue", "[]"));
            for (int i = 0; i < a.length(); i++) q.add(a.getString(i));
        } catch (Exception ignored) { }
        return q;
    }

    /** Post the queue in order on a background thread; stops at the first failure and tries again next time. */
    static void flush(Context c) {
        final Context app = c.getApplicationContext();
        synchronized (Reporter.class) { if (flushing) return; flushing = true; }
        new Thread(() -> {
            try {
                SharedPreferences p = app.getSharedPreferences("kiosk", Context.MODE_PRIVATE);
                String token = p.getString("reportToken", "").trim();
                if (token.isEmpty()) return;
                while (true) {
                    String next;
                    synchronized (Reporter.class) { List<String> q = read(p); if (q.isEmpty()) return; next = q.get(0); }
                    if (!post(token, next)) return;
                    synchronized (Reporter.class) {
                        List<String> q = read(p);
                        if (!q.isEmpty()) q.remove(0);
                        p.edit().putString("reportQueue", new JSONArray(q).toString()).apply();
                    }
                }
            } catch (Exception ignored) {
            } finally { synchronized (Reporter.class) { flushing = false; } }
        }).start();
    }

    /** The file a report becomes in the lists repo: log/<date>/<child>-<hash of the chore>.json (docs/lists.md); null if malformed. */
    static String pathFor(String event) {
        Matcher m = event == null ? null : EVENT.matcher(event);
        return m != null && m.matches() ? "log/" + m.group(1) + "/" + m.group(2) + "-" + ListsRepo.hash(m.group(3)) + ".json" : null;
    }

    /** The file body (the pattern already rules out quotes, backslashes and control characters in the chore text, so plain concatenation is safe JSON). */
    static String contentFor(String event) {
        Matcher m = event == null ? null : EVENT.matcher(event);
        return m != null && m.matches()
            ? "{\n \"chore\": \"" + m.group(3) + "\",\n \"at\": " + m.group(5) + ",\n \"done\": " + m.group(4) + ",\n \"by\": \"tablet\"\n}\n" : null;
    }

    private static boolean post(String token, String event) {
        try {
            String path = pathFor(event), content = contentFor(event);
            if (path == null || content == null || !ListsRepo.writable(path)) return true;   // never retry something that cannot be sent
            ListsRepo.putLatest(token, path, content);
            return true;
        } catch (Exception e) { return false; }   // no network or a bad token: the report stays queued
    }
}
