package com.arashi.xposed.hooks;

import android.content.Context;
import android.content.ContentResolver;
import android.util.Log;
import android.os.Bundle;

import io.github.libxposed.api.XposedModule;

import com.arashi.xposed.ConfigManager;
import com.arashi.xposed.HookConfig;
import com.arashi.xposed.Reflect;
import com.arashi.xposed.SettingsHeaderCompat;

import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public final class DevOptionsHooks {

    private static final String TAG = "[Arashi-DevOpt]";

    private static final String PROP_ALLOW = "sys.allow.development";
    private static final String KEY_SPECIAL_ADB = "special_enable_adb_debug";

    private DevOptionsHooks() {
    }

    public static void hookSettings(XposedModule mod, ClassLoader cl) {


        hookSysPropGet(mod, cl);

        hookSysPropSet(mod, cl);

        hookEnabler(mod, cl);

        hookSpecialAdbDebug(mod, cl);

        hookSettingsSideMenu(mod, cl);

        hookDeveloperPanelHostCompatibility(mod, cl);

        hookDeveloperSubPageNavigation(mod, cl);

        hookDeveloperPreferenceNavigation(mod, cl);

        hookDeveloperPanelPause(mod, cl);

        hookWirelessDebuggingHostCompatibility(mod, cl);

        hookDeveloperAppPickerTheme(mod, cl);

        hookGraphicsDriverHostCompatibility(mod, cl);

        hookMemoryDetailNavigation(mod, cl);

        Log.i(TAG, "settings hooks initialized");
    }

    private static final String DEV_FRAGMENT =
            "com.android.settings.development.DevelopmentSettingsDashboardFragment";

    private static boolean isZybSettingsHost(Object host) {
        if (host == null) return false;
        for (Class<?> type = host.getClass(); type != null; type = type.getSuperclass()) {
            String name = type.getName();
            if ("com.android.settings.ZybSettings".equals(name)
                    || "com.android.settings.zyb.ZybSettings".equals(name)) return true;
        }
        return false;
    }

    private static Object getHostSwitchBar(Object host) throws Exception {
        Method getter = Reflect.findMethodExact(host.getClass(), "getSwitchBar");
        if (getter != null) {
            Object switchBar = getter.invoke(host);
            if (switchBar != null) return switchBar;
        }
        // Android 12's ZybSettings has the same widget, but exposes no getter.
        Object switchBar = Reflect.getObjectField(host, "mMainSwitch");
        if (switchBar != null) return switchBar;
        if (host instanceof android.app.Activity) {
            android.app.Activity activity = (android.app.Activity) host;
            int id = activity.getResources().getIdentifier(
                    "switch_bar", "id", "com.android.settings");
            if (id != 0) return activity.findViewById(id);
        }
        return null;
    }

    private static void hookSettingsSideMenu(XposedModule mod, ClassLoader cl) {
        try {
            Class<?> headListClass = Class.forName("com.android.settings.zyb.ui.ZybHeadList", false, cl);
            Method method = Reflect.findMethodExact(headListClass, "getZybSettingsHeaders", Context.class);
            if (method == null) {
                Log.w(TAG, "getZybSettingsHeaders not found");
                return;
            }
            // OTA 7.10 checks a fixed built-in list when routing an existing Activity's intent.
            // Treat the injected dashboard as a top-level page, not a nested destination.
            Method isTopFragment = Reflect.findMethodExact(headListClass,
                    "isZybSettingsTopFragment", String.class);
            if (isTopFragment != null) {
                mod.hook(isTopFragment).intercept(chain -> {
                    if (DEV_FRAGMENT.equals(chain.getArg(0))
                            && ConfigManager.isEnabled(HookConfig.KEY_DEV_OPTIONS)
                            && ConfigManager.isEnabled(HookConfig.KEY_DEV_OPTIONS_MENU)) {
                        return true;
                    }
                    return chain.proceed();
                });
            }
            mod.hook(method).intercept(chain -> {
                Object original = chain.proceed();
                if (!(original instanceof List)
                        || !ConfigManager.isEnabled(HookConfig.KEY_DEV_OPTIONS)
                        || !ConfigManager.isEnabled(HookConfig.KEY_DEV_OPTIONS_MENU)) {
                    return original;
                }
                try {
                    List<?> headers = (List<?>) original;
                    for (Object header : headers) {
                        Object fragment = Reflect.callMethod(header, "getFragmentName");
                        if (DEV_FRAGMENT.equals(fragment)) return original;
                    }

                    Context context = (Context) chain.getArg(0);
                    int title = context.getResources().getIdentifier(
                            "development_settings_title", "string", "com.android.settings");
                    int icon = context.getResources().getIdentifier(
                            "ic_settings_development", "drawable", "com.android.settings");
                    if (title == 0 || icon == 0) {
                        Log.w(TAG, "developer menu title or icon resource missing");
                        return original;
                    }

                    Class<?> headerClass = Class.forName("com.android.settings.zyb.ui.ZybSettingsHeader", false, cl);
                    Object injected = SettingsHeaderCompat.create(
                            headerClass, headers, title, icon, DEV_FRAGMENT);
                    List<Object> result = new ArrayList<>(headers);
                    result.add(injected);
                    Log.i(TAG, "developer options side menu entry injected");
                    return result;
                } catch (Exception error) {
                    Log.w(TAG, "developer menu injection failed; keeping Settings headers", error);
                    return original;
                }
            });
            Log.i(TAG, "settings side menu hook registered");
        } catch (Throwable error) {
            Log.w(TAG, "settings side menu hook failed: " + error);
        }
    }

    /** ZybSettings is a two-pane host, while the stock dashboard assumes SettingsActivity. */
    private static void hookDeveloperPanelHostCompatibility(XposedModule mod, ClassLoader cl) {
        try {
            Class<?> fragmentClass = Class.forName(DEV_FRAGMENT, false, cl);
            Method method = Reflect.findMethodExact(fragmentClass, "onActivityCreated", Bundle.class);
            if (method == null) {
                Log.w(TAG, "developer dashboard onActivityCreated not found");
                return;
            }
            mod.hook(method).intercept(chain -> {
                try {
                    return chain.proceed();
                } catch (ClassCastException error) {
                    Object fragment = chain.getThisObject();
                    Object activity = Reflect.callMethod(fragment, "getActivity");
                    if (!isZybSettingsHost(activity)) {
                        throw error;
                    }
                    try {
                        Object switchBar = getHostSwitchBar(activity);
                        if (switchBar == null) throw error;
                        setField(fragment, "mSwitchBar", switchBar);
                        switchBar.getClass().getMethod("setTitle", CharSequence.class).invoke(
                                switchBar, ((Context) activity).getString(
                                        ((Context) activity).getResources().getIdentifier(
                                                "developer_options_main_switch_title", "string",
                                                "com.android.settings")));
                        switchBar.getClass().getMethod("show").invoke(switchBar);

                        Class<?> switchController = Class.forName(
                                "com.android.settings.development.DevelopmentSwitchBarController", false, cl);
                        Class<?> switchBarClass = Class.forName(
                                "com.android.settings.widget.SettingsMainSwitchBar", false, cl);
                        Class<?> lifecycleClass = Class.forName(
                                "com.android.settingslib.core.lifecycle.Lifecycle", false, cl);
                        Object lifecycle = Reflect.callMethod(fragment, "getSettingsLifecycle");
                        Constructor<?> constructor = switchController.getConstructor(
                                fragmentClass, switchBarClass, boolean.class, lifecycleClass);
                        Object controller = constructor.newInstance(fragment, switchBar, true, lifecycle);
                        setField(fragment, "mSwitchBarController", controller);
                        if (ConfigManager.isEnabled(HookConfig.KEY_DEV_OPTIONS)) {
                            Reflect.callMethod(fragment, "enableDeveloperOptions");
                        }
                        Log.i(TAG, "developer dashboard attached to ZybSettings side panel");
                        return null;
                    } catch (Throwable recoveryError) {
                        Log.w(TAG, "developer dashboard host adaptation failed: " + recoveryError);
                        return null;
                    }
                }
            });
            Log.i(TAG, "developer dashboard host compatibility hook registered");
        } catch (Throwable error) {
            Log.w(TAG, "developer dashboard host compatibility hook failed: " + error);
        }
    }

    private static void setField(Object instance, String name, Object value) throws Exception {
        Class<?> type = instance.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(instance, value);
                return;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static void hookDeveloperPanelPause(XposedModule mod, ClassLoader cl) {
        try {
            Class<?> fragmentClass = Class.forName(DEV_FRAGMENT, false, cl);
            Method method = Reflect.findMethodExact(fragmentClass, "onPause");
            if (method == null) return;
            mod.hook(method).intercept(chain -> {
                Object result = chain.proceed();
                Object activity = Reflect.callMethod(chain.getThisObject(), "getActivity");
                if (isZybSettingsHost(activity)) {
                    Object switchBar = getHostSwitchBar(activity);
                    if (switchBar != null) Reflect.callMethod(switchBar, "hide");
                }
                return result;
            });
            hookPanelSwitchVisibility(mod, fragmentClass,
                    "developer_options_main_switch_title");
        } catch (Throwable error) {
            Log.w(TAG, "developer panel pause hook failed: " + error);
        }
    }

    /** Keep Settings' nested developer pages inside ZybSettings' right-hand pane. */
    private static void hookDeveloperSubPageNavigation(XposedModule mod, ClassLoader cl) {
        try {
            Class<?> launcherClass = Class.forName(
                    "com.android.settings.core.SubSettingLauncher", false, cl);
            Method method = Reflect.findMethodExact(launcherClass, "launch", android.content.Intent.class);
            if (method == null) {
                Log.w(TAG, "SubSettingLauncher.launch(Intent) not found");
                return;
            }
            mod.hook(method).intercept(chain -> {
                Object launcher = chain.getThisObject();
                Object context = Reflect.getObjectField(launcher, "mContext");
                if (!isZybSettingsHost(context)) {
                    return chain.proceed();
                }
                android.content.Intent intent = (android.content.Intent) chain.getArg(0);
                Method navigate = Reflect.findMethodExact(context.getClass(),
                        "launchInnerSettingFragment", android.content.Intent.class);
                if (navigate == null) {
                    Log.w(TAG, "ZybSettings inner navigation method not found");
                    return chain.proceed();
                }
                navigate.invoke(context, intent);
                Log.i(TAG, "developer subpage opened in ZybSettings panel: "
                        + intent.getStringExtra(":settings:show_fragment"));
                return null;
            });
            Log.i(TAG, "developer subpage navigation hook registered");
        } catch (Throwable error) {
            Log.w(TAG, "developer subpage navigation hook failed: " + error);
        }
    }

    /** XML fragment preferences use PreferenceFragmentCompat, not SubSettingLauncher. */
    private static void hookDeveloperPreferenceNavigation(XposedModule mod, ClassLoader cl) {
        try {
            Class<?> preferenceFragment = Class.forName(
                    "androidx.preference.PreferenceFragmentCompat", false, cl);
            Class<?> preferenceClass = Class.forName("androidx.preference.Preference", false, cl);
            Method method = Reflect.findMethodExact(preferenceFragment,
                    "onPreferenceTreeClick", preferenceClass);
            if (method == null) return;
            mod.hook(method).intercept(chain -> {
                Object fragment = chain.getThisObject();
                if (!fragment.getClass().getName().startsWith("com.android.settings.development.")) {
                    return chain.proceed();
                }
                Object activity = Reflect.callMethod(fragment, "getActivity");
                if (!isZybSettingsHost(activity)) return chain.proceed();
                Object preference = chain.getArg(0);
                String destination = (String) Reflect.callMethod(preference, "getFragment");
                if (destination == null || destination.isEmpty()) return chain.proceed();
                Method navigate = Reflect.findMethodExact(activity.getClass(),
                        "launchInnerSettingFragment", android.content.Intent.class);
                if (navigate == null) return chain.proceed();
                android.content.Intent intent = new android.content.Intent();
                intent.putExtra(":settings:show_fragment", destination);
                intent.putExtra(":settings:show_fragment_args",
                        (Bundle) Reflect.callMethod(preference, "getExtras"));
                navigate.invoke(activity, intent);
                Log.i(TAG, "developer preference opened in ZybSettings panel: " + destination);
                return true;
            });
        } catch (Throwable error) {
            Log.w(TAG, "developer preference navigation hook failed: " + error);
        }
    }

    /** WirelessDebuggingFragment also assumes its Activity is a SettingsActivity. */
    private static void hookWirelessDebuggingHostCompatibility(XposedModule mod, ClassLoader cl) {
        try {
            Class<?> fragmentClass = Class.forName(
                    "com.android.settings.development.WirelessDebuggingFragment", false, cl);
            Method method = Reflect.findMethodExact(fragmentClass, "onActivityCreated", Bundle.class);
            if (method == null) {
                Log.w(TAG, "wireless debugging onActivityCreated not found");
                return;
            }
            mod.hook(method).intercept(chain -> {
                try {
                    return chain.proceed();
                } catch (ClassCastException error) {
                    Object fragment = chain.getThisObject();
                    Object activity = Reflect.callMethod(fragment, "getActivity");
                    if (!isZybSettingsHost(activity)) {
                        throw error;
                    }
                    try {
                        Object switchBar = getHostSwitchBar(activity);
                        if (switchBar == null) throw error;
                        int titleId = ((Context) activity).getResources().getIdentifier(
                                "wireless_debugging_main_switch_title", "string", "com.android.settings");
                        if (titleId != 0) {
                            switchBar.getClass().getMethod("setTitle", CharSequence.class)
                                    .invoke(switchBar, ((Context) activity).getString(titleId));
                        }
                        Reflect.callMethod(switchBar, "show");
                        Class<?> controllerClass = Class.forName(
                                "com.android.settings.widget.MainSwitchBarController", false, cl);
                        Class<?> switchBarClass = Class.forName(
                                "com.android.settings.widget.SettingsMainSwitchBar", false, cl);
                        Object controller = controllerClass.getConstructor(switchBarClass)
                                .newInstance(switchBar);
                        Class<?> enablerClass = Class.forName(
                                "com.android.settings.development.WirelessDebuggingEnabler", false, cl);
                        Class<?> lifecycleClass = Class.forName(
                                "com.android.settingslib.core.lifecycle.Lifecycle", false, cl);
                        Object lifecycle = Reflect.callMethod(fragment, "getSettingsLifecycle");
                        Constructor<?> enablerConstructor = enablerClass.getConstructor(
                                Context.class, Class.forName(
                                        "com.android.settings.widget.SwitchWidgetController", false, cl),
                                Class.forName(
                                        "com.android.settings.development.WirelessDebuggingEnabler$OnEnabledListener",
                                        false, cl), lifecycleClass);
                        Object enabler = enablerConstructor.newInstance(activity, controller, fragment, lifecycle);
                        setField(fragment, "mWifiDebuggingEnabler", enabler);
                        Log.i(TAG, "wireless debugging attached to ZybSettings switch bar");
                        return null;
                    } catch (Throwable recoveryError) {
                        Log.w(TAG, "wireless debugging host adaptation failed: " + recoveryError);
                        return null;
                    }
                }
            });
            Log.i(TAG, "wireless debugging host compatibility hook registered");

            Method onPause = Reflect.findMethodExact(fragmentClass, "onPause");
            if (onPause != null) {
                mod.hook(onPause).intercept(chain -> {
                    Object result = chain.proceed();
                    Object activity = Reflect.callMethod(chain.getThisObject(), "getActivity");
                    if (isZybSettingsHost(activity)) {
                        Object switchBar = getHostSwitchBar(activity);
                        if (switchBar != null) Reflect.callMethod(switchBar, "hide");
                    }
                    return result;
                });
            }
            hookPanelSwitchVisibility(mod, fragmentClass,
                    "wireless_debugging_main_switch_title");
        } catch (Throwable error) {
            Log.w(TAG, "wireless debugging host compatibility hook failed: " + error);
        }
    }

    private static void hookGraphicsDriverHostCompatibility(XposedModule mod, ClassLoader cl) {
        try {
            Class<?> fragmentClass = Class.forName(
                    "com.android.settings.development.graphicsdriver.GraphicsDriverDashboard", false, cl);
            Method onCreate = Reflect.findMethodExact(fragmentClass, "onActivityCreated", Bundle.class);
            if (onCreate == null) return;
            mod.hook(onCreate).intercept(chain -> {
                try {
                    return chain.proceed();
                } catch (ClassCastException error) {
                    Object fragment = chain.getThisObject();
                    Object activity = Reflect.callMethod(fragment, "getActivity");
                    if (!isZybSettingsHost(activity)) throw error;
                    Object switchBar = getHostSwitchBar(activity);
                    if (switchBar == null) throw error;
                    Class<?> switchControllerClass = Class.forName(
                            "com.android.settings.widget.MainSwitchBarController", false, cl);
                    Object switchController = switchControllerClass.getConstructor(Class.forName(
                            "com.android.settings.widget.SettingsMainSwitchBar", false, cl))
                            .newInstance(switchBar);
                    Class<?> controllerClass = Class.forName(
                            "com.android.settings.development.graphicsdriver.GraphicsDriverGlobalSwitchBarController",
                            false, cl);
                    Constructor<?> constructor = controllerClass.getDeclaredConstructor(Context.class,
                            Class.forName("com.android.settings.widget.SwitchWidgetController", false, cl));
                    constructor.setAccessible(true);
                    Object controller = constructor.newInstance(activity, switchController);
                    Object lifecycle = Reflect.callMethod(fragment, "getSettingsLifecycle");
                    Method addObserver = Reflect.findMethodExact(lifecycle.getClass(), "addObserver",
                            Class.forName("androidx.lifecycle.LifecycleObserver", false, cl));
                    if (addObserver == null) throw new NoSuchMethodException("Lifecycle.addObserver");
                    addObserver.invoke(lifecycle, controller);
                    setPanelSwitchVisible(fragment, "graphics_driver_main_switch_title", false);
                    Log.i(TAG, "graphics driver dashboard attached to ZybSettings switch bar");
                    return null;
                }
            });
            hookPanelSwitchVisibility(mod, fragmentClass, "graphics_driver_main_switch_title");
            Method onPause = Reflect.findMethodExact(fragmentClass, "onPause");
            if (onPause != null) {
                mod.hook(onPause).intercept(chain -> {
                    Object result = chain.proceed();
                    if (fragmentClass.isInstance(chain.getThisObject())) {
                        setPanelSwitchVisible(chain.getThisObject(), "graphics_driver_main_switch_title", true);
                    }
                    return result;
                });
            }
        } catch (Throwable error) {
            Log.w(TAG, "graphics driver host compatibility hook failed: " + error);
        }
    }

    private static void hookMemoryDetailNavigation(XposedModule mod, ClassLoader cl) {
        try {
            Class<?> fragmentClass = Class.forName(
                    "com.android.settings.applications.ProcessStatsUi", false, cl);
            Class<?> preferenceClass = Class.forName("androidx.preference.Preference", false, cl);
            Class<?> statsPreferenceClass = Class.forName(
                    "com.android.settings.applications.ProcessStatsPreference", false, cl);
            Method onClick = Reflect.findMethodExact(fragmentClass, "onPreferenceTreeClick", preferenceClass);
            if (onClick == null) return;
            mod.hook(onClick).intercept(chain -> {
                Object fragment = chain.getThisObject();
                Object activity = Reflect.callMethod(fragment, "getActivity");
                Object preference = chain.getArg(0);
                if (!isZybSettingsHost(activity) || !statsPreferenceClass.isInstance(preference)) {
                    return chain.proceed();
                }
                Object manager = Reflect.getObjectField(fragment, "mStatsManager");
                Object memInfo = Reflect.callMethod(manager, "getMemInfo");
                Object entry = Reflect.callMethod(preference, "getEntry");
                // Match ProcessStatsBase.launchMemoryDetail without its SettingsActivity parameter.
                Bundle args = new Bundle();
                args.putParcelable("package_entry", (android.os.Parcelable) entry);
                double weightToRam = ((Number) Reflect.getObjectField(memInfo, "weightToRam")).doubleValue();
                args.putDouble("weight_to_ram", weightToRam);
                args.putLong("total_time", ((Number) Reflect.getObjectField(memInfo, "memTotalTime")).longValue());
                args.putDouble("max_memory_usage",
                        ((Number) Reflect.getObjectField(memInfo, "usedWeight")).doubleValue() * weightToRam);
                args.putDouble("total_scale", ((Number) Reflect.getObjectField(memInfo, "totalScale")).doubleValue());
                android.content.Intent intent = new android.content.Intent();
                intent.putExtra(":settings:show_fragment",
                        "com.android.settings.applications.ProcessStatsDetail");
                intent.putExtra(":settings:show_fragment_args", args);
                intent.putExtra(":settings:source_metrics", 0);
                int title = ((Context) activity).getResources().getIdentifier(
                        "memory_usage", "string", "com.android.settings");
                intent.putExtra(":settings:show_fragment_title_resid", title);
                Method navigate = Reflect.findMethodExact(activity.getClass(),
                        "launchInnerSettingFragment", android.content.Intent.class);
                if (navigate == null) throw new NoSuchMethodException("launchInnerSettingFragment");
                navigate.invoke(activity, intent);
                Log.i(TAG, "memory application detail opened in ZybSettings panel");
                return true;
            });
        } catch (Throwable error) {
            Log.w(TAG, "memory detail navigation hook failed: " + error);
        }
    }

    /** The vendor theme has no platform ActionBar, but AppPicker requires one. */
    private static void hookDeveloperAppPickerTheme(XposedModule mod, ClassLoader cl) {
        try {
            Class<?> pickerClass = Class.forName(
                    "com.android.settings.development.AppPicker", false, cl);
            Method onCreate = Reflect.findMethodExact(pickerClass, "onCreate", Bundle.class);
            if (onCreate == null) return;
            mod.hook(onCreate).intercept(chain -> {
                if (ConfigManager.isEnabled(HookConfig.KEY_DEV_OPTIONS)) {
                    android.app.Activity activity = (android.app.Activity) chain.getThisObject();
                    int nightMode = activity.getResources().getConfiguration().uiMode
                            & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
                    activity.setTheme(nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
                            ? android.R.style.Theme_DeviceDefault
                            : android.R.style.Theme_DeviceDefault_Light);
                }
                return chain.proceed();
            });
        } catch (Throwable error) {
            Log.w(TAG, "developer app picker theme hook failed: " + error);
        }
    }

    private static void hookPanelSwitchVisibility(XposedModule mod, Class<?> fragmentClass,
                                                  String titleResource) {
        try {
            Method onResume = Reflect.findMethodExact(fragmentClass, "onResume");
            if (onResume != null) {
                mod.hook(onResume).intercept(chain -> {
                    Object result = chain.proceed();
                    if (fragmentClass.isInstance(chain.getThisObject())) {
                        setPanelSwitchVisible(chain.getThisObject(), titleResource, false);
                    }
                    return result;
                });
            }
            Method onHiddenChanged = Reflect.findMethodExact(fragmentClass,
                    "onHiddenChanged", boolean.class);
            if (onHiddenChanged != null) {
                mod.hook(onHiddenChanged).intercept(chain -> {
                    Object result = chain.proceed();
                    if (fragmentClass.isInstance(chain.getThisObject())) {
                        setPanelSwitchVisible(chain.getThisObject(), titleResource,
                                (Boolean) chain.getArg(0));
                    }
                    return result;
                });
            }
        } catch (Throwable error) {
            Log.w(TAG, "panel switch visibility hook failed: " + error);
        }
    }

    private static void setPanelSwitchVisible(Object fragment, String titleResource,
                                               boolean hidden) {
        try {
            Object activity = Reflect.callMethod(fragment, "getActivity");
            if (!isZybSettingsHost(activity)) return;
            Object switchBar = getHostSwitchBar(activity);
            if (switchBar == null) return;
            if (hidden || (Boolean) Reflect.callMethod(fragment, "isHidden")) {
                Reflect.callMethod(switchBar, "hide");
                return;
            }
            fitSwitchBarToRightPanel((android.app.Activity) activity,
                    (android.view.View) switchBar, fragment);
            int titleId = ((Context) activity).getResources().getIdentifier(
                    titleResource, "string", "com.android.settings");
            if (titleId != 0) {
                switchBar.getClass().getMethod("setTitle", CharSequence.class)
                        .invoke(switchBar, ((Context) activity).getString(titleId));
            }
            Reflect.callMethod(switchBar, "show");
        } catch (Throwable error) {
            Log.w(TAG, "panel switch visibility update failed: " + error);
        }
    }

    /** The vendor layout places switch_bar above both panes and leaves an empty 96dp title. */
    private static void fitSwitchBarToRightPanel(android.app.Activity activity,
                                                 android.view.View switchBar, Object fragment)
            throws Exception {
        int contentId = activity.getResources().getIdentifier(
                "main_content", "id", "com.android.settings");
        android.view.View content = activity.findViewById(contentId);
        if (content == null || !(content.getParent() instanceof android.view.ViewGroup)) return;
        android.view.ViewGroup contentParent = (android.view.ViewGroup) content.getParent();
        if (switchBar.getParent() != contentParent) {
            if (!(switchBar.getParent() instanceof android.view.ViewGroup)) return;
            android.view.ViewGroup switchParent = (android.view.ViewGroup) switchBar.getParent();
            int contentIndex = contentParent.indexOfChild(content);
            android.view.ViewGroup.LayoutParams panelParams = content.getLayoutParams();
            android.widget.LinearLayout.LayoutParams switchParams =
                    new android.widget.LinearLayout.LayoutParams(switchBar.getLayoutParams());
            switchParams.width = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
            switchParams.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
            switchParams.weight = 0;
            android.widget.LinearLayout panel = new android.widget.LinearLayout(activity);
            panel.setOrientation(android.widget.LinearLayout.VERTICAL);
            switchParent.removeView(switchBar);
            contentParent.removeView(content);
            panel.addView(switchBar, switchParams);
            // Preserve main_content's ID for Settings' fragment navigation and back stack.
            panel.addView(content, new android.widget.LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            contentParent.addView(panel, contentIndex, panelParams);
            Log.i(TAG, "developer switch bar moved into right panel");
        }
        android.view.View fragmentView = (android.view.View) Reflect.callMethod(fragment, "getView");
        if (fragmentView == null) return;
        int titleId = activity.getResources().getIdentifier(
                "title_container", "id", "com.android.settings");
        android.view.View emptyTitle = titleId == 0 ? null : fragmentView.findViewById(titleId);
        if (emptyTitle == null) {
            // The Android 12 layout uses an unnamed 96dp header around tv_title.
            int textId = activity.getResources().getIdentifier(
                    "tv_title", "id", "com.android.settings");
            android.view.View title = textId == 0 ? null : fragmentView.findViewById(textId);
            if (title != null && title.getParent() instanceof android.view.ViewGroup) {
                android.view.ViewGroup header = (android.view.ViewGroup) title.getParent();
                if (header != fragmentView && header.findViewById(android.R.id.list_container) == null) {
                    emptyTitle = header;
                }
            }
        }
        if (emptyTitle != null) emptyTitle.setVisibility(android.view.View.GONE);
        android.view.View list = fragmentView.findViewById(android.R.id.list_container);
        if (list != null && list.getLayoutParams() instanceof android.view.ViewGroup.MarginLayoutParams) {
            android.view.ViewGroup.MarginLayoutParams params =
                    (android.view.ViewGroup.MarginLayoutParams) list.getLayoutParams();
            if (params.topMargin != 0) {
                params.topMargin = 0;
                list.setLayoutParams(params);
            }
        }
    }

private static void hookSysPropGet(XposedModule mod, ClassLoader cl) {
        try {
            Class<?> clz = Class.forName("android.os.SystemProperties", false, cl);
            Method m = Reflect.findMethodExact(clz, "getBoolean", String.class, boolean.class);
            if (m == null) {
                Log.w(TAG, "SystemProperties.getBoolean not found");
                return;
            }
            mod.hook(m).intercept(chain -> {
                if (PROP_ALLOW.equals(chain.getArg(0))
                        && ConfigManager.isEnabled(HookConfig.KEY_DEV_OPTIONS)) {
                    return true;
                }
                return chain.proceed();
            });
            Log.i(TAG, "SystemProperties.getBoolean hook registered");
        } catch (Throwable t) {
            Log.w(TAG, "SystemProperties.getBoolean hook failed: " + t);
        }
    }

private static void hookSysPropSet(XposedModule mod, ClassLoader cl) {
        try {
            Class<?> clz = Class.forName("android.os.SystemProperties", false, cl);
            Method m = Reflect.findMethodExact(clz, "set", String.class, String.class);
            if (m == null) {
                Log.w(TAG, "SystemProperties.set not found");
                return;
            }
            mod.hook(m).intercept(chain -> {
                if (PROP_ALLOW.equals(chain.getArg(0))
                        && "false".equals(chain.getArg(1))
                        && ConfigManager.isEnabled(HookConfig.KEY_DEV_OPTIONS)) {
                    Log.i(TAG, "block sys.allow.development -> false");
                    return null;
                }
                return chain.proceed();
            });
            Log.i(TAG, "SystemProperties.set hook registered");
        } catch (Throwable t) {
            Log.w(TAG, "SystemProperties.set hook failed: " + t);
        }
    }

private static void hookEnabler(XposedModule mod, ClassLoader cl) {
        try {
            Class<?> clz = Class.forName(
                    "com.android.settingslib.development.DevelopmentSettingsEnabler", false, cl);
            Method m = Reflect.findMethodExact(clz, "isDevelopmentSettingsEnabled",
                    Context.class);
            if (m == null) {
                Log.w(TAG, "isDevelopmentSettingsEnabled not found");
                return;
            }
            mod.hook(m).intercept(chain -> {
                if (ConfigManager.isEnabled(HookConfig.KEY_DEV_OPTIONS)) {
                    return true;
                }
                return chain.proceed();
            });
            Log.i(TAG, "isDevelopmentSettingsEnabled hook registered");
        } catch (Throwable t) {
            Log.w(TAG, "isDevelopmentSettingsEnabled hook failed: " + t);
        }
    }

private static void hookSpecialAdbDebug(XposedModule mod, ClassLoader cl) {
        try {
            Class<?> clz = Class.forName("android.provider.Settings$Global", false, cl);
            Method m = Reflect.findMethodExact(clz, "getInt",
                    ContentResolver.class, String.class, int.class);
            if (m == null) {
                Log.w(TAG, "Settings.Global.getInt not found");
                return;
            }
            mod.hook(m).intercept(chain -> {
                if (KEY_SPECIAL_ADB.equals(chain.getArg(1))
                        && ConfigManager.isEnabled(HookConfig.KEY_DEV_OPTIONS)) {
                    return 1;
                }
                return chain.proceed();
            });
            Log.i(TAG, "Settings.Global.getInt hook registered");
        } catch (Throwable t) {
            Log.w(TAG, "Settings.Global.getInt hook failed: " + t);
        }
    }
}
