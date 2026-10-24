package com.arashi.about;

import com.arashi.ui.BasePage;
import com.arashi.ui.MiuixPages;

public final class About extends BasePage {
    @Override protected void populate() {
        MiuixPages.renderAbout(this);
    }
}
