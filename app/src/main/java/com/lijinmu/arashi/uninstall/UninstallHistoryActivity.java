package com.lijinmu.arashi.uninstall;

import com.lijinmu.arashi.ui.BasePage;
import com.lijinmu.arashi.ui.MiuixPages;

public final class UninstallHistoryActivity extends BasePage {
    @Override protected void populate() {
        MiuixPages.renderUninstallHistory(this);
    }
}
