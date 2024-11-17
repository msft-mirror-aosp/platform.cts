/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.service.settings.preferences

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.Parcel
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.settingslib.flags.Flags.FLAG_SETTINGS_CATALYST
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
@RequiresFlagsEnabled(FLAG_SETTINGS_CATALYST)
class SettingsPreferenceMetadataTest {

    @get:Rule
    val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    private lateinit var pendingIntent: PendingIntent

    @Before
    fun setup() {
        pendingIntent = PendingIntent.getActivity(
            InstrumentationRegistry.getInstrumentation().context,
            0,
            Intent().setPackage("test"),
            PendingIntent.FLAG_IMMUTABLE
        )
    }

    private val metadata: SettingsPreferenceMetadata
        get() = SettingsPreferenceMetadata.Builder("screenKey", "key")
            .setTitle("title")
            .setSummary("summary")
            .setBreadcrumbs(listOf("first", "second"))
            .setReadPermissions(listOf("readPermission"))
            .setWritePermissions(listOf("writePermission"))
            .setEnabled(true)
            .setAvailable(true)
            .setWritable(true)
            .setRestricted(true)
            .setWriteSensitivity(SettingsPreferenceMetadata.SENSITIVE)
            .setLaunchIntent(pendingIntent)
            .setExtras(Bundle().apply { putString("bKey", "bValue") })
            .build()

    @Test
    fun buildMetadata_allFieldsSet() {
        with(metadata) {
            assertThat(key).isEqualTo("key")
            assertThat(screenKey).isEqualTo("screenKey")
            assertThat(title!!).isEqualTo("title")
            assertThat(summary!!).isEqualTo("summary")
            assertThat(breadcrumbs).isEqualTo(listOf("first", "second"))
            assertThat(readPermissions).isEqualTo(listOf("readPermission"))
            assertThat(writePermissions).isEqualTo(listOf("writePermission"))
            assertThat(isEnabled).isTrue()
            assertThat(isAvailable).isTrue()
            assertThat(isWritable).isTrue()
            assertThat(isRestricted).isTrue()
            assertThat(writeSensitivity).isEqualTo(SettingsPreferenceMetadata.SENSITIVE)
            assertThat(launchIntent!!).isEqualTo(pendingIntent)
            assertThat(extras.getString("bKey")!!).isEqualTo("bValue")
        }
    }

    @Test
    fun buildMetadata_fromParcelable() {
        val parcel = Parcel.obtain()
        metadata.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)
        val new = SettingsPreferenceMetadata.CREATOR.createFromParcel(parcel)

        with(new) {
            assertThat(key).isEqualTo("key")
            assertThat(screenKey).isEqualTo("screenKey")
            assertThat(title!!).isEqualTo("title")
            assertThat(summary!!).isEqualTo("summary")
            assertThat(breadcrumbs).isEqualTo(listOf("first", "second"))
            assertThat(readPermissions).isEqualTo(listOf("readPermission"))
            assertThat(writePermissions).isEqualTo(listOf("writePermission"))
            assertThat(isEnabled).isTrue()
            assertThat(isAvailable).isTrue()
            assertThat(isWritable).isTrue()
            assertThat(isRestricted).isTrue()
            assertThat(writeSensitivity).isEqualTo(SettingsPreferenceMetadata.SENSITIVE)
            assertThat(launchIntent!!).isEqualTo(pendingIntent)
            assertThat(extras.getString("bKey")!!).isEqualTo("bValue")
        }
    }
}
