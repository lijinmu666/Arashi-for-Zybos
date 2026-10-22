package com.lijinmu.arashi.xposed;

public final class TcpLoginPolicy {
    public static final String PACKAGE = "com.zuoyebang.iot.zptcpservice";
    public static final String ACTION = PACKAGE + ".login.fail";
    private TcpLoginPolicy() {}

    public static boolean shouldBlock(boolean enabled, String action) {
        return enabled && ACTION.equals(action);
    }
}
