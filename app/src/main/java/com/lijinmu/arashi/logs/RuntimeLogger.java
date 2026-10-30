package com.lijinmu.arashi.logs;

import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.os.Process;
import android.util.Log;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Logging must never block UI threads or prevent the original operation from completing. */
public final class RuntimeLogger {
    private static volatile Context application;
    private static final ThreadPoolExecutor WORKER = new ThreadPoolExecutor(1, 1, 30,
            TimeUnit.SECONDS, new ArrayBlockingQueue<>(256), runnable -> {
                Thread thread = new Thread(runnable, "Arashi-LogWriter");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.DiscardPolicy());

    private RuntimeLogger() {}

    public static void initialize(Context context) {
        application = context.getApplicationContext();
        WORKER.allowCoreThreadTimeOut(true);
        WORKER.execute(() -> {
            try { RuntimeLogStore.prune(application); }
            catch (RuntimeException ignored) { /* Logging cannot break app startup. */ }
        });
    }

    public static void info(String tag, String message) { record("INFO", tag, message, null); }
    public static void warn(String tag, String message) { record("WARN", tag, message, null); }
    public static void error(String tag, String message, Throwable error) { record("ERROR", tag, message, error); }

    public static void record(String level, String tag, String message, Throwable error) {
        Context context = application;
        if (context == null) return;
        Bundle data = entry(level, tag, message, error);
        WORKER.execute(() -> {
            try { RuntimeLogStore.append(context, data); }
            catch (RuntimeException ignored) { /* Do not recursively log storage failures. */ }
        });
    }

    public static void crash(Thread thread, Throwable error) {
        Context context = application;
        if (context == null) return;
        try {
            // The process may terminate before an asynchronous write gets a chance to run.
            RuntimeLogStore.append(context, entry("ERROR", "Crash", "未捕获异常，线程：" + thread.getName(), error));
        } catch (Throwable ignored) { /* Always let the original crash handler proceed. */ }
    }

    private static Bundle entry(String level, String tag, String message, Throwable error) {
        Bundle data = new Bundle();
        data.putLong("time", System.currentTimeMillis());
        data.putString("level", level);
        data.putString("tag", tag);
        data.putString("process", Application.getProcessName());
        data.putInt("pid", Process.myPid());
        String body = message == null ? "" : message;
        if (error != null) body += "\n" + Log.getStackTraceString(error);
        data.putString("message", body.length() <= 12000 ? body : body.substring(0, 12000));
        return data;
    }
}
