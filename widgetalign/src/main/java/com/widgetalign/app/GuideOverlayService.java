package com.widgetalign.app;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

public class GuideOverlayService extends Service {
    static final String ACTION_SHOW = "com.widgetalign.app.SHOW";
    static final String ACTION_HIDE = "com.widgetalign.app.HIDE";

    private WindowManager windowManager;
    private View guideView;

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : ACTION_SHOW;

        if (ACTION_HIDE.equals(action)) {
            removeGuide();
            stopSelf();
            return START_NOT_STICKY;
        }

        showGuide();
        return START_STICKY;
    }

    private void showGuide() {
        if (!Settings.canDrawOverlays(this) || windowManager == null) {
            stopSelf();
            return;
        }

        removeGuide();

        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        float nx = prefs.getFloat(MainActivity.KEY_X, 0.12f);
        float ny = prefs.getFloat(MainActivity.KEY_Y, 0.10f);
        float nw = prefs.getFloat(MainActivity.KEY_W, 0.76f);
        float nh = prefs.getFloat(MainActivity.KEY_H, 0.23f);

        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(metrics);

        int width = Math.max(1, Math.round(nw * metrics.widthPixels));
        int height = Math.max(1, Math.round(nh * metrics.heightPixels));
        int x = Math.round(nx * metrics.widthPixels);
        int y = Math.round(ny * metrics.heightPixels);

        guideView = new OverlayGuideView(this);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                width,
                height,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = x;
        params.y = y;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        windowManager.addView(guideView, params);
    }

    private void removeGuide() {
        if (guideView != null && windowManager != null) {
            try {
                windowManager.removeView(guideView);
            } catch (Throwable ignored) {
            }
            guideView = null;
        }
    }

    @Override
    public void onDestroy() {
        removeGuide();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private static final class OverlayGuideView extends View {
        private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint cross = new Paint(Paint.ANTI_ALIAS_FLAG);

        OverlayGuideView(android.content.Context context) {
            super(context);
            float density = getResources().getDisplayMetrics().density;

            border.setColor(Color.argb(235, 255, 255, 255));
            border.setStyle(Paint.Style.STROKE);
            border.setStrokeWidth(Math.max(1f, 1.5f * density));

            cross.setColor(Color.argb(125, 255, 255, 255));
            cross.setStyle(Paint.Style.STROKE);
            cross.setStrokeWidth(Math.max(1f, density));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float inset = border.getStrokeWidth() / 2f + 1f;
            float right = getWidth() - inset;
            float bottom = getHeight() - inset;

            canvas.drawRect(inset, inset, right, bottom, border);

            float tick = 14f * getResources().getDisplayMetrics().density;
            canvas.drawLine(inset, inset, inset + tick, inset, border);
            canvas.drawLine(inset, inset, inset, inset + tick, border);
            canvas.drawLine(right, inset, right - tick, inset, border);
            canvas.drawLine(right, inset, right, inset + tick, border);
            canvas.drawLine(inset, bottom, inset + tick, bottom, border);
            canvas.drawLine(inset, bottom, inset, bottom - tick, border);
            canvas.drawLine(right, bottom, right - tick, bottom, border);
            canvas.drawLine(right, bottom, right, bottom - tick, border);

            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float crossLen = 9f * getResources().getDisplayMetrics().density;
            canvas.drawLine(cx - crossLen, cy, cx + crossLen, cy, cross);
            canvas.drawLine(cx, cy - crossLen, cx, cy + crossLen, cross);
        }
    }
}
