package com.locktodo.app;

import android.Manifest;
import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class QuickTodoActivity extends Activity {
    public static final String EXTRA_EDIT_INDEX = "edit_index";
    private static final int REQUEST_NOTIFICATIONS = 4701;

    private EditText input;
    private TextView reminderButton;
    private int editIndex = -1;
    private long selectedReminderAt = 0L;
    private long lastOutsideTap = 0L;
    private boolean openPickerAfterPermission = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setShowWhenLocked(true);
        setFinishOnTouchOutside(false);

        Window window = getWindow();
        window.setBackgroundDrawableResource(android.R.color.transparent);
        window.getDecorView().setBackgroundColor(Color.TRANSPARENT);
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND | WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        window.addFlags(WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH);
        window.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE |
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        );

        SharedPreferences prefs = AppPrefs.get(this);
        boolean light = prefs.getBoolean(AppPrefs.KEY_LIGHT_TEXT, true);
        int fg = light ? Color.WHITE : Color.BLACK;
        int muted = light ? Color.argb(150, 255, 255, 255) : Color.argb(125, 0, 0, 0);
        int panel = light ? Color.argb(224, 25, 25, 27) : Color.argb(235, 247, 247, 247);

        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(dp(6), dp(4), dp(6), dp(4));
        outer.setBackgroundColor(Color.TRANSPARENT);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(2), dp(6), dp(2));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(panel);
        bg.setCornerRadius(dp(17));
        bg.setStroke(dp(1), light ? Color.argb(72, 255, 255, 255) : Color.argb(58, 0, 0, 0));
        row.setBackground(bg);

        reminderButton = new TextView(this);
        reminderButton.setText("◷");
        reminderButton.setTextSize(21);
        reminderButton.setTextColor(muted);
        reminderButton.setGravity(Gravity.CENTER);
        row.addView(reminderButton, new LinearLayout.LayoutParams(dp(40), dp(46)));

        input = new EditText(this);
        input.setSingleLine(true);
        input.setTextColor(fg);
        input.setHintTextColor(light ? Color.argb(125, 255, 255, 255) : Color.argb(110, 0, 0, 0));
        input.setHint("할 일 입력…");
        input.setTextSize(17);
        input.setBackgroundColor(Color.TRANSPARENT);
        input.setPadding(dp(2), 0, dp(4), 0);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        row.addView(input, new LinearLayout.LayoutParams(0, dp(46), 1f));

        TextView done = new TextView(this);
        done.setText("✓");
        done.setTextSize(20);
        done.setTextColor(fg);
        done.setGravity(Gravity.CENTER);
        row.addView(done, new LinearLayout.LayoutParams(dp(40), dp(46)));

        outer.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        setContentView(outer);

        DisplayMetrics dm = getResources().getDisplayMetrics();
        WindowManager.LayoutParams attrs = window.getAttributes();
        attrs.width = Math.min(dm.widthPixels - dp(48), dp(310));
        attrs.height = WindowManager.LayoutParams.WRAP_CONTENT;
        attrs.gravity = Gravity.CENTER;
        attrs.dimAmount = 0f;
        window.setAttributes(attrs);

        editIndex = getIntent().getIntExtra(EXTRA_EDIT_INDEX, -1);
        if (editIndex >= 0) {
            TodoStore store = new TodoStore(this);
            List<String> items = store.load();
            if (editIndex < items.size()) {
                input.setText(items.get(editIndex));
                input.setSelection(input.length());
                selectedReminderAt = store.getReminderAt(editIndex);
            } else {
                editIndex = -1;
            }
        }
        updateReminderButton();

        reminderButton.setOnClickListener(v -> openReminderPickerWithPermissionCheck());
        done.setOnClickListener(v -> save());
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                save();
                return true;
            }
            return false;
        });

        input.requestFocus();
        input.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        }, 80);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getActionMasked() == MotionEvent.ACTION_OUTSIDE) {
            long now = SystemClock.elapsedRealtime();
            if (now - lastOutsideTap <= 420L) {
                finish();
            } else {
                lastOutsideTap = now;
            }
            return true;
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_NOTIFICATIONS && openPickerAfterPermission) {
            openPickerAfterPermission = false;
            showReminderPicker();
        }
    }

    private void openReminderPickerWithPermissionCheck() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            openPickerAfterPermission = true;
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
            return;
        }
        showReminderPicker();
    }

    private void showReminderPicker() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);

        final Calendar chosen = Calendar.getInstance();
        if (selectedReminderAt > 0L) {
            chosen.setTimeInMillis(selectedReminderAt);
        } else {
            chosen.add(Calendar.MINUTE, 2);
        }

        SharedPreferences prefs = AppPrefs.get(this);
        boolean light = prefs.getBoolean(AppPrefs.KEY_LIGHT_TEXT, true);
        int fg = light ? Color.WHITE : Color.BLACK;
        int muted = light ? Color.argb(155, 255, 255, 255) : Color.argb(125, 0, 0, 0);
        int panel = light ? Color.argb(238, 27, 27, 29) : Color.argb(242, 248, 248, 248);

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(10), dp(14), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(panel);
        bg.setCornerRadius(dp(20));
        bg.setStroke(dp(1), light ? Color.argb(72, 255, 255, 255) : Color.argb(58, 0, 0, 0));
        card.setBackground(bg);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView dateLabel = new TextView(this);
        dateLabel.setTextSize(15);
        dateLabel.setTextColor(fg);
        dateLabel.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(dateLabel, new LinearLayout.LayoutParams(0, dp(42), 1f));

        TextView dateSettings = new TextView(this);
        dateSettings.setText("설정");
        dateSettings.setTextSize(14);
        dateSettings.setTextColor(muted);
        dateSettings.setGravity(Gravity.CENTER);
        top.addView(dateSettings, new LinearLayout.LayoutParams(dp(54), dp(42)));
        card.addView(top);

        LinearLayout wheels = new LinearLayout(this);
        wheels.setOrientation(LinearLayout.HORIZONTAL);
        wheels.setGravity(Gravity.CENTER);

        NumberPicker amPm = picker(light, 0, 1);
        amPm.setDisplayedValues(new String[]{"오전", "오후"});
        NumberPicker hour = picker(light, 1, 12);
        NumberPicker minute = picker(light, 0, 59);
        String[] minuteValues = new String[60];
        for (int i = 0; i < 60; i++) minuteValues[i] = String.format(Locale.KOREA, "%02d", i);
        minute.setDisplayedValues(minuteValues);

        int hour24 = chosen.get(Calendar.HOUR_OF_DAY);
        amPm.setValue(hour24 >= 12 ? 1 : 0);
        int hour12 = hour24 % 12;
        if (hour12 == 0) hour12 = 12;
        hour.setValue(hour12);
        minute.setValue(chosen.get(Calendar.MINUTE));

        wheels.addView(amPm, new LinearLayout.LayoutParams(0, dp(132), 1f));
        wheels.addView(hour, new LinearLayout.LayoutParams(0, dp(132), 1f));
        wheels.addView(minute, new LinearLayout.LayoutParams(0, dp(132), 1f));
        card.addView(wheels);

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);

        TextView clear = new TextView(this);
        clear.setText(selectedReminderAt > 0L ? "해제" : "");
        clear.setTextSize(13);
        clear.setTextColor(muted);
        clear.setGravity(Gravity.CENTER_VERTICAL);
        bottom.addView(clear, new LinearLayout.LayoutParams(0, dp(40), 1f));

        TextView apply = new TextView(this);
        apply.setText("✓");
        apply.setTextSize(19);
        apply.setTextColor(fg);
        apply.setGravity(Gravity.CENTER);
        bottom.addView(apply, new LinearLayout.LayoutParams(dp(48), dp(40)));
        card.addView(bottom);

        Runnable updateDateLabel = () -> dateLabel.setText(formatDateLabel(chosen));
        updateDateLabel.run();

        dateSettings.setOnClickListener(v -> {
            Calendar today = Calendar.getInstance();
            DatePickerDialog datePicker = new DatePickerDialog(
                    this,
                    (view, year, month, dayOfMonth) -> {
                        chosen.set(Calendar.YEAR, year);
                        chosen.set(Calendar.MONTH, month);
                        chosen.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                        updateDateLabel.run();
                    },
                    chosen.get(Calendar.YEAR),
                    chosen.get(Calendar.MONTH),
                    chosen.get(Calendar.DAY_OF_MONTH)
            );
            datePicker.getDatePicker().setMinDate(startOfDay(today).getTimeInMillis());
            datePicker.show();
        });

        clear.setOnClickListener(v -> {
            if (selectedReminderAt > 0L) {
                selectedReminderAt = 0L;
                updateReminderButton();
                dialog.dismiss();
            }
        });

        apply.setOnClickListener(v -> {
            int selectedHour = hour.getValue() % 12;
            if (amPm.getValue() == 1) selectedHour += 12;
            chosen.set(Calendar.HOUR_OF_DAY, selectedHour);
            chosen.set(Calendar.MINUTE, minute.getValue());
            chosen.set(Calendar.SECOND, 0);
            chosen.set(Calendar.MILLISECOND, 0);
            if (chosen.getTimeInMillis() <= System.currentTimeMillis()) {
                Toast.makeText(this, "지난 시간입니다.", Toast.LENGTH_SHORT).show();
                return;
            }
            selectedReminderAt = chosen.getTimeInMillis();
            updateReminderButton();
            dialog.dismiss();
        });

        dialog.setContentView(card);
        Window dialogWindow = dialog.getWindow();
        if (dialogWindow != null) {
            dialogWindow.setBackgroundDrawableResource(android.R.color.transparent);
            dialogWindow.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            DisplayMetrics dm = getResources().getDisplayMetrics();
            WindowManager.LayoutParams p = dialogWindow.getAttributes();
            p.width = Math.min(dm.widthPixels - dp(48), dp(310));
            p.height = WindowManager.LayoutParams.WRAP_CONTENT;
            p.gravity = Gravity.CENTER;
            dialogWindow.setAttributes(p);
        }
        dialog.show();
    }

    private NumberPicker picker(boolean light, int min, int max) {
        NumberPicker picker = new NumberPicker(this);
        picker.setMinValue(min);
        picker.setMaxValue(max);
        picker.setWrapSelectorWheel(true);
        picker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        picker.setBackgroundColor(Color.TRANSPARENT);
        picker.setAlpha(light ? 0.92f : 0.86f);
        return picker;
    }

    private String formatDateLabel(Calendar selected) {
        Calendar today = Calendar.getInstance();
        if (sameDay(selected, today)) return "오늘";
        SimpleDateFormat formatter = new SimpleDateFormat("M월 d일", Locale.KOREA);
        return formatter.format(selected.getTime());
    }

    private boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    private Calendar startOfDay(Calendar source) {
        Calendar result = (Calendar) source.clone();
        result.set(Calendar.HOUR_OF_DAY, 0);
        result.set(Calendar.MINUTE, 0);
        result.set(Calendar.SECOND, 0);
        result.set(Calendar.MILLISECOND, 0);
        return result;
    }

    private void updateReminderButton() {
        if (reminderButton == null) return;
        boolean light = AppPrefs.get(this).getBoolean(AppPrefs.KEY_LIGHT_TEXT, true);
        int alpha = selectedReminderAt > 0L ? 235 : 145;
        reminderButton.setTextColor(light
                ? Color.argb(alpha, 255, 255, 255)
                : Color.argb(alpha, 0, 0, 0));
    }

    private void save() {
        String text = input.getText().toString().trim();
        TodoStore store = new TodoStore(this);

        if (editIndex >= 0) {
            store.replaceAt(editIndex, text, selectedReminderAt);
            LockTodoWidget.updateAll(this);
            ReminderScheduler.reschedule(this);
            if (selectedReminderAt > 0L && !ReminderScheduler.canScheduleExact(this)) {
                ReminderScheduler.requestExactAlarmPermission(this);
            }
            finish();
            return;
        }

        if (text.isEmpty()) return;
        store.add(text, selectedReminderAt);
        LockTodoWidget.updateAll(this);
        ReminderScheduler.reschedule(this);
        if (selectedReminderAt > 0L && !ReminderScheduler.canScheduleExact(this)) {
            ReminderScheduler.requestExactAlarmPermission(this);
        }

        selectedReminderAt = 0L;
        updateReminderButton();
        input.setText("");
        input.requestFocus();
        input.post(() -> {
            InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (inputManager != null) inputManager.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
