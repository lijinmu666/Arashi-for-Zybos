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
        {"com.bytedance.os.slardar"}, {"com.zuoyebang.iot.zlog"}
    };
    private static final String[] RECOMMENDED_NAMES = {
        "ZpReport", "PadMs", "SlardarOsClient", "日志服务"
    };

    public void queryRecommendedFrozen(Completion completion) {
        new Thread(() -> {
            RootShell.Result result = RootShell.run("pm list packages -d --user current");
            java.util.List<String> disabled = java.util.Arrays.asList(result.output.split("\\r?\\n"));
            boolean allFrozen = result.ok;
            for (String[] packages : RECOMMENDED_PACKAGES) {
                boolean groupFrozen = false;
                for (String pkg : packages) {
                    if (disabled.contains("package:" + pkg)) groupFrozen = true;
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
            java.util.List<String> failed = new java.util.ArrayList<>();
            for (int i = 0; i < RECOMMENDED_PACKAGES.length; i++) {
                boolean succeeded = false;
                // Try every alias even after one succeeds; count PadMs as one application.
                for (String pkg : RECOMMENDED_PACKAGES[i]) {
                    RootShell.Result result = RootShell.run("pm " + (freeze ? "disable " : "enable ") + pkg);
                    RootShell.Result state = RootShell.run(
                            "pm list packages " + (freeze ? "-d" : "-e") + " --user current " + pkg);
                    if (result.ok && state.ok
                            && java.util.Arrays.asList(state.output.split("\\r?\\n")).contains("package:" + pkg)) {
                        succeeded = true;
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
