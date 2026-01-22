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

package android.graphics.gpuprofiling.cts

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import perfetto.protos.PerfettoTrace.GpuRenderStageEvent
import perfetto.protos.PerfettoTrace.Trace
import perfetto.protos.PerfettoTrace.TracePacket
import perfetto.protos.PerfettoTrace.VulkanApiEvent
import perfetto.protos.PerfettoTrace.VulkanApiEvent.VkQueueSubmit

class RenderStagesChecksTest {

    @Test
    fun checkRenderStagesNotEmpty_emptyList_fails() {
        val fakeCollector = FakeErrorCollector()

        checkRenderStagesNotEmpty(fakeCollector, emptyList())

        fakeCollector.assertFailure()
        assertThat(
            fakeCollector.getErrorMessages()
        ).containsExactly(
            "Trace does not contain GPU render stages."
        )
    }

    @Test
    fun checkRenderStagesNotEmpty_nonEmptyList_passes() {
        val fakeCollector = FakeErrorCollector()
        val list = listOf(RenderStageEvent(1, GpuRenderStageEvent.getDefaultInstance()))

        checkRenderStagesNotEmpty(fakeCollector, list)

        fakeCollector.assertSuccess()
    }

    @Test
    fun checkRenderStagesValidity_validEvents_passes() {
        val fakeCollector = FakeErrorCollector()
        val validEvent = GpuRenderStageEvent.newBuilder()
            .setEventId(1)
            .setHwQueueIid(1)
            .setStageIid(1)
            .setContext(1)
            .build()
        val list = listOf(RenderStageEvent(1, validEvent))

        checkRenderStagesValidity(fakeCollector, list)

        fakeCollector.assertSuccess()
    }

    @Test
    @Suppress("DEPRECATION")
    fun checkRenderStagesValidity_deprecatedFields_reportsError() {
        val fakeCollector = FakeErrorCollector()
        val deprecatedEvent = GpuRenderStageEvent.newBuilder()
            .setEventId(10)
            .setContext(1)
            .setHwQueueId(1)
            .setStageId(1)
            .build()
        val list = listOf(RenderStageEvent(1, deprecatedEvent))

        checkRenderStagesValidity(fakeCollector, list)

        fakeCollector.assertFailure()
        assertThat(fakeCollector.getErrorMessages()).hasSize(1)
        assertThat(
            fakeCollector.getErrors().first().message
        ).contains("event_id 10 uses deprecated `hw_queue_id`;")
    }

    @Test
    fun checkRenderStagesValidity_malformed_reportsError() {
        val fakeCollector = FakeErrorCollector()
        val malformedEvent = GpuRenderStageEvent.newBuilder()
            .setEventId(20)
            // Missing other required fields
            .build()
        val list = listOf(RenderStageEvent(1, malformedEvent))

        checkRenderStagesValidity(fakeCollector, list)

        fakeCollector.assertFailure()
        assertThat(fakeCollector.getErrorMessages()).hasSize(1)
        val errorMessage = fakeCollector.getErrors().first().message
        assertThat(errorMessage).contains("event_id 20 is missing `hw_queue_iid`")
        assertThat(errorMessage).contains("event_id 20 is missing `stage_iid`")
        assertThat(errorMessage).contains("event_id 20 is missing `context`")
    }

    @Test
    fun checkQueueSubmitsNotEmpty_empty_fails() {
        val fakeCollector = FakeErrorCollector()

        checkQueueSubmitsNotEmpty(fakeCollector, emptyList())

        fakeCollector.assertFailure()
        assertThat(
            fakeCollector.getErrorMessages()
        ).containsExactly("QueueSubmit events are missing.")
    }

    @Test
    fun checkQueueSubmitsStrictlyMonotonicallyIncreasing_increasing_passes() {
        val fakeCollector = FakeErrorCollector()
        val list = listOf(
            QueueSubmitEvent(1, 1),
            QueueSubmitEvent(2, 2),
            QueueSubmitEvent(3, 3)
        )

        checkQueueSubmitsStrictlyMonotonicallyIncreasing(fakeCollector, list)

        fakeCollector.assertSuccess()
    }

