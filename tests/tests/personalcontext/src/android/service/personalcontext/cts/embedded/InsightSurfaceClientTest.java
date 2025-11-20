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

package android.service.personalcontext.cts.embedded;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.service.personalcontext.Flags;
import android.service.personalcontext.embedded.InsightSurfaceClient;
import android.service.personalcontext.hint.BundleHint;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.Executor;

/** Build/Install/Run: atest CtsPersonalContextTestCases:InsightSurfaceClientTest */
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
@RunWith(AndroidJUnit4.class)
public class InsightSurfaceClientTest {
    @Rule
    public final CheckFlagsRule checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Mock private Context mContext;
    @Mock private InsightSurfaceClient.ClientCallback mClientCallbacks;
    @Mock private InsightSurfaceClient.InsightReceiver mInsightReceiver;
    private final Executor mExecutor = Runnable::run;
    private final BundleHint mHint = new BundleHint();

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.embedded.InsightSurfaceClient.Builder#addReceiver",
                "android.service.personalcontext.embedded.InsightSurfaceClient.Builder#addHint",
                "android.service.personalcontext.embedded.InsightSurfaceClient.Builder#build",
                "android.service.personalcontext.embedded.InsightSurfaceClient#getHints",
                "android.service.personalcontext.embedded.InsightSurfaceClient#getReceivers",
            })
    @Test
    public void testClientBuilder() {
        final InsightSurfaceClient client =
                new InsightSurfaceClient.Builder(mContext, mExecutor, mClientCallbacks)
                        .addReceiver(mInsightReceiver)
                        .addHint(mHint)
                        .build();

        assertThat(client.getHints()).contains(mHint);
        assertThat(client.getReceivers()).contains(mInsightReceiver);
    }
}
