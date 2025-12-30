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
import perfetto.protos.PerfettoTrace.TracePacket

/** Run as {@code atest GpuProfilingUtilsTestCases}. */
class ClockUtilsTest {
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
    fun getTimestampNs_interpolatesCorrectly() {
        val bootTime = BuiltinClock.BUILTIN_CLOCK_BOOTTIME.number
        val testSnapshots = listOf(
            mapOf(1 to 100L, bootTime to 1000L),
            mapOf(1 to 200L, bootTime to 2000L),
            mapOf(1 to 300L, bootTime to 3000L)
        )
        val packet = TracePacket.newBuilder()
            .setTimestamp(150L)
            .setTimestampClockId(1)
            .build()
        assertThat(packet.getTimestampNs(testSnapshots)).isEqualTo(1500L)
    }

    @Test
    fun getTimestampNs_handlesExactMatch() {
        val bootTime = BuiltinClock.BUILTIN_CLOCK_BOOTTIME.number
        val testSnapshots = listOf(
            mapOf(1 to 100L, bootTime to 1000L),
            mapOf(1 to 200L, bootTime to 2000L),
            mapOf(1 to 300L, bootTime to 3000L)
        )
        val packet = TracePacket.newBuilder()
            .setTimestamp(200L)
            .setTimestampClockId(1)
            .build()
        assertThat(packet.getTimestampNs(testSnapshots)).isEqualTo(2000L)
    }

    @Test
    fun getTimestampNs_extrapolatesBeforeFirstSnapshot() {
        val bootTime = BuiltinClock.BUILTIN_CLOCK_BOOTTIME.number
        val testSnapshots = listOf(
            mapOf(1 to 100L, bootTime to 1000L),
            mapOf(1 to 200L, bootTime to 2000L),
            mapOf(1 to 300L, bootTime to 3000L)
        )
        val packet = TracePacket.newBuilder()
            .setTimestamp(50L)
            .setTimestampClockId(1)
            .build()
        assertThat(packet.getTimestampNs(testSnapshots)).isEqualTo(500L)
    }

    @Test
    fun getTimestampNs_extrapolatesAfterLastSnapshot() {
        val bootTime = BuiltinClock.BUILTIN_CLOCK_BOOTTIME.number
        val testSnapshots = listOf(
            mapOf(1 to 100L, bootTime to 1000L),
            mapOf(1 to 200L, bootTime to 2000L),
            mapOf(1 to 300L, bootTime to 3000L)
        )
        val packet = TracePacket.newBuilder()
            .setTimestamp(350L)
            .setTimestampClockId(1)
            .build()
        assertThat(packet.getTimestampNs(testSnapshots)).isEqualTo(3500L)
    }

    @Test
    fun getTimestampNs_withDuplicateSnapshots_interpolatesCorrectly() {
        val bootTime = BuiltinClock.BUILTIN_CLOCK_BOOTTIME.number
        val testSnapshots = listOf(
            mapOf(1 to 100L, bootTime to 1000L),
            mapOf(1 to 100L, bootTime to 1000L),
            mapOf(1 to 200L, bootTime to 2000L),
            mapOf(1 to 300L, bootTime to 3000L)
        )
        val packet = TracePacket.newBuilder()
            .setTimestamp(150L)
            .setTimestampClockId(1)
            .build()
        assertThat(packet.getTimestampNs(testSnapshots)).isEqualTo(1500L)
    }

    @Test
    fun getTimestampNs_withClockDrift_interpolatesCorrectly() {
        val bootTime = BuiltinClock.BUILTIN_CLOCK_BOOTTIME.number
        val snapshotsWithDrift = listOf(
            mapOf(1 to 100L, bootTime to 1000L),
            mapOf(1 to 200L, bootTime to 2000L),
            mapOf(1 to 300L, bootTime to 4000L)
        )
        val packetBuilder = TracePacket.newBuilder().setTimestampClockId(1)

        assertThat(
            packetBuilder.setTimestamp(50L).build().getTimestampNs(snapshotsWithDrift)
        ).isEqualTo(500L)
        assertThat(
            packetBuilder.setTimestamp(150L).build().getTimestampNs(snapshotsWithDrift)
        ).isEqualTo(1500L)
        assertThat(
            packetBuilder.setTimestamp(250L).build().getTimestampNs(snapshotsWithDrift)
        ).isEqualTo(3000L)
        assertThat(
            packetBuilder.setTimestamp(350L).build().getTimestampNs(snapshotsWithDrift)
        ).isEqualTo(5000L)
    }

    @Test
    fun getTimestampNs_withMissingClocks_interpolatesWherePossible() {
        val bootTime = BuiltinClock.BUILTIN_CLOCK_BOOTTIME.number
        val snapshotsWithMissing = listOf(
            mapOf(1 to 100L, bootTime to 1000L, 3 to 10000L),
            mapOf(bootTime to 2000L, 3 to 20000L), // Missing clock 1
            mapOf(1 to 300L, bootTime to 3000L, 3 to 30000L),
        )
        val packet = TracePacket.newBuilder()
            .setTimestamp(150L)
            .setTimestampClockId(1)
            .build()
        assertThat(packet.getTimestampNs(snapshotsWithMissing)).isEqualTo(1500L)
    }

    @Test
    fun getTimestampNs_withMissingClocks_throwsForIncompatibleSnapshots() {
        val bootTime = BuiltinClock.BUILTIN_CLOCK_BOOTTIME.number
        val snapshotsWithMissing = listOf(
            mapOf(1 to 100L, bootTime to 1000L, 3 to 10000L),
            mapOf(bootTime to 2000L, 3 to 20000L), // Missing clock 1
            mapOf(1 to 300L, 3 to 30000L), // Missing clock bootTime
        )
        val packet = TracePacket.newBuilder()
            .setTimestamp(150L)
            .setTimestampClockId(1)
            .build()
        assertThrows(IllegalArgumentException::class.java) {
            packet.getTimestampNs(snapshotsWithMissing)
        }
    }

    @Test
    fun getTimestampNs_throwsForInsufficientSnapshots() {
        val bootTime = BuiltinClock.BUILTIN_CLOCK_BOOTTIME.number
        val notEnoughSnapshots = listOf(mapOf(1 to 100L, bootTime to 1000L))
        val packet = TracePacket.newBuilder()
            .setTimestamp(150L)
            .setTimestampClockId(1)
            .build()
        assertThrows(IllegalArgumentException::class.java) {
            packet.getTimestampNs(notEnoughSnapshots)
        }
    }
}
