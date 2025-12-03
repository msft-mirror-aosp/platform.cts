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

package com.android.cts.verifier.notifications

import android.annotation.DrawableRes
import android.annotation.StringRes
import android.app.Flags
import android.app.Notification
import android.app.Notification.SEMANTIC_STYLE_CAUTION
import android.app.Notification.SEMANTIC_STYLE_DANGER
import android.app.Notification.SEMANTIC_STYLE_INFO
import android.app.Notification.SEMANTIC_STYLE_SAFE
import android.app.Notification.SEMANTIC_STYLE_UNSPECIFIED
import android.app.Notification.createSemanticStyleAnnotation
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
import android.content.res.Resources
import android.graphics.drawable.Icon
import android.os.SystemClock
import android.provider.Settings
import android.provider.Settings.EXTRA_APP_PACKAGE
import android.text.SpannableStringBuilder
import android.text.format.DateUtils.MINUTE_IN_MILLIS
import android.view.View
import android.view.ViewGroup
import com.android.cts.verifier.R
import java.time.Instant
import java.time.temporal.ChronoUnit

open class LiveUpdateNotificationVerifierActivity : InteractiveVerifierActivity() {

    override fun getTitleResource(): Int = R.string.live_update_notification_test

    override fun getInstructionsResource(): Int = R.string.live_update_notification_test_info

    override fun createTestItems(): List<InteractiveTestCase> {
        val tests = mutableListOf(
            enableLiveUpdateStep(),
            disableLiveUpdateStep(),
            enableLiveUpdateStep(),
            assertOngoingPromotionStep(),
            verifyLiveUpdateIsExpandedStep(),
            verifyLiveUpdateTextAppearanceStep()
        )

        if (Flags.apiNotificationSemanticStyle()) {
            tests.add(verifyLiveUpdateSemanticTextAppearanceStep())
            if (Flags.apiMetricStyle()) {
                tests.add(verifyLiveUpdateSemanticMetricAppearanceStep())
            }
        }

        tests.addAll(
            listOf(
            verifyLiveUpdateRendersActionStep(),
            verifyLiveUpdateNotRenderReplyActionsStep(),
            verifyLiveUpdateNotRenderContextualActionsStep(),
            demoteLiveUpdateStep(),
            verifyDemotedLiveUpdateIsExpandableStep(),
            verifyDemotedLiveUpdateTextStylingStep(),
            verifyDemotedLiveUpdateRendersActionStep(),
            verifyDemotedLiveUpdateRendersReplayActionsStep(),
            verifyDemotedLiveUpdateRendersContextualActionsStep())
        )

        if (Flags.apiNotificationSemanticStyle()) {
            tests.add(verifyDemotedLiveUpdateSemanticTextAppearanceStep())
            if (Flags.apiMetricStyle()) {
                tests.add(verifyDemotedLiveUpdateSemanticMetricAppearanceStep())
            }
        }

        tests.addAll(
        listOf(
            enableLiveUpdateStep(),
            verifyLiveUpdatePriorityStep(),
            verifyLiveUpdateStatusBarChipStep(),
            verifyLiveUpdateStatusBarChipChronometerStep(),
            verifyLiveUpdateStatusBarChipTimerStep(),
        )
        )

        if (Flags.apiMetricStyle()) {
            tests.add(verifyStatusBarChipTextFromMetric())
            tests.add(verifyStatusBarChipChronometerFromMetric())
        }

        return tests
    }

    // region Steps
    private fun enableLiveUpdateStep() =
        EnableOrDisablePromotionStep(R.string.live_update_enable_step, expectedEnabled = true)

    private fun disableLiveUpdateStep() =
        EnableOrDisablePromotionStep(R.string.live_update_disable_step, expectedEnabled = false)

    private fun demoteLiveUpdateStep() =
        DemoteLiveUpdateNotificationStep(R.string.live_update_demote_step)

    private fun assertOngoingPromotionStep() =
        AssertOngoingPromotionStep(
            R.string.live_update_promoted_ongoing_set,
            expectedEnabled = true
        )