    @Test
    fun checkQueueSubmitsStrictlyMonotonicallyIncreasing_duplicates_fails() {
        val fakeCollector = FakeErrorCollector()
        val list = listOf(
            QueueSubmitEvent(1, 1),
            QueueSubmitEvent(2, 1), // Duplicate
            QueueSubmitEvent(3, 2)
        )

        checkQueueSubmitsStrictlyMonotonicallyIncreasing(fakeCollector, list)

        fakeCollector.assertFailure()
        assertThat(
            fakeCollector.getErrorMessages()
        ).containsExactly(
            "The queue submit IDs are not strictly monotonically increasing."
        )
    }

    @Test
    fun checkRenderStagesMatchQueueSubmits_matches_passes() {
        val fakeCollector = FakeErrorCollector()

        val trace = Trace.newBuilder().apply {
            // Padding start (ID 0)
            addPacket(
                TracePacket.newBuilder().setTimestamp(
                    10
                ).setVulkanApiEvent(
                    VulkanApiEvent.newBuilder().setVkQueueSubmit(
                        VkQueueSubmit.newBuilder().setSubmissionId(0)
                    )
                )
            )
            addPacket(
                TracePacket.newBuilder().setTimestamp(
                    20
                ).setGpuRenderStageEvent(GpuRenderStageEvent.newBuilder().setSubmissionId(0))
            )

            // ID 1
            addPacket(
                TracePacket.newBuilder().setTimestamp(
                    100
                ).setVulkanApiEvent(
                    VulkanApiEvent.newBuilder().setVkQueueSubmit(
                        VkQueueSubmit.newBuilder().setSubmissionId(1)
                    )
                )
            )
            addPacket(
                TracePacket.newBuilder().setTimestamp(
                    150
                ).setGpuRenderStageEvent(GpuRenderStageEvent.newBuilder().setSubmissionId(1))
            )

            // ID 2
            addPacket(
                TracePacket.newBuilder().setTimestamp(
                    200
                ).setVulkanApiEvent(
                    VulkanApiEvent.newBuilder().setVkQueueSubmit(
                        VkQueueSubmit.newBuilder().setSubmissionId(2)
                    )
                )
            )
            addPacket(
                TracePacket.newBuilder().setTimestamp(
                    250
                ).setGpuRenderStageEvent(GpuRenderStageEvent.newBuilder().setSubmissionId(2))
            )

            // Padding end (ID 3)
            addPacket(
                TracePacket.newBuilder().setTimestamp(
                    300
                ).setVulkanApiEvent(
                    VulkanApiEvent.newBuilder().setVkQueueSubmit(
                        VkQueueSubmit.newBuilder().setSubmissionId(3)
                    )
                )
            )
            addPacket(
                TracePacket.newBuilder().setTimestamp(
                    350
                ).setGpuRenderStageEvent(GpuRenderStageEvent.newBuilder().setSubmissionId(3))
            )
        }.build()

        val data = RenderStagesData(trace)

        checkRenderStagesMatchQueueSubmits(fakeCollector, data)

        fakeCollector.assertSuccess()
    }

