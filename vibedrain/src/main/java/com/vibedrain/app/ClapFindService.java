package com.vibedrain.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Foreground microphone listener. No audio is retained, transmitted or saved.
 * Requires manual activation from a visible Activity on Android 14+.
 */
public class ClapFindService extends Service {
    static final String ACTION_START = "com.vibedrain.app.CLAP_START";
    static final String ACTION_STOP = "com.vibedrain.app.CLAP_STOP";
    static final String ACTION_STOP_ALERT = "com.vibedrain.app.CLAP_STOP_ALERT";
    static final String PREFS = "clap_find_state";
    static final String KEY_ENABLED = "enabled";

    private static final String CHANNEL = "vibe_sound_clap_listening_v1";
    private static final int NOTIFICATION_ID = 7418;
    private static final int SAMPLE_RATE = 16000;
    private static final long ALERT_MS = 30000L;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Long> hitTimes = new ArrayList<>();
    private volatile boolean recordingRunning;
    private AudioRecord recorder;
    private Thread recordingThread;
    private PowerManager.WakeLock wakeLock;
    private Vibrator vibrator;
    private AudioManager audioManager;
    private ToneGenerator tone;
    private boolean alertActive;
    private boolean soundActive;
    private int oldMediaVolume = -1;
    private boolean mediaVolumeRaised;
    private long lastHit = -10000L;
    private long lastAlert = -60000L;