    private fun verifyLiveUpdateIsExpandedStep() = LiveUpdateUserVerificationBase(
        instructionsText = R.string.live_update_notification_appearance_expanded,
        onBefore = {
            mNm.notify(
                NOTIFICATION_ID,
                NotificationFactory.createLiveUpdateNotificationBuilder(mContext, CHANNEL_ID)
                    .build()
            )
        },
        preconditionCanPostPromotedNotifications = true
    )

    private fun verifyLiveUpdateTextAppearanceStep() = LiveUpdateUserVerificationBase(
        instructionsText = R.string.live_update_notification_appearance_text_styling,
        onBefore = {
            mNm.notify(
                NOTIFICATION_ID,
                NotificationFactory.createLiveUpdateNotificationBuilder(mContext, CHANNEL_ID)
                    .build()
            )
        },
        preconditionCanPostPromotedNotifications = true
    )

    private fun verifyLiveUpdateSemanticTextAppearanceStep() = LiveUpdateUserVerificationBase(
        instructionsText = R.string.live_update_notification_appearance_semantic_text_styling,
        onBefore = {
            mNm.notify(
                NOTIFICATION_ID,
                NotificationFactory.createLiveUpdateNotificationBuilderWithSemanticStyledText(
                    mContext,
                    CHANNEL_ID
                )
                    .build()
            )
        },
        preconditionCanPostPromotedNotifications = true
    )

    private fun verifyLiveUpdateSemanticMetricAppearanceStep() = LiveUpdateUserVerificationBase(
        instructionsText = R.string.live_update_notification_appearance_semantic_metric_styling,
        onBefore = {
                mNm.notify(
                    NOTIFICATION_ID,
                    NotificationFactory.createLiveUpdateNotificationBuilderWithSemanticStyledMetric(
                        mContext,
                        CHANNEL_ID
                    )
                        .build()
                )
        },
        preconditionCanPostPromotedNotifications = true
    )

    private fun verifyLiveUpdateRendersActionStep() = LiveUpdateUserVerificationBase(
        instructionsText = R.string.live_update_notification_appearance_action,
        onBefore = {
                mNm.notify(
                    NOTIFICATION_ID,
                    NotificationFactory.createLiveUpdateNotificationBuilder(mContext, CHANNEL_ID)
                        .build()
                )
        },
        preconditionCanPostPromotedNotifications = true
    )

    private fun verifyLiveUpdateNotRenderReplyActionsStep() = LiveUpdateUserVerificationBase(
        instructionsText = R.string.live_update_notification_appearance_reply_action,
        onBefore = {
                mNm.notify(
                    NOTIFICATION_ID,
                    NotificationFactory.createLiveUpdateNotificationBuilder(mContext, CHANNEL_ID)
                        .build()
                )
        },
        preconditionCanPostPromotedNotifications = true
    )

    private fun verifyLiveUpdateNotRenderContextualActionsStep() = LiveUpdateUserVerificationBase(
        instructionsText = R.string.live_update_notification_appearance_reply_contextual_action,
        onBefore = {
                mNm.notify(
                    NOTIFICATION_ID,
                    NotificationFactory.createLiveUpdateNotificationBuilder(mContext, CHANNEL_ID)
                        .build()
                )
        },
        preconditionCanPostPromotedNotifications = true
    )

    private fun verifyDemotedLiveUpdateIsExpandableStep() = LiveUpdateUserVerificationBase(
        instructionsText = R.string.live_update_notification_demoted_appearance_expander,
        onBefore = {
                mNm.notify(
                    NOTIFICATION_ID,
                    NotificationFactory.createLiveUpdateNotificationBuilder(mContext, CHANNEL_ID)
                        .build()
                )
        },
        preconditionCanPostPromotedNotifications = false
    )

    private fun verifyDemotedLiveUpdateTextStylingStep() = LiveUpdateUserVerificationBase(
        instructionsText = R.string.live_update_notification_demoted_appearance_text_styling,
        onBefore = {
                mNm.notify(
                    NOTIFICATION_ID,
                    NotificationFactory.createLiveUpdateNotificationBuilder(mContext, CHANNEL_ID)
                        .build()
                )
        },
        preconditionCanPostPromotedNotifications = false
    )

