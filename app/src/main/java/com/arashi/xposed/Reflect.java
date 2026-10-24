package com.arashi.xposed;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;


public final class Reflect {

    private Reflect() {
    }

    public static Class<?> findClass(String name, ClassLoader cl) throws ClassNotFoundException {
        return Class.forName(name, false, cl);
    }

    public static Method findMethodExact(Class<?> clazz, String name, Class<?>... params) {
        Class<?> c = clazz;
        while (c != null) {
            try {
                Method m = c.getDeclaredMethod(name, params);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
                c = c.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    public static Object getStaticObjectField(Class<?> clazz, String name) {
        Class<?> c = clazz;
        while (c != null) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(null);
            } catch (NoSuchFieldException ignored) {
                c = c.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    public static Object getObjectField(Object instance, String name) {
        if (instance == null) {
            return null;
        }
        Class<?> c = instance.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(instance);
            } catch (NoSuchFieldException ignored) {
                c = c.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    public static Constructor<?>[] getDeclaredConstructors(Class<?> clazz) {
        try {
            Constructor<?>[] cs = clazz.getDeclaredConstructors();
            for (Constructor<?> c : cs) {
                c.setAccessible(true);
            }
            return cs;
        } catch (Throwable t) {
            return new Constructor<?>[0];
        }
    }

    
    public static Object callMethod(Object instance, String name, Object... args)
            throws Exception {
        if (instance == null) {
            return null;
        }
        Method m = findMethodByArgs(instance.getClass(), name, args);
        if (m == null) {
            throw new NoSuchMethodException(name);
        }
        m.setAccessible(true);
        return m.invoke(instance, args);
    }

    
    public static Object callStaticMethod(Class<?> clazz, String name, Object... args)
            throws Exception {
        Method m = findMethodByArgs(clazz, name, args);
        if (m == null) {
            throw new NoSuchMethodException(name);
        }
        m.setAccessible(true);
        return m.invoke(null, args);
    }

    private static Method findMethodByArgs(Class<?> clazz, String name, Object[] args) {
        int n = args == null ? 0 : args.length;
        Class<?> c = clazz;
        while (c != null) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterTypes().length == n) {
                    return m;
                }
            }
            c = c.getSuperclass();
        }
        return null;
    }
}
