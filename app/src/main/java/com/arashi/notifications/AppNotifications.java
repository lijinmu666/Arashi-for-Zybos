package com.arashi.notifications;

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

/** Posts saved event notifications as Arashi, never from a hooked process. */
public final class AppNotifications {
    private static final String PERMISSION_PREFS = "tcp_notifications";
    private AppNotifications() {}

    public static void post(Context context, String channel, String channelName,
            String tag, String title, String text, Class<?> history) {
        try {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager == null) return;
            manager.createNotificationChannel(new NotificationChannel(
                    channel, channelName, NotificationManager.IMPORTANCE_DEFAULT));
            if (!manager.areNotificationsEnabled()) return;
            Intent intent = new Intent(context, history)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent openHistory = PendingIntent.getActivity(context, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification notification = new Notification.Builder(context, channel)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setStyle(new Notification.BigTextStyle().bigText(text))
                    .setContentIntent(openHistory)
                    .setAutoCancel(true)
                    .setVisibility(Notification.VISIBILITY_PRIVATE)
                    .build();
            // Use the persistent record ID so distinct interceptions never overwrite each other.
            manager.notify(tag, 0, notification);
        } catch (RuntimeException error) {
            Log.w("[Arashi-Notifications]", "Event notification unavailable", error);
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
