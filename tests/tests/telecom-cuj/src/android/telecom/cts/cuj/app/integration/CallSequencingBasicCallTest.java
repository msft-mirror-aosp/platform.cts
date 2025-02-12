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

package android.telecom.cts.cuj.app.integration;

import static android.telecom.Call.STATE_DIALING;
import static android.telecom.Call.STATE_DISCONNECTED;
import static android.telecom.Call.STATE_RINGING;
import static android.telecom.cts.apps.TelecomTestApp.ManagedConnectionServiceApp;
import static android.telecom.cts.apps.TelecomTestApp.ManagedConnectionServiceAppClone;

import android.platform.test.annotations.RequiresFlagsEnabled;
import android.telecom.CallAttributes;
import android.telecom.cts.apps.AppControlWrapper;
import android.telecom.cts.cuj.BaseAppVerifier;

import com.android.server.telecom.flags.Flags;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.List;

/** Basic call sequencing call tests */
@RunWith(JUnit4.class)
@RequiresFlagsEnabled({Flags.FLAG_ENABLE_CALL_SEQUENCING})
public class CallSequencingBasicCallTest extends BaseAppVerifier {
    /**
     * Verify that for the managed case that we disallow an incoming call to be received when
     * there's already another ringing (unanswered) call.
     */
    @Test
    public void testTwoRingingCallsFail() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        verifySecondRingingCallFailsHelper(true /* testFirstCallIncoming */);
    }

    /**
     * Verify that for the managed case that we disallow an incoming call to be received when
     * there's already a dialing call.
     */
    @Test
    public void testDialingAndRingingCallsFail() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        verifySecondRingingCallFailsHelper(false /* testFirstCallIncoming */);
    }

    private void verifySecondRingingCallFailsHelper(boolean testFirstCallIncoming)
            throws Exception {
        AppControlWrapper managedApp = null;
        AppControlWrapper managedAppClone = null;
        int expectedCallState = testFirstCallIncoming ? STATE_RINGING : STATE_DIALING;

        try {
            // Place either a dialing or ringing call based on testFirstCallIncoming
            managedApp = bindToApp(ManagedConnectionServiceApp);
            String call =
                    testFirstCallIncoming
                            ? addIncomingCallAndVerify(managedApp)
                            : addOutgoingCallAndVerify(managedApp);
            verifyCallIsInState(call, expectedCallState);

            // Verify that incoming call failed and we never attempted to create the connection.
            managedAppClone = bindToApp(ManagedConnectionServiceAppClone);
            CallAttributes defaultIncomingAttrs =
                    getDefaultAttributes(ManagedConnectionServiceAppClone, false);
            addFailedCallWithCreateConnectionVerify(managedAppClone, defaultIncomingAttrs);

            // Verify that the state of the first call is unchanged
            verifyCallIsInState(call, expectedCallState);
            setCallStateAndVerify(managedApp, call, STATE_DISCONNECTED);
        } finally {
            List<AppControlWrapper> controls = new ArrayList<>();
            controls.add(managedApp);
            controls.add(managedAppClone);
            tearDownApps(controls);
        }
    }
}
