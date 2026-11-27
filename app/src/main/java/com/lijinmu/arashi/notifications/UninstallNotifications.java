package com.lijinmu.arashi.notifications;

import android.content.Context;
import android.os.Bundle;
import com.lijinmu.arashi.uninstall.UninstallConfig;
import com.lijinmu.arashi.uninstall.UninstallHistoryActivity;
import com.lijinmu.arashi.uninstall.UninstallHistoryStore;

public final class UninstallNotifications {
    private UninstallNotifications() {}
    public static void post(Context context, long recordId, Bundle data) {
        String key = UninstallNoticePolicy.key(data.getString(UninstallHistoryStore.REASON),
                data.getBoolean(UninstallHistoryStore.ALLOWED, false));
        if (key == null || !NotificationPreferences.enabled(context, key)) return;
        String caller = data.getString(UninstallHistoryStore.CALLER_NAME);
        if (caller == null || caller.trim().isEmpty()) caller = data.getString(UninstallHistoryStore.CALLER_PACKAGE);
        String action = UninstallConfig.operationLabel(data.getString(UninstallConfig.OPERATION));
        String text = UninstallNoticePolicy.text(key, caller,
                data.getInt(UninstallHistoryStore.CALLER_UID, -1), action);
        String title = "包管理服务 · " + (
                key.equals(NotificationPreferences.BLOCKED_TARGET) ? "保护名单拒绝" :
                key.equals(NotificationPreferences.WHITELIST_ONLY_DENIED) ? "非白名单拒绝" :
                key.equals(NotificationPreferences.BLACKLIST) ? "黑名单拒绝" :
                key.equals(NotificationPreferences.WHITELIST) ? "白名单放行" :
                key.equals(NotificationPreferences.SCREEN_OFF) ? "息屏自动拒绝" : "验证超时拒绝");
        AppNotifications.post(context, key, title,
                "uninstall-" + recordId, "包管理服务", text, UninstallHistoryActivity.class);
    }
}
