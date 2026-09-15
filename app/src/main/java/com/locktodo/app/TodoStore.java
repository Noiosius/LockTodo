package com.locktodo.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

final class TodoStore {
    private static final String KEY_TODOS = "todos_json";
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

    synchronized void save(List<String> items) {
        JSONArray array = new JSONArray();
        for (String item : items) {
            if (item != null && !item.trim().isEmpty()) array.put(item.trim());
        }
        prefs.edit().putString(KEY_TODOS, array.toString()).apply();
    }

    synchronized void add(String text) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) return;
        List<String> items = load();
        items.add(value);
        save(items);
    }

    synchronized void replaceAt(int index, String text) {
        String value = text == null ? "" : text.trim();
        List<String> items = load();
        if (index < 0 || index >= items.size()) return;
        if (value.isEmpty()) items.remove(index);
        else items.set(index, value);
        save(items);
    }

    synchronized void removeAt(int index) {
        List<String> items = load();
        if (index < 0 || index >= items.size()) return;
        items.remove(index);
        save(items);
    }

    synchronized boolean removeAtIfMatches(int index, String expectedText) {
        List<String> items = load();
        if (index < 0 || index >= items.size()) return false;
        if (expectedText == null || !expectedText.equals(items.get(index))) return false;
        items.remove(index);
        save(items);
        return true;
    }

    synchronized void move(int from, int to) {
        List<String> items = load();
        if (from < 0 || from >= items.size() || to < 0 || to >= items.size() || from == to) return;
        String item = items.remove(from);
        items.add(to, item);
        save(items);
    }
}
