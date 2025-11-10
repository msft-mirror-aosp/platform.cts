/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.cts.pcc.processes;

public class PccSecondProcessBroadcastReceiver extends PccBroadcastReceiver {

    public static final String ACTION_TEST_BROADCAST_SECOND_PROCESS =
            "com.android.cts.pcc.processes.ACTION_TEST_BROADCAST_SECOND_PROCESS";

    @Override
    protected String getAction() {
        return ACTION_TEST_BROADCAST_SECOND_PROCESS;
    }
}
