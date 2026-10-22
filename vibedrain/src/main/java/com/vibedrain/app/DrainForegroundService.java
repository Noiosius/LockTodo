package com.vibedrain.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

import java.util.Random;
import java.util.ArrayList;

public class DrainForegroundService extends Service {
    public static final String ACTION_START_VIBRATION =
            "com.vibedrain.app.START_VIBRATION";
    public static final String ACTION_STOP_VIBRATION =
            "com.vibedrain.app.STOP_VIBRATION";
    public static final String ACTION_UPDATE_VIBRATION =
            "com.vibedrain.app.UPDATE_VIBRATION";

    public static final String ACTION_START_TONE =
            "com.vibedrain.app.START_TONE";
    public static final String ACTION_STOP_TONE =
            "com.vibedrain.app.STOP_TONE";
    public static final String ACTION_UPDATE_TONE =
            "com.vibedrain.app.UPDATE_TONE";
    public static final String ACTION_STOP_ALL =
            "com.vibedrain.app.STOP_ALL";
    public static final String ACTION_START_RANDOM =
            "com.vibedrain.app.START_RANDOM";
    public static final String ACTION_STOP_RANDOM =
            "com.vibedrain.app.STOP_RANDOM";

    public static final String EXTRA_VIBRATION_STRENGTH =
            "vibration_strength";
    public static final String EXTRA_TONE_FREQUENCY =
            "tone_frequency";
    public static final String EXTRA_DRAIN_MODE =
            "drain_mode";

    public static final String STATE_PREFS =
            "vibe_drain_runtime_state";
    public static final String KEY_VIBRATION_RUNNING =
            "vibration_running";
    public static final String KEY_TONE_RUNNING =
            "tone_running";
    public static final String KEY_VIBRATION_STRENGTH =
            "vibration_strength";
    public static final String KEY_TONE_FREQUENCY =
            "tone_frequency";
    public static final String KEY_DRAIN_MODE =
            "drain_mode";
    public static final String KEY_RANDOM_RUNNING = "random_running";

    private static final String CHANNEL_ID =
            "vibe_drain_running";
    private static final int NOTIFICATION_ID = 7401;

    private Vibrator vibrator;
    private ToneEngine toneEngine;
    private PowerManager.WakeLock wakeLock;
    private SharedPreferences prefs;

    private boolean vibrationRunning;
    private boolean toneRunning;
    private int vibrationStrength = 100;
    private int toneFrequency = 165;
    private boolean drainMode = true;
    private boolean randomRunning;
    private boolean previousVibrationRunning;
    private boolean previousToneRunning;
    private boolean previousDrainMode;
    private int previousStrength;
    private int previousFrequency;
    private final Handler randomHandler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final Runnable randomStep = new Runnable() {
        @Override
        public void run() {
            if (!randomRunning) return;
            toneFrequency = 120 + random.nextInt(121);
            if (toneRunning) {
                // Update only the destination pitch. ToneEngine glides toward it
                // sample-by-sample without stopping or recreating AudioTrack.
                toneEngine.setFrequency(toneFrequency);
            }
            // Vibration follows a separate, precomputed smooth looping waveform.
            // Pitch destinations change irregularly; neither output is restarted.
            randomHandler.postDelayed(this, 350L + random.nextInt(501));
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        prefs = getSharedPreferences(
                STATE_PREFS,
                MODE_PRIVATE
        );

        vibrationStrength = prefs.getInt(
                KEY_VIBRATION_STRENGTH,
                100
        );
        toneFrequency = prefs.getInt(
                KEY_TONE_FREQUENCY,
                165
        );
        drainMode = prefs.getBoolean(
                KEY_DRAIN_MODE,
                true
        );

        vibrator = getVibrator();
        toneEngine = new ToneEngine(this);

        PowerManager powerManager =
                (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "VibeDrain:ActiveOutput"
            );
            wakeLock.setReferenceCounted(false);
        }

        createChannel();
    }

