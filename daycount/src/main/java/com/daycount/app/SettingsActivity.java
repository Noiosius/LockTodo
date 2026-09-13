package com.daycount.app;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Locale;

public class SettingsActivity extends Activity {
    private SeekBar sizeSeek;
    private TextView sizeValue;
    private EditText colorInput;
    private TextView colorPreview;
    private final Button[] positionButtons = new Button[9];
    private int selectedPosition;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.setStatusBarColor(Color.BLACK);
        window.setNavigationBarColor(Color.BLACK);

        selectedPosition = WidgetPrefs.position(this);
        setContentView(buildUi());
        refreshPositionButtons();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(12, 12, 12));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("위젯 설정");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        root.addView(title);

        TextView sizeLabel = sectionLabel("글자 크기");
        LinearLayout.LayoutParams sizeLabelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sizeLabelParams.topMargin = dp(28);
        root.addView(sizeLabel, sizeLabelParams);

        LinearLayout sizeRow = new LinearLayout(this);
        sizeRow.setOrientation(LinearLayout.HORIZONTAL);
        sizeRow.setGravity(Gravity.CENTER_VERTICAL);

        sizeSeek = new SeekBar(this);
        sizeSeek.setMax(56);
        sizeSeek.setProgress(WidgetPrefs.textSize(this) - 24);
        sizeRow.addView(sizeSeek, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        sizeValue = new TextView(this);
        sizeValue.setTextColor(Color.WHITE);
        sizeValue.setTextSize(14);
        sizeValue.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        sizeValue.setText((sizeSeek.getProgress() + 24) + "sp");
        sizeRow.addView(sizeValue, new LinearLayout.LayoutParams(dp(58), ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(sizeRow);

        sizeSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                sizeValue.setText((progress + 24) + "sp");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        TextView colorLabel = sectionLabel("글자 색상");
        LinearLayout.LayoutParams colorLabelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        colorLabelParams.topMargin = dp(28);
        root.addView(colorLabel, colorLabelParams);

        int currentColor = WidgetPrefs.textColor(this);
        LinearLayout colorRow = new LinearLayout(this);
        colorRow.setOrientation(LinearLayout.HORIZONTAL);
        colorRow.setGravity(Gravity.CENTER_VERTICAL);

        colorInput = new EditText(this);
        colorInput.setSingleLine(true);
        colorInput.setHint("#FFFFFF");
        colorInput.setText(String.format(Locale.US, "#%06X", 0xFFFFFF & currentColor));
        colorInput.setTextColor(Color.WHITE);
        colorInput.setHintTextColor(Color.rgb(110, 110, 110));
        colorRow.addView(colorInput, new LinearLayout.LayoutParams(0, dp(52), 1f));

        colorPreview = new TextView(this);
        colorPreview.setBackgroundColor(currentColor);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(dp(42), dp(42));
        previewParams.leftMargin = dp(12);
        colorRow.addView(colorPreview, previewParams);
        root.addView(colorRow);

        colorInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                Integer color = parseColor(s.toString());
                if (color != null) colorPreview.setBackgroundColor(color);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        LinearLayout presets = new LinearLayout(this);
        presets.setOrientation(LinearLayout.HORIZONTAL);
        String[] presetNames = {"흰색", "연회색", "회색", "검정"};
        String[] presetHex = {"#FFFFFF", "#D0D0D0", "#8A8A8A", "#000000"};
        for (int i = 0; i < presetNames.length; i++) {
            final String hex = presetHex[i];
            Button b = new Button(this);
            b.setAllCaps(false);
            b.setText(presetNames[i]);
            b.setTextSize(12);
            b.setOnClickListener(v -> colorInput.setText(hex));
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, dp(46), 1f);
            if (i > 0) bp.leftMargin = dp(4);
            presets.addView(b, bp);
        }
        LinearLayout.LayoutParams presetParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        presetParams.topMargin = dp(8);
        root.addView(presets, presetParams);

        TextView positionLabel = sectionLabel("위젯 위치");
        LinearLayout.LayoutParams positionLabelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        positionLabelParams.topMargin = dp(28);
        root.addView(positionLabel, positionLabelParams);

        String[][] symbols = {
                {"↖", "↑", "↗"},
                {"←", "●", "→"},
                {"↙", "↓", "↘"}
        };
        int index = 0;
        for (int row = 0; row < 3; row++) {
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            for (int col = 0; col < 3; col++) {
                final int position = index;
                Button button = new Button(this);
                button.setText(symbols[row][col]);
                button.setTextSize(18);
                button.setAllCaps(false);
                button.setOnClickListener(v -> {
                    selectedPosition = position;
                    refreshPositionButtons();
                });
                positionButtons[index] = button;
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(48), 1f);
                if (col > 0) p.leftMargin = dp(4);
                line.addView(button, p);
                index++;
            }
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (row > 0) rowParams.topMargin = dp(4);
            root.addView(line, rowParams);
        }

        Button save = new Button(this);
        save.setAllCaps(false);
        save.setText("저장");
        save.setTextSize(15);
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        saveParams.topMargin = dp(30);
        root.addView(save, saveParams);

        save.setOnClickListener(v -> {
            Integer color = parseColor(colorInput.getText().toString());
            if (color == null) {
                colorInput.setError("#RRGGBB 형식으로 입력하세요");
                return;
            }
            int size = sizeSeek.getProgress() + 24;
            WidgetPrefs.save(this, size, color, selectedPosition);
            DayCountWidget.updateAll(this);
            finish();
        });

        return scroll;
    }

    private TextView sectionLabel(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(Color.rgb(185, 185, 185));
        label.setTextSize(13);
        return label;
    }

    private void refreshPositionButtons() {
        for (int i = 0; i < positionButtons.length; i++) {
            Button b = positionButtons[i];
            if (b == null) continue;
            boolean selected = i == selectedPosition;
            b.setTextColor(selected ? Color.WHITE : Color.rgb(170, 170, 170));
            b.setBackgroundColor(selected ? Color.rgb(70, 70, 70) : Color.rgb(30, 30, 30));
        }
    }

    private Integer parseColor(String text) {
        if (text == null) return null;
        String s = text.trim();
        if (!s.startsWith("#")) s = "#" + s;
        if (s.length() != 7 && s.length() != 9) return null;
        try {
            return Color.parseColor(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