    private fun verifyDemotedLiveUpdateRendersActionStep() = LiveUpdateUserVerificationBase(
        instructionsText = R.string.live_update_notification_demoted_appearance_action,
        onBefore = {
                mNm.notify(
                    NOTIFICATION_ID,
                    NotificationFactory.createLiveUpdateNotificationBuilder(mContext, CHANNEL_ID)
                        .build()
                )
        },
        preconditionCanPostPromotedNotifications = false
    )

    private fun verifyDemotedLiveUpdateRendersReplayActionsStep() = LiveUpdateUserVerificationBase(
        instructionsText = R.string.live_update_notification_demoted_appearance_reply_action,
        onBefore = {
                mNm.notify(
                    NOTIFICATION_ID,
                    NotificationFactory.createLiveUpdateNotificationBuilder(mContext, CHANNEL_ID)
                        .build()
                )
        },
        preconditionCanPostPromotedNotifications = false
    )

    private fun verifyDemotedLiveUpdateRendersContextualActionsStep(
    ) = LiveUpdateUserVerificationBase(
        instructionsText = R.string.live_update_notification_demoted_appearance_contextual_action,
        onBefore = {
                mNm.notify(
                    NOTIFICATION_ID,
                    NotificationFactory.createLiveUpdateNotificationBuilder(mContext, CHANNEL_ID)
                        .build()
                )
        },
        preconditionCanPostPromotedNotifications = false
    )

    private fun verifyDemotedLiveUpdateSemanticTextAppearanceStep() = LiveUpdateUserVerificationBase(
        instructionsText = R.string.live_update_notification_demoted_appearance_semantic_text_styling,
        onBefore = {
            mNm.notify(
                NOTIFICATION_ID,
                NotificationFactory.createLiveUpdateNotificationBuilderWithSemanticStyledText(
                    mContext,
                    CHANNEL_ID
                )
                    .build()
            )
        },
        preconditionCanPostPromotedNotifications = false
    )

    private fun verifyDemotedLiveUpdateSemanticMetricAppearanceStep() = LiveUpdateUserVerificationBase(
        instructionsText = R.string.live_update_notification_demoted_appearance_semantic_metric_styling,
        onBefore = {
            mNm.notify(
                NOTIFICATION_ID,
                NotificationFactory.createLiveUpdateNotificationBuilderWithSemanticStyledMetric(
                    mContext,
                    CHANNEL_ID
                )
                    .build()
            )
        },
        preconditionCanPostPromotedNotifications = false
    )

    private fun verifyLiveUpdatePriorityStep() = LiveUpdateUserVerificationBase(
        instructionsText = R.string.live_update_notification_priority,
        onBefore = {
            val liveUpdate =
                NotificationFactory.createLiveUpdateNotificationBuilder(mContext, CHANNEL_ID)
                    .build()

            val nonLiveUpdate = Notification.Builder(mContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_charlie)
                .setContentTitle("Not Live Update")
                .setContentText("This notification is not a Live Update")
                .build()

            mNm.notify(NOTIFICATION_ID, liveUpdate)
            mNm.notify(NOTIFICATION_ID + 1, nonLiveUpdate)
        }
    )

    private fun verifyLiveUpdateStatusBarChipStep() = LiveUpdateUserVerificationBase(
        instructionsText = R.string.live_update_notification_status_bar_chip,
        onBefore = {
            val notification =
                NotificationFactory.createLiveUpdateNotificationBuilder(mContext, CHANNEL_ID)
                    .setContentTitle("Status Bar Chip")
                    .setShortCriticalText("test")
                    .setContentIntent(getLaunchPendingIntent())
                    .build()
            mNm.notify(NOTIFICATION_TAG, NOTIFICATION_ID, notification)
        }
    )

    private fun verifyLiveUpdateStatusBarChipTimerStep() = LiveUpdateUserVerificationBase(
        instructionsText = R.string.live_update_notification_status_bar_chip_timer,
        onBefore = {
            val timer =
                NotificationFactory.createLiveUpdateNotificationBuilder(mContext, CHANNEL_ID)
                    .setContentTitle("Status Bar Chip Timer")
                    .setShowWhen(true)
                    .setWhen(Instant.now().plus(3, ChronoUnit.MINUTES).toEpochMilli())
                    .setContentIntent(getLaunchPendingIntent())
                    .build()
            mNm.notify(NOTIFICATION_ID, timer)
        }
    )

