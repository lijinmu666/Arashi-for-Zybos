package com.arashi.uninstall;

import com.arashi.ui.BasePage;
import com.arashi.ui.MiuixPages;

public final class UninstallSettings extends BasePage {
    @Override protected void populate() {
        MiuixPages.renderUninstallSettings(this);
        if (getSharedPreferences(UninstallConfig.PREF_NAME, MODE_PRIVATE).getBoolean(UninstallConfig.ENABLED, false)
                && com.arashi.notifications.NotificationPreferences.uninstallEnabled(this)) {
            com.arashi.notifications.AppNotifications.requestPermissionIfNeeded(this);
        }
    }
}