    @Override
    public int onStartCommand(
            Intent intent,
            int flags,
            int startId
    ) {
        if (intent == null || intent.getAction() == null) {
            restoreAfterProcessRestart();
            return isAnythingRunning()
                    ? START_STICKY
                    : START_NOT_STICKY;
        }

        String action = intent.getAction();

        if (ACTION_START_RANDOM.equals(action)) {
            startRandom();
        } else if (ACTION_STOP_RANDOM.equals(action)) {
            stopRandom(true);
        } else {
            if (randomRunning) stopRandom(true);
            handleNormalAction(action, intent);
        }

        saveState();
        updateWakeLock();

        if (isAnythingRunning()) {
            ensureForeground();
            updateNotification();
            return START_STICKY;
        }

        stopForeground(true);
        stopSelf();
        return START_NOT_STICKY;
    }

    private void handleNormalAction(String action, Intent intent) {
        if (ACTION_START_VIBRATION.equals(action)) {
            vibrationStrength = clamp(
                    intent.getIntExtra(
                            EXTRA_VIBRATION_STRENGTH,
                            vibrationStrength
                    ),
                    1,
                    100
            );
            ensureForeground();
            startVibration();
        } else if (ACTION_STOP_VIBRATION.equals(action)) {
            stopVibration();
        } else if (ACTION_UPDATE_VIBRATION.equals(action)) {
            vibrationStrength = clamp(
                    intent.getIntExtra(
                            EXTRA_VIBRATION_STRENGTH,
                            vibrationStrength
                    ),
                    1,
                    100
            );
            if (vibrationRunning) {
                startVibration();
            }
        } else if (ACTION_START_TONE.equals(action)) {
            drainMode = intent.getBooleanExtra(
                    EXTRA_DRAIN_MODE,
                    drainMode
            );
            toneFrequency = clamp(
                    intent.getIntExtra(
                            EXTRA_TONE_FREQUENCY,
                            toneFrequency
                    ),
                    120,
                    240
            );
            ensureForeground();
            startTone();
        } else if (ACTION_STOP_TONE.equals(action)) {
            stopTone();
        } else if (ACTION_UPDATE_TONE.equals(action)) {
            drainMode = intent.getBooleanExtra(
                    EXTRA_DRAIN_MODE,
                    drainMode
            );
            toneFrequency = clamp(
                    intent.getIntExtra(
                            EXTRA_TONE_FREQUENCY,
                            toneFrequency
                    ),
                    120,
                    240
            );
            toneEngine.setDrainMode(drainMode);
            toneEngine.setFrequency(toneFrequency);
        } else if (ACTION_STOP_ALL.equals(action)) {
            stopVibration();
            stopTone();
        }
    }

    private void startRandom() {
        if (randomRunning) return;
        previousVibrationRunning = vibrationRunning;
        previousToneRunning = toneRunning;
        previousStrength = vibrationStrength;
        previousFrequency = toneFrequency;
        previousDrainMode = drainMode;

        randomRunning = true;
        ensureForeground();
        vibrationRunning = true;
        toneRunning = true;
        drainMode = false;
        toneEngine.setSmoothMode(true);
        startSmoothRandomVibration();
        startToneInternal();
        randomHandler.removeCallbacks(randomStep);
        randomStep.run();
    }


