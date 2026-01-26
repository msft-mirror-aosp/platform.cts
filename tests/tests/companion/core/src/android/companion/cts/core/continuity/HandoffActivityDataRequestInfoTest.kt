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

package android.companion.cts.core.continuity

import android.app.HandoffActivityDataRequestInfo
import android.companion.cts.core.CoreTestBase
import android.platform.test.annotations.AppModeFull
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.compatibility.common.util.ApiTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test HandoffActivityDataRequestInfo.
 *
 * Run: atest CtsCompanionDeviceManagerCoreTestCases:HandoffActivityDataRequestInfoTest
 *
 * @see android.app.HandoffActivityDataRequestInfo
 */
@ApiTest(apis = ["android.app.HandoffActivityDataRequestInfo#isActiveRequest"])
@AppModeFull(reason = "CompanionDeviceManager APIs are not available to the instant apps.")
@RunWith(AndroidJUnit4::class)
class HandoffActivityDataRequestInfoTest : CoreTestBase() {

    @Test
    fun construct_setsFieldsForGetter() {
        val requestInfo = HandoffActivityDataRequestInfo(true)
        assertTrue(requestInfo.isActiveRequest)
    }
}
