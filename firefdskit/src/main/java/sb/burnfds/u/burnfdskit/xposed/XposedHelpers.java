package sb.burnfds.u.burnfdskit.xposed;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Reflection helpers with the legacy XposedHelpers shape. The modern API ships none of these.
 */
public final class XposedHelpers {

    private static final Map<Object, Map<String, Object>> ADDITIONAL_FIELDS = new WeakHashMap<>();

    private XposedHelpers() {
    }

    public static final class ClassNotFoundError extends Error {
        public ClassNotFoundError(String message, Throwable cause) {
            super(message, cause);
        }
    }

    // ---------------------------------------------------------------- classes

    public static Class<?> findClass(String className, ClassLoader classLoader) {
        final ClassLoader loader = classLoader == null ? ClassLoader.getSystemClassLoader() : classLoader;
        // Like the legacy helper, accept nested classes written with dots (Outer.Inner) by retrying with '$'
        String candidate = className;
        while (true) {
            try {
                return Class.forName(candidate, false, loader);
            } catch (ClassNotFoundException e) {
                final int lastDot = candidate.lastIndexOf('.');
                if (lastDot < 0) {
                    throw new ClassNotFoundError(className, e);
                }
                candidate = candidate.substring(0, lastDot) + '$' + candidate.substring(lastDot + 1);
            }
        }
    }

    // ---------------------------------------------------------------- hooks

    public static void findAndHookMethod(Class<?> clazz, String methodName, Object... parameterTypesAndCallback) {
        final XC_MethodHook callback = lastCallback(parameterTypesAndCallback);
        final Class<?>[] types = toClasses(clazz.getClassLoader(), parameterTypesAndCallback, 1);
        XposedBridge.hookMethod(findMethodExact(clazz, methodName, types), callback);
    }

    public static void findAndHookMethod(String className,
                                         ClassLoader classLoader,
                                         String methodName,
                                         Object... parameterTypesAndCallback) {
        findAndHookMethod(findClass(className, classLoader), methodName, parameterTypesAndCallback);
    }

    public static void findAndHookConstructor(Class<?> clazz, Object... parameterTypesAndCallback) {
        final XC_MethodHook callback = lastCallback(parameterTypesAndCallback);
        final Class<?>[] types = toClasses(clazz.getClassLoader(), parameterTypesAndCallback, 1);
        XposedBridge.hookMethod(findConstructorExact(clazz, types), callback);
    }

    public static void findAndHookConstructor(String className,
                                              ClassLoader classLoader,
                                              Object... parameterTypesAndCallback) {
        findAndHookConstructor(findClass(className, classLoader), parameterTypesAndCallback);
    }

    private static XC_MethodHook lastCallback(Object[] args) {
        if (args.length == 0 || !(args[args.length - 1] instanceof XC_MethodHook)) {
            throw new IllegalArgumentException("no callback defined");
        }
        return (XC_MethodHook) args[args.length - 1];
    }

    // Converts class objects / class names into classes, dropping the trailing callback
    private static Class<?>[] toClasses(ClassLoader classLoader, Object[] values, int trailing) {
        final Class<?>[] result = new Class<?>[Math.max(0, values.length - trailing)];
        for (int i = 0; i < result.length; i++) {
            final Object value = values[i];
            if (value instanceof Class) {
                result[i] = (Class<?>) value;
            } else if (value instanceof String) {
                result[i] = findClass((String) value, classLoader);
            } else {
                throw new IllegalArgumentException("parameter type must be a Class or a String: " + value);
            }
        }
        return result;
    }

    // ---------------------------------------------------------------- lookup

