package com.noiosius.igdm;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    private static final String DIRECT_URL = "https://www.instagram.com/direct/inbox/";
    private WebView webView;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.setStatusBarColor(Color.BLACK);
        window.setNavigationBarColor(Color.BLACK);
        window.getDecorView().setSystemUiVisibility(0);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.BLACK);
        setContentView(webView);

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, true);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleNavigation(view, request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleNavigation(view, Uri.parse(url));
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Uri uri = Uri.parse(url);
                if (isInstagramHost(uri.getHost()) && isDirectPath(uri.getPath())) {
                    injectMinimalUi(view);
                } else if (isInstagramHost(uri.getHost()) && shouldReturnToDirect(uri.getPath())) {
                    view.loadUrl(DIRECT_URL);
                }
            }
        });

        if (savedInstanceState == null) {
            webView.loadUrl(DIRECT_URL);
        } else {
            webView.restoreState(savedInstanceState);
        }
    }

    private boolean handleNavigation(WebView view, Uri uri) {
        if (uri == null) return true;
        String scheme = uri.getScheme();
        String host = uri.getHost();
        String path = uri.getPath();

        if ("about".equals(scheme) || "data".equals(scheme) || "blob".equals(scheme)) {
            return false;
        }

        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            return true;
        }

        if (!isInstagramHost(host)) {
            return true;
        }

        if (isAllowedInstagramPath(path)) {
            return false;
        }

        view.loadUrl(DIRECT_URL);
        return true;
    }

    private boolean isInstagramHost(String host) {
        if (host == null) return false;
        return "instagram.com".equals(host)
                || "www.instagram.com".equals(host)
                || host.endsWith(".instagram.com");
    }

    private boolean isAllowedInstagramPath(String path) {
        if (path == null) return false;
        return isDirectPath(path)
                || path.startsWith("/accounts/")
                || path.startsWith("/challenge/")
                || path.startsWith("/two_factor/")
                || path.startsWith("/oauth/");
    }

    private boolean isDirectPath(String path) {
        return path != null && path.startsWith("/direct/");
    }

    private boolean shouldReturnToDirect(String path) {
        if (path == null) return false;
        return !isAllowedInstagramPath(path);
    }

    private void injectMinimalUi(WebView view) {
        String script =
                "(function(){" +
                "if(window.__igdm_minimal)return;window.__igdm_minimal=true;" +
                "var style=document.createElement('style');" +
                "style.textContent=\"a[href='/'],a[href^='/explore'],a[href^='/reels'],a[href^='/accounts/activity'],a[href^='/create'],a[href^='/stories']{display:none!important;visibility:hidden!important;}\";" +
                "document.documentElement.appendChild(style);" +
                "var clean=function(){" +
                "document.querySelectorAll('a[href]').forEach(function(a){" +
                "try{var p=new URL(a.href,location.origin).pathname;" +
                "if(p==='/'||p.indexOf('/explore')===0||p.indexOf('/reels')===0||p.indexOf('/accounts/activity')===0||p.indexOf('/create')===0||p.indexOf('/stories')===0){" +
                "a.style.setProperty('display','none','important');" +
                "a.style.setProperty('visibility','hidden','important');" +
                "}}catch(e){}" +
                "});" +
                "};clean();new MutationObserver(clean).observe(document.documentElement,{childList:true,subtree:true});" +
                "})();";
        view.evaluateJavascript(script, null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            String url = webView.getUrl();
            if (url == null || url.isEmpty()) {
                webView.loadUrl(DIRECT_URL);
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (webView != null) webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
