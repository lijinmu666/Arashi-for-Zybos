package com.arashi.xposed;

import java.lang.reflect.Method;
import java.util.List;

/** Menu model differences between the Android 12 and 14 vendor Settings apps. */
public final class SettingsHeaderCompat {
    private static final int ARASHI_MENU_ID = 0x41524153;

    private SettingsHeaderCompat() {
    }

    public static Object create(Class<?> headerClass, List<?> existing, int title, int icon,
                                String fragment) throws Exception {
        Object header = headerClass.getDeclaredConstructor().newInstance();
        headerClass.getMethod("setTitleResId", int.class).invoke(header, title);
        headerClass.getMethod("setImgResId", int.class).invoke(header, icon);
        headerClass.getMethod("setFragmentName", String.class).invoke(header, fragment);

        // Older Settings exposes this setter; newer Settings defaults to visible without it.
        Method visible = Reflect.findMethodExact(headerClass, "setVisible", boolean.class);
        if (visible != null) visible.invoke(header, true);

        // Newer adapters identify Wi-Fi/Bluetooth by ID rather than list position.
        Method setId = Reflect.findMethodExact(headerClass, "setZybId", int.class);
        if (setId != null) {
            Method getId = headerClass.getMethod("getZybId");
            int id = ARASHI_MENU_ID;
            while (containsId(existing, getId, id)) id++;
            setId.invoke(header, id);
        }
        return header;
    }

    private static boolean containsId(List<?> headers, Method getter, int id) throws Exception {
        for (Object header : headers) {
            if (((Number) getter.invoke(header)).intValue() == id) return true;
        }
        return false;
    }
}
