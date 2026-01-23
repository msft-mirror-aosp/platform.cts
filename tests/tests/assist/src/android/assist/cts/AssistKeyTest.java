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

package android.assist.cts;

import static android.view.Display.DEFAULT_DISPLAY;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.assist.common.Utils;
import android.content.Intent;
import android.content.pm.PackageManager;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Test;

/** Test that sending KEYCODE_ASSIST starts the assistant. */
public class AssistKeyTest extends AssistTestBase {
    private static final String TAG = "AssistKeyTest";
    private static final String TEST_CASE_TYPE = Utils.EXTRA_ASSIST;

    @Override
    protected void customSetup() throws Exception {
        assumeTrue(mContext.getPackageManager().hasSystemFeature(FEATURE_VOICE_RECOGNIZERS));
        assumeFalse("KEYCODE_ASSIST not supported in automotive", Utils.isAutomotive(mContext));
        startTestActivity(TEST_CASE_TYPE);
    }

    /** Verifies the assistant is launched via the KEYCODE_ASSIST key. */
    @Test
    @ApiTest(apis = {"android.content.Intent#EXTRA_ASSIST_DISPLAY_ID"})
    public void testAssistKeyStartsAssistant() throws Exception {
        startTest(TEST_CASE_TYPE);
        waitForAssistantToBeReady();
        start3pApp(TEST_CASE_TYPE);

        runShellCommand("input keyevent KEYCODE_ASSIST");
        waitForContext(mSessionDataReceivedLatch);
        assertThat(mSessionBundle.getInt(Intent.EXTRA_ASSIST_DISPLAY_ID))
                .isEqualTo(DEFAULT_DISPLAY);
    }

    /** Verifies the assistant is launched on the focused display via the KEYCODE_ASSIST key. */
    @Test
    @ApiTest(apis = {"android.content.Intent#EXTRA_ASSIST_DISPLAY_ID"})
    public void testAssistKeyStartsAssistantOnVirtualDisplay() throws Exception {
        assumeTrue(
                mContext.getPackageManager()
                        .hasSystemFeature(PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS));
        final int virtualDisplayId = createVirtualDisplay();

        startTest(TEST_CASE_TYPE, virtualDisplayId);
        waitForAssistantToBeReady();
        start3pApp(TEST_CASE_TYPE, null, virtualDisplayId);

        runShellCommand("input keyevent KEYCODE_ASSIST");
        waitForContext(mSessionDataReceivedLatch);
        assertThat(mSessionBundle.getInt(Intent.EXTRA_ASSIST_DISPLAY_ID))
                .isEqualTo(virtualDisplayId);
    }
}
