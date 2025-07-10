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

package android.appwidget.cts

import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.UiAutomation
import android.appwidget.AppWidgetEvent
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.cts.activity.EmptyActivity
import android.appwidget.cts.provider.AppWidgetProviderCallbacks
import android.appwidget.cts.provider.FirstAppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.os.Bundle
import android.os.PowerManager
import android.platform.test.annotations.AppModeFull
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.provider.DeviceConfig
import android.view.View
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.RemoteViews
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.android.compatibility.common.util.DeviceConfigStateHelper
import com.android.compatibility.common.util.ProtoUtils
import com.android.compatibility.common.util.SystemUtil
import com.android.server.job.nano.JobSchedulerServiceDumpProto
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Tests the logging of widget interaction events.
 */
@AppModeFull(reason = "Instant apps cannot provide or host app widgets")
@RequiresFlagsEnabled(android.appwidget.flags.Flags.FLAG_ENGAGEMENT_METRICS)
class WidgetEventsTest : AppWidgetTestCase() {
    @get:Rule(order = 0)
    val checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()
    @get:Rule
    val activityRule = ActivityScenarioRule(EmptyActivity::class.java)
    private val context = instrumentation.targetContext!!
    private val deviceConfigStateHelper = DeviceConfigStateHelper(DeviceConfig.NAMESPACE_SYSTEMUI)
    private val host = AppWidgetHost(context, 1234)

    @Before
    fun before() {
        host.deleteHost()
        runBlocking {
            context.waitForInteractive()
        }
        setWidgetEventsReportInterval(0L)
    }

    @After
    fun cleanUp() {
        FirstAppWidgetProvider.setCallbacks(null)
        host.deleteHost()
        revokeBindAppWidgetPermission()
        deviceConfigStateHelper.close()
    }

    @Test
    fun tap() = runBlocking<Unit> {
        val hostView = bindWidgetWithRemoteViews(
            RemoteViews(context.packageName, R.layout.remoteviews_adapter).apply {
                setOnClickPendingIntent(
                    R.id.remoteViews_empty,
                    PendingIntent.getBroadcast(context, 0, Intent(), FLAG_IMMUTABLE)
                )
                setAppWidgetEventTag(R.id.remoteViews_empty, 1)
            }
        )

        val event = hostView.onImpression {
            val item = waitForViewId(R.id.remoteViews_empty)
            assertThat(item.performClick()).isTrue()
        }

        assertThat(event.clickedIds).asList().containsExactly(1)
    }

    @Test
    fun tap_listItem() = runBlocking<Unit> {
        val hostView = bindWidgetWithRemoteViews(
            RemoteViews(context.packageName, R.layout.remoteviews_adapter).apply {
                setAppWidgetEventTag(R.id.remoteViews_list, 1)
                setPendingIntentTemplate(
                    R.id.remoteViews_list,
                    PendingIntent.getBroadcast(context, 0, Intent(), FLAG_IMMUTABLE)
                )
                setRemoteAdapter(
                    R.id.remoteViews_list,
                    RemoteViews.RemoteCollectionItems.Builder().run {
                        val item =
                            RemoteViews(context.packageName, R.layout.remoteviews_adapter_item)
                        item.setOnClickFillInIntent(R.id.item, Intent())
                        item.setAppWidgetEventTag(R.id.item, 2)
                        addItem(0, item)
                        build()
                    }
                )
            }
        )

        val event = hostView.onImpression {
            val list = waitForViewId(R.id.remoteViews_list) as ListView
            // Ensure list is laid out to populate children.
            list.layout(0, 0, 200, 200)
            val item = list.getChildAt(0).findViewById<View>(R.id.item)
            assertThat(item.performClick()).isTrue()
        }

        assertThat(event.clickedIds).asList().containsExactly(2)
    }

