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
    private PositionPickerView positionPicker;
    private TextView positionValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.setStatusBarColor(Color.BLACK);
        window.setNavigationBarColor(Color.BLACK);

        setContentView(buildUi());
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
                int size = progress + 24;
                sizeValue.setText(size + "sp");
                if (positionPicker != null) {
                    Integer color = parseColor(colorInput == null ? null : colorInput.getText().toString());
                    positionPicker.setPreviewStyle(size, color == null ? WidgetPrefs.textColor(SettingsActivity.this) : color);
                }
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
                if (color != null) {
                    colorPreview.setBackgroundColor(color);
                    if (positionPicker != null) {
                        positionPicker.setPreviewStyle(sizeSeek.getProgress() + 24, color);
                    }
                }
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

        TextView positionGuide = new TextView(this);
        positionGuide.setText("아래 미리보기에서 숫자를 직접 끌어서 원하는 위치에 놓으세요.");
        positionGuide.setTextColor(Color.rgb(125, 125, 125));
        positionGuide.setTextSize(12);
        positionGuide.setPadding(0, dp(6), 0, dp(8));
        root.addView(positionGuide);

        positionPicker = new PositionPickerView(this);
        positionPicker.setPosition(WidgetPrefs.positionX(this), WidgetPrefs.positionY(this));
        positionPicker.setPreviewStyle(sizeSeek.getProgress() + 24, currentColor);
        root.addView(positionPicker, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(210)));

        positionValue = new TextView(this);
        positionValue.setTextColor(Color.rgb(135, 135, 135));
        positionValue.setTextSize(11);
        positionValue.setGravity(Gravity.CENTER);
        positionValue.setPadding(0, dp(5), 0, 0);
        root.addView(positionValue);
        updatePositionValue();

        positionPicker.setOnPositionChangedListener((x, y) -> updatePositionValue());

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
            WidgetPrefs.save(
                    this,
                    size,
                    color,
                    positionPicker.getPositionX(),
                    positionPicker.getPositionY()
            );
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

    private void updatePositionValue() {
        if (positionPicker == null || positionValue == null) return;
        int x = Math.round(positionPicker.getPositionX() * 100f);
        int y = Math.round(positionPicker.getPositionY() * 100f);
        positionValue.setText("가로 " + x + "%  ·  세로 " + y + "%");
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
