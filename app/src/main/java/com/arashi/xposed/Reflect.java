package com.arashi.xposed;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

public final class Reflect {

private static final ConcurrentHashMap<String, Holder> FIELD_CACHE = new ConcurrentHashMap<>();

private static final class Holder {
        final Field field;

        Holder(Field field) {
            this.field = field;
        }
    }

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
        Field f = lookupField(clazz, name);
        if (f == null) {
            return null;
        }
        try {
            return f.get(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

public static boolean setStaticObjectField(Class<?> clazz, String name, Object value) {
        Field f = lookupField(clazz, name);
        if (f == null) {
            return false;
        }
        try {
            f.set(null, value);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static Object getObjectField(Object instance, String name) {
        if (instance == null) {
            return null;
        }
        Field f = lookupField(instance.getClass(), name);
        if (f == null) {
            return null;
        }
        try {
            return f.get(instance);
        } catch (Throwable ignored) {
            return null;
        }
    }

private static Field lookupField(Class<?> clazz, String name) {
        if (clazz == null || name == null) {
            return null;
        }
        String key = clazz.getName() + '#' + name;
        Holder holder = FIELD_CACHE.computeIfAbsent(key, k -> {
            for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
                try {
                    Field f = c.getDeclaredField(name);
                    f.setAccessible(true);
                    return new Holder(f);
                } catch (NoSuchFieldException ignored) {
                } catch (Throwable ignored) {
                    break;
                }
            }
            return new Holder(null);
        });
        return holder.field;
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

public static Object defaultReturnValue(Class<?> returnType) {
        if (returnType == void.class) {
            return null;
        }
        if (returnType == boolean.class) {
            return Boolean.FALSE;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == float.class) {
            return 0f;
        }
        if (returnType == double.class) {
            return 0d;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == char.class) {
            return (char) 0;
        }
        return null;
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
