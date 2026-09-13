package com.batterycheck.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class PowerReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_POWER_CONNECTED.equals(action)) {
            ChargeSession.start(context);
        } else if (Intent.ACTION_POWER_DISCONNECTED.equals(action)) {
            ChargeSession.stop(context);
        }
    }
}
