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

import perfetto.protos.PerfettoConfig.GpuCounterDescriptor.GpuCounterSpec
import perfetto.protos.PerfettoConfig.TracingServiceState

object DataSourceParser {

    data class DataSources(
        val renderStagesSourceFound: Boolean,
        val gpuCountersSourceFound: Boolean,
        val gpuCounterSpecsList: List<GpuCounterSpec>,
        val allGpuCounterIds: Set<Int>,
        val defaultGpuCounterIds: Set<Int>
    )

    @JvmStatic
    fun parse(state: TracingServiceState): DataSources {
        var renderStagesSourceFound = false
        var gpuCountersSourceFound = false
        var gpuCounterSpecsList: List<GpuCounterSpec> = emptyList()
        var allGpuCounterIds: Set<Int> = emptySet()
        var defaultGpuCounterIds: Set<Int> = emptySet()

        state.dataSourcesList.forEach { source ->
            val descriptor = source.dsDescriptor

            if (descriptor.name == STAGES_SOURCE_NAME) {
                renderStagesSourceFound = true
            }

            if (descriptor.name == COUNTERS_SOURCE_NAME) {
                gpuCountersSourceFound = true
                if (descriptor.hasGpuCounterDescriptor()) {
                    gpuCounterSpecsList = descriptor.gpuCounterDescriptor.specsList
                    allGpuCounterIds = gpuCounterSpecsList.map { it.counterId }.toSet()
                    defaultGpuCounterIds = gpuCounterSpecsList.filter { it.selectByDefault }
                        .map { it.counterId }
                        .toSet()
                }
            }
        }

        return DataSources(
            renderStagesSourceFound = renderStagesSourceFound,
            gpuCountersSourceFound = gpuCountersSourceFound,
            gpuCounterSpecsList = gpuCounterSpecsList,
            allGpuCounterIds = allGpuCounterIds,
            defaultGpuCounterIds = defaultGpuCounterIds
        )
    }
}
