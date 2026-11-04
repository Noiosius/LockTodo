package com.locktodo.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.appwidget.AppWidgetManager;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import java.util.ArrayList;
import java.util.List;

public class TodoRemoteViewsService extends RemoteViewsService {
    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new Factory(getApplicationContext(), intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID));
    }

    private static final class Factory implements RemoteViewsFactory {
        private final Context context;
        private final int appWidgetId;
        private List<String> items = new ArrayList<>();
        private List<Long> reminders = new ArrayList<>();
        private SharedPreferences prefs;

        Factory(Context context, int appWidgetId) {
            this.context = context;
            this.appWidgetId = appWidgetId;
        }

        @Override public void onCreate() {
            onDataSetChanged();
        }

        @Override public void onDataSetChanged() {
            TodoStore store = new TodoStore(context);
            items = store.load();
            reminders = store.loadReminders();
            prefs = AppPrefs.get(context);
        }

        @Override public void onDestroy() {
            items = new ArrayList<>();
            reminders = new ArrayList<>();
        }

        @Override public int getCount() {
            return items.size();
        }

        @Override public RemoteViews getViewAt(int position) {
            if (position < 0 || position >= items.size()) return null;

            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_todo_list_row);
            boolean lightText = prefs.getBoolean(AppPrefs.KEY_LIGHT_TEXT, true);
            int base = lightText ? 255 : 0;
            int textSize = prefs.getInt(AppPrefs.KEY_TEXT_SIZE, 16);
            int rowPadding = prefs.getInt(AppPrefs.KEY_ROW_PADDING, 5);
            int checkingIndex = prefs.getInt(AppPrefs.KEY_CHECKING_INDEX, -1);

            int textColor = Color.argb(alpha255FromTransparency(prefs.getInt(AppPrefs.KEY_TEXT_ALPHA, 0)), base, base, base);
            int checkColor = Color.argb(alpha255FromTransparency(prefs.getInt(AppPrefs.KEY_CHECK_ALPHA, 10)), base, base, base);
            int handleColor = Color.argb(alpha255FromTransparency(prefs.getInt(AppPrefs.KEY_HANDLE_ALPHA, 45)), base, base, base);
            int clockColor = Color.argb(Math.max(42, (int) (Color.alpha(handleColor) * 0.72f)), base, base, base);

            views.setTextViewText(R.id.todo_text, items.get(position));
            views.setTextViewText(R.id.todo_check, position == checkingIndex ? "●" : "○");
            views.setTextViewText(R.id.todo_clock, "◷");
            views.setTextViewText(R.id.todo_handle, "≡");
            views.setTextViewText(R.id.todo_read_more, "↔");
            views.setTextColor(R.id.todo_text, textColor);
            views.setTextColor(R.id.todo_check, checkColor);
            views.setTextColor(R.id.todo_clock, clockColor);
            views.setTextColor(R.id.todo_handle, handleColor);
            views.setTextColor(R.id.todo_read_more, handleColor);
            views.setTextViewTextSize(R.id.todo_text, TypedValue.COMPLEX_UNIT_SP, textSize);
            views.setTextViewTextSize(R.id.todo_check, TypedValue.COMPLEX_UNIT_SP, Math.max(14, textSize + 2));
            views.setTextViewTextSize(R.id.todo_clock, TypedValue.COMPLEX_UNIT_SP, Math.max(20, textSize + 4));
            views.setTextViewTextSize(R.id.todo_handle, TypedValue.COMPLEX_UNIT_SP, Math.max(15, textSize + 1));
            views.setTextViewTextSize(R.id.todo_read_more, TypedValue.COMPLEX_UNIT_SP, Math.max(16, textSize));
            views.setViewPadding(R.id.todo_row_root, 0, dp(rowPadding), 0, dp(rowPadding));

            long reminderAt = position < reminders.size() ? reminders.get(position) : 0L;
            boolean clockVisible = reminderAt > System.currentTimeMillis();
            views.setViewVisibility(R.id.todo_clock, clockVisible ? View.VISIBLE : View.GONE);
            boolean overflows = isTooLong(items.get(position), textSize, clockVisible);
            views.setViewVisibility(R.id.todo_read_more, overflows ? View.VISIBLE : View.GONE);

            Intent add = new Intent();
            add.setAction(LockTodoWidget.ACTION_ADD);
            views.setOnClickFillInIntent(R.id.todo_row_root, add);

            Intent edit = new Intent();
            edit.setAction(LockTodoWidget.ACTION_EDIT);
            edit.putExtra(LockTodoWidget.EXTRA_INDEX, position);
            views.setOnClickFillInIntent(R.id.todo_text, edit);

            Intent read = new Intent();
            read.setAction(LockTodoWidget.ACTION_READ_TEXT);
            read.putExtra(LockTodoWidget.EXTRA_INDEX, position);
            views.setOnClickFillInIntent(R.id.todo_read_more, read);

            Intent complete = new Intent();
            complete.setAction(LockTodoWidget.ACTION_COMPLETE);
            complete.putExtra(LockTodoWidget.EXTRA_INDEX, position);
            views.setOnClickFillInIntent(R.id.todo_check, complete);

            Intent reorder = new Intent();
            reorder.setAction(LockTodoWidget.ACTION_REORDER);
            views.setOnClickFillInIntent(R.id.todo_handle, reorder);
            views.setOnClickFillInIntent(R.id.todo_clock, reorder);

            return views;
        }

        @Override public RemoteViews getLoadingView() {
            return null;
        }

        @Override public int getViewTypeCount() {
            return 1;
        }

        @Override public long getItemId(int position) {
            return position;
        }

        @Override public boolean hasStableIds() {
            return true;
        }

        private boolean isTooLong(String text, int textSizeSp, boolean clockVisible) {
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            paint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, textSizeSp,
                    context.getResources().getDisplayMetrics()));

            // The launcher owns widget sizing; use its last known available
            // width and reserve the checkbox, reorder handle and optional clock.
            int widthDp = 280;
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                Bundle options = AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId);
                if (options != null) {
                    widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp);
                }
            }
            int paddingDp = prefs.getInt(AppPrefs.KEY_HORIZONTAL_PADDING, 8);
            float availablePx = dp(Math.max(42, widthDp - paddingDp * 2
                    - 34 /* checkbox */ - 34 /* reorder */
                    - (clockVisible ? 34 : 0) - 16 /* text padding and safety */));
            return paint.measureText(text) > availablePx;
        }

        private int dp(int value) {
            return Math.round(value * context.getResources().getDisplayMetrics().density);
        }

        private int alpha255FromTransparency(int transparency) {
            int clamped = Math.max(0, Math.min(100, transparency));
            return Math.max(0, Math.min(255, Math.round((100 - clamped) * 2.55f)));
        }
    }
}
