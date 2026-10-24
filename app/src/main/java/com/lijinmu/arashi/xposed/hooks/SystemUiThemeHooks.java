package com.lijinmu.arashi.xposed.hooks;

import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableWrapper;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.lijinmu.arashi.xposed.ConfigManager;
import com.lijinmu.arashi.xposed.HookConfig;
import com.lijinmu.arashi.xposed.Reflect;
import com.lijinmu.arashi.xposed.SystemUiThemeColors;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Scoped to resources/classes present in both supplied Zybos SystemUI 12 and 14 APKs. */
public final class SystemUiThemeHooks {
    private static final String TAG = "[Arashi-SystemUITheme]";
    private static final String PREFIX = "com.android.systemui:";
    private static final ThreadLocal<Boolean> DRAWABLE_BUSY = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> ACTIVE_ICON = ThreadLocal.withInitial(() -> false);

    private static final Map<View, Object> TILE_STATES = new WeakHashMap<>();
    private static final Map<View, Boolean> BRIGHTNESS_VIEWS = new WeakHashMap<>();
    private static final Map<View, Boolean> PASSWORD_VIEWS = new WeakHashMap<>();

    private SystemUiThemeHooks() {}

    public static void hookSystemUI(XposedModule module, ClassLoader loader) {
        hookResources(module);
        hookTiles(module, loader);
        hookIcons(module, loader);
        hookPassword(module, loader);
        hookBrightness(module, loader);
    }

    private static boolean enabled() {
        return SystemUiMonetHooks.enabled();
    }

    private static boolean tilesEnabled() { return enabled() || SystemUiQsHooks.enabled(); }

    private static int accent(Resources resources) {
        return SystemUiMonetHooks.accent(resources);
    }
    private static int foreground(Resources resources) {
        return SystemUiThemeColors.foreground(accent(resources));
    }

    private static String resourceName(Resources resources, int id) {
        try { return resources.getResourceName(id); }
        catch (Resources.NotFoundException ignored) { return ""; }
    }

    private static void hookResources(XposedModule module) {
        for (Method method : Resources.class.getDeclaredMethods()) {
            String name = method.getName();
            Class<?>[] types = method.getParameterTypes();
            try {
                if ((name.equals("getColor") || name.equals("getColorStateList"))
                        && types.length > 0 && types[0] == int.class) {
                    method.setAccessible(true);
                    module.hook(method).intercept(chain -> {
                        if (tilesEnabled()) {
                            Resources resources = (Resources) chain.getThisObject();
                            String entry = resourceName(resources, (Integer) chain.getArg(0));
                            Integer replacement = null;
                            if (entry.equals(PREFIX + "color/qs_selected_background")) replacement = accent(resources);
                            if (SystemUiQsHooks.enabled() && entry.equals(PREFIX + "color/qs_default_background"))
                                replacement = SystemUiQsHooks.neutral(resources, false);
                            if (entry.equals(PREFIX + "color/qs_tile_text_and_icon_color")) {
                                if (ACTIVE_ICON.get()) replacement = foreground(resources);
                                else if (SystemUiQsHooks.enabled()) replacement = SystemUiQsHooks.neutral(resources, true);
                            }
                            if (replacement != null) return name.equals("getColor")
                                    ? replacement : ColorStateList.valueOf(replacement);
                        }
                        return chain.proceed();
                    });
                }
                // TypedArray.getDrawable (XML backgrounds) calls hidden loadDrawable directly.
                // Public getDrawable hooks alone miss the vendor password selectors.
                int idIndex = name.equals("loadDrawable") && types.length > 1 && types[1] == int.class ? 1
                        : (name.equals("getDrawable") || name.equals("getDrawableForDensity"))
                                && types.length > 0 && types[0] == int.class ? 0 : -1;
                if (idIndex < 0 || !Drawable.class.isAssignableFrom(method.getReturnType())) continue;
                method.setAccessible(true);
                module.hook(method).intercept(chain -> {
                    if (!enabled() || DRAWABLE_BUSY.get()) return chain.proceed();
                    Resources resources = (Resources) chain.getThisObject();
                    String entry = resourceName(resources, (Integer) chain.getArg(idIndex));
                    if (!isAccentDrawable(entry)) return chain.proceed();
                    DRAWABLE_BUSY.set(true);
                    try {
                        Object original = chain.proceed();
                        if (!(original instanceof Drawable)) return original;
                        Drawable.ConstantState state = ((Drawable) original).getConstantState();
                        if (state == null) return original;
                        try {
                            // Never mutate cached firmware ConstantState; disabling + restarting restores it.
                            Drawable copy = state.newDrawable(resources).mutate();
                            recolor(copy, entry, accent(resources));
                            return copy;
                        } catch (RuntimeException error) {
                            Log.w(TAG, "Keeping firmware drawable for " + entry, error);
                            return original;
                        }
                    } finally { DRAWABLE_BUSY.set(false); }
                });
            } catch (Throwable error) { Log.w(TAG, "Resource hook unavailable: " + method, error); }
        }
    }

