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
package android.appwidget.cts.app

import android.R
import android.app.Activity
import android.appwidget.AppWidgetEvent
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.PowerManager
import android.provider.DeviceConfig
import android.view.View
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.DeviceConfigStateHelper
import com.android.compatibility.common.util.SystemUtil
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlin.coroutines.resume
import kotlin.test.assertNotNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StatsDeviceTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(EmptyActivity::class.java)
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager = AppWidgetManager.getInstance(context)
    private val deviceConfigStateHelper = DeviceConfigStateHelper(DeviceConfig.NAMESPACE_SYSTEMUI)
    private val host = AppWidgetHost(context, 0)

    @Before
    fun before() {
        assumeTrue(context.packageManager.hasSystemFeature(PackageManager.FEATURE_APP_WIDGETS))
        SystemUtil.runShellCommandOrThrow(
            "appwidget grantbind --package ${context.packageName} --user ${context.user.identifier}"
        )
        TestAppWidgetProvider.updates.value = 0
        host.deleteHost()
    }

    @After
    fun after() {
        SystemUtil.runShellCommandOrThrow(
            "appwidget revokebind --package ${context.packageName} " +
                "--user ${context.user.identifier}"
        )
    }

    @Test
    fun bindWidget() = runBlocking<Unit> {
        bindAppWidget()

        withTimeout(10.seconds) {
            TestAppWidgetProvider.updates.first { it > 0 }
        }
    }

    @Test
    fun reportWidgetEvent() = runBlocking<Unit> {
        setWidgetEventsReportInterval(0L)
        context.waitForInteractive()

        // Add host view to activity
        val appWidgetId = bindAppWidget()
        var hostView: AppWidgetHostView? = null
        activityRule.scenario.onActivity { activity ->
            hostView =
                host.createView(
                    context,
                    appWidgetId,
                    manager.getAppWidgetInfo(appWidgetId)
                )
            val container = FrameLayout(activity)
            container.addView(hostView)
            activity.setContentView(container)
        }
        assertNotNull(hostView)

        // Create impression of widget
        val testStartTimeMs = System.currentTimeMillis()
        hostView.waitForVisible()
        hostView.startVisibilityTracking()
        // Delay to ensure impression duration is > 0 milliseconds
        delay(10.milliseconds)
        hostView.stopVisibilityTracking()
        host.stopListening()

        // Verify widget event was created
        withTimeout(10.seconds) {
            widgetEventsForId(context, testStartTimeMs, appWidgetId).first()
        }
    }

    private fun bindAppWidget(): Int {
        host.startListening()

        val views = RemoteViews(context.packageName, R.layout.simple_gallery_item)
        views.setImageViewBitmap(
            R.id.empty,
            Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
        )
        TestAppWidgetProvider.views.set(views)

        val appWidgetId = host.allocateAppWidgetId()
        assertWithMessage("Failed to bind widget")
            .that(
                manager.bindAppWidgetIdIfAllowed(
                    appWidgetId,
                    ComponentName(context, TestAppWidgetProvider::class.java)
                )
            ).isTrue()
        return appWidgetId
    }

    /**
     * Set the interval in milliseconds at which the events will be reported into UsageStatsService.
     * A value of 0 or less means the events will be reported immediately.
     */
    private fun setWidgetEventsReportInterval(reportIntervalMs: Long) {
        val props =
            DeviceConfig.Properties.Builder(DeviceConfig.NAMESPACE_SYSTEMUI)
                .setLong("widget_events_report_interval_ms", reportIntervalMs)
                .build()
        deviceConfigStateHelper.set(props)
    }
}

/**
 * Wait for the screen to be on. Suspends while waiting for the broadcast.
 */
private suspend fun Context.waitForInteractive() {
    SystemUtil.runShellCommandOrThrow("input keyevent KEYCODE_WAKEUP")
    SystemUtil.runShellCommandOrThrow("wm dismiss-keyguard")

    val powerManager = getSystemService(PowerManager::class.java)
    if (powerManager.isInteractive) return
    val receiver = object : BroadcastReceiver() {
        var continuation: kotlinx.coroutines.CancellableContinuation<Unit>? = null

        override fun onReceive(context: Context, intent: Intent) {
            if (powerManager.isInteractive) continuation?.resume(Unit)
        }
    }
    try {
        suspendCancellableCoroutine { co ->
            receiver.continuation = co
            registerReceiver(
                receiver,
                IntentFilter(Intent.ACTION_SCREEN_ON),
                Context.RECEIVER_EXPORTED,
            )
        }
    } finally {
        unregisterReceiver(receiver)
    }
}

/**
 * Check AppWidgetManager for the next widget interaction event, polling every 250ms
 */
private fun widgetEventsForId(
    context: Context,
    testStartTimeMs: Long,
    appWidgetId: Int,
): Flow<AppWidgetEvent> =
    flow {
        val appWidgetManager = context.getSystemService(AppWidgetManager::class.java)
        while (true) {
            appWidgetManager.queryAppWidgetEvents(testStartTimeMs, System.currentTimeMillis())
                .firstOrNull { it.appWidgetId == appWidgetId }
                ?.let { emit(it) }
            delay(250.milliseconds)
        }
    }

/** Waits for the View to become visible and be laid out */
private suspend fun View.waitForVisible() {
    val result = waitUntilNonNull {
        visibility.takeIf { it == View.VISIBLE && getGlobalVisibleRect(Rect()) && isLaidOut() }
    }
    assertWithMessage("$this was not visible within 10 seconds")
        .that(result).isNotNull()
}

/**
 * Runs [test] on every draw until it returns a non-null result. Returns null if [test] does not
 * succeed before timeout.
 */
private suspend fun <T> View.waitUntilNonNull(
    timeout: Duration = 10.seconds,
    test: () -> T?,
): T? {
    test()?.let { return it }
    val channel = Channel<T>(Channel.RENDEZVOUS)
    val onDraw = ViewTreeObserver.OnDrawListener {
        test()?.let {
            assertThat(channel.trySend(it).isSuccess).isTrue()
        }
    }
    val result = try {
        viewTreeObserver.addOnDrawListener(onDraw)
        withTimeoutOrNull(timeout) { channel.receive() }
    } finally {
        viewTreeObserver.removeOnDrawListener(onDraw)
    }
    return result
}

class EmptyActivity : Activity()
