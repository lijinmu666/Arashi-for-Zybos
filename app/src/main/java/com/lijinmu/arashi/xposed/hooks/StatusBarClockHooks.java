package com.lijinmu.arashi.xposed.hooks;

import android.content.res.Resources;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.util.Log;
import android.widget.TextView;
import android.view.View;
import android.view.ViewParent;
import com.lijinmu.arashi.xposed.ConfigManager;
import com.lijinmu.arashi.xposed.HookConfig;
import com.lijinmu.arashi.xposed.Reflect;
import com.lijinmu.arashi.xposed.StatusBarClockFormat;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Calendar;
import java.util.Map;
import java.util.WeakHashMap;
import java.lang.ref.WeakReference;

/** Status bar date extension for the supplied Android 12 and 14 SystemUI builds. */
public final class StatusBarClockHooks {
    private static final String TAG = "[Arashi-Clock]";
    private static final Map<TextView, ClockState> CLOCKS = new WeakHashMap<>();

    private static final class ClockState {
        final WeakReference<ViewParent> parent;
        final int id;
        final boolean statusClock;
        final StatusBarClockFormat.Cache date = new StatusBarClockFormat.Cache();
        ClockState(TextView view) {
            parent = new WeakReference<>(view.getParent());
            id = view.getId();
            statusClock = isStatusClock(view);
        }
    }

    private StatusBarClockHooks() {}

    public static void hookSystemUI(XposedModule module, ClassLoader loader) {
        try {
            Class<?> clock = Class.forName("com.android.systemui.statusbar.policy.Clock", false, loader);
            Method smallTime = Reflect.findMethodExact(clock, "getSmallTime");
            Field calendar = clock.getDeclaredField("mCalendar");
            calendar.setAccessible(true);
            if (smallTime == null) throw new NoSuchMethodException("getSmallTime");
            module.hook(smallTime).intercept(chain -> {
                // Initialize the native time/accessibility formatters and preserve
                // AM/PM spans, 12/24-hour settings and optional seconds.
                Object original = chain.proceed();
                if (!ConfigManager.isEnabledCached(HookConfig.KEY_STATUS_BAR_DATE)) return original;
                TextView view = (TextView) chain.getThisObject();
                if (!(original instanceof CharSequence)) return original;
                ClockState state = CLOCKS.get(view);
                if (state == null || state.parent.get() != view.getParent() || state.id != view.getId()) {
                    state = new ClockState(view);
                    CLOCKS.put(view, state);
                }
                if (!state.statusClock) return original;
                try {
                    Calendar current = (Calendar) calendar.get(view);
                    if (current != null) {
                        String suffix = state.date.suffix(current);
                        // Native plain strings can stay plain: creating a spanned
                        // buffer every second needlessly increases TextView work.
                        return original instanceof Spanned
                                ? new SpannableStringBuilder((CharSequence) original).append(suffix)
                                : original.toString() + suffix;
                    }
                } catch (Exception error) { Log.w(TAG, "Unable to format status bar date", error); }
                return original;
            });
            // Keep the native contentDescription/updateClock pipeline untouched.
            // Replacing its description twice per second creates redundant
            // accessibility events; the system still announces its native time.
            Log.i(TAG, "Status bar date hooks registered");
        } catch (Throwable error) { Log.w(TAG, "Status bar date hooks unavailable", error); }
    }

    private static boolean isStatusClock(TextView view) {
        // QS and other clocks must keep their original text and layout.
        if (view.getId() <= 0) return false;
        try {
            if (!"clock".equals(view.getResources().getResourceEntryName(view.getId()))) return false;
        } catch (Resources.NotFoundException ignored) { return false; }
        for (ViewParent parent = view.getParent(); parent instanceof View; parent = parent.getParent()) {
            for (Class<?> type = parent.getClass(); type != null; type = type.getSuperclass()) {
                if (type.getName().equals("com.android.systemui.statusbar.phone.PhoneStatusBarView")) return true;
            }
        }
        return false;
    }
}
