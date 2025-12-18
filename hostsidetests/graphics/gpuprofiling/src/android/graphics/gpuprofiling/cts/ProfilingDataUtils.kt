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
import perfetto.protos.PerfettoTrace.GpuCounterDescriptor.GpuCounterSpec
import perfetto.protos.PerfettoTrace.Trace

private const val RAYTRACING_SUPPORT_EVENT = "CtsTestDeviceRayTracingSupport"

data class GpuCounterValue(val timestamp: Long, val value: Double)

fun PerfettoTrace.GpuCounterEvent.GpuCounter.doubleValue(): Double {
    if (this.hasDoubleValue()) return this.doubleValue
    return this.intValue.toDouble()
}

class GpuCounters(trace: Trace) {
    val counterSpecs: Map<Int, GpuCounterSpec>
    val counterValues: Map<Int, List<GpuCounterValue>>

    var counterDescriptorError: String = ""

    init {
        var mutableCounterSpecs: MutableMap<Int, GpuCounterSpec> = mutableMapOf()
        var mutableCounterValues: MutableMap<Int, MutableList<GpuCounterValue>> = mutableMapOf()

        val primaryClockId = trace.getPrimaryTraceClock().number
        val clockSnapshots = trace.getTraceClockSnapshots()

        for (packet in trace.packetList) {
            if (!packet.hasGpuCounterEvent()) continue
            if (!packet.hasTimestamp() || packet.timestamp == 0L) {
                if (!packet.gpuCounterEvent.hasCounterDescriptor()) {
                    counterDescriptorError = "first counter event must contain a descriptor"
                    continue
                }
                if (packet.gpuCounterEvent.counterDescriptor.specsCount == 0) {
                    counterDescriptorError = "descriptor contains no specs"
                    continue
                }
                val descriptor = packet.gpuCounterEvent.counterDescriptor
                for (spec in descriptor.specsList) {
                    if (!spec.hasName()) {
                        counterDescriptorError = "missing or empty name for counter id " +
                                spec.counterId
                        break
                    }
                    mutableCounterSpecs[spec.counterId] = spec
                }
                continue
            }
            if (packet.gpuCounterEvent.countersCount == 0) continue
            for (counter in packet.gpuCounterEvent.countersList) {
                val primaryClockTimestamp =
                    if (packet.hasTimestampClockId() && packet.timestampClockId != primaryClockId) {
                    convertTimestamp(
                        packet.timestamp,
                        packet.timestampClockId,
                        primaryClockId,
                        clockSnapshots
                    )
                } else {
                    packet.timestamp
                }
                mutableCounterValues.computeIfAbsent(counter.counterId) { mutableListOf() }
                    .add(GpuCounterValue(primaryClockTimestamp, counter.doubleValue()))
            }
        }
        counterSpecs = mutableCounterSpecs
        // Perfetto does not guarantee that the events are in chronological order; sort them here.
        counterValues = mutableCounterValues.mapValues {
            list -> list.value.sortedBy { it.timestamp }.toList()
        }
    }
}

fun Trace.deviceSupportsRayTracing(): Boolean? {
    for (packet in packetList) {
        if (!packet.hasFtraceEvents()) continue

        val eventBundle = packet.getFtraceEvents()
        for (event in eventBundle.eventList) {
            if (!event.hasPrint()) continue

            val ftraceBufItems = event.getPrint().getBuf().trim().split("|")
            // Ray tracing is reported via Atrace, with 1 meaning it's supported and 0 meaning not.
            if (ftraceBufItems.contains(RAYTRACING_SUPPORT_EVENT)) {
                return when (ftraceBufItems.last()) {
                    "1" -> true
                    "0" -> false
                    else -> throw IllegalArgumentException(
                        "Invalid value for ray tracing support: [" + ftraceBufItems.last() + "]"
                    )
                }
            }
        }
    }
    return null
}
