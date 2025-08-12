/*
 * Copyright (C) 2022 The Android Open Source Project
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
package android.sdksandbox.webkit.cts;

import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.webkit.Flags;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class SdkSandboxWebChromeClientTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @ClassRule
    public static final WebViewSandboxTestRule sSdkTestSuiteSetup =
            new WebViewSandboxTestRule("android.webkit.cts.WebChromeClientTest");

    @Test
    public void testOnProgressChanged() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testOnProgressChanged");
    }

    @Test
    public void testOnReceivedTitle() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testOnReceivedTitle");
    }

    @Test
    public void testOnReceivedIcon() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testOnReceivedIcon");
    }

    @Test
    public void testWindows() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testWindows");
    }

    @Test
    public void testBlockWindowsSync() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testBlockWindowsSync");
    }

    @Test
    public void testBlockWindowsAsync() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testBlockWindowsAsync");
    }

    @Test
    public void testOnJsAlert() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testOnJsAlert");
    }

    @Test
    public void testOnJsConfirm() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testOnJsConfirm");
    }

    @Test
    public void testOnJsPrompt() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testOnJsPrompt");
    }

    @Test
    public void testOnConsoleMessage() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testOnConsoleMessage");
    }

    @Test
    public void testOnShowFileChooserInputFile() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testOnShowFileChooserInputFile");
    }

    @Test
    public void testOnShowFileChooserInputFileMultiple() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testOnShowFileChooserInputFileMultiple");
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_FILE_SYSTEM_ACCESS)
    public void testOnShowFileChooserOpenReadWrite() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testOnShowFileChooserOpenReadWrite");
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_FILE_SYSTEM_ACCESS)
    public void testOnShowFileChooserDirectory() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testOnShowFileChooserDirectory");
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_FILE_SYSTEM_ACCESS)
    public void testOnShowFileChooserSave() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testOnShowFileChooserSave");
    }

    @Test
    public void testOnJsBeforeUnloadIsCalled() throws Throwable {
        sSdkTestSuiteSetup.assertSdkTestRunPasses("testOnJsBeforeUnloadIsCalled");
    }

}
