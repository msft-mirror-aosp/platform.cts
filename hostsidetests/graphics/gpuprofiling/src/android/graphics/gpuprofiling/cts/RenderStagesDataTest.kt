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

class RenderStagesDataTest {

    @Test
    fun constructor_extractsRenderStagesAndQueueSubmits() {
        val trace = Trace.newBuilder().apply {
            addPacket(TracePacket.newBuilder().apply {
                timestamp = 100L
                setGpuRenderStageEvent(GpuRenderStageEvent.newBuilder().setEventId(1))
            })
            addPacket(TracePacket.newBuilder().apply {
                timestamp = 200L
                setVulkanApiEvent(VulkanApiEvent.newBuilder().setVkQueueSubmit(
                    VkQueueSubmit.newBuilder().setSubmissionId(10)
                ))
            })
        }.build()

        val renderStagesData = RenderStagesData(trace)

        assertThat(renderStagesData.renderStages).containsExactly(
            RenderStageEvent(100L, GpuRenderStageEvent.newBuilder().setEventId(1).build())
        )
        assertThat(renderStagesData.queueSubmits).containsExactly(
            QueueSubmitEvent(200L, 10)
        )
    }
}
