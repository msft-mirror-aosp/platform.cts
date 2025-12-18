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

import kotlin.math.max
import kotlin.math.min
import perfetto.protos.PerfettoTrace
import perfetto.protos.PerfettoTrace.GpuCounterDescriptor.GpuCounterSpec
import perfetto.protos.PerfettoTrace.Trace

private const val RAYTRACING_SUPPORT_EVENT = "CtsTestDeviceRayTracingSupport"
private const val GPU_USAGE_MODE_EVENT = "CtsTestGpuUsageMode"

enum class Mode {
    LOW_GPU_USAGE,
    HIGH_GPU_USAGE;

    // Acts as the negation operator, might extend to more modes in the future.
    fun next(): Mode {
        return if (this == LOW_GPU_USAGE) HIGH_GPU_USAGE else LOW_GPU_USAGE
    }
}

data class GpuUsageEvent(val timestamp: Long, val mode: Mode)

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

fun Trace.getGpuUsageTimeline(): List<GpuUsageEvent> {
    val gpuUsageTimeline = mutableListOf<GpuUsageEvent>()

    for (packet in packetList) {
        if (!packet.hasFtraceEvents()) continue

        val eventBundle = packet.getFtraceEvents()
        for (event in eventBundle.eventList) {
            if (!event.hasPrint()) continue

            val ftraceBufItems = event.getPrint().getBuf().trim().split("|")
            // GPU usage is reported via Atrace, with 1 meaning high usage and 0 meaning low.
            if (ftraceBufItems.contains(GPU_USAGE_MODE_EVENT)) {
                when (ftraceBufItems.last()) {
                    "1" -> gpuUsageTimeline.add(
                        GpuUsageEvent(event.timestamp, Mode.HIGH_GPU_USAGE)
                    )
                    "0" -> gpuUsageTimeline.add(GpuUsageEvent(event.timestamp, Mode.LOW_GPU_USAGE))
                    else -> throw IllegalArgumentException(
                        "Invalid value for GPU usage mode: [" + ftraceBufItems.last() + "]"
                    )
                }
            }
        }
    }
    if (gpuUsageTimeline.isEmpty()) {
        throw IllegalArgumentException("No GPU usage timeline; this is a test infra issue")
    }
    return gpuUsageTimeline.sortedBy { it.timestamp }
}

fun counterMatchesGpuUtilisation(
    counterSpec: GpuCounterSpec,
    counterValues: List<GpuCounterValue>,
    gpuUsageTimeline: List<GpuUsageEvent>
): Boolean {
    if (counterSpec.numeratorUnitsList.size != 1 ||
        counterSpec.numeratorUnitsList[0] != PerfettoTrace.GpuCounterDescriptor.MeasureUnit.PERCENT
    ) {
        return false
    }
    if (gpuUsageTimeline.size < 2 || counterValues.size < 2 ||
        counterValues.last().timestamp < gpuUsageTimeline.last().timestamp) {
        return false
    }

    var highGpuUsageWeightedSum = 0.0
    var lowGpuUsageWeightedSum = 0.0
    var highGpuUsageTotalDuration = 0L
    var lowGpuUsageTotalDuration = 0L

    var counterIndex = 0
    // Discard values before the initial GPU usage event.
    while (counterIndex < counterValues.size - 1 &&
        counterValues[counterIndex].timestamp <= gpuUsageTimeline.first().timestamp) {
        counterIndex++
    }

    for (usagePeriodIndex in 0 until gpuUsageTimeline.size) {
        // Some usage periods might not contain any counter values; this is ok as counter events
        // timing is not aligned to the GPU usage events.
        while (counterIndex < counterValues.size) {
            val value = counterValues[counterIndex].value
            val intervalStart = if (counterIndex == 0) {
                gpuUsageTimeline[usagePeriodIndex].timestamp
            } else {
                max(
                    counterValues[counterIndex - 1].timestamp,
                    gpuUsageTimeline[usagePeriodIndex].timestamp
                )
            }
            val intervalEnd = if (usagePeriodIndex == gpuUsageTimeline.size - 1) {
                counterValues[counterIndex].timestamp
            } else {
                min(
                    counterValues[counterIndex].timestamp,
                    gpuUsageTimeline[usagePeriodIndex + 1].timestamp
                )
            }
            val duration = intervalEnd - intervalStart
            if (duration > 0) {
                if (gpuUsageTimeline[usagePeriodIndex].mode == Mode.HIGH_GPU_USAGE) {
                    highGpuUsageWeightedSum += value * duration
                    highGpuUsageTotalDuration += duration
                } else {
                    lowGpuUsageWeightedSum += value * duration
                    lowGpuUsageTotalDuration += duration
                }
            }
            if (usagePeriodIndex < gpuUsageTimeline.size - 1 &&
                intervalEnd == gpuUsageTimeline[usagePeriodIndex + 1].timestamp) {
                // Move on to the next usage period.
                break
            }
            ++counterIndex
        }
    }
    // Note: our assumption is that the application keeps its last reported usage mode till the end
    // of the trace.

    val highGpuUsageAverage = highGpuUsageWeightedSum / highGpuUsageTotalDuration
    val lowGpuUsageAverage = lowGpuUsageWeightedSum / lowGpuUsageTotalDuration

    return highGpuUsageAverage > lowGpuUsageAverage
}

fun List<GpuCounterValue>.containsThreeConsecutiveZeroes(): Boolean {
    if (size < 3) {
        return false
    }
    for (i in 0..size - 3) {
        if (this[i].value == 0.0 && this[i + 1].value == 0.0 && this[i + 2].value == 0.0) {
            return true
        }
    }
    return false
}
