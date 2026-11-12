package com.lijinmu.arashi.tools;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

final class RootShell {
    private static final int TIMEOUT_SECONDS = 60;
    private static final int OUTPUT_LIMIT = 64 * 1024;

    private RootShell() {}

    static final class Result {
        final boolean ok;
        final String output;

        Result(boolean ok, String output) {
            this.ok = ok;
            this.output = output;
        }
    }

    static Result run(String command) {
        return runProcess("su", "-c", command);
    }

    static Result runProcess(String executable, String option, String command) {
        Process process = null;
        try {
            process = new ProcessBuilder(executable, option, command).redirectErrorStream(true).start();
            InputStream stream = process.getInputStream();
            FutureTask<String> output = new FutureTask<>(() -> {
                try (stream) {
                    var prefix = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = stream.read(buffer)) != -1) {
                        prefix.write(buffer, 0, Math.min(count, OUTPUT_LIMIT - prefix.size()));
                    }
                    return new String(prefix.toByteArray(), StandardCharsets.UTF_8);
                }
            });
            Thread reader = new Thread(output, "Arashi-CommandOutput");
            reader.setDaemon(true);
            reader.start();
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                return new Result(false, "执行超时");
            }
            String body = output.get(2, TimeUnit.SECONDS);
            int code = process.exitValue();
            return code == 0 ? new Result(true, body) : new Result(false, "退出码 " + code + "\n" + body);
        } catch (Exception error) {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            return new Result(false, "无法执行 " + executable + "：" + error);
        } finally {
            if (process != null) process.destroyForcibly();
        }
    }
}
