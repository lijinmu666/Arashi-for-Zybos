package com.lijinmu.arashi.xposed.hooks;

import android.util.Log;
import com.lijinmu.arashi.xposed.ConfigManager;
import com.lijinmu.arashi.xposed.HookConfig;
import com.lijinmu.arashi.xposed.NavigationAccessPolicy;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Keeps navigation/shade usable without writing provisioning or activation settings. */
public final class NavigationAccessHooks {
    private static final String TAG = "[Arashi-Navigation]";

    private NavigationAccessHooks() {}

    private static boolean enabled() {
        return ConfigManager.isEnabledCached(HookConfig.KEY_KEEP_NAVIGATION);
    }

    public static void hookSystemServer(XposedModule module, ClassLoader loader) {
        for (Method method : methods(loader, "com.android.server.statusbar.StatusBarManagerService")) {
            Class<?>[] p = method.getParameterTypes();
            // Android 12/14: (displayId, userId, what, token, pkg, which)
            if (!method.getName().equals("disableLocked") || p.length != 6
                    || p[0] != int.class || p[1] != int.class || p[2] != int.class
                    || !p[3].getName().equals("android.os.IBinder")
                    || p[4] != String.class || p[5] != int.class) continue;
            install(module, method, chain -> {
                if (!enabled()) return chain.proceed();
                int which = (Integer) chain.getArg(5);
                int flags = (Integer) chain.getArg(2);
                if (which != 1 && which != 2) return chain.proceed();
                Object[] args = chain.getArgs().toArray();
                args[2] = which == 1 ? NavigationAccessPolicy.disable1(flags)
                        : NavigationAccessPolicy.disable2(flags);
                // Still execute permission/bookkeeping/dispatch logic in the original method.
                return chain.proceed(args);
            });
        }
        for (Method method : methods(loader, "com.android.server.policy.PhoneWindowManager")) {
            if (!method.getName().equals("isUserSetupComplete")
                    || method.getReturnType() != boolean.class || method.getParameterCount() != 0) continue;
            install(module, method, chain -> enabled()
                    && NavigationAccessPolicy.isNavigationKeyCaller(Thread.currentThread().getStackTrace())
                    ? true : chain.proceed());
        }
        deoptimize(module, loader, "com.android.server.statusbar.StatusBarManagerService",
                "disable", "disableForUser", "disable2", "disable2ForUser", "setDisableFlags");
        deoptimize(module, loader, "com.android.server.policy.PhoneWindowManager",
                "interceptKeyBeforeDispatching", "launchHomeFromHotKey", "startDockOrHome",
                "showRecentApps", "toggleRecentApps");
    }

    public static void hookSystemUI(XposedModule module, ClassLoader loader) {
        for (Method method : methods(loader, "com.android.systemui.statusbar.CommandQueue")) {
            Class<?>[] p = method.getParameterTypes();
            if (!method.getName().equals("disable") || (p.length != 3 && p.length != 4)
                    || p[0] != int.class || p[1] != int.class || p[2] != int.class
                    || (p.length == 4 && p[3] != boolean.class)) continue;
            install(module, method, chain -> {
                if (!enabled()) return chain.proceed();
                Object[] args = chain.getArgs().toArray();
                args[1] = NavigationAccessPolicy.disable1((Integer) args[1]);
                args[2] = NavigationAccessPolicy.disable2((Integer) args[2]);
                return chain.proceed(args);
            });
        }
        // Only this SystemUI process sees the vendor panel flag as enabled.
        // The real setting remains untouched, so disabling the hook restores its meaning.
        for (Method method : methods(loader, "android.provider.Settings$Global")) {
            Class<?>[] p = method.getParameterTypes();
            if (!method.getName().equals("getInt") || method.getReturnType() != int.class
                    || (p.length != 2 && p.length != 3) || p[1] != String.class
                    || !p[0].getName().equals("android.content.ContentResolver")
                    || (p.length == 3 && p[2] != int.class)) continue;
            install(module, method, chain -> enabled() && "control_panelbar".equals(chain.getArg(1))
                    ? 0 : chain.proceed());
        }
        for (Method method : methods(loader,
                "com.android.systemui.statusbar.policy.DeviceProvisionedControllerImpl")) {
            boolean noArg = method.getParameterCount() == 0
                    && (method.getName().equals("isDeviceProvisioned")
                        || method.getName().equals("isCurrentUserSetup"));
            boolean byUser = method.getName().equals("isUserSetup") && method.getParameterCount() == 1
                    && method.getParameterTypes()[0] == int.class;
            if ((!noArg && !byUser) || method.getReturnType() != boolean.class) continue;
            install(module, method, chain -> enabled()
                    && NavigationAccessPolicy.isNavigationUiCaller(Thread.currentThread().getStackTrace())
                    ? true : chain.proceed());
        }
        for (Method method : methods(loader, "com.android.systemui.navigationbar.NavigationBar")) {
            if (method.getName().equals("shouldDisableNavbarGestures")
                    && method.getReturnType() == boolean.class && method.getParameterCount() == 0) {
                install(module, method, chain -> enabled() ? false : chain.proceed());
            }
        }
        // These small setting/provisioning getters can be inlined in precompiled SystemUI.
        deoptimize(module, loader, "com.android.systemui.statusbar.CommandQueue", "panelsEnabled");
        deoptimize(module, loader, "com.android.systemui.navigationbar.NavigationBar",
                "shouldDisableNavbarGestures", "onHomeTouch", "onRecentsClick", "onNavigationModeChanged");
        deoptimize(module, loader, "com.android.systemui.statusbar.phone.StatusBar",
                "updateQsExpansionEnabled", "animateExpandNotificationsPanel",
                "animateExpandSettingsPanel", "togglePanel");
        deoptimize(module, loader, "com.android.systemui.statusbar.phone.StatusBar$1", "onUserSetupChanged");
        deoptimize(module, loader, "com.android.systemui.statusbar.phone.CentralSurfacesImpl",
                "updateQsExpansionEnabled", "animateExpandNotificationsPanel",
                "animateExpandSettingsPanel", "togglePanel");
    }

