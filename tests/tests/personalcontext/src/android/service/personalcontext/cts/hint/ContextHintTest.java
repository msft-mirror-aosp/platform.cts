/*
 * Copyright 2025 The Android Open Source Project
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
import android.service.personalcontext.PersonalContextManager;
import android.service.personalcontext.Token;
import android.service.personalcontext.hint.BundleHint;
import android.service.personalcontext.hint.ContextHint;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Instant;

/** Build/Install/Run: atest CtsPersonalContextTestCases:ContextHintTest */
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
@RunWith(AndroidJUnit4.class)
public class ContextHintTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private PersonalContextManager mPersonalContextManager;

    @Before
    public void setUp() throws Exception {
        mPersonalContextManager =
                InstrumentationRegistry.getInstrumentation()
                        .getTargetContext()
                        .getSystemService(PersonalContextManager.class);
    }

    // Tests bundling and unbundling fields on the base ContextHint.
    @ApiTest(
            apis = {
                "android.service.personalcontext.hint.ContextHint#getHintType",
                "android.service.personalcontext.hint.ContextHint#getHintId",
                "android.service.personalcontext.hint.ContextHint#getTags",
            })
    @Test
    public void testContextHintBundleUnbundle() {
        Token tokenA = mPersonalContextManager.mintToken();
        Token tokenB = mPersonalContextManager.mintToken();

        final BundleHint hint = new BundleHint.Builder().addToken(tokenA).addToken(tokenB).build();

        final ContextHint outputHint = bundleUnbundle(hint);

        assertThat(outputHint).isInstanceOf(BundleHint.class);
        assertThat(hint.getHintId()).isEqualTo(outputHint.getHintId());
        assertThat(hint.getTokens()).containsExactly(tokenA, tokenB);
        assertThat(hint.getCreationTime()).isGreaterThan(Instant.ofEpochMilli(0));
        assertThat(hint.getCreationTime().toEpochMilli())
                .isEqualTo(outputHint.getCreationTime().toEpochMilli());
    }

    /** Bundles then unbundles the given {@link ContextHint}. */
    public ContextHint bundleUnbundle(ContextHint hint) {
        return ContextHint.createHintFromBundle(hint.toBundle());
    }
}