    @Test
    fun scroll() = runBlocking<Unit> {
        val hostView = bindWidgetWithRemoteViews(
            RemoteViews(context.packageName, R.layout.remoteviews_adapter).apply {
                setAppWidgetEventTag(R.id.remoteViews_list, 1)
                setPendingIntentTemplate(
                    R.id.remoteViews_list,
                    PendingIntent.getBroadcast(context, 0, Intent(), FLAG_IMMUTABLE)
                )
                setRemoteAdapter(
                    R.id.remoteViews_list,
                    RemoteViews.RemoteCollectionItems.Builder().run {
                        for (i in 0L until 10L) {
                            val item =
                                RemoteViews(context.packageName, R.layout.remoteviews_adapter_item)
                            item.setOnClickFillInIntent(R.id.item, Intent())
                            item.setAppWidgetEventTag(R.id.item, i.toInt())
                            addItem(i, item)
                        }
                        build()
                    }
                )
            }
        )

        val event = hostView.onImpression {
            val list = waitForViewId(R.id.remoteViews_list) as ListView
            list.fling(100)
        }

        assertThat(event.scrolledIds).asList().containsExactly(1)
    }

    @Test
    fun position_noResize() = runBlocking<Unit> {
        val hostView = bindWidgetWithRemoteViews(
            RemoteViews(context.packageName, R.layout.remoteviews_adapter)
        )

        val event = hostView.onImpression {}

        val expectedRect = Rect()
        assertThat(hostView.getGlobalVisibleRect(expectedRect)).isTrue()
        assertThat(event.position).isEqualTo(expectedRect)
    }

    @Test
    fun position_resize() = runBlocking<Unit> {
        val hostView = bindWidgetWithRemoteViews(
            RemoteViews(context.packageName, R.layout.remoteviews_adapter)
        )

        val event = hostView.onImpression {
            layoutParams = FrameLayout.LayoutParams(200, 200)
            waitForIdle()
            layoutParams = FrameLayout.LayoutParams(400, 400)
            waitForIdle()
        }

        val expectedRect = Rect()
        assertThat(hostView.getGlobalVisibleRect(expectedRect)).isTrue()
        assertThat(event.position).isEqualTo(expectedRect)
    }

    @Test
    fun position_scrollOffset() = runBlocking<Unit> {
        val hostView = bindWidgetWithRemoteViews(
            RemoteViews(context.packageName, R.layout.remoteviews_adapter)
        )

        val event = hostView.onImpression {
            (parent as View).setScrollY(100)
            layoutParams = FrameLayout.LayoutParams(200, 200)
            waitForIdle()
        }

        val expectedRect = Rect()
        assertThat(hostView.getGlobalVisibleRect(expectedRect)).isTrue()
        expectedRect.offset(/* dx= */ 0, /* dy= */ 100)
        assertThat(event.position).isEqualTo(expectedRect)
    }

    @Test
    fun impression() = runBlocking<Unit> {
        val hostView = bindWidgetWithRemoteViews(
            RemoteViews(context.packageName, R.layout.remoteviews_adapter)
        )

        val event = hostView.onImpression {
            delay(2.seconds)
        }

        assertThat(event.visibleDuration).isGreaterThan(2.seconds.toJavaDuration())
    }

    @Test
    fun periodicJobIsScheduled() = runBlocking<Unit> {
        // check for initial state with events jobs disabled
        waitForJobSchedulerState(instrumentation.uiAutomation, "no events job") { state ->
            state.registeredJobs.toList().none { job ->
                job.dump.jobInfo.service.className ==
                    "com.android.server.appwidget.ReportWidgetEventsJob"
            }
        }

        // Set the reports interval, and check that a job is scheduled.
        setWidgetEventsReportInterval(15.minutes.inWholeMilliseconds)
        waitForJobSchedulerState(instrumentation.uiAutomation, "events job present") { state ->
            state.registeredJobs.toList().any { job ->
                job.dump.jobInfo.service.className ==
                    "com.android.server.appwidget.ReportWidgetEventsJob" &&
                    job.dump.jobInfo.periodIntervalMs == 15.minutes.inWholeMilliseconds
            }
        }

        val hostView = bindWidgetWithRemoteViews(
            RemoteViews(context.packageName, R.layout.remoteviews_adapter)
        )

        // Force-run the periodic job after the event is reported to app widget service.
        fun onStop() {
            SystemUtil.runShellCommandOrThrow(
                "cmd jobscheduler run -n " +
                    "com.android.server.appwidget.AppWidgetServiceImpl.ReportWidgetEventsJob " +
                    "android 1"
            )
        }
        // onImpression will wait for the event to be present in UsageStatsService, or fail.
        hostView.onImpression(::onStop) {}
    }

