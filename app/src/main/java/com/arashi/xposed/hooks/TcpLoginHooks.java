package com.arashi.xposed.hooks;

import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.os.Bundle;
import android.os.Binder;
import android.os.Process;
import android.util.Log;
import com.arashi.tcp.TcpHistoryStore;
import com.arashi.xposed.ConfigManager;
import com.arashi.xposed.HookConfig;
import com.arashi.xposed.Reflect;
import com.arashi.xposed.SettingsProvider;
import com.arashi.xposed.TcpLoginPolicy;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Blocks only login.fail emitted inside the scoped TCP process. */
public final class TcpLoginHooks {
    private static final String TAG = "[Arashi-TCP]";
    private static final ThreadLocal<Flow> FLOW = new ThreadLocal<>();
    private static final class Flow {
        final String source;
        Object response;
        Flow(String source, Object response) { this.source = source; this.response = response; }
    }
    private TcpLoginHooks() {}

    public static void hookTcpService(XposedModule module, ClassLoader loader) {
        // The framework boundary is a fallback if vendor class names change or are inlined.
        hookBroadcasts(module, ContextWrapper.class);
        try {
            hookBroadcasts(module, Class.forName("android.app.ContextImpl", false, loader));
        } catch (Throwable error) { Log.w(TAG, "ContextImpl broadcast hook unavailable", error); }
        hookSender(module, loader);
        hookTcpResponse(module, loader);
        hookHttpResponse(module, loader);
    }

    private static void hookSender(XposedModule module, ClassLoader loader) {
        try {
            Class<?> sender = Class.forName("q7.a", false, loader);
            // Check the known sender's recipient table before trusting an obfuscated class name.
            Object recipients = Reflect.getStaticObjectField(sender, "a");
            if (!(recipients instanceof String[]) || !java.util.Arrays.asList((String[]) recipients)
                    .contains("com.zuoyebang.iot.pad.supervise")) return;
            String targetPackage = null;
            for (String name : (String[]) recipients) {
                if (!"com.zuoyebang.iot.pad.supervise".equals(name)) targetPackage = name;
            }
            final String target = targetPackage;
            Method method = sender.getDeclaredMethod("a", int.class, String.class);
            method.setAccessible(true);
            module.hook(method).intercept(chain -> {
                if (!ConfigManager.isEnabled(HookConfig.KEY_BLOCK_TCP_LOGIN_FAIL)) return chain.proceed();
                record(application(), (Integer) chain.getArg(0), (String) chain.getArg(1),
                        "q7.a#a", target);
                return null;
            });
            Log.i(TAG, "login.fail dispatcher hooked");
        } catch (Throwable error) { Log.w(TAG, "Vendor login.fail dispatcher unavailable", error); }
    }

    private static void hookBroadcasts(XposedModule module, Class<?> owner) {
        for (Method method : owner.getDeclaredMethods()) {
            Class<?>[] args = method.getParameterTypes();
            if (Modifier.isStatic(method.getModifiers()) || method.getReturnType() != void.class
                    || !method.getName().startsWith("send") || !method.getName().contains("Broadcast")
                    || args.length == 0 || args[0] != Intent.class) continue;
            try {
                method.setAccessible(true);
                module.hook(method).intercept(chain -> {
                    Intent intent = (Intent) chain.getArg(0);
                    if (intent == null || !TcpLoginPolicy.ACTION.equals(intent.getAction())
                            || !TcpLoginPolicy.shouldBlock(ConfigManager.isEnabled(
                                    HookConfig.KEY_BLOCK_TCP_LOGIN_FAIL), intent.getAction())) {
                        return chain.proceed();
                    }
                    int type = -1;
                    String code = null;
                    try {
                        type = intent.getIntExtra("errorType", -1);
                        code = intent.getStringExtra("errorCode");
                    } catch (RuntimeException error) { Log.w(TAG, "Unable to read login.fail extras", error); }
                    record((Context) chain.getThisObject(), type, code,
                            owner.getName() + "#" + method.getName(), intent.getPackage());
                    return null;
                });
            } catch (Throwable error) { Log.w(TAG, "Broadcast hook unavailable: " + method.getName(), error); }
        }
    }

    private static void hookTcpResponse(XposedModule module, ClassLoader loader) {
        try {
            Class<?> owner = Class.forName("com.zuoyebang.iot.zptcpservice.interceptors.b", false, loader);
            for (Method method : owner.getDeclaredMethods()) {
                Class<?>[] args = method.getParameterTypes();
                if (args.length != 1 || !args[0].getName().equals("com.zuoyebang.iot.mod.tcp.TcpMessage")) continue;
                method.setAccessible(true);
                module.hook(method).intercept(chain -> {
                    Object packet = chain.getArg(0);
                    if (packet == null || !packet.getClass().getName().equals(
                            "com.zuoyebang.iot.zptcpservice.packet.receive.ReceiveLoginMessage")) return chain.proceed();
                    Flow previous = FLOW.get();
                    FLOW.set(new Flow("TCP 登录响应", packet));
                    try { return chain.proceed(); }
                    finally { restoreFlow(previous); }
                });
                module.deoptimize(method);
            }
        } catch (Throwable error) { Log.w(TAG, "TCP response details unavailable", error); }
    }

