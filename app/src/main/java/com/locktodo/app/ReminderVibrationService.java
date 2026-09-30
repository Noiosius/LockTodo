package com.locktodo.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

public class ReminderVibrationService extends Service {
    static final String ACTION_START =
            "com.locktodo.app.ACTION_START_REMINDER_VIBRATION";
    static final String ACTION_STOP =
            "com.locktodo.app.ACTION_STOP_REMINDER_VIBRATION";

    private static final int FOREGROUND_ID = 9305;
    private static final String CHANNEL_ID = "locktodo_reminder_vibration";

    private Vibrator vibrator;
    private PowerManager.WakeLock wakeLock;

    @Override
    public void onCreate() {
        super.onCreate();
        vibrator = getVibrator();

        PowerManager power =
                (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (power != null) {
            wakeLock = power.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "LockTodo:ReminderVibration"
            );
            wakeLock.setReferenceCounted(false);
        }

        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();

        if (ACTION_STOP.equals(action)) {
            stopVibration();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        startForeground(FOREGROUND_ID, buildNotification());
        startVibration();
        return START_STICKY;
    }

    private void startVibration() {
        if (vibrator == null || !vibrator.hasVibrator()) {
            stopSelf();
            return;
        }

        try {
            if (wakeLock != null && !wakeLock.isHeld()) {
                wakeLock.acquire();
            }

            AudioAttributes attributes =
                    new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build();

            vibrator.cancel();

            long[] timings = new long[]{0L, 220L, 110L, 260L, 450L};
            int[] amplitudes = new int[]{
                    0,
                    vibrator.hasAmplitudeControl() ? 255 : VibrationEffect.DEFAULT_AMPLITUDE,
                    0,
                    vibrator.hasAmplitudeControl() ? 255 : VibrationEffect.DEFAULT_AMPLITUDE,
                    0
            };

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                VibrationEffect effect =
                        VibrationEffect.createWaveform(
                                timings,
                                amplitudes,
                                0
                        );
                vibrator.vibrate(effect, attributes);
            } else {
                vibrator.vibrate(timings, 0, attributes);
            }
        } catch (Throwable ignored) {
        }
    }

    private void stopVibration() {
        if (vibrator != null) {
            try {
                vibrator.cancel();
            } catch (Throwable ignored) {
            }
        }

        if (wakeLock != null && wakeLock.isHeld()) {
            try {
                wakeLock.release();
            } catch (Throwable ignored) {
            }
        }
    }

    private Notification buildNotification() {
        Intent stop = new Intent(this, ReminderVibrationService.class);
        stop.setAction(ACTION_STOP);

        PendingIntent stopPending = PendingIntent.getService(
                this,
                9305,
                stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder =
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? new Notification.Builder(this, CHANNEL_ID)
                        : new Notification.Builder(this);

        return builder
                .setSmallIcon(R.drawable.ic_lock_todo)
                .setContentTitle("LockTodo")
                .setContentText("알림 진동 중")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(Notification.PRIORITY_LOW)
                .addAction(
                        new Notification.Action.Builder(
                                null,
                                "진동 끄기",
                                stopPending
                        ).build()
                )
                .build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationManager manager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager == null) return;

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "LockTodo 알림 진동",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setSound(null, null);
        channel.enableVibration(false);
        manager.createNotificationChannel(channel);
    }

    private Vibrator getVibrator() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager manager =
                        (VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                return manager == null ? null : manager.getDefaultVibrator();
            }
            return (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        } catch (Throwable ignored) {
            return null;
        }
    }

    static void start(Context context) {
        Intent intent = new Intent(context, ReminderVibrationService.class);
        intent.setAction(ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    static void stop(Context context) {
        Intent intent = new Intent(context, ReminderVibrationService.class);
        intent.setAction(ACTION_STOP);
        try {
            context.startService(intent);
        } catch (Throwable ignored) {
            context.stopService(new Intent(context, ReminderVibrationService.class));
        }
    }

    @Override
    public void onDestroy() {
        stopVibration();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
