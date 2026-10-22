package com.lijinmu.arashi.xposed.hooks;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Binder;
import android.os.Parcel;
import android.os.Process;
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
    private static final int TRANSACTION_SILENT_INSTALL_APK = 0x19;
    private static final ThreadLocal<Integer> arashiInstallCaller = new ThreadLocal<>();

    private InstallHooks() {
    }

public static void hookSystemServer(XposedModule mod, ClassLoader cl) {
        
        hookBooleanProp(mod, cl);

hookZybosNoArg(mod, cl, "isDisallowInstallPackage", false);

hookZybosStringArg(mod, cl, "isSilentInstallDisallow", false);

hookArashiInstallCaller(mod, cl);

hookHandleInstallEvent(mod, cl);

hookInstallPackageHelper(mod, cl);

hookLegacyPackageManagerBlacklist(mod, cl);

hookZybosServiceCtor(mod, cl);

        Log.i(TAG, "system_server hooks initialized");
    }

    private static void hookArashiInstallCaller(XposedModule mod, ClassLoader cl) {
        try {
            Class<?> service = Class.forName("com.android.server.ZybosSysApiService", false, cl);
            Method onTransact = Reflect.findMethodExact(service, "onTransact",
                    int.class, Parcel.class, Parcel.class, int.class);
            Method enforceCaller = Reflect.findMethodExact(service, "enforceCallerIsSystem");
            if (onTransact == null || enforceCaller == null
                    || enforceCaller.getReturnType() != void.class) {
                Log.i(TAG, "Zybos caller check not present on this system");
                return;
            }
            mod.hook(onTransact).intercept(chain -> {
                if ((Integer) chain.getArg(0) != TRANSACTION_SILENT_INSTALL_APK
                        || !isArashiCaller()) {
                    return chain.proceed();
                }
                Integer previous = arashiInstallCaller.get();
                arashiInstallCaller.set(Binder.getCallingUid());
                try {
                    return chain.proceed();
                } finally {
                    if (previous == null) arashiInstallCaller.remove();
                    else arashiInstallCaller.set(previous);
                }
            });
            mod.hook(enforceCaller).intercept(chain -> {
                Integer caller = arashiInstallCaller.get();
                if (caller != null && caller == Binder.getCallingUid()) {
                    Log.i(TAG, "Arashi silentInstallApk caller accepted, uid=" + caller);
                    return null;
                }
                return chain.proceed();
            });
            Log.i(TAG, "Zybos Arashi install caller hook registered");
        } catch (Throwable error) {
            Log.w(TAG, "Zybos Arashi install caller hook unavailable", error);
        }
    }

    private static boolean isArashiCaller() {
        int caller = Binder.getCallingUid();
        if (caller < Process.FIRST_APPLICATION_UID) return false;
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Object thread = Reflect.callStaticMethod(activityThread, "currentActivityThread");
            Context context = (Context) Reflect.callMethod(thread, "getSystemContext");
            if (context == null) return false;
            String[] packages = context.getPackageManager().getPackagesForUid(caller);
            if (packages == null) return false;
            for (String name : packages) {
                if ("com.lijinmu.arashi".equals(name)) return true;
            }
            return false;
        } catch (Throwable error) {
            Log.w(TAG, "Unable to verify Arashi install caller", error);
            return false;
        }
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
        hookInstallerBool(mod, cl, "isInstallRequestFromUnknownSource", false, Intent.class);
        hookInstallerBool(mod, cl, "hasInstallApplication", true, String.class);
        hookInstallerBool(mod, cl, "isConflictedOverlayInstallApk", false,
                String.class, String.class);
        Log.i(TAG, "PackageInstaller hooks initialized");
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
                && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                && ConfigManager.isEnabled(HookConfig.KEY_ANDROID12_BLACKLIST_HOOK);
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
