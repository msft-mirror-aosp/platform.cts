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
import perfetto.protos.PerfettoTrace
import perfetto.protos.PerfettoTrace.ClockSnapshot
import perfetto.protos.PerfettoTrace.ClockSnapshot.Clock
import perfetto.protos.PerfettoTrace.Trace
import perfetto.protos.PerfettoTrace.TracePacket

/** Run as {@code atest GpuProfilingUtilsTestCases}. */
class ClockUtilsTest {
    @Test
    fun getAllDataSourcesStartedNs_allEventsStartedEventIsFalse_returnsZero() {
        val trace = Trace.newBuilder().apply {
            addPacket(
                TracePacket.newBuilder().apply {
                    timestamp = 150L
                    setServiceEvent(
                        PerfettoTrace.TracingServiceEvent.newBuilder().apply {
                            allDataSourcesStarted = false
                        }
                    )
                }
            )
        }.build()

        assertThat(trace.getAllDataSourcesStartedNs()).isEqualTo(0)
    }

    @Test
    fun getAllDataSourcesStartedNs_returnsTimestamp() {
        val trace = Trace.newBuilder().apply {
            addPacket(
                TracePacket.newBuilder().apply {
                    timestamp = 150L
                    setServiceEvent(
                        PerfettoTrace.TracingServiceEvent.newBuilder().apply {
                            allDataSourcesStarted = true
                        }
                    )
                }
            )
        }.build()

        assertThat(trace.getAllDataSourcesStartedNs()).isEqualTo(150L)
    }

    @Test
    fun getTimestampNs_interpolatesCorrectly() {
        val testSnapshots = ClockSnapshots(
            Trace.newBuilder().apply {
                addPacket(clockPacket(mapOf(1 to 100L, BOOTTIME to 1000L)))
                addPacket(clockPacket(mapOf(1 to 200L, BOOTTIME to 2000L)))
                addPacket(clockPacket(mapOf(1 to 300L, BOOTTIME to 3000L)))
            }.build()
        )
        val packet = TracePacket.newBuilder()
            .setTimestamp(150L)
            .setTimestampClockId(1)
            .build()
        assertThat(packet.getTimestampNs(testSnapshots)).isEqualTo(1050L)
    }

    @Test
    fun getTimestampNs_handlesExactMatch() {
        val testSnapshots = ClockSnapshots(
            Trace.newBuilder().apply {
                addPacket(clockPacket(mapOf(1 to 100L, BOOTTIME to 1000L)))
                addPacket(clockPacket(mapOf(1 to 200L, BOOTTIME to 2000L)))
                addPacket(clockPacket(mapOf(1 to 300L, BOOTTIME to 3000L)))
            }.build()
        )
        val packet = TracePacket.newBuilder()
            .setTimestamp(200L)
            .setTimestampClockId(1)
            .build()
        assertThat(packet.getTimestampNs(testSnapshots)).isEqualTo(2000L)
    }

    @Test
    fun getTimestampNs_extrapolatesBeforeFirstSnapshot() {
        val testSnapshots = ClockSnapshots(
            Trace.newBuilder().apply {
                addPacket(clockPacket(mapOf(1 to 100L, BOOTTIME to 1000L)))
                addPacket(clockPacket(mapOf(1 to 200L, BOOTTIME to 2000L)))
                addPacket(clockPacket(mapOf(1 to 300L, BOOTTIME to 3000L)))
            }.build()
        )
        val packet = TracePacket.newBuilder()
            .setTimestamp(50L)
            .setTimestampClockId(1)
            .build()
        assertThat(packet.getTimestampNs(testSnapshots)).isEqualTo(950L)
    }

    @Test
    fun getTimestampNs_extrapolatesAfterLastSnapshot() {
        val testSnapshots = ClockSnapshots(
            Trace.newBuilder().apply {
                addPacket(clockPacket(mapOf(1 to 100L, BOOTTIME to 1000L)))
                addPacket(clockPacket(mapOf(1 to 200L, BOOTTIME to 2000L)))
                addPacket(clockPacket(mapOf(1 to 300L, BOOTTIME to 3000L)))
            }.build()
        )
        val packet = TracePacket.newBuilder()
            .setTimestamp(350L)
            .setTimestampClockId(1)
            .build()
        assertThat(packet.getTimestampNs(testSnapshots)).isEqualTo(3050L)
    }

