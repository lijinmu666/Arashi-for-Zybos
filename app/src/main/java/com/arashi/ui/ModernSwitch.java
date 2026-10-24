package com.arashi.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

final class ModernSwitch extends View {
    interface OnCheckedChangeListener { void onCheckedChanged(boolean checked); }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF track = new RectF();
    private boolean checked;
    private float position;
    private ValueAnimator animation;
    private OnCheckedChangeListener listener;

    ModernSwitch(Context context) {
        super(context);
        setClickable(true);
        setFocusable(true);
    }

    void setOnCheckedChangeListener(OnCheckedChangeListener value) { listener = value; }

    boolean isChecked() { return checked; }

    void setChecked(boolean value) {
        setChecked(value, false);
    }

    void setChecked(boolean value, boolean animate) {
        if (checked == value) return;
        checked = value;
        if (animation != null) {
            animation.cancel();
            animation = null;
        }
        if (animate) {
            animation = ValueAnimator.ofFloat(position, checked ? 1f : 0f);
            animation.setDuration(180);
            animation.addUpdateListener(a -> {
                position = (float) a.getAnimatedValue();
                invalidate();
            });
            animation.start();
        } else {
            position = checked ? 1f : 0f;
            invalidate();
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(resolveSize((int) dp(58), widthSpec),
                resolveSize((int) dp(48), heightSpec));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float left = (getWidth() - dp(50)) / 2f;
        float top = (getHeight() - dp(30)) / 2f;
        track.set(left, top, left + dp(50), top + dp(30));
        boolean dark = Ui.dark((android.app.Activity) getContext());

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(checked ? Ui.accent((android.app.Activity) getContext())
                : dark ? 0xFF5A5A60 : 0xFFE0E2E8);
        canvas.drawRoundRect(track, dp(15), dp(15), paint);
        if (!checked) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            paint.setColor(dark ? 0xFF707077 : 0xFFD1D3D9);
            canvas.drawRoundRect(track, dp(15), dp(15), paint);
        }

        float cx = left + dp(15) + dp(20) * position;
        float cy = getHeight() / 2f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(dark ? 0xFFF5F5F5 : 0xFFFFFFFF);
        canvas.drawCircle(cx, cy, dp(12), paint);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                setPressed(true);
                return true;
            case MotionEvent.ACTION_UP:
                setPressed(false);
                if (event.getX() >= 0 && event.getX() <= getWidth()
                        && event.getY() >= 0 && event.getY() <= getHeight()) return performClick();
                return true;
            case MotionEvent.ACTION_CANCEL:
                setPressed(false);
                return true;
            default:
                return true;
        }
    }

    @Override public boolean performClick() {
        super.performClick();
        checked = !checked;
        if (animation != null) animation.cancel();
        animation = ValueAnimator.ofFloat(position, checked ? 1f : 0f);
        animation.setDuration(180);
        animation.addUpdateListener(value -> {
            position = (float) value.getAnimatedValue();
            invalidate();
        });
        animation.start();
        if (listener != null) listener.onCheckedChanged(checked);
        return true;
    }

    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.Switch");
        info.setCheckable(true);
        info.setChecked(checked);
    }

    @Override protected void onDetachedFromWindow() {
        if (animation != null) animation.cancel();
        super.onDetachedFromWindow();
    }
}
