package com.lijinmu.arashi.xposed.hooks;

import android.content.Intent;
import android.util.Log;

import io.github.libxposed.api.XposedModule;

import com.lijinmu.arashi.xposed.ConfigManager;
import com.lijinmu.arashi.xposed.HookConfig;
import com.lijinmu.arashi.xposed.Reflect;

import java.lang.reflect.Method;

public final class RestrictionHooks {

    private static final String TAG = "[Arashi-Restrict]";

    private RestrictionHooks() {
    }

public static void hookSystemServer(XposedModule mod, ClassLoader cl) {
hookAllowUsb(mod, cl);

hookAllowWallpaper(mod, cl);

hookDetailBlacklist(mod, cl);

        Log.i(TAG, "restriction hooks initialized");
    }

    private static void hookAllowUsb(XposedModule mod, ClassLoader cl) {
        try {
            Class<?> clz = Class.forName("com.android.server.ZybosSysApiService", false, cl);
            Method m = Reflect.findMethodExact(clz, "isDisallowUSB");
            if (m == null) {
                Log.w(TAG, "isDisallowUSB not found");
                return;
            }
            mod.hook(m).intercept(chain -> {
                if (ConfigManager.isEnabled(HookConfig.KEY_ALLOW_USB)) {
                    Log.i(TAG, "isDisallowUSB -> false");
                    return false;
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            Log.w(TAG, "isDisallowUSB hook failed: " + t);
        }
    }

    private static void hookAllowWallpaper(XposedModule mod, ClassLoader cl) {
        // These are separate gates in the supplied Android 12 reference module.
        // A missing vendor injector must not prevent installing the service gate.
        hookWallpaperGate(mod, cl, "com.android.server.wallpaper.ZybWallpaperInjector",
                "allowedSetWallpaper");
        hookWallpaperGate(mod, cl, "com.android.server.wallpaper.WallpaperManagerService",
                "isSetWallpaperAllowed");
    }

    private static void hookWallpaperGate(XposedModule mod, ClassLoader cl,
                                          String className, String methodName) {
        try {
            Class<?> target = Class.forName(className, false, cl);
            Method gate = Reflect.findMethodExact(target, methodName, String.class);
            if (gate == null || gate.getReturnType() != boolean.class) {
                Log.w(TAG, className + "." + methodName + "(String): boolean not found");
                return;
            }
            mod.hook(gate).intercept(chain -> {
                if (ConfigManager.isEnabled(HookConfig.KEY_ALLOW_WALLPAPER)) return true;
                return chain.proceed();
            });
            Log.i(TAG, "Wallpaper gate registered: " + className + "." + methodName);
        } catch (Throwable error) {
            Log.w(TAG, "Wallpaper gate unavailable: " + className + "." + methodName, error);
        }
    }

    private static void hookDetailBlacklist(XposedModule mod, ClassLoader cl) {
        try {
            Class<?> clz = Class.forName(
                    "com.android.server.wm.ActivityTaskManagerService", false, cl);
            Method m = Reflect.findMethodExact(clz, "isDisabledDetailApp",
                    String.class, Intent.class);
            if (m == null) {
                Log.w(TAG, "isDisabledDetailApp not found");
                return;
            }
            mod.hook(m).intercept(chain -> {
                if (ConfigManager.isEnabled(HookConfig.KEY_CLEAR_DETAIL_BLACK)) {
                    Log.i(TAG, "isDisabledDetailApp -> false");
                    return false;
                }
                return chain.proceed();
            });
            Log.i(TAG, "isDisabledDetailApp hook registered");
        } catch (Throwable t) {
            Log.w(TAG, "isDisabledDetailApp hook failed: " + t);
        }
    }

}
