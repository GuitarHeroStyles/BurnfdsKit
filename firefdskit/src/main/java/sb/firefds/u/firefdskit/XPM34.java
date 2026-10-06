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

    private static final Set<String> LOGGED = new HashSet<>();

    public static void doHook(ClassLoader classLoader) {
        try {
            hookAccessPolicy(classLoader);
        } catch (Throwable e) {
            log("FFK: cannot hook the new permission service");
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
