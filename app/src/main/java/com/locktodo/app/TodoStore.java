package com.locktodo.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

final class TodoStore {
    private static final String KEY_TODOS = "todos_json";
    private static final String KEY_REMINDERS = "todo_reminders_json";
    private static final String KEY_REMINDER_VIBRATES = "todo_reminder_vibrates_json";
    private final SharedPreferences prefs;

    TodoStore(Context context) {
        prefs = context.getSharedPreferences(AppPrefs.PREFS, Context.MODE_PRIVATE);
    }

    synchronized List<String> load() {
        ArrayList<String> items = new ArrayList<>();
        String raw = prefs.getString(KEY_TODOS, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                String text = array.optString(i, "").trim();
                if (!text.isEmpty()) items.add(text);
            }
        } catch (JSONException ignored) {
        }
        return items;
    }

    synchronized List<Long> loadReminders() {
        return loadRemindersForSize(load().size());
    }

    synchronized long getReminderAt(int index) {
        List<String> items = load();
        if (index < 0 || index >= items.size()) return 0L;
        return loadRemindersForSize(items.size()).get(index);
    }

    synchronized boolean getReminderVibrate(int index) {
        List<String> items = load();
        if (index < 0 || index >= items.size()) return false;
        return loadVibratesForSize(items.size()).get(index);
    }

    synchronized void save(List<String> items) {
        List<Long> reminders = loadRemindersForSize(items.size());
        List<Boolean> vibrates = loadVibratesForSize(items.size());
        saveAll(items, reminders, vibrates);
    }

    synchronized void add(String text) {
        add(text, 0L, false);
    }

    synchronized void add(String text, long reminderAt) {
        add(text, reminderAt, false);
    }

    synchronized void add(String text, long reminderAt, boolean vibrate) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) return;
        List<String> items = load();
        List<Long> reminders = loadRemindersForSize(items.size());
        List<Boolean> vibrates = loadVibratesForSize(items.size());
        items.add(value);
        reminders.add(Math.max(0L, reminderAt));
        vibrates.add(vibrate);
        saveAll(items, reminders, vibrates);
    }

    synchronized void replaceAt(int index, String text) {
        replaceAt(index, text, getReminderAt(index), getReminderVibrate(index));
    }

    synchronized void replaceAt(int index, String text, long reminderAt) {
        replaceAt(index, text, reminderAt, getReminderVibrate(index));
    }

    synchronized void replaceAt(int index, String text, long reminderAt, boolean vibrate) {
        String value = text == null ? "" : text.trim();
        List<String> items = load();
        if (index < 0 || index >= items.size()) return;
        List<Long> reminders = loadRemindersForSize(items.size());
        List<Boolean> vibrates = loadVibratesForSize(items.size());
        if (value.isEmpty()) {
            items.remove(index);
            reminders.remove(index);
            vibrates.remove(index);
        } else {
            items.set(index, value);
            reminders.set(index, Math.max(0L, reminderAt));
            vibrates.set(index, vibrate);
        }
        saveAll(items, reminders, vibrates);
    }

    synchronized void removeAt(int index) {
        List<String> items = load();
        if (index < 0 || index >= items.size()) return;
        List<Long> reminders = loadRemindersForSize(items.size());
        List<Boolean> vibrates = loadVibratesForSize(items.size());
        items.remove(index);
        reminders.remove(index);
        vibrates.remove(index);
        saveAll(items, reminders, vibrates);
    }

    synchronized boolean removeAtIfMatches(int index, String expectedText) {
        List<String> items = load();
        if (index < 0 || index >= items.size()) return false;
        if (expectedText == null || !expectedText.equals(items.get(index))) return false;
        List<Long> reminders = loadRemindersForSize(items.size());
        List<Boolean> vibrates = loadVibratesForSize(items.size());
        items.remove(index);
        reminders.remove(index);
        vibrates.remove(index);
        saveAll(items, reminders, vibrates);
        return true;
    }

    synchronized void move(int from, int to) {
        List<String> items = load();
        if (from < 0 || from >= items.size() || to < 0 || to >= items.size() || from == to) return;
        List<Long> reminders = loadRemindersForSize(items.size());
        List<Boolean> vibrates = loadVibratesForSize(items.size());
        String item = items.remove(from);
        Long reminder = reminders.remove(from);
        Boolean vibrate = vibrates.remove(from);
        items.add(to, item);
        reminders.add(to, reminder);
        vibrates.add(to, vibrate);
        saveAll(items, reminders, vibrates);
    }

    synchronized boolean clearReminderAtIfMatches(int index, long expectedReminderAt) {
        List<String> items = load();
        if (index < 0 || index >= items.size()) return false;
        List<Long> reminders = loadRemindersForSize(items.size());
        List<Boolean> vibrates = loadVibratesForSize(items.size());
        if (reminders.get(index) != expectedReminderAt) return false;
        reminders.set(index, 0L);
        saveAll(items, reminders, vibrates);
        return true;
    }

    private List<Long> loadRemindersForSize(int size) {
        ArrayList<Long> reminders = new ArrayList<>();
        String raw = prefs.getString(KEY_REMINDERS, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < Math.min(array.length(), size); i++) {
                reminders.add(Math.max(0L, array.optLong(i, 0L)));
            }
        } catch (JSONException ignored) {
        }
        while (reminders.size() < size) reminders.add(0L);
        while (reminders.size() > size) reminders.remove(reminders.size() - 1);
        return reminders;
    }

    private List<Boolean> loadVibratesForSize(int size) {
        ArrayList<Boolean> vibrates = new ArrayList<>();
        String raw = prefs.getString(KEY_REMINDER_VIBRATES, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < Math.min(array.length(), size); i++) {
                vibrates.add(array.optBoolean(i, false));
            }
        } catch (JSONException ignored) {
        }
        while (vibrates.size() < size) vibrates.add(false);
        while (vibrates.size() > size) vibrates.remove(vibrates.size() - 1);
        return vibrates;
    }

    private void saveAll(List<String> items, List<Long> reminders, List<Boolean> vibrates) {
        JSONArray itemArray = new JSONArray();
        JSONArray reminderArray = new JSONArray();
        JSONArray vibrateArray = new JSONArray();
        int count = 0;
        for (int i = 0; i < items.size(); i++) {
            String value = items.get(i) == null ? "" : items.get(i).trim();
            if (value.isEmpty()) continue;
            itemArray.put(value);
            long reminder = i < reminders.size() ? reminders.get(i) : 0L;
            reminderArray.put(Math.max(0L, reminder));
            vibrateArray.put(i < vibrates.size() && vibrates.get(i));
            count++;
        }
        while (reminderArray.length() < count) reminderArray.put(0L);
        while (vibrateArray.length() < count) vibrateArray.put(false);
        prefs.edit()
                .putString(KEY_TODOS, itemArray.toString())
                .putString(KEY_REMINDERS, reminderArray.toString())
                .putString(KEY_REMINDER_VIBRATES, vibrateArray.toString())
                .apply();
    }
}
