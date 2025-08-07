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

package android.app.appops.cts

import android.app.AppOpsManager
import android.app.AppOpsManager.HISTORY_FLAGS_ALL
import android.app.AppOpsManager.HistoricalOps
import android.app.AppOpsManager.HistoricalOpsRequest
import android.app.AppOpsManager.MODE_ALLOWED
import android.app.AppOpsManager.MODE_IGNORED
import android.app.AppOpsManager.OPSTR_RESERVED_FOR_TESTING
import android.app.AppOpsManager.OPSTR_WAKE_LOCK
import android.app.AppOpsManager.OP_FLAGS_ALL
import android.content.Context
import android.os.Process
import android.permission.flags.Flags.FLAG_ENABLE_ALL_SQLITE_APPOPS_ACCESSES
import android.platform.test.annotations.AppModeFull
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import java.util.function.Consumer
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * This test verifies the correct recording of app ops history in SQLite databases.
 * History is segregated into two databases based on the app op's sensitivity and
 * aggregation time window:
 * 1. Short-interval database: Stores highly sensitive app ops like microphone, camera, and
 * location, aggregating events over shorter time windows.
 * 2. Long-interval database: Stores all other app ops, with events aggregated over longer
 * time windows.
 */
@RunWith(AndroidJUnit4::class)
@AppModeFull(reason = "Instant apps can't query package info")
@RequiresFlagsEnabled(FLAG_ENABLE_ALL_SQLITE_APPOPS_ACCESSES)
class HistoricalRegistryTest {
    companion object {
        private const val APK_PATH = "/data/local/tmp/cts/appops/AppForHistoricalRegistryTest.apk"
        private const val ONE_MINUTE_MILLIS: Long = 60 * 1000
        private const val SHORT_INTERVAL_QUANTIZATION_MILLIS: Long = ONE_MINUTE_MILLIS
        private const val LONG_INTERVAL_QUANTIZATION_MILLIS: Long = 15 * ONE_MINUTE_MILLIS
        private const val SHORT_INTERVAL_OP = OPSTR_RESERVED_FOR_TESTING
        private const val LONG_INTERVAL_OP = OPSTR_WAKE_LOCK
    }
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val appOpsManager = context.getSystemService(AppOpsManager::class.java)!!
    private var testUid = Process.INVALID_UID
    private val testPackageName = "android.app.appops.cts.appforhisotricalregistrytest"
    private val packageManager = context.packageManager

    @get:Rule
    val mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @Before
    fun setUpTest() {
        wakeScreenUp()
        runWithShellPermissionIdentity {
            appOpsManager.clearHistory()
        }
        installApk(APK_PATH)
        testUid = packageManager.getPackageUid(testPackageName, 0)
    }

    @After
    fun tearDownTest() {
        runWithShellPermissionIdentity {
            appOpsManager.clearHistory()
        }
        runCommand("pm uninstall --user ${context.userId} $testPackageName")
    }

    @Test
    fun appOpAccessesAreRecordedInBothDatabase() {
        ensureNoteOpBatchingDoesNotAffectTest()
        val opNames = listOf(SHORT_INTERVAL_OP, LONG_INTERVAL_OP)
        ensureAppOpsModeAllowed(testUid, testPackageName, opNames)
        noteOpWithShellIdentity(SHORT_INTERVAL_OP, testUid, testPackageName)
        noteOpWithShellIdentity(LONG_INTERVAL_OP, testUid, testPackageName)

        val historicalOps = getHistoricalOps(historyFlag = HISTORY_FLAGS_ALL, opNames = opNames)
        val discreteOps = convertHistoricalOpsToDiscreteAccessEvents(historicalOps)
        assertThat(discreteOps.size).isEqualTo(1)
        val discreteOp = discreteOps.first()
        assertThat(discreteOp.opName).isEqualTo(SHORT_INTERVAL_OP)
        assertThat(discreteOp.packageName).isEqualTo(testPackageName)
        assertThat(discreteOp.uid).isEqualTo(testUid)

        val aggregatedOps = convertHistoricalOpsToAggregatedAccessEvents(historicalOps)
        assertThat(aggregatedOps.size).isEqualTo(2)
        aggregatedOps.forEach { accessEvent ->
            assertThat(accessEvent.opName).isAnyOf(SHORT_INTERVAL_OP, LONG_INTERVAL_OP)
            assertThat(accessEvent.totalDurationMillis).isEqualTo(0)
            assertThat(accessEvent.rejectCount).isEqualTo(0)
            assertWithMessage(accessEvent.toString()).that(accessEvent.accessCount).isEqualTo(1)
        }
    }

