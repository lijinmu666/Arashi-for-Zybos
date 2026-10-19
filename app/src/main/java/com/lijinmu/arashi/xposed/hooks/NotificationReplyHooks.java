package com.lijinmu.arashi.xposed.hooks;

import android.util.Log;
import com.lijinmu.arashi.xposed.ConfigManager;
import com.lijinmu.arashi.xposed.HookConfig;
import com.lijinmu.arashi.xposed.Reflect;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Avoid the remote-input-only QS visibility transition on the vendor landscape shade. */
public final class NotificationReplyHooks {
    private static final String TAG = "[Arashi-Reply]";
    private NotificationReplyHooks() {}

    public static void hookSystemUI(XposedModule module, ClassLoader loader) {
        // Android 12 keeps this helper. Preserve every flag supplied by the caller;
        // only skip the extra DISABLE2_QUICK_SETTINGS bit introduced for remote input.
        try {
            Class<?> owner = Class.forName(
                    "com.android.systemui.statusbar.policy.RemoteInputQuickSettingsDisabler", false, loader);
            Method method = owner.getDeclaredMethod("adjustDisableFlags", int.class);
            method.setAccessible(true);
            module.hook(method).intercept(chain -> ConfigManager.isEnabledCached(HookConfig.KEY_NOTIFICATION_REPLY)
                    ? chain.getArg(0) : chain.proceed());
            Log.i(TAG, "Legacy remote-input QS adjustment hooked");
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            // Android 14's R8 build inlines the condition into both callbacks below.
        } catch (Throwable error) {
            Log.w(TAG, "Legacy reply hook unavailable", error);
        }
        hookDisable(module, loader, "com.android.systemui.statusbar.phone.StatusBar");
        hookDisable(module, loader, "com.android.systemui.statusbar.phone.CentralSurfacesCommandQueueCallbacks");
        hookDisable(module, loader, "com.android.systemui.qs.QSFragment");
    }

    private static void hookDisable(XposedModule module, ClassLoader loader, String name) {
        try {
            Class<?> owner = Class.forName(name, false, loader);
            Method method = owner.getDeclaredMethod("disable", int.class, int.class, int.class, boolean.class);
            method.setAccessible(true);
            module.hook(method).intercept(chain -> {
                if (!ConfigManager.isEnabledCached(HookConfig.KEY_NOTIFICATION_REPLY)) return chain.proceed();
                Object disabler = Reflect.getObjectField(chain.getThisObject(), "mRemoteInputQuickSettingsDisabler");
                Field landscape = null;
                try {
                    if (disabler != null && disabler.getClass().getName().equals(
                            "com.android.systemui.statusbar.policy.RemoteInputQuickSettingsDisabler")
                            && Boolean.TRUE.equals(Reflect.getObjectField(disabler, "remoteInputActive"))
                            && Boolean.FALSE.equals(Reflect.getObjectField(disabler, "shouldUseSplitNotificationShade"))) {
                        Field candidate = disabler.getClass().getDeclaredField("isLandscape");
                        candidate.setAccessible(true);
                        if (candidate.getBoolean(disabler)) {
                            candidate.setBoolean(disabler, false);
                            landscape = candidate;
                        }
                    }
                } catch (ReflectiveOperationException error) {
                    Log.w(TAG, "Reply layout state unavailable", error);
                }
                // The callbacks run synchronously on SystemUI's main thread. This
                // changes only their inlined remote-input condition, not device orientation.
                try {
                    return chain.proceed();
                } finally {
                    if (landscape != null) {
                        try { landscape.setBoolean(disabler, true); }
                        catch (IllegalAccessException error) { Log.w(TAG, "Reply state restore failed", error); }
                    }
                }
            });
            module.deoptimize(method);
            Log.i(TAG, "Reply QS callback hooked: " + name);
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
        } catch (Throwable error) {
            Log.w(TAG, "Reply callback hook unavailable: " + name, error);
        }
    }
}
