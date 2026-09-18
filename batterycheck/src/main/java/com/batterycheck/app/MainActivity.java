package com.batterycheck.app;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

public class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private GestureDetector gestures;
    private Metric powerMetric;
    private Metric speedMetric;
    private Metric remainingMetric;
    private Metric tempMetric;
    private int loadingPhase = 0;

    private final Runnable refreshTask = new Runnable() {
        @Override public void run() {
            refreshValues();
            handler.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setShowWhenLocked(true);
        BatteryReader.resetEstimates();

        Window window = getWindow();
        window.setBackgroundDrawableResource(android.R.color.transparent);
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);

        gestures = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }

            @Override public boolean onDoubleTap(MotionEvent e) {
                finish();
                return true;
            }

            @Override public void onLongPress(MotionEvent e) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                finish();
            }
        });

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.TRANSPARENT);
        root.setOnTouchListener((v, event) -> gestures.onTouchEvent(event));
        root.setClickable(true);

        LinearLayout card = buildCard();
        FrameLayout.LayoutParams cardParams = buildCardLayoutParams();
        root.addView(card, cardParams);
        setContentView(root);

        refreshValues();
    }

    @Override protected void onResume() {
        super.onResume();
        handler.removeCallbacks(refreshTask);
        handler.post(refreshTask);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(refreshTask);
        super.onPause();
    }

    private LinearLayout buildCard() {
        SharedPreferences prefs = Prefs.get(this);
        int transparency = prefs.getInt(Prefs.KEY_BG_TRANSPARENCY, 28);
        int textScale = prefs.getInt(Prefs.KEY_TEXT_SCALE, 100);
        boolean border = prefs.getBoolean(Prefs.KEY_BORDER, true);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(10), dp(16), dp(10), dp(16));
        card.setElevation(dp(6));

        int alpha = Math.max(0, Math.min(255, Math.round(255f * (100 - transparency) / 100f)));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(alpha, 20, 20, 20));
        bg.setCornerRadius(dp(26));
        if (border) bg.setStroke(dp(1), Color.argb(72, 255, 255, 255));
        card.setBackground(bg);

        powerMetric = addMetric(card, "충전 전력", textScale, false);
        addDivider(card);
        speedMetric = addMetric(card, "충전 속도", textScale, false);
        speedMetric.value.setText("");
        addDivider(card);
        remainingMetric = addMetric(card, "남은 시간", textScale, true);
        remainingMetric.value.setText("");
        if (remainingMetric.subValue != null) remainingMetric.subValue.setText("");
        addDivider(card);
        tempMetric = addMetric(card, "온도", textScale, false);

        return card;
    }

    private FrameLayout.LayoutParams buildCardLayoutParams() {
        SharedPreferences prefs = Prefs.get(this);
        int position = prefs.getInt(Prefs.KEY_POSITION, Prefs.POSITION_CENTER);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.leftMargin = dp(18);
        params.rightMargin = dp(18);

        if (position == Prefs.POSITION_TOP) {
            params.gravity = Gravity.TOP;
            params.topMargin = dp(110);
        } else if (position == Prefs.POSITION_BOTTOM) {
            params.gravity = Gravity.BOTTOM;
            params.bottomMargin = dp(110);
        } else {
            params.gravity = Gravity.CENTER;
        }
        return params;
    }

    private Metric addMetric(LinearLayout parent, String label, int textScale, boolean withSubValue) {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER);
        column.setPadding(dp(4), 0, dp(4), 0);

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextColor(Color.argb(165, 255, 255, 255));
        labelView.setTextSize(11f * textScale / 100f);
        labelView.setGravity(Gravity.CENTER);

        TextView valueView = new TextView(this);
        valueView.setText("—");
        valueView.setTextColor(Color.WHITE);
        valueView.setTextSize(19f * textScale / 100f);
        valueView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        valueView.setGravity(Gravity.CENTER);
        valueView.setPadding(0, dp(3), 0, 0);

        column.addView(labelView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        column.addView(valueView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView subView = new TextView(this);
        subView.setText(withSubValue ? "—" : "");
        subView.setTextColor(Color.argb(170, 255, 255, 255));
        subView.setTextSize(10.5f * textScale / 100f);
        subView.setGravity(Gravity.CENTER);
        subView.setPadding(0, dp(2), 0, 0);
        if (!withSubValue) subView.setVisibility(View.INVISIBLE);
        column.addView(subView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        parent.addView(column, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return new Metric(valueView, subView);
    }

    private void addDivider(LinearLayout parent) {
        View divider = new View(this);
        divider.setBackgroundColor(Color.argb(58, 255, 255, 255));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(1), dp(58));
        p.leftMargin = dp(2);
        p.rightMargin = dp(2);
        parent.addView(divider, p);
    }

    private void refreshValues() {
        if (powerMetric == null) return;

        BatteryReader.Snapshot s = BatteryReader.read(this);

        powerMetric.value.setText(Double.isNaN(s.watts)
                ? "—"
                : String.format(Locale.getDefault(), "%.1f W", s.watts));

        if (s.charging && !s.estimatesReady) {
            CharSequence loading = measuringDots();
            speedMetric.value.setText(loading);
            remainingMetric.value.setText(loading);
            if (remainingMetric.subValue != null) remainingMetric.subValue.setText("");
            loadingPhase = (loadingPhase + 1) % 3;
        } else if (!s.charging) {
            speedMetric.value.setTextColor(Color.WHITE);
            remainingMetric.value.setTextColor(Color.WHITE);
            speedMetric.value.setText("—");
            remainingMetric.value.setText("—");
            if (remainingMetric.subValue != null) remainingMetric.subValue.setText("—");
        } else {
            speedMetric.value.setTextColor(Color.WHITE);
            remainingMetric.value.setTextColor(Color.WHITE);

            speedMetric.value.setText(Double.isNaN(s.avgPercentPerHour)
                    ? "—"
                    : String.format(Locale.getDefault(), "+%.0f%%/h", s.avgPercentPerHour));

            if (s.remainingMinutes > 0) {
                remainingMetric.value.setText(s.remainingMinutes + "분");
                if (remainingMetric.subValue != null) remainingMetric.subValue.setText(s.fullTime);
            } else {
                remainingMetric.value.setText("—");
                if (remainingMetric.subValue != null) remainingMetric.subValue.setText("—");
            }
        }

        tempMetric.value.setText(Double.isNaN(s.temperatureC)
                ? "—"
                : String.format(Locale.getDefault(), "%.1f°C", s.temperatureC));
    }

    private CharSequence measuringDots() {
        String dots = "· · ·";
        SpannableString span = new SpannableString(dots);
        int[] positions = {0, 2, 4};

        for (int i = 0; i < positions.length; i++) {
            int color = i == loadingPhase
                    ? Color.argb(180, 255, 255, 255)
                    : Color.argb(48, 255, 255, 255);
            span.setSpan(
                    new ForegroundColorSpan(color),
                    positions[i],
                    positions[i] + 1,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }
        return span;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class Metric {
        final TextView value;
        final TextView subValue;

        Metric(TextView value, TextView subValue) {
            this.value = value;
            this.subValue = subValue;
        }
    }
}