    @Test
    fun getTimestampNs_withDuplicateSnapshots_interpolatesCorrectly() {
        val testSnapshots = ClockSnapshots(
            Trace.newBuilder().apply {
                addPacket(clockPacket(mapOf(1 to 100L, BOOTTIME to 1000L)))
                addPacket(clockPacket(mapOf(1 to 100L, BOOTTIME to 1000L)))
                addPacket(clockPacket(mapOf(1 to 200L, BOOTTIME to 2000L)))
                addPacket(clockPacket(mapOf(1 to 300L, BOOTTIME to 3000L)))
            }.build()
        )
        val packet = TracePacket.newBuilder()
            .setTimestamp(150L)
            .setTimestampClockId(1)
            .build()
        assertThat(packet.getTimestampNs(testSnapshots)).isEqualTo(1050L)
    }

    @Test
    fun getTimestampNs_withClockDrift_interpolatesCorrectly() {
        val snapshotsWithDrift = ClockSnapshots(
            Trace.newBuilder().apply {
                addPacket(clockPacket(mapOf(1 to 100L, BOOTTIME to 1000L)))
                addPacket(clockPacket(mapOf(1 to 200L, BOOTTIME to 2000L)))
                addPacket(clockPacket(mapOf(1 to 300L, BOOTTIME to 4000L)))
            }.build()
        )
        val packetBuilder = TracePacket.newBuilder().setTimestampClockId(1)

        assertThat(
            packetBuilder.setTimestamp(50L).build().getTimestampNs(snapshotsWithDrift)
        ).isEqualTo(950L)
        assertThat(
            packetBuilder.setTimestamp(150L).build().getTimestampNs(snapshotsWithDrift)
        ).isEqualTo(1050L)
        assertThat(
            packetBuilder.setTimestamp(250L).build().getTimestampNs(snapshotsWithDrift)
        ).isEqualTo(2050L)
        assertThat(
            packetBuilder.setTimestamp(350L).build().getTimestampNs(snapshotsWithDrift)
        ).isEqualTo(4050L)
    }

    @Test
    fun getTimestampNs_withMissingClocks_interpolatesWherePossible() {
        val snapshotsWithMissing = ClockSnapshots(
            Trace.newBuilder().apply {
                addPacket(clockPacket(mapOf(1 to 100L, BOOTTIME to 1000L, 3 to 10000L)))
                addPacket(clockPacket(mapOf(BOOTTIME to 2000L, 3 to 20000L))) // Missing clock 1
                addPacket(clockPacket(mapOf(1 to 300L, BOOTTIME to 3000L, 3 to 30000L)))
            }.build()
        )

        val packet = TracePacket.newBuilder()
            .setTimestamp(150L)
            .setTimestampClockId(1)
            .build()
        assertThat(packet.getTimestampNs(snapshotsWithMissing)).isEqualTo(1050L)
    }

    @Test
    fun getTimestampNs_throwsForEmptySnapshots() {
        val notEnoughSnapshots = ClockSnapshots(Trace.newBuilder().build())
        val packet = TracePacket.newBuilder()
            .setTimestamp(150L)
            .setTimestampClockId(1)
            .build()

        assertThrows(IllegalArgumentException::class.java) {
            packet.getTimestampNs(notEnoughSnapshots)
        }
    }

    @Test
    fun getTimestampNs_verifyInterpolationNearBoundaries() {
        val testSnapshots = ClockSnapshots(
            Trace.newBuilder().apply {
                addPacket(clockPacket(mapOf(1 to 100L, BOOTTIME to 1000L)))
                addPacket(clockPacket(mapOf(1 to 200L, BOOTTIME to 2000L)))
                addPacket(clockPacket(mapOf(1 to 300L, BOOTTIME to 3000L)))
            }.build()
        )

        assertThat(packet1(99).getTimestampNs(testSnapshots)).isEqualTo(999)
        assertThat(packet1(100).getTimestampNs(testSnapshots)).isEqualTo(1000)
        assertThat(packet1(101).getTimestampNs(testSnapshots)).isEqualTo(1001)

        assertThat(packet1(199).getTimestampNs(testSnapshots)).isEqualTo(1099)
        assertThat(packet1(200).getTimestampNs(testSnapshots)).isEqualTo(2000)
        assertThat(packet1(201).getTimestampNs(testSnapshots)).isEqualTo(2001)

        assertThat(packet1(299).getTimestampNs(testSnapshots)).isEqualTo(2099)
        assertThat(packet1(300).getTimestampNs(testSnapshots)).isEqualTo(3000)
        assertThat(packet1(301).getTimestampNs(testSnapshots)).isEqualTo(3001)
    }

    private fun packet1(timestamp: Long) = TracePacket.newBuilder()
            .setTimestamp(timestamp)
            .setTimestampClockId(1)
            .build()

    private fun clockPacket(clocks: Map<Int, Long>) = TracePacket.newBuilder().apply {
        setClockSnapshot(ClockSnapshot.newBuilder().apply {
            for (clock in clocks) {
                addClocks(Clock.newBuilder().setClockId(clock.key).setTimestamp(clock.value))
            }
        }).build()
    }
}
