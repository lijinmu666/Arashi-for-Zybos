package com.arashi.settings;

import com.arashi.ui.BasePage;
import com.arashi.ui.MiuixPages;

import android.content.Context;
import android.content.SharedPreferences;
import com.arashi.xposed.HookConfig;

public final class HookSettings extends BasePage {
    private SharedPreferences prefs;

    @Override
    protected void populate() {
        MiuixPages.renderHookSettings(this);
        if (getSharedPreferences(HookConfig.PREF_NAME, MODE_PRIVATE)
                .getBoolean(HookConfig.KEY_BLOCK_TCP_LOGIN_FAIL, false)
                && com.arashi.notifications.NotificationPreferences.enabled(this, com.arashi.notifications.NotificationPreferences.TCP)) {
            com.arashi.notifications.AppNotifications.requestPermissionIfNeeded(this);
        }

}
}
