package com.arashi.settings;

import com.arashi.ApkInstallerActivity;
import com.arashi.ui.BasePage;
import com.arashi.ui.MiuixPages;

import android.content.ComponentName;
import android.content.pm.PackageManager;

public final class ArashiSettings extends BasePage {
    @Override protected void populate() {
        MiuixPages.renderSettings(this);
    }

    public boolean isLauncherHidden() {
        ComponentName launcher = new ComponentName(this, "com.arashi.LauncherAlias");
        return getPackageManager().getComponentEnabledSetting(launcher)
                == PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
    }

    public void setLauncherHidden(boolean hidden) {
        ComponentName launcher = new ComponentName(this, "com.arashi.LauncherAlias");
        getPackageManager().setComponentEnabledSetting(launcher,
                hidden ? PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                        : PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP);
    }

    public boolean isInstallerEnabled() {
        ComponentName installer = new ComponentName(this, ApkInstallerActivity.class);
        return getPackageManager().getComponentEnabledSetting(installer)
                != PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
    }

    public void setInstallerEnabled(boolean enabled) {
        ComponentName installer = new ComponentName(this, ApkInstallerActivity.class);
        getPackageManager().setComponentEnabledSetting(installer,
                enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
    }
}