    @Test
    fun appOpAccessesArePersistedAcrossReboot() {
        ensureNoteOpBatchingDoesNotAffectTest()
        val opNames = listOf(SHORT_INTERVAL_OP, LONG_INTERVAL_OP)
        ensureAppOpsModeAllowed(testUid, testPackageName, opNames)
        noteOpWithShellIdentity(SHORT_INTERVAL_OP, testUid, testPackageName, null)
        noteOpWithShellIdentity(LONG_INTERVAL_OP, testUid, testPackageName, null)
        runWithShellPermissionIdentity {
            appOpsManager.rebootHistory(100)
        }

        val historicalOps = getHistoricalOps(historyFlag = HISTORY_FLAGS_ALL, opNames = opNames)
        val discreteOps = convertHistoricalOpsToDiscreteAccessEvents(historicalOps)
        assertThat(discreteOps.size).isEqualTo(1)
        assertThat(discreteOps.first().opName).isEqualTo(SHORT_INTERVAL_OP)

        val aggregatedOps = convertHistoricalOpsToAggregatedAccessEvents(historicalOps)
        assertThat(aggregatedOps.size).isEqualTo(2)
        aggregatedOps.forEach { accessEvent ->
            assertThat(accessEvent.opName).isAnyOf(SHORT_INTERVAL_OP, LONG_INTERVAL_OP)
            assertThat(accessEvent.totalDurationMillis).isEqualTo(0)
            assertThat(accessEvent.rejectCount).isEqualTo(0)
            assertWithMessage(accessEvent.toString()).that(accessEvent.accessCount).isEqualTo(1)
        }
    }

    @Ignore("b/420724585")
    @Test
    fun ensureAppOpAccessesCountsForAllowedOp() {
        waitUntilSafelyInTimeQuant(SHORT_INTERVAL_QUANTIZATION_MILLIS, 5 * 1000)
        val opNames = listOf(SHORT_INTERVAL_OP)
        ensureAppOpsModeAllowed(testUid, testPackageName, opNames)
        for (i in 0 until 10) {
            noteOpWithShellIdentity(SHORT_INTERVAL_OP, testUid, testPackageName, null)
        }
        Thread.sleep(2000) // wait for batching to be completed.

        val historicalOps =
            getHistoricalOps(historyFlag = HISTORY_FLAGS_ALL, opNames = opNames)
        val appOps = convertHistoricalOpsToAggregatedAccessEvents(historicalOps)
        assertThat(appOps.size).isEqualTo(1)
        val accessEvent = appOps.first()
        assertThat(accessEvent.rejectCount).isEqualTo(0)
        assertThat(accessEvent.totalDurationMillis).isEqualTo(0)
        assertWithMessage(accessEvent.toString()).that(accessEvent.accessCount)
            .isEqualTo(10)
    }

