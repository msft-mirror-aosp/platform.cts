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

import android.bluetooth.BluetoothFrameworkInitializer
import android.os.BluetoothServiceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

@RunWith(AndroidJUnit4::class)
@SmallTest
class BluetoothFrameworkInitializerTest {

    /**
     * BluetoothFrameworkInitializer.registerServiceWrappers() should only be called by
     * SystemServiceRegistry during boot up when Bluetooth is first initialized. Calling this API at
     * any other time should throw an exception.
     */
    @Test
    fun registerServiceWrappers_failsWhenCalledOutsideOfSystemServiceRegistry() {
        assertThrows(IllegalStateException::class.java) {
            BluetoothFrameworkInitializer.registerServiceWrappers()
        }
    }

    @Test
    fun setBluetoothServiceManager() {
        assertThrows(IllegalStateException::class.java) {
            BluetoothFrameworkInitializer.setBluetoothServiceManager(
                mock<BluetoothServiceManager>()
            )
        }
    }

    @Test
    fun setBinderCallsStatsInitializer() {
        assertThrows(IllegalStateException::class.java) {
            BluetoothFrameworkInitializer.setBinderCallsStatsInitializer { _ -> }
        }
    }
}
