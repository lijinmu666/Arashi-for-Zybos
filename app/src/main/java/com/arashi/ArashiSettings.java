package com.arashi;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

public final class ArashiSettings extends BasePage {
    @Override protected void populate() {
        toolbar("应用偏好设置");
        Ui.section(this, content, "外观");
        LinearLayout theme = Ui.card(this, content, Ui.surface(this));
        theme.addView(Ui.label(this, "配色设置", 16, Ui.text(this), true));
        int value = getSharedPreferences("ui", Activity.MODE_PRIVATE).getInt("mode", 0);
        String[] modes = {"系统", "浅色", "深色"};
        TextView chosen = Ui.label(this, modes[Math.max(0, Math.min(value, 2))], 15, Ui.accent(this), true);
        LinearLayout.LayoutParams cp = Ui.lp(-1, -2);
        cp.topMargin = Ui.dp(this, 12);
        theme.addView(chosen, cp);
        theme.setClickable(true);
        theme.setFocusable(true);
        Ui.touch(this, theme, Ui.surface(this), 24);
        theme.setOnClickListener(v -> chooseTheme(modes));

        Ui.section(this, content, "其他");
        launcherSwitch();
        installerSwitch();
        Ui.link(this, content, "关于 Arashi", "有关 Arashi 的更多详细信息页",
                () -> startActivity(new android.content.Intent(this, About.class)));
    }

    private void launcherSwitch() {
        LinearLayout card = Ui.card(this, content, Ui.surface(this));
        LinearLayout row = Ui.row(this);
        card.addView(row, Ui.lp(-1, -2));
        LinearLayout words = Ui.column(this);
        row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        words.addView(Ui.label(this, "隐藏桌面图标", 16, Ui.text(this), true));
        TextView note = Ui.label(this, "隐藏后可从 LSPosed 管理器打开模块设置", 13,
                Ui.secondary(this), false);
        LinearLayout.LayoutParams np = Ui.lp(-1, -2);
        np.topMargin = Ui.dp(this, 5);
        words.addView(note, np);

        ComponentName launcher = new ComponentName(this, "com.arashi.LauncherAlias");
        PackageManager pm = getPackageManager();
        ModernSwitch toggle = new ModernSwitch(this);
        toggle.setContentDescription("隐藏桌面图标");
        toggle.setChecked(pm.getComponentEnabledSetting(launcher)
                == PackageManager.COMPONENT_ENABLED_STATE_DISABLED);
        row.addView(toggle);
        toggle.setOnCheckedChangeListener(hidden -> {
            try {
                pm.setComponentEnabledSetting(launcher,
                        hidden ? PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                                : PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP);
            } catch (RuntimeException e) {
                toggle.setChecked(!hidden);
                Ui.dialog(this, "切换失败", "无法更改桌面图标状态：" + e.getMessage(), "知道了");
            }
        });
    }

    private void installerSwitch() {
        LinearLayout card = Ui.card(this, content, Ui.surface(this));
        LinearLayout row = Ui.row(this);
        card.addView(row, Ui.lp(-1, -2));
        LinearLayout words = Ui.column(this);
        row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        words.addView(Ui.label(this, "启用 Arashi 安装器", 16, Ui.text(this), true));
        TextView note = Ui.label(this, "启用后可在打开 APK 时选择 Arashi", 13,
                Ui.secondary(this), false);
        LinearLayout.LayoutParams np = Ui.lp(-1, -2);
        np.topMargin = Ui.dp(this, 5);
        words.addView(note, np);

        ComponentName installer = new ComponentName(this, ApkInstallerActivity.class);
        PackageManager pm = getPackageManager();
        ModernSwitch toggle = new ModernSwitch(this);
        toggle.setContentDescription("启用 Arashi 安装器");
        toggle.setChecked(pm.getComponentEnabledSetting(installer)
                != PackageManager.COMPONENT_ENABLED_STATE_DISABLED);
        row.addView(toggle);
        toggle.setOnCheckedChangeListener(enabled -> {
            try {
                pm.setComponentEnabledSetting(installer,
                        enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                                : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP);
            } catch (RuntimeException e) {
                toggle.setChecked(!enabled);
                Ui.dialog(this, "切换失败", "无法更改安装器状态：" + e.getMessage(), "知道了");
            }
        });
    }

    private void chooseTheme(String[] modes) {
        LinearLayout outer = Ui.column(this);
        outer.setPadding(Ui.dp(this, 22), Ui.dp(this, 22), Ui.dp(this, 22), Ui.dp(this, 20));
        outer.setBackground(Ui.shape(this, Ui.surface(this), 28));
        outer.addView(Ui.label(this, "配色设置", 21, Ui.text(this), true));
        RadioGroup group = new RadioGroup(this);
        int selected = getSharedPreferences("ui", Activity.MODE_PRIVATE).getInt("mode", 0);
        for (int i = 0; i < modes.length; i++) {
            RadioButton radio = new RadioButton(this);
            radio.setId(View.generateViewId());
            radio.setText(modes[i]);
            radio.setTextSize(16);
            radio.setTextColor(Ui.text(this));
            radio.setMinimumHeight(Ui.dp(this, 52));
            radio.setButtonTintList(ColorStateList.valueOf(Ui.accent(this)));
            group.addView(radio);
            if (i == selected) group.check(radio.getId());
        }
        LinearLayout.LayoutParams gp = Ui.lp(-1, -2);
        gp.topMargin = Ui.dp(this, 12);
        outer.addView(group, gp);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(outer).create();
        group.setOnCheckedChangeListener((g, id) -> {
            int index = g.indexOfChild(g.findViewById(id));
            if (index >= 0) {
                getSharedPreferences("ui", Activity.MODE_PRIVATE).edit().putInt("mode", index).apply();
                dialog.dismiss();
                recreate();
            }
        });
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout(getResources().getDisplayMetrics().widthPixels - Ui.dp(this, 48), -2);
        }
    }
}
