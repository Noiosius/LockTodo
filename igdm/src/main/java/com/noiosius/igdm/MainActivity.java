package com.noiosius.igdm;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
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
                if (!isInstagramHost(uri.getHost())) return;

                String path = safePath(uri);
                if (!isDirectPath(path) && !isAuthPath(path)) {
                    view.loadUrl(DIRECT_URL);
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

        String path = safePath(uri);
        if (isDirectPath(path) || isAuthPath(path)) {
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

    private String safePath(Uri uri) {
        String path = uri.getPath();
        return path == null || path.isEmpty() ? "/" : path;
    }

    private boolean isDirectPath(String path) {
        return path.startsWith("/direct/");
    }

    private boolean isAuthPath(String path) {
        return path.startsWith("/accounts/")
                || path.startsWith("/challenge/")
                || path.startsWith("/two_factor/")
                || path.startsWith("/oauth/");
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
