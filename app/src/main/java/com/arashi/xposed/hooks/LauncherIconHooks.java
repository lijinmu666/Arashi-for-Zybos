package com.arashi.xposed.hooks;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewParent;
import android.widget.ImageView;
import android.util.Log;
import com.arashi.xposed.ConfigManager;
import com.arashi.xposed.HookConfig;
import com.arashi.xposed.Reflect;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class LauncherIconHooks {
    private static final java.util.Map<Drawable, IosIconDrawable> DISPLAY_ICONS = new java.util.WeakHashMap<>();
    private static final java.util.Map<Drawable, Source> SOURCES = new java.util.WeakHashMap<>();
    private static final class Source {
        final Context context;
        final ComponentName component;
        Source(Context context, ComponentName component) {
            Context app = context.getApplicationContext();
            this.context = app == null ? context : app;
            this.component = component;
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
                    String identity = null;
                    Object callback = target.getCallback();
                    if (callback instanceof View) identity = identityFromTag(((View) callback).getTag());
                    IosIconDrawable styled;
                    synchronized (DISPLAY_ICONS) {
                        styled = DISPLAY_ICONS.get(target);
                        if (styled == null) {
                            Drawable source = sourceFor(target);
                            if (source == null && identity != null && callback instanceof View) {
                                try {
                                    source = ((View) callback).getContext().getPackageManager().getApplicationIcon(identity);
                                } catch (android.content.pm.PackageManager.NameNotFoundException ignored) {}
                            }
                            if (source == null) {
                                Bitmap bitmap = (Bitmap) field(target, "b");
                                if (bitmap == null || bitmap.getConfig() == Bitmap.Config.HARDWARE) return chain.proceed();
                                source = new BitmapDrawable(null, bitmap);
                            }
                            styled = new IosIconDrawable(source, identity);
                            DISPLAY_ICONS.put(target, styled);
                        }
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

    static void registerSource(Drawable icon, Context context, Object item) throws IllegalAccessException {
        if (icon == null || context == null) return;
        ComponentName component = componentFromTag(item);
        registerComponent(icon, context, component);
    }

    static void registerComponent(Drawable icon, Context context, ComponentName component) {
        if (icon == null || context == null) return;
        synchronized (SOURCES) {
            Source previous = SOURCES.get(icon);
            if (previous != null && previous.component.equals(component)
                    && previous.context == context.getApplicationContext()) return;
            if (component == null) SOURCES.remove(icon);
            else SOURCES.put(icon, new Source(context, component));
        }
        synchronized (DISPLAY_ICONS) { DISPLAY_ICONS.remove(icon); }
    }

    private static Drawable sourceFor(Drawable icon) {
        Source source;
        synchronized (SOURCES) { source = SOURCES.get(icon); }
        if (source == null) return null;
        try { return source.context.getPackageManager().getActivityIcon(source.component); }
        catch (android.content.pm.PackageManager.NameNotFoundException ignored) { return null; }
    }

    private static String identityFromTag(Object tag) throws IllegalAccessException {
        ComponentName component = componentFromTag(tag);
        return component == null ? null : component.getPackageName();
    }

    static ComponentName componentFromTag(Object tag) throws IllegalAccessException {
        if (tag == null) return null;
        for (Class<?> type = tag.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                if (field.getType() != ComponentName.class && field.getType() != Intent.class) continue;
                field.setAccessible(true);
                Object value = field.get(tag);
                ComponentName component = value instanceof Intent ? ((Intent) value).getComponent()
                        : value instanceof ComponentName ? (ComponentName) value : null;
                if (component != null) return component;
            }
        }
        return null;
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
