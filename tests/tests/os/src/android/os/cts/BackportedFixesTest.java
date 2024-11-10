/*
 * Copyright (C) 2024 The Android Open Source Project
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
package android.os.cts;

import android.os.Flags;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.cts.backportedfixes.BackportedFixRule;
import com.android.cts.backportedfixes.BackportedFixTest;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

/**
 * Tests for backported fixes used in {@link BuildTest}.
 *
 * <p>
 * Additional test for backported fixes should go in the test suite appropriate to the
 * affected code and be annotated {@link BackportedFixTest}.
 */
@RunWith(AndroidJUnit4.class)
public class BackportedFixesTest {

    @Rule(order = 0)
    public final CheckFlagsRule checkFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();
    @Rule(order = 1)
    public TestRule mKnownIssueRule = new BackportedFixRule();

    @RequiresFlagsEnabled(Flags.FLAG_API_FOR_BACKPORTED_FIXES)
    @BackportedFixTest(350037023L)
    @Test
    public void alwaysFixed() {
        // always passes
    }

    @RequiresFlagsEnabled(Flags.FLAG_API_FOR_BACKPORTED_FIXES)
    @BackportedFixTest(350037348L)
    @Test
    public void neverFixed() {
        Assert.fail(
                "This is never fixed on device. However because it is not declared fixed it "
                        + "will be an AssumptionFailure instead");
    }
}
