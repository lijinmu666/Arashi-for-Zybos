package com.lijinmu.arashi.xposed.hooks;

import android.content.res.Resources;
import android.util.Log;
import com.lijinmu.arashi.xposed.ConfigManager;
import com.lijinmu.arashi.xposed.HookConfig;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Method;

/** Both supplied SystemUI versions select this resource in PhoneStatusBarPolicy.updateBluetooth.
 * Match the owning package and resource name, including Android 14's inlined resource IDs.
 */
public final class AospBluetoothHooks {
    private static final String TAG = "[Arashi-Bluetooth]";
    private static final String CONNECTED = "com.android.systemui:drawable/ic_bluetooth_connected_zyb";
    private static final ThreadLocal<Boolean> BUSY = ThreadLocal.withInitial(() -> false);
    private static boolean logged;
    private AospBluetoothHooks() {}

    public static void hookSystemUI(XposedModule module) {
        for (Method method : Resources.class.getDeclaredMethods()) {
            String name = method.getName();
            if ((!name.equals("getDrawable") && !name.equals("getDrawableForDensity"))
                    || method.getParameterCount() == 0 || method.getParameterTypes()[0] != int.class) continue;
            try {
                module.hook(method).intercept(chain -> {
                    if (BUSY.get() || !ConfigManager.isEnabledCached(HookConfig.KEY_AOSP_STATUS_ICONS)) {
                        return chain.proceed();
                    }
                    BUSY.set(true);
                    try {
                        Resources resources = (Resources) chain.getThisObject();
                        int id = (Integer) chain.getArg(0);
                        if (CONNECTED.equals(resources.getResourceName(id))) {
                            float density = resources.getDisplayMetrics().density;
                            if (name.equals("getDrawableForDensity") && method.getParameterCount() > 1) {
                                int requestedDensity = (Integer) chain.getArg(1);
                                if (requestedDensity > 0) density = requestedDensity / 160f;
                            }
                            if (!logged) {
                                logged = true;
                                Log.i(TAG, "Replacing connected Bluetooth status icon with AOSP vector");
                            }
                            return new AospBluetoothDrawable(density);
                        }
                    } catch (Exception error) {
                        Log.w(TAG, "Bluetooth replacement unavailable; using firmware drawable", error);
                    } finally { BUSY.set(false); }
                    return chain.proceed();
                });
            } catch (Throwable error) { Log.w(TAG, "Unable to hook " + method, error); }
        }
    }
}
