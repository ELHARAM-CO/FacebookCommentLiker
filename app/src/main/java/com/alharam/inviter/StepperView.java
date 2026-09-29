package com.alharam.inviter;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Locale;

public class StepperView extends LinearLayout {
    private final TextView valueView;
    private final TextView upView;
    private final TextView downView;
    private int min = 0, max = 0, value = -1;
    private boolean blankAllowed = false;
    private String[] textValues = null;
    private int textIndex = -1;

    public StepperView(Context context) { this(context, null); }
    public StepperView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setLayoutDirection(LAYOUT_DIRECTION_LTR);
        setBackgroundResource(com.alharam.inviter.R.drawable.bg_input);
        int pad = dp(4);
        setPadding(pad, dp(3), pad, dp(3));
        setMinimumHeight(dp(48));

        LinearLayout arrows = new LinearLayout(context);
        arrows.setOrientation(VERTICAL);
        arrows.setGravity(Gravity.CENTER);
        arrows.setLayoutDirection(LAYOUT_DIRECTION_LTR);
        LayoutParams ap = new LayoutParams(dp(38), LayoutParams.MATCH_PARENT);
        arrows.setLayoutParams(ap);

        upView = arrow("▲");
        downView = arrow("▼");
        arrows.addView(upView, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));
        arrows.addView(downView, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));

        valueView = new TextView(context);
        valueView.setGravity(Gravity.CENTER);
        valueView.setTextColor(Color.parseColor("#172033"));
        valueView.setTextSize(14);
        valueView.setTypeface(null, android.graphics.Typeface.BOLD);
        valueView.setSingleLine(true);
        LayoutParams vp = new LayoutParams(0, LayoutParams.MATCH_PARENT, 1);

        addView(arrows);
        addView(valueView, vp);
        upView.setOnClickListener(v -> increment());
        downView.setOnClickListener(v -> decrement());
        refresh();
    }

    private TextView arrow(String t) {
        TextView v = new TextView(getContext());
        v.setText(t);
        v.setTextSize(11);
        v.setTextColor(Color.parseColor("#1769D1"));
        v.setGravity(Gravity.CENTER);
        v.setTypeface(null, android.graphics.Typeface.BOLD);
        v.setClickable(true);
        v.setFocusable(true);
        return v;
    }

    public void configure(int min, int max, int initial, boolean blankAllowed, String suffix) {
        this.min = min;
        this.max = max;
        this.blankAllowed = blankAllowed;
        this.textValues = null;
        if (blankAllowed && initial == 0) this.value = -1;
        else this.value = Math.max(min, Math.min(max, initial));
        refresh();
    }

    public void configureText(String[] values, int initialIndex, boolean blankAllowed) {
        this.textValues = values;
        this.blankAllowed = blankAllowed;
        this.textIndex = Math.max(-1, Math.min(values.length - 1, initialIndex));
        refresh();
    }

    private void increment() {
        if (textValues != null) {
            if (textIndex < textValues.length - 1) textIndex++;
        } else if (value < max) {
            value = value < min ? min : value + 1;
        }
        refresh();
    }

    private void decrement() {
        if (textValues != null) {
            if (textIndex > -1) textIndex--;
        } else if (value < 0) {
            // Keep an empty field empty.
        } else if (value > min) {
            value--;
        } else if (blankAllowed) {
            value = -1;
        }
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
