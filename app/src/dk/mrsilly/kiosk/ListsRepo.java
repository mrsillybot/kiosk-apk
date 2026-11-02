package dk.mrsilly.kiosk;

import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.json.JSONObject;

/** The lists repo (indkøb, opgaver, log; see docs/lists.md in life-automations): one small JSON file per thing, read as one zipball
 *  and written one file at a time through the GitHub contents API. The token is the one in Indstillinger and can only see this repo. */
final class ListsRepo {
    static final String REPO = "mrsillybot/life-lists";
    private static final Pattern PATH = Pattern.compile("(indkoeb|opgaver)/[A-Za-z0-9_-]{1,60}\\.json|log/\\d{4}-\\d{2}-\\d{2}/(Max|Laura|Stella)-[0-9a-z]{1,10}\\.json");

    static final class Item {
        final String sha, content;
        Item(String sha, String content) { this.sha = sha; this.content = content; }
    }

    /** Only these paths are ever written by the app (what the page and the reports use). */
    static boolean writable(String path) { return path != null && PATH.matcher(path).matches(); }
    /** And these are read: the three folders plus plan.json (tick copies the expected chores there). */
    static boolean wanted(String path) {
        return path.endsWith(".json") && (path.startsWith("indkoeb/") || path.startsWith("opgaver/") || path.startsWith("log/") || path.equals("plan.json"));
    }

    /** The same hash git uses for a file ("blob <size>\0<content>"), so a file we have read can be updated without asking for its sha. */
    static String blobSha(String content) {
        try {
            byte[] b = content.getBytes("UTF-8");
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update(("blob " + b.length + "\0").getBytes("UTF-8"));
            md.update(b);
            StringBuilder sb = new StringBuilder();
            for (byte x : md.digest()) sb.append(String.format("%02x", x & 0xff));
            return sb.toString();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    /** Same hash the pages use for a chore text (djb2 over code points, base36): the file name of a child's tick. */
    static String hash(String s) {
        long h = 5381;
        for (int i = 0; i < s.length(); ) { int cp = s.codePointAt(i); i += Character.charCount(cp); h = (h * 33 + cp) & 0xFFFFFFFFL; }
        return Long.toString(h, 36);
    }

    static Map<String, Item> readZip(InputStream in) throws IOException {
        Map<String, Item> out = new LinkedHashMap<>();
        ZipInputStream z = new ZipInputStream(in);
        for (ZipEntry e = z.getNextEntry(); e != null; e = z.getNextEntry()) {
            if (e.isDirectory()) continue;
            String name = e.getName();
            int i = name.indexOf('/');
            String path = i < 0 ? name : name.substring(i + 1);   // drop the <owner>-<repo>-<sha>/ folder
            if (!wanted(path)) continue;
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            for (int n = z.read(chunk); n > 0; n = z.read(chunk)) buf.write(chunk, 0, n);
            String content = buf.toString("UTF-8");
            out.put(path, new Item(blobSha(content), content));
        }
        return out;
    }

    private static HttpURLConnection open(String method, String url, String token) throws IOException {
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        h.setConnectTimeout(10000);
        h.setReadTimeout(20000);
        h.setRequestMethod(method);
        h.setRequestProperty("Authorization", "Bearer " + token);
        h.setRequestProperty("Accept", "application/vnd.github+json");
        return h;
    }

    static Map<String, Item> snapshot(String token) throws IOException {
        HttpURLConnection h = open("GET", "https://api.github.com/repos/" + REPO + "/zipball", token);
        try {
            if (h.getResponseCode() != 200) throw new IOException("GitHub " + h.getResponseCode());
            return readZip(h.getInputStream());
        } finally { h.disconnect(); }
    }

    /** The sha of a file on the server, or null if it does not exist. */
    static String shaOf(String token, String path) throws IOException {
        HttpURLConnection h = open("GET", "https://api.github.com/repos/" + REPO + "/contents/" + path, token);
        try {
            int code = h.getResponseCode();
            if (code == 404) return null;
            if (code != 200) throw new IOException("GitHub " + code);
            return new JSONObject(readAll(h.getInputStream())).getString("sha");
        } catch (org.json.JSONException e) { throw new IOException(e);
        } finally { h.disconnect(); }
    }

    /** Create or update one file; `sha` is the version we last saw (null = create). Returns the new sha. A 409/422 means someone changed it meanwhile. */
    static String put(String token, String path, String content, String sha) throws IOException {
        if (!writable(path)) throw new IOException("path not allowed");
        try {
            JSONObject body = new JSONObject().put("message", "lister: " + path).put("content", Base64.encodeToString(content.getBytes("UTF-8"), Base64.NO_WRAP));
            if (sha != null && !sha.isEmpty()) body.put("sha", sha);
            HttpURLConnection h = open("PUT", "https://api.github.com/repos/" + REPO + "/contents/" + path, token);
            h.setDoOutput(true);
            h.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            try (OutputStream o = h.getOutputStream()) { o.write(body.toString().getBytes("UTF-8")); }
            int code = h.getResponseCode();
            if (code != 200 && code != 201) { h.disconnect(); throw new IOException("GitHub " + code); }
            String res = readAll(h.getInputStream());
            h.disconnect();
            return new JSONObject(res).getJSONObject("content").getString("sha");
        } catch (org.json.JSONException e) { throw new IOException(e); }
    }

    /** Write a file whatever its current version is (used for the children's ticks, where the last write wins). */
    static void putLatest(String token, String path, String content) throws IOException {
        put(token, path, content, shaOf(token, path));
    }

    static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        for (int n = in.read(chunk); n > 0; n = in.read(chunk)) buf.write(chunk, 0, n);
        return buf.toString("UTF-8");
    }
}
