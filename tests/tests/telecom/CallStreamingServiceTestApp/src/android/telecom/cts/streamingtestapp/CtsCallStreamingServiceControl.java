/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.telecom.cts.streamingtestapp;
import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.telecom.StreamingCall;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class CtsCallStreamingServiceControl extends Service {
    public static final String CONTROL_INTERFACE_ACTION =
            "android.telecom.cts.streamingtestapp.ACTION_CONTROL";

    private static final String TAG = "CtsCallStreamingServiceControl";
    private static final long TIMEOUT_MILLIS = 5000L;

    // Method to reset latches and state from a central place
    private void resetState() {
        CtsCallStreamingService.sCallStreamingStartedLatch = new CountDownLatch(1);
        CtsCallStreamingService.sCallStreamingStoppedLatch = new CountDownLatch(1);
        CtsCallStreamingService.sCallStreamingStateChangedLatch = new CountDownLatch(1);
        CtsCallStreamingService.sStreamingCall = null;
        CtsCallStreamingService.sCallBundle = null;
        CtsCallStreamingService.sLastStreamingState = -1;
    }

    private final IBinder mCtsCallStreamingServiceControl =
            new ICtsCallStreamingServiceControl.Stub() {
                @Override
                public Bundle waitForCallAdded() {
                    Log.i(TAG, "waitForCallAdded: waiting for latch");
                    try {
                        if (!CtsCallStreamingService.sCallStreamingStartedLatch.await(
                                TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                            Log.w(TAG, "waitForCallAdded: latch timed out");
                            Bundle bundle = new Bundle();
                            bundle.putString(CtsCallStreamingService.EXTRA_FAILED, "timeout");
                            return bundle;
                        }
                    } catch (InterruptedException e) {
                        Log.e(TAG, "waitForCallAdded: interrupted", e);
                        Bundle bundle = new Bundle();
                        bundle.putString(CtsCallStreamingService.EXTRA_FAILED, "interrupted");
                        return bundle;
                    }
                    Log.i(TAG, "waitForCallAdded: latch finished");
                    return CtsCallStreamingService.sCallBundle;
                }

                @Override
                public void requestCallStreamingState(int state) {
                    Log.i(TAG, "requestCallStreamingState: state=" + state);
                    StreamingCall call = CtsCallStreamingService.sStreamingCall;
                    if (call != null) {
                        call.requestStreamingState(state);
                    } else {
                        Log.w(TAG, "requestCallStreamingState: sStreamingCall is null!");
                    }
                }

                @Override
                public void waitForCallStreamingStopped() {
                    Log.i(TAG, "waitForCallStreamingStopped: waiting for latch");
                    try {
                        if (!CtsCallStreamingService.sCallStreamingStoppedLatch.await(
                                TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                            Log.w(TAG, "waitForCallStreamingStopped: latch timed out");
                        }
                    } catch (InterruptedException e) {
                        Log.e(TAG, "waitForCallStreamingStopped: interrupted", e);
                    }
                    Log.i(TAG, "waitForCallStreamingStopped: latch finished");
                }

                @Override
                public int waitForCallStreamingStateChanged() {
                    Log.i(TAG, "waitForCallStreamingStateChanged: waiting for latch");
                    try {
                        if (!CtsCallStreamingService.sCallStreamingStateChangedLatch.await(
                                TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                            Log.w(TAG, "waitForCallStreamingStateChanged: latch timed out");
                            return -1; // Return an invalid state on timeout
                        }
                    } catch (InterruptedException e) {
                        Log.e(TAG, "waitForCallStreamingStateChanged: interrupted", e);
                        return -1; // Return an invalid state on error
                    }
                    int state = CtsCallStreamingService.sLastStreamingState;
                    Log.i(
                            TAG,
                            "waitForCallStreamingStateChanged: "
                                    + "latch finished stat=["
                                    + state
                                    + "]");
                    return state;
                }

                /** Resets the latches in the streaming service to prepare for a new event. */
                @Override
                public void resetCallbackLatches() {
                    Log.i(TAG, "resetCallbackLatches");
                    CtsCallStreamingService.sCallStreamingStoppedLatch = new CountDownLatch(1);
                    CtsCallStreamingService.sCallStreamingStateChangedLatch = new CountDownLatch(1);
                    CtsCallStreamingService.sLastStreamingState = -1;
                }
            };

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        if (CONTROL_INTERFACE_ACTION.equals(intent.getAction())) {
            Log.i(TAG, "onBind: return control interface.");
            // Reset all state for the new test session to ensure test isolation.
            resetState();
            return mCtsCallStreamingServiceControl;
        }
        Log.w(TAG, "onBind: invalid intent.");
        return null;
    }
}
