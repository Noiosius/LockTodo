package com.batterycheck.app;

import android.content.Context;
import android.content.SharedPreferences;

final class Prefs {
    static final String NAME = "battery_check_prefs";
    static final String KEY_BG_TRANSPARENCY = "bg_transparency";
    static final String KEY_TEXT_SCALE = "text_scale";
    static final String KEY_POSITION = "position";
    static final String KEY_BORDER = "border";

    static final int POSITION_TOP = 0;
    static final int POSITION_CENTER = 1;
    static final int POSITION_BOTTOM = 2;

    private Prefs() {}

    static SharedPreferences get(Context context) {
        return context.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    static void reset(Context context) {
        get(context).edit().clear().apply();
    }
}
