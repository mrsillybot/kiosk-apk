package dk.mrsilly.kiosk;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;

/** Lister (indkøb og opgaver) on its own, for the phone: the same built-in page as the fridge carousel shows, full screen. */
public class ListerActivity extends Activity {
    private WebView web;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        web.setBackgroundColor(Color.WHITE);
        web.addJavascriptInterface(new ListsBridge(this, web), "Lists");
        setContentView(web);
        web.loadUrl("file:///android_asset/lister.html");
    }

    @Override protected void onResume() { super.onResume(); if (web != null) web.evaluateJavascript("window.__listerShown&&window.__listerShown()", null); }
    @Override protected void onDestroy() { if (web != null) web.destroy(); super.onDestroy(); }
}
