package com.daycount.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.RemoteViews;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class DayCountWidget extends AppWidgetProvider {
    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        for (int id : appWidgetIds) updateWidget(context, manager, id);
    }

    @Override
    public void onAppWidgetOptionsChanged(
            Context context,
            AppWidgetManager appWidgetManager,
            int appWidgetId,
            Bundle newOptions
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions);
        updateWidget(context, appWidgetManager, appWidgetId);
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

        int textSizeSp = WidgetPrefs.textSize(context);
        int textColor = WidgetPrefs.textColor(context);

        views.setTextViewText(R.id.day_count_text, text);
        views.setTextViewTextSize(
                R.id.day_count_text,
                TypedValue.COMPLEX_UNIT_SP,
                textSizeSp
        );
        views.setTextColor(R.id.day_count_text, textColor);
        views.setInt(R.id.day_count_text, "setGravity", Gravity.TOP | Gravity.START);

        applyFreePosition(
                context,
                manager,
                appWidgetId,
                views,
                text,
                textSizeSp,
                WidgetPrefs.positionX(context),
                WidgetPrefs.positionY(context)
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

    private static void applyFreePosition(
            Context context,
            AppWidgetManager manager,
            int appWidgetId,
            RemoteViews views,
            String text,
            int textSizeSp,
            float x,
            float y
    ) {
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        Bundle options = manager.getAppWidgetOptions(appWidgetId);

        int widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 120);
        int heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 60);

        int contentWidthPx = Math.max(1, Math.round(widthDp * dm.density) - Math.round(8f * dm.density));
        int contentHeightPx = Math.max(1, Math.round(heightDp * dm.density) - Math.round(8f * dm.density));

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        paint.setTextSize(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                textSizeSp,
                dm
        ));

        float textWidth = paint.measureText(text);
        Paint.FontMetrics fm = paint.getFontMetrics();
        float textHeight = fm.descent - fm.ascent;

        int maxLeft = Math.max(0, contentWidthPx - Math.round(textWidth));
        int maxTop = Math.max(0, contentHeightPx - Math.round(textHeight));

        int left = Math.round(clamp01(x) * maxLeft);
        int top = Math.round(clamp01(y) * maxTop);

        views.setViewPadding(R.id.day_count_text, left, top, 0, 0);
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
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
