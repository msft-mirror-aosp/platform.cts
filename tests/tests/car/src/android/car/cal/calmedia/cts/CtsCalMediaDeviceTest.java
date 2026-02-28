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
package android.car.cal.calmedia.cts;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.android.bedstead.nene.TestApis;
import com.android.compatibility.common.util.CddTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests support of CarAppLibrary templated media apps */
@RunWith(AndroidJUnit4.class)
public class CtsCalMediaDeviceTest {

    private static final String CAL_MEDIA_TEST_APP_PACKAGE = "android.cts.calmediatestapp";
    private static final String FEATURE_CAR_APP_LIBRARY_MEDIA =
            "android.software.car.templates_host.media";
    private static final String INTENT_ACTION_MEDIA_TEMPLATE_KEY =
            "android.car.intent.action.MEDIA_TEMPLATE";
    private static final String INTENT_EXTRA_MEDIA_COMPONENT_KEY =
            "android.car.intent.extra.MEDIA_COMPONENT";

    /** Timeout for launching the media activity. */
    private static final long ACTIVITY_TIMEOUT_MS = 10_000L;

    private Context mContext;
    private UiDevice mUiDevice;

    @Before
    public void setup() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        mContext = instrumentation.getTargetContext();
        mUiDevice = UiDevice.getInstance(instrumentation);
    }

    /** Tests that the Template Host is installed, and all required permission are granted. */
    @Test
    @CddTest(requirements = "3.14/A-0-3")
    public void testCalScreenShows_startMediaDispatcherActivity_withCalMbs() {
        // Assumption Fail if the Feature does not exist on the Device
        PackageManager pm = mContext.getPackageManager();
        assumeTrue(
                "Device did not declare android.software.car.templates_host.media. "
                        + "Skipping Test.",
                pm.hasSystemFeature(FEATURE_CAR_APP_LIBRARY_MEDIA));

        // Send intent to launch MediaDispatcherActivity with CalMediaTestApp CalRedirectBrowser
        // component name
        ComponentName cn = new ComponentName(CAL_MEDIA_TEST_APP_PACKAGE, ".MediaService");
        Intent intent =
                new Intent(INTENT_ACTION_MEDIA_TEMPLATE_KEY)
                        .putExtra(INTENT_EXTRA_MEDIA_COMPONENT_KEY, cn.flattenToString())
                        .addFlags(FLAG_ACTIVITY_NEW_TASK);

        TestApis.activities().startActivity(intent);

        // Ensure the CalMediaTestApp package is launched
        assertTrue(
                "The CarAppLibrary Templated UI was not launched",
                hasViewInPackage(CAL_MEDIA_TEST_APP_PACKAGE));

        // Ensure the correct title Text from the CAL App UI is showing
        UiObject2 title =
                mUiDevice.wait(Until.findObject(By.text("CalMediaTestApp")), ACTIVITY_TIMEOUT_MS);
        assertNotNull(title);
    }

    private boolean hasViewInPackage(String packageName) {
        return mUiDevice.wait(Until.hasObject(By.pkg(packageName).depth(0)), ACTIVITY_TIMEOUT_MS);
    }
}
