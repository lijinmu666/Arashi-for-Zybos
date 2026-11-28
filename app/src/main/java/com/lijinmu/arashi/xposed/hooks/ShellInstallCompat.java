package com.lijinmu.arashi.xposed.hooks;

import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Restores command parsing when vendor builds strip the five runInstall handlers.
 * Uses the firmware's session helpers, preserving Binder identity and verification.
 */
public final class ShellInstallCompat {
    private ShellInstallCompat() {}

    public static boolean supports(String command) {
        return "install".equals(command) || "install-streaming".equals(command)
                || "install-incremental".equals(command) || "install-create".equals(command)
                || "install-commit".equals(command);
    }

    public static int run(Object shell, String command) {
        try {
            if ("install-incremental".equals(command)) {
                throw new IllegalArgumentException("Incremental installation is unavailable on this firmware; use adb install --no-incremental");
            }
            if ("install-commit".equals(command)) {
                int id = Integer.parseInt(required(shell));
                if (next(shell) != null) throw new IllegalArgumentException("Unexpected install-commit argument");
                return (Integer) call(shell, "doCommitSession", new Class<?>[]{int.class, boolean.class}, id, true);
            }
            return createOrInstall(shell, command);
        } catch (Throwable error) {
            try {
                PrintWriter err = (PrintWriter) call(shell, "getErrPrintWriter", new Class<?>[0]);
                err.println("Arashi: " + command + " failed: " + error);
                err.flush();
            } catch (Throwable ignored) {}
            return 1;
        }
    }

