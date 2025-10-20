/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.cts.deviceowner;

import static android.bluetooth.BluetoothAdapter.ACTION_BLE_STATE_CHANGED;
import static android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED;
import static android.bluetooth.BluetoothAdapter.EXTRA_STATE;
import static android.bluetooth.BluetoothAdapter.STATE_BLE_ON;
import static android.bluetooth.BluetoothAdapter.STATE_OFF;
import static android.bluetooth.BluetoothAdapter.STATE_ON;
import static android.bluetooth.BluetoothAdapter.STATE_TURNING_OFF;
import static android.bluetooth.BluetoothAdapter.STATE_TURNING_ON;
import static android.bluetooth.BluetoothAdapter.nameForState;
import static android.content.pm.PackageManager.FEATURE_BLUETOOTH;

import static androidx.test.espresso.intent.matcher.IntentMatchers.hasAction;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasExtra;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.timeout;

import android.app.admin.DevicePolicyManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.SystemClock;
import android.os.UserManager;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bedstead.dpmwrapper.TestAppSystemServiceFactory;

import org.hamcrest.Matcher;
import org.hamcrest.core.AllOf;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.hamcrest.MockitoHamcrest;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.time.Duration;

/**
 * Test interaction between {@link UserManager#DISALLOW_BLUETOOTH} user restriction and the state of
 * Bluetooth.
 */
@RunWith(AndroidJUnit4.class)
public class BluetoothRestrictionTest {
    @Rule public final MockitoRule mMockitoRule = MockitoJUnit.rule();

    @Mock private BroadcastReceiver mLeReceiver;
    @Mock private BroadcastReceiver mReceiver;

    private static final String TAG = BluetoothRestrictionTest.class.getSimpleName();
    private static final boolean VERBOSE = false;

    private static final Duration DISABLE_TIMEOUT = Duration.ofSeconds(8);
    private static final Duration ENABLE_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration CHECK_WAIT_TIME = Duration.ofSeconds(1);

    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();
    private final DevicePolicyManager mDevicePolicyManager =
            TestAppSystemServiceFactory.getDevicePolicyManager(
                    mContext, BasicAdminReceiver.class, /* forDeviceOwner= */ true);

    private int mState;
    private BluetoothAdapter mAdapter;
    private InOrder mInOrder;

    @Before
    public void setUp() throws Exception {
        assumeTrue(mContext.getPackageManager().hasSystemFeature(FEATURE_BLUETOOTH));
        mInOrder = inOrder(mLeReceiver, mReceiver);

        doAnswer(
                        invocation -> {
                            Intent intent = (Intent) invocation.getArgument(1);
                            mState = intent.getIntExtra(EXTRA_STATE, STATE_OFF);
                            return null;
                        })
                .when(mLeReceiver)
                .onReceive(any(), any());

        mContext.registerReceiver(mLeReceiver, new IntentFilter(ACTION_BLE_STATE_CHANGED));
        mContext.registerReceiver(mReceiver, new IntentFilter(ACTION_STATE_CHANGED));
        mAdapter = mContext.getSystemService(BluetoothManager.class).getAdapter();
        mState = mAdapter.getState();

        if (mState == STATE_OFF && mAdapter.isLeEnabled()) {
            // BLE_ON mode is valid state that can be reach by having privileged apps requesting it.
            // This mode is not overriding user restriction, but can be reach as soon as the
            // restriction is removed.
            mState = STATE_BLE_ON;
        }

        Log.d(TAG, "BluetoothAdapter=" + mAdapter + " state=" + nameForState(mState));
    }

    @After
    public void tearDown() throws Exception {
        clearBluetoothRestriction();
        if (mState != STATE_ON) {
            enableBluetooth();
            enforceBluetoothEnablingSteps();
        }
        mContext.unregisterReceiver(mLeReceiver);
        mContext.unregisterReceiver(mReceiver);
    }

    @Test
    public void enableBluetoothFailsWhenDisallowed() throws Exception {
        boolean disableClassic = mState == STATE_ON;
        boolean disableLe = disableClassic || mState == STATE_BLE_ON;

        // Make sure Bluetooth is initially disabled.
        if (disableClassic) {
            disableBluetooth();
            enforceBluetoothDisablingSteps();
        }
        assertThat(mAdapter.getState()).isEqualTo(STATE_OFF);
        assertThat(mAdapter.isEnabled()).isFalse();

        // Add the user restriction disallowing Bluetooth.
        addBluetoothRestriction();

        if (disableLe) {
            verifyLeIntentReceived(
                    DISABLE_TIMEOUT,
                    hasAction(ACTION_BLE_STATE_CHANGED),
                    hasExtra(EXTRA_STATE, STATE_OFF));
        }

        enforceBluetoothIsOff();

        // Check that enabling Bluetooth fails.
        assertThat(mAdapter.enable()).isFalse();

        enforceBluetoothIsOff();
    }

