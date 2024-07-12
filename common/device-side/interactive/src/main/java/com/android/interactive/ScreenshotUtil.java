/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.interactive;

import static com.android.interactive.testrules.TestNameSaver.INTERACTIVE_TEST_NAME;

import android.content.Context;
import android.os.Environment;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import com.android.bedstead.nene.TestApis;

import java.io.File;

/**
 * Utility class for taking screenshots during xTS-Interactive tests.
 *
 * <p>It saves screenshots as files to the /Documents/xts/screenshots/ directory on the external
 * storage.
 */
public final class ScreenshotUtil {

    /**
     * Captures a screenshot and saves it as a file.
     *
     * <p>The name of the current test will be appended as a prefix of the screenshot. And the
     * current system time will be appended as a suffix of the screenshot.
     *
     * @param screenshotName the screenshot name
     */
    public static void captureScreenshot(String screenshotName) {
        captureScreenshot(screenshotName, /* withTestName= */ true, /* withSystemTime= */ true);
    }

    /**
     * Captures a screenshot and saves it as a file.
     *
     * @param screenshotName the screenshot name
     * @param withTestName whether adding the name of the current test as the prefix of the
     *     screenshot
     * @param withSystemTime whether adding the current system time as the suffix of the screenshot
     */
    public static void captureScreenshot(
            String screenshotName, boolean withTestName, boolean withSystemTime) {
        String screenshotDir =
                Environment.getExternalStorageDirectory().getAbsolutePath()
                        + "/Documents/xts/screenshots/";
        File file = new File(screenshotDir);
        if (!file.exists() && !file.mkdirs()) {
            // Let the steps that require screenshots fail immediately.
            throw new RuntimeException("Failed to create " + screenshotDir + " directory on DUT.");
        }

        String screenshotFileName;
        if (withTestName) {
            String testName =
                    TestApis.context()
                            .instrumentedContext()
                            .getSharedPreferences(INTERACTIVE_TEST_NAME, Context.MODE_PRIVATE)
                            .getString(INTERACTIVE_TEST_NAME, "");
            screenshotFileName = testName + "__" + screenshotName;
        } else {
            screenshotFileName = screenshotName;
        }
        screenshotFileName =
                withSystemTime
                        ? screenshotFileName + "__" + System.currentTimeMillis()
                        : screenshotFileName;

        File screenshotFile = new File(screenshotDir, screenshotFileName + ".png");
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                .takeScreenshot(screenshotFile);
    }
}
