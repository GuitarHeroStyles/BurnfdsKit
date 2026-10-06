/*
 * Copyright (C) 2023 Shauli Bracha for Firefds Kit Project (Firefds@xda)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package sb.firefds.u.firefdskit;

import static de.robv.android.xposed.XposedBridge.hookMethod;
import static de.robv.android.xposed.XposedBridge.log;
import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.callStaticMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static sb.firefds.u.firefdskit.utils.Packages.FIREFDSKIT;
import static sb.firefds.u.firefdskit.utils.Packages.SYSTEM_UI;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;

public class XPM34 {
    private static final String PERMISSION = "com.android.server.pm.permission";
    private static final String PERMISSION_MANAGER_SERVICE = PERMISSION + ".PermissionManagerServiceImpl";

    private static final String REBOOT = "android.permission.REBOOT";
    private static final String WRITE_SETTINGS = "android.permission.WRITE_SETTINGS";
    private static final String STATUSBAR = "android.permission.EXPAND_STATUS_BAR";
    private static final String RECOVERY = "android.permission.RECOVERY";
    private static final String POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS";
    private static final String ACCESS_SCREEN_RECORDER_SVC = "com.samsung.android.app.screenrecorder.permission" +
                                                             ".ACCESS_SCREEN_RECORDER_SVC";

    private static boolean restoreLogged;

    public static void doHook(ClassLoader classLoader) {
        log("FFK: XPM34.doHook started");
        try {
            final Class<?> pmServiceClass = findClass(PERMISSION_MANAGER_SERVICE, classLoader);

            // The signature of restorePermissionState changed between Android releases
            // (AndroidPackage -> PackageState, extra/removed params), so hook every overload by name.
            int hooked = 0;
            for (Method method : pmServiceClass.getDeclaredMethods()) {
                if (!method.getName().equals("restorePermissionState") ||
                    method.getParameterTypes().length == 0) {
                    continue;
                }
                hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!restoreLogged) {
                            restoreLogged = true;
                            log("FFK: restorePermissionState called, first package: " + param.args[0]);
                        }
                        try {
                            grantPermissions(param.thisObject, param.args[0]);
                        } catch (Throwable e) {
                            log(e);
                        }
                    }
                });
                hooked++;
            }
            log("FFK: hooked " + hooked + " restorePermissionState method(s) in " + PERMISSION_MANAGER_SERVICE);


            // Probe + trigger: these are called constantly once the system runs, so use the first call to
            // (re)grant permissions from a background thread, independent of restorePermissionState timing.
            int probes = 0;
            for (Method method : pmServiceClass.getDeclaredMethods()) {
                final String name = method.getName();
                if (name.equals("checkUidPermission") || name.equals("checkPermission")) {
                    hookMethod(method, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            startGrantThread(param.thisObject, classLoader);
                        }
                    });
                    probes++;
                }
            }
            log("FFK: hooked " + probes + " permission check probe method(s)");


            // Discover which permission classes this ROM really uses
            logPermissionClasses(classLoader);
            hookOuterService(classLoader);

            // restorePermissionState may run before the hook is installed (or not at all for unchanged
            // packages), so grant again once the system is ready.
            Method onSystemReady = pmServiceClass.getDeclaredMethod("onSystemReady");
            hookMethod(onSystemReady, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    log("FFK: PermissionManagerServiceImpl.onSystemReady called");
                    try {
                        grantAfterSystemReady(param.thisObject, classLoader);
                    } catch (Throwable e) {
                        log(e);
                    }
                }
            });
        } catch (Throwable e) {
            log(e);
        }
    }

    private static void grantPermissions(Object permissionManager, Object pkg) {
        if (pkg == null) {
            return;
        }
        final String pkgName = (String) callMethod(pkg, "getPackageName");
        if (!FIREFDSKIT.equals(pkgName) && !SYSTEM_UI.equals(pkgName)) {
            return;
        }
        log("FFK: granting permissions to " + pkgName);
        final Object mRegistry = getObjectField(permissionManager, "mRegistry");
        if (pkgName.equals(FIREFDSKIT)) {
            grantInstallPermission(mRegistry, STATUSBAR, pkg, permissionManager);
            grantInstallPermission(mRegistry, WRITE_SETTINGS, pkg, permissionManager);
            grantInstallPermission(mRegistry, POST_NOTIFICATIONS, pkg, permissionManager);
        }
        grantInstallPermission(mRegistry, REBOOT, pkg, permissionManager);
        grantInstallPermission(mRegistry, RECOVERY, pkg, permissionManager);
        grantInstallPermission(mRegistry, ACCESS_SCREEN_RECORDER_SVC, pkg, permissionManager);
    }

    private static void grantInstallPermission(Object mRegistry,
                                               String permission,
                                               Object pkg,
                                               Object permissionManager) {
        try {
            Object bp = callMethod(mRegistry, "getPermission", permission);
            Object uidState = callMethod(permissionManager, "getUidStateLocked", pkg, 0);
            callMethod(uidState, "grantPermission", bp);
            log("FFK: granted " + permission);
        } catch (Throwable e) {
            log("FFK: failed to grant " + permission);
            log(e);
        }
    }

    private static boolean grantThreadStarted;
    private static int outerProbeAttempts;

    private static synchronized void startGrantThread(Object permissionManager, ClassLoader classLoader) {
        if (grantThreadStarted) {
            return;
        }
        grantThreadStarted = true;
        log("FFK: permission check probe fired, starting grant thread");
        new Thread(() -> {
            for (int attempt = 0; attempt < 6; attempt++) {
                try {
                    Thread.sleep(attempt == 0 ? 20000 : 30000);
                    grantAfterSystemReady(permissionManager, classLoader);
                } catch (Throwable e) {
                    log(e);
                }
            }
        }, "FFK-grant").start();
    }

    private static void grantAfterSystemReady(Object permissionManager, ClassLoader classLoader) {
        final Class<?> localServices = findClass("com.android.server.LocalServices", classLoader);
        final Class<?> pmInternal = findClass("com.android.server.pm.PackageManagerInternal", classLoader);
        final Object pmi = callStaticMethod(localServices, "getService", pmInternal);
        final Object lock = getObjectField(permissionManager, "mLock");
        for (String name : new String[]{FIREFDSKIT, SYSTEM_UI}) {
            final Object pkg = callMethod(pmi, "getPackage", name);
            if (pkg == null) {
                log("FFK: package not found on system ready: " + name);
                continue;
            }
            synchronized (lock) {
                grantPermissions(permissionManager, pkg);
            }
        }
    }

    private static void logPermissionClasses(ClassLoader classLoader) {
        new Thread(() -> {
            try {
                Object pathList = getObjectField(classLoader, "pathList");
                Object[] elements = (Object[]) getObjectField(pathList, "dexElements");
                StringBuilder sb = new StringBuilder();
                for (Object element : elements) {
                    dalvik.system.DexFile dexFile = (dalvik.system.DexFile) getObjectField(element, "dexFile");
                    if (dexFile == null) {
                        continue;
                    }
                    java.util.Enumeration<String> entries = dexFile.entries();
                    while (entries.hasMoreElements()) {
                        String name = entries.nextElement();
                        if (name.startsWith("com.android.server.pm.permission.")) {
                            sb.append(name.substring("com.android.server.pm.permission.".length())).append(' ');
                        }
                    }
                }
                logChunked("FFK: permission classes: " + sb);
            } catch (Throwable e) {
                log("FFK: cannot list permission classes");
                log(e);
            }
        }, "FFK-list").start();
    }

    private static void hookOuterService(ClassLoader classLoader) {
        final String[] classes = {PERMISSION + ".PermissionManagerService",
                                  PERMISSION + ".PermissionManagerService$PermissionManagerServiceInternalImpl"};
        for (String className : classes) {
            try {
                Class<?> clazz = findClass(className, classLoader);
                int count = 0;
                for (Method method : clazz.getDeclaredMethods()) {
                    final String name = method.getName();
                    if (name.equals("checkUidPermission") || name.equals("checkPermission")) {
                        hookMethod(method, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                if (grantThreadStarted || outerProbeAttempts >= 5) {
                                    return;
                                }
                                outerProbeAttempts++;
                                try {
                                    log("FFK: " + className + "." + name + " fired, attempt " + outerProbeAttempts);
                                    if (outerProbeAttempts == 1) {
                                        StringBuilder graph = new StringBuilder();
                                        dumpGraph(param.thisObject, 0, "", graph, new java.util.IdentityHashMap<>());
                                        logChunked("FFK: graph " + graph);
                                    }
                                    Object impl = resolveImpl(param.thisObject, 0, new java.util.IdentityHashMap<>());
                                    log("FFK: resolved impl=" + (impl == null ? null : impl.getClass().getName()));
                                    if (impl != null) {
                                        startGrantThread(impl, classLoader);
                                    }
                                } catch (Throwable e) {
                                    log(e);
                                }
                            }
                        });
                        count++;
                    }
                }
                log("FFK: hooked " + count + " check method(s) in " + className);
            } catch (Throwable e) {
                log("FFK: cannot hook " + className);
                log(e);
            }
        }
    }

    private static void logChunked(String text) {
        for (int i = 0; i < text.length(); i += 900) {
            log(text.substring(i, Math.min(text.length(), i + 900)));
        }
    }

    private static void dumpGraph(Object o,
                                  int depth,
                                  String prefix,
                                  StringBuilder out,
                                  java.util.Map<Object, Boolean> visited) throws IllegalAccessException {
        if (o == null || depth > 3 || visited.put(o, true) != null) {
            return;
        }
        out.append(prefix).append('<').append(o.getClass().getName()).append("> ");
        for (Class<?> c = o.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (java.lang.reflect.Field field : c.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) {
                    continue;
                }
                field.setAccessible(true);
                Object value = field.get(o);
                if (value != null && value.getClass().getName().startsWith("com.android.server.pm")) {
                    out.append(field.getName()).append('=').append(value.getClass().getSimpleName()).append(' ');
                    dumpGraph(value, depth + 1, "[" + field.getName() + "] ", out, visited);
                }
            }
        }
    }

    // Walk object fields until the object that owns the permission registry (the real implementation) is found
    private static Object resolveImpl(Object o, int depth, java.util.Map<Object, Boolean> visited)
            throws IllegalAccessException {
        if (o == null || depth > 5 || visited.put(o, true) != null) {
            return null;
        }
        for (Class<?> c = o.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (java.lang.reflect.Field field : c.getDeclaredFields()) {
                if (field.getName().equals("mRegistry")) {
                    return o;
                }
            }
        }
        for (Class<?> c = o.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (java.lang.reflect.Field field : c.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) {
                    continue;
                }
                field.setAccessible(true);
                Object value = field.get(o);
                if (value != null && value.getClass().getName().startsWith("com.android.server.pm")) {
                    Object found = resolveImpl(value, depth + 1, visited);
                    if (found != null) {
                        return found;
                    }
                }
            }
        }
        return null;
    }
}
