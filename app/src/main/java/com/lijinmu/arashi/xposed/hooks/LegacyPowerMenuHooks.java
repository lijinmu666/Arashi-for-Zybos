package com.lijinmu.arashi.xposed.hooks;

import android.app.Dialog;
import android.os.Handler;
import android.util.Log;
import com.lijinmu.arashi.xposed.ConfigManager;
import com.lijinmu.arashi.xposed.HookConfig;
import com.lijinmu.arashi.xposed.Reflect;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Framework legacy menu, with the two supplied SystemUI footer entry points redirected to it. */
public final class LegacyPowerMenuHooks {
    private static final String TAG = "[Arashi-LegacyPower]";
    private static final ThreadLocal<Boolean> FRAMEWORK_REQUEST = ThreadLocal.withInitial(() -> false);
    private LegacyPowerMenuHooks() {}

    private static boolean enabled() {
        return ConfigManager.isEnabledCached(HookConfig.KEY_LEGACY_POWER_MENU);
    }

    public static void hookSystemServer(XposedModule module, ClassLoader loader) {
        try {
            Class<?> actions = Class.forName("com.android.server.policy.GlobalActions", false, loader);
            // Validate the actual firmware before installing; these are AOSP 12/14 framework targets.
            Class.forName("com.android.server.policy.LegacyGlobalActions", false, loader)
                    .getDeclaredMethod("showDialog", boolean.class, boolean.class);
            actions.getDeclaredMethod("ensureLegacyCreated");
            Field available = actions.getDeclaredField("mGlobalActionsAvailable");
            if (available.getType() != boolean.class) throw new NoSuchFieldException("availability is not boolean");
            available.setAccessible(true);
            Method show = actions.getDeclaredMethod("showDialog", boolean.class, boolean.class);
            show.setAccessible(true);
            module.hook(show).intercept(chain -> {
                if (!enabled()) return chain.proceed();
                Object owner = chain.getThisObject();
                boolean previous = available.getBoolean(owner);
                // Cancel an older SysUI fallback timer before opening the native legacy path.
                Object handler = Reflect.getObjectField(owner, "mHandler");
                Object timeout = Reflect.getObjectField(owner, "mShowTimeout");
                if (handler instanceof Handler && timeout instanceof Runnable)
                    ((Handler) handler).removeCallbacks((Runnable) timeout);
                available.setBoolean(owner, false);
                try {
                    // Keep the original policy checks, lockscreen/provisioning arguments,
                    // legacy dialog lifecycle and shutdown/reboot callbacks.
                    return chain.proceed();
                } finally {
                    // Never mark SystemUI disconnected beyond this single menu request.
                    available.setBoolean(owner, previous);
                }
            });
            deoptimize(module, loader, "com.android.server.policy.PhoneWindowManager", "showGlobalActionsInternal");
            Log.i(TAG, "Framework legacy power menu hook installed");
        } catch (Throwable error) { Log.w(TAG, "Framework legacy menu unavailable; keeping firmware menu", error); }
    }

    public static void hookSystemUI(XposedModule module, ClassLoader loader) {
        try {
            Class<?> implementation = Class.forName("com.android.systemui.globalactions.GlobalActionsImpl", false, loader);
            int wrapped = 0;
            for (Method method : implementation.getDeclaredMethods()) {
                if (!method.getName().equals("showGlobalActions") || method.getParameterCount() != 1) continue;
                method.setAccessible(true);
                module.hook(method).intercept(chain -> {
                    boolean previous = FRAMEWORK_REQUEST.get();
                    FRAMEWORK_REQUEST.set(true);
                    try { return chain.proceed(); }
                    finally { FRAMEWORK_REQUEST.set(previous); }
                });
                wrapped++;
            }
            if (wrapped == 0) throw new NoSuchMethodException("GlobalActionsImpl.showGlobalActions");

            Method windowService = Class.forName("android.view.WindowManagerGlobal", false, loader)
                    .getDeclaredMethod("getWindowManagerService");
            Method requestMenu = Class.forName("android.view.IWindowManager", false, loader)
                    .getDeclaredMethod("showGlobalActions");
            windowService.setAccessible(true);
            requestMenu.setAccessible(true);
            Class<?> dialog = Class.forName("com.android.systemui.globalactions.GlobalActionsDialogLite", false, loader);
            for (Method method : dialog.getDeclaredMethods()) {
                Class<?>[] args = method.getParameterTypes();
                // Sample 12 uses (boolean, boolean), 14 adds an Expandable argument.
                if (!method.getName().equals("showOrHideDialog") || method.getReturnType() != void.class
                        || (args.length != 2 && args.length != 3) || args[0] != boolean.class
                        || args[1] != boolean.class || (args.length == 3
                        && !args[2].getName().equals("com.android.systemui.animation.Expandable"))) continue;
                method.setAccessible(true);
                module.hook(method).intercept(chain -> {
                    // If the framework hook is absent/incompatible, its callback must be allowed
                    // to display the stock menu instead of repeatedly redirecting over Binder.
                    if (!enabled() || FRAMEWORK_REQUEST.get()) return chain.proceed();
                    try {
                        Object service = windowService.invoke(null);
                        if (service != null) {
                            Object old = Reflect.getObjectField(chain.getThisObject(), "mDialog");
                            if (old instanceof Dialog && ((Dialog) old).isShowing()) ((Dialog) old).dismiss();
                            requestMenu.invoke(service);
                            return null;
                        }
                    } catch (ReflectiveOperationException | RuntimeException error) {
                        Log.w(TAG, "Legacy power request failed; keeping firmware menu", error);
                    }
                    return chain.proceed();
                });
                Log.i(TAG, "SystemUI footer redirection installed: " + method);
            }
            // Direct callers confirmed in the supplied 12 and 14 APKs, including the R8 bridge.
            deoptimize(module, loader, "com.android.systemui.qs.QSFooterViewController$2", "onClick");
            deoptimize(module, loader, "com.android.systemui.qs.footer.ui.viewmodel.FooterActionsViewModel$power$1", "invoke");
            deoptimize(module, loader, implementation.getName(), "showGlobalActions");
        } catch (Throwable error) { Log.w(TAG, "SystemUI legacy menu redirection unavailable", error); }
    }

    private static void deoptimize(XposedModule module, ClassLoader loader, String owner, String name) {
        try {
            for (Method method : Class.forName(owner, false, loader).getDeclaredMethods()) {
                if (method.getName().equals(name)) module.deoptimize(method);
            }
        } catch (Throwable error) { Log.w(TAG, "Unable to deoptimize " + owner + "#" + name, error); }
    }
}
