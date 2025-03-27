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

import android.nfc.NfcAdapter;
import android.nfc.NfcAdapter.ReaderCallback;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.os.Bundle;
import android.util.Log;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import java.io.IOException;
import java.util.HexFormat;

public class HceReaderPollingLoopTestActivity extends PassFailButtons.Activity
        implements ReaderCallback {
    static final String ACTION_TEST_SUCCESS = "success";
    static final String TAG = "HceReaderPollingLoopTestActivity";
    private boolean mTestingAnnotation = true;
    private boolean mPassedAnnotateedTest = false;
    private boolean mPassedUnannotatedTest = false;

    NfcAdapter mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.pass_fail_text);
        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);
        mAdapter = NfcAdapter.getDefaultAdapter(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        setUpReaderMode();
    }

    private void setUpReaderMode() {
        mAdapter.disableReaderMode(this);
        Bundle extras = new Bundle();
        extras.putByteArray(
                NfcAdapter.EXTRA_READER_TECH_A_POLLING_LOOP_ANNOTATION,
                HexFormat.of().parseHex("DEADBEEF"));
        mAdapter.enableReaderMode(
                this,
                this,
                NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
                mTestingAnnotation ? extras : null);
    }

    @Override
    public void onTagDiscovered(Tag tag) {
        IsoDep isoDep = IsoDep.get(tag);
        if (isoDep == null) {
            return;
        }
        try {
            isoDep.connect();
            isoDep.setTimeout(5000);
            CommandApdu selectCommand = HceUtils.buildSelectApdu("F00506070A", true);
            byte[] command = HexFormat.of().parseHex(selectCommand.getApdu());
            byte[] resp = isoDep.transceive(command);
            if (resp.length != 1) {
                return;
            }
            isoDep.close();
            if (mTestingAnnotation) {
                if (resp[0] == 0x01) {
                    mPassedAnnotateedTest = true;
                    mTestingAnnotation = false;
                }
            } else {
                if (resp[0] == 0x02) {
                    mPassedUnannotatedTest = true;
                    mTestingAnnotation = true;
                }
            }
            setUpReaderMode();

            if (mPassedUnannotatedTest && mPassedAnnotateedTest) {
                runOnUiThread(
                        new Runnable() {
                            @Override
                            public void run() {
                                getPassButton().setEnabled(true);
                            }
                        });
            }
        } catch (IOException ioe) {
            Log.i(TAG, "IO exception", ioe);
        }
    }
}
