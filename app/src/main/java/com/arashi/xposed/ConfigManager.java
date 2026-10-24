package com.arashi.xposed;

import android.content.ContentResolver;
import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;






public final class ConfigManager {
    private static final long CACHE_TTL_MS = 2000;
    private static final long RETRY_DELAY_MS = 5000;
    private static final long JOIN_TIMEOUT_MS = 300;

    private static volatile Map<String, Boolean> cache;
    private static volatile long cacheTime = 0;
    private static volatile long failureTime = -5000;
    private static volatile Thread refreshWorker;
    private static final AtomicBoolean REFRESH_RUNNING = new AtomicBoolean(false);
    private static volatile boolean isSystemServer = false;

    public static void init(String packageName) {
        isSystemServer = "android".equals(packageName);
        if (cache == null) {
            requestRefresh();
        }
    }

    public static boolean isEnabled(String key) {
        Boolean value;
        Map<String, Boolean> cached = getCurrentSettings();
        if (cached != null && (value = cached.get(key)) != null) {
            return value;
        }
        return false;
    }

    private static Map<String, Boolean> getCurrentSettings() {
        long now = SystemClock.elapsedRealtime();
        Map<String, Boolean> cached = cache;
        boolean stale = cached == null || now - cacheTime >= CACHE_TTL_MS;

        if (stale) {
            requestRefresh();
            if (!isSystemServer) {
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
            return null;
        }
    }

    private static Bundle callProvider(ContentResolver resolver) {
        try {
            Bundle result = resolver.call(SettingsProvider.URI,
                    SettingsProvider.METHOD_GET_ALL, null, null);
            if (result != null) {
                return result;
            }
        } catch (Throwable ignored) {
        }
        try {
            Bundle result = resolver.call(
                    android.net.Uri.parse("content://" + SettingsProvider.AUTHORITY),
                    SettingsProvider.METHOD_GET_ALL, null, null);
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
