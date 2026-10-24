package com.lijinmu.arashi.xposed.hooks;

import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import com.lijinmu.arashi.xposed.ConfigManager;
import com.lijinmu.arashi.xposed.HookConfig;
import io.github.libxposed.api.XposedModule;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

/** Reconnect the stock drawable retained by the supplied Android 12/14 SystemUI builds. */
public final class AospBatteryHooks {
    private static final String TAG = "[Arashi-Battery]";
    // Values contain weak references: children must not keep a weak-map parent alive.
    private static final Map<LinearLayout, State> STATES = new WeakHashMap<>();

    private static final class State {
        final WeakReference<ImageView> icon;
        final WeakReference<View> cross;
        final WeakReference<View> electricity;
        final boolean injected;
        final int iconVisibility;
        int crossVisibility;
        int electricityVisibility;
        boolean active;
        boolean failed;
        State(ImageView icon, View cross, View electricity, boolean injected) {
            this.icon = new WeakReference<>(icon);
            this.cross = new WeakReference<>(cross);
            this.electricity = new WeakReference<>(electricity);
            this.injected = injected;
            iconVisibility = icon.getVisibility();
        }
    }

    private AospBatteryHooks() {}

    public static void hookSystemUI(XposedModule module, ClassLoader loader) {
        String[] names = { "com.android.systemui.battery.BatteryMeterView",
                "com.android.systemui.BatteryMeterView" };
        for (String name : names) {
            try {
                Class<?> type = Class.forName(name, false, loader);
                // Validate required fields before installing any callback.
                field(type, "mDrawable");
                field(type, "mCrossBatteryView");
                field(type, "mElectricityView");
                for (java.lang.reflect.Constructor<?> ctor : type.getDeclaredConstructors()) {
                    module.hook(ctor).intercept(chain -> {
                        Object result = chain.proceed();
                        sync((LinearLayout) chain.getThisObject());
                        return result;
                    });
                }
                for (Method method : type.getDeclaredMethods()) {
                    String n = method.getName();
                    if (!n.equals("onBatteryLevelChanged") && !n.equals("onUpdateBatteryView")
                            && !n.equals("updateColors") && !n.equals("scaleBatteryMeterViews")
                            && !n.equals("onBatteryUnknownStateChanged") && !n.equals("onTuningChanged")
                            && !n.equals("onConfigurationChanged") && !n.equals("onAttachedToWindow")) continue;
                    module.hook(method).intercept(chain -> {
                        Object result = chain.proceed();
                        sync((LinearLayout) chain.getThisObject());
                        return result;
                    });
                }
                Log.i(TAG, "Stock battery hooks registered: " + name);
                return;
            } catch (ClassNotFoundException ignored) {
            } catch (Throwable error) {
                Log.w(TAG, "Stock battery hooks unavailable: " + name, error);
                return;
            }
        }
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static Object read(Object owner, String name) throws ReflectiveOperationException {
        return field(owner.getClass(), name).get(owner);
    }

    private static void sync(LinearLayout meter) {
        State state = STATES.get(meter);
        if (state != null && state.failed) return;
        boolean enabled = ConfigManager.isEnabledCached(HookConfig.KEY_AOSP_BATTERY);
        if (state == null && !enabled) return;
        try {
            if (state == null) {
                Drawable drawable = (Drawable) read(meter, "mDrawable");
                View cross = (View) read(meter, "mCrossBatteryView");
                View electricity = (View) read(meter, "mElectricityView");
                if (drawable == null || cross == null) return;
                ImageView icon;
                boolean injected;
                try {
                    icon = (ImageView) read(meter, "mBatteryIconView");
                    injected = false;
                    if (icon == null) return;
                } catch (NoSuchFieldException android14) {
                    icon = new ImageView(meter.getContext());
                    icon.setImageDrawable(drawable);
                    icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                    icon.setVisibility(View.GONE);
                    float density = meter.getResources().getDisplayMetrics().density;
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                            Math.round(12 * density), Math.round(20 * density));
                    meter.addView(icon, Math.max(0, meter.indexOfChild(cross)), params);
                    injected = true;
                }
                state = new State(icon, cross, electricity, injected);
                STATES.put(meter, state);
            }
            ImageView icon = state.icon.get();
            View cross = state.cross.get();
            View electricity = state.electricity.get();
            if (icon == null || cross == null) return;
            if (enabled) {
                if (!state.active) {
                    state.crossVisibility = cross.getVisibility();
                    state.electricityVisibility = electricity == null ? View.GONE : electricity.getVisibility();
                    state.active = true;
                }
                // Android 12 already updates its stock icon, including unknown state.
                // Android 14 retains the same drawable and updates its level/colors internally.
                cross.setVisibility(View.GONE);
                if (electricity != null) electricity.setVisibility(View.GONE);
                icon.setVisibility(View.VISIBLE);
                icon.invalidate();
            } else {
                restore(state);
            }
        } catch (Throwable error) {
            if (state != null) {
                restore(state);
                state.failed = true;
            }
            Log.w(TAG, "Unable to restore stock battery; keeping vendor view", error);
        }
    }

    private static void restore(State state) {
        if (!state.active) return;
        View cross = state.cross.get();
        View electricity = state.electricity.get();
        ImageView icon = state.icon.get();
        if (cross != null) cross.setVisibility(state.crossVisibility);
        if (electricity != null) electricity.setVisibility(state.electricityVisibility);
        if (icon != null) icon.setVisibility(state.injected ? View.GONE : state.iconVisibility);
        state.active = false;
    }
}
