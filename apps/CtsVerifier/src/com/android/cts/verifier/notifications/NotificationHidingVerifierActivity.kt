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
package com.android.cts.verifier.notifications

import android.app.Activity
import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.app.RemoteInput
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.Icon
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.android.cts.verifier.PassFailButtons
import com.android.cts.verifier.R

class NotificationHidingVerifierActivity : PassFailButtons.Activity() {

    private lateinit var mediaProjectionServiceIntent: Intent
    private lateinit var notificationManager: NotificationManager
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private lateinit var shortcutManager: ShortcutManager
    private lateinit var title: TextView
    private lateinit var instructions: TextView
    private lateinit var buttonView: View
    private var currentTestIdx = 0
    private var numFailures = 0
    private var mediaProjection: MediaProjection? = null
    private var serviceConnection: ServiceConnection? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaProjectionServiceIntent = Intent(this, MediaProjectionService::class.java)
        notificationManager = getSystemService(NotificationManager::class.java)!!
        mediaProjectionManager = getSystemService(MediaProjectionManager::class.java)!!
        shortcutManager = getSystemService(ShortcutManager::class.java)!!
        createChannel()
        setContentView(R.layout.notif_hiding_main)
        title = requireViewById(R.id.test_title)
        instructions = requireViewById(R.id.test_instructions)
        buttonView = requireViewById(R.id.action_button_layout)
        requireViewById<Button>(R.id.test_step_passed).setOnClickListener { _ ->
            showNextTestOrSummary()
        }
        requireViewById<Button>(R.id.test_step_failed).setOnClickListener { _ ->
            numFailures += 1
            showNextTestOrSummary()
        }
        requireViewById<Button>(R.id.send_notification_button).setOnClickListener { _ ->
            tests[currentTestIdx].sendNotification()
        }
        requireViewById<Button>(R.id.start_screenshare_button).setOnClickListener { _ ->
            startScreenRecording()
        }
        val am = getSystemService(ActivityManager::class.java)!!

        var supportsBubble = false
        try {
            supportsBubble = resources.getBoolean(resources.getIdentifier(
                "config_supportsBubble", "bool", "android"))
        } catch (e: Resources.NotFoundException) {
            // Assume device does not support bubble, no need to do anything.
        }

        if (!am.isLowRamDevice && supportsBubble) {
            // Only test bubbles if the device isn't a low ram device, and supports bubbles
            tests.add(notificationContentHiddenInBubblesTest)
            val shortcut =
                ShortcutInfo.Builder(this, SHORTCUT_ID)
                        .setCategories(setOf(Notification.CATEGORY_MESSAGE))
                        .setIntent(Intent(Intent.ACTION_MAIN))
                        .setLongLived(true)
                        .setShortLabel(PERSON)
                        .build()
            shortcutManager.addDynamicShortcuts(listOf(shortcut))
        }

