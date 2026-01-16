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

private const val APP_QUEUE_SUBMIT_EVENT = "CtsTestQueueSubmit"

data class RenderStageEvent(val timestamp: Long, val value: GpuRenderStageEvent)

data class QueueSubmitEvent(val timestamp: Long, val submissionId: Int, val durationNs: Long)

data class AppQueueSubmitEvent(val timestampBefore: Long, val timestampAfter: Long)

class RenderStagesData(trace: Trace) {
    val renderStages: List<RenderStageEvent>
    val queueSubmits: List<QueueSubmitEvent>
    val appQueueSubmits: List<AppQueueSubmitEvent>

    init {
        val clockSnapshots = trace.getTraceClockSnapshots()
        val allDataSourcesStartedNs = trace.getAllDataSourcesStartedNs()

        val mutableRenderStages: MutableList<RenderStageEvent> = mutableListOf()
        val mutableQueueSubmits: MutableList<QueueSubmitEvent> = mutableListOf()
        val mutableAppQueueSubmitStarts: MutableMap<Int, Long> = mutableMapOf()
        val mutableAppQueueSubmitEnds: MutableMap<Int, Long> = mutableMapOf()

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
                        packet.vulkanApiEvent.vkQueueSubmit.submissionId,
                        packet.vulkanApiEvent.vkQueueSubmit.durationNs
                    )
                )
            }

            if (packet.hasFtraceEvents()) {
                val eventBundle = packet.ftraceEvents
                for (event in eventBundle.eventList) {
                    if (!event.hasPrint()) continue

                    val ftraceBufItems = event.print.buf.trim().split("|")
                    if (ftraceBufItems.contains(APP_QUEUE_SUBMIT_EVENT)) {
                        val appSubmitId = ftraceBufItems.last().toInt()
                        if (ftraceBufItems.first() == "S") {
                            mutableAppQueueSubmitStarts[appSubmitId] = event.timestamp
                        } else {
                            mutableAppQueueSubmitEnds[appSubmitId] = event.timestamp
                        }
                    }
                }
            }
        }
        renderStages = mutableRenderStages.filter { it.timestamp > allDataSourcesStartedNs }
        queueSubmits = mutableQueueSubmits.filter {
            it.timestamp > allDataSourcesStartedNs }.sortedBy { it.timestamp }.toList()
        appQueueSubmits = mutableAppQueueSubmitStarts.keys.intersect(mutableAppQueueSubmitEnds.keys)
            .mapNotNull { id ->
                val start = mutableAppQueueSubmitStarts[id]
                val end = mutableAppQueueSubmitEnds[id]
                if (start != null && end != null) {
                    AppQueueSubmitEvent(start, end)
                } else {
                    null
                }
            }
            .filter { it.timestampBefore > allDataSourcesStartedNs }
            .sortedBy { it.timestampBefore }
    }
}
