package dk.mrsilly.kiosk;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Logs the tablet in to claude.ai (the Mr Silly account) so private pages like Lister can save and read live.
 *  Google refuses login inside a WebView, so use "Continue with email": type the address, get the link or code by mail,
 *  paste the link into the field above and press "Åbn link". The cookies live in the same CookieManager the pages use. */
public class LoginActivity extends Activity {
    private WebView web;
    private EditText link;

    /** Only claude.ai itself may be opened from the paste field or followed from the page. */
    static boolean allowed(String url) {
        try {
            java.net.URI u = new java.net.URI(url == null ? "" : url.trim());
            String h = u.getHost() == null ? "" : u.getHost().toLowerCase();
            return "https".equals(u.getScheme()) && (h.equals("claude.ai") || h.endsWith(".claude.ai"));
        } catch (Exception e) { return false; }
    }

    /** Static nested class: d8 here crashes on anonymous classes. */
    private static final class Client extends WebViewClient {
        @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) { return !allowed(r.getUrl().toString()); }
        @Override public void onPageFinished(WebView v, String url) { CookieManager.getInstance().flush(); }
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundColor(Color.WHITE);
        TextView hint = new TextView(this);
        hint.setText("1) Vælg \"Continue with email\" nedenfor og skriv mrsillybot@gmail.com.  2) Kopiér linket i mailen og indsæt det her.  3) Tryk Åbn link, og derefter Færdig.");
        hint.setTextColor(Color.BLACK);
        hint.setPadding(24, 16, 24, 8);
        col.addView(hint);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        link = new EditText(this);
        link.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        link.setHint("Indsæt link fra mailen");
        link.setSingleLine(true);
        row.addView(link, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button open = new Button(this);
        open.setText("Åbn link");
        open.setOnClickListener(v -> { String u = link.getText().toString().trim(); if (allowed(u)) web.loadUrl(u); else link.setError("Kun links til claude.ai"); });
        row.addView(open);
        Button done = new Button(this);
        done.setText("Færdig");
        done.setOnClickListener(v -> finish());
        row.addView(done);
        col.addView(row);
        web = new WebView(this);
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        web.setWebViewClient(new Client());
        col.addView(web, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(col);
        web.loadUrl("https://claude.ai/login");
    }

    @Override protected void onPause() { super.onPause(); CookieManager.getInstance().flush(); }
    @Override protected void onDestroy() { if (web != null) web.destroy(); super.onDestroy(); }
}
