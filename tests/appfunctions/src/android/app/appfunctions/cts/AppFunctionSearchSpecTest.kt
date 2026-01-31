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
import android.app.appfunctions.AppFunctionSearchSpec
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
class AppFunctionSearchSpecTest {

    @get:Rule val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @ApiTest(
        apis =
            [
                "android.app.appfunctions.AppFunctionSearchSpec.Builder#Builder",
                "android.app.appfunctions.AppFunctionSearchSpec.Builder#setPackageNames",
                "android.app.appfunctions.AppFunctionSearchSpec.Builder#setFunctionNames",
                "android.app.appfunctions.AppFunctionSearchSpec.Builder#setSchemaCategory",
                "android.app.appfunctions.AppFunctionSearchSpec.Builder#setSchemaName",
                "android.app.appfunctions.AppFunctionSearchSpec.Builder#setMinSchemaVersion",
                "android.app.appfunctions.AppFunctionSearchSpec.Builder#build",
                "android.app.appfunctions.AppFunctionSearchSpec#getPackageNames",
                "android.app.appfunctions.AppFunctionSearchSpec#getFunctionNames",
                "android.app.appfunctions.AppFunctionSearchSpec#getSchemaCategory",
                "android.app.appfunctions.AppFunctionSearchSpec#getSchemaName",
                "android.app.appfunctions.AppFunctionSearchSpec#getMinSchemaVersion",
            ]
    )
    @Test
    fun create() {
        val packageNames = listOf("pkg1", "pkg2")
        val functionNames = listOf(AppFunctionName("pkg1", "id1"), AppFunctionName("pkg2", "id2"))
        val schemaCategory = "category"
        val schemaName = "name"
        val minSchemaVersion = 2L

        val spec =
            AppFunctionSearchSpec.Builder()
                .setPackageNames(packageNames)
                .setFunctionNames(functionNames)
                .setSchemaCategory(schemaCategory)
                .setSchemaName(schemaName)
                .setMinSchemaVersion(minSchemaVersion)
                .build()

        assertThat(spec.packageNames).containsExactlyElementsIn(packageNames).inOrder()
        assertThat(spec.functionNames).containsExactlyElementsIn(functionNames).inOrder()
        assertThat(spec.schemaCategory).isEqualTo(schemaCategory)
        assertThat(spec.schemaName).isEqualTo(schemaName)
        assertThat(spec.minSchemaVersion).isEqualTo(minSchemaVersion)
    }

    @ApiTest(
        apis =
            [
                "android.app.appfunctions.AppFunctionSearchSpec#writeToParcel",
                "android.app.appfunctions.AppFunctionSearchSpec#CREATOR",
            ]
    )
    @Test
    fun parcelAndUnparcel() {
        val original =
            AppFunctionSearchSpec.Builder()
                .setPackageNames(listOf("pkg1", "pkg2"))
                .setFunctionNames(
                    listOf(AppFunctionName("pkg1", "id1"), AppFunctionName("pkg2", "id2"))
                )
                .setSchemaCategory("category")
                .setSchemaName("name")
                .setMinSchemaVersion(2L)
                .build()

        val restored = parcelAndUnparcel(original)

        assertThat(restored).isEqualTo(original)
        assertThat(restored.packageNames).isEqualTo(original.packageNames)
        assertThat(restored.functionNames).isEqualTo(original.functionNames)
        assertThat(restored.schemaCategory).isEqualTo(original.schemaCategory)
        assertThat(restored.schemaName).isEqualTo(original.schemaName)
        assertThat(restored.minSchemaVersion).isEqualTo(original.minSchemaVersion)
    }

    @ApiTest(
        apis =
            [
                "android.app.appfunctions.AppFunctionSearchSpec#equals",
                "android.app.appfunctions.AppFunctionSearchSpec#hashCode",
            ]
    )
    @Test
    fun equalsAndHashCode() {
        val spec1 =
            AppFunctionSearchSpec.Builder()
                .setPackageNames(listOf("pkg"))
                .setSchemaCategory("cat")
                .build()
        val spec2 =
            AppFunctionSearchSpec.Builder()
                .setPackageNames(listOf("pkg"))
                .setSchemaCategory("cat")
                .build()
        val spec3 =
            AppFunctionSearchSpec.Builder()
                .setPackageNames(listOf("pkg2"))
                .setSchemaCategory("cat")
                .build()

        assertThat(spec1).isEqualTo(spec2)
        assertThat(spec1.hashCode()).isEqualTo(spec2.hashCode())
        assertThat(spec1).isNotEqualTo(spec3)
        assertThat(spec1.hashCode()).isNotEqualTo(spec3.hashCode())
    }

    private fun parcelAndUnparcel(original: AppFunctionSearchSpec): AppFunctionSearchSpec {
        val parcel = Parcel.obtain()
        try {
            original.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            return AppFunctionSearchSpec.CREATOR.createFromParcel(parcel)
        } finally {
            parcel.recycle()
        }
    }
}
