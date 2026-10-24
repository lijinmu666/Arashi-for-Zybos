package com.arashi.xposed;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.arashi.uninstall.UninstallConfig;

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
            HookSettingsSync.publish(context, current);
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
                return new Snapshot(systemActive, true, new HashSet<>(current.getScope()));
            } catch (RuntimeException ignored) {
            }
        }
        return new Snapshot(systemActive, false, Collections.emptySet());
    }

    private static boolean usesSystemServer(String key) {
        return !HookConfig.KEY_EXTERNAL_INSTALL.equals(key)
                && !HookConfig.KEY_CAPTIVE_PORTAL.equals(key)
                && !HookConfig.KEY_DEV_OPTIONS.equals(key)
                && !HookConfig.KEY_NEUTRALIZE_POLICY.equals(key)
                && !HookConfig.KEY_NEUTRALIZE_ADVANCE.equals(key)
                && !HookConfig.KEY_NO_TIMEOUT_LOCK.equals(key)
                && !HookConfig.KEY_BLOCK_USAGE_REPORT.equals(key);
    }

    private static String[] applicationScopes(String key) {
        if (HookConfig.KEY_EXTERNAL_INSTALL.equals(key)) {
            return new String[] { "com.android.packageinstaller" };
        }
        if (HookConfig.KEY_CAPTIVE_PORTAL.equals(key)) {
            return new String[] { "com.android.captiveportallogin" };
        }
        if (HookConfig.KEY_DEV_OPTIONS.equals(key)) {
            return new String[] { "com.android.settings" };
        }
        if (HookConfig.KEY_NEUTRALIZE_POLICY.equals(key)
                || HookConfig.KEY_NEUTRALIZE_ADVANCE.equals(key)
                || HookConfig.KEY_NO_TIMEOUT_LOCK.equals(key)
                || HookConfig.KEY_BLOCK_USAGE_REPORT.equals(key)) {
            return new String[] { "com.zuoyebang.iot.pad.supervise" };
        }
        if (HookConfig.KEY_BLOCK_DEFAULT_BROWSER.equals(key)) {
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

        public boolean isAvailable(String key) {
            if (usesSystemServer(key) && systemActive) return true;
            if (scopeKnown) {
                for (String pkg : applicationScopes(key)) {
                    if (selected.contains(pkg)) return true;
                }
            }
            return false;
        }

        public String unavailableReason(String key) {
            String[] packages = applicationScopes(key);
            if (packages.length == 0) {
                return "未检测到本次开机的 system_server Hook，请勾选系统框架作用域并重启";
            }
            if (!scopeKnown) {
                return "暂无法读取 LSPosed 作用域，请确认模块已启用并返回此页面";
            }
            String targets = String.join("、", packages);
            if (usesSystemServer(key)) {
                return "未检测到系统框架 Hook，且未勾选应用作用域：" + targets;
            }
            return "未勾选对应作用域：" + targets + "；请在 LSPosed 中勾选后重启目标应用";
        }
    }
}
