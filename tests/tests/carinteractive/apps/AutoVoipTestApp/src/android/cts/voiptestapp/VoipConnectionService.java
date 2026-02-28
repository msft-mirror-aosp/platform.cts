/*
 * Copyright 2026 The Android Open Source Project
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
package android.cts.voiptestapp;

import android.content.ComponentName;
import android.net.Uri;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.ConnectionService;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.util.Log;

import java.util.Random;

/** Call back for Telecom */
public class VoipConnectionService extends ConnectionService {

    private static final String TAG = "VTA.VoipConnectionService";
    private static final String PHONE_ACCOUNT_HANDLE_ID = "TEST_PHONE_ACCOUNT_HANDLE";
    private static final String PACKAGE_NAME = "android.cts.voiptestapp";
    private static final String PHONE_URI_SCHEME = "tel";
    private static final String PHONE_URI_SSP = "1234567890";
    public static final Uri DEFAULT_ADDRESS = Uri.fromParts(PHONE_URI_SCHEME, PHONE_URI_SSP, null);

    public static final PhoneAccountHandle PHONE_ACCOUNT_HANDLE =
            new PhoneAccountHandle(
                    new ComponentName(PACKAGE_NAME, PACKAGE_NAME + ".VoipConnectionService"),
                    PHONE_ACCOUNT_HANDLE_ID);

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "onCreate");
        VoipCallManager.getInstance(getApplicationContext()).setConnectionService(this);
    }

    @Override
    public Connection onCreateOutgoingConnection(
            PhoneAccountHandle connectionManagerPhoneAccount, ConnectionRequest request) {
        Log.i(TAG, "onCreateOutgoingConnection");
        VoipConnection ongoingConnection = new VoipConnection(getApplicationContext());
        ongoingConnection.setAddress(
                request.getAddress() == null ? DEFAULT_ADDRESS : request.getAddress(),
                TelecomManager.PRESENTATION_ALLOWED);
        startDialing(ongoingConnection);
        return ongoingConnection;
    }

    @Override
    public void onCreateOutgoingConnectionFailed(
            PhoneAccountHandle connectionManagerPhoneAccount, ConnectionRequest request) {
        Log.i(TAG, "onCreateOutgoingConnectionFailed");
    }

    @Override
    public Connection onCreateIncomingConnection(
            PhoneAccountHandle connectionManagerPhoneAccount, ConnectionRequest request) {
        // no-op
        return null;
    }

    @Override
    public void onCreateIncomingConnectionFailed(
            PhoneAccountHandle connectionManagerPhoneAccount, ConnectionRequest request) {
        // no-op
    }

    private void startDialing(VoipConnection ongoingConnection) {
        if (ongoingConnection != null && ongoingConnection.getState() == Connection.STATE_NEW) {
            ongoingConnection.setDialing();
            ongoingConnection.updateSpeakerAndParticipants(null, new Random().nextInt(5));
        }
        becomeActive(ongoingConnection);
    }

    private void becomeActive(VoipConnection ongoingConnection) {
        if (ongoingConnection != null && ongoingConnection.getState() == Connection.STATE_DIALING) {
            ongoingConnection.setActive();
            ongoingConnection.updateSpeakerAndParticipants(
                    getString(R.string.caller_name), new Random().nextInt(5));
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        VoipCallManager.getInstance(getApplicationContext()).setConnectionService(null);
    }
}
