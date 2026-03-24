/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.server.cts.device.statsdatom;

import android.os.PersistableBundle;
import android.service.carrier.CarrierIdentifier;
import android.service.carrier.CarrierService;
import android.telephony.CarrierConfigManager;
import android.util.Log;

public class TestCarrierService extends CarrierService {
    private static final String TAG = "TestCarrierService";

    @Override
    public PersistableBundle onLoadConfig(CarrierIdentifier id) {
        Log.d(TAG, "onLoadConfig called");
        PersistableBundle config = new PersistableBundle();
        // Return a mock certificate array to trigger the atom
        // We use a dummy hex string for the cert hash, but a real package name so that getPackageUidForAnyUser succeeds.
        String dummyCert = "0000000000000000000000000000000000000000000000000000000000000000";
        config.putStringArray(
                CarrierConfigManager.KEY_CARRIER_CERTIFICATE_STRING_ARRAY,
                new String[] {dummyCert + ":" + getPackageName()}
        );
        return config;
    }
}
