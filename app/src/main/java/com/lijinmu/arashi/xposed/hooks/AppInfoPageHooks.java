package com.lijinmu.arashi.xposed.hooks;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.util.Log;
import com.lijinmu.arashi.xposed.ConfigManager;
import com.lijinmu.arashi.xposed.HookConfig;
import com.lijinmu.arashi.xposed.Reflect;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Restore the complete app-info preference tree and its native controller lifecycle.
 * The vendor Activity and its typed action controller must remain together on both samples.
 */
public final class AppInfoPageHooks {
    private static final String TAG = "[Arashi-AppInfoPage]";
    private static final String PREFIX = "com.android.settings.applications.appinfo.";
    private static final Map<Object, Plan> PAGES = Collections.synchronizedMap(new WeakHashMap<>());
    private static final ThreadLocal<Plan> INFLATING = new ThreadLocal<>();
    private AppInfoPageHooks() {}

    public static void hookSettings(XposedModule module, ClassLoader loader) {
        try {
            Class<?> page = loader.loadClass(PREFIX + "AppInfoDashboardFragment");
            Class<?> screen = loader.loadClass("androidx.preference.PreferenceScreen");
            Method create = required(page, "createPreferenceControllers", Context.class);
            Method xml = required(page, "getPreferenceScreenResId");
            Method preferences = required(page, "onCreatePreferences", Bundle.class, String.class);
            Method metadata = required(loader.loadClass("com.android.settings.core.PreferenceControllerListHelper"),
                    "getPreferenceControllersFromXml", Context.class, int.class);
            Method inflate = required(loader.loadClass("androidx.preference.PreferenceManager"),
                    "inflateFromResource", Context.class, int.class, screen);
            // Android 12's retained XML also contains a vendor-only title controller.
            // Its parent is never initialized by the stock page; exclude it before binding,
            // rather than merely hiding a preference whose controller could still crash.
            module.hook(metadata).intercept(chain -> {
                Object original = chain.proceed();
                if (!ConfigManager.isEnabled(HookConfig.KEY_AOSP_APP_DETAILS) || !(original instanceof List)
                        || ((Integer) chain.getArg(1)) != resource((Context) chain.getArg(0), loader, "xml", "app_info_settings")) {
                    return original;
                }
                List<Object> controllers = new ArrayList<>((List<?>) original);
                controllers.removeIf(c -> c.getClass().getName().equals(PREFIX + "AppDetailTitlePreferenceController"));
                return controllers;
            });
            // Install every prerequisite before selecting the retained XML.
            module.hook(inflate).intercept(chain -> {
                Object result = chain.proceed();
                Plan plan = INFLATING.get();
                if (plan != null && ((Integer) chain.getArg(1)) == plan.xml) {
                    plan.completeScreen(result);
                }
                return result;
            });
            module.hook(preferences).intercept(chain -> {
                Plan previous = INFLATING.get();
                Plan plan = PAGES.get(chain.getThisObject());
                if (plan == null) INFLATING.remove(); else INFLATING.set(plan);
                try { return chain.proceed(); }
                finally {
                    if (previous == null) INFLATING.remove(); else INFLATING.set(previous);
                }
            });
            module.hook(xml).intercept(chain -> {
                Plan plan = PAGES.get(chain.getThisObject());
                return plan == null ? chain.proceed() : plan.xml;
            });
            Method destroy = Reflect.findMethodExact(page, "onDestroy");
            if (destroy != null) module.hook(destroy).intercept(chain -> {
                try { return chain.proceed(); }
                finally { PAGES.remove(chain.getThisObject()); }
            });
            module.hook(create).intercept(chain -> {
                Object original = chain.proceed();
                if (!ConfigManager.isEnabled(HookConfig.KEY_AOSP_APP_DETAILS)
                        || !chain.getThisObject().getClass().getName().equals(PREFIX + "AppInfoDashboardFragment")
                        || !(original instanceof List)) return original;
                Plan plan = null;
                try {
                    plan = new Plan(chain.getThisObject(), (Context) chain.getArg(0), loader);
                    List<Object> controllers = plan.controllers((List<?>) original);
                    PAGES.put(chain.getThisObject(), plan);
                    return controllers;
                } catch (Throwable error) {
                    if (plan != null) plan.rollback();
                    Log.w(TAG, "Cannot restore full app-info page; keeping firmware page", error);
                    return original;
                }
            });
            for (Method method : new Method[] {create, xml, preferences, inflate, metadata}) module.deoptimize(method);
            // Firmware builds can inline getPreferenceScreenResId/createPreferenceControllers.
            for (Method method : loader.loadClass("com.android.settings.dashboard.DashboardFragment").getDeclaredMethods()) {
                if (Arrays.asList("onAttach", "displayResourceTiles", "refreshAllPreferences").contains(method.getName())) {
                    module.deoptimize(method);
                }
            }
        } catch (Throwable error) { Log.w(TAG, "Full app-info restoration unavailable", error); }
    }

