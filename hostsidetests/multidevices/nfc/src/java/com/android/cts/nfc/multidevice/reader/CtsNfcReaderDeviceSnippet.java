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
package com.android.cts.nfc.multidevice.reader;

import android.app.Instrumentation;
import android.content.Intent;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.cts.nfc.multidevice.emulator.service.TransportService1;
import com.android.cts.nfc.multidevice.utils.CommandApdu;
import com.android.cts.nfc.multidevice.utils.HceUtils;

import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.rpc.Rpc;

public class CtsNfcReaderDeviceSnippet implements Snippet {

    /** Opens NFC reader for single non-payment test */
    @Rpc(description = "Open simple reader activity for single non-payment test")
    public void startSingleNonPaymentReaderActivity() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Intent intent =
                buildReaderIntentWithApduSequence(
                        instrumentation,
                        HceUtils.COMMAND_APDUS_BY_SERVICE.get(TransportService1.class.getName()),
                        HceUtils.RESPONSE_APDUS_BY_SERVICE.get(TransportService1.class.getName()));
        instrumentation.startActivitySync(intent);
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
}
