package com.locktodo.app;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

public class SettingsActivity extends Activity {
    private SharedPreferences prefs;
    private LinearLayout content;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = AppPrefs.get(this);
        render();
    }

    private void render() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(12, 12, 12));
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(26), dp(20), dp(40));
        scroll.addView(content, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = text("Lock Todo", 24, Color.WHITE);
        content.addView(title);

        TextView subtitle = text("잠금화면을 덮지 않는 투명 Todo 위젯", 13, Color.argb(160, 255, 255, 255));
        subtitle.setPadding(0, dp(4), 0, dp(18));
        content.addView(subtitle);

        Button addWidget = new Button(this);
        addWidget.setText("홈 화면에 위젯 추가");
        addWidget.setAllCaps(false);
        addWidget.setOnClickListener(v -> requestWidget());
        content.addView(addWidget, matchWrap());

        TextView guide = text("잠금화면: Galaxy Store의 Good Lock → LockStar → 잠금화면 편집 → 위젯 추가 → Lock Todo\n\n위젯이 비어 있을 때도 그 영역을 탭하면 할 일을 입력할 수 있습니다.", 13, Color.argb(185, 255, 255, 255));
        guide.setLineSpacing(0, 1.15f);
        guide.setPadding(0, dp(12), 0, dp(14));
        content.addView(guide);

        section("표시");
        slider("글자 크기", AppPrefs.KEY_TEXT_SIZE, 10, 30, 16, "sp");
        slider("글자 투명도", AppPrefs.KEY_TEXT_ALPHA, 10, 100, 100, "%");
        slider("배경 투명도", AppPrefs.KEY_FILL_ALPHA, 0, 80, 18, "%");
        slider("테두리 투명도", AppPrefs.KEY_BORDER_ALPHA, 0, 100, 25, "%");
        slider("좌우 여백", AppPrefs.KEY_HORIZONTAL_PADDING, 0, 30, 8, "dp");
        slider("항목 세로 여백", AppPrefs.KEY_ROW_PADDING, 0, 16, 5, "dp");
        slider("최대 표시 항목", AppPrefs.KEY_MAX_ROWS, 1, 12, 8, "개");

        section("조작 요소");
        slider("체크 원 투명도", AppPrefs.KEY_CHECK_ALPHA, 10, 100, 90, "%");
        slider("≡ 핸들 투명도", AppPrefs.KEY_HANDLE_ALPHA, 5, 100, 55, "%");
        toggle("+ 버튼 표시", AppPrefs.KEY_SHOW_PLUS, true);
        toggle("할 일이 없을 때 + 숨기기", AppPrefs.KEY_HIDE_PLUS_WHEN_EMPTY, true);
        slider("+ 버튼 크기", AppPrefs.KEY_PLUS_SIZE, 10, 28, 16, "sp");
        slider("+ 버튼 투명도", AppPrefs.KEY_PLUS_ALPHA, 5, 100, 25, "%");
        slider("체크 후 제거 지연", AppPrefs.KEY_CHECK_DELAY, 0, 600, 240, "ms");
        toggle("체크할 때 진동", AppPrefs.KEY_HAPTIC, true);

        section("빈 상태 / 색상");
        toggle("할 일이 없어도 테두리 표시", AppPrefs.KEY_SHOW_EMPTY_PANEL, false);
        toggle("밝은 글자 (끄면 검은 글자)", AppPrefs.KEY_LIGHT_TEXT, true);

        TextView sizeNote = text("위젯의 실제 가로·세로 크기와 위치는 홈 화면 또는 LockStar에서 직접 자유롭게 조절합니다.", 12, Color.argb(145, 255, 255, 255));
        sizeNote.setPadding(0, dp(10), 0, dp(18));
        content.addView(sizeNote);

        Button reset = new Button(this);
        reset.setText("설정 초기화");
        reset.setAllCaps(false);
        reset.setOnClickListener(v -> {
            AppPrefs.reset(this);
            LockTodoWidget.updateAll(this);
            render();
        });
        content.addView(reset, matchWrap());

        setContentView(scroll);
    }

    private void requestWidget() {
        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        if (manager.isRequestPinAppWidgetSupported()) {
            manager.requestPinAppWidget(new ComponentName(this, LockTodoWidget.class), null, null);
        }
    }

    private void section(String label) {
        TextView t = text(label, 15, Color.WHITE);
        t.setPadding(0, dp(24), 0, dp(6));
        content.addView(t);
    }

    private void slider(String label, String key, int min, int max, int def, String suffix) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(0, dp(7), 0, dp(7));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = text(label, 14, Color.argb(225, 255, 255, 255));
        TextView value = text("", 13, Color.argb(155, 255, 255, 255));
        value.setGravity(Gravity.END);
        header.addView(name, new LinearLayout.LayoutParams(0, dp(28), 1f));
        header.addView(value, new LinearLayout.LayoutParams(dp(86), dp(28)));
        block.addView(header);

        SeekBar bar = new SeekBar(this);
        int current = prefs.getInt(key, def);
        current = Math.max(min, Math.min(max, current));
        bar.setMax(max - min);
        bar.setProgress(current - min);
        value.setText(current + suffix);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int v = min + progress;
                value.setText(v + suffix);
                prefs.edit().putInt(key, v).apply();
                LockTodoWidget.updateAll(SettingsActivity.this);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        block.addView(bar, matchWrap());
        content.addView(block, matchWrap());
    }

    private void toggle(String label, String key, boolean def) {
        Switch sw = new Switch(this);
        sw.setText(label);
        sw.setTextColor(Color.argb(225, 255, 255, 255));
        sw.setTextSize(14);
        sw.setPadding(0, dp(6), 0, dp(6));
        sw.setChecked(prefs.getBoolean(key, def));
        sw.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(key, isChecked).apply();
            LockTodoWidget.updateAll(this);
        });
        content.addView(sw, matchWrap());
    }

    private TextView text(String value, int sp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        return view;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
