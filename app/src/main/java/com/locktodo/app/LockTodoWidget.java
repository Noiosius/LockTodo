package com.locktodo.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;

import java.util.List;

public class LockTodoWidget extends AppWidgetProvider {
    private static final String ACTION_COMPLETE = "com.locktodo.app.ACTION_COMPLETE";
    private static final String EXTRA_INDEX = "index";

    private static final int[] ROW_IDS = {
            R.id.row1, R.id.row2, R.id.row3, R.id.row4,
            R.id.row5, R.id.row6, R.id.row7, R.id.row8
    };
    private static final int[] CHECK_IDS = {
            R.id.check1, R.id.check2, R.id.check3, R.id.check4,
            R.id.check5, R.id.check6, R.id.check7, R.id.check8
    };
    private static final int[] TEXT_IDS = {
            R.id.text1, R.id.text2, R.id.text3, R.id.text4,
            R.id.text5, R.id.text6, R.id.text7, R.id.text8
    };
    private static final int[] HANDLE_IDS = {
            R.id.handle1, R.id.handle2, R.id.handle3, R.id.handle4,
            R.id.handle5, R.id.handle6, R.id.handle7, R.id.handle8
    };

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        for (int id : appWidgetIds) updateWidget(context, manager, id);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager, int appWidgetId, Bundle newOptions) {
        updateWidget(context, manager, appWidgetId);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (!ACTION_COMPLETE.equals(intent.getAction())) return;

        int index = intent.getIntExtra(EXTRA_INDEX, -1);
        if (index < 0) return;

        SharedPreferences prefs = AppPrefs.get(context);
        prefs.edit().putInt(AppPrefs.KEY_CHECKING_INDEX, index).apply();
        updateAll(context);
        vibrate(context, prefs);

        PendingResult pending = goAsync();
        Context app = context.getApplicationContext();
        new Thread(() -> {
            try {
                int delay = prefs.getInt(AppPrefs.KEY_CHECK_DELAY, 240);
                Thread.sleep(Math.max(0, delay));
                new TodoStore(app).removeAt(index);
                AppPrefs.get(app).edit().putInt(AppPrefs.KEY_CHECKING_INDEX, -1).apply();
                updateAll(app);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } finally {
                pending.finish();
            }
        }).start();
    }

    static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName component = new ComponentName(context, LockTodoWidget.class);
        int[] ids = manager.getAppWidgetIds(component);
        for (int id : ids) updateWidget(context, manager, id);
    }

    private static void updateWidget(Context context, AppWidgetManager manager, int appWidgetId) {
        SharedPreferences prefs = AppPrefs.get(context);
        List<String> items = new TodoStore(context).load();
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_lock_todo);

        boolean lightText = prefs.getBoolean(AppPrefs.KEY_LIGHT_TEXT, true);
        boolean empty = items.isEmpty();
        boolean showEmptyPanel = prefs.getBoolean(AppPrefs.KEY_SHOW_EMPTY_PANEL, false);
        int fillAlpha = prefs.getInt(AppPrefs.KEY_FILL_ALPHA, 0);
        int borderAlpha = prefs.getInt(AppPrefs.KEY_BORDER_ALPHA, 0);

        int bgRes = lightText ? R.drawable.widget_panel_bg_dark : R.drawable.widget_panel_bg_light;
        int borderRes = lightText ? R.drawable.widget_panel_border_light : R.drawable.widget_panel_border_dark;
        views.setInt(R.id.panel_bg, "setBackgroundResource", bgRes);
        views.setInt(R.id.panel_border, "setBackgroundResource", borderRes);
        float panelVisibility = (empty && !showEmptyPanel) ? 0f : 1f;
        views.setFloat(R.id.panel_bg, "setAlpha", panelVisibility * clamp01(fillAlpha / 100f));
        views.setFloat(R.id.panel_border, "setAlpha", panelVisibility * clamp01(borderAlpha / 100f));

        int hPadding = prefs.getInt(AppPrefs.KEY_HORIZONTAL_PADDING, 8);
        views.setViewPadding(R.id.content_container, dp(context, hPadding), 0, dp(context, hPadding), 0);

        Intent addIntent = new Intent(context, QuickTodoActivity.class);
        addIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent addPending = PendingIntent.getActivity(
                context,
                200000 + appWidgetId,
                addIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        views.setOnClickPendingIntent(R.id.widget_root, addPending);
        views.setOnClickPendingIntent(R.id.blank_click_area, addPending);
        views.setOnClickPendingIntent(R.id.add_button, addPending);

        boolean showPlus = prefs.getBoolean(AppPrefs.KEY_SHOW_PLUS, true);
        boolean hidePlusWhenEmpty = prefs.getBoolean(AppPrefs.KEY_HIDE_PLUS_WHEN_EMPTY, true);
        boolean plusVisible = showPlus && !(empty && hidePlusWhenEmpty);
        views.setViewVisibility(R.id.add_button, plusVisible ? View.VISIBLE : View.GONE);
        views.setTextViewTextSize(R.id.add_button, TypedValue.COMPLEX_UNIT_SP, prefs.getInt(AppPrefs.KEY_PLUS_SIZE, 16));
        views.setFloat(R.id.add_button, "setAlpha", clamp01(prefs.getInt(AppPrefs.KEY_PLUS_ALPHA, 25) / 100f));

        for (int rowId : ROW_IDS) views.setViewVisibility(rowId, View.GONE);

        int textSize = prefs.getInt(AppPrefs.KEY_TEXT_SIZE, 16);
        int textAlpha = prefs.getInt(AppPrefs.KEY_TEXT_ALPHA, 100);
        int rowPadding = prefs.getInt(AppPrefs.KEY_ROW_PADDING, 5);
        int maxRows = Math.min(8, prefs.getInt(AppPrefs.KEY_MAX_ROWS, 8));
        int checkingIndex = prefs.getInt(AppPrefs.KEY_CHECKING_INDEX, -1);
        int capacity = visibleCapacity(manager, appWidgetId, textSize, rowPadding, maxRows);
        int visibleCount = Math.min(items.size(), Math.min(8, capacity));

        int base = lightText ? 255 : 0;
        int textColor = Color.argb(alpha255(textAlpha), base, base, base);
        int checkColor = Color.argb(alpha255(prefs.getInt(AppPrefs.KEY_CHECK_ALPHA, 90)), base, base, base);
        int handleColor = Color.argb(alpha255(prefs.getInt(AppPrefs.KEY_HANDLE_ALPHA, 55)), base, base, base);

        for (int i = 0; i < visibleCount; i++) {
            int rowId = ROW_IDS[i];
            int checkId = CHECK_IDS[i];
            int textId = TEXT_IDS[i];
            int handleId = HANDLE_IDS[i];

            views.setViewVisibility(rowId, View.VISIBLE);
            views.setTextViewText(textId, items.get(i));
            views.setTextViewText(checkId, i == checkingIndex ? "✓" : "○");
            views.setTextViewText(handleId, "≡");
            views.setTextColor(textId, textColor);
            views.setTextColor(checkId, checkColor);
            views.setTextColor(handleId, handleColor);
            views.setTextViewTextSize(textId, TypedValue.COMPLEX_UNIT_SP, textSize);
            views.setTextViewTextSize(checkId, TypedValue.COMPLEX_UNIT_SP, Math.max(14, textSize + 2));
            views.setTextViewTextSize(handleId, TypedValue.COMPLEX_UNIT_SP, Math.max(15, textSize + 1));
            views.setViewPadding(rowId, 0, dp(context, rowPadding), 0, dp(context, rowPadding));

            Intent complete = new Intent(context, LockTodoWidget.class);
            complete.setAction(ACTION_COMPLETE);
            complete.putExtra(EXTRA_INDEX, i);
            PendingIntent completePending = PendingIntent.getBroadcast(
                    context,
                    300000 + appWidgetId * 10 + i,
                    complete,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            views.setOnClickPendingIntent(checkId, completePending);

            Intent edit = new Intent(context, QuickTodoActivity.class);
            edit.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            edit.putExtra(QuickTodoActivity.EXTRA_EDIT_INDEX, i);
            PendingIntent editPending = PendingIntent.getActivity(
                    context,
                    400000 + appWidgetId * 10 + i,
                    edit,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            views.setOnClickPendingIntent(textId, editPending);

            Intent reorder = new Intent(context, ReorderActivity.class);
            reorder.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent reorderPending = PendingIntent.getActivity(
                    context,
                    500000 + appWidgetId * 10 + i,
                    reorder,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            views.setOnClickPendingIntent(handleId, reorderPending);
        }

        int hidden = items.size() - visibleCount;
        if (hidden > 0) {
            views.setViewVisibility(R.id.more_count, View.VISIBLE);
            views.setTextViewText(R.id.more_count, "… " + hidden);
            views.setTextColor(R.id.more_count, handleColor);
            Intent reorder = new Intent(context, ReorderActivity.class);
            reorder.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent reorderPending = PendingIntent.getActivity(
                    context,
                    590000 + appWidgetId,
                    reorder,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            views.setOnClickPendingIntent(R.id.more_count, reorderPending);
        } else {
            views.setViewVisibility(R.id.more_count, View.GONE);
        }

        manager.updateAppWidget(appWidgetId, views);
    }

    private static int visibleCapacity(AppWidgetManager manager, int appWidgetId, int textSize, int rowPadding, int maxRows) {
        Bundle options = manager.getAppWidgetOptions(appWidgetId);
        int minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0);
        if (minHeight <= 0) return Math.max(1, maxRows);
        int estimatedRow = Math.max(34, Math.round(textSize * 1.55f) + rowPadding * 2);
        int capacity = Math.max(1, (minHeight - 8) / estimatedRow);
        return Math.max(1, Math.min(Math.max(1, maxRows), capacity));
    }

    private static void vibrate(Context context, SharedPreferences prefs) {
        if (!prefs.getBoolean(AppPrefs.KEY_HAPTIC, true)) return;
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE));
        }
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static int alpha255(int percent) {
        return Math.max(0, Math.min(255, Math.round(percent * 2.55f)));
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
