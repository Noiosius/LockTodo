package com.batterycheck.app;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.text.format.DateFormat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

final class BatteryReader {
    private static final double S21_FALLBACK_CAPACITY_MAH = 4000.0;
    private static final int CURRENT_SAMPLE_WINDOW = 16;
    private static final int MIN_ESTIMATE_SAMPLES = 12;
    private static final int STABLE_WINDOWS_REQUIRED = 3;
    private static final int MAX_WARMUP_SAMPLES = 24;
    private static final double STABILITY_CV_LIMIT = 0.25;

    private static final ArrayDeque<Double> currentSamplesMa = new ArrayDeque<>();
    private static int validSampleCount = 0;
    private static int stableWindowCount = 0;
    private static boolean estimatesReady = false;

    static final class Snapshot {
        final boolean charging;
        final boolean estimatesReady;
        final int level;
        final double watts;
        final double avgPercentPerHour;
        final double temperatureC;
        final long remainingMinutes;
        final String fullTime;

        Snapshot(boolean charging, boolean estimatesReady, int level, double watts, double avgPercentPerHour,
                 double temperatureC, long remainingMinutes, String fullTime) {
            this.charging = charging;
            this.estimatesReady = estimatesReady;
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
            return new Snapshot(false, false, -1, Double.NaN, Double.NaN, Double.NaN, -1, "—");
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
        double chargeSpeedPercentPerHour = Double.NaN;
        long remainingMinutes = -1;
        long remainingMs = -1;

        if (!charging) {
            clearCurrentSamples();
        }

        if (manager != null && charging) {
            int currentNowRaw = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
            int currentAverageRaw = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE);

            double currentNowMa = normalizeCurrentMa(currentNowRaw);
            double currentAverageMa = normalizeCurrentMa(currentAverageRaw);

            // Prefer the live current. If a device momentarily reports no live value, use its
            // hardware-averaged current as a fallback. The same smoothed current drives BOTH W
            // and %/h so the two values always describe the same charging state.
            double sampleMa = !Double.isNaN(currentNowMa)
                    ? Math.abs(currentNowMa)
                    : (!Double.isNaN(currentAverageMa) ? Math.abs(currentAverageMa) : Double.NaN);

            double smoothedCurrentMa = addAndGetSmoothedCurrent(sampleMa);

            if (!Double.isNaN(smoothedCurrentMa) && voltageMv > 0) {
                watts = smoothedCurrentMa / 1000.0 * (voltageMv / 1000.0);
            }

            double fullCapacityMah = estimateFullCapacityMah(manager, level);
            if (estimatesReady && !Double.isNaN(smoothedCurrentMa) && smoothedCurrentMa > 0.0
                    && fullCapacityMah > 0.0) {
                // Instant-equivalent charging speed: if the present net charging current stayed
                // the same for one hour, approximately this many battery percentage points would
                // be added. This is intentionally NOT a charging-session average.
                chargeSpeedPercentPerHour = smoothedCurrentMa / fullCapacityMah * 100.0;

                if (chargeSpeedPercentPerHour < 0.1 || chargeSpeedPercentPerHour > 300.0) {
                    chargeSpeedPercentPerHour = Double.NaN;
                }
            }

            // Remaining time is intentionally based on the same present charging speed, so it is
            // internally consistent with the %/h field. Near full charge Android may taper current,
            // so this is a "current-rate" estimate rather than a prediction of future tapering.
            if (!Double.isNaN(chargeSpeedPercentPerHour) && chargeSpeedPercentPerHour > 0.0
                    && level >= 0 && level < 100) {
                remainingMinutes = Math.max(1,
                        Math.round((100.0 - level) / chargeSpeedPercentPerHour * 60.0));
                remainingMs = remainingMinutes * 60_000L;
            } else if (level >= 100) {
                remainingMinutes = 0;
                remainingMs = 0;
            }

            // Last-resort fallback only when the phone does not expose usable charging current.
            if (remainingMinutes < 0) {
                try {
                    long systemRemainingMs = manager.computeChargeTimeRemaining();
                    if (systemRemainingMs > 0) {
                        remainingMs = systemRemainingMs;
                        remainingMinutes = Math.max(1, Math.round(systemRemainingMs / 60000.0));
                    }
                } catch (Throwable ignored) {
                    // Not available on every device.
                }
            }
        }

        String fullTime = "—";
        if (remainingMs > 0) {
            fullTime = DateFormat.getTimeFormat(context)
                    .format(new Date(System.currentTimeMillis() + remainingMs));
        } else if (remainingMinutes == 0) {
            fullTime = DateFormat.getTimeFormat(context).format(new Date());
        }

        return new Snapshot(
                charging,
                estimatesReady,
                level,
                watts,
                chargeSpeedPercentPerHour,
                temperatureC,
                remainingMinutes,
                fullTime
        );
    }

    private static synchronized double addAndGetSmoothedCurrent(double sampleMa) {
        if (!Double.isNaN(sampleMa) && sampleMa > 0.0) {
            currentSamplesMa.addLast(sampleMa);
            validSampleCount++;
            while (currentSamplesMa.size() > CURRENT_SAMPLE_WINDOW) {
                currentSamplesMa.removeFirst();
            }
        }

        if (currentSamplesMa.isEmpty()) return Double.NaN;

        List<Double> values = new ArrayList<>(currentSamplesMa);
        Collections.sort(values);

        int trim;
        if (values.size() >= MIN_ESTIMATE_SAMPLES) {
            trim = 2;
        } else if (values.size() >= 5) {
            trim = 1;
        } else {
            trim = 0;
        }

        int start = trim;
        int end = values.size() - trim;
        double total = 0.0;
        for (int i = start; i < end; i++) total += values.get(i);
        double mean = total / (end - start);

        if (!estimatesReady && values.size() >= MIN_ESTIMATE_SAMPLES) {
            double variance = 0.0;
            for (int i = start; i < end; i++) {
                double delta = values.get(i) - mean;
                variance += delta * delta;
            }
            variance /= Math.max(1, end - start);
            double coefficientOfVariation = mean > 0.0 ? Math.sqrt(variance) / mean : Double.POSITIVE_INFINITY;

            if (coefficientOfVariation <= STABILITY_CV_LIMIT) {
                stableWindowCount++;
            } else {
                stableWindowCount = 0;
            }

            if (stableWindowCount >= STABLE_WINDOWS_REQUIRED || validSampleCount >= MAX_WARMUP_SAMPLES) {
                estimatesReady = true;
            }
        }

        return mean;
    }

    static synchronized void resetEstimates() {
        clearCurrentSamples();
    }

    private static synchronized void clearCurrentSamples() {
        currentSamplesMa.clear();
        validSampleCount = 0;
        stableWindowCount = 0;
        estimatesReady = false;
    }

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
                if (estimatedFullMah >= 2500.0 && estimatedFullMah <= 5000.0) {
                    return estimatedFullMah;
                }
            }
        }
        return S21_FALLBACK_CAPACITY_MAH;
    }
}
