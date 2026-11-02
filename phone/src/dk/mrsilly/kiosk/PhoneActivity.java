package dk.mrsilly.kiosk;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

/** The phone app: Alex' brief and the lists (indkøb, opgaver, tal) with a bar of buttons at the bottom.
 *  Lists = the built-in page app/assets/lister.html in app mode; Brief = Alex' public brief artifact with the Claude bar hidden. */
public class PhoneActivity extends Activity {
    static final String BRIEF_URL = "https://claude.ai/artifact/18FB2Uw8GYtvVSfGyvVSzu";
    private static final long BRIEF_STALE_MS = 10 * 60 * 1000L;
    private static final String[][] TABS = { {"Indkøb", "indkob"}, {"Brief", "brief"}, {"Opgaver", "opgaver"}, {"Tal", "tal"} };

    private WebView lists, brief;
    private final Button[] buttons = new Button[TABS.length];
    private long briefLoadedAt = 0;
    private int current = 0;

    /** Static nested class (d8 here crashes on anonymous classes): hides the Claude bar on the brief page. */
    private static final class BriefClient extends WebViewClient {
        @Override public void onPageFinished(WebView v, String url) {
            ClaudeBar.hide(v, url);
            for (int ms : new int[] { 800, 2500 }) v.postDelayed(() -> ClaudeBar.hide(v, v.getUrl()), ms);
        }
    }

    private static WebView web(Activity a) {
        WebView w = new WebView(a);
        WebSettings ws = w.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        w.setBackgroundColor(Color.WHITE);
        return w;
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        FrameLayout content = new FrameLayout(this);
        lists = web(this);
        lists.addJavascriptInterface(new ListsBridge(this, lists), "Lists");
        brief = web(this);
        brief.setWebViewClient(new BriefClient());
        content.addView(lists, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        content.addView(brief, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(0xFFEEF2EC);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            Button t = new Button(this);
            t.setText(TABS[i][0]);
            t.setAllCaps(false);
            t.setTextSize(14);
            t.setBackgroundColor(Color.TRANSPARENT);
            t.setOnClickListener(v -> select(idx));
            buttons[i] = t;
            bar.addView(t, new LinearLayout.LayoutParams(0, (int) (56 * getResources().getDisplayMetrics().density), 1f));
        }
        Button gear = new Button(this);
        gear.setText("⚙");
        gear.setBackgroundColor(Color.TRANSPARENT);
        gear.setOnClickListener(v -> startActivity(new Intent(this, PhoneSettingsActivity.class)));
        bar.addView(gear, new LinearLayout.LayoutParams((int) (48 * getResources().getDisplayMetrics().density), (int) (56 * getResources().getDisplayMetrics().density)));
        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(root);
        lists.loadUrl("file:///android_asset/lister.html?app#indkob");
        select(b == null ? 0 : b.getInt("tab", 0));
        new Thread(() -> Updater.check(getApplicationContext(), false)).start();
    }

    private void select(int idx) {
        current = idx;
        boolean isBrief = "brief".equals(TABS[idx][1]);
        brief.setVisibility(isBrief ? View.VISIBLE : View.INVISIBLE);
        lists.setVisibility(isBrief ? View.INVISIBLE : View.VISIBLE);
        if (isBrief) {
            if (System.currentTimeMillis() - briefLoadedAt > BRIEF_STALE_MS) { brief.loadUrl(BRIEF_URL); briefLoadedAt = System.currentTimeMillis(); }
        } else {
            lists.evaluateJavascript("location.hash='#" + TABS[idx][1] + "';window.__listerShown&&window.__listerShown()", null);
        }
        for (int i = 0; i < buttons.length; i++) {
            buttons[i].setTextColor(i == idx ? 0xFF2F7F77 : 0xFF66726B);
            buttons[i].setTypeface(null, i == idx ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        }
    }

    @Override protected void onSaveInstanceState(Bundle b) { super.onSaveInstanceState(b); b.putInt("tab", current); }

    @Override protected void onResume() {
        super.onResume();
        if (lists != null) lists.evaluateJavascript("window.__listerShown&&window.__listerShown()", null);
        if (brief != null && "brief".equals(TABS[current][1]) && System.currentTimeMillis() - briefLoadedAt > BRIEF_STALE_MS) { brief.loadUrl(BRIEF_URL); briefLoadedAt = System.currentTimeMillis(); }
    }

    @Override public void onBackPressed() {
        if ("brief".equals(TABS[current][1]) && brief.canGoBack()) brief.goBack(); else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (lists != null) lists.destroy();
        if (brief != null) brief.destroy();
        super.onDestroy();
    }
}
