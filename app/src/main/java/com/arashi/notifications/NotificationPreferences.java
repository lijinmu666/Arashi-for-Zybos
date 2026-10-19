package com.arashi.notifications;

import android.content.Context;

public final class NotificationPreferences {
    public static final String PREFS = "event_notifications";
    public static final String TCP = "tcp_blocked";
    public static final String BLACKLIST = "uninstall_blacklist";
    public static final String WHITELIST = "uninstall_whitelist";
    public static final String SCREEN_OFF = "uninstall_screen_off";
    public static final String TIMEOUT = "uninstall_timeout";
    private NotificationPreferences() {}

    public static boolean enabled(Context context, String key) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(key, true);
    }
    public static boolean uninstallEnabled(Context context) {
        return enabled(context, BLACKLIST) || enabled(context, WHITELIST) || enabled(context, TIMEOUT) || enabled(context, SCREEN_OFF);
    }
}
