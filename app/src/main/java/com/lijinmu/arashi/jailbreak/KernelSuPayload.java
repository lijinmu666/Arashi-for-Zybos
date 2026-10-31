package com.lijinmu.arashi.jailbreak;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/** Resolves only the KernelSU manager explicitly shown in the jailbreak UI. */
public final class KernelSuPayload {
    public static final String PACKAGE = "me.weishu.kernelsu";
    private static final String ENTRY = "lib/arm64-v8a/libksud.so";
    private KernelSuPayload() { }

    public static void stage(Context context, File destination) throws Exception {
        ApplicationInfo info = context.getPackageManager().getApplicationInfo(PACKAGE, 0);
        File library = info.nativeLibraryDir == null ? null : new File(info.nativeLibraryDir, "libksud.so");
        if (library != null && library.isFile()) {
            try (InputStream input = new FileInputStream(library)) { copy(input, destination); }
            return;
        }
        List<String> apks = new ArrayList<>();
        apks.add(info.sourceDir);
        if (info.splitSourceDirs != null) Collections.addAll(apks, info.splitSourceDirs);
        for (String apk : apks) {
            try (ZipFile zip = new ZipFile(apk)) {
                ZipEntry entry = zip.getEntry(ENTRY);
                if (entry == null) continue;
                try (InputStream input = zip.getInputStream(entry)) { copy(input, destination); }
                return;
            }
        }
        throw new IOException("未找到 KernelSU 的 arm64 ksud，请安装支持越狱模式的官方管理器");
    }

    private static void copy(InputStream input, File destination) throws IOException {
        File temporary = new File(destination.getParentFile(), destination.getName() + ".tmp");
        try {
            try (OutputStream output = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[8192];
                for (int n; (n = input.read(buffer)) != -1;) output.write(buffer, 0, n);
            }
            try (InputStream header = new FileInputStream(temporary)) {
                byte[] elf = new byte[20];
                new DataInputStream(header).readFully(elf);
                if (elf.length != 20 || elf[0] != 0x7f || elf[1] != 'E' || elf[2] != 'L'
                        || elf[3] != 'F' || elf[4] != 2 || elf[5] != 1 || (elf[18] & 255) != 183 || elf[19] != 0)
                    throw new IOException("KernelSU ksud 不是有效的 arm64 ELF 文件");
            }
            if (!temporary.setExecutable(true, false) || !temporary.renameTo(destination))
                throw new IOException("无法准备 KernelSU ksud");
        } finally { temporary.delete(); }
    }
}
