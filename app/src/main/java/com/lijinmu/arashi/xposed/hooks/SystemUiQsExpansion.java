// Expansion/layout behavior adapted from AOSP SystemUI (Android 12 and 14).
// SPDX-License-Identifier: Apache-2.0

package com.lijinmu.arashi.xposed.hooks;

import com.lijinmu.arashi.xposed.Reflect;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** AOSP expansion bookkeeping; vendor 12/14 replaced the input clamp with the minimum height. */
final class SystemUiQsExpansion {
    private SystemUiQsExpansion() {}

    static float clamp(float height, int min, int max) {
        if (!Float.isFinite(height)) return min;
        return Math.min(Math.max(height, min), Math.max(min, max));
    }

    static boolean supports(Class<?> type, boolean modern) {
        return Reflect.findMethodExact(type, modern ? "setExpanded" : "setQsExpanded", boolean.class) != null
                && Reflect.findMethodExact(type, modern ? "updateExpansion" : "updateQsExpansion") != null
                && field(type, modern ? "mMinExpansionHeight" : "mQsMinExpansionHeight") != null
                && field(type, modern ? "mMaxExpansionHeight" : "mQsMaxExpansionHeight") != null
                && field(type, modern ? "mExpansionHeight" : "mQsExpansionHeight") != null
                && field(type, modern ? "mFullyExpanded" : "mQsFullyExpanded") != null
                && field(type, modern ? "mExpanded" : "mQsExpanded") != null;
    }

    static void apply(Object controller, float requested, boolean modern) throws ReflectiveOperationException {
        int min = number(controller, modern ? "mMinExpansionHeight" : "mQsMinExpansionHeight").intValue();
        int max = number(controller, modern ? "mMaxExpansionHeight" : "mQsMaxExpansionHeight").intValue();
        float height = clamp(requested, min, max);
        boolean fullyExpanded = height == max && max != 0;
        set(controller, modern ? "mFullyExpanded" : "mQsFullyExpanded", fullyExpanded);
        boolean expanded = flag(controller, modern ? "mExpanded" : "mQsExpanded");
        boolean animatingAway = modern && !flag(controller, "mAnimatorExpand") && flag(controller, "mAnimating");
        if (height > min && !expanded && !flag(controller, "mStackScrollerOverscrolling")
                && !flag(controller, "mDozing") && !animatingAway) {
            call(controller, modern ? "setExpanded" : "setQsExpanded", true);
        } else if (height <= min && expanded) {
            call(controller, modern ? "setExpanded" : "setQsExpanded", false);
        }
        set(controller, modern ? "mExpansionHeight" : "mQsExpansionHeight", height);
        call(controller, modern ? "updateExpansion" : "updateQsExpansion");
        if (modern) {
            Object listener = Reflect.getObjectField(controller, "mExpansionHeightListener");
            if (listener != null) {
                // The supplied 14 build R8-inlines the listener interface into this synthetic class.
                Object panel = Reflect.getObjectField(listener, "f$0");
                call(panel != null ? panel : listener, "onQsSetExpansionHeightCalled", fullyExpanded);
            }
            return;
        }
        call(controller, "requestScrollerTopPaddingUpdate", false);
        call(controller, "updateHeaderKeyguardAlpha");
        int state = number(controller, "mBarState").intValue();
        if (state == 1 || state == 2) {
            call(controller, "updateKeyguardBottomAreaAlpha");
            call(controller, "positionClockAndNotifications");
            call(controller, "updateBigClockAlpha");
        }
        Object accessibility = Reflect.getObjectField(controller, "mAccessibilityManager");
        if (accessibility != null && Boolean.TRUE.equals(call(accessibility, "isEnabled"))) {
            call(Reflect.getObjectField(controller, "mView"), "setAccessibilityPaneTitle",
                    call(controller, "determineAccessibilityPaneTitle"));
        }
        // Keep the firmware's falsing/bouncer enforcement when QS becomes fully expanded.
        Object falsing = Reflect.getObjectField(controller, "mFalsingManager");
        Object collector = Reflect.getObjectField(controller, "mFalsingCollector");
        if (fullyExpanded && falsing != null && collector != null
                && !Boolean.TRUE.equals(call(falsing, "isUnlockingDisabled"))
                && Boolean.TRUE.equals(call(collector, "shouldEnforceBouncer"))) {
            call(Reflect.getObjectField(controller, "mStatusBar"), "executeRunnableDismissingKeyguard",
                    null, null, false, true, false);
        }
        Object listeners = Reflect.getObjectField(controller, "mExpansionListeners");
        if (listeners instanceof Iterable<?>) {
            for (Object listener : (Iterable<?>) listeners)
                call(listener, "onQsExpansionChanged", max == 0 ? 0f : height / max);
        }
    }

    static boolean flag(Object object, String name) {
        return Boolean.TRUE.equals(Reflect.getObjectField(object, name));
    }

    static Number number(Object object, String name) {
        Object value = Reflect.getObjectField(object, name);
        if (!(value instanceof Number)) throw new IllegalStateException("Missing QS field: " + name);
        return (Number) value;
    }

    static Field field(Class<?> type, String name) {
        for (Class<?> owner = type; owner != null; owner = owner.getSuperclass()) {
            try { Field result = owner.getDeclaredField(name); result.setAccessible(true); return result; }
            catch (NoSuchFieldException ignored) {}
        }
        return null;
    }

    static void set(Object object, String name, Object value) throws ReflectiveOperationException {
        Field field = field(object.getClass(), name);
        if (field == null) throw new NoSuchFieldException(name);
        field.set(object, value);
    }

    static Object call(Object object, String name, Object... args) throws ReflectiveOperationException {
        if (object == null) throw new IllegalStateException("Missing QS receiver: " + name);
        for (Class<?> owner = object.getClass(); owner != null; owner = owner.getSuperclass()) {
            methods: for (Method method : owner.getDeclaredMethods()) {
                if (!method.getName().equals(name) || method.getParameterTypes().length != args.length) continue;
                Class<?>[] types = method.getParameterTypes();
                for (int i = 0; i < args.length; i++) {
                    Class<?> type = types[i];
                    if (type == boolean.class) type = Boolean.class;
                    else if (type == int.class) type = Integer.class;
                    else if (type == float.class) type = Float.class;
                    if (args[i] != null && !type.isInstance(args[i])) continue methods;
                }
                method.setAccessible(true);
                return method.invoke(object, args);
            }
        }
        throw new NoSuchMethodException(object.getClass().getName() + "." + name);
    }
}
