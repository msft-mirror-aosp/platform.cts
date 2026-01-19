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

package android.telephony.satellite.cts;

import static android.Manifest.permission.SATELLITE_COMMUNICATION;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.platform.test.annotations.AppModeFull;
import android.telephony.satellite.SatelliteManager;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

/**
 * CTS tests for SatelliteManager service metadata functionality.
 */
@AppModeFull(reason = "Cannot get SatelliteManager in instant app mode")
@RunWith(AndroidJUnit4.class)
@SmallTest
public class SatelliteManagerServiceMetaDataTest {
    private static final String SATELLITE_OPTIMIZED_APP_PACKAGE_NAME = "android.telephony2.cts";

    private Context mContext;
    private SatelliteManager mSatelliteManager;

    @Before
    public void setUp() throws Exception {
        mContext = ApplicationProvider.getApplicationContext();
        if (isSatelliteSupported()) {
            mSatelliteManager = mContext.getSystemService(SatelliteManager.class);
            assertNotNull(mSatelliteManager);
        }
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(SATELLITE_COMMUNICATION);
    }

    @After
    public void tearDown() throws Exception {
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .dropShellPermissionIdentity();
    }

    @Test
    public void testAppIsSatelliteOptimized() {
        if (!isSatelliteSupported()) return;

        List<String> optimizedApps = mSatelliteManager.getSatelliteDataOptimizedApps();
        assertNotNull(optimizedApps);
        assertTrue(
                "Optimized app list should contain the test app",
                optimizedApps.contains(SATELLITE_OPTIMIZED_APP_PACKAGE_NAME));
    }

    private boolean isSatelliteSupported() {
        return mContext.getPackageManager()
                .hasSystemFeature("android.hardware.telephony.satellite");
    }
}
