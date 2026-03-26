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
import android.app.contentrestriction.ContentRestrictionManager
import android.app.contentrestriction.flags.Flags
import android.content.LocusId
import android.os.OutcomeReceiver
import com.android.bedstead.flags.annotations.RequireFlagsEnabled
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.compatibility.common.util.ApiTest
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
@RequireFlagsEnabled(Flags.FLAG_CONTENT_RESTRICTION_API)
class ContentRestrictionManagerTest : BaseContentRestrictionTest() {

    @Test
    @ApiTest(
        apis =
            [
                "android.app.contentrestriction.ContentRestrictionManager#createContentRestrictedIntent"
            ]
    )
    fun testCreateContentRestrictedIntent() {
        val locusId = LocusId("test_locus")
        val intent = contentRestrictionManager.createContentRestrictedIntent(locusId)

        assertThat(intent).isNotNull()
        assertThat(intent.action)
            .isEqualTo(ContentRestrictionManager.ACTION_SHOW_RESTRICTED_CONTENT_DETAILS)
        assertThat(intent.hasExtra(ContentRestrictionManager.EXTRA_CONTENT_LOCUS_ID)).isTrue()
    }

    @Test
    @ApiTest(
        apis = ["android.app.contentrestriction.ContentRestrictionManager#requestClassification"]
    )
    fun testRequestClassification() {
        // Run with Content Restriction disabled. requestClassification should instantly return
        // true.
        val locusId = LocusId("test_locus")
        val content = ClassifiableContent.Builder(locusId, "text/plain").build()

        val latch = CountDownLatch(1)
        val classificationResult = AtomicBoolean()

        contentRestrictionManager.requestClassification(
            content,
            Executors.newSingleThreadExecutor(),
            object : OutcomeReceiver<Boolean, Exception> {
                override fun onResult(result: Boolean) {
                    classificationResult.set(result)
                    latch.countDown()
                }

                override fun onError(error: Exception) {
                    latch.countDown()
                }
            },
        )

        latch.await(1, TimeUnit.SECONDS)

        assertThat(classificationResult.get()).isTrue()
    }
}
