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

package android.supervision.cts

import android.app.supervision.SupervisionManager
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.nene.TestApis
import com.google.common.truth.Truth.assertThat
import org.junit.ClassRule
import org.junit.Rule

/** Base class for supervision CTS tests. */
open class BaseSupervisionTest {

    fun setSupervisionEnabled(enabled: Boolean) {
        supervisionManager.setSupervisionEnabled(enabled)
        assertThat(supervisionManager.isSupervisionEnabled()).isEqualTo(enabled)
    }

    companion object {
        @[JvmField ClassRule Rule]
        val deviceState = DeviceState()

        val context = TestApis.context().instrumentedContext()
        val supervisionManager = context.getSystemService(SupervisionManager::class.java)
    }
}
