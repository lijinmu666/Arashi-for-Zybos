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
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
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
    private static final String TAG = "[Arashi-Uninstall]";
    private static final String DENIED_MESSAGE = "WoShiJiangJieShi";
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
                if (context != null) {
                    Decision decision = evaluateRequest(context, Binder.getCallingUid(), target, null);
                    if (!decision.allowed) {
                        if (decision.spoofSuccess) {
                            sendZybosSuccess(chain.getThisObject(), target);
                        }
                        return Reflect.defaultReturnValue(method.getReturnType());
                    }
                }
                INSTALLER_CALL.set(Boolean.TRUE);
                try {
                    return chain.proceed();
                } finally {
                    INSTALLER_CALL.remove();
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
                    if (context != null && target != null) {
                        Decision decision = evaluateRequest(context, Binder.getCallingUid(),
                                target.getPackageName(), (String) chain.getArg(1));
                        if (!decision.allowed) {
                            denyInstaller(context, (IntentSender) chain.getArg(3),
                                    target.getPackageName(), decision.spoofSuccess);
                            return null;
                        }
                    }
                    INSTALLER_CALL.set(Boolean.TRUE);
                    try {
                        return chain.proceed();
                    } finally {
                        INSTALLER_CALL.remove();
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
                        if (context != null && target != null) {
                            Decision decision = evaluateRequest(context, Binder.getCallingUid(),
                                    target.getPackageName(), null);
                            if (!decision.allowed) {
                                denyPackageManager(observer, target.getPackageName(), decision.spoofSuccess);
                                return null;
                            }
                        }
                        return chain.proceed();
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
                        if (context != null && target != null) {
                            Decision decision = evaluateRequest(context, Binder.getCallingUid(), target, null);
                            if (!decision.allowed) {
                                denyLegacyPackageManager(chain.getArg(callbackIndex), target,
                                        decision.spoofSuccess);
                                return null;
                            }
                        }
                        return chain.proceed();
                    });
                    Log.i(TAG, "Legacy deletePackageAsUser hooked");
                } catch (Throwable error) {
                    Log.w(TAG, "Legacy delete hook failed", error);
                }
            }
        }
    }

    private static Decision evaluateRequest(Context context, int uid, String target, String callerPackage) {
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
        } else if (matches(policy.getStringArrayList(UninstallConfig.BLACKLIST), identities)) {
            Log.i(TAG, "Blacklist denied uid=" + uid + " target=" + target);
            decision = new Decision(false, "黑名单");
        } else if (matches(policy.getStringArrayList(UninstallConfig.WHITELIST), identities)) {
            decision = new Decision(true, "白名单");
        } else {
            int seconds = UninstallConfig.normalizeTimeoutSeconds(policy.getInt(
                    UninstallConfig.TIMEOUT_SECONDS, UninstallConfig.DEFAULT_TIMEOUT));
            decision = confirm(context, uid,
                    verifiedCaller == null ? new String[0] : new String[] { verifiedCaller },
                    target, seconds);
        }
        // Snapshot the setting for this request, including while its prompt is pending.
        decision = new Decision(decision.allowed, decision.reason,
                !decision.allowed && policy.getBoolean(UninstallConfig.SPOOF_RESULT, false));
        recordDecision(context, uid, verifiedCaller, target, decision);
        return decision;
    }

    private static void recordDecision(Context context, int uid, String callerPackage,
            String target, Decision decision) {
        long identity = 0;
        boolean cleared = false;
        try {
            identity = Binder.clearCallingIdentity();
            cleared = true;
            Bundle record = new Bundle();
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
            if (result == null) Log.w(TAG, "Uninstall record was not saved");
        } catch (Throwable error) {
            Log.w(TAG, "Uninstall record unavailable", error);
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
        final boolean spoofSuccess;

        Decision(boolean allowed, String reason) {
            this(allowed, reason, false);
        }

        Decision(boolean allowed, String reason, boolean spoofSuccess) {
            this.allowed = allowed;
            this.reason = reason;
            this.spoofSuccess = spoofSuccess;
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
            String target, int seconds) {
        // Only requests that reached popup confirmation use this rule.
        // Enabled state and black/white lists were already handled above.
        if (UninstallScreenState.isScreenOff(context)) {
            return new Decision(false, UninstallScreenState.SCREEN_OFF_REASON);
        }
        if (!PROMPT_SLOT.tryAcquire()) {
            Log.w(TAG, "Another uninstall confirmation is pending");
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
        prompt.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        prompt.putExtra("target", target);
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
                                      boolean spoofSuccess) {
        if (receiver == null) {
            return;
        }
        Intent result = new Intent();
        result.putExtra(PackageInstaller.EXTRA_STATUS, spoofSuccess
                ? PackageInstaller.STATUS_SUCCESS : PackageInstaller.STATUS_FAILURE_ABORTED);
        result.putExtra("android.content.pm.extra.LEGACY_STATUS", spoofSuccess ? 1 : -5);
        if (!spoofSuccess) result.putExtra(PackageInstaller.EXTRA_STATUS_MESSAGE, DENIED_MESSAGE);
        result.putExtra(PackageInstaller.EXTRA_PACKAGE_NAME, target);
        try {
            receiver.sendIntent(context, 0, result, null, null);
        } catch (IntentSender.SendIntentException error) {
            Log.w(TAG, "Uninstall callback failed", error);
        }
    }

    private static void denyPackageManager(Object observer, String target, boolean spoofSuccess) {
        if (observer == null) {
            return;
        }
        try {
            Method callback = observer.getClass().getMethod("onPackageDeleted",
                    String.class, int.class, String.class);
            callback.setAccessible(true);
            callback.invoke(observer, target, spoofSuccess ? 1 : -5, spoofSuccess ? null : DENIED_MESSAGE);
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
