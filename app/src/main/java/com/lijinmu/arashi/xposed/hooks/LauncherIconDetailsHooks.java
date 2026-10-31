package com.lijinmu.arashi.xposed.hooks;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewParent;
import android.util.Log;
import com.lijinmu.arashi.xposed.ConfigManager;
import com.lijinmu.arashi.xposed.HookConfig;
import com.lijinmu.arashi.xposed.Reflect;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

/** Version-specific folder miniatures and recent-task header icons. */
final class LauncherIconDetailsHooks {
    private static final String TAG = "[Arashi-Icons]";
    private static final Map<View, RecentCard> RECENT_CARDS = new WeakHashMap<>();
    private static final class RecentCard {
        final float radius;
        final android.content.res.ColorStateList color;
        RecentCard(float radius, android.content.res.ColorStateList color) {
            this.radius = radius;
            this.color = color;
        }
    }
    private LauncherIconDetailsHooks() {}

    static void hookLauncher(XposedModule module, ClassLoader loader) {
        hookLauncher(module, loader, false);
    }

    static void hookLauncher(XposedModule module, ClassLoader loader, boolean launcher3) {
        try {
            Class<?> iconView = Class.forName("com.android.quickstep.views.IconView", false, loader);
            if (launcher3) {
                Method bindIcon = Reflect.findMethodExact(iconView, "setDrawable", Drawable.class);
                if (bindIcon != null) module.hook(bindIcon).intercept(chain -> {
                    Object result = chain.proceed();
                    updateRecentRadius((View) chain.getThisObject());
                    return result;
                });
            }
            Method draw = Reflect.findMethodExact(iconView, "onDraw", Canvas.class);
            if (draw != null) module.hook(draw).intercept(chain -> {
                View view = (View) chain.getThisObject();
                if (launcher3) updateRecentRadius(view);
                if (!ConfigManager.isEnabled(HookConfig.KEY_IOS_ICONS)) return chain.proceed();
                Drawable original = (Drawable) Reflect.callMethod(view, "getDrawable");
                if (original == null) return chain.proceed();
                // Launcher3 expands cached icons by 20% and crops them in CardView.
                // Draw the raw artwork in the visible area instead.
                Rect bounds = launcher3 ? new Rect(0, 0, view.getWidth(), view.getHeight())
                        : new Rect(original.getBounds());
                if (bounds.isEmpty()) return chain.proceed();
                Canvas canvas = (Canvas) chain.getArg(0);
                boolean nativeRenderer = false;
                try {
                    ComponentName component = taskComponent(view, launcher3);
                    if (component != null) {
                        String type = original.getClass().getName();
                        if (!launcher3 && (type.equals("g8.v") || type.equals("g8.p0"))) {
                            // Keep the drawable's own badges, tint and disabled-state
                            // effects; the base/themed renderer applies the new mask.
                            nativeRenderer = true;
                        }
                    }
                } catch (Exception error) { Log.w(TAG, "Unable to resolve recent-task icon", error); }
                if (nativeRenderer) return chain.proceed();
                // Preserve the task's supplied artwork, including an app-selected icon.
                int save = canvas.save();
                try {
                    canvas.clipPath(IosIconDrawable.maskFor(new RectF(bounds)));
                    return chain.proceed();
                } finally { canvas.restoreToCount(save); }
            });
        } catch (Throwable error) { Log.w(TAG, "Recent-task icon hook unavailable", error); }
    }

    private static void updateRecentRadius(View view) {
        try {
            if (ConfigManager.isEnabled(HookConfig.KEY_IOS_ICONS)) {
                float radius = ((Number) Reflect.callMethod(view, "getRadius")).floatValue();
                android.content.res.ColorStateList color = (android.content.res.ColorStateList)
                        Reflect.callMethod(view, "getCardBackgroundColor");
                RecentCard previous = RECENT_CARDS.get(view);
                if (previous == null || radius != 0f) {
                    RECENT_CARDS.put(view, new RecentCard(radius,
                            previous == null ? color : previous.color));
                }
                if (radius != 0f) Reflect.callMethod(view, "setRadius", 0f);
                // CardView's default fill must not show a square behind the icon.
                if (color.getDefaultColor() != 0) view.getClass().getMethod("setCardBackgroundColor", int.class).invoke(view, 0);
            } else {
                RecentCard original = RECENT_CARDS.remove(view);
                if (original != null) {
                    Reflect.callMethod(view, "setRadius", original.radius);
                    view.getClass().getMethod("setCardBackgroundColor",
                            android.content.res.ColorStateList.class).invoke(view, original.color);
                }
            }
        } catch (Exception error) { Log.w(TAG, "Unable to update recent-task corner clipping", error); }
    }

    private static ComponentName taskComponent(View iconView, boolean launcher3) throws Exception {
        for (ViewParent parent = iconView.getParent(); parent instanceof View; parent = parent.getParent()) {
            if (!isTaskView(parent.getClass())) continue;
            if (launcher3) return componentOf(Reflect.callMethod(parent, "getTask"));
            Object containers = Reflect.callMethod(parent, "getTaskIdAttributeContainers");
            if (containers instanceof Object[]) {
                for (Object container : (Object[]) containers) {
                    if (container != null && LauncherIconHooks.field(container, "c") == iconView) {
                        return componentOf(LauncherIconHooks.field(container, "b"));
                    }
                }
                // A grouped card has two distinct icons; never use the other task.
                if (((Object[]) containers).length > 1) return null;
            }
            return componentOf(Reflect.callMethod(parent, "getTask"));
        }
        return null;
    }

    private static boolean isTaskView(Class<?> type) {
        for (; type != null; type = type.getSuperclass()) {
            if (type.getName().equals("com.android.quickstep.views.TaskView")) return true;
        }
        return false;
    }

    private static ComponentName componentOf(Object task) throws Exception {
        if (task == null) return null;
        Object key = LauncherIconHooks.field(task, "key");
        Object component = key == null ? null : Reflect.callMethod(key, "getComponent");
        if (!(component instanceof ComponentName)) component = Reflect.callMethod(task, "getTopComponent");
        return component instanceof ComponentName ? (ComponentName) component : null;
    }
}