    private final Runnable timeout = this::stopAlert;
    private final Runnable beep = new Runnable() {
        @Override public void run() {
            if (!alertActive || !soundActive) return;
            if (tone != null) {
                try { tone.startTone(ToneGenerator.TONE_PROP_BEEP, 420); }
                catch (Throwable ignored) {}
            }
            main.postDelayed(this, 900L);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (Build.VERSION.SDK_INT >= 31) {
            VibratorManager manager = (VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            vibrator = manager == null ? null : manager.getDefaultVibrator();
        } else {
            vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        }
        PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
        if (power != null) {
            wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VibeSound:ClapDetection");
            wakeLock.setReferenceCounted(false);
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP_ALERT.equals(action)) {
            stopAlert();
            return recordingRunning ? START_STICKY : START_NOT_STICKY;
        }
        if (ACTION_STOP.equals(action)) {
            stopAlert();
            stopListening();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        // No background restart: Android 14+ prohibits starting a microphone
        // foreground service from a boot receiver or background process.
        if (!ACTION_START.equals(action)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (recordingRunning) return START_STICKY;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            stopSelf();
            return START_NOT_STICKY;
        }

        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            } else {
                startForeground(NOTIFICATION_ID, notification());
            }
        } catch (Throwable error) {
            stopSelf();
            return START_NOT_STICKY;
        }
        startListening();
        return START_STICKY;
    }

    private void startListening() {
        int min = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        if (min <= 0) { stopOnRecorderFailure(); return; }
        try {
            recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    Math.max(min * 2, SAMPLE_RATE / 2));
            if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
                stopOnRecorderFailure();
                return;
            }
            recorder.startRecording();
            recordingRunning = true;
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, true).apply();
            if (wakeLock != null && !wakeLock.isHeld()) wakeLock.acquire();
            final AudioRecord local = recorder;
            recordingThread = new Thread(() -> listenLoop(local), "VibeSound-ClapMonitor");
            recordingThread.start();
        } catch (Throwable e) {
            stopOnRecorderFailure();
        }
    }

    private void listenLoop(AudioRecord local) {
        short[] frames = new short[256];
        double baseline = 220.0;
        double lastSample = 0;
        int quietFrames = 0;
        boolean armed = true;
        try {
            while (recordingRunning) {
                int count = local.read(frames, 0, frames.length);
                if (count <= 0) break;
                double sum = 0;
                double transientSum = 0;
                for (int i = 0; i < count; i++) {
                    double sample = frames[i];
                    sum += sample * sample;
                    transientSum += Math.abs(sample - lastSample);
                    lastSample = sample;
                }
                double rms = Math.sqrt(sum / count);
                double sharpness = transientSum / count;
                long now = SystemClock.elapsedRealtime();

                // A short broadband transient above the ambient noise level.
                boolean loudClap = rms > Math.max(1050.0, baseline * 3.4)
                        && sharpness > Math.max(430.0, baseline * 1.10);
                if (!alertActive && loudClap && armed && now - lastHit > 145L) {
                    lastHit = now;
                    armed = false;
                    quietFrames = 0;
                    main.post(() -> onImpulse(now));
                } else if (rms < Math.max(760.0, baseline * 1.6)) {
                    if (++quietFrames >= 3) armed = true;
                } else {
                    quietFrames = 0;
                }

                if (!loudClap) baseline = baseline * 0.995 + Math.min(rms, 3000.0) * 0.005;
                baseline = Math.max(95.0, baseline);
            }
        } catch (Throwable ignored) {
        } finally {
            try { local.stop(); } catch (Throwable ignored) {}
            try { local.release(); } catch (Throwable ignored) {}
            if (recorder == local) recorder = null;
            if (recordingRunning) main.post(this::stopOnRecorderFailure);
        }
    }

    private void onImpulse(long when) {
        if (!recordingRunning || alertActive || when - lastAlert < 3500L) return;
        if (!hitTimes.isEmpty() && when - hitTimes.get(hitTimes.size() - 1) > 1500L) {
            hitTimes.clear();
        }
        hitTimes.add(when);
        if (hitTimes.size() > 4) hitTimes.remove(0);
        main.removeCallbacks(evaluatePattern);
        main.postDelayed(evaluatePattern, 650L);
    }

    private final Runnable evaluatePattern = () -> {
        int n = hitTimes.size();
        if (n == 3 || n == 4) {
            long gap = hitTimes.get(1) - hitTimes.get(0);
            boolean valid = gap >= 400L && gap <= 1400L;
            for (int i = 2; i < n; i++) {
                long fastGap = hitTimes.get(i) - hitTimes.get(i - 1);
                if (fastGap < 125L || fastGap > 395L) valid = false;
            }
            if (valid) {
                hitTimes.clear();
                startAlert(n == 4);
                return;
            }
        }
        hitTimes.clear();
    };

    private void startAlert(boolean withSound) {
        if (alertActive) return;
        alertActive = true;
        soundActive = withSound;
        lastAlert = SystemClock.elapsedRealtime();
        try {
            if (vibrator != null && vibrator.hasVibrator()) {
                AudioAttributes attributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
                vibrator.vibrate(VibrationEffect.createWaveform(
                        new long[]{0, 650, 260, 650, 260}, 1), attributes);
            }
        } catch (Throwable ignored) {}
        if (withSound) {
            try {
                oldMediaVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, max, 0);
                mediaVolumeRaised = true;
            } catch (Throwable ignored) { mediaVolumeRaised = false; }
            try { tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 100); }
            catch (Throwable ignored) { tone = null; }
            beep.run();
        }
        main.postDelayed(timeout, ALERT_MS);
        updateNotification();
    }

    private void stopAlert() {
        main.removeCallbacks(timeout);
        main.removeCallbacks(beep);
        if (!alertActive) return;
        alertActive = false;
        soundActive = false;
        if (vibrator != null) {
            try { vibrator.cancel(); } catch (Throwable ignored) {}
        }
        if (tone != null) {
            try { tone.stopTone(); tone.release(); } catch (Throwable ignored) {}
            tone = null;
        }
        if (mediaVolumeRaised && oldMediaVolume >= 0 && audioManager != null) {
            try {
                if (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        == audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, oldMediaVolume, 0);
                }
            } catch (Throwable ignored) {}
        }
        oldMediaVolume = -1;
        mediaVolumeRaised = false;
        if (recordingRunning) updateNotification();
    }

    private void stopListening() {
        recordingRunning = false;
        main.removeCallbacks(evaluatePattern);
        hitTimes.clear();
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, false).apply();
        AudioRecord local = recorder;
        if (local != null) {
            try { local.stop(); } catch (Throwable ignored) {}
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            try { wakeLock.release(); } catch (Throwable ignored) {}
        }
    }

    private void stopOnRecorderFailure() {
        stopAlert();
        stopListening();
        try { stopForeground(true); } catch (Throwable ignored) {}
        stopSelf();
    }

    private void updateNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(NOTIFICATION_ID, notification());
    }

    private Notification notification() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent openPending = PendingIntent.getActivity(this, 7419, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stop = new Intent(this, ClapFindService.class).setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(this, 7420, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_vibe_drain)
                .setContentTitle(alertActive ? "휴대폰 찾기 알림" : "Vibe & Sound · 박수 감지 중")
                .setContentText(alertActive ? (soundActive ? "진동 + 소리 재생 중" : "진동 중")
                        : "짝 · 짝짝: 진동 / 짝 · 짝짝짝: 진동 + 소리")
                .setContentIntent(openPending)
                .setOngoing(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .addAction(new Notification.Action.Builder(null, "감지 끄기", stopPending).build());
        if (alertActive) {
            Intent dismiss = new Intent(this, ClapFindService.class).setAction(ACTION_STOP_ALERT);
            PendingIntent dismissPending = PendingIntent.getService(this, 7421, dismiss,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            builder.addAction(new Notification.Action.Builder(null, "알림 중지", dismissPending).build());
        }
        return builder.build();
    }

    private void createChannel() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL, "박수 감지",
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("화면이 꺼져도 박수를 감지하기 위해 마이크 사용");
        channel.setSound(null, null);
        manager.createNotificationChannel(channel);
    }

    @Override public void onDestroy() {
        stopAlert();
        stopListening();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
