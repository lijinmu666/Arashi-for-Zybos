package com.arashi.settings;

import com.arashi.ui.BasePage;
import com.arashi.ui.MiuixPages;

public final class NotificationSettings extends BasePage {
    @Override protected void populate() {
        MiuixPages.renderNotificationSettings(this);
    }
}
