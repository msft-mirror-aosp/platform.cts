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
package android.hardware.input.cts.tests.virtualdevices

import android.companion.virtualdevice.flags.Flags
import android.hardware.input.ViewBehaviorConfig
import android.os.Parcel
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RequiresFlagsEnabled(Flags.FLAG_VIRTUAL_INPUT_VIEW_BEHAVIOR)
@SmallTest
@RunWith(AndroidJUnit4::class)
class ViewBehaviorConfigTest {
    @get:Rule
    val mCheckFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @Test
    fun testBuilderAndGetters() {
        val config = ViewBehaviorConfig.Builder()
            .setPrimaryDirectionalMotionAxis(MotionEvent.AXIS_X)
            .setShouldSmoothScroll(true)
            .build()

        assertThat(config.primaryDirectionalMotionAxis).isEqualTo(MotionEvent.AXIS_X)
        assertThat(config.shouldSmoothScroll()).isTrue()
    }

    @Test
    fun testDefaults() {
        val config = ViewBehaviorConfig.Builder().build()

        assertThat(config.primaryDirectionalMotionAxis)
            .isEqualTo(UNSPECIFIED_PRIMARY_DIRECTIONAL_MOTION_AXIS)
        assertThat(config.shouldSmoothScroll()).isFalse()
    }

    @Test
    fun testParcelAndUnparcel() {
        val config = ViewBehaviorConfig.Builder()
            .setPrimaryDirectionalMotionAxis(MotionEvent.AXIS_Y)
            .setShouldSmoothScroll(true)
            .build()
        val parcel = Parcel.obtain()
        config.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)

        val configFromParcel = ViewBehaviorConfig.CREATOR.createFromParcel(parcel)
        assertThat(configFromParcel.primaryDirectionalMotionAxis)
            .isEqualTo(config.primaryDirectionalMotionAxis)
        assertThat(configFromParcel.shouldSmoothScroll())
            .isEqualTo(config.shouldSmoothScroll())
        parcel.recycle()
    }

    @Test
    fun invalidAxis_throwsException() {
        // Test with unspecified value
        assertThrows(IllegalArgumentException::class.java) {
            ViewBehaviorConfig.Builder()
                .setPrimaryDirectionalMotionAxis(UNSPECIFIED_PRIMARY_DIRECTIONAL_MOTION_AXIS)
        }
        // Test with an arbitrarily large negative value
        assertThrows(IllegalArgumentException::class.java) {
            ViewBehaviorConfig.Builder().setPrimaryDirectionalMotionAxis(-1000)
        }
        // Test with an arbitrarily large positive value
        assertThrows(IllegalArgumentException::class.java) {
            ViewBehaviorConfig.Builder().setPrimaryDirectionalMotionAxis(1000)
        }
    }

    companion object {
        private const val UNSPECIFIED_PRIMARY_DIRECTIONAL_MOTION_AXIS = -1
    }
}
