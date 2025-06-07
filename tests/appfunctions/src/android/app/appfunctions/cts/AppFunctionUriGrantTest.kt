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

import android.app.appfunctions.AppFunctionUriGrant
import android.content.Intent
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
class AppFunctionUriGrantTest {
    @get:Rule val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @Test
    fun build_AppFunctionUriGrantWithoutAccessMode_shouldFail() {
        val uri = Uri.EMPTY
        val modeFlags = 0

        assertThrows(IllegalArgumentException::class.java) { AppFunctionUriGrant(uri, modeFlags) }
    }

    @Test
    fun build_AppFunctionUriGrantWithPersistFlag_shouldFail() {
        val uri = Uri.EMPTY
        val modeFlags =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION

        assertThrows(IllegalArgumentException::class.java) { AppFunctionUriGrant(uri, modeFlags) }
    }

    @ApiTest(
        apis =
            [
                "android.app.appfunctions.AppFunctionUriGrant#CREATOR",
                "android.app.appfunctions.AppFunctionUriGrant#writeToParcel",
                "android.app.appfunctions.AppFunctionUriGrant#getUri",
                "android.app.appfunctions.AppFunctionUriGrant#getModeFlags",
            ]
    )
    @Test
    fun build_AppFunctionUriGrantWithReadAccessMode() {
        val uri = Uri.parse("content://com.android/example")
        val modeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        val grantUri = AppFunctionUriGrant(uri, modeFlags)

        val restored = parcelAndUnparcel(grantUri)

        assertThat(restored.uri).isEqualTo(uri)
        assertThat(restored.modeFlags).isEqualTo(modeFlags)
    }

    @ApiTest(
        apis =
            [
                "android.app.appfunctions.AppFunctionUriGrant#CREATOR",
                "android.app.appfunctions.AppFunctionUriGrant#writeToParcel",
                "android.app.appfunctions.AppFunctionUriGrant#getUri",
                "android.app.appfunctions.AppFunctionUriGrant#getModeFlags",
            ]
    )
    @Test
    fun build_AppFunctionUriGrantWithWriteAccessMode() {
        val uri = Uri.parse("content://com.android/example")
        val modeFlags = Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val grantUri = AppFunctionUriGrant(uri, modeFlags)

        val restored = parcelAndUnparcel(grantUri)

        assertThat(restored.uri).isEqualTo(uri)
        assertThat(restored.modeFlags).isEqualTo(modeFlags)
    }

    @ApiTest(
        apis =
            [
                "android.app.appfunctions.AppFunctionUriGrant#CREATOR",
                "android.app.appfunctions.AppFunctionUriGrant#writeToParcel",
                "android.app.appfunctions.AppFunctionUriGrant#getUri",
                "android.app.appfunctions.AppFunctionUriGrant#getModeFlags",
            ]
    )
    @Test
    fun build_AppFunctionUriGrantWithReadWriteAccessMode() {
        val uri = Uri.parse("content://com.android/example")
        val modeFlags =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val grantUri = AppFunctionUriGrant(uri, modeFlags)

        val restored = parcelAndUnparcel(grantUri)

        assertThat(restored.uri).isEqualTo(uri)
        assertThat(restored.modeFlags).isEqualTo(modeFlags)
    }

    private fun parcelAndUnparcel(original: AppFunctionUriGrant): AppFunctionUriGrant {
        val parcel = Parcel.obtain()
        try {
            original.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            return AppFunctionUriGrant.CREATOR.createFromParcel(parcel)
        } finally {
            parcel.recycle()
        }
    }
}