    private static boolean isAccentDrawable(String entry) {
        return entry.equals(PREFIX + "drawable/zyb_keyguard_password_keyboard")
                || entry.equals(PREFIX + "drawable/zyb_keyguard_password_keyboard_pressed")
                || entry.equals(PREFIX + "drawable/zyb_keyguard_password_dot_bg")
                || entry.equals(PREFIX + "drawable/zyb_keyguard_password_dot_bg_pressed")
                || entry.equals(PREFIX + "drawable/zzz_brightness_progress_drawable");
    }

    private static void recolor(Drawable drawable, String entry, int color) {
        if (entry.endsWith("/zzz_brightness_progress_drawable") && drawable instanceof LayerDrawable) {
            replaceFill(((LayerDrawable) drawable).findDrawableByLayerId(android.R.id.progress), color);
        } else if (drawable instanceof StateListDrawable) {
            StateListDrawable selector = (StateListDrawable) drawable;
            int wanted = entry.endsWith("_keyboard") ? android.R.attr.state_pressed : android.R.attr.state_selected;
            for (int i = 0; i < selector.getStateCount(); i++) {
                for (int state : selector.getStateSet(i)) {
                    if (state == wanted) replaceFill(selector.getStateDrawable(i), color);
                }
            }
        } else if (entry.endsWith("_pressed")) replaceFill(drawable, color);
    }

    private static void replaceFill(Drawable drawable, int color) {
        if (drawable instanceof GradientDrawable) {
            ((GradientDrawable) drawable).setColors(new int[] { color, color });
        } else if (drawable instanceof DrawableWrapper) {
            replaceFill(((DrawableWrapper) drawable).getDrawable(), color);
        }
    }

    private static void hookTiles(XposedModule module, ClassLoader loader) {
        try {
            Class<?> type = Class.forName("com.android.systemui.qs.tileimpl.QSTileViewImpl", false, loader);
            for (Method method : type.getDeclaredMethods()) {
                String name = method.getName();
                if (name.equals("handleStateChanged") || name.equals("onStateChanged")) {
                    deoptimize(module, method);
                    if (name.equals("handleStateChanged") && method.getParameterTypes().length == 1) {
                        method.setAccessible(true);
                        module.hook(method).intercept(chain -> {
                            if ((SystemUiMonetHooks.enabled() || SystemUiQsHooks.enabled()) && chain.getThisObject() instanceof View) {
                                synchronized (TILE_STATES) {
                                    TILE_STATES.put((View) chain.getThisObject(), chain.getArg(0));
                                }
                            }
                            return chain.proceed();
                        });
                    }
                }
                if (!name.equals("getBackgroundColorForState") && !name.equals("getLabelColorForState")
                        && !name.equals("getSecondaryLabelColorForState")) continue;
                Class<?>[] args = method.getParameterTypes();
                // Android 12: (state); Android 14: (state, disabledByPolicy).
                if (method.getReturnType() != int.class || args.length < 1 || args.length > 2
                        || args[0] != int.class || (args.length == 2 && args[1] != boolean.class)) continue;
                method.setAccessible(true);
                module.hook(method).intercept(chain -> {
                    if (tilesEnabled() && Integer.valueOf(2).equals(chain.getArg(0))
                            && (args.length == 1 || !Boolean.TRUE.equals(chain.getArg(1)))) {
                        Resources resources = ((View) chain.getThisObject()).getResources();
                        return name.equals("getBackgroundColorForState") ? accent(resources) : foreground(resources);
                    }
                    if (SystemUiQsHooks.enabled() && Integer.valueOf(1).equals(chain.getArg(0))
                            && (args.length == 1 || !Boolean.TRUE.equals(chain.getArg(1)))) {
                        return SystemUiQsHooks.neutral(((View) chain.getThisObject()).getResources(),
                                !name.equals("getBackgroundColorForState"));
                    }
                    return chain.proceed();
                });
            }
        } catch (Throwable error) { Log.w(TAG, "QS theme hooks unavailable", error); }
    }

