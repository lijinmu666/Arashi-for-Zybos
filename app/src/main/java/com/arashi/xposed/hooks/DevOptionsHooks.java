package com.arashi.xposed.hooks;

import android.content.Context;
import android.content.ContentResolver;
import android.util.Log;

import io.github.libxposed.api.XposedModule;

import com.arashi.xposed.ConfigManager;
import com.arashi.xposed.HookConfig;
import com.arashi.xposed.Reflect;

import java.lang.reflect.Method;

public final class DevOptionsHooks {

    private static final String TAG = "[Arashi-DevOpt]";

    private static final String PROP_ALLOW = "sys.allow.development";
    private static final String KEY_SPECIAL_ADB = "special_enable_adb_debug";

    private DevOptionsHooks() {
    }

    public static void hookSettings(XposedModule mod, ClassLoader cl) {

RestrictionHooks.hookZybReporting(mod, cl);

        hookSysPropGet(mod, cl);

        hookSysPropSet(mod, cl);

        hookEnabler(mod, cl);

        hookSpecialAdbDebug(mod, cl);

        Log.i(TAG, "settings hooks initialized");
    }

private static void hookSysPropGet(XposedModule mod, ClassLoader cl) {
        try {
            Class<?> clz = Class.forName("android.os.SystemProperties", false, cl);
            Method m = Reflect.findMethodExact(clz, "getBoolean", String.class, boolean.class);
            if (m == null) {
                Log.w(TAG, "SystemProperties.getBoolean not found");
                return;
            }
            mod.hook(m).intercept(chain -> {
                if (PROP_ALLOW.equals(chain.getArg(0))
                        && ConfigManager.isEnabled(HookConfig.KEY_DEV_OPTIONS)) {
                    return true;
                }
                return chain.proceed();
            });
            Log.i(TAG, "SystemProperties.getBoolean hook registered");
        } catch (Throwable t) {
            Log.w(TAG, "SystemProperties.getBoolean hook failed: " + t);
        }
    }

private static void hookSysPropSet(XposedModule mod, ClassLoader cl) {
        try {
            Class<?> clz = Class.forName("android.os.SystemProperties", false, cl);
            Method m = Reflect.findMethodExact(clz, "set", String.class, String.class);
            if (m == null) {
                Log.w(TAG, "SystemProperties.set not found");
                return;
            }
            mod.hook(m).intercept(chain -> {
                if (PROP_ALLOW.equals(chain.getArg(0))
                        && "false".equals(chain.getArg(1))
                        && ConfigManager.isEnabled(HookConfig.KEY_DEV_OPTIONS)) {
                    Log.i(TAG, "block sys.allow.development -> false");
                    return null;
                }
                return chain.proceed();
            });
            Log.i(TAG, "SystemProperties.set hook registered");
        } catch (Throwable t) {
            Log.w(TAG, "SystemProperties.set hook failed: " + t);
        }
    }

private static void hookEnabler(XposedModule mod, ClassLoader cl) {
        try {
            Class<?> clz = Class.forName(
                    "com.android.settingslib.development.DevelopmentSettingsEnabler", false, cl);
            Method m = Reflect.findMethodExact(clz, "isDevelopmentSettingsEnabled",
                    Context.class);
            if (m == null) {
                Log.w(TAG, "isDevelopmentSettingsEnabled not found");
                return;
            }
            mod.hook(m).intercept(chain -> {
                if (ConfigManager.isEnabled(HookConfig.KEY_DEV_OPTIONS)) {
                    return true;
                }
                return chain.proceed();
            });
            Log.i(TAG, "isDevelopmentSettingsEnabled hook registered");
        } catch (Throwable t) {
            Log.w(TAG, "isDevelopmentSettingsEnabled hook failed: " + t);
        }
    }

private static void hookSpecialAdbDebug(XposedModule mod, ClassLoader cl) {
        try {
            Class<?> clz = Class.forName("android.provider.Settings$Global", false, cl);
            Method m = Reflect.findMethodExact(clz, "getInt",
                    ContentResolver.class, String.class, int.class);
            if (m == null) {
                Log.w(TAG, "Settings.Global.getInt not found");
                return;
            }
            mod.hook(m).intercept(chain -> {
                if (KEY_SPECIAL_ADB.equals(chain.getArg(1))
                        && ConfigManager.isEnabled(HookConfig.KEY_DEV_OPTIONS)) {
                    return 1;
                }
                return chain.proceed();
            });
            Log.i(TAG, "Settings.Global.getInt hook registered");
        } catch (Throwable t) {
            Log.w(TAG, "Settings.Global.getInt hook failed: " + t);
        }
    }
}
