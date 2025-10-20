/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.cts.verifier.car;

import android.car.Car;
import android.car.VehicleGear;
import android.car.VehiclePropertyIds;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** A CTS Verifier test case to verify GEAR_SELECTION is implemented correctly.*/
public final class GearSelectionTestActivity extends PassFailButtons.Activity {
    private static final String TAG = GearSelectionTestActivity.class.getSimpleName();

    // Need to finish the test in 10 minutes.
    private static final long TEST_TIMEOUT_MINUTES = 10;

    private TextView mExpectedGearSelectionTextView;
    private TextView mCurrentGearSelectionTextView;
    private CarPropertyManager mCarPropertyManager;
    private ExecutorService mExecutor;
    private GearSelectionCallback mGearSelectionCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Setup the UI.
        setContentView(R.layout.gear_selection_test);
        setPassFailButtonClickListeners();
        setInfoResources(R.string.gear_selection_test, R.string.gear_selection_test_desc, -1);
        getPassButton().setEnabled(false);

        mExpectedGearSelectionTextView = (TextView) findViewById(R.id.expected_gear_selection);
        mCurrentGearSelectionTextView = (TextView) findViewById(R.id.current_gear_selection);
        mExecutor = Executors.newSingleThreadExecutor();
        runTest();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mExecutor != null) {
            // Immediately cancel all tasks
            mExecutor.shutdownNow();
        }
    }

    private void runTest() {
        mCarPropertyManager =
                (CarPropertyManager) Car.createCar(this).getCarManager(Car.PROPERTY_SERVICE);
        if (mCarPropertyManager == null) {
            Log.e(TAG, "Failed to get CarPropertyManager");
            mExpectedGearSelectionTextView.setText("CONNECTING ERROR");
            return;
        }

        //Verify property config
        CarPropertyConfig<?> gearConfig = mCarPropertyManager.getCarPropertyConfig(
                VehiclePropertyIds.GEAR_SELECTION);
        if (gearConfig == null || gearConfig.getConfigArray().size() == 0) {
            Log.e(TAG, "No gears specified in the config array of GEAR_SELECTION property");
            mExpectedGearSelectionTextView.setText("GEAR CONFIG ERROR");
            return;
        }

        if (!gearConfig.getConfigArray().contains(VehicleGear.GEAR_PARK)) {
            Log.e(TAG, "No GEAR_PARK specified in the config array of GEAR_SELECTION property");
            mExpectedGearSelectionTextView.setText("GEAR CONFIG MISSING PARK");
            return;
        }

        // To avoid clashing with UX Restrictions, vehicle will shift into park between every
        // gear change.
        ArrayList<Integer> gearOrder = new ArrayList<>();
        for (Integer supportedGear : gearConfig.getConfigArray()) {
            if (supportedGear.equals(VehicleGear.GEAR_PARK)) {
                continue;
            }
            gearOrder.add(supportedGear);
            gearOrder.add(VehicleGear.GEAR_PARK);
        }

        Log.i(TAG, "New Expected Gear: " + VehicleGear.toString(gearOrder.get(0)));
        mExpectedGearSelectionTextView.setText(VehicleGear.toString(gearOrder.get(0)));
        mGearSelectionCallback = new GearSelectionCallback(gearOrder);
        if (!mCarPropertyManager.registerCallback(mGearSelectionCallback,
                VehiclePropertyIds.GEAR_SELECTION, CarPropertyManager.SENSOR_RATE_ONCHANGE)) {
            Log.e(TAG,
                    "Failed to register callback for GEAR_SELECTION with CarPropertyManager");
            mExpectedGearSelectionTextView.setText("CONNECTING ERROR");
            return;
        }

        mExecutor.execute(
                () -> {
                    try {
                        boolean testPassed = mGearSelectionCallback.waitForTestToFinish();
                        if (testPassed) {
                            runOnUiThread(() -> mExpectedGearSelectionTextView.setText("Finished"));
                            runOnUiThread(() -> getPassButton().setEnabled(true));
                            Log.i(TAG, "Finished Test");
                        } else {
                            Log.e(TAG, "Failed to complete tests in 10 minutes");
                            runOnUiThread(
                                    () ->
                                            mExpectedGearSelectionTextView.setText(
                                                    "Failed(Timeout)"));
                        }
                    } catch (InterruptedException e) {
                        Log.e(TAG, "Test is interrupted: " + e);
                        runOnUiThread(() -> mExpectedGearSelectionTextView.setText("INTERRUPTED"));
                        Thread.currentThread().interrupt();
                    } finally {
                        mCarPropertyManager.unregisterCallback(mGearSelectionCallback);
                    }
                });
    }

    private final class GearSelectionCallback implements
            CarPropertyManager.CarPropertyEventCallback {
        private final CountDownLatch mCountDownLatch;
        private final List<Integer> mGearOrder;
        private final AtomicInteger mVerifyingIndex = new AtomicInteger(0);

        GearSelectionCallback(List<Integer> gearOrder) {
            mGearOrder = gearOrder;
            mCountDownLatch = new CountDownLatch(gearOrder.size());
        }

        @Override
        public void onChangeEvent(CarPropertyValue value) {
            if (value.getPropertyId() != VehiclePropertyIds.GEAR_SELECTION) {
                return;
            }
            if (value.getStatus() != CarPropertyValue.STATUS_AVAILABLE) {
                Log.e(TAG, "New CarPropertyValue's status is not available - propId: " +
                    value.getPropertyId() + " status: " + value.getStatus());
                return;
            }
            Integer newGearSelection = (Integer) value.getValue();
            runOnUiThread(
                    () ->
                            mCurrentGearSelectionTextView.setText(
                                    VehicleGear.toString(newGearSelection)));
            Log.i(TAG, "New Gear Selection: " + VehicleGear.toString(newGearSelection));

            // All expected gear values verified.
            if (mVerifyingIndex.get() == mGearOrder.size()) {
                return;
            }
            // Check to see if new gear matches the expected gear.
            if (!newGearSelection.equals(mGearOrder.get(mVerifyingIndex.get()))) {
                return;
            }

            mCountDownLatch.countDown();
            mVerifyingIndex.incrementAndGet();
            Log.i(TAG, "Matched gear: " + VehicleGear.toString(newGearSelection));
            // All expected gear values verified.
            if (mVerifyingIndex.get() == mGearOrder.size()) {
                return;
            }
            // Test is not finished so update the expected gear.
            runOnUiThread(
                    () ->
                            mExpectedGearSelectionTextView.setText(
                                    VehicleGear.toString(mGearOrder.get(mVerifyingIndex.get()))));
            Log.i(
                    TAG,
                    "New Expected Gear: "
                            + VehicleGear.toString(mGearOrder.get(mVerifyingIndex.get())));
        }

        @Override
        public void onErrorEvent(int propId, int zone) {
            Log.e(TAG, "propId: " + propId + " zone: " + zone);
        }

        /** Returns true if all expected gears are verified. */
        boolean waitForTestToFinish() throws InterruptedException {
            return mCountDownLatch.await(TEST_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        }
    }
}
