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

import static org.junit.Assert.assertTrue;

import android.telephony.mockmodem.MockModemManager;
import android.util.Log;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

/** Perform test cases of CellInfoTest on MockModem. */
public class CellInfoTestOnMockModem extends CellInfoTest {

    private static final String LOG_TAG = "CellInfoTestOnMockModem";

    private static final int TEST_SIM_SLOT_ID = 0;
    private static MockModemManager sMockModemManager;

    @BeforeClass
    public static void beforeAllTests() throws Exception {
        if (!MockModemTestBase.beforeAllTestsCheck()) {
            Log.e(LOG_TAG, "MockModem is not supported!");
            return;
        }
        MockModemTestBase.createMockModemAndConnectToService();
        sMockModemManager = MockModemTestBase.getMockModemManager();
    }

    @AfterClass
    public static void afterAllTests() throws Exception {
        MockModemTestBase.afterAllTestsBase();
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();

        // Remove the SIM for initial state, don't need to check the result
        sMockModemManager.removeSimCard(TEST_SIM_SLOT_ID);
        // Insert a SIM
        assertTrue(sMockModemManager.insertSimCard(TEST_SIM_SLOT_ID, MOCK_SIM_PROFILE_ID_TWN_CHT));
        // Change service state to be REGISTERED
        assertTrue(
                sMockModemManager.changeNetworkService(
                        TEST_SIM_SLOT_ID, MOCK_SIM_PROFILE_ID_TWN_CHT, true));
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();

        // Remove the SIM
        sMockModemManager.removeSimCard(TEST_SIM_SLOT_ID);
    }

    // All test cases are in CellInfoTest which will be performed here
}
