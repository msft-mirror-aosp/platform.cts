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
 *
 */

package com.android.cts.windowmanager.deviceside

import com.android.compatibility.common.util.ApiTest
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(DeviceJUnit4ClassRunner::class)
class PersistsAcrossRebootsTest : BaseHostJUnit4Test() {
    /**
     * Tests the persisted bundle is persisted across reboots through
     * [android.app.Activity.onSaveInstanceState] and [android.app.Activity.onCreate] when the
     * activity's [android.R.attr.persistableMode]
     */
    @Test
    @ApiTest(
        apis = [
            "android.app.Activity#onSaveInstanceState",
            "android.app.Activity#onCreate",
            "android.R.attr#persistableMode",
        ]
    )
    fun testPersistsAcrossReboots() {
        assertTrue(runDeviceTests(TEST_PKG, TEST_CLASS, TEST_METHOD_PREBOOT))
        device.reboot()
        assertTrue(runDeviceTests(TEST_PKG, TEST_CLASS, TEST_METHOD_POSTBOOT))
    }

    companion object {
        private const val APP_PKG = "com.android.cts.windowmanager.deviceside"
        private const val TEST_PKG = "$APP_PKG.test"
        private const val TEST_CLASS = "$APP_PKG.PersistAcrossRebootsActivityTest"
        private const val TEST_METHOD_PREBOOT = "testShowDefaultValue_preReboot"
        private const val TEST_METHOD_POSTBOOT = "testShowPersistedValue_postReboot"
    }
}
