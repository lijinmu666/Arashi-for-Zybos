package com.lijinmu.arashi.xposed.hooks;

import android.content.Intent;
import android.util.Log;
import com.lijinmu.arashi.xposed.ConfigManager;
import com.lijinmu.arashi.xposed.HookConfig;
import com.lijinmu.arashi.xposed.Reflect;
import java.lang.reflect.Method;
import io.github.libxposed.api.XposedModule;

/** Keep the original launcher's arguments, target user and result callback, but select the right host. */
public final class SettingsPageRoutes {
    static final String DETAILS = "com.android.settings.applications.appinfo.AppInfoDashboardFragment";
    static final String NOTIFICATIONS = "com.android.settings.notification.app.AppNotificationSettings";
    private static final String TAG = "[Arashi-SettingsRoute]";
    private SettingsPageRoutes() {}

    public static void hookSettings(XposedModule module, ClassLoader loader) {
        for (String name : new String[] { "com.android.settings.core.ZybSubSettingLauncher",
                "com.android.settings.zyb.ZybSubSettingLauncher", "com.android.settings.core.SubSettingLauncher" }) {
            try {
                Class<?> type = loader.loadClass(name);
                Method intent = Reflect.findMethodExact(type, "toIntent");
                if (intent == null) continue;
                module.hook(intent).intercept(chain -> {
                    Object result = chain.proceed();
                    if (!(result instanceof Intent)) return result;
                    try { return route((Intent) result, loader); }
                    catch (Throwable error) {
                        Log.w(TAG, "Keeping original settings destination", error);
                        return result;
                    }
                });
                for (Method method : type.getDeclaredMethods()) {
                    if (method.getName().equals("launch")) module.deoptimize(method);
                }
            } catch (ClassNotFoundException ignored) {
            } catch (Throwable error) { Log.w(TAG, "Launcher unavailable: " + name, error); }
        }
    }

    static Intent route(Intent original, ClassLoader loader) throws Exception {
        String destination = original.getStringExtra(":settings:show_fragment");
        String target;
        if (DETAILS.equals(destination) && ConfigManager.isEnabled(HookConfig.KEY_AOSP_APP_DETAILS)) {
            target = "com.android.settings.applications.InstalledAppDetailsTop";
        } else if (NOTIFICATIONS.equals(destination)
                && ConfigManager.isEnabled(HookConfig.KEY_AOSP_APP_DETAILS)) {
            target = "com.android.settings.Settings$AppNotificationSettingsActivity";
        } else return original;
        loader.loadClass(target); // An unknown firmware keeps its original route.
        Intent intent = new Intent(original);
        intent.setClassName("com.android.settings", target);
        intent.removeExtra(":settings:show_zyb_index");
        return intent;
    }
}
