package dk.mrsilly.kiosk;

import android.content.Context;
import android.content.SharedPreferences;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import java.util.Map;
import org.json.JSONObject;

/** `Lists` in the built-in Lister page: call(id, op, argJson) runs on a thread and answers with window.__listsDone(id, ok, resultJson).
 *  ops: "snapshot" -> {"files": {path: {"sha", "c"}}}; "put" {path, content, sha} -> {"sha"}. */
final class ListsBridge {
    private final Context ctx;
    private final WebView web;

    ListsBridge(Context ctx, WebView web) { this.ctx = ctx.getApplicationContext(); this.web = web; }

    @JavascriptInterface public void call(int id, String op, String arg) {
        new Thread(() -> {
            boolean ok = false;
            String res;
            try {
                SharedPreferences p = ctx.getSharedPreferences("kiosk", Context.MODE_PRIVATE);
                String token = p.getString("reportToken", "").trim();
                if (token.isEmpty()) throw new Exception("Ingen token i Indstillinger");
                JSONObject a = new JSONObject(arg == null ? "{}" : arg);
                if ("snapshot".equals(op)) {
                    JSONObject files = new JSONObject();
                    for (Map.Entry<String, ListsRepo.Item> e : ListsRepo.snapshot(token).entrySet())
                        files.put(e.getKey(), new JSONObject().put("sha", e.getValue().sha).put("c", e.getValue().content));
                    res = new JSONObject().put("files", files).toString();
                } else if ("put".equals(op)) {
                    res = new JSONObject().put("sha", ListsRepo.put(token, a.getString("path"), a.getString("content"), a.optString("sha", null))).toString();
                } else throw new Exception("unknown op");
                ok = true;
            } catch (Exception e) {
                res = JSONObject.quote(String.valueOf(e.getMessage())).replaceAll("^\"|\"$", "");
            }
            final String js = "window.__listsDone&&window.__listsDone(" + id + "," + ok + "," + JSONObject.quote(res) + ")";
            web.post(() -> web.evaluateJavascript(js, null));
        }).start();
    }
}
