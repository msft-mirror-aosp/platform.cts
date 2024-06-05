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
package com.android.bedstead.flags

import android.app.admin.flags.Flags
import com.android.bedstead.flags.annotations.RequireFlagsDisabled
import com.android.bedstead.flags.annotations.RequireFlagsEnabled
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.google.common.truth.Truth.assertThat
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
class FlagsTest {

    @Test
    @RequireFlagsEnabled(Flags.FLAG_DUMPSYS_POLICY_ENGINE_MIGRATION_ENABLED)
    fun requireFlagEnabledAnnotation_flagIsEnabled() {
        assertThat(Flags.dumpsysPolicyEngineMigrationEnabled()).isTrue()
    }

    @Test
    @RequireFlagsDisabled(Flags.FLAG_DUMPSYS_POLICY_ENGINE_MIGRATION_ENABLED)
    fun requireFlagDisabledAnnotation_flagIsDisabled() {
        assertThat(Flags.dumpsysPolicyEngineMigrationEnabled()).isFalse()
    }

    companion object {
        @ClassRule @Rule @JvmField
        val deviceState = DeviceState()
    }
}
