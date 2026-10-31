package com.lijinmu.arashi.jailbreak;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Checks Root granted to this app, not whether a manager APK is installed.
 * Run off the UI thread. Invoking su may show the manager's authorization prompt.
 */
public final class RootAccessProbe {
    public enum Access { NONE, KERNELSU, OTHER }
    private RootAccessProbe() { }

    public static Access detect() {
        // A version string alone never proves this app has Root permission.
        String uid = query(10, "su", "-c", "id -u");
        if (uid == null || !uid.trim().equals("0")) return Access.NONE;
        String version = query(3, "su", "-v");
        if (version != null) {
            String normalized = version.toLowerCase(Locale.ROOT);
            if (normalized.contains("kernelsu")
                    || normalized.matches("(?s).*\\bksu\\b.*")) {
                return Access.KERNELSU;
            }
        }
        // Includes Magisk and unrecognized providers with confirmed UID 0.
        return Access.OTHER;
    }

    private static String query(int seconds, String... command) {
        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
            if (!process.waitFor(seconds, TimeUnit.SECONDS) || process.exitValue() != 0)
                return null;
            StringBuilder result = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    process.getInputStream(), StandardCharsets.UTF_8))) {
                for (String line; (line = reader.readLine()) != null;) {
                    if (result.length() + line.length() > 4096) return null;
                    if (result.length() > 0) result.append('\n');
                    result.append(line);
                }
            }
            return result.toString();
        } catch (Exception failure) {
            return null;
        } finally {
            if (process != null) {
                if (process.isAlive()) process.destroyForcibly();
                else process.destroy();
            }
        }
    }
}
