package com.lijinmu.arashi.logs;

import android.app.Application;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
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
            try {
                RuntimeLogStore.prune(application);
            }
            catch (RuntimeException ignored) { /* Logging cannot break app startup. */ }
            finally { updateCleanupJob(application); }
        });
    }

    public static void setEnabled(Context context, boolean enabled) {
        try {
            RuntimeLogStore.setEnabled(context, enabled);
        } finally {
            if (!RuntimeLogStore.isEnabled(context)) WORKER.getQueue().clear();
            updateCleanupJob(context);
        }
    }

    private static void updateCleanupJob(Context context) {
        try {
            JobScheduler scheduler = context.getSystemService(JobScheduler.class);
            if (scheduler == null) return;
            if (!RuntimeLogStore.isEnabled(context)) {
                scheduler.cancel(LogCleanupJobService.JOB_ID);
            } else if (scheduler.getPendingJob(LogCleanupJobService.JOB_ID) == null) {
                scheduler.schedule(new JobInfo.Builder(LogCleanupJobService.JOB_ID,
                        new ComponentName(context, LogCleanupJobService.class))
                        .setPeriodic(15L * 60 * 1000).setPersisted(true).build());
            }
        } catch (RuntimeException error) {
            error("Logs", "后台日志清理任务更新失败", error);
        }
    }

    public static void info(String tag, String message) { record("INFO", tag, message, null); }
    public static void warn(String tag, String message) { record("WARN", tag, message, null); }
    public static void error(String tag, String message, Throwable error) { record("ERROR", tag, message, error); }

    public static void record(String level, String tag, String message, Throwable error) {
        Context context = application;
        if (context == null || !RuntimeLogStore.isEnabled(context)) return;
        Bundle data = entry(level, tag, message, error);
        WORKER.execute(() -> {
            try { RuntimeLogStore.append(context, data); }
            catch (RuntimeException ignored) { /* Do not recursively log storage failures. */ }
        });
    }

    public static void crash(Thread thread, Throwable error) {
        Context context = application;
        if (context == null || !RuntimeLogStore.isEnabled(context)) return;
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
