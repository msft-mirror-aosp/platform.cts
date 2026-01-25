/*
 * Copyright 2026 The Android Open Source Project
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

package android.service.personalcontext.cts.hint;

import static com.google.common.truth.Truth.assertThat;

import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.service.personalcontext.Flags;
import android.service.personalcontext.hint.BundleHint;
import android.service.personalcontext.hint.ContextHint;
import android.service.personalcontext.hint.HintInvalidationHint;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Build/Install/Run: atest CtsPersonalContextTestCases:HintInvalidationHintTest */
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
@RunWith(AndroidJUnit4.class)
public class HintInvalidationHintTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @ApiTest(
            apis = {
                "android.service.personalcontext.hint.HintInvalidationHint#getInvalidatedHintId",
                "android.service.personalcontext.hint.HintInvalidationHint.Builder#build",
            })
    @Test
    public void testHintInvalidationHintId() {
        final BundleHint hint = new BundleHint.Builder().build();
        final HintInvalidationHint invalidationHint =
                (HintInvalidationHint)
                        bundleUnbundle(new HintInvalidationHint.Builder(hint).build());

        assertThat(invalidationHint.getInvalidatedHintId()).isEqualTo(hint.getHintId());
    }

    /** Bundles then unbundles the given {@link ContextHint}. */
    public ContextHint bundleUnbundle(ContextHint hint) {
        return ContextHint.createHintFromBundle(hint.toBundle());
    }
}
