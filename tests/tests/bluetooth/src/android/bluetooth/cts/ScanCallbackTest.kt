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

import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.CddTest
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.mock

/** Test cases for [ScanCallback]. */
@RunWith(AndroidJUnit4::class)
class ScanCallbackTest {
    @get:Rule val mockitoRule = MockitoJUnit.rule()

    @Mock private lateinit var scanCallback: ScanCallback

    private val mockScanner = MockScanner()

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().context
        Assume.assumeTrue(TestUtils.isBleSupported(context))
    }

    @CddTest(requirements = ["7.4.3/C-2-1"])
    @SmallTest
    @Test
    fun scanSuccess() {
        mockScanner.startScan(ScanSettings.Builder().build(), scanCallback)
        verify(scanCallback).onScanResult(anyInt(), any())
        verifyNoMoreInteractions(scanCallback)
    }

    @CddTest(requirements = ["7.4.3/C-2-1"])
    @SmallTest
    @Test
    fun batchScans() {
        mockScanner.startScan(ScanSettings.Builder().setReportDelay(1000).build(), scanCallback)
        verify(scanCallback).onBatchScanResults(any())
        verifyNoMoreInteractions(scanCallback)
    }

    @CddTest(requirements = ["7.4.3/C-2-1"])
    @SmallTest
    @Test
    fun scanFail() {
        val settings = ScanSettings.Builder().build()
        // The first scan is success.
        mockScanner.startScan(settings, scanCallback)
        verify(scanCallback).onScanResult(anyInt(), any())
        verifyNoMoreInteractions(scanCallback)

        // A second scan with the same callback should fail.
        mockScanner.startScan(settings, scanCallback)
        verify(scanCallback).onScanFailed(anyInt())
        verifyNoMoreInteractions(scanCallback)
    }

    // A mock scanner for mocking BLE scanner functionalities.
    private class MockScanner {
        private val callbacks = mutableSetOf<ScanCallback>()

        fun startScan(settings: ScanSettings, callback: ScanCallback) {
            synchronized(callbacks) {
                if (callbacks.contains(callback)) {
                    callback.onScanFailed(ScanCallback.SCAN_FAILED_ALREADY_STARTED)
                    return
                }
                callbacks.add(callback)
                if (settings.reportDelayMillis == 0L) {
                    callback.onScanResult(0, mock<ScanResult>())
                } else {
                    callback.onBatchScanResults(mock<List<ScanResult>>())
                }
            }
        }
    }
}
