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

package android.cts.tagging.is_mte_policy_enforced;

import android.app.admin.flags.Flags;

import static com.google.common.truth.Truth.assertThat;

import android.app.admin.DevicePolicyManager;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.Rule;
import org.junit.runner.RunWith;

// This is driven by MemtagBootctlTest from the host, which enables / disables
// MTE, reboots the device, and then calls testMteIsEnabled / testMteIsDisabled
// to check whether this API returns the correct state.
@RunWith(AndroidJUnit4.class)
@SmallTest
public final class TaggingTest {
    @Test
    public void testMtePolicyEnforcedTrue() {
        if (Flags.isMtePolicyEnforced()) {
            assertThat(DevicePolicyManager.isMtePolicyEnforced()).isTrue();
        }
    }
    @Test
    public void testMtePolicyEnforcedFalse() {
        if (Flags.isMtePolicyEnforced()) {
            assertThat(DevicePolicyManager.isMtePolicyEnforced()).isFalse();
        }
    }
}