package com.locktodo.app;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
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

        TextView subtitle = text("v0.8 · 잠금화면 Todo 위젯", 13, Color.argb(160, 255, 255, 255));
        subtitle.setPadding(0, dp(4), 0, dp(18));
        content.addView(subtitle);

        section("Todo 관리");

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

        LinearLayout actionRow1 = new LinearLayout(this);
        actionRow1.setOrientation(LinearLayout.HORIZONTAL);
        Button quick = smallButton("빠른 입력창");
        quick.setOnClickListener(v -> startActivity(new Intent(this, QuickTodoActivity.class)));
        actionRow1.addView(quick, weighted());
        Button reorder = smallButton("순서 변경");
        reorder.setOnClickListener(v -> {
            if (!new TodoStore(this).load().isEmpty()) startActivity(new Intent(this, ReorderActivity.class));
        });
        actionRow1.addView(reorder, weighted());
        content.addView(actionRow1, matchWrap());

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
        addWidget.setText("LockTodo 위젯 추가 요청");
        addWidget.setAllCaps(false);
        addWidget.setOnClickListener(v -> requestWidget());
        content.addView(addWidget, matchWrap());

        TextView guide = text("위젯 높이에 맞춰 항목이 보이고, 넘치는 항목은 위젯 안에서 스크롤할 수 있습니다.", 13, Color.argb(185, 255, 255, 255));
        guide.setPadding(0, dp(12), 0, dp(14));
        content.addView(guide);

        section("표시");
        slider("글자 크기", AppPrefs.KEY_TEXT_SIZE, 10, 30, 16, "sp");
        slider("글자 투명도", AppPrefs.KEY_TEXT_ALPHA, 0, 100, 0, "%");
        slider("배경 투명도", AppPrefs.KEY_FILL_ALPHA, 0, 100, 100, "%");
        slider("테두리 투명도", AppPrefs.KEY_BORDER_ALPHA, 0, 100, 100, "%");
        toggle("Todo가 없어도 배경/테두리 표시", AppPrefs.KEY_SHOW_EMPTY_PANEL, false);
        slider("좌우 여백", AppPrefs.KEY_HORIZONTAL_PADDING, 0, 30, 8, "dp");
        slider("항목 세로 여백", AppPrefs.KEY_ROW_PADDING, 0, 16, 5, "dp");
        toggle("밝은 글자 (끄면 검은 글자)", AppPrefs.KEY_LIGHT_TEXT, true);

        TextView transparencyNote = text("투명도: 0% = 완전히 보임 · 100% = 완전히 숨김", 12, Color.argb(145, 255, 255, 255));
        transparencyNote.setPadding(0, dp(8), 0, dp(10));
        content.addView(transparencyNote);

        section("조작 요소");
        slider("체크 원 투명도", AppPrefs.KEY_CHECK_ALPHA, 0, 100, 10, "%");
        slider("≡ 핸들 투명도", AppPrefs.KEY_HANDLE_ALPHA, 0, 100, 45, "%");
        toggle("+ 버튼 표시", AppPrefs.KEY_SHOW_PLUS, true);
        toggle("할 일이 없을 때 + 숨기기", AppPrefs.KEY_HIDE_PLUS_WHEN_EMPTY, true);
        slider("+ 버튼 크기", AppPrefs.KEY_PLUS_SIZE, 10, 28, 17, "sp");
        slider("+ 버튼 투명도", AppPrefs.KEY_PLUS_ALPHA, 0, 100, 45, "%");
        slider("완료 표시 후 제거 지연", AppPrefs.KEY_CHECK_DELAY, 0, 3000, 300, "ms");
        toggle("완료할 때 진동", AppPrefs.KEY_HAPTIC, true);

        TextView behavior = text("빈 영역/+ → 빠른 입력 · Todo → 수정 · ○ → ● 뒤 삭제 · ≡ → 순서 변경 · 입력/순서 창 바깥은 두 번 탭해 닫기", 12, Color.argb(155, 255, 255, 255));
        behavior.setLineSpacing(0, 1.15f);
        behavior.setPadding(0, dp(12), 0, dp(18));
        content.addView(behavior);

        Button reset = new Button(this);
        reset.setText("설정 초기화 (Todo 유지)");
        reset.setAllCaps(false);
        reset.setOnClickListener(v -> {
            AppPrefs.reset(this);
            prefs = AppPrefs.get(this);
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

    private Button smallButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        return button;
    }

    private LinearLayout.LayoutParams weighted() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        p.setMargins(dp(2), dp(2), dp(2), dp(2));
        return p;
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
