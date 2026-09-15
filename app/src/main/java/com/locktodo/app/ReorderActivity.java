package com.locktodo.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

public class ReorderActivity extends Activity {
    private LinearLayout listContainer;
    private boolean light;
    private long lastOutsideTap = 0L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setShowWhenLocked(true);
        setFinishOnTouchOutside(false);

        Window window = getWindow();
        window.setBackgroundDrawableResource(android.R.color.transparent);
        window.getDecorView().setBackgroundColor(Color.TRANSPARENT);
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND | WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        window.addFlags(WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH);

        SharedPreferences prefs = AppPrefs.get(this);
        light = prefs.getBoolean(AppPrefs.KEY_LIGHT_TEXT, true);
        int fg = light ? Color.WHITE : Color.BLACK;
        int panel = light ? Color.argb(222, 25, 25, 25) : Color.argb(235, 248, 248, 248);

        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(dp(6), dp(6), dp(6), dp(6));
        outer.setBackgroundColor(Color.TRANSPARENT);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(10), dp(14), dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(panel);
        bg.setCornerRadius(dp(20));
        bg.setStroke(dp(1), light ? Color.argb(70, 255, 255, 255) : Color.argb(55, 0, 0, 0));
        card.setBackground(bg);

        TextView title = new TextView(this);
        title.setText("순서 변경");
        title.setTextSize(15);
        title.setTextColor(fg);
        title.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(title, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(34)));

        ScrollView scroll = new ScrollView(this);
        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(listContainer, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        card.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(280)));

        outer.addView(card, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        setContentView(outer);

        DisplayMetrics dm = getResources().getDisplayMetrics();
        WindowManager.LayoutParams attrs = window.getAttributes();
        attrs.width = Math.min(dm.widthPixels - dp(48), dp(310));
        attrs.height = WindowManager.LayoutParams.WRAP_CONTENT;
        attrs.gravity = Gravity.CENTER;
        attrs.dimAmount = 0f;
        window.setAttributes(attrs);

        render();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getActionMasked() == MotionEvent.ACTION_OUTSIDE) {
            long now = SystemClock.elapsedRealtime();
            if (now - lastOutsideTap <= 420L) {
                finish();
            } else {
                lastOutsideTap = now;
            }
            return true;
        }
        return super.dispatchTouchEvent(ev);
    }

    private void render() {
        listContainer.removeAllViews();
        List<String> items = new TodoStore(this).load();
        int fg = light ? Color.WHITE : Color.BLACK;
        int muted = light ? Color.argb(145, 255, 255, 255) : Color.argb(120, 0, 0, 0);

        if (items.isEmpty()) {
            finish();
            return;
        }

        for (int i = 0; i < items.size(); i++) {
            final int targetIndex = i;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(4), dp(5), dp(2), dp(5));

            TextView text = new TextView(this);
            text.setText(items.get(i));
            text.setTextSize(16);
            text.setTextColor(fg);
            text.setSingleLine(true);
            row.addView(text, new LinearLayout.LayoutParams(0, dp(42), 1f));

            TextView handle = new TextView(this);
            handle.setText("≡");
            handle.setTextSize(21);
            handle.setTextColor(muted);
            handle.setGravity(Gravity.CENTER);
            row.addView(handle, new LinearLayout.LayoutParams(dp(46), dp(42)));

            handle.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    ClipData data = ClipData.newPlainText("todo", "");
                    View.DragShadowBuilder shadow = new View.DragShadowBuilder(row);
                    row.startDragAndDrop(data, shadow, targetIndex, 0);
                    return true;
                }
                return false;
            });

            row.setOnDragListener((v, event) -> {
                if (event.getAction() == DragEvent.ACTION_DRAG_ENTERED) {
                    v.setAlpha(0.55f);
                } else if (event.getAction() == DragEvent.ACTION_DRAG_EXITED || event.getAction() == DragEvent.ACTION_DRAG_ENDED) {
                    v.setAlpha(1f);
                } else if (event.getAction() == DragEvent.ACTION_DROP) {
                    Object local = event.getLocalState();
                    if (local instanceof Integer) {
                        int from = (Integer) local;
                        if (from != targetIndex) {
                            new TodoStore(this).move(from, targetIndex);
                            LockTodoWidget.updateAll(this);
                            render();
                        }
                    }
                    return true;
                }
                return true;
            });

            listContainer.addView(row, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
