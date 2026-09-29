package com.alharam.inviter;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Locale;

public class StepperView extends LinearLayout {
    private final TextView valueView;
    private final TextView upView;
    private final TextView downView;
    private final Handler repeatHandler = new Handler(Looper.getMainLooper());
    private int min = 0, max = 0, value = -1;
    private boolean blankAllowed = false;
    private String[] textValues = null;
    private int textIndex = -1;
    private long pressStart;
    private boolean repeated;
    private int repeatCount;
    private Runnable repeatRunnable;

    public StepperView(Context context) { this(context, null); }

    public StepperView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setLayoutDirection(LAYOUT_DIRECTION_LTR);
        setBackgroundResource(R.drawable.bg_input);
        setPadding(dp(4), dp(3), dp(4), dp(3));
        setMinimumHeight(dp(48));

        LinearLayout arrows = new LinearLayout(context);
        arrows.setOrientation(VERTICAL);
        arrows.setGravity(Gravity.CENTER);
        arrows.setLayoutDirection(LAYOUT_DIRECTION_LTR);
        arrows.setLayoutParams(new LayoutParams(dp(44), LayoutParams.MATCH_PARENT));

        upView = arrow("▲");
        downView = arrow("▼");
        arrows.addView(upView, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));
        arrows.addView(downView, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));

        valueView = new TextView(context);
        valueView.setGravity(Gravity.CENTER);
        valueView.setTextColor(Color.parseColor("#172033"));
        valueView.setTextSize(15);
        valueView.setTypeface(null, Typeface.BOLD);
        valueView.setSingleLine(true);
        addView(arrows);
        addView(valueView, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1));

        attachHold(upView, true);
        attachHold(downView, false);
        refresh();
    }

    private TextView arrow(String t) {
        TextView v = new TextView(getContext());
        v.setText(t);
        v.setTextSize(15);
        v.setTextColor(Color.parseColor("#1769D1"));
        v.setGravity(Gravity.CENTER);
        v.setTypeface(null, Typeface.BOLD);
        v.setClickable(true);
        v.setFocusable(true);
        v.setPadding(0, 0, 0, 0);
        return v;
    }

    private void attachHold(View v, boolean up) {
        v.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    pressStart = System.currentTimeMillis();
                    repeated = false;
                    repeatCount = 0;
                    step(up);
                    repeatRunnable = new Runnable() {
                        @Override public void run() {
                            if (!view.isPressed()) return;
                            repeated = true;
                            repeatCount++;
                            step(up);
                            long elapsed = System.currentTimeMillis() - pressStart;
                            long next = elapsed > 3000 ? 45 : elapsed > 1800 ? 65 : elapsed > 900 ? 95 : 145;
                            repeatHandler.postDelayed(this, next);
                        }
                    };
                    view.setPressed(true);
                    repeatHandler.postDelayed(repeatRunnable, 450);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.setPressed(false);
                    if (repeatRunnable != null) repeatHandler.removeCallbacks(repeatRunnable);
                    repeatRunnable = null;
                    return true;
                default:
                    return true;
            }
        });
    }

    private void step(boolean up) {
        if (textValues != null) {
            if (textIndex < 0) textIndex = up ? 0 : -1;
            else if (up && textIndex < textValues.length - 1) textIndex++;
            else if (!up && textIndex > -1) textIndex--;
        } else if (up) {
            if (value < min) value = min;
            else if (value < max) value++;
        } else {
            if (value < 0) return;
            if (value > min) value--;
            else if (blankAllowed) value = -1;
        }
        refresh();
    }

    public void configure(int min, int max, int initial, boolean blankAllowed, String suffix) {
        this.min = min;
        this.max = max;
        this.blankAllowed = blankAllowed;
        this.textValues = null;
        if (blankAllowed && initial <= 0) this.value = -1;
        else this.value = Math.max(min, Math.min(max, initial));
        refresh();
    }

    public void configureText(String[] values, int initialIndex, boolean blankAllowed) {
        this.textValues = values;
        this.blankAllowed = blankAllowed;
        this.textIndex = Math.max(-1, Math.min(values.length - 1, initialIndex));
        refresh();
    }

    private void refresh() {
        if (textValues != null) {
            valueView.setText(textIndex < 0 ? "" : textValues[textIndex]);
        } else {
            valueView.setText(value < 0 ? "" : String.format(Locale.getDefault(), "%02d", value));
        }
    }

    public int getNumericValue() { return textValues == null ? value : -1; }
    public int getTextIndex() { return textValues == null ? -1 : textIndex; }
    public String getTextValue() { return textValues != null && textIndex >= 0 ? textValues[textIndex] : ""; }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
}
