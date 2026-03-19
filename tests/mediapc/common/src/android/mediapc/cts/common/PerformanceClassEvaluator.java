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
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.DeviceReportLog;
import com.android.cts.verifier.CtsVerifierReportLog;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;

import org.junit.rules.TestName;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Logs a set of measurements and results for defined performance class requirements. */
public class PerformanceClassEvaluator {
    private static final String TAG = PerformanceClassEvaluator.class.getSimpleName();

    private final boolean mIsPerfClass;
    private final int mDeclaredPc;
    private final String mTestName;
    private final Set<Requirement> mRequirements;

    /**
     * Creates a PerformanceClassEvaluator with the given test name.
     *
     * <p>Use {@link PerformanceClassTestRule} instead of creating this directly where possible.
     */
    public PerformanceClassEvaluator(TestName testName) {
        this(testName, Utils.isPerfClass(), Utils.getPerfClass());
    }

    /**
     * Creates a PerformanceClassEvaluator with the given test name.
     *
     * @param testName the test name.
     */
    public PerformanceClassEvaluator(@Nullable String testName) {
        this(testName, Utils.isPerfClass(), Utils.getPerfClass());
    }

    private PerformanceClassEvaluator(TestName testName, boolean isPerfClass, int declaredPc) {
        this(Preconditions.checkNotNull(testName).getMethodName(), isPerfClass, declaredPc);
    }

    @VisibleForTesting
    protected PerformanceClassEvaluator(
            @Nullable String testName, boolean isPerfClass, int declaredPc) {
        mIsPerfClass = isPerfClass;
        mDeclaredPc = declaredPc;
        String baseTestName = testName != null ? testName : "";
        this.mTestName = baseTestName.replace("{", "(").replace("}", ")");
        this.mRequirements = new HashSet<>();
    }

    String getTestName() {
        return mTestName;
    }

    /**
     * @return the set of requirements added to the evaluator.
     */
    public ImmutableSet<Requirement> getRequirements() {
        return ImmutableSet.copyOf(mRequirements);
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
     *
     * <p>Test should use {@link PerformanceClassTestRule} instead of calling this method directly.
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
        submitAndVerify(mRequirements);
    }

    /**
     * Submits the evaluation results for a list of requirements and logs warnings if requirements
     * are not met for the declared performance class.
     *
     * <p>Each requirement is removed from the internal requirements list as it is submitted.
     *
     * @param requirements the requirements to submit.
     */
    public void submitAndVerify(Iterable<? extends Requirement> requirements) {
        // compute before submitting
        Map<Requirement, Integer> grades = computeGrades(requirements);
        boolean perfClassMet = submit(SubmitType.VERIFIER, requirements);

        if (!perfClassMet && mIsPerfClass) {
            String msg = "Declared performance class %s but requirement [%s] grades as %s";
            grades.forEach((r, grade) -> Log.w(TAG, String.format(msg, mDeclaredPc, r, grade)));
        }
    }

    @NonNull
    private Map<Requirement, Integer> computeGrades() {
        return computeGrades(mRequirements);
    }

    @NonNull
    @VisibleForTesting // Prevents warning about using computePerformanceClass
    Map<Requirement, Integer> computeGrades(Iterable<? extends Requirement> requirements) {
        Map<Requirement, Integer> grades = new java.util.HashMap<>();
        for (Requirement r : requirements) {
            grades.put(r, r.computePerformanceClass());
        }
        return grades;
    }

    private boolean submit(SubmitType type) {
        return submit(type, mRequirements);
    }

    /**
     * submits the requirements
     *
     * <p>Each requirement is removed from the internal requirements list as it is submitted.
     */
    private boolean submit(SubmitType type, Iterable<? extends Requirement> requirements) {
        boolean hasRequirements = false;
        boolean perfClassMet = true;
        for (Requirement req : ImmutableSet.copyOf(requirements)) {
            hasRequirements = true;
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
            req.setSubmitted(true);
            mRequirements.remove(req);
        }

        if (!hasRequirements) {
            Log.w(
                    TAG,
                    String.format(
                            "No requirements provided to PerformanceClassEvaluator for test %s."
                                    + " Submission skipped.",
                            mTestName));
            return true;
        }
        return perfClassMet;
    }
}
