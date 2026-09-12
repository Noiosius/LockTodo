package com.locktodo.app;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.util.List;

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

        TextView subtitle = text("v0.4 잠금화면 위젯 진단 버전", 13, Color.argb(160, 255, 255, 255));
        subtitle.setPadding(0, dp(4), 0, dp(18));
        content.addView(subtitle);

        section("Todo 테스트");

        LinearLayout addRow = new LinearLayout(this);
        addRow.setOrientation(LinearLayout.HORIZONTAL);
        addRow.setGravity(Gravity.CENTER_VERTICAL);

        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.argb(120, 255, 255, 255));
        input.setHint("할 일 입력");
        input.setTextSize(16);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        addRow.addView(input, new LinearLayout.LayoutParams(0, dp(52), 1f));

        Button add = new Button(this);
        add.setText("추가");
        add.setAllCaps(false);
        addRow.addView(add, new LinearLayout.LayoutParams(dp(82), dp(52)));
        content.addView(addRow, matchWrap());

        Runnable addTodo = () -> {
            String value = input.getText().toString().trim();
            if (!value.isEmpty()) {
                new TodoStore(this).add(value);
                input.setText("");
                LockTodoWidget.updateAll(this);
                render();
            }
        };
        add.setOnClickListener(v -> addTodo.run());
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addTodo.run();
                return true;
            }
            return false;
        });

        List<String> items = new TodoStore(this).load();
        TextView current = text(items.isEmpty() ? "현재 Todo: 없음" : "현재 Todo:\n• " + String.join("\n• ", items), 14, Color.argb(210, 255, 255, 255));
        current.setLineSpacing(0, 1.15f);
        current.setPadding(0, dp(8), 0, dp(8));
        content.addView(current);

        Button addTests = new Button(this);
        addTests.setText("테스트 Todo 3개 넣기");
        addTests.setAllCaps(false);
        addTests.setOnClickListener(v -> {
            TodoStore store = new TodoStore(this);
            store.add("테스트 1");
            store.add("테스트 2");
            store.add("테스트 3");
            LockTodoWidget.updateAll(this);
            render();
        });
        content.addView(addTests, matchWrap());

        Button refresh = new Button(this);
        refresh.setText("위젯 강제 새로고침");
        refresh.setAllCaps(false);
        refresh.setOnClickListener(v -> LockTodoWidget.updateAll(this));
        content.addView(refresh, matchWrap());

        Button clear = new Button(this);
        clear.setText("Todo 전부 지우기");
        clear.setAllCaps(false);
        clear.setOnClickListener(v -> {
            new TodoStore(this).save(java.util.Collections.emptyList());
            LockTodoWidget.updateAll(this);
            render();
        });
        content.addView(clear, matchWrap());

        section("위젯");
        Button addWidget = new Button(this);
        addWidget.setText("홈 화면에 위젯 추가");
        addWidget.setAllCaps(false);
        addWidget.setOnClickListener(v -> requestWidget());
        content.addView(addWidget, matchWrap());

        TextView guide = text("먼저 앱에서 Todo를 넣은 뒤 위젯 강제 새로고침을 누르세요. 그 상태로 LockStar 잠금화면에 위젯을 배치해 Todo가 보이는지 확인합니다. 이번 버전은 진단을 위해 외곽 테두리를 그리지 않습니다.", 13, Color.argb(185, 255, 255, 255));
        guide.setLineSpacing(0, 1.15f);
        guide.setPadding(0, dp(12), 0, dp(14));
        content.addView(guide);

        section("표시");
        slider("글자 크기", AppPrefs.KEY_TEXT_SIZE, 10, 30, 16, "sp");
        slider("글자 투명도", AppPrefs.KEY_TEXT_ALPHA, 10, 100, 100, "%");
        slider("배경 투명도", AppPrefs.KEY_FILL_ALPHA, 0, 80, 0, "%");
        slider("좌우 여백", AppPrefs.KEY_HORIZONTAL_PADDING, 0, 30, 8, "dp");
        slider("항목 세로 여백", AppPrefs.KEY_ROW_PADDING, 0, 16, 5, "dp");
        slider("최대 표시 항목", AppPrefs.KEY_MAX_ROWS, 1, 8, 8, "개");

        section("조작 요소");
        slider("체크 원 투명도", AppPrefs.KEY_CHECK_ALPHA, 10, 100, 90, "%");
        slider("≡ 핸들 투명도", AppPrefs.KEY_HANDLE_ALPHA, 5, 100, 55, "%");
        toggle("+ 버튼 표시", AppPrefs.KEY_SHOW_PLUS, true);
        toggle("할 일이 없을 때 + 숨기기", AppPrefs.KEY_HIDE_PLUS_WHEN_EMPTY, true);
        slider("+ 버튼 크기", AppPrefs.KEY_PLUS_SIZE, 10, 28, 16, "sp");
        slider("+ 버튼 투명도", AppPrefs.KEY_PLUS_ALPHA, 5, 100, 25, "%");
        slider("체크 후 제거 지연", AppPrefs.KEY_CHECK_DELAY, 0, 600, 240, "ms");
        toggle("체크할 때 진동", AppPrefs.KEY_HAPTIC, true);
        toggle("밝은 글자 (끄면 검은 글자)", AppPrefs.KEY_LIGHT_TEXT, true);

        TextView sizeNote = text("위젯의 실제 가로·세로 크기와 위치는 홈 화면 또는 LockStar에서 조절합니다.", 12, Color.argb(145, 255, 255, 255));
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
