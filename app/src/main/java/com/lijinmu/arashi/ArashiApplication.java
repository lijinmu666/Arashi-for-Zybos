package com.lijinmu.arashi;

import android.app.Application;
import android.content.SharedPreferences;
import com.lijinmu.arashi.logs.RuntimeLogger;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class ArashiApplication extends Application {
    private final List<SharedPreferences> preferences = new ArrayList<>();
    private final List<SharedPreferences.OnSharedPreferenceChangeListener> listeners = new ArrayList<>();

    @Override public void onCreate() {
        super.onCreate();
        RuntimeLogger.initialize(this);
        RuntimeLogger.info("Application", "进程启动，版本 " + BuildConfig.VERSION_NAME
                + "，Android " + android.os.Build.VERSION.RELEASE + "，设备 " + android.os.Build.MODEL);
        Thread.UncaughtExceptionHandler original = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try { RuntimeLogger.crash(thread, error); }
            finally {
                if (original != null) original.uncaughtException(thread, error);
                else {
                    android.os.Process.killProcess(android.os.Process.myPid());
                    System.exit(10);
                }
            }
        });
        observe("ui");
        observe(com.lijinmu.arashi.xposed.HookConfig.PREF_NAME);
        observe(com.lijinmu.arashi.uninstall.UninstallConfig.PREF_NAME);
        observe(com.lijinmu.arashi.notifications.NotificationPreferences.PREFS);
    }

    private void observe(String name) {
        SharedPreferences prefs = getSharedPreferences(name, MODE_PRIVATE);
        SharedPreferences.OnSharedPreferenceChangeListener listener = (changed, key) -> {
            if (key == null) {
                RuntimeLogger.info("Settings", name + "：已重置设置");
                return;
            }
            Object value = changed.getAll().get(key);
            // Do not copy package lists, free-form text, credentials or other input into logs.
            String state = value instanceof Boolean || value instanceof Number ? String.valueOf(value)
                    : value instanceof Set<?> ? "已选择 " + ((Set<?>) value).size() + " 项" : "已更新";
            RuntimeLogger.info("Settings", name + "/" + key + " = " + state);
        };
        prefs.registerOnSharedPreferenceChangeListener(listener);
        preferences.add(prefs);
        listeners.add(listener);
    }
}
