package com.lijinmu.arashi.tools;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

final class RootShell {

private static final int TIMEOUT_SECONDS = 60;

    private RootShell() {
    }

    static final class Result {
        final boolean ok;
        final String output;

        Result(boolean ok, String output) {
            this.ok = ok;
            this.output = output;
            if (!ok) com.lijinmu.arashi.logs.RuntimeLogger.warn("Root", "Root 命令执行失败：" + output);
        }
    }

static Result run(String command) {
        Process process = null;
        try {
            process = new ProcessBuilder("su", "-c", command)
                    .redirectErrorStream(true)
                    .start();
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                return new Result(false, "执行超时");
            }
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (output.length() > 0) {
                        output.append('\n');
                    }
                    output.append(line);
                }
            }
            int code = process.exitValue();
            if (code != 0) {
                return new Result(false, "退出码 " + code
                        + (output.length() == 0 ? "" : "\n" + output));
            }
            return new Result(true, output.toString());
        } catch (Throwable t) {
            String message = t.getMessage() == null ? t.toString() : t.getMessage();
            return new Result(false, "无法执行 su，请确认已授予 root 权限\n" + message);
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }
}

