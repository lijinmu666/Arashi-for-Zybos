package com.arashi.xposed.hooks;

import android.app.Activity;
import android.view.View;
import android.view.WindowManager;
import android.util.Log;
import com.arashi.xposed.ConfigManager;
import com.arashi.xposed.HookConfig;
import com.arashi.xposed.Reflect;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Method;

public final class LauncherWallpaperHooks {
    private static final String TAG = "[Arashi-Wallpaper]";
    private LauncherWallpaperHooks() {}

    public static void hookLauncher(XposedModule module, ClassLoader loader) {
        hookLauncher(module, loader, false);
    }

    public static void hookLauncher(XposedModule module, ClassLoader loader, boolean launcher3) {
        try {
            // Both launchers copy a static wallpaper into the content container.
            // Their snapshot coroutine names differ; c1 is unrelated on Launcher3.
            // Let the system wallpaper surface show through instead.
            Class<?> snapshot = Class.forName(launcher3 ? "com.zuoyebang.iot.pad.launcher.a1"
                    : "com.zuoyebang.iot.pad.launcher.c1", false, loader);
            Method load = Reflect.findMethodExact(snapshot, "invokeSuspend", Object.class);
            if (load != null) {
                module.hook(load).intercept(chain -> {
                    if (ConfigManager.isEnabled(HookConfig.KEY_ALLOW_WALLPAPER)) return null;
                    return chain.proceed();
                });
            }
            Class<?> launcher = Class.forName("com.zuoyebang.iot.pad.launcher.LauncherActivity", false, loader);
            for (String name : new String[] { "onStart", "onResume" }) {
                Method lifecycle = Reflect.findMethodExact(launcher, name);
                if (lifecycle == null) continue;
                module.hook(lifecycle).intercept(chain -> {
                    Object result = chain.proceed();
                    if (ConfigManager.isEnabled(HookConfig.KEY_ALLOW_WALLPAPER)) {
                        Activity activity = (Activity) chain.getThisObject();
                        int id = activity.getResources().getIdentifier("custom_container", "id", activity.getPackageName());
                        View container = id == 0 ? null : activity.findViewById(id);
                        if (container != null) {
                            container.setBackground(null);
                            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
                        }
                    }
                    return result;
                });
            }
            Log.i(TAG, "Launcher wallpaper background hooks registered");
        } catch (Throwable error) {
            Log.w(TAG, "Launcher wallpaper hooks unavailable", error);
        }
    }
}
