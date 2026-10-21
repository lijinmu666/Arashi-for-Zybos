package com.lijinmu.arashi.xposed.hooks;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.lijinmu.arashi.xposed.ConfigManager;
import com.lijinmu.arashi.xposed.HookConfig;
import com.lijinmu.arashi.xposed.Reflect;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;

import io.github.libxposed.api.XposedModule;

/** Restores the retained SettingsLib action bar without replacing the typed vendor controller. */
public final class AppDetailsHooks {
    private static final String TAG = "[Arashi-AppDetails]";
    private static final Map<Object, WeakReference<Bridge>> HEADERS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static final Set<String> SUPPORTED = new HashSet<>();

    private AppDetailsHooks() {}

    public static void hookSettings(XposedModule module, ClassLoader loader) {
        try {
            Class<?> fragment = loader.loadClass(
                    "com.android.settings.applications.appinfo.AppInfoDashboardFragment");
            Method create = Reflect.findMethodExact(fragment, "onCreatePreferences", Bundle.class, String.class);
            if (create == null) return;
            module.hook(create).intercept(chain -> {
                Object result = chain.proceed();
                if (ConfigManager.isEnabled(HookConfig.KEY_AOSP_APP_DETAILS)) {
                    try { install(chain.getThisObject(), loader); }
                    catch (Throwable error) { Log.w(TAG, "Keeping original app details", error); }
                }
                return result;
            });
            // The two samples use different classes, field names and state setters.
            hookHeader(module, loader, "com.android.settings.widget.AppStoreDetailHeaderPreference");
            hookHeader(module, loader, "com.android.settings.zyb.widget.ZybAppStoreDetailHeaderPreference");
            deoptimizeStateCallers(module, loader);
        } catch (Throwable error) {
            Log.w(TAG, "App details hook unavailable", error);
        }
    }

    private static void deoptimizeStateCallers(XposedModule module, ClassLoader loader) {
        // Tiny vendor setters may already be inlined into these callers by dex2oat.
        String prefix = "com.android.settings.applications.appinfo.";
        for (String name : new String[] { "AppButtonsPreferenceController",
                "ZybAppButtonsPreferenceController", "AppHeaderViewPreferenceController" }) {
            try {
                for (Method method : loader.loadClass(prefix + name).getDeclaredMethods()) {
                    String n = method.getName();
                    if (n.equals("refreshUi") || n.equals("updateUninstallButton")
                            || n.equals("updateForceStopButtonInner") || n.equals("handleDisableable")
                            || n.equals("setAppLabelAndIcon")) {
                        method.setAccessible(true);
                        module.deoptimize(method);
                    }
                }
            } catch (ClassNotFoundException ignored) {
            } catch (Throwable error) {
                Log.w(TAG, "Cannot deoptimize app state callers: " + name, error);
            }
        }
    }

    private static void hookHeader(XposedModule module, ClassLoader loader, String name) {
        try {
            Class<?> type = loader.loadClass(name);
            int hooked = 0;
            for (Method method : type.getDeclaredMethods()) {
                String n = method.getName();
                if (!n.equals("refreshHeader") && !n.equals("setForceStopButton")
                        && !n.equals("setInUnstallButton") && !n.equals("setForceStopButtonStatus")
                        && !n.equals("setUninstallButtonStatus")) continue;
                method.setAccessible(true);
                module.hook(method).intercept(chain -> {
                    Object result = chain.proceed();
                    WeakReference<Bridge> ref = HEADERS.get(chain.getThisObject());
                    Bridge bridge = ref == null ? null : ref.get();
                    if (bridge != null) bridge.refreshOrRestore();
                    return result;
                });
                hooked++;
            }
            if (hooked >= 3) SUPPORTED.add(name);
        } catch (ClassNotFoundException ignored) {
            // Only the class corresponding to this firmware is expected to exist.
        } catch (Throwable error) {
            Log.w(TAG, "Header state hook unavailable: " + name, error);
        }
    }

