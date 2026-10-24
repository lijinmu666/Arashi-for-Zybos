package com.lijinmu.arashi.xposed.hooks;

import android.util.Log;

import io.github.libxposed.api.XposedModule;

import com.lijinmu.arashi.xposed.ConfigManager;
import com.lijinmu.arashi.xposed.HookConfig;
import com.lijinmu.arashi.xposed.Reflect;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class SystemUIHooks {

    private static final String TAG = "[Arashi-SystemUI]";

    private static final String METHOD_SET_DEFAULT_BROWSER = "setDefaultBrowser";

private static final String BROWSER_TARGET = "android.os.ZybosSysApiManager";

    private SystemUIHooks() {
    }

public static void hookSystemUI(XposedModule mod, ClassLoader cl) {
        hookDefaultBrowser(mod, cl, "SystemUI");
        ScreenRecordingHooks.hookSystemUI(mod, cl);
        StatusBarClockHooks.hookSystemUI(mod, cl);
        AospBatteryHooks.hookSystemUI(mod, cl);
        AospWifiHooks.hookSystemUI(mod, cl);
        AospBluetoothHooks.hookSystemUI(mod);
        SystemUiThemeHooks.hookSystemUI(mod, cl);
        LegacyPowerMenuHooks.hookSystemUI(mod, cl);
        NotificationIconLimitHooks.hookSystemUI(mod, cl);
        NotificationReplyHooks.hookSystemUI(mod, cl);
        NavigationAccessHooks.hookSystemUI(mod, cl);
        
        Log.i(TAG, "systemui hooks initialized");
    }

public static void hookSystemServer(XposedModule mod, ClassLoader cl) {
        LegacyPowerMenuHooks.hookSystemServer(mod, cl);
        hookDefaultBrowser(mod, cl, "system_server");
        NavigationAccessHooks.hookSystemServer(mod, cl);
    }

private static void hookDefaultBrowser(XposedModule mod, ClassLoader cl, String where) {
        int total = hookDefaultBrowserIn(mod, cl, BROWSER_TARGET, where);
        Log.i(TAG, METHOD_SET_DEFAULT_BROWSER + " hooked: " + total + " (" + where + ")");
    }

    private static int hookDefaultBrowserIn(
            XposedModule mod, ClassLoader cl, String className, String where) {
        Class<?> clz;
        try {
            clz = Class.forName(className, false, cl);
        } catch (Throwable t) {
            return 0;
        }
        List<Method> targets = findByMethodName(clz, METHOD_SET_DEFAULT_BROWSER);
        if (targets.isEmpty()) {
            return 0;
        }
        int hooked = 0;
        for (Method m : targets) {
            try {
                m.setAccessible(true);
                mod.hook(m).intercept(chain -> {
                    if (ConfigManager.isEnabled(
                            HookConfig.KEY_BLOCK_DEFAULT_BROWSER, HookConfig.DEFAULT_ENABLED)) {
                        Log.i(TAG, "blocked " + METHOD_SET_DEFAULT_BROWSER + "/"
                                + m.getParameterTypes().length
                                + " via " + className + " in " + where);
                        return Reflect.defaultReturnValue(m.getReturnType());
                    }
                    return chain.proceed();
                });
                hooked++;
            } catch (Throwable ignored) {
                
            }
        }
        Log.i(TAG, className + " -> " + hooked + " target(s)");
        return hooked;
    }

private static List<Method> findByMethodName(Class<?> clz, String name) {
        List<Method> found = new ArrayList<>();
        for (Class<?> c = clz; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (name.equals(m.getName())) {
                    found.add(m);
                }
            }
        }
        return found;
    }
}
