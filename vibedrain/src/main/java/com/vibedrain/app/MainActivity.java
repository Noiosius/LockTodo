package com.vibedrain.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int REQUEST_NOTIFICATIONS = 4201;
    private static final String SETUP_PREFS = "vibe_drain_setup";
    private static final String KEY_MOISTURE_SETUP_ASKED = "moisture_setup_asked_v1";

    private Button vibrationButton;
    private Button toneButton;
    private Button singleModeButton;
    private Button drainModeButton;

    private TextView vibrationValue;
    private TextView frequencyValue;
    private TextView toneHint;

    private LinearLayout frequencyRow;
    private SeekBar frequencySeek;

    private int vibrationStrength = 100;
    private int toneFrequency = 165;

    private boolean vibrationRunning = false;
    private boolean toneRunning = false;
    private boolean drainMode = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.setStatusBarColor(Color.rgb(12, 12, 12));
        window.setNavigationBarColor(Color.rgb(12, 12, 12));

        loadRuntimeState();
        setContentView(buildUi());
        maybeRequestMoistureIntegration();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadRuntimeState();
        refreshUiState();
    }

    private void loadRuntimeState() {
        SharedPreferences prefs = getSharedPreferences(
                DrainForegroundService.STATE_PREFS,
                MODE_PRIVATE
        );
        vibrationStrength = prefs.getInt(
                DrainForegroundService.KEY_VIBRATION_STRENGTH,
                vibrationStrength
        );
        toneFrequency = prefs.getInt(
                DrainForegroundService.KEY_TONE_FREQUENCY,
                toneFrequency
        );
        drainMode = prefs.getBoolean(
                DrainForegroundService.KEY_DRAIN_MODE,
                drainMode
        );
        vibrationRunning = prefs.getBoolean(
                DrainForegroundService.KEY_VIBRATION_RUNNING,
                false
        );
        toneRunning = prefs.getBoolean(
                DrainForegroundService.KEY_TONE_RUNNING,
                false
        );
    }

    private void persistControls() {
        getSharedPreferences(DrainForegroundService.STATE_PREFS, MODE_PRIVATE)
                .edit()
                .putInt(
                        DrainForegroundService.KEY_VIBRATION_STRENGTH,
                        vibrationStrength
                )
                .putInt(
                        DrainForegroundService.KEY_TONE_FREQUENCY,
                        toneFrequency
                )
                .putBoolean(
                        DrainForegroundService.KEY_DRAIN_MODE,
                        drainMode
                )
                .apply();
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
        note.setText(
                "실행 중에는 화면을 끄거나 다른 앱으로 이동해도 계속 작동합니다.\n"
                        + "소리 크기는 휴대폰 미디어 음량으로 조절하세요."
        );
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

        vibrationButton = actionButton(vibrationRunning ? "정지" : "시작");
        applyButtonStyle(vibrationButton, vibrationRunning);
        vibrationButton.setOnClickListener(v -> toggleVibration());

        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(50)
        );
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
        strength.setProgress(Math.max(0, Math.min(99, vibrationStrength - 1)));
        strength.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(
                    SeekBar seekBar,
                    int progress,
                    boolean fromUser
            ) {
                vibrationStrength = progress + 1;
                vibrationValue.setText(vibrationStrength + "%");
                persistControls();

                if (vibrationRunning) {
                    Intent intent = serviceIntent(
                            DrainForegroundService.ACTION_UPDATE_VIBRATION
                    );
                    intent.putExtra(
                            DrainForegroundService.EXTRA_VIBRATION_STRENGTH,
                            vibrationStrength
                    );
                    startService(intent);
                }
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
                0,
                dp(44),
                1f
        );
        left.rightMargin = dp(5);
        modeRow.addView(singleModeButton, left);

        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(
                0,
                dp(44),
                1f
        );
        right.leftMargin = dp(5);
        modeRow.addView(drainModeButton, right);

        LinearLayout.LayoutParams modeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        modeParams.topMargin = dp(12);
        card.addView(modeRow, modeParams);

        toneButton = actionButton(toneRunning ? "정지" : "시작");
        applyButtonStyle(toneButton, toneRunning);
        toneButton.setOnClickListener(v -> toggleTone());

        LinearLayout.LayoutParams startParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(50)
        );
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
        frequencySeek.setProgress(
                Math.max(0, Math.min(120, toneFrequency - 120))
        );
        frequencySeek.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(
                            SeekBar seekBar,
                            int progress,
                            boolean fromUser
                    ) {
                        toneFrequency = 120 + progress;
                        frequencyValue.setText(toneFrequency + " Hz");
                        persistControls();

                        if (toneRunning && !drainMode) {
                            sendToneUpdate();
                        }
                    }

                    @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                    @Override public void onStopTrackingTouch(SeekBar seekBar) {}
                }
        );
        card.addView(frequencySeek);

        toneHint = hint("");
        card.addView(toneHint);

        applySpeakerModeUi();
        return card;
    }

    private void setSpeakerMode(boolean useDrainMode) {
        drainMode = useDrainMode;
        persistControls();
        applySpeakerModeUi();

        if (toneRunning) {
            sendToneUpdate();
        }
    }

    private void applySpeakerModeUi() {
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
                toneHint.setText(
                        "150–220 Hz 자동 스윕 · 화면이 꺼져도 계속 재생"
                );
            } else {
                toneHint.setText(
                        "120–240 Hz 단음 · 화면이 꺼져도 계속 재생"
                );
            }
        }
    }

    private void refreshUiState() {
        if (vibrationValue != null) {
            vibrationValue.setText(vibrationStrength + "%");
        }
        if (frequencyValue != null) {
            frequencyValue.setText(toneFrequency + " Hz");
        }
        if (frequencySeek != null) {
            frequencySeek.setProgress(
                    Math.max(0, Math.min(120, toneFrequency - 120))
            );
        }

        if (vibrationButton != null) {
            vibrationButton.setText(vibrationRunning ? "정지" : "시작");
            applyButtonStyle(vibrationButton, vibrationRunning);
        }

        if (toneButton != null) {
            toneButton.setText(toneRunning ? "정지" : "시작");
            applyButtonStyle(toneButton, toneRunning);
        }

        applySpeakerModeUi();
    }

    private void toggleVibration() {
        if (vibrationRunning) {
            startService(
                    serviceIntent(
                            DrainForegroundService.ACTION_STOP_VIBRATION
                    )
            );
            vibrationRunning = false;
        } else {
            Intent intent = serviceIntent(
                    DrainForegroundService.ACTION_START_VIBRATION
            );
            intent.putExtra(
                    DrainForegroundService.EXTRA_VIBRATION_STRENGTH,
                    vibrationStrength
            );
            startOutputService(intent);
            vibrationRunning = true;
        }

        getSharedPreferences(
                DrainForegroundService.STATE_PREFS,
                MODE_PRIVATE
        ).edit().putBoolean(
                DrainForegroundService.KEY_VIBRATION_RUNNING,
                vibrationRunning
        ).apply();

        refreshUiState();
    }

    private void toggleTone() {
        if (toneRunning) {
            startService(
                    serviceIntent(
                            DrainForegroundService.ACTION_STOP_TONE
                    )
            );
            toneRunning = false;
        } else {
            Intent intent = serviceIntent(
                    DrainForegroundService.ACTION_START_TONE
            );
            intent.putExtra(
                    DrainForegroundService.EXTRA_DRAIN_MODE,
                    drainMode
            );
            intent.putExtra(
                    DrainForegroundService.EXTRA_TONE_FREQUENCY,
                    toneFrequency
            );
            startOutputService(intent);
            toneRunning = true;
        }

        getSharedPreferences(
                DrainForegroundService.STATE_PREFS,
                MODE_PRIVATE
        ).edit().putBoolean(
                DrainForegroundService.KEY_TONE_RUNNING,
                toneRunning
        ).apply();

        refreshUiState();
    }

    private void sendToneUpdate() {
        Intent intent = serviceIntent(
                DrainForegroundService.ACTION_UPDATE_TONE
        );
        intent.putExtra(
                DrainForegroundService.EXTRA_DRAIN_MODE,
                drainMode
        );
        intent.putExtra(
                DrainForegroundService.EXTRA_TONE_FREQUENCY,
                toneFrequency
        );
        startService(intent);
    }

    private Intent serviceIntent(String action) {
        Intent intent = new Intent(this, DrainForegroundService.class);
        intent.setAction(action);
        return intent;
    }

    private void startOutputService(Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
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
        view.setTypeface(
                Typeface.create("sans-serif-medium", Typeface.NORMAL)
        );
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
        bg.setColor(
                active
                        ? Color.rgb(235, 235, 235)
                        : Color.rgb(42, 42, 42)
        );
        bg.setCornerRadius(dp(16));
        button.setTextColor(active ? Color.BLACK : Color.WHITE);
        button.setBackground(bg);
    }

    private void applyModeButtonStyle(Button button, boolean selected) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(
                selected
                        ? Color.rgb(224, 224, 224)
                        : Color.rgb(34, 34, 34)
        );
        bg.setCornerRadius(dp(14));
        bg.setStroke(
                dp(1),
                selected
                        ? Color.rgb(224, 224, 224)
                        : Color.rgb(58, 58, 58)
        );
        button.setTextColor(
                selected ? Color.BLACK : Color.rgb(165, 165, 165)
        );
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
        row.addView(
                label,
                new LinearLayout.LayoutParams(0, dp(28), 1f)
        );

        TextView value = new TextView(this);
        value.setTextColor(Color.WHITE);
        value.setTextSize(12);
        value.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        row.addView(
                value,
                new LinearLayout.LayoutParams(dp(88), dp(28))
        );

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

    private void maybeRequestMoistureIntegration() {
        SharedPreferences prefs = getSharedPreferences(
                SETUP_PREFS,
                MODE_PRIVATE
        );
        if (prefs.getBoolean(KEY_MOISTURE_SETUP_ASKED, false)) return;

        prefs.edit().putBoolean(
                KEY_MOISTURE_SETUP_ASKED,
                true
        ).apply();

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
            startActivity(
                    new Intent(
                            Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
                    )
            );
        } catch (Throwable ignored) {
            try {
                startActivity(
                        new Intent(
                                "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"
                        )
                );
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
        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );
        if (requestCode == REQUEST_NOTIFICATIONS) {
            getWindow().getDecorView().postDelayed(
                    this::openNotificationListenerSettings,
                    250L
            );
        }
    }

    private int dp(int value) {
        return Math.round(
                value * getResources().getDisplayMetrics().density
        );
    }
}
