/*
 * Copyright (C) 2026 The Android Open Source Project
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
package android.allowlist.cts

import android.annotation.SuppressLint
import android.app.appfunctions.flags.Flags
import android.content.Context
import android.content.pm.SignedPackage
import android.os.Bundle
import android.os.allowlist.AllowlistManager
import android.os.allowlist.AllowlistRequest
import android.os.allowlist.AllowlistResponse
import android.os.allowlist.SignedPackageMultiMap
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.ApiTest
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import org.junit.AfterClass
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test

/**
 * These tests ensure the platform side of the allowlist service is functioning properly. Notably,
 * they do not involve the real AllowlistProviderService, only a test one built into the system.
 */
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_APP_FUNCTION_PERMISSION_V2)
@SuppressLint("MissingPermission")
class AllowlistManagerTest {

    private val testPackage1 = SignedPackage("test.package.1", byteArrayOf(0x1))
    private val testPackage2 = SignedPackage("test.package.2", byteArrayOf(0x2))
    private val testTarget1 = SignedPackage("test.target.1", byteArrayOf(0x3))
    private val emptyListener = Consumer<AllowlistRequest> { }
    private lateinit var allowlistManager: AllowlistManager
    private val context: Context = InstrumentationRegistry.getInstrumentation().context

    @get:Rule val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @Before
    fun setUp() {
        allowlistManager = context.getSystemService(AllowlistManager::class.java)!!
    }

    @ApiTest(
        apis = ["android.allowlist.AllowlistManager#queryAllowlist"]
    )
    @Test
    fun testQueryAllowlist_withoutPermission_throwsException() {
        val data = Bundle().apply {
            putParcelableArrayList(
                AllowlistManager.REQUEST_KEY_FILTER_PACKAGES,
                arrayListOf(testPackage1)
            )
        }
        val request = AllowlistRequest(AllowlistManager.ALLOWLIST_ID_TEST, data)

        assertThrows(SecurityException::class.java) {
            allowlistManager.queryAllowlist(request, context.mainExecutor, {})
        }
    }

    @ApiTest(
        apis = ["android.allowlist.AllowlistManager#queryAllowlist"]
    )
    @Test
    fun testQueryAllowlist_filterPackages_success() {
        runWithShellPermissionIdentity {
            val data = Bundle().apply {
                putParcelableArrayList(
                    AllowlistManager.REQUEST_KEY_FILTER_PACKAGES,
                    arrayListOf(testPackage1)
                )
            }
            val request = AllowlistRequest(AllowlistManager.ALLOWLIST_ID_TEST, data)
            val latch = CountDownLatch(1)
            var response: AllowlistResponse? = null

            allowlistManager.queryAllowlist(request, context.mainExecutor) { resp ->
                response = resp
                latch.countDown()
            }
            assertThat(latch.await(LATCH_TIMEOUT_UNEXPECTED_MS, TimeUnit.MILLISECONDS)).isTrue()
            assertThat(response).isNotNull()
            assertThat(response!!.status).isEqualTo(AllowlistManager.RESPONSE_STATUS_SUCCESS)
            val returnedPackages = response.data.getParcelableArrayList<SignedPackage>(
                AllowlistManager.RESPONSE_KEY_ALLOWED_PACKAGES,
                SignedPackage::class.java
            )
            assertThat(returnedPackages).containsExactly(testPackage1)
        }
    }

    @ApiTest(
        apis = ["android.allowlist.AllowlistManager#queryAllowlist"]
    )
    @Test
    fun testQueryAllowlist_filterAgentsAndTargets_success() {
        runWithShellPermissionIdentity {
            val data = Bundle().apply {
                putParcelableArrayList(
                    AllowlistManager.REQUEST_KEY_FILTER_PACKAGES,
                    arrayListOf(testPackage1, testPackage2)
                )

                putParcelableArrayList(
                    AllowlistManager.REQUEST_KEY_FILTER_TARGETS,
                    arrayListOf(testTarget1)
                )
            }
            val request = AllowlistRequest(AllowlistManager.ALLOWLIST_ID_TEST, data)
            val latch = CountDownLatch(1)
            var response: AllowlistResponse? = null

            allowlistManager.queryAllowlist(request, context.mainExecutor) { resp ->
                response = resp
                latch.countDown()
            }
            assertThat(latch.await(LATCH_TIMEOUT_UNEXPECTED_MS, TimeUnit.MILLISECONDS)).isTrue()
            assertThat(response).isNotNull()
            assertThat(response!!.status).isEqualTo(AllowlistManager.RESPONSE_STATUS_SUCCESS)

            val allowedAgentsAndTargets =
                response.data.getParcelable(
                    AllowlistManager.RESPONSE_KEY_ALLOWED_PACKAGE_MULTI_MAP,
                    SignedPackageMultiMap::class.java
                )?.map

            assertThat(allowedAgentsAndTargets!!.keys).containsExactly(testPackage1, testPackage2)

            for ((_, targets) in allowedAgentsAndTargets) {
                assertThat(targets.map { it.packageName }).containsExactly(testTarget1.packageName)
            }
        }
    }