    public static Method findMethodExact(Class<?> clazz, String methodName, Object... parameterTypes) {
        final Class<?>[] types = toClasses(clazz.getClassLoader(), parameterTypes, 0);
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            try {
                final Method method = c.getDeclaredMethod(methodName, types);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                // keep looking in the superclass
            }
        }
        throw new NoSuchMethodError(clazz.getName() + "#" + methodName + Arrays.toString(types));
    }

    public static Constructor<?> findConstructorExact(Class<?> clazz, Class<?>... parameterTypes) {
        try {
            final Constructor<?> constructor = clazz.getDeclaredConstructor(parameterTypes);
            constructor.setAccessible(true);
            return constructor;
        } catch (NoSuchMethodException e) {
            throw new NoSuchMethodError(clazz.getName() + "#<init>" + Arrays.toString(parameterTypes));
        }
    }

    public static Method findMethodBestMatch(Class<?> clazz, String methodName, Object... args) {
        final Class<?>[] argTypes = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            argTypes[i] = args[i] instanceof Class ? (Class<?>) args[i] : args[i] == null ? null : args[i].getClass();
        }
        final Method method = findBestMethod(clazz, methodName, argTypes);
        if (method == null) {
            throw new NoSuchMethodError(clazz.getName() + "#" + methodName + Arrays.toString(argTypes));
        }
        return method;
    }

    private static Method findBestMethod(Class<?> clazz, String methodName, Class<?>[] argTypes) {
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            for (Method method : c.getDeclaredMethods()) {
                if (method.getName().equals(methodName) && isAssignable(method.getParameterTypes(), argTypes)) {
                    method.setAccessible(true);
                    return method;
                }
            }
        }
        return null;
    }

    private static boolean isAssignable(Class<?>[] parameterTypes, Class<?>[] argTypes) {
        if (parameterTypes.length != argTypes.length) {
            return false;
        }
        for (int i = 0; i < parameterTypes.length; i++) {
            if (argTypes[i] == null) {
                if (parameterTypes[i].isPrimitive()) {
                    return false;
                }
            } else if (!box(parameterTypes[i]).isAssignableFrom(box(argTypes[i]))) {
                return false;
            }
        }
        return true;
    }

    private static Class<?> box(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == int.class) return Integer.class;
        if (type == boolean.class) return Boolean.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        if (type == short.class) return Short.class;
        if (type == byte.class) return Byte.class;
        if (type == char.class) return Character.class;
        return type;
    }

    // ---------------------------------------------------------------- calls

    public static Object callMethod(Object obj, String methodName, Object... args) {
        return invoke(obj.getClass(), obj, methodName, null, args);
    }

    public static Object callMethod(Object obj, String methodName, Class<?>[] parameterTypes, Object... args) {
        return invoke(obj.getClass(), obj, methodName, parameterTypes, args);
    }

    public static Object callStaticMethod(Class<?> clazz, String methodName, Object... args) {
        return invoke(clazz, null, methodName, null, args);
    }

    private static Object invoke(Class<?> clazz, Object obj, String methodName, Class<?>[] parameterTypes, Object[] args) {
        final Class<?>[] types = parameterTypes != null ? parameterTypes : argumentTypes(args);
        final Method method = findBestMethod(clazz, methodName, types);
        if (method == null) {
            throw new NoSuchMethodError(clazz.getName() + "#" + methodName + Arrays.toString(types));
        }
        try {
            return method.invoke(obj, args);
        } catch (IllegalAccessException e) {
            throw new IllegalAccessError(e.getMessage());
        } catch (InvocationTargetException e) {
            throw new InvocationTargetError(e.getCause());
        }
    }

    private static Class<?>[] argumentTypes(Object[] args) {
        final Class<?>[] types = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            types[i] = args[i] == null ? null : args[i].getClass();
        }
        return types;
    }

    public static final class InvocationTargetError extends Error {
        public InvocationTargetError(Throwable cause) {
            super(cause);
        }
    }

    // ---------------------------------------------------------------- fields

    public static Field findField(Class<?> clazz, String fieldName) {
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            try {
                final Field field = c.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // keep looking in the superclass
            }
        }
        throw new NoSuchFieldError(clazz.getName() + "#" + fieldName);
    }

    public static Object getObjectField(Object obj, String fieldName) {
        try {
            return findField(obj.getClass(), fieldName).get(obj);
        } catch (IllegalAccessException e) {
            throw new IllegalAccessError(e.getMessage());
        }
    }

    public static void setObjectField(Object obj, String fieldName, Object value) {
        try {
            findField(obj.getClass(), fieldName).set(obj, value);
        } catch (IllegalAccessException e) {
            throw new IllegalAccessError(e.getMessage());
        }
    }

    public static int getIntField(Object obj, String fieldName) {
        try {
            return findField(obj.getClass(), fieldName).getInt(obj);
        } catch (IllegalAccessException e) {
            throw new IllegalAccessError(e.getMessage());
        }
    }

    public static void setIntField(Object obj, String fieldName, int value) {
        try {
            findField(obj.getClass(), fieldName).setInt(obj, value);
        } catch (IllegalAccessException e) {
            throw new IllegalAccessError(e.getMessage());
        }
    }

    public static boolean getBooleanField(Object obj, String fieldName) {
        try {
            return findField(obj.getClass(), fieldName).getBoolean(obj);
        } catch (IllegalAccessException e) {
            throw new IllegalAccessError(e.getMessage());
        }
    }

    public static void setBooleanField(Object obj, String fieldName, boolean value) {
        try {
            findField(obj.getClass(), fieldName).setBoolean(obj, value);
        } catch (IllegalAccessException e) {
            throw new IllegalAccessError(e.getMessage());
        }
    }

    public static Object getStaticObjectField(Class<?> clazz, String fieldName) {
        try {
            return findField(clazz, fieldName).get(null);
        } catch (IllegalAccessException e) {
            throw new IllegalAccessError(e.getMessage());
        }
    }

    public static void setStaticObjectField(Class<?> clazz, String fieldName, Object value) {
        try {
            findField(clazz, fieldName).set(null, value);
        } catch (IllegalAccessException e) {
            throw new IllegalAccessError(e.getMessage());
        }
    }

    public static void setStaticIntField(Class<?> clazz, String fieldName, int value) {
        try {
            findField(clazz, fieldName).setInt(null, value);
        } catch (IllegalAccessException e) {
            throw new IllegalAccessError(e.getMessage());
        }
    }

    public static void setStaticBooleanField(Class<?> clazz, String fieldName, boolean value) {
        try {
            findField(clazz, fieldName).setBoolean(null, value);
        } catch (IllegalAccessException e) {
            throw new IllegalAccessError(e.getMessage());
        }
    }

    // ------------------------------------------------- additional instance fields

    public static Object getAdditionalInstanceField(Object obj, String key) {
        synchronized (ADDITIONAL_FIELDS) {
            final Map<String, Object> fields = ADDITIONAL_FIELDS.get(obj);
            return fields == null ? null : fields.get(key);
        }
    }

    public static Object setAdditionalInstanceField(Object obj, String key, Object value) {
        synchronized (ADDITIONAL_FIELDS) {
            Map<String, Object> fields = ADDITIONAL_FIELDS.get(obj);
            if (fields == null) {
                fields = new HashMap<>();
                ADDITIONAL_FIELDS.put(obj, fields);
            }
            return fields.put(key, value);
        }
    }

    static boolean isStatic(Field field) {
        return Modifier.isStatic(field.getModifiers());
    }
}
