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

package android.telecom.cts.cuj;

import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.telecom.Call;
import android.telecom.InCallService;
import android.util.Log;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class CujInCallService extends InCallService {

    public static class CujCallCallback extends Call.Callback {
        private String mExpectedEvent = null;
        private CountDownLatch mExpectedEventLatch = new CountDownLatch(1);

        @Override
        public void onConnectionEvent(Call call, String event, Bundle extras) {
            if (Objects.equals(event, mExpectedEvent)) {
                mExpectedEventLatch.countDown();
            }
        }

        public void setExpectedEvent(String expectedEvent) {
            mExpectedEvent = expectedEvent;
        }

        public boolean waitOnExpectedEvent() {
            try {
                return mExpectedEventLatch.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                return false;
            }
        }
    }

    private static final String TAG = CujInCallService.class.getSimpleName();
    public static boolean sIsServiceBound = false;
    public static final Map<String, Call> sCallIdToCall = new ConcurrentHashMap();
    public static final Map<String, CujCallCallback> sCallIdToCallback = new ConcurrentHashMap();
    public static Call sLastCall = null;

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "onBind");
        sIsServiceBound = true;
        return super.onBind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(TAG, "onUnbind");
        sIsServiceBound = false;
        sLastCall = null;
        sCallIdToCall.clear();
        return super.onUnbind(intent);
    }

    @Override
    public void onCallAdded(Call call) {
        Log.i(TAG, String.format("onCallAdded: call=[%s]", call));
        sCallIdToCall.put(call.getDetails().getId(), call);
        CujCallCallback cujCallCallback = new CujCallCallback();
        call.registerCallback(cujCallCallback);
        sCallIdToCallback.put(call.getDetails().getId(), cujCallCallback);
        if (call.getDetails().getState() == Call.STATE_SELECT_PHONE_ACCOUNT) {
            Log.w(TAG, "Call moved into STATE_SELECT_PHONE_ACCOUNT unexpectedly, disconnecting: "
                    + call);
            // If this unexpected state happens, the test and calls could get stuck. Manually
            // disconnect here until we support moving into SELECT_PHONE_ACCOUNT
            call.disconnect();
        }
        sLastCall = call;
    }

    @Override
    public void onCallRemoved(Call call) {
        Log.i(TAG, String.format("onCallRemoved: call=[%s]", call));
        sCallIdToCall.remove(call.getDetails().getId());
    }

    public static boolean isServiceBound() {
        return sIsServiceBound;
    }

    public static int getCurrentCallCount() {
        return sCallIdToCall.size();
    }

    public static List<Call> getOngoingCalls() {
        return sCallIdToCall.values().stream().toList();
    }

    public static Call getLastAddedCall() {
        return sLastCall;
    }

    public static CujCallCallback getCallbackForCall(String callId) {
        return sCallIdToCallback.get(callId);
    }
}

