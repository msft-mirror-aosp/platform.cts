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
 * limitations under the License
 */

package android.telecom.cts;

import static com.android.compatibility.common.util.ShellIdentityUtils
        .invokeMethodWithShellPermissionsNoReturn;
import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import android.app.AppOpsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.telecom.TelecomManager;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiTest;

import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class TelecomManagerTest extends BaseTelecomTestWithMockServices {

    private static final String TEST_EMERGENCY_NUMBER = "5553637";
    private static final Uri TEST_EMERGENCY_URI = Uri.fromParts("tel", TEST_EMERGENCY_NUMBER, null);
    private static final String CTS_TELECOM_PKG = TelecomManagerTest.class.getPackage().getName();

    public void testGetCurrentTtyMode() {
        if (!mShouldTestTelecom) {
            return;
        }

        LinkedBlockingQueue<Integer> queue = new LinkedBlockingQueue(1);
        runWithShellPermissionIdentity(() ->
                queue.put(mTelecomManager.getCurrentTtyMode()));
        try {
            int currentTtyMode = queue.poll(TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                    TimeUnit.MILLISECONDS);
            assertEquals(TelecomManager.TTY_MODE_OFF, currentTtyMode);
            assertFalse(TelecomManager.TTY_MODE_FULL == currentTtyMode);
            assertFalse(TelecomManager.TTY_MODE_HCO == currentTtyMode);
            assertFalse(TelecomManager.TTY_MODE_VCO == currentTtyMode);
        } catch (InterruptedException e) {
            fail("Couldn't get TTY mode.");
            e.printStackTrace();
        }
    }

    public void testHasManageOngoingCallsPermission() {
        if (!mShouldTestTelecom) {
            return;
        }
        AppOpsManager appOpsManager = mContext.getSystemService(AppOpsManager.class);
        PackageManager packageManager = mContext.getPackageManager();
        try {
            final int uid = packageManager.getApplicationInfo(CTS_TELECOM_PKG, 0).uid;
            invokeMethodWithShellPermissionsNoReturn(appOpsManager,
                    (appOpsMan) -> appOpsMan.setUidMode(AppOpsManager.OPSTR_MANAGE_ONGOING_CALLS,
                            uid, AppOpsManager.MODE_ALLOWED));
            assertTrue(mTelecomManager.hasManageOngoingCallsPermission());
            invokeMethodWithShellPermissionsNoReturn(appOpsManager,
                    (appOpsMan) -> appOpsMan.setUidMode(AppOpsManager.OPSTR_MANAGE_ONGOING_CALLS,
                            uid, AppOpsManager.opToDefaultMode(
                                    AppOpsManager.OPSTR_MANAGE_ONGOING_CALLS)));
            assertFalse(mTelecomManager.hasManageOngoingCallsPermission());
        } catch (PackageManager.NameNotFoundException ex) {
            fail("Couldn't get uid for android.telecom.cts");
        }
    }

    public void testTtyModeBroadcasts() {
        // We only expect the actual tty mode to change if there's a wired headset plugged in, so
        // don't do the test if there isn't one plugged in.
        if (!mShouldTestTelecom || !isWiredHeadsetPluggedIn()) {
            return;
        }
        LinkedBlockingQueue<Intent> ttyModeQueue = new LinkedBlockingQueue<>(1);
        BroadcastReceiver ttyModeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (TelecomManager.ACTION_CURRENT_TTY_MODE_CHANGED.equals(intent.getAction())) {
                    ttyModeQueue.offer(intent);
                }
            }
        };
        mContext.registerReceiver(ttyModeReceiver,
                new IntentFilter(TelecomManager.ACTION_CURRENT_TTY_MODE_CHANGED));
        Intent changePreferredTtyMode =
                new Intent(TelecomManager.ACTION_TTY_PREFERRED_MODE_CHANGED);
        changePreferredTtyMode.putExtra(TelecomManager.EXTRA_TTY_PREFERRED_MODE,
                TelecomManager.TTY_MODE_FULL);

        try {
            // Hold SHELL permission identity to ensure CTS tests have READ_PRIVILEGED_PHONE_STATE
            // during delivery of ACTION_CURRENT_TTY_MODE_CHANGED.
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .adoptShellPermissionIdentity();
            mContext.sendBroadcast(changePreferredTtyMode);
            Intent intent = ttyModeQueue.poll(
                    TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            assertTrue(intent.hasExtra(TelecomManager.EXTRA_CURRENT_TTY_MODE));
            assertEquals(TelecomManager.TTY_MODE_FULL,
                    intent.getIntExtra(TelecomManager.EXTRA_CURRENT_TTY_MODE, -1));
        } catch (InterruptedException e) {
            fail("interrupted");
        } finally {
            Intent revertPreferredTtyMode =
                    new Intent(TelecomManager.ACTION_TTY_PREFERRED_MODE_CHANGED);
            revertPreferredTtyMode.putExtra(TelecomManager.EXTRA_TTY_PREFERRED_MODE,
                    TelecomManager.TTY_MODE_OFF);
            mContext.sendBroadcast(revertPreferredTtyMode);
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    public void testIsInEmergencyCall_noOngoingEmergencyCall() {
        if (!mShouldTestTelecom) {
            return;
        }

        LinkedBlockingQueue<Boolean> queue = new LinkedBlockingQueue(1);
        runWithShellPermissionIdentity(() ->
                queue.put(mTelecomManager.isInEmergencyCall()));
        try {
            boolean isInEmergencyCall = queue.poll(TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                    TimeUnit.MILLISECONDS);
            assertFalse(isInEmergencyCall);
        } catch (InterruptedException e) {
            fail("Couldn't check if in emergency call.");
            e.printStackTrace();
        }
    }

    public void testIsInEmergencyCall_ongoingEmergencyCall() throws Exception {
        if (!mShouldTestTelecom || !TestUtils.hasTelephonyFeature(mContext)) {
            return;
        }

        // Place an emergency call
        setupConnectionService(null, 0);
        setupForEmergencyCalling(TEST_EMERGENCY_NUMBER);
        Bundle extras = new Bundle();
        extras.putParcelable(TestUtils.EXTRA_PHONE_NUMBER, TEST_EMERGENCY_URI);
        placeAndVerifyCall(extras);
        verifyConnectionForOutgoingCall();
        assertIsInCall(true);
        assertIsInManagedCall(true);
        try {
            TestUtils.waitOnAllHandlers(getInstrumentation());
        } catch (Exception e) {
            fail("Failed to wait on handlers " + e);
        }

        LinkedBlockingQueue<Boolean> queue = new LinkedBlockingQueue(1);
        runWithShellPermissionIdentity(() ->
                queue.put(mTelecomManager.isInEmergencyCall()));
        try {
            boolean isInEmergencyCall = queue.poll(TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                    TimeUnit.MILLISECONDS);
            assertTrue(isInEmergencyCall);
        } catch (InterruptedException e) {
            fail("Couldn't check if in emergency call.");
            e.printStackTrace();
        }
    }

    /**
     * Verifies that calling getVoipCallLogIntegrationStatus() without the required permission
     * throws a SecurityException.
     */
    @ApiTest(apis = {"android.telecom.TelecomManager#getVoipCallLogIntegrationStatus"})
    public void testGetVoipCallLogIntegrationStatus_NoPermission() {
        if (!mShouldTestTelecom || !android.telecom.flags.Flags.integratedCallLogsStage2()) {
            return;
        }
        try {
            mTelecomManager.getVoipCallLogIntegrationStatus();
            fail("getVoipCallLogIntegrationStatus should require READ_PRIVILEGED_PHONE_STATE");
        } catch (SecurityException e) {
            // Security exception should've been thrown at this point. Do nothing.
        }
    }

    /**
     * Verifies that calling setVoipCallLogIntegrationEnabled() without the required permission
     * throws a SecurityException.
     */
    @ApiTest(apis = {"android.telecom.TelecomManager#setVoipCallLogIntegrationEnabled"})
    public void testSetVoipCallLogIntegrationEnabled_NoPermission() {
        if (!mShouldTestTelecom || !android.telecom.flags.Flags.integratedCallLogsStage2()) {
            return;
        }
        try {
            mTelecomManager.setVoipCallLogIntegrationEnabled("testPkg", true);
            fail("setVoipCallLogIntegrationEnabled should require READ_PRIVILEGED_PHONE_STATE");
        } catch (SecurityException e) {
            // Security exception should've been thrown at this point. Do nothing.
        }
    }

    /**
     * Verifies that calling getVoipCallLogIntegrationStatus() with the required permission does
     * not throw an error.
     */
    @ApiTest(apis = {"android.telecom.TelecomManager#getVoipCallLogIntegrationStatus"})
    public void testGetVoipCallLogIntegrationStatus() {
        if (!mShouldTestTelecom || !android.telecom.flags.Flags.integratedCallLogsStage2()) {
            return;
        }
        try {
            // Hold SHELL permission identity to ensure CTS tests have READ_PRIVILEGED_PHONE_STATE.
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .adoptShellPermissionIdentity();
            // Verify that the getter doesn't throw an exception
            Map<String, Boolean> supportedPackages = mTelecomManager
                    .getVoipCallLogIntegrationStatus();
            // Since the CTS doesn't define the callback action, the list should be empty.
            assertTrue(supportedPackages.isEmpty());
        } catch (SecurityException e) {
            // Security exception should not be thrown at this point. Fail the test if it does.
            throw new AssertionError("Security exception should not have been thrown.", e);
        } finally {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    /**
     * Verifies that calling setVoipCallLogIntegrationEnabled() with the required permission does
     * not throw an error.
     */
    @ApiTest(apis = {"android.telecom.TelecomManager#setVoipCallLogIntegrationEnabled"})
    public void testSetVoipCallLogIntegrationEnabled() {
        if (!mShouldTestTelecom || !android.telecom.flags.Flags.integratedCallLogsStage2()) {
            return;
        }
        try {
            // Hold SHELL permission identity to ensure CTS tests have MODIFY_PHONE_STATE.
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .adoptShellPermissionIdentity();
            // No-op but verify that the setter doesn't throw an exception.
            mTelecomManager.setVoipCallLogIntegrationEnabled("testPkg", true);
        } catch (SecurityException e) {
            // Security exception should not be thrown at this point. Fail the test if it does.
            throw new AssertionError("Security exception should not have been thrown.", e);
        } catch (IllegalArgumentException e) {
            // Exception expected since the package hasn't registered the callback action.
        }finally {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    public void testEnforceConfigureCallLogPreferenceSettings() {
        if (!mShouldTestTelecom || !android.telecom.flags.Flags.integratedCallLogsStage2()) {
            return;
        }
        // Verify that there's an app that can handle the intent.
        Intent intent = new Intent(TelecomManager.ACTION_CONFIGURE_CALL_LOG_INTEGRATION);
        PackageManager pm = mContext.getPackageManager();
        assertNotNull(pm.resolveActivity(intent, PackageManager.MATCH_ALL));
    }

    private boolean isWiredHeadsetPluggedIn() {
        AudioManager audioManager = mContext.getSystemService(AudioManager.class);
        AudioDeviceInfo[] devices = audioManager.getDevices(AudioManager.GET_DEVICES_ALL);
        boolean isPluggedIn = false;
        for (AudioDeviceInfo device : devices) {
            switch (device.getType()) {
                case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
                case AudioDeviceInfo.TYPE_WIRED_HEADSET:
                case AudioDeviceInfo.TYPE_USB_HEADSET:
                case AudioDeviceInfo.TYPE_USB_DEVICE:
                    isPluggedIn = true;
            }
            if (isPluggedIn) {
                break;
            }
        }
        return isPluggedIn;
    }

}
