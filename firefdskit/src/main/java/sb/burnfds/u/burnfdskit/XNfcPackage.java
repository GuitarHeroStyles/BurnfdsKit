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

import android.app.StatusBarManager;

import static sb.burnfds.u.burnfdskit.xposed.XposedBridge.log;
import static sb.burnfds.u.burnfdskit.xposed.XposedHelpers.callMethod;
import static sb.burnfds.u.burnfdskit.xposed.XposedHelpers.findAndHookMethod;
import static sb.burnfds.u.burnfdskit.xposed.XposedHelpers.getAdditionalInstanceField;
import static sb.burnfds.u.burnfdskit.xposed.XposedHelpers.getIntField;
import static sb.burnfds.u.burnfdskit.xposed.XposedHelpers.getObjectField;
import static sb.burnfds.u.burnfdskit.xposed.XposedHelpers.setAdditionalInstanceField;
import static sb.burnfds.u.burnfdskit.xposed.XposedHelpers.setIntField;
import static sb.burnfds.u.burnfdskit.Xposed.reloadAndGetStringPref;
import static sb.burnfds.u.burnfdskit.utils.Packages.NFC;
import static sb.burnfds.u.burnfdskit.utils.Preferences.PREF_NFC_BEHAVIOR;

import sb.burnfds.u.burnfdskit.xposed.XC_MethodHook;
import sb.burnfds.u.burnfdskit.xposed.XC_MethodReplacement;
import sb.burnfds.u.burnfdskit.xposed.XposedHelpers;

@SuppressWarnings("SynchronizeOnNonFinalField")
public class XNfcPackage {

    private static final int SCREEN_STATE_ON_LOCKED = 4;
    private static final int SCREEN_STATE_ON_UNLOCKED = 8;
    private static String behavior;

    private static final String NFC_SERVICE = NFC + ".NfcService";
    private static final String NFC_ICON = "com.samsung.android.nfc.ui.NfcIcon";

    public static void doHook(ClassLoader classLoader) {

        try {
            findAndHookMethod(NFC_ICON, classLoader, "showIcon", new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) {
                    Object mStatusBarManager = getObjectField(param.thisObject, "mStatusBarManager");
                    callMethod(mStatusBarManager, "removeIcon", "nfc");
                    return null;
                }
            });
        } catch (XposedHelpers.ClassNotFoundError | NoSuchMethodError ignored) {
            // NfcIcon no longer exists in One UI 8, the StatusBarManager hook below takes over
        } catch (Throwable e) {
            log(e);
        }

        try {
            // The NFC service asks the status bar for its icon through StatusBarManager, whatever class does it
            findAndHookMethod(StatusBarManager.class,
                              "setIcon",
                              String.class,
                              int.class,
                              int.class,
                              String.class,
                              new XC_MethodHook() {
                                  @Override
                                  protected void beforeHookedMethod(MethodHookParam param) {
                                      if ("nfc".equals(param.args[0])) {
                                          param.setResult(null);
                                      }
                                  }
                              });
        } catch (Throwable e) {
            log(e);
        }

        try {
            findAndHookMethod(NFC_SERVICE, classLoader, "applyRouting", boolean.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    behavior = reloadAndGetStringPref(PREF_NFC_BEHAVIOR, "0");
                    if (behavior.equals("0")) {
                        return;
                    }
                    try {
                        final int currScreenState;
                        final Object mScreenStateHelper = getObjectField(param.thisObject, "mScreenStateHelper");
                        if (mScreenStateHelper != null) {
                            currScreenState = (Integer) callMethod(mScreenStateHelper, "checkScreenState");
                        } else {
                            currScreenState = (Integer) callMethod(param.thisObject, "checkScreenState");
                        }
                        if ((currScreenState == SCREEN_STATE_ON_UNLOCKED) ||
                            (behavior.equals("1") && currScreenState != SCREEN_STATE_ON_LOCKED)) {
                            setAdditionalInstanceField(param.thisObject, "mOrigScreenState", -1);
                            return;
                        }

                        synchronized (param.thisObject) {
                            setAdditionalInstanceField(param.thisObject,
                                                       "mOrigScreenState",
                                                       getIntField(param.thisObject, "mScreenState"));
                            setIntField(param.thisObject, "mScreenState", SCREEN_STATE_ON_UNLOCKED);
                        }
                    } catch (Throwable e) {
                        log(e);
                    }
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (behavior == null || behavior.equals("0")) {
                        return;
                    }

                    final Integer mOrigScreenState = (Integer) getAdditionalInstanceField(param.thisObject,
                                                                                          "mOrigScreenState");
                    if (mOrigScreenState == null || mOrigScreenState == -1) return;

                    try {
                        synchronized (param.thisObject) {
                            setIntField(param.thisObject, "mScreenState", mOrigScreenState);
                        }
                    } catch (Throwable e) {
                        log(e);
                    }
                }
            });
        } catch (Throwable e) {
            log(e);
        }
    }
}
