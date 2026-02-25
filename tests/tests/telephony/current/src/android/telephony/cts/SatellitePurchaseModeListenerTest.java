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

package android.telephony.cts;

import static android.telephony.TelephonyManager.SATELLITE_PURCHASE_MODE_STATE_ACTIVE;
import static android.telephony.TelephonyManager.SATELLITE_PURCHASE_MODE_STATE_INACTIVE;
import static android.telephony.TelephonyManager.SATELLITE_PURCHASE_MODE_STATE_NETWORK_SETUP;
import static android.telephony.TelephonyManager.SATELLITE_PURCHASE_MODE_STATE_NETWORK_TEARDOWN;

import static androidx.test.InstrumentationRegistry.getContext;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.pm.PackageManager;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.telephony.TelephonyRegistryManager;

import com.android.compatibility.common.util.ShellIdentityUtils;
import com.android.internal.telephony.flags.Flags;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class SatellitePurchaseModeListenerTest {
    private static final String TAG = "SatellitePurchaseModeListenerTest";

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private TelephonyManager mTelephonyManager;
    private TelephonyRegistryManager mTelephonyRegistryManager;
    private final LinkedBlockingQueue<Boolean> mQueueEnabled = new LinkedBlockingQueue<>();
    private final LinkedBlockingQueue<Integer> mQueueState = new LinkedBlockingQueue<>();
    private static final long WAIT_TIME = 5000;
    private static final long TIMEOUT_MILLIS = 1000;
    private final Executor mSimpleExecutor = Runnable::run;

    @Before
    public void setUp() throws Exception {
        PackageManager packageManager = getContext().getPackageManager();
        assumeTrue(
                "Skipping test that requires FEATURE_TELEPHONY",
                packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY));

        mTelephonyManager = getContext().getSystemService(TelephonyManager.class);
        mTelephonyRegistryManager = getContext().getSystemService(TelephonyRegistryManager.class);
    }

    private class SatellitePurchaseModeListener extends TelephonyCallback
            implements TelephonyCallback.SatellitePurchaseModeListener {
        @Override
        public void onSatellitePurchaseModeChanged(
                int subId, boolean isEnabled, int purchaseModeState) {
            mQueueEnabled.offer(isEnabled);
            mQueueState.offer(purchaseModeState);
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_SATELLITE_UPSELL_26Q4)
    public void testOnSatellitePurchaseModeChanged() throws Throwable {
        SatellitePurchaseModeListener callback = new SatellitePurchaseModeListener();

        // Register the callback
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                mTelephonyManager, (tm) -> tm.registerTelephonyCallback(mSimpleExecutor, callback));

        try {
            // Consume initial callback if any
            mQueueEnabled.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            mQueueState.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

            int subId = mTelephonyManager.getSubscriptionId();

            // 1. Setup satellite purchase Mode network
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    mTelephonyRegistryManager,
                    (trm) ->
                            trm.notifySatellitePurchaseModeChanged(
                                    subId, true, SATELLITE_PURCHASE_MODE_STATE_NETWORK_SETUP));

            Boolean isEnabled = mQueueEnabled.poll(WAIT_TIME, TimeUnit.MILLISECONDS);
            Integer purchaseModeState = mQueueState.poll(WAIT_TIME, TimeUnit.MILLISECONDS);

            assertNotNull("Timed out waiting for purchase mode change to true", isEnabled);
            assertTrue(isEnabled);

            assertNotNull(
                    "Timed out waiting for purchase mode state change to network setup",
                    purchaseModeState);
            assertEquals(SATELLITE_PURCHASE_MODE_STATE_NETWORK_SETUP, (int) purchaseModeState);

            // 2. Purchase Mode is now active, network available
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    mTelephonyRegistryManager,
                    (trm) ->
                            trm.notifySatellitePurchaseModeChanged(
                                    subId, true, SATELLITE_PURCHASE_MODE_STATE_ACTIVE));

            isEnabled = mQueueEnabled.poll(WAIT_TIME, TimeUnit.MILLISECONDS);
            purchaseModeState = mQueueState.poll(WAIT_TIME, TimeUnit.MILLISECONDS);

            assertNotNull("Timed out waiting for purchase mode change to true", isEnabled);
            assertTrue(isEnabled);

            assertNotNull(
                    "Timed out waiting for purchase mode state change to active",
                    purchaseModeState);
            assertEquals(SATELLITE_PURCHASE_MODE_STATE_ACTIVE, (int) purchaseModeState);

            // 3. Teardown satellite purchase mode network
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    mTelephonyRegistryManager,
                    (trm) ->
                            trm.notifySatellitePurchaseModeChanged(
                                    subId, false, SATELLITE_PURCHASE_MODE_STATE_NETWORK_TEARDOWN));

            isEnabled = mQueueEnabled.poll(WAIT_TIME, TimeUnit.MILLISECONDS);
            purchaseModeState = mQueueState.poll(WAIT_TIME, TimeUnit.MILLISECONDS);

            assertNotNull("Timed out waiting for purchase mode change to false", isEnabled);
            assertFalse(isEnabled);

            assertNotNull(
                    "Timed out waiting for purchase mode state change to network teardown",
                    purchaseModeState);
            assertEquals(SATELLITE_PURCHASE_MODE_STATE_NETWORK_TEARDOWN, (int) purchaseModeState);

            // 4. Purchase Mode inactive
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    mTelephonyRegistryManager,
                    (trm) ->
                            trm.notifySatellitePurchaseModeChanged(
                                    subId, false, SATELLITE_PURCHASE_MODE_STATE_INACTIVE));

            isEnabled = mQueueEnabled.poll(WAIT_TIME, TimeUnit.MILLISECONDS);
            purchaseModeState = mQueueState.poll(WAIT_TIME, TimeUnit.MILLISECONDS);

            assertNotNull("Timed out waiting for purchase mode change to false", isEnabled);
            assertFalse(isEnabled);

            assertNotNull(
                    "Timed out waiting for purchase mode state change to inactive",
                    purchaseModeState);
            assertEquals(SATELLITE_PURCHASE_MODE_STATE_INACTIVE, (int) purchaseModeState);

        } finally {
            mTelephonyManager.unregisterTelephonyCallback(callback);
        }
    }
}
