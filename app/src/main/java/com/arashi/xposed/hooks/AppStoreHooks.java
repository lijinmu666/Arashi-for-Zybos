package com.arashi.xposed.hooks;

import android.util.Log;

import com.arashi.xposed.ConfigManager;
import com.arashi.xposed.HookConfig;
import com.arashi.xposed.Reflect;

import io.github.libxposed.api.XposedModule;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Hooks the app store's automatic update queue while leaving manual updates alone. */
public final class AppStoreHooks {
    private static final String TAG = "[Arashi-AppStore]";
    private static final String AUTO_UPDATER = "b7.c";
    private static final String APK_ENTITY_V =
            "com.zuoyebang.iot.pad.appstore.manage.entity.ApkEntityV";
    private static final int APP_TYPE_SYSTEM = 2;

    private AppStoreHooks() {
    }

    public static void hookAppStore(XposedModule module, ClassLoader classLoader) {
        try {
            Class<?> updaterClass = Class.forName(AUTO_UPDATER, false, classLoader);
            Method queueBuilder = Reflect.findMethodExact(updaterClass, "q", List.class);
            if (queueBuilder == null) {
                Log.w(TAG, "automatic update queue builder not found");
                return;
            }

            Class<?> updateEntityClass = Class.forName(APK_ENTITY_V, false, classLoader);
            Method getType = Reflect.findMethodExact(updateEntityClass, "getType");
            if (getType == null) {
                Log.w(TAG, "update type accessor not found");
                return;
            }

            module.hook(queueBuilder).intercept(chain -> {
                if (!ConfigManager.isEnabled(HookConfig.KEY_BLOCK_SYSTEM_APP_AUTO_UPDATE)) {
                    return chain.proceed();
                }

                List<Object> args = chain.getArgs();
                if (args.isEmpty() || !(args.get(0) instanceof List<?>)) {
                    return chain.proceed();
                }

                List<?> candidates = (List<?>) args.get(0);
                ArrayList<Object> filtered = new ArrayList<>(candidates.size());
                int blocked = 0;
                for (Object candidate : candidates) {
                    if (candidate != null && updateEntityClass.isInstance(candidate)) {
                        try {
                            if (((Number) getType.invoke(candidate)).intValue() == APP_TYPE_SYSTEM) {
                                blocked++;
                                continue;
                            }
                        } catch (Throwable error) {
                            // Keep entries whose type cannot be read, so a mismatch cannot
                            // accidentally suppress a user app update.
                        }
                    }
                    filtered.add(candidate);
                }

                if (blocked == 0) {
                    return chain.proceed();
                }
                Log.i(TAG, "filtered " + blocked + " system app(s) from automatic update queue");
                return chain.proceed(new Object[] { filtered });
            });
            Log.i(TAG, "automatic system app update filter installed");
        } catch (Throwable error) {
            Log.w(TAG, "automatic system app update filter failed", error);
        }
    }
}
