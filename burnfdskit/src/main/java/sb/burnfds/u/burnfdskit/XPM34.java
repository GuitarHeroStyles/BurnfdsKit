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
package sb.burnfds.u.burnfdskit;

import static sb.burnfds.u.burnfdskit.xposed.XposedBridge.hookMethod;
import static sb.burnfds.u.burnfdskit.xposed.XposedBridge.log;
import static sb.burnfds.u.burnfdskit.xposed.XposedHelpers.callMethod;
import static sb.burnfds.u.burnfdskit.xposed.XposedHelpers.callStaticMethod;
import static sb.burnfds.u.burnfdskit.xposed.XposedHelpers.findClass;
import static sb.burnfds.u.burnfdskit.xposed.XposedHelpers.getObjectField;
import static sb.burnfds.u.burnfdskit.utils.Packages.FIREFDSKIT;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import sb.burnfds.u.burnfdskit.xposed.XC_MethodHook;

/**
 * Grants the system permissions Firefds Kit needs.
 * <p>
 * Android 16 uses the access-checking permission service (com.android.server.permission.access), whose
 * PermissionService answers every permission check, so Firefds Kit is granted its permissions there.
 * The legacy PermissionManagerServiceImpl hook is kept for ROMs that still run the old service.
 * The permissions must also be declared with uses-permission in the manifest.
 */
public class XPM34 {
    private static final String PERMISSION_SERVICE = "com.android.server.permission.access.permission" +
                                                     ".PermissionService";
    private static final String LEGACY_PERMISSION_MANAGER_SERVICE = "com.android.server.pm.permission" +
                                                                    ".PermissionManagerServiceImpl";

    private static final String REBOOT = "android.permission.REBOOT";
    private static final String WRITE_SETTINGS = "android.permission.WRITE_SETTINGS";
    private static final String STATUSBAR = "android.permission.EXPAND_STATUS_BAR";
    private static final String RECOVERY = "android.permission.RECOVERY";
    private static final String ACCESS_SCREEN_RECORDER_SVC = "com.samsung.android.app.screenrecorder.permission" +
                                                             ".ACCESS_SCREEN_RECORDER_SVC";

    private static final Set<String> GRANTED_PERMISSIONS = new HashSet<>(Arrays.asList(REBOOT,
                                                                                       WRITE_SETTINGS,
                                                                                       STATUSBAR,
                                                                                       RECOVERY,
                                                                                       ACCESS_SCREEN_RECORDER_SVC));
    private static final int PERMISSION_GRANTED = 0;
    private static final int PER_USER_RANGE = 100000;

    private static volatile int firefdsAppId = -1;
    private static boolean grantLogged;
    private static int failureLogs;

    public static void doHook(ClassLoader classLoader) {
        try {
            hookPermissionService(classLoader);
        } catch (Throwable e) {
            log("FFK: cannot hook " + PERMISSION_SERVICE);
            log(e);
        }
        try {
            hookLegacyService(classLoader);
        } catch (Throwable e) {
            log("FFK: legacy permission service hook skipped: " + e);
        }
    }

    private static void hookPermissionService(ClassLoader classLoader) {
        final Class<?> service = findClass(PERMISSION_SERVICE, classLoader);
        for (Method method : service.getDeclaredMethods()) {
            final String name = method.getName();
            if (!name.equals("checkUidPermission") && !name.equals("checkPermission")) {
                continue;
            }
            hookMethod(method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        if (shouldGrant(param.args, classLoader)) {
                            param.setResult(PERMISSION_GRANTED);
                        }
                    } catch (Throwable e) {
                        logFailure("permission check failed", e);
                    }
                }
            });
        }
    }

    // checkUidPermission(int uid, String permission, ...) or checkPermission(String pkg, String permission, ...)
    private static boolean shouldGrant(Object[] args, ClassLoader classLoader) {
        if (args.length < 2 || !(args[1] instanceof String) || !GRANTED_PERMISSIONS.contains(args[1])) {
            return false;
        }
        final boolean isFirefds;
        if (args[0] instanceof String) {
            isFirefds = FIREFDSKIT.equals(args[0]);
        } else if (args[0] instanceof Integer) {
            isFirefds = isFirefdsUid((Integer) args[0], classLoader);
        } else {
            return false;
        }
        if (isFirefds && !grantLogged) {
            grantLogged = true;
            log("FFK: granting system permissions to " + FIREFDSKIT);
        }
        return isFirefds;
    }

    private static boolean isFirefdsUid(int uid, ClassLoader classLoader) {
        if (firefdsAppId < 0) {
            try {
                final Class<?> localServices = findClass("com.android.server.LocalServices", classLoader);
                final Class<?> pmInternal = findClass("android.content.pm.PackageManagerInternal", classLoader);
                final Object pmi = callStaticMethod(localServices, "getService", pmInternal);
                if (pmi == null) {
                    return false;
                }
                final int packageUid = (int) callMethod(pmi, "getPackageUid", FIREFDSKIT, 0L, 0);
                if (packageUid < 0) {
                    return false;
                }
                firefdsAppId = packageUid % PER_USER_RANGE;
            } catch (Throwable e) {
                logFailure("cannot resolve the Firefds Kit uid", e);
                return false;
            }
        }
        return uid % PER_USER_RANGE == firefdsAppId;
    }

    private static void logFailure(String what, Throwable e) {
        if (failureLogs++ < 4) {
            log("FFK: " + what + ": " + e);
        }
    }

    private static void hookLegacyService(ClassLoader classLoader) {
        final Class<?> legacy = findClass(LEGACY_PERMISSION_MANAGER_SERVICE, classLoader);
        for (Method method : legacy.getDeclaredMethods()) {
            if (!method.getName().equals("restorePermissionState") || method.getParameterTypes().length == 0) {
                continue;
            }
            hookMethod(method, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        grantLegacy(param.thisObject, param.args[0]);
                    } catch (Throwable e) {
                        logFailure("legacy grant failed", e);
                    }
                }
            });
        }
    }

    private static void grantLegacy(Object permissionManager, Object pkg) {
        if (pkg == null || !FIREFDSKIT.equals(callMethod(pkg, "getPackageName"))) {
            return;
        }
        final Object registry = getObjectField(permissionManager, "mRegistry");
        for (String permission : GRANTED_PERMISSIONS) {
            final Object bp = callMethod(registry, "getPermission", permission);
            final Object uidState = callMethod(permissionManager, "getUidStateLocked", pkg, 0);
            callMethod(uidState, "grantPermission", bp);
        }
    }
}
