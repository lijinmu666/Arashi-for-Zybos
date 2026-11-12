package com.lijinmu.arashi.tools;

import androidx.annotation.Keep;

@Keep
public final class ShellCommandService extends IShellCommandService.Stub {
    @Override public String[] execute(String command) {
        RootShell.Result result = RootShell.runProcess("/system/bin/sh", "-c", command);
        return new String[] { result.ok ? "0" : "1", result.output };
    }

    @Override public void destroy() {
        System.exit(0);
    }
}
