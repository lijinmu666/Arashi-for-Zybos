package com.lijinmu.arashi.uninstall;

import com.lijinmu.arashi.ui.BasePage;
import com.lijinmu.arashi.ui.MiuixPages;

public final class UninstallSettings extends BasePage {
    @Override protected void populate() {
        MiuixPages.renderUninstallSettings(this);
        if (getSharedPreferences(UninstallConfig.PREF_NAME, MODE_PRIVATE).getBoolean(UninstallConfig.ENABLED, false)
                && com.lijinmu.arashi.notifications.NotificationPreferences.uninstallEnabled(this)) {
            com.lijinmu.arashi.notifications.AppNotifications.requestPermissionIfNeeded(this);
        }
    }
}
