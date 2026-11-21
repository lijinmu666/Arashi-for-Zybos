package com.lijinmu.arashi.xposed.hooks;

import android.util.Log;

import io.github.libxposed.api.XposedModule;

import com.lijinmu.arashi.xposed.ConfigManager;
import com.lijinmu.arashi.xposed.Reflect;

import java.lang.reflect.Method;

public final class ControlHooks {

    private static final String TAG = "[Arashi-Control]";


    private ControlHooks() {
    }

    public static void hookSystemServer(XposedModule mod, ClassLoader cl) {
        hookKeepBackground(mod, cl);
        Log.i(TAG, "control hooks initialized");
    }

private static void hookKeepBackground(XposedModule mod, ClassLoader cl) {
        try {
            Class<?> clz = Class.forName(
                    "com.android.server.am.ActivityManagerService$LocalService", false, cl);
            Method m = Reflect.findMethodExact(clz, "zybForceStopPackage",
                    String.class, int.class);
            if (m == null) {
                Log.w(TAG, "zybForceStopPackage not found");
                return;
            }
            mod.hook(m).intercept(chain -> {
                String packageName = (String) chain.getArg(0);
                if (ConfigManager.shouldKeepBackground(packageName)) {
                    Log.i(TAG, "keep background process: " + packageName);
                    return null;
                }
                return chain.proceed();
            });
            Log.i(TAG, "zybForceStopPackage hook registered");
        } catch (Throwable t) {
            Log.w(TAG, "zybForceStopPackage hook failed: " + t);
        }
    }
}
