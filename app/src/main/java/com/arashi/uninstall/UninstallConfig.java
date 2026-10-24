package com.arashi.uninstall;

import android.content.Context;
import android.content.SharedPreferences;

public final class UninstallConfig {
    public static final String PREF_NAME = "uninstall_validation";
    public static final String ENABLED = "enabled";
    public static final String RESTORE_ENABLED = "restore_enabled_after_activation";
    public static final String SPOOF_RESULT = "spoof_uninstall_result";
    public static final String TIMEOUT_SECONDS = "timeout_seconds";
    public static final String WHITELIST = "whitelist";
    public static final String BLACKLIST = "blacklist";
    public static final int DEFAULT_TIMEOUT = 10;

    public static int normalizeTimeoutSeconds(int seconds) {
        return seconds == 5 || seconds == 10 || seconds == 15 || seconds == 30
                ? seconds : DEFAULT_TIMEOUT;
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
