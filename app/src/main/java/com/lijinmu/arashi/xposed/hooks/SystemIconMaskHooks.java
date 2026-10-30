package com.lijinmu.arashi.xposed.hooks;

import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.util.Log;
import com.lijinmu.arashi.xposed.ConfigManager;
import com.lijinmu.arashi.xposed.HookConfig;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

/** Per-process adaptive mask replacement; original Android rendering and layer geometry stay intact. */
public final class SystemIconMaskHooks {
    private static final String TAG = "[Arashi-SystemIcons]";
    private static final Map<AdaptiveIconDrawable, OriginalMask> ORIGINALS = new WeakHashMap<>();
    private static Field maskField, scaleField, transparentField;
    private static boolean installed;
    private static boolean warned;

    private SystemIconMaskHooks() {}

    public static synchronized void hook(XposedModule module) {
        if (installed) return;
        try {
            Class<AdaptiveIconDrawable> type = AdaptiveIconDrawable.class;
            maskField = field(type, "mMask");
            scaleField = field(type, "mMaskScaleOnly");
            transparentField = field(type, "mTransparentRegion");
            // Android 12 and 14 rebuild both paths here whenever bounds/theme changes.
            Method bounds = type.getDeclaredMethod("updateMaskBoundsInternal", Rect.class);
            module.hook(bounds).intercept(chain -> {
                Object result = chain.proceed();
                refreshSafely((AdaptiveIconDrawable) chain.getThisObject(), true);
                return result;
            });
            before(module, type.getDeclaredMethod("draw", Canvas.class));
            before(module, type.getDeclaredMethod("getIconMask"));
            before(module, type.getDeclaredMethod("getOutline", Outline.class));
            before(module, type.getDeclaredMethod("getTransparentRegion"));
            before(module, type.getDeclaredMethod("getSafeZone"));
            installed = true;
            Log.i(TAG, "Adaptive icon mask hooks registered");
        } catch (Throwable error) {
            Log.w(TAG, "Adaptive icon mask hooks unavailable", error);
            com.lijinmu.arashi.logs.HookLogBridge.error("系统 iOS 图标遮罩 Hook 注册失败", error);
        }
    }

    private static Field field(Class<?> type, String name) throws ReflectiveOperationException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void before(XposedModule module, Method method) {
        module.hook(method).intercept(chain -> {
            refreshSafely((AdaptiveIconDrawable) chain.getThisObject(), false);
            return chain.proceed();
        });
    }

    private static void refreshSafely(AdaptiveIconDrawable icon, boolean reset) {
        try {
            refresh(icon, reset, ConfigManager.isEnabledCached(HookConfig.KEY_IOS_SYSTEM_ICON_MASK));
        } catch (Exception error) {
            if (!warned) {
                warned = true;
                Log.w(TAG, "Unable to refresh adaptive mask", error);
                com.lijinmu.arashi.logs.HookLogBridge.error("系统 iOS 图标遮罩刷新失败", error);
            }
        }
    }

    private static void refresh(AdaptiveIconDrawable icon, boolean reset, boolean enabled)
            throws IllegalAccessException {
        synchronized (ORIGINALS) {
            // At this point Android has already restored its original mask for the new bounds.
            if (reset) ORIGINALS.remove(icon);
            OriginalMask original = ORIGINALS.get(icon);
            if (!enabled && original == null) return;
            Object maskValue = maskField.get(icon), scaleValue = scaleField.get(icon);
            if (!(maskValue instanceof Path) || !(scaleValue instanceof Path)) return;
            Path mask = (Path) maskValue, scaleOnly = (Path) scaleValue;
            if (!enabled) {
                mask.set(original.mask);
                scaleOnly.set(original.scaleOnly);
                ORIGINALS.remove(icon);
            } else {
                if (original != null) return; // No path allocations on steady-state draw calls.
                original = new OriginalMask(mask, scaleOnly);
                Rect bounds = icon.getBounds();
                RectF full = bounds.isEmpty() ? new RectF(0, 0, 100, 100) : new RectF(bounds);
                RectF local = bounds.isEmpty() ? new RectF(0, 0, 100, 100)
                        : new RectF(0, 0, bounds.width(), bounds.height());
                Path iosMask = IosIconDrawable.maskFor(full);
                Path iosScale = IosIconDrawable.maskFor(local);
                mask.set(iosMask);
                scaleOnly.set(iosScale);
                ORIGINALS.put(icon, original);
            }
            // getTransparentRegion caches a region derived from mMask, including inverse fill.
            Object region = transparentField.get(icon);
            if (region instanceof Region) ((Region) region).setEmpty();
        }
    }

    private static final class OriginalMask {
        final Path mask, scaleOnly;
        OriginalMask(Path mask, Path scaleOnly) {
            this.mask = new Path(mask);
            this.scaleOnly = new Path(scaleOnly);
        }
    }
}
