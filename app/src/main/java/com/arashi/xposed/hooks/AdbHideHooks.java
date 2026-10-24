package com.arashi.xposed.hooks;

import android.content.Context;
import android.content.pm.ProviderInfo;
import android.os.Binder;
import android.os.Bundle;
import android.util.Log;

import io.github.libxposed.api.XposedModule;

import com.arashi.xposed.ConfigManager;
import com.arashi.xposed.HookConfig;
import com.arashi.xposed.Reflect;

import java.lang.reflect.Method;

public final class AdbHideHooks {

    private static final String TAG = "[Arashi-AdbHide]";
    private static final int FIRST_APP_UID = 10000;
    private static final String[] DEV_KEYS = {
            "adb_enabled",
            "development_settings_enabled",
            "adb_wifi_enabled"
    };

    private static final ThreadLocal<Integer> sCallingUid = new ThreadLocal<>();
    private static volatile boolean sCallHooked = false;

    private AdbHideHooks() {
    }

    public static void hookSystemServer(XposedModule mod, ClassLoader cl) {
        try {
            Class<?> cp = Class.forName("android.content.ContentProvider", false, cl);
            Method attach = Reflect.findMethodExact(cp, "attachInfo",
                    Context.class, ProviderInfo.class);
            if (attach == null) {
                Log.w(TAG, "ContentProvider.attachInfo not found");
                return;
            }
            mod.hook(attach).intercept(chain -> {
                Object result = chain.proceed();
                try {
                    Object obj = chain.getThisObject();
                    if (obj != null && "com.android.providers.settings.SettingsProvider"
                            .equals(obj.getClass().getName())) {
                        hookSettingsProviderCall(mod, obj.getClass());
                    }
                } catch (Throwable ignored) {
                }
                return result;
            });
            Log.i(TAG, "ContentProvider.attachInfo hooked (awaiting SettingsProvider)");
        } catch (Throwable t) {
            Log.w(TAG, "attachInfo hook failed: " + t);
        }
    }

    public static void hookSettingsProvider(XposedModule mod, ClassLoader cl) {
        try {
            Class<?> provider = Class.forName(
                    "com.android.providers.settings.SettingsProvider", false, cl);
            hookSettingsProviderCall(mod, provider);
        } catch (Throwable error) {
            Log.w(TAG, "SettingsProvider hook unavailable", error);
        }
    }

    private static void hookSettingsProviderCall(XposedModule mod, Class<?> providerClass) {
        if (sCallHooked) {
            return;
        }
        int hooked = 0;
        for (Method method : providerClass.getDeclaredMethods()) {
            if (!method.getName().equals("call")
                    || method.getParameterTypes().length < 3) {
                continue;
            }
            try {
                method.setAccessible(true);
                mod.hook(method).intercept(chain -> {
                    
                    try {
                        sCallingUid.set(Binder.getCallingUid());
                    } catch (Throwable ignored) {
                    }
                    try {
                        Object result = chain.proceed();
                        try {
                            spoofAdbSettings(chain, result);
                        } catch (Throwable ignored) {
                        }
                        return result;
                    } finally {
                        sCallingUid.remove();
                    }
                });
                hooked++;
            } catch (Throwable ignored) {
            }
        }
        sCallHooked = hooked > 0;
        Log.i(TAG, "SettingsProvider.call hooks installed: " + hooked);
    }

    private static void spoofAdbSettings(
            io.github.libxposed.api.XposedInterface.Chain chain, Object result) {
        if (!ConfigManager.isEnabled(HookConfig.KEY_HIDE_ADB)) {
            return;
        }
        Integer uid = sCallingUid.get();
        if (uid == null || uid % 100000 < FIRST_APP_UID) {
            return; 
        }
        String targetKey = null;
        for (Object arg : chain.getArgs()) {
            if (arg instanceof String && isDevKey((String) arg)) {
                targetKey = (String) arg;
                break;
            }
        }
        if (targetKey == null) {
            return;
        }
        if (result instanceof Bundle) {
            Bundle bundle = (Bundle) result;
            if (bundle.containsKey("value")) {
                bundle.putString("value", "0");
                Log.i(TAG, "SPOOF " + targetKey + " -> 0 for uid=" + uid);
            }
        }
    }

    private static boolean isDevKey(String key) {
        for (String devKey : DEV_KEYS) {
            if (devKey.equals(key)) {
                return true;
            }
        }
        return false;
    }
}
