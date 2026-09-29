package com.noiosius.igdm;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import org.json.JSONArray;

public class MainActivity extends Activity {
    private static final String DIRECT_URL = "https://www.instagram.com/direct/inbox/";
    private static final String HOME_URL = "https://www.instagram.com/";

    private static final int MODE_DM = 0;
    private static final int MODE_STORY = 1;
    private static final int MODE_PROFILE = 2;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private WebView mainWebView;
    private WebView storyWebView;
    private FrameLayout webContainer;

    private ImageButton dmButton;
    private ImageButton storyButton;
    private ImageButton profileButton;

    private int mode = MODE_DM;
    private boolean profileBootstrap = false;
    private int navigationGeneration = 0;
    private boolean storyReady = false;
    private boolean storySelectedWaiting = false;

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

        webContainer = new FrameLayout(this);
        webContainer.setBackgroundColor(Color.BLACK);

        mainWebView = new WebView(this);
        mainWebView.setBackgroundColor(Color.BLACK);
        mainWebView.setAlpha(0f);

        storyWebView = new WebView(this);
        storyWebView.setBackgroundColor(Color.BLACK);
        storyWebView.setVisibility(View.INVISIBLE);

        webContainer.addView(mainWebView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        webContainer.addView(storyWebView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        root.addView(webContainer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setBackgroundColor(Color.rgb(8, 10, 15));

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

        configureWebView(mainWebView);
        configureWebView(storyWebView);

        mainWebView.setWebChromeClient(new WebChromeClient());
        mainWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                mainWebView.setAlpha(0f);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleMainNavigation(view, request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleMainNavigation(view, Uri.parse(url));
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                handleMainFinished(view, Uri.parse(url));
            }
        });

        storyWebView.setWebChromeClient(new WebChromeClient());
        storyWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                String path = safePath(Uri.parse(url));
                if ("/".equals(path)) {
                    storyReady = false;
                } else if (isStoryPath(path)) {
                    expandStoryWebView();
                    if (mode == MODE_STORY) storyWebView.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleStoryNavigation(view, request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleStoryNavigation(view, Uri.parse(url));
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                handleStoryFinished(view, Uri.parse(url));
            }
        });

        dmButton.setOnClickListener(v -> openDm());
        storyButton.setOnClickListener(v -> openStories());
        profileButton.setOnClickListener(v -> openProfile());

        mode = MODE_DM;
        updateNavState();
        mainWebView.loadUrl(DIRECT_URL);

        // STORY는 사용자가 누르기 전에 백그라운드에서 미리 준비한다.
        storyWebView.loadUrl(HOME_URL);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView(WebView view) {
        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(view, true);

        WebSettings settings = view.getSettings();
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
        navigationGeneration++;
        storySelectedWaiting = false;
        mode = MODE_DM;
        profileBootstrap = false;
        updateNavState();
        showMainLayer();
        mainWebView.setAlpha(0f);
        mainWebView.loadUrl(DIRECT_URL);
    }

    private void openStories() {
        navigationGeneration++;
        mode = MODE_STORY;
        profileBootstrap = false;
        updateNavState();

        if (storyReady || isStoryPath(safePath(Uri.parse(storyWebView.getUrl() == null ? HOME_URL : storyWebView.getUrl())))) {
            showStoryLayer();
        } else {
            // 준비 전이면 기존 화면을 그대로 두고, 준비되는 순간 바로 전환한다.
            storySelectedWaiting = true;
        }
    }

    private void openProfile() {
        navigationGeneration++;
        storySelectedWaiting = false;
        mode = MODE_PROFILE;
        profileBootstrap = true;
        updateNavState();
        showMainLayer();
        mainWebView.setAlpha(0f);
        mainWebView.loadUrl(HOME_URL);
    }

    private void showMainLayer() {
        storyWebView.setVisibility(View.INVISIBLE);
        mainWebView.setVisibility(View.VISIBLE);
    }

    private void showStoryLayer() {
        storySelectedWaiting = false;
        mainWebView.setVisibility(View.INVISIBLE);
        storyWebView.setVisibility(View.VISIBLE);
    }

    private void showMainContent() {
        if (mode == MODE_DM || mode == MODE_PROFILE) {
            mainWebView.setVisibility(View.VISIBLE);
            mainWebView.animate().alpha(1f).setDuration(80L).start();
        }
    }

    private void updateNavState() {
        dmButton.setAlpha(mode == MODE_DM ? 1f : 0.42f);
        storyButton.setAlpha(mode == MODE_STORY ? 1f : 0.42f);
        profileButton.setAlpha(mode == MODE_PROFILE ? 1f : 0.42f);
    }

