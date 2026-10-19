package com.lijinmu.arashi.settings;

import com.lijinmu.arashi.ui.BasePage;
import com.lijinmu.arashi.ui.MiuixPages;

import android.content.Context;
import android.content.SharedPreferences;
import com.lijinmu.arashi.xposed.HookConfig;

public final class HookSettings extends BasePage {
    private SharedPreferences prefs;

    @Override
    protected void populate() {
        MiuixPages.renderHookSettings(this);
        if (getSharedPreferences(HookConfig.PREF_NAME, MODE_PRIVATE)
                .getBoolean(HookConfig.KEY_BLOCK_TCP_LOGIN_FAIL, false)
                && com.lijinmu.arashi.notifications.NotificationPreferences.enabled(this, com.lijinmu.arashi.notifications.NotificationPreferences.TCP)) {
            com.lijinmu.arashi.notifications.AppNotifications.requestPermissionIfNeeded(this);
        }

}
}
