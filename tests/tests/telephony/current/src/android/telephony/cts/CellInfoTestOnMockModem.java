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
package android.telephony.cts;

import static android.telephony.mockmodem.MockSimService.MOCK_SIM_PROFILE_ID_TWN_CHT;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertTrue;

import android.telephony.mockmodem.MockModemManager;
import android.telephony.ServiceState;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.util.Log;
import com.android.compatibility.common.util.ShellIdentityUtils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

/** Perform test cases of CellInfoTest on MockModem. */
public class CellInfoTestOnMockModem extends CellInfoTest {

    private static final String LOG_TAG = "CellInfoTestOnMockModem";

    private static final int TEST_SIM_SLOT_ID = 0;
    private static MockModemManager sMockModemManager;
    private static boolean sIsMockModemSupported = true;

    @BeforeClass
    public static void beforeAllTests() throws Exception {
        if (!MockModemTestBase.beforeAllTestsCheck()) {
            Log.e(LOG_TAG, "MockModem is not supported!");
            sIsMockModemSupported = false;
            return;
        }
        MockModemTestBase.createMockModemAndConnectToService();
        sMockModemManager = MockModemTestBase.getMockModemManager();
    }

    @AfterClass
    public static void afterAllTests() throws Exception {
        MockModemTestBase.afterAllTestsBase();
    }

    private void waitForServiceStateInService() throws Exception {
        CountDownLatch serviceStateLatch = new CountDownLatch(1);

        class ServiceStateListener extends TelephonyCallback
                implements TelephonyCallback.ServiceStateListener {
            @Override
            public void onServiceStateChanged(ServiceState serviceState) {
                if (serviceState != null
                        && serviceState.getState() == ServiceState.STATE_IN_SERVICE) {
                    serviceStateLatch.countDown();
                }
            }
        }

        ServiceStateListener listener = new ServiceStateListener();
        TelephonyManager tm = getInstrumentation().getContext()
                .getSystemService(TelephonyManager.class);

        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                tm,  (t) -> t.registerTelephonyCallback(Runnable::run, listener));

        try {
            assertTrue("Framework did not update ServiceState to IN_SERVICE within timeout",
                    serviceStateLatch.await(5, TimeUnit.SECONDS));
        } finally {
            tm.unregisterTelephonyCallback(listener);
        }
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();

        if (!sIsMockModemSupported) return;

        // Remove the SIM for initial state, don't need to check the result
        sMockModemManager.removeSimCard(TEST_SIM_SLOT_ID);
        // Insert a SIM
        assertTrue(sMockModemManager.insertSimCard(TEST_SIM_SLOT_ID, MOCK_SIM_PROFILE_ID_TWN_CHT));
        // Change service state to be REGISTERED
        assertTrue(
                sMockModemManager.changeNetworkService(
                        TEST_SIM_SLOT_ID, MOCK_SIM_PROFILE_ID_TWN_CHT, true));

        waitForServiceStateInService();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();

        if (!sIsMockModemSupported) return;

        // Change service state to be NOT REGISTERED
        assertTrue(
                sMockModemManager.changeNetworkService(
                        TEST_SIM_SLOT_ID, MOCK_SIM_PROFILE_ID_TWN_CHT, false));
        // Remove the SIM
        sMockModemManager.removeSimCard(TEST_SIM_SLOT_ID);
    }

    // All test cases are in CellInfoTest which will be performed here
}
