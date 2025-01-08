/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static com.android.cts.verifier.PassFailButtons.showInfoDialog;

import android.content.Intent;
import android.util.Log;
import android.view.View;
import android.widget.ListView;

/** Activity for general host-side interactive tests in CtsVerifier. */
public class InteractiveTestsActivity extends HostTestsActivity {
    private static final String TAG = "InteractiveTestsActivity";

    public InteractiveTestsActivity() {
        super(
                R.string.interactive_tests_dialog_title,
                R.string.interactive_tests_dialog_content,
                // Add module categories
                new HostTestCategory("CompanionDeviceManager Tests")
                        .addTest(
                            "CtsCompanionDeviceManagerMultiDeviceTestCases",
                            "CtsCompanionDeviceManagerMultiDeviceTestCases"),
                new HostTestCategory("NFC Tests")
                        .addTest("CtsNfcHceMultiDeviceTestCases", "CtsNfcHceMultiDeviceTestCases"));
    }

    @Override
    protected void handleItemClick(ListView l, View v, int position, long id) {
        TestListAdapter.TestListItem item = mTestListAdapter.getItem(position);
        if (mTestListAdapter.getTestResult(position) == TestResult.TEST_RESULT_NOT_EXECUTED) {
            showInfoDialog(
                    this,
                    R.string.interactive_tests_dialog_title,
                    R.string.interactive_tests_dialog_content,
                    /* viewId= */ 0);
            return;
        }
        Intent intent = new Intent(this, InteractiveTestListActivity.class);
        intent.putExtra(InteractiveTestListActivity.MODULE_TITLE, item.title);
        intent.putExtra(InteractiveTestListActivity.MODULE_NAME, item.testName);
        Log.i(TAG, "Launching activity with " + IntentDrivenTestActivity.toString(this, intent));
        startActivity(intent);
    }
}
