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

package android.car.compat;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.pm.PackageManager;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.SystemUtil;
import com.android.interactive.Step;
import com.android.interactive.annotations.Interactive;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

// TODO(b/487790316): Move this test to a new module. Since this test suite contains tests that
// require driving state, they cannot be run on a bench. Ideally, we should move this test to a
// new module that can be run on a bench.
@RunWith(AndroidJUnit4.class)
public class CarBackButtonTest {
    private static final String TEST_PKG = "com.android.car.displaycompat.backpresstest";
    private static final String FEATURE_DISPLAY_COMPAT =
            "android.software.car.display_compatibility";

    private Context mContext;

    @Before
    public void setup() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
    }

    @Test
    @Interactive
    @CddTest(requirements = "7.1.1/A-1-1")
    public void testBackNavigation() throws Exception {
        assumeTrue(
                "Device must be automotive",
                mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE));
        assumeTrue(
                "Device must support display compatibility",
                mContext.getPackageManager().hasSystemFeature(FEATURE_DISPLAY_COMPAT));

        SystemUtil.runShellCommand("am start -n " + TEST_PKG + "/.RedActivity");
        SystemUtil.runShellCommand("am start -n " + TEST_PKG + "/.YellowActivity");

        assertThat(Step.execute(VerifyBackNavigatedToRedStep.class)).isTrue();
    }
}
