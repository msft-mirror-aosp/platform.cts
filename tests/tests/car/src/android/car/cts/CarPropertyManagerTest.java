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
package android.car.cts;

import static org.testng.Assert.assertThrows;
import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import static java.lang.Integer.toHexString;

import android.car.Car;
import android.car.VehicleAreaSeat;
import android.car.VehicleAreaType;
import android.car.VehiclePropertyIds;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyManager;
import android.car.hardware.property.CarPropertyManager.CarPropertyEventCallback;
import android.platform.test.annotations.RequiresDevice;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.ArraySet;
import android.util.Log;
import android.util.SparseArray;

import androidx.annotation.GuardedBy;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.CddTest;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@SmallTest
@RequiresDevice
@RunWith(AndroidJUnit4.class)
public class CarPropertyManagerTest extends CarApiTestBase {

    private static final String TAG = CarPropertyManagerTest.class.getSimpleName();
    private static final long WAIT_CALLBACK = 1500L;
    private CarPropertyManager mCarPropertyManager;
    /** contains property Ids for the properties required by CDD*/
    private ArraySet<Integer> mPropertyIds = new ArraySet<>();


    private static class CarPropertyEventCounter implements CarPropertyEventCallback {
        private final Object mLock = new Object();

        @GuardedBy("mLock")
        private SparseArray<Integer> mEventCounter = new SparseArray<>();

        @GuardedBy("mLock")
        private SparseArray<Integer> mErrorCounter = new SparseArray<>();

        public int receivedEvent(int propId) {
            int val;
            synchronized (mLock) {
                val = mEventCounter.get(propId, 0);
            }
            return val;
        }

        public int receivedError(int propId) {
            int val;
            synchronized (mLock) {
                val = mErrorCounter.get(propId, 0);
            }
            return val;
        }

        @Override
        public void onChangeEvent(CarPropertyValue value) {
            synchronized (mLock) {
                int val = mEventCounter.get(value.getPropertyId(), 0) + 1;
                mEventCounter.put(value.getPropertyId(), val);
            }
        }

        @Override
        public void onErrorEvent(int propId, int zone) {
            synchronized (mLock) {
                int val = mErrorCounter.get(propId, 0) + 1;
                mErrorCounter.put(propId, val);
            }
        }
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
        mCarPropertyManager = (CarPropertyManager) getCar().getCarManager(Car.PROPERTY_SERVICE);
        mPropertyIds.add(VehiclePropertyIds.PERF_VEHICLE_SPEED);
        mPropertyIds.add(VehiclePropertyIds.GEAR_SELECTION);
        mPropertyIds.add(VehiclePropertyIds.NIGHT_MODE);
        mPropertyIds.add(VehiclePropertyIds.PARKING_BRAKE_ON);
    }

    /**
     * Test for {@link CarPropertyManager#getPropertyList()}
     */
    @Test
    public void testGetPropertyList() {
        List<CarPropertyConfig> allConfigs = mCarPropertyManager.getPropertyList();
        assertNotNull(allConfigs);
    }

    /**
     * Test for {@link CarPropertyManager#getPropertyList(ArraySet)}
     */
    @Test
    public void testGetPropertyListWithArraySet() {
        List<CarPropertyConfig> requiredConfigs = mCarPropertyManager.getPropertyList(mPropertyIds);
        // Vehicles need to implement all of those properties
        assertEquals(mPropertyIds.size(), requiredConfigs.size());
    }

    /**
     * Test for {@link CarPropertyManager#getCarPropertyConfig(int)}
     */
    @Test
    public void testGetPropertyConfig() {
        List<CarPropertyConfig> allConfigs = mCarPropertyManager.getPropertyList();
        for (CarPropertyConfig cfg : allConfigs) {
            assertNotNull(mCarPropertyManager.getCarPropertyConfig(cfg.getPropertyId()));
        }
    }

