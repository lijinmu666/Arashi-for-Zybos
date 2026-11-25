package com.lijinmu.arashi.xposed.hooks;

import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.VersionedPackage;
import android.os.Binder;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.os.SystemClock;
import android.util.Log;

import com.lijinmu.arashi.xposed.Reflect;
import com.lijinmu.arashi.xposed.SettingsProvider;
import com.lijinmu.arashi.uninstall.UninstallConfig;
import com.lijinmu.arashi.uninstall.UninstallScreenState;
import com.lijinmu.arashi.uninstall.UninstallHistoryStore;

import java.io.FileInputStream;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.libxposed.api.XposedModule;

public final class UninstallHooks {
    private static final String TAG = "[Arashi-PackageManagement]";
    private static final int DELETE_KEEP_DATA = 1;
    private static final ThreadLocal<Boolean> KEEP_DATA = new ThreadLocal<>();
    private static final ThreadLocal<String> CLEARING_PACKAGE = new ThreadLocal<>();
    private static final String DENIED_MESSAGE = "Arashi 已拒绝此操作";
    private static final ThreadLocal<ShellDenial> SHELL_DENIAL = new ThreadLocal<>();
    private static final Semaphore PROMPT_SLOT = new Semaphore(1);
    private static final ThreadLocal<Boolean> INSTALLER_CALL = new ThreadLocal<>();
    private static final Map<Object, String> APPROVED_OBSERVERS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private UninstallHooks() {
    }

    public static void hookSystemServer(XposedModule module, ClassLoader loader) {
        hookObserverAdapter(module, loader);
        hookInstaller(module, loader);
        hookZybos(module, loader);
        hookPackageManager(module, loader);
        hookFreeze(module, loader);
        hookPackageOperations(module, loader);
        hookClearActivityManager(module, loader);
        hookShellResponse(module, loader);
        hookDeleteFlags(module, loader, "com.android.server.pm.PackageManagerService");
        hookDeleteFlags(module, loader, "com.android.server.pm.DeletePackageHelper");
    }

    private static void hookFreeze(XposedModule module, ClassLoader loader) {
        try {
            Class<?> owner = Class.forName("com.android.server.pm.PackageManagerService", false, loader);
            int count = 0;
            for (Method method : owner.getDeclaredMethods()) {
                Class<?>[] types = method.getParameterTypes();
                boolean single = "setEnabledSetting".equals(method.getName())
                        && Arrays.equals(types, new Class<?>[] { String.class,
                        String.class, int.class, int.class, int.class, String.class });
                boolean batch = "setEnabledSettings".equals(method.getName())
                        && types.length == 3 && List.class.isAssignableFrom(types[0])
                        && types[1] == int.class && types[2] == String.class;
                if (!single && !batch) continue;
                method.setAccessible(true);
                module.hook(method).intercept(chain -> {
                    Context context = systemContext();
                    if (context == null) return chain.proceed();
                    if (single) {
                        // Component state changes are not whole-application freezing.
                        if (chain.getArg(1) == null) {
                            int state = (Integer) chain.getArg(2);
                            if (isDisabledState(state)) {
                                Decision decision = evaluateRequest(context, Binder.getCallingUid(),
                                        (String) chain.getArg(0), (String) chain.getArg(5),
                                        UninstallConfig.FREEZE, state, (Integer) chain.getArg(4));
                                if (!decision.allowed) return denyFreeze(decision);
                            }
                        }
                    } else {
                        List<?> settings = (List<?>) chain.getArg(0);
                        if (settings != null) {
                            for (Object setting : settings) {
                                if (setting == null || Reflect.callMethod(setting, "getComponentName") != null) continue;
                                int state = (Integer) Reflect.callMethod(setting, "getEnabledState");
                                if (!isDisabledState(state)) continue;
                                Decision decision = evaluateRequest(context, Binder.getCallingUid(),
                                        (String) Reflect.callMethod(setting, "getPackageName"),
                                        (String) chain.getArg(2), UninstallConfig.FREEZE, state, (Integer) chain.getArg(1));
                                // Keep the platform's batch atomic: a denial rejects the batch.
                                if (!decision.allowed) return denyFreeze(decision);
                            }
                        }
                    }
                    return chain.proceed();
                });
                count++;
            }
            Log.i(TAG, "Application freeze hooks=" + count);
        } catch (Throwable error) {
            Log.w(TAG, "Application freeze hook unavailable", error);
        }
    }

    private static boolean isDisabledState(int state) {
        return state == 2 || state == 3 || state == 4;
    }

    private static Object denyFreeze(Decision decision) {
        if (!decision.spoofSuccess) {
            throw new SecurityException(decision.message);
        }
        return null;
    }

