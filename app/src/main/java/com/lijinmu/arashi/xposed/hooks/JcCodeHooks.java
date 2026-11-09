package com.lijinmu.arashi.xposed.hooks;

import android.content.Context;
import android.util.Log;
import com.lijinmu.arashi.xposed.ConfigManager;
import com.lijinmu.arashi.xposed.HookConfig;
import io.github.libxposed.api.XposedModule;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicBoolean;

/** Best-effort hooks for the specific JC Code V4 APK analysed in November 2026. */
public final class JcCodeHooks {
    private static final String TAG = "[Arashi-JC]";
    private static final AtomicBoolean PAYLOAD_HOOKED = new AtomicBoolean();

    private JcCodeHooks() {}

    public static void install(XposedModule module, ClassLoader loader) {
        hookNetwork(module);
        try {
            Class<?> shell = Class.forName("com.jc.shell.ShellLoader", false, loader);
            Method install = shell.getDeclaredMethod("install", Context.class);
            install.setAccessible(true);
            module.hook(install).intercept(chain -> {
                Object result = chain.proceed();
                if (result instanceof ClassLoader && PAYLOAD_HOOKED.compareAndSet(false, true)) {
                    hookPayload(module, (ClassLoader) result);
                }
                return result;
            });
            Log.i(TAG, "Shell loader hook installed");
        } catch (Throwable error) {
            Log.w(TAG, "Shell loader hook unavailable", error);
        }
    }

    private static void hookPayload(XposedModule module, ClassLoader loader) {
        hookResult(module, loader, "com.jc.code.v3.MainActivity", "Qns", true);
        hookResult(module, loader, "com.jc.code.v3.MainActivity", "c", true);
        hookResult(module, loader, "com.jc.code.v3.MainActivity", "b", null);
        hookResult(module, loader, "com.jc.code.v3.Crypt", "verifyApk", true, Context.class);
        hookResult(module, loader, "com.jc.code.v3.Crypt", "verifyApk2", true, Context.class);
        hookResult(module, loader, "com.jc.code.v3.Crypt", "verifyApk3", true, Context.class);
        hookResult(module, loader, "com.jc.code.v3.GateNative", "h", true, Context.class);
    }

    private static void hookResult(XposedModule module, ClassLoader loader, String owner,
                                   String name, Object result, Class<?>... parameters) {
        try {
            Class<?> type = Class.forName(owner, false, loader);
            Method method = type.getDeclaredMethod(name, parameters);
            if (result == null && method.getReturnType().isPrimitive()) return;
            if (result instanceof Boolean && method.getReturnType() != boolean.class) return;
            method.setAccessible(true);
            module.hook(method).intercept(chain -> {
                if (!ConfigManager.isEnabled(HookConfig.KEY_JC_CHECKS)) return chain.proceed();
                return result;
            });
            Log.i(TAG, "Hook installed: " + owner + "#" + name);
        } catch (Throwable error) {
            Log.w(TAG, "Hook unavailable: " + owner + "#" + name, error);
        }
    }

    private static void hookNetwork(XposedModule module) {
        hookConnection(module, Socket.class, "connect", SocketAddress.class);
        hookConnection(module, Socket.class, "connect", SocketAddress.class, int.class);
        hookConnection(module, DatagramSocket.class, "send", DatagramPacket.class);
        hookConnection(module, DatagramSocket.class, "connect", SocketAddress.class);
    }

    private static void hookConnection(XposedModule module, Class<?> owner, String name,
                                       Class<?>... parameters) {
        try {
            Method method = owner.getDeclaredMethod(name, parameters);
            method.setAccessible(true);
            module.hook(method).intercept(chain -> {
                if (ConfigManager.isEnabled(HookConfig.KEY_JC_NETWORK)) {
                    throw new IOException("JC Code V4 network blocked by Arashi");
                }
                return chain.proceed();
            });
            Log.i(TAG, "Network hook installed: " + owner.getName() + "#" + name);
        } catch (Throwable error) {
            Log.w(TAG, "Network hook unavailable: " + owner.getName() + "#" + name, error);
        }
    }
}
