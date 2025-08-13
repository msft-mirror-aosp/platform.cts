/*
 * Copyright (C) 2022 The Android Open Source Project
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

import android.app.admin.ConnectEvent
import android.app.admin.DnsEvent
import android.app.admin.NetworkEvent
import android.os.SystemClock
import android.util.Log
import com.android.bedstead.enterprise.annotations.CanSetPolicyTest
import com.android.bedstead.enterprise.annotations.CannotSetPolicyTest
import com.android.bedstead.enterprise.annotations.EnsureHasDeviceOwner
import com.android.bedstead.enterprise.annotations.EnsureHasProfileOwner
import com.android.bedstead.enterprise.annotations.EnsureHasWorkProfile
import com.android.bedstead.enterprise.annotations.PolicyAppliesTest
import com.android.bedstead.enterprise.dpc
import com.android.bedstead.enterprise.dpcOnly
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.UserType
import com.android.bedstead.harrier.UserType.ADDITIONAL_USER
import com.android.bedstead.harrier.annotations.Postsubmit
import com.android.bedstead.harrier.annotations.RequireRunOnInitialUser
import com.android.bedstead.harrier.policies.GlobalNetworkLogging
import com.android.bedstead.harrier.policies.NetworkLogging
import com.android.bedstead.multiuser.additionalUser
import com.android.bedstead.multiuser.annotations.EnsureHasAdditionalUser
import com.android.bedstead.multiuser.annotations.EnsureHasNoAdditionalUser
import com.android.bedstead.multiuser.annotations.RequireRunOnPrimaryUser
import com.android.bedstead.nene.TestApis
import com.android.bedstead.nene.users.UserReference
import com.android.bedstead.permissions.CommonPermissions.INTERNET
import com.android.bedstead.testapps.testApps
import com.android.compatibility.common.util.ApiTest
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertWithMessage
import java.net.InetAddress
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.fail
import org.junit.Assume
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.testng.Assert

// These tests currently only cover checking that the appropriate methods are callable. They should
// be replaced with more complete tests once the other network logging tests are ready to be
// migrated to the new infrastructure
@RunWith(BedsteadJUnit4::class)
class NetworkLoggingTest {
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#isNetworkLoggingEnabled"])
    @CannotSetPolicyTest(policy = [GlobalNetworkLogging::class, NetworkLogging::class])
    @Postsubmit(reason = "new test")
    fun isNetworkLoggingEnabled_notAllowed_throwsException() {
        Assert.assertThrows(SecurityException::class.java) {
            isNetworkLoggingEnabled()
        }
    }

    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#isNetworkLoggingEnabled"])
    @EnsureHasNoAdditionalUser
    @CanSetPolicyTest(policy = [NetworkLogging::class, GlobalNetworkLogging::class])
    @Postsubmit(reason = "new test")
    fun isNetworkLoggingEnabled_networkLoggingIsEnabled_returnsTrue() {
        removeUnaffiliatedUsersIfLoggingDeviceWide()

        try {
            setNetworkLoggingEnabled(true)

            Truth.assertThat(isNetworkLoggingEnabled()).isTrue()
        } finally {
            setNetworkLoggingEnabled(false)
        }
    }

    @ApiTest(
        apis = ["android.app.admin.DevicePolicyManager#isNetworkLoggingEnabled",
        "android.app.admin.DevicePolicyManager#retrieveNetworkLogs"]
    )
    @CanSetPolicyTest(policy = [NetworkLogging::class, GlobalNetworkLogging::class])
    @Postsubmit(reason = "new test")
    fun retrieveNetworkLogs_withInvalidBatch_returnsNull() {
        removeUnaffiliatedUsersIfLoggingDeviceWide()

        try {
            setNetworkLoggingEnabled(true)

            Truth.assertThat(retrieveNetworkLogs(-12345)).isNull()
        } finally {
            setNetworkLoggingEnabled(false)
        }
    }

    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#isNetworkLoggingEnabled"])
    @CanSetPolicyTest(policy = [NetworkLogging::class, GlobalNetworkLogging::class])
    @Postsubmit(reason = "new test")
    fun isNetworkLoggingEnabled_networkLoggingIsNotEnabled_returnsFalse() {
        removeUnaffiliatedUsersIfLoggingDeviceWide()

        setNetworkLoggingEnabled(false)

        Truth.assertThat(isNetworkLoggingEnabled()).isFalse()
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = [NetworkLogging::class, GlobalNetworkLogging::class])
    @ApiTest(
        apis = ["android.app.admin.DevicePolicyManager#setNetworkLoggingEnabled",
            "android.app.admin.DeviceAdminReceiver#onNetworkLogsAvailable",
            "android.app.admin.DelegatedAdminReceiver#onNetworkLogsAvailable",
            "android.app.admin.DevicePolicyManager#retrieveNetworkLogs"]
    )
    fun networkLogging_logsContainDnsEvents() {
        removeUnaffiliatedUsersIfLoggingDeviceWide()

        try {
            setNetworkLoggingEnabled(true)
            Log.d(TAG, "Enabled logging")

            val hostList = uniqueHostList(10)
            testApp.install(TestApis.users().instrumented()).use { primaryApp ->
                hostList.forEach { primaryApp.makeHttpRequest("https://$it") }
            }

            val logs = getLogs()

            Truth.assertThat(logs).isNotEmpty()
            Truth.assertThat(logs.filterIsInstance<DnsEvent>().map { it.hostname })
                .containsAtLeastElementsIn(hostList)
        } finally {
            setNetworkLoggingEnabled(false)
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = [NetworkLogging::class, GlobalNetworkLogging::class])
    @ApiTest(
        apis = ["android.app.admin.DevicePolicyManager#setNetworkLoggingEnabled",
            "android.app.admin.DeviceAdminReceiver#onNetworkLogsAvailable",
            "android.app.admin.DelegatedAdminReceiver#onNetworkLogsAvailable",
            "android.app.admin.DevicePolicyManager#retrieveNetworkLogs"]
    )
    fun networkLogging_logsContainConnectEvents() {
        removeUnaffiliatedUsersIfLoggingDeviceWide()

        try {
            setNetworkLoggingEnabled(true)
            Log.d(TAG, "Enabled logging")

            testApp.install(TestApis.users().instrumented()).use { primaryApp ->
                REACHABLE_DOMAINS.forEach {
                    Truth.assertWithMessage(
                        "Failed to connect to $it, ensure the device has connectivity"
                    ).that(primaryApp.makeHttpRequest("https://$it")).isTrue()
                }
            }

            val logs = getLogs()
            val connected = logs.filterIsInstance<ConnectEvent>().map{it.inetAddress}.toSet()

            REACHABLE_DOMAINS.forEach {
                Truth.assertWithMessage("Can't find connect event for $it")
                    .that(connected).containsAnyIn(InetAddress.getAllByName(it))
            }
        } finally {
            setNetworkLoggingEnabled(false)
        }
    }

    @Test
    @Postsubmit(reason = "new test")
    @EnsureHasWorkProfile
    @EnsureHasProfileOwner(onUser = UserType.WORK_PROFILE, isPrimary = true)
    @RequireRunOnPrimaryUser
    fun workProfileNetworkLogging_doesNotSeePersonalTraffic() {
        try {
            setNetworkLoggingEnabled(true)
            Log.d(TAG, "Enabled logging")

            // Access some hosts from the primary user.
            val primaryHosts = uniqueHostList(10)
            testApp.install(TestApis.users().instrumented()).use { primaryApp ->
                primaryHosts.forEach { primaryApp.makeHttpRequest("https://$it") }
            }

            val logs = getLogs()
            Truth.assertThat(logs).isNotEmpty()

            Truth.assertThat(logs.filterIsInstance<DnsEvent>().map{it.hostname})
                .containsNoneIn(primaryHosts)
        } finally {
            setNetworkLoggingEnabled(false)
        }
    }

    @Postsubmit(reason = "new test")
    @CannotSetPolicyTest(policy = [GlobalNetworkLogging::class, NetworkLogging::class])
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#setNetworkLoggingEnabled"])
    fun setNetworkLoggingEnabled_notAllowed_throwsException() {
        Assert.assertThrows(SecurityException::class.java) {
            setNetworkLoggingEnabled(true)
        }
    }

    @Postsubmit(reason = "new test")
    @CannotSetPolicyTest(policy = [GlobalNetworkLogging::class, NetworkLogging::class])
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#retrieveNetworkLogs"])
    fun retrieveNetworkLogs_notAllowed_throwsException() {
        Assert.assertThrows(SecurityException::class.java) {
            retrieveNetworkLogs(0)
        }
    }

    @CanSetPolicyTest(policy = [GlobalNetworkLogging::class])
    @EnsureHasAdditionalUser
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#retrieveNetworkLogs"])
    fun retrieveNetworkLogs_unaffiliatedAdditionalUser_throwsException() {
        try {
            setNetworkLoggingEnabled(true)

            Assert.assertThrows(SecurityException::class.java) {
                retrieveNetworkLogs(0)
            }
        } finally {
            setNetworkLoggingEnabled(false)
        }
    }

    @CanSetPolicyTest(policy = [GlobalNetworkLogging::class, NetworkLogging::class])
    @EnsureHasAdditionalUser
    @EnsureHasProfileOwner(onUser = ADDITIONAL_USER, affiliationIds = ["affiliated"])
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#retrieveNetworkLogs"])
    fun retrieveNetworkLogs_affiliatedAdditionalUser_doesNotThrowException() {
        TestApis.users().ensureNoOtherUsersExcept {
            u: UserReference -> u == deviceState.additionalUser()
        }

        val affiliationIds: MutableSet<String> = HashSet(
            deviceState.dpcOnly().devicePolicyManager()
                .getAffiliationIds(deviceState.dpcOnly().componentName())
        )
        affiliationIds.add("affiliated")
        deviceState.dpcOnly().devicePolicyManager().setAffiliationIds(
            deviceState.dpc().componentName(),
            affiliationIds
        )

        try {
            setNetworkLoggingEnabled(true)
            retrieveNetworkLogs(0)
        } finally {
            setNetworkLoggingEnabled(false)
        }
    }

    /**
     * This test verifies network log collection when multiple batches of events are produced.
     *
     * The test is only running in DO configuration since batching logic isn't really dependent on
     * management mode and the test may take quite some time.
     */
    @Postsubmit(reason = "new test")
    @Test
    @EnsureHasDeviceOwner
    @RequireRunOnInitialUser
    fun retrieveNetworkLogs_multipleBatches() {
        ensureNoUnaffiliatedAdditionalUsers()

        // Strictly speaking this may change and isn't part of public API. But we need to make some
        // assumptions about batch size. If it is not too far off, it should work.
        val batchSize = 1200
        // This is arbitrary, but ATM the platform may keep up to 5 unfetched batches in the queue,
        // so using a larger number has more chances of hitting a bug.
        val numBatchesToVerify = 7
        // We generate a few batches worth of padding events after those we care about. There are
        // two reasons:
        // 1. If the tail of the events we want to verify gets stuck in an incomplete batch, we'll
        // have to wait a long time. Padding batches will ensure that all the batches with the
        // events we care about are full.
        // 2. If something else in the system makes DNS requests during the test (and it is almost
        // certainly the case), the events we care about will be spread across more batches.
        val numPaddingBatches = 4

        val totalBatches = numBatchesToVerify + numPaddingBatches
        val numEventsToVerify = batchSize * numBatchesToVerify
        val numEventsToGenerate = numEventsToVerify + batchSize * numPaddingBatches

        try {
            setNetworkLoggingEnabled(true)
            Log.d(TAG, "Enabled logging")

            // Generate list of unique unresolvable (to avoid "connect" events) domains.
            val hostsToConnect = generateSequence { addUniqueSubdomain("test.domain.invalid") }
                .take(numEventsToGenerate)
                .toList()

            // Separate thread to make network request to all of these hosts.
            val requestSenderCancelled = AtomicBoolean(false)
            val requestSenderThread = Thread {
                testApp.install(TestApis.users().instrumented()).use { app ->
                    var counter = 0
                    run loop@{
                        hostsToConnect.forEach {
                            if (requestSenderCancelled.get()) {
                                Log.i(TAG, "Request sender thread cancelled.")
                                return@loop
                            }
                            Log.i(TAG, "Making request #${++counter} to $it")
                            app.makeHttpRequest("https://$it")
                            // Sleep a tiny bit to make sure other processes and threads have time
                            // to execute, so that the logs are collected in time.
                            Thread.sleep(5)
                        }
                    }
                    Log.i(TAG, "Request sender thread finishing.")
                }
            }

            // We'll fetch logs continuously and verify that they contain each of the hosts.
            val hostsToVerifyQueue = hostsToConnect.take(numEventsToVerify).toMutableSet()
            requestSenderThread.start()
            try {
                var batchToken = -1L
                while (!hostsToVerifyQueue.isEmpty() && batchToken < totalBatches) {
                    batchToken = waitForNextBatchToken()
                    Log.i(TAG, "New batch token: $batchToken")

                    val batch = retrieveNetworkLogs(batchToken)!!
                    run loop@{
                        batch.filterIsInstance<DnsEvent>().map { it.hostname }.forEach {
                            if (hostsToVerifyQueue.isEmpty()) {
                                return@loop
                            }
                            hostsToVerifyQueue.remove(it)
                        }
                    }
                }
                // No events should remain in the queue.
                assertWithMessage("${hostsToVerifyQueue.size} DNS events weren't found in logs")
                    .that(hostsToVerifyQueue).isEmpty()
            } finally {
                requestSenderCancelled.set(true)
                requestSenderThread.join(60_000)
            }
        } finally {
            setNetworkLoggingEnabled(false)
        }
    }

    private fun getLogs(): List<NetworkEvent> {
        // Generate a unique network event to be used as a marker meaning that all the previous
        // events have been fetched
        val markerHost = addUniqueSubdomain("example.com")
        Log.d(TAG, "Marker host: $markerHost")

        deviceState.dpc().makeHttpRequest("https://$markerHost")

        val result = ArrayList<NetworkEvent>()
        val deadline = SystemClock.elapsedRealtime() + Duration.ofMinutes(2).toMillis()

        while (SystemClock.elapsedRealtime() < deadline) {
            Log.d(TAG, "Forcing network logs")
            TestApis.devicePolicy().forceNetworkLogs()

            Log.d(TAG, "Waiting for batch token")
            val batchToken = waitForNextBatchToken()

            Log.d(TAG, "Retrieving batch with token: $batchToken")
            val batch = retrieveNetworkLogs(batchToken)

            if (batch != null) {
                batch.forEach {
                    if (it is DnsEvent) {
                        Log.d(TAG, "Got DnsEvent for ${it.hostname}")
                    } else {
                        Log.d(TAG, "Got non-dns event")
                    }
                }

                Log.d(TAG, "Got ${batch.size} new events")
                result.addAll(batch)

                // If marker is found, we are done.
                if (batch.any { it is DnsEvent && it.hostname == markerHost }) {
                    Log.d(TAG, "Got all logs")
                    return result
                }
            } else {
                Log.d(TAG, "Null batch")
            }
        }
        throw AssertionError("Timed out waiting for logs")
    }

    // Event queries for DPC and delegate have different types, but we only need tokens from either
    // of them, so wrap them into a lambda for a single interface.
    private var batchTokenQuery: (() -> Long)? = null
    private var previousBatchToken: Long = 0

    private fun waitForNextBatchToken(): Long {
        if (batchTokenQuery == null) {
            if (deviceState.dpc().isDelegate) {
                val query = deviceState.dpc().events().delegateNetworkLogsAvailable()
                batchTokenQuery = { query.waitForEvent().batchToken() }
            } else {
                val query = deviceState.dpc().events().networkLogsAvailable()
                batchTokenQuery = { query.waitForEvent().batchToken() }
            }
        }
        return try {
            batchTokenQuery!!().also {
                if (it != previousBatchToken + 1) {
                    fail("Unexpected batch token: $it, previous: $previousBatchToken")
                }
                previousBatchToken = it
            }
        } catch (e: AssertionError) {
            // Collect relevant logs
            throw AssertionError(
                "Error receiving batch token. Relevant logs: " +
                        TestApis.logcat().dump { l: String ->
                            l.contains("NetworkLoggingHandler") ||
                                    l.contains("sendDeviceOwnerOrProfileOwnerCommand")
                        },
                e
            )
        }
    }

    private fun setNetworkLoggingEnabled(enabled: Boolean) =
        deviceState.dpc().devicePolicyManager().setNetworkLoggingEnabled(
            deviceState.dpc().componentName(),
            enabled
        )

    private fun isNetworkLoggingEnabled(): Boolean =
        deviceState.dpc().devicePolicyManager().isNetworkLoggingEnabled(
            deviceState.dpc().componentName()
        )

    private fun retrieveNetworkLogs(batchToken: Long): List<NetworkEvent>? =
        deviceState.dpc().devicePolicyManager().retrieveNetworkLogs(
            deviceState.dpc().componentName(),
            batchToken
        )

    /**
     * Remove unaffiliated users if logging is about to be enabled device wide.
     *
     * When logging is enabled by PO, only events from the same user are visible, and there is no
     * requirement on other users. In all other cases logging is only allowed when there are no
     * unaffiliated users.
     */
    private fun removeUnaffiliatedUsersIfLoggingDeviceWide() {
        if (!deviceState.dpcOnly()
                .devicePolicyManager()
                .isProfileOwnerApp(deviceState.dpcOnly().packageName())) {
            ensureNoUnaffiliatedAdditionalUsers()
        }
    }

    private fun ensureNoUnaffiliatedAdditionalUsers() {
        // We need to skip tests on an unaffiliated user - this should be expressible in
        // annotation so it doesn't generate the incorrect test

        Assume.assumeTrue(
            "Cannot run on an unaffiliate user",
            TestApis.devicePolicy().isAffiliated()
        )

        TestApis.users().ensureNoOtherUsers()
    }

    companion object {
        @JvmField
        @ClassRule
        @Rule
        val deviceState = DeviceState()

        private val testApp = deviceState.testApps().query()
            .wherePermissions().contains(INTERNET)
            .get()

        var counter: Int = 0
        private fun addUniqueSubdomain(host: String): String =
            "host${SystemClock.elapsedRealtimeNanos()}-${counter++}.$host"

        private fun uniqueHostList(n: Int): List<String> =
            generateSequence { addUniqueSubdomain("test.domain.invalid") }
                .take(n)
                .toList()

        // Domains to use when we need "connect events". Reserved by RFC 6761.
        private val REACHABLE_DOMAINS = arrayOf(
            "example.com",
            "example.edu",
            "example.org",
            "example.net"
        )

        const val TAG = "NetworkLoggingTest"
    }
}
