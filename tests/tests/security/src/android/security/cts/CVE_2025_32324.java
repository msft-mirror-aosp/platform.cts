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

package android.security.cts;

import static com.android.sts.common.DumpsysUtils.isActivityResumed;
import static com.android.sts.common.SystemUtil.poll;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;

import android.app.Instrumentation;
import android.platform.test.annotations.AsbSecurityTest;
import android.support.test.uiautomator.UiDevice;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.internal.app.PlatLogoActivity;
import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CVE_2025_32324 extends StsExtraBusinessLogicTestCase {

    @AsbSecurityTest(cveBugId = 406763872)
    @Test
    public void testPocCVE_2025_32324() {
        UiDevice uiDevice = null;
        try {
            Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
            uiDevice = UiDevice.getInstance(instrumentation);

            // Fetch PlatLogoActivity name
            final String platLogoActivityName = PlatLogoActivity.class.getName();

            // Launch the PlatLogo activity using 'start' shell command to ensure 'platlogo'
            // is not an exported activity.
            uiDevice.executeShellCommand("am start android/" + platLogoActivityName);

            // Assumption fail if 'platlogo' is exported.
            assume().withMessage("The platlogo activity is exported or already launched.")
                    .that(poll(() -> isActivityResumed(platLogoActivityName)))
                    .isFalse();

            // Launch the PlatLogo activity using 'start-in-vsync' shell command
            uiDevice.executeShellCommand("am start-in-vsync android/" + platLogoActivityName);

            // Without the fix, getCallingUid() returns spoofed UID 1000, bypassing security
            // checks and launching the privileged platlogo activity and the test fails.
            // With the fix, getCallingUid() returns the original UID, blocking the privileged
            // activity launch and the test passes.
            assertWithMessage(
                            "Device is vulnerable to b/406763872 !! Any arbitrary activity can be"
                                    + " launched without root using start-in-vsync")
                    .that(poll(() -> isActivityResumed(platLogoActivityName)))
                    .isFalse();
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }
}
