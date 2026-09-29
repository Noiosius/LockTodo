package com.locktodo.app;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import java.util.List;

final class ReminderScheduler {
    static final String ACTION_FIRE_REMINDER = "com.locktodo.app.ACTION_FIRE_REMINDER";
    private static final int ALARM_REQUEST_CODE = 9301;

    private ReminderScheduler() {
    }

    static void reschedule(Context context) {
        Context app = context.getApplicationContext();
        AlarmManager alarm = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        if (alarm == null) return;

        PendingIntent pending = alarmPendingIntent(app);
        alarm.cancel(pending);

        TodoStore store = new TodoStore(app);
        List<Long> reminders = store.loadReminders();
        long next = Long.MAX_VALUE;
        for (long reminderAt : reminders) {
            if (reminderAt > 0L && reminderAt < next) next = reminderAt;
        }
        if (next == Long.MAX_VALUE) return;

        long triggerAt = Math.max(next, System.currentTimeMillis() + 500L);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarm.canScheduleExactAlarms()) {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending);
            return;
        }
        alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending);
    }

    static boolean canScheduleExact(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true;
        AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        return alarm != null && alarm.canScheduleExactAlarms();
    }

    static void requestExactAlarmPermission(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || canScheduleExact(activity)) return;
        try {
            Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
            intent.setData(Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(intent);
        } catch (Exception ignored) {
        }
    }

    private static PendingIntent alarmPendingIntent(Context context) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction(ACTION_FIRE_REMINDER);
        return PendingIntent.getBroadcast(
                context,
                ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }
}
