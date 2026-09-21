package com.vibedrain.app;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int REQUEST_NOTIFICATIONS = 4201;
    private static final String SETUP_PREFS = "vibe_drain_setup";
    private static final String KEY_MOISTURE_SETUP_ASKED = "moisture_setup_asked_v1";

    private Vibrator vibrator;
    private TonePlayer tonePlayer;

    private Button vibrationButton;
    private Button toneButton;
    private Button singleModeButton;
    private Button drainModeButton;

    private TextView vibrationValue;
    private TextView frequencyValue;
    private TextView volumeValue;
    private TextView toneHint;

    private LinearLayout frequencyRow;
    private SeekBar frequencySeek;

    private int vibrationStrength = 100;
    private int toneFrequency = 165;
    private int toneVolume = 100;

    private boolean vibrationRunning = false;
    private boolean toneRunning = false;
    private boolean drainMode = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.setStatusBarColor(Color.rgb(12, 12, 12));
        window.setNavigationBarColor(Color.rgb(12, 12, 12));
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        vibrator = getVibrator();
        tonePlayer = new TonePlayer(this);

        setContentView(buildUi());
        maybeRequestMoistureIntegration();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(12, 12, 12));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(22), dp(20), dp(24));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView title = new TextView(this);
        title.setText("Vibe Drain");
        title.setTextColor(Color.WHITE);
        title.setTextSize(23);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("진동 · 단음 · 배수");
        subtitle.setTextColor(Color.rgb(125, 125, 125));
        subtitle.setTextSize(12);
        subtitle.setPadding(0, dp(3), 0, dp(20));
        root.addView(subtitle);

        root.addView(buildVibrationCard());

        LinearLayout.LayoutParams speakerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        speakerParams.topMargin = dp(14);
        root.addView(buildSpeakerCard(), speakerParams);

        TextView note = new TextView(this);
        note.setText("배수 모드는 150–220 Hz를 자동으로 왕복하며 출력합니다.\n충전단자에 습기 경고가 있으면 완전히 마를 때까지 충전하지 마세요.");
        note.setTextColor(Color.rgb(105, 105, 105));
        note.setTextSize(11);
        note.setLineSpacing(0, 1.25f);
        note.setPadding(dp(4), dp(18), dp(4), 0);
        root.addView(note);

        return scroll;
    }

    private View buildVibrationCard() {
        LinearLayout card = card();

        card.addView(header("진동"));

        vibrationButton = actionButton("시작");
        vibrationButton.setOnClickListener(v -> toggleVibration());
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        buttonParams.topMargin = dp(12);
        card.addView(vibrationButton, buttonParams);

        LinearLayout labelRow = valueRow("세기");
        vibrationValue = (TextView) labelRow.getChildAt(1);
        vibrationValue.setText(vibrationStrength + "%");
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        labelParams.topMargin = dp(18);
        card.addView(labelRow, labelParams);

        SeekBar strength = new SeekBar(this);
        strength.setMax(99);
        strength.setProgress(vibrationStrength - 1);
        strength.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                vibrationStrength = progress + 1;
                vibrationValue.setText(vibrationStrength + "%");
                if (vibrationRunning) startVibration();
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        card.addView(strength);

        card.addView(hint("연속 진동 · 세기 1–100%"));

        return card;
    }

    private View buildSpeakerCard() {
        LinearLayout card = card();

        card.addView(header("스피커"));

        LinearLayout modeRow = new LinearLayout(this);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        modeRow.setGravity(Gravity.CENTER_VERTICAL);

        singleModeButton = modeButton("단음");
        drainModeButton = modeButton("배수");

        singleModeButton.setOnClickListener(v -> setSpeakerMode(false));
        drainModeButton.setOnClickListener(v -> setSpeakerMode(true));

        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(
                0, dp(44), 1f);
        left.rightMargin = dp(5);
        modeRow.addView(singleModeButton, left);

        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(
                0, dp(44), 1f);
        right.leftMargin = dp(5);
        modeRow.addView(drainModeButton, right);

        LinearLayout.LayoutParams modeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        modeParams.topMargin = dp(12);
        card.addView(modeRow, modeParams);

        toneButton = actionButton("시작");
        toneButton.setOnClickListener(v -> toggleTone());
        LinearLayout.LayoutParams startParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        startParams.topMargin = dp(10);
        card.addView(toneButton, startParams);

        frequencyRow = valueRow("주파수");
        frequencyValue = (TextView) frequencyRow.getChildAt(1);
        frequencyValue.setText(toneFrequency + " Hz");
        LinearLayout.LayoutParams freqRowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        freqRowParams.topMargin = dp(16);
        card.addView(frequencyRow, freqRowParams);

        frequencySeek = new SeekBar(this);
        frequencySeek.setMax(120);
        frequencySeek.setProgress(toneFrequency - 120);
        frequencySeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                toneFrequency = 120 + progress;
                frequencyValue.setText(toneFrequency + " Hz");
                tonePlayer.setFrequency(toneFrequency);
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        card.addView(frequencySeek);

        LinearLayout volumeRow = valueRow("출력");
        volumeValue = (TextView) volumeRow.getChildAt(1);
        volumeValue.setText(toneVolume + "%");
        LinearLayout.LayoutParams volumeRowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        volumeRowParams.topMargin = dp(10);
        card.addView(volumeRow, volumeRowParams);

        SeekBar volume = new SeekBar(this);
        volume.setMax(99);
        volume.setProgress(toneVolume - 1);
        volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                toneVolume = progress + 1;
                volumeValue.setText(toneVolume + "%");
                tonePlayer.setVolume(toneVolume / 100f);
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        card.addView(volume);

        toneHint = hint("");
        card.addView(toneHint);

        setSpeakerMode(true);
        return card;
    }

    private void setSpeakerMode(boolean useDrainMode) {
        if (toneRunning) {
            tonePlayer.stop();
            toneRunning = false;
            if (toneButton != null) {
                toneButton.setText("시작");
                applyButtonStyle(toneButton, false);
            }
        }

        drainMode = useDrainMode;
        tonePlayer.setDrainMode(drainMode);

        if (singleModeButton != null) {
            applyModeButtonStyle(singleModeButton, !drainMode);
        }
        if (drainModeButton != null) {
            applyModeButtonStyle(drainModeButton, drainMode);
        }

        int visibility = drainMode ? View.GONE : View.VISIBLE;
        if (frequencyRow != null) frequencyRow.setVisibility(visibility);
        if (frequencySeek != null) frequencySeek.setVisibility(visibility);

        if (toneHint != null) {
            if (drainMode) {
                toneHint.setText("150–220 Hz 자동 스윕 · 강화 파형");
            } else {
                toneHint.setText("120–240 Hz 단음 · 주파수 직접 조절");
            }
        }
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(15), dp(16), dp(15));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(21, 21, 21));
        bg.setCornerRadius(dp(24));
        bg.setStroke(dp(1), Color.rgb(45, 45, 45));
        card.setBackground(bg);
        return card;
    }

    private TextView header(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.WHITE);
        view.setTextSize(17);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return view;
    }

    private Button actionButton(String text) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextSize(15);
        applyButtonStyle(button, false);
        return button;
    }

    private Button modeButton(String text) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextSize(14);
        applyModeButtonStyle(button, false);
        return button;
    }

    private void applyButtonStyle(Button button, boolean active) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(active ? Color.rgb(235, 235, 235) : Color.rgb(42, 42, 42));
        bg.setCornerRadius(dp(16));
        button.setTextColor(active ? Color.BLACK : Color.WHITE);
        button.setBackground(bg);
    }

    private void applyModeButtonStyle(Button button, boolean selected) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(selected ? Color.rgb(224, 224, 224) : Color.rgb(34, 34, 34));
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), selected
                ? Color.rgb(224, 224, 224)
                : Color.rgb(58, 58, 58));
        button.setTextColor(selected ? Color.BLACK : Color.rgb(165, 165, 165));
        button.setBackground(bg);
    }

    private LinearLayout valueRow(String labelText) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView label = new TextView(this);
        label.setText(labelText);
        label.setTextColor(Color.rgb(165, 165, 165));
        label.setTextSize(12);
        row.addView(label, new LinearLayout.LayoutParams(0, dp(28), 1f));

        TextView value = new TextView(this);
        value.setTextColor(Color.WHITE);
        value.setTextSize(12);
        value.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        row.addView(value, new LinearLayout.LayoutParams(dp(88), dp(28)));

        return row;
    }

    private TextView hint(String text) {
        TextView hint = new TextView(this);
        hint.setText(text);
        hint.setTextColor(Color.rgb(105, 105, 105));
        hint.setTextSize(11);
        hint.setPadding(0, dp(6), 0, 0);
        return hint;
    }

    private void toggleVibration() {
        vibrationRunning = !vibrationRunning;
        if (vibrationRunning) {
            startVibration();
            vibrationButton.setText("정지");
        } else {
            stopVibration();
            vibrationButton.setText("시작");
        }
        applyButtonStyle(vibrationButton, vibrationRunning);
    }

    private void startVibration() {
        if (vibrator == null || !vibrator.hasVibrator()) return;

        int amplitude = Math.max(1, Math.min(255,
                Math.round(255f * vibrationStrength / 100f)));

        AudioAttributes vibrationAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        try {
            vibrator.cancel();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                int actualAmplitude = vibrator.hasAmplitudeControl()
                        ? amplitude
                        : VibrationEffect.DEFAULT_AMPLITUDE;

                VibrationEffect effect = VibrationEffect.createWaveform(
                        new long[]{0L, 1000L},
                        new int[]{0, actualAmplitude},
                        0
                );
                vibrator.vibrate(effect, vibrationAttributes);
            } else {
                vibrator.vibrate(new long[]{0L, 1000L}, 0, vibrationAttributes);
            }
        } catch (Throwable ignored) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(
                            VibrationEffect.createOneShot(
                                    1200L,
                                    vibrator.hasAmplitudeControl()
                                            ? amplitude
                                            : VibrationEffect.DEFAULT_AMPLITUDE
                            ),
                            vibrationAttributes
                    );
                } else {
                    vibrator.vibrate(1200L);
                }
            } catch (Throwable ignoredAgain) {
            }
        }
    }

    private void stopVibration() {
        if (vibrator != null) {
            try {
                vibrator.cancel();
            } catch (Throwable ignored) {
            }
        }
    }

    private void toggleTone() {
        toneRunning = !toneRunning;
        if (toneRunning) {
            tonePlayer.setDrainMode(drainMode);
            tonePlayer.setFrequency(toneFrequency);
            tonePlayer.setVolume(toneVolume / 100f);
            tonePlayer.start();
            toneButton.setText("정지");
        } else {
            tonePlayer.stop();
            toneButton.setText("시작");
        }
        applyButtonStyle(toneButton, toneRunning);
    }

    private Vibrator getVibrator() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager manager =
                        (VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                return manager == null ? null : manager.getDefaultVibrator();
            }
            return (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        } catch (Throwable e) {
            return null;
        }
    }

    private void maybeRequestMoistureIntegration() {
        SharedPreferences prefs = getSharedPreferences(SETUP_PREFS, MODE_PRIVATE);
        if (prefs.getBoolean(KEY_MOISTURE_SETUP_ASKED, false)) return;

        prefs.edit().putBoolean(KEY_MOISTURE_SETUP_ASKED, true).apply();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_NOTIFICATIONS
            );
        } else {
            getWindow().getDecorView().postDelayed(
                    this::openNotificationListenerSettings,
                    350L
            );
        }
    }

    private void openNotificationListenerSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        } catch (Throwable ignored) {
            try {
                startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
            } catch (Throwable ignoredAgain) {
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_NOTIFICATIONS) {
            getWindow().getDecorView().postDelayed(
                    this::openNotificationListenerSettings,
                    250L
            );
        }
    }

    @Override
    protected void onStop() {
        stopVibration();
        vibrationRunning = false;
        if (vibrationButton != null) {
            vibrationButton.setText("시작");
            applyButtonStyle(vibrationButton, false);
        }

        if (tonePlayer != null) tonePlayer.stop();
        toneRunning = false;
        if (toneButton != null) {
            toneButton.setText("시작");
            applyButtonStyle(toneButton, false);
        }

        super.onStop();
    }

    @Override
    protected void onDestroy() {
        stopVibration();
        if (tonePlayer != null) tonePlayer.release();
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class TonePlayer {
        private static final int SAMPLE_RATE = 44100;
        private static final double DRAIN_MIN_HZ = 150.0;
        private static final double DRAIN_MAX_HZ = 220.0;
        private static final double DRAIN_SWEEP_SECONDS = 2.4;

        private final Context context;
        private final AudioManager audioManager;

        private volatile double frequency = 165.0;
        private volatile float volume = 1.0f;
        private volatile boolean drainMode = true;
        private volatile boolean running = false;

        private AudioTrack track;
        private Thread thread;
        private int previousMusicVolume = -1;
        private boolean mediaVolumeForced = false;

        TonePlayer(Context context) {
            this.context = context.getApplicationContext();
            this.audioManager =
                    (AudioManager) this.context.getSystemService(Context.AUDIO_SERVICE);
        }

        void setFrequency(double frequency) {
            this.frequency = Math.max(120.0, Math.min(240.0, frequency));
        }

        void setVolume(float volume) {
            this.volume = Math.max(0.01f, Math.min(1f, volume));
        }

        void setDrainMode(boolean drainMode) {
            this.drainMode = drainMode;
        }

        synchronized void start() {
            if (running) return;
            running = true;
            forceMaximumMediaVolume();

            int min = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
            );
            int bufferBytes = Math.max(min, 4096);

            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();

            AudioFormat format = new AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build();

            track = new AudioTrack(
                    attributes,
                    format,
                    bufferBytes,
                    AudioTrack.MODE_STREAM,
                    AudioManager.AUDIO_SESSION_ID_GENERATE
            );

            routeToBuiltInSpeaker(track);

            try {
                track.play();
            } catch (Throwable e) {
                running = false;
                releaseTrack();
                restoreMediaVolume();
                return;
            }

            thread = new Thread(this::audioLoop, "VibeDrain-Audio");
            thread.start();
        }

        private void audioLoop() {
            short[] buffer = new short[1024];
            double phase = 0.0;
            long sampleIndex = 0L;
            final double sweepSamples = SAMPLE_RATE * DRAIN_SWEEP_SECONDS;

            while (running) {
                boolean drain = drainMode;
                float gain = volume;

                for (int i = 0; i < buffer.length; i++) {
                    double f;

                    if (drain) {
                        double cycle = (sampleIndex % (long) (sweepSamples * 2.0))
                                / sweepSamples;
                        double triangle = cycle <= 1.0 ? cycle : 2.0 - cycle;
                        f = DRAIN_MIN_HZ
                                + (DRAIN_MAX_HZ - DRAIN_MIN_HZ) * triangle;
                    } else {
                        f = frequency;
                    }

                    double step = 2.0 * Math.PI * f / SAMPLE_RATE;
                    phase += step;
                    if (phase >= 2.0 * Math.PI) phase -= 2.0 * Math.PI;

                    double sample;
                    if (drain) {
                        double raw = Math.sin(phase)
                                + 0.30 * Math.sin(phase * 3.0);
                        double clipped = Math.tanh(raw * 1.55) / Math.tanh(1.55);
                        sample = clipped;
                    } else {
                        sample = Math.sin(phase);
                    }

                    sample *= gain;
                    sample = Math.max(-1.0, Math.min(1.0, sample));
                    buffer[i] = (short) Math.round(sample * 32767.0);
                    sampleIndex++;
                }

                AudioTrack t = track;
                if (t == null) break;

                try {
                    int written = t.write(
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

        private void routeToBuiltInSpeaker(AudioTrack audioTrack) {
            try {
                AudioDeviceInfo[] devices =
                        audioManager == null
                                ? new AudioDeviceInfo[0]
                                : audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);

                for (AudioDeviceInfo device : devices) {
                    if (device.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
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

            AudioTrack t = track;
            if (t != null) {
                try { t.pause(); } catch (Throwable ignored) {}
                try { t.flush(); } catch (Throwable ignored) {}
                try { t.stop(); } catch (Throwable ignored) {}
            }

            Thread current = thread;
            if (current != null) {
                try {
                    current.join(150L);
                } catch (InterruptedException ignored) {
                }
            }
            thread = null;

            releaseTrack();
            restoreMediaVolume();
        }

        synchronized void release() {
            stop();
            restoreMediaVolume();
        }

        private void forceMaximumMediaVolume() {
            if (audioManager == null || mediaVolumeForced) return;

            try {
                previousMusicVolume =
                        audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                int max =
                        audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, max, 0);
                mediaVolumeForced = true;
            } catch (Throwable ignored) {
                previousMusicVolume = -1;
                mediaVolumeForced = false;
            }
        }

        private void restoreMediaVolume() {
            if (audioManager == null || !mediaVolumeForced) return;

            try {
                if (previousMusicVolume >= 0) {
                    audioManager.setStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            previousMusicVolume,
                            0
                    );
                }
            } catch (Throwable ignored) {
            } finally {
                previousMusicVolume = -1;
                mediaVolumeForced = false;
            }
        }

        private void releaseTrack() {
            AudioTrack t = track;
            track = null;

            if (t != null) {
                try {
                    t.release();
                } catch (Throwable ignored) {
                }
            }
        }
    }
}
