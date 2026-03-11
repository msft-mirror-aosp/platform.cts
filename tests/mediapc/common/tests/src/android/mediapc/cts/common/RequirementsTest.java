/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.mediapc.cts.common;

import static com.google.common.truth.Truth.assertThat;

import android.mediapc.cts.common.Requirements.SequentialWriteRequirement;
import android.mediapc.cts.common.Requirements.TapToToneLatencyRequirement;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for select {@link Requirement} subclasses generated in {@link Requirements}. */
@RunWith(JUnit4.class)
public class RequirementsTest {

    @Rule
    public final TestName mTestName = new TestName();

    /** Verifies autogeneration of requirements with more than one measurement */
    @Test
    public void twoMeasurements() {
        var pce = new PerformanceClassEvaluator(mTestName);
        TapToToneLatencyRequirement req = Requirements.addR5_6__H_1_1().to(pce);
        req.setJavaLatencyMs(80);
        req.setNativeLatencyMs(80);

        var pc = req.computePerformanceClass();
        assertThat(pc).isEqualTo(35);
    }

    /** Verifies autogeneration of requirements with more than MPC level */
    @Test
    public void multiMpcLevels_0() {
        var pce = new PerformanceClassEvaluator(mTestName);
        SequentialWriteRequirement req = Requirements.addR8_2__H_1_1().to(pce);
        req.setFilesystemIoRateMbps(34);

        var pc = req.computePerformanceClass();
        assertThat(pc).isEqualTo(0);
    }

    /** Verifies autogeneration of requirements with more than MPC level */
    @Test
    public void multiMpcLevels_30() {
        var pce = new PerformanceClassEvaluator(mTestName);
        SequentialWriteRequirement req = Requirements.addR8_2__H_1_1().to(pce);
        req.setFilesystemIoRateMbps(100);

        var pc = req.computePerformanceClass();
        assertThat(pc).isEqualTo(30);
    }

    /** Verifies autogeneration of requirements with more than MPC level */
    @Test
    public void multiMpcLevels_33() {
        var pce = new PerformanceClassEvaluator(mTestName);
        SequentialWriteRequirement req = Requirements.addR8_2__H_1_1().to(pce);
        req.setFilesystemIoRateMbps(125);

        var pc = req.computePerformanceClass();
        assertThat(pc).isEqualTo(33);
    }

}
