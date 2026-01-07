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

package android.supervision.cts

import android.app.admin.DevicePolicyManager
import android.app.supervision.PackageUsagePolicy
import android.app.supervision.flags.Flags
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import com.android.bedstead.flags.annotations.RequireFlagsEnabled
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.nene.TestApis
import com.android.bedstead.testapp.TestApp
import com.android.bedstead.testapp.TestAppInstance
import com.android.bedstead.testapp.TestAppProvider
import com.android.compatibility.common.util.ApiTest
import com.android.eventlib.EventLogs
import com.android.eventlib.truth.EventLogsSubject.assertThat
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
@RequireFlagsEnabled(
    Flags.FLAG_ENABLE_APP_SERVICE_CONNECTION_CALLBACKS,
    Flags.FLAG_ENABLE_SUPERVISION_APP_SERVICE,
    Flags.FLAG_ENABLE_SUPERVISION_MANAGER_POLICY_APIS,
)
class SupervisionPolicyTest : BaseSupervisionTest() {

    @Before
    fun setUp() {
        EventLogs.resetLogs()
    }

    @Test
    @ApiTest(
        apis =
            [
                "android.app.supervision.PackageUsagePolicy.Builder#setPackageName",
                "android.app.supervision.PackageUsagePolicy.Builder#setType",
                "android.app.supervision.Policy.Builder#build",
                "android.app.supervision.Policy.Builder#setVersion",
                "android.app.supervision.SupervisionAppService#onPolicyChanged",
                "android.app.supervision.SupervisionManager#getPolicies",
                "android.app.supervision.SupervisionManager#setPolicy",
            ]
    )
    fun setPolicy_packagePolicy_blocked_successfullyHidesApp() {
        verifySetPackageUsagePolicy(PackageUsagePolicy.TYPE_BLOCKED)
    }

    @Test
    @ApiTest(
        apis =
            [
                "android.app.supervision.PackageUsagePolicy.Builder#setPackageName",
                "android.app.supervision.PackageUsagePolicy.Builder#setType",
                "android.app.supervision.Policy.Builder#build",
                "android.app.supervision.Policy.Builder#setVersion",
                "android.app.supervision.SupervisionAppService#onPolicyChanged",
                "android.app.supervision.SupervisionManager#getPolicies",
                "android.app.supervision.SupervisionManager#setPolicy",
            ]
    )
    fun setPolicy_packagePolicy_allowed_successfullyUnhidesApp() {
        verifySetPackageUsagePolicy(PackageUsagePolicy.TYPE_ALLOWED)
    }

    @Test
    @ApiTest(
        apis =
            [
                "android.app.supervision.PackageUsagePolicy.Builder#setPackageName",
                "android.app.supervision.PackageUsagePolicy.Builder#setType",
                "android.app.supervision.PackageUsagePolicy#getPackageName",
                "android.app.supervision.PackageUsagePolicy#getType",
                "android.app.supervision.Policy.Builder#build",
                "android.app.supervision.Policy.Builder#setVersion",
                "android.app.supervision.SupervisionAppService#onPolicyChanged",
                "android.app.supervision.SupervisionManager#getPolicies",
                "android.app.supervision.SupervisionManager#setPolicy",
            ]
    )
    fun setPolicy_multiplePolicies_successfullyApplied() {
        withTestApp(MULTIPLE_ACTIVITIES_TEST_APP_PACKAGE_NAME) { multipleActivitiesTestApp ->
            withTestApp(SMS_TEST_APP_PACKAGE_NAME) { smsTestApp ->
                withSupervisionApp(enableSupervision = true) { supervisionApp ->
                    val policy1 =
                        PackageUsagePolicy.Builder(
                                multipleActivitiesTestApp.packageName(),
                                PackageUsagePolicy.TYPE_BLOCKED,
                            )
                            .build()
                    val policy2 =
                        PackageUsagePolicy.Builder(
                                smsTestApp.packageName(),
                                PackageUsagePolicy.TYPE_ALLOWED,
                            )
                            .build()
                    setAndVerifyPackageUsagePolicy(policy1, supervisionApp)
                    setAndVerifyPackageUsagePolicy(policy2, supervisionApp)

                    val expectedPolicies =
                        listOf(
                            PackageUsagePolicy.Builder(policy1)
                                .setVersion(policy1.version + 1)
                                .build(),
                            PackageUsagePolicy.Builder(policy2)
                                .setVersion(policy2.version + 1)
                                .build(),
                        )
                    assertThat(supervisionApp.supervisionManager().getPolicies())
                        .containsExactlyElementsIn(expectedPolicies)

                    // Verify that the getters for policies returns correctly
                    assertThat(policy1.getPackageName())
                        .isEqualTo(MULTIPLE_ACTIVITIES_TEST_APP_PACKAGE_NAME)
                    assertThat(policy1.getType()).isEqualTo(PackageUsagePolicy.TYPE_BLOCKED)
                    assertThat(policy2.getPackageName()).isEqualTo(SMS_TEST_APP_PACKAGE_NAME)
                    assertThat(policy2.getType()).isEqualTo(PackageUsagePolicy.TYPE_ALLOWED)
                }
            }
        }
    }

