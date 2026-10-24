package com.arashi.xposed;

import android.os.SystemClock;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ProcUtil {

private static final long CACHE_TTL_MS = 1000;

private static final int CACHE_MAX = 64;

    private static final Map<String, Entry> CACHE = new ConcurrentHashMap<>();

    private static final class Entry {
        final boolean matches;
        final long expireAt;

        Entry(boolean matches, long expireAt) {
            this.matches = matches;
            this.expireAt = expireAt;
        }
    }

    private ProcUtil() {
    }

public static boolean isProcess(int pid, String name) {
        if (pid <= 0 || name == null) {
            return false;
        }
        String key = pid + ":" + name;
        long now = SystemClock.elapsedRealtime();
        Entry cached = CACHE.get(key);
        if (cached != null && now < cached.expireAt) {
            return cached.matches;
        }
        boolean matches = readCmdline(pid, name);
        if (CACHE.size() >= CACHE_MAX) {
            CACHE.clear();
        }
        CACHE.put(key, new Entry(matches, now + CACHE_TTL_MS));
        return matches;
    }

    private static boolean readCmdline(int pid, String name) {
        InputStream in = null;
        try {
            in = new FileInputStream("/proc/" + pid + "/cmdline");
            byte[] buffer = new byte[256];
            int length = in.read(buffer);
            if (length <= 0) {
                return false;
            }
            int end = 0;
            while (end < length && buffer[end] != 0) {
                end++;
            }
            if (end == 0) {
                return false;
            }
            String cmdline = new String(buffer, 0, end, "UTF-8");
            return name.equals(cmdline) || cmdline.startsWith(name + ":");
        } catch (Throwable t) {
            return false;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }
}
