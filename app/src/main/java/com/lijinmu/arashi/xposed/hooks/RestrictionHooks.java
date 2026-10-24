package com.lijinmu.arashi.xposed.hooks;

import android.content.Intent;
import android.util.Log;

import io.github.libxposed.api.XposedModule;

import com.lijinmu.arashi.xposed.ConfigManager;
import com.lijinmu.arashi.xposed.HookConfig;
import com.lijinmu.arashi.xposed.Reflect;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

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
        try {
            Class<?> clz = Class.forName(
                    "com.android.server.wallpaper.ZybWallpaperInjector", false, cl);
            Method m = findStaticBool(clz, "allowedSetWallpaper", 1);
            if (m == null) {
                Log.w(TAG, "allowedSetWallpaper not found");
                return;
            }
            mod.hook(m).intercept(chain -> {
                if (ConfigManager.isEnabled(HookConfig.KEY_ALLOW_WALLPAPER)) {
                    Log.i(TAG, "allowedSetWallpaper -> true");
                    return true;
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            Log.w(TAG, "allowedSetWallpaper hook failed: " + t);
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

    private static Method findStaticBool(Class<?> clz, String name, int paramCount) {
        for (Method method : clz.getDeclaredMethods()) {
            if (name.equals(method.getName())
                    && Modifier.isStatic(method.getModifiers())
                    && method.getParameterCount() == paramCount
                    && method.getReturnType() == boolean.class) {
                method.setAccessible(true);
                return method;
            }
        }
        return null;
    }

}