    private fun verifyLiveUpdateStatusBarChipChronometerStep() = LiveUpdateUserVerificationBase(
        instructionsText = R.string.live_update_notification_status_bar_chip_chronometer,
        onBefore = {
            val chronometer =
                NotificationFactory.createLiveUpdateNotificationBuilder(mContext, CHANNEL_ID)
                    .setContentTitle("Status Bar Chip Chronometer")
                    .setUsesChronometer(true)
                    .setContentIntent(getLaunchPendingIntent())
                    .build()
            mNm.notify(NOTIFICATION_ID, chronometer)
        }
    )

    private fun verifyStatusBarChipTextFromMetric() = LiveUpdateUserVerificationBase(
        // Same instructions as verifyLiveUpdateStatusBarChipStep. Chip displays the same text,
        // even though the data comes from the Metric rather than shortCriticalText.
        instructionsText = R.string.live_update_notification_status_bar_chip,
        onBefore = {
            val notification =
                NotificationFactory.createLiveUpdateNotificationBuilder(mContext, CHANNEL_ID)
                    .setContentTitle("Status Bar Chip (text from metric)")
                    .setStyle(
                        Notification.MetricStyle()
                            .addMetric(
                                Notification.Metric(
                                    Notification.Metric.FixedText("test"),
                                    "Text"
                                )
                            )
                    )
                    .build()
            mNm.notify(NOTIFICATION_ID, notification)
        }
    )

    private fun verifyStatusBarChipChronometerFromMetric() = LiveUpdateUserVerificationBase(
        // Same instructions as verifyLiveUpdateStatusBarChipTimerStep. Chip displays the same
        // stopwatch-chronometer, even though the data comes from the Metric rather than "when".
        instructionsText = R.string.live_update_notification_status_bar_chip_chronometer,
        onBefore = {
            val notification =
                NotificationFactory.createLiveUpdateNotificationBuilder(mContext, CHANNEL_ID)
                    .setContentTitle("Status Bar Chip (chronometer from metric)")
                    .setStyle(
                        Notification.MetricStyle()
                            .addMetric(
                                Notification.Metric(
                                    Notification.Metric.TimeDifference.forStopwatch(
                                        SystemClock.elapsedRealtime(),
                                        Notification.Metric.TimeDifference.FORMAT_CHRONOMETER
                                    ),
                                    "Stopwatch"
                                )
                            )
                    )
                    // Set a different when (which, if used, would be counting DOWN rather than up)
                    // to verify that the chip is showing the Metric data.
                    .setShowWhen(true)
                    .setWhen(Instant.now().plus(3, ChronoUnit.MINUTES).toEpochMilli())
                    .build()
            mNm.notify(NOTIFICATION_ID, notification)
        }
    )

    private fun getLaunchPendingIntent(): PendingIntent {
        val launchIntent =
            Intent(applicationContext, LiveUpdateNotificationVerifierActivity::class.java)
        return PendingIntent.getActivity(
            applicationContext,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
    }
    // endregion

    private object NotificationFactory {

        fun createLiveUpdateNotificationBuilder(
            context: Context,
            channelId: String
        ): Notification.Builder {
            return Notification.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_stat_charlie)
                .setContentTitle("Hello World!")
                .setContentText(context.getText(R.string.live_update_notification_styled_text))
                .setOngoing(true)
                .setRequestPromotedOngoing(true)
                .setStyle(Notification.ProgressStyle().setProgress(40))
                .addAction(createContextualAction(context))
                .addAction(createRemoteInputAction(context))
                .addAction(createNormalAction(context))
        }

        fun createLiveUpdateNotificationBuilderWithSemanticStyledText(
            context: Context,
            channelId: String
        ): Notification.Builder {
            val ssb = SpannableStringBuilder()
                .append("Colors: ")
                .append("NONE", createSemanticStyleAnnotation(SEMANTIC_STYLE_UNSPECIFIED), 0)
                .append(", ")
                .append("INFO", createSemanticStyleAnnotation(SEMANTIC_STYLE_INFO), 0)
                .append(", ")
                .append("SAFE", createSemanticStyleAnnotation(SEMANTIC_STYLE_SAFE), 0)
                .append(", ")
                .append("CAUTION", createSemanticStyleAnnotation(SEMANTIC_STYLE_CAUTION), 0)
                .append(", ")
                .append("DANGER", createSemanticStyleAnnotation(SEMANTIC_STYLE_DANGER), 0)

            return Notification.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_stat_charlie)
                .setContentTitle("Hello World!")
                .setContentText(ssb)
                .setOngoing(true)
                .setRequestPromotedOngoing(true)
        }

