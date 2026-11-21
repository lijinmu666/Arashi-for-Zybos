package com.lijinmu.arashi.tools;

import androidx.activity.ComponentActivity;

import android.content.Context;
import android.content.pm.PackageManager;
import android.provider.Settings;
import java.util.function.Supplier;

import android.os.Handler;
import android.os.Looper;

/** Command operations used by Settings Editor and Device Control. */
public final class QuickCommandController {
    @FunctionalInterface
    public interface Completion {
        void onComplete(boolean success, String output);
    }

    private final ComponentActivity activity;
    private final Handler ui = new Handler(Looper.getMainLooper());

    public QuickCommandController(ComponentActivity activity) {
        this.activity = activity;
    }

    public static boolean canWriteSecureSettings(Context context) {
        return context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS)
                == PackageManager.PERMISSION_GRANTED;
    }

    public void writeSettingsToggle(String title, String key, boolean system, boolean enabled, Completion completion) {
        if (!key.matches("[a-z0-9_]+")) throw new IllegalArgumentException("Invalid settings key");
        WorkMode mode = WorkMode.get(activity);
        int value = enabled ? 1 : 0;
        String table = system ? "system" : "global";
        String command = "settings put " + table + " " + key + " " + value
                + " && [ \"$(settings get " + table + " " + key + ")\" = '" + value + "' ]";
        executeAsync(title, () -> {
            WorkMode alternate = mode == WorkMode.ROOT ? WorkMode.SHELL : WorkMode.ROOT;
            RootShell.Result availability = mode.execute(activity, "id -u");
            if (isModeAvailable(mode, availability)) {
                return mode.execute(activity, command);
            }
            RootShell.Result alternateAvailability = alternate.execute(activity, "id -u");
            if (isModeAvailable(alternate, alternateAvailability)) {
                return alternate.execute(activity, command);
            }
            return writeSettingsWithPermission(key, system, value);
        }, completion);
    }

    private static boolean isModeAvailable(WorkMode mode, RootShell.Result probe) {
        String uid = probe.output.trim();
        return probe.ok && ("0".equals(uid) || (mode == WorkMode.SHELL && "2000".equals(uid)));
    }

    private RootShell.Result writeSettingsWithPermission(String key, boolean system, int value) {
        try {
            boolean canWriteSecure = canWriteSecureSettings(activity);
            if (!canWriteSecure && !(system && Settings.System.canWrite(activity))) {
                String permission = system ? "修改系统设置或写入安全设置" : "写入安全设置";
                return new RootShell.Result(false, "Shell 和 Root 均不可用。请在工作模式的备用权限中授予" + permission + "权限");
            }
            var resolver = activity.getContentResolver();
            boolean written = system ? Settings.System.putInt(resolver, key, value)
                    : Settings.Global.putInt(resolver, key, value);
            if (!written) {
                return new RootShell.Result(false, "系统未接受写入 " + key);
            }
            String actual;
            try {
                actual = system ? Settings.System.getString(resolver, key)
                        : Settings.Global.getString(resolver, key);
            } catch (SecurityException error) {
                return new RootShell.Result(true, "");
            } catch (RuntimeException error) {
                return new RootShell.Result(false, key + " 已写入，回读出错：" + error);
            }
            if (Integer.toString(value).equals(actual)) {
                return new RootShell.Result(true, "");
            }
            return new RootShell.Result(false, key + " 回读不一致，预期 " + value
                    + "，实际 " + (actual == null ? "未读取到值" : actual));
        } catch (RuntimeException error) {
            return new RootShell.Result(false, "写入 " + key + " 时出错：" + error);
        }
    }

    public void run(String title, String command, Completion completion) {
        WorkMode mode = WorkMode.get(activity);
        executeAsync(title, () -> mode.execute(activity, command), completion);
    }

    private void executeAsync(String title, Supplier<RootShell.Result> operation, Completion completion) {
        com.lijinmu.arashi.logs.RuntimeLogger.info("QuickCommands", "执行：" + title);
        new Thread(() -> {
            RootShell.Result result = operation.get();
            com.lijinmu.arashi.logs.RuntimeLogger.info("QuickCommands", title + (result.ok ? "：成功" : "：失败"));
            ui.post(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                completion.onComplete(result.ok, result.output);
            });
        }, "Arashi-Command").start();
    }
}