    public static void hookLauncher(XposedModule module, ClassLoader loader, boolean legacy) {
        // Raw DEX names in the supplied Launcher 12/14 samples, not JADX's renamed f... fields.
        String bridgeName = legacy ? "com.android.launcher3.g5" : "com.android.launcher3.g6";
        Field recent = field(loader, bridgeName, "s");
        Field blockTouch = field(loader, bridgeName, "t");
        if (recent == null || blockTouch == null) {
            Log.w(TAG, "Launcher navigation bridge unavailable: " + bridgeName);
            return;
        }
        // Restore immediately after setup/ErrorActivity writes the vendor bridge flags.
        for (Method method : methods(loader, "com.zuoyebang.iot.pad.launcher.w0")) {
            Class<?>[] p = method.getParameterTypes();
            if (!method.getName().equals("a") || p.length != 1
                    || !p[0].getName().equals("android.app.Application")) continue;
            install(module, method, chain -> {
                Object result = chain.proceed();
                restoreBridge(recent, blockTouch);
                return result;
            });
        }
        for (Method method : methods(loader, "com.zuoyebang.iot.pad.launcher.ErrorActivity")) {
            if (!method.getName().equals("onCreate") && !method.getName().equals("onResume")) continue;
            install(module, method, chain -> {
                Object result = chain.proceed();
                restoreBridge(recent, blockTouch);
                return result;
            });
        }
        // A12 tests the flag inside the binder callback; A14 defers it to q0.accept.
        // Recheck at the point of use to cover later writes, including uninstall UI.
        for (Method method : methods(loader, "com.android.systemui.shared.recents.IOverviewProxy$Stub")) {
            if (!method.getName().equals("onTransact")) continue;
            install(module, method, chain -> {
                restoreBridge(recent, blockTouch);
                return chain.proceed();
            });
        }
        if (!legacy) {
            for (Method method : methods(loader, "com.android.launcher3.q0")) {
                if (!method.getName().equals("accept") || method.getParameterCount() != 1) continue;
                install(module, method, chain -> {
                    Object target = chain.getArg(0);
                    if (target != null && target.getClass().getName().equals(
                            "com.android.quickstep.TouchInteractionService")) restoreBridge(recent, blockTouch);
                    return chain.proceed();
                });
            }
        }
        for (Method method : methods(loader, "com.android.launcher3.views.BaseDragLayer")) {
            if (!method.getName().equals("onInterceptTouchEvent")) continue;
            install(module, method, chain -> {
                restoreBridge(recent, blockTouch);
                return chain.proceed();
            });
        }
    }

    private static void restoreBridge(Field recent, Field blockTouch) {
        if (!enabled()) return;
        try {
            recent.set(null, Boolean.TRUE);
            blockTouch.set(null, Boolean.FALSE);
        } catch (IllegalAccessException error) {
            Log.w(TAG, "Unable to restore launcher navigation", error);
        }
    }

    private static Field field(ClassLoader loader, String owner, String name) {
        try {
            Field field = Class.forName(owner, false, loader).getDeclaredField(name);
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != Boolean.class) return null;
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException ignored) { return null; }
    }

    private static Method[] methods(ClassLoader loader, String owner) {
        try { return Class.forName(owner, false, loader).getDeclaredMethods(); }
        catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            Log.w(TAG, "Optional navigation target unavailable: " + owner);
            return new Method[0];
        }
    }

    private static void install(XposedModule module, Method method,
            io.github.libxposed.api.XposedInterface.Hooker hooker) {
        try {
            method.setAccessible(true);
            module.hook(method).intercept(hooker);
            Log.i(TAG, "Hooked " + method.getDeclaringClass().getName() + "#" + method.getName());
        } catch (RuntimeException | LinkageError error) {
            Log.w(TAG, "Unable to hook " + method, error);
        }
    }

    private static void deoptimize(XposedModule module, ClassLoader loader, String owner, String... names) {
        for (Method method : methods(loader, owner)) {
            for (String name : names) {
                if (!method.getName().equals(name)) continue;
                try { module.deoptimize(method); }
                catch (RuntimeException | LinkageError error) {
                    Log.w(TAG, "Unable to deoptimize " + method, error);
                }
                break;
            }
        }
    }
}
