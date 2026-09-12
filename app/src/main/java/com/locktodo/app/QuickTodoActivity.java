package com.locktodo.app;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
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
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams attrs = window.getAttributes();
        attrs.dimAmount = 0.16f;
        attrs.gravity = Gravity.CENTER;
        window.setAttributes(attrs);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);

        SharedPreferences prefs = AppPrefs.get(this);
        boolean light = prefs.getBoolean(AppPrefs.KEY_LIGHT_TEXT, true);
        int fg = light ? Color.WHITE : Color.BLACK;
        int panel = light ? Color.argb(210, 28, 28, 28) : Color.argb(225, 245, 245, 245);

        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(dp(22), dp(10), dp(22), dp(10));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(5), dp(8), dp(5));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(panel);
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), light ? Color.argb(70, 255, 255, 255) : Color.argb(55, 0, 0, 0));
        row.setBackground(bg);

        input = new EditText(this);
        input.setSingleLine(true);
        input.setTextColor(fg);
        input.setHintTextColor(light ? Color.argb(125, 255, 255, 255) : Color.argb(110, 0, 0, 0));
        input.setHint("할 일 입력…");
        input.setTextSize(18);
        input.setBackgroundColor(Color.TRANSPARENT);
        input.setPadding(0, 0, dp(8), 0);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        row.addView(input, new LinearLayout.LayoutParams(0, dp(48), 1f));

        TextView done = new TextView(this);
        done.setText("✓");
        done.setTextSize(21);
        done.setTextColor(fg);
        done.setGravity(Gravity.CENTER);
        row.addView(done, new LinearLayout.LayoutParams(dp(42), dp(48)));

        outer.addView(row, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        setContentView(outer);

        WindowManager.LayoutParams finalAttrs = window.getAttributes();
        finalAttrs.width = WindowManager.LayoutParams.MATCH_PARENT;
        finalAttrs.height = WindowManager.LayoutParams.WRAP_CONTENT;
        window.setAttributes(finalAttrs);

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
        }, 120);
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
