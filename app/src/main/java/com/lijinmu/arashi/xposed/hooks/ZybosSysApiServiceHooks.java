package com.lijinmu.arashi.xposed.hooks;

import android.util.Log;

import com.lijinmu.arashi.xposed.ConfigManager;
import com.lijinmu.arashi.xposed.HookConfig;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import io.github.libxposed.api.XposedModule;

/** Prevent the vendor service from being constructed or published during boot. */
public final class ZybosSysApiServiceHooks {
    private static final String TAG = "[Arashi-ZybosSysApi]";
    private static final String SERVICE_CLASS = "com.android.server.ZybosSysApiService";
    private static final String SERVICE_NAME = "zybos_manager";

    private ZybosSysApiServiceHooks() {}

    public static void hookSystemServer(XposedModule module, ClassLoader loader) {
        BootState state = new BootState();
        try {
            Class<?> service = Class.forName(SERVICE_CLASS, false, loader);
            Class<?> registry = Class.forName("android.os.ServiceManager", false, loader);
            int factories = 0;
            for (Method method : service.getDeclaredMethods()) {
                if (!method.getName().equals("create")
                        || !Modifier.isStatic(method.getModifiers())
                        || method.getReturnType() != service) continue;
                method.setAccessible(true);
                module.hook(method).intercept(chain -> {
                    // Skip the factory itself: the constructor registers receivers and observers.
                    if (state.removeService()) return null;
                    return chain.proceed();
                });
                deoptimize(module, method);
                factories++;
            }
            if (factories == 0) throw new NoSuchMethodException(SERVICE_CLASS + ".create");

            int registrations = 0;
            for (Method method : registry.getDeclaredMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                if (!method.getName().equals("addService")
                        || !Modifier.isStatic(method.getModifiers())
                        || method.getReturnType() != void.class
                        || parameters.length < 2 || parameters[0] != String.class
                        || !parameters[1].getName().equals("android.os.IBinder")) continue;
                method.setAccessible(true);
                module.hook(method).intercept(chain -> {
                    // SystemServer still calls addService after create returns null.
                    if (SERVICE_NAME.equals(chain.getArg(0)) && state.removeService()) return null;
                    return chain.proceed();
                });
                deoptimize(module, method);
                registrations++;
            }
            if (registrations == 0) throw new NoSuchMethodException("ServiceManager.addService");

            Class<?> systemServer = Class.forName("com.android.server.SystemServer", false, loader);
            for (Method method : systemServer.getDeclaredMethods()) {
                if (method.getName().equals("startOtherServices")
                        || method.getName().contains("lambda$startOtherServices$")) {
                    deoptimize(module, method);
                }
            }
            // Any installation failure leaves all installed interceptors as pass-throughs.
            state.ready = true;
        } catch (ClassNotFoundException error) {
            Log.i(TAG, "Vendor service unavailable; removal hook not enabled");
        } catch (Throwable error) {
            Log.w(TAG, "Unable to install vendor service removal hook", error);
        }
    }

    private static final class BootState {
        volatile boolean ready;
        private Boolean removed;

        synchronized boolean removeService() {
            if (!ready) return false;
            // Keep creation and publication consistent even if preferences change during boot.
            // Restoring a service that was never constructed requires another system restart.
            if (removed == null) {
                removed = ConfigManager.isEnabledCached(HookConfig.KEY_REMOVE_ZYBOS_SYS_API_SERVICE);
                if (removed) Log.i(TAG, "Blocked ZybosSysApiService creation and zybos_manager publication");
            }
            return removed;
        }
    }

    private static void deoptimize(XposedModule module, Method method) {
        try { module.deoptimize(method); }
        catch (Throwable error) { Log.w(TAG, "Unable to deoptimize " + method.getName(), error); }
    }
}
