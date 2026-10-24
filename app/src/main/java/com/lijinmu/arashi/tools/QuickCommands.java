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

private void setGlobal(String title, String key, boolean enabled) {
        run(title, "settings put global " + key + " " + (enabled ? "1" : "0")
                + "; settings get global " + key);
    }

    public void freezeLauncherErrorActivity() {
        String title = "冻结桌面 ErrorActivity";
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
            command.append("pm disable --user current ").append(component.flattenToString());
        }
        if (command.length() == 0) {
            MiuixPages.showMessage(this, title, "未找到支持的桌面 ErrorActivity 组件");
            return;
        }
        run(title, command.toString());
    }

    public void run(String title, String command) {
        new Thread(() -> {
            RootShell.Result result = RootShell.run(command);
            ui.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                String body = result.ok ? "执行成功" : "执行失败";
                if (!result.output.isEmpty()) {
                    body += "\n" + result.output;
                }
                MiuixPages.showMessage(this, title, body);
            });
        }, "Arashi-Root").start();
    }
}