    /**
     * Test for {@link CarPropertyManager#getAreaId(int, int)}
     */
    @Test
    public void testGetAreaId() {
        // For global properties, getAreaId should always return 0.
        List<CarPropertyConfig> allConfigs = mCarPropertyManager.getPropertyList();
        for (CarPropertyConfig cfg : allConfigs) {
            if (cfg.isGlobalProperty()) {
                assertEquals(0, mCarPropertyManager.getAreaId(cfg.getPropertyId(),
                        VehicleAreaSeat.SEAT_ROW_1_LEFT));
            } else {
                int[] areaIds = cfg.getAreaIds();
                // Because areaId in propConfig must not be overlapped with each other.
                // The result should be itself.
                for (int areaIdInConfig : areaIds) {
                    int areaIdByCarPropertyManager =
                            mCarPropertyManager.getAreaId(cfg.getPropertyId(), areaIdInConfig);
                    assertEquals(areaIdInConfig, areaIdByCarPropertyManager);
                }
            }
        }
    }

    @CddTest(requirement="2.5.1")
    @Test
    public void testMustSupportGearSelection() throws Exception {
        assertTrue("Must support GEAR_SELECTION",
            mCarPropertyManager.getPropertyList().stream().anyMatch(cfg -> cfg.getPropertyId() ==
                VehiclePropertyIds.GEAR_SELECTION));
    }

    @CddTest(requirement="2.5.1")
    @Test
    public void testMustSupportNightMode() {
        assertTrue("Must support NIGHT_MODE",
            mCarPropertyManager.getPropertyList().stream().anyMatch(cfg -> cfg.getPropertyId() ==
                VehiclePropertyIds.NIGHT_MODE));
    }

    @CddTest(requirement="2.5.1")
    @Test
    public void testMustSupportPerfVehicleSpeed() throws Exception {
        assertTrue("Must support PERF_VEHICLE_SPEED",
            mCarPropertyManager.getPropertyList().stream().anyMatch(cfg -> cfg.getPropertyId() ==
                VehiclePropertyIds.PERF_VEHICLE_SPEED));
    }

