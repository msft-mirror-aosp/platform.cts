/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.bedstead.usb

import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.nene.TestApis
import com.android.interactive.annotations.Interactive
import com.android.interactive.annotations.UntetheredTest
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
class UsbTest {

    @Test
    fun isConnected_isConnected_returnsTrue() {
        // This is true by default in all scenarios we would be testing
        assertThat(TestApis.usb().isConnected()).isTrue()
    }

    @Test
    @UntetheredTest
    @Interactive
    fun isConnected_isNotConnected_returnsFalse() {
        assertThat(TestApis.usb().isConnected()).isFalse()
    }
}