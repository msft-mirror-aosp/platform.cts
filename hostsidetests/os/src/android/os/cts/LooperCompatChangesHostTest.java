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

package android.os.cts;

import android.compat.cts.CompatChangeGatingTestCase;

import com.google.common.collect.ImmutableSet;

/** Host side of compat test for android.os.Looper. */
public class LooperCompatChangesHostTest extends CompatChangeGatingTestCase {

    protected static final String TEST_APK = "CtsHostLooperCompatTestApp.apk";
    protected static final String TEST_PKG = "android.os.loopercompattests";

    private static final long LOOPER_CLEARS_THREAD_INTERRUPTED = 458413887L;

    @Override
    protected void setUp() throws Exception {
        installPackage(TEST_APK, true);
    }

    @Override
    protected void tearDown() throws Exception {
        uninstallPackage(TEST_PKG, true);
    }

    public void testLooperClearsThreadInterruptedEnabled() throws Exception {
        runDeviceCompatTest(
                TEST_PKG,
                ".LooperCompatChangesTest",
                "checkLooperClearedInterrupts",
                /*enabledChanges*/ ImmutableSet.of(LOOPER_CLEARS_THREAD_INTERRUPTED),
                /*disabledChanges*/ ImmutableSet.of());
    }

    public void testLooperClearsThreadInterruptedDisabled() throws Exception {
        runDeviceCompatTest(
                TEST_PKG,
                ".LooperCompatChangesTest",
                "checkLooperRetainedInterrupts",
                /*enabledChanges*/ ImmutableSet.of(),
                /*disabledChanges*/ ImmutableSet.of(LOOPER_CLEARS_THREAD_INTERRUPTED));
    }
}
