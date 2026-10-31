package com.lijinmu.arashi.xposed.hooks;

import android.graphics.Path;
import android.graphics.RectF;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import com.lijinmu.arashi.xposed.ConfigManager;
import com.lijinmu.arashi.xposed.HookConfig;
import io.github.libxposed.api.XposedModule;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Framework resource overlay, not a drawable hook in each app. Android 14 added
 * fabricated string resources, allowing config_icon_mask to reach every package.
 */
public final class GlobalIconMaskHooks {
    private static final String TAG = "[Arashi-GlobalIcons]";
    private static final String OWNER = "android";
    private static final String NAME = "ArashiIosIconMask";
    private static boolean started;
    private static volatile String pathData;

    private GlobalIconMaskHooks() {}

    public static synchronized void hookSystemServer(XposedModule module, ClassLoader loader) {
        if (started || Build.VERSION.SDK_INT < 34) return;
        try {
            // Do not activate an overlay unless we can keep its enabled state out
            // of overlays.xml. Disabling LSPosed + reboot must restore stock icons.
            Class<?> settings = Class.forName("com.android.server.om.OverlayManagerSettings", false, loader);
            Field items = settings.getDeclaredField("mItems");
            items.setAccessible(true);
            Class<?> item = Class.forName(
                    "com.android.server.om.OverlayManagerSettings$SettingsItem", false, loader);
            Field identifier = item.getDeclaredField("mOverlay");
            identifier.setAccessible(true);
            Method persist = settings.getDeclaredMethod("persist", OutputStream.class);
            module.hook(persist).intercept(chain -> {
                @SuppressWarnings("unchecked")
                List<Object> table = (List<Object>) items.get(chain.getThisObject());
                // OMS holds its own lock across this call. Restore the exact ordering
                // even if serialization throws; other overlays are never changed.
                List<Object> saved = new ArrayList<>(table);
                boolean removed = false;
                try {
                    for (int index = table.size() - 1; index >= 0; index--) {
                        Object id = identifier.get(table.get(index));
                        if (isOwnOverlay(id)) {
                            table.remove(index);
                            removed = true;
                        }
                    }
                    return chain.proceed();
                } finally {
                    if (removed) {
                        table.clear();
                        table.addAll(saved);
                    }
                }
            });
            started = true;
            Thread worker = new Thread(() -> run(loader), "Arashi-GlobalIconMask");
            worker.setDaemon(true);
            worker.start();
        } catch (Throwable error) {
            report("全局图标遮罩初始化失败，未应用覆盖", error);
        }
    }

    static boolean isOwnOverlay(Object id) throws ReflectiveOperationException {
        return id != null
                && OWNER.equals(id.getClass().getMethod("getPackageName").invoke(id))
                && NAME.equals(id.getClass().getMethod("getOverlayName").invoke(id));
    }

