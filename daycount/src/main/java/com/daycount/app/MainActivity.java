package com.daycount.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy.MM.dd", Locale.KOREA);
    private EventStore store;
    private LinearLayout listContainer;
    private TextView emptyView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new EventStore(this);

        Window window = getWindow();
        window.setStatusBarColor(Color.BLACK);
        window.setNavigationBarColor(Color.BLACK);

        setContentView(buildUi());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshList();
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(12, 12, 12));
        root.setPadding(dp(18), dp(14), dp(18), dp(18));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("Day Count");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView settings = new TextView(this);
        settings.setText("⚙");
        settings.setTextColor(Color.rgb(205, 205, 205));
        settings.setTextSize(22);
        settings.setGravity(Gravity.CENTER);
        settings.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, SettingsActivity.class)));
        header.addView(settings, new LinearLayout.LayoutParams(dp(48), dp(54)));

        TextView add = new TextView(this);
        add.setText("+");
        add.setTextColor(Color.WHITE);
        add.setTextSize(30);
        add.setGravity(Gravity.CENTER);
        add.setPadding(dp(6), dp(2), dp(2), dp(6));
        add.setOnClickListener(v -> showEditor(null));
        header.addView(add, new LinearLayout.LayoutParams(dp(48), dp(54)));
        root.addView(header);

        TextView guide = new TextView(this);
        guide.setText("날짜를 누르면 위젯 표시가 바뀝니다");
        guide.setTextColor(Color.rgb(145, 145, 145));
        guide.setTextSize(12);
        guide.setPadding(0, 0, 0, dp(12));
        root.addView(guide);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(listContainer, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        emptyView = new TextView(this);
        emptyView.setText("+ 를 눌러 날짜를 추가하세요");
        emptyView.setTextColor(Color.rgb(120, 120, 120));
        emptyView.setTextSize(14);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setPadding(0, dp(48), 0, dp(48));

        return root;
    }

    private void refreshList() {
        if (listContainer == null) return;
        listContainer.removeAllViews();
        List<EventStore.Event> events = store.load();
        long selectedId = store.getSelectedId();

        if (events.isEmpty()) {
            listContainer.addView(emptyView);
            DayCountWidget.updateAll(this);
            return;
        }

        for (EventStore.Event event : events) {
            boolean selected = event.id == selectedId;
            listContainer.addView(buildRow(event, selected));
        }
    }

    private View buildRow(EventStore.Event event, boolean selected) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(14), dp(4), dp(14));
        row.setClickable(true);

        TextView dot = new TextView(this);
        dot.setText(selected ? "●" : "○");
        dot.setTextColor(selected ? Color.WHITE : Color.rgb(105, 105, 105));
        dot.setTextSize(18);
        dot.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(dot, new LinearLayout.LayoutParams(dp(32), ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout textBox = new LinearLayout(this);
        textBox.setOrientation(LinearLayout.VERTICAL);

        TextView name = new TextView(this);
        name.setText(event.name);
        name.setTextColor(Color.WHITE);
        name.setTextSize(16);
        name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        textBox.addView(name);

        TextView date = new TextView(this);
        date.setText(event.date.format(dateFormat));
        date.setTextColor(Color.rgb(135, 135, 135));
        date.setTextSize(12);
        date.setPadding(0, dp(3), 0, 0);
        textBox.addView(date);

        row.addView(textBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView count = new TextView(this);
        count.setText(DayCountWidget.formatCount(event.date));
        count.setTextColor(selected ? Color.WHITE : Color.rgb(165, 165, 165));
        count.setTextSize(22);
        count.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        count.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        row.addView(count, new LinearLayout.LayoutParams(dp(105), ViewGroup.LayoutParams.WRAP_CONTENT));

        row.setOnClickListener(v -> {
            store.select(event.id);
            DayCountWidget.updateAll(MainActivity.this);
            refreshList();
        });
        row.setOnLongClickListener(v -> {
            showActions(event);
            return true;
        });

        View separator = new View(this);
        separator.setBackgroundColor(Color.rgb(35, 35, 35));
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        wrapper.addView(separator, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        return wrapper;
    }

    private void showActions(EventStore.Event event) {
        new AlertDialog.Builder(this)
                .setItems(new String[]{"수정", "삭제"}, (dialog, which) -> {
                    if (which == 0) {
                        showEditor(event);
                    } else {
                        new AlertDialog.Builder(this)
                                .setMessage("'" + event.name + "' 날짜를 삭제할까요?")
                                .setNegativeButton("취소", null)
                                .setPositiveButton("삭제", (d, w) -> {
                                    store.delete(event.id);
                                    DayCountWidget.updateAll(MainActivity.this);
                                    refreshList();
                                })
                                .show();
                    }
                })
                .show();
    }

    private void showEditor(EventStore.Event existing) {
        final LocalDate[] picked = {existing == null ? LocalDate.now() : existing.date};

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = dp(20);
        box.setPadding(p, dp(6), p, 0);

        EditText nameInput = new EditText(this);
        nameInput.setHint("이름 (예: 사귄 날)");
        nameInput.setSingleLine(true);
        nameInput.setText(existing == null ? "" : existing.name);
        box.addView(nameInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button dateButton = new Button(this);
        dateButton.setAllCaps(false);
        dateButton.setText(picked[0].format(dateFormat));
        dateButton.setOnClickListener(v -> {
            LocalDate d = picked[0];
            new DatePickerDialog(
                    MainActivity.this,
                    (view, year, month, day) -> {
                        picked[0] = LocalDate.of(year, month + 1, day);
                        dateButton.setText(picked[0].format(dateFormat));
                    },
                    d.getYear(), d.getMonthValue() - 1, d.getDayOfMonth()
            ).show();
        });
        LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dateParams.topMargin = dp(10);
        box.addView(dateButton, dateParams);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(existing == null ? "날짜 추가" : "날짜 수정")
                .setView(box)
                .setNegativeButton("취소", null)
                .setPositiveButton("저장", null)
                .create();

        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            if (name.isEmpty()) name = picked[0].format(dateFormat);
            long id = existing == null ? System.currentTimeMillis() : existing.id;
            store.save(new EventStore.Event(id, name, picked[0]), existing == null);
            DayCountWidget.updateAll(MainActivity.this);
            dialog.dismiss();
            refreshList();
        }));
        dialog.show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
