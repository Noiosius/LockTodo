package com.locktodo.app;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;

public class LockService extends Service {
    private static final String SERVICE_CHANNEL = "lock_todo_service";
    private static final String LOCK_CHANNEL = "lock_todo_lock";
    private static final int SERVICE_NOTIFICATION_ID = 41;
    private static final int LOCK_NOTIFICATION_ID = 42;

    private BroadcastReceiver screenReceiver;

    public static void start(Context context) {
        Intent intent = new Intent(context, LockService.class);
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent);
        else context.startService(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannels();
        startForeground(SERVICE_NOTIFICATION_ID, buildServiceNotification());
        registerScreenReceiver();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (screenReceiver != null) {
            try { unregisterReceiver(screenReceiver); } catch (Exception ignored) {}
            screenReceiver = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createChannels() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        NotificationChannel service = new NotificationChannel(
                SERVICE_CHANNEL, "Lock Todo 실행", NotificationManager.IMPORTANCE_LOW);
        service.setDescription("잠금화면 Todo 기능을 준비 상태로 유지합니다.");
        service.setShowBadge(false);
        service.setSound(null, null);
        service.enableVibration(false);
        service.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
        nm.createNotificationChannel(service);

        NotificationChannel lock = new NotificationChannel(
                LOCK_CHANNEL, "잠금화면 Todo 표시", NotificationManager.IMPORTANCE_HIGH);
        lock.setDescription("화면을 켰을 때 Todo 패널을 잠금화면 위에 표시합니다.");
        lock.setShowBadge(false);
        lock.setSound(null, null);
        lock.enableVibration(false);
        lock.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
        nm.createNotificationChannel(lock);
    }

    private Notification buildServiceNotification() {
        Intent settings = new Intent(this, SettingsActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, settings,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, SERVICE_CHANNEL)
                : new Notification.Builder(this);

        return builder
                .setSmallIcon(R.drawable.ic_lock_todo)
                .setContentTitle("Lock Todo")
                .setContentText("잠금화면 Todo 활성화")
                .setContentIntent(pi)
                .setOngoing(true)
                .setShowWhen(false)
                .setVisibility(Notification.VISIBILITY_SECRET)
                .build();
    }

    private void registerScreenReceiver() {
        screenReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (Intent.ACTION_SCREEN_ON.equals(action)) {
                    SharedPreferences prefs = AppPrefs.get(context);
                    if (!prefs.getBoolean(AppPrefs.KEY_AUTO_SHOW, true)) return;
                    KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
                    if (km != null && km.isKeyguardLocked()) showLockTodo();
                } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                    NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                    if (nm != null) nm.cancel(LOCK_NOTIFICATION_ID);
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screenReceiver, filter);
    }

    private void showLockTodo() {
        Intent activity = new Intent(this, LockTodoActivity.class);
        activity.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TOP |
                Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent fullScreen = PendingIntent.getActivity(this, 100, activity,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, LOCK_CHANNEL)
                : new Notification.Builder(this);

        Notification notification = builder
                .setSmallIcon(R.drawable.ic_lock_todo)
                .setContentTitle("Lock Todo")
                .setContentText("잠금화면 할 일")
                .setCategory(Notification.CATEGORY_ALARM)
                .setPriority(Notification.PRIORITY_MAX)
                .setVisibility(Notification.VISIBILITY_SECRET)
                .setFullScreenIntent(fullScreen, true)
                .setContentIntent(fullScreen)
                .setAutoCancel(true)
                .setTimeoutAfter(5000)
                .setShowWhen(false)
                .build();

        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(LOCK_NOTIFICATION_ID, notification);
    }
}
