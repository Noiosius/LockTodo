package com.noiosius.igdm;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.WebBackForwardList;
import android.webkit.WebChromeClient;
import android.webkit.WebHistoryItem;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    private static final String DIRECT_URL = "https://www.instagram.com/direct/";

    private WebView webView;
    private String lastDirectUrl = DIRECT_URL;
    private boolean restoringDirect = false;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.setStatusBarColor(Color.BLACK);
        window.setNavigationBarColor(Color.BLACK);
        window.getDecorView().setSystemUiVisibility(0);
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(true);
        }

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
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                handleVisitedUrl(view, url);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleNavigation(view, request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleNavigation(view, Uri.parse(url));
            }

            @Override
            public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                super.doUpdateVisitedHistory(view, url, isReload);
                handleVisitedUrl(view, url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                handleVisitedUrl(view, url);

                if (isAllowedDirectUrl(url)) {
                    lastDirectUrl = normalizeDirectUrl(url);
                    restoringDirect = false;
                    injectDirectOnlyGuard(view);
                }
            }
        });

        webView.loadUrl(DIRECT_URL);
    }

    private boolean handleNavigation(WebView view, Uri uri) {
        if (uri == null) return true;

        String scheme = uri.getScheme();
        if ("about".equals(scheme) || "data".equals(scheme) || "blob".equals(scheme)) {
            return false;
        }

        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            return true;
        }

        if (!isInstagramHost(uri.getHost())) {
            return true;
        }

        if (isDirectPath(safePath(uri))) {
            lastDirectUrl = normalizeDirectUrl(uri.toString());
            restoringDirect = false;
            return false;
        }

        restoreLastDirect(view);
        return true;
    }

    private void handleVisitedUrl(WebView view, String url) {
        if (url == null || url.isEmpty()) return;

        Uri uri;
        try {
            uri = Uri.parse(url);
        } catch (Exception ignored) {
            restoreLastDirect(view);
            return;
        }

        String scheme = uri.getScheme();
        if ("about".equals(scheme) || "data".equals(scheme) || "blob".equals(scheme)) return;

        if (!isInstagramHost(uri.getHost()) || !isDirectPath(safePath(uri))) {
            restoreLastDirect(view);
            return;
        }

        lastDirectUrl = normalizeDirectUrl(url);
        restoringDirect = false;
        injectDirectOnlyGuard(view);
    }

    private void restoreLastDirect(WebView view) {
        if (view == null || restoringDirect) return;

        String target = isAllowedDirectUrl(lastDirectUrl)
                ? normalizeDirectUrl(lastDirectUrl)
                : DIRECT_URL;

        String current = view.getUrl();
        if (current != null && normalizeDirectUrl(current).equals(target)) {
            restoringDirect = false;
            return;
        }

        restoringDirect = true;
        view.stopLoading();
        view.loadUrl(target);
    }

    private boolean isAllowedDirectUrl(String url) {
        if (url == null || url.isEmpty()) return false;
        try {
            Uri uri = Uri.parse(url);
            return isInstagramHost(uri.getHost()) && isDirectPath(safePath(uri));
        } catch (Exception ignored) {
            return false;
        }
    }

    private String normalizeDirectUrl(String url) {
        if (!isAllowedDirectUrl(url)) return DIRECT_URL;
        return url;
    }

    private void injectDirectOnlyGuard(WebView view) {
        if (view == null) return;

        String safeLast = escapeJs(lastDirectUrl);
        String safeFallback = escapeJs(DIRECT_URL);

        String script =
                "(function(){" +
                "var FALLBACK='" + safeFallback + "';" +
                "var allowed=function(u){" +
                "try{" +
                "var x=new URL(u,location.href);" +
                "var h=x.hostname;" +
                "var host=(h==='instagram.com'||h==='www.instagram.com'||h.endsWith('.instagram.com'));" +
                "return host&&x.pathname.indexOf('/direct/')===0;" +
                "}catch(e){return false;}" +
                "};" +
                "if(allowed(location.href)){window.__dmOnlyLast=location.href;}" +
                "else if(!window.__dmOnlyLast){window.__dmOnlyLast='" + safeLast + "';}" +
                "var restore=function(){" +
                "var target=allowed(window.__dmOnlyLast)?window.__dmOnlyLast:FALLBACK;" +
                "if(!allowed(location.href)){location.replace(target);}" +
                "};" +
                "if(window.__dmOnlyInstalled){restore();return;}" +
                "window.__dmOnlyInstalled=true;" +

                "document.addEventListener('click',function(e){" +
                "try{" +
                "var t=e.target;" +
                "var a=t&&t.closest?t.closest('a[href]'):null;" +
                "if(a&&!allowed(a.href)){" +
                "e.preventDefault();" +
                "e.stopPropagation();" +
                "e.stopImmediatePropagation();" +
                "return false;" +
                "}" +
                "}catch(x){}" +
                "},true);" +

                "var wrap=function(name){" +
                "var original=history[name];" +
                "history[name]=function(state,title,url){" +
                "if(url!=null){" +
                "try{" +
                "var absolute=new URL(url,location.href).href;" +
                "if(!allowed(absolute)){return;}" +
                "window.__dmOnlyLast=absolute;" +
                "}catch(e){return;}" +
                "}" +
                "return original.apply(this,arguments);" +
                "};" +
                "};" +
                "wrap('pushState');wrap('replaceState');" +

                "window.addEventListener('popstate',function(){" +
                "setTimeout(function(){" +
                "if(allowed(location.href)){window.__dmOnlyLast=location.href;}else{restore();}" +
                "},0);" +
                "});" +

                "setInterval(function(){" +
                "if(allowed(location.href)){window.__dmOnlyLast=location.href;}else{restore();}" +
                "},120);" +
                "})();";

        view.evaluateJavascript(script, null);
    }

    private String escapeJs(String value) {
        if (value == null) return "";
        return value
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\r", "")
                .replace("\n", "");
    }

    private boolean isInstagramHost(String host) {
        if (host == null) return false;
        return "instagram.com".equals(host)
                || "www.instagram.com".equals(host)
                || host.endsWith(".instagram.com");
    }

    private String safePath(Uri uri) {
        String path = uri.getPath();
        return path == null || path.isEmpty() ? "/" : path;
    }

    private boolean isDirectPath(String path) {
        return path != null && path.startsWith("/direct/");
    }

    @Override
    public void onBackPressed() {
        if (webView == null) {
            finish();
            return;
        }

        WebBackForwardList list = webView.copyBackForwardList();
        int current = list.getCurrentIndex();

        for (int i = current - 1; i >= 0; i--) {
            WebHistoryItem item = list.getItemAtIndex(i);
            if (item != null && isAllowedDirectUrl(item.getUrl())) {
                webView.goBackOrForward(i - current);
                return;
            }
        }

        finish();
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
