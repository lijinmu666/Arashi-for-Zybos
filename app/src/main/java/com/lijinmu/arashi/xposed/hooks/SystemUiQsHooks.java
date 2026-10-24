// Expansion/layout behavior adapted from AOSP SystemUI (Android 12 and 14).
// SPDX-License-Identifier: Apache-2.0

package com.lijinmu.arashi.xposed.hooks;

import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableWrapper;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.SeekBar;
import com.lijinmu.arashi.xposed.SystemUiThemeColors;

import com.lijinmu.arashi.xposed.ConfigManager;
import com.lijinmu.arashi.xposed.HookConfig;
import com.lijinmu.arashi.xposed.Reflect;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/** Restore the retained AOSP QS hierarchy and expansion path in the supplied Zybos 12/14 builds. */
public final class SystemUiQsHooks {
    private static final String TAG = "[Arashi-AospQs]";
    private static final Map<String, Integer> DIMENSIONS = new HashMap<>();
    static {
        DIMENSIONS.put("qs_tile_height", 80);
        DIMENSIONS.put("qs_card_width_height", 80);
        DIMENSIONS.put("qs_card_margin_horizontal", 8);
        DIMENSIONS.put("qs_quick_tile_size", 80);
        DIMENSIONS.put("qs_icon_size", 20);
        DIMENSIONS.put("qs_side_view_size", 20);
        DIMENSIONS.put("qs_tile_margin_horizontal", 8);
        DIMENSIONS.put("qs_tile_margin_vertical", 8);
        DIMENSIONS.put("qs_tile_margin_top_bottom", 4);
        DIMENSIONS.put("qs_corner_radius", 28);
        DIMENSIONS.put("qs_label_container_margin", 10);
        DIMENSIONS.put("qs_tile_padding", 12);
        DIMENSIONS.put("qs_tile_start_padding", 16);
        DIMENSIONS.put("qqs_layout_margin_top", 16);
        DIMENSIONS.put("qqs_layout_padding_bottom", 24);
    }

    private SystemUiQsHooks() {}
    static boolean enabled() { return ConfigManager.isEnabledCached(HookConfig.KEY_AOSP_QS); }

    public static void hookSystemUI(XposedModule module, ClassLoader loader) {
        hookResources(module);
        hookSplitShade(module, loader);
        hookExpansion(module, loader, "com.android.systemui.shade.QuickSettingsController", true);
        hookExpansion(module, loader, "com.android.systemui.statusbar.phone.NotificationPanelViewController", false);
        after(module, loader, "com.android.systemui.qs.QSFragment", "updateQsState", instance -> {
            Object controller = Reflect.getObjectField(instance, "mQSPanelController");
            Object panel = Reflect.getObjectField(controller, "mView");
            if (!(panel instanceof View)) return;
            boolean show = !SystemUiQsExpansion.flag(instance, "mQsDisabled")
                    && (SystemUiQsExpansion.flag(instance, "mQsExpanded")
                    || SystemUiQsExpansion.flag(instance, "mStackScrollerOverscrolling")
                    || SystemUiQsExpansion.flag(instance, "mHeaderAnimating"));
            ((View) panel).setVisibility(show ? View.VISIBLE : View.INVISIBLE);
        });
        // 14 retains the separate MotionLayout status header but forces it GONE.
        replaceHeaderVisibility(module, loader);
        after(module, loader, "com.android.systemui.qs.QuickStatusBarHeader", "onFinishInflate",
                object -> restoreHeader((ViewGroup) object));
        after(module, loader, "com.android.systemui.qs.QuickStatusBarHeader", "onConfigurationChanged",
                object -> {
                    restoreHeader((ViewGroup) object);
                    ((View) object).post(SystemUiThemeHooks::refreshDynamicColors);
                });
        after(module, loader, "com.android.systemui.qs.QuickStatusBarHeader", "updateResources",
                object -> restoreHeader((ViewGroup) object));
        after(module, loader, "com.android.systemui.qs.QuickQSPanelController", "onInit", object -> {
            Object view = Reflect.getObjectField(object, "mView");
            if (view instanceof View) hideQuickExtras((View) view);
        });
        after(module, loader, "com.android.systemui.qs.QuickQSPanelController", "onConfigurationChanged", object -> {
            Object view = Reflect.getObjectField(object, "mView");
            if (view instanceof View) hideQuickExtras((View) view);
        });
        after(module, loader, "com.android.systemui.qs.QsCustomModeController", "updateButtonState", object -> {
            Object root = Reflect.getObjectField(object, "mRootView");
            if (root instanceof View) ((View) root).setVisibility(View.GONE);
        });
        after(module, loader, "com.android.systemui.qs.QSPanelControllerBase", "switchTileLayout", object -> {
            Object root = Reflect.getObjectField(object, "mView");
            if (!(root instanceof ViewGroup) || root.getClass().getName().endsWith("QuickQSPanel")) return;
            ViewGroup panel = (ViewGroup) root;
            Object brightness = Reflect.getObjectField(panel, "mBrightnessView");
            if (brightness instanceof View && ((View) brightness).getParent() == panel
                    && panel.indexOfChild((View) brightness) != 0) {
                // Vendor 14 moves brightness below tiles/footer during each layout switch.
                View view = (View) brightness;
                ViewGroup.LayoutParams params = view.getLayoutParams();
                panel.removeView(view);
                panel.addView(view, 0, params);
            }
        });
        hookTileGeometry(module, loader);
    }


