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
import perfetto.protos.PerfettoTrace.ClockSnapshot.Clock
import perfetto.protos.PerfettoTrace.Trace
import perfetto.protos.PerfettoTrace.TraceConfig
import perfetto.protos.PerfettoTrace.TraceConfig.BuiltinDataSource
import perfetto.protos.PerfettoTrace.TracePacket

/** Run as {@code atest GpuProfilingUtilsTestCases}. */
class ClockUtilsTest {
    @Test
    fun getPrimaryTraceClock_configHasClock_usesClockFromConfig() {
        val trace = Trace.newBuilder().apply {
            addPacket(TracePacket.newBuilder().apply {
                setTraceConfig(TraceConfig.newBuilder().apply {
                    setBuiltinDataSources(BuiltinDataSource.newBuilder().apply {
                        setPrimaryTraceClock(BuiltinClock.BUILTIN_CLOCK_MONOTONIC)
                    })
                })
            })
            // This snapshot should be ignored
            addPacket(TracePacket.newBuilder().apply {
                setClockSnapshot(ClockSnapshot.newBuilder().apply {
                    setPrimaryTraceClock(BuiltinClock.BUILTIN_CLOCK_UNKNOWN)
                })
            })
        }.build()

        assertThat(trace.getPrimaryTraceClock()).isEqualTo(BuiltinClock.BUILTIN_CLOCK_MONOTONIC)
    }

    @Test
    fun getPrimaryTraceClock_noConfig_usesClockFromSnapshot() {
        val trace = Trace.newBuilder().apply {
            addPacket(TracePacket.newBuilder().apply {
                setClockSnapshot(ClockSnapshot.newBuilder().apply {
                    setPrimaryTraceClock(BuiltinClock.BUILTIN_CLOCK_UNKNOWN)
                })
            })
        }.build()

        assertThat(trace.getPrimaryTraceClock()).isEqualTo(BuiltinClock.BUILTIN_CLOCK_UNKNOWN)
    }

    @Test
    fun getPrimaryTraceClock_noPrimaryClockData_fallsBackToBoottime() {
        val trace = Trace.newBuilder().apply {
            addPacket(TracePacket.newBuilder().apply {
                setClockSnapshot(ClockSnapshot.newBuilder().apply {
                    addClocks(Clock.newBuilder().apply {
                        setClockId(456)
                    })
                    addClocks(Clock.newBuilder().apply {
                        setClockId(123)
                    })
                })
            })
        }.build()

        assertThat(trace.getPrimaryTraceClock()).isEqualTo(BuiltinClock.BUILTIN_CLOCK_BOOTTIME)
    }

    @Test
    fun getTraceClockSnapshots_extractsSnapshotsCorrectly() {
        val trace = Trace.newBuilder().apply {
            addPacket(TracePacket.newBuilder().apply {
                setClockSnapshot(ClockSnapshot.newBuilder().apply {
                    addClocks(Clock.newBuilder().setClockId(1).setTimestamp(100L))
                    addClocks(Clock.newBuilder().setClockId(2).setTimestamp(200L))
                })
            })
            addPacket(TracePacket.newBuilder().apply {
                setClockSnapshot(ClockSnapshot.newBuilder().apply {
                    addClocks(Clock.newBuilder().setClockId(1).setTimestamp(150L))
                    addClocks(Clock.newBuilder().setClockId(3).setTimestamp(300L))
                })
            })
            addPacket(TracePacket.newBuilder()) // Packet without clock snapshot
        }.build()

        val snapshots = trace.getTraceClockSnapshots()

        assertThat(snapshots).hasSize(2)
        assertThat(snapshots[0]).containsExactly(1, 100L, 2, 200L)
        assertThat(snapshots[1]).containsExactly(1, 150L, 3, 300L)
    }

    @Test
    fun convertTimestamp_interpolatesCorrectly() {
        val testSnapshots = listOf(
            mapOf(1 to 100L, 2 to 1000L),
            mapOf(1 to 200L, 2 to 2000L),
            mapOf(1 to 300L, 2 to 3000L)
        )
        assertThat(convertTimestamp(150L, 1, 2, testSnapshots)).isEqualTo(1500L)
    }

