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

import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.not
import org.junit.rules.ErrorCollector

class DataSourceValidator(private val errorCollector: ErrorCollector) {

    fun validateRenderStagesFound(parsedData: DataSourceParser.DataSources) {
        errorCollector.checkThat(
            "Producer $STAGES_SOURCE_NAME not found",
            parsedData.renderStagesSourceFound,
            `is`(true)
        )
    }

    fun validateGpuCountersFound(parsedData: DataSourceParser.DataSources) {
        errorCollector.checkThat(
            "Producer $COUNTERS_SOURCE_NAME not found",
            parsedData.gpuCountersSourceFound,
            `is`(true)
        )
    }

    fun validateGpuCounters(parsedData: DataSourceParser.DataSources) {
        val gpuCounterSpecsList = parsedData.gpuCounterSpecsList
        for (spec in gpuCounterSpecsList) {
            errorCollector.checkThat(
                "GpuCounterDescriptor must have a non-empty name. " +
                        "GPU counter id is [${spec.counterId}].",
                spec.hasName() && spec.name.isNotEmpty(),
                `is`(true)
            )
            errorCollector.checkThat(
                "GpuCounterDescriptor must have a non-empty description. " +
                        "GPU counter id is [${spec.counterId}].",
                spec.hasDescription() && spec.description.isNotEmpty(),
                `is`(true)
            )
        }

        val counterIdsList = gpuCounterSpecsList.map { it.counterId }
        val allCounterIds = counterIdsList.toSet()
        errorCollector.checkThat(
            "Counter IDs in DataSourceDescriptor must be unique.",
            counterIdsList.size == allCounterIds.size,
            `is`(true)
        )

        val defaultCounterIds = gpuCounterSpecsList.filter { it.selectByDefault }
            .map { it.counterId }
            .toSet()

        errorCollector.checkThat(
            "No default counters set.",
            defaultCounterIds,
            not(empty())
        )
    }
}
