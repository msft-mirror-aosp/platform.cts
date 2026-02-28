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

package android.car.cal;

import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeTrue;

import android.car.Car;
import android.car.common.BaseDrivingTest;
import android.car.common.SetToDriveStep;
import android.car.common.SetToParkStep;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.permissions.annotations.EnsureHasPermission;
import com.android.compatibility.common.util.CddTest;
import com.android.interactive.Step;
import com.android.interactive.annotations.Interactive;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@EnsureHasPermission(Car.PERMISSION_CAR_DRIVING_STATE)
public class CarCalHybridTest extends BaseDrivingTest {
    @ClassRule @Rule public static final DeviceState sDeviceState = new DeviceState();

    private static final String BACKGROUND_AUDIO_FEATURE =
            "com.android.car.background_audio_while_driving";
    private static final String APP_PACKAGE = "android.cts.calmediatestapp";
    private static final int ACTIVITY_TIMEOUT_MS = 5000;

    @Test
    @Interactive
    @CddTest(requirements = {"3.14/A-0-3"})
    public void testNativeWhileParked_CalWhileDriving() throws Exception {
        PackageManager pm = mContext.getPackageManager();
        assumeTrue(
                "Device did not declare " + BACKGROUND_AUDIO_FEATURE + ". " + "Skipping Test.",
                pm.hasSystemFeature(BACKGROUND_AUDIO_FEATURE));

        // Start MainActivity
        ComponentName cn = new ComponentName(APP_PACKAGE, APP_PACKAGE + ".MainActivity");
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setComponent(cn);
        intent.addFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP);
        TestApis.activities().startActivity(intent);

        // Wait for MainActivity to appear
        UiObject2 mainTitle =
                mDevice.wait(
                        Until.findObject(By.text("MainActivity (Not Distraction Optimized)")),
                        ACTIVITY_TIMEOUT_MS);
        assertNotNull("MainActivity did not launch", mainTitle);

        Step.execute(SetToDriveStep.class);

        // Validate that CarAppActivity is presented instead of BlockingActivity
        UiObject2 libraryTitle =
                mDevice.wait(
                        Until.findObject(By.text("Driver-optimized CarAppActivity")),
                        ACTIVITY_TIMEOUT_MS);
        assertNotNull("CarAppActivity did not launch upon entering DRIVE", libraryTitle);
        Step.execute(SetToParkStep.class);
    }
}
