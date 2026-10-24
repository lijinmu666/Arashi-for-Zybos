package com.lijinmu.arashi.xposed.hooks;

import android.content.res.Resources;
import android.util.Log;
import com.lijinmu.arashi.xposed.ConfigManager;
import com.lijinmu.arashi.xposed.HookConfig;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/** Match exact Wi-Fi resource IDs, covering both legacy and modern status bar pipelines. */
public final class AospWifiHooks {
    private static final String TAG = "[Arashi-Wifi]";
    private static final ThreadLocal<Boolean> BUSY = ThreadLocal.withInitial(() -> false);
    private static volatile Map<Integer, Integer> icons;
    private static boolean logged;
    private AospWifiHooks() {}

    public static void hookSystemUI(XposedModule module, ClassLoader loader) {
        for (Method method : Resources.class.getDeclaredMethods()) {
            String name = method.getName();
            if (!name.equals("getDrawable") && !name.equals("getDrawableForDensity")) continue;
            if (method.getParameterCount() == 0 || method.getParameterTypes()[0] != int.class) continue;
            try {
                module.hook(method).intercept(chain -> {
                    if (BUSY.get() || !ConfigManager.isEnabledCached(HookConfig.KEY_AOSP_STATUS_ICONS))
                        return chain.proceed();
                    BUSY.set(true);
                    try {
                        Map<Integer, Integer> map = icons;
                        if (map == null) {
                            map = resolve(loader);
                            icons = map;
                        }
                        int id = (Integer) chain.getArg(0);
                        Integer index = map.get(id);
                        if (index != null) {
                            Resources resources = (Resources) chain.getThisObject();
                            if (!logged) {
                                logged = true;
                                Log.i(TAG, "Replacing Wi-Fi resource " + resources.getResourceName(id));
                            }
                            float density = resources.getDisplayMetrics().density;
                            if (name.equals("getDrawableForDensity") && method.getParameterCount() > 1) {
                                int requestedDensity = (Integer) chain.getArg(1);
                                if (requestedDensity > 0) density = requestedDensity / 160f;
                            }
                            return new AospWifiDrawable(index, density);
                        }
                    } catch (Exception error) {
                        Log.w(TAG, "Wi-Fi replacement unavailable; using vendor drawable", error);
                    } finally { BUSY.set(false); }
                    return chain.proceed();
                });
            } catch (Throwable error) {
                Log.w(TAG, "Unable to hook " + method, error);
            }
        }
    }

    private static Map<Integer, Integer> resolve(ClassLoader loader) throws Exception {
        Class<?> type;
        try {
            type = Class.forName("com.android.systemui.statusbar.connectivity.WifiIcons", true, loader);
        } catch (ClassNotFoundException android12) {
            type = Class.forName("com.android.systemui.statusbar.policy.WifiIcons", true, loader);
        }
        Map<Integer, Integer> map = new HashMap<>();
        add(map, type, "WIFI_FULL_ICONS", 0);
        add(map, type, "WIFI6_FULL_ICONS", 0);
        add(map, type, "WIFI_NO_INTERNET_ICONS", 5);
        return map;
    }

    private static void add(Map<Integer, Integer> map, Class<?> type, String name, int offset)
            throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        int[] ids = (int[]) field.get(null);
        if (ids == null || ids.length != 5) throw new IllegalStateException("Unexpected Wi-Fi levels");
        for (int level = 0; level < 5; level++) if (ids[level] > 0) map.put(ids[level], offset + level);
    }
}