    /**
     * Create a widget that displays the given [remoteViews], and return the bound
     * [AppWidgetHostView].
     */
    private fun bindWidgetWithRemoteViews(remoteViews: RemoteViews): AppWidgetHostView {
        // Set provider callbacks
        FirstAppWidgetProvider.setCallbacks(object : AppWidgetProviderCallbacks() {
            override fun onUpdate(
                context: Context,
                appWidgetManager: AppWidgetManager,
                appWidgetIds: IntArray,
            ) {
                appWidgetManager.updateAppWidget(appWidgetIds, remoteViews)
            }

            override fun onAppWidgetOptionsChanged(
                context: Context?,
                appWidgetManager: AppWidgetManager?,
                appWidgetId: Int,
                newOptions: Bundle?
            ) {
            }

            override fun onDeleted(context: Context?, appWidgetIds: IntArray?) {}

            override fun onEnabled(context: Context?) {}

            override fun onDisabled(context: Context?) {}

            override fun onRestored(
                context: Context?,
                oldWidgetIds: IntArray?,
                newWidgetIds: IntArray?
            ) {
            }
        })

        // Bind widget
        grantBindAppWidgetPermission()
        val appWidgetId = host.allocateAppWidgetId()
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val bound =
            appWidgetManager.bindAppWidgetIdIfAllowed(
                appWidgetId,
                ComponentName(context, FirstAppWidgetProvider::class.java)
            )
        assertWithMessage("Failed to bind").that(bound).isTrue()

        // Add view to activity
        var hostView: AppWidgetHostView? = null
        activityRule.scenario.onActivity { activity ->
            hostView =
                host.createView(
                    context,
                    appWidgetId,
                    appWidgetManager.getAppWidgetInfo(appWidgetId)
                )
            val container = FrameLayout(activity)
            container.addView(hostView)
            activity.setContentView(container)
        }
        return hostView!!
    }

    /**
     * Run the [block] during an impression of the AppWidgetHostView to generate a usage event.
     * Returns the first [AppWidgetEvent] that was generated since the start of the test.
     */
    private suspend fun AppWidgetHostView.onImpression(
        onStop: () -> Unit = {},
        block: suspend AppWidgetHostView.() -> Unit,
    ): AppWidgetEvent = withContext(Dispatchers.Main) {
        val testStartTimeMs = System.currentTimeMillis()
        host.startListening()
        waitForVisible()
        startVisibilityTracking()

        // Delay to ensure impression duration is > 0 milliseconds
        delay(1.milliseconds)
        block()

        stopVisibilityTracking()
        host.stopListening()
        onStop()
        val event = withTimeout(10.seconds) {
            widgetEventsForId(context, testStartTimeMs, appWidgetId).first()
        }
        assertThat(event.start.toEpochMilli()).isGreaterThan(testStartTimeMs)
        assertThat(event.end.toEpochMilli()).isLessThan(System.currentTimeMillis())
        event
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
 * Posts a runnable to this View's handler, and suspend until it runs. This is used to wait for
 * InteractionLogger::onPositionChanged to run after a layout change, since it is posted from
 * AppWidgetHostView::onLayout.
 */
private suspend fun View.waitForIdle() {
    suspendCancellableCoroutine<Unit> { co ->
        post {
            co.resume(Unit)
        }
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

private suspend fun waitForJobSchedulerState(
    uiAutomation: UiAutomation,
    debugTag: String,
    timeout: Duration = 10.seconds,
    predicate: (JobSchedulerServiceDumpProto) -> Boolean,
) {
    val result = withTimeoutOrNull(timeout) {
        do {
            val dump = ProtoUtils.getProto(
                uiAutomation,
                JobSchedulerServiceDumpProto::class.java,
                "dumpsys jobscheduler --proto",
            )
            if (predicate(dump)) break
            delay(250.milliseconds)
        } while (true)
        return@withTimeoutOrNull Unit
    }
    assertWithMessage(
        "Timed out while waiting for job scheduler to have expected state: $debugTag"
    ).that(result).isNotNull()
}

/** Waits for the View to have a child with the expected ID */
private suspend fun View.waitForViewId(id: Int): View {
    val view = waitUntilNonNull { findViewById<View>(id) }
    assertWithMessage("Did not find a view with ID $id in $this within 10 seconds")
        .that(view).isNotNull()
    return view!!
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
