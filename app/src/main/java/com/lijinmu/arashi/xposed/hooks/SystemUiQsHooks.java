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
import android.view.ViewOutlineProvider;
import android.graphics.Outline;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.TextClock;
import android.text.format.DateFormat;
import java.util.Locale;
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
import java.util.WeakHashMap;
import java.lang.ref.WeakReference;

/** Restore the retained AOSP QS hierarchy and expansion path in the supplied Zybos 12/14 builds. */
public final class SystemUiQsHooks {
    private static final String TAG = "[Arashi-AospQs]";
    private static final Map<View, Integer> STATUS_BARS = new WeakHashMap<>();
    private static boolean shadeOpen;
    private static int statusBarState;
    private static boolean lastBarEnabled;
    private static final Map<View, NativeBounds> NATIVE_BOUNDS = new WeakHashMap<>();
    private static final Map<View, Boolean> MEDIA_PLAYERS = new WeakHashMap<>();
    private static final Map<ViewGroup, WeakReference<TextClock>> TOP_CLOCKS = new WeakHashMap<>();
    private static final Map<View, NotificationTopInset> NOTIFICATION_TOP_INSETS = new WeakHashMap<>();
    private static final class NotificationTopInset {
        int nativeTop;
        int appliedTop;
        NotificationTopInset(int top) { nativeTop = appliedTop = top; }
    }
    private static final Map<String, Integer> DIMENSIONS = new HashMap<>();
    static {
        DIMENSIONS.put("notification_corner_radius", 28);
        DIMENSIONS.put("notification_corner_radius_small", 4);
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
        hookShadeBounds(module, loader);
        hookNotificationCorners(module, loader);
        hookLockscreenClipping(module, loader);
        hookLockscreenNotificationPosition(module, loader);
        hookGlobalStatusBar(module, loader);
        for (String container : new String[] {
                "com.android.systemui.shade.NotificationsQuickSettingsContainer",
                "com.android.systemui.statusbar.phone.NotificationsQuickSettingsContainer"}) {
            after(module, loader, container, "onFinishInflate", root -> restoreTopStatusRow((ViewGroup) root));
            after(module, loader, container, "onAttachedToWindow", root -> restoreTopStatusRow((ViewGroup) root));
            after(module, loader, container, "onConfigurationChanged", root -> restoreTopStatusRow((ViewGroup) root));
        }
        hookMediaAndFooter(module, loader);
        hookNotificationInteractions(module, loader);
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
    static boolean portrait(Resources resources) {
        return resources.getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_PORTRAIT;
    }

    private static boolean isVendorStatusRow(View view) {
        boolean icons = false;
        for (String name : new String[] {"system_icon_area", "system_icons", "statusIcons"}) {
            int id = view.getResources().getIdentifier(name, "id", "com.android.systemui");
            if (id != 0 && view.getId() == id) icons = true;
        }
        if (!icons) return false;
        View ancestor = view;
        while (ancestor != null) {
            // The AOSP header owns its own icons. Only suppress the vendor's extra row.
            for (String name : new String[] {"qs_frame", "header", "quick_qs_status_icons",
                    "large_screen_shade_header", "shade_header"}) {
                int id = view.getResources().getIdentifier(name, "id", "com.android.systemui");
                if (id != 0 && ancestor.getId() == id) return false;
            }
            String type = ancestor.getClass().getName();
            if (type.endsWith(".QuickStatusBarHeader")) return false;
            if (type.endsWith(".NotificationsQuickSettingsContainer")) return true;
            Object parent = ancestor.getParent();
            ancestor = parent instanceof View ? (View) parent : null;
        }
        return false;
    }

    static void hideVendorStatusRow(ViewGroup root) {
        // Inspect every branch: findViewById may return a native header's identically
        // named group before the vendor row, and the vendor may wrap its icons.
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (isVendorStatusRow(child) && !isTopBarSubtree(child)) child.setVisibility(View.GONE);
            else if (child instanceof ViewGroup) hideVendorStatusRow((ViewGroup) child);
        }
    }

    private static boolean isTopBarSubtree(View view) {
        View ancestor = view;
        while (ancestor != null) {
            if (ancestor instanceof ViewGroup && TOP_CLOCKS.containsKey((ViewGroup) ancestor)) return true;
            Object parent = ancestor.getParent();
            ancestor = parent instanceof View ? (View) parent : null;
        }
        return false;
    }

