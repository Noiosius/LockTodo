package com.locktodo.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public class ReminderOverlayService extends Service {
    static final String EXTRA_TEXT = "overlay_text";
    private static final int FOREGROUND_ID = 9304;
    private static final String CHANNEL_ID = "locktodo_overlay";

    private WindowManager windowManager;
    private View overlayView;
    private float dragStartX;
    private float knobStartTranslation;
    private int dragLimit;
    private int dismissThreshold;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String text = intent == null ? "" : intent.getStringExtra(EXTRA_TEXT);
        startForeground(FOREGROUND_ID, buildNotification(text));

        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        showOverlay(text == null ? "" : text);
        return START_NOT_STICKY;
    }

    private Notification buildNotification(String text) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager != null) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "LockTodo 팝업",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setSound(null, null);
            channel.enableVibration(false);
            manager.createNotificationChannel(channel);
        }

        Intent open = new Intent(this, SettingsActivity.class);
        PendingIntent pending = PendingIntent.getActivity(
                this, 9304, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        return builder
                .setSmallIcon(R.drawable.ic_lock_todo)
                .setContentTitle("LockTodo")
                .setContentText(text)
                .setContentIntent(pending)
                .setOngoing(true)
                .setPriority(Notification.PRIORITY_LOW)
                .build();
    }

    private void showOverlay(String value) {
        if (overlayView != null) removeOverlay();

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (windowManager == null) {
            stopSelf();
            return;
        }

        SharedPreferences prefs = AppPrefs.get(this);
        boolean light = prefs.getBoolean(AppPrefs.KEY_LIGHT_TEXT, true);
        int fg = light ? Color.WHITE : Color.BLACK;
        int muted = light ? Color.argb(145, 255, 255, 255) : Color.argb(120, 0, 0, 0);
        int panel = light ? Color.argb(220, 25, 25, 27) : Color.argb(232, 247, 247, 247);

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
        text.setText(value);
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

        track.addView(knob, new FrameLayout.LayoutParams(dp(38), dp(38), Gravity.CENTER));
        LinearLayout.LayoutParams trackParams = new LinearLayout.LayoutParams(dp(174), dp(42));
        trackParams.topMargin = dp(5);
        card.addView(track, trackParams);

        dragLimit = dp(62);
        dismissThreshold = dp(50);
        knob.setOnTouchListener((v, event) -> handleDrag(v, event));

        overlayView = card;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                dp(310),
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.CENTER;

        try {
            windowManager.addView(overlayView, params);
        } catch (Exception ignored) {
            overlayView = null;
            stopSelf();
        }
    }

    private boolean handleDrag(View knob, MotionEvent event) {
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
                    removeOverlay();
                    stopSelf();
                } else {
                    knob.animate().translationX(0f).setDuration(140L).start();
                }
                return true;
            default:
                return false;
        }
    }

    private void removeOverlay() {
        if (overlayView != null && windowManager != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (Exception ignored) {
            }
        }
        overlayView = null;
    }

    @Override
    public void onDestroy() {
        removeOverlay();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
