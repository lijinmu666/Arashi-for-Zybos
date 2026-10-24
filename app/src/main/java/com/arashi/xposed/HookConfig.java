package com.arashi.xposed;


public final class HookConfig {
    private HookConfig() {}

    public static final String PREF_NAME = "hook_settings";
    
    
    public static final String KEY_EXTERNAL_INSTALL = "hook_external_install";
    public static final String KEY_SHELL_INSTALL = "hook_shell_install";
    public static final String KEY_CLEAR_BLACKLIST = "hook_clear_blacklist";
    
    
    public static final String KEY_CAPTIVE_PORTAL = "hook_captive_portal";
    public static final String KEY_HIDE_ADB = "hook_hide_adb";
}
