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

package android.service.personalcontext.cts.refiner;

import static com.google.common.truth.Truth.assertThat;

import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.service.personalcontext.Flags;
import android.service.personalcontext.PersonalContextManager;
import android.service.personalcontext.Token;
import android.service.personalcontext.hint.BundleHint;
import android.service.personalcontext.hint.ContextHintWithSignature;
import android.service.personalcontext.refiner.HintFilter;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;

import javax.crypto.spec.SecretKeySpec;

/** Build/Install/Run: atest CtsPersonalContextTestCases:HintFilterTest */
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
@RunWith(AndroidJUnit4.class)
public class HintFilterTest {
    @Rule
    public final CheckFlagsRule checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static ContextHintWithSignature makeHint(
            Function<BundleHint.Builder, BundleHint.Builder> hintTuner)
            throws GeneralSecurityException {
        final byte[] key = new byte[64];
        new Random().nextBytes(key);

        return new ContextHintWithSignature.Builder(
                        hintTuner.apply(new BundleHint.Builder()).build(),
                        new SecretKeySpec(key, ContextHintWithSignature.HMAC_ALGORITHM))
                .build();
    }

    private PersonalContextManager mPersonalContextManager;

    @Before
    public void setUp() throws Exception {
        mPersonalContextManager =
                InstrumentationRegistry.getInstrumentation()
                        .getTargetContext()
                        .getSystemService(PersonalContextManager.class);
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.refiner.HintFilter#getInterestedHintClusters",
                "android.service.personalcontext.refiner.HintFilter.Builder"
                        + "#addRequiredHintToken",
                "android.service.personalcontext.refiner.HintFilter.Builder#build",
            })
    @Test
    public void testHintFilterRequireAll() throws GeneralSecurityException {
        final Token tokenA = mPersonalContextManager.mintToken();
        final Token tokenB = mPersonalContextManager.mintToken();
        final Token tokenC = mPersonalContextManager.mintToken();
        ContextHintWithSignature hintA = makeHint(b -> b.addToken(tokenA));
        ContextHintWithSignature hintB = makeHint(b -> b.addToken(tokenB));
        ContextHintWithSignature hintC = makeHint(b -> b.addToken(tokenC));

        final Set<ContextHintWithSignature> interestedHintSet =
                new HintFilter.Builder()
                        .addHintToken(tokenA, true)
                        .addHintToken(tokenB, true)
                        .addHintToken(tokenC, true)
                        .build()
                        .getInterestedHintClusters(
                                Set.of(hintA, hintB, hintC), Collections.emptySet());

        assertThat(interestedHintSet).containsExactly(hintA, hintB, hintC);
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.refiner.HintFilter#getInterestedHintClusters",
                "android.service.personalcontext.refiner.HintFilter.Builder"
                        + "#addRequiredHintToken",
                "android.service.personalcontext.refiner.HintFilter.Builder#build",
            })
    @Test
    public void testHintFilterRequireSome() throws GeneralSecurityException {
        final Token tokenA = mPersonalContextManager.mintToken();
        final Token tokenB = mPersonalContextManager.mintToken();
        final Token tokenC = mPersonalContextManager.mintToken();
        ContextHintWithSignature hintA = makeHint(b -> b.addToken(tokenA));
        ContextHintWithSignature hintB = makeHint(b -> b.addToken(tokenB));
        ContextHintWithSignature hintC = makeHint(b -> b.addToken(tokenC));

        final Set<ContextHintWithSignature> interestedHintSet =
                new HintFilter.Builder()
                        .addHintToken(tokenA, true)
                        .addHintToken(tokenB, true)
                        .build()
                        .getInterestedHintClusters(
                                Set.of(hintA, hintB, hintC), Collections.emptySet());

        assertThat(interestedHintSet).containsExactly(hintA, hintB);
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.refiner.HintFilter#getInterestedHintClusters",
                "android.service.personalcontext.refiner.HintFilter.Builder"
                        + "#addRequiredHintToken",
                "android.service.personalcontext.refiner.HintFilter.Builder#build",
            })
    @Test
    public void testHintFilterRequireMissingSome() throws GeneralSecurityException {
        final Token tokenA = mPersonalContextManager.mintToken();
        final Token tokenB = mPersonalContextManager.mintToken();
        final Token tokenC = mPersonalContextManager.mintToken();
        ContextHintWithSignature hintA = makeHint(b -> b.addToken(tokenA));
        ContextHintWithSignature hintB = makeHint(b -> b.addToken(tokenB));
        ContextHintWithSignature hintC = makeHint(b -> b.addToken(tokenC));

        final Set<ContextHintWithSignature> interestedHintSet =
                new HintFilter.Builder()
                        .addHintToken(tokenA, true)
                        .addHintToken(tokenB, true)
                        .addHintToken(tokenC, true)
                        .addHintToken(mPersonalContextManager.mintToken(), true)
                        .addHintToken(mPersonalContextManager.mintToken(), true)
                        .build()
                        .getInterestedHintClusters(
                                Set.of(hintA, hintB, hintC), Collections.emptySet());

        assertThat(interestedHintSet).isEmpty();
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.refiner.HintFilter#getInterestedHintClusters",
                "android.service.personalcontext.refiner.HintFilter.Builder"
                        + "#addRequiredHintToken",
                "android.service.personalcontext.refiner.HintFilter.Builder#build",
            })
    @Test
    public void testHintFilterRequireNone() throws GeneralSecurityException {
        final Token tokenA = mPersonalContextManager.mintToken();
        final Token tokenB = mPersonalContextManager.mintToken();
        final Token tokenC = mPersonalContextManager.mintToken();
        ContextHintWithSignature hintA = makeHint(b -> b.addToken(tokenA));
        ContextHintWithSignature hintB = makeHint(b -> b.addToken(tokenB));
        ContextHintWithSignature hintC = makeHint(b -> b.addToken(tokenC));

        final Set<ContextHintWithSignature> interestedHintSet =
                new HintFilter.Builder()
                        .addHintToken(mPersonalContextManager.mintToken(), true)
                        .addHintToken(mPersonalContextManager.mintToken(), true)
                        .build()
                        .getInterestedHintClusters(
                                Set.of(hintA, hintB, hintC), Collections.emptySet());

        assertThat(interestedHintSet).isEmpty();
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.refiner.HintFilter#getInterestedHintClusters",
                "android.service.personalcontext.refiner.HintFilter.Builder"
                        + "#addAllowedHintToken",
                "android.service.personalcontext.refiner.HintFilter.Builder#build",
            })
    @Test
    public void testHintFilterAllowOne() throws GeneralSecurityException {
        final Token tokenA = mPersonalContextManager.mintToken();
        final Token tokenB = mPersonalContextManager.mintToken();
        final Token tokenC = mPersonalContextManager.mintToken();
        ContextHintWithSignature hintA = makeHint(b -> b.addToken(tokenA));
        ContextHintWithSignature hintB = makeHint(b -> b.addToken(tokenB));
        ContextHintWithSignature hintC = makeHint(b -> b.addToken(tokenC));

        final Set<ContextHintWithSignature> interestedHintSet =
                new HintFilter.Builder()
                        .addHintToken(tokenA, false)
                        .addHintToken(mPersonalContextManager.mintToken(), false)
                        .build()
                        .getInterestedHintClusters(
                                Set.of(hintA, hintB, hintC), Collections.emptySet());

        assertThat(interestedHintSet).containsExactly(hintA);
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.refiner.HintFilter#getInterestedHintClusters",
                "android.service.personalcontext.refiner.HintFilter.Builder"
                        + "#addAllowedHintToken",
                "android.service.personalcontext.refiner.HintFilter.Builder#build",
            })
    @Test
    public void testHintFilterAllowMany() throws GeneralSecurityException {
        final Token tokenA = mPersonalContextManager.mintToken();
        final Token tokenB = mPersonalContextManager.mintToken();
        final Token tokenC = mPersonalContextManager.mintToken();
        ContextHintWithSignature hintA = makeHint(b -> b.addToken(tokenA));
        ContextHintWithSignature hintB = makeHint(b -> b.addToken(tokenB));
        ContextHintWithSignature hintC = makeHint(b -> b.addToken(tokenC));

        final Set<ContextHintWithSignature> interestedHintSet =
                new HintFilter.Builder()
                        .addHintToken(tokenA, false)
                        .addHintToken(tokenB, false)
                        .addHintToken(tokenC, false)
                        .build()
                        .getInterestedHintClusters(
                                Set.of(hintA, hintB, hintC), Collections.emptySet());

        assertThat(interestedHintSet).containsExactly(hintA, hintB, hintC);
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.refiner.HintFilter#getInterestedHintClusters",
                "android.service.personalcontext.refiner.HintFilter.Builder"
                        + "#addAllowedHintToken",
                "android.service.personalcontext.refiner.HintFilter.Builder#build",
            })
    @Test
    public void testHintFilterAllowSome() throws GeneralSecurityException {
        final Token tokenA = mPersonalContextManager.mintToken();
        final Token tokenB = mPersonalContextManager.mintToken();
        final Token tokenC = mPersonalContextManager.mintToken();
        ContextHintWithSignature hintA = makeHint(b -> b.addToken(tokenA));
        ContextHintWithSignature hintB = makeHint(b -> b.addToken(tokenB));
        ContextHintWithSignature hintC = makeHint(b -> b.addToken(tokenC));

        final Set<ContextHintWithSignature> interestedHintSet =
                new HintFilter.Builder()
                        .addHintToken(tokenA, false)
                        .addHintToken(tokenB, false)
                        .build()
                        .getInterestedHintClusters(
                                Set.of(hintA, hintB, hintC), Collections.emptySet());

        assertThat(interestedHintSet).containsExactly(hintA, hintB);
    }
}
