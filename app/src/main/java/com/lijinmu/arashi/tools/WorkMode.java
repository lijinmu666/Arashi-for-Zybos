package com.lijinmu.arashi.tools;

import android.content.Context;
import android.content.pm.PackageManager;
import rikka.shizuku.Shizuku;

public enum WorkMode {
    SHELL("Shell"), ROOT("Root");

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
        };
    }
}
