/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.app.appfunctions.cts

import android.app.appfunctions.AppFunctionMetadata
import android.app.appfunctions.AppFunctionName
import android.app.appfunctions.AppFunctionSearchSpec
import android.app.appfunctions.flags.Flags
import android.os.Parcel
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_DYNAMIC_APP_FUNCTIONS)
class AppFunctionSearchSpecTest {

    @get:Rule val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @Test
    fun create() {
        val packageNames = setOf("pkg1", "pkg2")
        val functionNames = setOf(AppFunctionName("pkg1", "id1"), AppFunctionName("pkg2", "id2"))
        val schemaCategory = "category"
        val schemaName = "name"
        val minSchemaVersion = 2L
        val scopes = setOf(AppFunctionMetadata.SCOPE_GLOBAL)

        val spec =
            AppFunctionSearchSpec.Builder()
                .setPackageNames(packageNames)
                .setFunctionNames(functionNames)
                .setSchemaCategory(schemaCategory)
                .setSchemaName(schemaName)
                .setMinSchemaVersion(minSchemaVersion)
                .setScopes(scopes)
                .build()

        assertThat(spec.packageNames).containsExactlyElementsIn(packageNames).inOrder()
        assertThat(spec.functionNames).containsExactlyElementsIn(functionNames).inOrder()
        assertThat(spec.schemaCategory).isEqualTo(schemaCategory)
        assertThat(spec.schemaName).isEqualTo(schemaName)
        assertThat(spec.minSchemaVersion).isEqualTo(minSchemaVersion)
    }

    @Test
    fun parcelAndUnparcel_allFieldsSet() {
        val original = SEARCH_SPEC_WITH_ALL_PROPERTIES

        val restored = parcelAndUnparcel(original)

        assertThat(restored).isEqualTo(original)
    }

    @Test
    fun parcelAndUnparcel_minimalFields() {
        val original = AppFunctionSearchSpec.Builder().build()

        val restored = parcelAndUnparcel(original)

        assertThat(restored).isEqualTo(original)
    }

    @Test
    fun equalsAndHashCode() {
        val base = SEARCH_SPEC_WITH_ALL_PROPERTIES

        val sameAsBase =
            AppFunctionSearchSpec.Builder()
                .setPackageNames(setOf("testPackage1", "testPackage2"))
                .setFunctionNames(
                    setOf(
                        AppFunctionName("testPackage1", "id1"),
                        AppFunctionName("testPackage2", "id2"),
                    )
                )
                .setSchemaCategory("testCategory")
                .setSchemaName("testName")
                .setMinSchemaVersion(1L)
                .setScopes(
                    setOf(AppFunctionMetadata.SCOPE_GLOBAL, AppFunctionMetadata.SCOPE_ACTIVITY)
                )
                .build()

        val differentPackage =
            AppFunctionSearchSpec.Builder(base).setPackageNames(setOf("other")).build()
        val differentFunction =
            AppFunctionSearchSpec.Builder(base)
                .setFunctionNames(setOf(AppFunctionName("p", "i")))
                .build()
        val differentCategory =
            AppFunctionSearchSpec.Builder(base).setSchemaCategory("other").build()
        val differentName = AppFunctionSearchSpec.Builder(base).setSchemaName("other").build()
        val differentVersion = AppFunctionSearchSpec.Builder(base).setMinSchemaVersion(99L).build()
        val differentScopes =
            AppFunctionSearchSpec.Builder(base)
                .setScopes(setOf(AppFunctionMetadata.SCOPE_GLOBAL))
                .build()

        assertThat(base).isEqualTo(sameAsBase)
        assertThat(base.hashCode()).isEqualTo(sameAsBase.hashCode())

        assertThat(base).isNotEqualTo(differentPackage)
        assertThat(base).isNotEqualTo(differentFunction)
        assertThat(base).isNotEqualTo(differentCategory)
        assertThat(base).isNotEqualTo(differentName)
        assertThat(base).isNotEqualTo(differentVersion)
        assertThat(base).isNotEqualTo(differentScopes)

        assertThat(base.hashCode()).isNotEqualTo(differentPackage.hashCode())
    }

    @Test
    fun builder_emptyCollections_throwsException() {
        assertThrows(IllegalArgumentException::class.java) {
            AppFunctionSearchSpec.Builder().setPackageNames(emptySet())
        }
        assertThrows(IllegalArgumentException::class.java) {
            AppFunctionSearchSpec.Builder().setFunctionNames(emptySet())
        }
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

    companion object {
        private val SEARCH_SPEC_WITH_ALL_PROPERTIES =
            AppFunctionSearchSpec.Builder()
                .setPackageNames(setOf("testPackage1", "testPackage2"))
                .setFunctionNames(
                    setOf(
                        AppFunctionName("testPackage1", "id1"),
                        AppFunctionName("testPackage2", "id2"),
                    )
                )
                .setSchemaCategory("testCategory")
                .setSchemaName("testName")
                .setMinSchemaVersion(1L)
                .setScopes(
                    setOf(AppFunctionMetadata.SCOPE_GLOBAL, AppFunctionMetadata.SCOPE_ACTIVITY)
                )
                .build()
    }
}
