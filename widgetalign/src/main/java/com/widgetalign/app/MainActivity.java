package com.widgetalign.app;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;

public class MainActivity extends Activity {
    private static final int REQ_IMAGE = 1001;
    private static final int REQ_OVERLAY = 1002;

    static final String PREFS = "widget_align";
    static final String KEY_IMAGE_URI = "image_uri";
    static final String KEY_X = "x";
    static final String KEY_Y = "y";
    static final String KEY_W = "w";
    static final String KEY_H = "h";
    static final String KEY_SCALE = "scale";

    private SharedPreferences prefs;
    private AlignCanvasView canvas;
    private boolean pendingStartGuide = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        setContentView(buildUi());
        restoreGuideRect();
        restoreImage();
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(dp(18), dp(16), dp(18), dp(10));

        TextView title = new TextView(this);
        title.setText("Widget Align");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        top.addView(title, matchWrap());

        TextView guide = new TextView(this);
        guide.setText("잠금화면 전체 캡처를 불러온 뒤 사각형을 시계에 맞추세요.\n사각형 안쪽: 이동 · 오른쪽 아래 점: 크기 조절 · 아래 슬라이더: 실제 화면 보정");
        guide.setTextColor(Color.argb(155, 255, 255, 255));
        guide.setTextSize(12);
        guide.setLineSpacing(0, 1.15f);
        guide.setPadding(0, dp(5), 0, 0);
        top.addView(guide, matchWrap());

        root.addView(top, matchWrap());

        canvas = new AlignCanvasView(this);
        root.addView(canvas, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout scaleRow = new LinearLayout(this);
        scaleRow.setOrientation(LinearLayout.VERTICAL);
        scaleRow.setPadding(dp(18), dp(8), dp(18), dp(2));

        LinearLayout scaleHeader = new LinearLayout(this);
        scaleHeader.setGravity(Gravity.CENTER_VERTICAL);

        TextView scaleLabel = new TextView(this);
        scaleLabel.setText("가이드 크기 보정");
        scaleLabel.setTextColor(Color.argb(220, 255, 255, 255));
        scaleLabel.setTextSize(13);

        TextView scaleValue = new TextView(this);
        scaleValue.setTextColor(Color.argb(165, 255, 255, 255));
        scaleValue.setTextSize(12);
        scaleValue.setGravity(Gravity.END);

        scaleHeader.addView(scaleLabel, new LinearLayout.LayoutParams(0, dp(28), 1f));
        scaleHeader.addView(scaleValue, new LinearLayout.LayoutParams(dp(72), dp(28)));
        scaleRow.addView(scaleHeader);

        SeekBar scaleBar = new SeekBar(this);
        scaleBar.setMax(70); // 80% ~ 150%
        int currentScale = prefs.getInt(KEY_SCALE, 100);
        currentScale = Math.max(80, Math.min(150, currentScale));
        scaleBar.setProgress(currentScale - 80);
        scaleValue.setText(currentScale + "%");
        scaleBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = 80 + progress;
                scaleValue.setText(value + "%");
                prefs.edit().putInt(KEY_SCALE, value).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        scaleRow.addView(scaleBar, matchWrap());
        root.addView(scaleRow, matchWrap());

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER);
        buttons.setPadding(dp(10), dp(10), dp(10), dp(14));

        Button choose = button("캡처 선택");
        choose.setOnClickListener(v -> chooseImage());
        buttons.addView(choose, weighted());

        Button show = button("가이드 켜기");
        show.setOnClickListener(v -> requestShowGuide());
        buttons.addView(show, weighted());

        Button hide = button("가이드 끄기");
        hide.setOnClickListener(v -> {
            stopService(new Intent(this, GuideOverlayService.class));
            Toast.makeText(this, "가이드를 껐습니다", Toast.LENGTH_SHORT).show();
        });
        buttons.addView(hide, weighted());

        root.addView(buttons, matchWrap());
        return root;
    }

    private void chooseImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQ_IMAGE);
    }

    private void requestShowGuide() {
        if (!canvas.hasImage()) {
            Toast.makeText(this, "먼저 잠금화면 캡처를 선택해 주세요", Toast.LENGTH_SHORT).show();
            return;
        }

        saveGuideRect();

        if (!Settings.canDrawOverlays(this)) {
            pendingStartGuide = true;
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())
            );
            startActivityForResult(intent, REQ_OVERLAY);
            return;
        }

        startGuideAndGoHome();
    }

    private void startGuideAndGoHome() {
        saveGuideRect();

        Intent service = new Intent(this, GuideOverlayService.class);
        service.setAction(GuideOverlayService.ACTION_SHOW);
        startService(service);

        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(home);
    }

    private void saveGuideRect() {
        RectF n = canvas.getNormalizedRect();
        prefs.edit()
                .putFloat(KEY_X, n.left)
                .putFloat(KEY_Y, n.top)
                .putFloat(KEY_W, n.width())
                .putFloat(KEY_H, n.height())
                .apply();
    }

    private void restoreGuideRect() {
        float x = prefs.getFloat(KEY_X, 0.12f);
        float y = prefs.getFloat(KEY_Y, 0.10f);
        float w = prefs.getFloat(KEY_W, 0.76f);
        float h = prefs.getFloat(KEY_H, 0.23f);
        canvas.setNormalizedRect(x, y, w, h);
    }

    private void restoreImage() {
        String saved = prefs.getString(KEY_IMAGE_URI, null);
        if (saved == null) return;
        loadImage(Uri.parse(saved));
    }

    private void loadImage(Uri uri) {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            Bitmap bitmap = BitmapFactory.decodeStream(in);
            if (bitmap == null) throw new IllegalStateException("decode failed");
            canvas.setBitmap(bitmap);
        } catch (Throwable e) {
            Toast.makeText(this, "이미지를 불러오지 못했습니다", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (pendingStartGuide && Settings.canDrawOverlays(this)) {
            pendingStartGuide = false;
            startGuideAndGoHome();
        }
    }

    @Override
    protected void onPause() {
        saveGuideRect();
        super.onPause();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQ_IMAGE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                );
            } catch (Throwable ignored) {
            }
            prefs.edit().putString(KEY_IMAGE_URI, uri.toString()).apply();
            loadImage(uri);
            return;
        }

        if (requestCode == REQ_OVERLAY && Settings.canDrawOverlays(this)) {
            pendingStartGuide = false;
            startGuideAndGoHome();
        }
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(13);
        return button;
    }

    private LinearLayout.LayoutParams weighted() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(48), 1f);
        p.setMargins(dp(3), 0, dp(3), 0);
        return p;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