    @Ignore("b/420724585")
    @Test
    fun ensureAppOpAccessesCountsForIgnoredOp() {
        waitUntilSafelyInTimeQuant(SHORT_INTERVAL_QUANTIZATION_MILLIS, 5 * 1000)
        runWithShellPermissionIdentity {
            appOpsManager.setUidMode(SHORT_INTERVAL_OP, testUid, MODE_IGNORED)
        }
        eventually {
            assertWithMessage("$SHORT_INTERVAL_OP mode should be MODE_IGNORED ")
                .that(appOpsManager.checkOp(SHORT_INTERVAL_OP, testUid, testPackageName))
                .isEqualTo(MODE_IGNORED)
        }
        for (i in 0 until 10) {
            noteOpWithShellIdentity(SHORT_INTERVAL_OP, testUid, testPackageName, null)
        }
        Thread.sleep(2000) // wait for batching to be completed.

        val historicalOps =
            getHistoricalOps(historyFlag = HISTORY_FLAGS_ALL, opNames = listOf(SHORT_INTERVAL_OP))
        val appOps = convertHistoricalOpsToAggregatedAccessEvents(historicalOps)
        assertThat(appOps.size).isEqualTo(1)
        val accessEvent = appOps.first()
        assertThat(accessEvent.accessCount).isEqualTo(0)
        assertThat(accessEvent.rejectCount).isEqualTo(10)
        assertThat(accessEvent.totalDurationMillis).isEqualTo(0)
    }

    @Test
    fun ensureAppOpAccessesDurationForAllowedOp() {
        waitUntilSafelyInTimeQuant(SHORT_INTERVAL_QUANTIZATION_MILLIS, 5 * 1000)
        val opNames = listOf(SHORT_INTERVAL_OP)
        ensureAppOpsModeAllowed(testUid, testPackageName, opNames)
        runWithShellPermissionIdentity {
            for (i in 0 until 10) {
                appOpsManager.startOp(SHORT_INTERVAL_OP, testUid, testPackageName, null, null)
                Thread.sleep(200)
                appOpsManager.finishOp(SHORT_INTERVAL_OP, testUid, testPackageName, null)
            }
        }

        val historicalOps =
            getHistoricalOps(historyFlag = HISTORY_FLAGS_ALL, opNames = opNames)
        val appOps = convertHistoricalOpsToAggregatedAccessEvents(historicalOps)
        assertThat(appOps.size).isEqualTo(1)
        val accessEvent = appOps.first()
        assertThat(accessEvent.accessCount).isEqualTo(10)
        assertThat(accessEvent.rejectCount).isEqualTo(0)
        val toleranceMillis = 200 // Actual duration would be a little more than 2 seconds
        assertThat(accessEvent.totalDurationMillis).isAtLeast(2000)
        assertThat(accessEvent.totalDurationMillis).isAtMost(2000 + toleranceMillis)
    }

    @Test
    fun getHistoricalOpsInDisabledMode() {
        setAppOpHistoryParameters("mode=HISTORICAL_MODE_DISABLED")
        try {
            waitUntilSafelyInTimeQuant(SHORT_INTERVAL_QUANTIZATION_MILLIS, 5 * 1000)
            val opNames = listOf(SHORT_INTERVAL_OP)
            ensureAppOpsModeAllowed(testUid, testPackageName, opNames)
            runWithShellPermissionIdentity {
                appOpsManager.startOp(SHORT_INTERVAL_OP, testUid, testPackageName, null, null)
                Thread.sleep(50) // duration for the operation
                appOpsManager.finishOp(SHORT_INTERVAL_OP, testUid, testPackageName, null)
            }

            val historicalOps =
                getHistoricalOps(historyFlag = HISTORY_FLAGS_ALL, opNames = opNames)
            val appOps = convertHistoricalOpsToAggregatedAccessEvents(historicalOps)
            assertThat(appOps.size).isEqualTo(0)
        } finally {
            setAppOpHistoryParameters("mode=HISTORICAL_MODE_ENABLED_ACTIVE")
        }
    }

    private fun setAppOpHistoryParameters(value: String) {
        runWithShellPermissionIdentity {
            instrumentation.uiAutomation
                .executeShellCommand("settings put global appop_history_parameters $value")
        }
    }

