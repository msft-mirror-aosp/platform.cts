/*
 * Copyright (C) 2014 The Android Open Source Project
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

import static android.content.Context.RECEIVER_EXPORTED;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.cts.nfc.multidevice.utils.SnippetBroadcastReceiver;

import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.event.SnippetEvent;
import com.google.android.mobly.snippet.rpc.AsyncRpc;
import com.google.android.mobly.snippet.rpc.Rpc;

public class CtsNfcEmulatorDeviceSnippet implements Snippet {
    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();

    /** Opens single Non Payment emulator */
    @Rpc(description = "Open single non payment emulator activity")
    public void startSingleNonPaymentEmulatorActivity() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setClassName(
                instrumentation.getTargetContext(),
                SingleNonPaymentEmulatorActivity.class.getName());

        instrumentation.startActivitySync(intent);
    }

    /** Registers receiver for Test Pass event */
    @AsyncRpc(description = "Waits for Test Pass event")
    public void asyncWaitForTestPass(String callbackId, String eventName) {
        registerSnippetBroadcastReceiver(
                callbackId, eventName, BaseEmulatorActivity.ACTION_TEST_PASSED);
    }

    /** Creates a SnippetBroadcastReceiver that listens for when the specified action is received */
    private void registerSnippetBroadcastReceiver(
            String callbackId, String eventName, String action) {
        IntentFilter filter = new IntentFilter(action);
        mContext.registerReceiver(
                new SnippetBroadcastReceiver(
                        mContext, new SnippetEvent(callbackId, eventName), action),
                filter,
                RECEIVER_EXPORTED);
    }
}
