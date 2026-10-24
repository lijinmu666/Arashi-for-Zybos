package com.lijinmu.arashi.zybos;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import com.lijinmu.arashi.ApkInstallerActivity;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SystemToolSupport {
    public static final String MESSAGE = "系统特权工具仅支持 7.10.0 以下的 AIbangOS 版本，你当前的版本已无法使用";
    private static final boolean BLOCKED = isBlockedVersion(readVersion());
    private SystemToolSupport() {}

    public static boolean isBlocked() { return BLOCKED; }

    public static boolean isBlockedVersion(String value) {
        if (value == null) return false;
        Matcher match = Pattern.compile("(?i)^V?(\\d+)\\.(\\d+)\\.(\\d+)(?:$|[^0-9].*)").matcher(value.trim());
        if (!match.matches()) return false;
        // Compare numeric components without integer overflow or lexicographic ordering.
        int[] minimum = {7, 10, 0};
        for (int i = 0; i < minimum.length; i++) {
            java.math.BigInteger part = new java.math.BigInteger(match.group(i + 1));
            int comparison = part.compareTo(java.math.BigInteger.valueOf(minimum[i]));
            if (comparison != 0) return comparison > 0;
        }
        return true;
    }

    public static void enforceInstallerState(Context context) {
        if (!BLOCKED) return;
        ComponentName component = new ComponentName(context, ApkInstallerActivity.class);
        PackageManager pm = context.getPackageManager();
        if (pm.getComponentEnabledSetting(component) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
            pm.setComponentEnabledSetting(component, PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
        }
    }

    private static String readVersion() {
        Process process = null;
        try {
            process = new ProcessBuilder("/system/bin/getprop", "ro.build.version.os").start();
            if (!process.waitFor(2, TimeUnit.SECONDS) || process.exitValue() != 0) return "";
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String value = reader.readLine();
                return value == null ? "" : value;
            }
        } catch (Exception ignored) {
            return "";
        } finally {
            if (process != null) process.destroy();
        }
    }
}