    /**
     * A repeating amplitude envelope avoids cancel()/vibrate() on every random
     * update, which caused the physical motor to stop between changes.
     * Each segment smoothly approaches an independently chosen target.
     * The final segment returns to the first amplitude, so the loop has
     * no discontinuity at its repeat boundary.
     */
    private void startSmoothRandomVibration() {
        if (vibrator == null || !vibrator.hasVibrator()) {
            vibrationRunning = false;
            return;
        }
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        if (!vibrator.hasAmplitudeControl()) {
            // No proportional control available on this vibrator.
            // Use a steady, uninterrupted waveform rather than restarting.
            try {
                vibrator.cancel();
                vibrator.vibrate(VibrationEffect.createWaveform(
                        new long[]{0L, 1000L}, 1), attributes);
            } catch (Throwable ignored) {
                vibrationRunning = false;
            }
            return;
        }

        final int durationPerStep = 50;
        final ArrayList<Long> durations = new ArrayList<>();
        final ArrayList<Integer> amplitudes = new ArrayList<>();
        durations.add(0L);
        amplitudes.add(0);

        final int first = 60 + random.nextInt(145);
        int current = first;
        for (int segment = 0; segment <= 26; segment++) {
            int target = segment == 26 ? first : 20 + random.nextInt(235);
            int steps = 28 + random.nextInt(38);
            for (int step = 1; step <= steps; step++) {
                double t = (double) step / steps;
                double eased = t * t * (3.0 - 2.0 * t);
                int amplitude = (int) Math.round(current + (target - current) * eased);
                durations.add((long) durationPerStep);
                amplitudes.add(Math.max(1, Math.min(255, amplitude)));
            }
            current = target;
        }

        long[] timing = new long[durations.size()];
        int[] strength = new int[amplitudes.size()];
        for (int i = 0; i < timing.length; i++) {
            timing[i] = durations.get(i);
            strength[i] = amplitudes.get(i);
        }
        try {
            vibrator.cancel();
            // Repeat from index 1; index 0 is the initial zero-duration delay.
            vibrator.vibrate(VibrationEffect.createWaveform(timing, strength, 1),
                    attributes);
        } catch (Throwable ignored) {
            vibrationRunning = false;
        }
    }

    private void stopRandom(boolean restoreManualOutputs) {
        if (!randomRunning) return;
        randomRunning = false;
        randomHandler.removeCallbacks(randomStep);
        stopVibration();
        stopTone();
        toneEngine.setSmoothMode(false);

        vibrationStrength = previousStrength;
        toneFrequency = previousFrequency;
        drainMode = previousDrainMode;
        if (restoreManualOutputs) {
            if (previousVibrationRunning) startVibration();
            if (previousToneRunning) startTone();
        }
    }

    private void restoreAfterProcessRestart() {
        vibrationRunning = prefs.getBoolean(
                KEY_VIBRATION_RUNNING,
                false
        );
        toneRunning = prefs.getBoolean(
                KEY_TONE_RUNNING,
                false
        );
        vibrationStrength = prefs.getInt(
                KEY_VIBRATION_STRENGTH,
                100
        );
        toneFrequency = prefs.getInt(
                KEY_TONE_FREQUENCY,
                165
        );
        drainMode = prefs.getBoolean(
                KEY_DRAIN_MODE,
                true
        );

        if (prefs.getBoolean(KEY_RANDOM_RUNNING, false)) {
            // The persisted controls are the manual values from before random.
            startRandom();
            saveState();
            updateWakeLock();
            updateNotification();
            return;
        }

        if (!isAnythingRunning()) {
            stopSelf();
            return;
        }

        ensureForeground();

        if (vibrationRunning) {
            startVibrationInternal();
        }

        if (toneRunning) {
            startToneInternal();
        }

        updateWakeLock();
        updateNotification();
    }

    private void startVibration() {
        vibrationRunning = true;
        startVibrationInternal();
    }

