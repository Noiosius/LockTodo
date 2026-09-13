package com.batterycheck.app;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;

final class ChargeSession {
    private static final String PREFS = "battery_check_charge_session";
    private static final String KEY_START_TIME = "start_time";
    private static final String KEY_START_LEVEL = "start_level";
    private static final String KEY_START_COUNTER_MAH = "start_counter_mah_bits";
    private static final String KEY_CAPACITY_MAH = "capacity_mah_bits";

    private ChargeSession() {}

    static void start(Context context) {
        Sample s = sample(context);
        SharedPreferences.Editor e = prefs(context).edit()
                .putLong(KEY_START_TIME, System.currentTimeMillis())
                .putInt(KEY_START_LEVEL, s.level);

        if (!Double.isNaN(s.chargeCounterMah)) {
            e.putLong(KEY_START_COUNTER_MAH, Double.doubleToRawLongBits(s.chargeCounterMah));
        } else {
            e.remove(KEY_START_COUNTER_MAH);
        }

        double capacity = estimateCapacityMah(s.level, s.chargeCounterMah);
        if (!Double.isNaN(capacity)) {
            e.putLong(KEY_CAPACITY_MAH, Double.doubleToRawLongBits(capacity));
        } else {
            e.remove(KEY_CAPACITY_MAH);
        }
        e.apply();
    }

    static void stop(Context context) {
        prefs(context).edit().clear().apply();
    }

    static void ensureStarted(Context context) {
        if (prefs(context).getLong(KEY_START_TIME, 0L) <= 0L) {
            start(context);
        }
    }

    static double getPercentPerHour(Context context, int currentLevel) {
        SharedPreferences p = prefs(context);
        long startTime = p.getLong(KEY_START_TIME, 0L);
        int startLevel = p.getInt(KEY_START_LEVEL, -1);
        if (startTime <= 0L || startLevel < 0 || currentLevel < 0) return Double.NaN;

        long elapsedMs = System.currentTimeMillis() - startTime;
        if (elapsedMs < 60_000L) return Double.NaN;
        double hours = elapsedMs / 3_600_000.0;
        if (hours <= 0.0) return Double.NaN;

        Sample now = sample(context);
        double startCounter = getStoredDouble(p, KEY_START_COUNTER_MAH);
        double capacity = getStoredDouble(p, KEY_CAPACITY_MAH);

        // Prefer the charge counter when the phone exposes it. It changes more smoothly than
        // the integer battery percentage and therefore gives a useful session speed sooner.
        if (!Double.isNaN(startCounter) && !Double.isNaN(now.chargeCounterMah)
                && !Double.isNaN(capacity) && capacity > 0.0) {
            double addedMah = now.chargeCounterMah - startCounter;
            if (addedMah > 0.0) {
                double speed = (addedMah / capacity * 100.0) / hours;
                if (speed >= 3.0 && speed <= 250.0) return speed;
            }
        }

        // Fallback: use actual percentage gained during this charge session. Require at least
        // one full percent so integer rounding does not create extreme values in the first minute.
        int gainedPercent = currentLevel - startLevel;
        if (gainedPercent >= 1 && elapsedMs >= 120_000L) {
            double speed = gainedPercent / hours;
            if (speed >= 3.0 && speed <= 250.0) return speed;
        }

        return Double.NaN;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static double getStoredDouble(SharedPreferences p, String key) {
        if (!p.contains(key)) return Double.NaN;
        return Double.longBitsToDouble(p.getLong(key, 0L));
    }

    private static Sample sample(Context context) {
        Intent battery = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int level = -1;
        if (battery != null) {
            int rawLevel = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            if (rawLevel >= 0 && scale > 0) level = Math.round(rawLevel * 100f / scale);
        }

        double counterMah = Double.NaN;
        BatteryManager manager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        if (manager != null) {
            int raw = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
            if (raw != Integer.MIN_VALUE && raw > 0) {
                // Android specifies microamp-hours, but keep a small-value fallback for vendor
                // implementations that expose milliamp-hours instead.
                counterMah = raw > 100_000 ? raw / 1000.0 : raw;
                if (counterMah < 100.0 || counterMah > 10_000.0) counterMah = Double.NaN;
            }
        }
        return new Sample(level, counterMah);
    }

    private static double estimateCapacityMah(int level, double counterMah) {
        if (level > 5 && level <= 100 && !Double.isNaN(counterMah)) {
            double full = counterMah / (level / 100.0);
            if (full >= 2500.0 && full <= 5000.0) return full;
        }
        return 4000.0; // Galaxy S21 nominal capacity fallback.
    }

    private static final class Sample {
        final int level;
        final double chargeCounterMah;

        Sample(int level, double chargeCounterMah) {
            this.level = level;
            this.chargeCounterMah = chargeCounterMah;
        }
    }
}
