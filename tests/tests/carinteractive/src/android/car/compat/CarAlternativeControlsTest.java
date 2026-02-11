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

package android.car.compat;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.car.Car;
import android.car.drivingstate.CarDrivingStateEvent;
import android.car.drivingstate.CarDrivingStateManager;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.permissions.annotations.EnsureHasPermission;
import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.SystemUtil;
import com.android.interactive.Step;
import com.android.interactive.annotations.Interactive;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@EnsureHasPermission(Car.PERMISSION_CAR_DRIVING_STATE)
public class CarAlternativeControlsTest {

    private static final String VOIP_TEST_PKG = "android.cts.voiptestapp";
    private static final String MEDIA_TEST_PKG = "android.cts.mediatestapp";
    private static final String BACKGROUND_AUDIO_FEATURE =
            "com.android.car.background_audio_while_driving";

    @ClassRule @Rule public static final DeviceState sDeviceState = new DeviceState();

    private static int sDriveState;
    private static long sLastDriveTimestamp;
    private static final long DRIVE_DURATION = 5000;

    private Context mContext;
    private CarDrivingStateManager mCarDrivingStateManager;

    private final CarDrivingStateManager.CarDrivingStateEventListener mDrivingStateEventListener =
            this::handleDrivingEvent;

    @Before
    public void setup() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
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

    @Test
    @Interactive
    @CddTest(requirements = "3.14/A-0-2")
    public void testDialerControlsShow_placeCall_thenDrive() throws Exception {
        assumeTrue("Device must be automotive", isAutomotive());
        assertTrue(
                "Device must support background audio " + BACKGROUND_AUDIO_FEATURE,
                supportsAlternativeAppControls());

        SystemUtil.runShellCommand("am start -n " + VOIP_TEST_PKG + "/.VoipTestActivity");

        assertThat(Step.execute(VerifyVoipReadyStep.class)).isTrue();

        SystemUtil.runShellCommand(
                "am broadcast -a PLACE_CALL -n " + VOIP_TEST_PKG + "/.VoipBroadcastReceiver");

        Step.execute(DriveForDurationStep.class);
        Step.execute(SetToParkStep.class);
        assertThat(Step.execute(VerifyDialerControlsVisibleStep.class)).isTrue();

        SystemUtil.runShellCommand(
                "am broadcast -a DECLINE_CALL -n " + VOIP_TEST_PKG + "/.VoipBroadcastReceiver");
    }

    @Test
    @Interactive
    @CddTest(requirements = "3.14/A-0-2")
    public void testDialerControlsShow_setDrive_thenCall() throws Exception {
        assumeTrue("Device must be automotive", isAutomotive());
        assertTrue(
                "Device must support background audio " + BACKGROUND_AUDIO_FEATURE,
                supportsAlternativeAppControls());

        SystemUtil.runShellCommand("am start -n " + VOIP_TEST_PKG + "/.VoipTestActivity");

        assertThat(Step.execute(VerifyVoipReadyStep.class)).isTrue();
        Step.execute(SetToDriveStep.class);

        SystemUtil.runShellCommand(
                "am broadcast -a PLACE_CALL -n " + VOIP_TEST_PKG + "/.VoipBroadcastReceiver");

        Step.execute(DriveForDurationStep.class);
        Step.execute(SetToParkStep.class);

        assertThat(Step.execute(VerifyDialerControlsVisibleStep.class)).isTrue();

        SystemUtil.runShellCommand(
                "am broadcast -a DECLINE_CALL -n " + VOIP_TEST_PKG + "/.VoipBroadcastReceiver");
    }

    @Test
    @Interactive
    @CddTest(requirements = "3.14/A-0-2")
    public void testMediaControlsShow_startMedia_thenDrive() throws Exception {
        assumeTrue("Device must be automotive", isAutomotive());
        assertTrue(
                "Device must support background audio " + BACKGROUND_AUDIO_FEATURE,
                supportsAlternativeAppControls());

        SystemUtil.runShellCommand("am start -n " + MEDIA_TEST_PKG + "/.MediaTestActivity");

        assertThat(Step.execute(VerifyMediaReadyStep.class)).isTrue();
        Step.execute(DriveForDurationStep.class);
        Step.execute(SetToParkStep.class);

        assertThat(Step.execute(VerifyMediaControlsVisibleStep.class)).isTrue();
    }

    @Test
    @Interactive
    @CddTest(requirements = "3.14/A-0-2")
    public void testMediaControlsShow_setDrive_thenStartMedia() throws Exception {
        assumeTrue("Device must be automotive", isAutomotive());
        assertTrue(
                "Device must support background audio " + BACKGROUND_AUDIO_FEATURE,
                supportsAlternativeAppControls());

        Step.execute(SetToDriveStep.class);

        SystemUtil.runShellCommand("am start -n " + MEDIA_TEST_PKG + "/.MediaTestActivity");
        Step.execute(DriveForDurationStep.class);
        Step.execute(SetToParkStep.class);

        assertThat(Step.execute(VerifyMediaControlsVisibleStep.class)).isTrue();
    }

    private boolean supportsAlternativeAppControls() {
        return mContext.getPackageManager().hasSystemFeature(BACKGROUND_AUDIO_FEATURE);
    }

    private boolean isAutomotive() {
        return mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);
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
