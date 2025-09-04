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

package android.app.privatecompute.cts;

import static android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT;

import static com.google.common.truth.Truth.assertThat;

import android.app.privatecompute.PccEntity;
import android.app.privatecompute.PccSandboxManager;
import android.content.Context;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.stream.Collectors;

/** CTS tests for {@link PccSandboxManager}. */
@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
public class PccSandboxManagerTest {

    private static final String APP_WITH_FLAG_AND_QUERYABLE_PKG =
            "android.privatecompute.cts.appwithflagandqueryable";
    private static final String APP_WITH_INTENT_AND_PCC_FLAG_PKG =
            "android.privatecompute.cts.appwithintentandpccflag";

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private Context mContext;
    private PccSandboxManager mPccSandboxManager;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mPccSandboxManager = mContext.getSystemService(PccSandboxManager.class);
    }

    @Test
    public void testPccSandboxManagerExists() {
        assertThat(mPccSandboxManager).isNotNull();
    }

    @Test
    public void testGetPccEntities_returnsOnlyVisibleAppsWithFlag() {
        List<PccEntity> entities = mPccSandboxManager.getPccEntities();
        assertThat(entities).isNotNull();
        assertThat(entities).hasSize(2);

        List<String> packageNames =
                entities.stream().map(PccEntity::getPackageName).collect(Collectors.toList());
        assertThat(packageNames)
                .containsExactly(APP_WITH_FLAG_AND_QUERYABLE_PKG, APP_WITH_INTENT_AND_PCC_FLAG_PKG);
    }
}
