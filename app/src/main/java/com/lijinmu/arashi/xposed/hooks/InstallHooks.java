package com.lijinmu.arashi.xposed.hooks;

import android.content.Context;
import android.content.Intent;
import android.util.ArraySet;
import android.util.Log;

import io.github.libxposed.api.XposedModule;

import com.lijinmu.arashi.xposed.ConfigManager;
import com.lijinmu.arashi.xposed.HookConfig;
import com.lijinmu.arashi.xposed.Reflect;

import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public final class InstallHooks {

    private static final String TAG = "[Arashi-Install]";

    private static volatile Object zybServiceInstance;

    private InstallHooks() {
    }

public static void hookSystemServer(XposedModule mod, ClassLoader cl) {
        
        hookInstallerAuthorization(mod, cl);
        hookPackageManagerShellCommand(mod, cl);
        hookShellSessionCreation(mod, cl);

hookZybosNoArg(mod, cl, "isDisallowInstallPackage", false);

hookZybosStringArg(mod, cl, "isSilentInstallDisallow", false);

hookHandleInstallEvent(mod, cl);

hookInstallPackageHelper(mod, cl);

hookLegacyPackageManagerBlacklist(mod, cl);

hookZybosServiceCtor(mod, cl);

        Log.i(TAG, "system_server hooks initialized");
    }

    /** Only mask the installer name for the vendor authorization branch.
     * The original source and all subsequent installation verification stay intact.
     */
    private static void hookInstallerAuthorization(XposedModule mod, ClassLoader cl) {
        try {
            Class<?> session = Class.forName("com.android.server.pm.PackageInstallerSession", false, cl);
            Method install = Reflect.findMethodExact(session, "handleInstall");
            Method privileged = Reflect.findMethodExact(session, "isZybInstallerPrivileged");
            Method installer = Reflect.findMethodExact(session, "getInstallerPackageName");
            if (install == null || privileged == null || installer == null) {
                Log.i(TAG, "Installer authorization guard absent on this firmware");
                return;
            }
            // The two small vendor methods may have been inlined into handleInstall.
            mod.deoptimize(install);
            ThreadLocal<Object> active = new ThreadLocal<>();
            ThreadLocal<Object> maskNextInstaller = new ThreadLocal<>();
            mod.hook(installer).intercept(chain -> {
                if (chain.getThisObject() == maskNextInstaller.get()) {
                    maskNextInstaller.remove();
                    return null;
                }
                return chain.proceed();
            });
            mod.hook(privileged).intercept(chain -> {
                if (chain.getThisObject() == active.get()) {
                    maskNextInstaller.set(chain.getThisObject());
                    return true;
                }
                return chain.proceed();
            });
            mod.hook(install).intercept(chain -> {
                // Keep the broad feature's version restriction, while Shell enablement
                // independently covers authorization for sessions created by UID 2000.
                if (!ConfigManager.isEnabled(HookConfig.KEY_DISABLE_INSTALLER_AUTH)
                        && !isShellSession(chain.getThisObject())) return chain.proceed();
                Object previous = active.get();
                Object previousMask = maskNextInstaller.get();
                active.set(chain.getThisObject());
                try {
                    return chain.proceed();
                } finally {
                    if (previous == null) active.remove(); else active.set(previous);
                    if (previousMask == null) maskNextInstaller.remove(); else maskNextInstaller.set(previousMask);
                }
            });
            Log.i(TAG, "Installer authorization hooks registered");
        } catch (Throwable t) {
            Log.w(TAG, "Installer authorization hook unavailable: " + t);
        }
    }

    private static boolean isShellSession(Object session) {
        if (session == null || !ConfigManager.isEnabled(HookConfig.KEY_SHELL_INSTALL)) return false;
        try {
            // The installation worker's Binder UID is not the original installer UID.
            return Integer.valueOf(2000).equals(Reflect.callMethod(session, "getInstallerUid"));
        } catch (Throwable error) {
            Log.w(TAG, "Cannot identify Shell installation session", error);
            return false;
        }
    }

    /**
     * Use the PackageManager Shell command dispatcher as the single compatibility
     * point for AIbangOS 7.10 and 7.13. This works whether the vendor gate reads
     * sys.allow.install.package or has already compiled the value to a constant.
     * Only commands blocked by the vendor gate are dispatched directly.
     */
    private static void hookPackageManagerShellCommand(XposedModule mod, ClassLoader cl) {
        try {
            Class<?> shellCommand = Class.forName(
                    "com.android.server.pm.PackageManagerShellCommand", false, cl);
            Method onCommand = Reflect.findMethodExact(shellCommand, "onCommand", String.class);
            if (onCommand == null) {
                Log.w(TAG, "PackageManagerShellCommand.onCommand not found");
                return;
            }
            Map<String, Method> handlers = new LinkedHashMap<>();
            String[][] commands = {
                    {"install", "runInstall"}, {"install-streaming", "runStreamingInstall"},
                    {"install-incremental", "runIncrementalInstall"},
                    {"install-commit", "runInstallCommit"}, {"install-create", "runInstallCreate"}
            };
            for (String[] command : commands) {
                Method method = Reflect.findMethodExact(shellCommand, command[1]);
                if (method != null && method.getReturnType() != int.class) method = null;
                handlers.put(command[0], method);
                if (method == null) Log.w(TAG, command[1] + " handler not found");
            }
            mod.hook(onCommand).intercept(chain -> {
                if (!ConfigManager.isEnabled(HookConfig.KEY_SHELL_INSTALL)) return chain.proceed();
                String command = (String) chain.getArg(0);
                if (!handlers.containsKey(command)) return chain.proceed();
                Method handler = handlers.get(command);
                if (handler == null) {
                    Log.i(TAG, command + " dispatched through session compatibility implementation");
                    return ShellInstallCompat.run(chain.getThisObject(), command);
                }
                try {
                    Log.i(TAG, command + " dispatched through " + handler.getName());
                    return handler.invoke(chain.getThisObject());
                } catch (Throwable error) {
                    Throwable cause = error instanceof InvocationTargetException
                            ? ((InvocationTargetException) error).getCause() : error;
                    // The handler can consume arguments or create a session before failing.
                    // Do not invoke it again through the original, restricted dispatcher.
                    return reportShellInstallFailure(chain.getThisObject(), command, cause);
                }
            });
            deoptimizeNamed(mod, shellCommand, "onCommand");
            try {
                deoptimizeNamed(mod, Class.forName("com.android.server.pm.PackageManagerService", false, cl),
                        "onShellCommand");
            } catch (Throwable error) { Log.w(TAG, "Shell dispatcher caller unavailable", error); }
            Log.i(TAG, "PackageManagerShellCommand install gate hook registered");
        } catch (Throwable error) { Log.w(TAG, "PackageManagerShellCommand hook failed", error); }
    }

    private static int reportShellInstallFailure(Object shell, String command, Throwable error) {
        Log.w(TAG, command + " direct dispatch failed", error);
        try {
            Object writer = Reflect.callMethod(shell, "getErrPrintWriter");
            if (writer instanceof PrintWriter) {
                ((PrintWriter) writer).println("Arashi: " + command + " failed: " + error);
                ((PrintWriter) writer).flush();
            }
        } catch (Throwable outputError) { Log.w(TAG, "Cannot write Shell error", outputError); }
        return 1;
    }

    /** Vendor firmware rejects Shell before an installation session is created. */
    private static void hookShellSessionCreation(XposedModule mod, ClassLoader cl) {
        try {
            Class<?> service = Class.forName("com.android.server.pm.PackageInstallerService", false, cl);
            boolean registered = false;
            for (Method gate : service.getDeclaredMethods()) {
                if (!"enforceZybInstallerAllowed".equals(gate.getName())
                        || gate.getReturnType() != void.class) continue;
                Class<?>[] parameters = gate.getParameterTypes();
                // 9.1.2 prepends a Computer snapshot; UID remains the first int.
                final int uidIndex;
                if (parameters.length == 4) uidIndex = 0;
                else if (parameters.length == 5
                        && "com.android.server.pm.Computer".equals(parameters[0].getName())) uidIndex = 1;
                else continue;
                if (parameters[uidIndex] != int.class || parameters[uidIndex + 1] != int.class
                        || parameters[uidIndex + 2] != String.class
                        || parameters[uidIndex + 3] != String.class) continue;
                mod.hook(gate).intercept(chain -> {
                    if (ConfigManager.isEnabled(HookConfig.KEY_SHELL_INSTALL)
                            && Integer.valueOf(2000).equals(chain.getArg(uidIndex))) return null;
                    return chain.proceed();
                });
                registered = true;
            }
            if (!registered) {
                Log.i(TAG, "Shell session-creation guard absent on this firmware");
                return;
            }
            deoptimizeNamed(mod, service, "createSessionInternal");
        } catch (Throwable error) { Log.w(TAG, "Shell session-creation hook unavailable", error); }
    }

    private static void deoptimizeNamed(XposedModule mod, Class<?> owner, String name) {
        for (Method method : owner.getDeclaredMethods()) {
            if (name.equals(method.getName())) {
                try { mod.deoptimize(method); }
                catch (Throwable error) { Log.w(TAG, "Cannot deoptimize " + method, error); }
            }
        }
    }

    private static void hookZybosNoArg(XposedModule mod, ClassLoader cl,
            String method, boolean result) {
        try {
            Class<?> clz = Class.forName("com.android.server.ZybosSysApiService", false, cl);
            Method m = Reflect.findMethodExact(clz, method);
            if (m == null) {
                Log.w(TAG, method + " not found");
                return;
            }
            mod.hook(m).intercept(chain -> {
                if (ConfigManager.isEnabled(HookConfig.KEY_SHELL_INSTALL)) {
                    Log.i(TAG, method + " -> " + result);
                    return result;
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            Log.w(TAG, method + " hook failed: " + t);
        }
    }

    private static void hookZybosStringArg(XposedModule mod, ClassLoader cl,
            String method, boolean result) {
        try {
            Class<?> clz = Class.forName("com.android.server.ZybosSysApiService", false, cl);
            Method m = Reflect.findMethodExact(clz, method, String.class);
            if (m == null) {
                Log.w(TAG, method + " not found");
                return;
            }
            mod.hook(m).intercept(chain -> {
                if (ConfigManager.isEnabled(HookConfig.KEY_SHELL_INSTALL)) {
                    Log.i(TAG, method + " -> " + result);
                    return result;
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            Log.w(TAG, method + " hook failed: " + t);
        }
    }

    private static void hookHandleInstallEvent(XposedModule mod, ClassLoader cl) {
        try {
            Class<?> clz = Class.forName("com.android.server.ZybosSysApiService", false, cl);
            Method m = Reflect.findMethodExact(clz, "handleInstallEvent",
                    Context.class, Intent.class);
            if (m == null) {
                Log.w(TAG, "handleInstallEvent not found");
                return;
            }
            mod.hook(m).intercept(chain -> {
                if (ConfigManager.isEnabled(HookConfig.KEY_SHELL_INSTALL)) {
                    Log.i(TAG, "handleInstallEvent blocked");
                    return null;
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            Log.w(TAG, "handleInstallEvent hook failed: " + t);
        }
    }

    private static void hookInstallPackageHelper(XposedModule mod, ClassLoader cl) {
        hookBlacklistOwner(mod, cl, "com.android.server.pm.InstallPackageHelper");
    }

    private static void hookLegacyPackageManagerBlacklist(XposedModule mod, ClassLoader cl) {
        // Actual vendor class layout, not SDK level, determines the owner.
        hookBlacklistOwner(mod, cl, "com.android.server.pm.PackageManagerService");
    }

    private static void hookBlacklistOwner(XposedModule mod, ClassLoader cl, String owner) {
        try {
            Class<?> clazz = Class.forName(owner, false, cl);
            Field field = clazz.getDeclaredField("mZybBlacklistApp");
            if (!Modifier.isStatic(field.getModifiers())
                    || !ArraySet.class.isAssignableFrom(field.getType())) return;
            for (Constructor<?> ctor : Reflect.getDeclaredConstructors(clazz)) {
                mod.hook(ctor).intercept(chain -> {
                    Object result = chain.proceed();
                    if (ConfigManager.isEnabled(HookConfig.KEY_CLEAR_BLACKLIST))
                        clearBlacklistField(clazz, "mZybBlacklistApp");
                    return result;
                });
            }
            Log.i(TAG, owner + " blacklist hook registered");
        } catch (ClassNotFoundException | NoSuchFieldException absent) {
            Log.i(TAG, owner + " vendor installation blacklist absent");
        } catch (Throwable error) { Log.w(TAG, owner + " blacklist hook failed", error); }
    }

    private static void hookZybosServiceCtor(XposedModule mod, ClassLoader cl) {
        try {
            Class<?> clz = Class.forName("com.android.server.ZybosSysApiService", false, cl);
            for (Constructor<?> ctor : Reflect.getDeclaredConstructors(clz)) {
                mod.hook(ctor).intercept(chain -> {
                    Object result = chain.proceed();
                    zybServiceInstance = chain.getThisObject();
                    if (ConfigManager.isEnabled(HookConfig.KEY_CLEAR_BLACKLIST)) {
                        clearListField(zybServiceInstance, "allowClassList");
                        clearListField(zybServiceInstance, "disallowClassList");
                        clearListField(zybServiceInstance, "disallowPackageList");
                        Log.i(TAG, "ZybosSysApiService disallow lists cleared");
                    }
                    return result;
                });
            }
        } catch (Throwable t) {
            Log.w(TAG, "ZybosSysApiService constructor hook failed: " + t);
        }
    }

public static void hookPackageInstaller(XposedModule mod, ClassLoader cl) {
        hookInstallerEnvironment(mod, cl);
        hookInstallerErrorText(mod);
        hookInstallerBool(mod, cl, "isInstallRequestFromUnknownSource", false, Intent.class);
        hookInstallerBool(mod, cl, "hasInstallApplication", true, String.class);
        hookInstallerBool(mod, cl, "isConflictedOverlayInstallApk", false,
                String.class, String.class);
        Log.i(TAG, "PackageInstaller hooks initialized");
    }


    private static final ThreadLocal<Boolean> installerResume = new ThreadLocal<>();

    private static void hookInstallerEnvironment(XposedModule mod, ClassLoader cl) {
        try {
            Class<?> activity = Class.forName(
                    "com.android.packageinstaller.PackageInstallerActivity", false, cl);
            Method resume = Reflect.findMethodExact(activity, "onResume");
            Method setting = android.provider.Settings.Global.class.getDeclaredMethod(
                    "getInt", android.content.ContentResolver.class, String.class, int.class);
            if (resume != null) {
                mod.hook(resume).intercept(chain -> {
                    Boolean previous = installerResume.get();
                    installerResume.set(Boolean.TRUE);
                    try { return chain.proceed(); }
                    finally {
                        if (previous == null) installerResume.remove();
                        else installerResume.set(previous);
                    }
                });
                mod.hook(setting).intercept(chain -> {
                    if (Boolean.TRUE.equals(installerResume.get())
                            // Installer 4.3 renamed the self-update-only gate. Keep both
                            // variants scoped to onResume; do not change stored settings.
                            && ("special_enable_adb_debug".equals(chain.getArg(1))
                                || "zpad_prop_special_settings".equals(chain.getArg(1)))
                            && ConfigManager.isEnabled(HookConfig.KEY_EXTERNAL_INSTALL)) return 1;
                    return chain.proceed();
                });
            }
        } catch (Throwable error) {
            Log.w(TAG, "Installer settings compatibility unavailable", error);
        }
        try {
            Class<?> properties = Class.forName("android.os.SystemProperties", false, cl);
            Method get = Reflect.findMethodExact(properties, "get", String.class);
            if (get != null) mod.hook(get).intercept(chain -> {
                if ("ro.build.type".equals(chain.getArg(0))
                        && ConfigManager.isEnabled(HookConfig.KEY_EXTERNAL_INSTALL)) {
                    // Only the vendor installer's static debug gate, not other readers.
                    for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
                        if ("com.android.packageinstaller.PackageInstallerActivity".equals(frame.getClassName())
                                && "<clinit>".equals(frame.getMethodName())) return "userdebug";
                    }
                }
                return chain.proceed();
            });
        } catch (Throwable error) {
            Log.w(TAG, "Installer build-type compatibility unavailable", error);
        }
    }

    private static void hookInstallerErrorText(XposedModule mod) {
        try {
            Method getText = android.content.res.Resources.class.getDeclaredMethod("getText", int.class);
            mod.hook(getText).intercept(chain -> {
                if (!ConfigManager.isEnabled(HookConfig.KEY_EXTERNAL_INSTALL)) return chain.proceed();
                android.content.res.Resources resources =
                        (android.content.res.Resources) chain.getThisObject();
                int id = (Integer) chain.getArg(0);
                String replacement = null;
                try {
                    if ("com.android.packageinstaller".equals(resources.getResourcePackageName(id))) {
                        String name = resources.getResourceEntryName(id);
                        if ("Parse_error_dlg_text".equals(name))
                            replacement = "解析软件包时出现问题。";
                        else if ("install_failed_invalid_apk".equals(name))
                            replacement = "应用未安装：软件包似乎无效。";
                    }
                } catch (android.content.res.Resources.NotFoundException ignored) {}
                return replacement != null ? replacement : chain.proceed();
            });
        } catch (Throwable error) {
            Log.w(TAG, "Installer error text restoration unavailable", error);
        }
    }

    private static void hookInstallerBool(XposedModule mod, ClassLoader cl, String method,
            boolean result, Class<?>... params) {
        try {
            Class<?> clz = Class.forName(
                    "com.android.packageinstaller.PackageInstallerActivity", false, cl);
            Method m = Reflect.findMethodExact(clz, method, params);
            if (m == null) {
                Log.w(TAG, method + " not found");
                return;
            }
            mod.hook(m).intercept(chain -> {
                if (ConfigManager.isEnabled(HookConfig.KEY_EXTERNAL_INSTALL)) {
                    Log.i(TAG, method + " -> " + result);
                    return result;
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            Log.w(TAG, method + " hook failed: " + t);
        }
    }

    @SuppressWarnings("unchecked")
    private static void clearBlacklistField(Class<?> clazz, String fieldName) {
        try {
            ArraySet<String> bl =
                    (ArraySet<String>) Reflect.getStaticObjectField(clazz, fieldName);
            if (bl != null && !bl.isEmpty()) {
                bl.clear();
            }
        } catch (Throwable ignored) {
        }
    }

    private static void clearListField(Object instance, String fieldName) {
        try {
            Object o = Reflect.getObjectField(instance, fieldName);
            if (o instanceof ArrayList) {
                ArrayList<?> list = (ArrayList<?>) o;
                if (!list.isEmpty()) {
                    list.clear();
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
