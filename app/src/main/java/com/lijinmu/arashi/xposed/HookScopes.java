package com.lijinmu.arashi.xposed;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.lijinmu.arashi.uninstall.UninstallConfig;

import io.github.libxposed.service.HookedTarget;
import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

public final class HookScopes {
    private static volatile XposedService service;
    private static boolean initialized;
    private static volatile boolean systemObserved;
    private static SharedPreferences.OnSharedPreferenceChangeListener settingsListener;
    private static final ExecutorService SYNC_WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread worker = new Thread(task, "Arashi-RemoteSettings");
        worker.setDaemon(true);
        return worker;
    });

    private HookScopes() {
    }

    private static synchronized void initialize(Context context) {
        if (initialized) return;
        initialized = true;
        Context app = context.getApplicationContext();
        settingsListener = (prefs, key) -> SYNC_WORKER.execute(() -> {
            XposedService current = service;
            if (current != null) HookSettingsSync.publish(app, current);
        });
        app.getSharedPreferences(HookConfig.PREF_NAME, Context.MODE_PRIVATE)
                .registerOnSharedPreferenceChangeListener(settingsListener);
        XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener() {
            @Override public void onServiceBind(XposedService bound) {
                service = bound;
                SYNC_WORKER.execute(() -> HookSettingsSync.publish(app, bound));
            }

            @Override public void onServiceDied(XposedService dead) {
                if (service == dead) service = null;
            }
        });
    }

    public static boolean isSystemServerActive(Context context) {
        try {
            int boot = Settings.Global.getInt(context.getContentResolver(), "boot_count", -1);
            int reported = context.getSharedPreferences(SettingsProvider.STATUS_PREF_NAME,
                    Context.MODE_PRIVATE).getInt(SettingsProvider.KEY_SYSTEM_SERVER_BOOT, -1);
            if (boot >= 0 && boot == reported) return true;
        } catch (RuntimeException ignored) {
        }
        return systemObserved;
    }

    public static Snapshot read(Context context) {
        initialize(context);
        boolean systemActive = isSystemServerActive(context);
        XposedService current = service;
        if (current != null) {
            try {
                if (current.getApiVersion() >= XposedService.API_102) {
                    for (HookedTarget target : current.getRunningTargets()) {
                        if (("system_server".equals(target.getProcessName())
                                || "system".equals(target.getProcessName()))
                                && target.getUid() == android.os.Process.SYSTEM_UID) {
                            systemActive = true;
                            systemObserved = true;
                            int boot = Settings.Global.getInt(context.getContentResolver(),
                                    "boot_count", -1);
                            if (boot >= 0) {
                                SharedPreferences runtime = context.getSharedPreferences(
                                        SettingsProvider.STATUS_PREF_NAME, Context.MODE_PRIVATE);
                                if (runtime.getInt(SettingsProvider.KEY_SYSTEM_SERVER_BOOT, -1) != boot) {
                                    runtime.edit().putInt(SettingsProvider.KEY_SYSTEM_SERVER_BOOT, boot).apply();
                                }
                            }
                            UninstallConfig.syncActivation(context, true);
                            break;
                        }
                    }
                }
            } catch (RuntimeException ignored) {
            }
            try {
                Snapshot snapshot = new Snapshot(systemActive, true, new HashSet<>(current.getScope()));
                SharedPreferences prefs = context.getSharedPreferences(HookConfig.PREF_NAME, Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = null;
                for (String key : HookConfig.keys()) {
                    if (!snapshot.hasSelectedScope(key)
                            && prefs.getBoolean(key, HookConfig.defaultEnabled(key))) {
                        if (editor == null) editor = prefs.edit();
                        editor.putBoolean(key, false);
                    }
                }
                if (editor != null) editor.apply();
                HookSettingsSync.publish(context, current);
                return snapshot;
            } catch (RuntimeException ignored) {
            }
        }
        return new Snapshot(systemActive, false, Collections.emptySet());
    }


    public interface ScopeResult {
        void complete(boolean approved, String message);
    }

    /** Submit only missing targets; callback always returns on the main thread. */
    public static boolean requestForHook(Context context, String key, ScopeResult result) {
        initialize(context);
        android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
        XposedService current = service;
        if (current == null) {
            main.post(() -> result.complete(false, "无法连接 LSPosed，请确认模块已启用"));
            return false;
        }
        try {
            java.util.List<String> required = new java.util.ArrayList<>();
            if (usesSystemServer(key)) required.add("system");
            String[] apps = applicationScopes(key);
            if (apps.length > 0) {
                String target = null;
                for (String pkg : apps) {
                    try {
                        context.getPackageManager().getApplicationInfo(pkg, 0);
                        target = pkg;
                        break;
                    } catch (android.content.pm.PackageManager.NameNotFoundException ignored) {}
                }
                if (target == null) {
                    main.post(() -> result.complete(false, "未找到对应作用域应用"));
                    return false;
                }
                required.add(target);
            }
            java.util.List<String> missing = new java.util.ArrayList<>(required);
            missing.removeAll(current.getScope());
            if (missing.isEmpty()) {
                main.post(() -> result.complete(true, ""));
                return false;
            }
            current.requestScope(missing, new XposedService.OnScopeEventListener() {
                @Override public void onScopeRequestApproved(java.util.List<String> approved) {
                    boolean granted = approved.containsAll(missing);
                    main.post(() -> result.complete(granted, granted ? "" : "作用域未全部获准，开关保持关闭"));
                }
                @Override public void onScopeRequestFailed(String message) {
                    main.post(() -> result.complete(false, "作用域申请未获准：" + message));
                }
            });
            return true;
        } catch (RuntimeException error) {
            main.post(() -> result.complete(false, "作用域申请失败：" + error.getMessage()));
            return false;
        }
    }

    private static boolean usesSystemServer(String key) {
        return !HookConfig.KEY_JC_LOCAL_MODE.equals(key)
                && !HookConfig.KEY_AOSP_APP_DETAILS.equals(key)
                && !HookConfig.KEY_UNLIMITED_NOTIFICATION_ICONS.equals(key)
                && !HookConfig.KEY_SYSTEMUI_MONET.equals(key)
                && !HookConfig.KEY_AOSP_STATUS_ICONS.equals(key)
                && !HookConfig.KEY_NOTIFICATION_LONG_PRESS.equals(key)
                && !HookConfig.KEY_NOTIFICATION_REPLY.equals(key)
                && !HookConfig.KEY_BLOCK_TCP_LOGIN_FAIL.equals(key)
                && !HookConfig.KEY_RECORD_INTERNAL_AUDIO.equals(key)
                && !HookConfig.KEY_STATUS_BAR_DATE.equals(key)
                && !HookConfig.KEY_IOS_ICONS.equals(key)
                && !HookConfig.KEY_EXTERNAL_INSTALL.equals(key)
                && !HookConfig.KEY_CAPTIVE_PORTAL.equals(key)
                && !HookConfig.KEY_DEV_OPTIONS.equals(key)
                && !HookConfig.KEY_DEV_OPTIONS_MENU.equals(key)
                && !HookConfig.KEY_NEUTRALIZE_POLICY.equals(key)
                && !HookConfig.KEY_NEUTRALIZE_ADVANCE.equals(key)
                && !HookConfig.KEY_NO_TIMEOUT_LOCK.equals(key)
                && !HookConfig.KEY_BLOCK_USAGE_REPORT.equals(key);
    }

    private static String[] applicationScopes(String key) {
        if (HookConfig.KEY_JC_LOCAL_MODE.equals(key)) return new String[] { "JC.CODE.V3" };
        if (HookConfig.KEY_KEEP_NAVIGATION.equals(key)) {
            return new String[] { "com.android.systemui", "com.zuoyebang.iot.pad.launcher14", "com.android.launcher3" };
        }
        if (HookConfig.KEY_LEGACY_POWER_MENU.equals(key)) {
            return new String[] { "com.android.systemui" };
        }
        if (HookConfig.KEY_BLOCK_TCP_LOGIN_FAIL.equals(key)) {
            return new String[] { "com.zuoyebang.iot.zptcpservice" };
        }
        if (HookConfig.KEY_EXTERNAL_INSTALL.equals(key)) {
            return new String[] { "com.android.packageinstaller" };
        }
        if (HookConfig.KEY_CAPTIVE_PORTAL.equals(key)) {
            return new String[] { "com.android.captiveportallogin" };
        }
        if (HookConfig.KEY_DEV_OPTIONS.equals(key) || HookConfig.KEY_DEV_OPTIONS_MENU.equals(key)
                || HookConfig.KEY_AOSP_APP_DETAILS.equals(key)) {
            return new String[] { "com.android.settings" };
        }
        if (HookConfig.KEY_NEUTRALIZE_POLICY.equals(key)
                || HookConfig.KEY_NEUTRALIZE_ADVANCE.equals(key)
                || HookConfig.KEY_NO_TIMEOUT_LOCK.equals(key)
                || HookConfig.KEY_BLOCK_USAGE_REPORT.equals(key)) {
            return new String[] { "com.zuoyebang.iot.pad.supervise" };
        }
        if (HookConfig.KEY_ALLOW_WALLPAPER.equals(key)
                || HookConfig.KEY_IOS_ICONS.equals(key)) {
            return new String[] { "com.zuoyebang.iot.pad.launcher14", "com.android.launcher3" };
        }
        if (HookConfig.KEY_NOTIFICATION_LONG_PRESS.equals(key)
                || HookConfig.KEY_NOTIFICATION_REPLY.equals(key)
                || HookConfig.KEY_UNLIMITED_NOTIFICATION_ICONS.equals(key)
                || HookConfig.KEY_SYSTEMUI_MONET.equals(key)
                || HookConfig.KEY_BLOCK_DEFAULT_BROWSER.equals(key)
                || HookConfig.KEY_AOSP_STATUS_ICONS.equals(key)
                || HookConfig.KEY_STATUS_BAR_DATE.equals(key)
                || HookConfig.KEY_RECORD_INTERNAL_AUDIO.equals(key)) {
            return new String[] { "com.android.systemui" };
        }
        return new String[0];
    }

    public static final class Snapshot {
        public final boolean systemActive;
        public final boolean scopeKnown;
        private final Set<String> selected;

        public Snapshot(boolean systemActive, boolean scopeKnown, Set<String> selected) {
            this.systemActive = systemActive;
            this.scopeKnown = scopeKnown;
            this.selected = Collections.unmodifiableSet(new HashSet<>(selected));
        }

        public boolean hasSelectedScope(String key) {
            if (!scopeKnown) return false;
            boolean systemSelected = selected.contains("system");
            if (HookConfig.KEY_LEGACY_POWER_MENU.equals(key))
                return systemSelected && selected.contains("com.android.systemui");
            if (usesSystemServer(key) && systemSelected) return true;
            for (String pkg : applicationScopes(key)) {
                if (selected.contains(pkg)) return true;
            }
            return false;
        }

        public boolean isAvailable(String key) {
            if (scopeKnown && !hasSelectedScope(key)) return false;
            if (HookConfig.KEY_LEGACY_POWER_MENU.equals(key)) {
                return systemActive && scopeKnown && selected.contains("com.android.systemui");
            }
            if (usesSystemServer(key) && systemActive) return true;
            if (scopeKnown) {
                for (String pkg : applicationScopes(key)) {
                    if (selected.contains(pkg)) return true;
                }
            }
            return false;
        }

        public Set<String> getSelectedPackages() {
            return selected;
        }

        public String unavailableReason(String key) {
            if (HookConfig.KEY_LEGACY_POWER_MENU.equals(key)) {
                if (!systemActive) return "请勾选系统框架作用域并重启设备";
                if (!scopeKnown) return "无法读取 LSPosed 作用域，请确认模块已启用并返回此页面";
                return "请勾选系统界面 com.android.systemui 作用域并重启设备";
            }
            String[] packages = applicationScopes(key);
            if (packages.length == 0) {
                return "未检测到本次开机的 SystemServer 的 Hook，请勾选系统框架作用域并重启";
            }
            if (!scopeKnown) {
                return "无法读取 LSPosed 作用域，请确认模块已启用并返回此页面";
            }
            String targets = String.join("、", packages);
            if (usesSystemServer(key)) {
                return "未检测到系统框架 Hook，并且且未勾选应用作用域：" + targets;
            }
            return "未勾选对应作用域：" + targets + "，请在 LSPosed 中勾选后重启目标应用";
        }
    }
}
