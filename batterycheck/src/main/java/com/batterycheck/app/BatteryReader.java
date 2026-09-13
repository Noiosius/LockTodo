package com.batterycheck.app;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.text.format.DateFormat;

import java.util.Date;

final class BatteryReader {
    static final class Snapshot {
        final boolean charging;
        final int level;
        final double watts;
        final double avgPercentPerHour;
        final double temperatureC;
        final long remainingMinutes;
        final String fullTime;

        Snapshot(boolean charging, int level, double watts, double avgPercentPerHour,
                 double temperatureC, long remainingMinutes, String fullTime) {
            this.charging = charging;
            this.level = level;
            this.watts = watts;
            this.avgPercentPerHour = avgPercentPerHour;
            this.temperatureC = temperatureC;
            this.remainingMinutes = remainingMinutes;
            this.fullTime = fullTime;
        }
    }

    private BatteryReader() {}

    static Snapshot read(Context context) {
        Intent battery = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery == null) {
            return new Snapshot(false, -1, Double.NaN, Double.NaN, Double.NaN, -1, "—");
        }

        int status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN);
        boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL;

        int rawLevel = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int level = (rawLevel >= 0 && scale > 0)
                ? Math.round(rawLevel * 100f / scale)
                : -1;

        int voltageMv = battery.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
        int temperatureTenths = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
        double temperatureC = temperatureTenths == Integer.MIN_VALUE
                ? Double.NaN
                : temperatureTenths / 10.0;

        BatteryManager manager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        double watts = Double.NaN;
        long remainingMs = -1;

        if (manager != null) {
            int currentUa = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
            if (currentUa == Integer.MIN_VALUE || currentUa == 0) {
                currentUa = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE);
            }

            if (voltageMv > 0 && currentUa != Integer.MIN_VALUE && currentUa != 0) {
                watts = Math.abs(currentUa) / 1_000_000.0 * (voltageMv / 1000.0);
            }

            if (charging) {
                try {
                    remainingMs = manager.computeChargeTimeRemaining();
                } catch (Throwable ignored) {
                    remainingMs = -1;
                }
            }
        }

        long remainingMinutes = remainingMs > 0
                ? Math.max(1, Math.round(remainingMs / 60000.0))
                : -1;

        double avgPercentPerHour = Double.NaN;
        if (remainingMs > 0 && level >= 0 && level < 100) {
            double hours = remainingMs / 3_600_000.0;
            if (hours > 0.0) {
                avgPercentPerHour = (100.0 - level) / hours;
            }
        }

        String fullTime = "—";
        if (remainingMs > 0) {
            fullTime = DateFormat.getTimeFormat(context)
                    .format(new Date(System.currentTimeMillis() + remainingMs));
        }

        return new Snapshot(
                charging,
                level,
                watts,
                avgPercentPerHour,
                temperatureC,
                remainingMinutes,
                fullTime
        );
    }
}
