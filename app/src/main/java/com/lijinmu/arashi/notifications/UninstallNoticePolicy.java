package com.lijinmu.arashi.notifications;

/** Never infer allowance from a spoofed callback or from an unrelated decision reason. */
public final class UninstallNoticePolicy {
    private UninstallNoticePolicy() {}
    public static String key(String reason, boolean allowed) {
        if (!allowed && "目标应用禁止卸载".equals(reason)) return NotificationPreferences.BLOCKED_TARGET;
        if (!allowed && "非白名单拒绝".equals(reason)) return NotificationPreferences.WHITELIST_ONLY_DENIED;
        if (!allowed && "黑名单".equals(reason)) return NotificationPreferences.BLACKLIST;
        if (allowed && "白名单".equals(reason)) return NotificationPreferences.WHITELIST;
        if (!allowed && "超时默认拒绝".equals(reason)) return NotificationPreferences.TIMEOUT;
        if (!allowed && "设备息屏".equals(reason)) return NotificationPreferences.SCREEN_OFF;
        return null;
    }
    public static String text(String key, String caller, int uid) {
        if (caller == null || caller.trim().isEmpty()) caller = "未知发起者";
        String cause;
        if (NotificationPreferences.BLOCKED_TARGET.equals(key)) {
            cause = "由于目标应用已设置为禁止卸载，已拒绝本次卸载";
        } else if (NotificationPreferences.WHITELIST_ONLY_DENIED.equals(key)) {
            cause = "由于已开启仅允许白名单卸载应用，且发起方不在白名单中，已拒绝本次卸载";
        } else if (NotificationPreferences.BLACKLIST.equals(key)) {
            cause = "由于其已被添加至黑名单，已拒绝本次卸载";
        } else if (NotificationPreferences.WHITELIST.equals(key)) {
            cause = "由于其已被添加至白名单，已放行本次卸载";
        } else if (NotificationPreferences.TIMEOUT.equals(key)) {
            cause = "由于你未响应弹窗，倒计时已自动拒绝本次卸载";
        } else if (NotificationPreferences.SCREEN_OFF.equals(key)) {
            cause = "由于设备未使用，已拒绝本次卸载";
        } else return null;
        return caller + "(" + uid + ") 试图卸载应用，" + cause + "。点按查看详情信息";
    }
}
