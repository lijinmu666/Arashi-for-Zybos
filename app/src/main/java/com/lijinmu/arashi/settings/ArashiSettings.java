package com.lijinmu.arashi.settings;

import com.lijinmu.arashi.ui.BasePage;
import com.lijinmu.arashi.ui.MiuixPages;

import android.content.ComponentName;
import android.content.pm.PackageManager;

public final class ArashiSettings extends BasePage {
    @Override protected void populate() {
        MiuixPages.renderSettings(this);
    }

    public boolean isLauncherHidden() {
        ComponentName launcher = new ComponentName(this, "com.lijinmu.arashi.LauncherAlias");
        return getPackageManager().getComponentEnabledSetting(launcher)
                == PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
    }

    public void setLauncherHidden(boolean hidden) {
        ComponentName launcher = new ComponentName(this, "com.lijinmu.arashi.LauncherAlias");
        getPackageManager().setComponentEnabledSetting(launcher,
                hidden ? PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                        : PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP);
    }

}
