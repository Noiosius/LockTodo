package com.locktodo.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

public class SettingsActivity extends Activity {
    private SharedPreferences prefs;
    private LinearLayout content;
    private TextView permissionStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = AppPrefs.get(this);
        buildUi();
        requestNotificationPermissionIfNeeded();
        if (prefs.getBoolean(AppPrefs.KEY_AUTO_SHOW, true)) {
            try {
                LockService.start(this);
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshPermissionStatus();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(15, 16, 18));
        scroll.setFillViewport(true);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(24), dp(22), dp(42));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);

        TextView title = text("Lock Todo", 28, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        content.addView(title, matchWrap());

        TextView subtitle = text("보이는 건 최소한으로, 조절은 최대한 자유롭게.", 14, Color.rgb(163, 168, 178));
        LinearLayout.LayoutParams subLp = matchWrap();
        subLp.topMargin = dp(6);
        subLp.bottomMargin = dp(16);
        content.addView(subtitle, subLp);

        Button preview = button("잠금화면 패널 미리보기");
        preview.setOnClickListener(v -> {
            Intent intent = new Intent(this, LockTodoActivity.class);
            intent.putExtra(LockTodoActivity.EXTRA_PREVIEW, true);
            startActivity(intent);
        });
        content.addView(preview, matchWrap());

        permissionStatus = text("", 13, Color.rgb(170, 174, 182));
        LinearLayout.LayoutParams permissionLp = matchWrap();
        permissionLp.topMargin = dp(12);
        content.addView(permissionStatus, permissionLp);

        if (Build.VERSION.SDK_INT >= 34) {
            Button fsi = button("잠금화면 자동 표시 권한 열기");
            fsi.setOnClickListener(v -> openFullScreenIntentSettings());
            LinearLayout.LayoutParams fsiLp = matchWrap();
            fsiLp.topMargin = dp(8);
            content.addView(fsi, fsiLp);
        }

        section("동작");
        addSwitch("잠금화면에서 자동 표시", AppPrefs.KEY_AUTO_SHOW, true, (button, checked) -> {
            prefs.edit().putBoolean(AppPrefs.KEY_AUTO_SHOW, checked).apply();
            if (checked) {
                try { LockService.start(this); } catch (Exception ignored) {}
            } else {
                stopService(new Intent(this, LockService.class));
            }
        });
        addSwitch("작은 + 버튼 표시", AppPrefs.KEY_SHOW_PLUS, true, null);
        addSwitch("할 일이 없으면 +도 숨김", AppPrefs.KEY_HIDE_PLUS_WHEN_EMPTY, true, null);
        addSwitch("터치 진동", AppPrefs.KEY_HAPTIC, true, null);
        addSeek("체크 후 사라지는 속도", AppPrefs.KEY_CHECK_DELAY, 80, 700, 170, "ms");

        section("패널 크기 · 위치");
        addSeek("패널 가로 크기", AppPrefs.KEY_PANEL_WIDTH, 35, 100, 88, "%");
        addSeek("패널 세로 크기", AppPrefs.KEY_PANEL_HEIGHT, 100, 650, 300, "dp");
        addSeek("가로 위치", AppPrefs.KEY_PANEL_X, 0, 100, 50, "%");
        addSeek("세로 위치", AppPrefs.KEY_TOP_OFFSET, 30, 700, 250, "dp");

        section("Todo 행");
        addSeek("행 높이", AppPrefs.KEY_ROW_HEIGHT, 34, 80, 44, "dp");
        addSeek("항목 간격", AppPrefs.KEY_ROW_GAP, 0, 20, 5, "dp");
        addSeek("좌우 안쪽 여백", AppPrefs.KEY_ROW_PADDING, 0, 24, 8, "dp");
        addSeek("모서리 둥글기", AppPrefs.KEY_CORNER_RADIUS, 0, 32, 14, "dp");

        section("글자 · 배경");
        addSwitch("밝은 글자", AppPrefs.KEY_LIGHT_TEXT, true, null);
        addSeek("글자 크기", AppPrefs.KEY_TEXT_SIZE, 12, 30, 17, "sp");
        addSeek("글자 투명도", AppPrefs.KEY_TEXT_ALPHA, 20, 100, 92, "%");
        addSeek("배경 투명도", AppPrefs.KEY_FILL_ALPHA, 0, 50, 7, "%");
        addSeek("테두리 투명도", AppPrefs.KEY_BORDER_ALPHA, 0, 100, 35, "%");
        addSeek("테두리 두께", AppPrefs.KEY_BORDER_WIDTH, 0, 4, 1, "dp");

        section("왼쪽 체크");
        addSeek("체크 크기", AppPrefs.KEY_CHECK_SIZE, 16, 34, 22, "sp");
        addSeek("체크 투명도", AppPrefs.KEY_CHECK_ALPHA, 10, 100, 88, "%");

        section("오른쪽 = 핸들");
        addSeek("핸들 크기", AppPrefs.KEY_HANDLE_SIZE, 12, 30, 18, "sp");
        addSeek("핸들 투명도", AppPrefs.KEY_HANDLE_ALPHA, 5, 100, 55, "%");

        section("+ 버튼");
        addSeek("+ 크기", AppPrefs.KEY_PLUS_SIZE, 12, 34, 18, "sp");
        addSeek("+ 투명도", AppPrefs.KEY_PLUS_ALPHA, 5, 100, 24, "%");
        addSeek("오른쪽 여백", AppPrefs.KEY_PLUS_END_MARGIN, 0, 32, 6, "dp");
        addSeek("아래 여백", AppPrefs.KEY_PLUS_BOTTOM_MARGIN, 0, 32, 4, "dp");

        section("사용법");
        TextView gesture = text(
                "• Todo가 0개면 기본적으로 아무것도 보이지 않음\n" +
                "• 투명 패널 영역을 길게 누르면 입력창 표시\n" +
                "• ○ 누르기 → ✓ 후 목록에서 제거\n" +
                "• 오른쪽 = 를 길게 눌러 드래그 → 순서 변경\n" +
                "• + 는 별도로 표시/투명도 조절 가능\n" +
                "• 패널 밖 터치는 잠금화면으로 통과",
                14, Color.rgb(205, 208, 214));
        gesture.setLineSpacing(dp(4), 1f);
        content.addView(gesture, matchWrap());

        Button reset = button("설정만 기본값으로 초기화");
        LinearLayout.LayoutParams resetLp = matchWrap();
        resetLp.topMargin = dp(26);
        content.addView(reset, resetLp);
        reset.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("설정 초기화")
                .setMessage("Todo 내용은 유지하고 화면 설정만 초기화합니다.")
                .setNegativeButton("취소", null)
                .setPositiveButton("초기화", (dialog, which) -> {
                    AppPrefs.reset(this);
                    recreate();
                })
                .show());
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 3001);
        }
    }

    private void refreshPermissionStatus() {
        if (permissionStatus == null) return;
        boolean notifications = Build.VERSION.SDK_INT < 33 ||
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        boolean fullScreen = true;
        if (Build.VERSION.SDK_INT >= 34) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            fullScreen = nm != null && nm.canUseFullScreenIntent();
        }

        if (notifications && fullScreen) {
            permissionStatus.setText("잠금화면 자동 표시 권한: 준비됨");
            permissionStatus.setTextColor(Color.rgb(139, 205, 153));
        } else {
            permissionStatus.setText("자동 표시를 위해 알림 권한과 잠금화면 전체 화면 표시 권한을 허용해 주세요.");
            permissionStatus.setTextColor(Color.rgb(230, 177, 119));
        }
    }

    private void openFullScreenIntentSettings() {
        if (Build.VERSION.SDK_INT < 34) return;
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception e) {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        }
    }

    private void section(String label) {
        TextView section = text(label, 15, Color.rgb(170, 183, 255));
        section.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams lp = matchWrap();
        lp.topMargin = dp(24);
        lp.bottomMargin = dp(8);
        content.addView(section, lp);
    }

    private void addSwitch(String label, String key, boolean defaultValue,
                           CompoundButton.OnCheckedChangeListener custom) {
        Switch sw = new Switch(this);
        sw.setText(label);
        sw.setTextColor(Color.WHITE);
        sw.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        sw.setGravity(Gravity.CENTER_VERTICAL);
        sw.setPadding(0, dp(7), 0, dp(7));
        sw.setChecked(prefs.getBoolean(key, defaultValue));
        sw.setOnCheckedChangeListener((button, checked) -> {
            if (custom != null) custom.onCheckedChanged(button, checked);
            else prefs.edit().putBoolean(key, checked).apply();
        });
        content.addView(sw, matchWrap());
    }

    private void addSeek(String label, String key, int min, int max, int defaultValue, String unit) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(0, dp(5), 0, dp(9));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        TextView name = text(label, 15, Color.WHITE);
        TextView value = text("", 14, Color.rgb(166, 171, 181));
        value.setGravity(Gravity.END);
        head.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(value, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        wrap.addView(head, matchWrap());

        SeekBar seek = new SeekBar(this);
        seek.setMin(min);
        seek.setMax(max);
        int current = Math.max(min, Math.min(max, prefs.getInt(key, defaultValue)));
        seek.setProgress(current);
        value.setText(current + unit);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int actual = Math.max(min, progress);
                value.setText(actual + unit);
                if (fromUser) prefs.edit().putInt(key, actual).apply();
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        wrap.addView(seek, matchWrap());
        content.addView(wrap, matchWrap());
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        return b;
    }

    private TextView text(String value, int sp, int color) {
        TextView tv = new TextView(this);
        tv.setText(value);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        tv.setTextColor(color);
        return tv;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }
}
