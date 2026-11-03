package com.netmon.app;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {

    private static final String SETUP = "file:///android_asset/setup.html";
    // small floating gear button injected into the server pages, to change the server address
    private static final String GEAR =
        "(function(){if(document.getElementById('nmgear'))return;"
        + "var b=document.createElement('button');b.id='nmgear';b.textContent='\\u2699';"
        + "b.setAttribute('aria-label','Change server');"
        + "b.style.cssText='position:fixed;right:10px;bottom:calc(10px + env(safe-area-inset-bottom));"
        + "z-index:99;width:40px;height:40px;border-radius:50%;border:0;background:rgba(100,116,139,.55);"
        + "color:#fff;font-size:20px';"
        + "b.onclick=function(){Android.reset()};document.body.appendChild(b)})();";

    private WebView web;
    private SharedPreferences sp;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window w = getWindow();
        w.setStatusBarColor(Color.parseColor("#0F172A"));
        w.setNavigationBarColor(Color.parseColor("#0F172A"));

        sp = getSharedPreferences("netmon", MODE_PRIVATE);
        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        CookieManager.getInstance().setAcceptCookie(true);

        web.addJavascriptInterface(new Bridge(), "Android");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                if (r.isForMainFrame()) v.loadUrl(SETUP + "?err=1");
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                CookieManager.getInstance().flush();
                if (url != null && url.startsWith("http")) v.evaluateJavascript(GEAR, null);
            }
        });
        load();
    }

    private void load() {
        String u = sp.getString("url", "");
        web.loadUrl(u.isEmpty() ? SETUP : u);
    }

    @Override
    public void onBackPressed() {
        String u = web.getUrl();
        boolean home = u != null && u.matches(".*/m(\\?.*)?(#.*)?$");
        if (!home && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
    }

    @Override
    protected void onPause() {
        CookieManager.getInstance().flush();
        web.onPause();
        super.onPause();
    }

    private class Bridge {
        @JavascriptInterface
        public String saved() {
            return sp.getString("addr", "");
        }

        @JavascriptInterface
        public void save(String input) {
            String t = input == null ? "" : input.trim();
            if (t.isEmpty()) return;
            if (!t.matches("(?i)^https?://.*")) t = "http://" + t;
            while (t.endsWith("/")) t = t.substring(0, t.length() - 1);
            String path = Uri.parse(t).getPath();
            final String url = (path == null || path.isEmpty()) ? t + "/m" : t;
            sp.edit().putString("addr", input.trim()).putString("url", url).apply();
            runOnUiThread(() -> web.loadUrl(url));
        }

        @JavascriptInterface
        public void retry() {
            runOnUiThread(() -> load());
        }

        @JavascriptInterface
        public void reset() {
            runOnUiThread(() -> web.loadUrl(SETUP));
        }
    }
}