    private static void hookIcons(XposedModule module, ClassLoader loader) {
        try {
            Class<?> type = Class.forName("com.android.systemui.qs.tileimpl.QSIconViewImpl", false, loader);
            for (Method method : type.getDeclaredMethods()) {
                if (!method.getName().equals("setIcon")) continue;
                Class<?>[] args = method.getParameterTypes();
                if (args.length != 3 || args[0] != ImageView.class) {
                    deoptimize(module, method);
                    continue;
                }
                method.setAccessible(true);
                module.hook(method).intercept(chain -> {
                    boolean previous = ACTIVE_ICON.get();
                    Object state = chain.getArg(1);
                    ACTIVE_ICON.set(tilesEnabled() && Integer.valueOf(2).equals(Reflect.getObjectField(state, "state"))
                            && !Boolean.TRUE.equals(Reflect.getObjectField(state, "disabledByPolicy")));
                    try { return chain.proceed(); }
                    finally { ACTIVE_ICON.set(previous); }
                });
            }
        } catch (Throwable error) { Log.w(TAG, "QS icon theme hook unavailable", error); }
    }

    private static void hookPassword(XposedModule module, ClassLoader loader) {
        try {
            Class<?> type = Class.forName("com.android.keyguard.KeyguardPasswordViewZyb", false, loader);
            Method inflate = type.getDeclaredMethod("onFinishInflate");
            inflate.setAccessible(true);
            module.hook(inflate).intercept(chain -> {
                Object result = chain.proceed();
                if (enabled() && chain.getThisObject() instanceof View) {
                    View root = (View) chain.getThisObject();
                    if (SystemUiMonetHooks.enabled()) {
                        synchronized (PASSWORD_VIEWS) { PASSWORD_VIEWS.put(root, true); }
                    }
                    applyPasswordColors(root);
                }
                return result;
            });
        } catch (Throwable error) { Log.w(TAG, "Vendor password theme hook unavailable", error); }
    }

    private static void applyPasswordColors(View root) {
        try {
            for (int digit = 0; digit <= 9; digit++) {
                int id = root.getResources().getIdentifier("zyb_keyguard_password_keyboard_" + digit,
                        "id", "com.android.systemui");
                View key = id == 0 ? null : root.findViewById(id);
                recolorViewBackground(key, PREFIX + "drawable/zyb_keyguard_password_keyboard");
                if (!(key instanceof TextView)) continue;
                TextView label = (TextView) key;
                ColorStateList original = label.getTextColors();
                label.setTextColor(new ColorStateList(new int[][] {
                        { -android.R.attr.state_enabled }, { android.R.attr.state_pressed }, {} },
                        new int[] { original.getColorForState(new int[] { -android.R.attr.state_enabled },
                                original.getDefaultColor()), foreground(root.getResources()), original.getDefaultColor() }));
            }
            // Also handle an XML drawable already inflated by an optimized framework path.
            for (int digit = 1; digit <= 6; digit++) {
                int id = root.getResources().getIdentifier("zyb_keyguard_password_input_" + digit,
                        "id", "com.android.systemui");
                recolorViewBackground(id == 0 ? null : root.findViewById(id),
                        PREFIX + "drawable/zyb_keyguard_password_dot_bg");
            }
        } catch (RuntimeException error) { Log.w(TAG, "Password key colors unavailable", error); }
    }

