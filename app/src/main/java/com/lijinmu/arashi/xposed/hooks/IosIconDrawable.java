package com.lijinmu.arashi.xposed.hooks;

import android.graphics.*;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;

/** Keeps the app artwork and changes only its outer mask. */
final class IosIconDrawable extends Drawable implements Drawable.Callback {
    private static final Path MASK = mask();
    private final Drawable original;
    private final Drawable artwork;
    private boolean drawing;
    private final Paint composite = new Paint(Paint.ANTI_ALIAS_FLAG);

    IosIconDrawable(Drawable original, String ignoredIdentity) {
        this.original = original;
        Drawable source = original;
        // The dock's drawable already clips to the old corner shape. Remove
        // that wrapper so it cannot cut off the new mask from inside.
        if (source.getClass().getName().equals(
                "com.zuoyebang.iot.pad.launcher.mysubject.presentation.view.dock.j")) {
            try {
                java.lang.reflect.Field field = source.getClass().getDeclaredField("a");
                field.setAccessible(true);
                Object inner = field.get(source);
                if (inner instanceof Drawable) source = (Drawable) inner;
            } catch (ReflectiveOperationException ignored) {}
        }
        artwork = source;
        original.setCallback(this);
    }

    Drawable original() { return original; }

    private static Path mask() {
        // Continuous-corner approximation, not Apple's private mask formula.
        // A wider corner span gives a rounder silhouette than the old n=5
        // superellipse. Curvature eases to zero where each corner meets a side.
        final int steps = 32;
        final double span = 38;
        double[] x = new double[steps + 1];
        double[] y = new double[steps + 1];
        double dt = 1.0 / steps;
        for (int i = 0; i < steps; i++) {
            double a = heading(i * dt), b = heading((i + .5) * dt);
            double c = heading((i + 1) * dt);
            x[i + 1] = x[i] + dt * (Math.cos(a) + 4 * Math.cos(b) + Math.cos(c)) / 6;
            y[i + 1] = y[i] + dt * (Math.sin(a) + 4 * Math.sin(b) + Math.sin(c)) / 6;
        }
        double scale = span / ((x[steps] + y[steps]) / 2);
        double handle = scale * dt / 3;
        Path path = new Path();
        for (int corner = 0; corner < 4; corner++) {
            float startX = rotateX((float) (100 - span), 0, corner);
            float startY = rotateY((float) (100 - span), 0, corner);
            if (corner == 0) path.moveTo(startX, startY); else path.lineTo(startX, startY);
            for (int i = 0; i < steps; i++) {
                double a = heading(i * dt), b = heading((i + 1) * dt);
                float c1x = (float) (100 - span + x[i] * scale + handle * Math.cos(a));
                float c1y = (float) (y[i] * scale + handle * Math.sin(a));
                float c2x = (float) (100 - span + x[i + 1] * scale - handle * Math.cos(b));
                float c2y = (float) (y[i + 1] * scale - handle * Math.sin(b));
                float endX = (float) (100 - span + x[i + 1] * scale);
                float endY = (float) (y[i + 1] * scale);
                path.cubicTo(rotateX(c1x, c1y, corner), rotateY(c1x, c1y, corner),
                        rotateX(c2x, c2y, corner), rotateY(c2x, c2y, corner),
                        rotateX(endX, endY, corner), rotateY(endX, endY, corner));
            }
        }
        path.close();
        return path;
    }

    private static double heading(double t) {
        return Math.PI / 2 * t * t * (3 - 2 * t);
    }

    private static float rotateX(float x, float y, int corner) {
        switch (corner) {
            case 1: return 100 - y;
            case 2: return 100 - x;
            case 3: return y;
            default: return x;
        }
    }

    private static float rotateY(float x, float y, int corner) {
        switch (corner) {
            case 1: return x;
            case 2: return 100 - y;
            case 3: return 100 - x;
            default: return y;
        }
    }

    static Path maskFor(RectF bounds) {
        Path path = new Path(MASK);
        Matrix transform = new Matrix();
        transform.setRectToRect(new RectF(0, 0, 100, 100), bounds, Matrix.ScaleToFit.FILL);
        path.transform(transform);
        return path;
    }

    @Override public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds.isEmpty()) return;
        int save = canvas.saveLayer(new RectF(bounds), composite);
        drawing = true;
        try {
            canvas.translate(bounds.left, bounds.top);
            Rect viewport = new Rect(0, 0, bounds.width(), bounds.height());
            canvas.clipPath(maskFor(new RectF(viewport)));
            if (artwork instanceof AdaptiveIconDrawable) {
                AdaptiveIconDrawable adaptive = (AdaptiveIconDrawable) artwork;
                // Android expands BOTH layers into the same viewport. Artwork
                // can live in the background too; scaling only the foreground
                // makes background logos smaller and splits aligned artwork.
                float halfScale = .5f + AdaptiveIconDrawable.getExtraInsetFraction();
                int halfWidth = (int) (viewport.width() * halfScale);
                int halfHeight = (int) (viewport.height() * halfScale);
                int cx = viewport.width() / 2, cy = viewport.height() / 2;
                Rect layers = new Rect(cx - halfWidth, cy - halfHeight,
                        cx + halfWidth, cy + halfHeight);
                drawLayer(canvas, adaptive.getBackground(), layers);
                drawLayer(canvas, adaptive.getForeground(), layers);
            } else {
                drawLayer(canvas, artwork, viewport);
            }
        } finally {
            drawing = false;
            canvas.restoreToCount(save);
        }
    }
    private static void drawLayer(Canvas canvas, Drawable layer, Rect bounds) {
        if (layer == null) return;
        Rect old = new Rect(layer.getBounds());
        try { layer.setBounds(bounds); layer.draw(canvas); }
        finally { layer.setBounds(old); }
    }
    @Override public int getIntrinsicWidth() { return original.getIntrinsicWidth(); }
    @Override public int getIntrinsicHeight() { return original.getIntrinsicHeight(); }
    @Override public void setAlpha(int alpha) { composite.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter value) { composite.setColorFilter(value); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    @Override public void invalidateDrawable(Drawable who) { if (!drawing) invalidateSelf(); }
    @Override public void scheduleDrawable(Drawable who, Runnable task, long when) { scheduleSelf(task, when); }
    @Override public void unscheduleDrawable(Drawable who, Runnable task) { unscheduleSelf(task); }
}
