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

package android.nativeservice.test;

import android.platform.test.annotations.RequiresFlagsEnabled;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled({
    com.android.server.am.Flags.FLAG_ENABLE_ACTIVITY_MANAGER_STRUCTURED_SERVICE,
    android.os.Flags.FLAG_NATIVE_FRAMEWORK_PROTOTYPE
})
public final class NativeServiceTest extends NativeServiceTestBase {
    public NativeServiceTest() {
        mUseAppZygote = false;
        mNativeServiceClassName = "android.nativeservice.test.NativeService";
    }
}
