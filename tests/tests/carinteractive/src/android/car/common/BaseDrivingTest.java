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

package android.car.common;

import android.car.Car;
import android.car.drivingstate.CarDrivingStateEvent;
import android.car.drivingstate.CarDrivingStateManager;
import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.UiDevice;

import com.android.bedstead.permissions.annotations.EnsureHasPermission;
import com.android.interactive.Step;

import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@EnsureHasPermission(Car.PERMISSION_CAR_DRIVING_STATE)
public abstract class BaseDrivingTest {

    private static int sDriveState;
    private static long sLastDriveTimestamp;
    private static final long DRIVE_DURATION = 5000;

    public Context mContext;
    public UiDevice mDevice;
    private CarDrivingStateManager mCarDrivingStateManager;

    private final CarDrivingStateManager.CarDrivingStateEventListener mDrivingStateEventListener =
            this::handleDrivingEvent;

    @Before
    public void setup() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        Car car = Car.createCar(mContext);
        mCarDrivingStateManager =
                (CarDrivingStateManager) car.getCarManager(Car.CAR_DRIVING_STATE_SERVICE);
        mCarDrivingStateManager.registerListener(mDrivingStateEventListener);
        handleDrivingEvent(mCarDrivingStateManager.getCurrentCarDrivingState());

        // Ensure starting in PARK
        Step.execute(SetToParkStep.class);
    }

    @After
    public void tearDown() throws Exception {
        mCarDrivingStateManager.unregisterListener();
    }

    private void handleDrivingEvent(CarDrivingStateEvent event) {
        int oldDrivingState = sDriveState;
        sDriveState = event == null ? -1 : event.eventValue;

        if (oldDrivingState != sDriveState && isMovingState()) {
            sLastDriveTimestamp = System.currentTimeMillis();
        }
    }

    /** Whether the vehicle is in a parked state */
    public static boolean isParkedState() {
        return sDriveState == CarDrivingStateEvent.DRIVING_STATE_PARKED;
    }

    /** Whether the vehicle is in a driving state */
    public static boolean isMovingState() {
        return sDriveState == CarDrivingStateEvent.DRIVING_STATE_MOVING;
    }

    /** Returns true if the car has been driving for at least the defined DRIVE_DURATION */
    public static boolean hasMovedForDuration() {
        return isMovingState() && System.currentTimeMillis() - sLastDriveTimestamp > DRIVE_DURATION;
    }
}
