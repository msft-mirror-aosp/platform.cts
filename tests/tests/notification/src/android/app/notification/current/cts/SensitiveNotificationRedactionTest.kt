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

import android.Manifest
import android.Manifest.permission.POST_NOTIFICATIONS
import android.Manifest.permission.RECEIVE_SENSITIVE_NOTIFICATIONS
import android.app.AppOpsManager
import android.app.Notification
import android.app.Notification.BubbleMetadata
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
import android.app.role.RoleManager
import android.app.stubs.R
import android.app.stubs.shared.NotificationHelper.SEARCH_TYPE
import android.companion.CompanionDeviceManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.net.MacAddress
import android.os.Bundle
import android.os.Parcelable
import android.os.Process
import android.permission.cts.PermissionUtils
import android.platform.test.annotations.RequiresFlagsDisabled
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.provider.Settings
import android.provider.Telephony
import android.security.Flags.FLAG_APP_LOCK_APIS
import android.security.Flags.FLAG_APP_LOCK_CORE
import android.service.notification.Adjustment
import android.service.notification.Adjustment.KEY_IMPORTANCE
import android.service.notification.Adjustment.KEY_RANKING_SCORE
import android.service.notification.Flags
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.ArraySet
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import com.android.bedstead.nene.TestApis
import com.android.compatibility.common.util.CddTest
import com.android.compatibility.common.util.SystemUtil.callWithShellPermissionIdentity
import com.android.compatibility.common.util.SystemUtil.runShellCommand
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.android.compatibility.common.util.UserHelper
import com.android.sts.common.LockSettingsUtil
import com.android.sts.common.SystemUtil.poll
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import org.junit.After
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeNotNull
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
        val userHelper = UserHelper(mContext)
        // TODO(b/380297485): Remove this assumption check once NotificationListeners
        // support visible background users.
        assumeFalse(
            "NotificationListeners do not support visible background users",
                userHelper.isVisibleBackgroundUser()
        )
        PermissionUtils.grantPermission(STUB_PACKAGE_NAME, POST_NOTIFICATIONS)

        disableRoleFallbackAndRemoveSmsRole()

        setUpNotifListener()
        mAssistant = mNotificationHelper.enableAssistant(mContext.packageName)
        mAssistant.mMarkSensitiveContent = true
        mAssistant.mSmartReplies =
            ArrayList<CharSequence>(listOf(OTP_MESSAGE_BASIC as CharSequence))
        mAssistant.mSmartActions = ArrayList<Notification.Action>(listOf(createAction()))
    }

    @After
    fun tearDown() {
        setSmsComponentsEnabled(true)
    }

    /**
     * Ensure the test package does not hold the SMS role, which would bypass redaction.
     * On some devices (like Wear), the system auto-assigns the SMS role to this package
     * as a fallback. This flags the listener as "trusted" and bypasses redaction.
     * To stop this, we disable the components that make it qualify for the role.
     */
    private fun disableRoleFallbackAndRemoveSmsRole() {
        setSmsComponentsEnabled(false)
        removeSmsRole(STUB_PACKAGE_NAME)
    }

    private fun setSmsComponentsEnabled(enabled: Boolean) {
        val state = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        val pm = mContext.packageManager
        val components = listOf(
            "android.app.stubs.shared.SmsActivity",
            "android.app.stubs.shared.SmsService",
            "android.app.stubs.shared.SmsReceiver",
            "android.app.stubs.shared.SmsReceiver2"
        )
        for (component in components) {
            try {
                pm.setComponentEnabledSetting(
                    ComponentName(STUB_PACKAGE_NAME, component),
                    state,
                    PackageManager.DONT_KILL_APP
                )
                Log.d(TAG, "Set $component enabled state to $state")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to set $component state to $state", e)
            }
        }
    }

    private fun removeSmsRole(packageName: String) {
        val latch = CountDownLatch(1)
        Log.d(TAG, "Attempting to remove $packageName from SMS role")
        runWithShellPermissionIdentity {
            mRoleManager.removeRoleHolderAsUser(
                RoleManager.ROLE_SMS,
                packageName,
                0,
                Process.myUserHandle(),
                Executors.newSingleThreadExecutor()
            ) { success ->
                Log.d(TAG, "Removed $packageName from SMS role: $success")
                latch.countDown()
            }
        }
        latch.await()
    }

    fun sendNotification(
        text: String = OTP_MESSAGE_BASIC,
        title: String = OTP_MESSAGE_BASIC,
        subtext: String = OTP_MESSAGE_BASIC,
        category: String = CATEGORY_MESSAGE,
        actions: List<Notification.Action>? = null,
        people: List<Person>? = null,
        style: Notification.Style? = null,
        extras: Bundle? = null,
        fullScreenIntent: PendingIntent? = null,
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

        if (fullScreenIntent != null) {
            nb.setFullScreenIntent(fullScreenIntent, true)
        }
        if (actions != null) {
            nb.setActions(*actions.toTypedArray())
        }
        if (people != null) {
            people.forEach { nb.addPerson(it) }
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
    @RequiresFlagsEnabled(FLAG_APP_LOCK_CORE, FLAG_APP_LOCK_APIS)
    fun testAppLock_onAppLockEnabled_notificationRepostedAsRedacted() {
        LockSettingsUtil(mContext).withLockScreen().use {
            mAssistant.mMarkSensitiveContent = false
            buildAndSendNotification()

            assertNotificationUnredacted(waitForNotification())
            mListener.resetData()

            setAppLockEnabledState().use {
                assertNotificationRedacted(waitForNotification())
            }
        }
    }

    @Test
    @RequiresFlagsEnabled(FLAG_APP_LOCK_CORE, FLAG_APP_LOCK_APIS)
    fun testAppLock_onAppLockDisabled_notificationRepostedAsUnredacted() {
        mAssistant.mMarkSensitiveContent = false
        LockSettingsUtil(mContext).withLockScreen().use {
            setAppLockEnabledState().use {
                buildAndSendNotification()

                assertNotificationRedacted(waitForNotification())
                mListener.resetData()
            }
            assertNotificationUnredacted(waitForNotification())
        }
    }

    @Test
    @RequiresFlagsEnabled(FLAG_APP_LOCK_CORE, FLAG_APP_LOCK_APIS)
    fun testAppLock_onAppUnlocked_notificationRepostedAsUnredacted() {
        mAssistant.mMarkSensitiveContent = false

        LockSettingsUtil(mContext).withLockScreen().use {
            setAppLockEnabledState().use {
            buildAndSendNotification()

            assertNotificationRedacted(waitForNotification())
            mListener.resetData()
            unlockAppViaPin()

            assertNotificationUnredacted(waitForNotification())
            }
        }
    }

    @Test
    @RequiresFlagsEnabled(FLAG_APP_LOCK_CORE, FLAG_APP_LOCK_APIS)
    fun testAppLock_onAppRelocked_notificationRepostedAsRedacted() {
        mAssistant.mMarkSensitiveContent = false

        LockSettingsUtil(mContext).withLockScreen().use {
            setAppLockEnabledState().use {
                buildAndSendNotification()
                waitForNotification()
                mListener.resetData()
                unlockAppViaPin()

                assertNotificationUnredacted(waitForNotification())
                mListener.resetData()
                UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressHome()
                poll {
                    mNotificationHelper.findPostedNotification(groupKey, NOTIFICATION_ID,
                            SEARCH_TYPE.POSTED) != null
                }

                assertNotificationRedacted(waitForNotification())
            }
        }
    }

    @Test
    @RequiresFlagsEnabled(FLAG_APP_LOCK_CORE, FLAG_APP_LOCK_APIS)
    fun testAppLock_callStyleNotification_alwaysPostedAsUnredacted() {
        val caller = "Test Caller"
        mAssistant.mMarkSensitiveContent = false

        LockSettingsUtil(mContext).withLockScreen().use {
            setAppLockEnabledState().use {
                val person = Person.Builder().setName(caller).build()
                val intent = createTestPendingIntent()
                val callStyle = Notification.CallStyle.forIncomingCall(person, intent, intent)
                sendNotification( title = caller, style = callStyle, fullScreenIntent = intent)

                val sbn = waitForNotification()
                assertCommonUnredactedFeatures(sbn, hasPeople = false)

                val extras = sbn.notification.extras
                val actualTitle = extras.getCharSequence(EXTRA_TITLE)?.toString()
                val template = extras.getString(Notification.EXTRA_TEMPLATE)

                assertWithMessage("CallStyle title must never be redacted regardless of lock state")
                        .that(actualTitle).isEqualTo(caller)
                assertWithMessage("CallStyle template must be preserved")
                        .that(template).isEqualTo(Notification.CallStyle::class.java.name)
            }
        }
    }

    @Test
    @RequiresFlagsEnabled(FLAG_APP_LOCK_CORE, FLAG_APP_LOCK_APIS)
    fun testAppLock_messagingStyleNotification_notBubbled_messagesAreFullyRedacted() {
        mAssistant.mMarkSensitiveContent = false

        LockSettingsUtil(mContext).withLockScreen().use {
            val notificationBuilder = Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                .setContentTitle(NOTIFICATION_TITLE)
                .setSmallIcon(R.drawable.black)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setStyle(getMessagingStyle())

            mNotificationManager.notify(groupKey, NOTIFICATION_ID, notificationBuilder.build())
            waitForNotification()
            mListener.resetData()

            setAppLockEnabledState().use {
                val sbn = waitForNotification()
                val extras = sbn.notification.extras

                assertNotificationRedacted(sbn)
                assertThat(extras.getString(Notification.EXTRA_TEMPLATE)).isNull()
                assertThat(extras.getParcelableArray(EXTRA_MESSAGES, Parcelable::class.java))
                        .isNull()
            }
        }
    }

    @Test
    @RequiresFlagsEnabled(FLAG_APP_LOCK_CORE, FLAG_APP_LOCK_APIS)
    fun testAppLock_messagingStyleNotification_bubbled_messagesAndSenderAreRedacted() {
        // TODO(b/492535797): Add a check for the android.content.pm.app_lock_shortcut_removal flag
        mAssistant.mMarkSensitiveContent = false

        LockSettingsUtil(mContext).withLockScreen().use {
            allowAllNotificationsToBubble()
            createShortcut()

            val notificationBuilder = Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                .setContentTitle(NOTIFICATION_TITLE)
                .setSmallIcon(R.drawable.black)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setShortcutId(NOTIFICATION_SHORTCUT_ID)
                .setStyle(getMessagingStyle())
                .setBubbleMetadata(getDefaultBubbleMetadata())
                .setFlag(Notification.FLAG_BUBBLE, true)
            mNotificationManager.notify(groupKey, NOTIFICATION_ID, notificationBuilder.build())

            // Verify notification posted successfully as a bubble.
            val statusBarNotification = waitForNotification()
            val hasBubbleFlag =
                    (statusBarNotification.notification.flags and Notification.FLAG_BUBBLE) != 0

            assertThat(hasBubbleFlag).isTrue()
            mListener.resetData()

            setAppLockEnabledState().use {
                val sbn = waitForNotification()

                assertCommonRedactionFeatures(sbn)

                val extras = sbn.notification.extras
                val messagesArray =
                        extras.getParcelableArray(EXTRA_MESSAGES, Parcelable::class.java)
                val postedMessages = Message.getMessagesFromBundleArray(messagesArray)
                assertThat(messagesArray).isNotNull()

                val title = extras.getCharSequence(EXTRA_TITLE)?.toString()
                val subtext = extras.getCharSequence(EXTRA_SUB_TEXT)?.toString()
                assertWithMessage("Title should be redacted").that(title).isEmpty()
                assertWithMessage("Subtext should be removed").that(subtext).isNull()

                val template = extras.getString(Notification.EXTRA_TEMPLATE)
                assertThat(template).isEqualTo(Notification.MessagingStyle::class.java.name)

                val messageText = postedMessages[0].text?.toString()
                assertThat(messageText).matches("New (notification|message)")

                val senderName = postedMessages[0].senderPerson?.name?.toString() ?: ""
                assertWithMessage("Sender name should be redacted").that(senderName).isEmpty()

                val bubbleMetadata = sbn.notification.bubbleMetadata
                assertWithMessage("Bubble metadata should be preserved")
                        .that(bubbleMetadata).isNotNull()
                assertWithMessage("Bubble icon should remain the same")
                        .that(bubbleMetadata?.icon).isNotNull()
            }
        }
    }

    @Test
    @RequiresFlagsEnabled(FLAG_APP_LOCK_CORE, FLAG_APP_LOCK_APIS)
    fun testAppLock_assistantGetsUnredactedNotificationWhenLocked() {
        mAssistant.mMarkSensitiveContent = false
        LockSettingsUtil(mContext).withLockScreen().use {
            setAppLockEnabledState().use {
                buildAndSendNotification()

                assertNotificationRedacted(waitForNotification())

                var assistantSbn: StatusBarNotification? = null
                poll {
                    assistantSbn = mAssistant.activeNotifications?.find {
                        it.tag == groupKey && it.id == NOTIFICATION_ID
                    }
                    assistantSbn != null
                }

                assertWithMessage("Assistant should receive the unredacted notification")
                        .that(assistantSbn).isNotNull()
                assertNotificationUnredacted(assistantSbn!!)
            }
        }
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
    @RequiresFlagsEnabled(
        Flags.FLAG_REDACT_SENSITIVE_NOTIFICATIONS_FROM_UNTRUSTED_LISTENERS,
        Flags.FLAG_REDACT_SENSITIVE_NOTIFICATIONS_BIG_TEXT_STYLE
    )
    fun testBigTextRedacted() {
        val style = Notification.BigTextStyle()
        val bigText = "BIG TEXT"
        val bigTitleText = "BIG TITLE TEXT"
        val summaryText = "summary text"
        style.bigText(bigText)
        style.setBigContentTitle(bigTitleText)
        style.setSummaryText(summaryText)
        sendNotification(style = style)
        val sbn = waitForNotification()
        val extras = sbn.notification.extras
        val testBigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString()
        val testBigTitleText = extras.getCharSequence(Notification.EXTRA_TITLE_BIG).toString()
        val testSummaryText = extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT).toString()
        assertWithMessage("expected big text to be redacted: $testBigText")
            .that(testBigText).doesNotContain(bigText)
        assertWithMessage("expected big title text to be redacted: $testBigTitleText")
            .that(testBigTitleText).doesNotContain(bigTitleText)
        assertWithMessage("expected summary text to be redacted: $testSummaryText")
            .that(testSummaryText).doesNotContain(summaryText)
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
            if (android.companion.Flags.cmdOptions()) {
                runShellCommand(
                    "cmd companiondevice associate " +
                            "${mContext.userId} ${mContext.packageName} --mac-address $macAddress"
                )
            } else {
                runShellCommand(
                    "cmd companiondevice associate " +
                            "${mContext.userId} ${mContext.packageName} $macAddress"
                )
            }
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
    fun testListenerWithReceiveSensitiveNotificationsPermissionsGetsUnredacted() {
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
    @RequiresFlagsEnabled(Flags.FLAG_REDACT_SENSITIVE_NOTIFICATIONS_FROM_UNTRUSTED_LISTENERS)
    fun testListenerWithReceiveSensitiveNotificationsAppOpGetsUnredacted() {
        val appOpsManager = mContext.getSystemService(AppOpsManager::class.java)!!
        try {
            runWithShellPermissionIdentity {
                assertEquals(
                    AppOpsManager.MODE_IGNORED,
                    appOpsManager.checkOp(
                        AppOpsManager.OPSTR_RECEIVE_SENSITIVE_NOTIFICATIONS,
                        Process.myUid(),
                        STUB_PACKAGE_NAME
                    )
                )
                appOpsManager.setUidMode(
                    AppOpsManager.OPSTR_RECEIVE_SENSITIVE_NOTIFICATIONS,
                    Process.myUid(),
                    AppOpsManager.MODE_ALLOWED
                )
            }
            // Trusted status is cached on helper enable, so disable + enable the listener
            mNotificationHelper.disableListener(STUB_PACKAGE_NAME)
            mNotificationHelper.enableListener(STUB_PACKAGE_NAME)
            assertNotificationNotRedacted()
        } finally {
            runWithShellPermissionIdentity {
                appOpsManager.setUidMode(
                    AppOpsManager.OPSTR_RECEIVE_SENSITIVE_NOTIFICATIONS,
                    Process.myUid(),
                    AppOpsManager.MODE_IGNORED
                )
            }
        }
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
        assumeFalse(
            mPackageManager.hasSystemFeature(PackageManager.FEATURE_WATCH) ||
                    mPackageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE) ||
                    // Because this test relies on a real TextClassifier, cuttlefish isn't performant
                    // enough to get classification done in the 200ms timeout period, causing the
                    // test to be very flaky
                    NotificationManagerTest.onCuttlefish()
        )
        assertTrue(
            "Expected a notification assistant to be present",
            mPreviousEnabledAssistant != null
        )
        setSmsComponentsEnabled(true)
        mNotificationHelper.disableAssistant(STUB_PACKAGE_NAME)
        val existingSmsApp = callWithShellPermissionIdentity {
            Telephony.Sms.getDefaultSmsPackage(mContext)
        }
        assumeNotNull(existingSmsApp)
        val shouldRedact = mutableListOf(
            "your code 54-234-3 was sent on 1-1-01",
            "your code is 34-58-30",
            "your code is 12-1-3089",
            "your code is:G-345821",
            "your code is (G-345821",
            "your code is \nG-345821",
            "you code is G-345821.",
            "you code is (G-345821)"
        )
        var notifNum = 0
        val notRedactedFailures = StringBuilder("")
        try {
            setSmsApp(mContext.packageName)
            mNotificationHelper.enableOtherPkgAssistantIfNeeded(mPreviousEnabledAssistant)
            // We just re-enabled the NAS. send one notification in order to start its process
            sendNotification(text = "staring NAS process", title = "", subtext = "", tag = "start")
            waitForNotification(tag = "start")
            // Newly enabled NAS can sometimes take a short while to start properly responding
            for (i in 0..<20) {
                val basicOtp = "your one time code is 3434"
                val tag = groupKey
                sendNotification(text = basicOtp, title = "", subtext = "", tag = tag)
                val sbn = waitForNotification(tag = tag)
                val text = sbn.notification.extras.getCharSequence(EXTRA_TEXT)!!.toString()
                if (!text.contains(basicOtp)) {
                    // Detector is up and running
                    break
                }
                Thread.sleep(100)
            }

            for (otp in shouldRedact) {
                val tag = "$groupKey #$notifNum"
                lateinit var text: String
                for (i in 0..<10) {
                    sendNotification(text = otp, title = "", subtext = "", tag = tag)
                    val sbn = waitForNotification(tag = tag)
                    text = sbn.notification.extras.getCharSequence(EXTRA_TEXT)!!.toString()
                    if (!text.contains(otp)) {
                        break
                    }
                    Thread.sleep(200)
                }
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
        } finally {
            setSmsApp(existingSmsApp)
        }
    }

    // see packages/modules/ExtServices/java/tests/src/android/ext/services/notification/
    // NotificationOtpDetectionHelperTest.kt for more granular tests of these otp messages
    @Test
    @CddTest(requirement = "3.8.3.4/C-1-1")
    @RequiresFlagsEnabled(Flags.FLAG_REDACT_SENSITIVE_NOTIFICATIONS_FROM_UNTRUSTED_LISTENERS)
    fun testE2ERedaction_shouldNotRedact() {
        assumeFalse(
            mPackageManager.hasSystemFeature(PackageManager.FEATURE_WATCH) ||
                    mPackageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE) ||
                    // Because this test relies on a real TextClassifier, cuttlefish isn't performant
                    // enough to get classification done in the 200ms timeout period, causing the
                    // test to be very flaky
                    NotificationManagerTest.onCuttlefish()
        )
        assertTrue(
            "Expected a notification assistant to be present",
            mPreviousEnabledAssistant != null
        )
        setSmsComponentsEnabled(true)
        mNotificationHelper.disableAssistant(STUB_PACKAGE_NAME)
        val existingSmsApp = callWithShellPermissionIdentity {
            Telephony.Sms.getDefaultSmsPackage(mContext)
        }
        assumeNotNull(existingSmsApp)
        setSmsApp(mContext.packageName)
        mNotificationHelper.enableOtherPkgAssistantIfNeeded(mPreviousEnabledAssistant)
        // We just re-enabled the NAS. send one notification in order to start its process
        sendNotification(text = "staring NAS process", title = "", subtext = "", tag = "start")
        waitForNotification(tag = "start")

        val shouldNotRedact =
            mutableListOf(
                "your code is 01-01-2001",
                "your code is 1-1-2001",
                "your code is 1-1-01",
                "your code is 6--7893",
                "your code is ------",
            )
        var notifNum = 0
        val redactedFailures = StringBuilder("")
        try {
            // Newly enabled NAS can sometimes take a short while to start properly responding
            for (i in 0..20) {
                val basicOtp = "your one time code is 3434"
                val tag = groupKey
                sendNotification(text = basicOtp, title = "", subtext = "", tag = tag)
                val sbn = waitForNotification(tag = tag)
                val text = sbn.notification.extras.getCharSequence(EXTRA_TEXT)!!.toString()
                if (!text.contains(basicOtp)) {
                    // Detector is up and running
                    break
                }
                Thread.sleep(100)
            }

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
        } finally {
            setSmsApp(existingSmsApp)
        }
    }

    private fun setSmsApp(packageName: String) {
        val latch = CountDownLatch(1)
        runWithShellPermissionIdentity {
            mRoleManager.addRoleHolderAsUser(
                RoleManager.ROLE_SMS,
                packageName,
                0,
                Process.myUserHandle(),
                Executors.newSingleThreadExecutor()
            ) { success ->
                assertTrue("Failed to set sms role holder", success)
                latch.countDown()
            }
        }
        latch.await()
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

    private fun setAppLockState(state: Boolean) {
        runWithShellPermissionIdentity({
            val isAppLockStateChanged =
                mContext.packageManager.setPackageAppLockEnabled(mContext.packageName, state)

            assertWithMessage("App lock state change should be successful")
                .that(isAppLockStateChanged).isTrue()
        }, Manifest.permission.TEST_LOCK_APPS)
    }

    private fun setAppLockEnabledState(): AutoCloseable {
        setAppLockState(true)
        return AutoCloseable { setAppLockState(false) }
    }

    private fun unlockAppViaPin() {
        val launchIntent = mContext.packageManager.getLaunchIntentForPackage(mContext.packageName)
        launchIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        TestApis.activities().startActivity(launchIntent)

        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.waitForIdle()
        val pin = TEST_PIN
        for (digit in pin) {
            val button = device.findObject(UiSelector().text(digit.toString()))
            assertWithMessage("PIN digit button '$digit' not found on screen").that(
                    button.waitForExists(UI_WAIT_TIMEOUT_MS)).isTrue()

            button.click()
        }
        device.pressEnter()
        device.waitForIdle()
    }

    private fun buildAndSendNotification() {
        val action = Notification.Action.Builder(null, ACTION_TITLE, createTestPendingIntent())
                .build()
        sendNotification(
            title = NOTIFICATION_TITLE,
            text = NOTIFICATION_TEXT,
            subtext = NOTIFICATION_SUBTEXT,
            actions = listOf(action),
            people = listOf(Person.Builder().setName(PERSON_NAME).build())
        )
    }

    private fun assertNotificationRedacted(statusBarNotification: StatusBarNotification) {
        assertCommonRedactionFeatures(statusBarNotification)

        val extras = statusBarNotification.notification.extras
        val title = extras.getCharSequence(EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(EXTRA_TEXT)?.toString()
        val subtext = extras.getCharSequence(EXTRA_SUB_TEXT)?.toString()
        val appLabel = mContext.packageManager
                .getApplicationLabel(mContext.applicationInfo).toString()

        assertWithMessage("Title should be redacted").that(title).isEqualTo(appLabel)
        assertWithMessage("Text should be redacted").that(text)
                .matches("New (notification|message)")
        assertWithMessage("Subtext should be removed").that(subtext).isNull()
    }

    private fun assertNotificationUnredacted(statusBarNotification: StatusBarNotification) {
        assertCommonUnredactedFeatures(statusBarNotification)

        val extras = statusBarNotification.notification.extras
        val notification = statusBarNotification.notification
        val actions = notification.actions
        val people = extras.getParcelableArrayList(Notification.EXTRA_PEOPLE_LIST,
                Person::class.java)

        assertWithMessage("Title should be unredacted")
                .that(extras.getCharSequence(EXTRA_TITLE)?.toString()).isEqualTo(NOTIFICATION_TITLE)
        assertWithMessage("Text should be unredacted")
                .that(extras.getCharSequence(EXTRA_TEXT)?.toString()).isEqualTo(NOTIFICATION_TEXT)
        assertWithMessage("Subtext should be present")
                .that(extras.getCharSequence(EXTRA_SUB_TEXT)?.toString())
                        .isEqualTo(NOTIFICATION_SUBTEXT)
        assertWithMessage("Action title should match expected value").that(
                actions!![0].title.toString()).isEqualTo(ACTION_TITLE)
        assertWithMessage("Person name should match expected value").that(people!![0].name)
                .isEqualTo(PERSON_NAME)
    }

    private fun allowAllNotificationsToBubble() {
        val userId = mContext.user.identifier
        val pkg = mContext.packageName

        runShellCommand("cmd notification set_bubbles $pkg 1 $userId")
        runShellCommand(
            "cmd notification set_bubbles_channel $pkg $NOTIFICATION_CHANNEL_ID true $userId"
        )
        runWithShellPermissionIdentity {
            Settings.Secure.putInt(
                mContext.contentResolver,
                Settings.Secure.NOTIFICATION_BUBBLES,
                1
            )
        }
        poll {
            mNotificationManager.areBubblesAllowed()
        }
    }

    fun createShortcut() {
        val person = Person.Builder()
            .setBot(false)
            .setIcon(Icon.createWithResource(mContext, R.drawable.black))
            .setName(BUBBLE_SENDER_NAME)
            .setImportant(true)
            .build()

        val categorySet = ArraySet<String>()
        categorySet.add("com.android.app.notification.current.cts.SHORTCUT_CATEGORY")
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val shortcutIntent = Intent().apply {
            action = Intent.ACTION_VIEW
            component = ComponentName(testContext, AppLockDummyActivity::class.java)
        }

        val shortcut = ShortcutInfo.Builder(mContext, NOTIFICATION_SHORTCUT_ID)
            .setShortLabel(NOTIFICATION_SHORTCUT_ID)
            .setIcon(Icon.createWithResource(mContext, R.drawable.black))
            .setIntent(shortcutIntent)
            .setPerson(person)
            .setCategories(categorySet)
            .setLongLived(true)
            .build()

        val shortcutManager = mContext.getSystemService(ShortcutManager::class.java)
        shortcutManager.addDynamicShortcuts(listOf(shortcut))
    }

    private fun getDefaultBubbleMetadata(): Notification.BubbleMetadata {
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val intent = Intent().apply {
            action = Intent.ACTION_MAIN
            component = ComponentName(testContext, AppLockDummyActivity::class.java)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            mContext,
            /* requestCode= */ 0,
            intent,
            PendingIntent.FLAG_MUTABLE
        )

        return Notification.BubbleMetadata.Builder(
            pendingIntent,
            Icon.createWithResource(mContext, R.drawable.black)
        ).build()
    }

    private fun getMessagingStyle(): Notification.MessagingStyle {
        val notificationSender = Person.Builder()
            .setName(BUBBLE_SENDER_NAME)
            .setImportant(true)
            .build()
        val notificationReceiver = Person.Builder().setName(BUBBLE_RECEIVER_NAME).build()

        return Notification.MessagingStyle(notificationReceiver)
            .setConversationTitle(NOTIFICATION_TITLE)
            .addMessage(NOTIFICATION_TEXT, System.currentTimeMillis() - 300000, notificationSender)
    }

    private fun assertCommonRedactionFeatures(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val people = extras.getParcelableArrayList(Notification.EXTRA_PEOPLE_LIST, Person::class.java)

        assertWithMessage("Actions should be removed").that(sbn.notification.actions).isNull()
        assertWithMessage("People should be removed").that(people).isNull()
    }

    private fun assertCommonUnredactedFeatures(sbn: StatusBarNotification, hasPeople: Boolean = true) {
        val extras = sbn.notification.extras
        val actions = sbn.notification.actions

        assertWithMessage("Actions list should be present").that(actions).isNotNull()
        assertWithMessage("Actions list should not be empty").that(actions!!).asList().isNotEmpty()

        if (hasPeople) {
            val people = extras.getParcelableArrayList(Notification.EXTRA_PEOPLE_LIST, Person::class.java)
            assertWithMessage("People list should be present").that(people).isNotNull()
            assertWithMessage("People list should not be empty").that(people!!).isNotEmpty()
        }
    }

    companion object {
        private val TAG = SensitiveNotificationRedactionTest::class.java.simpleName
        private const val BUBBLE_SENDER_NAME = "Test Notification Sender"
        private const val BUBBLE_RECEIVER_NAME = "Test Notification Receiver"
        private const val OTP_CODE = "123645"
        private const val OTP_MESSAGE_BASIC = "your one time code is 123645"
        private const val PERSON_NAME = "Alan Smithee"
        private const val NOTIFICATION_ID = 42
        private const val NOTIFICATION_TITLE = "Test Notification Title"
        private const val NOTIFICATION_TEXT = "Test Notification Content"
        private const val NOTIFICATION_SUBTEXT = "Test Notification Subtext"
        private const val ACTION_TITLE = "Test Action"
        private const val SHORT_SLEEP_TIME_MS: Long = 100
        private const val NOTIFICATION_SHORTCUT_ID = "TestNotificationShortcut"
        private const val TEST_PIN = "1234"
        private const val UI_WAIT_TIMEOUT_MS: Long = 5000
    }
}
