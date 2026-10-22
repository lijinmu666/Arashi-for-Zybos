package com.lijinmu.arashi;

import com.lijinmu.arashi.security.SignatureVerifier;
import com.lijinmu.arashi.ui.BasePage;
import com.lijinmu.arashi.ui.MiuixPages;
import com.lijinmu.arashi.ui.Ui;

import android.content.Intent;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.provider.Settings;
import com.lijinmu.arashi.xposed.SettingsProvider;

public final class Main extends BasePage {
    private TextView lsposedState;
    private boolean verifySignatureOnLaunch;

    @Override protected void onResume() {
        super.onResume();
        updateModuleStatus();
    }

    @Override public void onCreate(android.os.Bundle state) {
        super.onCreate(state);
        // Verify new launches, not a restored page or a return from the background.
        verifySignatureOnLaunch = state == null;
        new Thread(() -> {
            boolean supported = "ZYB".equals(readSystemBrand());
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (supported) {
                    populateHome();
                } else {
                    MiuixPages.renderUnsupportedDevice(this);
                }
            });
        }, "Arashi-BrandCheck").start();
    }

    private static String readSystemBrand() {
        Process process = null;
        try {
            process = new ProcessBuilder("/system/bin/getprop", "ro.product.system.brand").start();
            if (!process.waitFor(2, TimeUnit.SECONDS) || process.exitValue() != 0) return "";
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String value = reader.readLine();
                return value == null ? "" : value.trim();
            }
        } catch (Exception ignored) {
            return "";
        } finally {
            if (process != null) process.destroy();
        }
    }

    @Override protected void populate() {
        
    }

    public void exitUnsupportedDevice() {
        finishAffinity();
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    private void populateHome() {
        MiuixPages.renderHome(this);
        if (getSharedPreferences(com.lijinmu.arashi.xposed.HookConfig.PREF_NAME, MODE_PRIVATE)
                .getBoolean(com.lijinmu.arashi.xposed.HookConfig.KEY_BLOCK_TCP_LOGIN_FAIL, false)
                && com.lijinmu.arashi.notifications.NotificationPreferences.enabled(this, com.lijinmu.arashi.notifications.NotificationPreferences.TCP)
                || getSharedPreferences(com.lijinmu.arashi.uninstall.UninstallConfig.PREF_NAME, MODE_PRIVATE)
                .getBoolean(com.lijinmu.arashi.uninstall.UninstallConfig.ENABLED, false)
                && com.lijinmu.arashi.notifications.NotificationPreferences.uninstallEnabled(this)) {
            com.lijinmu.arashi.notifications.AppNotifications.requestPermissionIfNeeded(this);
        }
        if (verifySignatureOnLaunch) {
            verifySignatureOnLaunch = false;
            if (SignatureVerifier.isMismatched(this)) {
                MiuixPages.showSignatureWarning(this);
            }
        }
    }

    private void updateModuleStatus() {
        if (lsposedState == null) return;
        int boot;
        try {
            boot = Settings.Global.getInt(getContentResolver(), "boot_count", -1);
        } catch (RuntimeException ignored) {
            boot = -1;
        }
        int reportedBoot = getSharedPreferences(SettingsProvider.STATUS_PREF_NAME, MODE_PRIVATE)
                .getInt(SettingsProvider.KEY_SYSTEM_SERVER_BOOT, -1);
        if (boot >= 0 && reportedBoot == boot) {
            lsposedState.setText("已激活");
            lsposedState.setTextColor(Ui.STATUS_GREEN);
        } else {
            lsposedState.setText("未激活");
            lsposedState.setTextColor(Ui.STATUS_RED);
        }
    }

    private TextView status(LinearLayout parent, String name, String value) {
        LinearLayout line = Ui.row(this);
        LinearLayout.LayoutParams rp = Ui.lp(-1, -2);
        rp.topMargin = Ui.dp(this, 15);
        parent.addView(line, rp);
        TextView title = Ui.label(this, name, 15, Ui.text(this), true);
        line.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView state = Ui.label(this, value, 12, Ui.secondary(this), false);
        state.setGravity(Gravity.END);
        line.addView(state);
        return state;
    }

public boolean isModuleActive() {

return false;
    }
}
