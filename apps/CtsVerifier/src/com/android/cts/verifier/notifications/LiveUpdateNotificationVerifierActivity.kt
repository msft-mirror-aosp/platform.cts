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
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.drawable.Icon
import android.view.View
import android.view.ViewGroup
import com.android.cts.verifier.R

open class LiveUpdateNotificationVerifierActivity : InteractiveVerifierActivity() {

    override fun getTitleResource(): Int = R.string.live_update_notification_test

    override fun getInstructionsResource(): Int = R.string.live_update_notification_test_info

    override fun createTestItems(): List<InteractiveTestCase> = listOf(
        liveUpdatePriorityTestCase(),
        liveUpdateAppearanceTestCase(),
        liveUpdateStatusBarChipTestCase(),
        liveUpdateDemotionOffAndOnTestCase(),
        liveUpdateRemoteInputTestCase(),
        liveUpdateContextualActionsTestCase()
    )

    private fun liveUpdatePriorityTestCase() = LiveUpdateTestCase(
        channelId = "LUNVA.LiveUpdatePriorityTestCase",
        instructionsText = R.string.live_update_notification_priority,
        createLiveUpdate = {
            Notification.Builder(mContext, channelId)
                .setSmallIcon(R.drawable.ic_stat_charlie)
                .setContentTitle("Updating Your App")
                .setContentText("40% complete")
                .setOngoing(true)
                .setRequestPromotedOngoing(true)
                .setStyle(
                    Notification.ProgressStyle().setProgress(40)
                )
                .build()
        },
        beforeCreateLiveUpdate = {
            val notification =
                Notification.Builder(mContext, channelId)
                    .setSmallIcon(R.drawable.ic_stat_charlie)
                    .setContentTitle("Not Live Update")
                    .setContentText("This notification is not a Live Update")
                    .build()
            mNm.notify(NOTIFICATION_ID + 1, notification)
        }
    )

    private fun liveUpdateAppearanceTestCase() = LiveUpdateTestCase(
        channelId = "LUNVA.LiveUpdateAppearanceTestCase",
        instructionsText = R.string.live_update_notification_appearance,
        createLiveUpdate = {
            Notification.Builder(mContext, channelId)
                .setSmallIcon(R.drawable.ic_stat_charlie)
                .setContentTitle("Styled Texts")
                .setContentText(getText(R.string.live_update_notification_styled_text))
                .setOngoing(true)
                .setRequestPromotedOngoing(true)
                .setStyle(
                    Notification.ProgressStyle().setProgress(40)
                )
                .build()
        }
    )

    private fun liveUpdateStatusBarChipTestCase() = LiveUpdateTestCase(
        channelId = "LUNVA.LiveUpdateStatusBarChipTestCase",
        instructionsText = R.string.live_update_notification_status_bar_chip,
        createLiveUpdate = {
            val launchIntent = PendingIntent.getActivity(
                /* context = */
                applicationContext,
                /* requestCode = */
                0,
                /* intent = */
                Intent(
                    applicationContext,
                LiveUpdateNotificationVerifierActivity::class.java
                ),
                /* flags = */
                PendingIntent.FLAG_IMMUTABLE
            )
            Notification.Builder(mContext, channelId)
                .setSmallIcon(R.drawable.ic_stat_charlie)
                .setContentTitle("Status Bar Chip")
                .setContentText("This notification is a Live Update.")
                .setShortCriticalText("test")
                .setOngoing(true)
                .setContentIntent(launchIntent)
                .setRequestPromotedOngoing(true)
                .build()
        }
    )

    private fun liveUpdateDemotionOffAndOnTestCase() = LiveUpdateTestCase(
        channelId = "LUNVA.LiveUpdateDemotionOffAndOnTestCase",
        instructionsText = R.string.live_update_notification_demotion_off_and_on,
        createLiveUpdate = {
            Notification.Builder(mContext, channelId)
                .setSmallIcon(R.drawable.ic_stat_charlie)
                .setContentTitle("Demotion Menu")
                .setContentText("This notification is a Live Update.")
                .setOngoing(true)
                .setRequestPromotedOngoing(true)
                .build()
        }
    )

