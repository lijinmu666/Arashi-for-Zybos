package com.lijinmu.arashi.xposed.hooks;

import android.content.Context;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import com.lijinmu.arashi.xposed.ConfigManager;
import com.lijinmu.arashi.xposed.HookConfig;
import com.lijinmu.arashi.xposed.Reflect;
import io.github.libxposed.api.XposedModule;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Restore retained AOSP notification preferences, with a bridge for the source-modified Android 12 switch. */
public final class AppNotificationHooks {
    private static final String TAG = "[Arashi-AppNotifications]";
    private static final String PREFIX = "com.android.settings.notification.app.";
    private static final Map<Object, Integer> PAGES = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Set<Object> LEGACY_BLOCKS = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<Object, WeakReference<LegacySwitch>> SWITCHES = new WeakHashMap<>();
    private static final Map<Object, WeakReference<LegacySwitch>> CONTROLS = new WeakHashMap<>();
    private AppNotificationHooks() {}

    public static void hookSettings(XposedModule module, ClassLoader loader) {
        try {
            Class<?> page = loader.loadClass(PREFIX + "AppNotificationSettings");
            Method create = Reflect.findMethodExact(page, "createPreferenceControllers", Context.class);
            Method xml = Reflect.findMethodExact(page, "getPreferenceScreenResId");
            if (create == null || xml == null) return;
            // Register bridges first; no page is selected until all required hooks exist.
            hookHeader(module, loader);
            hookLegacyBlock(module, loader);
            module.hook(create).intercept(chain -> {
                Object original = chain.proceed();
                if (!ConfigManager.isEnabled(HookConfig.KEY_AOSP_APP_NOTIFICATIONS)
                        || !(original instanceof List)) return original;
                Object fragment = chain.getThisObject();
                try {
                    Context context = (Context) chain.getArg(0);
                    List<?> controllers = (List<?>) original;
                    boolean modern = controllers.stream().anyMatch(c -> c.getClass().getName().equals(PREFIX + "ZybBlockPreferenceController"));
                    // All required retained widgets/resources must exist before selecting a layout.
                    loader.loadClass("com.android.settings.widget.SettingsMainSwitchPreference");
                    loader.loadClass("com.android.settingslib.widget.OnMainSwitchChangeListener");
                    resource(context, loader, "layout", "settings_entity_header");
                    resource(context, loader, "id", "entity_header_title");
                    resource(context, loader, "id", "entity_header_icon");
                    int screen = modern ? resource(context, loader, "xml", "app_notification_settings") : 0;
                    List<Object> restored = new ArrayList<>();
                    for (Object controller : controllers) {
                        String name = controller.getClass().getName();
                        if (modern && name.equals(PREFIX + "ZybAllowSoundPreferenceController")) continue;
                        if (modern && name.equals(PREFIX + "ZybBlockPreferenceController")) {
                            controller = loader.loadClass(PREFIX + "BlockPreferenceController").getConstructor(
                                    Context.class, loader.loadClass(PREFIX + "NotificationSettings$DependentFieldListener"),
                                    loader.loadClass("com.android.settings.notification.NotificationBackend"))
                                    .newInstance(context, Reflect.getObjectField(fragment, "mDependentFieldListener"),
                                            Reflect.getObjectField(fragment, "mBackend"));
                        }
                        restored.add(controller);
                    }
                    setField(fragment, "mControllers", restored);
                    PAGES.put(fragment, screen);
                    if (!modern) for (Object controller : restored) {
                        if (controller.getClass().getName().equals(PREFIX + "BlockPreferenceController")) LEGACY_BLOCKS.add(controller);
                    }
                    return restored;
                } catch (Throwable error) {
                    PAGES.remove(fragment);
                    Log.w(TAG, "Keeping original notification preferences", error);
                    return original;
                }
            });
            module.hook(xml).intercept(chain -> {
                Integer id = PAGES.get(chain.getThisObject());
                return id != null && id != 0 ? id : chain.proceed();
            });
            for (Method method : page.getDeclaredMethods()) module.deoptimize(method);
        } catch (Throwable error) { Log.w(TAG, "Notification restoration unavailable", error); }
    }

