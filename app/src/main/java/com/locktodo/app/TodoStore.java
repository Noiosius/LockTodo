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
}
