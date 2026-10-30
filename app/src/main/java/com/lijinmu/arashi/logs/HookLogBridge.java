package com.lijinmu.arashi.logs;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Process;
import android.util.Log;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Host processes queue small batches; only Arashi's provider touches its private database. */
public final class HookLogBridge {
    public static final String METHOD = "append_runtime_logs";
    private static final ArrayDeque<Bundle> pending = new ArrayDeque<>();
    private static String process = "unknown";
    private static boolean started;

    private HookLogBridge() {}

    public static synchronized void start(String processName) {
        process = processName == null ? "unknown" : processName;
        if (started) return;
        started = true;
        Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread worker = new Thread(runnable, "Arashi-HookLogs");
            worker.setDaemon(true);
            return worker;
        }).scheduleWithFixedDelay(HookLogBridge::flush, 2, 5, TimeUnit.SECONDS);
    }

    public static void info(String message) { append("INFO", message); }
    public static void error(String message, Throwable error) {
        append("ERROR", message + "\n" + Log.getStackTraceString(error));
    }

    private static synchronized void append(String level, String message) {
        Bundle entry = new Bundle();
        entry.putLong("time", System.currentTimeMillis());
        entry.putString("level", level);
        entry.putString("tag", "Hook");
        entry.putString("process", process);
        entry.putInt("pid", Process.myPid());
        entry.putString("message", message.length() <= 12000 ? message : message.substring(0, 12000));
        if (pending.size() >= 128) pending.removeFirst();
        pending.addLast(entry);
    }

    private static void flush() {
        ArrayList<Bundle> batch = new ArrayList<>();
        boolean delivered = false;
        try {
            synchronized (HookLogBridge.class) {
                long cutoff = System.currentTimeMillis() - RuntimeLogStore.RETENTION_MILLIS;
                pending.removeIf(entry -> entry.getLong("time", 0) <= cutoff);
                while (!pending.isEmpty() && batch.size() < 16) batch.add(pending.removeFirst());
            }
            if (batch.isEmpty()) return;
            Context context = context();
            Bundle result = null;
            if (context != null) {
                Bundle payload = new Bundle();
                payload.putParcelableArrayList("entries", batch);
                result = context.getContentResolver().call(
                        Uri.parse("content://com.lijinmu.arashi.settings"), METHOD, null, payload);
            }
            delivered = result != null;
        } catch (Throwable ignored) {
            // Never throw into system_server or recursively send a logger failure.
        } finally {
            if (!delivered) {
                synchronized (HookLogBridge.class) {
                    // Prefer fresh records if the provider remains unavailable.
                    for (int i = batch.size() - 1; i >= 0 && pending.size() < 128; i--)
                        pending.addFirst(batch.get(i));
                }
            }
        }
    }

    private static Context context() {
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Object application = activityThread.getMethod("currentApplication").invoke(null);
            if (application instanceof Context) return (Context) application;
            Object thread = activityThread.getMethod("currentActivityThread").invoke(null);
            if (thread != null) {
                Object context = activityThread.getMethod("getSystemContext").invoke(thread);
                if (context instanceof Context) return (Context) context;
            }
        } catch (Throwable ignored) {}
        return null;
    }
}
