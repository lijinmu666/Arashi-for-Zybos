/* Copyright (C) 2021 The Android Open Source Project
 * SPDX-License-Identifier: Apache-2.0
 * Adapted from SystemUI util/BrightnessProgressDrawable.kt.
 */
package com.lijinmu.arashi.xposed.hooks;

import android.content.res.Resources;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableWrapper;
import android.graphics.drawable.InsetDrawable;

/** Slide the rounded progress end without stretching the sun icon or flattening its corners. */
final class AospBrightnessProgressDrawable extends InsetDrawable {
    AospBrightnessProgressDrawable(Drawable drawable) { super(drawable, 0); }

    @Override protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        onLevelChange(getLevel());
    }
    @Override protected boolean onLevelChange(int level) {
        Drawable child = getDrawable();
        Rect bounds = getBounds();
        Rect previous = child.getBounds();
        int width = Math.max(bounds.height(), Math.min(bounds.width(),
                bounds.width() * level / 10000 + bounds.height() / 2));
        child.setBounds(bounds.left, previous.top, bounds.left + width, previous.bottom);
        return super.onLevelChange(level);
    }
    @Override public boolean onLayoutDirectionChanged(int layoutDirection) {
        onLevelChange(getLevel());
        return super.onLayoutDirectionChanged(layoutDirection);
    }
    @Override public ConstantState getConstantState() {
        ConstantState original = super.getConstantState();
        if (original == null) return null;
        return new ConstantState() {
            @Override public Drawable newDrawable() { return newDrawable(null); }
            @Override public Drawable newDrawable(Resources resources) {
                DrawableWrapper copy = (DrawableWrapper) original.newDrawable(resources);
                return new AospBrightnessProgressDrawable(copy.getDrawable());
            }
            @Override public int getChangingConfigurations() { return original.getChangingConfigurations(); }
        };
    }
}
