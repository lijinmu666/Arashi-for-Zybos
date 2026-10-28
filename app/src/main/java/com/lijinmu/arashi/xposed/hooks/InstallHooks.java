package com.lijinmu.arashi.xposed.hooks;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.ArraySet;
import android.util.Log;

import io.github.libxposed.api.XposedModule;

import com.lijinmu.arashi.xposed.ConfigManager;
import com.lijinmu.arashi.xposed.HookConfig;
import com.lijinmu.arashi.xposed.Reflect;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;

public final class InstallHooks {

    private static final String TAG = "[Arashi-Install]";

    private static volatile Class<?> installPkgHelperClass;
    private static volatile Class<?> legacyPackageManagerClass;
    private static volatile Object zybServiceInstance;

    private InstallHooks() {
    }

public static void hookSystemServer(XposedModule mod, ClassLoader cl) {
        
        hookBooleanProp(mod, cl);

hookZybosNoArg(mod, cl, "isDisallowInstallPackage", false);

hookZybosStringArg(mod, cl, "isSilentInstallDisallow", false);

hookHandleInstallEvent(mod, cl);

hookInstallPackageHelper(mod, cl);

hookLegacyPackageManagerBlacklist(mod, cl);

hookZybosServiceCtor(mod, cl);

        Log.i(TAG, "system_server hooks initialized");
    }

    private static void hookBooleanProp(XposedModule mod, ClassLoader cl) {
        try {
            Class<?> sysProp = Class.forName("android.os.SystemProperties", false, cl);
            Method m = Reflect.findMethodExact(sysProp, "getBoolean",
                    String.class, boolean.class);
            if (m == null) {
                Log.w(TAG, "SystemProperties.getBoolean not found");
                return;
            }
            mod.hook(m).intercept(chain -> {
                if (!"sys.allow.install.package".equals(chain.getArg(0))) {
                    return chain.proceed();
                }
                clearBlacklistsIfEnabled();
                try {
                    if (ConfigManager.isEnabled(HookConfig.KEY_SHELL_INSTALL)) {
                        Log.i(TAG, "sys.allow.install.package -> true");
                        return true;
                    }
                } catch (Throwable ignored) {
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            Log.w(TAG, "SystemProperties hook failed: " + t);
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
        try {
            Class<?> clazz = Class.forName(
                    "com.android.server.pm.InstallPackageHelper", false, cl);
            for (Constructor<?> ctor : Reflect.getDeclaredConstructors(clazz)) {
                mod.hook(ctor).intercept(chain -> {
                    Object result = chain.proceed();
                    installPkgHelperClass = clazz;
                    if (ConfigManager.isEnabled(HookConfig.KEY_CLEAR_BLACKLIST)
                            && !useAndroid12BlacklistHook()) {
                        clearBlacklistField(clazz, "mZybBlacklistApp");
                    }
                    return result;
                });
            }
            Log.i(TAG, "InstallPackageHelper blacklist hook registered");
        } catch (Throwable t) {
            Log.w(TAG, "InstallPackageHelper hook failed: " + t);
        }
    }

    private static void hookLegacyPackageManagerBlacklist(XposedModule mod, ClassLoader cl) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        try {
            Class<?> clazz = Class.forName("com.android.server.pm.PackageManagerService", false, cl);
            Field field = clazz.getDeclaredField("mZybBlacklistApp");
            if (!Modifier.isStatic(field.getModifiers())
                    || !ArraySet.class.isAssignableFrom(field.getType())) {
                return;
            }
            for (Constructor<?> ctor : Reflect.getDeclaredConstructors(clazz)) {
                mod.hook(ctor).intercept(chain -> {
                    Object result = chain.proceed();
                    legacyPackageManagerClass = clazz;
                    clearLegacyBlacklistIfEnabled();
                    return result;
                });
            }
            Log.i(TAG, "AIBangOS Android 12 PackageManager blacklist hook registered");
        } catch (Throwable t) {
            Log.w(TAG, "AIBangOS Android 12 blacklist hook unavailable: " + t);
        }
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
                            && "special_enable_adb_debug".equals(chain.getArg(1))
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

private static void clearBlacklistsIfEnabled() {
        if (!ConfigManager.isEnabled(HookConfig.KEY_CLEAR_BLACKLIST)) {
            return;
        }
        if (useAndroid12BlacklistHook()) {
            clearLegacyBlacklistIfEnabled();
        } else if (installPkgHelperClass != null) {
            clearBlacklistField(installPkgHelperClass, "mZybBlacklistApp");
        }
        if (zybServiceInstance != null) {
            clearListField(zybServiceInstance, "allowClassList");
            clearListField(zybServiceInstance, "disallowClassList");
            clearListField(zybServiceInstance, "disallowPackageList");
        }
    }

    private static void clearLegacyBlacklistIfEnabled() {
        if (legacyPackageManagerClass != null
                && useAndroid12BlacklistHook()
                && ConfigManager.isEnabled(HookConfig.KEY_CLEAR_BLACKLIST)) {
            clearBlacklistField(legacyPackageManagerClass, "mZybBlacklistApp");
        }
    }

    private static boolean useAndroid12BlacklistHook() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU;
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
