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
package com.android.cts.verifier.nfc.hce;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NfcAdapter;
import android.nfc.cardemulation.CardEmulation;
import android.os.Bundle;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

public class HceEmulatorPollingLoopTestActivity extends PassFailButtons.TestListActivity {
    static final String TAG = "HceEmulatorPollingLoopTestActivity";

    final BroadcastReceiver mFieldStateReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (action.equals(PollingLoopService.POLLING_FRAME_ACTION)) {
                        runOnUiThread(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        getPassButton().setEnabled(true);
                                    }
                                });
                    }
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.pass_fail_list);
        setInfoResources(R.string.nfc_test, R.string.nfc_hce_emulator_test_info, 0);
        setPassFailButtonClickListeners();
        IntentFilter filter = new IntentFilter(PollingLoopService.POLLING_FRAME_ACTION);
        registerReceiver(mFieldStateReceiver, filter, RECEIVER_EXPORTED);
        CardEmulation cardemulation = CardEmulation.getInstance(NfcAdapter.getDefaultAdapter(this));
        cardemulation.setPreferredService(this, new ComponentName(this, PollingLoopService.class));
        setObserveMode(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        CardEmulation cardemulation = CardEmulation.getInstance(NfcAdapter.getDefaultAdapter(this));
        cardemulation.setPreferredService(this, new ComponentName(this, PollingLoopService.class));
        setObserveMode(true);
    }

    void setObserveMode(boolean enabled) {
        NfcAdapter.getDefaultAdapter(this).setObserveModeEnabled(enabled);
    }
}
