package com.arashi.uninstall;

import com.arashi.ui.BasePage;
import com.arashi.ui.MiuixPages;

public final class UninstallHistoryActivity extends BasePage {
    @Override protected void populate() {
        MiuixPages.renderUninstallHistory(this);
    }
}
