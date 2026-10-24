package com.arashi.xposed.hooks;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.util.Log;

import io.github.libxposed.api.XposedModule;

import com.arashi.xposed.ConfigManager;
import com.arashi.xposed.HookConfig;
import com.arashi.xposed.Reflect;

import java.lang.reflect.Method;
import java.util.List;

public final class SuperviseHooks {

    private static final String TAG = "[Arashi-Supervise]";
    private static final String AUTHORITY = "com.zuoyebang.iot.pad.supervise";

private static final String COL_PACKAGE = "packageName";
    private static final String COL_ALLOW = "allow";
     
    private static final String COL_CLASS = "className";

    private SuperviseHooks() {
    }

    public static void hookSupervise(XposedModule mod, ClassLoader cl) {
        hookProviderQuery(mod, cl);
        hookPadUseTimeOut(mod, cl);
        hookUsageReport(mod, cl);
        Log.i(TAG, "supervise hooks initialized");
    }

    private static void hookUsageReport(XposedModule mod, ClassLoader cl) {
        try {
            Class<?> handler = Class.forName(
                    "com.zuoyebang.iot.pad.supervise.common.SuperviseHandler", false, cl);
            int hooked = 0;
            for (Method method : handler.getDeclaredMethods()) {
                if (!"uploadUsageBusiness".equals(method.getName())) {
                    continue;
                }
                method.setAccessible(true);
                mod.hook(method).intercept(chain -> {
                    if (ConfigManager.isEnabled(HookConfig.KEY_BLOCK_USAGE_REPORT)) {
                        Log.i(TAG, "block app usage duration report");
                        return Reflect.defaultReturnValue(method.getReturnType());
                    }
                    return chain.proceed();
                });
                hooked++;
            }
            if (hooked == 0) {
                Log.w(TAG, "uploadUsageBusiness not found");
            } else {
                Log.i(TAG, "uploadUsageBusiness hooked: " + hooked + " total");
            }
        } catch (Throwable t) {
            Log.w(TAG, "usage report hook failed: " + t);
        }
    }

private static void hookProviderQuery(XposedModule mod, ClassLoader cl) {
        try {
            Class<?> clz = Class.forName(
                    "com.zuoyebang.iot.pad.supervise.provider.SuperviseProvider", false, cl);
            Method m = Reflect.findMethodExact(clz, "query",
                    Uri.class, String[].class, String.class, String[].class, String.class);
            if (m == null) {
                Log.w(TAG, "SuperviseProvider.query not found");
                return;
            }
            mod.hook(m).intercept(chain -> {
                Cursor neutralized = neutralized((Uri) chain.getArg(0));
                if (neutralized != null) {
                    return neutralized;
                }
                return chain.proceed();
            });
            Log.i(TAG, "SuperviseProvider.query hook registered");
        } catch (Throwable t) {
            Log.w(TAG, "SuperviseProvider.query hook failed: " + t);
        }
    }

private static void hookPadUseTimeOut(XposedModule mod, ClassLoader cl) {
        try {
            Class<?> handler = Class.forName(
                    "com.zuoyebang.iot.pad.supervise.common.SuperviseHandler", false, cl);
            Method m = Reflect.findMethodExact(handler, "dealWithPadUseTimeOut",
                    android.os.Message.class);
            if (m == null) {
                Log.w(TAG, "dealWithPadUseTimeOut not found");
                return;
            }
            mod.hook(m).intercept(chain -> {
                if (ConfigManager.isEnabled(HookConfig.KEY_NO_TIMEOUT_LOCK)) {
                    Log.i(TAG, "block dealWithPadUseTimeOut (pad time out)");
                    return null;
                }
                return chain.proceed();
            });
            Log.i(TAG, "dealWithPadUseTimeOut hook registered");
        } catch (Throwable t) {
            Log.w(TAG, "dealWithPadUseTimeOut hook failed: " + t);
        }
    }

private static Cursor neutralized(Uri uri) {
        if (uri == null || !AUTHORITY.equals(uri.getAuthority())) {
            return null;
        }
        List<String> segments = uri.getPathSegments();
        if (segments.isEmpty()) {
            return null;
        }

        String first = segments.get(0);

if ("disable".equals(first)) {
            if (!ConfigManager.isEnabled(HookConfig.KEY_NEUTRALIZE_POLICY)) {
                return null;
            }
            Log.i(TAG, "neutralize disabled apps query");
            return new MatrixCursor(new String[]{COL_PACKAGE});
        }

if ("app".equals(first) && segments.size() >= 3) {
            String second = segments.get(1);
            if ("enable".equals(second) || "enableExcludeAdvance".equals(second)) {
                if (!ConfigManager.isEnabled(HookConfig.KEY_NEUTRALIZE_POLICY)) {
                    return null;
                }
                String packageName = segments.get(2);
                Log.i(TAG, "neutralize app enable query: " + packageName);
                MatrixCursor cursor = new MatrixCursor(new String[]{COL_PACKAGE, COL_ALLOW});
                cursor.addRow(new Object[]{packageName, Boolean.TRUE});
                return cursor;
            }
        }

if ("advance".equals(first)) {
            if (!ConfigManager.isEnabled(HookConfig.KEY_NEUTRALIZE_ADVANCE)) {
                return null;
            }
            Log.i(TAG, "neutralize advance control query");
            return new MatrixCursor(new String[]{COL_CLASS});
        }

        return null;
    }
}
