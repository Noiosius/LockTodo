package com.locktodo.app;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

import java.util.List;

public class ReminderReceiver extends BroadcastReceiver {
    static final String EXTRA_REMINDER_TEXT = "reminder_text";
    static final String EXTRA_ITEM_INDEX = "reminder_item_index";
    static final int NOTIFICATION_ID = 9302;
    private static final String CHANNEL_ID = "locktodo_reminders";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ReminderScheduler.ACTION_FIRE_REMINDER.equals(intent.getAction())) return;

        TodoStore store = new TodoStore(context);
        List<String> items = store.load();
        List<Long> reminders = store.loadReminders();
        if (items.isEmpty() || reminders.isEmpty()) {
            ReminderScheduler.reschedule(context);
            return;
        }

        long now = System.currentTimeMillis();
        int dueIndex = -1;
        long dueAt = Long.MAX_VALUE;
        for (int i = 0; i < Math.min(items.size(), reminders.size()); i++) {
            long at = reminders.get(i);
            if (at > 0L && at <= now + 60_000L && at < dueAt) {
                dueAt = at;
                dueIndex = i;
            }
        }

        if (dueIndex < 0) {
            ReminderScheduler.reschedule(context);
            return;
        }

        String text = items.get(dueIndex);
        boolean vibrate = store.getReminderVibrate(dueIndex);

        store.clearReminderAtIfMatches(dueIndex, dueAt);
        LockTodoWidget.updateAll(context);
        ReminderScheduler.reschedule(context);

        if (vibrate) {
            ReminderVibrationService.start(context);
        }

        showReminder(context, text, dueIndex);
    }

    private void showReminder(Context context, String text, int itemIndex) {
        PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        KeyguardManager keyguard = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);

        boolean interactive = power != null && power.isInteractive();
        boolean locked = keyguard != null && keyguard.isKeyguardLocked();

        if (!interactive) wakeScreen(context);

        if (interactive && !locked && Settings.canDrawOverlays(context)) {
            Intent overlay = new Intent(context, ReminderOverlayService.class);
            overlay.putExtra(ReminderOverlayService.EXTRA_TEXT, text);
            overlay.putExtra(ReminderOverlayService.EXTRA_ITEM_INDEX, itemIndex);
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(overlay);
                } else {
                    context.startService(overlay);
                }
                return;
            } catch (Exception ignored) {
            }
        }

        showFullScreenReminder(context, text, itemIndex);
    }

    @SuppressWarnings("deprecation")
    private void wakeScreen(Context context) {
        PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (power == null || power.isInteractive()) return;

        try {
            PowerManager.WakeLock wakeLock = power.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK
                            | PowerManager.ACQUIRE_CAUSES_WAKEUP
                            | PowerManager.ON_AFTER_RELEASE,
                    "LockTodo:ReminderWake"
            );
            wakeLock.acquire(5000L);
        } catch (Exception ignored) {
        }
    }

    private void showFullScreenReminder(Context context, String text, int itemIndex) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "LockTodo 알림",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("설정한 시간에 할 일을 화면 가운데 표시합니다.");
            channel.enableVibration(false);
            channel.setSound(null, null);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            manager.createNotificationChannel(channel);
        }

        Intent popup = new Intent(context, ReminderPopupActivity.class);
        popup.putExtra(EXTRA_REMINDER_TEXT, text);
        popup.putExtra(EXTRA_ITEM_INDEX, itemIndex);
        popup.setData(Uri.parse("locktodo://reminder/" + System.currentTimeMillis()));
        popup.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent fullScreen = PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                popup,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(context, CHANNEL_ID)
                : new Notification.Builder(context);

        Notification notification = builder
                .setSmallIcon(R.drawable.ic_lock_todo)
                .setContentTitle("LockTodo")
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setCategory(Notification.CATEGORY_ALARM)
                .setPriority(Notification.PRIORITY_HIGH)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setColor(Color.TRANSPARENT)
                .setAutoCancel(true)
                .setContentIntent(fullScreen)
                .setFullScreenIntent(fullScreen, true)
                .setTimeoutAfter(10 * 60 * 1000L)
                .build();

        manager.notify(NOTIFICATION_ID, notification);

        boolean notificationAvailable = Build.VERSION.SDK_INT < Build.VERSION_CODES.N || manager.areNotificationsEnabled();
        boolean fullScreenAvailable = Build.VERSION.SDK_INT < 34 || manager.canUseFullScreenIntent();
        if (!notificationAvailable || !fullScreenAvailable) {
            try {
                context.startActivity(popup);
            } catch (Exception ignored) {
            }
        }
    }
}
