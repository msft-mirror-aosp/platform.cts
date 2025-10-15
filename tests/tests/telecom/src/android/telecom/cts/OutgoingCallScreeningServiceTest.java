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

package android.telecom.cts;

import static android.telecom.cts.TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS;
import static android.telecom.cts.TestUtils.shouldTestTelecom;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.platform.test.flag.junit.SetFlagsRule;
import android.telecom.Call;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telecom.cts.screeningtestapp.CallScreeningServiceControl;
import android.telecom.cts.screeningtestapp.ICallScreeningControl;
import android.telecom.flags.Flags;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@AppModeFull(reason = "Need to control test CallScreeningService app")
@RunWith(AndroidJUnit4.class)
public class OutgoingCallScreeningServiceTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final String TEST_PKG = "android.telecom.cts.screeningtestapp";
    private static final String CSS_CLASS = "/.CtsCallScreeningService";
    private static final String TEST_OEM_CSS_COMPONENT = TEST_PKG + CSS_CLASS;
    private static final Uri TEST_NUMBER = Uri.fromParts(PhoneAccount.SCHEME_TEL, "5551212", null);

    private static final ComponentName MOCK_CONNECTION_SERVICE_COMPONENT =
            new ComponentName("android.telecom.cts", MockConnectionService.class.getName());
    private static final PhoneAccountHandle TEST_PHONE_ACCOUNT_HANDLE =
            new PhoneAccountHandle(MOCK_CONNECTION_SERVICE_COMPONENT, "cts-test-account-id");

    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    private Context mContext;
    private TelecomManager mTelecomManager;
    private String mPreviousDefaultDialer;
    private MyInCallServiceCallbacks mInCallCallbacks;
    private ICallScreeningControl mScreeningControl;
    private ServiceConnection mControlConnection;

    static class MyInCallServiceCallbacks extends MockInCallService.InCallServiceCallbacks {
        private final Semaphore mCallAddedLock = new Semaphore(0);
        private final Semaphore mCallStateLock = new Semaphore(0);
        private Call mAddedCall;

        @Override
        public void onCallAdded(Call call, int numCalls) {
            mAddedCall = call;
            mCallAddedLock.release();
        }

        @Override
        public void onCallStateChanged(Call call, int state) {
            mCallStateLock.release();
        }

        Call waitForCallAdded(long timeoutMs) throws InterruptedException {
            if (!mCallAddedLock.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS)) {
                fail("Timeout waiting for onCallAdded in MockInCallService");
            }
            return mAddedCall;
        }

        boolean waitForCallState(Call call, int expectedState, long timeoutMs)
                throws InterruptedException {
            long startTime = System.currentTimeMillis();
            while (System.currentTimeMillis() - startTime < timeoutMs) {
                if (call != null && call.getState() == expectedState) return true;
                mCallStateLock.tryAcquire(200, TimeUnit.MILLISECONDS);
            }
            return call != null && call.getState() == expectedState;
        }

        void assertCallNotAdded() throws InterruptedException {
            if (mCallAddedLock.tryAcquire(2, TimeUnit.SECONDS)) {
                fail("A call was added to InCallService, but it should have been blocked.");
            }
        }
    }

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mTelecomManager = mContext.getSystemService(TelecomManager.class);
        assumeTrue(shouldTestTelecom(mContext));

        registerTestPhoneAccount();
        enableTestPhoneAccount(true);

        SystemUtil.runWithShellPermissionIdentity(
                () ->
                        mTelecomManager.setUserSelectedOutgoingPhoneAccount(
                                TEST_PHONE_ACCOUNT_HANDLE));

        mPreviousDefaultDialer =
                TestUtils.getDefaultDialer(InstrumentationRegistry.getInstrumentation());
        mInCallCallbacks = new MyInCallServiceCallbacks();
        MockInCallService.setCallbacks(mInCallCallbacks);
        TestUtils.setDefaultDialer(InstrumentationRegistry.getInstrumentation(), TestUtils.PACKAGE);

        setupControlInterface();
        mScreeningControl.reset();
    }

    @After
    public void tearDown() throws Exception {
        if (mTelecomManager != null) {
            ComponentName screeningServiceComponent =
                    ComponentName.unflattenFromString(TEST_OEM_CSS_COMPONENT);
            setComponentEnabled(screeningServiceComponent, true);
            SystemUtil.runWithShellPermissionIdentity(
                    () -> mTelecomManager.setUserSelectedOutgoingPhoneAccount(null));

            setCallScreeningService("default");
            if (mScreeningControl != null) {
                try {
                    mScreeningControl.reset();
                } catch (RemoteException e) {
                    throw new RuntimeException(e);
                }
            }
            if (mControlConnection != null) {
                mContext.unbindService(mControlConnection);
            }
            if (mInCallCallbacks != null) {
                MockInCallService service = mInCallCallbacks.getService();
                if (service != null && service.getCallCount() > 0) {
                    service.disconnectAllCalls();
                }
                MockInCallService.setCallbacks(null);
            }

            if (mPreviousDefaultDialer != null) {
                TestUtils.setDefaultDialer(
                        InstrumentationRegistry.getInstrumentation(), mPreviousDefaultDialer);
            }
            unregisterTestPhoneAccount();
        }
    }

    private void setupControlInterface() throws Exception {
        Intent intent = new Intent(CallScreeningServiceControl.CONTROL_INTERFACE_ACTION);
        intent.setComponent(CallScreeningServiceControl.CONTROL_INTERFACE_COMPONENT);
        CountDownLatch latch = new CountDownLatch(1);
        mControlConnection =
                new ServiceConnection() {
                    @Override
                    public void onServiceConnected(ComponentName name, IBinder service) {
                        mScreeningControl = ICallScreeningControl.Stub.asInterface(service);
                        latch.countDown();
                    }

                    @Override
                    public void onServiceDisconnected(ComponentName name) {
                        mScreeningControl = null;
                    }
                };
        mContext.bindService(intent, mControlConnection, Context.BIND_AUTO_CREATE);
        if (!latch.await(WAIT_FOR_STATE_CHANGE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            fail("Failed to bind to CallScreeningServiceControl");
        }
    }

    private void setCallScreeningService(String componentName) {
        final String command = "cmd telecom set-oem-call-screening-service " + componentName;
        try {
            SystemUtil.runShellCommand(InstrumentationRegistry.getInstrumentation(), command);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void registerTestPhoneAccount() {
        PhoneAccount phoneAccount =
                PhoneAccount.builder(TEST_PHONE_ACCOUNT_HANDLE, "CTS Test Account")
                        .setCapabilities(
                                PhoneAccount.CAPABILITY_CALL_PROVIDER
                                        | PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION)
                        .build();
        SystemUtil.runWithShellPermissionIdentity(
                () -> mTelecomManager.registerPhoneAccount(phoneAccount));
    }

    private void unregisterTestPhoneAccount() {
        SystemUtil.runWithShellPermissionIdentity(
                () -> mTelecomManager.unregisterPhoneAccount(TEST_PHONE_ACCOUNT_HANDLE));
    }

    private void enableTestPhoneAccount(boolean enable) {
        final String componentName = TEST_PHONE_ACCOUNT_HANDLE.getComponentName().flattenToString();
        final String command =
                "telecom set-phone-account-enabled " + componentName + " " + (enable ? "1" : "0");
        try {
            SystemUtil.runShellCommand(InstrumentationRegistry.getInstrumentation(), command);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_OEM_OUTGOING_CALL_SCREENING)
    public void testOutgoingScreeningBlocked() throws Exception {
        setCallScreeningService(TEST_OEM_CSS_COMPONENT);
        mScreeningControl.setShouldBlockOutgoingCall(true);

        mInCallCallbacks.mCallAddedLock.drainPermits();
        Bundle extras = new Bundle();
        extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, TEST_PHONE_ACCOUNT_HANDLE);
        mTelecomManager.placeCall(TEST_NUMBER, extras);

        String handle =
                mScreeningControl.waitForOutgoingCallScreened(WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);
        assertNotNull("Screening service was not called or timed out", handle);
        assertEquals(TEST_NUMBER.toString(), handle);

        mInCallCallbacks.assertCallNotAdded();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_OEM_OUTGOING_CALL_SCREENING)
    public void testOutgoingScreeningAllowed() throws Exception {
        setCallScreeningService(TEST_OEM_CSS_COMPONENT);
        mScreeningControl.setShouldBlockOutgoingCall(false);

        mInCallCallbacks.mCallAddedLock.drainPermits();
        Bundle extras = new Bundle();
        extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, TEST_PHONE_ACCOUNT_HANDLE);
        mTelecomManager.placeCall(TEST_NUMBER, extras);

        String handle =
                mScreeningControl.waitForOutgoingCallScreened(WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);
        assertNotNull("Screening service was not called or timed out", handle);
        assertEquals(TEST_NUMBER.toString(), handle);

        Call call = mInCallCallbacks.waitForCallAdded(WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);
        assertNotNull("Call was not added to InCallService", call);

        assertFalse(
                "Call should NOT be disconnected",
                mInCallCallbacks.waitForCallState(call, Call.STATE_DISCONNECTED, 3000));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_OEM_OUTGOING_CALL_SCREENING)
    public void testOutgoingScreeningNoResponse_callGoesThrough() throws Exception {
        setCallScreeningService(TEST_OEM_CSS_COMPONENT);
        mScreeningControl.setShouldNoResponseOutgoingCall(true);
        mInCallCallbacks.mCallAddedLock.drainPermits();
        Bundle extras = new Bundle();
        extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, TEST_PHONE_ACCOUNT_HANDLE);
        mTelecomManager.placeCall(TEST_NUMBER, extras);

        String handle =
                mScreeningControl.waitForOutgoingCallScreened(WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);
        assertNotNull("Screening service was not called or timed out", handle);
        assertEquals(TEST_NUMBER.toString(), handle);
        Call call = mInCallCallbacks.waitForCallAdded(WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);
        assertNotNull("Call was not added to InCallService", call);
        assertFalse(
                "Call should NOT be disconnected",
                mInCallCallbacks.waitForCallState(call, Call.STATE_DISCONNECTED, 3000));
    }

    @Test
    public void testNoOemCssOverride_callGoesThrough() throws Exception {
        setCallScreeningService("default");
        ComponentName screeningServiceComponent =
                ComponentName.unflattenFromString(TEST_OEM_CSS_COMPONENT);
        setComponentEnabled(screeningServiceComponent, false);

        // Place an outgoing call.
        mInCallCallbacks.mCallAddedLock.drainPermits();
        Bundle extras = new Bundle();
        extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, TEST_PHONE_ACCOUNT_HANDLE);
        mTelecomManager.placeCall(TEST_NUMBER, extras);

        String handle =
                mScreeningControl.waitForOutgoingCallScreened(1000); // Use a shorter timeout
        assertNull(
                "Screening service should NOT have been called when override is absent, "
                        + "but a handle was returned.",
                handle);

        Call call = mInCallCallbacks.waitForCallAdded(WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);
        assertNotNull("Call was not added to InCallService", call);

        assertFalse(
                "Call should NOT be disconnected",
                mInCallCallbacks.waitForCallState(call, Call.STATE_DISCONNECTED, 3000));
    }

    private void setComponentEnabled(ComponentName componentName, boolean enabled) {
        int state =
                enabled
                        ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    mContext.getPackageManager()
                            .setComponentEnabledSetting(
                                    componentName, state, PackageManager.DONT_KILL_APP);
                });
    }
}
