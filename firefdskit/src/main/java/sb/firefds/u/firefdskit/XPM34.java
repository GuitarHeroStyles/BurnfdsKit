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

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;

/**
 * Grants the system permissions Firefds Kit needs.
 * <p>
 * Android 16 uses the new access-checking permission service
 * (com.android.server.permission.access), so the signature-permission decision is overridden there.
 * The legacy PermissionManagerServiceImpl hook is kept for ROMs still running the old service.
 * The permissions must also be declared with uses-permission in the manifest, otherwise the new
 * service never evaluates them for the package.
 */
public class XPM34 {
    private static final String ACCESS_PERMISSION = "com.android.server.permission.access.permission";
    private static final String APP_ID_PERMISSION_POLICY = ACCESS_PERMISSION + ".AppIdPermissionPolicy";
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

    private static final String PERMISSION_SERVICE = ACCESS_PERMISSION + ".PermissionService";
    private static final int PERMISSION_GRANTED = 0;

    private static final Set<String> LOGGED = new HashSet<>();
    private static volatile int firefdsAppId = -1;
    private static int diagnosticLogs;
    private static int failureLogs;

    public static void doHook(ClassLoader classLoader) {
        try {
            hookAccessPolicy(classLoader);
        } catch (Throwable e) {
            log("FFK: cannot hook the new permission service");
            log(e);
        }
        try {
            hookPermissionService(classLoader);
        } catch (Throwable e) {
            log("FFK: cannot hook PermissionService");
            log(e);
        }
        try {
            hookLegacyService(classLoader);
        } catch (Throwable e) {
            log("FFK: legacy permission service hook skipped: " + e);
        }
    }

    private static void hookAccessPolicy(ClassLoader classLoader) {
        final Class<?> policy = findClass(APP_ID_PERMISSION_POLICY, classLoader);
        int hooked = 0;
        for (Method method : policy.getDeclaredMethods()) {
            // Kotlin extension functions: (MutateStateScope, PackageState, Permission) -> Boolean
            if (method.getName().equals("shouldGrantPermissionBySignature") &&
                method.getParameterTypes().length == 3) {
                hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            final String packageName = (String) callMethod(param.args[1], "getPackageName");
                            if (!FIREFDSKIT.equals(packageName)) {
                                return;
                            }
                            final Object permissionInfo = getObjectField(param.args[2], "permissionInfo");
                            final String permission = (String) getObjectField(permissionInfo, "name");
                            if (GRANTED_PERMISSIONS.contains(permission)) {
                                if (LOGGED.add(permission)) {
                                    log("FFK: granting " + permission + " to " + packageName);
                                }
                                param.setResult(Boolean.TRUE);
                            }
                        } catch (Throwable e) {
                            log(e);
                        }
                    }
                });
                hooked++;
            }
        }
        log("FFK: hooked " + hooked + " shouldGrantPermissionBySignature method(s) in " + APP_ID_PERMISSION_POLICY);
    }

    private static void hookPermissionService(ClassLoader classLoader) {
        final Class<?> service = findClass(PERMISSION_SERVICE, classLoader);
        int hooked = 0;
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
                        log(e);
                    }
                }
            });
            hooked++;
        }
        log("FFK: hooked " + hooked + " check method(s) in " + PERMISSION_SERVICE);
    }

    // checkUidPermission(int uid, String permission, ...) or checkPermission(String pkg, String permission, ...)
    private static boolean shouldGrant(Object[] args, ClassLoader classLoader) {
        if (args.length < 2 || !(args[1] instanceof String) || !GRANTED_PERMISSIONS.contains(args[1])) {
            return false;
        }
        final String permission = (String) args[1];
        if (diagnosticLogs < 6) {
            diagnosticLogs++;
            log("FFK: check " + permission + " arg0=" + args[0] + " (" + args[0].getClass().getSimpleName() + ")");
        }
        boolean isFirefds;
        if (args[0] instanceof String) {
            isFirefds = FIREFDSKIT.equals(args[0]);
        } else if (args[0] instanceof Integer) {
            isFirefds = isFirefdsUid((Integer) args[0], classLoader);
        } else {
            return false;
        }
        if (isFirefds && LOGGED.add(permission)) {
            log("FFK: granting " + permission + " to " + FIREFDSKIT);
        }
        return isFirefds;
    }

    private static boolean isFirefdsUid(int uid, ClassLoader classLoader) {
        if (firefdsAppId < 0) {
            Object pmi = null;
            try {
                final Class<?> localServices = findClass("com.android.server.LocalServices", classLoader);
                final Class<?> pmInternal = findClass("android.content.pm.PackageManagerInternal", classLoader);
                pmi = callStaticMethod(localServices, "getService", pmInternal);
            } catch (Throwable e) {
                logFailure("cannot get PackageManagerInternal", e);
                return false;
            }
            if (pmi == null) {
                return false;
            }
            int packageUid = -1;
            try {
                packageUid = (int) callMethod(pmi, "getPackageUid", FIREFDSKIT, 0L, 0);
            } catch (Throwable e) {
                logFailure("getPackageUid failed", e);
                try {
                    final Object pkg = callMethod(pmi, "getPackage", FIREFDSKIT);
                    if (pkg != null) {
                        packageUid = (int) callMethod(pkg, "getUid");
                    }
                } catch (Throwable e2) {
                    logFailure("getPackage failed", e2);
                }
            }
            if (packageUid < 0) {
                return false;
            }
            firefdsAppId = packageUid % 100000;
            log("FFK: Firefds Kit appId=" + firefdsAppId);
        }
        return uid % 100000 == firefdsAppId;
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
                        log(e);
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
            Object bp = callMethod(registry, "getPermission", permission);
            Object uidState = callMethod(permissionManager, "getUidStateLocked", pkg, 0);
            callMethod(uidState, "grantPermission", bp);
        }
    }
}
