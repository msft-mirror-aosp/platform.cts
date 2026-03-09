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
import perfetto.protos.PerfettoTrace.FtraceEvent
import perfetto.protos.PerfettoTrace.FtraceEventBundle
import perfetto.protos.PerfettoTrace.GpuFrequencyFtraceEvent
import perfetto.protos.PerfettoTrace.GpuRenderStageEvent
import perfetto.protos.PerfettoTrace.PrintFtraceEvent
import perfetto.protos.PerfettoTrace.Trace
import perfetto.protos.PerfettoTrace.TracePacket
import perfetto.protos.PerfettoTrace.VulkanApiEvent
import perfetto.protos.PerfettoTrace.VulkanApiEvent.VkQueueSubmit

class TraceParserTest {

    @Test
    fun parse_extractsAllRequiredData() {
        val trace = Trace.newBuilder().apply {
            addPacket(TracePacket.newBuilder().apply {
                timestamp = 100L
                setGpuRenderStageEvent(
                    GpuRenderStageEvent.newBuilder().setEventId(1).setSubmissionId(10)
                )
            })
            addPacket(TracePacket.newBuilder().apply {
                timestamp = 200L
                setVulkanApiEvent(VulkanApiEvent.newBuilder().setVkQueueSubmit(
                    VkQueueSubmit.newBuilder().setSubmissionId(10).setPid(406).setDurationNs(30)
                ))
            })
            addPacket(TracePacket.newBuilder().apply {
                setFtraceEvents(FtraceEventBundle.newBuilder().apply {
                    addEvent(FtraceEvent.newBuilder().apply {
                        timestamp = 300L
                        setPrint(
                            PrintFtraceEvent.newBuilder().setBuf("S|406|CtsTestQueueSubmit|58\n")
                        )
                    })
                    addEvent(FtraceEvent.newBuilder().apply {
                        timestamp = 400L
                        setPrint(
                            PrintFtraceEvent.newBuilder().setBuf("F|406|CtsTestQueueSubmit|58\n")
                        )
                    })
                })
            })
            // GPU frequency event for containsGpuFrequencyEvent
            addPacket(TracePacket.newBuilder().apply {
                setFtraceEvents(FtraceEventBundle.newBuilder().apply {
                    addEvent(FtraceEvent.newBuilder().apply {
                        timestamp = 500L
                        gpuFrequency = GpuFrequencyFtraceEvent.newBuilder()
                            .setGpuId(0)
                            .setState(100)
                            .build()
                    })
                })
            })
            // Raytracing event reported by the native app
            addPacket(TracePacket.newBuilder().apply {
                setFtraceEvents(FtraceEventBundle.newBuilder().apply {
                    addEvent(FtraceEvent.newBuilder().apply {
                        timestamp = 600L
                        setPrint(
                            PrintFtraceEvent.newBuilder().setBuf(
                                "|CtsTestDeviceRayTracingSupport|1\n"
                            )
                        )
                    })
                })
            })
            // GPU usage mode event reported by the native app
            addPacket(TracePacket.newBuilder().apply {
                setFtraceEvents(FtraceEventBundle.newBuilder().apply {
                    addEvent(FtraceEvent.newBuilder().apply {
                        timestamp = 700L
                        setPrint(
                            PrintFtraceEvent.newBuilder().setBuf("|CtsTestGpuUsageMode|1\n")
                        )
                    })
                })
            })
        }.build()

        val parsedTrace = TraceParser.parse(trace)

        assertThat(parsedTrace.hasRayTracingSupport).isTrue()
        assertThat(parsedTrace.containsGpuFrequencyEvent).isTrue()
        assertThat(parsedTrace.gpuUsageTimeline.size).isEqualTo(1)
        assertThat(parsedTrace.gpuUsageTimeline[0].mode).isEqualTo(Mode.HIGH_GPU_USAGE)

        assertThat(parsedTrace.renderStagesData.renderStagesForApp).containsExactly(
            RenderStageEvent(
                100L,
                GpuRenderStageEvent.newBuilder().setEventId(1).setSubmissionId(10).build()
            )
        )
        assertThat(parsedTrace.renderStagesData.queueSubmitsWithAppPid).containsExactly(
            QueueSubmitEvent(200L, 10, 406, 30)
        )
        assertThat(parsedTrace.renderStagesData.appQueueSubmits).containsExactly(
            AppQueueSubmitEvent(300L, 400L)
        )
    }
}