    private static void hookPackageOperations(XposedModule module, ClassLoader loader) {
        for (String name : new String[] { "com.android.server.pm.PackageManagerService",
                "com.android.server.pm.PackageManagerService$IPackageManagerImpl" }) {
            try {
                Class<?> owner = Class.forName(name, false, loader);
                for (Method method : owner.getDeclaredMethods()) {
                    Class<?>[] types = method.getParameterTypes();
                    switch (method.getName()) {
                        case "setApplicationHiddenSettingAsUser":
                            if (Arrays.equals(types, new Class<?>[] {
                                    String.class, boolean.class, int.class })
                                    && method.getReturnType() == boolean.class) {
                                hookHide(module, method);
                            }
                            break;
                        case "setPackagesSuspendedAsUser":
                            if ((types.length == 7 || types.length == 9)
                                    && types[0] == String[].class && types[1] == boolean.class
                                    && types[types.length - 2] == (types.length == 7 ? String.class : int.class)
                                    && types[types.length - 1] == int.class
                                    && method.getReturnType() == String[].class) {
                                hookSuspend(module, method, types.length == 7 ? 5 : 6, types.length - 1);
                            }
                            break;
                        case "clearApplicationUserData":
                            if (types.length == 3 && types[0] == String.class
                                    && isDataObserver(types[1]) && types[2] == int.class
                                    && method.getReturnType() == void.class) {
                                hookClear(module, method, 1, 2, false);
                            }
                            break;
                    }
                }
            } catch (ClassNotFoundException ignored) {
                // Older Android versions expose these methods directly on PackageManagerService.
            } catch (Throwable error) {
                Log.w(TAG, "Package operation hook unavailable: " + name, error);
            }
        }
    }

    private static void hookHide(XposedModule module, Method method) {
        method.setAccessible(true);
        module.hook(method).intercept(chain -> {
            Context context = systemContext();
            String target = (String) chain.getArg(0);
            if (context == null || target == null || !(Boolean) chain.getArg(1)) return chain.proceed();
            Decision decision = evaluateRequest(context, Binder.getCallingUid(), target,
                    null, UninstallConfig.HIDE, -1, (Integer) chain.getArg(2));
            if (decision.allowed) return chain.proceed();
            rejectWithoutMessageCallback(decision);
            return decision.spoofSuccess;
        });
    }

    private static void hookSuspend(XposedModule module, Method method, int callerIndex, int userIndex) {
        method.setAccessible(true);
        module.hook(method).intercept(chain -> {
            Context context = systemContext();
            String[] targets = (String[]) chain.getArg(0);
            if (context == null || targets == null || !(Boolean) chain.getArg(1)) return chain.proceed();
            int uid = Binder.getCallingUid();
            String caller = (String) chain.getArg(callerIndex);
            int user = (Integer) chain.getArg(userIndex);
            Set<String> allowed = new LinkedHashSet<>();
            Set<String> failed = new LinkedHashSet<>();
            Set<String> seen = new HashSet<>();
            for (String target : targets) {
                if (!seen.add(target)) continue;
                if (target == null) {
                    allowed.add(null);
                    continue;
                }
                Decision decision = evaluateRequest(context, uid, target, caller,
                        UninstallConfig.SUSPEND, -1, user);
                if (decision.allowed) allowed.add(target);
                else {
                    rejectWithoutMessageCallback(decision);
                    if (!decision.spoofSuccess) failed.add(target);
                }
            }
            if (allowed.size() == seen.size()) return chain.proceed();
            if (!allowed.isEmpty()) {
                Object[] args = chain.getArgs().toArray();
                args[0] = allowed.toArray(new String[0]);
                String[] failures = (String[]) chain.proceed(args);
                if (failures != null) Collections.addAll(failed, failures);
            }
            return failed.toArray(new String[0]);
        });
    }

    private static boolean isDataObserver(Class<?> type) {
        return "android.content.pm.IPackageDataObserver".equals(type.getName());
    }

    private static void hookClearActivityManager(XposedModule module, ClassLoader loader) {
        try {
            Class<?> owner = Class.forName("com.android.server.am.ActivityManagerService", false, loader);
            for (Method method : owner.getDeclaredMethods()) {
                Class<?>[] types = method.getParameterTypes();
                if ("clearApplicationUserData".equals(method.getName())
                        && types.length == 4 && types[0] == String.class && types[1] == boolean.class
                        && isDataObserver(types[2]) && types[3] == int.class
                        && method.getReturnType() == boolean.class) {
                    hookClear(module, method, 2, 3, true);
                    return;
                }
            }
        } catch (Throwable error) {
            Log.w(TAG, "ActivityManager clear hook unavailable", error);
        }
    }

