package com.arashi;

import android.content.Intent;
import android.app.AlertDialog;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.provider.Settings;
import com.arashi.xposed.SettingsProvider;

public final class Main extends BasePage {
    private TextView lsposedState;

    @Override protected void onResume() {
        super.onResume();
        updateModuleStatus();
    }

    @Override public void onCreate(android.os.Bundle state) {
        super.onCreate(state);
        new Thread(() -> {
            boolean supported = "ZYB".equals(readSystemBrand());
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (supported) {
                    populateHome();
                } else {
                    new AlertDialog.Builder(this)
                            .setMessage("当前设备不支持 Arashi")
                            .setCancelable(false)
                            .setPositiveButton("确定", (dialog, which) -> finish())
                            .show();
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

    private void populateHome() {
        TextView brand = Ui.label(this, "Arashi", 36, Ui.text(this), true);
        content.addView(brand);

        LinearLayout hero = Ui.card(this, content, Ui.soft(this));
        LinearLayout.LayoutParams heroLp = (LinearLayout.LayoutParams) hero.getLayoutParams();
        heroLp.topMargin = Ui.dp(this, 28);
        hero.setLayoutParams(heroLp);
        hero.setPadding(Ui.dp(this, 24), Ui.dp(this, 24), Ui.dp(this, 24), Ui.dp(this, 24));
        TextView version = Ui.label(this, "版本 " + BuildConfig.VERSION_NAME, 13, Ui.secondary(this), true);
        hero.addView(version);
        lsposedState = status(hero, "LSPosed", "检测中");
        updateModuleStatus();
        
        hero.postDelayed(this::updateModuleStatus, 2000);
        hero.postDelayed(this::updateModuleStatus, 6000);
        com.arashi.zybos.ZybosService.detect(this);
        boolean zybosAvailable = com.arashi.zybos.ZybosService.isAvailable();
        TextView zybosState = status(hero, "ZybosSysApiManager", zybosAvailable ? "可用" : "不可用");
        zybosState.setTextColor(zybosAvailable ? Ui.STATUS_GREEN : Ui.STATUS_RED);

        Ui.section(this, content, "功能");
        Ui.link(this, content, "Hook 功能设置", "控制 Hook 是否启用",
                () -> startActivity(new Intent(this, HookSettings.class)));
        Ui.link(this, content, "系统特权工具", "利用 ZybosSysApiManager 执行系统操作",
                () -> startActivity(new Intent(this, ZybosTools.class)));
        Ui.link(this, content, "应用偏好设置", "配置主题、外观等",
                () -> startActivity(new Intent(this, ArashiSettings.class)));
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
            lsposedState.setText("模块已激活（系统进程）");
            lsposedState.setTextColor(Ui.STATUS_GREEN);
        } else if (isModuleActive()) {
            lsposedState.setText("模块已激活（本应用）");
            lsposedState.setTextColor(Ui.STATUS_GREEN);
        } else {
            lsposedState.setText("等待系统进程回报");
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
