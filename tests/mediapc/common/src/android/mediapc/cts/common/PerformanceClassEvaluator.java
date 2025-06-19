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
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeTrue;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.DeviceReportLog;
import com.android.cts.verifier.CtsVerifierReportLog;

import com.google.common.base.Preconditions;

import org.junit.rules.TestName;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Logs a set of measurements and results for defined performance class requirements.
 */
public class PerformanceClassEvaluator {
    private static final String TAG = PerformanceClassEvaluator.class.getSimpleName();

    private final boolean mIsPerfClass;
    private final int mDeclaredPc;
    private final String mTestName;
    private final Set<Requirement> mRequirements;

    public PerformanceClassEvaluator(TestName testName) {
        this(testName, Utils.isPerfClass(), Utils.getPerfClass());
    }

    @VisibleForTesting
    protected PerformanceClassEvaluator(TestName testName, boolean isPerfClass, int declaredPc) {
        Preconditions.checkNotNull(testName);
        mIsPerfClass = isPerfClass;
        mDeclaredPc = declaredPc;
        String baseTestName = testName.getMethodName() != null ? testName.getMethodName() : "";
        this.mTestName = baseTestName.replace("{", "(").replace("}", ")");
        this.mRequirements = new HashSet<>();
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
                mRequirements.stream().allMatch(Requirement::allMeasuredValuesSet);
        boolean hasRequirements = !mRequirements.isEmpty();
        return allMeasuredValuesSet && hasRequirements;
    }

    private enum SubmitType {
        TRADEFED, VERIFIER
    }

    /**
     * Submits the evaluation and checks them against the device's declared performance class, and
     * asserts that the requirements are met.
     *
     * <p>The set of requirements are cleared after submission.
     */
    public void submitAndCheck() {
        // submit clears the requirements so compute before submitting
        Map<Requirement, Integer> idToGrade = computeGrades();
        boolean perfClassMet = submit(SubmitType.TRADEFED);
        // check performance class
        assumeTrue("Build.VERSION.MEDIA_PERFORMANCE_CLASS is not declared", mIsPerfClass);

        if (!perfClassMet) {
            idToGrade.forEach(
                    (r, grade) -> {
                        if (r.appliesToPerformanceClass(mDeclaredPc)) {
                            assertWithMessage("%s performance class", r)
                                    .that(grade)
                                    .isAtLeast(mDeclaredPc);
                        }
                    });
        }
        // Safety catch.
        assertThat(perfClassMet).isTrue();
    }

    /**
     * Submits the evaluation results and logs warnings if requirements are not met for the declared
     * performance class.
     *
     * <p>The set of requirements are cleared after submission.
     */
    public void submitAndVerify() {
        // submit clears the requirements so compute before submitting
        Map<Requirement, Integer> grades = computeGrades();
        boolean perfClassMet = submit(SubmitType.VERIFIER);

        if (!perfClassMet && mIsPerfClass) {
            String msg = "Declared performance class %s but requirement [%s] grades as %s";
            grades.forEach((r, grade) -> Log.w(TAG, msg.formatted(mDeclaredPc, r, grade)));
        }
    }

    @NonNull
    @VisibleForTesting // Prevents warning about using computePerformanceClass
    private Map<Requirement, Integer> computeGrades() {
        return mRequirements.stream()
                .collect(Collectors.toMap(r -> r, Requirement::computePerformanceClass));
    }

    private boolean submit(SubmitType type) {
        if (mRequirements.isEmpty()) {
            Log.w(
                    TAG,
                    ("No requirements added to PerformanceClassEvaluator for test %s. Submission "
                                    + "skipped.")
                            .formatted(mTestName));
            return true;
        }
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