    private static void hookClear(XposedModule module, Method method, int observerIndex,
            int userIndex, boolean fromActivityManager) {
        method.setAccessible(true);
        module.hook(method).intercept(chain -> {
            Context context = systemContext();
            String target = (String) chain.getArg(0);
            String clearing = CLEARING_PACKAGE.get();
            if (context == null || target == null || (!fromActivityManager && target.equals(clearing))) {
                return chain.proceed();
            }
            Decision decision = evaluateRequest(context, Binder.getCallingUid(), target,
                    null, UninstallConfig.CLEAR, -1, (Integer) chain.getArg(userIndex));
            if (!decision.allowed) {
                rejectWithoutMessageCallback(decision);
                Object observer = chain.getArg(observerIndex);
                if (observer != null) {
                    try {
                        Reflect.callMethod(observer, "onRemoveCompleted", target, decision.spoofSuccess);
                    } catch (Throwable error) {
                        Log.w(TAG, "Clear data callback failed", error);
                    }
                }
                return fromActivityManager ? decision.spoofSuccess : null;
            }
            if (!fromActivityManager) return chain.proceed();
            // ActivityManager calls PackageManager again with the system identity.
            CLEARING_PACKAGE.set(target);
            try {
                return chain.proceed();
            } finally {
                if (clearing == null) CLEARING_PACKAGE.remove();
                else CLEARING_PACKAGE.set(clearing);
            }
        });
    }

    private static void hookDeleteFlags(XposedModule module, ClassLoader loader, String ownerName) {
        try {
            Class<?> owner = Class.forName(ownerName, false, loader);
            for (Method method : owner.getDeclaredMethods()) {
                Class<?>[] types = method.getParameterTypes();
                if (!"deletePackageVersionedInternal".equals(method.getName()) || types.length < 5
                        || types[0] != VersionedPackage.class || types[2] != int.class
                        || types[3] != int.class) continue;
                method.setAccessible(true);
                module.hook(method).intercept(chain -> {
                    if (!Boolean.TRUE.equals(KEEP_DATA.get())) return chain.proceed();
                    Object[] args = chain.getArgs().toArray();
                    args[3] = (Integer) args[3] | DELETE_KEEP_DATA;
                    return chain.proceed(args);
                });
            }
        } catch (ClassNotFoundException ignored) {
            // Android 12 implements this helper in PackageManagerService itself.
        } catch (Throwable error) {
            Log.w(TAG, "Uninstall data retention hook unavailable", error);
        }
    }

    private static void rejectWithoutMessageCallback(Decision decision) {
        // Boolean and string-array callbacks have no field for an error message.
        if (decision.customResponse) throw new SecurityException(decision.message);
    }

    private static final class ShellDenial {
        Decision decision;
    }

    private static final class ShellResponseException extends SecurityException {
        ShellResponseException(String message) {
            super(message);
        }
    }

    private static void hookShellResponse(XposedModule module, ClassLoader loader) {
        try {
            Class<?> owner = Class.forName("com.android.server.pm.PackageManagerShellCommand", false, loader);
            Method method = Reflect.findMethodExact(owner, "onCommand", String.class);
            if (method == null || method.getReturnType() != int.class) return;
            module.hook(method).intercept(chain -> {
                String command = (String) chain.getArg(0);
                if (!isManagedShellCommand(command)) return chain.proceed();
                ShellDenial denial = new ShellDenial();
                ShellDenial previous = SHELL_DENIAL.get();
                SHELL_DENIAL.set(denial);
                try {
                    int result;
                    try {
                        result = (Integer) chain.proceed();
                    } catch (ShellResponseException response) {
                        result = 1;
                    }
                    Decision decision = denial.decision;
                    if (decision == null) return result;
                    if (!decision.customResponse) return result == 0 ? 1 : result;
                    try {
                        PrintWriter writer = (PrintWriter) Reflect.callMethod(
                                chain.getThisObject(), "getErrPrintWriter");
                        if (writer != null) writer.println(decision.message);
                    } catch (Exception error) {
                        Log.w(TAG, "Unable to write package command response", error);
                    }
                    return 1;
                } finally {
                    if (previous == null) SHELL_DENIAL.remove();
                    else SHELL_DENIAL.set(previous);
                }
            });
        } catch (Throwable error) {
            Log.w(TAG, "Package shell response hook unavailable", error);
        }
    }

    private static boolean isManagedShellCommand(String command) {
        if (command == null) return false;
        switch (command) {
            case "disable":
            case "disable-user":
            case "disable-until-used":
            case "hide":
            case "suspend":
            case "uninstall":
            case "clear":
                return true;
            default:
                return false;
        }
    }