    private static void hookHeader(XposedModule module, ClassLoader loader) throws Exception {
        Class<?> type = loader.loadClass(PREFIX + "HeaderPreferenceController");
        Class<?> screenType = loader.loadClass("androidx.preference.PreferenceScreen");
        Class<?> preference = loader.loadClass("androidx.preference.Preference");
        Method display = Reflect.findMethodExact(type, "displayPreference", screenType);
        Method update = Reflect.findMethodExact(type, "updateState", preference);
        if (display == null || update == null) throw new NoSuchMethodException("notification header");
        module.hook(display).intercept(chain -> {
            Object controller = chain.getThisObject();
            Object fragment = Reflect.getObjectField(controller, "mFragment");
            Integer mode = PAGES.get(fragment);
            if (mode != null && mode != 0) {
                // The retained header controller still has a vendor-typed field. Keep a hidden
                // backing preference so its data refreshes continue without a class cast.
                Object screen = chain.getArg(0);
                if (find(screen, "pref_notice_header") == null) {
                    Context context = (Context) call(fragment, "getContext");
                    Object backing = newPreference(loader.loadClass("com.android.settings.zyb.widget.AppNotificationHeaderPreference"), context);
                    call(backing, "setKey", "pref_notice_header");
                    call(backing, "setVisible", false);
                    call(screen, "addPreference", backing);
                }
            }
            Object result = chain.proceed();
            if (mode != null) {
                try { restoreHeader(controller, chain.getArg(0), loader); }
                catch (Throwable error) { Log.w(TAG, "Keeping original notification header", error); }
            }
            return result;
        });
        module.hook(update).intercept(chain -> {
            Object result = chain.proceed();
            Object controller = chain.getThisObject();
            Object fragment = Reflect.getObjectField(controller, "mFragment");
            if (PAGES.containsKey(fragment)) {
                try { restoreHeader(controller, call(fragment, "getPreferenceScreen"), loader); }
                catch (Throwable error) { Log.w(TAG, "Cannot refresh notification header", error); }
            }
            return result;
        });
        for (Method method : type.getDeclaredMethods()) module.deoptimize(method);
    }

    private static void restoreHeader(Object controller, Object screen, ClassLoader loader) throws Exception {
        Object fragment = Reflect.getObjectField(controller, "mFragment");
        Context context = (Context) call(fragment, "getContext");
        Object row = Reflect.getObjectField(controller, "mAppRow");
        if (row == null) return;
        Object header = find(screen, "pref_app_header");
        boolean created = header == null;
        if (created) {
            header = loader.loadClass("com.android.settingslib.widget.LayoutPreference")
                    .getConstructor(Context.class, int.class).newInstance(context,
                            resource(context, loader, "layout", "settings_entity_header"));
            call(header, "setKey", "pref_app_header");
            call(header, "setOrder", -10000);
            call(header, "setSelectable", false);
        }
        Object title = call(header, "findViewById", resource(context, loader, "id", "entity_header_title"));
        Object icon = call(header, "findViewById", resource(context, loader, "id", "entity_header_icon"));
        if (!(title instanceof TextView) || !(icon instanceof ImageView)) throw new IllegalStateException("No AOSP entity header");
        ((TextView) title).setText((CharSequence) call(controller, "getLabel"));
        ((ImageView) icon).setImageDrawable((android.graphics.drawable.Drawable) Reflect.getObjectField(row, "icon"));
        if (created && !Boolean.TRUE.equals(call(screen, "addPreference", header))) {
            throw new IllegalStateException("Cannot add notification header");
        }
        Object old = find(screen, "pref_notice_header");
        if (old != null) call(old, "setVisible", false);
    }

    private static void hookLegacyBlock(XposedModule module, ClassLoader loader) throws Exception {
        Class<?> type = loader.loadClass(PREFIX + "BlockPreferenceController");
        Method update = Reflect.findMethodExact(type, "updateState", loader.loadClass("androidx.preference.Preference"));
        if (update == null) throw new NoSuchMethodException("notification block");
        module.hook(update).intercept(chain -> {
            Object result = chain.proceed(); // Preserve native controller state and restrictions.
            Object controller = chain.getThisObject();
            if (LEGACY_BLOCKS.contains(controller)) {
                WeakReference<LegacySwitch> ref = SWITCHES.get(controller);
                LegacySwitch state = ref == null ? null : ref.get();
                try {
                    if (state == null) {
                        state = new LegacySwitch(controller, chain.getArg(0), loader);
                        SWITCHES.put(controller, new WeakReference<>(state));
                    }
                    state.update();
                } catch (Throwable error) {
                    if (state != null) state.restore();
                    SWITCHES.remove(controller);
                    Log.w(TAG, "Keeping original notification switch", error);
                }
            }
            return result;
        });
        for (Method method : type.getDeclaredMethods()) module.deoptimize(method);
        Class<?> widget = loader.loadClass("com.android.settings.widget.SettingsMainSwitchPreference");
        Method bind = Reflect.findMethodExact(widget, "onBindViewHolder", loader.loadClass("androidx.preference.PreferenceViewHolder"));
        if (bind == null) throw new NoSuchMethodException("notification switch binding");
        module.hook(bind).intercept(chain -> {
            Object result = chain.proceed();
            WeakReference<LegacySwitch> ref = CONTROLS.get(chain.getThisObject());
            LegacySwitch state = ref == null ? null : ref.get();
            if (state != null) {
                try { state.bindBar(); }
                catch (Throwable error) { state.restore(); Log.w(TAG, "Cannot bind notification switch", error); }
            }
            return result;
        });
    }

