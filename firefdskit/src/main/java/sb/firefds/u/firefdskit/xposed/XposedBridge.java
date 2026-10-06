package sb.firefds.u.firefdskit.xposed;

import android.util.Log;

import androidx.annotation.NonNull;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Member;
import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;

/**
 * Bridge with the legacy XposedBridge shape on top of the modern libxposed API.
 */
public final class XposedBridge {
    private static final String TAG = "FFK";
    private static volatile XposedInterface xposed;

    private XposedBridge() {
    }

    public static void init(@NonNull XposedInterface xposedInterface) {
        xposed = xposedInterface;
    }

    public static void log(String text) {
        final XposedInterface framework = xposed;
        if (framework != null) {
            framework.log(Log.INFO, TAG, text == null ? "null" : text);
        } else {
            Log.i(TAG, String.valueOf(text));
        }
    }

    public static void log(Throwable t) {
        final XposedInterface framework = xposed;
        if (framework != null) {
            framework.log(Log.ERROR, TAG, "error", t);
        } else {
            Log.e(TAG, "error", t);
        }
    }

    public static void hookMethod(Member member, XC_MethodHook callback) {
        final XposedInterface framework = xposed;
        if (framework == null) {
            throw new IllegalStateException("Xposed framework not attached");
        }
        if (!(member instanceof Executable)) {
            throw new IllegalArgumentException("Only methods and constructors can be hooked: " + member);
        }
        framework.hook((Executable) member).intercept(chain -> {
            final XC_MethodHook.MethodHookParam param = new XC_MethodHook.MethodHookParam();
            param.method = chain.getExecutable();
            param.thisObject = chain.getThisObject();
            param.args = chain.getArgs().toArray();

            try {
                callback.beforeHookedMethod(param);
            } catch (Throwable t) {
                log(t);
                param.returnEarly = false;
            }

            if (!param.returnEarly) {
                try {
                    param.setOriginalOutcome(chain.proceed(param.args), null);
                } catch (Throwable t) {
                    param.setOriginalOutcome(null, t);
                }
            }

            try {
                callback.afterHookedMethod(param);
            } catch (Throwable t) {
                log(t);
            }

            if (param.hasThrowable()) {
                throw param.getThrowable();
            }
            return param.getResult();
        });
    }

    public static void hookAllMethods(Class<?> clazz, String methodName, XC_MethodHook callback) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                hookMethod(method, callback);
            }
        }
    }

    public static void hookAllConstructors(Class<?> clazz, XC_MethodHook callback) {
        for (Constructor<?> constructor : clazz.getDeclaredConstructors()) {
            hookMethod(constructor, callback);
        }
    }
}