    private static void hookZybos(XposedModule module, ClassLoader loader) {
        try {
            Class<?> owner = Class.forName("com.android.server.ZybosSysApiService", false, loader);
            Method method = Reflect.findMethodExact(owner, "silentUninstallApk",
                    String.class, boolean.class);
            if (method == null) {
                Log.w(TAG, "Zybos uninstall signature not found");
                return;
            }
            module.hook(method).intercept(chain -> {
                Context context = systemContext();
                String target = (String) chain.getArg(0);
                Decision decision = new Decision(true, "策略不可用");
                if (context != null) {
                    decision = evaluateRequest(context, Binder.getCallingUid(), target, null);
                    if (!decision.allowed) {
                        rejectWithoutMessageCallback(decision);
                        if (decision.spoofSuccess) {
                            sendZybosSuccess(chain.getThisObject(), target);
                        }
                        return Reflect.defaultReturnValue(method.getReturnType());
                    }
                }
                Boolean previous = INSTALLER_CALL.get();
                Boolean keepData = KEEP_DATA.get();
                INSTALLER_CALL.set(Boolean.TRUE);
                KEEP_DATA.set(decision.keepData);
                try {
                    return chain.proceed();
                } finally {
                    restoreUninstallScope(previous, keepData);
                }
            });
            Log.i(TAG, "Zybos silentUninstallApk hooked");
        } catch (Throwable error) {
            Log.w(TAG, "Zybos uninstall hook unavailable", error);
        }
    }

    private static void hookObserverAdapter(XposedModule module, ClassLoader loader) {
        try {
            Class<?> owner = Class.forName(
                    "com.android.server.pm.PackageInstallerService$PackageDeleteObserverAdapter",
                    false, loader);
            for (Constructor<?> constructor : owner.getDeclaredConstructors()) {
                constructor.setAccessible(true);
                module.hook(constructor).intercept(chain -> {
                    Object result = chain.proceed();
                    if (Boolean.TRUE.equals(INSTALLER_CALL.get())) {
                        try {
                            Object observer = Reflect.callMethod(chain.getThisObject(), "getBinder");
                            Object packageName = Reflect.getObjectField(chain.getThisObject(), "mPackageName");
                            if (observer != null && packageName instanceof String) {
                                APPROVED_OBSERVERS.put(observer, (String) packageName);
                            }
                        } catch (Throwable error) {
                            Log.w(TAG, "Observer tracking failed", error);
                        }
                    }
                    return result;
                });
            }
        } catch (Throwable error) {
            Log.w(TAG, "PackageDeleteObserverAdapter hook unavailable", error);
        }
    }

    private static void hookInstaller(XposedModule module, ClassLoader loader) {
        try {
            Class<?> owner = Class.forName("com.android.server.pm.PackageInstallerService", false, loader);
            for (Method method : owner.getDeclaredMethods()) {
                Class<?>[] types = method.getParameterTypes();
                if (!"uninstall".equals(method.getName()) || types.length != 5
                        || types[0] != VersionedPackage.class
                        || types[1] != String.class || types[3] != IntentSender.class) {
                    continue;
                }
                method.setAccessible(true);
                module.hook(method).intercept(chain -> {
                    Context context = systemContext();
                    VersionedPackage target = (VersionedPackage) chain.getArg(0);
                    Decision decision = new Decision(true, "策略不可用");
                    if (context != null && target != null && !Boolean.TRUE.equals(INSTALLER_CALL.get())) {
                        decision = evaluateRequest(context, Binder.getCallingUid(),
                                target.getPackageName(), (String) chain.getArg(1));
                        if (!decision.allowed) {
                            denyInstaller(context, (IntentSender) chain.getArg(3),
                                    target.getPackageName(), decision);
                            return null;
                        }
                    }
                    Boolean previous = INSTALLER_CALL.get();
                    Boolean keepData = KEEP_DATA.get();
                    INSTALLER_CALL.set(Boolean.TRUE);
                    KEEP_DATA.set(decision.keepData || Boolean.TRUE.equals(keepData));
                    try {
                        if (!Boolean.TRUE.equals(KEEP_DATA.get())) return chain.proceed();
                        Object[] args = chain.getArgs().toArray();
                        args[2] = (Integer) args[2] | DELETE_KEEP_DATA;
                        return chain.proceed(args);
                    } finally {
                        restoreUninstallScope(previous, keepData);
                    }
                });
                Log.i(TAG, "PackageInstallerService.uninstall hooked");
                return;
            }
            Log.w(TAG, "PackageInstallerService.uninstall signature not found");
        } catch (Throwable error) {
            Log.w(TAG, "PackageInstallerService hook failed", error);
        }
    }

