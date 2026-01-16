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

package android.app.notification.current.cts;

import static org.junit.Assert.assertEquals;

import android.content.res.Resources;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;
import com.android.server.notification.Flags;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class NotificationMaxSizeConfigTest {

    // 5MB is standard configuration across devices now and we
    // want to make sure all developers can rely on consistent behaviour as
    // documented in the API.
    private static final int NOTIFICATION_STRIP_SIZE_BYTES = 5000000;

    @Rule public CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    @ApiTest(
            apis = {
                "android.app.Notification.Builder#setCustomBigView",
                "android.app.Notification.Builder#setCustomContentView",
                "android.app.Notification.Builder#setCustomHeadsUpContentView"
            })
    @RequiresFlagsEnabled(Flags.FLAG_NOTIFICATION_CUSTOM_VIEW_URI_RESTRICTION)
    public void config_notificationStripRemoteViewSizeBytes_is5MB() {
        Resources systemRes = Resources.getSystem();
        int resId =
                systemRes.getIdentifier(
                        "config_notificationStripRemoteViewSizeBytes", "integer", "android");
        if (resId != 0) {
            assertEquals(
                    "config_notificationStripRemoteViewSizeBytes should be set to"
                        + " ${NOTIFICATION_STRIP_SIZE_BYTES} for consistency across the ecosystem",
                    NOTIFICATION_STRIP_SIZE_BYTES,
                    systemRes.getInteger(resId));
        } else {
            throw new AssertionError(
                    "Failed to get resource id for config_notificationStripRemoteViewSizeBytes.");
        }
    }
}
