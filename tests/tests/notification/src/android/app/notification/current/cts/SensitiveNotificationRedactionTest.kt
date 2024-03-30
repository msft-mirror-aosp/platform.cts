/*
 * Copyright (C) 2023 The Android Open Source Project
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
package android.app.notification.current.cts

import android.Manifest.permission.POST_NOTIFICATIONS
import android.Manifest.permission.RECEIVE_SENSITIVE_NOTIFICATIONS
import android.app.Notification
import android.app.Notification.CATEGORY_MESSAGE
import android.app.Notification.EXTRA_MESSAGES
import android.app.Notification.EXTRA_SUB_TEXT
import android.app.Notification.EXTRA_TEXT
import android.app.Notification.EXTRA_TEXT_LINES
import android.app.Notification.EXTRA_TITLE
import android.app.Notification.InboxStyle
import android.app.Notification.MessagingStyle
import android.app.Notification.MessagingStyle.Message
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.app.stubs.R
import android.app.stubs.shared.NotificationHelper.SEARCH_TYPE
import android.companion.CompanionDeviceManager
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.MacAddress
import android.os.Bundle
import android.os.Parcelable
import android.os.SystemProperties
import android.permission.cts.PermissionUtils
import android.platform.test.annotations.RequiresFlagsDisabled
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.provider.DeviceConfig
import android.service.notification.Adjustment
import android.service.notification.Adjustment.KEY_IMPORTANCE
import android.service.notification.Adjustment.KEY_RANKING_SCORE
import android.service.notification.Flags
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.test.runner.AndroidJUnit4
import com.android.compatibility.common.util.CddTest
import com.android.compatibility.common.util.SystemUtil.runShellCommand
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Assert
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// TODO: b/301960090: Add tests with real NAS
/**
 * These tests ensure that untrusted notification listeners get a redacted version of notifications,
 * if said notifications have sensitive content.
 */
@RunWith(AndroidJUnit4::class)
class SensitiveNotificationRedactionTest : BaseNotificationManagerTest() {
    private val groupKey = "SensitiveNotificationRedactionTest begun at " +
            System.currentTimeMillis()

    @JvmField
    @Rule
    val checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()!!

    @Before
    @Throws(Exception::class)
    fun setUp() {
        PermissionUtils.grantPermission(STUB_PACKAGE_NAME, POST_NOTIFICATIONS)

        setUpNotifListener()
        mAssistant = mNotificationHelper.enableAssistant(mContext.packageName)
        mAssistant.mMarkSensitiveContent = true
        mAssistant.mSmartReplies =
            ArrayList<CharSequence>(listOf(OTP_MESSAGE_BASIC as CharSequence))
        mAssistant.mSmartActions = ArrayList<Notification.Action>(listOf(createAction()))
    }

    fun sendNotification(
        text: String = OTP_MESSAGE_BASIC,
        title: String = OTP_MESSAGE_BASIC,
        subtext: String = OTP_MESSAGE_BASIC,
        category: String = CATEGORY_MESSAGE,
        actions: List<Notification.Action>? = null,
        style: Notification.Style? = null,
        extras: Bundle? = null,
        tag: String = groupKey
    ) {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.setFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    or Intent.FLAG_ACTIVITY_CLEAR_TOP
        )
        intent.setAction(Intent.ACTION_MAIN)
        intent.setPackage(mContext.getPackageName())

