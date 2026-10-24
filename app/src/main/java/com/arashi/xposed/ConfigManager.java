package com.arashi.xposed;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;

import io.github.libxposed.api.XposedModule;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ConfigManager {
    private static final long CACHE_TTL_MS = 2000;
    private static final long RETRY_DELAY_MS = 5000;
    private static final long JOIN_TIMEOUT_MS = 100;

    private static volatile Map<String, Boolean> cache;
    private static volatile long cacheTime = 0;
    private static volatile long failureTime = -5000;
    private static volatile Thread refreshWorker;
    private static final AtomicBoolean REFRESH_RUNNING = new AtomicBoolean(false);
    private static final AtomicBoolean POLLING_STARTED = new AtomicBoolean(false);
    private static volatile boolean isSystemServer = false;
    private static volatile Map<String, Boolean> remoteCache;
    private static SharedPreferences remotePreferences;
    private static SharedPreferences.OnSharedPreferenceChangeListener remoteListener;

    public static synchronized void attach(XposedModule module, boolean systemServer) {
        isSystemServer = isSystemServer || systemServer;
        if (remotePreferences != null) return;
        try {
            remotePreferences = module.getRemotePreferences(HookConfig.PREF_NAME);
            remoteListener = (prefs, key) -> refreshRemote(prefs);
            remotePreferences.registerOnSharedPreferenceChangeListener(remoteListener);
            refreshRemote(remotePreferences);
        } catch (RuntimeException error) {
            remotePreferences = null;
            Log.w("[Arashi-Settings]", "Remote settings unavailable, using provider", error);
        }
    }

    private static void refreshRemote(SharedPreferences prefs) {
        try {
            Map<String, ?> values = prefs.getAll();
            if (!Boolean.TRUE.equals(values.get(HookConfig.KEY_REMOTE_READY))) {
                remoteCache = null;
                return;
            }
            Map<String, Boolean> fresh = new HashMap<>();
            for (String key : HookConfig.keys()) {
                Object value = values.get(key);
                fresh.put(key, value instanceof Boolean
                        ? (Boolean) value : HookConfig.defaultEnabled(key));
            }
            remoteCache = fresh;
        } catch (RuntimeException error) {
            remoteCache = null;
            Log.w("[Arashi-Settings]", "Remote settings refresh failed", error);
        }
    }

    public static void init(String packageName) {
        isSystemServer = isSystemServer || "android".equals(packageName);
        if (cache == null) {
            requestRefresh();
        }
        startPolling();
    }

    private static void startPolling() {
        if (!POLLING_STARTED.compareAndSet(false, true)) return;
        Thread worker = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(CACHE_TTL_MS);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (remoteCache == null || cache == null) requestRefresh();
            }
        }, "Arashi-SettingsPoll");
        worker.setDaemon(true);
        worker.start();
    }

public static boolean isEnabled(String key) {
        return isEnabled(key, HookConfig.defaultEnabled(key));
    }

public static boolean isEnabled(String key, boolean fallback) {
        Boolean value;
        Map<String, Boolean> remote = remoteCache;
        if (remote != null && (value = remote.get(key)) != null) return value;
        Map<String, Boolean> cached = getCurrentSettings();
        if (cached != null && (value = cached.get(key)) != null) {
            return value;
        }
        return fallback;
    }

    private static Map<String, Boolean> getCurrentSettings() {
        long now = SystemClock.elapsedRealtime();
        Map<String, Boolean> cached = cache;
        boolean stale = cached == null || now - cacheTime >= CACHE_TTL_MS;

        if (stale) {
            requestRefresh();

if (!isSystemServer && cached == null) {
                Thread worker = refreshWorker;
                if (worker != null && worker.isAlive()) {
                    try {
                        worker.join(JOIN_TIMEOUT_MS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return cache;
                }
            }
            return cached;
        }
        return cached;
    }

    private static void requestRefresh() {
        long now = SystemClock.elapsedRealtime();
        if (now - failureTime < RETRY_DELAY_MS || !REFRESH_RUNNING.compareAndSet(false, true)) {
            return;
        }

        Thread worker = new Thread(() -> {
            try {
                Map<String, Boolean> fresh = querySettingsProvider();
                long done = SystemClock.elapsedRealtime();
                if (fresh != null) {
                    cache = fresh;
                    cacheTime = done;
                } else {
                    failureTime = done;
                }
            } finally {
                REFRESH_RUNNING.set(false);
            }
        }, "Arashi-Settings");
        worker.setDaemon(true);
        refreshWorker = worker;
        worker.start();
    }

    private static Map<String, Boolean> querySettingsProvider() {
        try {
            Context context = getContext();
            if (context == null) {
                return null;
            }

            Bundle bundle = callProvider(context.getContentResolver());
            if (bundle == null || bundle.isEmpty()) {
                return null;
            }

            Map<String, Boolean> settings = new HashMap<>();
            for (String key : bundle.keySet()) {
                settings.put(key, bundle.getBoolean(key, false));
            }
            return settings;
        } catch (Throwable th) {
            Log.w("[Arashi-Settings]", "Provider settings read failed", th);
            return null;
        }
    }

    private static Bundle callProvider(ContentResolver resolver) {
        try {
            Bundle result = resolver.call(SettingsProvider.URI,
                    SettingsProvider.METHOD_GET_ALL, isSystemServer ? "system_server" : null, null);
            if (result != null) {
                return result;
            }
        } catch (Throwable ignored) {
        }
        try {
            Bundle result = resolver.call(
                    android.net.Uri.parse("content://" + SettingsProvider.AUTHORITY),
                    SettingsProvider.METHOD_GET_ALL, isSystemServer ? "system_server" : null, null);
            if (result != null) {
                return result;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Context getContext() {
        try {
            Class<?> atClass = Reflect.findClass("android.app.ActivityThread", null);
            Object app = Reflect.callStaticMethod(atClass, "currentApplication");
            if (app instanceof Context) {
                return (Context) app;
            }

            Object thread = Reflect.callStaticMethod(atClass, "currentActivityThread");
            if (thread != null) {
                Object ctx = Reflect.callMethod(thread, "getSystemContext");
                if (ctx instanceof Context) {
                    return (Context) ctx;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
