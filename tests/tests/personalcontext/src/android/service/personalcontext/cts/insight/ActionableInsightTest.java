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

package android.service.personalcontext.cts.insight;

import static com.google.common.truth.Truth.assertThat;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.service.personalcontext.Flags;
import android.service.personalcontext.RenderToken;
import android.service.personalcontext.hint.BundleHint;
import android.service.personalcontext.hint.ContextHintWithSignature;
import android.service.personalcontext.insight.ActionableInsight;
import android.service.personalcontext.insight.InsightActionDetails;
import android.service.personalcontext.insight.InsightDisplayDetails;
import android.service.personalcontext.insight.interaction.ReturnHintReport;

import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import javax.crypto.spec.SecretKeySpec;

/** Build/Install/Run: atest CtsPersonalContextTestCases:ContextInsightTest */
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
@RunWith(AndroidJUnit4.class)
public class ActionableInsightTest {
    @Rule
    public final CheckFlagsRule checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    /** Generates a key to use when signing hints. */
    public static SecretKeySpec generateSignedHintKey() {
        byte[] key = new byte[64];
        new Random().nextBytes(key);
        return new SecretKeySpec(key, ContextHintWithSignature.HMAC_ALGORITHM);
    }

    // Tests bundling and unbundling fields on the base ContextInsight.
    @ApiTest(
            apis = {
                "android.service.personalcontext.insight.ActionableInsight"
                        + "#createReturnHintReporter",
                "android.service.personalcontext.insight.interaction.ReturnHintReporter"
                        + "#publishNewHints",
                "android.service.personalcontext.hint.InsightReferenceHint#getInsightId",
            })
    @Test
    public void testReturnHintReporter() throws GeneralSecurityException {
        final RenderToken renderToken = new RenderToken(UUID.randomUUID(), null);

        final ContextHintWithSignature originHint =
                new ContextHintWithSignature.Builder(
                                new BundleHint.Builder().build(), generateSignedHintKey())
                        .addRenderTokens(List.of(renderToken))
                        .build();
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final ActionableInsight insight =
                new ActionableInsight.Builder(
                                new InsightActionDetails.Builder()
                                        .setPendingIntent(
                                                PendingIntent.getActivity(
                                                        /* context= */ context,
                                                        /* requestCode= */ 0,
                                                        /* intent= */ new Intent("test"),
                                                        /* flags= */ PendingIntent.FLAG_IMMUTABLE))
                                        .build(),
                                new InsightDisplayDetails.Builder("test").build())
                        .addOriginHint(originHint)
                        .build();

        final ReturnHintReport reporter = insight.createReturnHintReport();
        assertThat(reporter).isNotNull();

        // TODO: Send the report into the service and make sure the hints are correct.
    }
}
