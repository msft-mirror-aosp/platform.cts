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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.test_utils.BlockingBluetoothAdapter;
import android.content.Context;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresDevice;
import android.util.Log;
import android.util.SparseArray;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.multiuser.annotations.RequireRunNotOnVisibleBackgroundNonProfileUser;
import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.FeatureUtil;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Contains the tests to prove compliance with android automotive specific bluetooth requirements.
 */
@RequireRunNotOnVisibleBackgroundNonProfileUser(reason = "No Bluetooth support on visible"
            + " background users currently, so skipping tests for"
            + " secondary_user_on_secondary_display.")
@SmallTest
@RequiresDevice
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "Instant Apps cannot get Bluetooth related permissions")
public final class CarBluetoothTest extends AbstractCarTestCase {
    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final String TAG = CarBluetoothTest.class.getSimpleName();
    private static final boolean DBG = false;

    private Context mContext;

    private final BluetoothAdapter mBluetoothAdapter = BlockingBluetoothAdapter.getAdapter();

    // Utility class to hold profile information and state
    private static class ProfileInfo {
        final String mName;
        boolean mConnected;

        public ProfileInfo(String name) {
            mName = name;
            mConnected = false;
        }
    }

    // Automotive required profiles and meta data. Profile defaults to 'not connected' and name
    // is used in debug and error messages
    private static SparseArray<ProfileInfo> sRequiredBluetoothProfiles = new SparseArray();
    static {
        sRequiredBluetoothProfiles.put(11,
                new ProfileInfo("A2DP Sink")); // BluetoothProfile.A2DP_SINK
        sRequiredBluetoothProfiles.put(16,
                new ProfileInfo("HSP Client")); // BluetoothProfile.HEADSET_CLIENT
        sRequiredBluetoothProfiles.put(17,
                new ProfileInfo("PBAP Client")); // BluetoothProfile.PBAP_CLIENT
    }
    private static final int MAX_PROFILES_SUPPORTED = sRequiredBluetoothProfiles.size();

    // Configurable timeout for waiting for profile proxies to connect
    private static final int PROXY_CONNECTIONS_TIMEOUT_MS = 1000; // ms

    // Objects to block until all profile proxy connections have finished, or the timeout occurs
    private Condition mConditionAllProfilesConnected;
    private ReentrantLock mProfileConnectedLock;
    private int mProfilesSupported;

    // Capture profile proxy connection events
    private final class ProfileServiceListener implements BluetoothProfile.ServiceListener {
        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            if (DBG) {
                Log.d(TAG, "Profile '" + profile + "' has connected");
            }
            mProfileConnectedLock.lock();
            try {
                sRequiredBluetoothProfiles.get(profile).mConnected = true;
                mProfilesSupported++;
                if (mProfilesSupported == MAX_PROFILES_SUPPORTED) {
                    mConditionAllProfilesConnected.signal();
                }
            } finally {
                mProfileConnectedLock.unlock();
            }
        }

