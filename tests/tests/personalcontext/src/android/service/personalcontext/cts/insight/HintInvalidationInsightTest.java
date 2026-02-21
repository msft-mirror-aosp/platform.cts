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

package android.service.personalcontext.cts.insight;

import static com.google.common.truth.Truth.assertThat;

import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.service.personalcontext.Flags;
import android.service.personalcontext.hint.BundleHint;
import android.service.personalcontext.hint.HintInvalidationHint;
import android.service.personalcontext.hint.PublishedContextHint;
import android.service.personalcontext.insight.ContextInsight;
import android.service.personalcontext.insight.HintInvalidationInsight;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.security.GeneralSecurityException;
import java.util.Random;

import javax.crypto.spec.SecretKeySpec;

/** Build/Install/Run: atest CtsPersonalContextTestCases:HintInvalidationInsightTest */
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
@RunWith(AndroidJUnit4.class)
public class HintInvalidationInsightTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    /** Generates a key to use when signing hints. */
    private static SecretKeySpec generateSignedHintKey() {
        final byte[] key = new byte[64];
        new Random().nextBytes(key);
        return new SecretKeySpec(key, PublishedContextHint.HMAC_ALGORITHM);
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.hint.HintInvalidationInsight#isHintInvalidated",
                "android.service.personalcontext.hint.HintInvalidationInsight.Builder#build",
            })
    @Test
    public void testRightHintInvalidated() throws GeneralSecurityException {
        final BundleHint originalHint = new BundleHint.Builder().build();
        final HintInvalidationHint invalidationHint =
                new HintInvalidationHint.Builder(originalHint).build();

        final PublishedContextHint signedOriginalHint =
                new PublishedContextHint.Builder(originalHint, generateSignedHintKey())
                        .setOriginatingPackage("a")
                        .build();

        final PublishedContextHint signedInvalidationHint =
                new PublishedContextHint.Builder(invalidationHint, generateSignedHintKey())
                        .setOriginatingPackage("a")
                        .build();

        final HintInvalidationInsight insight =
                (HintInvalidationInsight)
                        bundleUnbundle(
                                new HintInvalidationInsight.Builder(signedInvalidationHint)
                                        .build());

        assertThat(insight.getInvalidatedHintId()).isEqualTo(originalHint.getHintId());
        assertThat(insight.isHintInvalidated(signedOriginalHint)).isTrue();
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.hint.HintInvalidationInsight#isHintInvalidated",
                "android.service.personalcontext.hint.HintInvalidationInsight.Builder#build",
            })
    @Test
    public void testWrongHintNotInvalidated() throws GeneralSecurityException {
        final BundleHint originalHint = new BundleHint.Builder().build();
        final BundleHint otherHint = new BundleHint.Builder().build();
        final HintInvalidationHint invalidationHint =
                new HintInvalidationHint.Builder(originalHint).build();

        final PublishedContextHint signedOtherHint =
                new PublishedContextHint.Builder(otherHint, generateSignedHintKey())
                        .setOriginatingPackage("a")
                        .build();

        final PublishedContextHint signedInvalidationHint =
                new PublishedContextHint.Builder(invalidationHint, generateSignedHintKey())
                        .setOriginatingPackage("a")
                        .build();

        final HintInvalidationInsight insight =
                (HintInvalidationInsight)
                        bundleUnbundle(
                                new HintInvalidationInsight.Builder(signedInvalidationHint)
                                        .build());

        assertThat(insight.isHintInvalidated(signedOtherHint)).isFalse();
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.hint.HintInvalidationInsight#isHintInvalidated",
                "android.service.personalcontext.hint.HintInvalidationInsight.Builder#build",
            })
    @Test
    public void testPackageNotInvalidated() throws GeneralSecurityException {
        final BundleHint originalHint = new BundleHint.Builder().build();
        final HintInvalidationHint invalidationHint =
                new HintInvalidationHint.Builder(originalHint).build();

        final PublishedContextHint signedOriginalHint =
                new PublishedContextHint.Builder(originalHint, generateSignedHintKey())
                        .setOriginatingPackage("a")
                        .build();

        final PublishedContextHint signedInvalidationHint =
                new PublishedContextHint.Builder(invalidationHint, generateSignedHintKey())
                        .setOriginatingPackage("b")
                        .build();

        final HintInvalidationInsight insight =
                (HintInvalidationInsight)
                        bundleUnbundle(
                                new HintInvalidationInsight.Builder(signedInvalidationHint)
                                        .build());

        assertThat(insight.getInvalidatedHintId()).isEqualTo(originalHint.getHintId());
        assertThat(insight.isHintInvalidated(signedOriginalHint)).isFalse();
    }

    /** Bundles then unbundles the given {@link ContextInsight}. */
    public ContextInsight bundleUnbundle(ContextInsight insight) {
        return ContextInsight.createInsightFromBundle(insight.toBundle());
    }
}
