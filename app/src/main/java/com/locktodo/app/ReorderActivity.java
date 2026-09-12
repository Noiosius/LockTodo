package com.locktodo.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setShowWhenLocked(true);
        setFinishOnTouchOutside(true);

        Window window = getWindow();
        window.setBackgroundDrawableResource(android.R.color.transparent);
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams attrs = window.getAttributes();
        attrs.dimAmount = 0.18f;
        attrs.gravity = Gravity.CENTER;
        window.setAttributes(attrs);

        SharedPreferences prefs = AppPrefs.get(this);
        light = prefs.getBoolean(AppPrefs.KEY_LIGHT_TEXT, true);
        int fg = light ? Color.WHITE : Color.BLACK;
        int panel = light ? Color.argb(222, 25, 25, 25) : Color.argb(235, 248, 248, 248);

        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(dp(22), dp(10), dp(22), dp(10));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(panel);
        bg.setCornerRadius(dp(20));
        bg.setStroke(dp(1), light ? Color.argb(70, 255, 255, 255) : Color.argb(55, 0, 0, 0));
        card.setBackground(bg);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("순서 변경");
        title.setTextSize(15);
        title.setTextColor(fg);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(38), 1f));

        TextView done = new TextView(this);
        done.setText("완료");
        done.setTextSize(14);
        done.setGravity(Gravity.CENTER);
        done.setTextColor(fg);
        done.setAlpha(0.72f);
        done.setOnClickListener(v -> finish());
        header.addView(done, new LinearLayout.LayoutParams(dp(56), dp(38)));
        card.addView(header);

        ScrollView scroll = new ScrollView(this);
        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(listContainer, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        card.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(360)));

        outer.addView(card, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        setContentView(outer);

        WindowManager.LayoutParams finalAttrs = window.getAttributes();
        finalAttrs.width = WindowManager.LayoutParams.MATCH_PARENT;
        finalAttrs.height = WindowManager.LayoutParams.WRAP_CONTENT;
        window.setAttributes(finalAttrs);

        render();
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
            row.setPadding(dp(4), dp(8), dp(2), dp(8));

            TextView text = new TextView(this);
            text.setText(items.get(i));
            text.setTextSize(17);
            text.setTextColor(fg);
            text.setSingleLine(true);
            row.addView(text, new LinearLayout.LayoutParams(0, dp(46), 1f));

            TextView handle = new TextView(this);
            handle.setText("≡");
            handle.setTextSize(22);
            handle.setTextColor(muted);
            handle.setGravity(Gravity.CENTER);
            row.addView(handle, new LinearLayout.LayoutParams(dp(54), dp(46)));

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