    private static void install(Object fragment, ClassLoader loader) throws Exception {
        Object screen = invoke(fragment, "getPreferenceScreen");
        if (screen == null || find(screen, "arashi_app_actions") != null) return;
        Object header = find(screen, "app_store_detail_header");
        Object controller = Reflect.getObjectField(fragment, "mAppButtonsPreferenceController");
        // Do not change ordinary AOSP screens or an unrecognized firmware.
        if (header == null || !SUPPORTED.contains(header.getClass().getName()) || controller == null
                || Reflect.getObjectField(controller, "mButtonsPref") != header) return;
        Context context = (Context) invoke(fragment, "getContext");
        if (context == null) return;
        Bridge bridge = new Bridge(context, screen, header, controller, loader);
        try {
            bridge.prepare(); // Resolve every required component before touching the screen.
            if (!Boolean.TRUE.equals(invoke(screen, "addPreference", bridge.titlePreference))
                    || !Boolean.TRUE.equals(invoke(screen, "addPreference", bridge.actions))) {
                throw new IllegalStateException("Cannot add app detail preferences");
            }
            HEADERS.put(header, new WeakReference<>(bridge));
            invoke(header, "setVisible", false);
            Log.i(TAG, "SettingsLib app actions installed: " + controller.getClass().getName());
        } catch (Throwable error) {
            bridge.restore();
            if (error instanceof Exception) throw (Exception) error;
            throw new IllegalStateException("App details component unavailable", error);
        }
    }

    private static Object find(Object screen, String key) throws Exception {
        return invoke(screen, "findPreference", (CharSequence) key);
    }

    static final class Bridge {
        final Context context;
        final Object screen, header, controller;
        final ClassLoader loader;
        Object titlePreference, actions;
        ImageView icon;
        TextView title, version;
        boolean restored;

        Bridge(Context context, Object screen, Object header, Object controller, ClassLoader loader) {
            this.context = context;
            this.screen = screen;
            this.header = header;
            this.controller = controller;
            this.loader = loader;
        }