    // Original AOSP row keys and controllers. Empty parent denotes a top-level row.
    // Android 12 no longer has these rows in its retained app_info_settings XML.
    private static final String[][] ROWS = {
        {"app_settings_link", "app_settings_link", "AppSettingPreferenceController", ""},
        {"all_services_settings", "app_info_all_services_label", "AppAllServicesPreferenceController", ""},
        {"notification_settings", "notifications_label", "AppNotificationPreferenceController", ""},
        {"permission_settings", "permissions_label", "AppPermissionPreferenceController", ""},
        {"storage_settings", "storage_settings_for_app", "AppStoragePreferenceController", ""},
        {"instant_app_launch_supported_domain_urls", "app_launch_supported_domain_urls_title", "InstantAppDomainsPreferenceController", ""},
        {"data_settings", "data_usage_app_summary_title", "AppDataUsagePreferenceController", ""},
        {"extra_app_info_settings", "extra_app_info_label", "ExtraAppInfoPreferenceController", ""},
        {"time_spent_in_app", "time_spent_in_app_pref_title", "TimeSpentInAppPreferenceController", ""},
        {"battery", "power_usage_summary_title", "", ""},
        {"app_language_setting", "app_locale_preference_title", "AppLocalePreferenceController", ""},
        {"preferred_settings", "launch_by_default", "AppOpenByDefaultPreferenceController", ""},
        {"memory", "memory_settings_title", "", ""},
        {"default_home", "home_app", "", ""},
        {"default_browser", "default_browser_title", "", ""},
        {"default_phone_app", "default_phone_title", "", ""},
        {"default_emergency_app", "default_emergency_app", "", ""},
        {"default_sms_app", "sms_application_title", "", ""},
        {"app_hibernation_info", "unused_apps", "AppHibernationPreferenceCategoryController", ""},
        {"hibernation_switch", "unused_apps_switch", "HibernationSwitchPreferenceController", "app_hibernation_info"},
        {"advanced_app_info", "advanced_apps", "AdvancedAppInfoPreferenceCategoryController", ""},
        {"system_alert_window", "draw_overlay", "DrawOverlayDetailPreferenceController", "advanced_app_info"},
        {"write_settings_apps", "write_settings", "WriteSystemSettingsPreferenceController", "advanced_app_info"},
        {"picture_in_picture", "picture_in_picture_app_detail_title", "com.android.settings.applications.specialaccess.pictureinpicture.PictureInPictureDetailPreferenceController", "advanced_app_info"},
        {"install_other_apps", "install_other_apps", "ExternalSourceDetailPreferenceController", "advanced_app_info"},
        {"interact_across_profiles", "interact_across_profiles_title", "com.android.settings.applications.specialaccess.interactacrossprofiles.InteractAcrossProfilesDetailsPreferenceController", "advanced_app_info"},
        {"alarms_and_reminders", "alarms_and_reminders_title", "AlarmsAndRemindersDetailPreferenceController", "advanced_app_info"},
        {"long_background_tasks", "long_background_tasks_title", "LongBackgroundTasksDetailsPreferenceController", "advanced_app_info"},
        {"app_installer", "app_install_details_group_title", "AppInstallerPreferenceCategoryController", ""},
        {"app_info_store", "app_install_details_title", "AppInstallerInfoPreferenceController", "app_installer"},
        {"app_version", "", "AppVersionPreferenceController", ""},
    };

    static final class Plan {
        final Context context;
        final Object fragment;
        final ClassLoader loader;
        final int xml;
        final boolean modern;
        final List<Object> created = new ArrayList<>();
        final List<Object> previousCallbacks = new ArrayList<>();
        Object lifecycle;
        final Map<String, Object> controls = new LinkedHashMap<>();
        final Map<String, Object> additions = new LinkedHashMap<>();
        Object backing;

        Plan(Object fragment, Context context, ClassLoader loader) throws Exception {
            this.fragment = fragment;
            this.context = context;
            this.loader = loader;
            modern = loader.loadClass(PREFIX + "AppInfoDashboardFragment").getDeclaredField(
                    "mAppButtonsPreferenceController").getType().getName().contains("ZybAppButtons");
            Object callbacks = Reflect.getObjectField(fragment, "mCallbacks");
            if (callbacks instanceof List) previousCallbacks.addAll((List<?>) callbacks);
            xml = resource(context, loader, "xml", "app_info_settings");
            if (xml == 0) throw new NoSuchFieldException("app_info_settings");
        }

