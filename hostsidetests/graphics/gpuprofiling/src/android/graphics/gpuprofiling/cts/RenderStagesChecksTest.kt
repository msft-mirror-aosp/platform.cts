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

import org.junit.Rule
import org.junit.Test
import org.junit.rules.ErrorCollector
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.contains
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import perfetto.protos.PerfettoTrace.GpuRenderStageEvent
import perfetto.protos.PerfettoTrace.Trace
import perfetto.protos.PerfettoTrace.TracePacket
import perfetto.protos.PerfettoTrace.VulkanApiEvent
import perfetto.protos.PerfettoTrace.VulkanApiEvent.VkQueueSubmit

class RenderStagesChecksTest {

    @get:Rule
    val errorCollector = ErrorCollector()

    @Test
    fun checkRenderStagesNotEmpty_emptyList_fails() {
        val mockCollector = mock(ErrorCollector::class.java)

        checkRenderStagesNotEmpty(mockCollector, emptyList())

        verify(mockCollector).checkThat(
            eq("Trace does not contain GPU render stages."),
            eq(emptyList<RenderStageEvent>()),
            any()
        )
    }

    @Test
    fun checkRenderStagesNotEmpty_nonEmptyList_passes() {
        val mockCollector = mock(ErrorCollector::class.java)
        val list = listOf(RenderStageEvent(1, GpuRenderStageEvent.getDefaultInstance()))

        checkRenderStagesNotEmpty(mockCollector, list)

        verify(mockCollector).checkThat(
            eq("Trace does not contain GPU render stages."),
            eq(list),
            any()
        )
    }

    @Test
    fun checkRenderStagesValidity_validEvents_passes() {
        val mockCollector = mock(ErrorCollector::class.java)
        val validEvent = GpuRenderStageEvent.newBuilder()
            .setEventId(1)
            .setHwQueueIid(1)
            .setStageIid(1)
            .setContext(1)
            .build()
        val list = listOf(RenderStageEvent(1, validEvent))

        checkRenderStagesValidity(mockCollector, list)

        verify(mockCollector).checkThat(
            eq("Some of the reported GPU render stages are not valid."),
            eq(""),
            any()
        )
    }

    @Test
    @Suppress("DEPRECATION")
    fun checkRenderStagesValidity_deprecatedFields_reportsError() {
        val mockCollector = mock(ErrorCollector::class.java)
        val deprecatedEvent = GpuRenderStageEvent.newBuilder()
            .setEventId(10)
            .setContext(1)
            .setHwQueueId(1)
            .setStageId(1)
            .build()
        val list = listOf(RenderStageEvent(1, deprecatedEvent))

        checkRenderStagesValidity(mockCollector, list)

        verify(mockCollector).checkThat(
            eq("Some of the reported GPU render stages are not valid."),
            contains("event_id 10 uses deprecated `hw_queue_id`"),
            any()
        )
    }

    @Test
    fun checkRenderStagesValidity_malformed_reportsError() {
        val mockCollector = mock(ErrorCollector::class.java)
        val malformedEvent = GpuRenderStageEvent.newBuilder()
            .setEventId(20)
            // Missing other required fields
            .build()
        val list = listOf(RenderStageEvent(1, malformedEvent))

        checkRenderStagesValidity(mockCollector, list)

        verify(mockCollector).checkThat(
            eq("Some of the reported GPU render stages are not valid."),
            contains("event_id 20 is missing `hw_queue_iid`"),
            any()
        )
        verify(mockCollector).checkThat(
            eq("Some of the reported GPU render stages are not valid."),
            contains("event_id 20 is missing `stage_iid`"),
            any()
        )
        verify(mockCollector).checkThat(
            eq("Some of the reported GPU render stages are not valid."),
            contains("event_id 20 is missing `context`"),
            any()
        )
    }

    @Test
    fun checkQueueSubmitsNotEmpty_empty_fails() {
        val mockCollector = mock(ErrorCollector::class.java)
        checkQueueSubmitsNotEmpty(mockCollector, emptyList())

        verify(mockCollector).checkThat(
            eq("QueueSubmit events are missing."),
            eq(emptyList<QueueSubmitEvent>()),
            any()
        )
    }

    @Test
    fun checkQueueSubmitsStrictlyMonotonicallyIncreasing_increasing_passes() {
        val mockCollector = mock(ErrorCollector::class.java)
        val list = listOf(
            QueueSubmitEvent(1, 1),
            QueueSubmitEvent(2, 2),
            QueueSubmitEvent(3, 3)
        )

        checkQueueSubmitsStrictlyMonotonicallyIncreasing(mockCollector, list)

        verify(mockCollector).checkThat(
            eq("The queue submit IDs are not strictly monotonically increasing."),
            eq(listOf(1, 2, 3)),
            any()
        )
    }

    @Test
    fun checkQueueSubmitsStrictlyMonotonicallyIncreasing_duplicates_fails() {
        val mockCollector = mock(ErrorCollector::class.java)
        val list = listOf(
            QueueSubmitEvent(1, 1),
            QueueSubmitEvent(2, 1), // Duplicate
            QueueSubmitEvent(3, 2)
        )

        checkQueueSubmitsStrictlyMonotonicallyIncreasing(mockCollector, list)

        verify(mockCollector).checkThat(
            eq("The queue submit IDs are not strictly monotonically increasing."),
            eq(listOf(1, 2)), // This is the sorted distinct one
            any()
        )
    }

    @Test
    fun checkRenderStagesMatchQueueSubmits_matches_passes() {
        val mockCollector = mock(ErrorCollector::class.java)

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

        checkRenderStagesMatchQueueSubmits(mockCollector, data)

        // Verify no failure on matching IDs
        verify(mockCollector).checkThat(
            eq("Found mismatched queue submits and/or render stages."),
            eq(2),
            any()
        )
    }

    @Test
    fun checkRenderStagesMatchQueueSubmits_timestampMismatch_fails() {
        val mockCollector = mock(ErrorCollector::class.java)

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

        checkRenderStagesMatchQueueSubmits(mockCollector, data)

        verify(mockCollector).checkThat(
            eq("Render stage reported before its VkQueueSubmit for submission_id: 1."),
            eq(100L),
            any()
        )
    }

    @Test
    fun checkRenderStagesMatchQueueSubmits_missingQueueSubmit_fails() {
        val mockCollector = mock(ErrorCollector::class.java)

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

        checkRenderStagesMatchQueueSubmits(mockCollector, data)

        // Verify no failure on matching IDs
        verify(mockCollector).checkThat(
            eq("Found mismatched queue submits and/or render stages."),
            eq(2),
            any()
        )
    }
}
