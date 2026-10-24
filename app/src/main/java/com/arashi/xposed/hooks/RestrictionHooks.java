package com.arashi.xposed.hooks;

import android.content.Intent;
import android.util.Log;

import io.github.libxposed.api.XposedModule;

import com.arashi.xposed.ConfigManager;
import com.arashi.xposed.HookConfig;
import com.arashi.xposed.Reflect;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public final class RestrictionHooks {

    private static final String TAG = "[Arashi-Restrict]";

    private RestrictionHooks() {
    }

public static void hookSystemServer(XposedModule mod, ClassLoader cl) {
        
        hookUnrestrictNotify(mod, cl);

hookZybReporting(mod, cl);

hookAllowUsb(mod, cl);

hookAllowDisplay(mod, cl);

hookAllowWallpaper(mod, cl);

hookDetailBlacklist(mod, cl);

        Log.i(TAG, "restriction hooks initialized");
    }

private static void hookUnrestrictNotify(XposedModule mod, ClassLoader cl) {
        try {
            Class<?> clz = Class.forName(
                    "com.android.server.notification.NotificationManagerService", false, cl);
            Method m = Reflect.findMethodExact(clz, "isCallerZyb",
                    String.class, int.class, int.class);
            if (m == null) {
                Log.w(TAG, "isCallerZyb not found");
                return;
            }
            mod.hook(m).intercept(chain -> {
                if (ConfigManager.isEnabled(HookConfig.KEY_UNRESTRICT_NOTIFY)) {
                    Log.i(TAG, "isCallerZyb -> true");
                    return true;
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            Log.w(TAG, "isCallerZyb hook failed: " + t);
        }
    }

public static void hookZybReporting(XposedModule mod, ClassLoader cl) {
        Class<?> clz;
        try {
            clz = Class.forName("android.os.ZybosSysApiManager", false, cl);
        } catch (Throwable t) {
            Log.w(TAG, "ZybosSysApiManager not found: " + t);
            return;
        }
        hookAllByName(mod, clz, "sendAlertBroadcast");
        hookAllByName(mod, clz, "sendZybosReportCallback");
        hookAllByName(mod, clz, "sendZybosStateWakeUpCallback");
    }

private static void hookAllByName(XposedModule mod, Class<?> clz, String methodName) {
        int hooked = 0;
        for (Class<?> c = clz; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (!methodName.equals(m.getName())) {
                    continue;
                }
                try {
                    m.setAccessible(true);
                    mod.hook(m).intercept(chain -> {
                        if (ConfigManager.isEnabled(HookConfig.KEY_HIDE_ALERT)) {
                            Log.i(TAG, methodName + " blocked");
                            return Reflect.defaultReturnValue(m.getReturnType());
                        }
                        return chain.proceed();
                    });
                    hooked++;
                } catch (Throwable ignored) {
                    
                }
            }
        }
        Log.i(TAG, methodName + " hooked: " + hooked);
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

    private static void hookAllowDisplay(XposedModule mod, ClassLoader cl) {
        try {
            Class<?> clz = Class.forName(
                    "com.android.server.display.ZybDisplayInjector", false, cl);
            Method m = findStaticBool(clz, "allowedCreateDisplay", 3);
            if (m == null) {
                Log.w(TAG, "allowedCreateDisplay not found");
                return;
            }
            mod.hook(m).intercept(chain -> {
                if (ConfigManager.isEnabled(HookConfig.KEY_ALLOW_DISPLAY)) {
                    Log.i(TAG, "allowedCreateDisplay -> true");
                    return true;
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            Log.w(TAG, "allowedCreateDisplay hook failed: " + t);
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
        for (Method m : clz.getDeclaredMethods()) {
            if (m.getName().equals(name)
                    && Modifier.isStatic(m.getModifiers())
                    && m.getParameterCount() == paramCount
                    && m.getReturnType() == boolean.class) {
                m.setAccessible(true);
                return m;
            }
        }
        return null;
    }
}
