package com.arashi.tools;

import com.arashi.ui.BasePage;
import com.arashi.ui.MiuixPages;

import android.os.Handler;
import android.os.Looper;

public final class QuickCommands extends BasePage {

    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override protected void populate() {
        MiuixPages.renderQuickCommands(this);

}

private void setGlobal(String title, String key, boolean enabled) {
        run(title, "settings put global " + key + " " + (enabled ? "1" : "0")
                + "; settings get global " + key);
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
