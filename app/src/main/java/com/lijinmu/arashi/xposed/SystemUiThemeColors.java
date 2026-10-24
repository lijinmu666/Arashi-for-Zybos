package com.lijinmu.arashi.xposed;

import java.util.Locale;

/** Opaque RGB only, so a transparent accent cannot hide controls. */
public final class SystemUiThemeColors {
    public static final int DEFAULT_COLOR = 0xff3d54d9;
    private SystemUiThemeColors() {}

    public static Integer parse(String input) {
        if (input == null) return null;
        String value = input.trim();
        if (value.startsWith("#")) value = value.substring(1);
        if (!value.matches("[0-9a-fA-F]{6}")) return null;
        return 0xff000000 | Integer.parseInt(value, 16);
    }

    public static int opaque(int color) { return color | 0xff000000; }

    public static String format(int color) {
        return String.format(Locale.ROOT, "#%06X", color & 0xffffff);
    }

    public static int foreground(int background) {
        // Pick the better WCAG contrast ratio, including near-white and black accents.
        double luminance = 0.2126 * linear((background >> 16) & 255)
                + 0.7152 * linear((background >> 8) & 255)
                + 0.0722 * linear(background & 255);
        double black = (luminance + 0.05) / 0.05;
        double white = 1.05 / (luminance + 0.05);
        return black >= white ? 0xff000000 : 0xffffffff;
    }

    private static double linear(int channel) {
        double value = channel / 255.0;
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }
}