        fun createLiveUpdateNotificationBuilderWithSemanticStyledMetric(
            context: Context,
            channelId: String
        ): Notification.Builder {
            return Notification.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_stat_charlie)
                .setContentTitle("Hello World!")
                .setOngoing(true)
                .setRequestPromotedOngoing(true)
                .setStyle(Notification.MetricStyle().addMetric(
                    Notification.Metric(
                        Notification.Metric.TimeDifference.forTimer(
                            SystemClock.elapsedRealtime() + 3 * MINUTE_IN_MILLIS,
                            Notification.Metric.TimeDifference.FORMAT_CHRONOMETER
                        ),
                        "Countdown",
                        SEMANTIC_STYLE_DANGER
                        )
                    ))
        }

        private fun createRemoteInputAction(context: Context): Notification.Action {
            val remoteInput = RemoteInput.Builder("AnyKey").setLabel("label").build()
            val replyIntent = Intent(context, LiveUpdateNoOpReplyReceiver::class.java)
            val replyPendingIntent = PendingIntent.getBroadcast(
                context,
                1000,
                replyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            return Notification.Action.Builder(null, "Reply", replyPendingIntent)
                .addRemoteInput(remoteInput)
                .setAllowGeneratedReplies(false)
                .build()
        }

        private fun createContextualAction(context: Context): Notification.Action {
            val intent = Intent(context, BroadcastReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_MUTABLE
            )
            return Notification.Action.Builder(
                Icon.createWithResource(context, R.drawable.transit_s_icon),
                "Share",
                pendingIntent
            ).setContextual(true).setAllowGeneratedReplies(false).build()
        }

        private fun createNormalAction(context: Context): Notification.Action {
            val intent = Intent(context, BroadcastReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_MUTABLE
            )
            return Notification.Action.Builder(
                Icon.createWithResource(context, R.drawable.transit_e_icon),
                "Open",
                pendingIntent
            ).build()
        }
    }

    protected open inner class LiveUpdateUserVerificationBase(
        @StringRes private val instructionsText: Int,
        @DrawableRes private val instructionsImage: Int = Resources.ID_NULL,
        private val onBefore: LiveUpdateUserVerificationBase.() -> Unit,
        private val preconditionCanPostPromotedNotifications: Boolean? = null
    ) : InteractiveTestCase() {
        protected var view: View? = null

        override fun inflate(parent: ViewGroup?): View? {
            view = createPassFailItem(parent, instructionsText, instructionsImage)
            setButtonsEnabled(view, false)
            return view
        }

        override fun setUp() {
            mNm.createNotificationChannel(CHANNEL)
            if (preconditionCanPostPromotedNotifications != null) {
                if (preconditionCanPostPromotedNotifications && !mNm.canPostPromotedNotifications()) {
                    logFail(
                        "CTS Verifier app should be able to post a live update to verify ${javaClass.simpleName}"
                    )
                    status = FAIL
                    next()
                    return
                } else if (!preconditionCanPostPromotedNotifications && mNm.canPostPromotedNotifications()) {
                    logFail(
                        "CTS Verifier app should not be able to post a live update to verify ${javaClass.simpleName}"
                    )
                    status = FAIL
                    next()
                    return
                }
            }

            onBefore()
            setButtonsEnabled(view, false)
            super.setUp()
        }

        override fun autoStart(): Boolean {
            return true
        }

        protected override fun test() {
            setButtonsEnabled(view, true)
            status = WAIT_FOR_USER
            next()
        }

        override fun tearDown() {
            cleanUpNotifications()
            delay()
            super.tearDown()
        }
    }

    class LiveUpdateNoOpReplyReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = Unit
    }

    private inner class EnableOrDisablePromotionStep(
        @StringRes private val instructionRes: Int,
        private val expectedEnabled: Boolean
    ) : InteractiveTestCase() {
        private lateinit var view: View

        override fun inflate(parent: ViewGroup): View {
            view = createUserItem(parent, R.string.live_update_open_settings, instructionRes)
            setButtonsEnabled(view, false)
            return view
        }

        override fun setUp() {
            status = READY
            setButtonsEnabled(view, true)
            next()
        }

        override fun autoStart(): Boolean = true

        override fun test() {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS)
            if (intent.resolveActivity(mPackageManager) == null) {
                logFail("no settings activity")
                status = FAIL
            } else if (mNm.canPostPromotedNotifications() == expectedEnabled) {
                status = PASS
            } else {
                status = WAIT_FOR_USER
            }
            next()
        }

        override fun getIntent(): Intent {
            return Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS)
                .addFlags(FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra(EXTRA_APP_PACKAGE, mContext.packageName)
        }
    }

    private inner class DemoteLiveUpdateNotificationStep(
        @StringRes private val instructionRes: Int,
    ) : InteractiveTestCase() {

        private lateinit var view: View
        override fun inflate(parent: ViewGroup): View {
            view = createAutoItem(parent, instructionRes)
            return view
        }

        override fun setUp() {
            mNm.createNotificationChannel(CHANNEL)
            mNm.notify(
                NOTIFICATION_TAG,
                NOTIFICATION_ID,
                NotificationFactory.createLiveUpdateNotificationBuilder(mContext, CHANNEL_ID)
                    .build()
            )
            super.setUp()
        }

        override fun test() {
            val notification = getLiveUpdateNotification()
            if (notification == null) {
                logFail("Posted Notification Not Found")
                status = FAIL
            } else {
                val isDemoted =
                    (notification.flags and Notification.FLAG_PROMOTED_ONGOING) != 0
                if (!isDemoted) {
                    status = PASS
                    next()
                } else {
                    status = RETEST
                    delay()
                }
            }
        }

        override fun tearDown() {
            cleanUpNotifications()
            delay()
            super.tearDown()
        }
    }

    private inner class AssertOngoingPromotionStep(
        @StringRes private val instructionRes: Int,
        private val expectedEnabled: Boolean
    ) : InteractiveTestCase() {
        private lateinit var view: View

        override fun inflate(parent: ViewGroup): View {
            view = createAutoItem(parent, instructionRes)
            return view
        }

        override fun setUp() {
            mNm.createNotificationChannel(CHANNEL)
            val notification =
                NotificationFactory.createLiveUpdateNotificationBuilder(mContext, CHANNEL_ID)
                    .setContentTitle("Updating Your App")
                    .setContentText("40% complete")
                    .build()
            mNm.notify(NOTIFICATION_TAG, NOTIFICATION_ID, notification)
            super.setUp()
        }

        override fun test() {
            val notification = getLiveUpdateNotification()
            if (notification == null) {
                logFail("Posted Notification Not Found")
                status = FAIL
            } else {
                val isPromotedOngoingFlagSet =
                    (notification.flags and Notification.FLAG_PROMOTED_ONGOING) != 0
                if (isPromotedOngoingFlagSet == expectedEnabled) {
                    status = PASS
                    next()
                } else {
                    status = RETEST
                    delay()
                }
            }
        }

        override fun tearDown() {
            cleanUpNotifications()
            delay()
            super.tearDown()
        }

        override fun autoStart(): Boolean = true
    }

    private fun cleanUpNotifications() {
        mNm.cancelAll()
        mNm.deleteNotificationChannel(CHANNEL_ID)
    }

    private fun getLiveUpdateNotification(): Notification? {
        return mNm.activeNotifications.firstOrNull { NOTIFICATION_TAG == it.tag }?.notification
    }

    private companion object {
        private const val CHANNEL_ID = "LiveUpdatesTest"
        private const val NOTIFICATION_TAG = "PromotedNotifTag"

        private val CHANNEL = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_ID,
            NotificationManager.IMPORTANCE_DEFAULT
        )
    }
}
