// Adapted from Yuehong DirtyFragVrRunner.java (MPL-2.0); see third_party/dirtyfrag/NOTICE.md.
package com.lijinmu.arashi.jailbreak;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.IpSecAlgorithm;
import android.net.IpSecManager;
import android.net.IpSecTransform;
import android.os.Build;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/** Stages the manager selected by the assistant and runs the DirtyFrag VR chain. */
public final class DirtyFragVrRunner {
    private static final String NATIVE_BIN = "libdfroot.so";
    private static final String BOOTSTRAP_BIN = "libbootstrap.so";
    private static final String WORK_DIRECTORY = "dfroot";
    private static final String BOOTSTRAP_FILE = "bootstrap";
    private static final String KSUD_FILE = "ksud";
    private static final String PREFS_NAME = "dfroot";
    private static final String PREF_SU_MANAGER = "su_manager";
    private static final String PREF_SOFT_REBOOT = "soft_reboot";
    private static final String PREF_DISABLE_MODULES = "disable_modules";
    private DirtyFragVrRunner() {
    }

    public static boolean isArmed() {
        return new File("/dev/df").exists();
    }

    public static synchronized int run(Context context, boolean disableModules,
                                       boolean softReboot, DirtyFragVrReporter reporter)
            throws Exception {
        Context appContext = context.getApplicationContext();
        Context deContext = appContext.createDeviceProtectedStorageContext();
        if (isArmed()) {
            if (handoffComplete()) return 0;
            throw new IOException("检测到未完成的提权，请重启设备后重试");
        }
        String managerPackage = KernelSuPayload.PACKAGE;

        File work = new File(deContext.getFilesDir(), WORK_DIRECTORY);
        if (!work.exists() && !work.mkdirs()) {
            throw new IOException("unable to create DirtyFrag staging directory");
        }
        File ksud = new File(deContext.getFilesDir().getParentFile(), KSUD_FILE);
        KernelSuPayload.stage(appContext, ksud);
        stageBinary(new File(deContext.getApplicationInfo().nativeLibraryDir, BOOTSTRAP_BIN),
                new File(work, BOOTSTRAP_FILE));

        // The native bootstrap reads these DE preferences after the kernel hand-off.
        SharedPreferences prefs = deContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (!prefs.edit()
                .putString(PREF_SU_MANAGER, managerPackage)
                .putBoolean(PREF_SOFT_REBOOT, softReboot)
                .putBoolean(PREF_DISABLE_MODULES, disableModules)
                .commit()) {
            throw new IOException("unable to persist DirtyFrag preferences");
        }

        File bin = new File(deContext.getApplicationInfo().nativeLibraryDir, NATIVE_BIN);
        if (!bin.isFile()) throw new IOException(NATIVE_BIN + " is missing from nativeLibraryDir");

        reporter.report("DirtyFrag VR v1.16: KernelSU manager hand-off.\n");
        reporter.report("DirtyFrag manager package: " + managerPackage + "\n");
        reporter.report("DirtyFrag model: " + Build.MANUFACTURER + " " + Build.MODEL + "\n");
        reporter.report("DirtyFrag kernel: " + System.getProperty("os.version", "unknown") + "\n\n");

        IpSecManager ipsec = (IpSecManager) deContext.getSystemService(Context.IPSEC_SERVICE);
        if (ipsec == null) throw new IllegalStateException("IpSecManager unavailable");
        InetAddress loopback = InetAddress.getByName("127.0.0.1");
        try (IpSecManager.UdpEncapsulationSocket encapSock = ipsec.openUdpEncapsulationSocket();
             IpSecManager.SecurityParameterIndex spiObj = ipsec.allocateSecurityParameterIndex(loopback);
             DatagramSocket senderSock = new DatagramSocket()) {
            byte[] aesKey = new byte[32];
            new SecureRandom().nextBytes(aesKey);
            int senderPort = senderSock.getLocalPort();
            senderSock.close();
            try (IpSecTransform transform = new IpSecTransform.Builder(deContext)
                    .setEncryption(new IpSecAlgorithm(IpSecAlgorithm.CRYPT_AES_CBC, aesKey))
                    .setIpv4Encapsulation(encapSock, senderPort)
                    .buildTransportModeTransform(loopback, spiObj)) {
                int exitCode = runBinary(bin, reporter, encapSock.getPort(), spiObj.getSpi(),
                        aesKey, senderPort);
                if (exitCode == 0 && !handoffComplete()) {
                    throw new IOException("原生程序已退出，但 KernelSU 交接未完成，请重启后重试");
                }
                return exitCode;
            }
        }
    }

    public static boolean handoffComplete() {
        return new File("/dev/dfm6").exists()
                && (new File("/dev/dfm7").exists() || new File("/dev/dfme3").exists());
    }

    private static void stageBinary(File source, File destination) throws IOException {
        if (!source.isFile()) throw new IOException(source + " not found");
        try (InputStream input = new FileInputStream(source);
             FileOutputStream output = new FileOutputStream(destination)) {
            byte[] buffer = new byte[8192];
            for (int n; (n = input.read(buffer)) != -1;) output.write(buffer, 0, n);
        }
        if (!destination.setExecutable(true, false)) {
            throw new IOException("unable to mark " + destination + " executable");
        }
    }

    private static int runBinary(File bin, DirtyFragVrReporter reporter, int encapPort,
                                 int spi, byte[] aesKey, int senderPort) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(bin.getAbsolutePath());
        command.add("--encap-port");
        command.add(String.valueOf(encapPort));
        command.add("--sender-port");
        command.add(String.valueOf(senderPort));
        command.add("--spi");
        command.add(Integer.toUnsignedString(spi));
        command.add("--aes-key");
        command.add(hex(aesKey));
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        java.util.concurrent.ScheduledExecutorService timer =
                java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
        java.util.concurrent.atomic.AtomicBoolean timedOut = new java.util.concurrent.atomic.AtomicBoolean();
        java.util.concurrent.ScheduledFuture<?> timeout = timer.schedule(() -> {
            timedOut.set(true);
            process.destroyForcibly();
        }, 150, java.util.concurrent.TimeUnit.SECONDS);
        try {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    process.getInputStream(), StandardCharsets.UTF_8))) {
                for (String line; (line = reader.readLine()) != null; ) {
                    reporter.report(line + "\n");
                }
            }
            int exit = process.waitFor();
            if (timedOut.get()) throw new IOException("提权超时，请重启设备后重试");
            return exit;
        } finally {
            timeout.cancel(false);
            timer.shutdownNow();
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    private static String hex(byte[] value) {
        StringBuilder out = new StringBuilder(value.length * 2);
        for (byte item : value) out.append(String.format("%02x", item & 0xff));
        return out.toString();
    }
}
