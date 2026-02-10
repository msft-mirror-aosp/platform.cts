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
class AppFunctionNameTest {

    @get:Rule val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @ApiTest(
        apis =
            [
                "android.app.appfunctions.AppFunctionName#AppFunctionName",
                "android.app.appfunctions.AppFunctionName#getPackageName",
                "android.app.appfunctions.AppFunctionName#getFunctionId",
            ]
    )
    @Test
    fun create() {
        val packageName = "com.example.app"
        val functionId = "testFunction"
        val name = AppFunctionName(packageName, functionId)

        assertThat(name.packageName).isEqualTo(packageName)
        assertThat(name.functionIdentifier).isEqualTo(functionId)
    }

    @ApiTest(
        apis =
            [
                "android.app.appfunctions.AppFunctionName#writeToParcel",
                "android.app.appfunctions.AppFunctionName#CREATOR",
            ]
    )
    @Test
    fun parcelAndUnparcel() {
        val original = AppFunctionName("com.example.app", "testFunction")

        val restored = parcelAndUnparcel(original)

        assertThat(restored).isEqualTo(original)
        assertThat(restored.packageName).isEqualTo(original.packageName)
        assertThat(restored.functionIdentifier).isEqualTo(original.functionIdentifier)
    }

    @ApiTest(
        apis =
            [
                "android.app.appfunctions.AppFunctionName#equals",
                "android.app.appfunctions.AppFunctionName#hashCode",
            ]
    )
    @Test
    fun equalsAndHashCode() {
        val name1 = AppFunctionName("pkg", "func")
        val name2 = AppFunctionName("pkg", "func")
        val name3 = AppFunctionName("pkg2", "func")
        val name4 = AppFunctionName("pkg", "func2")

        assertThat(name1).isEqualTo(name2)
        assertThat(name1.hashCode()).isEqualTo(name2.hashCode())
        assertThat(name1).isNotEqualTo(name3)
        assertThat(name1.hashCode()).isNotEqualTo(name3.hashCode())
        assertThat(name1).isNotEqualTo(name4)
        assertThat(name1.hashCode()).isNotEqualTo(name4.hashCode())
    }

    private fun parcelAndUnparcel(original: AppFunctionName): AppFunctionName {
        val parcel = Parcel.obtain()
        try {
            original.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            return AppFunctionName.CREATOR.createFromParcel(parcel)
        } finally {
            parcel.recycle()
        }
    }
}