    private static void hookPackageManager(XposedModule module, ClassLoader loader) {
        try {
            Class<?> owner = Class.forName("com.android.server.pm.PackageManagerService", false, loader);
            int count = 0;
            for (Class<?> type = owner; type != null; type = type.getSuperclass()) {
                for (Method method : type.getDeclaredMethods()) {
                    Class<?>[] parameters = method.getParameterTypes();
                    if (!("deletePackageVersioned".equals(method.getName())
                            || "deleteExistingPackageAsUser".equals(method.getName()))
                            || parameters.length < 2 || parameters[0] != VersionedPackage.class
                            || !parameters[1].getName().contains("IPackageDeleteObserver")) {
                        continue;
                    }
                    method.setAccessible(true);
                    module.hook(method).intercept(chain -> {
                        Object observer = chain.getArg(1);
                        VersionedPackage target = (VersionedPackage) chain.getArg(0);
                        boolean approved;
                        synchronized (APPROVED_OBSERVERS) {
                            approved = target != null && target.getPackageName().equals(
                                    APPROVED_OBSERVERS.get(observer));
                            if (approved) {
                                APPROVED_OBSERVERS.remove(observer);
                            }
                        }
                        if (approved) {
                            return chain.proceed();
                        }
                        if (Boolean.TRUE.equals(INSTALLER_CALL.get())) {
                            return chain.proceed();
                        }
                        Context context = systemContext();
                        if (context == null || target == null) return chain.proceed();
                        int uid = parameters.length == 5 && parameters[4] == int.class
                                ? (Integer) chain.getArg(4) : Binder.getCallingUid();
                        int userId = parameters.length >= 3 && parameters[2] == int.class
                                ? (Integer) chain.getArg(2) : -1;
                        Decision decision = evaluateRequest(context, uid, target.getPackageName(),
                                null, UninstallConfig.UNINSTALL, -1, userId);
                        if (!decision.allowed) {
                            denyPackageManager(observer, target.getPackageName(), decision);
                            return null;
                        }
                        Boolean previous = INSTALLER_CALL.get();
                        Boolean keepData = KEEP_DATA.get();
                        INSTALLER_CALL.set(Boolean.TRUE);
                        KEEP_DATA.set(decision.keepData);
                        try {
                            if (decision.keepData && parameters.length >= 4 && parameters[3] == int.class) {
                                Object[] args = chain.getArgs().toArray();
                                args[3] = (Integer) args[3] | DELETE_KEEP_DATA;
                                return chain.proceed(args);
                            }
                            return chain.proceed();
                        } finally {
                            restoreUninstallScope(previous, keepData);
                        }
                    });
                    count++;
                }
            }
            Log.i(TAG, "PackageManager delete hooks=" + count);
            hookLegacyDelete(module, owner);
        } catch (Throwable error) {
            Log.w(TAG, "PackageManager hook failed", error);
        }
    }

