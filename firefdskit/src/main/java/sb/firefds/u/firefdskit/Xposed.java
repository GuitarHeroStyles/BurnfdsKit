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

import static sb.firefds.u.firefdskit.utils.Packages.FIREFDSKIT;
import static sb.firefds.u.firefdskit.xposed.XposedHelpers.findAndHookMethod;

import android.content.SharedPreferences;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

import java.util.HashSet;
import java.util.Set;

import io.github.libxposed.api.XposedModule;
import sb.firefds.u.firefdskit.utils.Packages;
import sb.firefds.u.firefdskit.utils.Utils;
import sb.firefds.u.firefdskit.xposed.XC_MethodReplacement;
import sb.firefds.u.firefdskit.xposed.XposedBridge;

/**
 * Module entry point (modern libxposed API). Registered in META-INF/xposed/java_init.list.
 */
@Keep
public class Xposed extends XposedModule {

    /** Remote preference group the module app mirrors its settings into (see RemotePreferencesSync). */
    public static final String PREF_GROUP = "conf";

    private static volatile SharedPreferences prefs;
    private static final Set<String> HOOKED_PACKAGES = new HashSet<>();
    private static boolean systemWideHooked;

    @Override
    public void onModuleLoaded(@NonNull ModuleLoadedParam param) {
        XposedBridge.init(this);
        try {
            prefs = getRemotePreferences(PREF_GROUP);
            XposedBridge.log("FFK: Firefds Kit remote preferences ready in " + param.getProcessName());
        } catch (Throwable e) {
            XposedBridge.log("FFK: cannot open remote preferences");
            XposedBridge.log(e);
        }
    }

    @Override
    public void onSystemServerStarting(@NonNull SystemServerStartingParam param) {
        if (!isSupported()) {
            return;
        }
        final ClassLoader classLoader = param.getClassLoader();
        hookSystemWide();

        try {
            XPM34.doHook(classLoader);
        } catch (Throwable e) {
            XposedBridge.log(e);
        }

        try {
            XAndroidPackage.doHook(classLoader);
        } catch (Throwable e) {
            XposedBridge.log(e);
        }
    }

    @Override
    public void onPackageReady(@NonNull PackageReadyParam param) {
        final String packageName = param.getPackageName();
        final ClassLoader classLoader = param.getClassLoader();

        synchronized (HOOKED_PACKAGES) {
            if (!HOOKED_PACKAGES.add(packageName)) {
                return;
            }
        }

        if (packageName.equals(FIREFDSKIT)) {
            try {
                findAndHookMethod(FIREFDSKIT + ".XposedChecker",
                                  classLoader,
                                  "isActive",
                                  XC_MethodReplacement.returnConstant(Boolean.TRUE));
            } catch (Throwable e) {
                XposedBridge.log(e);
            }
        }

        if (!isSupported()) {
            return;
        }

        hookSystemWide();

        if (packageName.equals(Packages.NFC)) {
            safeHook("NFC", () -> XNfcPackage.doHook(classLoader));
        } else if (packageName.equals(Packages.SYSTEM_UI)) {
            safeHook("SystemUI", () -> XSysUIPackage.doHook(prefs, classLoader));
        } else if (packageName.equals(Packages.SETTINGS)) {
            safeHook("Settings", () -> XSecSettingsPackage.doHook(classLoader));
        } else if (packageName.equals(Packages.EMAIL)) {
            safeHook("Email", () -> XSecEmailPackage.doHook(classLoader));
        } else if (packageName.equals(Packages.CAMERA)) {
            safeHook("Camera", () -> XSecCameraPackage.doHook(classLoader));
        } else if (packageName.equals(Packages.MTP_APPLICATION)) {
            safeHook("MTP", () -> XMtpApplication.doHook(classLoader));
        } else if (packageName.equals(Packages.FOTA_AGENT)) {
            safeHook("FOTA", () -> XFotaAgentPackage.doHook(classLoader));
        } else if (packageName.equals(Packages.SAMSUNG_MESSAGING)) {
            safeHook("Messaging", () -> XMessagingPackage.doHook(classLoader));
        } else if (packageName.equals(Packages.SAMSUNG_CONTACTS)) {
            safeHook("Contacts", () -> XContactsPackage.doHook(classLoader));
        } else if (packageName.equals(Packages.SMART_CAPTURE)) {
            safeHook("SmartCapture", () -> XSmartCapturePackage.doHook(classLoader));
        }
    }

    private static boolean isSupported() {
        if (Utils.isNotSamsungRom()) {
            XposedBridge.log("FFK: com.samsung.device.jar or com.samsung.device.lite.jar not found!");
            return false;
        }
        if (prefs == null) {
            XposedBridge.log("FFK: Xposed cannot read Firefds Kit preferences!");
            return false;
        }
        return true;
    }

    private static synchronized void hookSystemWide() {
        if (systemWideHooked) {
            return;
        }
        systemWideHooked = true;
        safeHook("system wide", XSystemWide::doHook);
    }

    private interface HookAction {
        void run() throws Throwable;
    }

    private static void safeHook(String name, HookAction action) {
        try {
            action.run();
        } catch (Throwable e) {
            XposedBridge.log("FFK: hooks for " + name + " failed");
            XposedBridge.log(e);
        }
    }

    public static Boolean reloadAndGetBooleanPref(String prefName, boolean defValue) {
        final SharedPreferences preferences = prefs;
        return preferences != null && preferences.getBoolean(prefName, defValue) ? Boolean.TRUE : Boolean.FALSE;
    }

    public static int reloadAndGetIntPref(String prefName, int defValue) {
        final SharedPreferences preferences = prefs;
        return preferences != null ? preferences.getInt(prefName, defValue) : defValue;
    }

    public static String reloadAndGetStringPref(String prefName, String defValue) {
        final SharedPreferences preferences = prefs;
        return preferences != null ? preferences.getString(prefName, defValue) : defValue;
    }
}
