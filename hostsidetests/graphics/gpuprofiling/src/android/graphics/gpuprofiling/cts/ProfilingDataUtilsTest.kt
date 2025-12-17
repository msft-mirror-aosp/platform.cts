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

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import perfetto.protos.PerfettoTrace.FtraceEvent
import perfetto.protos.PerfettoTrace.FtraceEventBundle
import perfetto.protos.PerfettoTrace.GpuCounterDescriptor
import perfetto.protos.PerfettoTrace.GpuCounterEvent
import perfetto.protos.PerfettoTrace.PrintFtraceEvent
import perfetto.protos.PerfettoTrace.Trace
import perfetto.protos.PerfettoTrace.TracePacket

class ProfilingDataUtilsTest {

    @Test
    fun doubleValue_returnsDoubleWhenSet() {
        val counterProto = GpuCounterEvent.GpuCounter.newBuilder().apply {
            setDoubleValue(123.45)
        }.build()
        assertThat(counterProto.doubleValue()).isEqualTo(123.45)
    }

    @Test
    fun doubleValue_returnsIntAsDoubleWhenSet() {
        val counterProto = GpuCounterEvent.GpuCounter.newBuilder().apply {
            setIntValue(678)
        }.build()
        assertThat(counterProto.doubleValue()).isEqualTo(678.0)
    }

    @Test
    fun getCounters_emptyTrace_returnsEmptyList() {
        val trace = Trace.newBuilder().build()
        assertThat(trace.getCounters()).isEmpty()
    }

    @Test
    fun getCounters_noGpuCounterEvents_returnsEmptyList() {
        val trace = Trace.newBuilder().apply {
            addPacket(TracePacket.newBuilder().setTimestamp(1)) // Some other packet
        }.build()
        assertThat(trace.getCounters()).isEmpty()
    }

    @Test
    fun getCounters_extractsCountersCorrectly() {
        // Trace containing multiple counter data packets - no descriptor packet.
        val trace = Trace.newBuilder().apply {
            // Packet 1 with two counters (double and int)
            addPacket(TracePacket.newBuilder().apply {
                setTimestamp(100L)
                setGpuCounterEvent(GpuCounterEvent.newBuilder().apply {
                    addCounters(GpuCounterEvent.GpuCounter.newBuilder().apply {
                        setCounterId(10)
                        setDoubleValue(1.1)
                    })
                    addCounters(GpuCounterEvent.GpuCounter.newBuilder().apply {
                        setCounterId(20)
                        setIntValue(22)
                    })
                })
            })
            // Packet 2 with one counter
            addPacket(TracePacket.newBuilder().apply {
                setTimestamp(200L)
                setGpuCounterEvent(GpuCounterEvent.newBuilder().apply {
                    addCounters(GpuCounterEvent.GpuCounter.newBuilder().apply {
                        setCounterId(30)
                        setDoubleValue(3.3)
                    })
                })
            })
            // Packet 3 with no counters in the event
            addPacket(TracePacket.newBuilder().apply {
                setTimestamp(300L)
                setGpuCounterEvent(GpuCounterEvent.newBuilder())
            })
            // Packet 4, another type
            addPacket(TracePacket.newBuilder().setTimestamp(400L))
        }.build()

        val counters = trace.getCounters()

        assertThat(counters).hasSize(3)
        assertThat(counters).containsExactly(
            GpuCounter(counterId = 10, timestamp = 100L, value = 1.1),
            GpuCounter(counterId = 20, timestamp = 100L, value = 22.0),
            GpuCounter(counterId = 30, timestamp = 200L, value = 3.3)
        ).inOrder()
    }

    @Test
    fun getCounters_withCounterDescriptorPacket_extractsCountersCorrectly() {
        // Same trace as in getCounters_extractsCountersCorrectly + a descriptor packet.
        val trace = Trace.newBuilder().apply {
            // No timestamp counter descriptor packets; must be ignored
            addPacket(TracePacket.newBuilder().apply {
                setGpuCounterEvent(GpuCounterEvent.newBuilder().apply {
                    setCounterDescriptor(GpuCounterDescriptor.newBuilder())
                })
            })
            // Packet 1 with two counters (double and int)
            addPacket(TracePacket.newBuilder().apply {
                setTimestamp(100L)
                setGpuCounterEvent(GpuCounterEvent.newBuilder().apply {
                    addCounters(GpuCounterEvent.GpuCounter.newBuilder().apply {
                        setCounterId(10)
                        setDoubleValue(1.1)
                    })
                    addCounters(GpuCounterEvent.GpuCounter.newBuilder().apply {
                        setCounterId(20)
                        setIntValue(22)
                    })
                })
            })
            // Packet 2 with one counter
            addPacket(TracePacket.newBuilder().apply {
                setTimestamp(200L)
                setGpuCounterEvent(GpuCounterEvent.newBuilder().apply {
                    addCounters(GpuCounterEvent.GpuCounter.newBuilder().apply {
                        setCounterId(30)
                        setDoubleValue(3.3)
                    })
                })
            })
            // Packet 3 with no counters in the event
            addPacket(TracePacket.newBuilder().apply {
                setTimestamp(300L)
                setGpuCounterEvent(GpuCounterEvent.newBuilder())
            })
            // Packet 4, another type
            addPacket(TracePacket.newBuilder().setTimestamp(400L))
        }.build()

        val counters = trace.getCounters()

        assertThat(counters).hasSize(3)
        assertThat(counters).containsExactly(
            GpuCounter(counterId = 10, timestamp = 100L, value = 1.1),
            GpuCounter(counterId = 20, timestamp = 100L, value = 22.0),
            GpuCounter(counterId = 30, timestamp = 200L, value = 3.3)
        ).inOrder()
    }

    @Test
    fun deviceSupportsRayTracing_eventPresentAndTrue_returnsTrue() {
        val trace = buildTraceWithRayTracingEvent("Test|Data|CtsTestDeviceRayTracingSupport|1")
        assertThat(trace.deviceSupportsRayTracing()).isTrue()
    }

    @Test
    fun deviceSupportsRayTracing_eventPresentAndFalse_returnsFalse() {
        val trace = buildTraceWithRayTracingEvent("Test|Data|CtsTestDeviceRayTracingSupport|0")
        assertThat(trace.deviceSupportsRayTracing()).isFalse()
    }

    @Test
    fun deviceSupportsRayTracing_eventNotPresent_returnsNull() {
        val trace = Trace.newBuilder().build()
        assertThat(trace.deviceSupportsRayTracing()).isNull()
    }

    @Test
    fun deviceSupportsRayTracing_eventMalformed_throws() {
        val trace = buildTraceWithRayTracingEvent("Test|Data|CtsTestDeviceRayTracingSupport|123")
        assertThrows(IllegalArgumentException::class.java) {
            trace.deviceSupportsRayTracing()
        }
    }

    @Test
    fun deviceSupportsRayTracing_ignoresWhitespace() {
        val trace = buildTraceWithRayTracingEvent("Test|Data|CtsTestDeviceRayTracingSupport|1 \n")
        assertThat(trace.deviceSupportsRayTracing()).isTrue()
    }

    private fun buildTraceWithRayTracingEvent(event: String): Trace {
        return Trace.newBuilder().apply {
            addPacket(TracePacket.newBuilder().apply {
                setFtraceEvents(FtraceEventBundle.newBuilder().apply {
                    addEvent(FtraceEvent.newBuilder().apply {
                        setPrint(PrintFtraceEvent.newBuilder().setBuf(event))
                    })
                })
            })
        }.build()
    }
}
