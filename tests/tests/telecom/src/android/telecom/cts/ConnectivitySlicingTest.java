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

package android.telecom.cts;

import android.content.ComponentName;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.OutcomeReceiver;
import android.os.SystemClock;
import android.telecom.CallAttributes;
import android.telecom.CallControl;
import android.telecom.CallControlCallback;
import android.telecom.CallEndpoint;
import android.telecom.CallEventCallback;
import android.telecom.CallException;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.test.filters.SdkSuppress;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
public class ConnectivitySlicingTest extends BaseTelecomTestWithMockServices {

    // Note: These constants are re-defined here for early validation on Android 16 (Baklava).
    // They should be replaced with official SDK references once the Baklava API is public.
    private static final int CAPABILITY_OPT_OUT_OF_PREMIUM_NETWORK = 0x200000;

    private static final long WAIT_FOR_NETWORK_TIMEOUT_MS = 5000;
    private static final long POLLING_INTERVAL_MS = 200;
    private static final Uri TEST_ADDRESS = Uri.fromParts("sip", "call1@test.com", null);

    private PhoneAccountHandle mSlicingHandle;
    private CallControlCallback mCallControlCallback;
    private CallEventCallback mCallEventCallback;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        if (mShouldTestTelecom && isAtLeastBaklava()) {
            mCallControlCallback = new CallControlCallback() {
                @Override
                public void onSetActive(@NonNull Consumer<Boolean> wasCompleted) {
                    wasCompleted.accept(true);
                }
                @Override
                public void onSetInactive(@NonNull Consumer<Boolean> wasCompleted) {
                    wasCompleted.accept(true);
                }
                @Override
                public void onAnswer(int videoState, @NonNull Consumer<Boolean> wasCompleted) {
                    wasCompleted.accept(true);
                }
                @Override
                public void onDisconnect(@NonNull DisconnectCause cause, @NonNull Consumer<Boolean> wasCompleted) {
                    wasCompleted.accept(true);
                }
                @Override
                public void onCallStreamingStarted(@NonNull Consumer<Boolean> wasCompleted) {
                    wasCompleted.accept(true);
                }
            };

            mCallEventCallback = new CallEventCallback() {
                @Override
                public void onMuteStateChanged(boolean isMuted) {}
                @Override
                public void onEvent(String event, Bundle extras) {}
                @Override
                public void onCallStreamingFailed(int reason) {}
                @Override
                public void onCallEndpointChanged(CallEndpoint endpoint) {}
                @Override
                public void onAvailableCallEndpointsChanged(List<CallEndpoint> endpoints) {}
            };

            getInstrumentation()
                    .getUiAutomation()
                    .adoptShellPermissionIdentity(
                            "android.permission.ACCESS_NETWORK_STATE",
                            "android.permission.NETWORK_SETTINGS",
                            "android.permission.MODIFY_PHONE_STATE",
                            "android.permission.READ_PRIVILEGED_PHONE_STATE");

            setupConnectionService(null, FLAG_REGISTER | FLAG_ENABLE);

            mSlicingHandle =
                    new PhoneAccountHandle(
                            new ComponentName(mContext, ConnectivitySlicingTest.class),
                            "UfcSlicingTestAccount");

            PhoneAccount slicingAccount =
                    PhoneAccount.builder(mSlicingHandle, "UfcSlicingTestAccount")
                            .setCapabilities(
                                    PhoneAccount.CAPABILITY_SELF_MANAGED
                                            | PhoneAccount.CAPABILITY_SUPPORTS_TRANSACTIONAL_OPERATIONS
                                            | PhoneAccount.CAPABILITY_VIDEO_CALLING
                                            | PhoneAccount.CAPABILITY_SUPPORTS_VIDEO_CALLING)
                            .addSupportedUriScheme(PhoneAccount.SCHEME_SIP)
                            .build();

            mTelecomManager.registerPhoneAccount(slicingAccount);
        }
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            if (mShouldTestTelecom && isAtLeastBaklava()) {
                if (mSlicingHandle != null) {
                    mTelecomManager.unregisterPhoneAccount(mSlicingHandle);
                }
                getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
            }
        } finally {
            super.tearDown();
        }
    }

    private boolean isAtLeastBaklava() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA;
    }

    private boolean waitForCondition(Callable<Boolean> condition, long timeoutMs) throws Exception {
        long startTime = SystemClock.uptimeMillis();
        while (SystemClock.uptimeMillis() - startTime < timeoutMs) {
            if (condition.call()) {
                return true;
            }
            SystemClock.sleep(POLLING_INTERVAL_MS);
        }
        return false;
    }

    private CallControl placeAndVerifyTransactionalCall(PhoneAccountHandle handle)
            throws Exception {
        CallAttributes attributes = new CallAttributes.Builder(handle,
                CallAttributes.DIRECTION_OUTGOING, "UfcTestUser", TEST_ADDRESS)
                .setCallType(CallAttributes.VIDEO_CALL)
                .build();

        final CountDownLatch latch = new CountDownLatch(1);
        final CallControl[] result = new CallControl[1];

        mTelecomManager.addCall(attributes, mContext.getMainExecutor(),
                new OutcomeReceiver<CallControl, CallException>() {
                    @Override
                    public void onResult(CallControl callControl) {
                        result[0] = callControl;
                        latch.countDown();
                    }

                    @Override
                    public void onError(CallException exception) {
                        latch.countDown();
                    }
                }, mCallControlCallback, mCallEventCallback);

        if (!latch.await(WAIT_FOR_NETWORK_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            fail("Transactional call addition timed out");
        }
        assertNotNull("Failed to add transactional call", result[0]);

        final CountDownLatch activeLatch = new CountDownLatch(1);
        result[0].setActive(mContext.getMainExecutor(), new OutcomeReceiver<Void, CallException>() {
            @Override
            public void onResult(Void v) { activeLatch.countDown(); }
            @Override
            public void onError(CallException e) { activeLatch.countDown(); }
        });
        activeLatch.await(2000, TimeUnit.MILLISECONDS);

        return result[0];
    }

    /**
     * Checks if a network request with the PRIORITIZE_UNIFIED_COMMUNICATIONS capability
     * is active for the target UID.
     *
     * TODO (b/500544346): Explore the use of a @TestApi or ConnectivityManager callbacks
     * to verify the system state. Using dumpsys is currently necessary as standard APIs do not
     * expose the internal state of all active network requests across the system.
     */
    private boolean isUfcSliceRequestActive(String targetUid) throws Exception {
        String dumpsys =
                TestUtils.executeShellCommand(
                        getInstrumentation(), "dumpsys connectivity requests");
        String[] lines = dumpsys.split("\\R");
        for (String line : lines) {
            boolean hasUfcCapability =
                    line.contains("PRIORITIZE_UNIFIED_COMMUNICATIONS")
                            || line.matches(".*Capabilities:.*\\b38\\b.*");

            if (!hasUfcCapability) continue;

            String uidRegex = ".*\\b(Uid|asUid):\\s*" + targetUid + "\\b.*";
            String requestorUidRegex = ".*\\bRequestorUid:\\s*" + targetUid + "\\b.*";

            if (line.contains(" REQUEST ") && line.matches(uidRegex)) {
                return true;
            }
            if (line.contains(" LISTEN ")
                    && (line.matches(uidRegex) || line.matches(requestorUidRegex))) {
                return true;
            }
        }
        return false;
    }

    private void disconnectCall(CallControl callControl) throws Exception {
        if (callControl == null) return;
        final CountDownLatch latch = new CountDownLatch(1);
        callControl.disconnect(new DisconnectCause(DisconnectCause.LOCAL),
                mContext.getMainExecutor(), new OutcomeReceiver<Void, CallException>() {
                    @Override
                    public void onResult(Void result) { latch.countDown(); }
                    @Override
                    public void onError(CallException e) { latch.countDown(); }
                });
        latch.await(2000, TimeUnit.MILLISECONDS);
    }

    private void verifyUfcSliceRequest(PhoneAccountHandle handle, String errorMsg)
            throws Exception {
        CallControl callControl = null;
        try {
            callControl = placeAndVerifyTransactionalCall(handle);

            String uidString = String.valueOf(mContext.getApplicationInfo().uid);

            boolean requestFound =
                    waitForCondition(
                            () -> isUfcSliceRequestActive(uidString), WAIT_FOR_NETWORK_TIMEOUT_MS);

            assertTrue(errorMsg, requestFound);
        } finally {
            disconnectCall(callControl);
        }
    }

    public void testOttCallInitiatesUfcSliceRequestWithCorrectUid() throws Exception {
        if (!mShouldTestTelecom || !isAtLeastBaklava()) return;
        String uidString = String.valueOf(mContext.getApplicationInfo().uid);
        verifyUfcSliceRequest(
                mSlicingHandle,
                "UFC slice request (Bit 38) was not initiated for UID: " + uidString);
    }

    public void testAutomaticOptOutCompliance() throws Exception {
        if (!mShouldTestTelecom || !isAtLeastBaklava()) return;
        String uidString = String.valueOf(mContext.getApplicationInfo().uid);

        // 1. POSITIVE PHASE: Ensure slicing works for a standard account.
        // This ensures the manifest and system-level binding are operational.
        verifyUfcSliceRequest(
                mSlicingHandle,
                "System Error: UFC slice request failed for standard account. "
                        + "Check manifest/binding.");

        // 2. CLEANUP VERIFICATION: Ensure the request is removed before the next phase.
        boolean requestRemoved =
                waitForCondition(
                        () -> !isUfcSliceRequestActive(uidString), WAIT_FOR_NETWORK_TIMEOUT_MS);
        assertTrue("System Error: UFC slice request was not removed after call disconnect",
                requestRemoved);

        // 3. NEGATIVE PHASE: Ensure slicing is BLOCKED for an opt-out account.
        PhoneAccountHandle optOutHandle =
                new PhoneAccountHandle(
                        new ComponentName(mContext, ConnectivitySlicingTest.class),
                        "UfcSlicingTestAccountOptOut");

        PhoneAccount optOutAccount =
                PhoneAccount.builder(optOutHandle, "UfcSlicingTestAccountOptOut")
                        .setCapabilities(
                                PhoneAccount.CAPABILITY_SELF_MANAGED
                                        | PhoneAccount.CAPABILITY_SUPPORTS_TRANSACTIONAL_OPERATIONS
                                        | PhoneAccount.CAPABILITY_VIDEO_CALLING
                                        | PhoneAccount.CAPABILITY_SUPPORTS_VIDEO_CALLING
                                        | CAPABILITY_OPT_OUT_OF_PREMIUM_NETWORK)
                        .addSupportedUriScheme(PhoneAccount.SCHEME_SIP)
                        .build();
        mTelecomManager.registerPhoneAccount(optOutAccount);

        CallControl callControl = null;
        try {
            callControl = placeAndVerifyTransactionalCall(optOutHandle);

            // Verify no request is created for this UID
            boolean requestFound = isUfcSliceRequestActive(uidString);

            assertFalse(
                    "UFC slice request was initiated despite app opt-out capability", requestFound);

        } finally {
            mTelecomManager.unregisterPhoneAccount(optOutHandle);
            disconnectCall(callControl);
        }
    }
}
