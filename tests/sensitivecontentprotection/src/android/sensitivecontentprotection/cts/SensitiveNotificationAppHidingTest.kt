/*
 * Copyright (C) 2024 The Android Open Source Project
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
package android.sensitivecontentprotection.cts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.NotificationManager.IMPORTANCE_DEFAULT
import android.app.Person
import android.app.stubs.shared.NotificationHelper
import android.app.stubs.shared.TestNotificationAssistant
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Icon
import android.permission.flags.Flags
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.service.notification.StatusBarNotification
import android.util.Log
import android.view.cts.surfacevalidator.BitmapPixelChecker
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertWithMessage
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SensitiveNotificationAppHidingTest {
    private val TAG = SensitiveNotificationAppHidingTest::class.java.simpleName
    private val mediaProjectionHelper = SensitiveContentMediaProjectionHelper()
    private val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
    private val groupKey =
        "SensitiveNotificationAppHidingTest begun at " + System.currentTimeMillis()

    private lateinit var context: Context
    private lateinit var notificationManager: NotificationManager
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var notificationAssistant: TestNotificationAssistant

    @JvmField @Rule val checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @JvmField @Rule var testName = TestName()

    fun sendNotification(
        text: String = OTP_MESSAGE_BASIC,
        title: String = OTP_MESSAGE_BASIC,
        subtext: String = OTP_MESSAGE_BASIC,
        category: String = CATEGORY_MESSAGE,
    ) {
        val empty = Person.Builder().setName(PERSON_NAME).build()
        val message =
            Notification.MessagingStyle.Message(
                text,
                System.currentTimeMillis(),
                empty
            )
        val style = Notification.MessagingStyle(empty).apply { addMessage(message) }

        val nb = Notification.Builder(context, NOTIFICATION_CHANNEL_ID)
        nb.setContentText(text)
        nb.setContentTitle(title)
        nb.setSubText(subtext)
        nb.setCategory(category)
        nb.setSmallIcon(R.drawable.black)
        nb.setLargeIcon(Icon.createWithResource(context, R.drawable.black))
        nb.setGroup(groupKey)
        nb.setStyle(style)

        notificationManager.notify(groupKey, NOTIFICATION_ID, nb.build())

        waitForNotification()
    }

    private fun waitForNotification(): StatusBarNotification {
        val sbn =
            notificationHelper.findPostedNotification(
                groupKey,
                NOTIFICATION_ID,
                NotificationHelper.SEARCH_TYPE.POSTED
            )
        assertWithMessage("Expected to find a notification with tag $groupKey")
            .that(sbn)
            .isNotNull()
        return sbn!!
    }

    protected fun setUpNotifListener() {
        try {
            val listener = notificationHelper.enableListener(NLS_PACKAGE_NAME)
            assertNotNull(listener)
            listener.resetData()
        } catch (e: Exception) {
            Log.e(TAG, "error in setUpNotifListener", e)
        }
    }

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        notificationHelper = NotificationHelper(context)
        // ensure listener access isn't allowed before test runs (other tests could put
        // TestListener in an unexpected state)
        notificationHelper.disableListener(NLS_PACKAGE_NAME)
        notificationManager = context.getSystemService(NotificationManager::class.java)!!
        // clear the deck so that our getActiveNotifications results are predictable
        notificationManager.cancelAll()
        notificationManager.createNotificationChannel(
            NotificationChannel(NOTIFICATION_CHANNEL_ID, "name", IMPORTANCE_DEFAULT)
        )

        notificationAssistant = notificationHelper.enableAssistant(context.packageName)
        notificationAssistant.mMarkSensitiveContent = true

        setUpNotifListener()
    }

    @After
    fun teardown() {
        notificationManager.cancelAll()
        notificationHelper.disableListener(NLS_PACKAGE_NAME)
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_SENSITIVE_NOTIFICATION_APP_PROTECTION)
    fun testScreenCaptureIsBlocked() {
        sendNotification()

        uiAutomation.adoptShellPermissionIdentity()
        mediaProjectionHelper.authorizeMediaProjection()
        val mediaProjection = mediaProjectionHelper.startMediaProjection()
        Truth.assertThat(mediaProjection).isNotNull()
        ActivityScenario.launch(SimpleActivity::class.java).use { activityScenario ->
            activityScenario.onActivity { activity: SimpleActivity? ->
                val pixelChecker = BitmapPixelChecker(Color.BLACK)
                BitmapPixelChecker.validateScreenshot(
                    testName,
                    activity,
                    pixelChecker,
                    -1, // expectedMatchingPixels
                    BitmapPixelChecker.getInsets(activity)
                )
            }
        }
    }

    companion object {
        private const val NLS_PACKAGE_NAME = "android.sensitivecontentprotection.cts"
        private const val PERSON_NAME = "Alan Smithee"
        private const val OTP_MESSAGE_BASIC = "your one time code is 123645"
        private const val CATEGORY_MESSAGE = "msg"
        private const val NOTIFICATION_CHANNEL_ID = "SensitiveNotificationAppHidingTest"
        private const val NOTIFICATION_ID = 42
    }
}
