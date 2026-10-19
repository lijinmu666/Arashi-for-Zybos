package com.lijinmu.arashi.xposed;

public final class HookConfig {
    private HookConfig() {}

    public static final String PREF_NAME = "hook_settings";
    public static final String KEY_BLOCK_TCP_LOGIN_FAIL = "hook_block_tcp_login_fail";
    public static final String KEY_REMOTE_READY = "remote_config_ready";
    public static final String KEY_ANDROID12_BLACKLIST_HOOK = "hook_legacy_aibangos";

public static final boolean DEFAULT_ENABLED = true;

public static final String KEY_EXTERNAL_INSTALL = "hook_external_install";
    public static final String KEY_SHELL_INSTALL = "hook_shell_install";
    public static final String KEY_CLEAR_BLACKLIST = "hook_clear_blacklist";

public static final String KEY_CAPTIVE_PORTAL = "hook_captive_portal";

    public static final String KEY_ALLOW_USB = "hook_allow_usb";
    public static final String KEY_RECORD_INTERNAL_AUDIO = "hook_record_internal_audio";
    public static final String KEY_KEEP_NAVIGATION = "hook_keep_navigation";
    public static final String KEY_STATUS_BAR_DATE = "hook_status_bar_date";
    public static final String KEY_IOS_ICONS = "hook_ios_icons";
    public static final String KEY_ALLOW_WALLPAPER = "hook_allow_wallpaper";
    public static final String KEY_CLEAR_DETAIL_BLACK = "hook_clear_detail_black";

public static final String KEY_BLOCK_SUPERVISE = "hook_block_supervise";
    public static final String KEY_BLOCK_USAGE_REPORT = "hook_block_usage_report";
    public static final String KEY_DEV_OPTIONS = "hook_dev_options";
    public static final String KEY_DEV_OPTIONS_MENU = "hook_dev_options_menu";
    public static final String KEY_KEEP_BACKGROUND = "hook_keep_background";
    public static final String KEY_KEEP_BACKGROUND_WHITELIST = "hook_keep_background_whitelist";

    public static boolean defaultEnabled(String key) {
        return !KEY_BLOCK_TCP_LOGIN_FAIL.equals(key)
                && !KEY_KEEP_NAVIGATION.equals(key)
                && !KEY_STATUS_BAR_DATE.equals(key)
                && !KEY_IOS_ICONS.equals(key)
                && !KEY_ANDROID12_BLACKLIST_HOOK.equals(key)
                && !KEY_DEV_OPTIONS_MENU.equals(key)
                && !KEY_BLOCK_SUPERVISE.equals(key)
                && !KEY_BLOCK_USAGE_REPORT.equals(key)
                && !KEY_NEUTRALIZE_POLICY.equals(key)
                && !KEY_NEUTRALIZE_ADVANCE.equals(key)
                && !KEY_NO_TIMEOUT_LOCK.equals(key)
                && !KEY_KEEP_BACKGROUND.equals(key);
    }

public static final String KEY_NEUTRALIZE_POLICY = "hook_neutralize_policy";
    public static final String KEY_NEUTRALIZE_ADVANCE = "hook_neutralize_advance";
    public static final String KEY_NO_TIMEOUT_LOCK = "hook_no_timeout_lock";
    public static final String KEY_BLOCK_DEFAULT_BROWSER = "hook_block_default_browser";

    public static String[] keys() {
        return new String[] {
                KEY_BLOCK_TCP_LOGIN_FAIL, KEY_ANDROID12_BLACKLIST_HOOK, KEY_EXTERNAL_INSTALL, KEY_SHELL_INSTALL,
                KEY_CLEAR_BLACKLIST, KEY_CAPTIVE_PORTAL,
                KEY_ALLOW_USB,
                KEY_RECORD_INTERNAL_AUDIO, KEY_KEEP_NAVIGATION, KEY_STATUS_BAR_DATE, KEY_IOS_ICONS, KEY_ALLOW_WALLPAPER, KEY_CLEAR_DETAIL_BLACK, KEY_BLOCK_SUPERVISE,
                KEY_BLOCK_USAGE_REPORT,
                KEY_DEV_OPTIONS, KEY_DEV_OPTIONS_MENU, KEY_KEEP_BACKGROUND, KEY_NEUTRALIZE_POLICY,
                KEY_NEUTRALIZE_ADVANCE, KEY_NO_TIMEOUT_LOCK, KEY_BLOCK_DEFAULT_BROWSER
        };
    }
}
