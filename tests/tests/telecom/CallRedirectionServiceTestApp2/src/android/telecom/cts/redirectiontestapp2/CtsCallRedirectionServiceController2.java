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

package android.telecom.cts.redirectiontestapp2;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.telecom.PhoneAccountHandle;
import android.os.IBinder;
import android.telecom.CallRedirectionService;
import android.telecom.cts.redirectiontestapp.ICtsCallRedirectionServiceController;
import android.util.Log;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class CtsCallRedirectionServiceController2 extends Service {
    private static final String TAG = CallRedirectionService.class.getSimpleName();
    public static final String CONTROL_INTERFACE_ACTION =
            "android.telecom.cts.redirectiontestapp2.ACTION_CONTROL_CALL_REDIRECTION_SERVICE";
    public static final ComponentName CONTROL_INTERFACE_COMPONENT =
            ComponentName.unflattenFromString(
                    "android.telecom.cts.redirectiontestapp2/.CtsCallRedirectionServiceController2");

    // Constants for call redirection decisions
    public static final int NO_DECISION_YET = 0;
    public static final int RESPONSE_TIMEOUT = 1;
    public static final int PLACE_CALL_UNMODIFIED = 2;
    public static final int PLACE_REDIRECTED_CALL = 3;
    public static final int CANCEL_CALL = 4;
    public static final int PLACE_CALL_TO_ALTERNATE_NUMBER = 5;
    public static final long TIMEOUT = 6000;

    private int mDecision = NO_DECISION_YET;

    // Redirection information, only valid if decision is PLACE_REDIRECTED_CALL.
    private Uri mTargetHandle = null;
    private Uri mDestinationUri = null;
    private Uri mDestinationOriginalUri = null;
    private PhoneAccountHandle mRedirectedPhoneAccount = null;
    private PhoneAccountHandle mOriginalPhoneAccount = null;
    private boolean mConfirmFirst = false;
    private CountDownLatch mTimeoutNotified = new CountDownLatch(1);
    private CountDownLatch mOnPlaceCallInvoked = new CountDownLatch(1);

    private static CtsCallRedirectionServiceController2 sCallRedirectionServiceController = null;

    private final IBinder mControllerInterface = new ICtsCallRedirectionServiceController.Stub() {
                @Override
                public void reset() {
                    mDecision = NO_DECISION_YET;
                    mTimeoutNotified = new CountDownLatch(1);
                    mOnPlaceCallInvoked = new CountDownLatch(1);
                }

                @Override
                public void setRedirectCall(Uri targetHandle,
                                            PhoneAccountHandle redirectedPhoneAccount,
                                            boolean confirmFirst) {
                    Log.i(TAG, "redirectCall");
                    mDecision = PLACE_REDIRECTED_CALL;
                    mTargetHandle = targetHandle;
                    mRedirectedPhoneAccount = redirectedPhoneAccount;
                    mConfirmFirst = confirmFirst;
                }

                @Override
                public void setPlaceCallToAlternateNumber(Uri targetHandle,
                        PhoneAccountHandle redirectedPhoneAccount,
                        boolean confirmFirst) {
                    Log.i(TAG, "placeCallToAlternateNumber");
                    mDecision = PLACE_CALL_TO_ALTERNATE_NUMBER;
                    mTargetHandle = targetHandle;
                    mRedirectedPhoneAccount = redirectedPhoneAccount;
                    mConfirmFirst = confirmFirst;
                }

                @Override
                public void setCancelCall() {
                    Log.i(TAG, "cancelCall");
                    mDecision = CANCEL_CALL;
                }

                @Override
                public void setPlaceCallUnmodified() {
                    Log.i(TAG, "placeCallUnmodified");
                    mDecision = PLACE_CALL_UNMODIFIED;
                }

                @Override
                public void setWaitForTimeout() {
                    Log.i(TAG, "setWaitForTimeout");
                    mDecision = RESPONSE_TIMEOUT;
                }

                @Override
                public boolean waitForTimeoutNotified() {
                    Log.i(TAG, "waitForTimeoutNotified");
                    try {
                        return mTimeoutNotified.await(TIMEOUT, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException e) {
                        return false;
                    }
                }

                @Override
                public Uri getReceivedOriginalHandle() {
                    return mDestinationOriginalUri;
                }

                @Override
                public boolean waitForOnPlaceCallInvoked() {
                    try {
                        return mOnPlaceCallInvoked.await(TIMEOUT, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException e) {
                        return false;
                    }
                }
            };

    public static CtsCallRedirectionServiceController2 getInstance() {
        return sCallRedirectionServiceController;
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (CONTROL_INTERFACE_ACTION.equals(intent.getAction())) {
            Log.i(TAG, "onBind: returning control interface");
            sCallRedirectionServiceController = this;
            return mControllerInterface;
        }
        Log.i(TAG, "onBind: ACTION is not matched");
        return null;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        sCallRedirectionServiceController = null;
        return false;
    }

    public int getCallRedirectionDecision() {
        return mDecision;
    }

    public Uri getTargetHandle() {
        return mTargetHandle;
    }

    public PhoneAccountHandle getTargetPhoneAccount() {
        return mRedirectedPhoneAccount != null ? mRedirectedPhoneAccount : mOriginalPhoneAccount;
    }

    public void setOriginalPhoneAccount(PhoneAccountHandle originalPhoneAccount) {
        mOriginalPhoneAccount = originalPhoneAccount;
    }

    public void setDestinationUri(Uri destinationUri) {
        mDestinationUri = destinationUri;
    }

    public void setDestinationOriginalUri(Uri destinationOriginalUri) {
        mDestinationOriginalUri = destinationOriginalUri;
    }

    public boolean isConfirmFirst() {
        return mConfirmFirst;
    }

    public void timeoutNotified() {
        mTimeoutNotified.countDown();
    }

    public void onPlaceCallInvoked() {
        mOnPlaceCallInvoked.countDown();
    }
}
