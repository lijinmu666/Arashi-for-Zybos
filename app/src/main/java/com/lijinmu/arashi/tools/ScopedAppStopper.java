package com.lijinmu.arashi.tools;

/** Kills a fixed set of hooked application processes without force-stopping packages. */
public final class ScopedAppStopper {
    private static final String[] TARGET_PACKAGES = {
            "com.android.systemui", "com.android.settings",
            "com.zuoyebang.iot.pad.launcher14", "com.android.launcher3",
            "com.android.packageinstaller", "com.android.captiveportallogin",
            "com.zuoyebang.iot.pad.supervise", "com.zuoyebang.iot.zptcpservice"
    };
    private ScopedAppStopper() {}

    public static RootResult stop() {
        RootShell.Result result = RootShell.run(buildCommand());
        return new RootResult(result.ok, result.ok
                ? "已终止目标应用进程" : result.output);
    }

    static String buildCommand() {
        StringBuilder patterns = new StringBuilder();
        for (String packageName : TARGET_PACKAGES) {
            if (patterns.length() > 0) patterns.append('|');
            patterns.append(packageName).append('|').append(packageName).append(":*");
        }
        StringBuilder command = new StringBuilder(
                "if [ \"$(id -u)\" != 0 ]; then echo ROOT_REQUIRED; exit 77; fi; "
                + "echo ROOT_GRANTED; failed=0; "
                // argv[0] preserves full package names; comm is truncated to 15 characters.
                + "for proc in /proc/[0-9]*; do "
                + "[ -r \"$proc/cmdline\" ] || continue; "
                + "name=$(tr '\\000' '\\n' < \"$proc/cmdline\" 2>/dev/null | head -n 1); "
                + "case \"$name\" in ");
        command.append(patterns)
                .append(") pid=$" + "{proc##*/}; ")
                // Already-exited processes are successful, not a permission failure.
                .append("kill -9 \"$pid\" 2>/dev/null || { [ ! -d \"$proc\" ] || failed=1; };; ")
                .append("esac; done; ")
                .append("if [ \"$failed\" != 0 ]; then echo APP_KILL_FAILED; exit 1; fi; ");
        return command.toString();
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
