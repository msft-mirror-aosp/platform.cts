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

import android.bluetooth.BufferConstraint
import android.bluetooth.BufferConstraints
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BufferConstraintsTest {

    private lateinit var bufferConstraints: BufferConstraints
    private lateinit var bufferConstraintList: MutableList<BufferConstraint>

    private var hasBluetooth = false

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().context
        hasBluetooth = context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)
    }

    @Test
    @SmallTest
    fun forCodec() {
        // Skip the test if bluetooth is not present.
        assumeTrue(hasBluetooth)

        bufferConstraintList = mutableListOf()
        for (i in 0 until BufferConstraints.BUFFER_CODEC_MAX_NUM) {
            val bufferConstraint =
                BufferConstraint(DEFAULT_BUFFER_TIME, MAXIMUM_BUFFER_TIME, MINIMUM_BUFFER_TIME)
            bufferConstraintList.add(bufferConstraint)
        }
        bufferConstraints = BufferConstraints(bufferConstraintList)

        for (i in 0 until 6) {
            assertThat(bufferConstraints.forCodec(i)?.defaultMillis).isEqualTo(DEFAULT_BUFFER_TIME)
            assertThat(bufferConstraints.forCodec(i)?.maxMillis).isEqualTo(MAXIMUM_BUFFER_TIME)
            assertThat(bufferConstraints.forCodec(i)?.minMillis).isEqualTo(MINIMUM_BUFFER_TIME)
        }
    }

    companion object {
        private const val DEFAULT_BUFFER_TIME = 1
        private const val MAXIMUM_BUFFER_TIME = 2
        private const val MINIMUM_BUFFER_TIME = 3
    }
}
