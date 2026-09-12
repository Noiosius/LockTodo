package com.locktodo.app;

import android.content.Context;
import android.content.SharedPreferences;

final class AppPrefs {
    static final String PREFS = "lock_todo_prefs";

    static final String KEY_AUTO_SHOW = "auto_show";
    static final String KEY_SHOW_PLUS = "show_plus";
    static final String KEY_HIDE_PLUS_WHEN_EMPTY = "hide_plus_when_empty";
    static final String KEY_HAPTIC = "haptic";
    static final String KEY_LIGHT_TEXT = "light_text";

    static final String KEY_PANEL_WIDTH = "panel_width_pct";
    static final String KEY_PANEL_HEIGHT = "panel_height_dp";
    static final String KEY_PANEL_X = "panel_x_pct";
    static final String KEY_TOP_OFFSET = "top_offset_dp";

    static final String KEY_ROW_HEIGHT = "row_height_dp";
    static final String KEY_ROW_GAP = "row_gap_dp";
    static final String KEY_ROW_PADDING = "row_padding_dp";

    static final String KEY_TEXT_SIZE = "text_size_sp";
    static final String KEY_TEXT_ALPHA = "text_alpha";
    static final String KEY_BORDER_ALPHA = "border_alpha";
    static final String KEY_BORDER_WIDTH = "border_width_dp";
    static final String KEY_FILL_ALPHA = "fill_alpha";
    static final String KEY_CORNER_RADIUS = "corner_radius_dp";

    static final String KEY_CHECK_SIZE = "check_size_sp";
    static final String KEY_CHECK_ALPHA = "check_alpha";
    static final String KEY_HANDLE_SIZE = "handle_size_sp";
    static final String KEY_HANDLE_ALPHA = "handle_alpha";
    static final String KEY_PLUS_SIZE = "plus_size_sp";
    static final String KEY_PLUS_ALPHA = "plus_alpha";
    static final String KEY_PLUS_END_MARGIN = "plus_end_margin_dp";
    static final String KEY_PLUS_BOTTOM_MARGIN = "plus_bottom_margin_dp";

    static final String KEY_CHECK_DELAY = "check_delay_ms";

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
