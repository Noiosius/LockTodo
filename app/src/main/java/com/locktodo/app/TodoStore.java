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

    synchronized void save(List<String> items) {
        List<Long> reminders = loadRemindersForSize(items.size());
        saveBoth(items, reminders);
    }

    synchronized void add(String text) {
        add(text, 0L);
    }

    synchronized void add(String text, long reminderAt) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) return;
        List<String> items = load();
        List<Long> reminders = loadRemindersForSize(items.size());
        items.add(value);
        reminders.add(Math.max(0L, reminderAt));
        saveBoth(items, reminders);
    }

    synchronized void replaceAt(int index, String text) {
        replaceAt(index, text, getReminderAt(index));
    }

    synchronized void replaceAt(int index, String text, long reminderAt) {
        String value = text == null ? "" : text.trim();
        List<String> items = load();
        if (index < 0 || index >= items.size()) return;
        List<Long> reminders = loadRemindersForSize(items.size());
        if (value.isEmpty()) {
            items.remove(index);
            reminders.remove(index);
        } else {
            items.set(index, value);
            reminders.set(index, Math.max(0L, reminderAt));
        }
        saveBoth(items, reminders);
    }

    synchronized void removeAt(int index) {
        List<String> items = load();
        if (index < 0 || index >= items.size()) return;
        List<Long> reminders = loadRemindersForSize(items.size());
        items.remove(index);
        reminders.remove(index);
        saveBoth(items, reminders);
    }

    synchronized boolean removeAtIfMatches(int index, String expectedText) {
        List<String> items = load();
        if (index < 0 || index >= items.size()) return false;
        if (expectedText == null || !expectedText.equals(items.get(index))) return false;
        List<Long> reminders = loadRemindersForSize(items.size());
        items.remove(index);
        reminders.remove(index);
        saveBoth(items, reminders);
        return true;
    }

    synchronized void move(int from, int to) {
        List<String> items = load();
        if (from < 0 || from >= items.size() || to < 0 || to >= items.size() || from == to) return;
        List<Long> reminders = loadRemindersForSize(items.size());
        String item = items.remove(from);
        Long reminder = reminders.remove(from);
        items.add(to, item);
        reminders.add(to, reminder);
        saveBoth(items, reminders);
    }

    synchronized boolean clearReminderAtIfMatches(int index, long expectedReminderAt) {
        List<String> items = load();
        if (index < 0 || index >= items.size()) return false;
        List<Long> reminders = loadRemindersForSize(items.size());
        if (reminders.get(index) != expectedReminderAt) return false;
        reminders.set(index, 0L);
        saveBoth(items, reminders);
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

    private void saveBoth(List<String> items, List<Long> reminders) {
        JSONArray itemArray = new JSONArray();
        JSONArray reminderArray = new JSONArray();
        int count = 0;
        for (int i = 0; i < items.size(); i++) {
            String value = items.get(i) == null ? "" : items.get(i).trim();
            if (value.isEmpty()) continue;
            itemArray.put(value);
            long reminder = i < reminders.size() ? reminders.get(i) : 0L;
            reminderArray.put(Math.max(0L, reminder));
            count++;
        }
        while (reminderArray.length() < count) reminderArray.put(0L);
        prefs.edit()
                .putString(KEY_TODOS, itemArray.toString())
                .putString(KEY_REMINDERS, reminderArray.toString())
                .apply();
    }
}
