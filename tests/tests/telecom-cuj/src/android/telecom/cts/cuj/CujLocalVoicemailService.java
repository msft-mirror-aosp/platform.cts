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
package android.telecom.cts.cuj;

import android.content.Intent;
import android.os.IBinder;
import android.telecom.Call;
import android.telecom.LocalVoicemailService;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;

public class CujLocalVoicemailService extends LocalVoicemailService {
    public static final String TAG = CujLocalVoicemailService.class.getSimpleName();
    public static CujLocalVoicemailService sService;

    /** Queue of call information that was received by the local voicemail service. */
    public static final LinkedBlockingQueue<Call.Details> sRequestedCalls =
            new LinkedBlockingQueue<>();

    /** Queue of call information that was stopped local vm. */
    public static final LinkedBlockingQueue<Call.Details> sStoppedCalls =
            new LinkedBlockingQueue<>();

    public static CountDownLatch sUnbindLatch = new CountDownLatch(1);

    public static LinkedBlockingQueue<Call.Details> getRequestedCalls() {
        return sRequestedCalls;
    }

    public static LinkedBlockingQueue<Call.Details> getStoppedCalls() {
        return sStoppedCalls;
    }

    public static CountDownLatch getUnbindLatch() {
        return sUnbindLatch;
    }

    public static void disconnectCurrentCall() {
        if (sService != null) {
            sService.disconnectCall();
        }
    }

    @Override
    public void onVoicemailRequested(@NonNull Call.Details call) {
        Log.i(TAG, "onVoicemailRequested: callId=" + call.getId());
        sRequestedCalls.offer(call);
    }

    @Override
    public void onVoicemailStopped(@NonNull Call.Details call) {
        Log.i(TAG, "onVoicemailStopped: callId=" + call.getId());
        sStoppedCalls.offer(call);
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "onBind");
        sService = this;
        sStoppedCalls.clear();
        sUnbindLatch = new CountDownLatch(1);
        return super.onBind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(TAG, "onUnbind");
        sUnbindLatch.countDown();
        sService = null;
        sRequestedCalls.clear();
        return super.onUnbind(intent);
    }
}