    @ApiTest(
        apis = ["android.allowlist.AllowlistManager#queryAllowlist"]
    )
    @Test
    fun testQueryAllowlist_filterInstalledPackages_success() {
        runWithShellPermissionIdentity {
            val data = Bundle().apply {
                putBoolean(AllowlistManager.REQUEST_KEY_INSTALLED_PACKAGES_ONLY, true)
            }
            val request = AllowlistRequest(AllowlistManager.ALLOWLIST_ID_TEST, data)
            val latch = CountDownLatch(1)
            var response: AllowlistResponse? = null

            allowlistManager.queryAllowlist(request, context.mainExecutor) { resp ->
                response = resp
                latch.countDown()
            }
            assertThat(latch.await(LATCH_TIMEOUT_UNEXPECTED_MS, TimeUnit.MILLISECONDS)).isTrue()
            assertThat(response).isNotNull()
            assertThat(response!!.status).isEqualTo(AllowlistManager.RESPONSE_STATUS_SUCCESS)
            val returnedPackages = response.data.getParcelableArrayList<SignedPackage>(
                AllowlistManager.RESPONSE_KEY_ALLOWED_PACKAGES,
                SignedPackage::class.java
            )
            assertThat(returnedPackages).isEmpty()
        }
    }

    @ApiTest(apis = ["android.allowlist.AllowlistManager#addOnAllowlistChangedListener"])
    @Test
    fun testAddOnAllowlistChangedListener_withoutPermission_throwsException() {
        val request = AllowlistRequest(AllowlistManager.ALLOWLIST_ID_TEST, Bundle())
        assertThrows(SecurityException::class.java) {
            allowlistManager.addOnAllowlistChangedListener(
                request,
                context.mainExecutor,
                emptyListener
            )
        }
    }

    @ApiTest(apis = ["android.allowlist.AllowlistManager#removeOnAllowlistChangedListener"])
    @Test
    fun testRemoveOnAllowlistChangedListener_withoutPermission_throwsException() {
        assertThrows(SecurityException::class.java) {
            allowlistManager.removeOnAllowlistChangedListener(emptyListener)
        }
    }

    @ApiTest(
        apis = ["android.allowlist.AllowlistManager#addOnAllowlistChangedListener",
            "android.allowlist.AllowlistManager#notifyAllowlistChangedListenersInTest"]
    )
    @Test
    fun testAddListener_notifyListener_listenerInvoked() {
        runWithShellPermissionIdentity {
            val latch = CountDownLatch(1)
            val request = AllowlistRequest(AllowlistManager.ALLOWLIST_ID_TEST, Bundle().apply {
                putParcelableArrayList(
                    AllowlistManager.REQUEST_KEY_FILTER_PACKAGES,
                    arrayListOf(testPackage1)
                )
            })
            val listener = Consumer<AllowlistRequest> { req ->
                latch.countDown()
                assertThat(req).isEqualTo(request)
            }

            try {
                allowlistManager.addOnAllowlistChangedListener(
                    request,
                    context.mainExecutor,
                    listener
                )
                allowlistManager.notifyAllowlistChangedListenersForTestProvider(listOf(request))
                assertThat(latch.await(LATCH_TIMEOUT_EXPECTED_MS, TimeUnit.MILLISECONDS)).isTrue()
            } finally {
                allowlistManager.removeOnAllowlistChangedListener(listener)
            }
        }
    }

    @ApiTest(
        apis = ["android.allowlist.AllowlistManager#removeOnAllowlistChangedListener",
            "android.allowlist.AllowlistManager#notifyAllowlistChangedListenersInTest"]
    )
    @Test
    fun testRemoveListener_notifyListener_listenerNotInvoked() {
        runWithShellPermissionIdentity {
            val latch = CountDownLatch(1)
            val request = AllowlistRequest(AllowlistManager.ALLOWLIST_ID_TEST, Bundle().apply {
                putParcelableArrayList(
                    AllowlistManager.REQUEST_KEY_FILTER_PACKAGES,
                    arrayListOf(testPackage1)
                )
            })

            val listener = Consumer<AllowlistRequest> { latch.countDown() }
            allowlistManager.addOnAllowlistChangedListener(
                request,
                context.mainExecutor,
                listener
            )
            allowlistManager.removeOnAllowlistChangedListener(listener)
            allowlistManager.notifyAllowlistChangedListenersForTestProvider(listOf(request))
            assertThat(latch.await(LATCH_TIMEOUT_UNEXPECTED_MS, TimeUnit.MILLISECONDS)).isFalse()
        }
    }

    companion object {
        const val LATCH_TIMEOUT_EXPECTED_MS = 5000L
        const val LATCH_TIMEOUT_UNEXPECTED_MS = 2000L

        @JvmStatic
        @BeforeClass
        fun enableTestProvider() {
            runWithShellPermissionIdentity {
                InstrumentationRegistry.getInstrumentation().context.getSystemService(
                    AllowlistManager::class.java
                )!!
                    .setTestProviderEnabled(true)
            }
        }

        @JvmStatic
        @AfterClass
        fun disableTestProvider() {
            runWithShellPermissionIdentity {
                InstrumentationRegistry.getInstrumentation().context.getSystemService(
                    AllowlistManager::class.java
                )!!
                    .setTestProviderEnabled(false)
            }
        }
    }
}
