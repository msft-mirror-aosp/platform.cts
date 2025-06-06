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

import static com.android.compatibility.common.util.ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn;
import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import android.os.Vibrator;
import android.os.Vibrator.OnVibratorStateChangedListener;
import android.telecom.TelecomManager;

import androidx.test.InstrumentationRegistry;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Verifies Telecom behavior with regards to call connected indicator. */
public class CallConnectedIndicatorTest extends BaseTelecomTestWithMockServices {

    private Vibrator mSystemVibrator;
    private OnVibratorStateChangedListener mOnVibratorStateChangedListener =
            new OnVibratorStateChangedListener() {
                @Override
                public void onVibratorStateChanged(boolean isVibrating) {
                    if (isVibrating) mLatch.countDown();
                }
            };
    private CountDownLatch mLatch = new CountDownLatch(1);
    private int mOldPrefs;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        if (mShouldTestTelecom) {
            setupConnectionService(null, FLAG_REGISTER | FLAG_ENABLE);
            mSystemVibrator =
                    InstrumentationRegistry.getInstrumentation()
                            .getContext()
                            .getSystemService(Vibrator.class);
            // Skip test on devices with no vibrator.
            if (!hasVibrator()) return;
            invokeMethodWithShellPermissionsNoReturn(
                    mSystemVibrator,
                    (sv) -> sv.addVibratorStateListener(mOnVibratorStateChangedListener));
            invokeMethodWithShellPermissionsNoReturn(
                    mTelecomManager, (tm) -> mOldPrefs = tm.getCallConnectedIndicatorPreference());
        }
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        if (mShouldTestTelecom) {
            // Skip test on devices with no vibrator.
            if (!hasVibrator()) return;

            invokeMethodWithShellPermissionsNoReturn(
                    mSystemVibrator,
                    (sv) -> sv.removeVibratorStateListener(mOnVibratorStateChangedListener));
            invokeMethodWithShellPermissionsNoReturn(
                    mTelecomManager, (tm) -> tm.setCallConnectedIndicatorPreference(mOldPrefs));
        }
    }

    public void testCallConnectedIndicatorPreference() throws Exception {
        if (!mShouldTestTelecom) return;
        if (!hasVibrator()) return;
        if (!isCallConnectedFeatureEnabled()) return;

        runWithShellPermissionIdentity(
                () -> {
                    mTelecomManager.setCallConnectedIndicatorPreference(
                            TelecomManager.CALL_CONNECTED_INDICATOR_VIBRATION);
                    assertEquals(
                            TelecomManager.CALL_CONNECTED_INDICATOR_VIBRATION,
                            mTelecomManager.getCallConnectedIndicatorPreference());

                    mTelecomManager.setCallConnectedIndicatorPreference(
                            TelecomManager.CALL_CONNECTED_INDICATOR_TONE);
                    assertEquals(
                            TelecomManager.CALL_CONNECTED_INDICATOR_TONE,
                            mTelecomManager.getCallConnectedIndicatorPreference());

                    mTelecomManager.setCallConnectedIndicatorPreference(
                            TelecomManager.CALL_CONNECTED_INDICATOR_VIBRATION
                                    | TelecomManager.CALL_CONNECTED_INDICATOR_TONE);
                    assertEquals(
                            TelecomManager.CALL_CONNECTED_INDICATOR_TONE
                                    | TelecomManager.CALL_CONNECTED_INDICATOR_VIBRATION,
                            mTelecomManager.getCallConnectedIndicatorPreference());
                });
    }

    public void testVibratingForMoCallConnected() throws Exception {
        if (!mShouldTestTelecom) return;
        if (!hasVibrator()) return;
        if (!isCallConnectedFeatureEnabled()) return;

        invokeMethodWithShellPermissionsNoReturn(
                mTelecomManager,
                (tm) ->
                        tm.setCallConnectedIndicatorPreference(
                                TelecomManager.CALL_CONNECTED_INDICATOR_VIBRATION));

        placeAndVerifyCall();
        MockConnection conn = verifyConnectionForOutgoingCall();
        conn.setActive();
        assertTrue(
                mLatch.await(
                        TestUtils.WAIT_FOR_PHONE_STATE_LISTENER_REGISTERED_TIMEOUT_S,
                        TimeUnit.SECONDS));

        cleanupCalls();
        assertCtsConnectionServiceUnbound();
        CtsConnectionService.tearDown();
        setupConnectionService(null, FLAG_REGISTER | FLAG_ENABLE);
        mLatch = new CountDownLatch(1);
        invokeMethodWithShellPermissionsNoReturn(
                mTelecomManager,
                (tm) ->
                        tm.setCallConnectedIndicatorPreference(
                                TelecomManager.CALL_CONNECTED_INDICATOR_NONE));
        placeAndVerifyCall(null);
        conn = verifyConnectionForOutgoingCall();
        conn.setActive();

        assertFalse(
                mLatch.await(
                        TestUtils.WAIT_FOR_PHONE_STATE_LISTENER_REGISTERED_TIMEOUT_S,
                        TimeUnit.SECONDS));
    }

    /**
     * Determines if the feature flag is on or not; Telecom CTS do not use JUnit4 so cannot use the
     * RequiresFlagsEnabled annotation.
     *
     * @return {@code true} if feature enabled.
     */
    private boolean isCallConnectedFeatureEnabled() {
        return new com.android.server.telecom.flags.FeatureFlagsImpl()
                .callConnectedIndicatorPreference();
    }

    /**
     * Determines if the device has a vibrator.
     * @return {@code true} if device has vibrator.
     */
    private boolean hasVibrator() {
        return mSystemVibrator.hasVibrator();
    }
}