    private static void hookBrightness(XposedModule module, ClassLoader loader) {
        try {
            Class<?> type = Class.forName("com.android.systemui.settings.brightness.BrightnessSliderView", false, loader);
            Method inflate = type.getDeclaredMethod("onFinishInflate");
            inflate.setAccessible(true);
            module.hook(inflate).intercept(chain -> {
                Object result = chain.proceed();
                if (tilesEnabled() && chain.getThisObject() instanceof View) {
                    View root = (View) chain.getThisObject();
                    if (SystemUiMonetHooks.enabled() || SystemUiQsHooks.enabled()) {
                        synchronized (BRIGHTNESS_VIEWS) { BRIGHTNESS_VIEWS.put(root, true); }
                    }
                    applyBrightnessColor(root);
                }
                return result;
            });
        } catch (Throwable error) { Log.w(TAG, "Brightness refresh hook unavailable", error); }
    }

    private static void applyBrightnessColor(View root) {
        try {
            if (SystemUiQsHooks.enabled()) {
                SystemUiQsHooks.restoreBrightness(root);
                return;
            }
            int id = root.getResources().getIdentifier("slider", "id", "com.android.systemui");
            View view = id == 0 ? null : root.findViewById(id);
            if (!(view instanceof SeekBar)) return;
            SeekBar slider = (SeekBar) view;
            Drawable original = slider.getProgressDrawable();
            if (original == null || original.getConstantState() == null) return;
            Drawable copy = original.getConstantState().newDrawable(root.getResources()).mutate();
            recolor(copy, PREFIX + "drawable/zzz_brightness_progress_drawable", accent(root.getResources()));
            slider.setProgressDrawable(copy);
        } catch (RuntimeException error) { Log.w(TAG, "Brightness color refresh failed", error); }
    }

    static void refreshDynamicColors() {
        if (!tilesEnabled()) return;
        Map<View, Object> tiles;
        ArrayList<View> brightness;
        ArrayList<View> passwords;
        synchronized (TILE_STATES) { tiles = new HashMap<>(TILE_STATES); }
        synchronized (BRIGHTNESS_VIEWS) { brightness = new ArrayList<>(BRIGHTNESS_VIEWS.keySet()); }
        synchronized (PASSWORD_VIEWS) { passwords = new ArrayList<>(PASSWORD_VIEWS.keySet()); }
        for (Map.Entry<View, Object> entry : tiles.entrySet()) {
            View tile = entry.getKey();
            Object state = entry.getValue();
            if (tile == null || state == null) continue;
            try {
                // Both samples cache icon tint by tile state, not by palette. Invalidate just
                // that cache so an unchanged active Wi-Fi/Bluetooth tile receives the new tint.
                Method getIcon = Reflect.findMethodExact(tile.getClass(), "getIcon");
                if (getIcon != null) SystemUiMonetHooks.setField(getIcon.invoke(tile), "mState", -1);
                refresh:
                for (Class<?> owner = tile.getClass(); owner != null; owner = owner.getSuperclass()) {
                    for (Method method : owner.getDeclaredMethods()) {
                        if (method.getName().equals("handleStateChanged") && method.getParameterTypes().length == 1
                                && method.getParameterTypes()[0].isInstance(state)) {
                            method.setAccessible(true);
                            method.invoke(tile, state);
                            break refresh;
                        }
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException error) {
                Log.w(TAG, "Tile palette refresh failed", error);
            }
        }
        for (View root : brightness) if (root != null) applyBrightnessColor(root);
        for (View root : passwords) if (root != null) applyPasswordColors(root);
    }

    private static void deoptimize(XposedModule module, Method method) {
        try { module.deoptimize(method); }
        catch (Throwable error) { Log.w(TAG, "Unable to deoptimize " + method, error); }
    }

    private static void recolorViewBackground(View view, String resource) {
        if (view == null || view.getBackground() == null) return;
        Drawable.ConstantState state = view.getBackground().getConstantState();
        if (state == null) return;
        Drawable copy = state.newDrawable(view.getResources()).mutate();
        recolor(copy, resource, accent(view.getResources()));
        view.setBackground(copy);
    }
}
