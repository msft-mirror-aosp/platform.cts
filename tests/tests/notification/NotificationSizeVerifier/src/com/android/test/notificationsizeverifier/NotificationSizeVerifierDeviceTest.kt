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

package com.android.test.notificationsizeverifier

import android.content.Context
import android.content.pm.PackageManager
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.interactive.Step
import com.android.interactive.annotations.Interactive
import com.android.interactive.annotations.NotFullyAutomated
import com.android.interactive.steps.sysui.VerifyNotificationCustomContentRendered
import com.android.interactive.steps.sysui.VerifyNotificationCustomContentStripped
import com.android.server.notification.Flags
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@RequiresFlagsEnabled(Flags.FLAG_NOTIFICATION_CUSTOM_VIEW_URI_RESTRICTION)
class NotificationSizeVerifierDeviceTest {
    private lateinit var context: Context
    private lateinit var poster: NotificationPoster

    companion object {
        private const val TAG = "NotificationSizeDeviceTest"
        private const val NOTIFICATION_ID_BITMAP_OVER = 12001
        private const val NOTIFICATION_ID_BITMAP_UNDER = 12002
        private const val NOTIFICATION_ID_URI_OVER = 12003
        private const val NOTIFICATION_ID_URI_UNDER = 12004
        private const val NOTIFICATION_STRIP_SIZE_BYTES = 5000000

        private const val LOG_COMPAT_CHANGE = "android.permission.LOG_COMPAT_CHANGE"
        private const val READ_COMPAT_CHANGE_CONFIG = "android.permission.READ_COMPAT_CHANGE_CONFIG"
    }

    @get:Rule
    val checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @Before
    fun setUp() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
            .adoptShellPermissionIdentity(LOG_COMPAT_CHANGE, READ_COMPAT_CHANGE_CONFIG)

        context = InstrumentationRegistry.getInstrumentation().targetContext
        poster = NotificationPoster(context, NOTIFICATION_STRIP_SIZE_BYTES)
        poster.createNotificationChannel()
        Assume.assumeTrue(platformSupportsNotificationStyles())
    }

    @After
    fun tearDown() {
        if (this::poster.isInitialized) {
            poster.cancelAllNotifications()
        }
        InstrumentationRegistry.getInstrumentation().getUiAutomation().dropShellPermissionIdentity()
    }

    private fun platformSupportsNotificationStyles(): Boolean =
        !(hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE) ||
            hasSystemFeature(PackageManager.FEATURE_LEANBACK))

    private fun hasSystemFeature(feature: String) =
        InstrumentationRegistry.getInstrumentation()
            .targetContext.packageManager.hasSystemFeature(feature)

    @Test
    @Interactive
    @NotFullyAutomated(reason = "Manual notification UI verification required")
    fun bitmapOverLimit_ChangeEnabled() {
        poster.postBitmapNotification(NOTIFICATION_ID_BITMAP_OVER, true, true)
        val result = Step.execute(VerifyNotificationCustomContentStripped::class.java)
        assertTrue("Tester indicated failure for Bitmap Over Limit (Change Enabled)", result)
    }

    @Test
    @Interactive
    @NotFullyAutomated(reason = "Manual notification UI verification required")
    fun bitmapUnderLimit_ChangeEnabled() {
        poster.postBitmapNotification(NOTIFICATION_ID_BITMAP_UNDER, false, false)
        val result = Step.execute(VerifyNotificationCustomContentRendered::class.java)
        assertTrue("Tester indicated failure for Bitmap Under Limit (Change Enabled)", result)
    }

    @Test
    @Interactive
    @NotFullyAutomated(reason = "Manual notification UI verification required")
    fun uriOverLimit_ChangeEnabled() {
        poster.postUriNotification(NOTIFICATION_ID_URI_OVER, true, true)
        val result = Step.execute(VerifyNotificationCustomContentStripped::class.java)
        assertTrue("Tester indicated failure for URI Over Limit (Change Enabled)", result)
    }

    @Test
    @Interactive
    @NotFullyAutomated(reason = "Manual notification UI verification required")
    fun uriUnderLimit_ChangeEnabled() {
        poster.postUriNotification(NOTIFICATION_ID_URI_UNDER, false, false)
        val result = Step.execute(VerifyNotificationCustomContentRendered::class.java)
        assertTrue("Tester indicated failure for URI Under Limit (Change Enabled)", result)
    }

    @Test
    @Interactive
    @NotFullyAutomated(reason = "Manual notification UI verification required")
    fun bitmapOverLimit_ChangeDisabled() {
        poster.postBitmapNotification(NOTIFICATION_ID_BITMAP_OVER, true, true)
        val result = Step.execute(VerifyNotificationCustomContentStripped::class.java)
        assertTrue("Tester indicated failure for Bitmap Over Limit (Change Disabled)", result)
    }

    @Test
    @Interactive
    @NotFullyAutomated(reason = "Manual notification UI verification required")
    fun bitmapUnderLimit_ChangeDisabled() {
        poster.postBitmapNotification(NOTIFICATION_ID_BITMAP_UNDER, false, false)
        val result = Step.execute(VerifyNotificationCustomContentRendered::class.java)
        assertTrue("Tester indicated failure for Bitmap Under Limit (Change Disabled)", result)
    }

    @Test
    @Interactive
    @NotFullyAutomated(reason = "Manual notification UI verification required")
    fun uriOverLimit_ChangeDisabled() {
        poster.postUriNotification(NOTIFICATION_ID_URI_OVER, true, false)
        val result = Step.execute(VerifyNotificationCustomContentRendered::class.java)
        assertTrue("Tester indicated failure for URI Over Limit (Change Disabled)", result)
    }

    @Test
    @Interactive
    @NotFullyAutomated(reason = "Manual notification UI verification required")
    fun uriUnderLimit_ChangeDisabled() {
        poster.postUriNotification(NOTIFICATION_ID_URI_UNDER, false, false)
        val result = Step.execute(VerifyNotificationCustomContentRendered::class.java)
        assertTrue("Tester indicated failure for URI Under Limit (Change Disabled)", result)
    }
}
