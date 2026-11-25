package com.lijinmu.arashi.uninstall;

import android.content.Context;
import android.content.SharedPreferences;

public final class UninstallConfig {
    public static final String PREF_NAME = "uninstall_validation";
    public static final String ENABLED = "enabled";
    public static final String FREEZE_ENABLED = "freeze_enabled";
    public static final String OPERATION = "operation";
    public static final String UNINSTALL = "uninstall";
    public static final String FREEZE = "freeze";
    public static final String HIDE = "hide";
    public static final String SUSPEND = "suspend";
    public static final String CLEAR = "clear";
    public static final String RESTORE_ENABLED = "restore_enabled_after_activation";
    public static final String SPOOF_RESULT = "spoof_uninstall_result";
    public static final String DENIAL_RESPONSE = "denial_response";
    public static final String DENIAL_MESSAGE = "denial_message";
    public static final int DEFAULT_RESPONSE = 0;
    public static final int SPOOF_SUCCESS = 1;
    public static final int CUSTOM_RESPONSE = 2;
    public static final int MAX_MESSAGE_LENGTH = 200;
    public static final String TIMEOUT_SECONDS = "timeout_seconds";
    public static final String WHITELIST = "whitelist";
    public static final String WHITELIST_ONLY = "whitelist_only";
    public static final String BLACKLIST = "blacklist";
    public static final String BLOCKED_TARGETS = "blocked_targets";
    public static final String UNMANAGED_TARGETS = "unmanaged_targets";
    public static final String KEEP_DATA = "keep_uninstall_data";
    public static final int DEFAULT_TIMEOUT = 10;

    public static int normalizeTimeoutSeconds(int seconds) {
        return seconds == 5 || seconds == 10 || seconds == 15 || seconds == 20 || seconds == 30
                ? seconds : DEFAULT_TIMEOUT;
    }

    public static String operationLabel(String operation) {
        if (FREEZE.equals(operation)) return "冻结应用";
        if (HIDE.equals(operation)) return "隐藏应用";
        if (SUSPEND.equals(operation)) return "暂停应用";
        if (CLEAR.equals(operation)) return "清空应用数据";
        return "卸载应用";
    }

    public static synchronized boolean syncActivation(Context context, boolean active) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        boolean enabled = prefs.getBoolean(ENABLED, false);
        boolean restore = prefs.getBoolean(RESTORE_ENABLED, false);
        if (!active) {
            if (enabled) {
                prefs.edit().putBoolean(ENABLED, false)
                        .putBoolean(RESTORE_ENABLED, true).apply();
            }
            return false;
        }
        if (restore) {
            prefs.edit().putBoolean(ENABLED, true)
                    .putBoolean(RESTORE_ENABLED, false).apply();
            return true;
        }
        return enabled;
    }

    private UninstallConfig() {
    }
}
