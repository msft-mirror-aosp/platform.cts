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

import android.app.appfunctions.AppFunctionAttribution
import android.net.Uri
import android.os.Parcel
import android.permission.flags.Flags.FLAG_APP_FUNCTION_ACCESS_API_ENABLED
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.compatibility.common.util.ApiTest
import com.google.common.truth.Truth.assertThat
import java.lang.IllegalArgumentException
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@RequiresFlagsEnabled(FLAG_APP_FUNCTION_ACCESS_API_ENABLED)
class AppFunctionAttributionTest {
    @get:Rule val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @ApiTest(
        apis =
            [
                "android.app.appfunctions.AppFunctionAttribution.Builder#Builder",
                "android.app.appfunctions.AppFunctionAttribution.Builder#build",
            ]
    )
    @Test
    fun build_interactionTypeOther_withoutCustomType() {
        assertThrows(IllegalArgumentException::class.java) {
            AppFunctionAttribution.Builder(AppFunctionAttribution.INTERACTION_TYPE_OTHER).build()
        }
    }

    @ApiTest(
        apis =
            [
                "android.app.appfunctions.AppFunctionAttribution.Builder#Builder",
                "android.app.appfunctions.AppFunctionAttribution.Builder#setCustomInteractionType",
                "android.app.appfunctions.AppFunctionAttribution.Builder#build",
                "android.app.appfunctions.AppFunctionAttribution#writeToParcel",
                "android.app.appfunctions.AppFunctionAttribution#CREATOR",
                "android.app.appfunctions.AppFunctionAttribution#getInteractionType",
                "android.app.appfunctions.AppFunctionAttribution#getCustomInteractionType",
                "android.app.appfunctions.AppFunctionAttribution#getThreadId",
                "android.app.appfunctions.AppFunctionAttribution#getInteractionUri",
            ]
    )
    @Test
    fun build_interactionTypeOther_withCustomType_noOtherParams() {
        val attribution =
            AppFunctionAttribution.Builder(AppFunctionAttribution.INTERACTION_TYPE_OTHER)
                .setCustomInteractionType("CustomInteractionType")
                .build()

        val restored = parcelAndUnparcel(attribution)

        assertThat(restored.interactionType)
            .isEqualTo(AppFunctionAttribution.INTERACTION_TYPE_OTHER)
        assertThat(restored.customInteractionType).isEqualTo("CustomInteractionType")
        assertThat(restored.threadId).isNull()
        assertThat(restored.interactionUri).isNull()
    }

    @ApiTest(
        apis =
            [
                "android.app.appfunctions.AppFunctionAttribution.Builder#Builder",
                "android.app.appfunctions.AppFunctionAttribution.Builder#setCustomInteractionType",
                "android.app.appfunctions.AppFunctionAttribution.Builder#setThreadId",
                "android.app.appfunctions.AppFunctionAttribution.Builder#setInteractionUri",
                "android.app.appfunctions.AppFunctionAttribution.Builder#build",
                "android.app.appfunctions.AppFunctionAttribution#writeToParcel",
                "android.app.appfunctions.AppFunctionAttribution#CREATOR",
                "android.app.appfunctions.AppFunctionAttribution#getInteractionType",
                "android.app.appfunctions.AppFunctionAttribution#getCustomInteractionType",
                "android.app.appfunctions.AppFunctionAttribution#getThreadId",
                "android.app.appfunctions.AppFunctionAttribution#getInteractionUri",
            ]
    )
    @Test
    fun build_interactionTypeOther_withAllParams() {
        val attribution =
            AppFunctionAttribution.Builder(AppFunctionAttribution.INTERACTION_TYPE_OTHER)
                .setCustomInteractionType("CustomInteractionType")
                .setThreadId("ThreadId")
                .setInteractionUri(Uri.parse("content://com.example/android"))
                .build()

        val restored = parcelAndUnparcel(attribution)

        assertThat(restored.interactionType)
            .isEqualTo(AppFunctionAttribution.INTERACTION_TYPE_OTHER)
        assertThat(restored.customInteractionType).isEqualTo("CustomInteractionType")
        assertThat(restored.threadId).isEqualTo("ThreadId")
        assertThat(restored.interactionUri).isEqualTo(Uri.parse("content://com.example/android"))
    }

    @ApiTest(
        apis =
            [
                "android.app.appfunctions.AppFunctionAttribution.Builder#Builder",
                "android.app.appfunctions.AppFunctionAttribution.Builder#setCustomInteractionType",
                "android.app.appfunctions.AppFunctionAttribution.Builder#build",
                "android.app.appfunctions.AppFunctionAttribution#writeToParcel",
                "android.app.appfunctions.AppFunctionAttribution#CREATOR",
                "android.app.appfunctions.AppFunctionAttribution#getInteractionType",
                "android.app.appfunctions.AppFunctionAttribution#getCustomInteractionType",
                "android.app.appfunctions.AppFunctionAttribution#getThreadId",
                "android.app.appfunctions.AppFunctionAttribution#getInteractionUri",
            ]
    )
    @Test
    fun build_interactionTypeUserQuery_withCustomType() {
        assertThrows(IllegalArgumentException::class.java) {
            AppFunctionAttribution.Builder(AppFunctionAttribution.INTERACTION_TYPE_USER_QUERY)
                .setCustomInteractionType("CustomInteractionType")
                .build()
        }
    }

    @ApiTest(
        apis =
            [
                "android.app.appfunctions.AppFunctionAttribution.Builder#Builder",
                "android.app.appfunctions.AppFunctionAttribution.Builder#setThreadId",
                "android.app.appfunctions.AppFunctionAttribution.Builder#setInteractionUri",
                "android.app.appfunctions.AppFunctionAttribution.Builder#build",
                "android.app.appfunctions.AppFunctionAttribution#writeToParcel",
                "android.app.appfunctions.AppFunctionAttribution#CREATOR",
                "android.app.appfunctions.AppFunctionAttribution#getInteractionType",
                "android.app.appfunctions.AppFunctionAttribution#getCustomInteractionType",
                "android.app.appfunctions.AppFunctionAttribution#getThreadId",
                "android.app.appfunctions.AppFunctionAttribution#getInteractionUri",
            ]
    )
    @Test
    fun build_interactionTypeUserQuery_onlyWithoutCustomType() {
        val attribution =
            AppFunctionAttribution.Builder(AppFunctionAttribution.INTERACTION_TYPE_USER_QUERY)
                .setThreadId("ThreadId")
                .setInteractionUri(Uri.parse("content://com.example/android"))
                .build()

        val restored = parcelAndUnparcel(attribution)

        assertThat(restored.interactionType)
            .isEqualTo(AppFunctionAttribution.INTERACTION_TYPE_USER_QUERY)
        assertThat(restored.customInteractionType).isNull()
        assertThat(restored.threadId).isEqualTo("ThreadId")
        assertThat(restored.interactionUri).isEqualTo(Uri.parse("content://com.example/android"))
    }

    private fun parcelAndUnparcel(original: AppFunctionAttribution): AppFunctionAttribution {
        val parcel = Parcel.obtain()
        try {
            original.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            return AppFunctionAttribution.CREATOR.createFromParcel(parcel)
        } finally {
            parcel.recycle()
        }
    }
}
