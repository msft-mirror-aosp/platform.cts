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

package android.graphics.gpuprofiling.cts

import perfetto.protos.PerfettoTrace
import perfetto.protos.PerfettoTrace.Trace

private const val RAYTRACING_SUPPORT_EVENT = "CtsTestDeviceRayTracingSupport"

data class GpuCounter(val counterId: Int, val timestamp: Long, val value: Double)

fun Trace.getCounters(): List<GpuCounter> {
    val counters = mutableListOf<GpuCounter>()
    for (packet in this.packetList) {
        if (!packet.hasGpuCounterEvent()) continue
        if (packet.gpuCounterEvent.countersCount == 0) continue
        for (counter in packet.gpuCounterEvent.countersList) {
            counters.add(GpuCounter(counter.counterId, packet.timestamp, counter.doubleValue()))
        }
    }
    return counters
}

fun PerfettoTrace.GpuCounterEvent.GpuCounter.doubleValue(): Double {
    if (this.hasDoubleValue()) return this.doubleValue
    return this.intValue.toDouble()
}

fun Trace.deviceSupportsRayTracing(): Boolean? {
    for (packet in packetList) {
        if (!packet.hasFtraceEvents()) continue

        val eventBundle = packet.getFtraceEvents()
        for (event in eventBundle.eventList) {
            if (!event.hasPrint()) continue

            val ftraceBufItems = event.getPrint().getBuf().split("|")
            // Ray tracing is reported via Atrace, with 1 meaning it's supported and 0 meaning not.
            if (ftraceBufItems.contains(RAYTRACING_SUPPORT_EVENT)) {
                return when (ftraceBufItems.last()) {
                    "1" -> true
                    "0" -> false
                    else -> throw IllegalArgumentException("Invalid value for ray tracing support")
                }
            }
        }
    }
    return null
}
