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

package android.app.cts

import android.app.AppInteractionAttribution
import android.app.appfunctions.flags.Flags
import android.net.Uri
import android.os.Parcel
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.compatibility.common.util.ApiTest
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_APP_INTERACTION_API)
class AppInteractionAttributionTest {
    @get:Rule val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @ApiTest(
        apis =
            [
                "android.app.AppInteractionAttribution.Builder#Builder",
                "android.app.AppInteractionAttribution.Builder#build",
            ]
    )
    @Test
    fun build_interactionTypeOther_withoutCustomType() {
        assertThrows(IllegalArgumentException::class.java) {
            AppInteractionAttribution.Builder(AppInteractionAttribution.INTERACTION_TYPE_OTHER)
                .build()
        }
    }

    @ApiTest(
        apis =
            [
                "android.app.AppInteractionAttribution.Builder#Builder",
                "android.app.AppInteractionAttribution.Builder#setCustomInteractionType",
                "android.app.AppInteractionAttribution.Builder#build",
                "android.app.AppInteractionAttribution#writeToParcel",
                "android.app.AppInteractionAttribution#CREATOR",
                "android.app.AppInteractionAttribution#getInteractionType",
                "android.app.AppInteractionAttribution#getCustomInteractionType",
                "android.app.AppInteractionAttribution#getInteractionUri",
            ]
    )
    @Test
    fun build_interactionTypeOther_withCustomType_noOtherParams() {
        val attribution =
            AppInteractionAttribution.Builder(AppInteractionAttribution.INTERACTION_TYPE_OTHER)
                .setCustomInteractionType("CustomInteractionType")
                .build()

        val restored = parcelAndUnparcel(attribution)

        assertThat(restored.interactionType)
            .isEqualTo(AppInteractionAttribution.INTERACTION_TYPE_OTHER)
        assertThat(restored.customInteractionType).isEqualTo("CustomInteractionType")
        assertThat(restored.interactionUri).isNull()
    }

    @ApiTest(
        apis =
            [
                "android.app.AppInteractionAttribution.Builder#Builder",
                "android.app.AppInteractionAttribution.Builder#setCustomInteractionType",
                "android.app.AppInteractionAttribution.Builder#setInteractionUri",
                "android.app.AppInteractionAttribution.Builder#build",
                "android.app.AppInteractionAttribution#writeToParcel",
                "android.app.AppInteractionAttribution#CREATOR",
                "android.app.AppInteractionAttribution#getInteractionType",
                "android.app.AppInteractionAttribution#getCustomInteractionType",
                "android.app.AppInteractionAttribution#getInteractionUri",
            ]
    )
    @Test
    fun build_interactionTypeOther_withAllParams() {
        val attribution =
            AppInteractionAttribution.Builder(AppInteractionAttribution.INTERACTION_TYPE_OTHER)
                .setCustomInteractionType("CustomInteractionType")
                .setInteractionUri(Uri.parse("content://com.example/android"))
                .build()

        val restored = parcelAndUnparcel(attribution)

        assertThat(restored.interactionType)
            .isEqualTo(AppInteractionAttribution.INTERACTION_TYPE_OTHER)
        assertThat(restored.customInteractionType).isEqualTo("CustomInteractionType")
        assertThat(restored.interactionUri).isEqualTo(Uri.parse("content://com.example/android"))
    }

    @ApiTest(
        apis =
            [
                "android.app.AppInteractionAttribution.Builder#Builder",
                "android.app.AppInteractionAttribution.Builder#setCustomInteractionType",
                "android.app.AppInteractionAttribution.Builder#build",
                "android.app.AppInteractionAttribution#writeToParcel",
                "android.app.AppInteractionAttribution#CREATOR",
                "android.app.AppInteractionAttribution#getInteractionType",
                "android.app.AppInteractionAttribution#getCustomInteractionType",
                "android.app.AppInteractionAttribution#getInteractionUri",
            ]
    )
    @Test
    fun build_interactionTypeUserQuery_withCustomType() {
        assertThrows(IllegalArgumentException::class.java) {
            AppInteractionAttribution.Builder(AppInteractionAttribution.INTERACTION_TYPE_USER_QUERY)
                .setCustomInteractionType("CustomInteractionType")
                .build()
        }
    }

    @ApiTest(
        apis =
            [
                "android.app.AppInteractionAttribution.Builder#Builder",
                "android.app.AppInteractionAttribution.Builder#setInteractionUri",
                "android.app.AppInteractionAttribution.Builder#build",
                "android.app.AppInteractionAttribution#writeToParcel",
                "android.app.AppInteractionAttribution#CREATOR",
                "android.app.AppInteractionAttribution#getInteractionType",
                "android.app.AppInteractionAttribution#getCustomInteractionType",
                "android.app.AppInteractionAttribution#getInteractionUri",
            ]
    )
    @Test
    fun build_interactionTypeUserQuery_onlyWithoutCustomType() {
        val attribution =
            AppInteractionAttribution.Builder(AppInteractionAttribution.INTERACTION_TYPE_USER_QUERY)
                .setInteractionUri(Uri.parse("content://com.example/android"))
                .build()

        val restored = parcelAndUnparcel(attribution)

        assertThat(restored.interactionType)
            .isEqualTo(AppInteractionAttribution.INTERACTION_TYPE_USER_QUERY)
        assertThat(restored.customInteractionType).isNull()
        assertThat(restored.interactionUri).isEqualTo(Uri.parse("content://com.example/android"))
    }

    @Test
    fun buildFromParcelWithMissingCustomInteractionType_shouldFail() {
        val parcel =
            Parcel.obtain().apply {
                writeInt(AppInteractionAttribution.INTERACTION_TYPE_OTHER)
                writeString(null)
                writeTypedObject(null, 0)
            }

        assertThrows(IllegalArgumentException::class.java) {
            AppInteractionAttribution.CREATOR.createFromParcel(parcel)
        }
    }

    @Test
    fun buildFromParcelWithInvalidInteractionType_shouldFail() {
        val parcel =
            Parcel.obtain().apply {
                writeInt(AppInteractionAttribution.INTERACTION_TYPE_USER_QUERY)
                writeString("CUSTOM_TYPE")
                writeTypedObject(null, 0)
            }

        assertThrows(IllegalArgumentException::class.java) {
            AppInteractionAttribution.CREATOR.createFromParcel(parcel)
        }
    }

    private fun parcelAndUnparcel(original: AppInteractionAttribution): AppInteractionAttribution {
        val parcel = Parcel.obtain()
        try {
            original.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            return AppInteractionAttribution.CREATOR.createFromParcel(parcel)
        } finally {
            parcel.recycle()
        }
    }
}
