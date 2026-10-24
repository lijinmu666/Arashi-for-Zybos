package com.arashi.xposed.hooks;

import android.content.Context;
import android.content.Intent;
import android.util.ArraySet;
import android.util.Log;

import io.github.libxposed.api.XposedModule;

import com.arashi.xposed.ConfigManager;
import com.arashi.xposed.HookConfig;
import com.arashi.xposed.Reflect;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;


public final class InstallHooks {

    private static final String TAG = "[Arashi-Install]";

    private static volatile Class<?> installPkgHelperClass;
    private static volatile Object zybServiceInstance;

    private InstallHooks() {
    }

    
    
    

    public static void hookSystemServer(XposedModule mod, ClassLoader cl) {
        
        hookBooleanProp(mod, cl);

        
        hookZybosNoArg(mod, cl, "isDisallowInstallPackage", false);

        
        hookZybosStringArg(mod, cl, "isSilentInstallDisallow", false);

        
        hookHandleInstallEvent(mod, cl);

        
        hookInstallPackageHelper(mod, cl);

        
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
                try {
                    if (ConfigManager.isEnabled(HookConfig.KEY_SHELL_INSTALL)
                            && "sys.allow.install.package".equals(chain.getArg(0))) {
                        Log.i(TAG, "sys.allow.install.package -> true");
                        return true;
                    }
                } catch (Throwable ignored) {
                }
                Object result = chain.proceed();
                clearBlacklistsIfEnabled();
                return result;
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
            installPkgHelperClass = clazz;
            for (Constructor<?> ctor : Reflect.getDeclaredConstructors(clazz)) {
                mod.hook(ctor).intercept(chain -> {
                    Object result = chain.proceed();
                    if (ConfigManager.isEnabled(HookConfig.KEY_CLEAR_BLACKLIST)) {
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
        if (installPkgHelperClass != null) {
            clearBlacklistField(installPkgHelperClass, "mZybBlacklistApp");
        }
        if (zybServiceInstance != null) {
            clearListField(zybServiceInstance, "allowClassList");
            clearListField(zybServiceInstance, "disallowClassList");
            clearListField(zybServiceInstance, "disallowPackageList");
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
