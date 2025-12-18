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
import perfetto.protos.PerfettoTrace.BuiltinClock
import perfetto.protos.PerfettoTrace.ClockSnapshot
import perfetto.protos.PerfettoTrace.FtraceEvent
import perfetto.protos.PerfettoTrace.FtraceEventBundle
import perfetto.protos.PerfettoTrace.GpuCounterDescriptor
import perfetto.protos.PerfettoTrace.GpuCounterDescriptor.GpuCounterSpec
import perfetto.protos.PerfettoTrace.GpuCounterEvent
import perfetto.protos.PerfettoTrace.PrintFtraceEvent
import perfetto.protos.PerfettoTrace.Trace
import perfetto.protos.PerfettoTrace.TraceConfig
import perfetto.protos.PerfettoTrace.TraceConfig.BuiltinDataSource
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

    @Test
    fun containsThreeConsecutiveZeroes_emptyList_returnsFalse() {
        val list = emptyList<GpuCounterValue>()
        assertThat(list.containsThreeConsecutiveZeroes()).isFalse()
    }

    @Test
    fun containsThreeConsecutiveZeroes_lessThanThreeElements_returnsFalse() {
        val list = listOf(
            GpuCounterValue(1, 0.0),
            GpuCounterValue(2, 0.0)
        )
        assertThat(list.containsThreeConsecutiveZeroes()).isFalse()
    }

    @Test
    fun containsThreeConsecutiveZeroes_noConsecutiveZeroes_returnsFalse() {
        val list = listOf(
            GpuCounterValue(1, 0.0),
            GpuCounterValue(2, 1.0),
            GpuCounterValue(3, 0.0),
            GpuCounterValue(4, 0.0),
            GpuCounterValue(5, 2.0)
        )
        assertThat(list.containsThreeConsecutiveZeroes()).isFalse()
    }

    @Test
    fun containsThreeConsecutiveZeroes_hasThreeConsecutiveZeroes_returnsTrue() {
        val list = listOf(
            GpuCounterValue(1, 1.0),
            GpuCounterValue(2, 0.0),
            GpuCounterValue(3, 0.0),
            GpuCounterValue(4, 0.0),
            GpuCounterValue(5, 2.0)
        )
        assertThat(list.containsThreeConsecutiveZeroes()).isTrue()
    }

    @Test
    fun containsThreeConsecutiveZeroes_moreThanThreeConsecutiveZeroes_returnsTrue() {
        val list = listOf(
            GpuCounterValue(1, 0.0),
            GpuCounterValue(2, 0.0),
            GpuCounterValue(3, 0.0),
            GpuCounterValue(4, 0.0)
        )
        assertThat(list.containsThreeConsecutiveZeroes()).isTrue()
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

class GpuCountersTest {
    @Test
    fun constructor_emptyTrace_initializesEmpty() {
        val trace = Trace.newBuilder().build()

        val gpuCounters = GpuCounters(trace)

        assertThat(gpuCounters.counterSpecs).isEmpty()
        assertThat(gpuCounters.counterValues).isEmpty()
        assertThat(gpuCounters.counterDescriptorError).isEmpty()
    }

    @Test
    fun constructor_noGpuCounterEvents_initializesEmpty() {
        val trace = Trace.newBuilder().apply {
            addPacket(TracePacket.newBuilder().setTimestamp(1)) // Some other packet
        }.build()

        val gpuCounters = GpuCounters(trace)

        assertThat(gpuCounters.counterSpecs).isEmpty()
        assertThat(gpuCounters.counterValues).isEmpty()
        assertThat(gpuCounters.counterDescriptorError).isEmpty()
    }

    @Test
    fun constructor_extractsNamesAndValues() {
        val trace = Trace.newBuilder().apply {
            addPacket(TracePacket.newBuilder().apply {
                setGpuCounterEvent(GpuCounterEvent.newBuilder().apply {
                    setCounterDescriptor(GpuCounterDescriptor.newBuilder().apply {
                        addSpecs(
                            GpuCounterSpec.newBuilder().setCounterId(
                                10
                            ).setName("CounterA")
                        )
                        addSpecs(
                            GpuCounterSpec.newBuilder().setCounterId(
                                20
                            ).setName("CounterB")
                        )
                    })
                })
            })
            // Note the packets are out of order
            addPacket(buildCounterPacket(200L, 20, 22.0))
            addPacket(buildCounterPacket(100L, 10, 11.0))
            addPacket(buildCounterPacket(300L, 10, 12.0))
        }.build()

        val gpuCounters = GpuCounters(trace)

        assertThat(gpuCounters.counterDescriptorError).isEmpty()
        assertThat(gpuCounters.counterSpecs).containsExactly(
            10,
            GpuCounterSpec.newBuilder().setCounterId(10).setName("CounterA").build(),
            20,
            GpuCounterSpec.newBuilder().setCounterId(20).setName("CounterB").build()
        )
        assertThat(gpuCounters.counterValues.keys).containsExactly(10, 20)
        assertThat(gpuCounters.counterValues[10]).containsExactly(
            GpuCounterValue(100L, 11.0),
            GpuCounterValue(300L, 12.0)
        ).inOrder()
        assertThat(gpuCounters.counterValues[20]).containsExactly(
            GpuCounterValue(200L, 22.0)
        ).inOrder()
    }

    @Test
    fun constructor_descriptorErrors_noDescriptor() {
        val trace = Trace.newBuilder().apply {
            addPacket(buildCounterPacket(100L, 10, 11.0))
            addPacket(TracePacket.newBuilder().setGpuCounterEvent(GpuCounterEvent.newBuilder()))
        }.build()

        val gpuCounters = GpuCounters(trace)

        assertThat(
            gpuCounters.counterDescriptorError
        ).isEqualTo("first counter event must contain a descriptor")
    }

    @Test
    fun constructor_descriptorErrors_noSpecs() {
        val trace = Trace.newBuilder().apply {
            addPacket(TracePacket.newBuilder().apply {
                setGpuCounterEvent(
                    GpuCounterEvent.newBuilder().setCounterDescriptor(
                        GpuCounterDescriptor.newBuilder()
                    )
                )
            })
        }.build()

        val gpuCounters = GpuCounters(trace)

        assertThat(gpuCounters.counterDescriptorError).isEqualTo("descriptor contains no specs")
    }

    @Test
    fun constructor_descriptorErrors_specMissingName() {
        val trace = Trace.newBuilder().apply {
            addPacket(TracePacket.newBuilder().apply {
                setGpuCounterEvent(GpuCounterEvent.newBuilder().apply {
                    setCounterDescriptor(GpuCounterDescriptor.newBuilder().apply {
                        addSpecs(GpuCounterDescriptor.GpuCounterSpec.newBuilder().setCounterId(10))
                    })
                })
            })
        }.build()
        val gpuCounters = GpuCounters(trace)
        assertThat(
            gpuCounters.counterDescriptorError
        ).isEqualTo("missing or empty name for counter id 10")
    }

    @Test
    fun constructor_handlesClockSync() {
        val trace = Trace.newBuilder().apply {
            addPacket(TracePacket.newBuilder().apply {
                setTraceConfig(TraceConfig.newBuilder().apply {
                    setBuiltinDataSources(BuiltinDataSource.newBuilder().apply {
                        setPrimaryTraceClock(BuiltinClock.BUILTIN_CLOCK_MONOTONIC)
                    })
                })
            })
            addPacket(TracePacket.newBuilder().apply {
                setClockSnapshot(ClockSnapshot.newBuilder().apply {
                    addClocks(
                        ClockSnapshot.Clock.newBuilder().setClockId(
                            BuiltinClock.BUILTIN_CLOCK_MONOTONIC.number
                        ).setTimestamp(10L)
                    )
                    addClocks(
                        ClockSnapshot.Clock.newBuilder().setClockId(
                            BuiltinClock.BUILTIN_CLOCK_UNKNOWN.number
                        ).setTimestamp(100L)
                    )
                })
            })
            addPacket(TracePacket.newBuilder().apply {
                setClockSnapshot(ClockSnapshot.newBuilder().apply {
                    addClocks(
                        ClockSnapshot.Clock.newBuilder().setClockId(
                            BuiltinClock.BUILTIN_CLOCK_MONOTONIC.number
                        ).setTimestamp(20L)
                    )
                    addClocks(
                        ClockSnapshot.Clock.newBuilder().setClockId(
                            BuiltinClock.BUILTIN_CLOCK_UNKNOWN.number
                        ).setTimestamp(200L)
                    )
                })
            })
            // Counter packet with a secondary clock id
            addPacket(TracePacket.newBuilder().apply {
                setTimestamp(150L)
                setTimestampClockId(BuiltinClock.BUILTIN_CLOCK_UNKNOWN.number)
                setGpuCounterEvent(GpuCounterEvent.newBuilder().apply {
                    addCounters(
                        GpuCounterEvent.GpuCounter.newBuilder().setCounterId(10).setIntValue(123)
                    )
                })
            })
        }.build()

        val gpuCounters = GpuCounters(trace)

        assertThat(gpuCounters.counterValues[10]).containsExactly(GpuCounterValue(15L, 123.0))
    }

    private fun buildCounterPacket(timestamp: Long, counterId: Int, value: Double): TracePacket {
        return TracePacket.newBuilder().apply {
            setTimestamp(timestamp)
            setGpuCounterEvent(GpuCounterEvent.newBuilder().apply {
                addCounters(GpuCounterEvent.GpuCounter.newBuilder().apply {
                    setCounterId(counterId)
                    setDoubleValue(value)
                })
            })
        }.build()
    }
}
