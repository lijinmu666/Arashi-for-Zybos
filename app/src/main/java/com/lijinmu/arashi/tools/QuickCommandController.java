package com.lijinmu.arashi.tools;

import androidx.activity.ComponentActivity;
import com.lijinmu.arashi.tools.QuickCommands.Completion;

import android.os.Handler;
import android.os.Looper;

/** Command operations shared by the navigation page and legacy Activity entry point. */
public final class QuickCommandController {
    private final ComponentActivity activity;
    private final Handler ui = new Handler(Looper.getMainLooper());

    public QuickCommandController(ComponentActivity activity) {
        this.activity = activity;
    }

    private static final String[][] RECOMMENDED_PACKAGES = {
        {"com.zuoyebang.iot.pad.zpreport"}, {"com.zuoyebang.padms", "com.zuoyebang.iot.padms"},
        {"com.bytedance.os.slardar"}, {"com.zuoyebang.iot.zlog"},
        {"com.zuoyebang.iot.pad.launcher14/com.zuoyebang.iot.pad.launcher.ErrorActivity",
         "com.android.launcher3/com.zuoyebang.iot.pad.launcher.ErrorActivity"}
    };
    private static final String[] RECOMMENDED_NAMES = {
        "ZpReport", "PadMs", "SlardarOsClient", "日志服务", "桌面 ErrorActivity"
    };

    private static final String[][] OTA_PACKAGES = {
        {"com.zuoyebang.iot.zp.lib.ota.server"}
    };
    private static final String[] OTA_NAMES = {"系统升级"};

    private int currentUserId(WorkMode mode) {
        RootShell.Result result = mode.execute(activity, "am get-current-user");
        if (result.ok) {
            try { return Integer.parseInt(result.output.trim()); }
            catch (NumberFormatException ignored) {}
        }
        return android.os.Process.myUid() / 100000;
    }

