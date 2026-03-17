/*
 * Copyright (C) 2026 The Android Open Source Project
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

package android.telephony.cts;

import android.annotation.NonNull;
import android.os.SystemClock;
import android.telephony.ServiceState;
import android.telephony.TelephonyCallback;
import android.util.Log;

import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;

public class ServiceStateRadioStateListener extends TelephonyCallback
        implements TelephonyCallback.ServiceStateListener,
        TelephonyCallback.RadioPowerStateListener {
    private static final String TAG = "ServiceStateRadioStateListener";
    private static final long TIMEOUT_TO_WAIT_FOR_DESIRED_STATE =
            TimeUnit.SECONDS.toMillis(20);
    private final Object mPowerStateLock = new Object();
    private final Object mServiceStateLock = new Object();
    ServiceState mServiceState;
    int mDesireServiceState;

    int mRadioPowerState;
    int mDesireRadioPowerState;

    public ServiceStateRadioStateListener(ServiceState serviceState, int radioPowerState) {
        mServiceState = serviceState;
        mRadioPowerState = radioPowerState;
        mDesireRadioPowerState = radioPowerState;
    }

    @Override
    public void onServiceStateChanged(ServiceState ss) {
        Log.d(TAG, "onServiceStateChanged to " + ss);
        synchronized (mServiceStateLock) {
            mServiceState = ss;
            if (ss.getState() == mDesireServiceState) {
                mServiceStateLock.notify();
            }
        }
    }

    @Override
    public void onRadioPowerStateChanged(int radioState) {
        Log.d(TAG, "onRadioPowerStateChanged to " + radioState);
        synchronized (mPowerStateLock) {
            mRadioPowerState = radioState;
            if (radioState == mDesireRadioPowerState) {
                mPowerStateLock.notify();
            }
        }
    }

    public void waitForRadioStateIntent(int desiredRadioState) {
        Log.d(TAG, "waitForRadioStateIntent: desiredRadioState=" + desiredRadioState);
        synchronized (mPowerStateLock) {
            mDesireRadioPowerState = desiredRadioState;
            /**
             * Since SST sets waiting time up to 10 seconds for the power off radio, the
             * RadioStateIntent timer extends the wait time up to 20 seconds here as well.
             */
            waitForDesiredState(mPowerStateLock, desiredRadioState,
                    () -> mRadioPowerState, true);
        }
    }

    public void waitForServiceStateIntent(int desiredServiceState, boolean failOnTimeOut) {
        Log.d(TAG, "waitForServiceStateIntent: desiredServiceState=" + desiredServiceState);
        synchronized (mServiceStateLock) {
            mDesireServiceState = desiredServiceState;
            waitForDesiredState(mServiceStateLock, desiredServiceState,
                    () -> mServiceState.getState(), failOnTimeOut);
        }
    }

    private void waitForDesiredState(@NonNull Object lock, int desiredState,
            @NonNull IntSupplier currentStateSupplier, boolean failOnTimeOut) {
        synchronized (lock) {
            long now = SystemClock.elapsedRealtime();
            long deadline = now + TIMEOUT_TO_WAIT_FOR_DESIRED_STATE;
            while (currentStateSupplier.getAsInt() != desiredState && now < deadline) {
                try {
                    lock.wait(TIMEOUT_TO_WAIT_FOR_DESIRED_STATE);
                } catch (Exception e) {
                    if (failOnTimeOut) {
                        throw new AssertionError("failOnTimeOut", e);
                    } else {
                        Log.w(TAG, "waitForDesiredState", e);
                    }
                }
                now = SystemClock.elapsedRealtime();
            }
        }
    }
}
