package com.lijinmu.arashi.tools;

import com.lijinmu.arashi.ui.BasePage;
import com.lijinmu.arashi.ui.MiuixPages;

import android.os.Handler;
import android.os.Looper;

public final class QuickCommands extends BasePage {

    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override protected void populate() {
        MiuixPages.renderQuickCommands(this);

    }

    public interface Completion {
        void onComplete(boolean success, String output);
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

    private static int currentUserId() {
        RootShell.Result result = RootShell.run("am get-current-user");
        if (result.ok) {
            try { return Integer.parseInt(result.output.trim()); }
            catch (NumberFormatException ignored) {}
        }
        return android.os.Process.myUid() / 100000;
    }

    private static boolean componentDisabled(String component, int userId) {
        int slash = component.indexOf('/');
        RootShell.Result state = RootShell.run("dumpsys package " + component.substring(0, slash));
        if (!state.ok) return false;
        String activity = component.substring(slash + 1);
        // Match the user queried by pm list packages and used for freeze commands.
        boolean userZero = false, disabledSection = false;
        int sectionIndent = -1;
        for (String line : state.output.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("User ")) {
                userZero = trimmed.startsWith("User " + userId + ":");
                disabledSection = false;
            }
            if (!userZero) continue;
            int indent = 0;
            while (indent < line.length() && Character.isWhitespace(line.charAt(indent))) indent++;
            if (trimmed.equals("disabledComponents:")) {
                disabledSection = true;
                sectionIndent = indent;
                continue;
            }
            if (disabledSection && !trimmed.isEmpty()) {
                if (indent <= sectionIndent) disabledSection = false;
                else if (trimmed.equals(activity)) return true;
            }
        }
        return false;
    }

    public void queryRecommendedFrozen(Completion completion) {
        new Thread(() -> {
            int userId = currentUserId();
            RootShell.Result result = RootShell.run("pm list packages -d --user " + userId);
            java.util.List<String> disabled = java.util.Arrays.asList(result.output.split("\\r?\\n"));
            boolean allFrozen = result.ok;
            for (String[] packages : RECOMMENDED_PACKAGES) {
                boolean groupFrozen = false;
                for (String pkg : packages) {
                    if (pkg.contains("/") ? componentDisabled(pkg, userId) : disabled.contains("package:" + pkg)) groupFrozen = true;
                }
                if (!groupFrozen) allFrozen = false;
            }
            final boolean frozen = allFrozen;
            ui.post(() -> {
                if (!isFinishing() && !isDestroyed()) completion.onComplete(frozen, "");
            });
        }, "Arashi-FreezeState").start();
    }

    public void setRecommendedFrozen(boolean freeze, Completion completion) {
        new Thread(() -> {
            int userId = currentUserId();
            java.util.List<String> failed = new java.util.ArrayList<>();
            for (int i = 0; i < RECOMMENDED_PACKAGES.length; i++) {
                boolean succeeded = false;
                // Try every alias even after one succeeds; count aliases as one logical item.
                for (String pkg : RECOMMENDED_PACKAGES[i]) {
                    RootShell.Result result = RootShell.run("pm " + (freeze ? "disable " : "enable ") + "--user " + userId + " " + pkg);
                    if (pkg.contains("/")) {
                        // pm reports component state directly; package-list filters cannot query activities.
                        if (result.ok && result.output.contains("new state: " + (freeze ? "disabled" : "enabled"))) {
                            succeeded = true;
                        }
                    } else {
                        RootShell.Result state = RootShell.run(
                                "pm list packages " + (freeze ? "-d" : "-e") + " --user " + userId + " " + pkg);
                        if (result.ok && state.ok
                                && java.util.Arrays.asList(state.output.split("\\r?\\n")).contains("package:" + pkg)) {
                            succeeded = true;
                        }
                    }
                }
                if (!succeeded) failed.add(RECOMMENDED_NAMES[i]);
            }
            String action = freeze ? "冻结" : "解冻";
            String message = failed.isEmpty() ? action + "指令全部执行成功"
                    : failed.size() == RECOMMENDED_PACKAGES.length ? action + "指令全部执行失败"
                    : action + "指令部分执行失败：" + String.join("、", failed);
            ui.post(() -> {
                if (!isFinishing() && !isDestroyed()) completion.onComplete(failed.isEmpty(), message);
            });
        }, "Arashi-FreezeApps").start();
    }

    public void run(String title, String command, Completion completion) {
        new Thread(() -> {
            RootShell.Result result = RootShell.run(command);
            ui.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                completion.onComplete(result.ok, result.output);
            });
        }, "Arashi-Root").start();
    }
}
