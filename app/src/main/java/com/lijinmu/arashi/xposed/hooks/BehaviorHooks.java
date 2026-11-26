package com.lijinmu.arashi.xposed.hooks;

import android.content.ContentResolver;
import android.provider.Settings;
import android.util.Log;

import com.lijinmu.arashi.xposed.BehaviorCompatibility;
import com.lijinmu.arashi.xposed.ConfigManager;
import com.lijinmu.arashi.xposed.HookConfig;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;

import io.github.libxposed.api.XposedModule;

/** Vendor wireless-debugging policy and boot-time unwanted-app removal. */
public final class BehaviorHooks {
    private static final String TAG = "[Arashi-Behavior]";
    private static final ThreadLocal<Boolean> adbSystemReady = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> appCleanup = new ThreadLocal<>();

    private BehaviorHooks() {}

    public static void hookSystemServer(XposedModule module, ClassLoader loader) {
        hookWirelessSettings(module);
        hookAdbService(module, loader);
        hookAppCleanup(module, loader);
        hookPadMsWatchdog(module, loader);
        hookForcedFreeze(module, loader);
    }

    /** Disable only the vendor enforced-disabled policy, preserving Android admin protections. */
    private static void hookForcedFreeze(XposedModule module, ClassLoader loader) {
        if (!BehaviorCompatibility.isForcedFreezeSupported()) return;
        try {
            Class<?> protectedPackages = Class.forName("com.android.server.pm.ProtectedPackages", false, loader);
            Method check = protectedPackages.getDeclaredMethod("isZybEnforcedDisabled", String.class);
            if (check.getReturnType() != boolean.class) return;
            check.setAccessible(true);
            module.hook(check).intercept(chain ->
                    ConfigManager.isEnabledCached(HookConfig.KEY_DISABLE_FORCED_FREEZE)
                            ? false : chain.proceed());
        } catch (Throwable error) {
            Log.w(TAG, "Enforced-disabled policy check unavailable on this firmware", error);
        }
        // Keep the backing collection empty too, including when callers inline the check.
        try {
            Class<?> protectedPackages = Class.forName("com.android.server.pm.ProtectedPackages", false, loader);
            Method set = protectedPackages.getDeclaredMethod("setZybEnforcedDisabledPackages", java.util.Set.class);
            if (set.getReturnType() == void.class) {
                set.setAccessible(true);
                module.hook(set).intercept(chain -> {
                    if (!ConfigManager.isEnabledCached(HookConfig.KEY_DISABLE_FORCED_FREEZE)) return chain.proceed();
                    clearEnforcedDisabled(chain.getThisObject());
                    return null;
                });
            }
        } catch (Throwable error) {
            Log.w(TAG, "Enforced-disabled policy setter unavailable on this firmware", error);
        }
        try {
            Class<?> pms = Class.forName("com.android.server.pm.PackageManagerService", false, loader);
            for (String name : new String[] { "registerZybEnforcedDisabledObserver", "refreshZybEnforcedDisabledPackages" }) {
                try {
                    Method method = pms.getDeclaredMethod(name);
                    if (method.getReturnType() != void.class) continue;
                    method.setAccessible(true);
                    Field field = pms.getDeclaredField("mProtectedPackages");
                    field.setAccessible(true);
                    module.hook(method).intercept(chain -> {
                        if (!ConfigManager.isEnabledCached(HookConfig.KEY_DISABLE_FORCED_FREEZE)) return chain.proceed();
                        try { clearEnforcedDisabled(field.get(chain.getThisObject())); }
                        catch (Throwable error) { Log.w(TAG, "Unable to access enforced-disabled policy", error); }
                        return null;
                    });
                } catch (Throwable error) {
                    Log.w(TAG, "Enforced-disabled policy loader unavailable: " + name, error);
                }
            }
            for (Method caller : pms.getDeclaredMethods()) {
                String name = caller.getName();
                if (name.equals("systemReady") || name.equals("setEnabledSetting")
                        || name.equals("updateSettingsInternalLI")
                        || name.equals("markPackageUninstalledForUserLPw")
                        || name.startsWith("access$")) deoptimize(module, caller);
            }
        } catch (Throwable error) {
            Log.w(TAG, "Enforced-disabled PMS policy unavailable on this firmware", error);
        }
    }

    private static void clearEnforcedDisabled(Object owner) {
        if (owner == null) return;
        try {
            Field field = owner.getClass().getDeclaredField("mZybEnforcedDisabledPackages");
            field.setAccessible(true);
            synchronized (owner) {
                Object value = field.get(owner);
                if (value instanceof Collection<?>) ((Collection<?>) value).clear();
            }
        } catch (Throwable error) {
            Log.w(TAG, "Unable to clear enforced-disabled policy collection", error);
        }
    }

