/*
 * Copyright (C) 2018 The Android Open Source Project
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
 * limitations under the License
 */

package android.telecom.cts.screeningtestapp;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;
import android.telecom.CallScreeningService;
import android.util.Log;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class CallScreeningServiceControl extends Service {
    private static final int ASYNC_TIMEOUT = 10000;
    private static final String TAG = CallScreeningServiceControl.class.getSimpleName();
    public static final String CONTROL_INTERFACE_ACTION =
            "android.telecom.cts.screeningtestapp.ACTION_CONTROL_CALL_SCREENING_SERVICE";
    public static final ComponentName CONTROL_INTERFACE_COMPONENT =
            new ComponentName(
                    "android.telecom.cts.screeningtestapp",
                    "android.telecom.cts.screeningtestapp.CallScreeningServiceControl");

    private static CallScreeningServiceControl sCallScreeningServiceControl = null;
    private static CountDownLatch sBindingLatch = new CountDownLatch(1);
    // For onScreenOutgoingCall
    private static volatile CountDownLatch sOutgoingScreeningLatch;
    private static volatile boolean sShouldBlockOutgoing = false;
    private static volatile boolean sShouldNoResponseOutgoing = false;
    private static AtomicReference<Uri> sLastOutgoingHandle = new AtomicReference<>();

    /** mIsBound represents the binding status from the test class to the test app */
    public static boolean mIsBound = false;

    private final IBinder mControlInterface =
            new android.telecom.cts.screeningtestapp.ICallScreeningControl.Stub() {
                @Override
                public void reset() {
                    Log.i(TAG, "reset: mCallResponse");
                    mCallResponse.set(
                            new CallScreeningService.CallResponse.Builder()
                                    .setDisallowCall(false)
                                    .setRejectCall(false)
                                    .setSkipCallLog(false)
                                    .setSkipNotification(false)
                                    .build());
                    sBindingLatch = new CountDownLatch(1);
                    sOutgoingScreeningLatch = new CountDownLatch(1);
                    sShouldBlockOutgoing = false;
                    sShouldNoResponseOutgoing = false;
                    sLastOutgoingHandle.set(null);
                    CtsPostCallActivity.resetPostCallActivity();
                }

                @Override
                public void setCallResponse(
                        boolean shouldDisallowCall,
                        boolean shouldRejectCall,
                        boolean shouldSilenceCall,
                        boolean shouldSkipCallLog,
                        boolean shouldSkipNotification) {

                    mCallResponse.set(
                            new CallScreeningService.CallResponse.Builder()
                                    .setSkipNotification(shouldSkipNotification)
                                    .setSkipCallLog(shouldSkipCallLog)
                                    .setDisallowCall(shouldDisallowCall)
                                    .setRejectCall(shouldRejectCall)
                                    .setSilenceCall(shouldSilenceCall)
                                    .build());

                    Log.i(
                            TAG,
                            String.format(
                                    "setCallResponse: shouldDisallowCall=[%b],"
                                        + " shouldRejectCall=[%b], shouldSilenceCall=[%b],"
                                        + " shouldSkipCallLog=[%b], shouldSkipNotification=[%b] ,"
                                        + " mCallResponse.hash=[%d] (AR)",
                                    shouldDisallowCall,
                                    shouldRejectCall,
                                    shouldSilenceCall,
                                    shouldSkipCallLog,
                                    shouldSkipNotification,
                                    mCallResponse.hashCode()));
                }

                @Override
                public boolean waitForBind() {
                    try {
                        return sBindingLatch.await(ASYNC_TIMEOUT, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException e) {
                        return false;
                    }
                }

                @Override
                public boolean waitForActivity() {
                    return CtsPostCallActivity.waitForActivity();
                }

                @Override
                public String getCachedHandle() {
                    return CtsPostCallActivity.getCachedHandle().getSchemeSpecificPart();
                }

                @Override
                public int getCachedDisconnectCause() {
                    return CtsPostCallActivity.getCachedDisconnectCause();
                }

                @Override
                public boolean isBound() {
                    return mIsBound;
                }

                @Override
                public void setShouldBlockOutgoingCall(boolean block) {
                    sShouldBlockOutgoing = block;
                    Log.i(TAG, "setShouldBlockOutgoingCall: " + block);
                }

                @Override
                public String waitForOutgoingCallScreened(long timeoutMs) {
                    try {
                        if (sOutgoingScreeningLatch == null
                                || !sOutgoingScreeningLatch.await(
                                        timeoutMs, TimeUnit.MILLISECONDS)) {
                            return null;
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        Log.w(TAG, "waitForScreeningAndGetHandle was interrupted");
                        return null;
                    }
                    Uri handle = sLastOutgoingHandle.get();
                    return handle != null ? handle.toString() : null;
                }

                @Override
                public String getLastOutgoingCallHandle() {
                    Uri handle = sLastOutgoingHandle.get();
                    return handle != null ? handle.toString() : null;
                }

                @Override
                public void setShouldNoResponseOutgoingCall(boolean noResponse) {
                    sShouldNoResponseOutgoing = noResponse;
                    Log.i(TAG, "setShouldNoResponseOutgoingCall: " + noResponse);
                }
            };

    /**
     * Returns whether the {@link CtsCallScreeningService} should block the next outgoing call.
     *
     * <p>This value is controlled by the test via the {@link
     * ICallScreeningControl#setShouldBlockOutgoingCall(boolean)} AIDL method.
     *
     * @return {@code true} if the next outgoing call should be blocked.
     */
    public boolean getShouldBlockOutgoingCall() {
        return sShouldBlockOutgoing;
    }

    public boolean getShouldNoResponseOutgoingCall() {
        return sShouldNoResponseOutgoing;
    }

    /**
     * Notifies this control service that an outgoing call has been screened.
     *
     * <p>This is called by the {@link CtsCallScreeningService} to report which call handle it has
     * processed. It also signals a latch to notify the waiting test.
     *
     * @param handle The {@link Uri} handle of the call that was screened.
     */
    public void onOutgoingCallScreened(Uri handle) {
        sLastOutgoingHandle.set(handle);
        if (sOutgoingScreeningLatch != null) {
            sOutgoingScreeningLatch.countDown();
        }
    }

    private AtomicReference<CallScreeningService.CallResponse> mCallResponse =
            new AtomicReference<>(
                    new CallScreeningService.CallResponse.Builder()
                            .setDisallowCall(false)
                            .setRejectCall(false)
                            .setSilenceCall(false)
                            .setSkipCallLog(false)
                            .setSkipNotification(false)
                            .build());

    public static CallScreeningServiceControl getInstance() {
        return sCallScreeningServiceControl;
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (CONTROL_INTERFACE_ACTION.equals(intent.getAction())) {
            Log.i(TAG, "onBind: returning control interface");
            sCallScreeningServiceControl = this;
            mIsBound = true;
            return mControlInterface;
        }
        Log.i(TAG, "onBind: uh oh");
        return null;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(TAG, "onUnbind: call screening control interface: intent= " + intent);
        sCallScreeningServiceControl = null;
        mIsBound = false;
        super.onUnbind(intent);
        return false;
    }

    public void onScreeningServiceBound() {
        sBindingLatch.countDown();
    }

    public CallScreeningService.CallResponse getCallResponse() {
        CallScreeningService.CallResponse currentResponse = mCallResponse.get();
        Log.i(
                TAG,
                String.format(
                        "getCallResponse: shouldDisallowCall=[%b], "
                                + "shouldRejectCall=[%b], "
                                + "shouldSilenceCall=[%b], "
                                + "shouldSkipCallLog=[%b], "
                                + "shouldSkipNotification=[%b] , mCallResponse.hash=[%d] (AR)",
                        currentResponse.getDisallowCall(),
                        currentResponse.getRejectCall(),
                        currentResponse.getSilenceCall(),
                        currentResponse.getSkipCallLog(),
                        currentResponse.getSkipNotification(),
                        currentResponse.hashCode()));
        return currentResponse;
    }
}