        List<Object> controllers(List<?> original) throws Exception {
            PackageInfo info = (PackageInfo) Reflect.getObjectField(fragment, "mPackageInfo");
            if (info == null || info.applicationInfo == null) throw new IllegalStateException("No target package");
            lifecycle = call(fragment, "getSettingsLifecycle");
            String pkg = info.packageName;
            List<Object> result = new ArrayList<>(original);
            for (String[] row : ROWS) {
                if (row[2].isEmpty()) continue;
                String name = row[2].contains(".") ? row[2] : PREFIX + row[2];
                Class<?> type;
                try { type = loader.loadClass(name); }
                catch (ClassNotFoundException absent) {
                    // Version-specific optional rows are also absent from that version's XML.
                    continue;
                }
                Object controller = type.getConstructor(Context.class, String.class).newInstance(context, row[0]);
                created.add(controller);
                optional(controller, "setPackageName", pkg);
                optional(controller, "setPackage", pkg);
                // The firmware onAttach initializes these four itself; registering their
                // callback twice would duplicate refreshes and loader requests.
                if (!Arrays.asList("notification_settings", "permission_settings", "storage_settings", "preferred_settings").contains(row[0])
                        && (modern || !row[0].equals("app_version"))) {
                    optional(controller, "setParentFragment", fragment);
                }
                optional(controller, "initLifeCycleOwner", fragment);
                controls.put(row[0], controller);
                result.add(controller);
            }
            configureCategory("app_hibernation_info", "hibernation_switch");
            configureCategory("app_installer", "app_info_store");
            configureCategory("advanced_app_info", "system_alert_window", "write_settings_apps", "picture_in_picture",
                    "install_other_apps", "interact_across_profiles", "alarms_and_reminders", "long_background_tasks");
            Object advanced = controls.get("advanced_app_info");
            if (advanced != null) optional(advanced, "setAppEntry", call(fragment, "getAppEntry"));
            Object battery = construct(PREFIX + "AppBatteryPreferenceController", context, fragment, pkg,
                    info.applicationInfo.uid, lifecycle);
            created.add(battery);
            controls.put("battery", battery);
            result.add(battery);
            Object memory = construct(PREFIX + "AppMemoryPreferenceController", context, fragment, lifecycle);
            created.add(memory);
            controls.put("memory", memory);
            result.add(memory);
            String[] defaults = {"Home", "Browser", "Phone", "Emergency", "Sms"};
            String[] keys = {"default_home", "default_browser", "default_phone_app", "default_emergency_app", "default_sms_app"};
            for (int i = 0; i < defaults.length; i++) {
                Object controller = construct(PREFIX + "Default" + defaults[i] + "ShortcutPreferenceController", context, pkg);
                created.add(controller);
                controls.put(keys[i], controller);
                result.add(controller);
            }
            Object instant = construct(PREFIX + "InstantAppButtonsPreferenceController", context, fragment, pkg, lifecycle);
            created.add(instant);
            result.add(instant);
            // The two typed firmware action/header controllers still require this hidden backing.
            String header = modern
                    ? "com.android.settings.zyb.widget.ZybAppStoreDetailHeaderPreference"
                    : "com.android.settings.widget.AppStoreDetailHeaderPreference";
            backing = construct(header, context);
            call(backing, "setKey", "app_store_detail_header");
            call(backing, "setVisible", false);
            call(backing, "setOrder", -10500);
            // Build missing rows before committing the plan. No displayed screen has been touched.
            for (String[] row : ROWS) {
                if (!controls.containsKey(row[0])) continue;
                String type = isCategory(row[0]) ? "androidx.preference.PreferenceCategory"
                        : row[0].equals("hibernation_switch") ? "androidx.preference.SwitchPreference"
                        : row[0].equals("instant_app_launch_supported_domain_urls") ? "com.android.settings.applications.AppDomainsPreference"
                        : row[0].equals("permission_settings") ? "com.android.settings.widget.FixedLineSummaryPreference"
                        : "androidx.preference.Preference";
                Object preference = construct(type, context);
                call(preference, "setKey", row[0]);
                if (!row[1].isEmpty()) {
                    int title = resource(context, loader, "string", row[1]);
                    if (title != 0) call(preference, "setTitle", title);
                }
                if (row[0].equals("app_version")) {
                    call(preference, "setSelectable", false);
                    optional(preference, "setCopyingEnabled", true);
                }
                additions.put(row[0], preference);
            }
            // These native rows must never become static labels without a controller.
            for (String key : new String[] {"notification_settings", "permission_settings", "storage_settings",
                    "data_settings", "preferred_settings", "battery"}) {
                if (!controls.containsKey(key)) throw new IllegalStateException("Missing native controller: " + key);
            }
            return result;
        }

