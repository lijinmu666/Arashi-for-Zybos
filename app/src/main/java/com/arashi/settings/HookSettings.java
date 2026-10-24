package com.arashi.settings;

import com.arashi.ui.BasePage;
import com.arashi.ui.MiuixPages;

import android.content.Context;
import android.content.SharedPreferences;
import com.arashi.xposed.HookConfig;

public final class HookSettings extends BasePage {
    private SharedPreferences prefs;

    @Override
    protected void populate() {
        MiuixPages.renderHookSettings(this);

}
}
