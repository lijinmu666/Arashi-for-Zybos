package com.lijinmu.arashi.xposed.hooks;

import android.content.ComponentCallbacks;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.lijinmu.arashi.xposed.ConfigManager;
import com.lijinmu.arashi.xposed.HookConfig;
import com.lijinmu.arashi.xposed.Reflect;
import com.lijinmu.arashi.xposed.SystemUiThemeColors;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import io.github.libxposed.api.XposedModule;

/** Reconnect the retained Android 12/14 Monet controller to its native wallpaper/OMS pipeline. */
public final class SystemUiMonetHooks {
    private static final String TAG = "[Arashi-Monet]";
    private static boolean callbacksRegistered;

    private SystemUiMonetHooks() {}

    static boolean enabled() {
        return ConfigManager.isEnabledCached(HookConfig.KEY_SYSTEMUI_MONET);
    }

    public static void hookSystemUI(XposedModule module, ClassLoader loader) {
        try {
            Method getBoolean = Resources.class.getDeclaredMethod("getBoolean", int.class);
            module.hook(getBoolean).intercept(chain -> {
                if (enabled()) {
                    Resources resources = (Resources) chain.getThisObject();
                    try {
                        if ("com.android.systemui:bool/flag_monet".equals(
                                resources.getResourceName((Integer) chain.getArg(0)))) return true;
                    } catch (Resources.NotFoundException ignored) {}
                }
                return chain.proceed();
            });
        } catch (Throwable error) { Log.w(TAG, "Monet resource flag unavailable", error); }

        try {
            Class<?> controller = Class.forName("com.android.systemui.theme.ThemeOverlayController", false, loader);
            for (Method method : controller.getDeclaredMethods()) {
                String name = method.getName();
                if (!name.equals("start") && !name.equals("reevaluateSystemTheme")
                        && !name.equals("updateThemeOverlays")) continue;
                if (method.getReturnType() != void.class) continue;
                method.setAccessible(true);
                // Some vendor call sites are already optimized before SystemUI initialization.
                try { module.deoptimize(method); } catch (Throwable ignored) {}
                module.hook(method).intercept(chain -> {
                    if (!enabled()) return chain.proceed();
                    Object instance = chain.getThisObject();
                    Object originalFlag = Reflect.getObjectField(instance, "mIsMonetEnabled");
                    if (name.equals("start")) setField(instance, "mIsMonetEnabled", true);
                    try {
                        Object result = chain.proceed();
                        if (name.equals("start")) {
                            Object context = Reflect.getObjectField(instance, "mContext");
                            if (context instanceof Context) registerCallbacks((Context) context);
                        }
                        return result;
                    } catch (RuntimeException error) {
                        // Missing overlay support must not make the SystemUI startup loop crash.
                        if (name.equals("start") && originalFlag instanceof Boolean)
                            setField(instance, "mIsMonetEnabled", originalFlag);
                        Log.w(TAG, "Native Monet update failed: " + name, error);
                        return null;
                    }
                });
            }
        } catch (Throwable error) { Log.w(TAG, "Native Monet controller unavailable", error); }
    }

    static int accent(Resources resources) {
        try {
            boolean dark = (resources.getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                    == Configuration.UI_MODE_NIGHT_YES;
            return resources.getColor(dark ? android.R.color.system_accent1_100
                    : android.R.color.system_accent1_600, null);
        } catch (RuntimeException error) {
            return SystemUiThemeColors.DEFAULT_COLOR;
        }
    }

    private static synchronized void registerCallbacks(Context context) {
        if (callbacksRegistered) return;
        Context app = context.getApplicationContext();
        if (app == null) app = context;
        Handler main = new Handler(Looper.getMainLooper());
        app.registerComponentCallbacks(new ComponentCallbacks() {
            @Override public void onConfigurationChanged(Configuration config) {
                // OMS publishes the palette by updating resource configuration. Run after views
                // finish receiving that update, not immediately when the asynchronous apply starts.
                main.post(() -> {
                    if (enabled()) SystemUiThemeHooks.refreshDynamicColors();
                });
            }
            @Override public void onLowMemory() {}
        });
        callbacksRegistered = true;
    }

    static boolean setField(Object instance, String name, Object value) {
        if (instance == null) return false;
        for (Class<?> owner = instance.getClass(); owner != null; owner = owner.getSuperclass()) {
            try {
                Field field = owner.getDeclaredField(name);
                field.setAccessible(true);
                field.set(instance, value);
                return true;
            } catch (NoSuchFieldException ignored) {
            } catch (ReflectiveOperationException | RuntimeException ignored) { return false; }
        }
        return false;
    }
}
