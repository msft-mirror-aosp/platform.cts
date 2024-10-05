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

package com.android.cts.testverifierapp;

import android.content.Intent;
import android.content.pm.verify.pkg.VerificationSession;
import android.content.pm.verify.pkg.VerificationStatus;
import android.content.pm.verify.pkg.VerifierService;
import android.os.IBinder;
import android.util.Log;

public class TestVerifierService extends VerifierService {
    static final String TAG = "TestVerifierService";
    static final String ACTION_SERVICE_CONNECTED =
            "android.content.pm.cts.verify.SERVICE_CONNECTED";
    static final String ACTION_NAME_RECEIVED =
            "android.content.pm.cts.verify.NAME_RECEIVED";
    static final String ACTION_CANCELLED_RECEIVED =
            "android.content.pm.cts.verify.CANCELLED_RECEIVED";
    static final String ACTION_REQUEST_RECEIVED =
            "android.content.pm.cts.verify.REQUEST_RECEIVED";

    @Override
    public void onCreate() {
        Log.i(TAG, "service is started");
        super.onCreate();
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "service is bound");
        Intent broadcastIntent = new Intent(ACTION_SERVICE_CONNECTED);
        broadcastIntent.setFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        sendBroadcast(broadcastIntent);
        return super.onBind(intent);
    }

    @Override
    public void onPackageNameAvailable(String packageName) {
        Log.i(TAG, "onPackageNameAvailable: " + packageName);
        Intent broadcastIntent = new Intent(ACTION_NAME_RECEIVED);
        broadcastIntent.putExtra(Intent.EXTRA_PACKAGE_NAME, packageName);
        broadcastIntent.setFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        sendBroadcast(broadcastIntent);
    }

    @Override
    public void onVerificationCancelled(String packageName) {
        Log.i(TAG, "onVerificationCancelled: " + packageName);
        Intent broadcastIntent = new Intent(ACTION_CANCELLED_RECEIVED);
        broadcastIntent.putExtra(Intent.EXTRA_PACKAGE_NAME, packageName);
        broadcastIntent.setFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        sendBroadcast(broadcastIntent);
    }

    @Override
    public void onVerificationRequired(VerificationSession session) {
        Log.i(TAG, "onVerificationRequired: " + session.getId());
        // Immediately return success
        VerificationStatus status = new VerificationStatus.Builder()
                .setVerified(true)
                .build();
        session.reportVerificationComplete(status);
        Intent broadcastIntent = new Intent(ACTION_REQUEST_RECEIVED);
        broadcastIntent.setFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        sendBroadcast(broadcastIntent);
        Log.i(TAG, "Returned verification success.");
    }

    @Override
    public void onVerificationRetry(VerificationSession session) {
        Log.i(TAG, "onVerificationRetry: " + session.getPackageName());

    }

    @Override
    public void onVerificationTimeout(int verificationId) {
        Log.i(TAG, "onVerificationTimeout: " + verificationId);
    }
}