    @Test
    fun checkRenderStagesMatchQueueSubmits_timestampMismatch_fails() {
        val fakeCollector = FakeErrorCollector()

        val trace = Trace.newBuilder().apply {
            // Padding start (ID 0)
            addPacket(
                TracePacket.newBuilder().setTimestamp(
                    10
                ).setVulkanApiEvent(
                    VulkanApiEvent.newBuilder().setVkQueueSubmit(
                        VkQueueSubmit.newBuilder().setSubmissionId(0)
                    )
                )
            )
            addPacket(
                TracePacket.newBuilder().setTimestamp(
                    20
                ).setGpuRenderStageEvent(GpuRenderStageEvent.newBuilder().setSubmissionId(0))
            )

            // ID 1 (Mismatch)
            addPacket(
                TracePacket.newBuilder().setTimestamp(
                    200
                ).setVulkanApiEvent(
                    VulkanApiEvent.newBuilder().setVkQueueSubmit(
                        VkQueueSubmit.newBuilder().setSubmissionId(1)
                    )
                )
            )
            addPacket(
                TracePacket.newBuilder().setTimestamp(
                    100
                ).setGpuRenderStageEvent(GpuRenderStageEvent.newBuilder().setSubmissionId(1))
            )

            // Padding end (ID 2)
            addPacket(
                TracePacket.newBuilder().setTimestamp(
                    300
                ).setVulkanApiEvent(
                    VulkanApiEvent.newBuilder().setVkQueueSubmit(
                        VkQueueSubmit.newBuilder().setSubmissionId(2)
                    )
                )
            )
            addPacket(
                TracePacket.newBuilder().setTimestamp(
                    350
                ).setGpuRenderStageEvent(GpuRenderStageEvent.newBuilder().setSubmissionId(2))
            )
        }.build()

        val data = RenderStagesData(trace)

        checkRenderStagesMatchQueueSubmits(fakeCollector, data)

        fakeCollector.assertFailure()
        val fullErrorMessage = fakeCollector.getErrors().first().message
        assertThat(
            fullErrorMessage
        ).contains("Render stages reported before their VkQueueSubmit events for submission_ids")
        assertThat(fullErrorMessage).contains("Expected: is an empty collection")
        assertThat(fullErrorMessage).contains("but: <[1]>")
    }

    @Test
    fun checkRenderStagesMatchQueueSubmits_missingQueueSubmit_fails() {
        val fakeCollector = FakeErrorCollector()

        val trace = Trace.newBuilder().apply {
            // ID 0 (Match)
            addPacket(
                TracePacket.newBuilder().setTimestamp(
                    10
                ).setVulkanApiEvent(
                    VulkanApiEvent.newBuilder().setVkQueueSubmit(
                        VkQueueSubmit.newBuilder().setSubmissionId(0)
                    )
                )
            )
            addPacket(
                TracePacket.newBuilder().setTimestamp(
                    20
                ).setGpuRenderStageEvent(GpuRenderStageEvent.newBuilder().setSubmissionId(0))
            )

            // ID 1 (Match)
            addPacket(
                TracePacket.newBuilder().setTimestamp(
                    100
                ).setVulkanApiEvent(
                    VulkanApiEvent.newBuilder().setVkQueueSubmit(
                        VkQueueSubmit.newBuilder().setSubmissionId(1)
                    )
                )
            )
            addPacket(
                TracePacket.newBuilder().setTimestamp(
                    120
                ).setGpuRenderStageEvent(GpuRenderStageEvent.newBuilder().setSubmissionId(1))
            )

            // ID 2 (Missing queue submit)
            addPacket(
                TracePacket.newBuilder().setTimestamp(
                    220
                ).setGpuRenderStageEvent(GpuRenderStageEvent.newBuilder().setSubmissionId(2))
            )

            // ID 3 (Match)
            addPacket(
                TracePacket.newBuilder().setTimestamp(
                    300
                ).setVulkanApiEvent(
                    VulkanApiEvent.newBuilder().setVkQueueSubmit(
                        VkQueueSubmit.newBuilder().setSubmissionId(3)
                    )
                )
            )
            addPacket(
                TracePacket.newBuilder().setTimestamp(
                    320
                ).setGpuRenderStageEvent(GpuRenderStageEvent.newBuilder().setSubmissionId(3))
            )

            // ID 4 (Match)
            addPacket(
                TracePacket.newBuilder().setTimestamp(
                    400
                ).setVulkanApiEvent(
                    VulkanApiEvent.newBuilder().setVkQueueSubmit(
                        VkQueueSubmit.newBuilder().setSubmissionId(4)
                    )
                )
            )
            addPacket(
                TracePacket.newBuilder().setTimestamp(
                    420
                ).setGpuRenderStageEvent(GpuRenderStageEvent.newBuilder().setSubmissionId(4))
            )
        }.build()

        val data = RenderStagesData(trace)

        checkRenderStagesMatchQueueSubmits(fakeCollector, data)

        fakeCollector.assertFailure()
        assertThat(
            fakeCollector.getErrorMessages()
        ).containsExactly("Found mismatched queue submits and/or render stages.")
    }
}