        @SuppressWarnings("unchecked")
        void rollback() {
            Object callbacks = Reflect.getObjectField(fragment, "mCallbacks");
            if (callbacks instanceof List) {
                List<Object> list = (List<Object>) callbacks;
                list.clear();
                list.addAll(previousCallbacks);
            }
            if (lifecycle != null) for (Object controller : created) {
                try { optional(lifecycle, "removeObserver", controller); }
                catch (Exception error) { Log.w(TAG, "Cannot detach incomplete app-info controller", error); }
            }
        }

        void configureCategory(String key, String... children) throws Exception {
            Object category = controls.get(key);
            if (category == null) return;
            List<Object> controllers = new ArrayList<>();
            for (String child : children) if (controls.containsKey(child)) controllers.add(controls.get(child));
            call(category, "setChildren", controllers);
        }

        void completeScreen(Object screen) throws Exception {
            if (screen == null) throw new IllegalStateException("Missing preference screen");
            if (call(screen, "findPreference", (CharSequence) "app_store_detail_header") == null) {
                call(screen, "addPreference", backing);
            }
            Object vendorTitle = call(screen, "findPreference", (CharSequence) "app_store_title");
            if (vendorTitle != null) call(vendorTitle, "setVisible", false);
            int order = 0;
            for (String[] row : ROWS) {
                Object preference = call(screen, "findPreference", (CharSequence) row[0]);
                if (preference == null && additions.containsKey(row[0])) {
                    Object parent = row[3].isEmpty() ? screen : call(screen, "findPreference", (CharSequence) row[3]);
                    if (parent == null) continue;
                    preference = additions.get(row[0]);
                    call(parent, "addPreference", preference);
                }
                if (preference != null) {
                    call(preference, "setOrder", row[0].equals("app_version") ? 9999 : order++);
                }
            }
            Log.i(TAG, "Full AOSP app-info preferences and native controllers restored");
        }

        Object construct(String name, Object... args) throws Exception {
            for (Constructor<?> constructor : loader.loadClass(name).getConstructors()) {
                if (matches(constructor.getParameterTypes(), args)) return constructor.newInstance(args);
            }
            // Several Settings widgets only retain their XML (Context, AttributeSet)
            // constructor. A null AttributeSet gives their normal native defaults.
            if (args.length == 1 && args[0] instanceof Context) {
                for (Constructor<?> constructor : loader.loadClass(name).getConstructors()) {
                    Class<?>[] types = constructor.getParameterTypes();
                    if (types.length == 2 && types[0] == Context.class
                            && types[1].getName().equals("android.util.AttributeSet")) {
                        return constructor.newInstance(args[0], null);
                    }
                }
            }
            throw new NoSuchMethodException(name + " constructor");
        }
    }

    private static boolean isCategory(String key) {
        return key.equals("app_hibernation_info") || key.equals("advanced_app_info") || key.equals("app_installer");
    }

    static int resource(Context context, ClassLoader loader, String type, String name) {
        int id = context.getResources().getIdentifier(name, type, "com.android.settings");
        if (id != 0) return id;
        try {
            Object value = Reflect.getStaticObjectField(loader.loadClass("com.android.settings.R$" + type), name);
            return value instanceof Integer ? (Integer) value : 0;
        } catch (ClassNotFoundException absent) { return 0; }
    }

    private static Method required(Class<?> type, String name, Class<?>... params) throws Exception {
        Method method = Reflect.findMethodExact(type, name, params);
        if (method == null) throw new NoSuchMethodException(type.getName() + "." + name);
        return method;
    }

    private static boolean matches(Class<?>[] types, Object[] args) {
        if (types.length != args.length) return false;
        for (int i = 0; i < types.length; i++) {
            Class<?> type = types[i];
            if (type == int.class) type = Integer.class;
            if (type == boolean.class) type = Boolean.class;
            if (args[i] != null && !type.isInstance(args[i])) return false;
        }
        return true;
    }

    private static void optional(Object target, String name, Object... args) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (method.getName().equals(name) && matches(method.getParameterTypes(), args)) {
                    method.setAccessible(true);
                    method.invoke(target, args);
                    return;
                }
            }
        }
    }

    private static Object call(Object target, String name, Object... args) throws Exception {
        return AppDetailsHooks.invoke(target, name, args);
    }
}