    private fun verifySetPackageUsagePolicy(type: Int) {
        withTestApp(MULTIPLE_ACTIVITIES_TEST_APP_PACKAGE_NAME) { testApp ->
            withSupervisionApp(enableSupervision = true) { supervisionApp ->
                val policy = PackageUsagePolicy.Builder(testApp.packageName(), type).build()
                setAndVerifyPackageUsagePolicy(policy, supervisionApp)

                // Verify that the APIs return the correct policy with updated version.
                val expectedPolicy =
                    PackageUsagePolicy.Builder(policy).setVersion(policy.version + 1).build()
                assertThat(supervisionApp.supervisionManager().getPolicies())
                    .containsExactly(expectedPolicy)
            }
        }
    }

    private fun setAndVerifyPackageUsagePolicy(
        policy: PackageUsagePolicy,
        supervisionApp: TestAppInstance,
    ) {
        val expectedEnabledState =
            when (policy.type) {
                PackageUsagePolicy.TYPE_ALLOWED -> true
                PackageUsagePolicy.TYPE_BLOCKED -> false
                else -> throw IllegalArgumentException("Unsupported policy type: ${policy.type}")
            }
        // If the app is already in the expected state, then no broadcast will be sent.
        val expectBroadcast = getApplicationEnabledState(policy.packageName) != expectedEnabledState
        val expectedPolicy =
            PackageUsagePolicy.Builder(policy).setVersion(policy.version + 1).build()

        // Set up a broadcast receiver to wait for the broadcast.
        val latch = CountDownLatch(1)
        val broadcastReceiver = TestBroadcastReceiver(policy.packageName, latch)
        val filter = IntentFilter(Intent.ACTION_PACKAGE_ADDED)
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED)
        filter.addDataScheme("package")

        context.registerReceiver(broadcastReceiver, filter, Context.RECEIVER_NOT_EXPORTED)

        try {
            supervisionApp.supervisionManager().setPolicy(policy)
            assertThat(latch.await(TIMEOUT, TimeUnit.SECONDS)).isEqualTo(expectBroadcast)
            // Verify that the onPolicyChanged event is logged with the correct policy.
            val onPolicyChangedEvent =
                supervisionApp.events().policyChanged().wherePolicy().isEqualTo(expectedPolicy)
            assertThat(onPolicyChangedEvent).eventOccurredWithin(Duration.ofSeconds(TIMEOUT))
        } finally {
            context.unregisterReceiver(broadcastReceiver)
        }

        assertThat(getApplicationEnabledState(policy.packageName)).isEqualTo(expectedEnabledState)
    }

    private fun withTestApp(packageName: String, action: (app: TestAppInstance) -> Unit) {
        val app: TestApp = TestAppProvider().query().wherePackageName().isEqualTo(packageName).get()

        val appInstance =
            checkNotNull(app.install(TestApis.users().instrumented())) {
                "Failed to install $packageName TestApp."
            }
        try {
            action(appInstance)
        } finally {
            appInstance.uninstall()
        }
    }

    private fun getApplicationEnabledState(packageName: String): Boolean {
        val pm: PackageManager = context.packageManager
        try {
            // First, check for installed apps. This will return the correct enabled state.
            val appInfo = pm.getApplicationInfo(packageName, 0)
            return appInfo.enabled
        } catch (e: PackageManager.NameNotFoundException) {
            // Not found in installed apps, check uninstalled apps.
            try {
                pm.getApplicationInfo(packageName, PackageManager.MATCH_UNINSTALLED_PACKAGES)
                // If found in uninstalled, it is not enabled.
                return false
            } catch (e2: PackageManager.NameNotFoundException) {
                // Not found in either installed or uninstalled.
                throw IllegalStateException(
                    "Package $packageName not found in installed or uninstalled apps"
                )
            }
        }
    }

    class TestBroadcastReceiver(
        val targetPackageName: String,
        val latch: CountDownLatch = CountDownLatch(1),
    ) : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val packageName = intent.data?.encodedSchemeSpecificPart
            if (packageName != null && targetPackageName.equals(packageName)) {
                latch.countDown()
            }
        }
    }

    companion object {
        const val MULTIPLE_ACTIVITIES_TEST_APP_PACKAGE_NAME =
            "com.android.bedstead.testapp.MultipleActivitiesTestApp"
        const val SMS_TEST_APP_PACKAGE_NAME = "com.android.bedstead.testapp.SmsApp"
        private val devicePolicyManager = context.getSystemService(DevicePolicyManager::class.java)
        const val TIMEOUT = 5L
        const val TAG = "SupervisionPolicyTest"
    }
}
