package com.lijinmu.arashi.uninstall;

import com.lijinmu.arashi.ui.BasePage;
import com.lijinmu.arashi.ui.MiuixPages;

public final class UninstallListActivity extends BasePage {
    @Override protected void populate() {
        MiuixPages.renderUninstallList(this,
                getIntent().getBooleanExtra("blacklist", false));
    }
}
