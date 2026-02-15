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

import android.content.ComponentName;
import android.os.Parcel;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.service.personalcontext.Flags;
import android.service.personalcontext.RenderToken;
import android.service.personalcontext.hint.BundleHint;
import android.service.personalcontext.hint.ContextHint;
import android.service.personalcontext.hint.ContextHintWithSignature;
import android.service.personalcontext.hint.ContextHintWithSignatureWrapper;
import android.util.ArraySet;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.security.GeneralSecurityException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import javax.crypto.spec.SecretKeySpec;

@RequiresFlagsEnabled(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
@RunWith(AndroidJUnit4.class)
public class ContextHintWithSignatureTest {
    private static final String SYSTEM_PACKAGE = "android";

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    /** Generates a key to use when signing hints. */
    private static SecretKeySpec generateSignedHintKey() {
        final byte[] key = new byte[64];
        new Random().nextBytes(key);
        return new SecretKeySpec(key, ContextHintWithSignature.HMAC_ALGORITHM);
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.hint.ContextHintWithSignature.Builder"
                        + "#addRenderTokens",
                "android.service.personalcontext.hint.ContextHintWithSignature.Builder"
                        + "#setOriginatingPackage",
                "android.service.personalcontext.hint.ContextHintWithSignature.Builder"
                        + "#addAttributionHint",
                "android.service.personalcontext.hint.ContextHintWithSignature.Builder#build",
                "android.service.personalcontext.hint.ContextHintWithSignature#isSignatureValid",
                "android.service.personalcontext.hint.ContextHintWithSignature#getContextHint",
                "android.service.personalcontext.hint.ContextHintWithSignature#getRenderTokens",
                "android.service.personalcontext.hint.ContextHintWithSignature"
                        + "#getOriginatingPackage",
                "android.service.personalcontext.hint.ContextHintWithSignature#getAttributionHints",
                "android.service.personalcontext.hint.ContextHintWithSignature#describeContents",
                "android.service.personalcontext.hint.ContextHintWithSignature#writeToParcel",
                "android.service.personalcontext.hint.ContextHintWithSignature#CREATOR",
            })
    private static void checkPresence(
            ContextHintWithSignature signedHint, List<ContextHint> hints) {
        final ArraySet<ContextHint> remainingHints = new ArraySet<>(hints);
        final HashSet<ContextHintWithSignature> attributionHints =
                new HashSet<>(signedHint.getAttributionHints());

        assertThat(remainingHints.size()).isEqualTo(attributionHints.size());
        for (ContextHintWithSignature targetHint : attributionHints) {
            assertThat(targetHint.getOriginatingPackage()).isEqualTo(SYSTEM_PACKAGE);
            final ContextHint targetContextHint = targetHint.getContextHint();
            final Optional<ContextHint> foundHint =
                    remainingHints.stream()
                            .filter(hint -> targetContextHint.getHintId().equals(hint.getHintId()))
                            .findFirst();

            foundHint.ifPresent(remainingHints::remove);
        }

        assertThat(remainingHints).isEmpty();
    }

    @Test
    public void testParcelAndUnparcel() throws GeneralSecurityException {
        final SecretKeySpec key = generateSignedHintKey();
        final ComponentName origin = new ComponentName("com.whatever", "com.whatever.Code");
        final BundleHint hint = new BundleHint.Builder().build();
        final BundleHint attributedHint1 = new BundleHint.Builder().build();
        final BundleHint attributedHint2 = new BundleHint.Builder().build();
        final RenderToken renderToken = new RenderToken(UUID.randomUUID());

        final ContextHintWithSignature signedAttributedHint1 =
                new ContextHintWithSignature.Builder(attributedHint1, key)
                        .addRenderTokens(List.of(renderToken))
                        .build();

        final ContextHintWithSignature signedAttributedHint2 =
                new ContextHintWithSignature.Builder(attributedHint2, key)
                        .addRenderTokens(List.of(renderToken))
                        .build();

        final Parcel parcel = Parcel.obtain();
        parcel.writeParcelable(
                new ContextHintWithSignatureWrapper(
                        new ContextHintWithSignature.Builder(hint, key)
                                .setOriginatingPackage(origin.getPackageName())
                                .addRenderTokens(List.of(renderToken))
                                .addAttributionHint(signedAttributedHint1)
                                .addAttributionHint(signedAttributedHint2)
                                .build()),
                0);

        parcel.setDataPosition(0);

        final ContextHintWithSignature signedHint =
                parcel.readParcelable(/* loader= */ null, ContextHintWithSignatureWrapper.class)
                        .getContextHintWithSignature();

        parcel.recycle();

        assertThat(signedHint.isSignatureValid(key)).isTrue();
        assertThat(signedHint.getContextHint().getHintId()).isEqualTo(hint.getHintId());
        assertThat(signedHint.getRenderTokens()).containsExactly(renderToken);
        assertThat(signedHint.getOriginatingPackage()).isEqualTo(origin.getPackageName());

        checkPresence(signedHint, List.of(attributedHint1, attributedHint2));
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.hint.ContextHintWithSignature.Builder"
                        + "#addRenderTokens",
                "android.service.personalcontext.hint.ContextHintWithSignature.Builder"
                        + "#addAttributionHint",
                "android.service.personalcontext.hint.ContextHintWithSignature.Builder#build",
                "android.service.personalcontext.hint.ContextHintWithSignature#isSignatureValid",
                "android.service.personalcontext.hint.ContextHintWithSignature#getContextHint",
                "android.service.personalcontext.hint.ContextHintWithSignature#getRenderTokens",
                "android.service.personalcontext.hint.ContextHintWithSignature"
                        + "#getOriginatingPackage",
                "android.service.personalcontext.hint.ContextHintWithSignature#getAttributionHints",
                "android.service.personalcontext.hint.ContextHintWithSignature#writeToParcel",
                "android.service.personalcontext.hint.ContextHintWithSignature#CREATOR",
            })
    @Test
    public void testParcelAndUnparcelWithoutOrigin() throws GeneralSecurityException {
        final SecretKeySpec key = generateSignedHintKey();
        final BundleHint hint = new BundleHint.Builder().build();
        final BundleHint attributedHint1 = new BundleHint.Builder().build();
        final BundleHint attributedHint2 = new BundleHint.Builder().build();
        final RenderToken renderToken = new RenderToken(UUID.randomUUID());

        final ContextHintWithSignature signedAttributedHint1 =
                new ContextHintWithSignature.Builder(attributedHint1, key)
                        .addRenderTokens(List.of(renderToken))
                        .build();

        final ContextHintWithSignature signedAttributedHint2 =
                new ContextHintWithSignature.Builder(attributedHint2, key)
                        .addRenderTokens(List.of(renderToken))
                        .build();

        final Parcel parcel = Parcel.obtain();
        parcel.writeParcelable(
                new ContextHintWithSignatureWrapper(
                        new ContextHintWithSignature.Builder(hint, key)
                                .addRenderTokens(List.of(renderToken))
                                .addAttributionHints(
                                        List.of(signedAttributedHint1, signedAttributedHint2))
                                .build()),
                0);

        parcel.setDataPosition(0);

        final ContextHintWithSignature signedHint =
                parcel.readParcelable(/* loader= */ null, ContextHintWithSignatureWrapper.class)
                        .getContextHintWithSignature();

        parcel.recycle();

        assertThat(signedHint.isSignatureValid(key)).isTrue();
        assertThat(signedHint.getContextHint().getHintId()).isEqualTo(hint.getHintId());
        assertThat(signedHint.getRenderTokens()).containsExactly(renderToken);
        assertThat(signedHint.getOriginatingPackage()).isEqualTo(SYSTEM_PACKAGE);

        checkPresence(signedHint, List.of(attributedHint1, attributedHint2));
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.hint.ContextHintWithSignature.Builder"
                        + "#setOriginatingPackage",
                "android.service.personalcontext.hint.ContextHintWithSignature.Builder"
                        + "#addAttributionHint",
                "android.service.personalcontext.hint.ContextHintWithSignature.Builder#build",
                "android.service.personalcontext.hint.ContextHintWithSignature#isSignatureValid",
                "android.service.personalcontext.hint.ContextHintWithSignature#getContextHint",
                "android.service.personalcontext.hint.ContextHintWithSignature#getRenderTokens",
                "android.service.personalcontext.hint.ContextHintWithSignature"
                        + "#getOriginatingPackage",
                "android.service.personalcontext.hint.ContextHintWithSignature#getAttributionHints",
                "android.service.personalcontext.hint.ContextHintWithSignature#writeToParcel",
                "android.service.personalcontext.hint.ContextHintWithSignature#CREATOR",
            })
    @Test
    public void testParcelAndUnparcelWithoutRenderToken() throws GeneralSecurityException {
        final SecretKeySpec key = generateSignedHintKey();
        final ComponentName origin = new ComponentName("com.whatever", "com.whatever.Code");
        final BundleHint hint = new BundleHint.Builder().build();
        final BundleHint attributedHint1 = new BundleHint.Builder().build();
        final BundleHint attributedHint2 = new BundleHint.Builder().build();

        final ContextHintWithSignature signedAttributedHint1 =
                new ContextHintWithSignature.Builder(attributedHint1, key).build();

        final ContextHintWithSignature signedAttributedHint2 =
                new ContextHintWithSignature.Builder(attributedHint2, key).build();

        final Parcel parcel = Parcel.obtain();
        parcel.writeParcelable(
                new ContextHintWithSignatureWrapper(
                        new ContextHintWithSignature.Builder(hint, key)
                                .setOriginatingPackage(origin.getPackageName())
                                .addAttributionHints(
                                        List.of(signedAttributedHint1, signedAttributedHint2))
                                .build()),
                0);

        parcel.setDataPosition(0);

        final ContextHintWithSignature signedHint =
                parcel.readParcelable(/* loader= */ null, ContextHintWithSignatureWrapper.class)
                        .getContextHintWithSignature();

        parcel.recycle();

        assertThat(signedHint.isSignatureValid(key)).isTrue();
        assertThat(signedHint.getContextHint().getHintId()).isEqualTo(hint.getHintId());
        assertThat(signedHint.getRenderTokens()).isEmpty();
        assertThat(signedHint.getOriginatingPackage()).isEqualTo(origin.getPackageName());

        checkPresence(signedHint, List.of(attributedHint1, attributedHint2));
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.hint.ContextHintWithSignature.Builder"
                        + "#addRenderTokens",
                "android.service.personalcontext.hint.ContextHintWithSignature.Builder"
                        + "#setOriginatingPackage",
                "android.service.personalcontext.hint.ContextHintWithSignature.Builder#build",
                "android.service.personalcontext.hint.ContextHintWithSignature#isSignatureValid",
                "android.service.personalcontext.hint.ContextHintWithSignature#getContextHint",
                "android.service.personalcontext.hint.ContextHintWithSignature#getRenderTokens",
                "android.service.personalcontext.hint.ContextHintWithSignature"
                        + "#getOriginatingPackage",
                "android.service.personalcontext.hint.ContextHintWithSignature#getAttributionHints",
                "android.service.personalcontext.hint.ContextHintWithSignature#writeToParcel",
                "android.service.personalcontext.hint.ContextHintWithSignature#CREATOR",
            })
    @Test
    public void testParcelAndUnparcelWithoutAttribution() throws GeneralSecurityException {
        final SecretKeySpec key = generateSignedHintKey();
        final ComponentName origin = new ComponentName("com.whatever", "com.whatever.Code");
        final BundleHint hint = new BundleHint.Builder().build();
        final RenderToken renderToken = new RenderToken(UUID.randomUUID());

        final Parcel parcel = Parcel.obtain();
        parcel.writeParcelable(
                new ContextHintWithSignatureWrapper(
                        new ContextHintWithSignature.Builder(hint, key)
                                .setOriginatingPackage(origin.getPackageName())
                                .addRenderTokens(List.of(renderToken))
                                .build()),
                0);

        parcel.setDataPosition(0);

        final ContextHintWithSignature signedHint =
                parcel.readParcelable(/* loader= */ null, ContextHintWithSignatureWrapper.class)
                        .getContextHintWithSignature();

        parcel.recycle();

        assertThat(signedHint.isSignatureValid(key)).isTrue();
        assertThat(signedHint.getContextHint().getHintId()).isEqualTo(hint.getHintId());
        assertThat(signedHint.getRenderTokens()).containsExactly(renderToken);
        assertThat(signedHint.getOriginatingPackage()).isEqualTo(origin.getPackageName());
        assertThat(signedHint.getAttributionHints().size()).isEqualTo(0);
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.hint.ContextHintWithSignature.Builder#build",
                "android.service.personalcontext.hint.ContextHintWithSignature#isSignatureValid",
                "android.service.personalcontext.hint.ContextHintWithSignature#writeToParcel",
                "android.service.personalcontext.hint.ContextHintWithSignature#CREATOR",
            })
    @Test
    public void testSignatureWrongKey() throws GeneralSecurityException {
        final BundleHint hint = new BundleHint.Builder().build();

        final ContextHintWithSignature signedHint =
                (new ContextHintWithSignature.Builder(hint, generateSignedHintKey()).build());

        assertThat(signedHint.isSignatureValid(generateSignedHintKey())).isFalse();
    }
}
