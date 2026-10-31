package com.lijinmu.arashi.xposed.hooks;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewParent;
import android.widget.ImageView;
import android.util.Log;
import com.lijinmu.arashi.xposed.ConfigManager;
import com.lijinmu.arashi.xposed.HookConfig;
import com.lijinmu.arashi.xposed.Reflect;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class LauncherIconHooks {
    private static final java.util.Map<Drawable, StyledBitmap> DISPLAY_ICONS = new java.util.WeakHashMap<>();
    private static final class StyledBitmap {
        final Bitmap bitmap;
        final IosIconDrawable drawable;
        StyledBitmap(Bitmap bitmap) {
            this.bitmap = bitmap;
            this.drawable = new IosIconDrawable(new BitmapDrawable(null, bitmap), null);
        }
    }
    private static final java.util.Map<Object, Float> CARD_RADII = new java.util.WeakHashMap<>();
    private static final ThreadLocal<Boolean> STYLING = new ThreadLocal<>();
    private static final String DOCK = "com.zuoyebang.iot.pad.launcher.mysubject.presentation.view.dock.DockView";
    private static final String PRESET = "com.zuoyebang.iot.pad.launcher.preset.view.PresetAppAdaptiveView";
    private LauncherIconHooks() {}

    public static void hookLauncher(XposedModule module, ClassLoader loader) {
        hookLauncher(module, loader, false);
    }

    public static void hookLauncher(XposedModule module, ClassLoader loader, boolean launcher3) {
        for (String name : new String[]{"setImageDrawable", "setImageBitmap", "setImageResource"}) {
            try {
                Class<?> arg = name.equals("setImageDrawable") ? Drawable.class
                        : name.equals("setImageBitmap") ? Bitmap.class : int.class;
                Method method = ImageView.class.getDeclaredMethod(name, arg);
                module.hook(method).intercept(chain -> {
                    Object result = chain.proceed();
                    if (Boolean.TRUE.equals(STYLING.get()) || !ConfigManager.isEnabled(HookConfig.KEY_IOS_ICONS)) return result;
                    ImageView image = (ImageView) chain.getThisObject();
                    try {
                        STYLING.set(true);
                        styleImage(image);
                    } catch (Throwable error) {
                        Log.w("[Arashi-Icons]", "Unable to style launcher icon", error);
                    } finally { STYLING.remove(); }
                    return result;
                });
            } catch (Throwable error) { Log.w("[Arashi-Icons]", "Image hook unavailable: " + name, error); }
        }
        try {
            // Image loading can finish before the asynchronous settings read.
            // Recheck at draw time so already-loaded icons also adopt the switch.
            Method onDraw = ImageView.class.getDeclaredMethod("onDraw", Canvas.class);
            module.hook(onDraw).intercept(chain -> {
                ImageView image = (ImageView) chain.getThisObject();
                if (!Boolean.TRUE.equals(STYLING.get())) {
                    try {
                        STYLING.set(true);
                        if (ConfigManager.isEnabled(HookConfig.KEY_IOS_ICONS)) {
                            styleImage(image);
                        } else if (image.getDrawable() instanceof IosIconDrawable) {
                            restoreCardRadius(image);
                            image.setImageDrawable(((IosIconDrawable) image.getDrawable()).original());
                        }
                    } catch (Exception error) {
                        Log.w("[Arashi-Icons]", "Unable to refresh displayed icon", error);
                    } finally { STYLING.remove(); }
                }
                return chain.proceed();
            });
        } catch (Throwable error) { Log.w("[Arashi-Icons]", "Image refresh hook unavailable", error); }
        // Launcher14 themed previews override the base renderer. Launcher3 uses
        // d8.q; its d8.d0 subclass is a loading placeholder, not a themed icon.
        for (String renderer : (launcher3 ? new String[] { "d8.q" } : new String[] { "g8.v", "g8.p0" })) try {
            // Style only the displayed drawable, never the persistent icon cache.
            Class<?> fastIcon = Class.forName(renderer, false, loader);
            Method draw = Reflect.findMethodExact(fastIcon, "a", Canvas.class, Rect.class);
            if (draw != null) module.hook(draw).intercept(chain -> {
                if (!ConfigManager.isEnabled(HookConfig.KEY_IOS_ICONS)) return chain.proceed();
                Drawable target = (Drawable) chain.getThisObject();
                try {
                    // The renderer's bitmap is the launcher's current icon, including
                    // app-selected alternatives. PackageManager lookups can instead
                    // return the default application/activity icon.
                    Bitmap bitmap = (Bitmap) field(target, "b");
                    if (bitmap == null || bitmap.getConfig() == Bitmap.Config.HARDWARE) return chain.proceed();
                    IosIconDrawable styled;
                    synchronized (DISPLAY_ICONS) {
                        StyledBitmap cached = DISPLAY_ICONS.get(target);
                        if (cached == null || cached.bitmap != bitmap) {
                            cached = new StyledBitmap(bitmap);
                            DISPLAY_ICONS.put(target, cached);
                        }
                        styled = cached.drawable;
                    }
                    styled.setBounds((Rect) chain.getArg(1));
                    Canvas canvas = (Canvas) chain.getArg(0);
                    android.graphics.Paint originalPaint = (android.graphics.Paint) field(target, "a");
                    int save = canvas.saveLayer(new android.graphics.RectF(styled.getBounds()), originalPaint);
                    try { styled.draw(canvas); }
                    finally { canvas.restoreToCount(save); }
                    return null;
                } catch (Exception error) {
                    Log.w("[Arashi-Icons]", "Unable to draw styled launcher icon", error);
                    return chain.proceed();
                }
            });
        } catch (Throwable error) { Log.w("[Arashi-Icons]", "Launcher icon renderer unavailable: " + renderer, error); }
        LauncherIconDetailsHooks.hookLauncher(module, loader, launcher3);
        Log.i("[Arashi-Icons]", "Launcher icon hooks registered");
    }

    private static void styleImage(ImageView image) throws Exception {
        Drawable drawable = image.getDrawable();
        if (drawable == null) return;
        ViewParent parent = image.getParent();
        for (int depth = 0; parent instanceof View && depth < 8; depth++, parent = parent.getParent()) {
            String type = parent.getClass().getName();
            String identity = null;
            if (PRESET.equals(type)) {
                if (field(parent, "bgKousuanPk") != image) return;
                Object card = field(parent, "cardView");
                if (!CARD_RADII.containsKey(card)) {
                    CARD_RADII.put(card, ((Number) Reflect.callMethod(card, "getRadius")).floatValue());
                }
                if (((Number) Reflect.callMethod(card, "getRadius")).floatValue() != 0f) {
                    Reflect.callMethod(card, "setRadius", 0f);
                }
            } else if (DOCK.equals(type)) {
                if (field(parent, "imageView") != image) return;
                Object element = Reflect.callMethod(parent, "getDockElement");
                if (element != null) identity = (String) Reflect.callMethod(element, "getPackageName");
            } else continue;
            if (!(drawable instanceof IosIconDrawable)) {
                image.setImageDrawable(new IosIconDrawable(drawable, identity));
            }
            return;
        }
    }
    private static void restoreCardRadius(ImageView image) throws Exception {
        ViewParent parent = image.getParent();
        for (int depth = 0; parent instanceof View && depth < 8; depth++, parent = parent.getParent()) {
            if (!PRESET.equals(parent.getClass().getName())) continue;
            Object card = field(parent, "cardView");
            Float radius = CARD_RADII.remove(card);
            if (radius != null) Reflect.callMethod(card, "setRadius", radius);
            return;
        }
    }

    static Object field(Object target, String name) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }
}
