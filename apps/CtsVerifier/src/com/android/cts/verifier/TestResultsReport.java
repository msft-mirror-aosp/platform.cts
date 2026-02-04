/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.cts.verifier;

import android.content.Context;
import android.os.Build;
import android.text.TextUtils;

import com.android.compatibility.common.util.DevicePropertyInfo;
import com.android.compatibility.common.util.ICaseResult;
import com.android.compatibility.common.util.IInvocationResult;
import com.android.compatibility.common.util.IModuleResult;
import com.android.compatibility.common.util.ITestResult;
import com.android.compatibility.common.util.InvocationResult;
import com.android.compatibility.common.util.ReportLog;
import com.android.compatibility.common.util.TestResultHistory;
import com.android.compatibility.common.util.TestScreenshotsMetadata;
import com.android.compatibility.common.util.TestStatus;
import com.android.cts.verifier.TestListActivity.DisplayMode;
import com.android.cts.verifier.TestListAdapter.TestListItem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Helper class for creating an {@code InvocationResult} for CTS result generation.
 */
class TestResultsReport {

    private static final String PREFIX_TAG = "build_";

    private final Context mContext;

    private final TestListAdapter mAdapter;

    TestResultsReport(Context context, TestListAdapter adapter) {
        this.mContext = context;
        this.mAdapter = adapter;
    }

    IInvocationResult generateResult() {
        String abis = null;
        String abis32 = null;
        String abis64 = null;
        String versionBaseOs = null;
        String versionSecurityPatch = null;
        String versionRelease = null;
        IInvocationResult result = new InvocationResult();

        // Collect build fields available in API level 21
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            abis = TextUtils.join(",", Build.SUPPORTED_ABIS);
            abis32 = TextUtils.join(",", Build.SUPPORTED_32_BIT_ABIS);
            abis64 = TextUtils.join(",", Build.SUPPORTED_64_BIT_ABIS);
        }

