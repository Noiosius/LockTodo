package com.noiosius.igdm;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.webkit.*;
import android.widget.*;
import androidx.webkit.ScriptHandler;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;

public class MainActivity extends Activity {
    private static final String ORIGIN = "https://www.instagram.com";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WebView webView;
    private TextView message;
    private LinearLayout navigation;
    private final Button[] tabs = new Button[3];
    private String mode = "DM", username = "", script;
    private ScriptHandler documentScript;
    private boolean resumed;
    private int generation;
    private final Runnable inspect = new Runnable() {
        @Override public void run() {
            if (!resumed || webView == null) return;
            final int expected = generation;
            webView.evaluateJavascript("JSON.stringify(window.__igMinimal ? window.__igMinimal.status() : null)", result -> {
                if (expected != generation || webView == null) return;
                try {
                    Object decoded = new JSONTokener(result).nextValue();
                    if (!(decoded instanceof String)) return;
                    JSONObject state = new JSONObject((String) decoded);
                    String observedUser = state.optString("username");
                    if (state.optBoolean("auth")) username = "";
                    else if (NavigationPolicy.validUsername(observedUser)) username = observedUser;
                    switch (state.optString("state")) {
                        case "ready": webView.setVisibility(View.VISIBLE); message.setVisibility(View.GONE); break;
                        case "blocked": openMode(mode); break;
                        case "missing-story": showMessage("스토리 목록을 확인할 수 없습니다.\n피드는 숨겨 두었습니다.\n\n여기를 눌러 다시 시도"); break;
                    }
                } catch (Exception ignored) { /* Page not ready. */ }
            });
            handler.postDelayed(this, 800);
        }
    };

