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
import android.content.res.Resources
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.interactive.Step
import com.android.interactive.annotations.Interactive
import com.android.interactive.annotations.NotFullyAutomated
import com.android.interactive.steps.sysui.VerifyNotificationBitmapOverChangeDisabled
import com.android.interactive.steps.sysui.VerifyNotificationBitmapOverChangeEnabled
import com.android.interactive.steps.sysui.VerifyNotificationBitmapUnderChangeDisabled
import com.android.interactive.steps.sysui.VerifyNotificationBitmapUnderChangeEnabled
import com.android.interactive.steps.sysui.VerifyNotificationUriOverChangeDisabled
import com.android.interactive.steps.sysui.VerifyNotificationUriOverChangeEnabled
import com.android.interactive.steps.sysui.VerifyNotificationUriUnderChangeDisabled
import com.android.interactive.steps.sysui.VerifyNotificationUriUnderChangeEnabled
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
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
    }

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        poster = NotificationPoster(context, NOTIFICATION_STRIP_SIZE_BYTES)
        poster.createNotificationChannel()
    }

    @After
    fun tearDown() {
        if (this::poster.isInitialized) {
            poster.cancelAllNotifications()
        }
    }

    @Test
    fun config_NotificationStripSizeBytes_VerifyValue() {
        try {
            val systemRes = Resources.getSystem()
            val resId =
                systemRes.getIdentifier(
                    "config_notificationStripRemoteViewSizeBytes",
                    "integer",
                    "android"
                )
            if (resId != 0) {
                assertEquals(
                    "config_notificationStripRemoteViewSizeBytes should be set to " +
                            "${NOTIFICATION_STRIP_SIZE_BYTES} for consistency across the ecosystem",
                    NOTIFICATION_STRIP_SIZE_BYTES,
                    systemRes.getInteger(resId)
                )
            } else {
                throw AssertionError(
                    "Failed to get resource id for config_notificationStripRemoteViewSizeBytes."
                )
            }
        } catch (e: Exception) {
            throw AssertionError("Error accessing system resource: ${e.message}")
        }
    }

    @Test
    @Interactive
    @NotFullyAutomated(reason = "Manual notification UI verification required")
    fun bitmapOverLimit_ChangeEnabled() {
        poster.postBitmapNotification(NOTIFICATION_ID_BITMAP_OVER, true)
        val result = Step.execute(VerifyNotificationBitmapOverChangeEnabled::class.java)
        assertTrue("Tester indicated failure for Bitmap Over Limit (Change Enabled)", result)
    }

    @Test
    @Interactive
    @NotFullyAutomated(reason = "Manual notification UI verification required")
    fun bitmapUnderLimit_ChangeEnabled() {
        poster.postBitmapNotification(NOTIFICATION_ID_BITMAP_UNDER, false)
        val result = Step.execute(VerifyNotificationBitmapUnderChangeEnabled::class.java)
        assertTrue("Tester indicated failure for Bitmap Under Limit (Change Enabled)", result)
    }

    @Test
    @Interactive
    @NotFullyAutomated(reason = "Manual notification UI verification required")
    fun uriOverLimit_ChangeEnabled() {
        poster.postUriNotification(NOTIFICATION_ID_URI_OVER, true)
        val result = Step.execute(VerifyNotificationUriOverChangeEnabled::class.java)
        assertTrue("Tester indicated failure for URI Over Limit (Change Enabled)", result)
    }

    @Test
    @Interactive
    @NotFullyAutomated(reason = "Manual notification UI verification required")
    fun uriUnderLimit_ChangeEnabled() {
        poster.postUriNotification(NOTIFICATION_ID_URI_UNDER, false)
        val result = Step.execute(VerifyNotificationUriUnderChangeEnabled::class.java)
        assertTrue("Tester indicated failure for URI Under Limit (Change Enabled)", result)
    }

    @Test
    @Interactive
    @NotFullyAutomated(reason = "Manual notification UI verification required")
    fun bitmapOverLimit_ChangeDisabled() {
        poster.postBitmapNotification(NOTIFICATION_ID_BITMAP_OVER, true)
        val result = Step.execute(VerifyNotificationBitmapOverChangeDisabled::class.java)
        assertTrue("Tester indicated failure for Bitmap Over Limit (Change Disabled)", result)
    }

    @Test
    @Interactive
    @NotFullyAutomated(reason = "Manual notification UI verification required")
    fun bitmapUnderLimit_ChangeDisabled() {
        poster.postBitmapNotification(NOTIFICATION_ID_BITMAP_UNDER, false)
        val result = Step.execute(VerifyNotificationBitmapUnderChangeDisabled::class.java)
        assertTrue("Tester indicated failure for Bitmap Under Limit (Change Disabled)", result)
    }

    @Test
    @Interactive
    @NotFullyAutomated(reason = "Manual notification UI verification required")
    fun uriOverLimit_ChangeDisabled() {
        poster.postUriNotification(NOTIFICATION_ID_URI_OVER, true)
        val result = Step.execute(VerifyNotificationUriOverChangeDisabled::class.java)
        assertTrue("Tester indicated failure for URI Over Limit (Change Disabled)", result)
    }

    @Test
    @Interactive
    @NotFullyAutomated(reason = "Manual notification UI verification required")
    fun uriUnderLimit_ChangeDisabled() {
        poster.postUriNotification(NOTIFICATION_ID_URI_UNDER, false)
        val result = Step.execute(VerifyNotificationUriUnderChangeDisabled::class.java)
        assertTrue("Tester indicated failure for URI Under Limit (Change Disabled)", result)
    }
}
