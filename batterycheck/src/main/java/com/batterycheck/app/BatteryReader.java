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
    // 0.5 s polling: 10 samples ≈ 5 seconds.
    // Compare the previous 5-second window with the newest 5-second window.
    private static final int FIVE_SECOND_SAMPLES = 10;
    private static final int STABILITY_BUFFER_SAMPLES = FIVE_SECOND_SAMPLES * 2;
    private static final int STABLE_CHECKS_REQUIRED = 3;
    private static final double RECENT_CV_LIMIT = 0.08;
    private static final double WINDOW_SHIFT_LIMIT = 0.04;

    private static final ArrayDeque<Double> currentSamplesMa = new ArrayDeque<>();
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
            while (currentSamplesMa.size() > STABILITY_BUFFER_SAMPLES) {
                currentSamplesMa.removeFirst();
            }
        }

        if (currentSamplesMa.isEmpty()) return Double.NaN;

        List<Double> chronological = new ArrayList<>(currentSamplesMa);

        // The displayed current always follows the newest ~5 seconds, with one high and
        // one low outlier removed when enough readings are available.
        int recentStart = Math.max(0, chronological.size() - FIVE_SECOND_SAMPLES);
        List<Double> recent = new ArrayList<>(chronological.subList(recentStart, chronological.size()));
        double recentMean = trimmedMean(recent);

        // Do not reveal charging speed / remaining time until two adjacent 5-second windows
        // are both calm and close to each other. There is intentionally no forced timeout:
        // if the charging current is still drifting, the estimate stays hidden.
        if (!estimatesReady && chronological.size() >= STABILITY_BUFFER_SAMPLES) {
            List<Double> previous = new ArrayList<>(
                    chronological.subList(0, FIVE_SECOND_SAMPLES));
            List<Double> newest = new ArrayList<>(
                    chronological.subList(FIVE_SECOND_SAMPLES, STABILITY_BUFFER_SAMPLES));

            double previousMean = trimmedMean(previous);
            double newestMean = trimmedMean(newest);
            double newestCv = trimmedCoefficientOfVariation(newest);

            double windowShift = previousMean > 0.0
                    ? Math.abs(newestMean - previousMean) / previousMean
                    : Double.POSITIVE_INFINITY;

            boolean stableNow = newestCv <= RECENT_CV_LIMIT
                    && windowShift <= WINDOW_SHIFT_LIMIT;

            if (stableNow) {
                stableWindowCount++;
            } else {
                stableWindowCount = 0;
            }

            if (stableWindowCount >= STABLE_CHECKS_REQUIRED) {
                estimatesReady = true;
            }
        }

        return recentMean;
    }

    private static double trimmedMean(List<Double> source) {
        if (source.isEmpty()) return Double.NaN;

        List<Double> values = new ArrayList<>(source);
        Collections.sort(values);
        int trim = values.size() >= 5 ? 1 : 0;
        int start = trim;
        int end = values.size() - trim;

        double total = 0.0;
        for (int i = start; i < end; i++) total += values.get(i);
        return total / Math.max(1, end - start);
    }

    private static double trimmedCoefficientOfVariation(List<Double> source) {
        if (source.isEmpty()) return Double.POSITIVE_INFINITY;

        List<Double> values = new ArrayList<>(source);
        Collections.sort(values);
        int trim = values.size() >= 5 ? 1 : 0;
        int start = trim;
        int end = values.size() - trim;

        double total = 0.0;
        for (int i = start; i < end; i++) total += values.get(i);
        double mean = total / Math.max(1, end - start);
        if (mean <= 0.0) return Double.POSITIVE_INFINITY;

        double variance = 0.0;
        for (int i = start; i < end; i++) {
            double delta = values.get(i) - mean;
            variance += delta * delta;
        }
        variance /= Math.max(1, end - start);
        return Math.sqrt(variance) / mean;
    }

    static synchronized void resetEstimates() {
        clearCurrentSamples();
    }

    private static synchronized void clearCurrentSamples() {
        currentSamplesMa.clear();
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
