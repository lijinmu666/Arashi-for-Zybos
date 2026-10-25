package com.lijinmu.arashi.xposed.hooks;

import android.util.Log;
import com.lijinmu.arashi.xposed.ConfigManager;
import com.lijinmu.arashi.xposed.HookConfig;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Constructor;

/** Independently restores the vendor notification long-press gates. */
public final class NotificationLongPressHooks {
    private static final String TAG = "[Arashi-NotificationLongPress]";
    private NotificationLongPressHooks() {}

    public static void hookSystemUI(XposedModule module, ClassLoader loader) {
        // Restore the gates before native row initialization installs its long-press
        // listener. R8 inlines that initialization into NotificationRowBinderImpl on 14.
        restoreNotificationGate(module, loader,
                "com.android.systemui.statusbar.notification.row.ExpandableNotificationRowController",
                "mAllowLongPress", true);
        restoreNotificationGate(module, loader,
                "com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayoutController",
                "mAllowLongPress", true);
        restoreNotificationGate(module, loader,
                "com.android.systemui.statusbar.notification.row.NotificationMenuRow",
                "mShouldShowMenu", false);
    }

    private static void restoreNotificationGate(XposedModule module, ClassLoader loader,
            String name, String field, boolean constructorFlag) {
        try {
            Class<?> type = Class.forName(name, false, loader);
            java.lang.reflect.Field gate = findField(type, field);
            if (gate == null || gate.getType() != boolean.class) return;
            for (Constructor<?> constructor : type.getDeclaredConstructors()) {
                Class<?>[] parameters = constructor.getParameterTypes();
                int booleanIndex = -1;
                int booleanCount = 0;
                for (int i = 0; i < parameters.length; i++) {
                    if (parameters[i] == boolean.class) {
                        booleanIndex = i;
                        booleanCount++;
                    }
                }
                // The supplied 12/14 row and stack controllers have a single boolean
                // constructor argument for allowLongPress. Do not guess on other builds.
                final int allowIndex = constructorFlag && booleanCount == 1 ? booleanIndex : -1;
                module.hook(constructor).intercept(chain -> {
                    if (!ConfigManager.isEnabledCached(HookConfig.KEY_NOTIFICATION_LONG_PRESS)) return chain.proceed();
                    Object result;
                    if (allowIndex >= 0) {
                        Object[] args = new Object[parameters.length];
                        for (int i = 0; i < args.length; i++) args[i] = chain.getArg(i);
                        args[allowIndex] = true;
                        result = chain.proceed(args);
                    } else {
                        result = chain.proceed();
                    }
                    try {
                        gate.setBoolean(chain.getThisObject(), true);
                    } catch (IllegalAccessException | RuntimeException error) {
                        Log.w(TAG, "Notification interaction gate unavailable: " + field, error);
                    }
                    return result;
                });
            }
        } catch (ClassNotFoundException ignored) {
        } catch (Throwable error) { Log.w(TAG, "Notification interaction hook unavailable: " + name, error); }
    }


    private static java.lang.reflect.Field findField(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                java.lang.reflect.Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {}
        }
        return null;
    }
}
