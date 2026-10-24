package com.lijinmu.arashi.settings;

import com.lijinmu.arashi.ui.BasePage;
import com.lijinmu.arashi.ui.MiuixPages;

public final class NotificationSettings extends BasePage {
    @Override protected void populate() {
        MiuixPages.renderNotificationSettings(this);
    }
}
