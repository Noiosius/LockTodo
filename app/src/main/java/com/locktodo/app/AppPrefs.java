package com.locktodo.app;

import android.content.Context;
import android.content.SharedPreferences;

final class AppPrefs {
    static final String PREFS = "lock_todo_prefs";

    static final String KEY_SHOW_PLUS = "show_plus";
    static final String KEY_HIDE_PLUS_WHEN_EMPTY = "hide_plus_when_empty";
    static final String KEY_SHOW_EMPTY_PANEL = "show_empty_panel";
    static final String KEY_HAPTIC = "haptic";
    static final String KEY_LIGHT_TEXT = "light_text";

    static final String KEY_TEXT_SIZE = "text_size_sp";
    static final String KEY_TEXT_ALPHA = "text_alpha";
    static final String KEY_FILL_ALPHA = "fill_alpha";
    static final String KEY_BORDER_ALPHA = "border_alpha";
    static final String KEY_ROW_PADDING = "row_padding_dp";
    static final String KEY_HORIZONTAL_PADDING = "horizontal_padding_dp";
    static final String KEY_MAX_ROWS = "max_rows";

    static final String KEY_CHECK_ALPHA = "check_alpha";
    static final String KEY_HANDLE_ALPHA = "handle_alpha";
    static final String KEY_PLUS_SIZE = "plus_size_sp";
    static final String KEY_PLUS_ALPHA = "plus_alpha";
    static final String KEY_CHECK_DELAY = "check_delay_ms";
    static final String KEY_CHECKING_INDEX = "checking_index";
    static final String KEY_POPUP_DIM = "popup_dim_percent";

    private static final String KEY_V07_MIGRATED = "v07_transparency_migrated";

    private AppPrefs() {}

    static SharedPreferences get(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        migrateToV07(prefs);
        return prefs;
    }

    private static void migrateToV07(SharedPreferences prefs) {
        if (prefs.getBoolean(KEY_V07_MIGRATED, false)) return;

        SharedPreferences.Editor editor = prefs.edit();
        invertIfPresent(prefs, editor, KEY_TEXT_ALPHA);
        invertIfPresent(prefs, editor, KEY_FILL_ALPHA);
        invertIfPresent(prefs, editor, KEY_BORDER_ALPHA);
        invertIfPresent(prefs, editor, KEY_CHECK_ALPHA);
        invertIfPresent(prefs, editor, KEY_HANDLE_ALPHA);

        if (prefs.contains(KEY_PLUS_ALPHA)) {
            int oldValue = clampPercent(prefs.getInt(KEY_PLUS_ALPHA, 25));
            // v0.6 default was intentionally faint. v0.7 aligns + with the handle by default.
            editor.putInt(KEY_PLUS_ALPHA, oldValue == 25 ? 45 : 100 - oldValue);
        }
        if (prefs.contains(KEY_PLUS_SIZE) && prefs.getInt(KEY_PLUS_SIZE, 16) == 16) {
            editor.putInt(KEY_PLUS_SIZE, 17);
        }
        if (prefs.contains(KEY_CHECK_DELAY) && prefs.getInt(KEY_CHECK_DELAY, 240) == 240) {
            editor.putInt(KEY_CHECK_DELAY, 300);
        }

        editor.putBoolean(KEY_V07_MIGRATED, true).apply();
    }

    private static void invertIfPresent(SharedPreferences prefs, SharedPreferences.Editor editor, String key) {
        if (!prefs.contains(key)) return;
        editor.putInt(key, 100 - clampPercent(prefs.getInt(key, 0)));
    }

    private static int clampPercent(int value) {
        return Math.max(0, Math.min(100, value));
    }

    static void reset(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String todos = prefs.getString("todos_json", "[]");
        prefs.edit()
                .clear()
                .putString("todos_json", todos)
                .putBoolean(KEY_V07_MIGRATED, true)
                .apply();
    }
}
