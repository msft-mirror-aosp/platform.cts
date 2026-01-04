/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.cts.verifier.nfc;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.nfc.NfcAdapter;
import android.nfc.tech.MifareUltralight;
import android.nfc.tech.Ndef;
import android.nfc.tech.TagTechnology;
import android.os.Build;
import android.os.Bundle;

import com.android.cts.verifier.ArrayTestListAdapter;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;
import com.android.cts.verifier.TestListAdapter.TestListItem;
import com.android.cts.verifier.nfc.hce.HceEmulatorPollingLoopTestActivity;
import com.android.cts.verifier.nfc.hce.HceReaderPollingLoopTestActivity;
import com.android.cts.verifier.nfc.hcef.HceFEmulatorTestActivity;
import com.android.cts.verifier.nfc.hcef.HceFReaderTestActivity;
import com.android.cts.verifier.nfc.offhost.OffhostUiccEmulatorTestActivity;
import com.android.cts.verifier.nfc.offhost.OffhostUiccReaderTestActivity;

/** Activity that lists all the NFC tests. */
public class NfcTestActivity extends PassFailButtons.TestListActivity {

    private static final String NDEF_ID =
            TagVerifierActivity.getTagTestId(Ndef.class);

    private static final String MIFARE_ULTRALIGHT_ID =
            TagVerifierActivity.getTagTestId(MifareUltralight.class);

    private static final String FEATURE_NFC_MIFARE = "com.nxp.mifare";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.pass_fail_list);
        setInfoResources(R.string.nfc_test, R.string.nfc_test_info, 0);
        setPassFailButtonClickListeners();

        ArrayTestListAdapter adapter = new ArrayTestListAdapter(this);
        NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(this);

        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_NFC)) {
            adapter.add(TestListItem.newBuilder(this, R.string.nfc_tag_verification).build());
            adapter.add(
                    TestListItem.newBuilder(this, R.string.nfc_ndef)
                            .setTestName(NDEF_ID)
                            .setIntent(getTagIntent(Ndef.class))
                            .build());
            if (getPackageManager().hasSystemFeature(FEATURE_NFC_MIFARE)) {
                adapter.add(
                        TestListItem.newBuilder(this, R.string.nfc_mifare_ultralight)
                                .setTestName(MIFARE_ULTRALIGHT_ID)
                                .setIntent(getTagIntent(MifareUltralight.class))
                                .build());
            }
        }

        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)
                && Build.VERSION.SDK_INT > Build.VERSION_CODES.BAKLAVA
                && nfcAdapter != null
                && nfcAdapter.isObserveModeSupported()
                && nfcAdapter.isReaderModeAnnotationSupported()) {
            adapter.add(TestListItem.newBuilder(this, R.string.nfc_hce).build());
            if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_NFC)) {
                adapter.add(
                        TestListItem.newBuilder(
                                        this, R.string.nfc_hce_reader_polling_loop_annotations)
                                .setTestName(HceReaderPollingLoopTestActivity.class.getName())
                                .setIntent(new Intent(this, HceReaderPollingLoopTestActivity.class))
                                .build());
            }
            adapter.add(
                    TestListItem.newBuilder(
                                    this, R.string.nfc_hce_emulator_polling_loop_annotations)
                            .setTestName(HceEmulatorPollingLoopTestActivity.class.getName())
                            .setIntent(new Intent(this, HceEmulatorPollingLoopTestActivity.class))
                            .build());
        }

        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION_NFCF)) {
            adapter.add(TestListItem.newBuilder(this, R.string.nfc_hce_f).build());
            if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_NFC)) {
                adapter.add(
                        TestListItem.newBuilder(this, R.string.nfc_hce_f_reader_tests)
                                .setTestName(HceFReaderTestActivity.class.getName())
                                .setIntent(new Intent(this, HceFReaderTestActivity.class))
                                .build());
            }
            adapter.add(
                    TestListItem.newBuilder(this, R.string.nfc_hce_f_emulator_tests)
                            .setTestName(HceFEmulatorTestActivity.class.getName())
                            .setIntent(new Intent(this, HceFEmulatorTestActivity.class))
                            .build());
        }

        if (getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_NFC_OFF_HOST_CARD_EMULATION_UICC)) {
            adapter.add(TestListItem.newBuilder(this, R.string.nfc_offhost_uicc).build());
            if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_NFC)) {
                adapter.add(
                        TestListItem.newBuilder(this, R.string.nfc_offhost_uicc_reader_tests)
                                .setTestName(OffhostUiccReaderTestActivity.class.getName())
                                .setIntent(new Intent(this, OffhostUiccReaderTestActivity.class))
                                .build());
            }
            adapter.add(
                    TestListItem.newBuilder(this, R.string.nfc_offhost_uicc_emulator_tests)
                            .setTestName(OffhostUiccEmulatorTestActivity.class.getName())
                            .setIntent(new Intent(this, OffhostUiccEmulatorTestActivity.class))
                            .build());
        }

        setTestListAdapter(adapter);
    }

    private Intent getTagIntent(Class<? extends TagTechnology> primaryTech) {
        return new Intent(this, TagVerifierActivity.class)
                .putExtra(TagVerifierActivity.EXTRA_TECH, primaryTech.getName());
    }
}
