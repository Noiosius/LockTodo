package com.locktodo.app;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/** Lightweight lockscreen-compatible reader for tasks too wide for RemoteViews. */
public class TodoReaderActivity extends Activity {
    static final String EXTRA_INDEX = "index";
    static final String EXTRA_TEXT = "text";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setShowWhenLocked(true);
        setFinishOnTouchOutside(true);

        int index = getIntent().getIntExtra(EXTRA_INDEX, -1);
        List<String> items = new TodoStore(this).load();
        if (index < 0 || index >= items.size()) {
            finish();
            return;
        }
        String text = items.get(index);

        SharedPreferences prefs = AppPrefs.get(this);
        boolean light = prefs.getBoolean(AppPrefs.KEY_LIGHT_TEXT, true);
        int fg = light ? Color.WHITE : Color.BLACK;
        int secondary = light ? Color.rgb(175, 175, 175) : Color.rgb(105, 105, 105);
        int bg = light ? Color.argb(235, 25, 25, 27) : Color.argb(245, 247, 247, 247);

        Window window = getWindow();
        window.setBackgroundDrawableResource(android.R.color.transparent);
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(9), dp(8), dp(8));
        GradientDrawable panel = new GradientDrawable();
        panel.setColor(bg);
        panel.setCornerRadius(dp(16));
        panel.setStroke(dp(1), light ? Color.argb(72, 255, 255, 255) : Color.argb(58, 0, 0, 0));
        box.setBackground(panel);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        HorizontalScrollView slider = new HorizontalScrollView(this);
        slider.setHorizontalScrollBarEnabled(false);
        slider.setOverScrollMode(HorizontalScrollView.OVER_SCROLL_NEVER);
        slider.setFillViewport(true);

        TextView task = new TextView(this);
        task.setText(text);
        task.setTextColor(fg);
        task.setTextSize(prefs.getInt(AppPrefs.KEY_TEXT_SIZE, 16));
        task.setSingleLine(true);
        task.setGravity(Gravity.CENTER_VERTICAL);
        task.setPadding(dp(3), 0, dp(30), 0);
        slider.addView(task, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(46)));
        row.addView(slider, new LinearLayout.LayoutParams(0, dp(46), 1f));

        TextView close = new TextView(this);
        close.setText("×");
        close.setTextColor(fg);
        close.setTextSize(24);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("닫기");
        close.setOnClickListener(v -> finish());
        row.addView(close, new LinearLayout.LayoutParams(dp(35), dp(46)));

        box.addView(row);

        TextView hint = new TextView(this);
        hint.setText("← 좌우로 밀어서 전체 내용 보기 →");
        hint.setTextColor(secondary);
        hint.setTextSize(11);
        hint.setGravity(Gravity.CENTER);
        box.addView(hint, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(24)));

        setContentView(box);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        WindowManager.LayoutParams a = window.getAttributes();
        a.width = Math.min(dm.widthPixels - dp(36), dp(360));
        a.height = WindowManager.LayoutParams.WRAP_CONTENT;
        a.gravity = Gravity.CENTER;
        window.setAttributes(a);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
