package com.arashi;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
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
        setMeasuredDimension(resolveSize((int) dp(64), widthSpec),
                resolveSize((int) dp(48), heightSpec));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float left = (getWidth() - dp(52)) / 2f;
        float top = (getHeight() - dp(32)) / 2f;
        track.set(left, top, left + dp(52), top + dp(32));
        boolean dark = Ui.dark((android.app.Activity) getContext());

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(checked ? Ui.accent((android.app.Activity) getContext())
                : dark ? 0xFF4A4453 : 0xFFE6E0E9);
        canvas.drawRoundRect(track, dp(16), dp(16), paint);
        if (!checked) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(dark ? 0xFF9C94A5 : 0xFF79717F);
            canvas.drawRoundRect(track, dp(16), dp(16), paint);
        }

        float cx = left + dp(16) + dp(20) * position;
        float cy = getHeight() / 2f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(checked ? (dark ? 0xFF38264E : 0xFFFFFFFF)
                : (dark ? 0xFFCFC6D7 : 0xFF79717F));
        canvas.drawCircle(cx, cy, dp(8) + dp(4) * position, paint);

        if (position > 0.5f) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeWidth(dp(2));
            paint.setColor(dark ? Ui.LAVENDER : Ui.PURPLE);
            Path tick = new Path();
            tick.moveTo(cx - dp(5), cy);
            tick.lineTo(cx - dp(1), cy + dp(4));
            tick.lineTo(cx + dp(6), cy - dp(4));
            canvas.drawPath(tick, paint);
        }
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
