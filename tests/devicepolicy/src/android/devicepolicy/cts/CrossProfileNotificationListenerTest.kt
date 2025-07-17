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
package android.devicepolicy.cts

import android.Manifest.permission.POST_NOTIFICATIONS
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import com.android.bedstead.enterprise.annotations.EnsureHasProfileOwner
import com.android.bedstead.enterprise.annotations.EnsureHasWorkProfile
import com.android.bedstead.enterprise.annotations.RequireRunOnWorkProfile
import com.android.bedstead.enterprise.dpc
import com.android.bedstead.enterprise.workProfile
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.UserType
import com.android.bedstead.harrier.annotations.NotificationsTest
import com.android.bedstead.harrier.annotations.Postsubmit
import com.android.bedstead.multiuser.annotations.RequireRunOnPrimaryUser
import com.android.bedstead.nene.TestApis
import com.android.bedstead.testapp.TestAppInstance
import com.android.bedstead.testapps.testApps
import com.android.compatibility.common.util.ApiTest
import com.google.common.truth.Truth.assertWithMessage
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

typealias BedsteadNotification = com.android.bedstead.nene.notifications.Notification

@RunWith(BedsteadJUnit4::class)
@NotificationsTest
@ApiTest(
    apis = ["android.app.admin.DevicePolicyManager#setPermittedCrossProfileNotificationListeners",
        "android.app.admin.DevicePolicyManager#getPermittedCrossProfileNotificationListeners"]
)
class CrossProfileNotificationListenerTest {

    @Test
    @Postsubmit(reason = "new test")
    @RequireRunOnWorkProfile
    @EnsureHasProfileOwner(onUser = UserType.WORK_PROFILE, isPrimary = true)
    fun testCrossProfileNotificationListeners_set_get() {
        val testValue = listOf("foo.bar", "bar.foo")

        workProfileDpm().setPermittedCrossProfileNotificationListeners(who(), testValue)
        try {
            assertWithMessage("Getter returned different value")
                .that(workProfileDpm().getPermittedCrossProfileNotificationListeners(who()))
                .isEqualTo(testValue)
        } finally {
            workProfileDpm().setPermittedCrossProfileNotificationListeners(who(), null)
        }
    }

    @Test
    @Postsubmit(reason = "new test")
    @EnsureHasWorkProfile
    @EnsureHasProfileOwner(onUser = UserType.WORK_PROFILE, isPrimary = true)
    @RequireRunOnPrimaryUser
    fun testCrossProfileNotificationListeners_NullAllowlist_canReceiveNotification() {
        workProfileDpm().setPermittedCrossProfileNotificationListeners(who(), null)

        val notification = tryReceiveWorkNotification()

        assertWithMessage("Wasn't ablet to retrieve work notification")
            .that(notification)
            .isNotNull()
    }

    @Test
    @Postsubmit(reason = "new test")
    @EnsureHasWorkProfile
    @EnsureHasProfileOwner(onUser = UserType.WORK_PROFILE, isPrimary = true)
    @RequireRunOnPrimaryUser
    fun testCrossProfileNotificationListeners_EmptyAllowlist_cantReceiveNotification() {
        workProfileDpm().setPermittedCrossProfileNotificationListeners(who(), listOf())

        val notification = tryReceiveWorkNotification()

        assertWithMessage("Wasn't ablet to retrieve work notification")
            .that(notification)
            .isNull()
    }

    @Test
    @Postsubmit(reason = "new test")
    @EnsureHasWorkProfile
    @EnsureHasProfileOwner(onUser = UserType.WORK_PROFILE, isPrimary = true)
    @RequireRunOnPrimaryUser
    fun testCrossProfileNotificationListeners_PresentInAllowlist_canReceiveNotification() {
        workProfileDpm().setPermittedCrossProfileNotificationListeners(
            who(),
            listOf(TestApis.context().instrumentedContext().packageName)
        )

        val notification = tryReceiveWorkNotification()

        assertWithMessage("Wasn't ablet to retrieve work notification")
            .that(notification)
            .isNotNull()
    }

    companion object {
        @JvmField
        @ClassRule
        @Rule
        val deviceState = DeviceState()

        fun who() = deviceState.dpc().componentName()
        fun workProfileDpm() = deviceState.dpc().devicePolicyManager()!!

        private val testApp = deviceState.testApps().query()
            .wherePermissions().contains(POST_NOTIFICATIONS)
            .get()

        private const val NOTIFICATION_ID: Int = 12345
        private const val CHANNEL_ID: String = "NotificationTest"
        private const val CHANNEL_NAME: String = "NotificationTest"
        private const val IMPORTANCE: Int = NotificationManager.IMPORTANCE_MAX

        private fun TestAppInstance.showNotification() {
            val c = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, IMPORTANCE)

            val notification =
                Notification.Builder(TestApis.context().instrumentedContext(), CHANNEL_ID)
                    .setSmallIcon(R.drawable.test_drawable_1)
                    .setContentTitle("Notification")
                    .setContentText("Notification")
                    .setAutoCancel(true)
                    .build()

            this.notificationManager().createNotificationChannel(c)
            this.notificationManager().notify(NOTIFICATION_ID, notification)
        }

        private fun tryReceiveWorkNotification(): BedsteadNotification? {
            var notification: BedsteadNotification?
            testApp.install(deviceState.workProfile()).use { profileApp ->
                profileApp.permissions().withPermission(POST_NOTIFICATIONS).use {
                    profileApp.showNotification()
                    TestApis.notifications().createListener().use {
                        notification = it.query()
                            .whereNotification().channelId().isEqualTo(CHANNEL_ID)
                            .poll()

                        notification?.cancel()
                    }
                }
            }
            return notification
        }
    }
}
