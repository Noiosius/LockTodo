package com.noiosius.igdm;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.LinearLayout;

public class MainActivity extends Activity {
    private static final String DIRECT_URL = "https://www.instagram.com/direct/inbox/";
    private static final String HOME_URL = "https://www.instagram.com/";

    private static final int MODE_DM = 0;
    private static final int MODE_STORY = 1;
    private static final int MODE_PROFILE = 2;

    private WebView webView;
    private ImageButton dmButton;
    private ImageButton storyButton;
    private ImageButton profileButton;
    private int mode = MODE_DM;
    private boolean profileBootstrap = false;

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

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.BLACK);
        root.addView(webView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setBackgroundColor(Color.rgb(8, 10, 15));
        nav.setPadding(0, 0, 0, 0);

        dmButton = makeNavButton(R.drawable.nav_dm);
        storyButton = makeNavButton(R.drawable.nav_story);
        profileButton = makeNavButton(R.drawable.nav_profile);

        nav.addView(dmButton, navParams());
        nav.addView(storyButton, navParams());
        nav.addView(profileButton, navParams());
        root.addView(nav, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(50)
        ));

        setContentView(root);

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
                handleFinishedPage(view, Uri.parse(url));
            }
        });

        dmButton.setOnClickListener(v -> openDm());
        storyButton.setOnClickListener(v -> openStories());
        profileButton.setOnClickListener(v -> openProfile());

        if (savedInstanceState == null) {
            openDm();
        } else {
            mode = savedInstanceState.getInt("mode", MODE_DM);
            profileBootstrap = savedInstanceState.getBoolean("profileBootstrap", false);
            webView.restoreState(savedInstanceState);
            updateNavState();
        }
    }

    private ImageButton makeNavButton(int drawable) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(drawable);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
        button.setPadding(dp(18), dp(10), dp(18), dp(10));
        return button;
    }

    private LinearLayout.LayoutParams navParams() {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
    }

    private void openDm() {
        mode = MODE_DM;
        profileBootstrap = false;
        updateNavState();
        webView.loadUrl(DIRECT_URL);
    }

    private void openStories() {
        mode = MODE_STORY;
        profileBootstrap = false;
        updateNavState();
        webView.loadUrl(HOME_URL);
    }

    private void openProfile() {
        mode = MODE_PROFILE;
        profileBootstrap = true;
        updateNavState();
        webView.loadUrl(HOME_URL);
    }

    private void updateNavState() {
        dmButton.setAlpha(mode == MODE_DM ? 1f : 0.42f);
        storyButton.setAlpha(mode == MODE_STORY ? 1f : 0.42f);
        profileButton.setAlpha(mode == MODE_PROFILE ? 1f : 0.42f);
    }

    private boolean handleNavigation(WebView view, Uri uri) {
        if (uri == null) return true;

        String scheme = uri.getScheme();
        String host = uri.getHost();
        String path = safePath(uri);

        if ("about".equals(scheme) || "data".equals(scheme) || "blob".equals(scheme)) {
            return false;
        }

        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            return true;
        }

        if (!isInstagramHost(host)) {
            return true;
        }

        if (isAuthPath(path)) {
            return false;
        }

        if (mode == MODE_DM) {
            if (isDirectPath(path) || isSingleReelPath(path)) {
                return false;
            }
            view.loadUrl(DIRECT_URL);
            return true;
        }

        if (mode == MODE_STORY) {
            if ("/".equals(path) || isStoryPath(path)) {
                return false;
            }
            view.loadUrl(HOME_URL);
            return true;
        }

        if (mode == MODE_PROFILE) {
            if (profileBootstrap && "/".equals(path)) {
                return false;
            }
            if (isLikelyProfilePath(path)) {
                profileBootstrap = false;
                return false;
            }
            if ("/".equals(path)) {
                return false;
            }
            return false;
        }

        return false;
    }

    private void handleFinishedPage(WebView view, Uri uri) {
        if (!isInstagramHost(uri.getHost())) return;
        String path = safePath(uri);

        if (isAuthPath(path)) return;

        if (mode == MODE_DM) {
            if (isDirectPath(path)) {
                injectHideInstagramBottomNav(view);
            } else if (isSingleReelPath(path)) {
                injectSingleReelLock(view);
            } else {
                view.loadUrl(DIRECT_URL);
            }
            return;
        }

        if (mode == MODE_STORY) {
            if ("/".equals(path)) {
                injectStoryOnly(view);
            } else if (isStoryPath(path)) {
                injectHideInstagramBottomNav(view);
            } else {
                view.loadUrl(HOME_URL);
            }
            return;
        }

        if (mode == MODE_PROFILE) {
            if (profileBootstrap && "/".equals(path)) {
                openOwnProfileFromHome(view);
            } else {
                injectHideInstagramBottomNav(view);
            }
        }
    }

    private void injectHideInstagramBottomNav(WebView view) {
        String script =
                "(function(){" +
                "var isNavLink=function(a){" +
                "try{" +
                "var p=new URL(a.href,location.origin).pathname;" +
                "return p==='/'||p.indexOf('/explore')===0||p.indexOf('/reels')===0||p.indexOf('/direct')===0||/^\\/[^\\/]+\\/$/.test(p);" +
                "}catch(e){return false;}" +
                "};" +
                "var hide=function(){" +
                "document.querySelectorAll('nav,[role=navigation],div').forEach(function(e){" +
                "try{" +
                "var r=e.getBoundingClientRect();" +
                "if(r.width<innerWidth*0.72||r.height<35||r.height>130||r.top<innerHeight*0.60)return;" +
                "var links=[].slice.call(e.querySelectorAll('a[href]')).filter(isNavLink);" +
                "if(links.length>=3)e.style.setProperty('display','none','important');" +
                "}catch(x){}" +
                "});" +
                "};" +
                "hide();" +
                "new MutationObserver(hide).observe(document.documentElement,{childList:true,subtree:true});" +
                "})();";
        view.evaluateJavascript(script, null);
    }

    private void injectStoryOnly(WebView view) {
        String script =
                "(function(){" +
                "var old=document.getElementById('igdm-story-cover');if(old)old.remove();" +
                "var tries=0;" +
                "var timer=setInterval(function(){" +
                "tries++;" +
                "document.querySelectorAll('article').forEach(function(a){a.style.setProperty('display','none','important');});" +
                "var top=0;" +
                "var articles=[].slice.call(document.querySelectorAll('article')).filter(function(a){var r=a.getBoundingClientRect();return r.width>0&&r.height>0;});" +
                "if(articles.length)top=articles[0].getBoundingClientRect().top;" +
                "if(!top||top<170){" +
                "var storyLinks=[].slice.call(document.querySelectorAll(\"a[href*='/stories/']\")).filter(function(a){var r=a.getBoundingClientRect();return r.width>0&&r.height>0&&r.top<innerHeight*0.45;});" +
                "if(storyLinks.length){" +
                "var max=0;storyLinks.forEach(function(a){var r=a.getBoundingClientRect();max=Math.max(max,r.bottom);});top=max+14;" +
                "}" +
                "}" +
                "if(!top||top<170)top=Math.min(330,Math.max(230,innerHeight*0.34));" +
                "var cover=document.getElementById('igdm-story-cover');" +
                "if(!cover){cover=document.createElement('div');cover.id='igdm-story-cover';document.body.appendChild(cover);}" +
                "cover.style.cssText='position:fixed;left:0;right:0;bottom:0;top:'+Math.round(top)+'px;background:#000;z-index:2147483000;pointer-events:auto;';" +
                "document.documentElement.style.setProperty('overflow','hidden','important');" +
                "document.body.style.setProperty('overflow','hidden','important');" +
                "if(tries>15)clearInterval(timer);" +
                "},180);" +
                "})();";
        view.evaluateJavascript(script, null);
        injectHideInstagramBottomNav(view);
    }

    private void openOwnProfileFromHome(WebView view) {
        String script =
                "(function(){" +
                "var tries=0;" +
                "var timer=setInterval(function(){" +
                "tries++;" +
                "var all=[].slice.call(document.querySelectorAll('a[href]'));" +
                "var target=all.find(function(a){" +
                "var s=a.querySelector(\"svg[aria-label='Profile'],svg[aria-label='프로필'],[aria-label='Profile'],[aria-label='프로필']\");" +
                "return !!s;" +
                "});" +
                "if(!target){" +
                "target=all.find(function(a){" +
                "try{" +
                "var p=new URL(a.href,location.origin).pathname;" +
                "var r=a.getBoundingClientRect();" +
                "return /^\\/[^\\/]+\\/$/.test(p)&&r.top>innerHeight*0.68&&a.querySelector('img');" +
                "}catch(e){return false;}" +
                "});" +
                "}" +
                "if(target){clearInterval(timer);location.href=target.href;}" +
                "else if(tries>35){clearInterval(timer);}" +
                "},150);" +
                "})();";
        view.evaluateJavascript(script, null);
    }

    private void injectSingleReelLock(WebView view) {
        String script =
                "(function(){" +
                "if(window.__igdm_reel_lock)return;window.__igdm_reel_lock=true;" +
                "document.documentElement.style.setProperty('overflow','hidden','important');" +
                "document.body.style.setProperty('overflow','hidden','important');" +
                "document.documentElement.style.setProperty('overscroll-behavior','none','important');" +
                "document.body.style.setProperty('overscroll-behavior','none','important');" +
                "var sy=0,sx=0;" +
                "document.addEventListener('touchstart',function(e){if(e.touches&&e.touches[0]){sx=e.touches[0].clientX;sy=e.touches[0].clientY;}},true);" +
                "document.addEventListener('touchmove',function(e){" +
                "if(!e.touches||!e.touches[0])return;" +
                "var dx=e.touches[0].clientX-sx,dy=e.touches[0].clientY-sy;" +
                "if(Math.abs(dy)>Math.abs(dx)&&Math.abs(dy)>8){e.preventDefault();e.stopImmediatePropagation();}" +
                "},{capture:true,passive:false});" +
                "document.addEventListener('wheel',function(e){if(Math.abs(e.deltaY)>Math.abs(e.deltaX)){e.preventDefault();e.stopImmediatePropagation();}},{capture:true,passive:false});" +
                "document.addEventListener('keydown',function(e){if(['ArrowDown','ArrowUp','PageDown','PageUp',' '].indexOf(e.key)>=0){e.preventDefault();e.stopImmediatePropagation();}},true);" +
                "})();";
        view.evaluateJavascript(script, null);
        injectHideInstagramBottomNav(view);
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

    private boolean isAuthPath(String path) {
        return path.startsWith("/accounts/")
                || path.startsWith("/challenge/")
                || path.startsWith("/two_factor/")
                || path.startsWith("/oauth/");
    }

    private boolean isDirectPath(String path) {
        return path.startsWith("/direct/");
    }

    private boolean isStoryPath(String path) {
        return path.startsWith("/stories/");
    }

    private boolean isSingleReelPath(String path) {
        return path.startsWith("/reel/");
    }

    private boolean isLikelyProfilePath(String path) {
        if (path == null) return false;
        if (!path.matches("^/[^/]+/$")) return false;
        return !"/explore/".equals(path)
                && !"/reels/".equals(path)
                && !"/direct/".equals(path)
                && !"/stories/".equals(path);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt("mode", mode);
        outState.putBoolean("profileBootstrap", profileBootstrap);
        if (webView != null) webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else if (mode != MODE_DM) {
            openDm();
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

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
