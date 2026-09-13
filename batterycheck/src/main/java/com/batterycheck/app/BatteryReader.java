package com.batterycheck.app;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.text.format.DateFormat;

import java.util.Date;

final class BatteryReader {
    private static final double S21_FALLBACK_CAPACITY_MAH = 4000.0;

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
        double avgPercentPerHour = Double.NaN;
        long remainingMinutes = -1;
        long remainingMs = -1;

        if (manager != null && charging) {
            int currentNowRaw = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
            int currentAverageRaw = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE);

            double currentNowMa = normalizeCurrentMa(currentNowRaw);
            double currentAverageMa = normalizeCurrentMa(currentAverageRaw);

            if (!Double.isNaN(currentNowMa) && voltageMv > 0) {
                watts = Math.abs(currentNowMa) / 1000.0 * (voltageMv / 1000.0);
            }

            double currentForSpeedMa = !Double.isNaN(currentAverageMa)
                    ? Math.abs(currentAverageMa)
                    : (!Double.isNaN(currentNowMa) ? Math.abs(currentNowMa) : Double.NaN);

            double fullCapacityMah = estimateFullCapacityMah(manager, level);
            if (!Double.isNaN(currentForSpeedMa) && currentForSpeedMa > 0
                    && fullCapacityMah > 0 && level >= 0 && level < 100) {
                avgPercentPerHour = currentForSpeedMa / fullCapacityMah * 100.0;

                // Reject obviously invalid vendor readings and fall back to the system estimate below.
                if (avgPercentPerHour >= 3.0 && avgPercentPerHour <= 250.0) {
                    remainingMinutes = Math.max(1,
                            Math.round((100.0 - level) / avgPercentPerHour * 60.0));
                    remainingMs = remainingMinutes * 60_000L;
                } else {
                    avgPercentPerHour = Double.NaN;
                }
            }

            // Only use Android's estimate as a fallback. It is deliberately not used to derive
            // the displayed average speed, because that made the two values circular in v0.1.
            if (remainingMinutes <= 0) {
                try {
                    long systemRemainingMs = manager.computeChargeTimeRemaining();
                    if (systemRemainingMs > 0) {
                        remainingMs = systemRemainingMs;
                        remainingMinutes = Math.max(1, Math.round(systemRemainingMs / 60000.0));
                        if (Double.isNaN(avgPercentPerHour) && level >= 0 && level < 100) {
                            double hours = systemRemainingMs / 3_600_000.0;
                            if (hours > 0) avgPercentPerHour = (100.0 - level) / hours;
                        }
                    }
                } catch (Throwable ignored) {
                    // No estimate available on this device.
                }
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

    /**
     * Android documents CURRENT_NOW/CURRENT_AVERAGE in microamps, but several Samsung devices
     * expose milliamp-sized values. Values in the normal phone-charging mA range are therefore
     * treated as mA; larger values are treated as microamps.
     */
    private static double normalizeCurrentMa(int raw) {
        if (raw == Integer.MIN_VALUE || raw == 0) return Double.NaN;
        double abs = Math.abs((double) raw);
        return abs < 20_000.0 ? abs : abs / 1000.0;
    }

    private static double estimateFullCapacityMah(BatteryManager manager, int level) {
        if (level > 5 && level <= 100) {
            int rawChargeCounter = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
            if (rawChargeCounter != Integer.MIN_VALUE && rawChargeCounter > 0) {
                double counterMah = rawChargeCounter > 100_000
                        ? rawChargeCounter / 1000.0
                        : rawChargeCounter;
                double estimatedFullMah = counterMah / (level / 100.0);

                // S21 nominal capacity is 4000 mAh. Keep only plausible learned values so a
                // vendor-specific/unsupported charge counter cannot distort the result.
                if (estimatedFullMah >= 2500.0 && estimatedFullMah <= 5000.0) {
                    return estimatedFullMah;
                }
            }
        }
        return S21_FALLBACK_CAPACITY_MAH;
    }
}
