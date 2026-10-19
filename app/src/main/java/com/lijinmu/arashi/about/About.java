package com.lijinmu.arashi.about;

import com.lijinmu.arashi.ui.BasePage;
import com.lijinmu.arashi.ui.MiuixPages;

public final class About extends BasePage {
    @Override protected void populate() {
        MiuixPages.renderAbout(this);
    }
}