    // noteOp calls within same second can be batched and affect the test, if 2 tests run fast
    // enough to run in the same second.
    private fun ensureNoteOpBatchingDoesNotAffectTest() {
        Thread.sleep(1000)
    }

    private fun ensureAppOpsModeAllowed(uid: Int, packageName: String, appOpNames: List<String>) {
        runWithShellPermissionIdentity {
            appOpNames.forEach { appOpName ->
                appOpsManager.setUidMode(appOpName, uid, MODE_ALLOWED)
            }
        }
        eventually {
            appOpNames.forEach { appOpName ->
                assertWithMessage("$appOpName mode should be MODE_ALLOWED ")
                    .that(appOpsManager.checkOp(appOpName, uid, packageName))
                    .isEqualTo(MODE_ALLOWED)
            }
        }
    }

    /** Provides guarantee that there is at least requiredSafetyMarginMillis milliseconds until next
     *  time quant starts.
     */
    private fun waitUntilSafelyInTimeQuant(
        quantSizeMillis: Long,
        requiredSafetyMarginMillis: Long
    ) {
        while (System.currentTimeMillis() / quantSizeMillis * quantSizeMillis
            != (System.currentTimeMillis() + requiredSafetyMarginMillis) /
            quantSizeMillis * quantSizeMillis
        ) {
            Thread.sleep(1)
        }
    }

    /**
     * Reads app ops for current process, and package name from short interval table, by default.
     */
    private fun getHistoricalOps(
        uid: Int = testUid,
        packageName: String? = testPackageName,
        opNames: List<String>? = null,
        beginTimeMillis: Long = 0,
        endTimeMillis: Long = Long.MAX_VALUE,
        historyFlag: Int = AppOpsManager.HISTORY_FLAG_DISCRETE
    ): HistoricalOps? {
        val appOpsManager = context.getSystemService(AppOpsManager::class.java)
        val array = arrayOfNulls<HistoricalOps>(1)
        val lock = ReentrantLock()
        val condition = lock.newCondition()
        try {
            lock.lock()
            val request = HistoricalOpsRequest.Builder(beginTimeMillis, endTimeMillis)
                .setUid(uid)
                .setPackageName(packageName)
                .setHistoryFlags(historyFlag)
                .setOpNames(opNames?.toList())
                .build()
            runWithShellPermissionIdentity {
                appOpsManager.getHistoricalOps(request, context.mainExecutor, Consumer { ops ->
                    array[0] = ops
                    try {
                        lock.lock()
                        condition.signalAll()
                    } finally {
                        lock.unlock()
                    }
                })
            }
            condition.await(5, TimeUnit.SECONDS)
            return array[0]
        } finally {
            lock.unlock()
        }
    }

    private fun wakeScreenUp() {
        val uiDevice = UiDevice.getInstance(instrumentation)
        uiDevice.wakeUp()
        uiDevice.executeShellCommand("wm dismiss-keyguard")
    }

    private fun installApk(apk: String) {
        val result = runCommand(
            "pm install -g --user ${context.userId} -r --force-queryable $apk"
        )
        assertThat(result.trim()).isEqualTo("Success")
    }

    private fun noteOpWithShellIdentity(
        opName: String,
        uid: Int,
        packageName: String,
        attributionTag: String? = null
    ) {
        runWithShellPermissionIdentity {
            appOpsManager.noteOp(opName, uid, packageName, attributionTag, null)
        }
    }

