package com.arashi.xposed;

/** Only navigation and shade restrictions; leave unrelated status bar bits intact. */
public final class NavigationAccessPolicy {
    private NavigationAccessPolicy() {}

    // StatusBarManager: EXPAND, HOME, BACK, RECENT, SEARCH
    public static int disable1(int flags) {
        return flags & ~0x03610000;
    }

    // StatusBarManager: DISABLE2_QUICK_SETTINGS, DISABLE2_NOTIFICATION_SHADE
    public static int disable2(int flags) {
        return flags & ~0x00000005;
    }

    public static boolean isNavigationUiCaller(StackTraceElement[] stack) {
        for (StackTraceElement frame : stack) {
            String name = frame.getClassName();
            if (name.startsWith("com.android.systemui.navigationbar.")
                    || name.startsWith("com.android.systemui.recents.")
                    || name.startsWith("com.android.systemui.qs.")
                    || isClassOrInner(name, "com.android.systemui.statusbar.phone.StatusBar")
                    || isClassOrInner(name, "com.android.systemui.statusbar.phone.CentralSurfacesImpl")
                    || isClassOrInner(name, "com.android.systemui.statusbar.phone.NotificationPanelViewController")) {
                return true;
            }
        }
        return false;
    }

    public static boolean isNavigationKeyCaller(StackTraceElement[] stack) {
        for (StackTraceElement frame : stack) {
            if (!frame.getClassName().equals("com.android.server.policy.PhoneWindowManager")) continue;
            String method = frame.getMethodName();
            if (method.equals("interceptKeyBeforeDispatching")
                    || method.equals("launchHomeFromHotKey")
                    || method.equals("showRecentApps") || method.equals("toggleRecentApps")) return true;
        }
        return false;
    }

    private static boolean isClassOrInner(String name, String target) {
        return name.equals(target) || name.startsWith(target + "$");
    }
}
