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
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
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

        guideView = new OverlayGuideView(this);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
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
        params.x = 0;
        params.y = 0;

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

            SharedPreferences prefs = getContext().getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
            float nx = prefs.getFloat(MainActivity.KEY_X, 0.12f);
            float ny = prefs.getFloat(MainActivity.KEY_Y, 0.10f);
            float nw = prefs.getFloat(MainActivity.KEY_W, 0.76f);
            float nh = prefs.getFloat(MainActivity.KEY_H, 0.23f);
            float scale = prefs.getInt(MainActivity.KEY_SCALE, 100) / 100f;

            // Draw inside the overlay's actual measured bounds instead of sizing a separate
            // overlay window from getRealMetrics(). Samsung/One UI can use slightly different
            // coordinate bounds for application overlays; using this canvas removes that mismatch.
            float cx = (nx + nw / 2f) * getWidth();
            float cy = (ny + nh / 2f) * getHeight();
            float width = nw * getWidth() * scale;
            float height = nh * getHeight() * scale;

            float left = cx - width / 2f;
            float top = cy - height / 2f;
            float right = cx + width / 2f;
            float bottom = cy + height / 2f;

            float inset = border.getStrokeWidth() / 2f + 1f;
            left = Math.max(inset, left);
            top = Math.max(inset, top);
            right = Math.min(getWidth() - inset, right);
            bottom = Math.min(getHeight() - inset, bottom);

            canvas.drawRect(left, top, right, bottom, border);

            float density = getResources().getDisplayMetrics().density;
            float tick = 14f * density;
            canvas.drawLine(left, top, left + tick, top, border);
            canvas.drawLine(left, top, left, top + tick, border);
            canvas.drawLine(right, top, right - tick, top, border);
            canvas.drawLine(right, top, right, top + tick, border);
            canvas.drawLine(left, bottom, left + tick, bottom, border);
            canvas.drawLine(left, bottom, left, bottom - tick, border);
            canvas.drawLine(right, bottom, right - tick, bottom, border);
            canvas.drawLine(right, bottom, right, bottom - tick, border);

            float centerX = (left + right) / 2f;
            float centerY = (top + bottom) / 2f;
            float crossLen = 9f * density;
            canvas.drawLine(centerX - crossLen, centerY, centerX + crossLen, centerY, cross);
            canvas.drawLine(centerX, centerY - crossLen, centerX, centerY + crossLen, cross);
        }
    }
}