    static void restoreTopStatusRow(ViewGroup root) {
        int rowId = root.getResources().getIdentifier("system_icon_area", "id", "com.android.systemui");
        View row = null;
        for (int i = 0; i < root.getChildCount(); i++)
            if (root.getChildAt(i).getId() == rowId) { row = root.getChildAt(i); break; }
        // Only the vendor's direct, full-width top row, never nested AOSP header icons.
        if (!(row instanceof LinearLayout) || row.getParent() != root) return;
        LinearLayout bar = (LinearLayout) row;
        WeakReference<TextClock> reference = TOP_CLOCKS.get(bar);
        TextClock clock = reference == null ? null : reference.get();
        if (clock == null) {
            clock = new TextClock(bar.getContext());
            clock.setSingleLine(true);
            clock.setTextSize(14);
            clock.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            bar.addView(clock, 0, new LinearLayout.LayoutParams(0, -1, 1f));
            TOP_CLOCKS.put(bar, new WeakReference<>(clock));
        }
        Locale locale = Locale.getDefault();
        String date = DateFormat.getBestDateTimePattern(locale, "MMMdEEE");
        clock.setFormat12Hour(DateFormat.getBestDateTimePattern(locale, "hm") + "  " + date);
        clock.setFormat24Hour(DateFormat.getBestDateTimePattern(locale, "Hm") + "  " + date);
        clock.setTextColor(neutral(bar.getResources(), true));
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        bar.setPaddingRelative(dp(bar, 16), 0, dp(bar, 16), 0);
        ViewGroup.LayoutParams params = bar.getLayoutParams();
        if (params != null) {
            params.height = dp(bar, 32);
            try {
                if (SystemUiQsExpansion.field(params.getClass(), "leftToLeft") != null) {
                    params.width = 0;
                    setLayoutField(params, "leftToLeft", 0);
                    setLayoutField(params, "rightToRight", 0);
                    setLayoutField(params, "topToTop", 0);
                    setLayoutField(params, "startToStart", -1);
                    setLayoutField(params, "endToEnd", -1);
                } else params.width = -1;
            } catch (ReflectiveOperationException error) {
                Log.w(TAG, "Top row constraints unavailable", error);
            }
            bar.setLayoutParams(params);
        }
        hideVendorStatusRow(root);
        normalizeTopStatusSize(bar);
        if (bar.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) bar.getLayoutParams();
            margins.topMargin = dp(bar, 48);
            bar.setLayoutParams(margins);
        }
        applyTopStatusVisibility();
        View lowerHeader = find(root, "split_shade_status_bar");
        if (lowerHeader != null) lowerHeader.setVisibility(View.INVISIBLE);
    }


    private static void normalizeTopStatusSize(ViewGroup row) {
        for (int i = 0; i < row.getChildCount(); i++) {
            View child = row.getChildAt(i);
            if (child instanceof TextView) ((TextView) child).setTextSize(14);
            if (child instanceof ViewGroup) normalizeTopStatusSize((ViewGroup) child);
        }
        for (String name : new String[] {"battery", "statusIcons"}) {
            View icons = find(row, name);
            if (icons == null || icons.getLayoutParams() == null) continue;
            ViewGroup.LayoutParams params = icons.getLayoutParams();
            params.height = dp(row, 20);
            icons.setLayoutParams(params);
        }
    }

    private static void applyTopStatusVisibility() {
        boolean keyguard = statusBarState == 1;
        int visibility = enabled() && (shadeOpen || keyguard) ? View.VISIBLE : View.INVISIBLE;
        for (Map.Entry<ViewGroup, WeakReference<TextClock>> entry : TOP_CLOCKS.entrySet()) {
            ViewGroup row = entry.getKey();
            TextClock clock = entry.getValue().get();
            if (clock != null) applyBarVisibility(clock, keyguard ? View.GONE : View.VISIBLE);
            if (row.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) row.getLayoutParams();
                int top = keyguard ? 0 : dp(row, 48);
                if (params.topMargin != top) { params.topMargin = top; row.setLayoutParams(params); }
            }
            applyBarVisibility(row, visibility);
        }
    }

    private static final String[] BOUND_FIELDS = {"width", "height", "leftMargin", "rightMargin",
            "topMargin", "bottomMargin", "gravity", "leftToLeft", "leftToRight", "rightToLeft",
            "rightToRight", "startToStart", "startToEnd", "endToStart", "endToEnd"};

    private static final class NativeBounds {
        final Class<?> paramsType;
        final java.lang.reflect.Field[] fields = new java.lang.reflect.Field[BOUND_FIELDS.length];
        final int[] nativeValues = new int[BOUND_FIELDS.length], applied = new int[BOUND_FIELDS.length];
        NativeBounds(Object params) throws ReflectiveOperationException {
            paramsType = params.getClass();
            for (int i = 0; i < fields.length; i++) {
                fields[i] = SystemUiQsExpansion.field(params.getClass(), BOUND_FIELDS[i]);
                if (fields[i] != null) nativeValues[i] = applied[i] = fields[i].getInt(params);
            }
        }
        void observe(Object params) throws ReflectiveOperationException {
            for (int i = 0; i < fields.length; i++) if (fields[i] != null) {
                int value = fields[i].getInt(params);
                if (value != applied[i]) nativeValues[i] = value;
            }
        }
        void remember(Object params) throws ReflectiveOperationException {
            for (int i = 0; i < fields.length; i++)
                if (fields[i] != null) applied[i] = fields[i].getInt(params);
        }
        void restore(View view) throws ReflectiveOperationException {
            Object params = view.getLayoutParams();
            boolean changed = false;
            for (int i = 0; i < fields.length; i++) if (fields[i] != null
                    && fields[i].getInt(params) != nativeValues[i]) {
                fields[i].setInt(params, nativeValues[i]); changed = true;
            }
            if (changed) view.setLayoutParams((ViewGroup.LayoutParams) params);
        }
    }

    private static void observeNativeBounds(View view) throws ReflectiveOperationException {
        if (view.getLayoutParams() == null) return;
        NativeBounds state = NATIVE_BOUNDS.get(view);
        if (state == null || state.paramsType != view.getLayoutParams().getClass()) NATIVE_BOUNDS.put(view, new NativeBounds(view.getLayoutParams()));
        else state.observe(view.getLayoutParams());
    }

    private static void restoreNativeBounds(View view) throws ReflectiveOperationException {
        NativeBounds state = NATIVE_BOUNDS.remove(view);
        if (state != null) state.restore(view);
        NOTIFICATION_TOP_INSETS.remove(view);
    }

    static boolean splitShade(Resources resources) {
        return resources.getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                && resources.getConfiguration().screenWidthDp >= 600;
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


    private static void hookLockscreenNotificationPosition(XposedModule module, ClassLoader loader) {
        try {
            Class<?> type = Class.forName("com.android.systemui.shade.NotificationPanelViewController", false, loader);
            Method method = Reflect.findMethodExact(type, "updateClockAppearance");
            if (method == null) return;
            try { module.deoptimize(method); } catch (Throwable ignored) {}
            module.hook(method).intercept(chain -> {
                Object algorithm = null;
                java.lang.reflect.Field margin = null;
                int original = 0;
                if (enabled()) {
                    Object controller = chain.getThisObject();
                    Object state = Reflect.getObjectField(controller, "mBarState");
                    if (Integer.valueOf(1).equals(state)
                            && SystemUiQsExpansion.flag(controller, "mSplitShadeEnabled")) {
                        Object owner = Reflect.getObjectField(controller, "mNotificationStackScrollLayoutController");
                        Object stack = Reflect.getObjectField(owner, "mView");
                        algorithm = Reflect.getObjectField(controller, "mClockPositionAlgorithm");
                        if (stack instanceof View && algorithm != null
                                && ((View) stack).getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                            margin = SystemUiQsExpansion.field(algorithm.getClass(), "mSplitShadeTopNotificationsMargin");
                            if (margin != null) {
                                original = margin.getInt(algorithm);
                                // AOSP subtracts the stack's header-height top margin from clockY.
                                // Zybos XML places the stack at 0dp instead: subtract its actual
                                // margin, otherwise notifications move up by an entire header.
                                int top = ((ViewGroup.MarginLayoutParams) ((View) stack).getLayoutParams()).topMargin;
                                margin.setInt(algorithm, Math.max(0, top));
                            }
                        }
                    }
                }
                try { return chain.proceed(); }
                finally { if (margin != null) margin.setInt(algorithm, original); }
            });
        } catch (ClassNotFoundException ignored) {
        } catch (Throwable error) { Log.w(TAG, "Lockscreen notification position hook unavailable", error); }
    }

    private static void hookLockscreenClipping(XposedModule module, ClassLoader loader) {
        String stack = "com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout";
        after(module, loader, stack, "updateClipping", SystemUiQsHooks::restoreLockscreenClipping);
        after(module, loader, stack, "updateUseRoundedRectClipping", SystemUiQsHooks::restoreLockscreenClipping);
        // Android 14 writes the clipping path directly, after updating QS expansion.
        after(module, loader, "com.android.systemui.shade.QuickSettingsController", "applyClippingImmediately", controller -> {
            Object owner = Reflect.getObjectField(controller, "mNotificationStackScrollLayoutController");
            Object view = Reflect.getObjectField(owner, "mView");
            if (view != null) restoreLockscreenClipping(view);
        });
    }

    static void restoreLockscreenClipping(Object object) throws ReflectiveOperationException {
        if (!(object instanceof View)) return;
        Method keyguard = Reflect.findMethodExact(object.getClass(), "onKeyguard");
        if (keyguard == null || !Boolean.TRUE.equals(keyguard.invoke(object))) return;
        // Keep ambient/doze hiding. Only remove the QS viewport mask on the lockscreen.
        Object ambient = Reflect.getObjectField(object, "mAmbientState");
        if (ambient != null) {
            Method hidden = Reflect.findMethodExact(ambient.getClass(), "isHiddenAtAll");
            if (hidden != null && Boolean.TRUE.equals(hidden.invoke(ambient))) return;
        }
        for (String name : new String[] {"mShouldUseRoundedRectClipping", "mIsClipped"}) {
            java.lang.reflect.Field field = SystemUiQsExpansion.field(object.getClass(), name);
            if (field != null && field.getBoolean(object)) field.setBoolean(object, false);
        }
        View view = (View) object;
        if (view.getClipBounds() != null) view.setClipBounds(null);
        if (view.getClipToOutline()) view.setClipToOutline(false);
    }

    private static void hookShadeBounds(XposedModule module, ClassLoader loader) {
        for (String name : new String[] {
                "com.android.systemui.shade.NotificationsQuickSettingsContainer",
                "com.android.systemui.statusbar.phone.NotificationsQuickSettingsContainer"}) {
            try {
                Class<?> type = Class.forName(name, false, loader);
                Method measure = Reflect.findMethodExact(type, "onMeasure", int.class, int.class);
                if (measure == null) continue;
                try { module.deoptimize(measure); } catch (Throwable ignored) {}
                module.hook(measure).intercept(chain -> {
                    if (enabled()) {
                        try {
                            restoreShadeBounds((ViewGroup) chain.getThisObject(),
                                    View.MeasureSpec.getSize((Integer) chain.getArg(0)));
                        } catch (ReflectiveOperationException | RuntimeException error) {
                            Log.w(TAG, "Shade bounds unavailable", error);
                        }
                    }
                    return chain.proceed();
                });
            } catch (ClassNotFoundException ignored) {
            } catch (Throwable error) { Log.w(TAG, "Shade bounds hook unavailable", error); }
        }
    }

    // Vendor XML keeps a landscape-sized, centered QS frame after rotation. Bound the
    // existing views before measuring; do not reparent notification rows or media controls.
    static void restoreShadeBounds(ViewGroup root, int width) throws ReflectiveOperationException {
        if (width <= 0) return;
        View qs = find(root, "qs_frame");
        View notifications = find(root, "notification_stack_scroller");
        if (qs == null || notifications == null || qs.getParent() != root
                || notifications.getParent() != root) return;
        observeNativeBounds(qs);
        observeNativeBounds(notifications);
        Method onKeyguard = Reflect.findMethodExact(notifications.getClass(), "onKeyguard");
        boolean keyguard = statusBarState == 1
                || (onKeyguard != null && Boolean.TRUE.equals(onKeyguard.invoke(notifications)));
        if (keyguard) {
            restoreNativeBounds(qs);
            restoreNativeBounds(notifications);
            return;
        }
        int edge = dp(root, portrait(root.getResources()) ? 24 : 16);
        int gap = dp(root, 16);
        boolean split = splitShade(root.getResources());
        int column = split ? Math.max(1, (width - edge * 2 - gap) / 2)
                : Math.max(1, width - edge * 2);
        int bottom = dp(root, 32);
        // Includes the gesture/navigation inset and still leaves breathing room above it.
        Method insetsMethod = Reflect.findMethodExact(root.getClass(), "getRootWindowInsets");
        if (insetsMethod != null) {
            Object insets = insetsMethod.invoke(root);
            if (insets != null) {
                Method stable = Reflect.findMethodExact(insets.getClass(), "getStableInsetBottom");
                if (stable != null) bottom = Math.max(bottom, ((Number) stable.invoke(insets)).intValue() + edge);
            }
        }
        boundColumn(qs, column, edge, bottom);
        if (!TOP_CLOCKS.isEmpty() && qs.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) qs.getLayoutParams();
            int minimum = dp(root, 28);
            if (margins.topMargin < minimum) {
                margins.topMargin = minimum;
                qs.setLayoutParams(margins);
            }
        }
        // A pinned banner uses the same stack as the shade: center it while the shade
        // is collapsed. Keep the right-hand stack on the lock screen.
        // Pinned mode ends before the exit animation finishes. Keep both phases
        // in the centered banner layout, so the card does not jump to the shade column.
        boolean banner = (Boolean.TRUE.equals(Reflect.getObjectField(notifications, "mInHeadsUpPinnedMode"))
                || Boolean.TRUE.equals(Reflect.getObjectField(notifications, "mHeadsUpAnimatingAway")))
                && Boolean.FALSE.equals(Reflect.getObjectField(notifications, "mIsExpanded"))
                && !Boolean.TRUE.equals(Reflect.getObjectField(
                        Reflect.getObjectField(notifications, "mAmbientState"), "mOnKeyguard"));
        int notificationStart = split ? width - edge - column : edge;
        if (banner) notificationStart = (width - column) / 2;
        boundColumn(notifications, column, notificationStart, bottom);
        boolean expanded = !Boolean.FALSE.equals(Reflect.getObjectField(notifications, "mIsExpanded"));
        insetNotificationTop(notifications, split && expanded && !banner ? dp(root, 64) : 0);
        NATIVE_BOUNDS.get(qs).remember(qs.getLayoutParams());
        NATIVE_BOUNDS.get(notifications).remember(notifications.getLayoutParams());
    }


    static void insetNotificationTop(View notifications, int extra) {
        ViewGroup.LayoutParams params = notifications.getLayoutParams();
        if (!(params instanceof ViewGroup.MarginLayoutParams)) return;
        ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) params;
        NotificationTopInset state = NOTIFICATION_TOP_INSETS.get(notifications);
        if (state == null) {
            state = new NotificationTopInset(margins.topMargin);
            NOTIFICATION_TOP_INSETS.put(notifications, state);
        } else if (margins.topMargin != state.appliedTop) {
            // The native controller can replace margins on rotation/configuration updates.
            state.nativeTop = margins.topMargin;
        }
        state.appliedTop = state.nativeTop + extra;
        if (margins.topMargin != state.appliedTop) {
            margins.topMargin = state.appliedTop;
            notifications.setLayoutParams(params);
        }
    }

    private static void boundColumn(View view, int width, int left, int bottom)
            throws ReflectiveOperationException {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params == null) return;
        // Retain all native vertical constraints, expansion heights and animation state.
        // Both FrameLayout (vendor) and ConstraintLayout (AOSP) are supported.
        boolean frame = params instanceof FrameLayout.LayoutParams;
        boolean constraint = SystemUiQsExpansion.field(params.getClass(), "startToStart") != null;
        if (!frame && !constraint) return;
        String previous = layoutBoundsKey(params);
        params.width = width;
        setLayoutField(params, "leftMargin", left);
        setLayoutField(params, "rightMargin", 0);
        Object previousBottom = Reflect.getObjectField(params, "bottomMargin");
        if (previousBottom instanceof Integer)
            setLayoutField(params, "bottomMargin", Math.max(bottom, (Integer) previousBottom));
        if (frame) setLayoutField(params, "gravity", Gravity.TOP | Gravity.LEFT);
        else {
            for (String field : new String[] {"leftToLeft", "leftToRight", "rightToLeft",
                    "rightToRight", "startToStart", "startToEnd", "endToStart", "endToEnd"})
                setLayoutField(params, field, -1);
            setLayoutField(params, "leftToLeft", 0);
        }
        // Clear resolved relative margins left by the vendor's centered/RTL layout.
        if (params instanceof ViewGroup.MarginLayoutParams) {
            ((ViewGroup.MarginLayoutParams) params).setMarginStart(Integer.MIN_VALUE);
            ((ViewGroup.MarginLayoutParams) params).setMarginEnd(Integer.MIN_VALUE);
        }
        // Rebuild ConstraintLayout's solver only when bounds actually change.
        if (!previous.equals(layoutBoundsKey(params))) view.setLayoutParams(params);
    }

    private static String layoutBoundsKey(Object params) {
        StringBuilder key = new StringBuilder();
        for (String field : new String[] {"width", "leftMargin", "rightMargin", "bottomMargin",
                "gravity", "leftToLeft", "leftToRight", "rightToLeft", "rightToRight",
                "startToStart", "startToEnd", "endToStart", "endToEnd"})
            key.append(Reflect.getObjectField(params, field)).append(';');
        return key.toString();
    }

    private static void setLayoutField(Object target, String name, int value)
            throws ReflectiveOperationException {
        if (SystemUiQsExpansion.field(target.getClass(), name) != null)
            SystemUiQsExpansion.set(target, name, value);
    }


    private static void hookNotificationCorners(XposedModule module, ClassLoader loader) {
        String name = "com.android.systemui.statusbar.notification.row.NotificationBackgroundView";
        try {
            Class<?> type = Class.forName(name, false, loader);
            Method radius = Reflect.findMethodExact(type, "setRadius", float.class, float.class);
            if (radius != null) {
                try { module.deoptimize(radius); } catch (Throwable ignored) {}
                module.hook(radius).intercept(chain -> {
                    if (!enabled()) return chain.proceed();
                    Object view = chain.getThisObject();
                    Object corners = Reflect.getObjectField(view, "mCornerRadii");
                    if (!(corners instanceof float[]) || ((float[]) corners).length != 8
                            || SystemUiQsExpansion.field(type, "mBottomIsRounded") == null)
                        return chain.proceed();
                    float top = ((Number) chain.getArg(0)).floatValue();
                    float bottom = ((Number) chain.getArg(1)).floatValue();
                    try {
                        float[] values = (float[]) corners;
                        // AOSP passes pixel radii here, already interpolated by RoundableState.
                        // Do not force every notification/group child to a uniform full radius.
                        for (int i = 0; i < 8; i++) values[i] = i < 4 ? top : bottom;
                        SystemUiQsExpansion.set(view, "mBottomIsRounded", bottom != 0f);
                        if (!restoreNotificationBackground(view)) return chain.proceed();
                        ((View) view).invalidate();
                        return null;
                    } catch (ReflectiveOperationException | RuntimeException error) {
                        Log.w(TAG, "Notification roundness unavailable", error);
                        return chain.proceed();
                    }
                });
            }
            Method update = Reflect.findMethodExact(type, "updateBackgroundRadii");
            if (update != null) {
                try { module.deoptimize(update); } catch (Throwable ignored) {}
                module.hook(update).intercept(chain -> {
                    if (enabled()) {
                        try {
                            if (restoreNotificationBackground(chain.getThisObject())) return null;
                        } catch (RuntimeException error) {
                            Log.w(TAG, "Notification background unavailable", error);
                        }
                    }
                    return chain.proceed();
                });
            }
            // Covers drawable replacement after theme changes and notification reinflation.
            after(module, loader, name, "setCustomBackground",
                    view -> restoreNotificationBackground(view));
        } catch (ClassNotFoundException ignored) {
        } catch (Throwable error) { Log.w(TAG, "Notification corner hook unavailable", error); }
    }

    static boolean restoreNotificationBackground(Object view) {
        Object corners = Reflect.getObjectField(view, "mCornerRadii");
        Object background = Reflect.getObjectField(view, "mBackground");
        if (!(corners instanceof float[]) || ((float[]) corners).length != 8
                || !(background instanceof Drawable)) return false;
        // Mutate isolates this notification's animated radii from shared drawable state.
        return applyNotificationRadii(((Drawable) background).mutate(), (float[]) corners);
    }

    private static boolean applyNotificationRadii(Drawable drawable, float[] radii) {
        if (drawable instanceof GradientDrawable) {
            ((GradientDrawable) drawable).setCornerRadii(radii);
            return true;
        }
        if (drawable instanceof LayerDrawable) {
            boolean restored = false;
            LayerDrawable layers = (LayerDrawable) drawable;
            for (int i = 0; i < layers.getNumberOfLayers(); i++)
                restored |= applyNotificationRadii(layers.getDrawable(i), radii);
            return restored;
        }
        if (drawable instanceof DrawableWrapper) {
            Drawable child = ((DrawableWrapper) drawable).getDrawable();
            return child != null && applyNotificationRadii(child, radii);
        }
        return false;
    }


    private static void hookGlobalStatusBar(XposedModule module, ClassLoader loader) {
        // Locking can keep the panel fraction at 1 without another expansion callback.
        // Observe the state transition itself, after all native listeners have run.
        after(module, loader, "com.android.systemui.statusbar.StatusBarStateControllerImpl", "setState", controller -> {
            Object state = Reflect.getObjectField(controller, "mState");
            if (state instanceof Number) onStatusBarState(((Number) state).intValue());
        });
        String bar = "com.android.systemui.statusbar.phone.PhoneStatusBarView";
        observeAlways(module, loader, bar, "onAttachedToWindow", instance -> {
            View view = (View) instance;
            STATUS_BARS.putIfAbsent(view, view.getVisibility());
            refreshGlobalStatusBars();
        });
        observeAlways(module, loader, bar, "onDetachedFromWindow", instance -> {
            View view = (View) instance;
            Integer wanted = STATUS_BARS.remove(view);
            if (wanted != null) applyBarVisibility(view, wanted);
        });
        observeAlways(module, loader, "com.android.systemui.shade.NotificationPanelViewController",
                "updateExpansionAndVisibility", SystemUiQsHooks::onShadeExpansion);
        observeAlways(module, loader, "com.android.systemui.statusbar.phone.PanelViewController",
                "notifyBarPanelExpansionChanged", SystemUiQsHooks::onShadeExpansion);
    }

    static void onShadeExpansion(Object controller) throws ReflectiveOperationException {
        Object fraction = Reflect.getObjectField(controller, "mExpandedFraction");
        if (!(fraction instanceof Number)) return;
        float value = ((Number) fraction).floatValue();
        Object barState = Reflect.getObjectField(controller, "mBarState");
        if (barState instanceof Number) onStatusBarState(((Number) barState).intValue());
        boolean keyguard = statusBarState == 1;
        Object qs = Reflect.getObjectField(controller, "mQsController");
        boolean qsExpanded = SystemUiQsExpansion.flag(qs, "mExpanded")
                || SystemUiQsExpansion.flag(controller, "mQsExpanded");
        boolean pinned = SystemUiQsExpansion.flag(controller, "mHeadsUpPinnedMode");
        boolean tracking = SystemUiQsExpansion.flag(controller, "mTracking");
        boolean open = Float.isFinite(value) && value > 0f && !keyguard
                && (!pinned || tracking || qsExpanded || value >= 1f);
        if (open == shadeOpen && lastBarEnabled == enabled()) return;
        if (open && !shadeOpen) {
            for (Map.Entry<View, Integer> entry : STATUS_BARS.entrySet())
                entry.setValue(entry.getKey().getVisibility());
        }
        shadeOpen = open;
        lastBarEnabled = enabled();
        refreshGlobalStatusBars();
        applyTopStatusVisibility();
    }

    static void onStatusBarState(int state) throws ReflectiveOperationException {
        if (statusBarState == state) return;
        statusBarState = state;
        if (state == 1) {
            shadeOpen = false;
            // Restore before the lockscreen animation, rather than waiting for a measure.
            for (ViewGroup row : TOP_CLOCKS.keySet()) {
                Object parent = row.getParent();
                if (!(parent instanceof View)) continue;
                View root = (View) parent;
                View qs = find(root, "qs_frame"), stack = find(root, "notification_stack_scroller");
                if (qs != null) restoreNativeBounds(qs);
                if (stack != null) restoreNativeBounds(stack);
            }
        }
        refreshGlobalStatusBars();
        applyTopStatusVisibility();
    }

    private static void refreshGlobalStatusBars() {
        for (Map.Entry<View, Integer> entry : STATUS_BARS.entrySet()) {
            int wanted = entry.getValue();
            applyBarVisibility(entry.getKey(),
                    enabled() && shadeOpen && wanted == View.VISIBLE ? View.INVISIBLE : wanted);
        }
    }

    private static void applyBarVisibility(View view, int visibility) {
        if (view.getVisibility() == visibility) return;
        view.setVisibility(visibility);
    }

    // Unlike the feature-gated after() helper, these observers must also restore the
    // global bar when the setting is turned off while the shade is still expanded.
    private static void observeAlways(XposedModule module, ClassLoader loader,
            String name, String target, Action action) {
        try {
            Class<?> type = Class.forName(name, false, loader);
            Method method = Reflect.findMethodExact(type, target);
            if (method == null) return;
            try { module.deoptimize(method); } catch (Throwable ignored) {}
            module.hook(method).intercept(chain -> {
                Object result = chain.proceed();
                try { action.run(chain.getThisObject()); }
                catch (ReflectiveOperationException | RuntimeException error) {
                    Log.w(TAG, "Status bar state update failed", error);
                }
                return result;
            });
        } catch (ClassNotFoundException ignored) {
        } catch (Throwable error) { Log.w(TAG, "Status bar observer unavailable", error); }
    }


    private static final ViewOutlineProvider MEDIA_OUTLINE = new ViewOutlineProvider() {
        @Override public void getOutline(View view, Outline outline) {
            outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(view, 28));
        }
    };

    static void restoreMediaCard(View player) {
        if (player.getOutlineProvider() != MEDIA_OUTLINE) player.setOutlineProvider(MEDIA_OUTLINE);
        if (!player.getClipToOutline()) player.setClipToOutline(true);
        // Keep the carousel's native measured height and child margins. Adding
        // margins to the player shifts content beyond the parent's clipping window.
    }

    static void restoreFooter(View footer) {
        // Clear only the outer bar, retaining the chip/button drawables and click targets.
        footer.setBackground(null);
        footer.setElevation(0f);
    }

    private static void hookMediaAndFooter(XposedModule module, ClassLoader loader) {
        after(module, loader, "com.android.systemui.util.animation.TransitionLayout",
                "onLayout", player -> {
                    if (MEDIA_PLAYERS.containsKey((View) player)) restoreMediaCard((View) player);
                });
        hookMediaMeasurement(module, loader);
        for (String name : new String[] {
                "com.android.systemui.media.controls.models.player.MediaViewHolder",
                "com.android.systemui.media.PlayerViewHolder",
                "com.android.systemui.media.MediaViewHolder"}) {
            try {
                Class<?> type = Class.forName(name, false, loader);
                for (Constructor<?> constructor : type.getDeclaredConstructors()) {
                    module.hook(constructor).intercept(chain -> {
                        Object result = chain.proceed();
                        if (enabled()) {
                            Object player = Reflect.getObjectField(chain.getThisObject(), "player");
                            if (player instanceof View) {
                                MEDIA_PLAYERS.put((View) player, Boolean.TRUE);
                                restoreMediaCard((View) player);
                            }
                        }
                        return result;
                    });
                }
            } catch (ClassNotFoundException ignored) {
            } catch (Throwable error) { Log.w(TAG, "Media card hook unavailable", error); }
        }
        // 14 constructs its footer through a static binder rather than a custom view.
        try {
            Class<?> type = Class.forName(
                    "com.android.systemui.qs.footer.ui.binder.FooterActionsViewBinder", false, loader);
            for (Method method : type.getDeclaredMethods()) {
                if (!method.getName().equals("bind") || method.getParameterTypes().length == 0
                        || !View.class.isAssignableFrom(method.getParameterTypes()[0])) continue;
                try { module.deoptimize(method); } catch (Throwable ignored) {}
                module.hook(method).intercept(chain -> {
                    Object result = chain.proceed();
                    if (enabled() && chain.getArg(0) instanceof View)
                        restoreFooter((View) chain.getArg(0));
                    return result;
                });
            }
        } catch (ClassNotFoundException ignored) {
        } catch (Throwable error) { Log.w(TAG, "Footer background hook unavailable", error); }
        after(module, loader, "com.android.systemui.qs.FooterActionsView", "onFinishInflate",
                footer -> restoreFooter((View) footer));
        after(module, loader, "com.android.systemui.qs.FooterActionsView", "onConfigurationChanged",
                footer -> restoreFooter((View) footer));
    }


    private static void hookNotificationInteractions(XposedModule module, ClassLoader loader) {
        // Restore the gates before native row initialization installs its long-press
        // listener. R8 inlines that initialization into NotificationRowBinderImpl on 14.
        restoreNotificationGate(module, loader,
                "com.android.systemui.statusbar.notification.row.ExpandableNotificationRowController",
                "mAllowLongPress", true);
        restoreNotificationGate(module, loader,
                "com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayoutController",
                "mAllowLongPress", true);
        restoreNotificationGate(module, loader,
                "com.android.systemui.statusbar.notification.row.NotificationMenuRow",
                "mShouldShowMenu", false);
    }

    private static void restoreNotificationGate(XposedModule module, ClassLoader loader,
            String name, String field, boolean constructorFlag) {
        try {
            Class<?> type = Class.forName(name, false, loader);
            java.lang.reflect.Field gate = SystemUiQsExpansion.field(type, field);
            if (gate == null || gate.getType() != boolean.class) return;
            for (Constructor<?> constructor : type.getDeclaredConstructors()) {
                Class<?>[] parameters = constructor.getParameterTypes();
                int booleanIndex = -1;
                int booleanCount = 0;
                for (int i = 0; i < parameters.length; i++) {
                    if (parameters[i] == boolean.class) {
                        booleanIndex = i;
                        booleanCount++;
                    }
                }
                // The supplied 12/14 row and stack controllers have a single boolean
                // constructor argument for allowLongPress. Do not guess on other builds.
                final int allowIndex = constructorFlag && booleanCount == 1 ? booleanIndex : -1;
                module.hook(constructor).intercept(chain -> {
                    if (!enabled()) return chain.proceed();
                    Object result;
                    if (allowIndex >= 0) {
                        Object[] args = new Object[parameters.length];
                        for (int i = 0; i < args.length; i++) args[i] = chain.getArg(i);
                        args[allowIndex] = true;
                        result = chain.proceed(args);
                    } else {
                        result = chain.proceed();
                    }
                    try {
                        gate.setBoolean(chain.getThisObject(), true);
                    } catch (IllegalAccessException | RuntimeException error) {
                        Log.w(TAG, "Notification interaction gate unavailable: " + field, error);
                    }
                    return result;
                });
            }
        } catch (ClassNotFoundException ignored) {
        } catch (Throwable error) { Log.w(TAG, "Notification interaction hook unavailable: " + name, error); }
    }


    static int mediaViewportWidth(View player) {
        int width = Integer.MAX_VALUE;
        boolean inQs = false;
        View current = player;
        while (current != null) {
            String name = current.getClass().getName();
            if (name.endsWith(".MediaScrollView") && current.getWidth() > 0)
                width = Math.min(width, current.getWidth() - current.getPaddingLeft() - current.getPaddingRight());
            int qsId = current.getResources().getIdentifier("qs_frame", "id", "com.android.systemui");
            if (qsId != 0 && current.getId() == qsId) inQs = true;
            Object parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return inQs && width > 0 && width < Integer.MAX_VALUE ? width : 0;
    }

    private static void hookMediaMeasurement(XposedModule module, ClassLoader loader) {
        for (String name : new String[] {"com.android.systemui.media.controls.ui.MediaViewController",
                "com.android.systemui.media.MediaViewController"}) {
            try {
                Class<?> type = Class.forName(name, false, loader);
                for (Method method : type.getDeclaredMethods()) {
                    if (!method.getName().equals("obtainViewState") || method.getParameterTypes().length != 1) continue;
                    try { module.deoptimize(method); } catch (Throwable ignored) {}
                    module.hook(method).intercept(chain -> {
                        if (!enabled()) return chain.proceed();
                        Object player = Reflect.getObjectField(chain.getThisObject(), "transitionLayout");
                        int width = player instanceof View ? mediaViewportWidth((View) player) : 0;
                        Object state = chain.getArg(0);
                        if (width == 0 || state == null) return chain.proceed();
                        MediaWidthOverride change;
                        try { change = prepareMediaWidth(state, width); }
                        catch (ReflectiveOperationException | RuntimeException error) {
                            Log.w(TAG, "Media measurement unavailable", error);
                            return chain.proceed();
                        }
                        if (change == null) return chain.proceed();
                        // Adjust before the native cache key and ConstraintSet are measured.
                        // Native execution stays outside the setup catch: never retry a failed call.
                        try { return chain.proceed(); }
                        finally { change.spec.setInt(change.input, change.original); }
                    });
                }
            } catch (ClassNotFoundException ignored) {
            } catch (Throwable error) { Log.w(TAG, "Media measurement hook unavailable", error); }
        }
    }


    private static final class MediaWidthOverride {
        final Object input;
        final java.lang.reflect.Field spec;
        final int original;
        MediaWidthOverride(Object input, java.lang.reflect.Field spec, int original) {
            this.input = input; this.spec = spec; this.original = original;
        }
    }

    private static MediaWidthOverride prepareMediaWidth(Object state, int width)
            throws ReflectiveOperationException {
        Method getInput = Reflect.findMethodExact(state.getClass(), "getMeasurementInput");
        if (getInput == null) return null;
        Object input = getInput.invoke(state);
        if (input == null) return null;
        java.lang.reflect.Field spec = SystemUiQsExpansion.field(input.getClass(), "widthMeasureSpec");
        if (spec == null) return null;
        int original = spec.getInt(input);
        if (View.MeasureSpec.getSize(original) <= width) return null;
        spec.setInt(input, View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY));
        return new MediaWidthOverride(input, spec, original);
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
                        : !TOP_CLOCKS.isEmpty() ? View.INVISIBLE
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
                                if (entry.equals("com.android.systemui:bool/config_clipNotificationsToOutline"))
                                    return false;
                                if (entry.equals("com.android.systemui:bool/config_use_split_notification_shade"))
                                    return splitShade(resources);
                                return chain.proceed();
                            }
                            Integer value = name.equals("getInteger") ? integer(entry) : dimension(entry);
                            if (!name.equals("getInteger") && portrait(resources)) {
                                switch (entry) {
                                    case "com.android.systemui:dimen/qs_tile_margin_vertical": value = 12; break;
                                    case "com.android.systemui:dimen/qs_tile_margin_top_bottom": value = 6; break;
                                    case "com.android.systemui:dimen/qqs_layout_margin_top": value = 24; break;
                                    case "com.android.systemui:dimen/qqs_layout_padding_bottom": value = 32; break;
                                    default: break;
                                }
                            }
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
        int accent = SystemUiMonetHooks.accent(root.getResources());
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
        quick.setPaddingRelative(quick.getPaddingStart(), quick.getPaddingTop(), quick.getPaddingEnd(), dp(quick, portrait(quick.getResources()) ? 32 : 24));
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
                params.topMargin = dp(header, portrait(header.getResources()) ? 24 : 16);
                quick.setLayoutParams(params);
            }
        } else {
            // 14: the status header is managed separately by ShadeHeaderController.
            View oldParent = quick.getParent() instanceof View ? (View) quick.getParent() : null;
            if (quick.getParent() != header) {
                ((ViewGroup) quick.getParent()).removeView(quick);
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, -2);
                params.topMargin = dp(header, portrait(header.getResources()) ? 24 : 16);
                header.addView(quick, params);
                if (oldParent != null && oldParent.getParent() instanceof View) hide((View) oldParent.getParent());
            }
            if (quick.getLayoutParams() instanceof FrameLayout.LayoutParams) {
                FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) quick.getLayoutParams();
                params.topMargin = dp(header, portrait(header.getResources()) ? 24 : 16);
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
