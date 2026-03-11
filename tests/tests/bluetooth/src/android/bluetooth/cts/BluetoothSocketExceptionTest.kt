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

import android.bluetooth.BluetoothSocketException
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/** Unit test to test APIs and functionality for {@link BluetoothSocketException}. */
@RunWith(AndroidJUnit4::class)
@SmallTest
class BluetoothSocketExceptionTest {

    @Test
    fun getErrorCode_returnsCorrectErrorCode() {
        val exception = BluetoothSocketException(BluetoothSocketException.SOCKET_CONNECTION_FAILURE)

        assertThat(exception.errorCode)
            .isEqualTo(BluetoothSocketException.SOCKET_CONNECTION_FAILURE)
    }

    @Test
    fun getMessage_returnsCustomErrorMsg() {
        val customErrMsg = "This is a custom error message"
        val exception = BluetoothSocketException(BluetoothSocketException.UNSPECIFIED, customErrMsg)

        assertThat(exception.message).isEqualTo(customErrMsg)
    }

    @Test
    fun getMessage_returnsErrorMsgWhenOnlyCodeIsProvided() {
        val exception = BluetoothSocketException(BluetoothSocketException.UNSPECIFIED)

        assertThat(exception.message).isNotNull()
    }
}
