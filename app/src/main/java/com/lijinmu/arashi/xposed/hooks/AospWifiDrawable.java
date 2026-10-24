/*
 * Vector paths Copyright (C) 2015, 2021 The Android Open Source Project.
 * Licensed under the Apache License, Version 2.0.
 * https://www.apache.org/licenses/LICENSE-2.0
 * Distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND.
 * Source: frameworks/base android-14.0.0_r1, core/res/res/drawable/ic_wifi_signal_*.xml
 * and packages/SettingsLib/res/drawable/ic_no_internet_wifi_signal_*.xml.
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
final class AospWifiDrawable extends Drawable {
    private static final String[][] DATA = {
        new String[] { "M12,4c3.42,0,6.73,1.27,9.3,3.53L12,18.85L2.7,7.53C5.27,5.27,8.58,4,12,4 M12,2C7.25,2,2.97,4.08,0,7.39L12,22L24,7.39 C21.03,4.08,16.75,2,12,2L12,2z" },
        new String[] { "M12,2.01c-4.75,0-9.03,2.08-12,5.39L12,22L24,7.4C21.03,4.09,16.75,2.01,12,2.01z M12,18.86L2.7,7.54 C5.27,5.28,8.58,4.01,12,4.01s6.73,1.27,9.3,3.53L12,18.86z", "M16.42,16.63L12,22l-4.42-5.37c0.15-0.74,0.48-1.43,0.95-1.99C9.35,13.64,10.6,13,12,13s2.65,0.64,3.47,1.64 C15.94,15.2,16.27,15.89,16.42,16.63z" },
        new String[] { "M12,2C7.25,2,2.97,4.08,0,7.39L12,22L24,7.39C21.03,4.08,16.75,2,12,2z M12,18.85L2.7,7.53C5.27,5.27,8.58,4,12,4 s6.73,1.27,9.3,3.53L12,18.85z", "M18.61,13.95L12,22l-6.61-8.05c0.33-0.61,0.74-1.17,1.22-1.66c1.36-1.42,3.27-2.3,5.39-2.3s4.03,0.88,5.39,2.3 C17.87,12.78,18.28,13.34,18.61,13.95z" },
        new String[] { "M12,2C7.25,2,2.97,4.08,0,7.39L12,22L24,7.39C21.03,4.08,16.75,2,12,2z M12,18.85L2.7,7.53C5.27,5.27,8.58,4,12,4 s6.73,1.27,9.3,3.53L12,18.85z", "M20.7,11.41L12,22L3.3,11.41c0.38-0.52,0.8-0.99,1.26-1.43C6.49,8.13,9.1,6.99,12,6.99s5.51,1.13,7.44,2.99 C19.9,10.41,20.33,10.89,20.7,11.41z" },
        new String[] { "M24,7.39L12,22L0,7.39C2.97,4.08,7.25,2,12,2S21.03,4.08,24,7.39z" },
        new String[] { "M20.66,7C18.19,5.07 15.14,4 12,4C8.58,4 5.27,5.27 2.7,7.53L12,18.85l6,-7.3v3.15L12,22L0,7.39C2.97,4.08 7.25,2 12,2c4.56,0 8.69,1.92 11.64,5H20.66z", "M22,10h-2v8h2V10z", "M22,20h-2v2h2V20z" },
        new String[] { "M23.62,7C20.65,3.93 16.6,2 12,2C7.2,2 3,4.1 0,7.4L12,22l6,-7.3v-3.19l-2.13,2.59C14.74,13.4 13.39,13 12,13s-2.74,0.4 -3.87,1.1L2.7,7.5C5.3,5.3 8.6,4 12,4c3.13,0 6.18,1.1 8.68,3H23.62z", "M22,10h-2v8h2V10z", "M22,20h-2v2h2V20z" },
        new String[] { "M23.62,7C20.65,3.93 16.6,2 12,2C7.2,2 3,4.1 0,7.4L12,22l6,-7.3v-3.19l-0.27,0.33C16.12,10.67 14.09,10 12,10c-2.09,0 -4.12,0.67 -5.73,1.84L2.7,7.5C5.3,5.3 8.6,4 12,4c3.13,0 6.18,1.1 8.68,3H23.62z", "M22,10h-2v8h2V10z", "M22,20h-2v2h2V20z" },
        new String[] { "M0,7.4L12,22l6,-7.3V8.57C16.21,7.56 14.14,7 12,7C9.2,7 6.53,7.96 4.45,9.62L2.7,7.5C5.3,5.3 8.6,4 12,4c3.13,0 6.18,1.1 8.68,3h2.95C20.65,3.93 16.6,2 12,2C7.2,2 3,4.1 0,7.4z", "M22,10h-2v8h2V10z", "M22,20h-2v2h2V20z" },
        new String[] { "M0,7.4C3,4.1 7.2,2 12,2c4.6,0 8.65,1.93 11.62,5H18v7.7L12,22L0,7.4z", "M22,10h-2v8h2V10z", "M22,20h-2v2h2V20z" },
    };
    private static final Path[][] PATHS = new Path[DATA.length][];
    static {
        for (int i = 0; i < DATA.length; i++) {
            PATHS[i] = new Path[DATA[i].length];
            for (int j = 0; j < DATA[i].length; j++)
                PATHS[i][j] = PathParser.createPathFromPathData(DATA[i][j]);
        }
    }
    private final Path[] paths;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int size;
    private ColorStateList tint = ColorStateList.valueOf(0xffffffff);
    private PorterDuff.Mode mode = PorterDuff.Mode.SRC_IN;
    private ColorFilter explicitFilter;

    AospWifiDrawable(int index, float density) {
        paths = PATHS[index];
        size = Math.round(24 * density);
        paint.setColor(0xffffffff);
        updateTint();
    }
    @Override public void draw(Canvas canvas) {
        int save = canvas.save();
        try {
            canvas.translate(getBounds().left, getBounds().top);
            canvas.scale(getBounds().width() / 24f, getBounds().height() / 24f);
            for (Path path : paths) canvas.drawPath(path, paint);
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