    @Test
    public void bluetoothGetsDisabledAfterRestrictionSet() throws Exception {
        // Make sure Bluetooth is enabled first.
        if (mState != STATE_ON) {
            clearBluetoothRestriction();
            enableBluetooth();
            enforceBluetoothEnablingSteps();
        }
        assertThat(mAdapter.getState()).isEqualTo(STATE_ON);
        assertThat(mAdapter.isEnabled()).isTrue();

        // Add the user restriction to disallow Bluetooth.
        addBluetoothRestriction();

        // Check that Bluetooth gets disabled as a result.
        enforceBluetoothDisablingSteps();
        enforceBluetoothLeDisablingSteps();

        enforceBluetoothIsOff();
    }

    @Test
    public void enableBluetoothSucceedsAfterRestrictionRemoved() throws Exception {
        boolean disableClassic = mState == STATE_ON;
        boolean disableLe = disableClassic || mState == STATE_BLE_ON;
        // Add the user restriction.
        addBluetoothRestriction();

        if (disableClassic) {
            enforceBluetoothDisablingSteps();
        }
        if (disableLe) {
            enforceBluetoothLeDisablingSteps();
        }

        enforceBluetoothIsOff();

        // Remove the user restriction.
        clearBluetoothRestriction();

        // Check that it is possible to enable Bluetooth again once the restriction has been removed
        enableBluetooth();
        enforceBluetoothEnablingSteps();
    }

    private void enforceBluetoothIsOff() {
        // Validate the state broadcasted in the intent
        assertThat(mState).isEqualTo(STATE_OFF);

        // Validate API return value
        assertThat(mAdapter.getState()).isEqualTo(STATE_OFF);
        assertThat(mAdapter.isEnabled()).isFalse();
        assertThat(mAdapter.isLeEnabled()).isFalse();

        // Validate the state is stable and not a transition
        sleep(CHECK_WAIT_TIME);
        mInOrder.verifyNoMoreInteractions();
    }

    /** Initiate a shutdown of classic Bluetooth but does not wait. Doesn't impact BLE_ON state. */
    private void disableBluetooth() {
        Log.i(TAG, "Disabling Bluetooth");
        assertThat(mAdapter.disable()).isTrue();
    }

    private void enforceBluetoothDisablingSteps() {
        verifyLeIntentReceived(DISABLE_TIMEOUT, hasExtra(EXTRA_STATE, STATE_TURNING_OFF));
        verifyIntentReceived(DISABLE_TIMEOUT, hasExtra(EXTRA_STATE, STATE_TURNING_OFF));
        verifyIntentReceived(DISABLE_TIMEOUT, hasExtra(EXTRA_STATE, STATE_OFF));
    }

    private void enforceBluetoothLeDisablingSteps() {
        verifyLeIntentReceived(DISABLE_TIMEOUT, hasExtra(EXTRA_STATE, STATE_OFF));
    }

    /** Initiate a start of classic Bluetooth but does not wait. */
    private void enableBluetooth() {
        Log.i(TAG, "Enabling Bluetooth");
        assertThat(mAdapter.enable()).isTrue();
    }

    private void enforceBluetoothEnablingSteps() {
        verifyLeIntentReceived(ENABLE_TIMEOUT, hasExtra(EXTRA_STATE, STATE_TURNING_ON));
        verifyIntentReceived(ENABLE_TIMEOUT, hasExtra(EXTRA_STATE, STATE_TURNING_ON));
        verifyLeIntentReceived(ENABLE_TIMEOUT, hasExtra(EXTRA_STATE, STATE_ON));
        verifyIntentReceived(ENABLE_TIMEOUT, hasExtra(EXTRA_STATE, STATE_ON));
    }

    @SafeVarargs
    private void verifyLeIntentReceived(Duration delay, Matcher<Intent>... matchers) {
        mInOrder.verify(mLeReceiver, timeout(delay.toMillis()))
                .onReceive(any(Context.class), MockitoHamcrest.argThat(AllOf.allOf(matchers)));
    }

    @SafeVarargs
    private void verifyIntentReceived(Duration delay, Matcher<Intent>... matchers) {
        mInOrder.verify(mReceiver, timeout(delay.toMillis()))
                .onReceive(any(Context.class), MockitoHamcrest.argThat(AllOf.allOf(matchers)));
    }

    private void addBluetoothRestriction() {
        addUserRestriction(UserManager.DISALLOW_BLUETOOTH);
        sleep(CHECK_WAIT_TIME); // Wait for restriction propagation
    }

    private void clearBluetoothRestriction() {
        clearUserRestriction(UserManager.DISALLOW_BLUETOOTH);
        sleep(CHECK_WAIT_TIME); // Wait for restriction propagation
    }

    private void addUserRestriction(String restriction) {
        Log.d(TAG, "Adding " + restriction + " using " + mDevicePolicyManager);
        mDevicePolicyManager.addUserRestriction(getAdmin(), restriction);
    }

    private void clearUserRestriction(String restriction) {
        Log.d(TAG, "Clearing " + restriction + " using " + mDevicePolicyManager);
        mDevicePolicyManager.clearUserRestriction(getAdmin(), restriction);
    }

    private ComponentName getAdmin() {
        return BasicAdminReceiver.getComponentName(mContext);
    }

    private static void sleep(Duration delay) {
        if (VERBOSE) {
            Log.v(TAG, "Sleeping for " + delay);
        }
        SystemClock.sleep(delay.toMillis());
    }
}
