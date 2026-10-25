package com.lijinmu.arashi.xposed.hooks;

/** No remote input, identifiers, update links or announcements in the local gate. */
public final class JcCodePolicy {
    private JcCodePolicy() {}

    public static String localStatus() {
        return "{\"enabled\":true,\"banned\":false,\"latest\":\"0\","
                + "\"force_min\":\"0\",\"block_pkgs\":\"\",\"popup_on\":false}";
    }
}
