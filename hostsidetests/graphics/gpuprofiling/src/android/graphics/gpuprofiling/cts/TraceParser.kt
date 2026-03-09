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

import perfetto.protos.PerfettoTrace.Trace

object TraceParser {

    data class ParsedTrace(
        val hasRayTracingSupport: Boolean?,
        val gpuUsageTimeline: List<GpuUsageEvent>,
        val gpuCounters: GpuCounters,
        val renderStagesData: RenderStagesData,
        val containsGpuFrequencyEvent: Boolean
    )

    /**
     * Parses the given [Trace] into a [ParsedTrace] to be used by the validator.
     */
    @JvmStatic
    fun parse(trace: Trace): ParsedTrace {
        val hasRayTracingSupport = trace.deviceSupportsRayTracing()

        // Some traces might not have usage mode events, depending on the test configuration,
        // so we catch the IllegalArgumentException thrown by getGpuUsageTimeline()
        val gpuUsageTimeline = try {
            trace.getGpuUsageTimeline()
        } catch (e: IllegalArgumentException) {
            emptyList()
        }

        val gpuCounters = GpuCounters(trace)
        val renderStagesData = RenderStagesData(trace)

        val containsGpuFrequencyEvent = containsGpuFrequencyEvent(trace)

        return ParsedTrace(
            hasRayTracingSupport = hasRayTracingSupport,
            gpuUsageTimeline = gpuUsageTimeline,
            gpuCounters = gpuCounters,
            renderStagesData = renderStagesData,
            containsGpuFrequencyEvent = containsGpuFrequencyEvent
        )
    }

    private fun containsGpuFrequencyEvent(trace: Trace): Boolean {
        for (packet in trace.packetList) {
            if (!packet.hasFtraceEvents()) continue

            val eventBundle = packet.ftraceEvents
            for (event in eventBundle.eventList) {
                if (!event.hasGpuFrequency()) continue

                if (event.gpuFrequency.hasGpuId() &&
                    event.gpuFrequency.hasState() &&
                    event.gpuFrequency.state > 0
                ) {
                    return true
                }
            }
        }
        return false
    }
}
