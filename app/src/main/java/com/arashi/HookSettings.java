package com.arashi;

import android.content.Context;
import android.content.SharedPreferences;
import com.arashi.xposed.HookConfig;

public final class HookSettings extends BasePage {
    private SharedPreferences prefs;

    @Override
    protected void populate() {
        prefs = getSharedPreferences(HookConfig.PREF_NAME, Context.MODE_PRIVATE);

        toolbar("Hook 功能设置");
        banner("LSPosed", "控制对应的 Hook 是否启用。部分 SystemServer 的 Hook 需要重启生效。第一次使用请在 LSPosed 管理器中启用模块勾选作用域后并重启");
        
        Ui.section(this, content, "安装");
        Ui.realSwitch(this, content, "外部应用安装支持", "允许直接在软件包安装程序中安装或更新应用",
                HookConfig.KEY_EXTERNAL_INSTALL, prefs);
        Ui.realSwitch(this, content, "Shell 安装支持", "允许 ADB 和 pm install 安装应用",
                HookConfig.KEY_SHELL_INSTALL, prefs);
        Ui.realSwitch(this, content, "清除内置黑名单", "允许安装硬编码在 PackageManager 里的 5 个黑名单应用",
                HookConfig.KEY_CLEAR_BLACKLIST, prefs);
        
        Ui.section(this, content, "行为");
        Ui.realSwitch(this, content, "WiFi 登录支持", "允许连接认证型 WiFi",
                HookConfig.KEY_CAPTIVE_PORTAL, prefs);
        Ui.realSwitch(this, content, "隐藏 ADB 状态", "普通应用不会读取到 ADB 已开启",
                HookConfig.KEY_HIDE_ADB, prefs);
        
        Ui.hint(this, content, "开关修改后立即保存");
    }
}
