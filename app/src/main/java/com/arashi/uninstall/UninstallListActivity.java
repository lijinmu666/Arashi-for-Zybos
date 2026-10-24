package com.arashi.uninstall;

import com.arashi.ui.BasePage;
import com.arashi.ui.MiuixPages;

public final class UninstallListActivity extends BasePage {
    @Override protected void populate() {
        MiuixPages.renderUninstallList(this,
                getIntent().getBooleanExtra("blacklist", false));
    }
}
