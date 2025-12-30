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

import kotlin.Int
import kotlin.Long
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.MutableList
import kotlin.collections.MutableMap
import perfetto.protos.PerfettoTrace.BuiltinClock
import perfetto.protos.PerfettoTrace.ClockSnapshot
import perfetto.protos.PerfettoTrace.Trace
import perfetto.protos.PerfettoTrace.TracePacket

fun Trace.getTraceClockSnapshots(): List<Map<Int, Long>> {
    val packetList: List<TracePacket> = this.packetList
    val snapshots: MutableList<Map<Int, Long>> = ArrayList()
    for (packet in packetList) {
        if (!packet.hasClockSnapshot()) continue

        val snapshot: ClockSnapshot = packet.clockSnapshot
        val snapshotMap: MutableMap<Int, Long> = HashMap()
        for (clock in snapshot.clocksList) {
            snapshotMap[clock.clockId] = clock.timestamp
        }
        snapshots.add(snapshotMap)
    }
    return snapshots
}

fun TracePacket.getTimestampNs(clockSnapshots: List<Map<Int, Long>>): Long {
    if (!this.hasTimestampClockId()) {
        return this.timestamp
    }
    return convertTimestamp(
        timestamp,
        this.timestampClockId,
        clockSnapshots
    )
}

private fun convertTimestamp(
    timestamp: Long,
    sourceClockId: Int,
    clockSnapshots: List<Map<Int, Long>>
): Long {
    val destClockId = BuiltinClock.BUILTIN_CLOCK_BOOTTIME.number
    val relevantSnapshots = clockSnapshots.filter {
        it.containsKey(sourceClockId) && it.containsKey(destClockId)
    }.distinctBy { it[sourceClockId] }.sortedBy { it[sourceClockId] }

    if (relevantSnapshots.size < 2) {
        throw IllegalArgumentException(
            "Need at least two snapshots with both clocks for interpolation"
        )
    }

    val searchResult = relevantSnapshots.map { it[sourceClockId]!! }.binarySearch(timestamp)
    if (searchResult >= 0) return relevantSnapshots[searchResult][destClockId]!!

    val index2 = searchResult.inv().coerceIn(1, relevantSnapshots.lastIndex)
    val index1 = index2 - 1

    // Interpolate between the two closest snapshots
    val sourceDiff = relevantSnapshots[index2][sourceClockId]!! -
            relevantSnapshots[index1][sourceClockId]!!
    val destDiff = relevantSnapshots[index2][destClockId]!! -
            relevantSnapshots[index1][destClockId]!!
    val timeOffset = timestamp - relevantSnapshots[index1][sourceClockId]!!

    return relevantSnapshots[index1][destClockId]!! + (timeOffset * destDiff / sourceDiff)
}
