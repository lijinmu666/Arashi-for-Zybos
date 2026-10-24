package com.arashi.zybos;

import android.content.Context;
import android.os.IBinder;
import android.util.Log;

import java.lang.reflect.Method;






public final class ZybosService {
    private static final String TAG = "ArashiZybos";

    
    public static final String CLASS_MANAGER = "android.os.ZybosSysApiManager";
    
    
    public static final String CLASS_IFACE = "android.os.IZybosSysApiManager";
    
    public static final String CLASS_STUB = "android.os.IZybosSysApiManager$Stub";
    private static final String[] CANDIDATE_SERVICES = {
            "zybos_manager"
    };

    
    private static Object proxy;
    private static IBinder rawBinder;
    private static Class<?> ifaceClass;
    private static boolean detected;
    private static String lastError = "";

    private ZybosService() {}

    
    public static synchronized boolean detect(Context context) {
        if (detected && (proxy != null || rawBinder != null)) return true;
        String binderError = "未尝试";

        ifaceClass = loadClass(CLASS_IFACE);

        
        try {
            Class<?> sm = Class.forName("android.os.ServiceManager");
            Method getService = sm.getMethod("getService", String.class);
            for (String name : CANDIDATE_SERVICES) {
                try {
                    IBinder binder = (IBinder) getService.invoke(null, name);
                    if (binder == null) {
                        binderError = name + ": getService 返回 null";
                        Log.w(TAG, binderError);
                        continue;
                    }
                    Log.i(TAG, name + ": 已取得 Binder");
                    
                    rawBinder = binder;
                    Object p = asInterface(binder);
                    if (p == null) {
                        binderError = name + ": asInterface 返回 null";
                        Log.w(TAG, binderError);
                        detected = true;
                        lastError = binderError;
                        return true;
                    }
                    Class<?> pc = p.getClass();
                    if (ifaceClass != null && !ifaceClass.isInstance(p)) {
                        binderError = name + ": 返回类型不匹配 " + pc.getName();
                        Log.w(TAG, binderError);
                        continue;
                    }
                    proxy = p;
                    rawBinder = binder;
                    detected = true;
                    lastError = "";
                    Log.i(TAG, "已绑定 ZybOS 服务：" + name + " -> " + pc.getName());
                    return true;
                } catch (Throwable t) {
                    binderError = "svc[" + name + "]: " + unwrap(t);
                    Log.w(TAG, binderError, t);
                }
            }
        } catch (Throwable t) {
            binderError = "ServiceManager: " + unwrap(t);
            Log.w(TAG, binderError, t);
        }
        lastError = binderError;

        
        try {
            Class<?> mgr = Class.forName(CLASS_MANAGER);
            Method getInstance = mgr.getDeclaredMethod("getInstance", Context.class);
            getInstance.setAccessible(true);
            Object inst = getInstance.invoke(null, context);
            if (inst != null) {
                Method getServiceM = findMethodOn(mgr, "getService", new Class<?>[0]);
                Object p = getServiceM != null ? getServiceM.invoke(inst) : null;
                if (p != null) {
                    proxy = p;
                    detected = true;
                    lastError = "";
                    Log.i(TAG, "已绑定 ZybOS 服务（getInstance 路径）");
                    return true;
                }
                proxy = inst;
                detected = true;
                lastError = "";
                Log.i(TAG, "已获取 ZybosSysApiManager 实例（直接使用）");
                return true;
            }
        } catch (Throwable t) {
            Log.w(TAG, "备用 getInstance(Context) 失败（不覆盖 Binder 错误）: " + unwrap(t));
        }

        Log.w(TAG, "ZybosSysApiManager 不可用：" + lastError);
        return false;
    }

    private static Object asInterface(IBinder binder) {
        try {
            Class<?> stub = Class.forName(CLASS_STUB);
            Method asInterface;
            try {
                asInterface = stub.getDeclaredMethod("asInterface", IBinder.class);
            } catch (NoSuchMethodException e) {
                asInterface = stub.getMethod("asInterface", IBinder.class);
            }
            asInterface.setAccessible(true);
            Object result = asInterface.invoke(null, binder);
            if (result != null) return result;
        } catch (Throwable t) {
            Log.w(TAG, "asInterface 失败，尝试 Proxy: " + unwrap(t), t);
        }
        try {
            Class<?> proxyClass = Class.forName(CLASS_STUB + "$Proxy");
            java.lang.reflect.Constructor<?> ctor = proxyClass.getDeclaredConstructor(IBinder.class);
            ctor.setAccessible(true);
            return ctor.newInstance(binder);
        } catch (Throwable t) {
            Log.w(TAG, "Proxy 构造失败: " + unwrap(t), t);
        }
        return null;
    }

