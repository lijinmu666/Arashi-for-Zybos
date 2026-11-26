package com.lijinmu.arashi.xposed;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Firmware requirement shared by the settings UI and system_server hooks. */
public final class BehaviorCompatibility {
    public static final String REQUIREMENT = "需要 AIbangOS V7.13.0 及更新版本";
    private static final Pattern VERSION = Pattern.compile(
            "^V([0-9]+)\\.([0-9]+)\\.([0-9]+)(?:[._+\\-].*)?$");
    private static final boolean SUPPORTED = readSupported(13);
    private static final boolean INSTALLER_AUTH_SUPPORTED = readSupported(14);
    public static final String INSTALLER_AUTH_REQUIREMENT = "需要 AIbangOS V7.14.0 及更新版本";

    public static boolean isInstallerAuthSupported() { return INSTALLER_AUTH_SUPPORTED; }

    private BehaviorCompatibility() {}

    public static boolean requiresVersion(String key) {
        return HookConfig.KEY_ALLOW_WIRELESS_DEBUGGING.equals(key)
                || HookConfig.KEY_BLOCK_APP_UNINSTALL.equals(key);
    }

    public static boolean isPadMsWatchdogSupported() { return INSTALLER_AUTH_SUPPORTED; }

    public static boolean isAllowed(String key) {
        if (HookConfig.KEY_DISABLE_PADMS_WATCHDOG.equals(key)) return isPadMsWatchdogSupported();
        return !requiresVersion(key) || isSupported();
    }

    public static boolean isSupported() { return SUPPORTED; }

    public static boolean supportsVersion(String value) {
        return supportsVersion(value, 13);
    }

    public static boolean supportsVersion(String value, int minimumMinor) {
        if (value == null) return false;
        Matcher match = VERSION.matcher(value.trim());
        if (!match.matches()) return false;
        try {
            int major = Integer.parseInt(match.group(1));
            int minor = Integer.parseInt(match.group(2));
            Integer.parseInt(match.group(3));
            return major > 7 || (major == 7 && minor >= minimumMinor);
        } catch (NumberFormatException error) {
            return false;
        }
    }

    private static boolean readSupported(int minimumMinor) {
        try {
            Class<?> properties = Class.forName("android.os.SystemProperties");
            return supportsVersion((String) properties.getDeclaredMethod("get", String.class)
                    .invoke(null, "ro.build.version.os"), minimumMinor);
        } catch (ReflectiveOperationException | RuntimeException error) {
            return false;
        }
    }
}
