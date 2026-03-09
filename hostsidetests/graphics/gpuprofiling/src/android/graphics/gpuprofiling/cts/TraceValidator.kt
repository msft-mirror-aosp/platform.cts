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

import java.time.Duration
import org.hamcrest.Matchers.both
import org.hamcrest.Matchers.greaterThan
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.lessThan
import org.junit.rules.ErrorCollector
import perfetto.protos.PerfettoConfig
import perfetto.protos.PerfettoConfig.GpuCounterDescriptor.GpuCounterGroup

class TraceValidator(private val errorCollector: ErrorCollector) {

    fun validateGpuFrequency(trace: TraceParser.ParsedTrace) {
        errorCollector.checkThat(
            "Trace does not contain valid GPU frequency.",
            trace.containsGpuFrequencyEvent,
            `is`(true)
        )
    }

    fun validateDefaultCounterValuesPresence(
        trace: TraceParser.ParsedTrace,
        defaultCounterIds: Set<Int>
    ) {
        errorCollector.checkThat(
            "Trace failed to report some of the default GPU counter values.",
            trace.gpuCounters.counterValues.keys.equals(defaultCounterIds),
            `is`(true)
        )
    }

    fun validateAllGpuCountersReported(
        trace: TraceParser.ParsedTrace,
        allCounterIds: Set<Int>
    ) {
        errorCollector.checkThat(
            "Trace does not contain valid and complete GPU counter descriptions: ",
            getSummaryDescriptionOfIdsInTrace(trace.gpuCounters, allCounterIds),
            `is`("SUCCESS")
        )
    }

    fun validateGpuUtilisationCounter(trace: TraceParser.ParsedTrace) {
        val foundGpuUtilisationCounter =
            trace.gpuCounters.counterValues.entries.any { entry ->
                val spec = trace.gpuCounters.counterSpecs[entry.key]
                spec != null && counterMatchesGpuUtilisation(
                    spec,
                    entry.value,
                    trace.gpuUsageTimeline
                )
            }

        errorCollector.checkThat(
            "Trace does not contain a GPU counter that reflects GPU utilisation.",
            foundGpuUtilisationCounter,
            `is`(true)
        )
    }

    fun validateZeroesOptimization(trace: TraceParser.ParsedTrace) {
        val summary = getSummaryComplianceWithZeroesOptimization(trace.gpuCounters)
        errorCollector.checkThat(
            "Some of the counters report 0 values unnecessarily: ",
            summary,
            `is`("")
        )
    }

    fun validateSamplingRate(trace: TraceParser.ParsedTrace, expected: Duration) {
        val expectedNanos = expected.toNanos()

        // The mode of bucketed rates from trace should be within 50% of expected rate.
        val mostCommonRateBucket = calculateMostCommonIntervalNs(
            trace.gpuCounters.eventTimestampsNs,
            expectedNanos / 10
        )

        errorCollector.checkThat(
            "Most common sampling rate too different from expected: ",
            mostCommonRateBucket,
            both(greaterThan((0.5 * expectedNanos).toLong()))
                .and(lessThan((1.5 * expectedNanos).toLong()))
        )
    }

    fun validateRequiredGroupsPresent(
        trace: TraceParser.ParsedTrace,
        gpuCounterSpecsList:
        List<perfetto.protos.PerfettoConfig.GpuCounterDescriptor.GpuCounterSpec>
    ) {
        errorCollector.checkThat(
            "Ray tracing support information is missing from the trace.",
            trace.hasRayTracingSupport,
            org.hamcrest.Matchers.notNullValue()
        )

        val requiredGroups = mutableSetOf(
            GpuCounterGroup.COMPUTE,
            GpuCounterGroup.FRAGMENTS,
            GpuCounterGroup.MEMORY,
            GpuCounterGroup.PRIMITIVES,
            GpuCounterGroup.VERTICES
        )
        if (trace.hasRayTracingSupport == true) {
            requiredGroups.add(GpuCounterGroup.RAY_TRACING)
        }
        val foundGroups = mutableSetOf<GpuCounterGroup>()
        for (spec in gpuCounterSpecsList) {
            foundGroups.addAll(spec.groupsList)
        }
        errorCollector.checkThat(
            "Required counter groups missing. Found: $foundGroups Required: $requiredGroups",
            foundGroups.containsAll(requiredGroups),
            `is`(true)
        )
    }

    fun validateRenderStages(trace: TraceParser.ParsedTrace) {
        val renderStages = trace.renderStagesData.renderStagesForApp

        checkRenderStagesNotEmpty(errorCollector, renderStages)
        checkRenderStagesValidity(errorCollector, renderStages)
    }

    fun validateQueueSubmits(trace: TraceParser.ParsedTrace) {
        val queueSubmits = trace.renderStagesData.queueSubmitsWithAppPid

        checkQueueSubmitsNotEmpty(errorCollector, queueSubmits)
        checkQueueSubmitsStrictlyMonotonicallyIncreasing(errorCollector, queueSubmits)

        checkQueueSubmitsMatchAppLogs(
            errorCollector,
            queueSubmits,
            trace.renderStagesData.appQueueSubmits
        )

        checkRenderStagesMatchQueueSubmits(errorCollector, trace.renderStagesData)
    }

    /**
     * Creates an error message summarizing the counters present in the trace vs those that were initially
     * requested.
     */
    private fun getSummaryDescriptionOfIdsInTrace(
        gpuCounters: GpuCounters,
        enabledIds: Set<Int>
    ): String {
        if (gpuCounters.counterDescriptorError.isNotEmpty()) {
            return gpuCounters.counterDescriptorError
        }
        if (gpuCounters.counterSpecs.isEmpty()) {
            return "no counter descriptor events found"
        }
        for ((id, spec) in gpuCounters.counterSpecs) {
            if (!enabledIds.contains(id)) return "unknown counter ID: $id"
            if (!spec.hasName() || spec.name.isEmpty()) {
                return "missing or empty name for counter id $id"
            }
        }
        return if (gpuCounters.counterSpecs.size == enabledIds.size) {
            "SUCCESS"
        } else {
            "not all enabled counters were found in the descriptor, diff, expected: " +
                    enabledIds.sorted().toString() +
                    ", found: " +
                    gpuCounters.counterSpecs.keys.sorted().toString()
        }
    }

    private fun getSummaryComplianceWithZeroesOptimization(gpuCounters: GpuCounters): String {
        val zeroesOptimizationSummary = java.lang.StringBuilder()

        for ((key, value) in gpuCounters.counterValues) {
            if (value.containsThreeConsecutiveZeroes()) {
                val spec = gpuCounters.counterSpecs[key]
                val name = spec?.name ?: "Unknown"
                zeroesOptimizationSummary
                    .append("counter ")
                    .append(name)
                    .append(" with ID ")
                    .append(key)
                    .append(" ; ")
            }
        }
        return zeroesOptimizationSummary.toString()
    }
}
