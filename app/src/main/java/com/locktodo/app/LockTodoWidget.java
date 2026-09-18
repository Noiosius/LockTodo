package com.locktodo.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;

import java.util.List;

public class LockTodoWidget extends AppWidgetProvider {
    static final String ACTION_COMPLETE = "com.locktodo.app.ACTION_COMPLETE";
    static final String ACTION_EDIT = "com.locktodo.app.ACTION_EDIT";
    static final String ACTION_REORDER = "com.locktodo.app.ACTION_REORDER";
    static final String ACTION_ADD = "com.locktodo.app.ACTION_ADD";
    static final String EXTRA_INDEX = "index";

    private static final long DOUBLE_TAP_WINDOW_MS = 500L;
    private static final String KEY_LAST_TAP_TIME = "gesture_last_tap_time";
    private static final String KEY_LAST_TAP_ACTION = "gesture_last_tap_action";
    private static final String KEY_LAST_TAP_INDEX = "gesture_last_tap_index";

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        for (int id : appWidgetIds) updateWidget(context, manager, id);
        if (appWidgetIds.length > 0) manager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.todo_list);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager, int appWidgetId, android.os.Bundle newOptions) {
        updateWidget(context, manager, appWidgetId);
        manager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.todo_list);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent.getAction();

        if (ACTION_ADD.equals(action)) {
            if (isConfirmedDoubleTap(context, ACTION_ADD, -1)) {
                openQuickTodo(context, -1);
            }
            return;
        }

        if (ACTION_EDIT.equals(action)) {
            int index = intent.getIntExtra(EXTRA_INDEX, -1);
            if (index < 0) return;
            if (isConfirmedDoubleTap(context, ACTION_EDIT, index)) {
                openQuickTodo(context, index);
            }
            return;
        }

        if (ACTION_REORDER.equals(action)) {
            if (isConfirmedDoubleTap(context, ACTION_REORDER, -1)) {
                Intent reorder = new Intent(context, ReorderActivity.class);
                reorder.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                context.startActivity(reorder);
            }
            return;
        }

        if (!ACTION_COMPLETE.equals(action)) return;

        int index = intent.getIntExtra(EXTRA_INDEX, -1);
        if (index < 0) return;
        if (!isConfirmedDoubleTap(context, ACTION_COMPLETE, index)) return;
        List<String> snapshot = new TodoStore(context).load();
        if (index >= snapshot.size()) return;
        String expectedText = snapshot.get(index);

        SharedPreferences prefs = AppPrefs.get(context);
        prefs.edit().putInt(AppPrefs.KEY_CHECKING_INDEX, index).apply();
        updateAll(context);
        vibrate(context, prefs);

        PendingResult pending = goAsync();
        Context app = context.getApplicationContext();
        new Thread(() -> {
            try {
                int delay = prefs.getInt(AppPrefs.KEY_CHECK_DELAY, 300);
                Thread.sleep(Math.max(0, delay));
                new TodoStore(app).removeAtIfMatches(index, expectedText);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } finally {
                AppPrefs.get(app).edit().putInt(AppPrefs.KEY_CHECKING_INDEX, -1).apply();
                updateAll(app);
                pending.finish();
            }
        }).start();
    }

    private static boolean isConfirmedDoubleTap(Context context, String action, int index) {
        SharedPreferences prefs = AppPrefs.get(context);
        long now = SystemClock.elapsedRealtime();
        long lastTime = prefs.getLong(KEY_LAST_TAP_TIME, 0L);
        String lastAction = prefs.getString(KEY_LAST_TAP_ACTION, "");
        int lastIndex = prefs.getInt(KEY_LAST_TAP_INDEX, Integer.MIN_VALUE);

        boolean confirmed = action.equals(lastAction)
                && index == lastIndex
                && now >= lastTime
                && now - lastTime <= DOUBLE_TAP_WINDOW_MS;

        SharedPreferences.Editor editor = prefs.edit();
        if (confirmed) {
            editor.remove(KEY_LAST_TAP_TIME)
                    .remove(KEY_LAST_TAP_ACTION)
                    .remove(KEY_LAST_TAP_INDEX)
                    .apply();
            return true;
        }

        editor.putLong(KEY_LAST_TAP_TIME, now)
                .putString(KEY_LAST_TAP_ACTION, action)
                .putInt(KEY_LAST_TAP_INDEX, index)
                .apply();
        return false;
    }

    private static void openQuickTodo(Context context, int editIndex) {
        Intent quick = new Intent(context, QuickTodoActivity.class);
        quick.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (editIndex >= 0) quick.putExtra(QuickTodoActivity.EXTRA_EDIT_INDEX, editIndex);
        context.startActivity(quick);
    }

    static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName component = new ComponentName(context, LockTodoWidget.class);
        int[] ids = manager.getAppWidgetIds(component);
        for (int id : ids) updateWidget(context, manager, id);
        if (ids.length > 0) manager.notifyAppWidgetViewDataChanged(ids, R.id.todo_list);
    }

    private static void updateWidget(Context context, AppWidgetManager manager, int appWidgetId) {
        SharedPreferences prefs = AppPrefs.get(context);
        List<String> items = new TodoStore(context).load();
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_lock_todo);

        boolean lightText = prefs.getBoolean(AppPrefs.KEY_LIGHT_TEXT, true);
        boolean empty = items.isEmpty();
        boolean showEmptyPanel = prefs.getBoolean(AppPrefs.KEY_SHOW_EMPTY_PANEL, false);
        int fillTransparency = prefs.getInt(AppPrefs.KEY_FILL_ALPHA, 100);
        int borderTransparency = prefs.getInt(AppPrefs.KEY_BORDER_ALPHA, 100);

        int bgRes = lightText ? R.drawable.widget_panel_bg_dark : R.drawable.widget_panel_bg_light;
        int borderRes = lightText ? R.drawable.widget_panel_border_light : R.drawable.widget_panel_border_dark;
        views.setInt(R.id.panel_bg, "setBackgroundResource", bgRes);
        views.setInt(R.id.panel_border, "setBackgroundResource", borderRes);
        float panelVisibility = (empty && !showEmptyPanel) ? 0f : 1f;
        views.setFloat(R.id.panel_bg, "setAlpha", panelVisibility * opacityFromTransparency(fillTransparency));
        views.setFloat(R.id.panel_border, "setAlpha", panelVisibility * opacityFromTransparency(borderTransparency));

        int hPadding = prefs.getInt(AppPrefs.KEY_HORIZONTAL_PADDING, 8);
        views.setViewPadding(R.id.content_container, dp(context, hPadding), 0, dp(context, hPadding), 0);

        Intent serviceIntent = new Intent(context, TodoRemoteViewsService.class);
        serviceIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        serviceIntent.setData(Uri.parse(serviceIntent.toUri(Intent.URI_INTENT_SCHEME)));
        views.setRemoteAdapter(R.id.todo_list, serviceIntent);

        Intent rowTemplateIntent = new Intent(context, LockTodoWidget.class);
        PendingIntent rowTemplate = PendingIntent.getBroadcast(
                context,
                300000 + appWidgetId,
                rowTemplateIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
        );
        views.setPendingIntentTemplate(R.id.todo_list, rowTemplate);

        Intent addBroadcast = new Intent(context, LockTodoWidget.class);
        addBroadcast.setAction(ACTION_ADD);
        PendingIntent addPending = PendingIntent.getBroadcast(
                context,
                200000 + appWidgetId,
                addBroadcast,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        views.setOnClickPendingIntent(R.id.widget_root, addPending);
        views.setOnClickPendingIntent(R.id.blank_click_area, addPending);
        views.setOnClickPendingIntent(R.id.add_button, addPending);

        boolean showPlus = prefs.getBoolean(AppPrefs.KEY_SHOW_PLUS, true);
        boolean hidePlusWhenEmpty = prefs.getBoolean(AppPrefs.KEY_HIDE_PLUS_WHEN_EMPTY, true);
        boolean plusVisible = showPlus && !(empty && hidePlusWhenEmpty);
        views.setViewVisibility(R.id.add_row, plusVisible ? View.VISIBLE : View.GONE);
        views.setViewVisibility(R.id.add_button, plusVisible ? View.VISIBLE : View.GONE);

        int base = lightText ? 255 : 0;
        int plusColor = Color.argb(
                alpha255FromTransparency(prefs.getInt(AppPrefs.KEY_PLUS_ALPHA, 45)),
                base, base, base
        );
        views.setTextViewTextSize(R.id.add_button, TypedValue.COMPLEX_UNIT_SP, prefs.getInt(AppPrefs.KEY_PLUS_SIZE, 17));
        views.setTextColor(R.id.add_button, plusColor);

        manager.updateAppWidget(appWidgetId, views);
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

    private static int alpha255FromTransparency(int transparency) {
        int clamped = Math.max(0, Math.min(100, transparency));
        return Math.max(0, Math.min(255, Math.round((100 - clamped) * 2.55f)));
    }

    private static float opacityFromTransparency(int transparency) {
        int clamped = Math.max(0, Math.min(100, transparency));
        return (100 - clamped) / 100f;
    }
}
