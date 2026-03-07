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
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.hasItem
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import perfetto.protos.PerfettoConfig.TracingServiceState

@RunWith(JUnit4::class)
class DataSourceValidatorTest {

    @Test
    fun testValidateRenderStagesFound_false_reportsError() {
        val errorCollector = FakeErrorCollector()
        val validator = DataSourceValidator(errorCollector)
        val parsedData = DataSourceParser.DataSources(
            false,
            true,
            emptyList(),
            emptySet(),
            emptySet()
        )

        validator.validateRenderStagesFound(parsedData)

        errorCollector.assertFailure()
        assertThat(
            errorCollector.getErrorMessages(),
            hasItem(allOf(containsString("Producer"), containsString("not found")))
        )
    }

    @Test
    fun testValidateGpuCountersFound_false_reportsError() {
        val errorCollector = FakeErrorCollector()
        val validator = DataSourceValidator(errorCollector)
        val parsedData = DataSourceParser.DataSources(
            true,
            false,
            emptyList(),
            emptySet(),
            emptySet()
        )

        validator.validateGpuCountersFound(parsedData)

        errorCollector.assertFailure()
        assertThat(
            errorCollector.getErrorMessages(),
            hasItem(allOf(containsString("Producer"), containsString("not found")))
        )
    }

    @Test
    fun testValidateGpuCounters_success() {
        val setup = setupValidator("tracing_service_state_counters.textproto")

        setup.validator.validateGpuCounters(setup.parsed)

        setup.errorCollector.assertSuccess()
    }

    @Test
    fun testValidateGpuCounters_missingNameAndDescription_reportsError() {
        val setup = setupValidator("tracing_service_state_counters_malformed.textproto")

        setup.validator.validateGpuCounters(setup.parsed)

        setup.errorCollector.assertFailure()
        val errors = setup.errorCollector.getErrorMessages()
        assertThat(errors, hasItem(containsString("must have a non-empty name")))
        assertThat(errors, hasItem(containsString("must have a non-empty description")))
    }

    @Test
    fun testValidateGpuCounters_nonUniqueIds_reportsError() {
        val setup = setupValidator("tracing_service_state_counters_non_unique.textproto")

        setup.validator.validateGpuCounters(setup.parsed)

        setup.errorCollector.assertFailure()
        val errors = setup.errorCollector.getErrorMessages()
        assertThat(
            errors,
            hasItem(containsString("Counter IDs in DataSourceDescriptor must be unique"))
        )
    }

    @Test
    fun testValidateGpuCounters_noDefault_reportsError() {
        val setup = setupValidator("tracing_service_state_counters_no_default.textproto")

        setup.validator.validateGpuCounters(setup.parsed)

        setup.errorCollector.assertFailure()
        val errors = setup.errorCollector.getErrorMessages()
        assertThat(errors, hasItem(containsString("No default counters set")))
    }

    private fun loadTracingServiceState(filename: String): TracingServiceState {
        val stream = javaClass.getResourceAsStream("/test_res/data_source/$filename")
            ?: throw IllegalArgumentException("Could not find /test_res/data_source/$filename")
        val builder = TracingServiceState.newBuilder()
        TextFormat.getParser().merge(InputStreamReader(stream), builder)
        return builder.build()
    }

    private data class ValidatorSetup(
        val errorCollector: FakeErrorCollector,
        val validator: DataSourceValidator,
        val parsed: DataSourceParser.DataSources
    )

    private fun setupValidator(filename: String): ValidatorSetup {
        val errorCollector = FakeErrorCollector()
        val validator = DataSourceValidator(errorCollector)
        val state = loadTracingServiceState(filename)
        val parsed = DataSourceParser.parse(state)
        return ValidatorSetup(errorCollector, validator, parsed)
    }
}
