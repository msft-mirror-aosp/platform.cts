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

import android.app.appfunctions.AppFunctionPackageMetadata
import android.app.appfunctions.flags.Flags
import android.app.appsearch.GenericDocument
import android.os.Parcel
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_DYNAMIC_APP_FUNCTIONS)
class AppFunctionPackageMetadataTest {

    @get:Rule val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @Test
    fun parcelAndUnparcel() {
        val packageMetadata =
            AppFunctionPackageMetadata.create(
                "com.example.app",
                listOf(
                    GenericDocument.Builder<GenericDocument.Builder<*>>("ns", "id", "schema")
                        .setPropertyString("key", "value")
                        .setCreationTimestampMillis(0)
                        .build()
                ),
            )
        val list = listOf(packageMetadata, packageMetadata)
        val parcelForRestore = Parcel.obtain()
        try {
            parcelForRestore.writeTypedList(list)
            parcelForRestore.setDataPosition(0)
            val restoredList =
                parcelForRestore.createTypedArrayList(AppFunctionPackageMetadata.CREATOR)

            assertThat(restoredList).hasSize(2)
            assertThat(restoredList!![0]).isEqualTo(packageMetadata)
            assertThat(restoredList[1]).isEqualTo(packageMetadata)
            assertThat(restoredList[0]).isNotSameInstanceAs(restoredList[1])
        } finally {
            parcelForRestore.recycle()
        }
    }
}
