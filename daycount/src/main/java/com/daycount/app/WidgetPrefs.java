package com.daycount.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

final class WidgetPrefs {
    private static final String PREFS = "day_count_widget_settings";
    private static final String KEY_TEXT_SIZE = "text_size";
    private static final String KEY_TEXT_COLOR = "text_color";
    private static final String KEY_POSITION = "position";
    private static final String KEY_POSITION_X = "position_x";
    private static final String KEY_POSITION_Y = "position_y";

    static final int DEFAULT_TEXT_SIZE = 44;
    static final int DEFAULT_TEXT_COLOR = Color.WHITE;
    static final float DEFAULT_POSITION_X = 0.5f;
    static final float DEFAULT_POSITION_Y = 0.5f;

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

    static float positionX(Context context) {
        SharedPreferences prefs = get(context);
        if (prefs.contains(KEY_POSITION_X)) {
            return clamp01(prefs.getFloat(KEY_POSITION_X, DEFAULT_POSITION_X));
        }
        return legacyX(prefs.getInt(KEY_POSITION, 4));
    }

    static float positionY(Context context) {
        SharedPreferences prefs = get(context);
        if (prefs.contains(KEY_POSITION_Y)) {
            return clamp01(prefs.getFloat(KEY_POSITION_Y, DEFAULT_POSITION_Y));
        }
        return legacyY(prefs.getInt(KEY_POSITION, 4));
    }

    static void save(Context context, int textSize, int textColor, float positionX, float positionY) {
        get(context).edit()
                .putInt(KEY_TEXT_SIZE, Math.max(24, Math.min(80, textSize)))
                .putInt(KEY_TEXT_COLOR, textColor)
                .putFloat(KEY_POSITION_X, clamp01(positionX))
                .putFloat(KEY_POSITION_Y, clamp01(positionY))
                .apply();
    }

    private static float legacyX(int position) {
        int col = Math.max(0, Math.min(2, position % 3));
        return col / 2f;
    }

    private static float legacyY(int position) {
        int row = Math.max(0, Math.min(2, position / 3));
        return row / 2f;
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