    /** Verified against AIbangOS 7.14.0 services.jar; absent methods are left alone. */
    private static void hookPadMsWatchdog(XposedModule module, ClassLoader loader) {
        try {
            Class<?> service = Class.forName("com.android.server.ZybosSysApiService", false, loader);
            // Resolve both signatures before installing either interceptor.
            Method start = service.getDeclaredMethod("startPadMsWatchdog");
            Method ensure = service.getDeclaredMethod("ensurePadMsAlive");
            if (start.getReturnType() != void.class || ensure.getReturnType() != void.class) return;
            for (Method method : new Method[] { start, ensure }) {
                method.setAccessible(true);
                module.hook(method).intercept(chain ->
                        ConfigManager.isEnabledCached(HookConfig.KEY_DISABLE_PADMS_WATCHDOG)
                                ? null : chain.proceed());
            }
            // These private methods can be inlined in systemReady and the synthetic accessor.
            for (Method caller : service.getDeclaredMethods()) {
                if (caller.getName().equals("systemReady")
                        || caller.getName().startsWith("access$")) deoptimize(module, caller);
            }
            // The 7.14.0 periodic callback invokes ensurePadMsAlive via access$500.
            try {
                Class<?> callback = Class.forName("com.android.server.ZybosSysApiService$3", false, loader);
                deoptimize(module, callback.getDeclaredMethod("run"));
            } catch (Throwable error) {
                Log.w(TAG, "PadMs watchdog callback deoptimization unavailable", error);
            }
        } catch (Throwable error) {
            Log.w(TAG, "PadMs watchdog hook unavailable on this firmware", error);
        }
    }

    private static void hookWirelessSettings(XposedModule module) {
        try {
            Method getInt = Settings.Global.class.getDeclaredMethod(
                    "getInt", ContentResolver.class, String.class, int.class);
            module.hook(getInt).intercept(chain -> {
                if (ConfigManager.isEnabledCached(HookConfig.KEY_ALLOW_WIRELESS_DEBUGGING)) {
                    String key = (String) chain.getArg(1);
                    // Limit the special-settings override to AdbService's boot policy.
                    if (Boolean.TRUE.equals(adbSystemReady.get())
                            && "zpad_prop_special_settings".equals(key)) return 1;
                    // Cleanup must not turn wireless debugging off when this Hook alone is on.
                    if (Boolean.TRUE.equals(appCleanup.get())
                            && "adb_wifi_enabled".equals(key)) return 0;
                }
                return chain.proceed();
            });
        } catch (Throwable error) {
            Log.w(TAG, "Wireless debugging settings hook unavailable", error);
        }
    }

    private static void hookAdbService(XposedModule module, ClassLoader loader) {
        try {
            Class<?> service = Class.forName("com.android.server.adb.AdbService", false, loader);
            Method disable = service.getDeclaredMethod("shouldDisableWifiAdb");
            disable.setAccessible(true);
            module.hook(disable).intercept(chain ->
                    ConfigManager.isEnabledCached(HookConfig.KEY_ALLOW_WIRELESS_DEBUGGING)
                            ? false : chain.proceed());
            Method ready = service.getDeclaredMethod("systemReady");
            ready.setAccessible(true);
            module.hook(ready).intercept(chain -> {
                Boolean previous = adbSystemReady.get();
                adbSystemReady.set(Boolean.TRUE);
                try { return chain.proceed(); }
                finally { restore(adbSystemReady, previous); }
            });
            // The small vendor policy helper can be inlined into this caller.
            deoptimize(module, service.getDeclaredMethod("setAdbEnabled", boolean.class, byte.class));
            deoptimize(module, ready);
        } catch (Throwable error) {
            Log.w(TAG, "Vendor wireless debugging policy hook unavailable", error);
        }
    }

    private static void hookAppCleanup(XposedModule module, ClassLoader loader) {
        try {
            Class<?> ams = Class.forName("com.android.server.am.ActivityManagerService", false, loader);
            Method cleanup = ams.getDeclaredMethod("deleteIllegalApps");
            cleanup.setAccessible(true);
            module.hook(cleanup).intercept(chain -> {
                if (ConfigManager.isEnabledCached(HookConfig.KEY_BLOCK_APP_UNINSTALL)) {
                    // Clear the actual hardcoded removal blacklist before skipping execution.
                    clearRemovalBlacklist(ams);
                    return null;
                }
                Boolean previous = appCleanup.get();
                appCleanup.set(Boolean.TRUE);
                try { return chain.proceed(); }
                finally { restore(appCleanup, previous); }
            });
            deoptimize(module, cleanup);
            for (Constructor<?> constructor : ams.getDeclaredConstructors()) {
                module.hook(constructor).intercept(chain -> {
                    Object result = chain.proceed();
                    if (ConfigManager.isEnabledCached(HookConfig.KEY_BLOCK_APP_UNINSTALL)) {
                        clearRemovalBlacklist(ams);
                    }
                    return result;
                });
            }
            for (Method caller : ams.getDeclaredMethods()) {
                if (caller.getName().contains("lambda$systemReady$")) deoptimize(module, caller);
            }
        } catch (Throwable error) {
            Log.w(TAG, "Vendor app cleanup hook unavailable", error);
        }
    }

    private static void clearRemovalBlacklist(Class<?> ams) {
        try {
            Field field = ams.getDeclaredField("mZybMustDeletedApps");
            if (!Modifier.isStatic(field.getModifiers())) return;
            field.setAccessible(true);
            Object value = field.get(null);
            if (value instanceof Collection<?>) ((Collection<?>) value).clear();
        } catch (Throwable error) {
            // Still block cleanup if a different firmware changes the blacklist field.
            Log.w(TAG, "Unable to clear vendor app removal blacklist", error);
        }
    }

    private static void restore(ThreadLocal<Boolean> local, Boolean previous) {
        if (previous == null) local.remove();
        else local.set(previous);
    }

    private static void deoptimize(XposedModule module, Method method) {
        try { module.deoptimize(method); }
        catch (Throwable error) { Log.w(TAG, "Unable to deoptimize " + method.getName(), error); }
    }
}