        setPassFailButtonClickListeners()
        passButton.isEnabled = false
        if (savedInstanceState != null) {
            restoreState(savedInstanceState)
        }
        showNextTestOrSummary(incrementCounter = false)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("currentTestIdx", currentTestIdx)
        outState.putInt("numFailures", numFailures)
    }

    private fun restoreState(savedState: Bundle) {
        currentTestIdx = savedState.getInt("currentTestIdx")
        numFailures = savedState.getInt("numFailures")
    }

    private fun createChannel() {
        val channel =
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_ID,
                NotificationManager.IMPORTANCE_HIGH
            )
        notificationManager.createNotificationChannel(channel)
    }

    private fun showNextTestOrSummary(incrementCounter: Boolean = true) {
        stopScreenRecording()
        notificationManager.cancelAll()
        if (incrementCounter) {
            currentTestIdx += 1
        }
        if (currentTestIdx >= tests.size) {
            showCompletionSummary()
        } else {
            title.setText(tests[currentTestIdx].getTestTitle())
            instructions.setText(tests[currentTestIdx].getTestInstructions())
        }
    }

    private fun showCompletionSummary() {
        shortcutManager.removeAllDynamicShortcuts()
        title.setText(R.string.notif_hiding_test)
        buttonView.visibility = View.GONE
        if (numFailures == 0) {
            instructions.setText(R.string.notif_hiding_success)
            passButton.isEnabled = true
        } else {
            instructions.text = (getString(R.string.notif_hiding_failure, numFailures, tests.size))
        }
    }

    private fun sendNotification(createBubble: Boolean) {
        val builder: Notification.Builder = getConversationNotif(SENSITIVE_TEXT)
        if (createBubble) {
            val metadata: Notification.BubbleMetadata = getBubbleMetadata()
            builder.setBubbleMetadata(metadata)
            builder.setShortcutId(SHORTCUT_ID)
        }

        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    /** Creates a [Notification.Builder] that is a conversation.  */
    private fun getConversationNotif(content: String): Notification.Builder {
        val timeContent = "$content ${System.currentTimeMillis()}"
        val context = applicationContext
        val intent = Intent(context, BubbleActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_MUTABLE
        )

        val person = Person.Builder()
                .setName(PERSON)
                .build()
        val remoteInput = RemoteInput.Builder("reply_key").setLabel("reply").build()
        val inputIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            Intent().setPackage(applicationContext.packageName),
            PendingIntent.FLAG_MUTABLE
        )
        val icon = Icon.createWithResource(
            applicationContext,
            R.drawable.ic_android
        )
        val replyAction = Notification.Action.Builder(
            icon,
            "Reply",
            inputIntent
        ).addRemoteInput(remoteInput)
                .build()

        return Notification.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_android)
                .setContentTitle("Test Notification")
                .setContentText(timeContent)
                .setColor(Color.GREEN)
                .setContentIntent(pendingIntent)
                .setActions(replyAction)
                .setStyle(
                    Notification.MessagingStyle(person)
                        .setConversationTitle("Chat")
                        .addMessage(
                            timeContent,
                            SystemClock.currentThreadTimeMillis(),
                            person
                        )
                )
    }

    /** Creates a minimally filled out [android.app.Notification.BubbleMetadata.Builder]  */
    private fun getBubbleMetadata(): Notification.BubbleMetadata {
        val context = applicationContext
        val intent = Intent(context, BubbleActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_MUTABLE
        )

        val builder = Notification.BubbleMetadata.Builder(
            pendingIntent,
                Icon.createWithResource(
                    applicationContext,
                    R.drawable.ic_android
                )
        )
        builder.setDesiredHeight(Short.MAX_VALUE.toInt())
        return builder.build()
    }

    private fun startScreenRecording() {
        startActivityForResult(
            mediaProjectionManager.createScreenCaptureIntent(),
            REQUEST_PROJECTION_CODE
        )
    }

    private fun stopScreenRecording() {
        mediaProjection?.stop()
        mediaProjection = null
        serviceConnection?.let { applicationContext.unbindService(it) }
        serviceConnection = null
        applicationContext.stopService(mediaProjectionServiceIntent)
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        if (requestCode == REQUEST_PROJECTION_CODE) {
            if (resultCode != Activity.RESULT_OK) {
                Toast.makeText(this, "Please approve screen recording", Toast.LENGTH_SHORT)
                return
            }
            applicationContext.startForegroundService(mediaProjectionServiceIntent)
            serviceConnection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data!!)
                }

                override fun onServiceDisconnected(name: ComponentName?) {}
            }
            applicationContext.bindService(
                mediaProjectionServiceIntent,
                serviceConnection!!,
                Context.BIND_AUTO_CREATE
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopScreenRecording()
        notificationManager.cancelAll()
        shortcutManager.removeAllDynamicShortcuts()
    }

    private val notificationContentHiddenInShadeTest = object : NotificationHidingTestCase() {

        override fun getTestTitle(): Int {
            return R.string.notif_hiding_shade_test
        }

        override fun getTestInstructions(): Int {
            return R.string.notif_hiding_shade_test_instructions
        }
    }

    private val notificationContentHiddenInShadePartialTest =
        object : NotificationHidingTestCase() {

        override fun getTestTitle(): Int {
            return R.string.notif_hiding_shade_partial_test
        }

        override fun getTestInstructions(): Int {
            return R.string.notif_hiding_shade_partial_test_instructions
        }
    }

    private val notificationContentHiddenInLauncherTest = object : NotificationHidingTestCase() {
        override fun getTestTitle(): Int {
            return R.string.notif_hiding_launcher_test
        }

        override fun getTestInstructions(): Int {
            return R.string.notif_hiding_launcher_test_instructions
        }
    }

    private val notificationContentHiddenInBubblesTest = object : NotificationHidingTestCase() {
        override fun getTestTitle(): Int {
            return R.string.notif_hiding_bubble_test
        }

        override fun getTestInstructions(): Int {
            return R.string.notif_hiding_bubble_test_instructions
        }

        override fun sendNotification() = sendNotification(createBubble = true)
    }

    private val tests = mutableListOf(
        notificationContentHiddenInShadeTest,
        notificationContentHiddenInShadePartialTest,
        notificationContentHiddenInLauncherTest
    )

    companion object {
        private const val TAG: String = "NotifHidingVerifier"
        private const val NOTIFICATION_CHANNEL_ID = TAG
        private const val NOTIFICATION_ID = 1
        private const val FGS_NOTIFICATION_ID = 2
        private const val SHORTCUT_ID = "shortcut"
        private const val REQUEST_PROJECTION_CODE = 1
        private const val PERSON = "Person"
        private const val FGS = "Media Projection FGS"
        private const val SENSITIVE_TEXT = "Sensitive Text"
        private const val FGS_MESSAGE = "FGS Running"
    }

    private abstract inner class NotificationHidingTestCase {
        /** The title of the test step.  */
        abstract fun getTestTitle(): Int

        /** What the tester should do & look for to verify this step was successful.  */
        abstract fun getTestInstructions(): Int

        open fun sendNotification() = sendNotification(createBubble = false)
    }

    class MediaProjectionService : Service() {

        val binder: IBinder = Binder()
        override fun onBind(intent: Intent?): IBinder? {
            return binder
        }

        override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
            val icon = Icon.createWithResource(
                applicationContext,
                R.drawable.ic_android
            )
            val notif = Notification.Builder(this, "NotifHidingVerifier")
                    .setSmallIcon(icon)
                    .setContentTitle(FGS)
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .setContentText(FGS_MESSAGE)
                    .build()
            startForeground(FGS_NOTIFICATION_ID, notif)
            return super.onStartCommand(intent, flags, startId)
        }
    }
}
