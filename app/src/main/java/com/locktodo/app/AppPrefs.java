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

    private AppPrefs() {}

    static SharedPreferences get(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static void reset(Context context) {
        SharedPreferences prefs = get(context);
        String todos = prefs.getString("todos_json", "[]");
        prefs.edit().clear().putString("todos_json", todos).apply();
    }
}
