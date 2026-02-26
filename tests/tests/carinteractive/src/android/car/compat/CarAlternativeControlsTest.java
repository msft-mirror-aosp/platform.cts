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

import android.car.Car;
import android.car.common.BaseDrivingTest;
import android.car.common.DriveForDurationStep;
import android.car.common.SetToDriveStep;
import android.car.common.SetToParkStep;

import androidx.test.runner.AndroidJUnit4;

import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.permissions.annotations.EnsureHasPermission;
import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.SystemUtil;
import com.android.interactive.Step;
import com.android.interactive.annotations.Interactive;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@EnsureHasPermission(Car.PERMISSION_CAR_DRIVING_STATE)
public class CarAlternativeControlsTest extends BaseDrivingTest {
    @ClassRule @Rule public static final DeviceState sDeviceState = new DeviceState();

    private static final String VOIP_TEST_PKG = "android.cts.voiptestapp";
    private static final String MEDIA_TEST_PKG = "android.cts.mediatestapp";
    private static final String BACKGROUND_AUDIO_FEATURE =
            "com.android.car.background_audio_while_driving";

    @Test
    @Interactive
    @CddTest(requirements = "3.14/A-0-2")
    public void testDialerControlsShow_placeCall_thenDrive() throws Exception {
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
}