    // Use one decision for layout, notification padding, QS expansion and scrim controllers.
    // Resource interception also covers R8-inlined LargeScreenUtils callers.
    static boolean splitShade(Resources resources) {
        return resources.getConfiguration().screenWidthDp >= 600;
    }

    private static void hookSplitShade(XposedModule module, ClassLoader loader) {
        try {
            Class<?> type = Class.forName("com.android.systemui.util.LargeScreenUtils", false, loader);
            Method method = Reflect.findMethodExact(type, "shouldUseSplitNotificationShade", Resources.class);
            if (method == null) return;
            try { module.deoptimize(method); } catch (Throwable ignored) {}
            module.hook(method).intercept(chain -> enabled()
                    ? splitShade((Resources) chain.getArg(0)) : chain.proceed());
        } catch (ClassNotFoundException ignored) {
        } catch (Throwable error) { Log.w(TAG, "Split shade decision unavailable", error); }
    }

    static int neutral(Resources resources, boolean foreground) {
        boolean dark = (resources.getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        return resources.getColor(foreground
                ? (dark ? android.R.color.system_neutral1_50 : android.R.color.system_neutral1_900)
                : (dark ? android.R.color.system_neutral1_800 : android.R.color.system_neutral1_100), null);
    }

    private static void hookExpansion(XposedModule module, ClassLoader loader, String name, boolean modern) {
        try {
            Class<?> type = Class.forName(name, false, loader);
            if (!SystemUiQsExpansion.supports(type, modern)) return;
            Method method = Reflect.findMethodExact(type, modern ? "setExpansionHeight" : "setQsExpansion", float.class);
            if (method == null) return;
            try { module.deoptimize(method); } catch (Throwable ignored) {}
            module.hook(method).intercept(chain -> {
                if (!enabled()) return chain.proceed();
                try {
                    SystemUiQsExpansion.apply(chain.getThisObject(), ((Number) chain.getArg(0)).floatValue(), modern);
                    return null;
                } catch (ReflectiveOperationException | RuntimeException error) {
                    Log.w(TAG, "Expansion restore failed: " + name, error);
                    return chain.proceed();
                }
            });
        } catch (ClassNotFoundException ignored) {
            // Different package/controller between Android 12 and 14.
        } catch (Throwable error) { Log.w(TAG, "Expansion hook unavailable: " + name, error); }
    }

    private static void replaceHeaderVisibility(XposedModule module, ClassLoader loader) {
        try {
            Class<?> type = Class.forName("com.android.systemui.shade.ShadeHeaderController", false, loader);
            Method method = Reflect.findMethodExact(type, "updateVisibility");
            if (method == null) return;
            try { module.deoptimize(method); } catch (Throwable ignored) {}
            module.hook(method).intercept(chain -> {
                if (!enabled()) return chain.proceed();
                Object controller = chain.getThisObject();
                Object header = Reflect.getObjectField(controller, "header");
                if (!(header instanceof View)) return chain.proceed();
                int visibility = SystemUiQsExpansion.flag(controller, "qsDisabled") ? View.GONE
                        : SystemUiQsExpansion.flag(controller, "qsVisible")
                        && !SystemUiQsExpansion.flag(controller, "customizing") ? View.VISIBLE : View.INVISIBLE;
                try {
                    boolean visible = visibility == View.VISIBLE;
                    ((View) header).setVisibility(visibility);
                    SystemUiQsExpansion.set(controller, "visible", visible);
                    Object carrier = Reflect.getObjectField(controller, "mShadeCarrierGroupController");
                    if (carrier != null) {
                        // 14 R8-inlines setListening into the caller; preserve its async update.
                        boolean previous = SystemUiQsExpansion.flag(carrier, "mListening");
                        if (previous != visible) {
                            Method setListening = Reflect.findMethodExact(carrier.getClass(), "setListening", boolean.class);
                            if (setListening != null) setListening.invoke(carrier, visible);
                            else {
                                SystemUiQsExpansion.set(carrier, "mListening", visible);
                                Object handler = Reflect.getObjectField(carrier, "mBgHandler");
                                Class<?> callback = Class.forName("com.android.systemui.shade.carrier.ShadeCarrierGroupController$$ExternalSyntheticLambda2", false, loader);
                                for (Constructor<?> constructor : callback.getDeclaredConstructors()) {
                                    if (constructor.getParameterTypes().length == 2
                                            && constructor.getParameterTypes()[0].isInstance(carrier)
                                            && constructor.getParameterTypes()[1] == int.class) {
                                        constructor.setAccessible(true);
                                        SystemUiQsExpansion.call(handler, "post", constructor.newInstance(carrier, 1));
                                        break;
                                    }
                                }
                            }
                        }
                        if (visible) SystemUiQsExpansion.call(controller, "updateSingleCarrier",
                                SystemUiQsExpansion.flag(carrier, "mIsSingleCarrier"));
                        if (SystemUiQsExpansion.field(carrier.getClass(), "mOnSingleCarrierChangedListener") != null) {
                            Object listener = null;
                            if (visible) {
                                Class<?> callback = Class.forName("com.android.systemui.shade.ShadeHeaderController$updateListeners$1", false, loader);
                                Constructor<?> constructor = callback.getDeclaredConstructor(controller.getClass());
                                constructor.setAccessible(true);
                                listener = constructor.newInstance(controller);
                            }
                            SystemUiQsExpansion.set(carrier, "mOnSingleCarrierChangedListener", listener);
                        }
                    }
                    return null;
                } catch (ReflectiveOperationException | RuntimeException error) {
                    Log.w(TAG, "Native status header unavailable", error);
                    return chain.proceed();
                }
            });
        } catch (ClassNotFoundException ignored) {
        } catch (Throwable error) { Log.w(TAG, "Status header hook unavailable", error); }
    }

    private interface Action { void run(Object instance) throws ReflectiveOperationException; }
    private static void after(XposedModule module, ClassLoader loader, String name, String target, Action action) {
        try {
            Class<?> type = Class.forName(name, false, loader);
            for (Method method : type.getDeclaredMethods()) {
                if (!method.getName().equals(target)) continue;
                method.setAccessible(true);
                try { module.deoptimize(method); } catch (Throwable ignored) {}
                module.hook(method).intercept(chain -> {
                    Object result = chain.proceed();
                    if (enabled()) {
                        try { action.run(chain.getThisObject()); }
                        catch (ReflectiveOperationException | RuntimeException error) { Log.w(TAG, target + " restore failed", error); }
                    }
                    return result;
                });
            }
        } catch (ClassNotFoundException ignored) {
        } catch (Throwable error) { Log.w(TAG, "QS hook unavailable: " + name + "." + target, error); }
    }

    static Integer integer(String resourceName) {
        switch (resourceName) {
            case "com.android.systemui:integer/quick_settings_num_columns": return 2;
            case "com.android.systemui:integer/quick_settings_max_rows": return 4;
            case "com.android.systemui:integer/quick_qs_panel_max_columns": return 4; // 12 stores tile count here
            case "com.android.systemui:integer/quick_qs_panel_max_tiles": return 4;
            case "com.android.systemui:integer/quick_qs_panel_max_rows": return 2;
            default: return null;
        }
    }
    static Integer dimension(String resourceName) {
        String prefix = "com.android.systemui:dimen/";
        return resourceName.startsWith(prefix) ? DIMENSIONS.get(resourceName.substring(prefix.length())) : null;
    }
    private static void hookResources(XposedModule module) {
        for (Method method : Resources.class.getDeclaredMethods()) {
            String name = method.getName();
            if (!name.equals("getBoolean") && !name.equals("getInteger") && !name.equals("getDimension")
                    && !name.equals("getDimensionPixelSize") && !name.equals("getDimensionPixelOffset")) continue;
            if (method.getParameterTypes().length != 1 || method.getParameterTypes()[0] != int.class) continue;
            try {
                module.hook(method).intercept(chain -> {
                    if (enabled()) {
                        Resources resources = (Resources) chain.getThisObject();
                        try {
                            String entry = resources.getResourceName((Integer) chain.getArg(0));
                            if (name.equals("getBoolean")) {
                                if (entry.equals("com.android.systemui:bool/config_use_split_notification_shade"))
                                    return splitShade(resources);
                                return chain.proceed();
                            }
                            Integer value = name.equals("getInteger") ? integer(entry) : dimension(entry);
                            if (value != null) {
                                if (name.equals("getInteger")) return value;
                                float pixels = value * resources.getDisplayMetrics().density;
                                if (name.equals("getDimension")) return pixels;
                                return name.equals("getDimensionPixelSize") ? Math.round(pixels) : (int) pixels;
                            }
                        } catch (Resources.NotFoundException ignored) {}
                    }
                    return chain.proceed();
                });
            } catch (Throwable error) { Log.w(TAG, "QS resource hook unavailable: " + name, error); }
        }
    }

    static View find(View root, String name) {
        int id = root.getResources().getIdentifier(name, "id", "com.android.systemui");
        return id == 0 ? null : root.findViewById(id);
    }
    private static int dp(View view, int value) { return Math.round(value * view.getResources().getDisplayMetrics().density); }
    private static void hide(View view) { if (view != null) view.setVisibility(View.GONE); }

    static void restoreBrightness(View root) {
        View view = find(root, "slider");
        if (!(view instanceof SeekBar)) return;
        SeekBar slider = (SeekBar) view;
        int accent = ConfigManager.isEnabledCached(HookConfig.KEY_SYSTEMUI_THEME)
                ? ConfigManager.systemUiThemeColorCached() : SystemUiMonetHooks.accent(root.getResources());
        int size = dp(root, 48);
        GradientDrawable track = new GradientDrawable();
        track.setColor(neutral(root.getResources(), false));
        track.setSize(-1, dp(root, 4));
        track.setCornerRadius(dp(root, 2));
        GradientDrawable fill = new GradientDrawable();
        fill.setColor(accent);
        fill.setSize(-1, size);
        fill.setCornerRadius(dp(root, 24));
        Drawable progress = fill;
        int sunId = root.getResources().getIdentifier("zzz_ic_brightness_bright", "drawable", "com.android.systemui");
        if (sunId != 0) {
            Drawable sun = root.getResources().getDrawable(sunId, null).mutate();
            sun.setTint(SystemUiThemeColors.foreground(accent));
            LayerDrawable content = new LayerDrawable(new Drawable[] {fill, sun});
            content.setLayerSize(1, dp(root, 20), dp(root, 20));
            content.setLayerGravity(1, Gravity.END | Gravity.CENTER_VERTICAL);
            content.setLayerInsetEnd(1, dp(root, 14));
            progress = content;
        }
        LayerDrawable drawable = new LayerDrawable(new Drawable[] {
                new InsetDrawable(track, dp(root, 22), 0, dp(root, 22), 0),
                new AospBrightnessProgressDrawable(progress)});
        drawable.setId(0, android.R.id.background);
        drawable.setId(1, android.R.id.progress);
        drawable.setLayerGravity(0, Gravity.CENTER_VERTICAL | Gravity.FILL_HORIZONTAL);
        drawable.setLayerGravity(1, Gravity.CENTER_VERTICAL | Gravity.FILL_HORIZONTAL);
        slider.setProgressDrawable(drawable);
        slider.setThumb(null);
        slider.setPaddingRelative(0, 0, 0, 0);
        slider.setLayoutParams(new FrameLayout.LayoutParams(-1, size));
        slider.setMinimumHeight(size);
        hide(find(root, "brightness_auto"));
        hide(find(root, "auto_brightness"));
        hide(find(root, "iv_bright"));
        ViewGroup.LayoutParams params = root.getLayoutParams();
        if (params != null) { params.height = size; root.setLayoutParams(params); }
    }

    static void hideQuickExtras(View panel) {
        Object brightness = Reflect.getObjectField(panel, "mBrightnessView");
        if (brightness instanceof View) hide((View) brightness);
        Object custom = Reflect.getObjectField(panel, "mQsCustomModeView");
        if (custom instanceof View) hide((View) custom);
        hide(find(panel, "custom_mode_container"));
    }

    static void restoreHeader(ViewGroup header) {
        View quick = find(header, "quick_qs_panel");
        if (quick == null) return;
        hideQuickExtras(quick);
        quick.setPaddingRelative(quick.getPaddingStart(), quick.getPaddingTop(), quick.getPaddingEnd(), dp(quick, 24));
        View status = find(header, "quick_qs_status_icons");
        if (status != null) {
            // 12: restore the two overlapping native rows and their existing animators.
            View date = find(header, "quick_status_bar_date_privacy");
            View container = find(header, "qs_container");
            if (date == null || container == null) return;
            View vendorClock = find(date, "clock");
            if (vendorClock != null) { vendorClock.setId(View.NO_ID); hide(vendorClock); }
            View profile = find(date, "zyb_status_head");
            if (profile != null && profile.getParent() instanceof View) hide((View) profile.getParent());
            hide(find(date, "settings_button"));
            moveToHeader(date, header);
            moveToHeader(container, header);
            FrameLayout.LayoutParams dateParams = new FrameLayout.LayoutParams(-1, dp(header, 48));
            date.setLayoutParams(dateParams);
            date.setMinimumHeight(dp(header, 48));
            View nativeClock = find(status, "clock");
            if (nativeClock != null) SystemUiMonetHooks.setField(header, "mClockView", nativeClock);
            status.setVisibility(View.VISIBLE);
            if (quick.getLayoutParams() instanceof RelativeLayout.LayoutParams) {
                RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) quick.getLayoutParams();
                params.addRule(RelativeLayout.BELOW, status.getId());
                params.topMargin = dp(header, 16);
                quick.setLayoutParams(params);
            }
        } else {
            // 14: the status header is managed separately by ShadeHeaderController.
            View oldParent = quick.getParent() instanceof View ? (View) quick.getParent() : null;
            if (quick.getParent() != header) {
                ((ViewGroup) quick.getParent()).removeView(quick);
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, -2);
                params.topMargin = dp(header, 16);
                header.addView(quick, params);
                if (oldParent != null && oldParent.getParent() instanceof View) hide((View) oldParent.getParent());
            }
            if (quick.getLayoutParams() instanceof FrameLayout.LayoutParams) {
                FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) quick.getLayoutParams();
                params.topMargin = dp(header, 16);
                quick.setLayoutParams(params);
            }
        }
    }

    private static void moveToHeader(View view, ViewGroup header) {
        if (view.getParent() == header) return;
        if (view.getParent() instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) view.getParent();
            parent.removeView(view);
            if (parent.getChildCount() == 0) hide(parent);
        }
        header.addView(view, new FrameLayout.LayoutParams(-1, -2));
    }

    private static void hookTileGeometry(XposedModule module, ClassLoader loader) {
        try {
            Class<?> type = Class.forName("com.android.systemui.qs.tileimpl.QSTileViewImpl", false, loader);
            Method measure = Reflect.findMethodExact(type, "onMeasure", int.class, int.class);
            if (measure != null) {
                try { module.deoptimize(measure); } catch (Throwable ignored) {}
                module.hook(measure).intercept(chain -> {
                    if (!enabled()) return chain.proceed();
                    // Both vendor TileLayouts measure square cells using width for height.
                    // Let the native view measure normally, with AOSP's independent 80dp height.
                    View tile = (View) chain.getThisObject();
                    return chain.proceed(new Object[] {chain.getArg(0), View.MeasureSpec.makeMeasureSpec(
                            dp(tile, 80), View.MeasureSpec.EXACTLY)});
                });
            }
            for (Constructor<?> constructor : type.getDeclaredConstructors()) {
                module.hook(constructor).intercept(chain -> {
                    Object result = chain.proceed();
                    if (enabled() && chain.getThisObject() instanceof LinearLayout) {
                        try { restoreTile((LinearLayout) chain.getThisObject()); }
                        catch (RuntimeException error) { Log.w(TAG, "Native tile geometry unavailable", error); }
                    }
                    return result;
                });
            }
            after(module, loader, type.getName(), "onConfigurationChanged", object -> restoreTile((LinearLayout) object));
        } catch (Throwable error) { Log.w(TAG, "Native tile hook unavailable", error); }
    }

    static void restoreTile(LinearLayout tile) {
        Object icon = Reflect.getObjectField(tile, "_icon");
        Object labels = Reflect.getObjectField(tile, "labelContainer");
        Object side = Reflect.getObjectField(tile, "sideView");
        if (!(icon instanceof View) || !(labels instanceof LinearLayout) || !(side instanceof View)) return;
        tile.setOrientation(LinearLayout.HORIZONTAL);
        tile.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        tile.setPaddingRelative(dp(tile, 16), dp(tile, 12), dp(tile, 12), dp(tile, 12));
        ((View) icon).setLayoutParams(new LinearLayout.LayoutParams(dp(tile, 20), dp(tile, 20)));
        LinearLayout labelGroup = (LinearLayout) labels;
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -2, 1f);
        labelParams.setMarginStart(dp(tile, 10));
        labelGroup.setLayoutParams(labelParams);
        labelGroup.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        for (int i = 0; i < labelGroup.getChildCount(); i++) {
            View view = labelGroup.getChildAt(i);
            if (view instanceof TextView) {
                TextView text = (TextView) view;
                text.setTextSize(14);
                text.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                text.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
            }
        }
        View sideView = (View) side;
        if (sideView.getParent() == null) tile.addView(sideView);
        LinearLayout.LayoutParams sideParams = new LinearLayout.LayoutParams(-2, -2);
        sideParams.setMarginStart(dp(tile, 10));
        sideView.setLayoutParams(sideParams);
        Object original = Reflect.getObjectField(tile, "ripple");
        if (original instanceof RippleDrawable && ((Drawable) original).getConstantState() != null) {
            RippleDrawable copy = (RippleDrawable) ((Drawable) original).getConstantState()
                    .newDrawable(tile.getResources()).mutate();
            int id = tile.getResources().getIdentifier("background", "id", "com.android.systemui");
            Drawable background = copy.findDrawableByLayerId(id);
            if (background != null) {
                round(background, dp(tile, 28));
                Drawable mask = copy.findDrawableByLayerId(android.R.id.mask);
                if (mask != null) round(mask, dp(tile, 28));
                SystemUiMonetHooks.setField(tile, "ripple", copy);
                SystemUiMonetHooks.setField(tile, "colorBackgroundDrawable", background);
                tile.setBackground(tile.isClickable() ? copy : background);
            }
        }
    }
    private static void round(Drawable drawable, int radius) {
        drawable = drawable.mutate();
        if (drawable instanceof GradientDrawable) ((GradientDrawable) drawable).setCornerRadius(radius);
        else if (drawable instanceof DrawableWrapper) round(((DrawableWrapper) drawable).getDrawable(), radius);
    }
}
