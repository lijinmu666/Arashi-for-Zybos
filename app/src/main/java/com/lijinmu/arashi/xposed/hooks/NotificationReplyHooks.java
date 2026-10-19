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
    private static final ThreadLocal<Object> OPENING_EDIT = new ThreadLocal<>();
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
        hookReplyDiagnostics(module, loader);

    }

    // Record only lifecycle state and callers, never reply text or notification contents.
    private static void hookReplyDiagnostics(XposedModule module, ClassLoader loader) {
        String prefix = "com.android.systemui.statusbar.policy.RemoteInputView";
        for (String name : new String[] {prefix, prefix + "$RemoteEditText"}) {
            try {
                Class<?> owner = Class.forName(name, false, loader);
                for (Method method : owner.getDeclaredMethods()) {
                    String event = method.getName();
                    if (!(event.equals("focus") || event.equals("onDefocus")
                            || event.equals("defocusIfNeeded") || event.equals("onFocusChanged"))) continue;
                    method.setAccessible(true);
                    module.hook(method).intercept(chain -> {
                        if (!ConfigManager.isEnabledCached(HookConfig.KEY_NOTIFICATION_REPLY)) return chain.proceed();
                        Object view = chain.getThisObject();
                        if (event.equals("defocusIfNeeded") && OPENING_EDIT.get() == view) {
                            Log.i(TAG, "Deferred synchronous focus-loss close while opening reply");
                            return null;
                        }
                        Log.i(TAG, "Reply lifecycle before " + event + " " + replyState(view));
                        if (event.equals("onDefocus") || event.equals("defocusIfNeeded")) {
                            Log.w(TAG, "Reply close caller: " + event, new Throwable("reply lifecycle trace"));
                        }
                        if (!event.equals("focus")) {
                            try { return chain.proceed(); }
                            finally { Log.i(TAG, "Reply lifecycle after " + event + " " + replyState(view)); }
                        }
                        Object previous = OPENING_EDIT.get();
                        Object edit = Reflect.getObjectField(view, "mEditText");
                        // A fresh, explicit reply action must be able to focus the reused
                        // editor before the visibility aggregation callback runs again.
                        boolean opening = edit != null && !Boolean.TRUE.equals(Reflect.getObjectField(view, "mSending"));
                        if (opening) {
                            OPENING_EDIT.set(edit);
                            invoke(edit, "setEnabled", true);
                        }
                        try {
                            Object result = chain.proceed();
                            if (opening && Boolean.TRUE.equals(invoke(view, "isShown"))
                                    && !Boolean.TRUE.equals(Reflect.getObjectField(view, "mSending"))
                                    && !Boolean.TRUE.equals(invoke(edit, "isFocused"))) {
                                invoke(edit, "setFocusableInTouchMode", true);
                                invoke(edit, "setFocusable", true);
                                invoke(edit, "setCursorVisible", true);
                                Log.i(TAG, "Reply focus retry result=" + invoke(edit, "requestFocus"));
                            }
                            return result;
                        } finally {
                            if (previous == null) OPENING_EDIT.remove(); else OPENING_EDIT.set(previous);
                            Log.i(TAG, "Reply lifecycle after " + event + " " + replyState(view));
                        }
                    });
                    module.deoptimize(method);
                }
                Log.i(TAG, "Reply lifecycle diagnostics hooked: " + name);
            } catch (ClassNotFoundException ignored) {
            } catch (Throwable error) {
                Log.w(TAG, "Reply diagnostics unavailable: " + name, error);
            }
        }
    }

    private static Object invoke(Object owner, String name, Object... args) {
        try {
            return args.length == 0 ? owner.getClass().getMethod(name).invoke(owner)
                    : owner.getClass().getMethod(name, boolean.class).invoke(owner, args);
        } catch (ReflectiveOperationException error) {
            Log.w(TAG, "Reply state operation failed: " + name, error);
            return null;
        }
    }

    private static String replyState(Object owner) {
        Object edit = Reflect.getObjectField(owner, "mEditText");
        if (edit == null) edit = owner;
        StringBuilder state = new StringBuilder();
        for (String name : new String[] {"isFocusable", "isFocused", "isEnabled", "isShown", "hasWindowFocus"}) {
            try {
                state.append(name).append('=').append(edit.getClass().getMethod(name).invoke(edit)).append(' ');
            } catch (ReflectiveOperationException ignored) { }
        }
        return state.toString();
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
