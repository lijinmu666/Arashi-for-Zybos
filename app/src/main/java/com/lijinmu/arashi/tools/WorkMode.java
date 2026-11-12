package com.lijinmu.arashi.tools;

import android.content.Context;
import android.content.pm.PackageManager;
import android.provider.Settings;
import java.util.regex.Pattern;
import rikka.shizuku.Shizuku;

public enum WorkMode {
    UNPRIVILEGED("无特权"), SHELL("Shell"), ROOT("Root");

    private static final Pattern SYSTEM_WRITE =
            Pattern.compile("settings put system ([A-Za-z0-9_.-]+) ([A-Za-z0-9_.-]+)");
    public final String label;

    WorkMode(String label) {
        this.label = label;
    }

    public static WorkMode get(Context context) {
        String value = context.getSharedPreferences("work_mode", Context.MODE_PRIVATE)
                .getString("selected", ROOT.name());
        try {
            return valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return ROOT;
        }
    }

    public void save(Context context) {
        context.getSharedPreferences("work_mode", Context.MODE_PRIVATE)
                .edit().putString("selected", name()).apply();
    }

    public static boolean shellReady() {
        try {
            return Shizuku.pingBinder() && !Shizuku.isPreV11()
                    && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    RootShell.Result execute(Context context, String command) {
        return switch (this) {
            case ROOT -> RootShell.run(command);
            case SHELL -> ShizukuCommands.run(context, command);
            case UNPRIVILEGED -> writeSystemSetting(context, command);
        };
    }

    private static RootShell.Result writeSystemSetting(Context context, String command) {
        var match = SYSTEM_WRITE.matcher(command);
        if (!match.matches()) {
            return new RootShell.Result(false, "此操作需要 Shell 或 Root 工作模式");
        }
        if (!Settings.System.canWrite(context)) {
            return new RootShell.Result(false, "请在工作模式中授予修改系统设置权限");
        }
        try {
            boolean written = Settings.System.putString(context.getContentResolver(), match.group(1), match.group(2));
            return new RootShell.Result(written, written ? "" : "系统设置写入失败");
        } catch (RuntimeException error) {
            return new RootShell.Result(false, "系统拒绝写入设置：" + error.getMessage());
        }
    }
}
