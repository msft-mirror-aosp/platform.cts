/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.server.wm.jetpack.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.window.sidecar.SidecarDeviceState;
import androidx.window.sidecar.SidecarInterface.SidecarCallback;
import androidx.window.sidecar.SidecarWindowLayoutInfo;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class SidecarCallbackCounter implements SidecarCallback {
    private static final int TIMEOUT_MILLISECONDS = 100;
    private final Object mLock = new Object();
    private final IBinder mWindowToken;
    private int mCallbackCount;
    private final CountDownLatch mCountDownLatch;

    public SidecarCallbackCounter(IBinder windowToken, int callbackCount) {
        mWindowToken = windowToken;
        mCountDownLatch = new CountDownLatch(callbackCount);
    }

    @Override
    public void onDeviceStateChanged(@NonNull SidecarDeviceState sidecarDeviceState) {
    }

    @Override
    public void onWindowLayoutChanged(@NonNull IBinder iBinder,
            @NonNull SidecarWindowLayoutInfo sidecarWindowLayoutInfo) {
        synchronized (mLock) {
            assertEquals(iBinder, mWindowToken);
            assertNotNull(sidecarWindowLayoutInfo);
            mCallbackCount++;
            mCountDownLatch.countDown();
        }
    }

    public void resetCallbackCount() {
        mCallbackCount = 0;
    }

    /**
     * Waits for the expected number of values to be emitted.
     *
     * @return {@code true} if expected number of values was reached within the timeout, {@code
     *     false} otherwise.
     */
    public boolean waitForCountdown() {
        try {
            return mCountDownLatch.await(TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            return false;
        }
    }

    public long getCallbackCount() {
        return mCallbackCount;
    }
}