    @CddTest(requirement = "2.5.1")
    @Test
    public void testMustSupportParkingBrakeOn() throws Exception {
        assertTrue("Must support PARKING_BRAKE_ON",
            mCarPropertyManager.getPropertyList().stream().anyMatch(cfg -> cfg.getPropertyId() ==
                VehiclePropertyIds.PARKING_BRAKE_ON));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testGetProperty() {
        List<CarPropertyConfig> configs = mCarPropertyManager.getPropertyList(mPropertyIds);
        for (CarPropertyConfig cfg : configs) {
            if (cfg.getAccess() == CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ) {
                int[] areaIds = getAreaIdsHelper(cfg);
                int propId = cfg.getPropertyId();
                // no guarantee if we can get values, just call and check if it throws exception.
                if (cfg.getPropertyType() == Boolean.class) {
                    for (int areaId : areaIds) {
                        mCarPropertyManager.getBooleanProperty(propId, areaId);
                    }
                } else if (cfg.getPropertyType() == Integer.class) {
                    for (int areaId : areaIds) {
                        mCarPropertyManager.getIntProperty(propId, areaId);
                    }
                } else if (cfg.getPropertyType() == Float.class) {
                    for (int areaId : areaIds) {
                        mCarPropertyManager.getFloatProperty(propId, areaId);
                    }
                } else if (cfg.getPropertyType() == Integer[].class) {
                    for (int areId : areaIds) {
                        mCarPropertyManager.getIntArrayProperty(propId, areId);
                    }
                } else {
                    for (int areaId : areaIds) {
                        mCarPropertyManager.getProperty(
                                cfg.getPropertyType(), propId, areaId);;
                    }
                }
            }
        }
    }

    @Test
    public void testGetIntArrayProperty() {
        List<CarPropertyConfig> allConfigs = mCarPropertyManager.getPropertyList();
        for (CarPropertyConfig cfg : allConfigs) {
            if (cfg.getAccess() == CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_NONE
                    || cfg.getAccess() == CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_WRITE
                    || cfg.getPropertyType() != Integer[].class) {
                // skip the test if the property is not readable or not an int array type property.
                continue;
            }
            switch (cfg.getPropertyId()) {
                case VehiclePropertyIds.INFO_FUEL_TYPE:
                    int[] fuelTypes = mCarPropertyManager.getIntArrayProperty(cfg.getPropertyId(),
                            VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL);
                    verifyEnumsRange(EXPECTED_FUEL_TYPES, fuelTypes);
                    break;
                case VehiclePropertyIds.INFO_MULTI_EV_PORT_LOCATIONS:
                    int[] evPortLocations = mCarPropertyManager.getIntArrayProperty(
                            cfg.getPropertyId(),VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL);
                    verifyEnumsRange(EXPECTED_PORT_LOCATIONS, evPortLocations);
                    break;
                default:
                    int[] areaIds = getAreaIdsHelper(cfg);
                    for(int areaId : areaIds) {
                        mCarPropertyManager.getIntArrayProperty(cfg.getPropertyId(), areaId);
                    }
            }
        }
    }

    private void verifyEnumsRange(List<Integer> expectedResults, int[] results) {
        assertThat(results).isNotNull();
        // If the property is not implemented in cars, getIntArrayProperty returns an empty array.
        if (results.length == 0) {
            return;
        }
        for (int result : results) {
            assertThat(result).isIn(expectedResults);
        }
    }

    @Test
    public void testIsPropertyAvailable() {
        List<CarPropertyConfig> configs = mCarPropertyManager.getPropertyList(mPropertyIds);

        for (CarPropertyConfig cfg : configs) {
            int[] areaIds = getAreaIdsHelper(cfg);
            for (int areaId : areaIds) {
                assertTrue(mCarPropertyManager.isPropertyAvailable(cfg.getPropertyId(), areaId));
            }
        }
    }

    @Test
    public void testSetProperty() {
        List<CarPropertyConfig> configs = mCarPropertyManager.getPropertyList();
        for (CarPropertyConfig cfg : configs) {
            if (cfg.getAccess() == CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE
                    && cfg.getPropertyType() == Boolean.class) {
                // In R, there is no property which is writable for third-party apps.
                for (int areaId : getAreaIdsHelper(cfg)) {
                    assertThrows(SecurityException.class,
                            () -> mCarPropertyManager.setBooleanProperty(
                                    cfg.getPropertyId(), areaId,true));
                }
            }
        }
    }

    @Test
    public void testRegisterCallback() throws Exception {
        //Test on registering a invalid property
        int invalidPropertyId = -1;
        boolean isRegistered = mCarPropertyManager.registerCallback(
            new CarPropertyEventCounter(), invalidPropertyId, 0);
        assertFalse(isRegistered);

        // Test for continuous properties
        int vehicleSpeed = VehiclePropertyIds.PERF_VEHICLE_SPEED;
        CarPropertyEventCounter speedListenerNormal = new CarPropertyEventCounter();
        CarPropertyEventCounter speedListenerUI = new CarPropertyEventCounter();

        assertEquals(0, speedListenerNormal.receivedEvent(vehicleSpeed));
        assertEquals(0, speedListenerNormal.receivedError(vehicleSpeed));
        assertEquals(0, speedListenerUI.receivedEvent(vehicleSpeed));
        assertEquals(0, speedListenerUI.receivedError(vehicleSpeed));

        mCarPropertyManager.registerCallback(speedListenerNormal, vehicleSpeed,
                CarPropertyManager.SENSOR_RATE_NORMAL);
        mCarPropertyManager.registerCallback(speedListenerUI, vehicleSpeed,
                CarPropertyManager.SENSOR_RATE_FASTEST);
        // TODO(b/149778976): Use CountDownLatch in listener instead of waitingTime
        Thread.sleep(WAIT_CALLBACK);
        assertNotEquals(0, speedListenerNormal.receivedEvent(vehicleSpeed));
        assertNotEquals(0, speedListenerUI.receivedEvent(vehicleSpeed));
        assertTrue(speedListenerUI.receivedEvent(vehicleSpeed) >
                speedListenerNormal.receivedEvent(vehicleSpeed));

        mCarPropertyManager.unregisterCallback(speedListenerUI);
        mCarPropertyManager.unregisterCallback(speedListenerNormal);

        // Test for on_change properties
        int nightMode = VehiclePropertyIds.NIGHT_MODE;
        CarPropertyEventCounter nightModeListener = new CarPropertyEventCounter();
        mCarPropertyManager.registerCallback(nightModeListener, nightMode, 0);
        Thread.sleep(WAIT_CALLBACK);
        assertEquals(1, nightModeListener.receivedEvent(nightMode));

        mCarPropertyManager.unregisterCallback(nightModeListener);

    }

    @Test
    public void testUnregisterCallback() throws Exception {

        int vehicleSpeed = VehiclePropertyIds.PERF_VEHICLE_SPEED;
        CarPropertyEventCounter speedListenerNormal = new CarPropertyEventCounter();
        CarPropertyEventCounter speedListenerUI = new CarPropertyEventCounter();

        mCarPropertyManager.registerCallback(speedListenerNormal, vehicleSpeed,
                CarPropertyManager.SENSOR_RATE_NORMAL);

        // test on unregistering a callback that was never registered
        try {
            mCarPropertyManager.unregisterCallback(speedListenerUI);
        } catch (Exception e) {
            Assert.fail();
        }

        mCarPropertyManager.registerCallback(speedListenerUI, vehicleSpeed,
                CarPropertyManager.SENSOR_RATE_UI);
        Thread.sleep(WAIT_CALLBACK);

        mCarPropertyManager.unregisterCallback(speedListenerNormal, vehicleSpeed);
        Thread.sleep(WAIT_CALLBACK);

        int currentEventNormal = speedListenerNormal.receivedEvent(vehicleSpeed);
        int currentEventUI = speedListenerUI.receivedEvent(vehicleSpeed);
        Thread.sleep(WAIT_CALLBACK);

        assertEquals(currentEventNormal, speedListenerNormal.receivedEvent(vehicleSpeed));
        assertNotEquals(currentEventUI, speedListenerUI.receivedEvent(vehicleSpeed));

        mCarPropertyManager.unregisterCallback(speedListenerUI);
        Thread.sleep(WAIT_CALLBACK);

        currentEventUI = speedListenerUI.receivedEvent(vehicleSpeed);
        Thread.sleep(WAIT_CALLBACK);
        assertEquals(currentEventUI, speedListenerUI.receivedEvent(vehicleSpeed));
    }

    @Test
    public void testUnregisterWithPropertyId() throws Exception {
        // Ignores the test if wheel_tick property does not exist in the car.
        Assume.assumeTrue("WheelTick is not available, skip unregisterCallback test",
                mCarPropertyManager.isPropertyAvailable(
                        VehiclePropertyIds.WHEEL_TICK, VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL));

        CarPropertyEventCounter speedAndWheelTicksListener = new CarPropertyEventCounter();
        mCarPropertyManager.registerCallback(speedAndWheelTicksListener,
                VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_FAST);
        mCarPropertyManager.registerCallback(speedAndWheelTicksListener,
                VehiclePropertyIds.WHEEL_TICK, CarPropertyManager.SENSOR_RATE_FAST);

        // TODO(b/149778976): Use CountDownLatch in listener instead of waitingTime
        Thread.sleep(WAIT_CALLBACK);
        mCarPropertyManager.unregisterCallback(speedAndWheelTicksListener,
                VehiclePropertyIds.PERF_VEHICLE_SPEED);
        int currentSpeedEvents = speedAndWheelTicksListener.receivedEvent(
                VehiclePropertyIds.PERF_VEHICLE_SPEED);
        int currentWheelTickEvents = speedAndWheelTicksListener.receivedEvent(
                VehiclePropertyIds.WHEEL_TICK);

        Thread.sleep(WAIT_CALLBACK);
        int speedEventsAfterUnregister = speedAndWheelTicksListener.receivedEvent(
                VehiclePropertyIds.PERF_VEHICLE_SPEED);
        int wheelTicksEventsAfterUnregister = speedAndWheelTicksListener.receivedEvent(
                VehiclePropertyIds.WHEEL_TICK);

        assertThat(currentSpeedEvents).isEqualTo(speedEventsAfterUnregister);
        assertThat(wheelTicksEventsAfterUnregister).isGreaterThan(currentWheelTickEvents);
    }


    // Returns {0} if the property is global property, otherwise query areaId for CarPropertyConfig
    private int[] getAreaIdsHelper(CarPropertyConfig config) {
        if (config.isGlobalProperty()) {
            int[] areaIds = {0};
            return areaIds;
        } else {
            return config.getAreaIds();
        }
    }
}