    private static void hookLegacyDelete(XposedModule module, Class<?> owner) {
        for (Class<?> type = owner; type != null; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                if (!"deletePackageAsUser".equals(method.getName())
                        || parameters.length < 3 || parameters[0] != String.class) {
                    continue;
                }
                int observerIndex = -1;
                for (int index = 1; index < parameters.length; index++) {
                    if (parameters[index].getName().contains("IPackageDeleteObserver")) {
                        observerIndex = index;
                        break;
                    }
                }
                if (observerIndex < 0) {
                    continue;
                }
                final int callbackIndex = observerIndex;
                try {
                    method.setAccessible(true);
                    module.hook(method).intercept(chain -> {
                        if (Boolean.TRUE.equals(INSTALLER_CALL.get())) {
                            return chain.proceed();
                        }
                        Context context = systemContext();
                        String target = (String) chain.getArg(0);
                        if (context == null || target == null) return chain.proceed();
                        Decision decision = evaluateRequest(context, Binder.getCallingUid(), target, null);
                        if (!decision.allowed) {
                            rejectWithoutMessageCallback(decision);
                            denyLegacyPackageManager(chain.getArg(callbackIndex), target,
                                    decision.spoofSuccess);
                            return null;
                        }
                        Boolean previous = INSTALLER_CALL.get();
                        Boolean keepData = KEEP_DATA.get();
                        INSTALLER_CALL.set(Boolean.TRUE);
                        KEEP_DATA.set(decision.keepData);
                        try {
                            if (decision.keepData && callbackIndex == 2 && parameters.length == 5
                                    && parameters[4] == int.class) {
                                Object[] args = chain.getArgs().toArray();
                                args[4] = (Integer) args[4] | DELETE_KEEP_DATA;
                                return chain.proceed(args);
                            }
                            return chain.proceed();
                        } finally {
                            restoreUninstallScope(previous, keepData);
                        }
                    });
                    Log.i(TAG, "Legacy deletePackageAsUser hooked");
                } catch (Throwable error) {
                    Log.w(TAG, "Legacy delete hook failed", error);
                }
            }
        }
    }

    private static void restoreUninstallScope(Boolean previous, Boolean keepData) {
        if (previous == null) INSTALLER_CALL.remove();
        else INSTALLER_CALL.set(previous);
        if (keepData == null) KEEP_DATA.remove();
        else KEEP_DATA.set(keepData);
    }

    private static Decision evaluateRequest(Context context, int uid, String target, String callerPackage) {
        return evaluateRequest(context, uid, target, callerPackage, UninstallConfig.UNINSTALL, -1, -1);
    }

    private static Decision evaluateRequest(Context context, int uid, String target,
            String callerPackage, String operation, int requestedState, int userId) {
        int callerPid = Binder.getCallingPid();
        Bundle policy;
        // Query as system_server, not as the application requesting deletion.
        // Keep uid/pid above intact for the actual uninstall policy evaluation.
        long policyIdentity = Binder.clearCallingIdentity();
        try {
            policy = context.getContentResolver().call(SettingsProvider.URI,
                    SettingsProvider.METHOD_GET_UNINSTALL, "system_server", null);
        } catch (Throwable error) {
            Log.w(TAG, "Policy unavailable", error);
            policy = null;
        } finally {
            Binder.restoreCallingIdentity(policyIdentity);
        }
        // Never resurrect an old enabled policy after the user disables validation.
        if (policy == null) {
            return new Decision(true, "策略不可用");
        }
        if (UninstallConfig.FREEZE.equals(operation) && (!policy.getBoolean(UninstallConfig.ENABLED, false)
                || !policy.getBoolean(UninstallConfig.FREEZE_ENABLED, false))) {
            return new Decision(true, "冻结验证未启用");
        }
        Set<String> targetIdentity = Collections.singleton("pkg:" + target);
        boolean protectedTarget = matches(policy.getStringArrayList(UninstallConfig.BLOCKED_TARGETS), targetIdentity);
        if (!protectedTarget && matches(policy.getStringArrayList(UninstallConfig.UNMANAGED_TARGETS), targetIdentity)) {
            return new Decision(true, "不拦截名单");
        }
        String[] packages;
        try {
            packages = context.getPackageManager().getPackagesForUid(uid);
        } catch (Throwable error) {
            packages = null;
        }
        Set<String> identities = new HashSet<>();
        identities.add("uid:" + uid);
        String verifiedCaller = null;
        if (packages != null) {
            String processPackage = packageForProcess(callerPid);
            if (processPackage != null) {
                for (String name : packages) {
                    if (processPackage.equals(name)) {
                        identities.add("pkg:" + name);
                        verifiedCaller = name;
                        break;
                    }
                }
            }
            if (callerPackage != null) {
                for (String name : packages) {
                    if (callerPackage.equals(name)) {
                        identities.add("pkg:" + name);
                        if (verifiedCaller == null) {
                            verifiedCaller = name;
                        }
                        break;
                    }
                }
            }
            if (verifiedCaller == null && packages.length == 1) {
                verifiedCaller = packages[0];
                identities.add("pkg:" + verifiedCaller);
            }
        }
        Decision decision;
        if (!policy.getBoolean(UninstallConfig.ENABLED, false)) {
            decision = new Decision(true, "验证未启用");
        } else if (protectedTarget) {
            String reason = UninstallConfig.UNINSTALL.equals(operation) ? "目标应用禁止卸载"
                    : UninstallConfig.FREEZE.equals(operation) ? "目标应用禁止冻结" : "目标应用禁止操作";
            decision = new Decision(false, reason);
        } else if (matches(policy.getStringArrayList(UninstallConfig.BLACKLIST), identities)) {
            Log.i(TAG, "Blacklist denied uid=" + uid + " target=" + target);
            decision = new Decision(false, "黑名单");
        } else if (matches(policy.getStringArrayList(UninstallConfig.WHITELIST), identities)) {
            decision = new Decision(true, "白名单");
        } else if (policy.getBoolean(UninstallConfig.WHITELIST_ONLY, false)) {
            decision = new Decision(false, "非白名单拒绝");
        } else {
            int seconds = UninstallConfig.normalizeTimeoutSeconds(policy.getInt(
                    UninstallConfig.TIMEOUT_SECONDS, UninstallConfig.DEFAULT_TIMEOUT));
            decision = confirm(context, uid,
                    verifiedCaller == null ? new String[0] : new String[] { verifiedCaller },
                    target, seconds, operation);
        }
        // Reuse the policy snapshot even if settings change while a prompt is pending.
        if (decision.allowed) {
            decision.keepData = UninstallConfig.UNINSTALL.equals(operation)
                    && policy.getBoolean(UninstallConfig.ENABLED, false)
                    && policy.getBoolean(UninstallConfig.KEEP_DATA, true);
        } else {
            int response = policy.getInt(UninstallConfig.DENIAL_RESPONSE,
                    policy.getBoolean(UninstallConfig.SPOOF_RESULT, false)
                            ? UninstallConfig.SPOOF_SUCCESS : UninstallConfig.DEFAULT_RESPONSE);
            decision.spoofSuccess = response == UninstallConfig.SPOOF_SUCCESS;
            decision.customResponse = response == UninstallConfig.CUSTOM_RESPONSE;
            if (decision.customResponse) {
                String message = policy.getString(UninstallConfig.DENIAL_MESSAGE);
                if (message != null && !message.trim().isEmpty()) {
                    decision.message = message.substring(0, Math.min(message.length(),
                            UninstallConfig.MAX_MESSAGE_LENGTH));
                }
            }
            ShellDenial shell = SHELL_DENIAL.get();
            if (shell != null && !decision.spoofSuccess && shell.decision == null) {
                shell.decision = decision;
            }
        }
        recordDecision(context, uid, verifiedCaller, target, decision,
                operation, callerPid, requestedState, userId);
        if (decision.customResponse && SHELL_DENIAL.get() != null) {
            throw new ShellResponseException(decision.message);
        }
        return decision;
    }

    private static void recordDecision(Context context, int uid, String callerPackage,
            String target, Decision decision, String operation, int callerPid, int requestedState, int userId) {
        long identity = 0;
        boolean cleared = false;
        try {
            identity = Binder.clearCallingIdentity();
            cleared = true;
            Bundle record = new Bundle();
            record.putString(UninstallConfig.OPERATION, operation);
            record.putInt("caller_pid", callerPid);
            record.putInt("requested_state", requestedState);
            record.putInt("user_id", userId);
            record.putLong(UninstallHistoryStore.TIME, System.currentTimeMillis());
            record.putInt(UninstallHistoryStore.CALLER_UID, uid);
            record.putString(UninstallHistoryStore.CALLER_PACKAGE, callerPackage);
            record.putString(UninstallHistoryStore.CALLER_NAME,
                    callerPackage == null ? uidName(uid) : packageLabel(context, callerPackage));
            record.putString(UninstallHistoryStore.TARGET_PACKAGE, target);
            record.putString(UninstallHistoryStore.TARGET_NAME, packageLabel(context, target));
            record.putBoolean(UninstallHistoryStore.ALLOWED, decision.allowed);
            record.putString(UninstallHistoryStore.REASON, decision.reason);
            Bundle result = context.getContentResolver().call(SettingsProvider.URI,
                    SettingsProvider.METHOD_ADD_UNINSTALL_RECORD, null, record);
            if (result == null) Log.w(TAG, "Package operation record was not saved");
        } catch (Throwable error) {
            Log.w(TAG, "Package operation record unavailable", error);
        } finally {
            if (cleared) Binder.restoreCallingIdentity(identity);
        }
    }

    private static String packageLabel(Context context, String packageName) {
        if (packageName == null || packageName.isEmpty()) return "未知应用";
        try {
            ApplicationInfo info = context.getPackageManager().getApplicationInfo(packageName, 0);
            CharSequence label = info.loadLabel(context.getPackageManager());
            return label == null || label.length() == 0 ? packageName : label.toString();
        } catch (Throwable ignored) {
            return packageName;
        }
    }

    private static String uidName(int uid) {
        switch (uid) {
            case 0: return "root";
            case 1: return "daemon";
            case 2: return "bin";
            case 3: return "sys";
            case 1000: return "system";
            case 2000: return "shell";
            default: return "进程";
        }
    }

    private static final class Decision {
        final boolean allowed;
        final String reason;
        boolean spoofSuccess;
        boolean keepData;
        boolean customResponse;
        String message = DENIED_MESSAGE;

        Decision(boolean allowed, String reason) {
            this.allowed = allowed;
            this.reason = reason;
        }
    }

    private static String packageForProcess(int pid) {
        if (pid <= 0) {
            return null;
        }
        byte[] buffer = new byte[256];
        try (FileInputStream input = new FileInputStream("/proc/" + pid + "/cmdline")) {
            int count = input.read(buffer);
            if (count <= 0) {
                return null;
            }
            int length = 0;
            while (length < count && buffer[length] != 0) {
                length++;
            }
            String processName = new String(buffer, 0, length, java.nio.charset.StandardCharsets.UTF_8);
            int separator = processName.indexOf(':');
            return separator < 0 ? processName : processName.substring(0, separator);
        } catch (Exception error) {
            Log.w(TAG, "Caller process unavailable pid=" + pid, error);
            return null;
        }
    }

    private static boolean matches(ArrayList<String> entries, Set<String> identities) {
        if (entries == null) {
            return false;
        }
        for (String entry : entries) {
            if (identities.contains(entry)) {
                return true;
            }
        }
        return false;
    }

    private static Decision confirm(Context context, int uid, String[] packages,
            String target, int seconds, String operation) {
        // Only requests that reached popup confirmation use this rule.
        // Enabled state and black/white lists were already handled above.
        if (UninstallScreenState.isScreenOff(context)) {
            return new Decision(false, UninstallScreenState.SCREEN_OFF_REASON);
        }
        if (!PROMPT_SLOT.tryAcquire()) {
            Log.w(TAG, "Another package operation confirmation is pending");
            return new Decision(false, "已有待确认请求");
        }
        CountDownLatch decided = new CountDownLatch(1);
        AtomicInteger response = new AtomicInteger(-1);
        ResultReceiver receiver = new ResultReceiver(null) {
            @Override protected void onReceiveResult(int resultCode, Bundle resultData) {
                response.set(resultCode);
                decided.countDown();
            }
        };
        Intent prompt = new Intent();
        prompt.setClassName("com.lijinmu.arashi", "com.lijinmu.arashi.UninstallPromptActivity");
        // Keep the floating prompt separate from any existing Arashi task.
        prompt.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        prompt.putExtra("target", target);
        prompt.putExtra(UninstallConfig.OPERATION, operation);
        prompt.putExtra("caller_uid", uid);
        prompt.putExtra("caller_packages", packages == null ? new String[0] : packages);
        prompt.putExtra("deadline_elapsed", SystemClock.elapsedRealtime() + seconds * 1000L);
        prompt.putExtra("decision_receiver", receiver);
        try {
            long identity = Binder.clearCallingIdentity();
            try {
                context.startActivity(prompt);
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
            boolean finished = decided.await(seconds, TimeUnit.SECONDS);
            if (!finished) {
                Log.w(TAG, "Confirmation timed out uid=" + uid + " target=" + target);
                return new Decision(false, "超时默认拒绝");
            }
            if (response.get() == UninstallScreenState.SCREEN_OFF_RESULT) {
                return new Decision(false, UninstallScreenState.SCREEN_OFF_REASON);
            }
            if (response.get() == 1) return new Decision(true, "弹窗允许");
            if (response.get() == 2) return new Decision(false, "超时默认拒绝");
            if (response.get() == 3) return new Decision(false, "弹窗关闭");
            return new Decision(false, "弹窗拒绝");
        } catch (Throwable error) {
            Log.w(TAG, "Confirmation failed", error);
            if (error instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return new Decision(false, "确认过程异常");
        } finally {
            PROMPT_SLOT.release();
        }
    }

    private static void sendZybosSuccess(Object service, String target) {
        long identity = Binder.clearCallingIdentity();
        try {
            Reflect.callMethod(service, "sendZybosUnInstallSuccessCallback", target);
        } catch (Throwable error) {
            Log.w(TAG, "Zybos uninstall result callback failed", error);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    private static void denyInstaller(Context context, IntentSender receiver, String target,
                                      Decision decision) {
        if (receiver == null) {
            return;
        }
        Intent result = new Intent();
        result.putExtra(PackageInstaller.EXTRA_STATUS, decision.spoofSuccess
                ? PackageInstaller.STATUS_SUCCESS : PackageInstaller.STATUS_FAILURE_ABORTED);
        result.putExtra("android.content.pm.extra.LEGACY_STATUS", decision.spoofSuccess ? 1 : -5);
        if (!decision.spoofSuccess) result.putExtra(PackageInstaller.EXTRA_STATUS_MESSAGE, decision.message);
        result.putExtra(PackageInstaller.EXTRA_PACKAGE_NAME, target);
        try {
            receiver.sendIntent(context, 0, result, null, null);
        } catch (IntentSender.SendIntentException error) {
            Log.w(TAG, "Uninstall callback failed", error);
        }
    }

    private static void denyPackageManager(Object observer, String target, Decision decision) {
        if (observer == null) {
            return;
        }
        try {
            Method callback = observer.getClass().getMethod("onPackageDeleted",
                    String.class, int.class, String.class);
            callback.setAccessible(true);
            callback.invoke(observer, target, decision.spoofSuccess ? 1 : -5, decision.spoofSuccess ? null : decision.message);
        } catch (Throwable error) {
            Log.w(TAG, "Delete observer callback failed", error);
        }
    }

    private static void denyLegacyPackageManager(Object observer, String target, boolean spoofSuccess) {
        if (observer == null) {
            return;
        }
        try {
            Method callback = observer.getClass().getMethod("packageDeleted",
                    String.class, int.class);
            callback.setAccessible(true);
            callback.invoke(observer, target, spoofSuccess ? 1 : -5);
        } catch (Throwable error) {
            Log.w(TAG, "Legacy delete observer callback failed", error);
        }
    }

    private static Context systemContext() {
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Object thread = Reflect.callStaticMethod(activityThread, "currentActivityThread");
            Object context = Reflect.callMethod(thread, "getSystemContext");
            return context instanceof Context ? (Context) context : null;
        } catch (Throwable error) {
            return null;
        }
    }
}
