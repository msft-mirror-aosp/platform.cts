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

import android.app.supervision.flags.Flags
import com.android.bedstead.flags.annotations.RequireFlagsEnabled
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.compatibility.common.util.ApiTest
import com.android.eventlib.EventLogs
import com.android.eventlib.truth.EventLogsSubject.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
@RequireFlagsEnabled(Flags.FLAG_ENABLE_SUPERVISION_APP_SERVICE)
class SupervisionAppServiceTest : BaseSupervisionTest() {

    @Before
    fun setUp() {
        EventLogs.resetLogs()
    }

    @Test
    @ApiTest(
        apis =
            [
                "android.app.supervision.SupervisionAppService#onSupervisionEnabled",
                "android.app.supervision.SupervisionAppService#onSupervisionDisabled",
            ]
    )
    fun testSupervisionAppService_withSupervisionApp() {
        withSupervisionApps(count = 1) { (app) ->
            val events = app.events()

            setSupervisionEnabled(true)
            assertThat(events.serviceBound()).eventOccurred()
            assertThat(events.supervisionEnabled()).eventOccurred()

            setSupervisionEnabled(false)
            assertThat(events.supervisionDisabled()).eventOccurred()
            assertThat(events.serviceUnbound()).eventOccurred()
        }
    }
}
