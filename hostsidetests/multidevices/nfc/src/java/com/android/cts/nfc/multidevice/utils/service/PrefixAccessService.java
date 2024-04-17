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
package com.android.cts.nfc.multidevice.utils.service;

import android.content.ComponentName;

import com.android.cts.nfc.multidevice.utils.HceUtils;

public class PrefixAccessService extends HceService {
    protected static final String TAG = "PrefixAccessService";

    public static final ComponentName COMPONENT =
            new ComponentName(
                    "com.android.cts.nfc.multidevice.emulator",
                    PrefixAccessService.class.getName());

    public PrefixAccessService() {
        super(
                HceUtils.COMMAND_APDUS_BY_SERVICE.get(PrefixAccessService.class.getName()),
                HceUtils.RESPONSE_APDUS_BY_SERVICE.get(PrefixAccessService.class.getName()));
    }

    @Override
    public ComponentName getComponent() {
        return COMPONENT;
    }
}
