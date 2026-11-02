package dk.mrsilly.kiosk;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private FrameLayout root, stage;
    private View black;
    private Config cfg;
    private int taps = 0;
    private long firstTap = 0;

    private final Runnable tick = this::step;
    private final Runnable gate = this::checkGate;
    private static final long PEEK_MS = 3 * 60 * 1000L;
    private boolean active = false;
    private long peekUntil = 0;
    private GestureDetector swipes;
    private TextView offlineText;
    private static final long STALE_MS = 5 * 60 * 1000L;
    private static final int SLIDE_MS = 260;
    /** One WebView per page, kept alive so a swipe slides to an already loaded page (no spinner). */
    private final Map<String, Slot> slots = new HashMap<>();
    private Slot current;

    private static final class Slot {
        WebView web;
        String url;
        long loadedAt = 0;
        boolean ready = false, failed = false, alive = true;
        Slot refreshing;   // a hidden copy that is loading fresh content
        Slot replaces;     // set on that copy: the slot it will take over from
    }

    private void step() {
        if (active) nextPage();
        handler.postDelayed(tick, cfg.seconds * 1000L);
    }

    /** Swipe left = next page, right = previous; the timer starts over. Touches still reach the page. */
    void onSwipe(int dir) {
        if (!active) return;
        stepPage(dir);
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, cfg.seconds * 1000L);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        swipes.onTouchEvent(ev);
        return super.dispatchTouchEvent(ev);
    }

    /** Detects a fast, mostly horizontal swipe. */
    private static final class SwipeListener extends GestureDetector.SimpleOnGestureListener {
        private final MainActivity a;
        SwipeListener(MainActivity a) { this.a = a; }
        @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
            if (e1 == null || e2 == null) return false;
            float dx = e2.getX() - e1.getX(), dy = e2.getY() - e1.getY();
            if (Math.abs(dx) > 150 && Math.abs(dx) > 2 * Math.abs(dy) && Math.abs(vx) > 600) {
                a.onSwipe(dx < 0 ? 1 : -1);
            }
            return false;
        }
    }

    /** Reports load results per slot. (Static nested class: d8 8.2 crashes on inner classes here.) */
    private static final class BarClient extends WebViewClient {
        private final MainActivity a;
        private final Slot slot;
        private boolean failed = false;
        BarClient(MainActivity a, Slot slot) { this.a = a; this.slot = slot; }

        @Override public void onReceivedError(WebView v, int code, String desc, String failingUrl) {
            if (failingUrl != null && failingUrl.startsWith(slot.url)) failed = true;
        }

        @Override public void onPageFinished(WebView v, String url) {
            boolean f = failed;
            failed = false;
            a.onSlotFinished(slot, f);
            if (f) return;
            hideClaudeBar(v, url);
            for (int ms : new int[] { 1000, 3000, 8000 }) {   // the shell builds its bar a little later
                v.postDelayed(() -> { if (slot.alive) hideClaudeBar(v, v.getUrl()); }, ms);
            }
        }
    }

    /** Pages can call KioskBridge.skip() to be left out for the rest of the day (Max' page does when he isn't here). */
    private static final class Bridge {
        private final MainActivity a;
        private final Slot slot;
        Bridge(MainActivity a, Slot slot) { this.a = a; this.slot = slot; }
        @JavascriptInterface public void skip() { a.runOnUiThread(() -> a.skipSlot(slot, 0)); }
        /** Leave this page out until the given minute of today (e.g. 720 = 12:00): the children are not here this morning but come after school. */
        @JavascriptInterface public void skipUntil(int minuteOfDay) { a.runOnUiThread(() -> a.skipSlot(slot, minuteOfDay)); }
        /** The child ticked a chore on or off: {"v":1,"d":date,"c":child,"t":chore,"on":bool,"at":ms}; queued and sent to GitHub. */
        @JavascriptInterface public void report(String json) { Reporter.enqueue(a.getApplicationContext(), json); }
    }

    private Slot newSlot(String url, Slot replaces) {
        Slot s = new Slot();
        s.url = url;
        s.replaces = replaces;
        WebView w = new WebView(this);
        WebSettings ws = w.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setLoadWithOverviewMode(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, true);
        w.addJavascriptInterface(new Bridge(this, s), "KioskBridge");
        w.setWebViewClient(new BarClient(this, s));
        w.setBackgroundColor(Color.BLACK);
        w.setVisibility(View.INVISIBLE);
        s.web = w;
        stage.addView(w, new FrameLayout.LayoutParams(-1, -1));
        w.addJavascriptInterface(new ListsBridge(this, w), "Lists");
        w.loadUrl(Config.realUrl(url));
        return s;
    }

    private void destroySlot(Slot s) {
        s.alive = false;
        s.web.animate().cancel();
        stage.removeView(s.web);
        s.web.destroy();
        if (s.refreshing != null) { s.refreshing.replaces = null; destroySlot(s.refreshing); s.refreshing = null; }
    }

    private void resetSlots() {
        for (Slot s : slots.values()) destroySlot(s);
        slots.clear();
        current = null;
    }

    void onSlotFinished(Slot s, boolean failed) {
        if (!s.alive) return;
        if (failed) {
            if (s.replaces != null) {                      // a failed background refresh: keep the old page
                s.replaces.refreshing = null;
                destroySlot(s);
                return;
            }
            s.failed = true;
            handler.postDelayed(() -> { if (s.alive && !s.ready && active) s.web.loadUrl(Config.realUrl(s.url)); }, 20000);
            updateOffline();
            return;
        }
        s.failed = false;
        s.ready = true;
        s.loadedAt = System.currentTimeMillis();
        if (s.replaces != null) swap(s);
        updateOffline();
    }

    /** A fresh copy finished loading: fade it in over the old one (or swap silently when the old one is hidden). */
    private void swap(Slot fresh) {
        Slot old = fresh.replaces;
        fresh.replaces = null;
        old.refreshing = null;
        if (slots.get(fresh.url) != old) { destroySlot(fresh); return; }
        slots.put(fresh.url, fresh);
        if (current == old) {
            current = fresh;
            fresh.web.setTranslationX(0);
            fresh.web.setAlpha(0f);
            fresh.web.setVisibility(View.VISIBLE);
            fresh.web.animate().alpha(1f).setDuration(300).withEndAction(() -> destroySlot(old)).start();
        } else {
            destroySlot(old);
        }
    }

    private void updateOffline() {
        boolean off = current != null && !current.ready && current.failed;
        offlineText.setVisibility(off ? View.VISIBLE : View.GONE);
    }

    /** The page told us it should not be shown today. */
    void skipSlot(Slot s, int untilMinute) {
        if (!s.alive) return;
        Calendar now = Calendar.getInstance();
        List<String> before = cfg.activeUrls(now);
        int pos = before.indexOf(s.url);
        if (untilMinute > 0 && untilMinute <= 24 * 60) {
            Calendar until = (Calendar) now.clone();
            until.set(Calendar.HOUR_OF_DAY, untilMinute / 60); until.set(Calendar.MINUTE, untilMinute % 60);
            until.set(Calendar.SECOND, 0); until.set(Calendar.MILLISECOND, 0);
            if (until.getTimeInMillis() > now.getTimeInMillis()) Config.skipUntil(this, s.url, until.getTimeInMillis());
            else Config.skipToday(this, s.url, now);
        } else Config.skipToday(this, s.url, now);
        cfg = Config.load(this);
        boolean wasCurrent = current == s;
        slots.remove(s.url);
        if (wasCurrent) current = null;
        destroySlot(s);
        List<String> urls = cfg.activeUrls(now);
        if (urls.isEmpty()) { black.setVisibility(View.VISIBLE); return; }
        if (wasCurrent) {
            show(urls.get(Math.max(0, pos) % urls.size()), 1);
            preload(urls, urls.indexOf(current.url));
        } else if (current != null) {
            preload(urls, Math.max(0, urls.indexOf(current.url)));
        }
    }

    private void ensureFresh(String url) {
        Slot s = slots.get(url);
        if (s == null) { slots.put(url, newSlot(url, null)); return; }
        if (s.ready && s.refreshing == null && System.currentTimeMillis() - s.loadedAt > STALE_MS) {
            s.refreshing = newSlot(url, s);
        }
    }

    /** Keeps the shown page and its two neighbours loaded and fresh; drops the rest. */
    private void preload(List<String> urls, int at) {
        Set<String> keep = new HashSet<>();
        for (int d = -1; d <= 1; d++) keep.add(urls.get(Math.floorMod(at + d, urls.size())));
        for (String u : keep) ensureFresh(u);
        for (Iterator<Map.Entry<String, Slot>> it = slots.entrySet().iterator(); it.hasNext();) {
            Slot s = it.next().getValue();
            if (!keep.contains(s.url) && s != current) { it.remove(); destroySlot(s); }
        }
    }

    /** Slides `url` in from the swipe direction (dir +1 = from the right) while the old page slides out. */
    private void show(String url, int dir) {
        Slot in = slots.get(url);
        if (in == null) { in = newSlot(url, null); slots.put(url, in); }
        Slot out = current;
        current = in;
        in.web.animate().cancel();
        in.web.bringToFront();
        in.web.setVisibility(View.VISIBLE);
        in.web.setAlpha(1f);
        int w = stage.getWidth();
        if (out != null && out != in && w > 0) {
            out.web.animate().cancel();
            in.web.setTranslationX(dir * w);
            in.web.animate().translationX(0).setDuration(SLIDE_MS).start();
            out.web.animate().translationX(-dir * w).setDuration(SLIDE_MS)
                .withEndAction(() -> { if (out.alive && current != out) out.web.setVisibility(View.INVISIBLE); }).start();
        } else {
            in.web.setTranslationX(0);
            if (out != null && out != in) out.web.setVisibility(View.INVISIBLE);
        }
        updateOffline();
    }

    /** Hides the Claude bar on top of an artifact page (the shell has a built-in data-header-hidden switch). */
    private static final String HIDE_BAR_JS = "(function(){var d=document.documentElement;d.setAttribute('data-header-hidden','');"
        + "if(!document.getElementById('kiosk-css')){var s=document.createElement('style');s.id='kiosk-css';"
        + "s.textContent='#hdr,#hdr-degraded,#top-edge{display:none!important}body>main{top:0!important;inset:0!important}';"
        + "document.head.appendChild(s);}})();";

    static void hideClaudeBar(WebView v, String url) {
        if (url != null && url.contains("claude.ai/")) v.evaluateJavascript(HIDE_BAR_JS, null);
    }

    /** Inside a window, or briefly after someone woke the tablet / touched the black screen. */
    private boolean isOn() {
        return cfg.isActive(Calendar.getInstance()) || System.currentTimeMillis() < peekUntil;
    }

    private void peek() {
        peekUntil = System.currentTimeMillis() + PEEK_MS;
        active = true;
        applyActive();
        nextPage();
    }

    private void applyBrightness() {
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = cfg.isDim(Calendar.getInstance()) ? Math.max(0.05f, cfg.dimLevel / 100f)
            : WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        getWindow().setAttributes(lp);
    }

    private void applyPinning() {
        try {
            if (cfg.pin) startLockTask(); else stopLockTask();
        } catch (Exception ignored) { }
    }

    /** Every 30 s: switch the screen on/off when a window starts or ends. */
    private void checkGate() {
        cfg = Config.load(this);
        applyBrightness();
        if (Updater.due(this)) new Thread(() -> Updater.check(getApplicationContext(), false)).start();
        boolean now = isOn();
        if (now != active) {
            active = now;
            applyActive();
            if (active) stepPage(0);
        }
        handler.postDelayed(gate, 30000);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        stage = new FrameLayout(this);
        root.addView(stage, new FrameLayout.LayoutParams(-1, -1));

        offlineText = new TextView(this);
        offlineText.setText("Ingen forbindelse – prøver igen …");
        offlineText.setTextColor(Color.GRAY);
        offlineText.setTextSize(22);
        offlineText.setGravity(Gravity.CENTER);
        offlineText.setVisibility(View.GONE);
        root.addView(offlineText, new FrameLayout.LayoutParams(-1, -1));

        black = new View(this);
        black.setBackgroundColor(Color.BLACK);
        black.setVisibility(View.GONE);
        black.setClickable(true);
        black.setOnClickListener(v -> peek());
        root.addView(black, new FrameLayout.LayoutParams(-1, -1));

        // Hidden settings door: tap the bottom-right corner 5 times within 4 seconds.
        View corner = new View(this);
        int size = (int) (72 * getResources().getDisplayMetrics().density);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size, Gravity.BOTTOM | Gravity.END);
        corner.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                long now = System.currentTimeMillis();
                if (now - firstTap > 4000) { firstTap = now; taps = 0; }
                if (++taps >= 5) { taps = 0; startActivity(new Intent(this, SettingsActivity.class)); }
            }
            return true;
        });
        root.addView(corner, lp);
        swipes = new GestureDetector(this, new SwipeListener(this));
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        cfg = Config.load(this);
        hideSystemUi();
        applyBrightness();
        applyPinning();
        setRequestedOrientation(cfg.orientation == 1 ? android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            : cfg.orientation == 2 ? android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            : android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR);
        handler.removeCallbacks(tick);
        handler.removeCallbacks(gate);
        if (!cfg.hasAnyPage()) {
            startActivity(new Intent(this, SettingsActivity.class));
            return;
        }
        if (!cfg.isActive(Calendar.getInstance())) peekUntil = System.currentTimeMillis() + PEEK_MS;
        active = isOn();
        applyActive();
        List<String> shown = cfg.activeUrls(Calendar.getInstance());
        if (current != null && !shown.contains(current.url)) resetSlots();   // settings changed under us
        if (active) stepPage(0);
        handler.postDelayed(tick, cfg.seconds * 1000L);
        handler.postDelayed(gate, 30000);
        new Thread(() -> Updater.check(getApplicationContext(), false)).start();
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(tick);
        handler.removeCallbacks(gate);
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (focus) hideSystemUi();
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    /** Keep the screen on and show the page, or go black and let the system sleep until the next window. */
    private void applyActive() {
        if (!active) {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            black.setVisibility(View.VISIBLE);
            scheduleWake();
            return;
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        black.setVisibility(View.GONE);
    }

    /** One step of the carousel (only while inside a window). */
    private void nextPage() { stepPage(1); }

    /** Moves the carousel one page forward (+1) or back (-1); 0 keeps the current page (and shows the first one if none). */
    void stepPage(int dir) {
        List<String> urls = cfg.activeUrls(Calendar.getInstance());
        if (urls.isEmpty()) { black.setVisibility(View.VISIBLE); return; }
        black.setVisibility(View.GONE);
        int n = urls.size();
        int i = current == null ? -1 : urls.indexOf(current.url);
        int ni = i < 0 ? (dir >= 0 ? 0 : n - 1) : Math.floorMod(i + dir, n);
        String url = urls.get(ni);
        if (current == null || !url.equals(current.url)) show(url, dir >= 0 ? 1 : -1);
        preload(urls, ni);
    }

    private void scheduleWake() {
        long at = cfg.nextStart(Calendar.getInstance());
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = PendingIntent.getBroadcast(this, 1, new Intent(this, BootReceiver.class),
            PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        if (Build.VERSION.SDK_INT >= 23) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        else am.set(AlarmManager.RTC_WAKEUP, at, pi);
    }

    @Override
    protected void onDestroy() {
        resetSlots();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() { /* kiosk: back does nothing */ }
}
