package com.widgetalign.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

final class AlignCanvasView extends View {
    private static final int MODE_NONE = 0;
    private static final int MODE_MOVE = 1;
    private static final int MODE_RESIZE = 2;

    private final Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF imageRect = new RectF();
    private final RectF normalizedRect = new RectF(0.12f, 0.10f, 0.88f, 0.33f);
    private final RectF guideRect = new RectF();

    private Bitmap bitmap;
    private int gestureMode = MODE_NONE;
    private float lastX;
    private float lastY;

    AlignCanvasView(Context context) {
        super(context);
        setBackgroundColor(Color.rgb(8, 8, 8));

        borderPaint.setColor(Color.WHITE);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(dp(1.5f));

        shadePaint.setColor(Color.argb(92, 0, 0, 0));
        shadePaint.setStyle(Paint.Style.FILL);

        handlePaint.setColor(Color.WHITE);
        handlePaint.setStyle(Paint.Style.FILL);

        setClickable(true);
    }

    void setBitmap(Bitmap bitmap) {
        if (this.bitmap != null && this.bitmap != bitmap) {
            this.bitmap.recycle();
        }
        this.bitmap = bitmap;
        invalidate();
    }

    boolean hasImage() {
        return bitmap != null && !bitmap.isRecycled();
    }

    void setNormalizedRect(float x, float y, float w, float h) {
        w = clamp(w, 0.08f, 1f);
        h = clamp(h, 0.06f, 1f);
        x = clamp(x, 0f, 1f - w);
        y = clamp(y, 0f, 1f - h);
        normalizedRect.set(x, y, x + w, y + h);
        invalidate();
    }

    RectF getNormalizedRect() {
        return new RectF(normalizedRect);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (!hasImage()) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(Color.argb(120, 255, 255, 255));
            p.setTextSize(dp(15));
            p.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("잠금화면 전체 캡처를 선택하세요", getWidth() / 2f, getHeight() / 2f, p);
            return;
        }

        calculateImageRect();
        canvas.drawBitmap(bitmap, null, imageRect, imagePaint);
        calculateGuideRect();

        // Slightly dim everything outside the guide so the target is easy to match.
        canvas.drawRect(imageRect.left, imageRect.top, imageRect.right, guideRect.top, shadePaint);
        canvas.drawRect(imageRect.left, guideRect.bottom, imageRect.right, imageRect.bottom, shadePaint);
        canvas.drawRect(imageRect.left, guideRect.top, guideRect.left, guideRect.bottom, shadePaint);
        canvas.drawRect(guideRect.right, guideRect.top, imageRect.right, guideRect.bottom, shadePaint);

        canvas.drawRect(guideRect, borderPaint);

        float tick = dp(13);
        drawCorner(canvas, guideRect.left, guideRect.top, tick, 1, 1);
        drawCorner(canvas, guideRect.right, guideRect.top, tick, -1, 1);
        drawCorner(canvas, guideRect.left, guideRect.bottom, tick, 1, -1);
        drawCorner(canvas, guideRect.right, guideRect.bottom, tick, -1, -1);

        canvas.drawCircle(guideRect.right, guideRect.bottom, dp(5), handlePaint);
    }

    private void drawCorner(Canvas canvas, float x, float y, float len, int dx, int dy) {
        canvas.drawLine(x, y, x + dx * len, y, borderPaint);
        canvas.drawLine(x, y, x, y + dy * len, borderPaint);
    }

    private void calculateImageRect() {
        float viewW = getWidth();
        float viewH = getHeight();
        float imageW = bitmap.getWidth();
        float imageH = bitmap.getHeight();

        float scale = Math.min(viewW / imageW, viewH / imageH);
        float w = imageW * scale;
        float h = imageH * scale;
        float left = (viewW - w) / 2f;
        float top = (viewH - h) / 2f;
        imageRect.set(left, top, left + w, top + h);
    }

    private void calculateGuideRect() {
        guideRect.set(
                imageRect.left + normalizedRect.left * imageRect.width(),
                imageRect.top + normalizedRect.top * imageRect.height(),
                imageRect.left + normalizedRect.right * imageRect.width(),
                imageRect.top + normalizedRect.bottom * imageRect.height()
        );
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!hasImage()) return true;

        calculateImageRect();
        calculateGuideRect();

        float x = event.getX();
        float y = event.getY();

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                float handleRadius = dp(32);
                float dx = x - guideRect.right;
                float dy = y - guideRect.bottom;
                if (dx * dx + dy * dy <= handleRadius * handleRadius) {
                    gestureMode = MODE_RESIZE;
                } else if (guideRect.contains(x, y)) {
                    gestureMode = MODE_MOVE;
                } else if (imageRect.contains(x, y)) {
                    float nw = normalizedRect.width();
                    float nh = normalizedRect.height();
                    float nx = (x - imageRect.left) / imageRect.width() - nw / 2f;
                    float ny = (y - imageRect.top) / imageRect.height() - nh / 2f;
                    setNormalizedRect(nx, ny, nw, nh);
                    gestureMode = MODE_MOVE;
                } else {
                    gestureMode = MODE_NONE;
                }
                lastX = x;
                lastY = y;
                return true;

            case MotionEvent.ACTION_MOVE:
                if (gestureMode == MODE_MOVE) {
                    float ndx = (x - lastX) / imageRect.width();
                    float ndy = (y - lastY) / imageRect.height();
                    setNormalizedRect(
                            normalizedRect.left + ndx,
                            normalizedRect.top + ndy,
                            normalizedRect.width(),
                            normalizedRect.height()
                    );
                } else if (gestureMode == MODE_RESIZE) {
                    float right = clamp((x - imageRect.left) / imageRect.width(),
                            normalizedRect.left + 0.08f, 1f);
                    float bottom = clamp((y - imageRect.top) / imageRect.height(),
                            normalizedRect.top + 0.06f, 1f);
                    normalizedRect.right = right;
                    normalizedRect.bottom = bottom;
                    invalidate();
                }
                lastX = x;
                lastY = y;
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                gestureMode = MODE_NONE;
                return true;
        }

        return true;
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
