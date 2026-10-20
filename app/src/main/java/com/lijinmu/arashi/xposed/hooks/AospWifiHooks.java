package com.lijinmu.arashi.xposed.hooks;

import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import com.lijinmu.arashi.xposed.ConfigManager;
import com.lijinmu.arashi.xposed.HookConfig;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.WeakHashMap;

/** Replace only the status-bar drawable; leave connectivity and visibility untouched. */
public final class AospWifiHooks {
    private static final String TAG = "[Arashi-Wifi]";
    private static final WeakHashMap<View, Integer> REPLACED = new WeakHashMap<>();
    private AospWifiHooks() {}

    public static void hookSystemUI(XposedModule module, ClassLoader loader) {
        try {
            Class<?> view = Class.forName("com.android.systemui.statusbar.StatusBarWifiView", false, loader);
            Field stateField = field(view, "mState");
            Field iconField = field(view, "mWifiIcon");
            Class<?> stateType = stateField.getType();
            Field resId = field(stateType, "resId");
            Method apply = view.getDeclaredMethod("applyWifiState", stateType);
            apply.setAccessible(true);
            module.hook(apply).intercept(chain -> {
                Object result = chain.proceed();
                View owner = (View) chain.getThisObject();
                try {
                    Object state = stateField.get(owner);
                    if (state == null) {
                        REPLACED.remove(owner);
                        return result;
                    }
                    int original = resId.getInt(state);
                    ImageView icon = (ImageView) iconField.get(owner);
                    if (icon == null || original <= 0) return result;
                    int replacement = ConfigManager.isEnabledCached(HookConfig.KEY_AOSP_WIFI)
                            ? stockIcon(loader, owner, original) : original;
                    Integer previous = REPLACED.get(owner);
                    if (replacement != original || previous != null) {
                        // ImageView retains the native tint; never modify the shared WifiIconState.
                        icon.setImageDrawable(owner.getContext().getDrawable(replacement));
                        if (replacement != original) REPLACED.put(owner, replacement);
                        else REPLACED.remove(owner);
                    }
                } catch (Exception error) {
                    Log.w(TAG, "Stock Wi-Fi icon unavailable; keeping original", error);
                }
                return result;
            });
            Log.i(TAG, "Stock Wi-Fi hooks registered");
        } catch (Throwable error) {
            Log.w(TAG, "Stock Wi-Fi hooks unavailable", error);
        }
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        Field f = type.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    static int stockIcon(ClassLoader loader, View owner, int original) throws Exception {
        Class<?> icons;
        try {
            icons = Class.forName("com.android.systemui.statusbar.connectivity.WifiIcons", true, loader);
        } catch (ClassNotFoundException android12) {
            icons = Class.forName("com.android.systemui.statusbar.policy.WifiIcons", true, loader);
        }
        int[] full = (int[]) field(icons, "WIFI_FULL_ICONS").get(null);
        int[] wifi6 = (int[]) field(icons, "WIFI6_FULL_ICONS").get(null);
        if (full == null || wifi6 == null || full.length != 5 || wifi6.length != 5) return original;
        for (int level = 0; level < 5; level++) {
            if (wifi6[level] == original) return full[level];
        }
        // The normal and no-internet paths already use AOSP resources in both samples.
        // Some Android 12 variants select a vendor activity variant of a normal icon.
        String name = owner.getResources().getResourceEntryName(original);
        if (name.matches("stat_sys_wifi_signal_[0-4]_fully_(in|out|inout)")) {
            int level = name.charAt("stat_sys_wifi_signal_".length()) - '0';
            return full[level];
        }
        return original;
    }
}
