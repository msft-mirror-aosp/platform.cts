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

package android.bluetooth.cts

import android.bluetooth.le.AdvertiseCallback
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.CddTest
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions

/** Test of {@link AdvertiseCallback}. */
@RunWith(AndroidJUnit4::class)
class AdvertiseCallbackTest {
    @get:Rule val mockitoRule = MockitoJUnit.rule()

    @Mock private lateinit var advertiseCallback: AdvertiseCallback

    private val mockAdvertiser = MockAdvertiser()

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Assume.assumeTrue(TestUtils.isBleSupported(context))
    }

    @CddTest(requirements = ["7.4.3/C-2-1"])
    @SmallTest
    @Test
    fun advertiseSuccess() {
        mockAdvertiser.startAdvertise(advertiseCallback)
        verify(advertiseCallback).onStartSuccess(anyOrNull())
        verifyNoMoreInteractions(advertiseCallback)
    }

    @CddTest(requirements = ["7.4.3/C-2-1"])
    @SmallTest
    @Test
    fun advertiseFailure() {
        mockAdvertiser.startAdvertise(advertiseCallback)
        verify(advertiseCallback).onStartSuccess(anyOrNull())
        verifyNoMoreInteractions(advertiseCallback)

        // Second advertise with the same callback should fail.
        mockAdvertiser.startAdvertise(advertiseCallback)
        verify(advertiseCallback).onStartFailure(any())
        verifyNoMoreInteractions(advertiseCallback)
    }

    // A mock advertiser which emulate BluetoothLeAdvertiser behavior.
    private class MockAdvertiser {
        private val callbacks = mutableSetOf<AdvertiseCallback>()

        fun startAdvertise(callback: AdvertiseCallback) {
            synchronized(callbacks) {
                if (callbacks.contains(callback)) {
                    callback.onStartFailure(AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED)
                } else {
                    callback.onStartSuccess(null)
                    callbacks.add(callback)
                }
            }
        }
    }
}
