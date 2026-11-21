package com.lijinmu.arashi;

import com.lijinmu.arashi.security.SignatureVerifier;
import com.lijinmu.arashi.ui.BasePage;
import com.lijinmu.arashi.ui.MiuixPages;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

public final class Main extends BasePage {
    private boolean verifySignatureOnLaunch;

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

public boolean isModuleActive() {

return false;
    }
}