    private boolean componentDisabled(WorkMode mode, String component, int userId) {
        if (userId == android.os.Process.myUid() / 100000) {
            try {
                int enabled = activity.getPackageManager().getComponentEnabledSetting(
                        android.content.ComponentName.unflattenFromString(component));
                return enabled == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                        || enabled == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
                        || enabled == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED;
            } catch (RuntimeException ignored) {
                // Fall back to the root query if the ROM restricts component-state access.
            }
        }
        int slash = component.indexOf('/');
        RootShell.Result state = mode.execute(activity, "dumpsys package " + component.substring(0, slash));
        if (!state.ok) return false;
        String activity = component.substring(slash + 1);
        // Match the user queried by pm list packages and used for freeze commands.
        boolean userZero = false, disabledSection = false;
        for (String line : state.output.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("User ")) {
                userZero = trimmed.startsWith("User " + userId + ":");
                disabledSection = false;
            }
            if (!userZero) continue;
            if (trimmed.equals("disabledComponents:")) {
                disabledSection = true;
                continue;
            }
            if (disabledSection && !trimmed.isEmpty()) {
                if (trimmed.equals(activity) || trimmed.equals(component)) return true;
                if (trimmed.endsWith(":") || trimmed.startsWith("User ")) disabledSection = false;
            }
        }
        return false;
    }

    public void queryRecommendedFrozen(Completion completion) {
        queryFrozen(RECOMMENDED_PACKAGES, completion);
    }

    public void queryOtaFrozen(Completion completion) {
        queryFrozen(OTA_PACKAGES, completion);
    }

    private void queryFrozen(String[][] targets, Completion completion) {
        WorkMode mode = WorkMode.get(activity);
        new Thread(() -> {
            int userId = currentUserId(mode);
            RootShell.Result result = mode.execute(activity, "pm list packages -d --user " + userId);
            java.util.List<String> disabled = java.util.Arrays.asList(result.output.split("\\r?\\n"));
            boolean allFrozen = result.ok;
            for (String[] packages : targets) {
                boolean groupFrozen = false;
                for (String pkg : packages) {
                    if (pkg.contains("/") ? componentDisabled(mode, pkg, userId) : disabled.contains("package:" + pkg)) groupFrozen = true;
                }
                if (!groupFrozen) allFrozen = false;
            }
            final boolean frozen = allFrozen;
            ui.post(() -> {
                if (!activity.isFinishing() && !activity.isDestroyed()) completion.onComplete(frozen, "");
            });
        }, "Arashi-FreezeState").start();
    }

    public void setRecommendedFrozen(boolean freeze, Completion completion) {
        setFrozen(RECOMMENDED_PACKAGES, RECOMMENDED_NAMES, "推荐的作业帮应用", freeze, completion);
    }

    public void setOtaFrozen(boolean freeze, Completion completion) {
        setFrozen(OTA_PACKAGES, OTA_NAMES, "系统升级", freeze, completion);
    }

    private void setFrozen(String[][] targets, String[] names, String title,
                           boolean freeze, Completion completion) {
        com.lijinmu.arashi.logs.RuntimeLogger.info("QuickCommands", (freeze ? "冻结" : "解冻") + title);
        WorkMode mode = WorkMode.get(activity);
        new Thread(() -> {
            if (mode == WorkMode.UNPRIVILEGED || (mode == WorkMode.SHELL && !WorkMode.shellReady())) {
                String message = mode == WorkMode.UNPRIVILEGED
                        ? "冻结和解冻需要 Shell 或 Root 工作模式"
                        : "请启动 Shizuku 或 Sui，并在工作模式中授权";
                ui.post(() -> {
                    if (!activity.isFinishing() && !activity.isDestroyed()) completion.onComplete(false, message);
                });
                return;
            }
            int userId = currentUserId(mode);
            java.util.List<String> failed = new java.util.ArrayList<>();
            for (int i = 0; i < targets.length; i++) {
                boolean succeeded = false;
                // Try every alias even after one succeeds; count aliases as one logical item.
                for (String pkg : targets[i]) {
                    RootShell.Result result = mode.execute(activity, "pm " + (freeze ? "disable " : "enable ") + "--user " + userId + " " + pkg);
                    if (pkg.contains("/")) {
                        // pm reports component state directly; package-list filters cannot query activities.
                        if (result.ok && result.output.contains("new state: " + (freeze ? "disabled" : "enabled"))) {
                            succeeded = true;
                        }
                    } else {
                        RootShell.Result state = mode.execute(activity, 
                                "pm list packages " + (freeze ? "-d" : "-e") + " --user " + userId + " " + pkg);
                        if (result.ok && state.ok
                                && java.util.Arrays.asList(state.output.split("\\r?\\n")).contains("package:" + pkg)) {
                            succeeded = true;
                        }
                    }
                }
                if (!succeeded) failed.add(names[i]);
            }
            String action = freeze ? "冻结" : "解冻";
            String message = failed.isEmpty() ? action + "指令全部执行成功"
                    : failed.size() == targets.length ? action + "指令全部执行失败"
                    : action + "指令部分执行失败：" + String.join("、", failed);
            com.lijinmu.arashi.logs.RuntimeLogger.record(failed.isEmpty() ? "INFO" : "WARN",
                    "QuickCommands", message, null);
            ui.post(() -> {
                if (!activity.isFinishing() && !activity.isDestroyed()) completion.onComplete(failed.isEmpty(), message);
            });
        }, "Arashi-FreezeApps").start();
    }

    public void run(String title, String command, Completion completion) {
        com.lijinmu.arashi.logs.RuntimeLogger.info("QuickCommands", "执行：" + title);
        WorkMode mode = WorkMode.get(activity);
        new Thread(() -> {
            RootShell.Result result = mode.execute(activity, command);
            com.lijinmu.arashi.logs.RuntimeLogger.info("QuickCommands", title + (result.ok ? "：成功" : "：失败"));
            ui.post(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                completion.onComplete(result.ok, result.output);
            });
        }, "Arashi-Root").start();
    }
}