    private static Class<?> loadClass(String name) {
        try { return Class.forName(name); } catch (Throwable t) { return null; }
    }

    public static boolean isAvailable() { return proxy != null || rawBinder != null; }
    public static String getLastError() { return lastError; }

    

    






    public static boolean silentInstallApk(String apkPath, boolean isSdcard,
                                           String packageName) {
        if (rawBinder != null && proxy == null) {
            return transactInstall(rawBinder, apkPath, isSdcard, packageName);
        }
        if (proxy == null) return false;
        try {
            
            Method m = findMethodOn(proxy.getClass(), "silentInstallApk",
                    String.class, boolean.class, String.class);
            if (m == null && ifaceClass != null) {
                m = findMethodOn(ifaceClass, "silentInstallApk",
                        String.class, boolean.class, String.class);
            }
            if (m == null) {
                lastError = "未找到 silentInstallApk(String,boolean,String)";
                Log.w(TAG, lastError);
                return false;
            }
            m.setAccessible(true);
            m.invoke(proxy, apkPath, isSdcard, packageName);
            Log.i(TAG, "silentInstallApk 已下发: " + apkPath);
            return true;
        } catch (Throwable t) {
            lastError = "silentInstallApk: " + unwrap(t);
            Log.w(TAG, lastError);
            return false;
        }
    }

    





    public static boolean silentUninstallApk(String packageName, boolean isKeepData) {
        if (rawBinder != null && proxy == null) {
            return transactUninstall(rawBinder, packageName, isKeepData);
        }
        if (proxy == null) return false;
        try {
            Method m = findMethodOn(proxy.getClass(), "silentUninstallApk",
                    String.class, boolean.class);
            if (m == null && ifaceClass != null) {
                m = findMethodOn(ifaceClass, "silentUninstallApk",
                        String.class, boolean.class);
            }
            if (m == null) {
                lastError = "未找到 silentUninstallApk(String,boolean)";
                Log.w(TAG, lastError);
                return false;
            }
            m.setAccessible(true);
            m.invoke(proxy, packageName, isKeepData);
            Log.i(TAG, "silentUninstallApk 已下发: " + packageName);
            return true;
        } catch (Throwable t) {
            lastError = "silentUninstallApk: " + unwrap(t);
            Log.w(TAG, lastError);
            return false;
        }
    }

    private static boolean transactInstall(IBinder binder, String path, boolean sdcard,
                                            String packageName) {
        android.os.Parcel data = android.os.Parcel.obtain(binder);
        android.os.Parcel reply = android.os.Parcel.obtain();
        try {
            data.writeInterfaceToken("android.os.IZybosSysApiManager");
            data.writeString(path);
            data.writeBoolean(sdcard);
            data.writeString(packageName);
            boolean sent = binder.transact(0x19, data, reply, 0);
            reply.readException();
            Log.i(TAG, "raw silentInstallApk 已下发，transact=" + sent);
            return sent;
        } catch (Throwable t) {
            lastError = "raw silentInstallApk: " + unwrap(t);
            Log.w(TAG, lastError, t);
            return false;
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    private static boolean transactUninstall(IBinder binder, String packageName, boolean keepData) {
        android.os.Parcel data = android.os.Parcel.obtain(binder);
        android.os.Parcel reply = android.os.Parcel.obtain();
        try {
            data.writeInterfaceToken("android.os.IZybosSysApiManager");
            data.writeString(packageName);
            data.writeBoolean(keepData);
            boolean sent = binder.transact(0x18, data, reply, 0);
            reply.readException();
            Log.i(TAG, "raw silentUninstallApk 已下发，transact=" + sent);
            return sent;
        } catch (Throwable t) {
            lastError = "raw silentUninstallApk: " + unwrap(t);
            Log.w(TAG, lastError, t);
            return false;
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    private static Throwable unwrap(Throwable t) {
        return t.getCause() != null ? t.getCause() : t;
    }

    

    private static Method findMethodOn(Class<?> cls, String name, Class<?>... paramTypes) {
        if (cls == null) return null;
        Class<?>[] types = paramTypes != null ? paramTypes : new Class<?>[0];
        try {
            Method m = cls.getDeclaredMethod(name, types);
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException ignored) { }
        try {
            Method m = cls.getMethod(name, types);
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException ignored) { }
        try {
            for (Method m : cls.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == types.length) {
                    m.setAccessible(true);
                    return m;
                }
            }
        } catch (Throwable ignored) { }
        try {
            for (Method m : cls.getMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == types.length) {
                    m.setAccessible(true);
                    return m;
                }
            }
        } catch (Throwable ignored) { }
        try {
            for (Class<?> itf : cls.getInterfaces()) {
                Method m = findMethodOn(itf, name, types);
                if (m != null) return m;
            }
        } catch (Throwable ignored) { }
        return null;
    }

}
