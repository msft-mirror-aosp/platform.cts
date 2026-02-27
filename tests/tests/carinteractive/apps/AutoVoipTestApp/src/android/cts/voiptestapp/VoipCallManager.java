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
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.telecom.Connection;
import android.telecom.ConnectionService;
import android.telecom.PhoneAccount;
import android.telecom.TelecomManager;
import android.util.Log;

/** Manages the VoIP calls for the test app. */
public class VoipCallManager {

    private static final String TAG = "VTA.VoipCallManager";
    private static VoipCallManager sInstance;
    private ConnectionService mConnectionService;
    private TelecomManager mTelecomManager;
    private Context mContext;

    private boolean mServiceRegistered;

    private ServiceConnection mRingingCallConnection =
            new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {}

                @Override
                public void onServiceDisconnected(ComponentName name) {}
            };

    private VoipCallManager(Context context) {
        mContext = context;
        mTelecomManager = context.getSystemService(TelecomManager.class);
        mTelecomManager.registerPhoneAccount(createPhoneAccount());
    }

    /** Returns the singleton instance of this class */
    public static VoipCallManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new VoipCallManager(context);
        }
        return sInstance;
    }

    public void setConnectionService(ConnectionService connectionService) {
        mConnectionService = connectionService;
    }

    /** Instantiates a mocked outgoing call */
    public void placeOutgoingCall() {
        Log.i(TAG, "Placing Call");
        Bundle extras = new Bundle();
        extras.putParcelable(
                TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE,
                VoipConnectionService.PHONE_ACCOUNT_HANDLE);
        mTelecomManager.placeCall(VoipConnectionService.DEFAULT_ADDRESS, extras);
    }

    /** Instantiates a mocked incoming call */
    public void receiveIncomingCall() {
        // no-op
    }

    /** Disconnects all active calls */
    public void clearConnections() {
        if (mConnectionService == null) {
            return;
        }
        for (Connection connection : mConnectionService.getAllConnections()) {
            connection.onDisconnect();
        }
    }

    /** Declines any call(s) that are currently ringing. */
    public void declineRingingCall() {
        for (Connection connection : mConnectionService.getAllConnections()) {
            if (connection.getState() == Connection.STATE_RINGING) {
                connection.onReject();
            }
        }
    }

    /** Starts the notification service for ringing calls */
    public void startNotificationService() {
        Log.i(TAG, "Starting notification service");
        if (!mServiceRegistered) {
            mServiceRegistered =
                    mContext.getApplicationContext()
                            .bindService(
                                    new Intent(mContext, NotificationService.class),
                                    mRingingCallConnection,
                                    Context.BIND_AUTO_CREATE);
        }
    }

    /** Stops the notification service for ringing calls */
    public void stopNotificationService() {
        Log.i(TAG, "Stopping notification service");
        if (mServiceRegistered) {
            mContext.unbindService(mRingingCallConnection);
            mServiceRegistered = false;
        }
    }

    private PhoneAccount createPhoneAccount() {
        return PhoneAccount.builder(
                        VoipConnectionService.PHONE_ACCOUNT_HANDLE,
                        mContext.getString(R.string.phone_account_label))
                .addSupportedUriScheme(
                        mContext.getString(R.string.phone_account_supported_uri_scheme))
                .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
                .build();
    }
}
