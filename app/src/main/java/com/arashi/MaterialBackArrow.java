package com.arashi;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.View;


final class MaterialBackArrow extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    MaterialBackArrow(Context context) {
        super(context);
        setClickable(true);
        setFocusable(true);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density = getResources().getDisplayMetrics().density;
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        paint.setColor(Ui.text((Activity) getContext()));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f * density);
        paint.setStrokeCap(Paint.Cap.SQUARE);
        paint.setStrokeJoin(Paint.Join.MITER);
        Path arrow = new Path();
        arrow.moveTo(cx + 7f * density, cy - 9f * density);
        arrow.lineTo(cx - 3f * density, cy);
        arrow.lineTo(cx + 7f * density, cy + 9f * density);
        canvas.drawPath(arrow, paint);
        canvas.drawLine(cx - 2f * density, cy, cx + 14f * density, cy, paint);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_UP) return performClick();
        return true;
    }
}
