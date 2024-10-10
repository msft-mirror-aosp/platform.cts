/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.security.cts;

import android.compat.cts.CompatChangeGatingTestCase;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.util.CommandResult;

import com.google.common.collect.ImmutableSet;

public class ReadOnlyDynamicCodeLoadTest extends CompatChangeGatingTestCase {
    protected static final String TEST_APK = "CtsDynamicCodeLoadingApp.apk";
    protected static final String TEST_PKG = "android.security.cts";

    private static final long FLAG_READ_ONLY_DYNAMIC_CODE_LOAD = 354921003L;

    @Override
    protected void setUp() throws Exception {
        installPackage(TEST_APK, true);

    }

    public void testLoadReadOnly() throws Exception {
        runDeviceCompatTest(TEST_PKG, ".ReadOnlyDynamicCodeLoadingTest", "testLoadReadOnly",
                /* enabledChanges */ ImmutableSet.of(),
                /* disabledChanges */ ImmutableSet.of());
    }

    public void testLoadWritable_changeDisabled() throws Exception {
        // TODO(b/324474892): Remove this and replace it with flag annotations.
        if (checkFlagIsEnabled()) {
            runDeviceCompatTest(TEST_PKG, ".ReadOnlyDynamicCodeLoadingTest", "testLoadWritable",
                    /* enabledChanges */ ImmutableSet.of(),
                    /* disabledChanges */ ImmutableSet.of(FLAG_READ_ONLY_DYNAMIC_CODE_LOAD));
        }
    }

    public void testWritableLoad_withChangeEnabled_throws() throws Exception {
        // TODO(b/324474892): Remove this and replace it with flag annotations.
        if (checkFlagIsEnabled()) {
            runDeviceCompatTest(TEST_PKG, ".ReadOnlyDynamicCodeLoadingTest",
                    "testLoadWritable_expectException",
                    /* enabledChanges */ ImmutableSet.of(FLAG_READ_ONLY_DYNAMIC_CODE_LOAD),
                    /* disabledChanges */ ImmutableSet.of());
        }
    }

    private boolean checkFlagIsEnabled() throws DeviceNotAvailableException {
        CommandResult commandResult = getDevice().executeShellV2Command(
                "aflags list | grep com.android.libcore.read_only_dynamic_code_load");
        return commandResult.getStdout().contains("enabled");
    }

}
