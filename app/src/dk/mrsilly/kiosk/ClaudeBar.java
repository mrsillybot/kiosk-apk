package dk.mrsilly.kiosk;

import android.webkit.WebView;

/** Hides the Claude bar on top of an artifact page (the shell has a built-in data-header-hidden switch). Shared by the kiosk and the phone app. */
final class ClaudeBar {
    private ClaudeBar() { }

    private static final String HIDE_BAR_JS = "(function(){var d=document.documentElement;d.setAttribute('data-header-hidden','');"
        + "if(!document.getElementById('kiosk-css')){var s=document.createElement('style');s.id='kiosk-css';"
        + "s.textContent='#hdr,#hdr-degraded,#top-edge{display:none!important}body>main{top:0!important;inset:0!important}';"
        + "document.head.appendChild(s);}})();";

    static void hide(WebView v, String url) {
        if (url != null && url.contains("claude.ai/")) v.evaluateJavascript(HIDE_BAR_JS, null);
    }
}