    private static void hookHttpResponse(XposedModule module, ClassLoader loader) {
        try {
            Class<?> login = Class.forName("com.zuoyebang.iot.zptcpservice.login.a", false, loader);
            for (Method method : login.getDeclaredMethods()) {
                if (!method.getName().equals("a") || method.getParameterCount() != 0) continue;
                method.setAccessible(true);
                module.hook(method).intercept(chain -> {
                    Flow previous = FLOW.get();
                    FLOW.set(new Flow("HTTP 登录响应", null));
                    try { return chain.proceed(); }
                    finally { restoreFlow(previous); }
                });
                module.deoptimize(method);
            }
            Class<?> response = Class.forName("com.zuoyebang.iot.zptcpservice.login.LoginResponse", false, loader);
            for (Method method : response.getDeclaredMethods()) {
                if (method.getParameterCount() != 0 || !method.getReturnType().getName().equals(
                        "com.zuoyebang.iot.zptcpservice.login.LoginData")) continue;
                method.setAccessible(true);
                module.hook(method).intercept(chain -> {
                    Object data = chain.proceed();
                    Flow flow = FLOW.get();
                    if (flow != null && flow.source.equals("HTTP 登录响应")) flow.response = data;
                    return data;
                });
            }
        } catch (Throwable error) { Log.w(TAG, "HTTP response details unavailable", error); }
    }

    private static void restoreFlow(Flow previous) {
        if (previous == null) FLOW.remove(); else FLOW.set(previous);
    }

    private static Context application() {
        try {
            return (Context) Reflect.callStaticMethod(Class.forName("android.app.ActivityThread"), "currentApplication");
        } catch (Throwable ignored) { return null; }
    }

    private static void record(Context context, int type, String code, String dispatch, String target) {
        // Logging failure must never release a blocked broadcast or crash the TCP service.
        try {
            Flow flow = FLOW.get();
            Bundle data = new Bundle();
            data.putLong(TcpHistoryStore.TIME, System.currentTimeMillis());
            data.putInt(TcpHistoryStore.ERROR_TYPE, type);
            data.putString(TcpHistoryStore.ERROR_CODE, code);
            data.putString(TcpHistoryStore.SOURCE, flow == null ? "未识别触发路径" : flow.source);
            data.putInt(TcpHistoryStore.PID, Process.myPid());
            data.putString(TcpHistoryStore.PROCESS, Application.getProcessName());
            StringBuilder detail = new StringBuilder("动作：").append(TcpLoginPolicy.ACTION)
                    .append("\n拦截位置：").append(dispatch);
            if (target != null) detail.append("\n目标包名：").append(target);
            // Explicit field allowlist: never serialize a login response, token, SN or secret key.
            if (flow != null && flow.response != null) {
                detail.append("\n响应字段：");
                for (String name : new String[] { "status", "code", "msg", "rop", "halted", "bound", "tcpVersion", "unbindType" }) {
                    Object value = Reflect.getObjectField(flow.response, name);
                    if (value instanceof Number || value instanceof Boolean || value instanceof String) {
                        String text = value.toString();
                        detail.append('\n').append(name).append(" = ")
                                .append(text.length() > 2048 ? text.substring(0, 2048) : text);
                    }
                }
            } else {
                detail.append("\n未捕获对应响应，以上错误码来自 login.fail 通知");
            }
            data.putString(TcpHistoryStore.DETAILS, detail.toString());
            StringBuilder stack = new StringBuilder();
            for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
                if (frame.getClassName().equals(Thread.class.getName())
                        || frame.getClassName().equals(TcpLoginHooks.class.getName())) continue;
                stack.append(frame).append('\n');
                if (stack.length() >= 10000) break;
            }
            data.putString(TcpHistoryStore.STACK, stack.toString());
            Log.i(TAG, "Blocked login.fail errorType=" + type + " errorCode=" + code);
            if (context == null) context = application();
            Bundle result;
            long identity = Binder.clearCallingIdentity();
            try {
                result = context == null ? null : context.getContentResolver().call(
                        SettingsProvider.URI, SettingsProvider.METHOD_ADD_TCP_RECORD, null, data);
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
            if (result == null) Log.w(TAG, "Blocked login.fail record was not saved");
        } catch (Throwable error) { Log.w(TAG, "Blocked login.fail record unavailable", error); }
    }
}
