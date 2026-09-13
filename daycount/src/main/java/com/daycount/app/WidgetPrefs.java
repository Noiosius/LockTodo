package com.daycount.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.view.Gravity;

final class WidgetPrefs {
    private static final String PREFS = "day_count_widget_settings";
    private static final String KEY_TEXT_SIZE = "text_size";
    private static final String KEY_TEXT_COLOR = "text_color";
    private static final String KEY_POSITION = "position";

    static final int DEFAULT_TEXT_SIZE = 44;
    static final int DEFAULT_TEXT_COLOR = Color.WHITE;
    static final int DEFAULT_POSITION = 4;

    private WidgetPrefs() {}

    static SharedPreferences get(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static int textSize(Context context) {
        return get(context).getInt(KEY_TEXT_SIZE, DEFAULT_TEXT_SIZE);
    }

    static int textColor(Context context) {
        return get(context).getInt(KEY_TEXT_COLOR, DEFAULT_TEXT_COLOR);
    }

    static int position(Context context) {
        return get(context).getInt(KEY_POSITION, DEFAULT_POSITION);
    }

    static void save(Context context, int textSize, int textColor, int position) {
        get(context).edit()
                .putInt(KEY_TEXT_SIZE, Math.max(24, Math.min(80, textSize)))
                .putInt(KEY_TEXT_COLOR, textColor)
                .putInt(KEY_POSITION, Math.max(0, Math.min(8, position)))
                .apply();
    }

    static int gravityForPosition(int position) {
        switch (position) {
            case 0: return Gravity.TOP | Gravity.START;
            case 1: return Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            case 2: return Gravity.TOP | Gravity.END;
            case 3: return Gravity.CENTER_VERTICAL | Gravity.START;
            case 5: return Gravity.CENTER_VERTICAL | Gravity.END;
            case 6: return Gravity.BOTTOM | Gravity.START;
            case 7: return Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            case 8: return Gravity.BOTTOM | Gravity.END;
            case 4:
            default: return Gravity.CENTER;
        }
    }
}
