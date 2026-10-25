package com.lijinmu.arashi.xposed.hooks;

import android.util.Log;
import com.lijinmu.arashi.xposed.ConfigManager;
import com.lijinmu.arashi.xposed.HookConfig;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Method;

/** Verified against the original obfuscated JC Code V3 3.2.6 entry points. */
public final class JcCodeHooks {
    private static final String TAG = "[Arashi-JC]";
    private JcCodeHooks() {}

    public static void hook(XposedModule module, ClassLoader loader) {
        try {
            // Resolve the entire known contract before installing anything. Never scan and
            // replace arbitrary boolean methods: these also represent real ADB/owner state.
            Class<?> activity = Class.forName("com.jc.code.v3.MainActivity", false, loader);
            Class<?> cloud = Class.forName("o.CrDc", false, loader);
            Method vpn = method(activity, "NU", String.class);
            Method environment = method(activity, "Ab", String.class);
            Method certificate = method(activity, "CBEvIj", boolean.class);
            Method status = method(cloud, "mjFkc", String.class, String.class);
            method(activity, "HBzZAD", void.class, boolean.class, boolean.class, boolean.class);
            method(activity, "HPA", void.class);

            module.hook(status).intercept(chain -> {
                if (!enabled()) return chain.proceed();
                // Replace before DNS, direct-IP TLS, device-code submission and HMAC
                // verification. The existing runnable still updates UI and real ADB/OW state.
                return JcCodePolicy.localStatus();
            });
            module.hook(vpn).intercept(chain -> enabled() ? null : chain.proceed());
            module.hook(environment).intercept(chain -> enabled() ? null : chain.proceed());
            module.hook(certificate).intercept(chain -> enabled() ? true : chain.proceed());
            // qGgY invokes these small methods; remove any existing compiled inlining.
            module.deoptimize(activity.getDeclaredMethod("h0", boolean.class));
            module.deoptimize(Class.forName("com.jc.code.v3.MainActivity$qGgY", false, loader)
                    .getDeclaredMethod("run"));
            Log.i(TAG, "JC 3.2.6 local gate hooks installed");
        } catch (Throwable error) {
            Log.e(TAG, "JC hook setup failed; supported target is 3.2.6", error);
        }
    }

    private static boolean enabled() {
        return ConfigManager.isEnabled(HookConfig.KEY_JC_LOCAL_MODE);
    }

    private static Method method(Class<?> owner, String name, Class<?> result,
                                 Class<?>... args) throws NoSuchMethodException {
        Method method = owner.getDeclaredMethod(name, args);
        if (method.getReturnType() != result) throw new NoSuchMethodException(name + " return type");
        method.setAccessible(true);
        return method;
    }
}
