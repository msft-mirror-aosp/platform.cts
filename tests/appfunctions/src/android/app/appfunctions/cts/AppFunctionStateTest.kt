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

import android.app.appfunctions.AppFunctionActivityId
import android.app.appfunctions.AppFunctionName
import android.app.appfunctions.AppFunctionState
import android.app.appfunctions.flags.Flags
import android.os.Binder
import android.os.Parcel
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.util.ArraySet
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
                "android.app.appfunctions.AppFunctionState#getActivityIds",
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
        val activityIds = ArraySet(listOf(AppFunctionActivityId(Binder())))

        val state = AppFunctionState(functionName, isEnabled, activityIds)

        val restoredState = parcelAndUnparcel(state)

        assertThat(restoredState.functionName).isEqualTo(functionName)
        assertThat(restoredState.isEnabled).isTrue()
        assertThat(restoredState.activityIds).isEqualTo(activityIds)
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
        val activityIds: ArraySet<AppFunctionActivityId>? = null

        val state = AppFunctionState(functionName, isEnabled, activityIds)

        val restoredState = parcelAndUnparcel(state)

        assertThat(restoredState.functionName).isEqualTo(functionName)
        assertThat(restoredState.isEnabled).isFalse()
        assertThat(restoredState.activityIds).isNull()
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
        val activityId1 = AppFunctionActivityId(Binder())
        val activityId2 = AppFunctionActivityId(Binder())

        val state = AppFunctionState(name1, true, ArraySet(listOf(activityId1)))
        val sameState = AppFunctionState(name1, true, ArraySet(listOf(activityId1)))
        val differentName = AppFunctionState(name2, true, ArraySet(listOf(activityId1)))
        val differentEnabled = AppFunctionState(name1, false, ArraySet(listOf(activityId1)))
        val differentActivityId = AppFunctionState(name1, true, ArraySet(listOf(activityId2)))
        val nullActivityIds = AppFunctionState(name1, true, null)

        assertThat(state).isEqualTo(state) // Same instance.

        assertThat(state).isEqualTo(sameState)
        assertThat(state.hashCode()).isEqualTo(sameState.hashCode())

        assertThat(state).isNotEqualTo(differentName)
        assertThat(state.hashCode()).isNotEqualTo(differentName.hashCode())
        assertThat(state).isNotEqualTo(differentEnabled)
        assertThat(state.hashCode()).isNotEqualTo(differentEnabled.hashCode())
        assertThat(state).isNotEqualTo(differentActivityId)
        assertThat(state.hashCode()).isNotEqualTo(differentActivityId.hashCode())
        assertThat(state).isNotEqualTo(nullActivityIds)
        assertThat(state.hashCode()).isNotEqualTo(nullActivityIds.hashCode())
        assertThat(nullActivityIds).isEqualTo(nullActivityIds)
        assertThat(nullActivityIds.hashCode()).isEqualTo(nullActivityIds.hashCode())
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
