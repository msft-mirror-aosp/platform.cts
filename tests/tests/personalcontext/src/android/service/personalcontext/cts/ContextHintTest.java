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

package android.service.personalcontext.cts;

import static com.google.common.truth.Truth.assertThat;

import android.os.Bundle;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.service.personalcontext.Flags;
import android.service.personalcontext.RenderToken;
import android.service.personalcontext.hint.BundleHint;
import android.service.personalcontext.hint.ContextHint;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Build/Install/Run: atest CtsPersonalContextTestCases:ContextHintTest */
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
@RunWith(AndroidJUnit4.class)
public class ContextHintTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    // Tests bundling and unbundling fields on the base ContextHint.
    @ApiTest(
            apis = {
                "android.service.personalcontext.hint.ContextHint#getHintType",
                "android.service.personalcontext.hint.ContextHint#getHintId",
                "android.service.personalcontext.hint.ContextHint#getAttributionHints",
                "android.service.personalcontext.RenderToken#getTokenId",
                "android.service.personalcontext.RenderToken#getRendererComponentId"
            })
    @Test
    public void testContextHintBundleUnbundle() {
        final BundleHint hint = new BundleHint();
        RenderToken renderToken =
                new RenderToken.RenderTokenBuilder()
                        .setRendererComponentId(UUID.randomUUID())
                        .build();
        hint.setRenderToken(renderToken);
        hint.setAttributionHints(new ArrayList<>(List.of(new BundleHint())));

        final ContextHint outputHint = bundleUnbundle(hint);

        assertThat(hint.getHintType()).isEqualTo(outputHint.getHintType());
        assertThat(hint.getHintId()).isEqualTo(outputHint.getHintId());
        assertThat(hint.getAttributionHints().size())
                .isEqualTo(outputHint.getAttributionHints().size());

        RenderToken out = outputHint.getRenderToken();
        assertThat(out.getTokenId()).isEqualTo(renderToken.getTokenId());
        assertThat(out.getRendererComponentId()).isEqualTo(renderToken.getRendererComponentId());
    }

    @ApiTest(apis = {"android.service.personalcontext.hint.BundleHint#getDataBundle"})
    @Test
    public void testBundleHintBundleUnbundle() {
        final int inputValue = 1234;
        final String dataKey = "test-key";
        final Bundle data = new Bundle();
        data.putInt(dataKey, inputValue);

        final BundleHint hint = new BundleHint();
        hint.getDataBundle().putAll(data);

        final ContextHint outputHint = bundleUnbundle(hint);
        assertThat(outputHint).isInstanceOf(BundleHint.class);
        final int outputValue = ((BundleHint) outputHint).getDataBundle().getInt(dataKey);

        assertThat(outputValue).isEqualTo(inputValue);
    }

    /** Bundles then unbundles the given {@link ContextHint}. */
    public ContextHint bundleUnbundle(ContextHint hint) {
        return ContextHint.createHintFromBundle(hint.toBundle());
    }
}
