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
package com.android.cts.nfc.multidevice.reader;

import static android.content.Context.RECEIVER_EXPORTED;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.cts.nfc.multidevice.emulator.service.OffHostService;
import com.android.cts.nfc.multidevice.emulator.service.PaymentService1;
import com.android.cts.nfc.multidevice.emulator.service.PaymentService2;
import com.android.cts.nfc.multidevice.emulator.service.PaymentServiceDynamicAids;
import com.android.cts.nfc.multidevice.emulator.service.PrefixPaymentService1;
import com.android.cts.nfc.multidevice.emulator.service.TransportService1;
import com.android.cts.nfc.multidevice.utils.CommandApdu;
import com.android.cts.nfc.multidevice.utils.HceUtils;
import com.android.cts.nfc.multidevice.utils.SnippetBroadcastReceiver;

import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.event.SnippetEvent;
import com.google.android.mobly.snippet.rpc.AsyncRpc;
import com.google.android.mobly.snippet.rpc.Rpc;

public class CtsNfcReaderDeviceSnippet implements Snippet {
    private BaseReaderActivity mActivity;
    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();

    /** Opens NFC reader for single non-payment test */
    @Rpc(description = "Open simple reader activity for single non-payment test")
    public void startSingleNonPaymentReaderActivity() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Intent intent =
                buildReaderIntentWithApduSequence(
                        instrumentation,
                        HceUtils.COMMAND_APDUS_BY_SERVICE.get(TransportService1.class.getName()),
                        HceUtils.RESPONSE_APDUS_BY_SERVICE.get(TransportService1.class.getName()));
        mActivity = (SimpleReaderActivity) instrumentation.startActivitySync(intent);
    }

    /** Open simple reader activity for single non-payment test */
    @Rpc(description = "Open simple reader activity for single non-payment test")
    public void startSinglePaymentReaderActivity() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Intent intent =
                buildReaderIntentWithApduSequence(
                        instrumentation,
                        HceUtils.COMMAND_APDUS_BY_SERVICE.get(PaymentService1.class.getName()),
                        HceUtils.RESPONSE_APDUS_BY_SERVICE.get(PaymentService1.class.getName()));
        mActivity = (SimpleReaderActivity) instrumentation.startActivitySync(intent);
    }

    /** Opens simple reader activity for dual payment services test */
    @Rpc(description = "Opens simple reader activity for dual payment services test")
    public void startDualPaymentReaderActivity() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Intent intent =
                buildReaderIntentWithApduSequence(
                        instrumentation,
                        HceUtils.COMMAND_APDUS_BY_SERVICE.get(PaymentService1.class.getName()),
                        HceUtils.RESPONSE_APDUS_BY_SERVICE.get(PaymentService1.class.getName()));
        mActivity = (SimpleReaderActivity) instrumentation.startActivitySync(intent);
    }

    /** Opens simple reader activity for foreground payment test */
    @Rpc(description = "Opens simple reader activity for foreground payment test")
    public void startForegroundPaymentReaderActivity() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Intent intent =
                buildReaderIntentWithApduSequence(
                        instrumentation,
                        HceUtils.COMMAND_APDUS_BY_SERVICE.get(PaymentService2.class.getName()),
                        HceUtils.RESPONSE_APDUS_BY_SERVICE.get(PaymentService2.class.getName()));
        mActivity = (SimpleReaderActivity) instrumentation.startActivitySync(intent);
    }

    /** Opens simple reader activity for dynamic AID test */
    @Rpc(description = "Opens simple reader activity for dynamic AID test")
    public void startDynamicAidReaderActivity() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Intent intent =
                buildReaderIntentWithApduSequence(
                        instrumentation,
                        HceUtils.COMMAND_APDUS_BY_SERVICE.get(
                                PaymentServiceDynamicAids.class.getName()),
                        HceUtils.RESPONSE_APDUS_BY_SERVICE.get(
                                PaymentServiceDynamicAids.class.getName()));
        mActivity = (SimpleReaderActivity) instrumentation.startActivitySync(intent);
    }

    /** Opens simple reader activity for prefix payment test */
    @Rpc(description = "Opens simple reader activity for prefix payment test")
    public void startPrefixPaymentReaderActivity() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Intent intent =
                buildReaderIntentWithApduSequence(
                        instrumentation,
                        HceUtils.COMMAND_APDUS_BY_SERVICE.get(
                                PrefixPaymentService1.class.getName()),
                        HceUtils.RESPONSE_APDUS_BY_SERVICE.get(
                                PrefixPaymentService1.class.getName()));
        mActivity = (SimpleReaderActivity) instrumentation.startActivitySync(intent);
    }

    /** Opens simple reader activity for prefix payment 2 test */
    @Rpc(description = "Opens simple reader activity for prefix payment 2 test")
    public void startPrefixPaymentReader2Activity() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Intent intent =
                buildReaderIntentWithApduSequence(
                        instrumentation,
                        HceUtils.COMMAND_APDUS_BY_SERVICE.get(
                                PrefixPaymentService1.class.getName()),
                        HceUtils.RESPONSE_APDUS_BY_SERVICE.get(
                                PrefixPaymentService1.class.getName()));
        mActivity = (SimpleReaderActivity) instrumentation.startActivitySync(intent);
    }

    /** Open simple reader activity for off host test. */
    @Rpc(description = "Open simple reader activity for off host test")
    public void startOffHostReaderActivity() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Intent intent =
                buildReaderIntentWithApduSequence(
                        instrumentation,
                        HceUtils.COMMAND_APDUS_BY_SERVICE.get(OffHostService.class.getName()),
                        HceUtils.RESPONSE_APDUS_BY_SERVICE.get(OffHostService.class.getName()));
        mActivity = (SimpleReaderActivity) instrumentation.startActivitySync(intent);
    }

    /** Open simple reader activity for on and off host test. */
    @Rpc(description = "Open simple reader activity for off host test")
    public void startOnAndOffHostReaderActivity() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        // Merge command/response APDU sequences.
        CommandApdu[] offHostCommands = HceUtils.COMMAND_APDUS_BY_SERVICE.get(
                OffHostService.class.getName());
        CommandApdu[] onHostCommands = HceUtils.COMMAND_APDUS_BY_SERVICE.get(
                TransportService1.class.getName());
        CommandApdu[] combinedCommands =
                new CommandApdu[offHostCommands.length + onHostCommands.length];
        System.arraycopy(offHostCommands, 0, combinedCommands, 0, offHostCommands.length);
        System.arraycopy(onHostCommands, 0, combinedCommands, offHostCommands.length,
                onHostCommands.length);

        String[] offHostResponses = HceUtils.RESPONSE_APDUS_BY_SERVICE.get(
                OffHostService.class.getName());
        String[] onHostResponses = HceUtils.RESPONSE_APDUS_BY_SERVICE.get(
                TransportService1.class.getName());
        String[] combinedResponses = new String[offHostResponses.length + onHostResponses.length];

        System.arraycopy(offHostResponses, 0, combinedResponses, 0, offHostResponses.length);
        System.arraycopy(onHostResponses, 0, combinedResponses, offHostResponses.length,
                onHostResponses.length);

        Intent intent =
                buildReaderIntentWithApduSequence(
                        instrumentation,
                        combinedCommands,
                        combinedResponses);
        mActivity = (SimpleReaderActivity) instrumentation.startActivitySync(intent);
    }

    /** Registers receiver for Test Pass event */
    @AsyncRpc(description = "Waits for Test Pass event")
    public void asyncWaitForTestPass(String callbackId, String eventName) {
        registerSnippetBroadcastReceiver(
                callbackId, eventName, BaseReaderActivity.ACTION_TEST_PASSED);
    }

    /** Closes reader activity between tests */
    @Rpc(description = "Close activity if one was opened.")
    public void closeActivity() {
        if (mActivity != null) {
            mActivity.finish();
        }
    }

    private Intent buildReaderIntentWithApduSequence(
            Instrumentation instrumentation, CommandApdu[] commandApdus, String[] responseApdus) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setClassName(
                instrumentation.getTargetContext(), SimpleReaderActivity.class.getName());
        intent.putExtra(SimpleReaderActivity.EXTRA_APDUS, commandApdus);
        intent.putExtra(SimpleReaderActivity.EXTRA_RESPONSES, responseApdus);
        return intent;
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
