/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.cts.nfc.multidevice.emulator;

import android.app.Activity;
import android.app.role.RoleManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NfcAdapter;
import android.nfc.cardemulation.CardEmulation;
import android.os.Bundle;
import android.util.Log;

import com.android.cts.nfc.multidevice.emulator.service.OffHostService;
import com.android.cts.nfc.multidevice.emulator.service.PaymentService1;
import com.android.cts.nfc.multidevice.emulator.service.PaymentService2;
import com.android.cts.nfc.multidevice.emulator.service.PaymentServiceDynamicAids;
import com.android.cts.nfc.multidevice.emulator.service.PrefixPaymentService1;
import com.android.cts.nfc.multidevice.emulator.service.PrefixPaymentService2;
import com.android.cts.nfc.multidevice.emulator.service.TransportService1;
import com.android.cts.nfc.multidevice.utils.HceUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class BaseEmulatorActivity extends Activity {

    // Intent action that's received when complete APDU sequence is received from an HCE service.
    public static final String ACTION_APDU_SEQUENCE_COMPLETE =
            "com.android.cts.nfc.multidevice.emulator.ACTION_APDU_SEQUENCE_COMPLETE";
    public static final String ACTION_ROLE_HELD =
            "com.android.cts.nfc.multidevice.emulator.ACTION_ROLE_HELD";
    public static final String EXTRA_COMPONENT = "component";
    public static final String EXTRA_DURATION = "duration";

    // Intent action that's sent after the test condition is met.
    protected static final String ACTION_TEST_PASSED =
            "com.android.cts.nfc.multidevice.emulator.ACTION_TEST_PASSED";

    protected static final ArrayList<ComponentName> SERVICES =
            new ArrayList<ComponentName>(
                    List.of(
                            TransportService1.COMPONENT, PaymentService1.COMPONENT,
                            PaymentService2.COMPONENT, PaymentServiceDynamicAids.COMPONENT,
                            PrefixPaymentService1.COMPONENT, PrefixPaymentService2.COMPONENT,
                            OffHostService.COMPONENT));

    protected static final String TAG = "BaseEmulatorActivity";
    protected NfcAdapter mAdapter;
    protected CardEmulation mCardEmulation;
    protected RoleManager mRoleManager;

    final BroadcastReceiver mReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (ACTION_APDU_SEQUENCE_COMPLETE.equals(action)) {
                        // Get component whose sequence was completed
                        ComponentName component = intent.getParcelableExtra(EXTRA_COMPONENT);
                        long duration = intent.getLongExtra(EXTRA_DURATION, 0);
                        if (component != null) {
                            onApduSequenceComplete(component, duration);
                        }
                    }
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mAdapter = NfcAdapter.getDefaultAdapter(this);
        mCardEmulation = CardEmulation.getInstance(mAdapter);
        mRoleManager = getSystemService(RoleManager.class);
        IntentFilter filter = new IntentFilter(ACTION_APDU_SEQUENCE_COMPLETE);
        registerReceiver(mReceiver, filter, RECEIVER_EXPORTED);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
    }

    /** Sets up HCE services for this emulator */
    public void setupServices(ComponentName... components) {
        List<ComponentName> enableComponents = Arrays.asList(components);
        for (ComponentName component : SERVICES) {
            if (enableComponents.contains(component)) {
                Log.d(TAG, "Enabling component " + component);
                HceUtils.enableComponent(getPackageManager(), component);
            } else {
                Log.d(TAG, "Disabling component " + component);
                HceUtils.disableComponent(getPackageManager(), component);
            }
        }
        ComponentName bogusComponent =
                new ComponentName(
                        "com.android.cts.nfc.multidevice.emulator",
                        "com.android.cts.nfc.multidevice.emulator.BogusService");
        mCardEmulation.isDefaultServiceForCategory(bogusComponent, CardEmulation.CATEGORY_PAYMENT);

        onServicesSetup();
    }

    /** Executed after services are set up */
    protected void onServicesSetup() {}

    /** Executed after successful APDU sequence received */
    protected void onApduSequenceComplete(ComponentName component, long duration) {}

    /** Call this in child classes when test condition is met */
    protected void setTestPassed() {
        Intent intent = new Intent(ACTION_TEST_PASSED);
        sendBroadcast(intent);
    }

    /** Makes this package the default wallet role holder */
    public void makeDefaultWalletRoleHolder() {
        if (!isWalletRoleHeld()) {
            Log.d(TAG, "Wallet Role not currently held. Setting default role now");
            setDefaultWalletRole();
        } else {
            Intent intent = new Intent(ACTION_ROLE_HELD);
            sendBroadcast(intent);
        }
    }

    protected boolean isWalletRoleHeld() {
        assert mRoleManager != null;
        return mRoleManager.isRoleHeld(RoleManager.ROLE_WALLET);
    }

    protected void setDefaultWalletRole() {
        if (HceUtils.setDefaultWalletRoleHolder(this, "com.android.cts.nfc.multidevice.emulator")) {
            Log.d(TAG, "Default role holder set: " + isWalletRoleHeld());
            Intent intent = new Intent(ACTION_ROLE_HELD);
            sendBroadcast(intent);
        }
    }
}
