package com.lijinmu.arashi.xposed.hooks;

import android.content.res.Resources;
import android.util.Log;
import android.view.ViewGroup;
import com.lijinmu.arashi.xposed.ConfigManager;
import com.lijinmu.arashi.xposed.HookConfig;
import com.lijinmu.arashi.xposed.Reflect;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Remove the count cap only; keep the firmware's width, filtering and overflow algorithm. */
public final class NotificationIconLimitHooks {
    private static final String TAG = "[Arashi-NotificationIcons]";
    private NotificationIconLimitHooks() {}

    public static void hookSystemUI(XposedModule module, ClassLoader loader) {
        try {
            Class<?> type = Class.forName("com.android.systemui.statusbar.phone.NotificationIconContainer", false, loader);
            if (!ViewGroup.class.isAssignableFrom(type)) throw new ClassCastException("not a ViewGroup");
            Field staticLayout = field(type, "mIsStaticLayout", boolean.class);
            Field lockscreen = field(type, "mOnLockScreen", boolean.class);
            Field maxStatic;
            try { maxStatic = field(type, "mMaxStaticIcons", int.class); }
            catch (NoSuchFieldException ignored) { maxStatic = null; }
            final Field limit = maxStatic;
            for (Method method : type.getDeclaredMethods()) {
                String name = method.getName();
                Class<?>[] args = method.getParameterTypes();
                boolean calculate = (name.equals("calculateIconTranslations") || name.equals("calculateIconXTranslations"))
                        && args.length == 0;
                boolean measure = name.equals("onMeasure") && args.length == 2
                        && args[0] == int.class && args[1] == int.class;
                if (method.getReturnType() != void.class || (!calculate && !(measure && limit != null))) continue;
                method.setAccessible(true);
                module.hook(method).intercept(chain -> {
                    Object owner = chain.getThisObject();
                    if (!ConfigManager.isEnabledCached(HookConfig.KEY_UNLIMITED_NOTIFICATION_ICONS)
                            || !(owner instanceof ViewGroup) || !isStatusBar((ViewGroup) owner)
                            || lockscreen.getBoolean(owner) || !staticLayout.getBoolean(owner)
                            || Boolean.TRUE.equals(Reflect.getObjectField(owner, "mDozing"))
                            || Boolean.TRUE.equals(Reflect.getObjectField(owner, "mInNotificationIconShelf")))
                        return chain.proceed();
                    if (limit != null) {
                        // Android 14 reads the cap in BOTH onMeasure and calculateIconXTranslations.
                        // Use the real child count rather than MAX_VALUE (measurement may do arithmetic).
                        int previous = limit.getInt(owner);
                        limit.setInt(owner, Math.max(previous, ((ViewGroup) owner).getChildCount()));
                        try { return chain.proceed(); }
                        finally { limit.setInt(owner, previous); }
                    }
                    // Android 12 inlines a four-icon constant when static layout is true.
                    // Its non-static calculation branch uses childCount, retaining width overflow.
                    staticLayout.setBoolean(owner, false);
                    try { return chain.proceed(); }
                    finally { staticLayout.setBoolean(owner, true); }
                });
                Log.i(TAG, "Installed count-cap hook: " + method);
            }
            // Keep ART from bypassing the hooked calculation through an inlined call site.
            for (Method method : type.getDeclaredMethods()) {
                if (method.getName().equals("onLayout") || method.getName().equals("updateState")) {
                    try { module.deoptimize(method); }
                    catch (Throwable error) { Log.w(TAG, "Unable to deoptimize " + method, error); }
                }
            }
        } catch (Throwable error) { Log.w(TAG, "Count-cap hook unavailable; keeping firmware layout", error); }
    }

    private static Field field(Class<?> owner, String name, Class<?> expected) throws NoSuchFieldException {
        Field field = owner.getDeclaredField(name);
        if (field.getType() != expected) throw new NoSuchFieldException(name + " has unexpected type");
        field.setAccessible(true);
        return field;
    }

    private static boolean isStatusBar(ViewGroup view) {
        if (view.getId() <= 0) return false;
        try {
            String name = view.getResources().getResourceName(view.getId());
            return name.equals("com.android.systemui:id/notificationIcons")
                    || name.equals("com.android.systemui:id/left_aligned_notification_icon_container");
        } catch (Resources.NotFoundException ignored) { return false; }
    }
}