    static final class LegacySwitch {
        final Object controller, old, preference, screen, dnd;
        final boolean dndVisible;
        boolean updating, enabled, attached, restored;
        LegacySwitch(Object controller, Object old, ClassLoader loader) throws Exception {
            this.controller = controller;
            this.old = old;
            Context context = (Context) Reflect.getObjectField(controller, "mContext");
            screen = call(old, "getParent");
            dnd = find(screen, "pref_do_not_disturb");
            dndVisible = dnd != null && Boolean.TRUE.equals(call(dnd, "isVisible"));
            preference = newPreference(loader.loadClass("com.android.settings.widget.SettingsMainSwitchPreference"), context);
            call(preference, "setKey", "arashi_notification_switch");
            call(preference, "setOrder", -9999);
            Class<?> listener = loader.loadClass("com.android.settingslib.widget.OnMainSwitchChangeListener");
            Object proxy = Proxy.newProxyInstance(loader, new Class<?>[] { listener }, (p, method, args) -> {
                if (method.getName().equals("equals")) return p == args[0];
                if (method.getName().equals("hashCode")) return System.identityHashCode(p);
                if (method.getName().equals("toString")) return "ArashiNotificationSwitch";
                if (!updating && !restored && enabled && method.getName().equals("onSwitchChanged")) {
                    call(controller, "onValueClick", args[0], args[1]);
                }
                return null;
            });
            call(preference, "addOnSwitchChangeListener", proxy);
            update();
            if (!Boolean.TRUE.equals(call(screen, "addPreference", preference))) throw new IllegalStateException("Cannot add notification switch");
            attached = true;
            CONTROLS.put(preference, new WeakReference<>(this));
            call(old, "setVisible", false);
            if (dnd != null) call(dnd, "setVisible", false);
        }

        void restore() {
            restored = true;
            enabled = false;
            CONTROLS.remove(preference);
            try { call(old, "setVisible", true); } catch (Exception ignored) {}
            try { call(screen, "removePreference", preference); } catch (Exception ignored) {}
            if (dnd != null) {
                try { call(dnd, "setVisible", dndVisible); } catch (Exception ignored) {}
            }
            attached = false;
        }

        void bindBar() throws Exception {
            Object bar = Reflect.getObjectField(preference, "mMainSwitchBar");
            if (bar != null) {
                call(bar, "setDisabledByAdmin", Reflect.getObjectField(controller, "mAdmin"));
                call(bar, "setEnabled", enabled);
            }
        }

        void update() throws Exception {
            if (restored) return;
            updating = true;
            try {
                Object row = Reflect.getObjectField(controller, "mAppRow");
                Object channel = Reflect.getObjectField(controller, "mChannel");
                Object group = Reflect.getObjectField(controller, "mChannelGroup");
                boolean allowed = channel != null ? Boolean.TRUE.equals(call(controller, "isChannelBlockable"))
                        : group != null ? Boolean.TRUE.equals(call(controller, "isChannelGroupBlockable"))
                        : Boolean.TRUE.equals(call(controller, "isAppBlockable"));
                boolean checked = row != null && !Boolean.TRUE.equals(Reflect.getObjectField(row, "banned"));
                if (channel != null) checked &= !Integer.valueOf(0).equals(call(channel, "getImportance"));
                else if (group != null) checked &= !Boolean.TRUE.equals(call(group, "isBlocked"));
                call(preference, "setTitle", (CharSequence) call(controller, "getSwitchBarText"));
                // Android 12 lacks the Android 14 switch-bar/admin setters. Disable the
                // entire retained preference when native policy or administrator forbids it.
                enabled = allowed && Reflect.getObjectField(controller, "mAdmin") == null;
                call(preference, "setEnabled", enabled);
                call(preference, "setChecked", checked);
                call(preference, "show");
                bindBar();
                if (attached) {
                    call(old, "setVisible", false);
                    if (dnd != null) call(dnd, "setVisible", false);
                }
            } finally { updating = false; }
        }
    }

    private static Object newPreference(Class<?> type, Context context) throws Exception {
        try { return type.getConstructor(Context.class).newInstance(context); }
        catch (NoSuchMethodException ignored) {
            Constructor<?> c = type.getConstructor(Context.class, android.util.AttributeSet.class);
            return c.newInstance(context, null);
        }
    }

    static Object call(Object target, String name, Object... args) throws Exception {
        return AppDetailsHooks.invoke(target, name, args);
    }
    private static Object find(Object screen, String key) throws Exception { return call(screen, "findPreference", (CharSequence) key); }
    private static void setField(Object target, String name, Object value) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(target, value); return; }
            catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }
    static int resource(Context context, ClassLoader loader, String type, String name) throws Exception {
        Object value = Reflect.getStaticObjectField(loader.loadClass("com.android.settings.R$" + type), name);
        int id = value instanceof Integer ? (Integer) value : 0;
        if (id == 0) id = context.getResources().getIdentifier(name, type, "com.android.settings");
        if (id == 0) throw new NoSuchFieldException(type + "/" + name);
        return id;
    }
}