    private boolean handleMainNavigation(WebView view, Uri uri) {
        if (uri == null) return true;
        String scheme = uri.getScheme();
        String path = safePath(uri);

        if ("about".equals(scheme) || "data".equals(scheme) || "blob".equals(scheme)) return false;
        if (!"http".equals(scheme) && !"https".equals(scheme)) return true;
        if (!isInstagramHost(uri.getHost())) return true;
        if (isAuthPath(path)) return false;

        if (mode == MODE_DM) {
            if (isDirectPath(path) || isSingleReelPath(path)) return false;
            mainWebView.setAlpha(0f);
            view.loadUrl(DIRECT_URL);
            return true;
        }

        if (mode == MODE_PROFILE) {
            if (profileBootstrap && "/".equals(path)) return false;
            if (isLikelyProfilePath(path)) {
                profileBootstrap = false;
                return false;
            }
            if ("/".equals(path)) return false;
            return false;
        }

        return false;
    }

    private void handleMainFinished(WebView view, Uri uri) {
        if (!isInstagramHost(uri.getHost())) return;
        String path = safePath(uri);

        if (isAuthPath(path)) {
            showMainContent();
            return;
        }

        if (mode == MODE_DM) {
            if (isDirectPath(path)) {
                injectHideInstagramBottomNav(view);
                handler.postDelayed(this::showMainContent, 60L);
            } else if (isSingleReelPath(path)) {
                injectSingleReelLock(view);
                handler.postDelayed(this::showMainContent, 60L);
            } else {
                mainWebView.setAlpha(0f);
                view.loadUrl(DIRECT_URL);
            }
            return;
        }

        if (mode == MODE_PROFILE) {
            if (profileBootstrap && "/".equals(path)) {
                int generation = navigationGeneration;
                resolveOwnProfile(view, generation, 0);
            } else {
                injectHideInstagramBottomNav(view);
                handler.postDelayed(this::showMainContent, 60L);
            }
        }
    }

    private boolean handleStoryNavigation(WebView view, Uri uri) {
        if (uri == null) return true;
        String scheme = uri.getScheme();
        String path = safePath(uri);

        if ("about".equals(scheme) || "data".equals(scheme) || "blob".equals(scheme)) return false;
        if (!"http".equals(scheme) && !"https".equals(scheme)) return true;
        if (!isInstagramHost(uri.getHost())) return true;
        if (isAuthPath(path)) return false;

        if ("/".equals(path) || isStoryPath(path)) return false;

        view.loadUrl(HOME_URL);
        return true;
    }

    private void handleStoryFinished(WebView view, Uri uri) {
        if (!isInstagramHost(uri.getHost())) return;
        String path = safePath(uri);

        if (isAuthPath(path)) {
            if (mode == MODE_STORY) showStoryLayer();
            return;
        }

        if ("/".equals(path)) {
            prepareStoryCrop(view, 0);
            return;
        }

        if (isStoryPath(path)) {
            expandStoryWebView();
            injectHideInstagramBottomNav(view);
            if (mode == MODE_STORY) showStoryLayer();
            return;
        }

        view.loadUrl(HOME_URL);
    }