        @Override
        public void onServiceDisconnected(int profile) {
            if (DBG) {
                Log.d(TAG, "Profile '" + profile + "' has disconnected");
            }
            mProfileConnectedLock.lock();
            try {
                sRequiredBluetoothProfiles.get(profile).mConnected = false;
                mProfilesSupported--;
            } finally {
                mProfileConnectedLock.unlock();
            }
        }
    }

    // Initiate connections to all profiles and wait until we connect to all, or time out
    private void waitForProfileConnections() {
        if (DBG) {
            Log.d(TAG, "Starting profile proxy connections...");
        }
        mProfileConnectedLock.lock();
        try {
            // Attempt connection to each required profile
            for (int i = 0; i < sRequiredBluetoothProfiles.size(); i++) {
                int profile = sRequiredBluetoothProfiles.keyAt(i);
                mBluetoothAdapter.getProfileProxy(mContext, new ProfileServiceListener(), profile);
            }

            // Wait for the Adapter to be disabled
            while (mProfilesSupported != MAX_PROFILES_SUPPORTED) {
                if (!mConditionAllProfilesConnected.await(
                    PROXY_CONNECTIONS_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                    Log.e(TAG, "Timeout while waiting for Profile Connections");
                    break;
                }
            }
        } catch (InterruptedException e) {
            Log.w(TAG, "waitForProfileConnections: interrupted", e);
        } finally {
            mProfileConnectedLock.unlock();
        }

        if (DBG) {
            Log.d(TAG, "Proxy connection attempts complete. Connected " + mProfilesSupported
                    + "/" + MAX_PROFILES_SUPPORTED + " profiles");
        }
    }

    // Check and make sure each profile is connected. If any are not supported then build an
    // error string to report each missing profile and assert a failure
    private void checkProfileConnections() {
        if (DBG) {
            Log.d(TAG, "Checking for all required profiles");
        }
        mProfileConnectedLock.lock();
        try {
            if (mProfilesSupported != MAX_PROFILES_SUPPORTED) {
                if (DBG) {
                    Log.d(TAG, "Some profiles failed to connect");
                }
                StringBuilder e = new StringBuilder();
                for (int i = 0; i < sRequiredBluetoothProfiles.size(); i++) {
                    int profile = sRequiredBluetoothProfiles.keyAt(i);
                    String name = sRequiredBluetoothProfiles.get(profile).mName;
                    if (!sRequiredBluetoothProfiles.get(profile).mConnected) {
                        if (e.length() == 0) {
                            e.append("Missing Profiles: ");
                        } else {
                            e.append(", ");
                        }
                        e.append(name + " (" + profile + ")");

                        if (DBG) {
                            Log.d(TAG, name + " failed to connect");
                        }
                    }
                }
                fail(e.toString());
            }
        } finally {
            mProfileConnectedLock.unlock();
        }
    }

    // Set the connection status for each profile to false
    private void clearProfileStatuses() {
        if (DBG) {
            Log.d(TAG, "Setting all profiles to 'disconnected'");
        }
        for (int i = 0; i < sRequiredBluetoothProfiles.size(); i++) {
            int profile = sRequiredBluetoothProfiles.keyAt(i);
            sRequiredBluetoothProfiles.get(profile).mConnected = false;
        }
    }

    @Before
    public void setUp() throws Exception {
        if (DBG) {
            Log.d(TAG, "Setting up Automotive Bluetooth test. Device is "
                    + (FeatureUtil.isAutomotive() ? "" : "not ") + "automotive");
        }

        // Get the context
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();

        // Initialize all the profile connection variables
        mProfilesSupported = 0;
        mProfileConnectedLock = new ReentrantLock();
        mConditionAllProfilesConnected = mProfileConnectedLock.newCondition();
        clearProfileStatuses();

        // Make sure Bluetooth is enabled before the test
        assertThat(BlockingBluetoothAdapter.enable()).isTrue();
    }

    // [A-0-2] : Android Automotive devices must support the following Bluetooth profiles:
    //  * Hands Free Profile (HFP) [Phone calling]
    //  * Audio Distribution Profile (A2DP) [Media playback]
    //  * Audio/Video Remote Control Profile (AVRCP) [Media playback control]
    //  * Phone Book Access Profile (PBAP) [Contact sharing/receiving]
    //
    // This test fires off connections to each required profile (which are asynchronous in nature)
    // and waits for all of them to connect (proving they are there and implemented), or for the
    // configured timeout. If all required profiles connect, the test passes.
    @Test
    @CddTest(requirements = {"7.4.3/A-0-2"})
    public void testRequiredBluetoothProfilesExist() throws Exception {
        if (DBG) {
            Log.d(TAG, "Begin testRequiredBluetoothProfilesExist()");
        }
        assertNotNull(mBluetoothAdapter);
        waitForProfileConnections();
        checkProfileConnections();
    }
}
