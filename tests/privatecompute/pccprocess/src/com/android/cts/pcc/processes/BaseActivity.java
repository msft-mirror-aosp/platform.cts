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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;

public class BaseActivity extends Activity {
    public static final String EXTRA_BINDER = "binder";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        if (intent != null) {
            Bundle extras = intent.getExtras();
            if (extras != null) {
                IBinder binder = extras.getBinder(EXTRA_BINDER);
                if (binder != null) {
                    ITestBinder testBinder = ITestBinder.Stub.asInterface(binder);
                    try {
                        testBinder.sendUid(Process.myUid());
                    } catch (RemoteException e) {
                        // Ignore
                    }
                }
            }
        }
        finish();
    }

    public static class PccActivity extends BaseActivity {}

    public static class NonPccActivity extends BaseActivity {}

    public static class PccSecondProcessActivity extends BaseActivity {}
}