        void prepare() throws Exception {
            if (!(Reflect.getObjectField(header, "uninstallListener") instanceof View.OnClickListener)
                    || !(Reflect.getObjectField(header, "forceStopListener") instanceof View.OnClickListener)) {
                throw new IllegalStateException("Native app action listeners unavailable");
            }
            for (String[] names : new String[][] { { "bEnableInUnstall", "enableUninstall" },
                    { "bEnableForceStop", "enableForceStop" } }) {
                if (!(Reflect.getObjectField(header, names[0]) instanceof Boolean)
                        && !(Reflect.getObjectField(header, names[1]) instanceof Boolean)) {
                    throw new IllegalStateException("Native button state unavailable");
                }
            }
            int order = (Integer) invoke(header, "getOrder");
            actions = loader.loadClass("com.android.settingslib.widget.ActionButtonsPreference")
                    .getConstructor(Context.class).newInstance(context);
            invoke(actions, "setKey", "arashi_app_actions");
            invoke(actions, "setOrder", order + 1);
            invoke(actions, "setButton1Visible", false);
            invoke(actions, "setButton2Visible", true);
            invoke(actions, "setButton3Visible", true);
            invoke(actions, "setButton4Visible", false);
            invoke(actions, "setButton2Icon", resource("drawable", "ic_settings_delete"));
            invoke(actions, "setButton3Icon", resource("drawable", "ic_settings_force_stop"));
            invoke(actions, "setButton3Text", resource("string", "force_stop"));
            invoke(actions, "setButton2OnClickListener", (View.OnClickListener) view -> click("uninstallListener", view));
            invoke(actions, "setButton3OnClickListener", (View.OnClickListener) view -> click("forceStopListener", view));
            LinearLayout content = new LinearLayout(context);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setGravity(Gravity.CENTER_HORIZONTAL);
            int padding = Math.round(24 * context.getResources().getDisplayMetrics().density);
            content.setPadding(padding, padding, padding, padding);
            icon = new ImageView(context);
            int size = padding * 2;
            content.addView(icon, new LinearLayout.LayoutParams(size, size));
            title = new TextView(context);
            title.setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Large);
            title.setGravity(Gravity.CENTER);
            title.setPadding(0, padding / 2, 0, 0);
            content.addView(title);
            version = new TextView(context);
            version.setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Small);
            version.setGravity(Gravity.CENTER);
            content.addView(version);
            titlePreference = loader.loadClass("com.android.settingslib.widget.LayoutPreference")
                    .getConstructor(Context.class, View.class).newInstance(context, content);
            invoke(titlePreference, "setKey", "arashi_app_header");
            invoke(titlePreference, "setOrder", order);
            invoke(titlePreference, "setSelectable", false);
            refresh();
        }

        void refresh() throws Exception {
            Object entry = Reflect.getObjectField(controller, "mAppEntry");
            ApplicationInfo app = (ApplicationInfo) Reflect.getObjectField(entry, "info");
            PackageInfo pkg = (PackageInfo) Reflect.getObjectField(controller, "mPackageInfo");
            if (app == null || pkg == null) throw new IllegalStateException("No app information");
            Object label = Reflect.getObjectField(entry, "label");
            title.setText(label instanceof CharSequence ? (CharSequence) label : app.loadLabel(context.getPackageManager()));
            icon.setImageDrawable(app.loadIcon(context.getPackageManager()));
            version.setText(pkg.versionName);
            String text = (app.flags & ApplicationInfo.FLAG_SYSTEM) == 0 ? "uninstall_text"
                    : app.enabled && !Boolean.TRUE.equals(invoke(controller, "isDisabledUntilUsed")) ? "disable_text" : "enable_text";
            invoke(actions, "setButton2Text", resource("string", text));
            String actionIcon = text.equals("uninstall_text") ? "ic_settings_delete"
                    : text.equals("disable_text") ? "ic_settings_disable" : "ic_settings_enable";
            invoke(actions, "setButton2Icon", resource("drawable", actionIcon));
            invoke(actions, "setButton2Enabled", state("bEnableInUnstall", "enableUninstall"));
            invoke(actions, "setButton3Enabled", state("bEnableForceStop", "enableForceStop"));
        }

        boolean state(String legacy, String modern) {
            Object value = Reflect.getObjectField(header, legacy);
            if (!(value instanceof Boolean)) value = Reflect.getObjectField(header, modern);
            return Boolean.TRUE.equals(value);
        }

        void click(String field, View view) {
            // The original controller owns user IDs, confirmation dialogs and package operations.
            if (restored || !(field.equals("uninstallListener")
                    ? state("bEnableInUnstall", "enableUninstall")
                    : state("bEnableForceStop", "enableForceStop"))) return;
            Object listener = Reflect.getObjectField(header, field);
            if (listener instanceof View.OnClickListener) ((View.OnClickListener) listener).onClick(view);
        }

        void refreshOrRestore() {
            if (restored) return;
            try { refresh(); }
            catch (Throwable error) {
                restore();
                Log.w(TAG, "Restored original app details after state update failure", error);
            }
        }

        void restore() {
            restored = true;
            HEADERS.remove(header);
            try { invoke(header, "setVisible", true); } catch (Exception ignored) {}
            if (titlePreference != null) {
                try { invoke(screen, "removePreference", titlePreference); } catch (Exception ignored) {}
            }
            if (actions != null) {
                try { invoke(screen, "removePreference", actions); } catch (Exception ignored) {}
            }
        }

        int resource(String type, String name) throws Exception {
            // Android 14 retains R fields; Android 12 drops some fields but keeps ARSC names.
            int value = 0;
            try {
                Object id = Reflect.getStaticObjectField(loader.loadClass("com.android.settings.R$" + type), name);
                if (id instanceof Integer) value = (Integer) id;
            } catch (ClassNotFoundException ignored) {}
            if (value == 0) value = context.getResources().getIdentifier(name, type, "com.android.settings");
            if (value == 0) throw new NoSuchFieldException(type + "/" + name);
            return value;
        }
    }

    private static Object invoke(Object target, String name, Object... args) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
                Class<?>[] params = method.getParameterTypes();
                boolean matches = true;
                for (int i = 0; i < args.length; i++) {
                    Class<?> p = params[i];
                    if (p == int.class) p = Integer.class;
                    if (p == boolean.class) p = Boolean.class;
                    if (args[i] != null && !p.isInstance(args[i])) { matches = false; break; }
                }
                if (!matches) continue;
                method.setAccessible(true);
                return method.invoke(target, args);
            }
        }
        throw new NoSuchMethodException(target.getClass().getName() + "." + name);
    }
}
