package com.arashi.tools;

import com.arashi.ui.BasePage;
import com.arashi.ui.MiuixPages;

import android.content.Intent;

public final class ZybosUninstallPickerActivity extends BasePage {
    static final String EXTRA_PACKAGE_NAME = "package_name";

    @Override protected void populate() {
        MiuixPages.renderZybosUninstallPicker(this);
    }

    public void choosePackage(String packageName) {
        setResult(RESULT_OK, new Intent().putExtra(EXTRA_PACKAGE_NAME, packageName));
        finish();
    }
}
