package com.android.cts.verifier;

import android.util.Log;

import com.android.compatibility.common.util.TestResultHistory;

import java.io.Serializable;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TestResultHistoryCollection implements Serializable {

    private static final long serialVersionUID = 0L;
    private static final String TAG = "TestResultHistoryCollection";
    private final Set<TestResultHistory> mHistoryCollection = new HashSet<>();

    /**
     * Covert object to set.
     *
     * @return A set of test result history.
     */
    public Set<TestResultHistory> asSet() {
        return mHistoryCollection;
    }

    /**
     * Add a test result history with test name, start time, end time and isAutomated.
     *
     * @param test a string of test name.
     * @param start start time of a test.
     * @param end end time of a test.
     * @param isAutomated whether test case was executed through automation.
     */
    public void add(String test, long start, long end, boolean isAutomated) {
        add(test, start, end, isAutomated, null);
    }

    /**
     * Add a test result history with test name, start time, end time, isAutomated and test details.
     *
     * @param test a string of test name.
     * @param start start time of a test.
     * @param end end time of a test.
     * @param isAutomated whether test case was executed through automation.
     * @param testDetails a list of test details.
     */
    public void add(String test, long start, long end, boolean isAutomated,
            List<TestResultHistory.TestDetails> testDetails) {
        if (testDetails != null) {
            Log.d(TAG, "Adding history for " + test + " with " + testDetails.size() + " details");
        } else {
            Log.d(TAG, "Adding history for " + test + " with null details");
        }
        Set<TestResultHistory.ExecutionRecord> executionRecords
                = new HashSet<TestResultHistory.ExecutionRecord> ();
        executionRecords.add(new TestResultHistory.ExecutionRecord(start, end, isAutomated));
        mHistoryCollection.add(new TestResultHistory(test, executionRecords));
    }

    /**
     * Add a empty test result history with test name.
     *
     * @param test a string of test name.
     */
    public void addEmptyTestResultHistory(String test) {
        mHistoryCollection.add(new TestResultHistory(test, new HashSet<>()));
    }

    /**
     * Add test result histories for tests containing test name and a set of ExecutionRecords
     *
     * @param test test name.
     * @param executionRecords set of ExecutionRecords.
     */
    public void addAll(String test, Set<TestResultHistory.ExecutionRecord> executionRecords) {
        addAll(test, executionRecords, null);
    }

    public void addAll(String test, Set<TestResultHistory.ExecutionRecord> executionRecords,
            List<TestResultHistory.TestDetails> testDetails) {
        Log.d(TAG, "addAll for " + test + ", details=" + (testDetails == null ? "null" : testDetails.size()));
        TestResultHistory matchedHistory = null;
        for (TestResultHistory resultHistory : mHistoryCollection) {
            if (resultHistory.getTestName().equals(test)) {
                matchedHistory = resultHistory;
                break;
            }
        }

        // Merge the execution records and test details if there is an existing test result history.
        // Otherwise, create a new test result history.
        if (matchedHistory != null) {
            Log.d(TAG, "Found existing history for " + test);
            mHistoryCollection.remove(matchedHistory);
            Set<TestResultHistory.ExecutionRecord> mergedRecords = matchedHistory.getExecutionRecords();
            mergedRecords.addAll(executionRecords);

            List<TestResultHistory.TestDetails> mergedDetails =
                    (matchedHistory.getTestDetails() != null && !matchedHistory.getTestDetails().isEmpty())
                    ? matchedHistory.getTestDetails() : testDetails;

            mHistoryCollection.add(new TestResultHistory(test, mergedRecords, mergedDetails));
        } else {
            Log.d(TAG, "Creating new history for " + test);
            mHistoryCollection.add(new TestResultHistory(test, executionRecords, testDetails));
        }
    }

    /**
     * Merge test with its sub-tests result histories.
     *
     * @param prefix optional test name prefix to apply.
     * @param resultHistoryCollection a set of test result histories.
     */
    public void merge(String prefix, TestResultHistoryCollection resultHistoryCollection) {
        if (resultHistoryCollection != null) {
            Log.d(TAG, "Merging collection with size: " + resultHistoryCollection.asSet().size());
            resultHistoryCollection.asSet().forEach(t-> addAll(
                prefix != null
                        ? prefix + ":" + t.getTestName()
                        : t.getTestName(), t.getExecutionRecords(), t.getTestDetails()));
        }
    }

    /**
     * Merge test with its sub-tests result histories.
     *
     * @param prefix optional test name prefix to apply.
     * @param resultHistories a list of test result history collection.
     */
    public void merge(String prefix, List<TestResultHistoryCollection> resultHistories) {
        resultHistories.forEach(resultHistoryCollection -> merge(prefix, resultHistoryCollection));
    }
}
