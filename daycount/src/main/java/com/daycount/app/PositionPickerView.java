package com.daycount.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

final class PositionPickerView extends View {
    interface OnPositionChangedListener {
        void onPositionChanged(float x, float y);
    }

    private final Paint panelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF panel = new RectF();

    private float x = 0.5f;
    private float y = 0.5f;
    private int previewTextSizeSp = 44;
    private int previewColor = Color.WHITE;
    private OnPositionChangedListener listener;

    PositionPickerView(Context context) {
        super(context);
        setClickable(true);

        panelPaint.setColor(Color.rgb(22, 22, 22));
        panelPaint.setStyle(Paint.Style.FILL);

        borderPaint.setColor(Color.rgb(65, 65, 65));
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(dp(1));

        hintPaint.setColor(Color.rgb(95, 95, 95));
        hintPaint.setTextSize(dp(10));
        hintPaint.setTextAlign(Paint.Align.CENTER);
    }

    void setPosition(float x, float y) {
        this.x = clamp01(x);
        this.y = clamp01(y);
        invalidate();
    }

    float getPositionX() {
        return x;
    }

    float getPositionY() {
        return y;
    }

    void setPreviewStyle(int textSizeSp, int color) {
        this.previewTextSizeSp = textSizeSp;
        this.previewColor = color;
        invalidate();
    }

    void setOnPositionChangedListener(OnPositionChangedListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float pad = dp(8);
        panel.set(pad, pad, getWidth() - pad, getHeight() - pad);
        float radius = dp(18);
        canvas.drawRoundRect(panel, radius, radius, panelPaint);
        canvas.drawRoundRect(panel, radius, radius, borderPaint);

        textPaint.setColor(previewColor);
        textPaint.setTextSize(sp(previewTextSizeSp));
        textPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        textPaint.setTextAlign(Paint.Align.LEFT);

        String sample = "123";
        float textWidth = textPaint.measureText(sample);
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float textHeight = fm.descent - fm.ascent;

        float innerPad = dp(10);
        float leftBound = panel.left + innerPad;
        float topBound = panel.top + innerPad;
        float rightBound = panel.right - innerPad;
        float bottomBound = panel.bottom - innerPad;

        float maxLeft = Math.max(leftBound, rightBound - textWidth);
        float maxTop = Math.max(topBound, bottomBound - textHeight);

        float left = leftBound + (maxLeft - leftBound) * x;
        float top = topBound + (maxTop - topBound) * y;
        float baseline = top - fm.ascent;

        canvas.drawText(sample, left, baseline, textPaint);

        if (getHeight() > dp(120)) {
            canvas.drawText("숫자를 끌어서 위치 조정", getWidth() / 2f, getHeight() - dp(12), hintPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                updateFromTouch(event.getX(), event.getY());
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                performClick();
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private void updateFromTouch(float touchX, float touchY) {
        float pad = dp(8);
        panel.set(pad, pad, getWidth() - pad, getHeight() - pad);

        textPaint.setTextSize(sp(previewTextSizeSp));
        textPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));

        String sample = "123";
        float textWidth = textPaint.measureText(sample);
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float textHeight = fm.descent - fm.ascent;

        float innerPad = dp(10);
        float leftBound = panel.left + innerPad;
        float topBound = panel.top + innerPad;
        float rightBound = panel.right - innerPad;
        float bottomBound = panel.bottom - innerPad;

        float maxLeft = Math.max(leftBound, rightBound - textWidth);
        float maxTop = Math.max(topBound, bottomBound - textHeight);

        float targetLeft = touchX - textWidth / 2f;
        float targetTop = touchY - textHeight / 2f;

        x = maxLeft <= leftBound ? 0f : clamp01((targetLeft - leftBound) / (maxLeft - leftBound));
        y = maxTop <= topBound ? 0f : clamp01((targetTop - topBound) / (maxTop - topBound));

        invalidate();
        if (listener != null) listener.onPositionChanged(x, y);
    }

    private float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }
}