        // Collect build fields available in API level 23
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            versionBaseOs = Build.VERSION.BASE_OS;
            versionSecurityPatch = Build.VERSION.SECURITY_PATCH;
        }

        versionRelease = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                ? Build.VERSION.RELEASE_OR_CODENAME : Build.VERSION.RELEASE;

        // at the time of writing, the build class has no REFERENCE_FINGERPRINT property
        String referenceFingerprint = null;

        String sdkVersion = Integer.toString(Build.VERSION.SDK_INT);
        String sdkVerionFull =
                String.format(
                        "%s.%s", sdkVersion, Build.getMinorSdkVersion(Build.VERSION.SDK_INT_FULL));

        DevicePropertyInfo devicePropertyInfo =
                DevicePropertyInfo.newBuilder()
                        .abi(Build.CPU_ABI)
                        .abi2(Build.CPU_ABI2)
                        .abis(abis)
                        .abis32(abis32)
                        .abis64(abis64)
                        .board(Build.BOARD)
                        .brand(Build.BRAND)
                        .device(Build.DEVICE)
                        .fingerprint(Build.FINGERPRINT)
                        .id(Build.ID)
                        .manufacturer(Build.MANUFACTURER)
                        .model(Build.MODEL)
                        .product(Build.PRODUCT)
                        .referenceFingerprint(referenceFingerprint)
                        .serial(Build.getSerial())
                        .tags(Build.TAGS)
                        .type(Build.TYPE)
                        .versionBaseOs(versionBaseOs)
                        .versionRelease(versionRelease)
                        .versionSdk(sdkVersion)
                        .versionSecurityPatch(versionSecurityPatch)
                        .versionIncremental(Build.VERSION.INCREMENTAL)
                        .versionSdkFull(sdkVerionFull)
                        .build();

        // add device properties to the result with a prefix tag for each key
        for (Entry<String, String> entry :
                devicePropertyInfo.getPropertytMapWithPrefix(PREFIX_TAG).entrySet()) {
            String entryValue = entry.getValue();
            if (entryValue != null) {
                result.addInvocationInfo(entry.getKey(), entry.getValue());
            }
        }

        // Get test result, including test name, result, report log, details and histories.
        getCaseResult(result);
        getHostCaseResult(result);

        return result;
    }

    /**
     * Creates results for all non-host test cases.
     *
     * <p>Tests are structured via "Category" (Module) -> "Test Item" (Case) -> "Sub Test" (Test).
     * Only tests that have been executed (have history) are included.
     *
     * @param result The invocation result to populate.
     */
    private void getCaseResult(IInvocationResult result) {
        String hostTestTitle = mContext.getResources().getString(R.string.host_tests_title);
        List<TestListItem> allItems = new ArrayList<>();

        // Filter and collect all relevant test items across all DisplayModes
        for (DisplayMode mode : DisplayMode.values()) {
            String displayMode = mode.toString();
            int count = mAdapter.getCount(displayMode);
            for (int i = 0; i < count; i++) {
                TestListItem item = mAdapter.getItem(displayMode, i);
                // Only include items that are actual tests and not part of the Host Test category
                if (item.category != null
                        && item.isTest()
                        && !item.title.equals(hostTestTitle)) {
                    allItems.add(item);
                }
            }
        }

        // Identify "active" categories/modules. A module is initialized in the report if it
        // contains at least one item with run histories.
        Set<String> activeCategories = new HashSet<>();
        for (TestListItem item : allItems) {
            if (shouldShownInReport(item) && !activeCategories.contains(item.category)) {
                activeCategories.add(item.category);
                IModuleResult moduleResult = result.getOrCreateModule(getModuleId(item.category));
                // Initialize as 'done', will be updated based on individual test results
                moduleResult.setDone(true);
            }
        }

        // Populate test results and update module 'done' status.
        for (TestListItem item : allItems) {
            if (activeCategories.contains(item.category)) {
                IModuleResult moduleResult = result.getOrCreateModule(getModuleId(item.category));
                // A module is considered 'done' only if all its constituent tests have passed.
                boolean isPassed =
                        mAdapter.getTestResult(item.testName) == TestResult.TEST_RESULT_PASSED;
                moduleResult.setDone(moduleResult.isDone() && isPassed);
                if (shouldShownInReport(item)) {
                    createCaseResult(moduleResult, item);
                }
            }
        }
    }

    /**
     * Creates the detailed result structure for a single TestListItem.
     *
     * <p>This processes the execution history to generate results for sub-tests.
     *
     * @param moduleResult The parent module.
     * @param testItem The test item definition.
     */
    private void createCaseResult(IModuleResult moduleResult, TestListItem testItem) {
        String testName = testItem.testName;
        ICaseResult caseResult = moduleResult.getOrCreateResult(testName);
        TestResultHistoryCollection historyCollection = mAdapter.getHistoryCollection(testName);
        if (historyCollection == null) {
            return;
        }
        List<TestResultHistory> leafTestHistories = getTestResultHistories(historyCollection);
        for (TestResultHistory history : leafTestHistories) {
            createTestResult(caseResult, history, testItem);
        }
    }

    /**
     * Creates a single test result entry.
     *
     * @param caseResult The parent case result.
     * @param history The history record for the sub-test.
     * @param testItem The parent test item.
     */
    private void createTestResult(
            ICaseResult caseResult, TestResultHistory history, TestListItem testItem) {
        // Resolve the actual internal name of the sub-test
        String subTestName = getSubTestName(history.getTestName());
        if (subTestName == null) {
            return;
        }

        // Format the name for the report (removing parent prefixes if necessary)
        String subTestNameInReport = getSubTestNameInReport(history.getTestName(), testItem);
        ITestResult currentTestResult = caseResult.getOrCreateResult(subTestNameInReport);

        fillTestResult(currentTestResult, subTestName, subTestNameInReport, history);
    }

    /**
     * Get case results per host test, including result, report log, details and histories.
     *
     * @param result The result bound with {@link IInvocationResult}.
     */
    private void getHostCaseResult(IInvocationResult result) {
        for (String module : mContext.getResources().getStringArray(R.array.host_modules)) {
            for (String testName : mAdapter.getTestResultNames()) {
                if (testName.startsWith(module)) {
                    String[] parts = testName.split(HostTestsActivity.TEST_ID_SEPARATOR, 3);
                    if (parts.length < 3) {
                        continue;
                    }
                    IModuleResult moduleResult = result.getOrCreateModule(getModuleId(parts[0]));
                    moduleResult.setDone(true);
                    ICaseResult caseResult = moduleResult.getOrCreateResult(parts[1]);
                    createHostTestResult(caseResult, testName, parts[2]);
                }
            }
        }
    }

    private void createHostTestResult(
            ICaseResult caseResult, String fullTestName, String testName) {
        ITestResult currentTestResult = caseResult.getOrCreateResult(testName);

        TestResultHistory resultHistory = null;
        TestResultHistoryCollection historyCollection = mAdapter.getHistoryCollection(fullTestName);

        if (historyCollection != null && !historyCollection.asSet().isEmpty()) {
            // For host side tests, there should only be one history.
            resultHistory = historyCollection.asSet().iterator().next();
        }

        fillTestResult(currentTestResult, fullTestName, testName, resultHistory);
    }

    /**
     * Populates a {@link ITestResult} object with detailed result metadata.
     *
     * @param currentTestResult The object to populate.
     * @param testName The internal test ID.
     * @param testNameInReport The name to display in the report.
     * @param history The execution history associated with this result.
     */
    private void fillTestResult(
            ITestResult currentTestResult,
            String testName,
            String testNameInReport,
            TestResultHistory history) {
        TestStatus resultStatus = getTestResultStatus(mAdapter.getTestResult(testName));
        currentTestResult.setResultStatus(resultStatus);
        // TODO: report test details with Extended Device Info (EDI) or CTS metrics
        String details = mAdapter.getTestDetails(testName);
        currentTestResult.setMessage(details);

        ReportLog reportLog = mAdapter.getReportLog(testName);
        if (reportLog != null) {
            currentTestResult.setReportLog(reportLog);
        }

        TestScreenshotsMetadata screenshotsMetadata = mAdapter.getScreenshotsMetadata(testName);
        if (screenshotsMetadata != null) {
            currentTestResult.setTestScreenshotsMetadata(screenshotsMetadata);
        }
        if (history != null) {
            currentTestResult.setTestResultHistories(
                    List.of(
                            new TestResultHistory(
                                    testNameInReport, history.getExecutionRecords())));
        }
    }

    private TestStatus getTestResultStatus(int testResult) {
        switch (testResult) {
            case TestResult.TEST_RESULT_PASSED:
                return TestStatus.PASS;

            case TestResult.TEST_RESULT_FAILED:
                return TestStatus.FAIL;

            case TestResult.TEST_RESULT_NOT_EXECUTED:
                return TestStatus.INCOMPLETE;

            default:
                throw new IllegalArgumentException("Unknown test result: " + testResult);
        }
    }

    /**
     * Get test histories per test by filtering out non-leaf histories.
     *
     * @param historyCollection The raw test history collection.
     * @return A list containing test result histories per test.
     */
    @SuppressWarnings("ReturnValueIgnored")
    private List<TestResultHistory> getTestResultHistories(
            TestResultHistoryCollection historyCollection) {
        // Get non-terminal prefixes.
        Set<String> prefixes = new HashSet<>();
        for (TestResultHistory history : historyCollection.asSet()) {
            Arrays.stream(history.getTestName().split(":")).reduce(
                    (total, current) -> {
                        prefixes.add(total);
                        return total + ":" + current;
                    });
        }

        // Filter out non-leaf test histories.
        List<TestResultHistory> leafTestHistories = new ArrayList<>();
        for (TestResultHistory history : historyCollection.asSet()) {
            if (!prefixes.contains(history.getTestName())) {
                leafTestHistories.add(history);
            }
        }
        return leafTestHistories;
    }

    private static String getModuleId(String category) {
        return "noabi " + category.replaceAll(" ", "");
    }

    /** Resolves the real test name by handling nested naming conventions.*/
    private String getSubTestName(String testName) {
        String currentKey = testName;
        while (currentKey != null) {
            if (mAdapter.getTestResultNames().contains(currentKey)) {
                return currentKey;
            }

            int idx = currentKey.indexOf(':');
            if (idx == -1) break;
            currentKey = currentKey.substring(idx + 1);
        }
        return null;
    }

    /** Formats the sub-test name for display in the report. */
    private String getSubTestNameInReport(String subTestName, TestListItem testItem) {
        String testName = testItem.testName;
        if (subTestName.startsWith(testName)) {
            if (subTestName.equals(testName)) {
                return testItem.title.replaceAll(" ", "");
            }
            return subTestName.substring(testName.length() + 1);
        }
        return subTestName;
    }

    /** Determines if a test item should be included in the report.*/
    private boolean shouldShownInReport(TestListItem testItem) {
        return mAdapter.getHistoryCollection(testItem.testName) != null;
    }
}
