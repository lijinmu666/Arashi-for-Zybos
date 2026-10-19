package com.arashi.tcp;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

/** Called by Arashi's provider only after an authenticated blocked-event record is saved. */
public final class TcpBlockNotifications {
    private static final String CHANNEL = "tcp_login_fail_blocked";
    private static final String PERMISSION_PREFS = "tcp_notifications";
    private TcpBlockNotifications() {}

    public static void post(Context context, long recordId, String code) {
        // Notification failure must not affect recording or release a blocked broadcast.
        try {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager == null) return;
            manager.createNotificationChannel(new NotificationChannel(
                    CHANNEL, "TCP 拦截通知", NotificationManager.IMPORTANCE_DEFAULT));
            if (!manager.areNotificationsEnabled()) return;
            String errorCode = code == null || code.trim().isEmpty() ? "未知错误码" : code.trim();
            if (errorCode.length() > 128) errorCode = errorCode.substring(0, 128);
            String text = "ZpTcpService 试图发送 login.fail (" + errorCode
                    + ") 广播，已被阻止。点按查看详情信息";
            Intent intent = new Intent(context, TcpHistoryActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent openHistory = PendingIntent.getActivity(context, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification notification = new Notification.Builder(context, CHANNEL)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("TCP 广播已拦截")
                    .setContentText(text)
                    .setStyle(new Notification.BigTextStyle().bigText(text))
                    .setContentIntent(openHistory)
                    .setAutoCancel(true)
                    .setVisibility(Notification.VISIBILITY_PRIVATE)
                    .build();
            // Use the persistent record ID so distinct interceptions never overwrite each other.
            manager.notify("tcp-block-" + recordId, 0, notification);
        } catch (RuntimeException error) {
            Log.w("[Arashi-TCP]", "Blocked-event notification unavailable", error);
        }
    }

    public static void requestPermissionIfNeeded(Activity activity) {
        if (Build.VERSION.SDK_INT < 33 || activity.checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return;
        android.content.SharedPreferences prefs = activity.getSharedPreferences(PERMISSION_PREFS, Context.MODE_PRIVATE);
        if (prefs.getBoolean("requested", false)) return;
        prefs.edit().putBoolean("requested", true).apply();
        activity.requestPermissions(new String[] { Manifest.permission.POST_NOTIFICATIONS }, 7301);
    }

    public static void openSettings(Activity activity) {
        activity.startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, activity.getPackageName()));
    }
}
