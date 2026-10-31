package com.lijinmu.arashi.xposed;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;

import io.github.libxposed.api.XposedModule;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ConfigManager {
    private static final long CACHE_TTL_MS = 2000;
    private static final long RETRY_DELAY_MS = 5000;
    private static final long JOIN_TIMEOUT_MS = 100;

    private static volatile Snapshot cache;
    private static volatile long cacheTime = 0;
    private static volatile long failureTime = -5000;
    private static volatile Thread refreshWorker;
    private static final AtomicBoolean REFRESH_RUNNING = new AtomicBoolean(false);
    private static final AtomicBoolean POLLING_STARTED = new AtomicBoolean(false);
    private static volatile boolean isSystemServer = false;
    private static volatile Snapshot remoteCache;
    private static SharedPreferences remotePreferences;
    private static SharedPreferences.OnSharedPreferenceChangeListener remoteListener;

    private static final class Snapshot {
        final Map<String, Boolean> enabled;
        final Set<String> backgroundWhitelist;

        Snapshot(Map<String, Boolean> enabled, Set<String> backgroundWhitelist) {
            this.enabled = Collections.unmodifiableMap(new HashMap<>(enabled));
            this.backgroundWhitelist = Collections.unmodifiableSet(new HashSet<>(backgroundWhitelist));
        }
    }

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
                fresh.put(key, HookConfig.KEY_AOSP_STATUS_ICONS.equals(key)
                        ? HookConfig.statusBarIconsEnabled(values) : value instanceof Boolean
                        ? (Boolean) value : HookConfig.defaultEnabled(key));
            }
            Set<String> selected = new HashSet<>();
            Object stored = values.get(HookConfig.KEY_KEEP_BACKGROUND_WHITELIST);
            if (stored instanceof Set<?>) {
                for (Object item : (Set<?>) stored) {
                    if (item instanceof String) selected.add((String) item);
                }
            }
            remoteCache = new Snapshot(fresh, selected);
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

/** Hot UI paths only read snapshots; init/polling and the remote listener refresh them. */
    public static boolean hasSettingsSnapshot() {
        return remoteCache != null || cache != null;
    }

    public static boolean isEnabledCached(String key) {
        Boolean value;
        Snapshot remote = remoteCache;
        if (remote != null && (value = remote.enabled.get(key)) != null) return value;
        Snapshot local = cache;
        if (local != null && (value = local.enabled.get(key)) != null) return value;
        return HookConfig.defaultEnabled(key);
    }

public static boolean isEnabled(String key) {
        return isEnabled(key, HookConfig.defaultEnabled(key));
    }

public static boolean isEnabled(String key, boolean fallback) {
        Boolean value;
        Snapshot remote = remoteCache;
        if (remote != null && (value = remote.enabled.get(key)) != null) return value;
        Snapshot cached = getCurrentSettings();
        if (cached != null && (value = cached.enabled.get(key)) != null) {
            return value;
        }
        return fallback;
    }

    public static boolean shouldKeepBackground(String packageName) {
        Snapshot settings = remoteCache;
        if (settings == null) settings = getCurrentSettings();
        return settings != null && BackgroundPolicy.shouldKeep(
                Boolean.TRUE.equals(settings.enabled.get(HookConfig.KEY_KEEP_BACKGROUND)),
                settings.backgroundWhitelist, packageName);
    }

    private static Snapshot getCurrentSettings() {
        long now = SystemClock.elapsedRealtime();
        Snapshot cached = cache;
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
                Snapshot fresh = querySettingsProvider();
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

    private static Snapshot querySettingsProvider() {
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
            for (String key : HookConfig.keys()) {
                settings.put(key, bundle.getBoolean(key, false));
            }
            java.util.ArrayList<String> selected = bundle.getStringArrayList(
                    HookConfig.KEY_KEEP_BACKGROUND_WHITELIST);
            return new Snapshot(settings, selected == null
                    ? Collections.emptySet() : new HashSet<>(selected));
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
