package com.arashi.tcp;

import com.arashi.ui.BasePage;
import com.arashi.ui.MiuixPages;

public final class TcpHistoryActivity extends BasePage {
    @Override protected void populate() {
        MiuixPages.renderTcpHistory(this);
    }
}
