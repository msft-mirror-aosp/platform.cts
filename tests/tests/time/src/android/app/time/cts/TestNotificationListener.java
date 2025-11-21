/*
 * Copyright (C) 2025 The Android Open Source Project
 *
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

package android.app.time.cts;

import android.content.ComponentName;
import android.content.Context;
import android.os.ConditionVariable;
import android.os.UserHandle;
import android.service.notification.NotificationListenerService;
import com.android.compatibility.common.util.SystemUtil;
import java.time.Duration;

/** Used in tests to clear all notifications after a test. */
public class TestNotificationListener extends NotificationListenerService {

    private static final String TAG = "TimeManagerTest";

    private static final Duration TIMEOUT_LONG = Duration.ofSeconds(30);

    private static final ConditionVariable connected = new ConditionVariable(false);
    private static final ConditionVariable disconnected = new ConditionVariable(true);
    private static volatile TestNotificationListener instance = null;

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        disconnected.close();
        instance = this;
        connected.open();
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        connected.close();
        instance = null;
        disconnected.open();
    }

    public static void clearAllNotifications() {
        TestNotificationListener currentInstance = getInstance();
        try {
            currentInstance.cancelAllNotifications();
        } catch (SecurityException e) {
            throw new AssertionError("SecurityException while clearing all notifications: ", e);
        }
    }

    public static TestNotificationListener getInstance() {
        boolean isConnected = connected.block(1);
        boolean isDisconnected = disconnected.block(1);
        if (isConnected == isDisconnected) {
            throw new IllegalStateException(
                    "Notification listener condition variables are inconsistent");
        }
        if (!isConnected) {
            throw new IllegalStateException("Notification listener was unexpectedly disconnected");
        }
        if (instance == null) {
            throw new IllegalStateException("Notification listener was unexpectedly null");
        }
        return instance;
    }

    /** Runs a shell command to allow or disallow the listener. Use before and after test. */
    private static void toggleListenerAccess(Context context, boolean allowed) {
        ComponentName componentName = new ComponentName(context, TestNotificationListener.class);
        String verb = allowed ? "allow" : "disallow";
        SystemUtil.runShellCommand(
                "cmd notification " + verb + "_listener " + componentName.flattenToString()
                        + " " + UserHandle.myUserId());

        if (allowed) {
            requestRebind(componentName);
            if (!connected.block(TIMEOUT_LONG.toMillis())) {
                throw new RuntimeException(
                        "Notification listener did not connect in " + TIMEOUT_LONG);
            }
        } else {
            if (!disconnected.block(TIMEOUT_LONG.toMillis())) {
                throw new RuntimeException(
                        "Notification listener did not disconnect in " + TIMEOUT_LONG);
            }
        }
    }

    /** Prepare the TestNotificationListener for a notification test */
    public static void setup(Context context) {
        toggleListenerAccess(context, true);
    }

    /** Clean up the TestNotificationListener after executing a notification test. */
    public static void teardown(Context context) {
        toggleListenerAccess(context, false);
    }
}
