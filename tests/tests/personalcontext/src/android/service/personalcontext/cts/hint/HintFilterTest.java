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

package android.service.personalcontext.cts.hint;

import static com.google.common.truth.Truth.assertThat;

import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.service.personalcontext.Flags;
import android.service.personalcontext.hint.BundleHint;
import android.service.personalcontext.hint.HintFilter;
import android.service.personalcontext.hint.PublishedContextHint;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.Random;
import java.util.Set;

import javax.crypto.spec.SecretKeySpec;

/** Build/Install/Run: atest CtsPersonalContextTestCases:HintFilterTest */
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
@RunWith(AndroidJUnit4.class)
public class HintFilterTest {
    private static final String HINT_CLASS_A =
            "android.service.personalcontext.hint.HintFilterTest.A";
    private static final String HINT_CLASS_B =
            "android.service.personalcontext.hint.HintFilterTest.B";
    private static final String HINT_CLASS_C =
            "android.service.personalcontext.hint.HintFilterTest.C";
    private static final String HINT_CLASS_D =
            "android.service.personalcontext.hint.HintFilterTest.D";
    private static final String HINT_CLASS_E =
            "android.service.personalcontext.hint.HintFilterTest.E";

    @Rule
    public final CheckFlagsRule checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static PublishedContextHint makeHint(String hintClass) throws GeneralSecurityException {
        final byte[] key = new byte[64];
        new Random().nextBytes(key);

        return new PublishedContextHint.Builder(
                        new BundleHint.Builder().setHintTypeName(hintClass).build(),
                        new SecretKeySpec(key, PublishedContextHint.HMAC_ALGORITHM))
                .build();
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.hint.HintFilter#getInterestedHintClusters",
                "android.service.personalcontext.hint.HintFilter.Builder#addHintType",
                "android.service.personalcontext.hint.HintFilter.Builder#build",
            })
    @Test
    public void testHintFilterRequireAll() throws GeneralSecurityException {
        PublishedContextHint hintA = makeHint(HINT_CLASS_A);
        PublishedContextHint hintB = makeHint(HINT_CLASS_B);
        PublishedContextHint hintC = makeHint(HINT_CLASS_C);

        final Set<PublishedContextHint> interestedHintSet =
                new HintFilter.Builder()
                        .addBundleHintTypeName(HINT_CLASS_A, HintFilter.FILTER_TYPE_REQUIRED)
                        .addBundleHintTypeName(HINT_CLASS_B, HintFilter.FILTER_TYPE_REQUIRED)
                        .addBundleHintTypeName(HINT_CLASS_C, HintFilter.FILTER_TYPE_REQUIRED)
                        .build()
                        .getInterestedHintClusters(
                                Set.of(hintA, hintB, hintC), Collections.emptySet());

        assertThat(interestedHintSet).containsExactly(hintA, hintB, hintC);
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.hint.HintFilter#getInterestedHintClusters",
                "android.service.personalcontext.hint.HintFilter.Builder#addHintType",
                "android.service.personalcontext.hint.HintFilter.Builder#build",
            })
    @Test
    public void testHintFilterRequireSome() throws GeneralSecurityException {
        PublishedContextHint hintA = makeHint(HINT_CLASS_A);
        PublishedContextHint hintB = makeHint(HINT_CLASS_B);
        PublishedContextHint hintC = makeHint(HINT_CLASS_C);

        final Set<PublishedContextHint> interestedHintSet =
                new HintFilter.Builder()
                        .addBundleHintTypeName(HINT_CLASS_A, HintFilter.FILTER_TYPE_REQUIRED)
                        .addBundleHintTypeName(HINT_CLASS_B, HintFilter.FILTER_TYPE_REQUIRED)
                        .build()
                        .getInterestedHintClusters(
                                Set.of(hintA, hintB, hintC), Collections.emptySet());

        assertThat(interestedHintSet).containsExactly(hintA, hintB);
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.hint.HintFilter#getInterestedHintClusters",
                "android.service.personalcontext.hint.HintFilter.Builder#addHintType",
                "android.service.personalcontext.hint.HintFilter.Builder#build",
            })
    @Test
    public void testHintFilterRequireMissingSome() throws GeneralSecurityException {
        PublishedContextHint hintA = makeHint(HINT_CLASS_A);
        PublishedContextHint hintB = makeHint(HINT_CLASS_B);
        PublishedContextHint hintC = makeHint(HINT_CLASS_C);

        final Set<PublishedContextHint> interestedHintSet =
                new HintFilter.Builder()
                        .addBundleHintTypeName(HINT_CLASS_A, HintFilter.FILTER_TYPE_REQUIRED)
                        .addBundleHintTypeName(HINT_CLASS_B, HintFilter.FILTER_TYPE_REQUIRED)
                        .addBundleHintTypeName(HINT_CLASS_C, HintFilter.FILTER_TYPE_REQUIRED)
                        .addBundleHintTypeName(HINT_CLASS_D, HintFilter.FILTER_TYPE_REQUIRED)
                        .addBundleHintTypeName(HINT_CLASS_E, HintFilter.FILTER_TYPE_REQUIRED)
                        .build()
                        .getInterestedHintClusters(
                                Set.of(hintA, hintB, hintC), Collections.emptySet());

        assertThat(interestedHintSet).isEmpty();
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.hint.HintFilter#getInterestedHintClusters",
                "android.service.personalcontext.hint.HintFilter.Builder#addHintType",
                "android.service.personalcontext.hint.HintFilter.Builder#build",
            })
    @Test
    public void testHintFilterRequireNone() throws GeneralSecurityException {
        PublishedContextHint hintA = makeHint(HINT_CLASS_A);
        PublishedContextHint hintB = makeHint(HINT_CLASS_B);
        PublishedContextHint hintC = makeHint(HINT_CLASS_C);

        final Set<PublishedContextHint> interestedHintSet =
                new HintFilter.Builder()
                        .addBundleHintTypeName(HINT_CLASS_D, HintFilter.FILTER_TYPE_REQUIRED)
                        .addBundleHintTypeName(HINT_CLASS_E, HintFilter.FILTER_TYPE_REQUIRED)
                        .build()
                        .getInterestedHintClusters(
                                Set.of(hintA, hintB, hintC), Collections.emptySet());

        assertThat(interestedHintSet).isEmpty();
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.hint.HintFilter#getInterestedHintClusters",
                "android.service.personalcontext.hint.HintFilter.Builder#addHintType",
                "android.service.personalcontext.hint.HintFilter.Builder#build",
            })
    @Test
    public void testHintFilterAllowOne() throws GeneralSecurityException {
        PublishedContextHint hintA = makeHint(HINT_CLASS_A);
        PublishedContextHint hintB = makeHint(HINT_CLASS_B);
        PublishedContextHint hintC = makeHint(HINT_CLASS_C);

        final Set<PublishedContextHint> interestedHintSet =
                new HintFilter.Builder()
                        .addBundleHintTypeName(HINT_CLASS_A, HintFilter.FILTER_TYPE_ALLOWED)
                        .addBundleHintTypeName(HINT_CLASS_D, HintFilter.FILTER_TYPE_ALLOWED)
                        .build()
                        .getInterestedHintClusters(
                                Set.of(hintA, hintB, hintC), Collections.emptySet());

        assertThat(interestedHintSet).containsExactly(hintA);
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.hint.HintFilter#getInterestedHintClusters",
                "android.service.personalcontext.hint.HintFilter.Builder#addHintType",
                "android.service.personalcontext.hint.HintFilter.Builder#build",
            })
    @Test
    public void testHintFilterAllowMany() throws GeneralSecurityException {
        PublishedContextHint hintA = makeHint(HINT_CLASS_A);
        PublishedContextHint hintB = makeHint(HINT_CLASS_B);
        PublishedContextHint hintC = makeHint(HINT_CLASS_C);

        final Set<PublishedContextHint> interestedHintSet =
                new HintFilter.Builder()
                        .addBundleHintTypeName(HINT_CLASS_A, HintFilter.FILTER_TYPE_ALLOWED)
                        .addBundleHintTypeName(HINT_CLASS_B, HintFilter.FILTER_TYPE_ALLOWED)
                        .addBundleHintTypeName(HINT_CLASS_C, HintFilter.FILTER_TYPE_ALLOWED)
                        .build()
                        .getInterestedHintClusters(
                                Set.of(hintA, hintB, hintC), Collections.emptySet());

        assertThat(interestedHintSet).containsExactly(hintA, hintB, hintC);
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.hint.HintFilter#getInterestedHintClusters",
                "android.service.personalcontext.hint.HintFilter.Builder#addHintType",
                "android.service.personalcontext.hint.HintFilter.Builder#build",
            })
    @Test
    public void testHintFilterAllowSome() throws GeneralSecurityException {
        PublishedContextHint hintA = makeHint(HINT_CLASS_A);
        PublishedContextHint hintB = makeHint(HINT_CLASS_B);
        PublishedContextHint hintC = makeHint(HINT_CLASS_C);

        final Set<PublishedContextHint> interestedHintSet =
                new HintFilter.Builder()
                        .addBundleHintTypeName(HINT_CLASS_A, HintFilter.FILTER_TYPE_ALLOWED)
                        .addBundleHintTypeName(HINT_CLASS_B, HintFilter.FILTER_TYPE_ALLOWED)
                        .build()
                        .getInterestedHintClusters(
                                Set.of(hintA, hintB, hintC), Collections.emptySet());

        assertThat(interestedHintSet).containsExactly(hintA, hintB);
    }
}
