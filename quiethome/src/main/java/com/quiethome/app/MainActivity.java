package com.quiethome.app;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;

import java.lang.reflect.Method;

import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {
    private static final int SHIZUKU_PERMISSION_REQUEST = 3001;
    private boolean actionStarted = false;

    private final Shizuku.OnBinderReceivedListener binderReceivedListener =
            this::continueAfterShizukuReady;

    private final Shizuku.OnRequestPermissionResultListener permissionResultListener =
            (requestCode, grantResult) -> {
                if (requestCode != SHIZUKU_PERMISSION_REQUEST || actionStarted) return;
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    actionStarted = true;
                    runQuietHome();
                } else {
                    Toast.makeText(this, "Shizuku 권한이 필요합니다", Toast.LENGTH_SHORT).show();
                    finish();
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setShowWhenLocked(true);
        getWindow().getDecorView().setBackgroundColor(Color.TRANSPARENT);

        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null || !nm.isNotificationPolicyAccessGranted()) {
            Toast.makeText(this, "처음 한 번만 방해금지 접근을 허용해 주세요", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
            finish();
            return;
        }

        Shizuku.addRequestPermissionResultListener(permissionResultListener);
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!actionStarted && !Shizuku.pingBinder()) {
                openShizukuForSetup();
            }
        }, 900L);
    }

    private void continueAfterShizukuReady() {
        if (actionStarted || !Shizuku.pingBinder()) return;

        try {
            if (Shizuku.isPreV11()) {
                Toast.makeText(this, "현재 Shizuku 버전은 지원하지 않습니다", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                actionStarted = true;
                runQuietHome();
            } else if (Shizuku.shouldShowRequestPermissionRationale()) {
                Toast.makeText(this, "Shizuku에서 Quiet Home 권한을 허용해 주세요", Toast.LENGTH_LONG).show();
                finish();
            } else {
                Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST);
            }
        } catch (Throwable e) {
            openShizukuForSetup();
        }
    }

    private void openShizukuForSetup() {
        if (isFinishing() || actionStarted) return;
        Toast.makeText(this, "Shizuku를 시작한 뒤 Quiet Home을 다시 실행해 주세요", Toast.LENGTH_LONG).show();
        Intent launch = getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(launch);
        }
        finish();
    }

    private void runQuietHome() {
        enableDoNotDisturb();

        new Thread(() -> {
            runShellCleanup();
            runOnUiThread(this::goHomeAndFinish);
        }, "QuietHome-Cleanup").start();
    }

    private void enableDoNotDisturb() {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null && nm.isNotificationPolicyAccessGranted()) {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY);
            }
        } catch (Throwable ignored) {
        }
    }

    private void runShellCleanup() {
        try {
            Method method = Shizuku.class.getDeclaredMethod(
                    "newProcess", String[].class, String[].class, String.class);
            method.setAccessible(true);

            Object remote = method.invoke(
                    null,
                    new Object[]{
                            new String[]{"sh", "-c", "am clear-recent-apps; am kill-all"},
                            null,
                            null
                    }
            );

            if (remote instanceof Process) {
                Process process = (Process) remote;
                try {
                    process.waitFor();
                } finally {
                    process.destroy();
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void goHomeAndFinish() {
        try {
            Intent home = new Intent(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME);
            home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(home);
        } catch (Throwable ignored) {
        }
        finishAndRemoveTask();
    }

    @Override
    protected void onDestroy() {
        try {
            Shizuku.removeRequestPermissionResultListener(permissionResultListener);
            Shizuku.removeBinderReceivedListener(binderReceivedListener);
        } catch (Throwable ignored) {
        }
        super.onDestroy();
    }
}
