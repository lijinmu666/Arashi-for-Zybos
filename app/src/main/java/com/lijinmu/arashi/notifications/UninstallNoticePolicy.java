package com.lijinmu.arashi.notifications;

/** Never infer allowance from a spoofed callback or from an unrelated decision reason. */
public final class UninstallNoticePolicy {
    private UninstallNoticePolicy() {}
    public static String key(String reason, boolean allowed) {
        if (!allowed && ("目标应用禁止卸载".equals(reason) || "目标应用禁止冻结".equals(reason)
                || "目标应用禁止操作".equals(reason))) return NotificationPreferences.BLOCKED_TARGET;
        if (!allowed && "非白名单拒绝".equals(reason)) return NotificationPreferences.WHITELIST_ONLY_DENIED;
        if (!allowed && "黑名单".equals(reason)) return NotificationPreferences.BLACKLIST;
        if (allowed && "白名单".equals(reason)) return NotificationPreferences.WHITELIST;
        if (!allowed && "超时默认拒绝".equals(reason)) return NotificationPreferences.TIMEOUT;
        if (!allowed && "设备息屏".equals(reason)) return NotificationPreferences.SCREEN_OFF;
        return null;
    }
    public static String text(String key, String caller, int uid, String action) {
        if (caller == null || caller.trim().isEmpty()) caller = "未知发起方";
        String cause;
        if (NotificationPreferences.BLOCKED_TARGET.equals(key)) {
            cause = "已由保护名单拒绝";
        } else if (NotificationPreferences.WHITELIST_ONLY_DENIED.equals(key)) {
            cause = "已拒绝，发起方不在白名单中";
        } else if (NotificationPreferences.BLACKLIST.equals(key)) {
            cause = "已由发起方黑名单拒绝";
        } else if (NotificationPreferences.WHITELIST.equals(key)) {
            cause = "已由发起方白名单放行";
        } else if (NotificationPreferences.TIMEOUT.equals(key)) {
            cause = "已因确认超时自动拒绝";
        } else if (NotificationPreferences.SCREEN_OFF.equals(key)) {
            cause = "已因设备息屏自动拒绝";
        } else return null;
        return caller + " (" + uid + ") 请求" + action + "，" + cause + "。点按查看记录";
    }
}
