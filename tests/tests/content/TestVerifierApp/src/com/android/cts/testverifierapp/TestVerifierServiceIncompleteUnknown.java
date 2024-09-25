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

import static android.content.pm.verify.pkg.VerificationSession.VERIFICATION_INCOMPLETE_UNKNOWN;

import android.content.Intent;
import android.content.pm.verify.pkg.VerificationSession;
import android.util.Log;

public class TestVerifierServiceIncompleteUnknown extends TestVerifierService {
    @Override
    public void onVerificationRequired(VerificationSession session) {
        Log.i(TAG, "onVerificationRequired: " + session.getId());
        // Report incomplete with unknown reason
        session.reportVerificationIncomplete(VERIFICATION_INCOMPLETE_UNKNOWN);
        Intent broadcastIntent = new Intent(ACTION_REQUEST_RECEIVED);
        broadcastIntent.setFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        sendBroadcast(broadcastIntent);
        Log.i(TAG, "Returned verification incomplete.");
    }
}