        val nb = Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
        nb.setContentText(text)
        nb.setContentTitle(title)
        nb.setSubText(subtext)
        nb.setCategory(category)
        nb.setSmallIcon(R.drawable.black)
        nb.setLargeIcon(Icon.createWithResource(mContext, R.drawable.black))
        nb.setContentIntent(createTestPendingIntent())
        nb.setGroup(groupKey)
        if (actions != null) {
            nb.setActions(*actions.toTypedArray())
        }
        if (style != null) {
            nb.setStyle(style)
        }
        if (extras != null) {
            nb.addExtras(extras)
        }
        mNotificationManager.notify(tag, NOTIFICATION_ID, nb.build())
    }

    private fun createTestPendingIntent(): PendingIntent {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.setFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    or Intent.FLAG_ACTIVITY_CLEAR_TOP
        )
        intent.setAction(Intent.ACTION_MAIN)
        intent.setPackage(mContext.getPackageName())

        return PendingIntent.getActivity(mContext, 0, intent, PendingIntent.FLAG_MUTABLE)
    }

    private fun createAction(): Notification.Action {
        val pendingIntent = createTestPendingIntent()
        return Notification.Action.Builder(
            Icon.createWithResource(mContext, R.drawable.black),
            OTP_MESSAGE_BASIC,
            pendingIntent
        ).build()
    }

    private fun waitForNotification(
        searchType: SEARCH_TYPE = SEARCH_TYPE.POSTED,
        tag: String = groupKey
    ): StatusBarNotification {
        val sbn = mNotificationHelper.findPostedNotification(tag, NOTIFICATION_ID, searchType)
        assertWithMessage("Expected to find a notification with tag $tag")
                .that(sbn).isNotNull()
        return sbn!!
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_REDACT_SENSITIVE_NOTIFICATIONS_FROM_UNTRUSTED_LISTENERS)
    fun testTextFieldsRedacted() {
        val style = InboxStyle()
        style.addLine(OTP_MESSAGE_BASIC)

        sendNotification(style = style)
        val sbn = waitForNotification()

        val title = sbn.notification.extras.getCharSequence(EXTRA_TITLE)!!
        val aInfo: ApplicationInfo = mPackageManager
                .getApplicationInfo(mContext.packageName, 0)
        val pkgLabel = aInfo.loadLabel(mPackageManager).toString()
        assertWithMessage("Expected title to be $pkgLabel, but was $title")
                .that(title).isEqualTo(title)

        assertNotificationTextRedacted(sbn)

        val subtext = sbn.notification.extras.getCharSequence(EXTRA_SUB_TEXT)
        assertWithMessage("Expected subtext to be null, but it was $subtext").that(subtext).isNull()

        val textLines = sbn.notification.extras.getCharSequenceArray(EXTRA_TEXT_LINES)
        assertWithMessage("Expected text lines to be null, but it was ${textLines?.toList()}")
                .that(textLines).isNull()
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_REDACT_SENSITIVE_NOTIFICATIONS_FROM_UNTRUSTED_LISTENERS)
    fun testActionsRedacted() {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.setFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    or Intent.FLAG_ACTIVITY_CLEAR_TOP
        )
        intent.setAction(Intent.ACTION_MAIN)
        intent.setPackage(mContext.getPackageName())

        val pendingIntent = PendingIntent.getActivity(
            mContext,
            0,
            intent,
            PendingIntent.FLAG_MUTABLE
        )
        sendNotification(actions = listOf(createAction()))
        val sbn = waitForNotification()
        val action = sbn.notification.actions.firstOrNull()
        assertWithMessage("expected notification to have an action").that(action).isNotNull()
        assertWithMessage("expected notification action title not to contain otp:${action!!.title}")
                .that(action.title.toString()).doesNotContain(OTP_CODE)
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_REDACT_SENSITIVE_NOTIFICATIONS_FROM_UNTRUSTED_LISTENERS)
    fun testMessagesRedacted() {
        val empty = Person.Builder().setName(PERSON_NAME).build()
        val message = Message(OTP_MESSAGE_BASIC, System.currentTimeMillis(), empty)
        val style = MessagingStyle(empty).apply {
            addMessage(message)
            addMessage(message)
        }
        sendNotification(style = style)
        val sbn = waitForNotification()
        val messages = Message.getMessagesFromBundleArray(
            sbn.notification.extras.getParcelableArray(EXTRA_MESSAGES, Parcelable::class.java)
        )
        assertWithMessage("expected notification to have exactly one message")
                .that(messages.size).isEqualTo(1)
        assertWithMessage("expected single message not to contain otp: ${messages[0].text}")
                .that(messages[0].text.toString()).doesNotContain(OTP_CODE)
        assertWithMessage("expected message person to be redacted: ${messages[0].senderPerson}")
                .that(messages[0].senderPerson?.name.toString()).isNotEqualTo(PERSON_NAME)
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_REDACT_SENSITIVE_NOTIFICATIONS_FROM_UNTRUSTED_LISTENERS)
    fun testCustomExtrasNotRedacted() {
        val customExtra = Bundle()
        customExtra.putBoolean(groupKey, true)
        sendNotification(extras = customExtra)
        val sbn = waitForNotification()

        // Assert the notification is redacted
        assertNotificationTextRedacted(sbn)

        // Assert the custom extra is still present

        assertWithMessage("Expected custom extra to still be present, but it wasn't")
                .that(sbn.notification.extras.getBoolean(groupKey, false)).isTrue()
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_REDACT_SENSITIVE_NOTIFICATIONS_FROM_UNTRUSTED_LISTENERS)
    fun testRankingRedactedInPost() {
        mListener.mRankingMap = null
        sendNotification()
        val sbn = waitForNotification()
        assertWithMessage("Expected to receive a ranking map")
                .that(mListener.mRankingMap).isNotNull()
        assertRankingRedacted(sbn.key, mListener.mRankingMap)
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_REDACT_SENSITIVE_NOTIFICATIONS_FROM_UNTRUSTED_LISTENERS)
    fun testRankingRedactedInUpdate() {
        sendNotification()
        val sbn = waitForNotification()
        for (key in mListener.mRankingMap.orderedKeys) {
            val ranking = NotificationListenerService.Ranking()
            mListener.mRankingMap.getRanking(key, ranking)
        }
        mListener.mRankingMap = null
        val b = Bundle().apply {
            putInt(KEY_IMPORTANCE, NotificationManager.IMPORTANCE_MAX)
            putFloat(KEY_RANKING_SCORE, 1.0f)
        }
        val latch = mListener.setRankingUpdateCountDown(1)
        mAssistant.adjustNotification(Adjustment(sbn.packageName, sbn.key, b, "", sbn.user))
        latch.await()
        assertWithMessage("Expected to receive a ranking map")
                .that(mListener.mRankingMap).isNotNull()
        assertRankingRedacted(sbn.key, mListener.mRankingMap)
    }

    private fun assertRankingRedacted(
        key: String,
        rankingMap: NotificationListenerService.RankingMap
    ) {
        val ranking = NotificationListenerService.Ranking()
        val foundPostedNotifRanking = rankingMap.getRanking(key, ranking)
        assertWithMessage("Expected to find a ranking with key $key")
                .that(foundPostedNotifRanking).isTrue()
        assertWithMessage("Expected smart actions to be empty").that(ranking.smartActions)
                .isEmpty()
        assertWithMessage("Expected smart replies to be empty").that(ranking.smartReplies)
                .isEmpty()
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_REDACT_SENSITIVE_NOTIFICATIONS_FROM_UNTRUSTED_LISTENERS)
    fun testGetActiveNotificationsRedacted() {
        sendNotification()
        val postedSbn = waitForNotification()
        val activeSbn = mListener.getActiveNotifications(arrayOf(postedSbn.key)).first()
        assertNotificationTextRedacted(activeSbn)
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_REDACT_SENSITIVE_NOTIFICATIONS_FROM_UNTRUSTED_LISTENERS)
    fun testGetSnoozedNotificationsRedacted() {
        sendNotification()
        val postedSbn = waitForNotification()
        mListener.snoozeNotification(postedSbn.key, SHORT_SLEEP_TIME_MS)
        val snoozedSbn = waitForNotification(SEARCH_TYPE.SNOOZED)
        // Allow the notification to be unsnoozed
        Thread.sleep(SHORT_SLEEP_TIME_MS * 2)
        assertNotificationTextRedacted(snoozedSbn)
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_REDACT_SENSITIVE_NOTIFICATIONS_FROM_UNTRUSTED_LISTENERS)
    fun testListenerWithCdmAssociationGetsUnredacted() {
        assumeFalse(
            mPackageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE) ||
                mPackageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        )
        val cdmManager = mContext.getSystemService(CompanionDeviceManager::class.java)!!
        val macAddress = MacAddress.fromString("00:00:00:00:00:AA")
        try {
            runShellCommand(
                "cmd companiondevice associate " +
                        "${mContext.userId} ${mContext.packageName} $macAddress"
            )
            // Trusted status is cached on helper enable, so disable + enable the listener
            mNotificationHelper.disableListener(STUB_PACKAGE_NAME)
            mNotificationHelper.enableListener(STUB_PACKAGE_NAME)
            assertNotificationNotRedacted()
        } finally {
            runWithShellPermissionIdentity {
                val assocInfo = cdmManager.allAssociations.find {
                    mContext.packageName.equals(it.packageName)
                }
                assertWithMessage("Expected to have an active cdm association")
                        .that(assocInfo).isNotNull()
                cdmManager.disassociate(assocInfo!!.id)
            }
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_REDACT_SENSITIVE_NOTIFICATIONS_FROM_UNTRUSTED_LISTENERS)
    fun testListenerWithReceiveSensitiveNotificationsGetsUnredacted() {
        runWithShellPermissionIdentity(
            {
                // Trusted status is cached on helper enable, so disable + enable the listener
                mNotificationHelper.disableListener(STUB_PACKAGE_NAME)
                mNotificationHelper.enableListener(STUB_PACKAGE_NAME)
                assertNotificationNotRedacted()
            },
            RECEIVE_SENSITIVE_NOTIFICATIONS
        )
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_REDACT_SENSITIVE_NOTIFICATIONS_FROM_UNTRUSTED_LISTENERS)
    fun testStandardListenerGetsUnredactedWhenFlagDisabled() {
        assertNotificationNotRedacted()
    }

    // see packages/modules/ExtServices/java/tests/src/android/ext/services/notification/
    // NotificationOtpDetectionHelperTest.kt for more granular tests of these otp messages
    @Test
    @CddTest(requirement = "3.8.3.4/C-1-1")
    @RequiresFlagsEnabled(Flags.FLAG_REDACT_SENSITIVE_NOTIFICATIONS_FROM_UNTRUSTED_LISTENERS)
    fun testE2ERedaction_shouldRedact() {
        assumeFlagEnabled()
        assertTrue(
            "Expected a notification assistant to be present",
            mPreviousEnabledAssistant != null
        )
        mNotificationHelper.disableAssistant(STUB_PACKAGE_NAME)
        mNotificationHelper.enableOtherPkgAssistantIfNeeded(mPreviousEnabledAssistant)
        // We just re-enabled the NAS. send one notification in order to start its process
        sendNotification(text = "staring NAS process", title = "", subtext = "", tag = "start")
        waitForNotification(tag = "start")

        val shouldRedact = mutableListOf(
            "123G5",
            "123 456",
            "123456F8",
            "123ķ4",
            "123Ŀ4",
            "1-1-01 is the date of your code T3425",
            "your code 54-234-3 was sent on 1-1-01",
            "34-58-30",
            "12-1-3089",
            "G-3d523",
            "G-FD-745",
            "your code is:G-345821",
            "your code is (G-345821",
            "your code is \nG-345821",
            "your code is 'G-345821",
            "your code is \"G-345821",
            "your code is [G-345821",
            "your code is码G-345821",
            "you code is G-345821.",
            "you code is (G-345821)",
            "you code is 'G-345821'",
            "your code is码G-345821码",
            "c'est g4zy75",
            "2109",
            "3035",
            "1899")
        var notifNum = 0
        val notRedactedFailures = StringBuilder("")
        for (otp in shouldRedact) {
            val tag = "$groupKey #$notifNum"
            sendNotification(text = otp, title = "", subtext = "", tag = tag)
            val sbn = waitForNotification(tag = tag)
            val text = sbn.notification.extras.getCharSequence(EXTRA_TEXT)!!.toString()
            if (text.contains(otp)) {
                notRedactedFailures.append("otp \"$otp\" is in notification text \"$text\"\n")
            }
            notifNum += 1
        }

        if (notRedactedFailures.toString() != "") {
            Assert.fail(
                "The following codes were not redacted, but should have been:" +
                        "\n$notRedactedFailures"
            )
        }
    }

    // see packages/modules/ExtServices/java/tests/src/android/ext/services/notification/
    // NotificationOtpDetectionHelperTest.kt for more granular tests of these otp messages
    @Test
    @CddTest(requirement = "3.8.3.4/C-1-1")
    @RequiresFlagsEnabled(Flags.FLAG_REDACT_SENSITIVE_NOTIFICATIONS_FROM_UNTRUSTED_LISTENERS)
    fun testE2ERedaction_shouldNotRedact() {
        assumeFlagEnabled()
        assertTrue(
            "Expected a notification assistant to be present",
            mPreviousEnabledAssistant != null
        )
        mNotificationHelper.disableAssistant(STUB_PACKAGE_NAME)
        mNotificationHelper.enableOtherPkgAssistantIfNeeded(mPreviousEnabledAssistant)
        // We just re-enabled the NAS. send one notification in order to start its process
        sendNotification(text = "staring NAS process", title = "", subtext = "", tag = "start")
        waitForNotification(tag = "start")

        val shouldNotRedact =
            mutableListOf(
                "123G",
                "123",
                "12 345",
                "123T56789",
                "123码456",
                "TEFHXES",
                "01-01-2001",
                "1-1-2001",
                "1-1-01",
                "6--7893",
                "------",
                "your code isG-345821",
                "your code is G-345821for real",
                "your code is4:G-345821",
                "GVRXY 2",
                "2009",
                "1945",
                "a4mst",
            )
        var notifNum = 0
        val redactedFailures = StringBuilder("")
        for (notOtp in shouldNotRedact) {
            val tag = "$groupKey #$notifNum"
            sendNotification(text = notOtp, title = "", subtext = "", tag = tag)
            val sbn = waitForNotification(tag = tag)
            val text = sbn.notification.extras.getCharSequence(EXTRA_TEXT)!!.toString()
            if (!text.contains(notOtp)) {
                redactedFailures.append(
                    "non-otp message \"$notOtp\" is not in notification text " +
                        "\"$text\"\n"
                )
            }
            notifNum += 1
        }

        if (redactedFailures.toString() != "") {
            Assert.fail(
                "The following codes were redacted, but should not have been:" +
                        "\n$redactedFailures"
            )
        }
    }

    private fun assumeFlagEnabled() {
        // TODO b/331633527: STOPSHIP remove once flag ramped
        runWithShellPermissionIdentity {
            assumeTrue(
                DeviceConfig.getBoolean(
                "device_personalization_services",
                "Notification__enable_otp_in_smart_suggestion",
                false
            ) || SystemProperties.get("ro.product.name", "").startsWith("aosp")
            )
        }
    }

    private fun assertNotificationNotRedacted() {
        sendNotification()
        val sbn = waitForNotification()
        val text = sbn.notification.extras.getCharSequence(EXTRA_TEXT)!!.toString()
        assertWithMessage("Expected notification text to contain OTP code, but it did not: $text")
                .that(text).contains(OTP_CODE)
    }

    private fun assertNotificationTextRedacted(sbn: StatusBarNotification) {
        val text = sbn.notification.extras.getCharSequence(EXTRA_TEXT)!!.toString()
        assertWithMessage("Expected notification text not to contain OTP code, but it did: $text")
                .that(text).doesNotContain(OTP_CODE)
    }

    companion object {
        private const val OTP_CODE = "123645"
        private const val OTP_MESSAGE_BASIC = "your one time code is 123645"
        private const val PERSON_NAME = "Alan Smithee"
        private const val NOTIFICATION_ID = 42
        private const val SHORT_SLEEP_TIME_MS: Long = 100
    }
}
