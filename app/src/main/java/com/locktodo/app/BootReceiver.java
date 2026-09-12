package com.locktodo.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) &&
                AppPrefs.get(context).getBoolean(AppPrefs.KEY_AUTO_SHOW, true)) {
            try {
                LockService.start(context);
            } catch (Exception ignored) {
            }
        }
    }
}
