package com.arashi.tcp;

import android.content.Context;
import com.arashi.notifications.AppNotifications;
import com.arashi.notifications.NotificationPreferences;

public final class TcpBlockNotifications {
    private TcpBlockNotifications() {}
    public static void post(Context context, long recordId, String code) {
        if (!NotificationPreferences.enabled(context, NotificationPreferences.TCP)) return;
        String errorCode = code == null || code.trim().isEmpty() ? "未知错误码" : code.trim();
        if (errorCode.length() > 128) errorCode = errorCode.substring(0, 128);
        String text = "ZpTcpService 试图发送 login.fail (" + errorCode
                + ") 广播，已被阻止。点按查看详情信息";
        AppNotifications.post(context, "tcp_login_fail_blocked", "TCP 拦截通知",
                "tcp-block-" + recordId, "TCP 广播已拦截", text, TcpHistoryActivity.class);
    }
}
