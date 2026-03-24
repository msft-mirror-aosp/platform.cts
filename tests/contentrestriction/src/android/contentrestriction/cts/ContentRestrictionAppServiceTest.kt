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

package android.contentrestriction.cts

import android.app.contentrestriction.ClassifiableContent
import android.app.contentrestriction.flags.Flags
import android.content.LocusId
import com.android.bedstead.flags.annotations.RequireFlagsEnabled
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.compatibility.common.util.ApiTest
import com.android.eventlib.EventLogs
import com.android.eventlib.truth.EventLogsSubject
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.Executors

@RunWith(BedsteadJUnit4::class)
@RequireFlagsEnabled(Flags.FLAG_CONTENT_RESTRICTION_API)
class ContentRestrictionAppServiceTest : BaseContentRestrictionTest() {

    @Before
    fun setUp() {
        EventLogs.resetLogs()
    }

    @Test
    @ApiTest(
        apis =
            [
                "android.app.contentrestriction.ContentRestrictionAppService#onContentRestrictionEnabled",
                "android.app.contentrestriction.ContentRestrictionAppService#onContentRestrictionDisabled",
                "android.app.contentrestriction.ContentRestrictionAppService#onBind"
            ]
    )
    fun testContentRestrictionAppService_withContentRestrictionApp() {
        withContentRestrictionApps(count = 1) { (app) ->
            EventLogsSubject.assertThat(app.events().serviceBound()).eventOccurred()
            EventLogsSubject.assertThat(app.events().contentRestrictionEnabled()).eventOccurred()

            setRestrictionApps(emptyList())
            EventLogsSubject.assertThat(app.events().contentRestrictionDisabled()).eventOccurred()
        }
    }

    @Test
    @ApiTest(apis = ["android.app.contentrestriction.ContentRestrictionAppService#onClassifyContent"])
    fun testOnClassifyContent_isCalled() {
        withContentRestrictionApps(count = 1) { (app) ->
            EventLogsSubject.assertThat(app.events().contentRestrictionEnabled()).eventOccurred()

            val locusId = LocusId("test_locus")
            val content = ClassifiableContent.Builder(locusId, "text/plain").build()

            contentRestrictionManager.requestClassification(
                content, Executors.newSingleThreadExecutor()) { _ -> }

            val event = app.events().classifyContent().waitForEvent()
            assertThat(event.classifiableContent?.id).isEqualTo(locusId)
        }
    }
}
