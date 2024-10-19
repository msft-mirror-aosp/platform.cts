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

package android.deviceconfig.cts;

import static com.android.aconfig.flags.Flags.FLAG_ENABLE_ONLY_NEW_STORAGE;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.configinfrastructure.aconfig.AconfigPackage;
import android.platform.test.annotations.DisabledOnRavenwood;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.flags.Flags;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@DisabledOnRavenwood(blockedBy = AconfigPackage.class)
public final class AconfigApiTest {
    @Rule
    public final CheckFlagsRule checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    @RequiresFlagsEnabled({Flags.FLAG_NEW_STORAGE_PUBLIC_API, FLAG_ENABLE_ONLY_NEW_STORAGE})
    public void testStorageReaderEnableInstance() {
        AconfigPackage reader = AconfigPackage.load("android.provider.flags");
        // return default as true if the flag doesn't exist on the device
        assertTrue(reader.getBooleanFlagValue("new_storage_public_api", true));
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_NEW_STORAGE_PUBLIC_API)
    public void testStorageReaderDisableInstance() {
        AconfigPackage reader = AconfigPackage.load("android.provider.flags");
        assertFalse(reader.getBooleanFlagValue("new_storage_public_api", true));
    }
}
