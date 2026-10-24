package com.arashi.uninstall;

import com.arashi.ui.BasePage;
import com.arashi.ui.MiuixPages;

public final class UninstallSettings extends BasePage {
    @Override protected void populate() {
        MiuixPages.renderUninstallSettings(this);
    }
}
