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

import static sb.burnfds.u.burnfdskit.xposed.XposedBridge.log;
import static sb.burnfds.u.burnfdskit.xposed.XposedHelpers.findAndHookMethod;
import static sb.burnfds.u.burnfdskit.xposed.XposedHelpers.findClass;
import static sb.burnfds.u.burnfdskit.Xposed.reloadAndGetBooleanPref;
import static sb.burnfds.u.burnfdskit.utils.Preferences.PREF_ENABLE_SCREEN_RECORDER_IN_CALL;

import android.content.Context;

import sb.burnfds.u.burnfdskit.xposed.XC_MethodHook;
import sb.burnfds.u.burnfdskit.xposed.XC_MethodReplacement;

public class XSmartCapturePackage {

    private static final String RECORDING_STOP_REASON = "com.samsung.android.app.screenrecorder" +
                                                        ".ScreenRecorderController.RecordingStopReason";
    private static final String SCREEN_RECORDER_CONTROLLER = "com.samsung.android.app.screenrecorder" +
                                                             ".ScreenRecorderController";
    private static final String SCREEN_RECORDER_CONTROLLER$1 = "com.samsung.android.app.screenrecorder" +
                                                               ".ScreenRecorderController$1";
    private static final String SCREEN_RECORDER_UTILS = "com.samsung.android.app.screenrecorder.util" +
                                                        ".ScreenRecorderUtils";

    public static void doHook(ClassLoader classLoader) {

        try {
            findAndHookMethod(SCREEN_RECORDER_CONTROLLER$1,
                              classLoader,
                              "onCallStateChanged",
                              int.class,
                              new XC_MethodHook() {
                                  @Override
                                  protected void beforeHookedMethod(MethodHookParam param) {
                                      if (reloadAndGetBooleanPref(PREF_ENABLE_SCREEN_RECORDER_IN_CALL, false)) {
                                          param.setResult(null);
                                      }
                                  }
                              });

        } catch (Throwable e) {
            log(e);
        }

        try {
            Class<?> recordingStopReason = findClass(RECORDING_STOP_REASON, classLoader);
            findAndHookMethod(SCREEN_RECORDER_CONTROLLER,
                              classLoader,
                              "stopRecordingAccordingToAction",
                              recordingStopReason,
                              new XC_MethodHook() {
                                  @Override
                                  protected void beforeHookedMethod(MethodHookParam param) {
                                      if (reloadAndGetBooleanPref(PREF_ENABLE_SCREEN_RECORDER_IN_CALL, false)) {
                                          if (((Enum<?>) param.args[0]).name().equalsIgnoreCase("INCOMING_CALL")) {
                                              param.setResult(null);
                                          }
                                      }
                                  }
                              });
        } catch (Throwable e) {
            log(e);
        }

        try {
            findAndHookMethod(SCREEN_RECORDER_UTILS,
                              classLoader,
                              "isDuringCsCallState",
                              Context.class,
                              XC_MethodReplacement.returnConstant(!reloadAndGetBooleanPref(
                                      PREF_ENABLE_SCREEN_RECORDER_IN_CALL,
                                      false)));

        } catch (Throwable e) {
            log(e);
        }

        try {
            findAndHookMethod(SCREEN_RECORDER_UTILS,
                              classLoader,
                              "isDuringPsCallState",
                              Context.class,
                              XC_MethodReplacement.returnConstant(!reloadAndGetBooleanPref(
                                      PREF_ENABLE_SCREEN_RECORDER_IN_CALL,
                                      false)));
        } catch (Throwable e) {
            log(e);
        }
    }
}
