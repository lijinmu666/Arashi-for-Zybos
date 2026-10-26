package com.lijinmu.arashi.tools;

import com.lijinmu.arashi.ui.BasePage;
import com.lijinmu.arashi.ui.MiuixPages;

import android.os.Handler;
import android.os.Looper;
import android.content.ComponentName;
import android.content.pm.PackageManager;

public final class QuickCommands extends BasePage {

    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override protected void populate() {
        MiuixPages.renderQuickCommands(this);

    }

    public interface Completion {
        void onComplete(boolean success, String output);
    }

    public void setLauncherErrorActivityEnabled(boolean enabled, Completion completion) {
        String title = "桌面的ErrorActivity";
        StringBuilder command = new StringBuilder();
        for (String pkg : new String[] {
                "com.zuoyebang.iot.pad.launcher14", "com.android.launcher3" }) {
            ComponentName component = new ComponentName(pkg,
                    "com.zuoyebang.iot.pad.launcher.ErrorActivity");
            try {
                getPackageManager().getActivityInfo(component, PackageManager.MATCH_DISABLED_COMPONENTS);
            } catch (PackageManager.NameNotFoundException ignored) {
                continue;
            }
            if (command.length() > 0) command.append(" && ");
            // Always include the activity class: never disable the launcher package.
            command.append(enabled ? "pm enable --user current " : "pm disable --user current ")
                    .append(component.flattenToString());
        }
        if (command.length() == 0) {
            completion.onComplete(false, "未找到支持的桌面 ErrorActivity 组件");
            return;
        }
        run(title, command.toString(), completion);
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
