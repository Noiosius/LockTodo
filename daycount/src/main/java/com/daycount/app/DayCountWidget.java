package com.daycount.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.TypedValue;
import android.widget.RemoteViews;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class DayCountWidget extends AppWidgetProvider {
    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        for (int id : appWidgetIds) updateWidget(context, manager, id);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent.getAction();
        if (Intent.ACTION_DATE_CHANGED.equals(action)
                || Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)) {
            updateAll(context);
        }
    }

    static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName component = new ComponentName(context, DayCountWidget.class);
        for (int id : manager.getAppWidgetIds(component)) updateWidget(context, manager, id);
    }

    private static void updateWidget(Context context, AppWidgetManager manager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_day_count);
        EventStore.Event selected = new EventStore(context).getSelected();
        String text = selected == null ? "—" : formatCount(selected.date);

        views.setTextViewText(R.id.day_count_text, text);
        views.setTextViewTextSize(
                R.id.day_count_text,
                TypedValue.COMPLEX_UNIT_SP,
                WidgetPrefs.textSize(context)
        );
        views.setTextColor(R.id.day_count_text, WidgetPrefs.textColor(context));
        views.setInt(
                R.id.day_count_text,
                "setGravity",
                WidgetPrefs.gravityForPosition(WidgetPrefs.position(context))
        );

        Intent open = new Intent(context, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pending = PendingIntent.getActivity(
                context,
                710000 + appWidgetId,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        views.setOnClickPendingIntent(R.id.widget_root, pending);
        views.setOnClickPendingIntent(R.id.day_count_text, pending);
        manager.updateAppWidget(appWidgetId, views);
    }

    static String formatCount(LocalDate eventDate) {
        LocalDate today = LocalDate.now();
        if (!eventDate.isAfter(today)) {
            long days = ChronoUnit.DAYS.between(eventDate, today) + 1L;
            return Long.toString(days);
        }
        long days = ChronoUnit.DAYS.between(today, eventDate);
        return "D-" + days;
    }
}
