package com.locktodo.app;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class LockTodoActivity extends Activity {
    public static final String EXTRA_PREVIEW = "preview";

    private FrameLayout root;
    private ScrollView scroller;
    private LinearLayout listContainer;
    private TextView plusButton;
    private EditText inputView;
    private TodoStore store;
    private SharedPreferences prefs;
    private boolean inputVisible;
    private boolean previewMode;
    private BroadcastReceiver stateReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        previewMode = getIntent().getBooleanExtra(EXTRA_PREVIEW, false);
        configureWindow();
        store = new TodoStore(this);
        prefs = AppPrefs.get(this);
        buildUi();
        renderTodos();
    }

    private void configureWindow() {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }

        Window window = getWindow();
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerStateReceiver();
    }

    @Override
    protected void onStop() {
        unregisterStateReceiver();
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!previewMode) {
            KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            if (km != null && !km.isKeyguardLocked()) {
                finish();
                return;
            }
        }
        if (root != null) {
            applyWindowGeometry();
            if (!inputVisible) renderTodos();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        previewMode = intent.getBooleanExtra(EXTRA_PREVIEW, false);
        renderTodos();
    }

    private void registerStateReceiver() {
        if (stateReceiver != null) return;
        stateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (Intent.ACTION_USER_PRESENT.equals(action) || Intent.ACTION_SCREEN_OFF.equals(action)) {
                    if (!previewMode) finish();
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(stateReceiver, filter);
    }

    private void unregisterStateReceiver() {
        if (stateReceiver != null) {
            try {
                unregisterReceiver(stateReceiver);
            } catch (Exception ignored) {
            }
            stateReceiver = null;
        }
    }

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.TRANSPARENT);
        attachAddGesture(root);
        setContentView(root);

        scroller = new ScrollView(this);
        scroller.setFillViewport(false);
        scroller.setClipToPadding(false);
        scroller.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroller.setBackgroundColor(Color.TRANSPARENT);
        attachAddGesture(scroller);

        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainer.setClipToPadding(false);
        listContainer.setBackgroundColor(Color.TRANSPARENT);
        attachAddGesture(listContainer);
        scroller.addView(listContainer, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout.LayoutParams scrollParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        int reserve = dp(42);
        scrollParams.bottomMargin = reserve;
        root.addView(scroller, scrollParams);

        plusButton = new TextView(this);
        plusButton.setText("+");
        plusButton.setGravity(Gravity.CENTER);
        plusButton.setContentDescription("할 일 추가");
        plusButton.setOnClickListener(v -> showAddInput());
        plusButton.setOnLongClickListener(v -> {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        });
        root.addView(plusButton);

        root.post(this::applyWindowGeometry);
    }

    private void attachAddGesture(View view) {
        view.setLongClickable(true);
        view.setOnLongClickListener(v -> {
            showAddInput();
            return true;
        });
    }

    private void applyWindowGeometry() {
        if (prefs == null) return;
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int screenWidth = dm.widthPixels;
        int screenHeight = dm.heightPixels;

        int widthPct = prefs.getInt(AppPrefs.KEY_PANEL_WIDTH, 88);
        int desiredWidth = Math.max(dp(120), Math.round(screenWidth * widthPct / 100f));
        desiredWidth = Math.min(screenWidth, desiredWidth);
        int desiredHeight = Math.max(dp(80), dp(prefs.getInt(AppPrefs.KEY_PANEL_HEIGHT, 300)));
        desiredHeight = Math.min(screenHeight, desiredHeight);

        int xPct = prefs.getInt(AppPrefs.KEY_PANEL_X, 50);
        int availableX = Math.max(0, screenWidth - desiredWidth);
        int x = Math.round(availableX * xPct / 100f);

        int y = dp(prefs.getInt(AppPrefs.KEY_TOP_OFFSET, 250));
        y = Math.max(0, Math.min(y, Math.max(0, screenHeight - desiredHeight)));

        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.width = desiredWidth;
        lp.height = desiredHeight;
        lp.x = x;
        lp.y = y;
        getWindow().setAttributes(lp);
    }

    private void renderTodos() {
        inputVisible = false;
        inputView = null;
        listContainer.removeAllViews();
        applyWindowGeometry();

        List<String> items = store.load();
        for (String text : items) {
            listContainer.addView(createTodoRow(text));
        }

        boolean showPlus = prefs.getBoolean(AppPrefs.KEY_SHOW_PLUS, true);
        boolean hideWhenEmpty = prefs.getBoolean(AppPrefs.KEY_HIDE_PLUS_WHEN_EMPTY, true);
        plusButton.setVisibility(showPlus && (!items.isEmpty() || !hideWhenEmpty) ? View.VISIBLE : View.GONE);
        stylePlus();
    }

    private void stylePlus() {
        int plusSize = prefs.getInt(AppPrefs.KEY_PLUS_SIZE, 18);
        plusButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, plusSize);
        plusButton.setTextColor(baseTextColor());
        plusButton.setAlpha(prefs.getInt(AppPrefs.KEY_PLUS_ALPHA, 24) / 100f);

        int touchSize = Math.max(dp(30), dp(plusSize + 12));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(touchSize, touchSize);
        lp.gravity = Gravity.END | Gravity.BOTTOM;
        lp.rightMargin = dp(prefs.getInt(AppPrefs.KEY_PLUS_END_MARGIN, 6));
        lp.bottomMargin = dp(prefs.getInt(AppPrefs.KEY_PLUS_BOTTOM_MARGIN, 4));
        plusButton.setLayoutParams(lp);
    }

    private View createTodoRow(String text) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int horizontalPadding = dp(prefs.getInt(AppPrefs.KEY_ROW_PADDING, 8));
        row.setPadding(horizontalPadding, 0, horizontalPadding, 0);
        row.setBackground(rowBackground());
        row.setTag(text);

        int rowHeight = dp(prefs.getInt(AppPrefs.KEY_ROW_HEIGHT, 44));
        int gap = dp(prefs.getInt(AppPrefs.KEY_ROW_GAP, 5));
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, rowHeight);
        rowParams.setMargins(0, 0, 0, gap);
        row.setLayoutParams(rowParams);

        TextView check = new TextView(this);
        check.setText("○");
        check.setGravity(Gravity.CENTER);
        check.setTextColor(textColor());
        check.setTextSize(TypedValue.COMPLEX_UNIT_SP, prefs.getInt(AppPrefs.KEY_CHECK_SIZE, 22));
        check.setAlpha(prefs.getInt(AppPrefs.KEY_CHECK_ALPHA, 88) / 100f);
        check.setContentDescription("완료");
        row.addView(check, new LinearLayout.LayoutParams(dp(34), ViewGroup.LayoutParams.MATCH_PARENT));

        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(textColor());
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, prefs.getInt(AppPrefs.KEY_TEXT_SIZE, 17));
        label.setGravity(Gravity.CENTER_VERTICAL);
        label.setMaxLines(1);
        label.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        labelParams.leftMargin = dp(2);
        row.addView(label, labelParams);

        TextView handle = new TextView(this);
        handle.setText("=");
        handle.setGravity(Gravity.CENTER);
        handle.setTextColor(baseTextColor());
        handle.setTextSize(TypedValue.COMPLEX_UNIT_SP, prefs.getInt(AppPrefs.KEY_HANDLE_SIZE, 18));
        handle.setAlpha(prefs.getInt(AppPrefs.KEY_HANDLE_ALPHA, 55) / 100f);
        handle.setContentDescription("길게 눌러 순서 변경");
        row.addView(handle, new LinearLayout.LayoutParams(dp(34), ViewGroup.LayoutParams.MATCH_PARENT));

        check.setOnClickListener(v -> completeRow(row, check));
        handle.setOnLongClickListener(v -> beginDrag(row));
        row.setOnDragListener(this::handleRowDrag);
        return row;
    }

    private void completeRow(View row, TextView check) {
        if (prefs.getBoolean(AppPrefs.KEY_HAPTIC, true)) {
            check.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
        check.setText("✓");
        int delay = prefs.getInt(AppPrefs.KEY_CHECK_DELAY, 170);
        row.animate()
                .alpha(0f)
                .translationX(dp(16))
                .setDuration(Math.max(80, delay))
                .withEndAction(() -> {
                    int index = listContainer.indexOfChild(row);
                    List<String> items = store.load();
                    if (index >= 0 && index < items.size()) {
                        items.remove(index);
                    } else {
                        items.remove(String.valueOf(row.getTag()));
                    }
                    store.save(items);
                    renderTodos();
                })
                .start();
    }

    private boolean beginDrag(View row) {
        if (prefs.getBoolean(AppPrefs.KEY_HAPTIC, true)) {
            row.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        }
        ClipData data = ClipData.newPlainText("todo", String.valueOf(row.getTag()));
        View.DragShadowBuilder shadow = new View.DragShadowBuilder(row);
        row.setAlpha(0.4f);
        boolean started;
        if (Build.VERSION.SDK_INT >= 24) {
            started = row.startDragAndDrop(data, shadow, row, 0);
        } else {
            started = row.startDrag(data, shadow, row, 0);
        }
        if (!started) row.setAlpha(1f);
        return started;
    }

    private boolean handleRowDrag(View target, DragEvent event) {
        View dragged = event.getLocalState() instanceof View ? (View) event.getLocalState() : null;
        if (dragged == null) return false;
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                return true;
            case DragEvent.ACTION_DRAG_ENTERED:
                if (target != dragged && target.getParent() == listContainer && dragged.getParent() == listContainer) {
                    int from = listContainer.indexOfChild(dragged);
                    int to = listContainer.indexOfChild(target);
                    if (from >= 0 && to >= 0 && from != to) {
                        listContainer.removeView(dragged);
                        int insertion = Math.min(to, listContainer.getChildCount());
                        listContainer.addView(dragged, insertion);
                    }
                }
                return true;
            case DragEvent.ACTION_DROP:
                saveOrderFromViews();
                return true;
            case DragEvent.ACTION_DRAG_ENDED:
                dragged.setAlpha(1f);
                saveOrderFromViews();
                return true;
            default:
                return true;
        }
    }

    private void saveOrderFromViews() {
        ArrayList<String> ordered = new ArrayList<>();
        for (int i = 0; i < listContainer.getChildCount(); i++) {
            Object tag = listContainer.getChildAt(i).getTag();
            if (tag instanceof String) ordered.add((String) tag);
        }
        if (!ordered.isEmpty() || store.load().isEmpty()) store.save(ordered);
    }

    private void showAddInput() {
        if (inputVisible) return;
        inputVisible = true;
        plusButton.setVisibility(View.GONE);

        inputView = new EditText(this);
        inputView.setSingleLine(true);
        inputView.setHint("새 할 일");
        inputView.setHintTextColor(withAlpha(baseTextColor(), 38));
        inputView.setTextColor(textColor());
        inputView.setTextSize(TypedValue.COMPLEX_UNIT_SP, prefs.getInt(AppPrefs.KEY_TEXT_SIZE, 17));
        inputView.setPadding(dp(12), 0, dp(12), 0);
        inputView.setBackground(rowBackground());
        inputView.setFilters(new InputFilter[]{new InputFilter.LengthFilter(120)});
        inputView.setImeOptions(EditorInfo.IME_ACTION_DONE);
        inputView.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                commitInput();
                return true;
            }
            return false;
        });

        int rowHeight = dp(prefs.getInt(AppPrefs.KEY_ROW_HEIGHT, 44));
        int gap = dp(prefs.getInt(AppPrefs.KEY_ROW_GAP, 5));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, rowHeight);
        params.setMargins(0, 0, 0, gap);
        listContainer.addView(inputView, 0, params);
        inputView.requestFocus();
        inputView.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(inputView, InputMethodManager.SHOW_IMPLICIT);
        }, 120);
    }

    private void commitInput() {
        if (!inputVisible || inputView == null) return;
        String value = inputView.getText().toString().trim();
        if (!value.isEmpty()) store.add(value);
        hideKeyboard();
        renderTodos();
    }

    private void cancelInput() {
        if (!inputVisible) return;
        hideKeyboard();
        renderTodos();
    }

    private void hideKeyboard() {
        if (inputView == null) return;
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(inputView.getWindowToken(), 0);
    }

    @Override
    public void onBackPressed() {
        if (inputVisible) cancelInput();
        else finish();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
            if (inputVisible) cancelInput();
            else if (previewMode) finish();
            return true;
        }
        return super.onTouchEvent(event);
    }

    private GradientDrawable rowBackground() {
        GradientDrawable drawable = new GradientDrawable();
        boolean light = prefs.getBoolean(AppPrefs.KEY_LIGHT_TEXT, true);
        int fillAlpha = prefs.getInt(AppPrefs.KEY_FILL_ALPHA, 7);
        int fillBase = light ? Color.BLACK : Color.WHITE;
        drawable.setColor(withAlpha(fillBase, fillAlpha));
        drawable.setCornerRadius(dp(prefs.getInt(AppPrefs.KEY_CORNER_RADIUS, 14)));

        int borderWidth = prefs.getInt(AppPrefs.KEY_BORDER_WIDTH, 1);
        if (borderWidth > 0) {
            int borderAlpha = prefs.getInt(AppPrefs.KEY_BORDER_ALPHA, 35);
            drawable.setStroke(dp(borderWidth), withAlpha(baseTextColor(), borderAlpha));
        }
        return drawable;
    }

    private int baseTextColor() {
        return prefs.getBoolean(AppPrefs.KEY_LIGHT_TEXT, true) ? Color.WHITE : Color.BLACK;
    }

    private int textColor() {
        return withAlpha(baseTextColor(), prefs.getInt(AppPrefs.KEY_TEXT_ALPHA, 92));
    }

    private int withAlpha(int color, int percent) {
        int alpha = Math.round(255f * Math.max(0, Math.min(100, percent)) / 100f);
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }
}
