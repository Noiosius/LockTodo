package com.batterycheck.app;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

public class SettingsActivity extends Activity {
    private SharedPreferences prefs;
    private LinearLayout content;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = Prefs.get(this);
        render();
    }

    private void render() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(12, 12, 12));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(28), dp(22), dp(36));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = text("Battery Check", 24, Color.WHITE);
        content.addView(title);

        TextView guide = text("두 번 탭: 닫기 · 길게 누르기: 설정", 13, Color.argb(155, 255, 255, 255));
        guide.setPadding(0, dp(5), 0, dp(20));
        content.addView(guide);

        section("화면");
        slider("배경 투명도", Prefs.KEY_BG_TRANSPARENCY, 0, 90, 28, "%");
        slider("글자 크기", Prefs.KEY_TEXT_SCALE, 85, 125, 100, "%");
        toggle("얇은 테두리", Prefs.KEY_BORDER, true);

        section("위치");
        RadioGroup group = new RadioGroup(this);
        group.setOrientation(RadioGroup.VERTICAL);

        RadioButton top = radio("위");
        RadioButton center = radio("가운데");
        RadioButton bottom = radio("아래");
        top.setId(1001);
        center.setId(1002);
        bottom.setId(1003);
        group.addView(top);
        group.addView(center);
        group.addView(bottom);

        int position = prefs.getInt(Prefs.KEY_POSITION, Prefs.POSITION_CENTER);
        group.check(position == Prefs.POSITION_TOP ? 1001 : position == Prefs.POSITION_BOTTOM ? 1003 : 1002);
        group.setOnCheckedChangeListener((g, checkedId) -> {
            int value = checkedId == 1001 ? Prefs.POSITION_TOP
                    : checkedId == 1003 ? Prefs.POSITION_BOTTOM
                    : Prefs.POSITION_CENTER;
            prefs.edit().putInt(Prefs.KEY_POSITION, value).apply();
        });
        content.addView(group, matchWrap());

        TextView note = text("표시는 충전 전력 · 평균 속도 · 남은 시간 · 온도 순서입니다. 남은 시간 아래에는 예상 완충 시각이 함께 표시됩니다.", 12, Color.argb(145, 255, 255, 255));
        note.setLineSpacing(0, 1.15f);
        note.setPadding(0, dp(18), 0, dp(18));
        content.addView(note);

        Button reset = new Button(this);
        reset.setText("설정 초기화");
        reset.setAllCaps(false);
        reset.setOnClickListener(v -> {
            Prefs.reset(this);
            render();
        });
        content.addView(reset, matchWrap());

        setContentView(scroll);
    }

    private void section(String label) {
        TextView t = text(label, 15, Color.WHITE);
        t.setPadding(0, dp(20), 0, dp(7));
        content.addView(t);
    }

    private void slider(String label, String key, int min, int max, int def, String suffix) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(0, dp(7), 0, dp(8));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = text(label, 14, Color.argb(225, 255, 255, 255));
        TextView value = text("", 13, Color.argb(155, 255, 255, 255));
        value.setGravity(Gravity.END);
        header.addView(name, new LinearLayout.LayoutParams(0, dp(28), 1f));
        header.addView(value, new LinearLayout.LayoutParams(dp(78), dp(28)));
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
        sw.setPadding(0, dp(8), 0, dp(8));
        sw.setChecked(prefs.getBoolean(key, def));
        sw.setOnCheckedChangeListener((buttonView, isChecked) -> prefs.edit().putBoolean(key, isChecked).apply());
        content.addView(sw, matchWrap());
    }

    private RadioButton radio(String label) {
        RadioButton b = new RadioButton(this);
        b.setText(label);
        b.setTextColor(Color.argb(225, 255, 255, 255));
        b.setTextSize(14);
        b.setPadding(0, dp(5), 0, dp(5));
        return b;
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