    @SuppressLint("SetJavaScriptEnabled")
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            mode = savedInstanceState.getString("mode", "DM");
            username = savedInstanceState.getString("username", "");
        }
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        else getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        FrameLayout content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        webView = new WebView(this);
        webView.setBackgroundColor(Color.BLACK);
        content.addView(webView, new FrameLayout.LayoutParams(-1, -1));
        message = new TextView(this);
        message.setGravity(Gravity.CENTER);
        message.setTextColor(Color.WHITE);
        message.setTextSize(15);
        message.setPadding(dp(24), dp(24), dp(24), dp(24));
        content.addView(message, new FrameLayout.LayoutParams(-1, -1));
        message.setOnClickListener(v -> openMode(mode));
        navigation = new LinearLayout(this);
        String[] names = {"DM", "STORY", "PROFILE"};
        for (int i = 0; i < names.length; i++) {
            final String name = names[i];
            Button button = new Button(this);
            button.setText(name); button.setTextSize(12); button.setBackgroundColor(Color.BLACK);
            button.setOnClickListener(v -> openMode(name));
            navigation.addView(button, new LinearLayout.LayoutParams(0, dp(56), 1));
            tabs[i] = button;
        }
        tabs[0].setOnLongClickListener(v -> { showDiagnostics(); return true; });
        root.addView(navigation);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int left, top, right, bottom; boolean keyboard;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets safe = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                left = safe.left; top = safe.top; right = safe.right; bottom = safe.bottom;
                keyboard = insets.isVisible(WindowInsets.Type.ime());
            } else {
                left = insets.getSystemWindowInsetLeft(); top = insets.getSystemWindowInsetTop();
                right = insets.getSystemWindowInsetRight(); bottom = insets.getSystemWindowInsetBottom();
                keyboard = bottom > dp(150);
            }
            v.setPadding(left, top, right, bottom);
            navigation.setVisibility(keyboard ? View.GONE : View.VISIBLE);
            return insets.consumeSystemWindowInsets();
        });
        setContentView(root); root.requestApplyInsets();
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setAllowFileAccess(false); settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportMultipleWindows(false);
        webView.setWebChromeClient(new WebChromeClient());
        try (InputStream input = getAssets().open("minimal.js")) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] bytes = new byte[4096]; int count;
            while ((count = input.read(bytes)) != -1) buffer.write(bytes, 0, count);
            script = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception error) { throw new IllegalStateException("Missing UI adapter", error); }
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return request.isForMainFrame() && !NavigationPolicy.allowed(request.getUrl().toString(), mode, username);
            }
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
                generation++; showMessage("불러오는 중…\n\n다시 시도하려면 누르세요");
                if (!NavigationPolicy.allowed(url, mode, username)) { view.stopLoading(); openMode(mode); }
            }
            @Override public void onPageFinished(WebView view, String url) {
                if (NavigationPolicy.allowed(url, mode, username)) view.evaluateJavascript(configuredScript(), null);
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) showMessage("연결하지 못했습니다.\n여기를 눌러 다시 시도");
            }
        });
        openMode(mode);
    }
    private String configuredScript() {
        return "window.__igConfig={mode:" + JSONObject.quote(mode) + ",username:" + JSONObject.quote(username) + "};\n" + script;
    }
    private void openMode(String selected) {
        if ("PROFILE".equals(selected) && !NavigationPolicy.validUsername(username)) {
            new AlertDialog.Builder(this).setTitle("내 프로필 확인 필요")
                    .setMessage("로그인 후 DM 화면에서 내 프로필 링크를 확인하면 자동으로 연결합니다. 먼저 DM을 열어 주세요.")
                    .setPositiveButton("DM 열기", (d, w) -> openMode("DM")).setNegativeButton("닫기", null).show();
            return;
        }
        mode = selected; generation++; showMessage("불러오는 중…\n\n다시 시도하려면 누르세요");
        for (Button tab : tabs) {
            boolean active = tab.getText().toString().equals(mode);
            tab.setTextColor(active ? Color.WHITE : Color.GRAY); tab.setSelected(active);
            tab.setContentDescription(tab.getText() + (active ? ", 선택됨" : ""));
        }
        if (documentScript != null) documentScript.remove();
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            documentScript = WebViewCompat.addDocumentStartJavaScript(webView, configuredScript(),
                    new HashSet<>(Arrays.asList(ORIGIN, "https://instagram.com")));
        }
        webView.loadUrl(ORIGIN + ("STORY".equals(mode) ? "/" : "PROFILE".equals(mode) ? "/" + username + "/" : "/direct/inbox/"));
    }
    private void showMessage(String text) { webView.setVisibility(View.INVISIBLE); message.setText(text); message.setVisibility(View.VISIBLE); }
    private void showDiagnostics() {
        webView.evaluateJavascript("JSON.stringify({serviceWorker:'serviceWorker' in navigator,push:'PushManager' in window,notification:'Notification' in window})", result ->
                new AlertDialog.Builder(this).setTitle("알림 지원 확인")
                        .setMessage("이 버전은 백그라운드 DM 알림을 제공하지 않습니다.\n\n현재 WebView 기능:\n" + result
                                + "\n\nService Worker 지원만으로 푸시가 가능해지는 것은 아닙니다.")
                        .setPositiveButton("확인", null).show());
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    @Override protected void onResume() { super.onResume(); resumed = true; if (webView != null) webView.onResume(); handler.post(inspect); }
    @Override protected void onPause() {
        resumed = false; handler.removeCallbacks(inspect); if (webView != null) webView.onPause();
        CookieManager.getInstance().flush(); super.onPause();
    }
    @Override protected void onSaveInstanceState(Bundle state) { state.putString("mode", mode); state.putString("username", username); super.onSaveInstanceState(state); }
    @Override public void onBackPressed() {
        if (webView.canGoBack()) { showMessage("불러오는 중…"); webView.goBack(); }
        else if (!"DM".equals(mode)) openMode("DM"); else super.onBackPressed();
    }
    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null); if (documentScript != null) documentScript.remove();
        if (webView != null) { webView.stopLoading(); webView.destroy(); webView = null; } super.onDestroy();
    }
}
