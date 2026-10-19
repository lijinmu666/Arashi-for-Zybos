package com.lijinmu.arashi.xposed.hooks;

import android.util.Log;

import io.github.libxposed.api.XposedModule;

import com.lijinmu.arashi.xposed.ConfigManager;
import com.lijinmu.arashi.xposed.HookConfig;
import com.lijinmu.arashi.xposed.Reflect;

import java.lang.reflect.Method;

public final class CaptivePortalHooks {

    private static final String TAG = "[Arashi-Captive]";

    private CaptivePortalHooks() {
    }

    public static void hookCaptivePortal(XposedModule mod, ClassLoader cl) {
        Class<?> activity;
        try {
            activity = Class.forName(
                    "com.android.captiveportallogin.CaptivePortalLoginActivity", false, cl);
        } catch (Throwable t) {
            Log.w(TAG, "CaptivePortal activity class failed: " + t);
            return;
        }

hookBool(mod, activity, "checkVerificationStatus", true);

hookBool(mod, activity, "hasVpnNetwork", false);

hookTestForCaptivePortal(mod, activity);

try {
            Class<?> downloadService = Class.forName(
                    "com.android.captiveportallogin.DownloadService", false, cl);
            hookBool(mod, downloadService, "isDirectlyOpenType", false);
        } catch (Throwable t) {
            Log.w(TAG, "DownloadService hook failed: " + t);
        }

        Log.i(TAG, "CaptivePortal hooks initialized");
    }

    private static void hookBool(XposedModule mod, Class<?> clazz, String method,
            boolean result) {
        try {
            Method m = Reflect.findMethodExact(clazz, method);
            if (m == null) {
                Log.w(TAG, method + " not found");
                return;
            }
            mod.hook(m).intercept(chain -> {
                if (ConfigManager.isEnabled(HookConfig.KEY_CAPTIVE_PORTAL)) {
                    Log.i(TAG, method + " -> " + result);
                    return result;
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            Log.w(TAG, method + " hook failed: " + t);
        }
    }

    private static void hookTestForCaptivePortal(XposedModule mod, Class<?> activity) {
        try {
            Method m = Reflect.findMethodExact(activity, "testForCaptivePortal");
            if (m == null) {
                Log.w(TAG, "testForCaptivePortal not found");
                return;
            }
            Class<?> retType = m.getReturnType();
            final Object replacement;
            if (retType == Integer.TYPE || retType == Integer.class) {
                replacement = 204;
            } else if (retType == Long.TYPE || retType == Long.class) {
                replacement = 204L;
            } else {
                Log.w(TAG, "testForCaptivePortal unsupported return type: "
                        + retType.getName());
                return;
            }
            mod.hook(m).intercept(chain -> {
                if (ConfigManager.isEnabled(HookConfig.KEY_CAPTIVE_PORTAL)) {
                    Log.i(TAG, "testForCaptivePortal -> 204");
                    return replacement;
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            Log.w(TAG, "testForCaptivePortal hook failed: " + t);
        }
    }
}
