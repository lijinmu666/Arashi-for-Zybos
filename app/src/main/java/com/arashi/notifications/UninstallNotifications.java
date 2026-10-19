package com.arashi.notifications;

import android.content.Context;
import android.os.Bundle;
import com.arashi.uninstall.UninstallHistoryActivity;
import com.arashi.uninstall.UninstallHistoryStore;

public final class UninstallNotifications {
    private UninstallNotifications() {}
    public static void post(Context context, long recordId, Bundle data) {
        String key = UninstallNoticePolicy.key(data.getString(UninstallHistoryStore.REASON),
                data.getBoolean(UninstallHistoryStore.ALLOWED, false));
        if (key == null || !NotificationPreferences.enabled(context, key)) return;
        String caller = data.getString(UninstallHistoryStore.CALLER_NAME);
        if (caller == null || caller.trim().isEmpty()) caller = data.getString(UninstallHistoryStore.CALLER_PACKAGE);
        String text = UninstallNoticePolicy.text(key, caller, data.getInt(UninstallHistoryStore.CALLER_UID, -1));
        AppNotifications.post(context, key, "卸载验证 · " + (
                key.equals(NotificationPreferences.BLACKLIST) ? "黑名单拒绝" :
                key.equals(NotificationPreferences.WHITELIST) ? "白名单放行" : "倒计时拒绝"),
                "uninstall-" + recordId, "卸载验证", text, UninstallHistoryActivity.class);
    }
}
