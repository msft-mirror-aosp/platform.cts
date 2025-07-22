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

package android.display.cts;

import static junit.framework.Assert.fail;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.app.Instrumentation;
import android.content.Context;
import android.hardware.devicestate.DeviceState;
import android.hardware.devicestate.DeviceStateManager;
import android.hardware.devicestate.DeviceStateRequest;
import android.server.wm.DeviceStateUtils;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;

/** A {@link TestRule} that will restore the current {@link DeviceState} after each test run. */
public class DeviceStateManagerTestRule implements TestRule {
    private static final int TIMEOUT_MS = 5000;
    private final Instrumentation mInstrumentation = InstrumentationRegistry.getInstrumentation();
    private final Context mTargetContext = mInstrumentation.getTargetContext();
    private final Executor mMainExecutor = mTargetContext.getMainExecutor();
    private final DeviceStateManager mDeviceStateManager =
            Objects.requireNonNull(mTargetContext.getSystemService(DeviceStateManager.class));

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {

                if (mDeviceStateManager == null
                        || mDeviceStateManager.getSupportedDeviceStates().size() < 2) {
                    base.evaluate();
                    return;
                }

                StateCallback callback = new StateCallback(mDeviceStateManager);
                try {
                    DeviceStateUtils.runWithControlDeviceStatePermission(
                            () -> {
                                mDeviceStateManager.registerCallback(mMainExecutor, callback);
                                if (callback.await(TIMEOUT_MS)) {
                                    try {
                                        base.evaluate();
                                    } catch (Throwable e) {
                                        throw new RuntimeException(e);
                                    }
                                } else {
                                    fail("Callback not called within the timeout.");
                                }
                            });
                } finally {
                    mDeviceStateManager.unregisterCallback(callback);
                    DeviceState deviceState = callback.getState();
                    if (deviceState != null) {
                        requestState(deviceState);
                    }
                }
            }
        };
    }

    /**
     * Runs the {@link Runnable} on all the supported device states. This skips the emulated states
     * to avoid flakiness.
     *
     * @param r The {@link Runnable} to run on all the supported device states.
     * @see DeviceStateManager#getSupportedDeviceStates()
     */
    public void cycleThroughHardwareStates(Runnable r) throws Throwable {
        List<DeviceState> states = mDeviceStateManager.getSupportedDeviceStates();
        for (DeviceState state : states) {
            if (state.hasProperties(DeviceState.PROPERTY_EMULATED_ONLY)) {
                continue;
            }
            if (requestState(state)) {
                r.run();
            } else {
                throw new RuntimeException("State request failed.");
            }
        }
    }

    private boolean requestState(DeviceState state) throws Throwable {
        RequestCallback requestCallback = new RequestCallback();
        DeviceStateUtils.runWithControlDeviceStatePermission(
                () -> {
                    DeviceStateRequest deviceStateRequest =
                            DeviceStateRequest.newBuilder(state.getIdentifier()).build();
                    mDeviceStateManager.requestState(
                            deviceStateRequest, mTargetContext.getMainExecutor(), requestCallback);
                });
        return requestCallback.await(TIMEOUT_MS);
    }

    private static final class RequestCallback implements DeviceStateRequest.Callback {

        private final CountDownLatch mLatch = new CountDownLatch(1);

        @Override
        public void onRequestActivated(@NonNull DeviceStateRequest request) {
            mLatch.countDown();
        }

        private boolean await(int timeoutMillis) {
            try {
                return mLatch.await(timeoutMillis, MILLISECONDS);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static final class StateCallback implements DeviceStateManager.DeviceStateCallback {

        private final DeviceStateManager mDeviceStateManager;
        private final CountDownLatch mLatch = new CountDownLatch(1);
        private DeviceState mState;

        StateCallback(DeviceStateManager deviceStateManager) {
            mDeviceStateManager = deviceStateManager;
        }

        @Override
        public void onDeviceStateChanged(@NonNull DeviceState state) {
            mState = state;
            mLatch.countDown();
            mDeviceStateManager.unregisterCallback(this);
        }

        private DeviceState getState() {
            if (await(TIMEOUT_MS)) {
                return mState;
            } else {
                throw new IllegalStateException("State not set within timeout.");
            }
        }

        private boolean await(int timeoutMillis) {
            try {
                return mLatch.await(timeoutMillis, MILLISECONDS);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
