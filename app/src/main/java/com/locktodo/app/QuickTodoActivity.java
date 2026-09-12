package com.locktodo.app;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

public class QuickTodoActivity extends Activity {
    public static final String EXTRA_EDIT_INDEX = "edit_index";

    private EditText input;
    private int editIndex = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setShowWhenLocked(true);
        setFinishOnTouchOutside(true);

        Window window = getWindow();
        window.setBackgroundDrawableResource(android.R.color.transparent);
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE |
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        );

        SharedPreferences prefs = AppPrefs.get(this);
        boolean light = prefs.getBoolean(AppPrefs.KEY_LIGHT_TEXT, true);
        int fg = light ? Color.WHITE : Color.BLACK;
        int panel = light ? Color.argb(224, 25, 25, 27) : Color.argb(235, 247, 247, 247);

        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(dp(8), dp(4), dp(8), dp(4));
        outer.setBackgroundColor(Color.TRANSPARENT);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(2), dp(6), dp(2));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(panel);
        bg.setCornerRadius(dp(17));
        bg.setStroke(dp(1), light ? Color.argb(72, 255, 255, 255) : Color.argb(58, 0, 0, 0));
        row.setBackground(bg);

        input = new EditText(this);
        input.setSingleLine(true);
        input.setTextColor(fg);
        input.setHintTextColor(light ? Color.argb(125, 255, 255, 255) : Color.argb(110, 0, 0, 0));
        input.setHint("할 일 입력…");
        input.setTextSize(17);
        input.setBackgroundColor(Color.TRANSPARENT);
        input.setPadding(0, 0, dp(6), 0);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        row.addView(input, new LinearLayout.LayoutParams(0, dp(46), 1f));

        TextView done = new TextView(this);
        done.setText("✓");
        done.setTextSize(20);
        done.setTextColor(fg);
        done.setGravity(Gravity.CENTER);
        row.addView(done, new LinearLayout.LayoutParams(dp(40), dp(46)));

        outer.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        setContentView(outer);

        DisplayMetrics dm = getResources().getDisplayMetrics();
        WindowManager.LayoutParams attrs = window.getAttributes();
        attrs.width = Math.min(dm.widthPixels - dp(24), dp(520));
        attrs.height = WindowManager.LayoutParams.WRAP_CONTENT;
        attrs.gravity = Gravity.CENTER;
        attrs.dimAmount = 0f;
        window.setAttributes(attrs);

        editIndex = getIntent().getIntExtra(EXTRA_EDIT_INDEX, -1);
        if (editIndex >= 0) {
            List<String> items = new TodoStore(this).load();
            if (editIndex < items.size()) {
                input.setText(items.get(editIndex));
                input.setSelection(input.length());
            } else {
                editIndex = -1;
            }
        }

        done.setOnClickListener(v -> saveAndClose());
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                saveAndClose();
                return true;
            }
            return false;
        });

        input.requestFocus();
        input.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        }, 80);
    }

    private void saveAndClose() {
        String text = input.getText().toString().trim();
        TodoStore store = new TodoStore(this);
        if (editIndex >= 0) store.replaceAt(editIndex, text);
        else if (!text.isEmpty()) store.add(text);
        LockTodoWidget.updateAll(this);
        finish();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
