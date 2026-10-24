package com.lijinmu.arashi.tools;

import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** Stops the selected LSPosed application scopes without targeting Android's framework process. */
public final class ScopedAppStopper {
    private static final String ARASHI_PACKAGE = "com.lijinmu.arashi";
    private static final Pattern PACKAGE_NAME = Pattern.compile("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+");

    private ScopedAppStopper() {
    }

    public static RootResult stop(Collection<String> selectedPackages) {
        Set<String> packages = new TreeSet<>();
        if (selectedPackages != null) {
            for (String packageName : selectedPackages) {
                if (packageName == null || !PACKAGE_NAME.matcher(packageName).matches()) continue;
                if ("system".equals(packageName) || "system_server".equals(packageName)
                        || "android".equals(packageName)) continue;
                packages.add(packageName);
            }
        }
        if (packages.isEmpty()) return new RootResult(false, "没有可停止的应用作用域");

        // Stop Arashi last so it can finish the root command and display its result first.
        boolean stopArashi = packages.remove(ARASHI_PACKAGE);
        StringBuilder command = new StringBuilder(
                "if [ \"$(id -u)\" != 0 ]; then echo ROOT_REQUIRED; exit 77; fi; echo ROOT_GRANTED; failed=0; ");
        for (String packageName : packages) {
            command.append("am force-stop --user current ").append(packageName).append(" || failed=1; ");
        }
        if (stopArashi) {
            command.append("if [ \"$failed\" = 0 ]; then (sleep 2; am force-stop --user current ")
                    .append(ARASHI_PACKAGE)
                    .append(" >/dev/null 2>&1) </dev/null >/dev/null 2>&1 & fi; ");
        }
        command.append("if [ \"$failed\" != 0 ]; then echo APP_STOP_FAILED; exit 1; fi");

        RootShell.Result result = RootShell.run(command.toString());
        if (!result.ok && !result.output.contains("APP_STOP_FAILED")) {
            return new RootResult(false, result.output);
        }
        if (result.output.contains("APP_STOP_FAILED")) {
            return new RootResult(false, "部分作用域应用停止失败，请检查设备及 root 管理器状态");
        }
        return new RootResult(true, "已发送强行停止命令：" + (packages.size() + (stopArashi ? 1 : 0)) + " 个应用");
    }

    public static final class RootResult {
        public final boolean ok;
        public final String output;

        private RootResult(boolean ok, String output) {
            this.ok = ok;
            this.output = output == null ? "" : output;
        }
    }
}