    private void prepareStoryCrop(WebView view, int attempt) {
        String script =
                "(function(){" +
                "var cut=0;" +
                "var articles=[].slice.call(document.querySelectorAll('article')).filter(function(a){" +
                "var r=a.getBoundingClientRect();return r.width>0&&r.height>0&&r.top>80;" +
                "});" +
                "if(articles.length){" +
                "articles.sort(function(a,b){return a.getBoundingClientRect().top-b.getBoundingClientRect().top;});" +
                "cut=articles[0].getBoundingClientRect().top;" +
                "}" +
                "if(!cut||cut<120){" +
                "var candidates=[].slice.call(document.querySelectorAll(\"a[href*='/stories/'],button,[role='button']\")).filter(function(e){" +
                "var r=e.getBoundingClientRect();" +
                "return r.width>38&&r.height>38&&r.top>45&&r.top<360;" +
                "});" +
                "var max=0;candidates.forEach(function(e){var r=e.getBoundingClientRect();max=Math.max(max,r.bottom);});" +
                "if(max>100)cut=max+14;" +
                "}" +
                "if(!cut||cut<120)cut=240;" +
                "document.querySelectorAll('article').forEach(function(a){a.style.setProperty('visibility','hidden','important');});" +
                "document.documentElement.style.setProperty('overflow-y','hidden','important');" +
                "document.body.style.setProperty('overflow-y','hidden','important');" +
                "return [Math.round(cut),Math.round(innerWidth)];" +
                "})();";

        view.evaluateJavascript(script, value -> {
            try {
                JSONArray data = new JSONArray(value);
                double cssCut = data.getDouble(0);
                double cssWidth = Math.max(1.0, data.getDouble(1));

                int webWidth = storyWebView.getWidth();
                if (webWidth <= 0) webWidth = getResources().getDisplayMetrics().widthPixels;

                int cropHeight = (int) Math.round(cssCut * webWidth / cssWidth);
                cropHeight = Math.max(dp(150), Math.min(dp(310), cropHeight));

                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        cropHeight
                );
                params.gravity = Gravity.TOP;
                storyWebView.setLayoutParams(params);
                storyWebView.scrollTo(0, 0);
                storyWebView.setVerticalScrollBarEnabled(false);

                injectHideInstagramBottomNav(view);

                storyReady = true;
                if (mode == MODE_STORY || storySelectedWaiting) showStoryLayer();
            } catch (Exception error) {
                if (attempt < 30) {
                    handler.postDelayed(() -> prepareStoryCrop(view, attempt + 1), 100L);
                } else {
                    // 끝까지 측정이 안 되면 홈 상단만 고정 높이로 보여준다.
                    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            dp(230)
                    );
                    params.gravity = Gravity.TOP;
                    storyWebView.setLayoutParams(params);
                    storyWebView.scrollTo(0, 0);
                    injectHideInstagramBottomNav(view);
                    storyReady = true;
                    if (mode == MODE_STORY || storySelectedWaiting) showStoryLayer();
                }
            }
        });
    }

    private void expandStoryWebView() {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        );
        params.gravity = Gravity.TOP;
        storyWebView.setLayoutParams(params);
        storyWebView.setVerticalScrollBarEnabled(false);
    }

    private void resolveOwnProfile(WebView view, int generation, int attempt) {
        if (generation != navigationGeneration || mode != MODE_PROFILE) return;

        String script =
                "(function(){" +
                "var all=[].slice.call(document.querySelectorAll('a[href]'));" +
                "var target=all.find(function(a){" +
                "return !!a.querySelector(\"svg[aria-label='Profile'],svg[aria-label='프로필'],[aria-label='Profile'],[aria-label='프로필']\");" +
                "});" +
                "if(!target){" +
                "target=all.find(function(a){" +
                "try{" +
                "var p=new URL(a.href,location.origin).pathname;" +
                "var r=a.getBoundingClientRect();" +
                "return /^\\/[^\\/]+\\/$/.test(p)&&r.top>innerHeight*0.58&&a.querySelector('img');" +
                "}catch(e){return false;}" +
                "});" +
                "}" +
                "return target?target.href:'';" +
                "})();";

        view.evaluateJavascript(script, value -> {
            if (generation != navigationGeneration || mode != MODE_PROFILE) return;
            String profileUrl = decodeJsString(value);
            if (profileUrl != null && !profileUrl.isEmpty()) {
                profileBootstrap = false;
                mainWebView.setAlpha(0f);
                mainWebView.loadUrl(profileUrl);
            } else if (attempt < 35) {
                handler.postDelayed(() -> resolveOwnProfile(view, generation, attempt + 1), 100L);
            } else {
                showMainContent();
            }
        });
    }

    private String decodeJsString(String value) {
        if (value == null || "null".equals(value) || "\"\"".equals(value)) return "";
        try {
            JSONArray array = new JSONArray("[" + value + "]");
            return array.getString(0);
        } catch (Exception ignored) {
            return "";
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
                "};hide();" +
                "new MutationObserver(hide).observe(document.documentElement,{childList:true,subtree:true});" +
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
        if (path == null || !path.matches("^/[^/]+/$")) return false;
        return !"/explore/".equals(path)
                && !"/reels/".equals(path)
                && !"/direct/".equals(path)
                && !"/stories/".equals(path);
    }

    @Override
    public void onBackPressed() {
        if (mode == MODE_STORY) {
            String path = safePath(Uri.parse(storyWebView.getUrl() == null ? HOME_URL : storyWebView.getUrl()));
            if (isStoryPath(path) && storyWebView.canGoBack()) {
                storyWebView.goBack();
            } else {
                openDm();
            }
            return;
        }

        if (mainWebView != null && mainWebView.canGoBack()) {
            mainWebView.setAlpha(0f);
            mainWebView.goBack();
        } else if (mode != MODE_DM) {
            openDm();
        } else {
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);

        if (mainWebView != null) {
            mainWebView.stopLoading();
            mainWebView.setWebChromeClient(null);
            mainWebView.setWebViewClient(null);
            mainWebView.destroy();
            mainWebView = null;
        }

        if (storyWebView != null) {
            storyWebView.stopLoading();
            storyWebView.setWebChromeClient(null);
            storyWebView.setWebViewClient(null);
            storyWebView.destroy();
            storyWebView = null;
        }

        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