    /**
     * Returns a list of flat discrete ops from deeply nested structure,
     * assertion on flat object is simpler.
     */
    private fun convertHistoricalOpsToDiscreteAccessEvents(
        historicalAppOps: HistoricalOps?
    ): List<AppOpAccessEvent> {
        val opEvents: MutableList<AppOpAccessEvent> = ArrayList()
        if (historicalAppOps == null || historicalAppOps.getUidCount() == 0) {
            return opEvents
        }
        val uidCount: Int = historicalAppOps.getUidCount()
        for (i in 0 until uidCount) {
            val uidOps: AppOpsManager.HistoricalUidOps = historicalAppOps.getUidOpsAt(i)
            val packageCount = uidOps.packageCount
            for (p in 0 until packageCount) {
                val packageOps = uidOps.getPackageOpsAt(p)
                val attrCount = packageOps.attributedOpsCount
                for (a in 0 until attrCount) {
                    val attributedOps = packageOps.getAttributedOpsAt(a)
                    val opCount = attributedOps.opCount
                    for (o in 0 until opCount) {
                        val historicalOp = attributedOps.getOpAt(o)
                        val accessCount = historicalOp.discreteAccessCount
                        for (x in 0 until accessCount) {
                            val opEntry = historicalOp.getDiscreteAccessAt(x)
                            val event = AppOpAccessEvent(
                                uid = uidOps.uid,
                                packageName = packageOps.packageName,
                                opName = historicalOp.opName,
                                attributionTag = attributedOps.tag,
                                accessTimeMillis = opEntry.getLastAccessTime(OP_FLAGS_ALL),
                                durationMillis = opEntry.getLastDuration(OP_FLAGS_ALL),
                            )
                            opEvents.add(event)
                        }
                    }
                }
            }
        }
        return opEvents
    }

    /**
     * Returns a list of flat aggregated ops from deeply nested structure,
     * assertion on flat object is simpler.
     */
    private fun convertHistoricalOpsToAggregatedAccessEvents(
        historicalAppOps: HistoricalOps?
    ): List<AggregatedAppOpAccessEvent> {
        val opEvents: MutableList<AggregatedAppOpAccessEvent> = ArrayList()
        if (historicalAppOps == null || historicalAppOps.getUidCount() == 0) {
            return opEvents
        }
        val uidCount: Int = historicalAppOps.getUidCount()
        for (i in 0 until uidCount) {
            val uidOps: AppOpsManager.HistoricalUidOps = historicalAppOps.getUidOpsAt(i)
            val packageCount = uidOps.packageCount
            for (p in 0 until packageCount) {
                val packageOps = uidOps.getPackageOpsAt(p)
                val attrCount = packageOps.attributedOpsCount
                for (a in 0 until attrCount) {
                    val attributedOps = packageOps.getAttributedOpsAt(a)
                    val opCount = attributedOps.opCount
                    for (o in 0 until opCount) {
                        val historicalOp = attributedOps.getOpAt(o)
                        val event = AggregatedAppOpAccessEvent(
                            uid = uidOps.uid,
                            packageName = packageOps.packageName,
                            opName = historicalOp.opName,
                            attributionTag = attributedOps.tag,
                            accessCount = historicalOp.getAccessCount(
                                AppOpsManager.UID_STATE_PERSISTENT,
                                AppOpsManager.UID_STATE_CACHED,
                                OP_FLAGS_ALL
                            ),
                            rejectCount = historicalOp.getRejectCount(
                                AppOpsManager.UID_STATE_PERSISTENT,
                                AppOpsManager.UID_STATE_CACHED,
                                OP_FLAGS_ALL
                            ),
                            totalDurationMillis = historicalOp.getAccessDuration(
                                AppOpsManager.UID_STATE_PERSISTENT,
                                AppOpsManager.UID_STATE_CACHED,
                                OP_FLAGS_ALL
                            )
                        )
                        opEvents.add(event)
                    }
                }
            }
        }
        return opEvents
    }

    // Flat object for discrete ops
    private data class AppOpAccessEvent(
        val uid: Int,
        val packageName: String,
        val opName: String,
        val attributionTag: String?,
        val accessTimeMillis: Long,
        val durationMillis: Long
    )

    // Flat object for aggregated ops
    private data class AggregatedAppOpAccessEvent(
        val uid: Int,
        val packageName: String,
        val opName: String,
        val attributionTag: String?,
        val accessCount: Long,
        val rejectCount: Long,
        val totalDurationMillis: Long
    )
}
