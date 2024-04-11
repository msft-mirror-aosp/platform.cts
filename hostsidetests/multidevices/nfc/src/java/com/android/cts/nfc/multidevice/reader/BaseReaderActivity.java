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
package com.android.cts.nfc.multidevice.reader;

import android.app.Activity;
import android.content.Intent;
import android.nfc.NfcAdapter;
import android.os.Bundle;

public class BaseReaderActivity extends Activity {

    // Intent action that's sent after the test condition is met.
    protected static final String ACTION_TEST_PASSED =
            "com.android.cts.nfc.multidevice.reader.ACTION_TEST_PASSED";

    /** Call this in child classes when test condition is met */
    protected void setTestPassed() {
        Intent intent = new Intent(ACTION_TEST_PASSED);
        sendBroadcast(intent);
    }

    protected NfcAdapter mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAdapter = NfcAdapter.getDefaultAdapter(this);
    }
}
