package com.arashi.uninstall;

import android.content.Context;
import android.os.PowerManager;

public final class UninstallScreenState {
    public static final int SCREEN_OFF_RESULT = 4;
    public static final String SCREEN_OFF_REASON = "设备息屏";
    private UninstallScreenState() {}

    public static boolean isScreenOff(Context context) {
        PowerManager power = context.getSystemService(PowerManager.class);
        return power != null && !power.isInteractive();
    }
}
