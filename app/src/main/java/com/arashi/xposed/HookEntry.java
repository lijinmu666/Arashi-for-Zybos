package com.arashi.xposed;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam;
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam;

import com.arashi.xposed.hooks.AdbHideHooks;
import com.arashi.xposed.hooks.CaptivePortalHooks;
import com.arashi.xposed.hooks.InstallHooks;

import java.lang.reflect.Method;









public final class HookEntry extends XposedModule {

    private static final String TAG = "[Arashi]";
    private static final String BUILD_TAG = "v2-libxposed102";
    private static final String PKG_SELF = "com.arashi";
    private static final String PKG_INSTALLER = "com.android.packageinstaller";
    private static final String PKG_CAPTIVE = "com.android.captiveportallogin";

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        log("=== libxposed102 BUILD " + BUILD_TAG + " ===");
        try {
            log("module loaded, process=" + param.getProcessName()
                    + " systemServer=" + param.isSystemServer());
        } catch (Throwable t) {
            log("onModuleLoaded param error: " + t);
        }
    }

    @Override
    public void onSystemServerStarting(SystemServerStartingParam param) {
        log("onSystemServerStarting");
        ClassLoader cl = param.getClassLoader();
        ConfigManager.init("android");
        InstallHooks.hookSystemServer(this, cl);
        AdbHideHooks.hookSystemServer(this, cl);
        log("system_server hooks complete");
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        String pkg = param.getPackageName();
        log("onPackageReady: " + pkg);
        if (PKG_SELF.equals(pkg)) {
            hookSelfActivation(param.getClassLoader());
        } else if (PKG_INSTALLER.equals(pkg)) {
            ConfigManager.init(pkg);
            InstallHooks.hookPackageInstaller(this, param.getClassLoader());
        } else if (PKG_CAPTIVE.equals(pkg)) {
            ConfigManager.init(pkg);
            CaptivePortalHooks.hookCaptivePortal(this, param.getClassLoader());
        }
    }

    
    private void hookSelfActivation(ClassLoader cl) {
        try {
            Method m = cl.loadClass("com.arashi.Main")
                    .getDeclaredMethod("isModuleActive");
            m.setAccessible(true);
            hook(m).intercept(chain -> true);
            log("module activation hook installed");
        } catch (Throwable t) {
            log("activation hook failed: " + t);
        }
    }

    
    private void log(String msg) {
        android.util.Log.i(TAG, msg);
        try {
            log(android.util.Log.INFO, TAG, msg);
        } catch (Throwable ignored) {
            
        }
    }
}
