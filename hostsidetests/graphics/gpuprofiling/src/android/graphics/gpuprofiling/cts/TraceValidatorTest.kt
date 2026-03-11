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

import com.google.protobuf.TextFormat
import java.io.InputStreamReader
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.containsString
import org.junit.Test
import perfetto.protos.PerfettoConfig.GpuCounterDescriptor.GpuCounterGroup
import perfetto.protos.PerfettoConfig.GpuCounterDescriptor.GpuCounterSpec
import perfetto.protos.PerfettoTrace.Trace

class TraceValidatorTest {
    @Test
    fun testValidateGpuFrequency_success() {
        val (errorCollector, validator) = setupValidator()
        val parsedTrace = loadTrace("sample_gpuFrequency_renderStages.textproto")

        validator.validateGpuFrequency(parsedTrace)

        errorCollector.assertSuccess()
    }

    @Test
    fun testValidateGpuFrequency_missing_reportsError() {
        val parsedTrace = TraceParser.ParsedTrace(
            hasRayTracingSupport = false,
            gpuUsageTimeline = emptyList(),
            gpuCounters = GpuCounters(Trace.newBuilder().build()),
            renderStagesData = RenderStagesData(Trace.newBuilder().build()),
            containsGpuFrequencyEvent = false // ERROR trigger
        )
        val (errorCollector, validator) = setupValidator()

        validator.validateGpuFrequency(parsedTrace)

        assertThat(errorCollector.getErrors().size, org.hamcrest.Matchers.`is`(1))
        assertThat(
            errorCollector.getErrors()[0].message,
            allOf(
                containsString("Trace does not contain valid GPU frequency.")
            )
        )
    }

    @Test
    fun testValidateRenderStages_success() {
        val (errorCollector, validator) = setupValidator()
        val parsedTrace = loadTrace("sample_gpuFrequency_renderStages.textproto")

        validator.validateRenderStages(parsedTrace)

        errorCollector.assertSuccess()
    }

    @Test
    fun testValidateQueueSubmits_success() {
        val (errorCollector, validator) = setupValidator()
        val parsedTrace = loadTrace("sample_gpuFrequency_renderStages.textproto")

        validator.validateQueueSubmits(parsedTrace)

        errorCollector.assertSuccess()
    }

    @Test
    fun testValidateRequiredGroupsPresent_hasRayTracingSupportNull_reportsError() {
        // Create a trace with hasRayTracingSupport = null
        val parsedTrace = TraceParser.ParsedTrace(
            hasRayTracingSupport = null,
            gpuUsageTimeline = emptyList(),
            gpuCounters = GpuCounters(Trace.newBuilder().build()),
            renderStagesData = RenderStagesData(Trace.newBuilder().build()),
            containsGpuFrequencyEvent = true
        )
        val (errorCollector, validator) = setupValidator()

        val validSpecs = listOf(
            GpuCounterSpec.newBuilder()
                .addGroups(GpuCounterGroup.COMPUTE)
                .addGroups(GpuCounterGroup.FRAGMENTS)
                .addGroups(GpuCounterGroup.MEMORY)
                .addGroups(GpuCounterGroup.PRIMITIVES)
                .addGroups(GpuCounterGroup.VERTICES)
                .build()
        )

        validator.validateRequiredGroupsPresent(parsedTrace, validSpecs)

        assertThat(errorCollector.getErrors().size, org.hamcrest.Matchers.`is`(1))
        assertThat(
            errorCollector.getErrors()[0].message,
            allOf(
                containsString("Ray tracing support information is missing from the trace.")
            )
        )
    }

    private fun setupValidator(): Pair<FakeErrorCollector, TraceValidator> {
        val errorCollector = FakeErrorCollector()
        val validator = TraceValidator(errorCollector)
        return Pair(errorCollector, validator)
    }

    private fun loadTrace(filename: String): TraceParser.ParsedTrace {
        val stream = javaClass.getResourceAsStream("/test_res/traces/$filename")
            ?: throw IllegalArgumentException("Could not find /test_res/traces/$filename")
        val builder = Trace.newBuilder()
        TextFormat.getParser().merge(InputStreamReader(stream), builder)
        return TraceParser.parse(builder.build())
    }
}
