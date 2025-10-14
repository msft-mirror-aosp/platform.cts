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

package android.companion.cts.core

import android.app.HandoffActivityData
import android.content.ComponentName
import android.net.Uri
import android.os.Parcel
import android.os.PersistableBundle
import android.platform.test.annotations.AppModeFull
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test [android.app.HandoffActivityData].
 *
 * Run: atest CtsCompanionDeviceManagerCoreTestCases:HandoffActivityDataTest
 */
@AppModeFull(reason = "CompanionDeviceManager APIs are not available to the instant apps.")
@RunWith(AndroidJUnit4::class)
class HandoffActivityDataTest : CoreTestBase() {

    @Test
    fun test_setters() =
        with(targetApp) {
            val handoffActivityData =
                HandoffActivityData.Builder(TEST_COMPONENT_NAME)
                    .setExtras(PersistableBundle().apply { putString(TEST_KEY, TEST_VALUE) })
                    .setFallbackUri(TEST_URI)
                    .build()

            assertEquals(handoffActivityData.componentName, TEST_COMPONENT_NAME)
            assertEquals(handoffActivityData.extras.getString(TEST_KEY), TEST_VALUE)
            assertEquals(handoffActivityData.fallbackUri, TEST_URI)
        }

    @Test
    fun test_parcelable() =
        with(targetApp) {
            val handoffActivityData =
                HandoffActivityData.Builder(TEST_COMPONENT_NAME)
                    .setExtras(PersistableBundle().apply { putString(TEST_KEY, TEST_VALUE) })
                    .setFallbackUri(TEST_URI)
                    .build()

            val parcel = Parcel.obtain()
            handoffActivityData.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            val newHandoffActivityData = HandoffActivityData.CREATOR.createFromParcel(parcel)

            assertEquals(handoffActivityData, newHandoffActivityData)
        }

    @Test
    fun test_parcelable_no_optional_fields() =
        with(targetApp) {
            val handoffActivityData = HandoffActivityData.Builder(TEST_COMPONENT_NAME).build()
            val parcel = Parcel.obtain()
            handoffActivityData.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            val newHandoffActivityData = HandoffActivityData.CREATOR.createFromParcel(parcel)

            assertEquals(handoffActivityData, newHandoffActivityData)
        }

    private companion object {
        val TEST_COMPONENT_NAME = ComponentName("test_package", "test_class")
        val TEST_URI = Uri.parse("https://www.google.com")

        const val TEST_KEY = "test_key"
        const val TEST_VALUE = "test_value"
    }
}
