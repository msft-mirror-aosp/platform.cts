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

import static android.os.Build.VERSION_CODES.BAKLAVA;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.pm.PackageManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.AmUtils;
import com.android.compatibility.common.util.ShellIdentityUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Build, install and run the tests by running the commands below: make cts -j64 cts-tradefed run
 * cts -m CtsTelephonyTestCases --test android.telephony.cts.NetworkSelectionModeTest
 */
public class NetworkSelectionModeTest {
    private static final String TAG = "NetworkSelectionModeTest";

    private PackageManager mPackageManager;
    private TelephonyManager mTelephonyManager;

    // FIXME(b/453757047) Modem Simulator Requires Specific Hard-Coded PLMN-IDs
    // private static final String TESTING_PLMN = "12345"; // 5-digit
    // private static final String TESTING_PLMN_2 = "456789"; // 6-digit
    private static final String TESTING_PLMN = "311740"; // 5-digit
    private static final String TESTING_PLMN_2 = "310300"; // 6-digit

    private int mTestSub;

    private static Context getContext() {
        return InstrumentationRegistry.getContext();
    }

    /** Checks whether the telephony feature is supported. */
    private boolean hasFeature(String feature) {
        return mPackageManager.hasSystemFeature(feature);
    }

    @Before
    public void setUp() throws Exception {
        mPackageManager = getContext().getPackageManager();
        mTestSub = SubscriptionManager.getDefaultSubscriptionId();
        assumeTrue(
                "Skipping tests because default subscription ID is invalid",
                mTestSub != SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        mTelephonyManager =
                getContext()
                        .getSystemService(TelephonyManager.class)
                        .createForSubscriptionId(mTestSub);
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(android.Manifest.permission.READ_PHONE_STATE);
        // Wait previously queued broadcasts to complete before starting the test
        AmUtils.waitForBroadcastBarrier();

        assumeTrue(
                "Device does not have FEATURE_TELEPHONY_RADIO_ACCESS",
                hasFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));
        assumeTrue(
                "Device does not have PHONE_TYPE_GSM",
                mTelephonyManager.getPhoneType() == TelephonyManager.PHONE_TYPE_GSM);
    }

    @After
    public void tearDown() throws Exception {
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .dropShellPermissionIdentity();
    }

    /**
     * Tests that the device properly sets the network selection mode to automatic. Expects a
     * security exception since the caller does not have carrier privileges.
     */
    @Test
    public void testSetNetworkSelectionModeAutomatic() {
        assertThrows(
                "Expected SecurityException. App does not have carrier privileges.",
                SecurityException.class,
                () -> mTelephonyManager.setNetworkSelectionModeAutomatic());
    }

    /**
     * Tests that the device properly asks the radio to connect to the input network and change
     * selection mode to manual. Expects a security exception since the caller does not have carrier
     * privileges.
     */
    @Test
    public void testSetNetworkSelectionModeManual() {
        assertThrows(
                "Expected SecurityException. App does not have carrier privileges.",
                SecurityException.class,
                () ->
                        mTelephonyManager.setNetworkSelectionModeManual(
                                "" /* operatorNumeric */, false /* persistSelection */));
    }

    /** Tests that the device properly check whether selection mode was manual. */
    @Test
    public void testIsManualNetworkSelectionAllowed() {
        assertTrue(
                "Manual selection is not allowed",
                ShellIdentityUtils.invokeMethodWithShellPermissions(
                        mTelephonyManager, (tm) -> tm.isManualNetworkSelectionAllowed()));
    }

    /** Tests that the device properly reports the contents of NetworkSelectionMode */
    @Test
    public void testGetNetworkSelectionMode() {
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    mTelephonyManager, (tm) -> tm.setNetworkSelectionModeAutomatic());
        } catch (Exception e) {
        }

        int networkMode =
                ShellIdentityUtils.invokeMethodWithShellPermissions(
                        mTelephonyManager, (tm) -> tm.getNetworkSelectionMode());

        assertEquals(
                "Network mode is not auto",
                TelephonyManager.NETWORK_SELECTION_MODE_AUTO,
                networkMode);
    }

    /**
     * Tests that the device properly reports the contents of ManualNetworkSelectionPlmn The setting
     * is not persisted selection
     */
    @Test
    public void testGetManualNetworkSelectionPlmnNonPersisted() {
        try {
            boolean result =
                    ShellIdentityUtils.invokeMethodWithShellPermissions(
                            mTelephonyManager,
                            (tm) ->
                                    tm.setNetworkSelectionModeManual(
                                            TESTING_PLMN /* operatorNumeric */,
                                            false /* persistSelection */));
            if (ApiLevelUtil.isVendorApiLevelGreaterThan(BAKLAVA)) {
                assertTrue("Failed to setNetworkSelectionModeManual", result);
            }
            String plmn =
                    ShellIdentityUtils.invokeMethodWithShellPermissions(
                            mTelephonyManager, (tm) -> tm.getManualNetworkSelectionPlmn());
            assertEquals("Unexpected plmn", TESTING_PLMN, plmn);

            result =
                    ShellIdentityUtils.invokeMethodWithShellPermissions(
                            mTelephonyManager,
                            (tm) ->
                                    tm.setNetworkSelectionModeManual(
                                            TESTING_PLMN_2 /* operatorNumeric */,
                                            false /* persistSelection */));

            if (ApiLevelUtil.isVendorApiLevelGreaterThan(BAKLAVA)) {
                assertTrue("Failed to setNetworkSelectionModeManual again", result);
            }
            plmn =
                    ShellIdentityUtils.invokeMethodWithShellPermissions(
                            mTelephonyManager, (tm) -> tm.getManualNetworkSelectionPlmn());
            assertEquals("Unexpected plmn", TESTING_PLMN_2, plmn);
        } finally {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    mTelephonyManager, (tm) -> tm.setNetworkSelectionModeAutomatic());
        }
    }

    /**
     * Tests that the device properly reports the contents of ManualNetworkSelectionPlmn The setting
     * is persisted selection
     */
    @Test
    public void testGetManualNetworkSelectionPlmnPersisted() {
        try {
            final boolean result =
                    ShellIdentityUtils.invokeMethodWithShellPermissions(
                            mTelephonyManager,
                            (tm) ->
                                    tm.setNetworkSelectionModeManual(
                                            TESTING_PLMN /* operatorNumeric */,
                                            true /* persistSelection */));
            if (ApiLevelUtil.isVendorApiLevelGreaterThan(BAKLAVA)) {
                assertTrue("Failed to setNetworkSelectionModeManual again", result);
            }
            String plmn =
                    ShellIdentityUtils.invokeMethodWithShellPermissions(
                            mTelephonyManager, (tm) -> tm.getManualNetworkSelectionPlmn());
            assertEquals("Unexpected plmn", TESTING_PLMN, plmn);
        } finally {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    mTelephonyManager, (tm) -> tm.setNetworkSelectionModeAutomatic());
        }
    }
}