    private static int createOrInstall(Object shell, String command) throws Throwable {
        Class<?> type = Class.forName("android.content.pm.PackageInstaller$SessionParams", false,
                shell.getClass().getClassLoader());
        Object params = type.getConstructor(int.class).newInstance(1);
        // Match normal pm install: replacement is enabled unless explicitly disabled.
        flags(params, 2, true);
        int user = -1;
        String installer = null;
        long size = -1;
        boolean multi = false;
        String option;
        while ((option = (String) call(shell, "getNextOption", new Class<?>[0])) != null) {
            switch (option) {
                case "-r": flags(params, 2, true); break;
                case "-R": flags(params, 2, false); break;
                case "-t": flags(params, 4, true); break;
                case "-d": flags(params, 128, true); break;
                case "-g": flags(params, 256, true); break;
                case "-s": flags(params, 8, true); break;
                case "-f": flags(params, 16, true); break;
                case "--dont-kill": flags(params, 4096, true); break;
                case "--force-sdk": flags(params, 8192, true); break;
                case "--instant": case "--ephemeral": flags(params, 2048, true); break;
                case "--full": flags(params, 2048, false); break;
                case "--user":
                    String value = required(shell);
                    user = "all".equals(value) ? -1 : ("current".equals(value) || "cur".equals(value) ? -2 : Integer.parseInt(value));
                    break;
                case "-i": installer = required(shell); break;
                case "-S":
                    size = Long.parseLong(required(shell));
                    if (size <= 0) throw new IllegalArgumentException("APK size must be positive");
                    set(params, "sizeBytes", size);
                    break;
                case "-p": set(params, "mode", 2); set(params, "appPackageName", required(shell)); break;
                case "--pkg": set(params, "appPackageName", required(shell)); break;
                case "--abi":
                    String abi = required(shell);
                    // Let PackageInstaller perform the final ABI validation.
                    set(params, "abiOverride", abi); break;
                case "--install-location": set(params, "installLocation", Integer.parseInt(required(shell))); break;
                case "--install-reason": set(params, "installReason", Integer.parseInt(required(shell))); break;
                case "--originating-uri": case "--referrer":
                    Class<?> uri = Class.forName("android.net.Uri", false, shell.getClass().getClassLoader());
                    Object parsed = uri.getMethod("parse", String.class).invoke(null, required(shell));
                    set(params, "--referrer".equals(option) ? "referrerUri" : "originatingUri", parsed); break;
                case "--staged": set(params, "isStaged", true); break;
                case "--multi-package": set(params, "isMultiPackage", true); multi = true; break;
                default: throw new IllegalArgumentException("Unsupported install option: " + option);
            }
        }
        List<String> paths = new ArrayList<>();
        String path;
        while ((path = next(shell)) != null) paths.add(path);
        boolean createOnly = "install-create".equals(command);
        if (createOnly && !paths.isEmpty()) throw new IllegalArgumentException("install-create takes no APK paths");
        if (!createOnly && multi) throw new IllegalArgumentException("Use install-create for a multi-package parent");
        if (!createOnly && paths.isEmpty()) paths.add("-");
        if (paths.contains("-") && (paths.size() != 1 || size <= 0))
            throw new IllegalArgumentException("Stdin installation requires one APK and -S SIZE");
        if (!createOnly && !paths.contains("-")) {
            long total = 0;
            for (String apk : paths) {
                Object fd = call(shell, "openFileForSystem", new Class<?>[]{String.class, String.class}, apk, "r");
                if (fd == null) throw new IllegalArgumentException("Cannot open APK: " + apk);
                try {
                    long length = ((Number) call(fd, "getStatSize", new Class<?>[0])).longValue();
                    if (length <= 0) throw new IllegalArgumentException("Empty or unknown APK size: " + apk);
                    total = Math.addExact(total, length);
                } finally { call(fd, "close", new Class<?>[0]); }
            }
            set(params, "sizeBytes", total);
        }
        int id = (Integer) call(shell, "doCreateSession", new Class<?>[]{type, String.class, int.class}, params, installer, user);
        boolean transferred = createOnly;
        try {
            if (createOnly) {
                PrintWriter out = (PrintWriter) call(shell, "getOutPrintWriter", new Class<?>[0]);
                out.println("Success: created install session [" + id + "]");
                out.flush();
                return 0;
            }
            for (int i = 0; i < paths.size(); i++) {
                int result = (Integer) call(shell, "doWriteSplit",
                        new Class<?>[]{int.class, String.class, long.class, String.class, boolean.class},
                        id, paths.get(i), size, i == 0 ? "base.apk" : "split_" + i + ".apk", false);
                if (result != 0) return result;
            }
            int result = (Integer) call(shell, "doCommitSession", new Class<?>[]{int.class, boolean.class}, id, true);
            transferred = result == 0;
            return result;
        } finally {
            if (!transferred) {
                try { call(shell, "doAbandonSession", new Class<?>[]{int.class, boolean.class}, id, false); }
                catch (Throwable ignored) {}
            }
        }
    }

    private static String next(Object shell) throws Throwable {
        return (String) call(shell, "getNextArg", new Class<?>[0]);
    }

    private static String required(Object shell) throws Throwable {
        String value = next(shell);
        if (value == null) throw new IllegalArgumentException("Missing install argument");
        return value;
    }

    private static void flags(Object params, int mask, boolean enabled) throws Exception {
        Field field = params.getClass().getField("installFlags");
        int value = field.getInt(params);
        field.setInt(params, enabled ? value | mask : value & ~mask);
    }

    private static void set(Object params, String name, Object value) throws Exception {
        params.getClass().getField(name).set(params, value);
    }

    private static Object call(Object target, String name, Class<?>[] types, Object... args) throws Throwable {
        for (Class<?> owner = target.getClass(); owner != null; owner = owner.getSuperclass()) {
            Method method;
            try { method = owner.getDeclaredMethod(name, types); }
            catch (NoSuchMethodException absent) { continue; }
            method.setAccessible(true);
            try { return method.invoke(target, args); }
            catch (InvocationTargetException error) { throw error.getCause(); }
        }
        throw new NoSuchMethodException(target.getClass().getName() + "." + name);
    }
}