    private static void run(ClassLoader loader) {
        Boolean applied = null;
        int[] appliedUsers = new int[0];
        String lastError = null;
        while (!Thread.currentThread().isInterrupted()) {
            try {
                // Never interpret a temporarily unavailable provider as "off".
                if (ConfigManager.hasSettingsSnapshot()) {
                    boolean enabled = ConfigManager.isEnabledCached(HookConfig.KEY_IOS_SYSTEM_ICON_MASK);
                    int[] users = userIds(loader);
                    if (applied == null || applied != enabled || !Arrays.equals(users, appliedUsers)) {
                        apply(enabled, users);
                        applied = enabled;
                        appliedUsers = users;
                        lastError = null;
                        com.lijinmu.arashi.logs.HookLogBridge.info(
                                enabled ? "已启用系统全局 iOS 图标遮罩" : "已恢复系统默认图标遮罩");
                    }
                }
            } catch (Throwable error) {
                // No binder/overlay work runs on the system_server boot or UI thread.
                String description = error.toString();
                if (!description.equals(lastError)) {
                    report("系统全局图标遮罩更新失败，将稍后重试", error);
                    lastError = description;
                }
            }
            try {
                Thread.sleep(5000);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static int[] userIds(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> local = Class.forName("com.android.server.LocalServices", false, loader);
        Class<?> userManager = Class.forName("com.android.server.pm.UserManagerInternal", false, loader);
        Object service = local.getMethod("getService", Class.class).invoke(null, userManager);
        if (service == null) throw new IllegalStateException("UserManager is not ready");
        int[] users = (int[]) userManager.getMethod("getUserIds").invoke(service);
        if (users == null || users.length == 0) throw new IllegalStateException("No Android users");
        users = users.clone();
        Arrays.sort(users);
        return users;
    }

    static void apply(boolean enabled, int[] users) throws ReflectiveOperationException {
        long identity = Binder.clearCallingIdentity();
        try {
            Class<?> manager = Class.forName("android.os.ServiceManager");
            IBinder binder = (IBinder) manager.getMethod("getService", String.class).invoke(null, "overlay");
            if (binder == null) throw new IllegalStateException("OverlayManager is not ready");
            Class<?> stub = Class.forName("android.content.om.IOverlayManager$Stub");
            Object service = stub.getMethod("asInterface", IBinder.class).invoke(null, binder);
            Class<?> api = Class.forName("android.content.om.IOverlayManager");
            Class<?> overlay = Class.forName("android.content.om.FabricatedOverlay");
            Class<?> idType = Class.forName("android.content.om.OverlayIdentifier");
            Object id = idType.getConstructor(String.class, String.class).newInstance(OWNER, NAME);
            Class<?> transaction = Class.forName("android.content.om.OverlayManagerTransaction");
            Class<?> builderType = Class.forName("android.content.om.OverlayManagerTransaction$Builder");
            Object builder = builderType.getConstructor().newInstance();
            if (enabled) {
                Class<?> fabricatedBuilder = Class.forName("android.content.om.FabricatedOverlay$Builder");
                Object fabricated = fabricatedBuilder.getConstructor(String.class, String.class, String.class)
                        .newInstance(OWNER, NAME, "android");
                // Type 3 is TYPE_STRING; no theme/accent/navigation resources are touched.
                fabricatedBuilder.getMethod("setResourceValue", String.class, int.class, String.class)
                        .invoke(fabricated, "android:string/config_icon_mask", 3, maskPathData());
                Object built = fabricatedBuilder.getMethod("build").invoke(fabricated);
                builderType.getMethod("registerFabricatedOverlay", overlay).invoke(builder, built);
                Method enable = builderType.getMethod("setEnabled", idType, boolean.class, int.class);
                for (int user : users) enable.invoke(builder, id, true, user);
            } else {
                // Unregister only our named overlay. Never disable the user's other
                // icon-shape overlays, and never fabricate anything when default-off.
                Method info = api.getMethod("getOverlayInfoByIdentifier", idType, int.class);
                boolean present = false;
                for (int user : users) {
                    if (info.invoke(service, id, user) != null) { present = true; break; }
                }
                if (!present) return;
                builderType.getMethod("unregisterFabricatedOverlay", idType).invoke(builder, id);
            }
            Object built = builderType.getMethod("build").invoke(builder);
            api.getMethod("commit", transaction).invoke(service, built);
            if (enabled) {
                Method info = api.getMethod("getOverlayInfoByIdentifier", idType, int.class);
                for (int user : users) {
                    Object state = info.invoke(service, id, user);
                    if (state == null || !Boolean.TRUE.equals(state.getClass().getMethod("isEnabled").invoke(state))) {
                        throw new IllegalStateException("Icon overlay did not enable for user " + user);
                    }
                }
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    static String maskPathData() {
        String cached = pathData;
        if (cached != null) return cached;
        // Reuse the desktop mask instead of maintaining another squircle formula.
        // Approximation error is <=0.02 of the 100-unit viewport, below a pixel.
        Path mask = IosIconDrawable.maskFor(new RectF(0, 0, 100, 100));
        float[] points = mask.approximate(0.02f);
        if (points.length < 9 || points.length % 3 != 0) {
            throw new IllegalStateException("Invalid iOS mask geometry");
        }
        StringBuilder result = new StringBuilder(points.length * 8);
        for (int index = 0; index < points.length; index += 3) {
            float x = points[index + 1], y = points[index + 2];
            if (!Float.isFinite(x) || !Float.isFinite(y)) {
                throw new IllegalStateException("Non-finite iOS mask point");
            }
            result.append(index == 0 ? 'M' : 'L').append(x).append(',').append(y);
        }
        cached = result.append('Z').toString();
        pathData = cached;
        return cached;
    }

    private static void report(String message, Throwable error) {
        Log.w(TAG, message, error);
        com.lijinmu.arashi.logs.HookLogBridge.error(message, error);
    }
}
