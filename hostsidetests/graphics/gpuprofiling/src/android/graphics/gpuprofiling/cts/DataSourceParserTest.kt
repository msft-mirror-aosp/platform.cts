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
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import perfetto.protos.PerfettoConfig.TracingServiceState

@RunWith(JUnit4::class)
class DataSourceParserTest {

    @Test
    fun testParseGpuCountersSuccess() {
        val state = loadTracingServiceState("tracing_service_state_counters.textproto")

        val result = DataSourceParser.parse(state)

        assertThat(result.gpuCountersSourceFound, `is`(true))
        assertThat(result.renderStagesSourceFound, `is`(true))
        assertThat(result.gpuCounterSpecsList.size, `is`(3))
        assertThat(result.allGpuCounterIds, `is`(setOf(1, 2, 3)))
        assertThat(result.defaultGpuCounterIds, `is`(setOf(1, 2)))
    }

    @Test
    fun testParseRenderStagesSuccess() {
        val state = loadTracingServiceState("tracing_service_state_stages.textproto")

        val result = DataSourceParser.parse(state)

        assertThat(result.renderStagesSourceFound, `is`(true))
        assertThat(result.gpuCountersSourceFound, `is`(false))
        assertThat(result.gpuCounterSpecsList.size, `is`(0))
        assertThat(result.allGpuCounterIds, `is`(emptySet()))
        assertThat(result.defaultGpuCounterIds, `is`(emptySet()))
    }

    @Test
    fun testParseEmptyState() {
        val emptyState = loadTracingServiceState("tracing_service_state_empty.textproto")

        val result = DataSourceParser.parse(emptyState)

        assertThat(result.renderStagesSourceFound, `is`(false))
        assertThat(result.gpuCountersSourceFound, `is`(false))
        assertThat(result.gpuCounterSpecsList.size, `is`(0))
        assertThat(result.allGpuCounterIds, `is`(emptySet()))
        assertThat(result.defaultGpuCounterIds, `is`(emptySet()))
    }

    private fun loadTracingServiceState(filename: String): TracingServiceState {
        val stream = javaClass.getResourceAsStream("/test_res/data_source/$filename")
            ?: throw IllegalArgumentException("Could not find /test_res/data_source/$filename")
        val builder = TracingServiceState.newBuilder()
        TextFormat.getParser().merge(InputStreamReader(stream), builder)
        return builder.build()
    }
}
