package com.lijinmu.arashi.xposed.hooks;

import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import android.util.Log;
import com.lijinmu.arashi.xposed.ConfigManager;
import com.lijinmu.arashi.xposed.HookConfig;
import com.lijinmu.arashi.xposed.Reflect;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Folder preview background; leaves child previews and notification dots intact. */
public final class LauncherFolderHooks {
    private LauncherFolderHooks() {}

    public static void hookLauncher(XposedModule module, ClassLoader loader) {
        hookLauncher(module, loader, false);
    }

    public static void hookLauncher(XposedModule module, ClassLoader loader, boolean launcher3) {
        try {
            Class<?> background = Class.forName(launcher3 ? "com.android.launcher3.folder.c0"
                    : "com.android.launcher3.folder.b0", false, loader);
            Method draw = Reflect.findMethodExact(background, "c", Canvas.class);
            Field size = declared(background, "l");
            Field left = declared(background, "m");
            Field top = declared(background, "n");
            Field scale = declared(background, "e");
            Field owner = declared(background, "k");
            if (draw == null) return;
            module.hook(draw).intercept(chain -> {
                if (!ConfigManager.isEnabled(HookConfig.KEY_IOS_ICONS)) return chain.proceed();
                Object target = chain.getThisObject();
                // Read all state before changing the canvas so fallback stays safe.
                float side = size.getInt(target);
                float factor = scale.getFloat(target);
                if (side <= 0 || factor <= 0 || !Float.isFinite(factor)) return chain.proceed();
                float extent = side * factor;
                float x = left.getInt(target) + (side - extent) / 2;
                float y = top.getInt(target) + (side - extent) / 2;
                Path mask = IosIconDrawable.maskFor(new RectF(x, y, x + extent, y + extent));
                View view = (View) owner.get(target);
                boolean dark = view != null && (view.getResources().getConfiguration().uiMode
                        & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
                Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                paint.setColor(dark ? 0xb354545f : 0xb3d8d8df);
                Canvas canvas = (Canvas) chain.getArg(0);
                canvas.drawPath(mask, paint);
                // The original method also leaves this clip in place for previews.
                canvas.clipPath(mask);
                return null;
            });
            Log.i("[Arashi-Icons]", "Folder preview background hook registered");
        } catch (Throwable error) {
            Log.w("[Arashi-Icons]", "Folder preview background hook unavailable", error);
        }
    }

    private static Field declared(Class<?> type, String name) throws NoSuchFieldException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
