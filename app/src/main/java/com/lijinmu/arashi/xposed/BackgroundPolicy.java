package com.lijinmu.arashi.xposed;

import java.util.Set;

public final class BackgroundPolicy {
    private static final Set<String> EXEMPT_PACKAGES = Set.of(
            "com.tencent.mm",
            "com.tencent.mobileqq",
            "com.zuoyebang.iot.pad.zpeyecare",
            "com.zuoyebang.iot.pad.zpvoiceassistant",
            "com.android.deskclock");

    private BackgroundPolicy() {}

    public static boolean isExempt(String packageName) {
        return packageName != null && EXEMPT_PACKAGES.contains(packageName);
    }

    public static boolean shouldKeep(boolean enabled, Set<String> selected, String packageName) {
        return enabled && packageName != null && !packageName.isEmpty()
                && (selected.isEmpty() || selected.contains(packageName));
    }
}
