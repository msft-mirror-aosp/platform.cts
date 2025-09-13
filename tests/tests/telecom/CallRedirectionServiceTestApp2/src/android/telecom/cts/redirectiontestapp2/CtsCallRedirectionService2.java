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

import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.graphics.drawable.Icon;
import android.os.IBinder;
import android.telecom.Call;
import android.telecom.CallRedirectionService;
import android.telecom.PhoneAccountHandle;

import android.text.TextUtils;
import android.util.Log;

import java.util.List;

/**
 * Provides a CTS-test implementation of {@link CallRedirectionService}.
 * To test onPlaceCall() API with originalHandle
 * This emulates a third-party implementation of {@link CallRedirectionService}.
 */
public class CtsCallRedirectionService2 extends CallRedirectionService {
    private static final String TAG = CtsCallRedirectionService2.class.getSimpleName();

    @Override
    public void onPlaceCall(Uri handle, Uri originalHandle, PhoneAccountHandle initialPhoneAccount,
            boolean allowInteractiveResponse) {
        Log.i(TAG, "onPlaceCall with originalHandle");
        CtsCallRedirectionServiceController2 controller =
                CtsCallRedirectionServiceController2.getInstance();
        if (controller != null) {
            controller.setDestinationUri(handle);
            controller.setDestinationOriginalUri(originalHandle);
            controller.setOriginalPhoneAccount(initialPhoneAccount);
            int decision = controller.getCallRedirectionDecision();
            // if decision == RESPONSE_TIMEOUT, do nothing and wait for timeout
            if (decision == CtsCallRedirectionServiceController2.PLACE_CALL_UNMODIFIED) {
                placeCallUnmodified();
            } else if (decision == CtsCallRedirectionServiceController2.CANCEL_CALL) {
                cancelCall();
            } else if (decision == CtsCallRedirectionServiceController2.PLACE_REDIRECTED_CALL) {
                redirectCall(controller.getTargetHandle(), controller.getTargetPhoneAccount(),
                        controller.isConfirmFirst());
            }
            controller.onPlaceCallInvoked();
        } else {
            Log.w(TAG, "onPlaceCall: No control interface.");
        }
    }


    @Override
    public void onPlaceCall(Uri handle, PhoneAccountHandle initialPhoneAccount,
            boolean allowInteractiveResponse) {
        Log.i(TAG, "Dummy onPlaceCall with originalHandle");
    }


    @Override
    public void onRedirectionTimeout() {
        Log.i(TAG, "onRedirectionTimeout");
        CtsCallRedirectionServiceController2 controller =
                CtsCallRedirectionServiceController2.getInstance();
        if (controller != null) {
            controller.timeoutNotified();
        } else {
            Log.w(TAG, "onDirectionTimeout: No control interface.");
        }
    }
}