    private fun liveUpdateRemoteInputTestCase() = LiveUpdateTestCase(
        channelId = "LUNVA.LiveUpdateRemoteInputTestCase",
        instructionsText = R.string.live_update_notification_remote_input,
        createLiveUpdate = {
            val remoteInput: RemoteInput = RemoteInput.Builder("AnyKey")
                .setLabel("label")
                .build()

            val replyIntent = Intent(mContext, LiveUpdateNoOpReplyReceiver::class.java)

            val replyPendingIntent: PendingIntent = PendingIntent.getBroadcast(
                mContext,
                1000,
                replyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

            val remoteInputAction: Notification.Action = Notification.Action.Builder(
                null,
                "Reply",
                replyPendingIntent
            ).addRemoteInput(remoteInput)
                .setAllowGeneratedReplies(false).build()

            Notification.Builder(mContext, channelId)
                .setSmallIcon(R.drawable.ic_stat_charlie)
                .setContentTitle("Remote Input")
                .setContentText("This notification is a Live Update.")
                .setOngoing(true)
                .setRequestPromotedOngoing(true)
                .addAction(remoteInputAction)
                .build()
        }
    )

    private fun liveUpdateContextualActionsTestCase() = LiveUpdateTestCase(
        channelId = "LUNVA.LiveUpdateContextualActionsTestCase",
        instructionsText = R.string.live_update_notification_contextual_action,
        createLiveUpdate = {
            val contextualAction: Notification.Action = Notification.Action.Builder(
                Icon.createWithResource(mContext, R.drawable.transit_e_icon),
                "Ok",
                PendingIntent.getBroadcast(
                    mContext,
                    0,
                    Intent(mContext, BroadcastReceiver::class.java),
                    PendingIntent.FLAG_MUTABLE
                ),
            ).setContextual(true).setAllowGeneratedReplies(false).build()

            Notification.Builder(mContext, channelId)
                .setSmallIcon(R.drawable.ic_stat_charlie)
                .setContentTitle("Contextual Actions")
                .setContentText("This notification is a Live Update.")
                .setOngoing(true)
                .setRequestPromotedOngoing(true)
                .addAction(contextualAction)
                .build()
        }
    )

     protected inner class LiveUpdateTestCase(
        val channelId: String,
        @StringRes private val instructionsText: Int,
        private val createLiveUpdate: LiveUpdateTestCase.() -> Notification,
        @DrawableRes private val instructionsImage: Int = Resources.ID_NULL,
        private val beforeCreateLiveUpdate: (LiveUpdateTestCase.() -> Unit)? = null
    ) : InteractiveTestCase() {
        private var mView: View? = null

        override fun inflate(parent: ViewGroup?): View? {
            mView = createPassFailItem(parent, instructionsText, instructionsImage)
            setButtonsEnabled(mView, false)
            return mView
        }

        protected override fun setUp() {
            super.setUp()
            mNm.createNotificationChannel(this.getChannel())
            beforeCreateLiveUpdate?.invoke(this@LiveUpdateTestCase)
            val notification = this.createLiveUpdate()
            require(
                notification.hasPromotableCharacteristics()
            ) {
                "Invalid notification: Missing promotable characteristics."
            }
            mNm.notify(NOTIFICATION_ID, notification)
            setButtonsEnabled(mView, true)
            status = READY
            next()
        }

        override fun autoStart(): Boolean {
            return true
        }

        protected override fun test() {
            setButtonsEnabled(mView, true)
            // In all tests we post a notification and ask the user to confirm that its appearance
            // matches expectations.
            status = WAIT_FOR_USER
            next()
        }

        protected override fun tearDown() {
            mNm.cancelAll()
            mNm.deleteNotificationChannel(this.getChannel().id)
            delay()
            super.tearDown()
        }

        protected fun getChannel(): NotificationChannel = NotificationChannel(
            channelId,
            channelId,
            NotificationManager.IMPORTANCE_DEFAULT
        )
    }

    class LiveUpdateNoOpReplyReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = Unit
    }
}
