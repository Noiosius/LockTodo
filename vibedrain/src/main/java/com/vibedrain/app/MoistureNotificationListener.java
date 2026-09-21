package com.vibedrain.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.Locale;

public class MoistureNotificationListener extends NotificationListenerService {
    private static final String CHANNEL_ID = "moisture_shortcut";
    private static final int NOTIFICATION_ID = 7301;
    private static final long ALERT_COOLDOWN_MS = 60_000L;

    private static final String STATE_PREFS = "moisture_alert_state";
    private static final String KEY_LAST_ALERT = "last_alert";

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        createChannel();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;
        if (getPackageName().equals(sbn.getPackageName())) return;
        if (!isSystemLikePackage(sbn.getPackageName())) return;

        String content = notificationText(sbn.getNotification());
        if (!looksLikeMoistureWarning(content)) return;

        long now = System.currentTimeMillis();
        SharedPreferences prefs = getSharedPreferences(STATE_PREFS, MODE_PRIVATE);
        long last = prefs.getLong(KEY_LAST_ALERT, 0L);
        if (now - last < ALERT_COOLDOWN_MS) return;

        prefs.edit().putLong(KEY_LAST_ALERT, now).apply();
        showShortcutNotification();
    }

    private boolean isSystemLikePackage(String packageName) {
        if (packageName == null) return false;
        return packageName.equals("android")
                || packageName.equals("com.android.systemui")
                || packageName.startsWith("com.samsung.android.")
                || packageName.startsWith("com.sec.android.");
    }

    private String notificationText(Notification notification) {
        if (notification == null) return "";

        Bundle extras = notification.extras;
        if (extras == null) return "";

        StringBuilder text = new StringBuilder();
        append(text, extras.getCharSequence(Notification.EXTRA_TITLE));
        append(text, extras.getCharSequence(Notification.EXTRA_TEXT));
        append(text, extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
        append(text, extras.getCharSequence(Notification.EXTRA_SUB_TEXT));
        append(text, extras.getCharSequence(Notification.EXTRA_INFO_TEXT));

        CharSequence[] lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        if (lines != null) {
            for (CharSequence line : lines) append(text, line);
        }

        return text.toString().toLowerCase(Locale.ROOT);
    }

    private void append(StringBuilder out, CharSequence value) {
        if (value == null) return;
        if (out.length() > 0) out.append(' ');
        out.append(value);
    }

    private boolean looksLikeMoistureWarning(String text) {
        if (text == null || text.isEmpty()) return false;

        boolean moisture =
                containsAny(text,
                        "수분", "습기", "물기",
                        "moisture", "wet", "liquid");

        boolean port =
                containsAny(text,
                        "usb", "충전", "단자", "포트", "커넥터",
                        "charger", "charging", "port", "connector");

        return moisture && port;
    }

    private boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) return true;
        }
        return false;
    }

    private void showShortcutNotification() {
        createChannel();

        Intent open = new Intent(this, MainActivity.class);
        open.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
        );
        open.putExtra("from_moisture_alert", true);

        PendingIntent pending = PendingIntent.getActivity(
                this,
                7302,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        builder.setSmallIcon(R.drawable.ic_water_alert)
                .setContentTitle("수분 감지 알림")
                .setContentText("Vibe Drain 열기")
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_RECOMMENDATION)
                .setPriority(Notification.PRIORITY_HIGH)
                .setColor(Color.WHITE)
                .addAction(
                        new Notification.Action.Builder(
                                null,
                                "앱 열기",
                                pending
                        ).build()
                );

        NotificationManager manager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            try {
                manager.notify(NOTIFICATION_ID, builder.build());
            } catch (SecurityException ignored) {
                // Android 13+: notification permission not granted yet.
            }
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationManager manager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager == null) return;

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "수분 감지 바로가기",
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription("삼성 수분·습기 경고 감지 시 Vibe Drain 바로가기 표시");
        channel.enableVibration(true);
        channel.setVibrationPattern(new long[]{0L, 120L, 90L, 120L});
        channel.setSound(null, null);
        manager.createNotificationChannel(channel);
    }
}
