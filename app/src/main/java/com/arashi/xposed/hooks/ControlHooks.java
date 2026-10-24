package com.arashi.xposed.hooks;

import android.os.Binder;
import android.util.Log;

import io.github.libxposed.api.XposedModule;

import com.arashi.xposed.ConfigManager;
import com.arashi.xposed.HookConfig;
import com.arashi.xposed.ProcUtil;
import com.arashi.xposed.Reflect;

import java.lang.reflect.Method;

public final class ControlHooks {

    private static final String TAG = "[Arashi-Control]";

    private static final String PKG_SUPERVISE = "com.zuoyebang.iot.pad.supervise";

    private ControlHooks() {
    }

    public static void hookSystemServer(XposedModule mod, ClassLoader cl) {
        hookByName(mod, cl, "com.android.server.am.ActivityManagerService", "forceStopPackage");
        hookByName(mod, cl, "com.android.server.wm.ActivityTaskManagerService", "removeTask");
        hookKeepBackground(mod, cl);
        Log.i(TAG, "control hooks initialized");
    }

private static String blockedCaller() {
        if (!ConfigManager.isEnabled(HookConfig.KEY_BLOCK_SUPERVISE)) {
            return null;
        }
        int pid;
        try {
            pid = Binder.getCallingPid();
        } catch (Throwable t) {
            return null;
        }
        return ProcUtil.isProcess(pid, PKG_SUPERVISE) ? PKG_SUPERVISE : null;
    }

private static void hookByName(XposedModule mod, ClassLoader cl,
            String className, String methodName) {
        Class<?> clz;
        try {
            clz = Class.forName(className, false, cl);
        } catch (Throwable t) {
            Log.w(TAG, className + " not found: " + t);
            return;
        }
        int hooked = 0;
        for (Method m : clz.getDeclaredMethods()) {
            if (!methodName.equals(m.getName())) {
                continue;
            }
            try {
                m.setAccessible(true);
                mod.hook(m).intercept(chain -> {
                    String caller = blockedCaller();
                    if (caller != null) {
                        String first = m.getParameterTypes().length > 0
                                ? String.valueOf(chain.getArg(0)) : "";
                        Log.i(TAG, "block " + methodName + "(" + first + ") from " + caller);
                        return Reflect.defaultReturnValue(m.getReturnType());
                    }
                    return chain.proceed();
                });
                hooked++;
                Log.i(TAG, "  " + methodName + " hooked ("
                        + m.getParameterTypes().length + " args)");
            } catch (Throwable ignored) {
                
            }
        }
        Log.i(TAG, methodName + " hooked: " + hooked + " total");
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
                if (ConfigManager.isEnabled(HookConfig.KEY_KEEP_BACKGROUND)) {
                    Log.i(TAG, "keep background process: " + chain.getArg(0));
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
