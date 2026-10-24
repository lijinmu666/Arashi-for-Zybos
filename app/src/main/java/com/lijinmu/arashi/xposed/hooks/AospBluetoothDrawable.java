/*
 * Vector paths Copyright (C) 2017 The Android Open Source Project.
 * Licensed under the Apache License, Version 2.0.
 * https://www.apache.org/licenses/LICENSE-2.0
 * Distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND.
 * Source: frameworks/base android-14.0.0_r1, packages/SystemUI/res/drawable/ic_bluetooth_connected.xml
 * (identical in android-12.0.0_r1).
 */
package com.lijinmu.arashi.xposed.hooks;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import androidx.core.graphics.PathParser;

/** AOSP paths independent of vendor resource overlays. Each instance owns its tint state. */
final class AospBluetoothDrawable extends Drawable {
    private static final Path[] PATHS = {
        PathParser.createPathFromPathData("M17.71,7.71L12,2h-1v7.59L6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 11,14.41L11,22h1l5.71,-5.71 -4.3,-4.29 4.3,-4.29zM13,5.83l1.88,1.88L13,9.59L13,5.83zM14.88,16.29L13,18.17v-3.76l1.88,1.88z"),
        PathParser.createPathFromPathData("M5,12m-1.5,0a1.5,1.5 0,1 1,3 0a1.5,1.5 0,1 1,-3 0"),
        PathParser.createPathFromPathData("M19,12m-1.5,0a1.5,1.5 0,1 1,3 0a1.5,1.5 0,1 1,-3 0"),
    };
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int size;
    private ColorStateList tint = ColorStateList.valueOf(0xffffffff);
    private PorterDuff.Mode mode = PorterDuff.Mode.SRC_IN;
    private ColorFilter explicitFilter;

    AospBluetoothDrawable(float density) {
        size = Math.max(1, Math.round(17 * density));
        paint.setColor(0xffffffff);
        updateTint();
    }
    @Override public void draw(Canvas canvas) {
        int save = canvas.save();
        try {
            canvas.translate(getBounds().left, getBounds().top);
            canvas.scale(getBounds().width() / 24f, getBounds().height() / 24f);
            for (Path path : PATHS) canvas.drawPath(path, paint);
        } finally { canvas.restoreToCount(save); }
    }
    @Override public int getIntrinsicWidth() { return size; }
    @Override public int getIntrinsicHeight() { return size; }
    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
    @Override public int getAlpha() { return paint.getAlpha(); }
    @Override public void setColorFilter(ColorFilter filter) {
        explicitFilter = filter;
        updateTint();
    }
    @Override public void setTintList(ColorStateList tint) { this.tint = tint; updateTint(); }
    @Override public void setTintMode(PorterDuff.Mode mode) { this.mode = mode; updateTint(); }
    @Override public boolean isStateful() { return tint != null && tint.isStateful(); }
    @Override protected boolean onStateChange(int[] state) { updateTint(); return true; }
    private void updateTint() {
        paint.setColorFilter(explicitFilter != null ? explicitFilter :
                tint == null || mode == null ? null : new PorterDuffColorFilter(
                        tint.getColorForState(getState(), tint.getDefaultColor()), mode));
        invalidateSelf();
    }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
