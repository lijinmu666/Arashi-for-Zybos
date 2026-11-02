package com.lijinmu.arashi.xposed.hooks;

import android.os.Handler;
import android.util.Log;

import com.lijinmu.arashi.xposed.ConfigManager;
import com.lijinmu.arashi.xposed.HookConfig;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;

/** Removes Zybos 14's delayed boot-animation exit without bypassing WMS readiness gates. */
public final class BootAnimationHooks {
    private static final String TAG = "[Arashi-BootAnimation]";
    // Verified in the supplied A14 7.10 services.jar: H handles 101 by setting exit=1.
    private static final int BOOT_ANIMATION_EXIT = 101;

    private BootAnimationHooks() {}

    public static void hookSystemServer(XposedModule module, ClassLoader loader) {
        try {
            Class<?> wms = Class.forName("com.android.server.wm.WindowManagerService", false, loader);
            // This vendor-only field makes unsupported/AOSP firmware a no-op.
            Field delayed = field(wms, "mDelayedExitAnimation", boolean.class);
            Field stopped = field(wms, "mBootAnimationStopped", boolean.class);
            Field booted = field(wms, "mSystemBooted", boolean.class);
            Field displayed = field(wms, "mDisplayEnabled", boolean.class);
            Field handler = wms.getDeclaredField("mH");
            Field lock = wms.getDeclaredField("mGlobalLock");
            handler.setAccessible(true);
            lock.setAccessible(true);
            if (!Handler.class.isAssignableFrom(handler.getType()))
                throw new NoSuchFieldException("mH is not a Handler");
            Method set = Class.forName("android.os.SystemProperties", false, loader)
                    .getDeclaredMethod("set", String.class, String.class);
            set.setAccessible(true);
            Method enable = wms.getDeclaredMethod("performEnableScreen");
            enable.setAccessible(true);
            module.hook(enable).intercept(chain -> {
                // Run all original boot, policy, window-drawn and SurfaceControl checks first.
                Object result = chain.proceed();
                if (!ConfigManager.isEnabledCached(HookConfig.KEY_BOOT_ANIMATION_NO_WAIT)) return result;
                try {
                    Object owner = chain.getThisObject();
                    Object globalLock = lock.get(owner);
                    if (globalLock == null) return result;
                    synchronized (globalLock) {
                        Handler h = (Handler) handler.get(owner);
                        if (booted.getBoolean(owner) && displayed.getBoolean(owner)
                                && stopped.getBoolean(owner) && delayed.getBoolean(owner)
                                && h != null && h.hasMessages(BOOT_ANIMATION_EXIT)) {
                            // Keep the vendor timeout if the property write fails.
                            set.invoke(null, "service.bootanim.exit", "1");
                            h.removeMessages(BOOT_ANIMATION_EXIT);
                            Log.i(TAG, "Display ready; requested boot animation exit without 60s delay");
                        }
                    }
                } catch (Throwable error) {
                    Log.w(TAG, "Unable to end animation; keeping firmware fallback", error);
                }
                return result;
            });
            // performEnableScreen is final and can be inlined into these firmware callers.
            for (Method caller : wms.getDeclaredMethods()) {
                String name = caller.getName();
                if (name.equals("enableScreenAfterBoot") || name.equals("performBootTimeout")
                        || name.equals("showBootMessage")) deoptimize(module, caller);
            }
            Class<?> h = handler.getType();
            for (Method caller : h.getDeclaredMethods()) {
                if (caller.getName().equals("handleMessage")) deoptimize(module, caller);
            }
            Log.i(TAG, "Vendor boot animation delay hook installed");
        } catch (Throwable error) {
            Log.w(TAG, "Vendor boot animation delay hook unavailable; keeping firmware behavior", error);
        }
    }

    private static Field field(Class<?> owner, String name, Class<?> type) throws ReflectiveOperationException {
        Field result = owner.getDeclaredField(name);
        if (result.getType() != type) throw new NoSuchFieldException(name + " has unexpected type");
        result.setAccessible(true);
        return result;
    }

    private static void deoptimize(XposedModule module, Method method) {
        try { module.deoptimize(method); }
        catch (Throwable error) { Log.w(TAG, "Unable to deoptimize " + method.getName(), error); }
    }
}
