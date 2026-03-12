/*
 * Copyright (C) 2026 The Android Open Source Project
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

package android.virtualdevice.cts.computercontrol

import android.companion.virtual.VirtualDeviceManager
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.extensions.computercontrol.ComputerControlExtensions
import com.google.common.truth.Truth.assertThat
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComputerControlSetupTest {

    private val context = getInstrumentation().context

    @Test
    fun testComputerControl_isSetupCorrectly() {
        val packageManager = context.packageManager

        assumeTrue(packageManager.hasSystemFeature("com.android.extensions.computercontrol"))

        assertThat(context.getSystemService(VirtualDeviceManager::class.java)).isNotNull()
        assertThat(
                packageManager.hasSystemFeature(
                    PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS
                )
            )
            .isTrue()
        assertThat(packageManager.getSharedLibraries(0).map { it.name })
            .contains("com.android.extensions.computercontrol")
        assertThat(packageManager.getPackageInfo("com.android.virtualdevicemanager", 0)).isNotNull()

        assertThat(ComputerControlExtensions.getInstance(context)).isNotNull()
    }
}
