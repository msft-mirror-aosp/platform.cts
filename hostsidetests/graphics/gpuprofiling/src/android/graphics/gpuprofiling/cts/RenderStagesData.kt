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

import perfetto.protos.PerfettoTrace.GpuRenderStageEvent
import perfetto.protos.PerfettoTrace.Trace

data class RenderStageEvent(val timestamp: Long, val value: GpuRenderStageEvent)

data class QueueSubmitEvent(val timestamp: Long, val submissionId: Int)

class RenderStagesData(trace: Trace) {
    val renderStages: List<RenderStageEvent>
    val queueSubmits: List<QueueSubmitEvent>

    init {
        val clockSnapshots = trace.getTraceClockSnapshots()

        val mutableRenderStages: MutableList<RenderStageEvent> = mutableListOf()
        val mutableQueueSubmits: MutableList<QueueSubmitEvent> = mutableListOf()

        for (packet in trace.packetList) {
            if (packet.hasGpuRenderStageEvent()) {
                mutableRenderStages.add(
                    RenderStageEvent(
                        packet.getTimestampNs(clockSnapshots),
                        packet.gpuRenderStageEvent
                    )
                )
            }

            if (packet.hasVulkanApiEvent() && packet.vulkanApiEvent.hasVkQueueSubmit()) {
                mutableQueueSubmits.add(
                    QueueSubmitEvent(
                        packet.getTimestampNs(clockSnapshots),
                        packet.vulkanApiEvent.vkQueueSubmit.submissionId
                    )
                )
            }
        }
        renderStages = mutableRenderStages
        queueSubmits = mutableQueueSubmits.sortedBy { it.timestamp }.toList()
    }
}
