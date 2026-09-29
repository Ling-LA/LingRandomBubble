package io.github.ling.randombubble.core;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class Reflect {
    private Reflect() {}
    private static final ConcurrentHashMap<Class<?>, ConcurrentHashMap<String, Field>> CACHE = new ConcurrentHashMap<>();
    public static Field field(Class<?> c, String name) throws NoSuchFieldException {
        ConcurrentHashMap<String, Field> byName = CACHE.get(c);
        if (byName == null) { byName = new ConcurrentHashMap<>(); CACHE.putIfAbsent(c, byName); byName = CACHE.get(c); }
        Field found = byName.get(name);
        if (found != null) return found;
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            try {
                found = k.getDeclaredField(name);
                if (Modifier.isStatic(found.getModifiers())) throw new NoSuchFieldException(name);
                found.setAccessible(true); byName.put(name, found); return found;
            } catch (NoSuchFieldException ignored) { /* try superclass */ }
        }
        throw new NoSuchFieldException(c.getName() + "." + name);
    }
    public static Object get(Object o, String name) throws ReflectiveOperationException {
        if (o == null) return null;
        return field(o.getClass(), name).get(o);
    }
    public static void set(Object o, String name, Object value) throws ReflectiveOperationException {
        field(o.getClass(), name).set(o, value);
    }
    public static Object newInstance(Class<?> c) throws ReflectiveOperationException {
        Constructor<?> ctor = c.getDeclaredConstructor(); ctor.setAccessible(true); return ctor.newInstance();
    }
    public static Object copy(Object o) throws ReflectiveOperationException {
        if (o == null) return null;
        Object dest = newInstance(o.getClass());
        for (Class<?> c = o.getClass(); c != Object.class && c != null; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                if (Modifier.isFinal(f.getModifiers())) throw new IllegalAccessException("Final field in mutable host object");
                f.setAccessible(true); f.set(dest, f.get(o));
            }
        }
        return dest;
    }
    public static Integer nullableInt(Object o) {
        if (o == null) return null;
        if (!(o instanceof Integer)) throw new IllegalArgumentException("Expected Integer");
        return (Integer)o;
    }
    public static int intValue(Object o) {
        if (!(o instanceof Integer)) throw new IllegalArgumentException("Expected int");
        return (Integer)o;
    }
    public static long longValue(Object o) {
        if (!(o instanceof Long)) throw new IllegalArgumentException("Expected long");
        return (Long)o;
    }
}