    @Test
    fun convertTimestamp_handlesExactMatch() {
        val testSnapshots = listOf(
            mapOf(1 to 100L, 2 to 1000L),
            mapOf(1 to 200L, 2 to 2000L),
            mapOf(1 to 300L, 2 to 3000L)
        )
        assertThat(convertTimestamp(200L, 1, 2, testSnapshots)).isEqualTo(2000L)
    }

    @Test
    fun convertTimestamp_extrapolatesBeforeFirstSnapshot() {
        val testSnapshots = listOf(
            mapOf(1 to 100L, 2 to 1000L),
            mapOf(1 to 200L, 2 to 2000L),
            mapOf(1 to 300L, 2 to 3000L)
        )
        assertThat(convertTimestamp(50L, 1, 2, testSnapshots)).isEqualTo(500L)
    }

    @Test
    fun convertTimestamp_extrapolatesAfterLastSnapshot() {
        val testSnapshots = listOf(
            mapOf(1 to 100L, 2 to 1000L),
            mapOf(1 to 200L, 2 to 2000L),
            mapOf(1 to 300L, 2 to 3000L)
        )
        assertThat(convertTimestamp(350L, 1, 2, testSnapshots)).isEqualTo(3500L)
    }

    @Test
    fun convertTimestamp_withDuplicateSnapshots_interpolatesCorrectly() {
        val testSnapshots = listOf(
            mapOf(1 to 100L, 2 to 1000L),
            mapOf(1 to 100L, 2 to 1000L),
            mapOf(1 to 200L, 2 to 2000L),
            mapOf(1 to 300L, 2 to 3000L)
        )
        assertThat(convertTimestamp(150L, 1, 2, testSnapshots)).isEqualTo(1500L)
    }

    @Test
    fun convertTimestamp_withClockDrift_interpolatesCorrectly() {
        val snapshotsWithDrift = listOf(
            mapOf(1 to 100L, 2 to 1000L),
            mapOf(1 to 200L, 2 to 2000L),
            mapOf(1 to 300L, 2 to 4000L)
        )
        assertThat(convertTimestamp(50L, 1, 2, snapshotsWithDrift)).isEqualTo(500L)
        assertThat(convertTimestamp(150L, 1, 2, snapshotsWithDrift)).isEqualTo(1500L)
        assertThat(convertTimestamp(250L, 1, 2, snapshotsWithDrift)).isEqualTo(3000L)
        assertThat(convertTimestamp(350L, 1, 2, snapshotsWithDrift)).isEqualTo(5000L)
    }

    @Test
    fun convertTimestamp_withMissingClocks_interpolatesWherePossible() {
        val snapshotsWithMissing = listOf(
            mapOf(1 to 100L, 2 to 1000L, 3 to 10000L),
            mapOf(2 to 2000L, 3 to 20000L), // Missing clock 1
            mapOf(1 to 300L, 2 to 3000L, 3 to 30000L),
        )
        assertThat(convertTimestamp(150L, 1, 2, snapshotsWithMissing)).isEqualTo(1500L)
        assertThat(convertTimestamp(150L, 1, 3, snapshotsWithMissing)).isEqualTo(15000L)
    }

    @Test
    fun convertTimestamp_withMissingClocks_throwsForIncompatibleSnapshots() {
        val snapshotsWithMissing = listOf(
            mapOf(1 to 100L, 2 to 1000L, 3 to 10000L),
            mapOf(2 to 2000L, 3 to 20000L), // Missing clock 1
            mapOf(1 to 300L, 3 to 30000L), // Missing clock 2
        )
        assertThrows(IllegalArgumentException::class.java) {
            convertTimestamp(150L, 1, 2, snapshotsWithMissing)
        }
    }

    @Test
    fun convertTimestamp_throwsForInsufficientSnapshots() {
        val notEnoughSnapshots = listOf(mapOf(1 to 100L, 2 to 1000L))
        assertThrows(IllegalArgumentException::class.java) {
            convertTimestamp(150L, 1, 2, notEnoughSnapshots)
        }
    }
}
