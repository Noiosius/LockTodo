package com.daycount.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

final class EventStore {
    private static final String PREFS = "day_count_events";
    private static final String KEY_EVENTS = "events";
    private static final String KEY_SELECTED_ID = "selected_id";

    static final class Event {
        final long id;
        final String name;
        final LocalDate date;

        Event(long id, String name, LocalDate date) {
            this.id = id;
            this.name = name;
            this.date = date;
        }
    }

    private final SharedPreferences prefs;

    EventStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    List<Event> load() {
        List<Event> out = new ArrayList<>();
        String raw = prefs.getString(KEY_EVENTS, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                out.add(new Event(
                        o.getLong("id"),
                        o.optString("name", "날짜"),
                        LocalDate.of(o.getInt("year"), o.getInt("month"), o.getInt("day"))
                ));
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    void save(Event event, boolean selectAfterSave) {
        List<Event> events = load();
        boolean replaced = false;
        for (int i = 0; i < events.size(); i++) {
            if (events.get(i).id == event.id) {
                events.set(i, event);
                replaced = true;
                break;
            }
        }
        if (!replaced) events.add(event);
        write(events);
        if (selectAfterSave || getSelectedId() < 0) select(event.id);
    }

    void delete(long id) {
        List<Event> events = load();
        events.removeIf(e -> e.id == id);
        write(events);
        if (getSelectedId() == id) {
            prefs.edit().putLong(KEY_SELECTED_ID, events.isEmpty() ? -1L : events.get(0).id).apply();
        }
    }

    void select(long id) {
        prefs.edit().putLong(KEY_SELECTED_ID, id).apply();
    }

    long getSelectedId() {
        return prefs.getLong(KEY_SELECTED_ID, -1L);
    }

    Event getSelected() {
        long id = getSelectedId();
        for (Event e : load()) if (e.id == id) return e;
        return null;
    }

    private void write(List<Event> events) {
        JSONArray array = new JSONArray();
        for (Event e : events) {
            try {
                JSONObject o = new JSONObject();
                o.put("id", e.id);
                o.put("name", e.name);
                o.put("year", e.date.getYear());
                o.put("month", e.date.getMonthValue());
                o.put("day", e.date.getDayOfMonth());
                array.put(o);
            } catch (Throwable ignored) {
            }
        }
        prefs.edit().putString(KEY_EVENTS, array.toString()).apply();
    }
}
