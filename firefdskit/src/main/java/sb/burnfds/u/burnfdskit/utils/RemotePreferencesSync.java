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
package sb.burnfds.u.burnfdskit.utils;

import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.Map;
import java.util.Set;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;
import sb.burnfds.u.burnfdskit.Xposed;

/**
 * Mirrors the module's local settings into the framework's remote preferences, which is what the hooked
 * processes read with the modern Xposed API.
 */
public final class RemotePreferencesSync {
    private static final String TAG = "FFK";

    private static SharedPreferences local;
    private static volatile XposedService service;
    private static boolean started;

    // Held statically because SharedPreferences keeps its listeners weakly
    private static final SharedPreferences.OnSharedPreferenceChangeListener LISTENER =
            (preferences, key) -> push(preferences, key);

    private RemotePreferencesSync() {
    }

    public static synchronized void start(@NonNull SharedPreferences localPreferences) {
        local = localPreferences;
        if (started) {
            return;
        }
        started = true;
        localPreferences.registerOnSharedPreferenceChangeListener(LISTENER);
        XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener() {
            @Override
            public void onServiceBind(@NonNull XposedService xposedService) {
                service = xposedService;
                Log.i(TAG, "Xposed service bound: " + xposedService.getFrameworkName() + " " +
                           xposedService.getFrameworkVersion() + " API " + xposedService.getApiVersion());
                pushAll();
            }

            @Override
            public void onServiceDied(@NonNull XposedService xposedService) {
                service = null;
            }
        });
    }

    public static boolean isConnected() {
        return service != null;
    }

    private static synchronized void pushAll() {
        final XposedService xposedService = service;
        final SharedPreferences source = local;
        if (xposedService == null || source == null) {
            return;
        }
        try {
            final SharedPreferences.Editor editor = xposedService.getRemotePreferences(Xposed.PREF_GROUP).edit();
            editor.clear();
            for (Map.Entry<String, ?> entry : source.getAll().entrySet()) {
                put(editor, entry.getKey(), entry.getValue());
            }
            editor.apply();
        } catch (Throwable e) {
            Log.e(TAG, "cannot mirror preferences", e);
        }
    }

    private static synchronized void push(SharedPreferences source, String key) {
        final XposedService xposedService = service;
        if (xposedService == null) {
            return;
        }
        if (key == null) {
            pushAll();
            return;
        }
        try {
            final SharedPreferences.Editor editor = xposedService.getRemotePreferences(Xposed.PREF_GROUP).edit();
            if (source.contains(key)) {
                put(editor, key, source.getAll().get(key));
            } else {
                editor.remove(key);
            }
            editor.apply();
        } catch (Throwable e) {
            Log.e(TAG, "cannot mirror preference " + key, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static void put(SharedPreferences.Editor editor, String key, Object value) {
        if (value instanceof Boolean) {
            editor.putBoolean(key, (Boolean) value);
        } else if (value instanceof Integer) {
            editor.putInt(key, (Integer) value);
        } else if (value instanceof Long) {
            editor.putLong(key, (Long) value);
        } else if (value instanceof Float) {
            editor.putFloat(key, (Float) value);
        } else if (value instanceof String) {
            editor.putString(key, (String) value);
        } else if (value instanceof Set) {
            editor.putStringSet(key, (Set<String>) value);
        }
    }
}
