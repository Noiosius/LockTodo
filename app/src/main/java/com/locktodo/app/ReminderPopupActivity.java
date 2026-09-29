package com.locktodo.app;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public class ReminderPopupActivity extends Activity {
    private float dragStartX;
    private float knobStartTranslation;
    private int dragLimit;
    private int dismissThreshold;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setShowWhenLocked(true);
        setTurnScreenOn(true);
        setFinishOnTouchOutside(false);

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(ReminderReceiver.NOTIFICATION_ID);

        Window window = getWindow();
        window.setBackgroundDrawableResource(android.R.color.transparent);
        window.getDecorView().setBackgroundColor(Color.TRANSPARENT);
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND | WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        SharedPreferences prefs = AppPrefs.get(this);
        boolean light = prefs.getBoolean(AppPrefs.KEY_LIGHT_TEXT, true);
        int fg = light ? Color.WHITE : Color.BLACK;
        int muted = light ? Color.argb(145, 255, 255, 255) : Color.argb(120, 0, 0, 0);
        int panel = light ? Color.argb(220, 25, 25, 27) : Color.argb(232, 247, 247, 247);

        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(dp(6), dp(4), dp(6), dp(4));
        outer.setBackgroundColor(Color.TRANSPARENT);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(panel);
        bg.setCornerRadius(dp(20));
        bg.setStroke(dp(1), light ? Color.argb(72, 255, 255, 255) : Color.argb(58, 0, 0, 0));
        card.setBackground(bg);

        TextView clock = new TextView(this);
        clock.setText("◷");
        clock.setTextSize(25);
        clock.setTextColor(muted);
        clock.setGravity(Gravity.CENTER);
        card.addView(clock, new LinearLayout.LayoutParams(dp(48), dp(42)));

        TextView text = new TextView(this);
        text.setText(getIntent().getStringExtra(ReminderReceiver.EXTRA_REMINDER_TEXT));
        text.setTextSize(21);
        text.setTextColor(fg);
        text.setGravity(Gravity.CENTER);
        text.setMaxLines(4);
        text.setPadding(dp(4), dp(2), dp(4), dp(12));
        card.addView(text, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        FrameLayout track = new FrameLayout(this);
        GradientDrawable trackBg = new GradientDrawable();
        trackBg.setColor(light ? Color.argb(28, 255, 255, 255) : Color.argb(24, 0, 0, 0));
        trackBg.setCornerRadius(dp(22));
        track.setBackground(trackBg);

        View knob = new View(this);
        GradientDrawable knobBg = new GradientDrawable();
        knobBg.setShape(GradientDrawable.OVAL);
        knobBg.setColor(light ? Color.argb(220, 255, 255, 255) : Color.argb(210, 35, 35, 35));
        knobBg.setStroke(dp(1), light ? Color.argb(90, 255, 255, 255) : Color.argb(70, 0, 0, 0));
        knob.setBackground(knobBg);

        FrameLayout.LayoutParams knobParams = new FrameLayout.LayoutParams(dp(38), dp(38), Gravity.CENTER);
        track.addView(knob, knobParams);

        LinearLayout.LayoutParams trackParams = new LinearLayout.LayoutParams(dp(174), dp(42));
        trackParams.topMargin = dp(5);
        card.addView(track, trackParams);

        dragLimit = dp(62);
        dismissThreshold = dp(50);
        knob.setOnTouchListener((v, event) -> handleKnobDrag(v, event));

        outer.addView(card, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        setContentView(outer);

        DisplayMetrics dm = getResources().getDisplayMetrics();
        WindowManager.LayoutParams attrs = window.getAttributes();
        attrs.width = Math.min(dm.widthPixels - dp(48), dp(310));
        attrs.height = WindowManager.LayoutParams.WRAP_CONTENT;
        attrs.gravity = Gravity.CENTER;
        attrs.dimAmount = 0f;
        window.setAttributes(attrs);
    }

    private boolean handleKnobDrag(View knob, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragStartX = event.getRawX();
                knobStartTranslation = knob.getTranslationX();
                return true;
            case MotionEvent.ACTION_MOVE:
                float next = knobStartTranslation + event.getRawX() - dragStartX;
                next = Math.max(-dragLimit, Math.min(dragLimit, next));
                knob.setTranslationX(next);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (Math.abs(knob.getTranslationX()) >= dismissThreshold) {
                    finish();
                } else {
                    knob.animate().translationX(0f).setDuration(140L).start();
                }
                return true;
            default:
                return false;
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
