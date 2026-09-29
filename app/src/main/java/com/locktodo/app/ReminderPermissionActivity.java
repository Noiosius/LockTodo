package com.locktodo.app;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;

public class ReminderPermissionActivity extends Activity {
    private int step = 0;
    private boolean returning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            step = savedInstanceState.getInt("step", 0);
            returning = savedInstanceState.getBoolean("returning", false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (returning) {
            returning = false;
            step++;
        }

        while (step < 3) {
            Intent intent = null;

            if (step == 0) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !ReminderScheduler.canScheduleExact(this)) {
                    intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                }
            } else if (step == 1) {
                if (!Settings.canDrawOverlays(this)) {
                    intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                }
            } else if (step == 2 && Build.VERSION.SDK_INT >= 34) {
                NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                if (manager != null && !manager.canUseFullScreenIntent()) {
                    intent = new Intent("android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT");
                    intent.setData(Uri.parse("package:" + getPackageName()));
                }
            }

            if (intent != null) {
                try {
                    returning = true;
                    startActivity(intent);
                    return;
                } catch (Exception ignored) {
                }
            }

            step++;
        }

        finish();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt("step", step);
        outState.putBoolean("returning", returning);
        super.onSaveInstanceState(outState);
    }
}