    private void startVibrationInternal() {
        if (vibrator == null || !vibrator.hasVibrator()) {
            vibrationRunning = false;
            return;
        }

        int amplitude = Math.max(
                1,
                Math.min(
                        255,
                        Math.round(
                                255f
                                        * vibrationStrength
                                        / 100f
                        )
                )
        );

        AudioAttributes attributes =
                new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(
                                AudioAttributes.CONTENT_TYPE_SONIFICATION
                        )
                        .build();

        try {
            vibrator.cancel();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                int actualAmplitude =
                        vibrator.hasAmplitudeControl()
                                ? amplitude
                                : VibrationEffect.DEFAULT_AMPLITUDE;

                VibrationEffect effect =
                        VibrationEffect.createWaveform(
                                new long[]{0L, 1000L},
                                new int[]{0, actualAmplitude},
                                0
                        );
                vibrator.vibrate(effect, attributes);
            } else {
                vibrator.vibrate(
                        new long[]{0L, 1000L},
                        0,
                        attributes
                );
            }
        } catch (Throwable ignored) {
            vibrationRunning = false;
        }
    }

    private void stopVibration() {
        vibrationRunning = false;

        if (vibrator != null) {
            try {
                vibrator.cancel();
            } catch (Throwable ignored) {
            }
        }
    }

    private void startTone() {
        toneRunning = true;
        startToneInternal();
    }

    private void startToneInternal() {
        toneEngine.setDrainMode(drainMode);
        toneEngine.setFrequency(toneFrequency);

        if (!toneEngine.isRunning()) {
            boolean started = toneEngine.start();
            if (!started) {
                toneRunning = false;
            }
        }
    }

    private void stopTone() {
        toneRunning = false;
        toneEngine.stop();
    }

    private void ensureForeground() {
        createChannel();
        startForeground(
                NOTIFICATION_ID,
                buildNotification()
        );
    }

    private void updateNotification() {
        NotificationManager manager =
                (NotificationManager) getSystemService(
                        NOTIFICATION_SERVICE
                );
        if (manager != null) {
            manager.notify(
                    NOTIFICATION_ID,
                    buildNotification()
            );
        }
    }

    private Notification buildNotification() {
        Intent open = new Intent(
                this,
                MainActivity.class
        );
        open.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
        );

        PendingIntent openPending =
                PendingIntent.getActivity(
                        this,
                        7402,
                        open,
                        PendingIntent.FLAG_UPDATE_CURRENT
                                | PendingIntent.FLAG_IMMUTABLE
                );

        Intent stop = new Intent(
                this,
                DrainForegroundService.class
        );
        stop.setAction(ACTION_STOP_ALL);

        PendingIntent stopPending =
                PendingIntent.getService(
                        this,
                        7403,
                        stop,
                        PendingIntent.FLAG_UPDATE_CURRENT
                                | PendingIntent.FLAG_IMMUTABLE
                );

        Notification.Builder builder =
                Build.VERSION.SDK_INT
                        >= Build.VERSION_CODES.O
                        ? new Notification.Builder(
                                this,
                                CHANNEL_ID
                        )
                        : new Notification.Builder(this);

        builder.setSmallIcon(R.drawable.ic_water_alert)
                .setContentTitle("Vibe & Sound 실행 중")
                .setContentText(statusText())
                .setContentIntent(openPending)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setPriority(Notification.PRIORITY_LOW)
                .setColor(Color.WHITE)
                .addAction(
                        new Notification.Action.Builder(
                                null,
                                "모두 정지",
                                stopPending
                        ).build()
                );

        return builder.build();
    }

    private String statusText() {
        if (randomRunning) return "랜덤 · 진동 세기와 주파수 변경 중";

        if (vibrationRunning && toneRunning) {
            return drainMode
                    ? "진동 · 배수 실행 중"
                    : "진동 · 단음 "
                            + toneFrequency
                            + " Hz 실행 중";
        }

        if (vibrationRunning) {
            return "진동 실행 중";
        }

        if (toneRunning) {
            return drainMode
                    ? "배수 실행 중"
                    : "단음 "
                            + toneFrequency
                            + " Hz 실행 중";
        }

        return "실행 중";
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT
                < Build.VERSION_CODES.O) {
            return;
        }

        NotificationManager manager =
                (NotificationManager) getSystemService(
                        NOTIFICATION_SERVICE
                );
        if (manager == null) return;

        NotificationChannel channel =
                new NotificationChannel(
                        CHANNEL_ID,
                        "Vibe & Sound 실행 상태",
                        NotificationManager.IMPORTANCE_LOW
                );
        channel.setDescription(
                "화면이 꺼져도 진동과 배수를 계속 실행하기 위한 알림"
        );
        channel.setSound(null, null);
        channel.enableVibration(false);
        manager.createNotificationChannel(channel);
    }

    private void updateWakeLock() {
        if (wakeLock == null) return;

        if (isAnythingRunning()) {
            if (!wakeLock.isHeld()) {
                try {
                    wakeLock.acquire();
                } catch (Throwable ignored) {
                }
            }
        } else if (wakeLock.isHeld()) {
            try {
                wakeLock.release();
            } catch (Throwable ignored) {
            }
        }
    }

    private boolean isAnythingRunning() {
        return vibrationRunning || toneRunning;
    }

    private void saveState() {
        prefs.edit()
                .putBoolean(KEY_RANDOM_RUNNING, randomRunning)
                .putBoolean(KEY_VIBRATION_RUNNING,
                        randomRunning ? previousVibrationRunning : vibrationRunning)
                .putBoolean(KEY_TONE_RUNNING,
                        randomRunning ? previousToneRunning : toneRunning)
                .putInt(KEY_VIBRATION_STRENGTH,
                        randomRunning ? previousStrength : vibrationStrength)
                .putInt(KEY_TONE_FREQUENCY,
                        randomRunning ? previousFrequency : toneFrequency)
                .putBoolean(KEY_DRAIN_MODE,
                        randomRunning ? previousDrainMode : drainMode)
                .apply();
    }

    private Vibrator getVibrator() {
        try {
            if (Build.VERSION.SDK_INT
                    >= Build.VERSION_CODES.S) {
                VibratorManager manager =
                        (VibratorManager) getSystemService(
                                Context.VIBRATOR_MANAGER_SERVICE
                        );
                return manager == null
                        ? null
                        : manager.getDefaultVibrator();
            }

            return (Vibrator) getSystemService(
                    Context.VIBRATOR_SERVICE
            );
        } catch (Throwable ignored) {
            return null;
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(
                min,
                Math.min(max, value)
        );
    }

    @Override
    public void onDestroy() {
        stopRandom(false);
        stopVibration();
        stopTone();

        if (wakeLock != null && wakeLock.isHeld()) {
            try {
                wakeLock.release();
            } catch (Throwable ignored) {
            }
        }

        vibrationRunning = false;
        toneRunning = false;
        saveState();

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private static final class ToneEngine {
        private static final int SAMPLE_RATE = 44100;
        private static final double DRAIN_MIN_HZ = 150.0;
        private static final double DRAIN_MAX_HZ = 220.0;
        private static final double DRAIN_SWEEP_SECONDS = 2.4;

        private final AudioManager audioManager;

        private volatile double frequency = 165.0;
        private volatile boolean smoothMode = false;
        private volatile boolean drainMode = true;
        private volatile boolean running = false;

        private AudioTrack track;
        private Thread thread;

        ToneEngine(Context context) {
            Context appContext =
                    context.getApplicationContext();
            audioManager =
                    (AudioManager) appContext.getSystemService(
                            Context.AUDIO_SERVICE
                    );
        }

        void setFrequency(double frequency) {
            this.frequency = Math.max(
                    120.0,
                    Math.min(240.0, frequency)
            );
        }

        void setDrainMode(boolean drainMode) {
            this.drainMode = drainMode;
        }

        void setSmoothMode(boolean enabled) {
            this.smoothMode = enabled;
        }

        boolean isRunning() {
            return running;
        }

        synchronized boolean start() {
            if (running) return true;

            int min = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
            );
            if (min <= 0) return false;

            int bufferBytes = Math.max(min, 4096);

            AudioAttributes attributes =
                    new AudioAttributes.Builder()
                            .setUsage(
                                    AudioAttributes.USAGE_MEDIA
                            )
                            .setContentType(
                                    AudioAttributes.CONTENT_TYPE_MUSIC
                            )
                            .build();

            AudioFormat format =
                    new AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE)
                            .setEncoding(
                                    AudioFormat.ENCODING_PCM_16BIT
                            )
                            .setChannelMask(
                                    AudioFormat.CHANNEL_OUT_MONO
                            )
                            .build();

            try {
                track = new AudioTrack(
                        attributes,
                        format,
                        bufferBytes,
                        AudioTrack.MODE_STREAM,
                        AudioManager.AUDIO_SESSION_ID_GENERATE
                );
                routeToBuiltInSpeaker(track);
                track.play();
            } catch (Throwable e) {
                releaseTrack();
                return false;
            }

            running = true;
            thread = new Thread(
                    this::audioLoop,
                    "VibeDrain-BackgroundAudio"
            );
            thread.start();
            return true;
        }

        private void audioLoop() {
            short[] buffer = new short[1024];
            double phase = 0.0;
            double glidingFrequency = frequency;
            // ~0.10-second smoothing time constant; target changes can
            // overlap without instantaneous jumps in the sound frequency.
            final double pitchGlide = 1.0 / (SAMPLE_RATE * 0.10);
            long sampleIndex = 0L;

            final double sweepSamples =
                    SAMPLE_RATE * DRAIN_SWEEP_SECONDS;

            while (running) {
                boolean drain = drainMode;

                for (
                        int i = 0;
                        i < buffer.length && running;
                        i++
                ) {
                    double f;

                    if (drain) {
                        double cycle =
                                (sampleIndex
                                        % (long) (
                                                sweepSamples
                                                        * 2.0
                                        ))
                                        / sweepSamples;
                        double triangle =
                                cycle <= 1.0
                                        ? cycle
                                        : 2.0 - cycle;

                        f = DRAIN_MIN_HZ
                                + (
                                        DRAIN_MAX_HZ
                                                - DRAIN_MIN_HZ
                                )
                                * triangle;
                    } else if (smoothMode) {
                        glidingFrequency += (frequency - glidingFrequency) * pitchGlide;
                        f = glidingFrequency;
                    } else {
                        glidingFrequency = frequency;
                        f = frequency;
                    }

                    double step =
                            2.0
                                    * Math.PI
                                    * f
                                    / SAMPLE_RATE;

                    phase += step;
                    if (phase >= 2.0 * Math.PI) {
                        phase -= 2.0 * Math.PI;
                    }

                    double sample;
                    if (drain) {
                        double raw =
                                Math.sin(phase)
                                        + 0.30
                                        * Math.sin(
                                                phase * 3.0
                                        );
                        sample =
                                Math.tanh(raw * 1.55)
                                        / Math.tanh(1.55);
                    } else {
                        sample = Math.sin(phase);
                    }

                    sample = Math.max(
                            -1.0,
                            Math.min(1.0, sample)
                    );

                    buffer[i] =
                            (short) Math.round(
                                    sample * 32767.0
                            );
                    sampleIndex++;
                }

                AudioTrack current = track;
                if (current == null || !running) break;

                try {
                    int written =
                            current.write(
                                    buffer,
                                    0,
                                    buffer.length,
                                    AudioTrack.WRITE_BLOCKING
                            );
                    if (written < 0) break;
                } catch (Throwable e) {
                    break;
                }
            }
        }

        private void routeToBuiltInSpeaker(
                AudioTrack audioTrack
        ) {
            try {
                AudioDeviceInfo[] devices =
                        audioManager == null
                                ? new AudioDeviceInfo[0]
                                : audioManager.getDevices(
                                        AudioManager.GET_DEVICES_OUTPUTS
                                );

                for (AudioDeviceInfo device : devices) {
                    if (device.getType()
                            == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                        audioTrack.setPreferredDevice(device);
                        return;
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        synchronized void stop() {
            if (!running && track == null) return;

            running = false;

            AudioTrack current = track;
            if (current != null) {
                try {
                    current.pause();
                } catch (Throwable ignored) {
                }
                try {
                    current.flush();
                } catch (Throwable ignored) {
                }
                try {
                    current.stop();
                } catch (Throwable ignored) {
                }
            }

            Thread currentThread = thread;
            if (currentThread != null) {
                try {
                    currentThread.join(200L);
                } catch (InterruptedException ignored) {
                }
            }
            thread = null;

            releaseTrack();
        }

        private void releaseTrack() {
            AudioTrack current = track;
            track = null;

            if (current != null) {
                try {
                    current.release();
                } catch (Throwable ignored) {
                }
            }
        }
    }
}
