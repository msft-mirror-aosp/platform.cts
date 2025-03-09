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

import static org.junit.Assume.assumeTrue;

import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.DeviceReportLog;
import com.android.cts.verifier.CtsVerifierReportLog;

import com.google.common.base.Preconditions;

import org.junit.rules.TestName;

import java.util.HashSet;
import java.util.Set;

/**
 * Logs a set of measurements and results for defined performance class requirements.
 *
 * <p> Nested classes are organized alphabetically, add[Requirement] functions are organized by
 * their requirement number in the order they appear in the Android CDD
 */
public class PerformanceClassEvaluator {
    private static final String TAG = PerformanceClassEvaluator.class.getSimpleName();

    private final String mTestName;
    private Set<Requirement> mRequirements;

    public PerformanceClassEvaluator(TestName testName) {
        Preconditions.checkNotNull(testName);
        String baseTestName = testName.getMethodName() != null ? testName.getMethodName() : "";
        this.mTestName = baseTestName.replace("{", "(").replace("}", ")");
        this.mRequirements = new HashSet<Requirement>();
    }

    String getTestName() {
        return mTestName;
    }

    public <R extends Requirement> R addRequirement(R req) {
        if (!this.mRequirements.add(req)) {
            throw new IllegalStateException("Requirement " + req.id() + " already added");
        }
        return req;
    }

    /**
     * Returns if the PerformanceClassEvaluator is ready to be submitted.
     *
     * <p>The PerformanceClassEvaluator is ready for submission if: all added requirements have all
     * their required measurements recorded AND there is at least one requirement added.
     *
     * <p>Note: this function is ONLY meant to be used by ITS. Other tests should attempt to submit
     * and make sure an exception is not thrown during submission.
     */
    public boolean isReadyToSubmitItsResults() {
        boolean allMeasuredValuesSet =
                mRequirements.stream().allMatch(r -> r.allMeasuredValuesSet());
        boolean hasRequirements = !mRequirements.isEmpty();
        return allMeasuredValuesSet && hasRequirements;
    }

    private enum SubmitType {
        TRADEFED, VERIFIER
    }

    public void submitAndCheck() {
        boolean perfClassMet = submit(SubmitType.TRADEFED);

        // check performance class
        assumeTrue("Build.VERSION.MEDIA_PERFORMANCE_CLASS is not declared", Utils.isPerfClass());
        assertThat(perfClassMet).isTrue();
    }

    public void submitAndVerify() {
        boolean perfClassMet = submit(SubmitType.VERIFIER);

        if (!perfClassMet && Utils.isPerfClass()) {
            Log.w(TAG, "Device did not meet specified performance class: " + Utils.getPerfClass());
        }
    }

    private boolean submit(SubmitType type) {
        boolean perfClassMet = true;
        for (Requirement req : this.mRequirements) {
            switch (type) {
                case VERIFIER:
                    CtsVerifierReportLog verifierLog = new CtsVerifierReportLog(
                            RequirementConstants.REPORT_LOG_NAME, req.id());
                    perfClassMet &= req.writeLogAndCheck(verifierLog, this.mTestName);
                    verifierLog.submit();
                    break;

                case TRADEFED:
                default:
                    DeviceReportLog tradefedLog = new DeviceReportLog(
                            RequirementConstants.REPORT_LOG_NAME, req.id());
                    perfClassMet &= req.writeLogAndCheck(tradefedLog, this.mTestName);
                    tradefedLog.submit(InstrumentationRegistry.getInstrumentation());
                    break;
            }
        }
        this.mRequirements.clear(); // makes sure report isn't submitted twice
        return perfClassMet;
    }
}
