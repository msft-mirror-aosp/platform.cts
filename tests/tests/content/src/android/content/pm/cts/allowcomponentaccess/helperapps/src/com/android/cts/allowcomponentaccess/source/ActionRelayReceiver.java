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

package com.android.cts.allowcomponentaccess.source;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.cts.allowcomponentaccess.Constants;
import android.os.Bundle;
import android.os.IBinder;

import java.util.UUID;

/**
 * A relay component running in the Source app.
 *
 * <p>It listens for a command from the Test Runner and relays that action to the Target app. This
 * ensures the connection attempt originates from the restricted Source app, subjecting it to the OS
 * security checks.
 */
public class ActionRelayReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getStringExtra(Constants.TEST_ACTION);
        String targetPkg = intent.getStringExtra(Constants.TARGET_PKG);

        if (action == null) action = Constants.ACTION_TYPE_BIND;

        if (Constants.ACTION_TYPE_BIND.equals(action)) {
            relayBind(
                    context.getApplicationContext(),
                    intent,
                    targetPkg,
                    /* checkPolicyOnly= */ false);
        } else if (Constants.ACTION_TYPE_BROADCAST.equals(action)) {
            relayBroadcast(context.getApplicationContext(), intent, targetPkg);
        } else if (Constants.ACTION_TYPE_CHECK_POLICY_ONLY.equals(action)) {
            relayBind(
                    context.getApplicationContext(),
                    intent,
                    targetPkg,
                    /* checkPolicyOnly= */ true);
        }
    }

    private void relayBind(
            Context context, Intent triggerIntent, String targetPkg, boolean checkPolicyOnly) {
        Intent serviceIntent = new Intent();
        serviceIntent.setClassName(targetPkg, Constants.TARGET_SERVICE_CLASS);

        // Use a unique ID to ensure the OS treats this as a fresh bind attempt
        // (preventing result caching).
        serviceIntent.setAction(UUID.randomUUID().toString());

        Bundle extras = triggerIntent.getExtras();
        if (extras != null && !checkPolicyOnly) {
            serviceIntent.putExtras(extras);
        }

        ServiceConnection conn =
                new ServiceConnection() {
                    @Override
                    public void onServiceConnected(ComponentName name, IBinder service) {}

                    @Override
                    public void onServiceDisconnected(ComponentName name) {}
                };
        boolean amsAllowed = false;
        try {
            context.bindService(serviceIntent, conn, Context.BIND_AUTO_CREATE);
            amsAllowed = true;
        } catch (SecurityException e) {
            // Expected behavior if the access is blocked by the OS
        }
        // If this is just a policy check, and AMS allowed it, fire the test callback.
        if (checkPolicyOnly && amsAllowed && extras != null) {
            IBinder binder = extras.getBinder(Constants.CALLBACK_BINDER);
            if (binder != null) {
                android.content.pm.cts.allowcomponentaccess.ITestCallback callback =
                        android.content.pm.cts.allowcomponentaccess.ITestCallback.Stub.asInterface(
                                binder);
                try {
                    callback.onActionReceived();
                } catch (android.os.RemoteException e) {
                    // ignore
                }
            }
        }
    }

    private void relayBroadcast(Context context, Intent triggerIntent, String targetPkg) {
        Intent broadcastIntent = new Intent(Constants.ACTION_PING);
        broadcastIntent.setComponent(new ComponentName(targetPkg, Constants.TARGET_RECEIVER_CLASS));

        broadcastIntent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);

        Bundle extras = triggerIntent.getExtras();
        if (extras != null) {
            broadcastIntent.putExtras(extras);
        }

        try {
            context.sendBroadcast(broadcastIntent);
        } catch (SecurityException e) {
            // Expected behavior if the access is blocked by the OS
        }
    }
}
