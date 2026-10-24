package com.lijinmu.arashi.xposed.hooks;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.util.Log;
import android.view.View;
import android.view.animation.AccelerateInterpolator;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.WeakHashMap;

import io.github.libxposed.api.XposedModule;

/** Keeps the launcher's uninstall panel from reopening while it is closing. */
public final class LauncherUninstallHooks {
    private static final String TAG = "[Arashi-Uninstall]";
    // Activity lifecycle and scroll animations all run on the launcher's main thread.
    private static final Map<Object, ScrollState> STATES = new WeakHashMap<>();

    private LauncherUninstallHooks() {}

    public static void hookLauncher(XposedModule module, ClassLoader loader) {
        try {
            Class<?> owner = Class.forName("com.android.launcher3.UninstallAppActivity", false, loader);
            Method scroll = null;
            for (Method method : owner.getDeclaredMethods()) {
                Class<?>[] args = method.getParameterTypes();
                if (!Modifier.isStatic(method.getModifiers()) && method.getReturnType() == void.class
                        && args.length == 3 && args[0] == int.class
                        && ((args[1] == View.class && args[2] == int.class)
                            || (args[1] == int.class && args[2] == View.class))) {
                    if (scroll != null) {
                        Log.w(TAG, "Ambiguous launcher uninstall scroll method");
                        return;
                    }
                    scroll = method;
                }
            }
            if (scroll == null) return;
            // Launcher 12: (start, target, view), Launcher 14: (start, view, target).
            final int viewIndex = scroll.getParameterTypes()[1] == View.class ? 1 : 2;
            final int targetIndex = viewIndex == 1 ? 2 : 1;
            Method destroy = owner.getDeclaredMethod("onDestroy");
            scroll.setAccessible(true);
            destroy.setAccessible(true);
            module.hook(scroll).intercept(chain -> {
                Activity activity = (Activity) chain.getThisObject();
                ScrollState state = STATES.get(activity);
                if (state == null) {
                    state = new ScrollState();
                    STATES.put(activity, state);
                }
                View view = (View) chain.getArg(viewIndex);
                int target = (Integer) chain.getArg(targetIndex);
                if (target == 0 || activity.isFinishing()) state.closing = true;
                // Returning from Arashi can call onResume during the 500 ms close delay.
                // Its downward animation must not undo the close animation.
                if (state.closing && target < 0) target = 0;
                state.animate(view, target);
                return null;
            });
            module.hook(destroy).intercept(chain -> {
                try {
                    return chain.proceed();
                } finally {
                    ScrollState state = STATES.remove(chain.getThisObject());
                    if (state != null) state.restore();
                }
            });
            Log.i(TAG, "Launcher uninstall scroll lifecycle hooked");
        } catch (Throwable error) {
            Log.w(TAG, "Launcher uninstall scroll hook unavailable", error);
        }
    }

    private static final class ScrollState {
        boolean closing;
        WeakReference<View> view = new WeakReference<>(null);
        ValueAnimator animator;

        void cancelAnimation() {
            if (animator != null) {
                animator.cancel();
                animator.removeAllUpdateListeners();
                animator = null;
            }
        }

        void animate(View nextView, int target) {
            cancelAnimation();
            if (nextView == null) return;
            View previous = view.get();
            if (previous != null && previous != nextView) previous.scrollTo(previous.getScrollX(), 0);
            view = new WeakReference<>(nextView);
            if (nextView.getScrollY() == target) return;
            // Start at the current position, including when an earlier animation was interrupted.
            animator = ValueAnimator.ofInt(nextView.getScrollY(), target);
            animator.setInterpolator(new AccelerateInterpolator());
            animator.setDuration(100L);
            animator.addUpdateListener(animation -> nextView.scrollTo(
                    nextView.getScrollX(), (Integer) animation.getAnimatedValue()));
            animator.start();
        }

        void restore() {
            cancelAnimation();
            View current = view.get();
            if (current != null) current.scrollTo(current.getScrollX(), 0);
            view.clear();
        }
    }
}
