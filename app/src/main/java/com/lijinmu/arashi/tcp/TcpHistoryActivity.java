package com.lijinmu.arashi.tcp;

import com.lijinmu.arashi.ui.BasePage;
import com.lijinmu.arashi.ui.MiuixPages;

public final class TcpHistoryActivity extends BasePage {
    @Override protected void populate() {
        MiuixPages.renderTcpHistory(this);
    }
}
