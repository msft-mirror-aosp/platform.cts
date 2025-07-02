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

package android.webkit.cts.preconditions;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.ITestLogger;
import com.android.tradefed.result.ByteArrayInputStreamSource;
import com.android.tradefed.result.ITestLoggerReceiver;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.TargetSetupError;

import java.nio.charset.StandardCharsets;

/** Output WebView version information into a tradefed test artifact. */
public class WebViewVersionPreparer implements ITargetPreparer, ITestLoggerReceiver {
    private ITestLogger mLogger;
    private static final String LOG_FILE_NAME = "webview-version";

    @Override
    public void setTestLogger(ITestLogger testLogger) {
        mLogger = testLogger;
    }

    @Override
    public void setUp(TestInformation testInformation)
            throws TargetSetupError, BuildError, DeviceNotAvailableException {
        ITestDevice device = testInformation.getDevice();
        String output = device.executeShellCommand("dumpsys webviewupdate");
        try (InputStreamSource source =
                new ByteArrayInputStreamSource(output.getBytes(StandardCharsets.UTF_8))) {
            mLogger.testLog(LOG_FILE_NAME, LogDataType.DUMPSYS, source);
        }
    }
}
