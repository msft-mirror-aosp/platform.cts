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

package android.app.appfunctions.cts

import android.app.appfunctions.AppFunctionName
import android.app.appfunctions.AppFunctionState
import android.app.appfunctions.flags.Flags
import android.os.Parcel
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.compatibility.common.util.ApiTest
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_DYNAMIC_APP_FUNCTIONS)
class AppFunctionStateTest {

    @get:Rule val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @ApiTest(
        apis =
            [
                "android.app.appfunctions.AppFunctionState#AppFunctionState",
                "android.app.appfunctions.AppFunctionState#getFunctionName",
                "android.app.appfunctions.AppFunctionState#isEnabled",
                "android.app.appfunctions.AppFunctionState#writeToParcel",
                "android.app.appfunctions.AppFunctionState#CREATOR",
                "android.app.appfunctions.AppFunctionState#equals",
                "android.app.appfunctions.AppFunctionState#hashCode",
            ]
    )
    @Test
    fun constructor_andGetters_enabled() {
        val functionName = AppFunctionName("com.example.package", "testFunctionId")
        val isEnabled = true

        val state = AppFunctionState(functionName, isEnabled)

        val restoredState = parcelAndUnparcel(state)

        assertThat(restoredState.functionName).isEqualTo(functionName)
        assertThat(restoredState.isEnabled).isTrue()
        assertThat(restoredState).isEqualTo(state)
        assertThat(restoredState.hashCode()).isEqualTo(state.hashCode())
    }

    @ApiTest(
        apis =
            [
                "android.app.appfunctions.AppFunctionState#AppFunctionState",
                "android.app.appfunctions.AppFunctionState#getFunctionName",
                "android.app.appfunctions.AppFunctionState#isEnabled",
                "android.app.appfunctions.AppFunctionState#writeToParcel",
                "android.app.appfunctions.AppFunctionState#CREATOR",
            ]
    )
    @Test
    fun constructor_andGetters_disabled() {
        val functionName = AppFunctionName("com.example.package", "testFunctionId")
        val isEnabled = false

        val state = AppFunctionState(functionName, isEnabled)

        val restoredState = parcelAndUnparcel(state)

        assertThat(restoredState.functionName).isEqualTo(functionName)
        assertThat(restoredState.isEnabled).isFalse()
    }

    @ApiTest(
        apis =
            [
                "android.app.appfunctions.AppFunctionState#equals",
                "android.app.appfunctions.AppFunctionState#hashCode",
            ]
    )
    @Test
    fun equals_andHashCode() {
        val name1 = AppFunctionName("com.example", "func1")
        val name2 = AppFunctionName("com.example", "func2")

        val state1 = AppFunctionState(name1, true)
        val state1Copy = AppFunctionState(name1, true)
        val state2 = AppFunctionState(name2, true)
        val state3 = AppFunctionState(name1, false)

        assertThat(state1).isEqualTo(state1)

        assertThat(state1).isEqualTo(state1Copy)
        assertThat(state1.hashCode()).isEqualTo(state1Copy.hashCode())

        assertThat(state1).isNotEqualTo(state2)
        assertThat(state1.hashCode()).isNotEqualTo(state2.hashCode())
        assertThat(state1).isNotEqualTo(state3)
        assertThat(state1.hashCode()).isNotEqualTo(state3.hashCode())
    }

    private fun parcelAndUnparcel(original: AppFunctionState): AppFunctionState {
        val parcel = Parcel.obtain()
        try {
            original.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            return AppFunctionState.CREATOR.createFromParcel(parcel)
        } finally {
            parcel.recycle()
        }
    }
}
