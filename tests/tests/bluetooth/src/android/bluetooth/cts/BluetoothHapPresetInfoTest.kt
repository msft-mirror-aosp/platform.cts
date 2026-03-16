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

import android.bluetooth.BluetoothHapPresetInfo
import android.bluetooth.BluetoothProfile
import android.bluetooth.test_utils.BlockingBluetoothAdapter
import android.os.Build
import android.os.Parcel
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.ApiLevelUtil
import com.android.compatibility.common.util.CddTest
import com.google.common.truth.Expect
import com.google.common.truth.Truth.assertThat
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class BluetoothHapPresetInfoTest {
    @get:Rule val expect: Expect = Expect.create()

    private val context = InstrumentationRegistry.getInstrumentation().context

    @Before
    fun setUp() {
        assumeTrue(ApiLevelUtil.isAtLeast(Build.VERSION_CODES.TIRAMISU))
        assumeTrue(TestUtils.isBleSupported(context))
        assumeTrue(TestUtils.isProfileEnabled(BluetoothProfile.HAP_CLIENT))
        assertThat(BlockingBluetoothAdapter.enable()).isTrue()
    }

    @CddTest(requirements = ["7.4.3/C-2-1", "7.4.3/C-3-2"])
    @Test
    fun createHapPresetInfo() {
        val presetInfo =
            createHapPresetInfo(
                TEST_PRESET_INDEX,
                TEST_PRESET_NAME,
                isAvailable = true,
                isWritable = false,
            )
        expect.that(presetInfo.index).isEqualTo(TEST_PRESET_INDEX)
        expect.that(presetInfo.name).isEqualTo(TEST_PRESET_NAME)
        expect.that(presetInfo.isAvailable).isTrue()
        expect.that(presetInfo.isWritable).isFalse()
    }

    private fun createHapPresetInfo(
        presetIndex: Int,
        presetName: String,
        isAvailable: Boolean,
        isWritable: Boolean,
    ): BluetoothHapPresetInfo {
        val out = Parcel.obtain()
        out.writeInt(presetIndex)
        out.writeString(presetName)
        out.writeBoolean(isWritable)
        out.writeBoolean(isAvailable)
        out.setDataPosition(0) // reset position of parcel before passing to constructor
        return BluetoothHapPresetInfo.CREATOR.createFromParcel(out)
    }

    companion object {
        private const val TEST_PRESET_INDEX = 15
        private const val TEST_PRESET_NAME = "Test"
    }
}
