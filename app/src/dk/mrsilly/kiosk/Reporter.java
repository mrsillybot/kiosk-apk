package dk.mrsilly.kiosk;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

/** Sends what the children tick off (KioskBridge.report) as a comment on one GitHub issue, where tick picks it up.
 *  Reports wait in a small queue on the tablet until there is network and a token (Indstillinger). The token should be
 *  a fine-grained one for the one repo with only Issues: write. */
final class Reporter {
    static final String REPO = "mrsillybot/life-automations";
    static final int ISSUE = 2;
    static final int MAX_QUEUE = 200;
    /** Exactly the shape kid.html writes with JSON.stringify; nothing else is accepted or sent. */
    private static final Pattern EVENT = Pattern.compile(
        "\\{\"v\":1,\"d\":\"\\d{4}-\\d{2}-\\d{2}\",\"c\":\"(Max|Laura|Stella)\",\"t\":\"[^\"\\\\\\p{Cntrl}]{1,80}\",\"on\":(true|false),\"at\":\\d{10,14}\\}");
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

    private static boolean post(String token, String event) {
        try {
            HttpURLConnection h = (HttpURLConnection) new URL("https://api.github.com/repos/" + REPO + "/issues/" + ISSUE + "/comments").openConnection();
            h.setConnectTimeout(10000); h.setReadTimeout(10000);
            h.setRequestMethod("POST"); h.setDoOutput(true);
            h.setRequestProperty("Authorization", "Bearer " + token);
            h.setRequestProperty("Accept", "application/vnd.github+json");
            h.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            byte[] body = new JSONObject().put("body", event).toString().getBytes("UTF-8");
            try (OutputStream o = h.getOutputStream()) { o.write(body); }
            int code = h.getResponseCode();
            h.disconnect();
            return code == 201;   // anything else (no network, bad token) keeps the report queued
        } catch (Exception e) { return false; }
    }
}
